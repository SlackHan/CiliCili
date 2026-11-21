import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import sync_rules


SAMPLE_PUBLIC_CARDIGANN_YAML = """
id: samplepublic
name: Sample Public Tracker
description: Clean public GET tracker
type: public
links:
  - https://sample.example.org/
search:
  paths:
    - path: search/{{ .Keywords }}/{{ .Page }}/
      method: get
  rows:
    selector: table.torrents tbody tr
  fields:
    title:
      selector: td.name a.title
    details:
      selector: td.name a.title
      attribute: href
    size:
      selector: td.size
    date:
      selector: td.date
    magnet:
      selector: td.dl a.magnet
      attribute: href
"""

SAMPLE_PRIVATE_CARDIGANN_YAML = """
id: sampleprivate
name: Private Tracker
type: private
links:
  - https://private.example.org/
search:
  paths:
    - path: browse.php?q={{ .Keywords }}
      method: get
  rows:
    selector: table tr
  fields:
    title:
      selector: td a
    download:
      selector: td a
      attribute: href
"""

SAMPLE_CONDITIONAL_LIMETORRENTS_YAML = """
id: limetorrents
name: LimeTorrents
description: LimeTorrents is a Public general torrent index
type: public
links:
  - https://www.limetorrents.lol/
settings:
  - name: primarydownloadlink
    type: select
    default: "magnet:"
  - name: sort
    type: select
    default: date
download:
  selectors:
    - selector: a.csprite_dltorrent[href*="{{ .Config.primarydownloadlink }}"]
      attribute: href
search:
  paths:
    - path: "{{ if .Keywords }}search/all/{{ .Keywords }}/{{ .Config.sort }}/1/{{ else }}/latest100{{ end }}"
  rows:
    selector: .table2 > tbody > tr[bgcolor]
  fields:
    title:
      selector: div.tt-name > a[href^="/"]
      attribute: href
    details:
      selector: div.tt-name > a[href^="/"]
      attribute: href
    download:
      selector: div.tt-name > a[href^="/"]
      attribute: href
    date:
      selector: td:nth-child(2)
    size:
      selector: td:nth-child(3)
"""

SAMPLE_INFOHASH_AND_ENTRY_INPUTS_YAML = """
id: magnetcat
name: MagnetCat
type: public
links:
  - https://magnetcat.example.org/
settings:
  - name: cat-id
    type: select
    default: 0
  - name: sort
    type: select
    default: 2
search:
  paths:
    - path: search.html
      inputs:
        q: "{{ if .Keywords }}{{ .Keywords }}{{ else }}{{ .Today.Year }}{{ end }}"
        cat: "{{ .Config.cat-id }}"
  inputs:
    sort: "{{ .Config.sort }}"
    unused: "{{ .Config.nonexistent }}"
  rows:
    selector: article.zsky-result-row
  fields:
    title_default:
      selector: a[href^="/hash/"]
    infohash:
      selector: a[href^="/hash/"]
      attribute: href
    date:
      selector: div.zsky-result-meta span:nth-child(1)
    size:
      selector: div.zsky-result-meta span:nth-child(2)
"""

SAMPLE_JSON_CARDIGANN_YAML = """
id: thepiratebay
name: The Pirate Bay
type: public
links:
  - https://thepiratebay.org/
settings:
  - name: apiurl
    type: text
    default: apibay.org
  - name: uploader
    type: text
search:
  paths:
    - path: "https://{{ .Config.apiurl }}/{{ if .Keywords }}q.php?q={{ .Keywords }}&cat={{ join .Categories \",\" }}{{ else }}precompiled/data_top100.json{{ end }}"
      response:
        type: json
  rows:
    selector: "${{ if .Config.uploader }}:has(username:contains({{ .Config.uploader }})){{ else }}{{ end }}"
  fields:
    title:
      selector: name
    infohash:
      selector: info_hash
    date:
      selector: added
    size:
      selector: size
"""


class SyncRulesTest(unittest.TestCase):
    def test_convert_cardigann_yaml_public_and_reject_private(self) -> None:
        converted = sync_rules.convert_cardigann_yaml(SAMPLE_PUBLIC_CARDIGANN_YAML, 10201)
        self.assertIsNotNone(converted)
        assert converted is not None
        self.assertEqual(10201, converted["id"])
        self.assertEqual("Sample Public Tracker", converted["name"])
        self.assertEqual("HTML", converted["sourceType"])
        self.assertEqual("https://sample.example.org", converted["request"]["mainUrl"])
        self.assertEqual(
            "https://sample.example.org/search/[keyword]/[page]/",
            converted["request"]["searchUrl"],
        )
        expr = converted["expressionModel"]
        self.assertEqual("css:table.torrents tbody tr", expr["group"])
        self.assertEqual("css:td.name a.title", expr["title"])
        self.assertEqual("css:td.size", expr["size"])
        self.assertEqual("css:td.dl a.magnet@href", expr["magnet"])
        self.assertEqual("", expr["url"])

        self.assertIsNone(sync_rules.convert_cardigann_yaml(SAMPLE_PRIVATE_CARDIGANN_YAML, 10202))

    def test_convert_cardigann_yaml_conditional_template_and_top_download(self) -> None:
        converted = sync_rules.convert_cardigann_yaml(SAMPLE_CONDITIONAL_LIMETORRENTS_YAML, 10205)
        self.assertIsNotNone(converted)
        assert converted is not None
        self.assertEqual("LimeTorrents", converted["name"])
        self.assertEqual(
            "https://www.limetorrents.lol/search/all/[keyword]/date/1/",
            converted["request"]["searchUrl"],
        )
        expr = converted["expressionModel"]
        self.assertEqual('css:div.tt-name > a[href^="/"]@href', expr["url"])
        self.assertEqual('css:a.csprite_dltorrent[href*="magnet:"]@href', expr["magnet"])

    def test_convert_cardigann_yaml_entry_inputs_title_default_and_infohash(self) -> None:
        converted = sync_rules.convert_cardigann_yaml(SAMPLE_INFOHASH_AND_ENTRY_INPUTS_YAML, 10206)
        self.assertIsNotNone(converted)
        assert converted is not None
        self.assertEqual(
            "https://magnetcat.example.org/search.html?q=[keyword]&cat=0&sort=2",
            converted["request"]["searchUrl"],
        )
        expr = converted["expressionModel"]
        self.assertEqual('css:a[href^="/hash/"]', expr["title"])
        self.assertEqual("", expr["url"])
        self.assertEqual('css:a[href^="/hash/"]@href', expr["magnet"])

    def test_convert_cardigann_yaml_json_response_type(self) -> None:
        converted = sync_rules.convert_cardigann_yaml(SAMPLE_JSON_CARDIGANN_YAML, 10207)
        self.assertIsNotNone(converted)
        assert converted is not None
        self.assertEqual("JSON_API", converted["sourceType"])
        self.assertEqual("https://apibay.org", converted["request"]["mainUrl"])
        self.assertEqual(
            "https://apibay.org/q.php?q=[keyword]&cat=0",
            converted["request"]["searchUrl"],
        )
        expr = converted["expressionModel"]
        self.assertEqual("", expr["group"])
        self.assertEqual("name", expr["title"])
        self.assertEqual("info_hash", expr["magnet"])

    def test_prowlarr_version_directory_fallback(self) -> None:
        calls: list[str] = []

        def fake_http_get(url: str, headers=None, timeout: int = 12):
            calls.append(url)
            if "/v12/" in url:
                return 404, {}, "Not Found"
            if "/v11/" in url:
                return 200, {}, SAMPLE_PUBLIC_CARDIGANN_YAML
            return 404, {}, "Not Found"

        with mock.patch.object(sync_rules, "http_get_text", side_effect=fake_http_get):
            rules, logs = sync_rules.sync_prowlarr_public_rules(
                start_id=10201,
                indexers=["samplepublic"],
                versions=("v12", "v11", "v10"),
            )

        self.assertEqual(1, len(rules))
        self.assertEqual(10201, rules[0]["id"])
        self.assertEqual(2, len(calls))
        self.assertTrue(any("samplepublic@v11" in line for line in logs))

    def test_replace_rule_origin_updates_urls_and_headers(self) -> None:
        rule = {
            "id": 10001,
            "name": "SOBT",
            "request": {
                "method": "GET",
                "mainUrl": "https://sobt22.top",
                "searchUrl": "https://sobt22.top/[keyword].html?sort=rel&page=[page]",
                "header": {
                    "Host": "sobt22.top",
                    "Referer": "https://sobt22.top/",
                    "Origin": "https://sobt22.top",
                },
            },
        }
        updated = sync_rules.replace_rule_origin(rule, "https://sobt99.top")
        self.assertEqual("https://sobt99.top", updated["request"]["mainUrl"])
        self.assertEqual(
            "https://sobt99.top/[keyword].html?sort=rel&page=[page]",
            updated["request"]["searchUrl"],
        )
        self.assertEqual("sobt99.top", updated["request"]["header"]["Host"])
        self.assertEqual("https://sobt99.top/", updated["request"]["header"]["Referer"])
        self.assertEqual("https://sobt99.top", updated["request"]["header"]["Origin"])

    def test_apply_domain_sources_update_and_graceful_degradation(self) -> None:
        base_rules = [
            {
                "id": 10001,
                "name": "SOBT",
                "request": {
                    "mainUrl": "https://sobt22.top",
                    "searchUrl": "https://sobt22.top/[keyword].html",
                    "header": {"Referer": "https://sobt22.top/"},
                },
            },
            {
                "id": 10019,
                "name": "磁力爬",
                "request": {
                    "mainUrl": "https://clp138.shop",
                    "searchUrl": "https://clp138.shop/Search/[keyword]",
                    "header": {"Referer": "https://clp138.shop/"},
                },
            },
        ]
        cfg = {
            "entries": [
                {
                    "id": 10001,
                    "name": "SOBT",
                    "publishUrl": "https://dead-publish.example.org/",
                    "publishUrls": [
                        "https://dead-publish.example.org/",
                        "https://mirror-publish.example.org/",
                    ],
                    "domainRegex": r"https?://sobt[0-9]+\.top",
                },
                {
                    "id": 10019,
                    "name": "磁力爬",
                    "publishUrl": "https://unreachable.example.org/",
                    "domainRegex": r"https?://clp[0-9]+\.shop",
                },
            ]
        }

        def fake_http_get(url: str, headers=None, timeout: int = 12):
            if "dead-publish" in url:
                return 502, {}, "Bad Gateway"
            if "mirror-publish" in url:
                return 200, {}, "最新地址: <a href='https://sobt88.top'>进入</a>"
            return 404, {}, "Not Found"

        with tempfile.TemporaryDirectory() as tmp_dir:
            cfg_path = Path(tmp_dir) / "domain_sources.json"
            cfg_path.write_text(json.dumps(cfg, ensure_ascii=False), encoding="utf-8")
            with mock.patch.object(sync_rules, "http_get_text", side_effect=fake_http_get):
                updated_rules, logs = sync_rules.apply_domain_sources(base_rules, cfg_path)

        self.assertEqual("https://sobt88.top", updated_rules[0]["request"]["mainUrl"])
        self.assertEqual(
            "https://sobt88.top/[keyword].html",
            updated_rules[0]["request"]["searchUrl"],
        )
        # Unreachable publish page preserves original rule without mutating it
        self.assertEqual("https://clp138.shop", updated_rules[1]["request"]["mainUrl"])
        self.assertTrue(any("域名已更新 [SOBT]" in line for line in logs))
        self.assertTrue(any("域名发现失败 [磁力爬]" in line for line in logs))

    def test_repo_domain_sources_json_has_valid_entries(self) -> None:
        cfg_path = Path(__file__).resolve().parent / "domain_sources.json"
        self.assertTrue(cfg_path.exists())
        data = json.loads(cfg_path.read_text(encoding="utf-8"))
        entries = data.get("entries", [])
        self.assertIsInstance(entries, list)
        self.assertGreaterEqual(len(entries), 5)
        for entry in entries:
            self.assertIn("id", entry)
            self.assertTrue(entry.get("publishUrl"))
            self.assertTrue(entry.get("domainRegex"))

    def test_is_valid_json_api_response_filters_sentinel(self) -> None:
        sentinel_body = '[{"id":"0","name":"No results returned","info_hash":"0000000000000000000000000000000000000000"}]'
        ok, count, _ = sync_rules.is_valid_json_api_response(sentinel_body, "")
        self.assertFalse(ok)
        self.assertEqual(0, count)

        nested_body = '{"data":{"movies":[{"title":"Ubuntu ISO","info_hash":"1111111111111111111111111111111111111111"}]}}'
        ok2, count2, _ = sync_rules.is_valid_json_api_response(nested_body, "data.movies")
        self.assertTrue(ok2)
        self.assertEqual(1, count2)

    def test_is_valid_rss_response_rss_and_atom(self) -> None:
        rss_xml = "<rss version='2.0'><channel><item><title>Ubuntu</title></item></channel></rss>"
        ok, count, _ = sync_rules.is_valid_rss_response(rss_xml)
        self.assertTrue(ok)
        self.assertEqual(1, count)

        atom_xml = "<feed xmlns='http://www.w3.org/2005/Atom'><entry><title>Debian</title></entry></feed>"
        ok2, count2, _ = sync_rules.is_valid_rss_response(atom_xml)
        self.assertTrue(ok2)
        self.assertEqual(1, count2)


if __name__ == "__main__":
    unittest.main()
