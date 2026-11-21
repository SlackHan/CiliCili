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
CURATED_PROWLARR_INDEXERS = [
    "bt4g",
    "uindex",
    "knaben",
    "limetorrents",
    "torlock",
    "solidtorrents",
    "bitsearch",
]

ZERO_INFO_HASH = "0" * 40


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
    if not domain_sources_path.exists():
        return rules, logs

    raw_cfg = json.loads(domain_sources_path.read_text(encoding="utf-8"))
    entries = raw_cfg.get("entries", []) if isinstance(raw_cfg, dict) else raw_cfg
    if not isinstance(entries, list) or not entries:
        return rules, logs

    rules_by_id = {r["id"]: i for i, r in enumerate(rules) if "id" in r}
    out = list(rules)

    for entry in entries:
        if not isinstance(entry, dict):
            continue
        rule_id = entry.get("id")
        publish_url = (entry.get("publishUrl") or "").strip()
        pattern = (entry.get("domainRegex") or "").strip()
        if rule_id not in rules_by_id or not publish_url or not pattern:
            continue

        try:
            status, _, body = http_get_text(publish_url, timeout=timeout)
            if status != 200:
                logs.append(f"域名发现失败 [{entry.get('name', rule_id)}]: HTTP {status}")
                continue
            match = re.search(pattern, body, flags=re.IGNORECASE)
            if not match:
                logs.append(f"域名发现未匹配 [{entry.get('name', rule_id)}]")
                continue
            found_url = match.group(0)
            idx = rules_by_id[rule_id]
            out[idx] = replace_rule_origin(out[idx], found_url)
            out[idx]["updateTime"] = utc_now_str()
            logs.append(f"域名已更新 [{out[idx]['name']}]: {found_url}")
        except Exception as exc:  # pragma: no cover - network fallback
            logs.append(f"域名发现异常 [{entry.get('name', rule_id)}]: {exc}")

    return out, logs


def _field_to_css(field_spec: Any) -> str:
    if isinstance(field_spec, str):
        sel = field_spec.strip()
        return f"css:{sel}" if sel else ""
    if not isinstance(field_spec, dict):
        return ""
    sel = (field_spec.get("selector") or "").strip()
    if not sel:
        return ""
    attr = (field_spec.get("attribute") or "").strip()
    # Skip non-standard pseudo attributes handled only by Cardigann filters
    if attr and re.fullmatch(r"[a-zA-Z0-9_-]+", attr):
        return f"css:{sel}@{attr}"
    return f"css:{sel}"


def _convert_cardigann_path(path_template: str, inputs: dict[str, Any] | None) -> str | None:
    if not path_template:
        return None
    converted = path_template.strip()
    # Replace common Cardigann keyword/page placeholders
    converted = re.sub(
        r"\{\{\s*(?:\.Query\.Keywords|\.Keywords)\s*\}\}",
        "[keyword]",
        converted,
    )
    converted = re.sub(
        r"\{\{\s*(?:\.Query\.Page|\.Page)\s*\}\}",
        "[page]",
        converted,
    )
    # Strip benign {{ if .Keywords }}...{{ end }} wrappers if keyword already replaced
    if "[keyword]" not in converted and inputs:
        # Try constructing query string from simple inputs mapping
        params = []
        for key, val in inputs.items():
            if isinstance(val, str):
                v = re.sub(
                    r"\{\{\s*(?:\.Query\.Keywords|\.Keywords)\s*\}\}",
                    "[keyword]",
                    val.strip(),
                )
                v = re.sub(
                    r"\{\{\s*(?:\.Query\.Page|\.Page)\s*\}\}",
                    "[page]",
                    v,
                )
                if "{{" not in v and "}}" not in v:
                    params.append(f"{key}={v}")
        if params:
            sep = "&" if "?" in converted else "?"
            converted = f"{converted}{sep}{'&'.join(params)}"

    if "{{" in converted or "}}" in converted:
        return None
    if "[keyword]" not in converted:
        return None
    return converted


def convert_cardigann_yaml(yaml_text: str, rule_id: int) -> dict[str, Any] | None:
    """Converts a simple public GET Cardigann YAML definition into a CiliCili rule dict."""
    if yaml is None:
        return None
    data = yaml.safe_load(yaml_text)
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

    paths = search.get("paths") or []
    inputs = search.get("inputs") if isinstance(search.get("inputs"), dict) else None
    search_rel: str | None = None
    for entry in paths:
        if not isinstance(entry, dict):
            continue
        method = str(entry.get("method", "get")).strip().lower()
        if method != "get":
            continue
        candidate = _convert_cardigann_path(str(entry.get("path", "")), inputs)
        if candidate:
            search_rel = candidate
            break
    if not search_rel:
        return None

    search_url = (
        search_rel
        if search_rel.startswith(("http://", "https://"))
        else f"{main_url}/{search_rel.lstrip('/')}"
    )

    rows = search.get("rows")
    fields = search.get("fields")
    if not isinstance(rows, dict) or not isinstance(fields, dict):
        return None

    group_sel = str(rows.get("selector", "")).strip()
    if not group_sel:
        return None
    group_expr = f"css:{group_sel}"

    title_expr = _field_to_css(fields.get("title"))
    if not title_expr:
        return None

    size_expr = _field_to_css(fields.get("size"))
    date_expr = _field_to_css(fields.get("date"))
    magnet_direct = _field_to_css(fields.get("magnet"))
    details_expr = _field_to_css(fields.get("details"))
    download_expr = _field_to_css(fields.get("download"))

    if magnet_direct:
        url_expr = ""
        magnet_expr = magnet_direct
    elif details_expr:
        url_expr = details_expr
        magnet_expr = download_expr
    elif download_expr:
        url_expr = ""
        magnet_expr = download_expr
    else:
        return None

    now_str = utc_now_str()
    name = str(data.get("name") or data.get("id") or f"Indexer {rule_id}").strip()
    info = str(data.get("description") or f"Synced from Prowlarr ({data.get('id', rule_id)})").strip()

    return {
        "id": rule_id,
        "name": name,
        "info": info,
        "state": True,
        "time": now_str,
        "updateTime": now_str,
        "sourceType": "HTML",
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


def sync_prowlarr_public_rules(
    start_id: int = 10201,
    timeout: int = 12,
) -> tuple[list[dict[str, Any]], list[str]]:
    logs: list[str] = []
    if yaml is None:
        logs.append("跳过 Prowlarr 上游同步：未安装 PyYAML")
        return [], logs

    converted: list[dict[str, Any]] = []
    next_id = start_id
    for slug in CURATED_PROWLARR_INDEXERS:
        raw_url = f"{PROWLARR_RAW_BASE}/{slug}.yml"
        try:
            status, _, body = http_get_text(raw_url, timeout=timeout)
            if status != 200:
                logs.append(f"拉取 Prowlarr 规则失败 [{slug}]: HTTP {status}")
                continue
            rule = convert_cardigann_yaml(body, next_id)
            if rule is None:
                logs.append(f"跳过不兼容的 Cardigann 规则 [{slug}]")
                continue
            converted.append(rule)
            logs.append(f"已转换 Prowlarr 公开规则 [{slug}] -> #{next_id} {rule['name']}")
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
