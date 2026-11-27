# -*- coding: utf-8 -*-
"""순서 11b-1: 채점 v1의 글 검사(prep/numerals.py, 순서 11b 설계 4판 4.2).

- 같은 예문 묶음(data/numerals_v1.json): 기대값은 손으로 정했다. 11b-2의 자바 검사기 v1도 이 묶음으로 시험한다.
- v0 셈은 자바 Numerals v0과 같은 식이어야 한다(11a의 수와 잇는다). 자바 소스가 있으면 네 낱말 목록을 글자까지 댄다.
"""
import json
import os
import re
import unittest
from fractions import Fraction

from prep import numerals as N
from prep import score

HERE = os.path.dirname(os.path.abspath(__file__))
CASES = os.path.join(HERE, "data", "numerals_v1.json")
JAVA = os.path.join(score.CODE_ROOT, os.pardir, "zipinfo-project", "ai-core", "src", "main", "java", "com", "zipinfo", "ai", "report",
                    "Numerals.java")
WANT_KEYS = ["after", "form", "numerals", "periods", "truncated", "v0"]


def load():
    with open(CASES, encoding="utf-8") as f:
        return json.load(f)


class TestExampleSet(unittest.TestCase):
    def test_every_case_gives_the_hand_set_answer(self):
        doc = load()
        self.assertEqual(doc["version"], "v1")
        names = [c["name"] for c in doc["cases"]]
        self.assertEqual(len(names), len(set(names)))
        for c in doc["cases"]:
            with self.subTest(name=c["name"]):
                self.assertEqual(sorted(c["want"]), WANT_KEYS)
                got = N.scan(c["text"], {"stat_claims": c["stat_claims"], "value_claims": c["value_claims"]})
                got.pop("v0_record", None)
                self.assertEqual(got, c["want"])

    def test_the_set_covers_each_kind(self):
        ws = [c["want"] for c in load()["cases"]]
        self.assertTrue(any(w["form"] and w["truncated"] for w in ws) and any(w["form"] and not w["truncated"] for w in ws))
        self.assertTrue(any(p[1] for w in ws for p in w["periods"]) and any(not p[1] and p[0] is not None for w in ws for p in w["periods"]))
        self.assertTrue(any(p[0] is None for w in ws for p in w["periods"]))
        self.assertEqual({k for w in ws for k in w["after"]}, set(N.AFTER_KINDS))
        self.assertTrue(any(w["v0"] and not w["numerals"] and not w["periods"] for w in ws), "v0만 세는 것(자리표시자 이름)")


class TestPieces(unittest.TestCase):
    def test_values(self):
        for token, v in (("한", 1), ("석", 3), ("넉", 4), ("열", 10), ("열두", 12), ("스무", 20), ("스물두", 22), ("아흔아홉", 99), ("일", 1),
                         ("십", 10), ("십이", 12), ("이십", 20), ("삼백오십", 350), ("천", 1000), ("백만", 1_000_000), ("이억삼천만", 230_000_000)):
            with self.subTest(token=token):
                self.assertEqual(N.value_of(token), Fraction(v))
        self.assertEqual(N.value_of("반"), Fraction(1, 2))
        for token in N.APPROX:
            self.assertIsNone(N.value_of(token))
        self.assertIsNone(N.value_of("열두세"))

    def test_months_count_both_ends(self):
        self.assertEqual(N.months_of("202511", "202610"), 12)
        self.assertEqual(N.months_of("202610", "202610"), 1)
        self.assertEqual(N.months_of("202312", "202401"), 2)
        for a, b in (("202610", "202511"), ("202513", "202610"), ("20251", "202610"), (202511, "202610"), (None, None), ("２０２５１１", "202610")):
            self.assertIsNone(N.months_of(a, b), (a, b))

    def test_units_are_tried_longest_first(self):
        # '세 개월'이 '세 개'(수사)로 잘리지 않는다
        self.assertEqual(N.scan("최근 세 개월", {})["periods"], [[3, False]])
        self.assertEqual(N.scan("세 개", {})["numerals"], 1)

    def test_odd_inputs(self):
        self.assertEqual(N.scan(None, None), {"form": False, "truncated": False, "periods": [], "numerals": 0, "v0": 0, "after": {}})
        self.assertEqual(N.scan("최근 일 년", {"stat_claims": "x", "value_claims": [None, {"slot": 3}]})["periods"], [[12, False]])


class TestJavaV0(unittest.TestCase):
    def test_the_v0_lists_are_the_java_ones(self):
        if not os.path.exists(JAVA):
            self.skipTest("자바 소스가 없다")
        with open(JAVA, encoding="utf-8") as f:
            src = f.read()
        got = {k: v for k, v in re.findall(r'static final String (\w+) = "([^"]*)";', src)}
        self.assertEqual((got["NATIVE"], got["SINO_COMPOUND"], got["SINO_SINGLE"], got["UNIT"]),
                         (N.V0_NATIVE, N.V0_SINO_COMPOUND, N.V0_SINO_SINGLE, N.V0_UNIT))
        self.assertIn('"(?<![가-힣])(?:(?:" + NATIVE + "|" + SINO_COMPOUND + ")\\\\s?|(?:" + SINO_SINGLE + ")\\\\s)(?:" + UNIT + ")"', src)

    def test_the_placeholder_is_the_scorer_one(self):
        self.assertEqual(N.PLACEHOLDER.pattern, score.PLACEHOLDER.pattern)


if __name__ == "__main__":
    unittest.main()
