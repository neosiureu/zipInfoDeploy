# -*- coding: utf-8 -*-
"""순서 11b-2 채점 v2(설계 b판 5.4)와 검사기 v1 기록 읽기.

- 실제 꼴의 기록: data/score_fx_v1은 하네스(Java)가 검사기 v1·그리기 v1로 지은 단계 폴더다(ScoreFixtureTest, 설정 version v1,
  제출 사건의 rendered). 매물·거래·문항은 하네스 시험의 지어낸 자료다. c06-q002-r1의 받은 답은 반말 끝이라 REGISTER가 기록돼 있다.
- 옛 꼴(data/score_fx, 검사기 v0)은 test_score.py가 채점 v1로 본다. 여기서는 그 기록에 채점 v2를 걸면 멈추는 것만 본다.
- 규칙 v2 = 규칙 v1 + 5.3: 그린 글의 여섯 코드는 rendered를 textcheck로 다시 센 것, DIFF_HIDDEN은 기록의 것. 규칙 v1은 기록의 5.3 코드를 보지 않는다.
"""
import copy
import json
import os
import shutil
import tempfile
import unittest
from decimal import Decimal

from prep import score
from prep.tests import test_score as base

HERE = os.path.dirname(os.path.abspath(__file__))
FX1 = os.path.join(HERE, "data", "score_fx_v1")
LISTING = {"stockName": "", "stockAddress": "18000^^^경기도 화성시 만세구 가로 1^^^"}


def v2_config(**kw):
    """시험용 채점 v2 설정: score_v2.json에서 뽑기 수를 줄이고 비교를 시험 자료의 판으로, 같은 실수 표와 판정 표본 빼기를 비운다."""
    with open(score.CONFIG_V2, encoding="utf-8") as f:
        cfg = json.load(f)
    cfg["bootstrap"]["draws"] = base.DRAWS
    cfg["comparisons"] = {"main": ["c06", "c01"], "aux": [["c04", "c01"], ["c05", "c01"], ["c07", "c01"]]}
    cfg["judge_sample_exclude"] = {}
    cfg["mistakes"] = None
    cfg.update(kw)
    return cfg


class Exp1(base.Exp):
    """검사기 v1 기록(score_fx_v1)을 experiment 폴더처럼 꾸린 임시 폴더. 매물 입력 파일(로마자 낱말)도 쓴다."""

    def __init__(self):
        self.root = tempfile.mkdtemp()
        for d in ("harness", "questions", "variants"):
            shutil.copytree(os.path.join(FX1, d), os.path.join(self.root, d))
        os.makedirs(self.p("cards/v0"))
        shutil.copy(base.K10, self.p("cards/v0/K10.md"))
        os.makedirs(self.p("frozen"))
        with open(self.p("frozen/example_listings_v1.csv"), "w", encoding="utf-8", newline="\n") as f:
            f.write("complex_id,deal,layer,split\n" + "".join(f"apt|41591|가동|0|12|0,매매,화성형,{sp}\n" for sp in ("연습", "연습", "본시험")))
        os.makedirs(self.p("prep/out"))
        self.write_answers(base.fx_answers(score.sha256_lf(self.p("questions/questions_fx.json"))))
        self.write_listings({k: dict(LISTING) for k in ("L01", "L02", "L03")})
        self.settings = self.p("harness/settings/fx_v1.json")
        self.config = self.write_v2("prep/score_test_v2.json")
        self.config_v1 = self.write_config("prep/score_test.json")

    def write_v2(self, rel, **kw):
        path = self.p(rel)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(v2_config(**kw), f, ensure_ascii=False)
        return path

    def write_listings(self, fields):
        items = [{"key": k, "fields": f, "evidence": {}} for k, f in fields.items()]
        with open(self.p("prep/out/listings_input_v1.json"), "w", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps(items, ensure_ascii=False))

    def edit_record(self, rid, fn):
        """기록 한 파일의 사건을 고친다(fn이 사건 목록을 바꾼다). 판정 줄은 고친 기록의 해시로 다시 묶는다(판정은 그대로)."""
        lines = self.lines(rid + ".jsonl")
        fn(lines)
        self.write_lines(rid + ".jsonl", lines)
        sha = score.sha256(score.lf(self.read(rid + ".jsonl")))
        v = self.lines("judge/verdicts.jsonl")
        for x in v:
            if x["run_id"] == rid:
                x["record_sha256"] = sha
        self.write_lines("judge/verdicts.jsonl", v)


def by_id(rows):
    return {r["run_id"]: r for r in rows}


class TestFixtureV1(unittest.TestCase):
    def setUp(self):
        self.x = Exp1()

    def tearDown(self):
        self.x.close()

    def test_v1_records_are_scored_with_v2(self):
        code, out = self.x.run()
        self.assertEqual(0, code, out)
        self.assertIn("완전 정답 v2(판정 넣음)", out[0])
        line = next(l for l in out if l.startswith("글 검사 v1"))
        self.assertIn("기록 대조(검사기 v1)가 다른 제출 0(NUMERAL 0·PERIOD_WORD 0·PLACEHOLDER_FORM 0·TEXT 0)", line)
        self.assertIn("REGISTER 1", line)
        rows = by_id(self.x.results())
        self.assertIn("CHECKER:REGISTER", rows["c06-q002-r1"]["codes"])
        self.assertEqual(["REGISTER"], rows["c06-q002-r1"]["text_check"]["text_codes"])
        self.assertEqual([r for r in rows if "CHECKER:REGISTER" in rows[r]["codes"]], ["c06-q002-r1"])
        self.assertTrue(rows["c01-q001-r1"]["full"])
        self.assertEqual([], rows["c01-q001-r1"]["text_check"]["record_diff"])
        with open(self.x.stage("score/metrics.tsv"), encoding="utf-8") as f:
            m = f.read()
        self.assertIn("c06\t진단\t그린 글 REGISTER 제출\t1\t2", m)
        self.assertIn("c06\t진단\t기록 대조(검사기 v1)가 다른 제출\t0\t2", m)
        self.assertNotIn("글 수사 v0", m)

    def test_rule_v1_does_not_see_the_new_codes(self):
        code, out = self.x.run(config=self.x.config_v1)
        self.assertEqual(0, code, out)
        self.assertIn("완전 정답 v1(판정 넣음)", out[0])
        rows = by_id(self.x.results())
        self.assertNotIn("CHECKER:REGISTER", rows["c06-q002-r1"]["codes"])
        self.assertEqual(["REGISTER"], rows["c06-q002-r1"]["text_check"]["text_codes"], "대조와 그린 글 코드는 규칙 판과 상관없이 낸다")

    def test_a_new_code_alone_fails_rule_v2_only(self):
        """c01-q001-r1(완전 정답)의 그린 글을 반말로 바꾸면(받아들여진 답도 같게) 규칙 v2만 떨어진다. 기록 findings의 REGISTER는 대조가 잡는다."""
        def fn(lines):
            for e in lines:
                if e.get("type") == "submit":
                    e["rendered"] = e["rendered"][:-len("비쌉니다.")] + "비싸다."
                elif e.get("type") in ("accepted", "end"):
                    e["answer"] = e["answer"][:-len("비쌉니다.")] + "비싸다."
        self.x.edit_record("c01-q001-r1", fn)
        code, out = self.x.run(pin=None)
        self.assertEqual(0, code, out)
        r = by_id(self.x.results())["c01-q001-r1"]
        self.assertEqual((r["value"], r["rule"], r["full"]), (True, False, False))
        self.assertEqual(["CHECKER:REGISTER"], r["codes"])
        self.assertEqual(["TEXT"], r["text_check"]["record_diff"], "기록 findings에는 REGISTER가 없다")
        self.assertEqual(0, self.x.run(config=self.x.config_v1)[0])
        r = by_id(self.x.results())["c01-q001-r1"]
        self.assertEqual((r["rule"], r["full"]), (True, True))

    def test_recorded_text_codes_do_not_score(self):
        """그린 글 코드는 다시 센 것으로 매긴다. 기록 findings에만 있는 GARBLED는 규칙을 바꾸지 않고 대조에 잡힌다."""
        def fn(lines):
            next(e for e in lines if e.get("type") == "submit")["findings"].append("answer GARBLED")
        self.x.edit_record("c01-q001-r1", fn)
        code, out = self.x.run()
        self.assertEqual(0, code, out)
        r = by_id(self.x.results())["c01-q001-r1"]
        self.assertTrue(r["full"])
        self.assertEqual(["TEXT"], r["text_check"]["record_diff"])
        self.assertIn("기록 대조(검사기 v1)가 다른 제출 1(NUMERAL 0·PERIOD_WORD 0·PLACEHOLDER_FORM 0·TEXT 1)",
                      next(l for l in out if l.startswith("글 검사 v1")))

    def test_recorded_diff_hidden_fails_rule_v2(self):
        def fn(lines):
            next(e for e in lines if e.get("type") == "submit")["findings"].append("text DIFF_HIDDEN")
        self.x.edit_record("c01-q001-r1", fn)
        self.assertEqual(0, self.x.run()[0])
        r = by_id(self.x.results())["c01-q001-r1"]
        self.assertEqual((r["rule"], r["codes"]), (False, ["CHECKER:DIFF_HIDDEN"]))
        self.assertEqual(0, self.x.run(config=self.x.config_v1)[0])
        self.assertTrue(by_id(self.x.results())["c01-q001-r1"]["full"])

    def test_rendered_must_be_there_and_match_the_answer(self):
        def other(lines):
            next(e for e in lines if e.get("type") == "submit")["rendered"] = "다른 글입니다."
        self.x.edit_record("c01-q001-r1", other)
        code, out = self.x.run()
        self.assertEqual(3, code)
        self.assertIn("받아들여진 답(accepted)이 그 제출 사건의 그린 글(rendered)과 다르다", out[-1])

    def test_rendered_missing_stops(self):
        def drop(lines):
            for e in lines:
                if e.get("type") == "submit":
                    del e["rendered"]
        self.x.edit_record("c07-q001-r1", drop)
        code, out = self.x.run()
        self.assertEqual(3, code)
        self.assertIn("그린 글(rendered)", out[-1])

    def test_listing_input_is_needed_for_v1_records(self):
        os.remove(self.x.p("prep/out/listings_input_v1.json"))
        code, out = self.x.run()
        self.assertEqual(2, code)
        self.assertIn("매물 입력 파일", out[-1])
        self.x.write_listings({"L01": dict(LISTING)})
        code, out = self.x.run()
        self.assertEqual(2, code)
        self.assertIn("매물 L02이 없다", out[-1])

    def test_latin_words_of_the_listing_and_question_are_allowed(self):
        """매물 이름의 로마자는 받고, 없는 로마자는 GARBLED다(그린 글은 c01-q001-r1의 것을 바꾼다)."""
        def fn(lines):
            for e in lines:
                if e.get("type") == "submit":
                    e["rendered"] = "BX " + e["rendered"]
                elif e.get("type") in ("accepted", "end"):
                    e["answer"] = "BX " + e["answer"]
        self.x.edit_record("c01-q001-r1", fn)
        self.assertEqual(0, self.x.run()[0])
        self.assertIn("CHECKER:GARBLED", by_id(self.x.results())["c01-q001-r1"]["codes"])
        self.x.write_listings({"L01": dict(LISTING, stockName="BX빌"), "L02": dict(LISTING), "L03": dict(LISTING)})
        self.assertEqual(0, self.x.run()[0])
        self.assertNotIn("CHECKER:GARBLED", by_id(self.x.results())["c01-q001-r1"]["codes"])

    def test_the_fingerprint_counts_the_listing_input(self):
        self.assertEqual(0, self.x.run()[0])
        a = self.x.results()[0]
        p1 = self.x.read("score/summary.tsv")
        self.x.write_listings({k: dict(LISTING, stockName="다른 이름") for k in ("L01", "L02", "L03")})
        self.assertEqual(0, self.x.run()[0])
        self.assertNotEqual(p1, self.x.read("score/summary.tsv"))
        self.assertEqual(a["codes"], self.x.results()[0]["codes"])


class TestVersions(unittest.TestCase):
    def test_checker_and_default_config(self):
        self.assertEqual(("v0", "v1", "v1"), (score.checker_of("v0"), score.checker_of("v1"), score.checker_of("v2")))
        self.assertEqual(score.CONFIG, score.default_config("v0"))
        self.assertEqual(score.CONFIG_V2, score.default_config("v1"))
        cfg = score.load_config(score.CONFIG_V2)
        self.assertEqual(("v2", "harness/frozen/mistakes_v0.json"), (cfg["version"], cfg["mistakes"]))
        v1 = score.load_config(score.CONFIG)
        self.assertEqual({k: v for k, v in v1.items() if k not in ("version", "mistakes")},
                         {k: v for k, v in cfg.items() if k not in ("version", "mistakes")}, "v2 설정은 version과 같은 실수 표만 다르다")

    def test_v2_on_checker_v0_records_stops(self):
        x = base.Exp()
        try:
            path = x.p("prep/score_test_v2.json")
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                json.dump(v2_config(), f, ensure_ascii=False)
            code, out = x.run(config=path)
            self.assertEqual(2, code)
            self.assertIn("채점 v2는 검사기 v1 기록", out[-1])
        finally:
            x.close()

    def test_unknown_rule_version_stops(self):
        x = Exp1()
        try:
            code, out = x.run(config=x.write_v2("prep/bad.json", version="v3"))
            self.assertEqual(2, code)
            self.assertIn("채점 설정 version은 v1이나 v2다", out[-1])
        finally:
            x.close()


# ---------------------------------------------------------------- 실행 하나(검사기 v1 기록 흉내)

CFG1 = dict(score.load_config(score.CONFIG), checker="v1", placeholder=6, latin={})
CFG2 = dict(score.load_config(score.CONFIG_V2), checker="v1", placeholder=6, latin={})
GOOD = "이 매물은 같은 법정동 실거래 기준값 3억원보다 12.5% 비쌉니다."


def v1_record(text=base.TEXT, rendered=GOOD, findings=(), slot="가"):
    ev = base.good_t1()
    sub = next(e for e in ev if e["type"] == "submit")
    sub["input"]["text"] = text
    sub["input"]["value_claims"][0]["slot"] = slot
    sub["rendered"] = rendered
    sub["findings"] = list(findings)
    next(e for e in ev if e["type"] == "accepted")["answer"] = rendered
    return ev


def run1(ev, cfg):
    return score.score_run(ev, base.answer(), "SUBMITTED", True, base.FORBIDDEN, cfg)


class TestPieces(unittest.TestCase):
    def test_good_v1_record(self):
        for cfg in (CFG1, CFG2):
            r = run1(v1_record(), cfg)
            self.assertEqual((r["full"], r["codes"], r["text_check"]["record_diff"]), (True, [], []))

    def test_six_letter_placeholders_are_valid_only_for_checker_v1(self):
        ev = v1_record(text="같은 법정동 실거래 기준값보다 {차이값} {차이값.방향}.", slot="차이값")
        self.assertTrue(run1(ev, CFG2)["full"])
        ev = v1_record(text="같은 법정동 실거래 기준값보다 {매물차이값} {매물차이값.방향}.", slot="매물차이값")
        r = run1(ev, CFG2)
        self.assertTrue(r["full"])
        self.assertFalse(r["text_check"]["form"])
        r = run1(ev, base.CFG)
        self.assertIn("CHECKER:PLACEHOLDER_FORM", r["codes"], "검사기 v0 기록은 1~3자다")

    def test_the_name_slot_is_not_a_broken_form(self):
        ev = v1_record(text="{매물명}은 같은 법정동 실거래 기준값보다 {가} {가.방향}.")
        r = run1(ev, CFG2)
        self.assertEqual((r["full"], r["text_check"]["form"]), (True, False))

    def test_record_diff_kinds(self):
        r = run1(v1_record(text=base.TEXT + " 두 배입니다.", findings=["text NUMERAL"]), CFG2)
        self.assertEqual([], r["text_check"]["record_diff"])
        self.assertIn("CHECKER:NUMERAL", r["codes"])
        r = run1(v1_record(text=base.TEXT + " 두 배입니다."), CFG2)
        self.assertEqual(["NUMERAL"], r["text_check"]["record_diff"])
        r = run1(v1_record(text=base.TEXT + " 최근 두 달 거래입니다.", findings=["text PERIOD_WORD"]), CFG2)
        self.assertEqual([], r["text_check"]["record_diff"])
        r = run1(v1_record(text=base.TEXT + " {가", findings=["text PLACEHOLDER_FORM"]), CFG2)
        self.assertEqual([], r["text_check"]["record_diff"])
        r = run1(v1_record(rendered=GOOD + " {가", findings=["answer BRACE", "answer REGISTER"]), CFG2)
        self.assertEqual([], r["text_check"]["record_diff"])
        self.assertEqual(["CHECKER:BRACE", "CHECKER:REGISTER"], r["codes"])
        r = run1(v1_record(rendered=GOOD + " {가", findings=["answer REGISTER", "answer BRACE"]), CFG2)
        self.assertEqual(["TEXT"], r["text_check"]["record_diff"], "자바는 코드를 정한 차례로 낸다")

    def test_the_three_definitions_all_get_the_new_codes(self):
        r = run1(v1_record(rendered="이 매물은 비싸다."), CFG2)
        self.assertEqual((r["rule"], r["full"], r["full_all_numerals"], r["full_no_numeral"]), (False, False, False, False))
        r = run1(v1_record(rendered="이 매물은 비싸다."), CFG1)
        self.assertEqual((r["rule"], r["full"], r["full_all_numerals"], r["full_no_numeral"]), (True, True, True, True))

    def test_sound_submission_uses_rule_v2(self):
        ev = v1_record(rendered="이 매물은 비싸다.")
        sub = next(e for e in ev if e["type"] == "submit")
        self.assertTrue(score.sound_submission(sub, ev, base.answer(), base.FORBIDDEN, CFG1))
        self.assertFalse(score.sound_submission(sub, ev, base.answer(), base.FORBIDDEN, CFG2))


if __name__ == "__main__":
    unittest.main()
