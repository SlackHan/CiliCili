import sys
import unittest
from pathlib import Path

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


class SyncRulesTest(unittest.TestCase):
    @unittest.skipUnless(sync_rules.yaml is not None, "PyYAML not installed")
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
