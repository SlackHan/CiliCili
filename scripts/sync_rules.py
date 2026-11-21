#!/usr/bin/env python3
"""Sync and health-check CiliCili site rules for GitHub Actions and local maintenance."""

from __future__ import annotations

import argparse
import concurrent.futures
import copy
import datetime as dt
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

try:
    import yaml  # type: ignore
except ImportError:  # pragma: no cover - optional when --no-sync-prowlarr is used
    yaml = None

DEFAULT_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"
)

PROWLARR_RAW_BASE = (
    "https://raw.githubusercontent.com/Prowlarr/Indexers/master/definitions/v11"
)
PROWLARR_DEFINITIONS_ROOT = (
    "https://raw.githubusercontent.com/Prowlarr/Indexers/master/definitions"
)
PROWLARR_DEFINITION_VERSIONS: tuple[str, ...] = ("v11", "v12", "v10", "v9")
CURATED_PROWLARR_INDEXERS = [
    "uindex",
    "nyaasi",
    "tokyotosho",
    "dmhy",
    "eztv",
    "limetorrents",
    "thepiratebay",
    "1337x",
    "acgrip",
    "0magnet",
    "magnetcat",
]

ZERO_INFO_HASH = "0" * 40


def _strip_yaml_comment(line: str) -> str:
    in_single = False
    in_double = False
    escaped = False
    for i, ch in enumerate(line):
        if escaped:
            escaped = False
            continue
        if ch == "\\" and in_double:
            escaped = True
            continue
        if ch == "'" and not in_double:
            in_single = not in_single
        elif ch == '"' and not in_single:
            in_double = not in_double
        elif ch == "#" and not in_single and not in_double:
            if i == 0 or line[i - 1].isspace():
                return line[:i].rstrip()
    return line.rstrip()


def _find_unquoted_colon(text: str) -> int:
    in_single = False
    in_double = False
    escaped = False
    for i, ch in enumerate(text):
        if escaped:
            escaped = False
            continue
        if ch == "\\" and in_double:
            escaped = True
            continue
        if ch == "'" and not in_double:
            in_single = not in_single
        elif ch == '"' and not in_single:
            in_double = not in_double
        elif ch == ":" and not in_single and not in_double:
            if i + 1 == len(text) or text[i + 1].isspace():
                return i
    return -1


def _parse_yaml_scalar(val: str) -> Any:
    val = val.strip()
    if not val:
        return ""
    if (val.startswith('"') and val.endswith('"')) or (
        val.startswith("'") and val.endswith("'")
    ):
        inner = val[1:-1]
        if val.startswith('"'):
            inner = inner.replace('\\"', '"').replace("\\\\", "\\")
        return inner
    low = val.lower()
    if low == "true":
        return True
    if low == "false":
        return False
    if low in ("null", "~"):
        return None
    if val.isdigit() or (val.startswith("-") and val[1:].isdigit()):
        return int(val)
    return val


def _parse_yaml_block(
    lines: list[tuple[int, str]],
    idx: int,
    min_indent: int,
) -> tuple[Any, int]:
    if idx >= len(lines) or lines[idx][0] < min_indent:
        return None, idx
    base_indent, first_text = lines[idx]
    if first_text == "-" or first_text.startswith("- "):
        arr: list[Any] = []
        while (
            idx < len(lines)
            and lines[idx][0] == base_indent
            and (lines[idx][1] == "-" or lines[idx][1].startswith("- "))
        ):
            item_text = lines[idx][1][1:].strip()
            idx += 1
            if not item_text:
                val, idx = _parse_yaml_block(lines, idx, base_indent + 1)
                arr.append(val)
            elif (
                not item_text.startswith("{")
                and not item_text.startswith("[")
                and _find_unquoted_colon(item_text) != -1
            ):
                sub_lines: list[tuple[int, str]] = [(base_indent + 2, item_text)]
                while idx < len(lines) and lines[idx][0] > base_indent:
                    sub_lines.append(lines[idx])
                    idx += 1
                val, _ = _parse_yaml_block(sub_lines, 0, base_indent + 2)
                arr.append(val)
            else:
                arr.append(_parse_yaml_scalar(item_text))
                while idx < len(lines) and lines[idx][0] > base_indent:
                    idx += 1
        return arr, idx

    mapping: dict[str, Any] = {}
    while idx < len(lines) and lines[idx][0] >= base_indent:
        cur_indent, text = lines[idx]
        if cur_indent > base_indent:
            idx += 1
            continue
        if text == "-" or text.startswith("- "):
            break
        colon = _find_unquoted_colon(text)
        if colon == -1:
            idx += 1
            continue
        key = str(_parse_yaml_scalar(text[:colon]))
        val_part = text[colon + 1 :].strip()
        idx += 1
        if val_part:
            mapping[key] = _parse_yaml_scalar(val_part)
        else:
            if idx < len(lines) and (
                lines[idx][0] > cur_indent
                or (
                    lines[idx][0] == cur_indent
                    and (lines[idx][1] == "-" or lines[idx][1].startswith("- "))
                )
            ):
                child_indent = lines[idx][0]
                val, idx = _parse_yaml_block(lines, idx, child_indent)
                mapping[key] = val
            else:
                mapping[key] = None
    return mapping, idx


def load_yaml(yaml_text: str) -> Any:
    """Loads YAML using PyYAML if available, falling back to the built-in Cardigann YAML parser."""
    if yaml is not None:
        return yaml.safe_load(yaml_text)
    prepared: list[tuple[int, str]] = []
    for raw in yaml_text.splitlines():
        cleaned = _strip_yaml_comment(raw)
        if not cleaned.strip():
            continue
        indent = len(cleaned) - len(cleaned.lstrip(" "))
        prepared.append((indent, cleaned.strip()))
    if not prepared:
        return None
    res, _ = _parse_yaml_block(prepared, 0, prepared[0][0])
    return res


def utc_now_str() -> str:
    return dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%d %H:%M:%S")


def http_get_text(
    url: str,
    headers: dict[str, str] | None = None,
    timeout: int = 12,
) -> tuple[int, dict[str, str], str]:
    req_headers = {
        "User-Agent": DEFAULT_USER_AGENT,
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    }
    if headers:
        for k, v in headers.items():
            if k.lower() != "host" and v:
                req_headers[k] = v

    req = urllib.request.Request(url, headers=req_headers, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            charset = resp.headers.get_content_charset() or "utf-8"
            text = raw.decode(charset, errors="replace")
            resp_headers = {k.lower(): v for k, v in resp.headers.items()}
            return resp.status, resp_headers, text
    except urllib.error.HTTPError as err:
        raw = err.read() if err.fp else b""
        text = raw.decode("utf-8", errors="replace")
        resp_headers = {k.lower(): v for k, v in err.headers.items()} if err.headers else {}
        return err.code, resp_headers, text


def replace_rule_origin(rule: dict[str, Any], new_origin: str) -> dict[str, Any]:
    """Updates mainUrl, searchUrl, and Referer/Origin/Host headers to use [new_origin]."""
    updated = copy.deepcopy(rule)
    parsed_new = urllib.parse.urlparse(new_origin.strip())
    if not parsed_new.scheme or not parsed_new.netloc:
        return updated

    clean_origin = f"{parsed_new.scheme}://{parsed_new.netloc}"
    req = updated.setdefault("request", {})
    req["mainUrl"] = clean_origin

    old_search = req.get("searchUrl", "")
    if old_search:
        parsed_old = urllib.parse.urlparse(old_search)
        if parsed_old.scheme and parsed_old.netloc:
            req["searchUrl"] = urllib.parse.urlunparse(
                parsed_old._replace(scheme=parsed_new.scheme, netloc=parsed_new.netloc)
            )

    headers = req.get("header")
    if isinstance(headers, dict):
        for key in list(headers.keys()):
            lower = key.lower()
            if lower == "referer":
                headers[key] = f"{clean_origin}/"
            elif lower == "origin":
                headers[key] = clean_origin
            elif lower == "host":
                headers[key] = parsed_new.netloc

    return updated


def apply_domain_sources(
    rules: list[dict[str, Any]],
    domain_sources_path: Path,
    timeout: int = 12,
) -> tuple[list[dict[str, Any]], list[str]]:
    logs: list[str] = []
    out = [copy.deepcopy(r) for r in rules]
    if not domain_sources_path.exists():
        return out, logs

    try:
        raw_cfg = json.loads(domain_sources_path.read_text(encoding="utf-8"))
    except Exception as exc:
        logs.append(f"域名发现配置解析失败: {exc}")
        return out, logs

    entries = raw_cfg.get("entries", []) if isinstance(raw_cfg, dict) else raw_cfg
    if not isinstance(entries, list) or not entries:
        return out, logs

    rules_by_id = {r["id"]: i for i, r in enumerate(out) if "id" in r}

    for entry in entries:
        if not isinstance(entry, dict) or entry.get("enabled") is False:
            continue
        rule_id = entry.get("id")
        pattern = (entry.get("domainRegex") or "").strip()
        candidate_urls: list[str] = []
        primary_url = (entry.get("publishUrl") or "").strip()
        if primary_url:
            candidate_urls.append(primary_url)
        extra_urls = entry.get("publishUrls")
        if isinstance(extra_urls, list):
            for u in extra_urls:
                u_str = str(u or "").strip()
                if u_str and u_str not in candidate_urls:
                    candidate_urls.append(u_str)

        if rule_id not in rules_by_id or not candidate_urls or not pattern:
            continue

        entry_name = entry.get("name", rule_id)
        updated_domain = False
        last_failure_kind = "unmatched"
        last_failure_detail = ""

        for publish_url in candidate_urls:
            try:
                status, _, body = http_get_text(publish_url, timeout=timeout)
                if status != 200:
                    last_failure_kind = "http"
                    last_failure_detail = f"HTTP {status}"
                    continue
                match = re.search(pattern, body, flags=re.IGNORECASE)
                if not match:
                    last_failure_kind = "unmatched"
                    continue
                found_url = match.group(0).strip().rstrip("/")
                parsed_found = urllib.parse.urlparse(found_url)
                if not parsed_found.scheme or not parsed_found.netloc:
                    last_failure_kind = "unmatched"
                    continue
                idx = rules_by_id[rule_id]
                out[idx] = replace_rule_origin(out[idx], found_url)
                out[idx]["updateTime"] = utc_now_str()
                logs.append(f"域名已更新 [{out[idx]['name']}]: {found_url}")
                updated_domain = True
                break
            except Exception as exc:  # pragma: no cover - network fallback
                last_failure_kind = "error"
                last_failure_detail = str(exc)

        if not updated_domain:
            if last_failure_kind == "http":
                logs.append(f"域名发现失败 [{entry_name}]: {last_failure_detail}")
            elif last_failure_kind == "error":
                logs.append(f"域名发现异常 [{entry_name}]: {last_failure_detail}")
            else:
                logs.append(f"域名发现未匹配 [{entry_name}]")

    return out, logs


def _extract_config_defaults(data: dict[str, Any]) -> dict[str, str]:
    defaults: dict[str, str] = {}
    settings = data.get("settings")
    if not isinstance(settings, list):
        return defaults
    for item in settings:
        if not isinstance(item, dict):
            continue
        name = str(item.get("name") or "").strip()
        if not name:
            continue
        default_val = item.get("default")
        defaults[name] = "" if default_val is None else str(default_val).strip()
    return defaults


def _eval_cardigann_condition(cond: str, config_defaults: dict[str, str]) -> bool:
    cond_str = cond.strip()
    config_refs = re.findall(r"\.Config\.([a-zA-Z0-9_-]+)", cond_str)
    has_keywords = ".Keywords" in cond_str or ".Query.Keywords" in cond_str
    if config_refs:
        return not has_keywords and any(
            bool(config_defaults.get(k, "").strip())
            and config_defaults.get(k, "").strip().lower() not in ("false", "0")
            for k in config_refs
        )
    return has_keywords


def _expand_cardigann_template(
    template: str,
    config_defaults: dict[str, str] | None = None,
) -> str:
    if not template:
        return ""
    cfg = config_defaults or {}
    expanded = template.strip()

    # Replace simple value placeholders first so conditional branches become plain text
    expanded = re.sub(
        r"\{\{\s*(?:\.Query\.Keywords|\.Keywords)\s*\}\}",
        "[keyword]",
        expanded,
    )
    expanded = re.sub(
        r"\{\{\s*(?:\.Query\.Page|\.Page)\s*\}\}",
        "[page]",
        expanded,
    )
    expanded = re.sub(
        r"\{\{\s*\.Config\.([a-zA-Z0-9_-]+)\s*\}\}",
        lambda m: cfg.get(m.group(1), ""),
        expanded,
    )
    expanded = re.sub(
        r"\{\{\s*join\s+\.Categories\s+[^}]*\}\}",
        "0",
        expanded,
    )
    expanded = re.sub(
        r"\{\{\s*\.Today\.Year\s*\}\}",
        str(dt.datetime.now(dt.timezone.utc).year),
        expanded,
    )

    if_pattern = re.compile(
        r"\{\{\s*if\s+([^{}]+?)\s*\}\}([^{}]*?)(?:\{\{\s*else\s*\}\}([^{}]*?))?\{\{\s*end\s*\}\}"
    )
    for _ in range(6):
        if not if_pattern.search(expanded):
            break
        expanded = if_pattern.sub(
            lambda m: (m.group(2) or "")
            if _eval_cardigann_condition(m.group(1), cfg)
            else (m.group(3) or ""),
            expanded,
        )

    return expanded.strip()


def _field_to_css(
    field_spec: Any,
    config_defaults: dict[str, str] | None = None,
    raw_selector: bool = False,
) -> str:
    prefix = "" if raw_selector else "css:"
    if isinstance(field_spec, str):
        sel = _expand_cardigann_template(field_spec, config_defaults)
        if not sel or "{{" in sel or "}}" in sel:
            return ""
        return f"{prefix}{sel}"
    if not isinstance(field_spec, dict):
        return ""
    sel = _expand_cardigann_template(
        str(field_spec.get("selector") or ""),
        config_defaults,
    )
    if not sel or "{{" in sel or "}}" in sel:
        return ""
    if raw_selector:
        return sel
    attr = str(field_spec.get("attribute") or "").strip()
    # Skip non-standard pseudo attributes handled only by Cardigann filters
    if attr and re.fullmatch(r"[a-zA-Z0-9_-]+", attr):
        return f"css:{sel}@{attr}"
    return f"css:{sel}"


def _convert_cardigann_path(
    path_template: str,
    inputs: dict[str, Any] | None,
    config_defaults: dict[str, str] | None = None,
) -> str | None:
    if not path_template:
        return None
    converted = _expand_cardigann_template(path_template, config_defaults)
    if not converted:
        return None

    if inputs:
        params: list[str] = []
        for key, val in inputs.items():
            if val is None or not isinstance(val, (str, int, float)):
                continue
            raw_val = str(val).strip()
            v = _expand_cardigann_template(raw_val, config_defaults)
            if not v or "{{" in v or "}}" in v:
                continue
            if f"{key}=" in converted:
                continue
            params.append(f"{key}={v}")
        if params:
            sep = "&" if "?" in converted else "?"
            converted = f"{converted}{sep}{'&'.join(params)}"

    if "{{" in converted or "}}" in converted:
        return None
    if "[keyword]" not in converted:
        return None
    return converted


def _extract_top_download_css(
    data: dict[str, Any],
    config_defaults: dict[str, str],
) -> str:
    top_dl = data.get("download")
    if not isinstance(top_dl, dict):
        return ""
    selectors = top_dl.get("selectors")
    if not isinstance(selectors, list):
        return ""
    for item in selectors:
        css = _field_to_css(item, config_defaults)
        if css:
            return css
    return ""


def convert_cardigann_yaml(yaml_text: str, rule_id: int) -> dict[str, Any] | None:
    """Converts a public GET Cardigann YAML definition into a CiliCili rule dict."""
    try:
        data = load_yaml(yaml_text)
    except Exception:
        return None
    if not isinstance(data, dict):
        return None
    if str(data.get("type", "")).strip().lower() != "public":
        return None
    if data.get("login"):
        return None

    links = data.get("links") or []
    if not isinstance(links, list) or not links:
        return None
    main_url = str(links[0]).strip().rstrip("/")
    if not main_url.startswith(("http://", "https://")):
        return None

    search = data.get("search")
    if not isinstance(search, dict):
        return None

    config_defaults = _extract_config_defaults(data)
    paths = search.get("paths") or []
    search_inputs = search.get("inputs") if isinstance(search.get("inputs"), dict) else {}
    search_rel: str | None = None
    is_json_response = False

    for entry in paths:
        if not isinstance(entry, dict):
            continue
        method = str(entry.get("method", "get")).strip().lower()
        if method != "get":
            continue
        merged_inputs: dict[str, Any] = {}
        entry_inputs = entry.get("inputs")
        if isinstance(entry_inputs, dict):
            merged_inputs.update(entry_inputs)
        if search_inputs:
            for k, v in search_inputs.items():
                merged_inputs.setdefault(k, v)

        candidate = _convert_cardigann_path(
            str(entry.get("path", "")),
            merged_inputs or None,
            config_defaults,
        )
        if candidate:
            search_rel = candidate
            resp_cfg = entry.get("response")
            if (
                isinstance(resp_cfg, dict)
                and str(resp_cfg.get("type", "")).strip().lower() == "json"
            ):
                is_json_response = True
            break
    if not search_rel:
        return None

    if search_rel.startswith(("http://", "https://")):
        search_url = search_rel
        parsed_search = urllib.parse.urlparse(search_url)
        if parsed_search.scheme and parsed_search.netloc:
            main_url = f"{parsed_search.scheme}://{parsed_search.netloc}"
    else:
        search_url = f"{main_url}/{search_rel.lstrip('/')}"

    rows = search.get("rows")
    fields = search.get("fields")
    if not isinstance(rows, dict) or not isinstance(fields, dict):
        return None

    group_sel = _expand_cardigann_template(
        str(rows.get("selector", "")),
        config_defaults,
    )
    if not group_sel or "{{" in group_sel or "}}" in group_sel:
        return None

    if is_json_response:
        source_type = "JSON_API"
        group_expr = "" if group_sel == "$" else group_sel
    else:
        source_type = "HTML"
        group_expr = f"css:{group_sel}"

    title_expr = _field_to_css(
        fields.get("title"),
        config_defaults,
        raw_selector=is_json_response,
    ) or _field_to_css(
        fields.get("title_default"),
        config_defaults,
        raw_selector=is_json_response,
    )
    if not title_expr:
        return None
    if not is_json_response and title_expr.endswith("@href"):
        title_expr = title_expr[: -len("@href")]

    size_expr = _field_to_css(
        fields.get("size"),
        config_defaults,
        raw_selector=is_json_response,
    )
    date_expr = (
        _field_to_css(
            fields.get("date"),
            config_defaults,
            raw_selector=is_json_response,
        )
        or _field_to_css(
            fields.get("date_added"),
            config_defaults,
            raw_selector=is_json_response,
        )
        or _field_to_css(
            fields.get("date_default"),
            config_defaults,
            raw_selector=is_json_response,
        )
    )
    magnet_direct = _field_to_css(
        fields.get("magnet"),
        config_defaults,
        raw_selector=is_json_response,
    )
    details_expr = _field_to_css(
        fields.get("details"),
        config_defaults,
        raw_selector=is_json_response,
    )
    download_expr = _field_to_css(
        fields.get("download"),
        config_defaults,
        raw_selector=is_json_response,
    )
    infohash_expr = _field_to_css(
        fields.get("infohash"),
        config_defaults,
        raw_selector=is_json_response,
    )
    top_download_expr = (
        "" if is_json_response else _extract_top_download_css(data, config_defaults)
    )

    if magnet_direct:
        url_expr = ""
        magnet_expr = magnet_direct
    elif top_download_expr and (details_expr or download_expr):
        url_expr = details_expr or download_expr
        magnet_expr = top_download_expr
    elif download_expr and "magnet" in download_expr.lower():
        url_expr = ""
        magnet_expr = download_expr
    elif details_expr and download_expr:
        url_expr = details_expr
        magnet_expr = download_expr
    elif download_expr:
        url_expr = ""
        magnet_expr = download_expr
    elif infohash_expr:
        url_expr = ""
        magnet_expr = infohash_expr
    else:
        return None

    now_str = utc_now_str()
    name = str(data.get("name") or data.get("id") or f"Indexer {rule_id}").strip()
    info = str(
        data.get("description") or f"Synced from Prowlarr ({data.get('id', rule_id)})"
    ).strip()

    return {
        "id": rule_id,
        "name": name,
        "info": info,
        "state": True,
        "time": now_str,
        "updateTime": now_str,
        "sourceType": source_type,
        "request": {
            "method": "GET",
            "mainUrl": main_url,
            "searchUrl": search_url,
            "header": {
                "Referer": f"{main_url}/",
                "User-Agent": DEFAULT_USER_AGENT,
            },
        },
        "expressionModel": {
            "group": group_expr,
            "title": title_expr,
            "date": date_expr,
            "size": size_expr,
            "url": url_expr,
            "magnet": magnet_expr,
        },
    }


def fetch_prowlarr_definition(
    slug: str,
    versions: tuple[str, ...] | list[str] = PROWLARR_DEFINITION_VERSIONS,
    timeout: int = 12,
) -> tuple[int, str, str]:
    """Fetches a Cardigann YAML definition from Prowlarr/Indexers with version directory fallback."""
    version_list = list(versions) if versions else ["v11"]
    last_status = 404
    last_ver = version_list[0]
    for ver in version_list:
        last_ver = ver
        raw_url = f"{PROWLARR_DEFINITIONS_ROOT}/{ver}/{slug}.yml"
        status, _, body = http_get_text(raw_url, timeout=timeout)
        last_status = status
        if status == 200 and body.strip():
            return 200, ver, body
    return last_status, last_ver, ""


def sync_prowlarr_public_rules(
    start_id: int = 10201,
    timeout: int = 12,
    indexers: list[str] | None = None,
    versions: tuple[str, ...] | list[str] = PROWLARR_DEFINITION_VERSIONS,
) -> tuple[list[dict[str, Any]], list[str]]:
    logs: list[str] = []
    converted: list[dict[str, Any]] = []
    next_id = start_id
    target_indexers = indexers if indexers is not None else CURATED_PROWLARR_INDEXERS

    for slug in target_indexers:
        try:
            status, ver, body = fetch_prowlarr_definition(
                slug, versions=versions, timeout=timeout
            )
            if status != 200:
                logs.append(f"拉取 Prowlarr 规则失败 [{slug}]: HTTP {status}")
                continue
            rule = convert_cardigann_yaml(body, next_id)
            if rule is None:
                logs.append(f"跳过不兼容的 Cardigann 规则 [{slug}]")
                continue
            converted.append(rule)
            logs.append(
                f"已转换 Prowlarr 公开规则 [{slug}@{ver}] -> #{next_id} {rule['name']}"
            )
            next_id += 1
        except Exception as exc:  # pragma: no cover - network fallback
            logs.append(f"同步 Prowlarr 规则异常 [{slug}]: {exc}")

    return converted, logs


def is_valid_json_api_response(body: str, group_expr: str) -> tuple[bool, int, str]:
    try:
        root = json.loads(body)
    except Exception as exc:
        return False, 0, f"JSON 解析失败: {exc}"

    current: Any = root
    path = group_expr.strip()
    if path and path != "$":
        path = path[2:] if path.startswith("$.") else path.lstrip("$")
        for seg in path.split("."):
            seg = seg.strip()
            if not seg:
                continue
            if isinstance(current, dict) and seg in current:
                current = current[seg]
            else:
                return False, 0, f"JSON 路径缺失: {group_expr}"

    if not isinstance(current, list):
        return False, 0, "JSON 结果根节点非数组"

    valid_count = 0
    for item in current:
        if not isinstance(item, dict):
            continue
        title = str(item.get("title") or item.get("name") or "").strip()
        if not title or title.lower() == "no results returned":
            continue
        info_hash = str(
            item.get("info_hash") or item.get("infoHash") or item.get("hash") or ""
        ).strip().lower()
        if info_hash == ZERO_INFO_HASH:
            continue
        valid_count += 1

    if valid_count == 0:
        return False, 0, "JSON 接口无有效结果"
    return True, valid_count, f"命中 {valid_count} 条"


def is_valid_rss_response(body: str) -> tuple[bool, int, str]:
    try:
        root = ET.fromstring(body.strip())
    except Exception as exc:
        return False, 0, f"XML 解析失败: {exc}"

    count = 0
    for elem in root.iter():
        tag = elem.tag.split("}")[-1].lower()
        if tag in ("item", "entry"):
            count += 1

    if count == 0:
        return False, 0, "RSS/Atom 无 <item> 或 <entry>"
    return True, count, f"命中 {count} 条"


def is_cloudflare_challenge(status_code: int, headers: dict[str, str], body: str) -> bool:
    if status_code not in (403, 503, 429):
        return False
    if headers.get("cf-mitigated", "").lower() == "challenge":
        return True
    lower = body.lower()
    return (
        "just a moment..." in lower
        or "challenge-platform" in lower
        or "__cf_chl_" in lower
        or "enable javascript and cookies to continue" in lower
    )


def build_probe_url(rule: dict[str, Any], keyword: str) -> str:
    encoded = urllib.parse.quote(keyword.strip())
    search_url = str(rule.get("request", {}).get("searchUrl", ""))
    return (
        search_url.replace("[keyword]", encoded)
        .replace("[page]", "1")
        .replace("[offset]", "0")
    )


def probe_rule(
    rule: dict[str, Any],
    keyword: str = "ubuntu",
    timeout: int = 12,
) -> tuple[bool, int, str]:
    url = build_probe_url(rule, keyword)
    if not url.startswith(("http://", "https://")):
        return False, 0, "searchUrl 无效"

    start = time.monotonic()
    headers = rule.get("request", {}).get("header", {})
    source_type = str(rule.get("sourceType", "HTML")).upper()

    try:
        status, resp_headers, body = http_get_text(
            url,
            headers=headers if isinstance(headers, dict) else None,
            timeout=timeout,
        )
        latency_ms = int((time.monotonic() - start) * 1000)

        if is_cloudflare_challenge(status, resp_headers, body):
            return False, latency_ms, f"HTTP {status} Cloudflare 盾"
        if status < 200 or status >= 300:
            return False, latency_ms, f"HTTP {status}"

        if source_type == "JSON_API":
            group_expr = str(rule.get("expressionModel", {}).get("group", ""))
            ok, _, msg = is_valid_json_api_response(body, group_expr)
            return ok, latency_ms, msg
        if source_type == "RSS":
            ok, _, msg = is_valid_rss_response(body)
            return ok, latency_ms, msg

        if len(body.strip()) < 512:
            return False, latency_ms, "HTML 响应内容过短"
        return True, latency_ms, "HTTP 200 可达"
    except Exception as exc:
        latency_ms = int((time.monotonic() - start) * 1000)
        return False, latency_ms, f"网络异常: {exc}"


def merge_rules_by_id(
    base_rules: list[dict[str, Any]],
    extra_rules: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    by_id: dict[int, dict[str, Any]] = {}
    for rule in base_rules + extra_rules:
        rid = rule.get("id")
        if isinstance(rid, int):
            by_id[rid] = copy.deepcopy(rule)
    return [by_id[k] for k in sorted(by_id.keys())]


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Sync & health-check CiliCili site rules.")
    parser.add_argument(
        "--base-rules",
        type=Path,
        default=Path("app/src/main/assets/sites_default.json"),
    )
    parser.add_argument(
        "--domain-sources",
        type=Path,
        default=Path("scripts/domain_sources.json"),
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("rules/sites_live.json"),
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=Path("rules/sync_report.md"),
    )
    parser.add_argument("--keyword", default="ubuntu")
    parser.add_argument(
        "--sync-prowlarr",
        action=argparse.BooleanOptionalAction,
        default=True,
    )
    parser.add_argument(
        "--probe",
        action=argparse.BooleanOptionalAction,
        default=True,
    )
    parser.add_argument("--keep-failed", action="store_true", default=False)
    parser.add_argument("--timeout", type=int, default=12)
    parser.add_argument("--workers", type=int, default=8)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    base_rules = json.loads(args.base_rules.read_text(encoding="utf-8"))
    if not isinstance(base_rules, list):
        raise ValueError("base-rules must contain a JSON array")

    logs: list[str] = []
    updated_base, domain_logs = apply_domain_sources(
        base_rules, args.domain_sources, timeout=args.timeout
    )
    logs.extend(domain_logs)

    prowlarr_rules: list[dict[str, Any]] = []
    if args.sync_prowlarr:
        prowlarr_rules, prowlarr_logs = sync_prowlarr_public_rules(timeout=args.timeout)
        logs.extend(prowlarr_logs)

    merged = merge_rules_by_id(updated_base, prowlarr_rules)
    probe_rows: list[tuple[dict[str, Any], bool, int, str]] = []
    final_rules: list[dict[str, Any]] = []

    if args.probe:
        with concurrent.futures.ThreadPoolExecutor(max_workers=max(1, args.workers)) as pool:
            future_map = {
                pool.submit(probe_rule, rule, args.keyword, args.timeout): rule
                for rule in merged
            }
            results_by_id: dict[int, tuple[bool, int, str]] = {}
            for future in concurrent.futures.as_completed(future_map):
                rule = future_map[future]
                ok, latency_ms, msg = future.result()
                results_by_id[rule["id"]] = (ok, latency_ms, msg)

        now_str = utc_now_str()
        for rule in merged:
            ok, latency_ms, msg = results_by_id[rule["id"]]
            probe_rows.append((rule, ok, latency_ms, msg))
            if ok:
                rule_copy = copy.deepcopy(rule)
                rule_copy["state"] = True
                rule_copy["updateTime"] = now_str
                final_rules.append(rule_copy)
            elif args.keep_failed:
                rule_copy = copy.deepcopy(rule)
                rule_copy["state"] = False
                final_rules.append(rule_copy)
    else:
        final_rules = merged
        for rule in merged:
            probe_rows.append((rule, True, 0, "未测活（--no-probe）"))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(final_rules, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    ok_count = sum(1 for _, ok, _, _ in probe_rows if ok)
    report_lines = [
        "# CiliCili 规则同步与测活报告",
        "",
        f"- 更新时间 (UTC): `{utc_now_str()}`",
        f"- 候选总数: `{len(merged)}`",
        f"- 通过测活 / 保留输出: `{ok_count}` / `{len(final_rules)}`",
        "",
    ]
    if logs:
        report_lines.extend(["## 同步日志", ""])
        report_lines.extend([f"- {line}" for line in logs])
        report_lines.append("")

    report_lines.extend(
        [
            "## 节点状态明细",
            "",
            "| ID | 名称 | 类型 | 状态 | 耗时 | 说明 |",
            "| --- | --- | --- | --- | --- | --- |",
        ]
    )
    for rule, ok, latency_ms, msg in probe_rows:
        st = "可用" if ok else "剔除"
        stype = rule.get("sourceType", "HTML")
        report_lines.append(
            f"| {rule['id']} | {rule['name']} | {stype} | {st} | {latency_ms}ms | {msg} |"
        )

    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text("\n".join(report_lines) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
