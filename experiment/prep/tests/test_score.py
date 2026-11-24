# -*- coding: utf-8 -*-
"""순서 8a: 채점기(prep score, 순서 8 설계 d판 7절). 8b(설계 2판 2절 가·나, 시험 자료 넓힘)도 여기서 본다. 8b 지표는 test_metrics.py.

- 실제 꼴의 기록: data/score_fx는 하네스(Java)가 가짜 모델·배치 서버로 지은 단계 폴더다(ScoreFixtureTest). 매물·거래는 하네스 시험의 지어낸 자료다.
  여기에 정답 파일·매물 파일·K10을 더해 experiment 폴더처럼 꾸리고 끝까지 매긴다. 묶기 검사는 그 폴더를 한 곳씩 바꿔 본다.
- 실행 하나 매기기는 같은 꼴의 사건을 지어 본다(답을 아는 가짜 기록). 한 곳씩 바꿔 실패 코드가 하나씩 나는지 본다.
- 실제 파일(정답·문항·틀 문장)이 있을 때만 항목 수를 찍는다.
"""
import copy
import io
import json
import os
import shutil
import tempfile
import unicodedata
import unittest
from collections import Counter
from decimal import Decimal

from prep import score

HERE = os.path.dirname(os.path.abspath(__file__))
FX = os.path.join(HERE, "data", "score_fx")
STAGE = os.path.join("harness", "runs", "pilot")
K10 = os.path.join(score.CODE_ROOT, "cards", "v0", "K10.md")
RUNS = ["c01-q001-r1", "c01-q002-r1", "c04-q001-r1", "c04-q002-r1", "c05-q001-r1", "c05-q002-r1", "c06-q001-r1", "c06-q002-r1", "c07-q001-r1",
        "c07-q002-r1"]
OUTPUTS = ("runs.jsonl", "summary.tsv", "metrics.tsv")


def fx_answers(qsha):
    call = {"api": "apt_trade", "region_code": "41591", "start_month": "202610", "end_month": "202610", "deal": "매매", "dong": "", "jibun": ""}
    t1 = {"months": 1, "scope": "시군구", "call": call, "sample": 4, "listing_value": "350000000", "basis": "33741", "diff": "3.7"}
    t2 = {"months": 1, "scope": "단지", "call": dict(call, dong="가동", jibun="12"), "sample": 2, "listing_value": "350000000", "basis": "31770",
          "diff": "10.2"}
    rows = [dict(id="q001", key="L01", split="연습", qtype="T1", **t1), dict(id="q002", key="L02", split="연습", qtype="T2", **t2),
            dict(id="q900", key="L03", split="본시험", qtype="T1", **t1)]
    return {"version": "fx", "questions_sha256": qsha, "answers": rows}


class Exp:
    """시험 자료를 experiment 폴더처럼 꾸린 임시 폴더."""

    def __init__(self):
        self.root = tempfile.mkdtemp()
        for d in ("harness", "questions", "variants"):
            shutil.copytree(os.path.join(FX, d), os.path.join(self.root, d))
        os.makedirs(self.p("cards/v0"))
        shutil.copy(K10, self.p("cards/v0/K10.md"))
        os.makedirs(self.p("frozen"))
        with open(self.p("frozen/example_listings_v1.csv"), "w", encoding="utf-8", newline="\n") as f:
            f.write("complex_id,deal,layer,split\n" + "".join(f"apt|41591|가동|0|12|0,매매,화성형,{sp}\n" for sp in ("연습", "연습", "본시험")))
        os.makedirs(self.p("prep/out"))
        self.write_answers(fx_answers(score.sha256_lf(self.p("questions/questions_fx.json"))))
        self.settings = self.p("harness/settings/fx_v0.json")
        self.config = score.CONFIG

    def p(self, rel):
        return os.path.join(self.root, *rel.split("/"))

    def stage(self, name=""):
        return os.path.join(self.root, STAGE, name) if name else os.path.join(self.root, STAGE)

    def write_answers(self, doc):
        with open(self.p("prep/out/answers_fx.json"), "w", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps(doc, ensure_ascii=False))

    def lines(self, name):
        with open(self.stage(name), "r", encoding="utf-8", newline="") as f:
            return [json.loads(x) for x in f.read().split("\n") if x.strip()]

    def write_lines(self, name, lines):
        with open(self.stage(name), "w", encoding="utf-8", newline="\n") as f:
            f.write("".join(json.dumps(x, ensure_ascii=False, separators=(",", ":")) + "\n" for x in lines))

    def edit_text(self, rel, old, new):
        """글 파일 한 곳을 바꾼다. git이 CRLF로 꺼낸 시험 자료도 있어 줄 끝을 LF로 맞춰 읽고 쓴다(채점기는 둘 다 읽는다)."""
        path = os.path.join(self.root, *rel.split("/"))
        with open(path, "rb") as f:
            t = score.lf(f.read()).decode("utf-8")
        assert old in t, old
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(t.replace(old, new, 1))

    def run(self, **kw):
        out = []
        kw.setdefault("config", self.config)
        code = score.run(self.settings, exp=self.root, pin=kw.pop("pin", None), out=out.append, **kw)
        return code, out

    def results(self):
        with open(self.stage("score/runs.jsonl"), "r", encoding="utf-8") as f:
            return [json.loads(x) for x in f]

    def read(self, name):
        with open(self.stage(name), "rb") as f:
            return f.read()

    def close(self):
        shutil.rmtree(self.root)


class TestFixture(unittest.TestCase):
    """하네스가 지은 기록을 끝까지 매긴다."""

    def setUp(self):
        self.x = Exp()

    def tearDown(self):
        self.x.close()

    def test_scores_the_harness_records(self):
        code, out = self.x.run()
        self.assertEqual(code, 0, out)
        self.assertTrue(out[0].startswith("채점 검사: 문항 파일 "), out[0])
        self.assertIn("기록 10개가 이 문항 파일과 plan.txt 지문으로 돎(단계·설정 판 같음), 판정 7개가 기록 해시에 묶임(판정 지문 CEA01B2E0224, 옛 줄 0, 다른 줄 0)",
                      out[0])
        self.assertEqual(out[1:6], ["c01 B0: 계획 2, 제출 1, 값 1, 규칙 1, 판정 답함 1, 완전 정답 1, 수사 뺀 완전 정답 1",
                                    "c04 ①+⓪: 계획 2, 제출 1, 값 1, 규칙 1, 판정 답함 1, 완전 정답 1, 수사 뺀 완전 정답 1",
                                    "c05 ①+②: 계획 2, 제출 1, 값 1, 규칙 1, 판정 답함 1, 완전 정답 1, 수사 뺀 완전 정답 1",
                                    "c06 ①+③: 계획 2, 제출 2, 값 1, 규칙 1, 판정 답함 2, 완전 정답 1, 수사 뺀 완전 정답 1",
                                    "c07 ①+④: 계획 2, 제출 2, 값 2, 규칙 1, 판정 답함 1, 완전 정답 0, 수사 뺀 완전 정답 0"])
        rows = self.x.results()
        self.assertEqual([r["run_id"] for r in rows], RUNS)
        a, b, c, d = rows[0], rows[1], rows[8], rows[9]
        self.assertEqual((a["outcome"], a["value"], a["rule"], a["judge"], a["full"], a["codes"]), ("SUBMITTED", True, True, True, True, []))
        self.assertEqual([(i["item"], i["claim"]) for i in a["items"]], [("기준값", "stat_claims[0]"), ("차이", "value_claims[0]")])
        self.assertEqual((b["outcome"], b["full"], b["codes"], b["items"], b["judge"]), ("FAILED_REFUSAL", False, ["FAILED_REFUSAL"], [], None))
        # ④가 막은 첫 제출이 아니라 받아들여진 둘째 제출을 매긴다
        self.assertEqual((c["value"], c["rule"], c["judge"], c["full"], c["codes"]), (True, True, False, False, ["JUDGE_NOT_ANSWERED"]))
        # T2: 시군구 0건 claim(S2)이 단지가 아니라 넓힘. 그 0건은 정답과 범위·기간이 달라 꼬리표가 없다
        self.assertEqual((d["value"], d["rule"], d["codes"], d["tags"], d["full"]), (True, False, ["T2_WIDENED"], [], False))
        self.assertEqual((a["layer"], a["template"], a["rep"], a["key"], a["split"]), ("화성형", None, 1, "L01", "연습"))
        with open(self.x.stage("score/summary.tsv"), encoding="utf-8") as f:
            tsv = [line.rstrip("\n").split("\t") for line in f]
        self.assertEqual(tsv[0], list(score.SUMMARY_HEAD))
        self.assertEqual(tsv[1][:10], ["c01", "B0", "2", "1", "1", "1", "1", "0", "1", "1"])
        self.assertEqual(tsv[5][:10], ["c07", "①+④", "2", "2", "2", "1", "1", "0", "0", "0"])
        self.assertEqual(len(tsv[1][10]), 64)
        self.assertEqual(tsv[1][11], "CEA01B2E0224544C9BE97B0C0FF325A647C747B90FA81D2A485C443553550E9E")

    def test_no_text_goes_into_the_outputs(self):
        self.assertEqual(self.x.run()[0], 0)
        blob = b"".join(self.x.read("score/" + n) for n in OUTPUTS).decode("utf-8")
        for text in ("시험 글", "이 매물은", "묻는 값을", "가로 1", "기준값보다", "같은 단지 거래는", "없는 길", "모르겠다", "길다", "잠시 뒤",
                     "의도도", "지역 코드가", "41590", "먼저 카드"):
            self.assertNotIn(text, blob)

    def test_same_bytes_twice_and_from_crlf_records(self):
        self.assertEqual(self.x.run()[0], 0)
        first = tuple(self.x.read("score/" + n) for n in OUTPUTS)
        self.assertEqual(self.x.run()[0], 0)
        self.assertEqual(tuple(self.x.read("score/" + n) for n in OUTPUTS), first)
        # git이 CRLF로 꺼낸 기록·plan.txt·판정 파일·문항 파일(판정 해시와 문항 해시는 LF로 맞춰 센다)
        for d, _, files in os.walk(self.x.root):
            if "score" in d.split(os.sep):
                continue
            for f in files:
                if f.endswith((".jsonl", ".txt", ".json", ".md", ".csv", ".tsv")):
                    path = os.path.join(d, f)
                    with open(path, "rb") as h:
                        b = h.read()
                    with open(path, "wb") as h:
                        h.write(score.lf(b).replace(b"\n", b"\r\n"))
        code, out = self.x.run()
        self.assertEqual(code, 0, out)
        self.assertEqual(tuple(self.x.read("score/" + n) for n in OUTPUTS), first)

    def test_rewrite_replaces_the_old_folder(self):
        os.makedirs(self.x.stage("score"))
        with open(self.x.stage("score/old.txt"), "w") as f:
            f.write("old")
        os.makedirs(self.x.stage("score.tmp"))
        os.makedirs(self.x.stage("score.old"))
        self.assertEqual(self.x.run()[0], 0)
        self.assertEqual(sorted(os.listdir(self.x.stage("score"))), sorted(OUTPUTS))
        self.assertFalse(os.path.exists(self.x.stage("score.tmp")) or os.path.exists(self.x.stage("score.old")))

    def swap(self, fail_rename=None, fail_rmtree=None):
        """score 모듈의 os.rename·shutil.rmtree를 바꿔 끼워 한 자리에서 PermissionError를 낸다(8b 2절 나)."""
        real_rename, real_rmtree = score.os.rename, score.shutil.rmtree
        def rename(a, b):
            if fail_rename and fail_rename(a, b):
                raise PermissionError("열려 있다")
            real_rename(a, b)
        def rmtree(d, *k, **kw):
            if fail_rmtree and fail_rmtree(d):
                raise PermissionError("열려 있다")
            real_rmtree(d, *k, **kw)
        score.os.rename, score.shutil.rmtree = rename, rmtree
        try:
            return self.x.run()
        finally:
            score.os.rename, score.shutil.rmtree = real_rename, real_rmtree

    def old_score(self):
        os.makedirs(self.x.stage("score"))
        with open(self.x.stage("score/old.txt"), "w") as f:
            f.write("old")

    def test_a_failed_swap_puts_the_old_folder_back_and_ends_with_2(self):
        self.old_score()
        code, out = self.swap(fail_rename=lambda a, b: a.endswith("score.tmp"))
        self.assertEqual(code, 2, out)
        self.assertIn("결과 폴더를 바꾸지 못했다", out[0])
        self.assertEqual(os.listdir(self.x.stage("score")), ["old.txt"])
        self.assertFalse(os.path.exists(self.x.stage("score.tmp")) or os.path.exists(self.x.stage("score.old")))
        code, out = self.swap(fail_rename=lambda a, b: b.endswith("score.old"))
        self.assertEqual(code, 2, out)
        self.assertEqual(os.listdir(self.x.stage("score")), ["old.txt"])
        self.assertFalse(os.path.exists(self.x.stage("score.tmp")))

    def test_only_the_last_cleanup_failing_ends_with_0_and_a_line(self):
        self.old_score()
        code, out = self.swap(fail_rmtree=lambda d: d.endswith("score.old") and os.path.exists(os.path.join(self.x.stage("score"), "runs.jsonl")))
        self.assertEqual(code, 0, out)
        self.assertIn("score.old를 지우지 못했다(다음에 칠 때 지운다)", out)
        self.assertEqual(sorted(os.listdir(self.x.stage("score"))), sorted(OUTPUTS))
        self.assertEqual(self.x.run()[0], 0)  # 다음에 칠 때 지운다
        self.assertFalse(os.path.exists(self.x.stage("score.old")))

    def test_an_old_folder_from_an_earlier_cut_is_not_brought_back(self):
        # 전에 끊긴 판: score를 old로 옮긴 뒤 끝나 score/가 없다. 이번 앞 정리에서 old 지우기가 실패해도 그 old를 score로 되살리지 않는다(상대 8b 1판 검토)
        os.makedirs(self.x.stage("score.old"))
        with open(self.x.stage("score.old/half.txt"), "w") as f:
            f.write("half")
        code, out = self.swap(fail_rmtree=lambda d: d.endswith("score.old"))
        self.assertEqual(code, 2, out)
        self.assertFalse(os.path.exists(self.x.stage("score")))
        self.assertTrue(os.path.exists(self.x.stage("score.old/half.txt")))

    def test_unjudged_runs_can_be_counted_when_the_config_says_so(self):
        v = [x for x in self.x.lines("judge/verdicts.jsonl") if x["run_id"] != "c01-q001-r1"]
        self.x.write_lines("judge/verdicts.jsonl", v)
        code, out = self.x.run()
        self.assertEqual(code, 3)
        self.assertIn("판정 없는 SUBMITTED 실행 1개(예: c01-q001-r1)", out[0])
        cfg = json.load(open(score.CONFIG, encoding="utf-8"))
        cfg["unjudged"] = "fail"
        path = self.x.p("prep/score_fail.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(cfg, f, ensure_ascii=False)
        code, out = self.x.run(config=path)
        self.assertEqual(code, 0, out)
        self.assertIn("판정 6개가 기록 해시에 묶임(판정 지문 CEA01B2E0224, 옛 줄 0, 다른 줄 0, 판정 없음 1)", out[0])
        a = self.x.results()[0]
        self.assertEqual((a["judge"], a["full"], a["codes"]), (None, False, ["JUDGE_MISSING"]))

    def test_the_cli_runs_the_scorer(self):
        from prep import cli
        old = os.getcwd()
        buf = io.StringIO()
        try:
            os.chdir(self.x.root)
            import contextlib
            with contextlib.redirect_stdout(buf):
                code = cli.main(["score", "harness/settings/fx_v0.json"])
        finally:
            os.chdir(old)
        self.assertEqual(code, 2)  # 매물 파일 SHA-256이 실제 것과 달라 멈춘다
        self.assertIn("매물 파일의 SHA-256이 다르다", buf.getvalue())


class TestBinding(unittest.TestCase):
    """묶기 검사(2.2): 하나라도 걸리면 아무것도 쓰지 않고 3(파일 읽기는 2)."""

    def setUp(self):
        self.x = Exp()

    def tearDown(self):
        self.x.close()

    def stops(self, words, code=3, **kw):
        got, out = self.x.run(**kw)
        self.assertEqual(got, code, out)
        self.assertIn(words, out[0])
        self.assertFalse(os.path.exists(self.x.stage("score")), "걸리면 아무것도 쓰지 않는다")

    def test_answers_must_match_the_question_file(self):
        self.x.edit_text("questions/questions_fx.json", "시험 글 가", "시험 글 라")
        self.stops("questions_sha256")
        self.x.write_answers(fx_answers(score.sha256_lf(self.x.p("questions/questions_fx.json"))))
        self.stops("머리의 문항이 문항 파일의 것과 다르다")

    def test_answers_must_be_one_to_one(self):
        doc = fx_answers(score.sha256_lf(self.x.p("questions/questions_fx.json")))
        doc["answers"][1]["qtype"] = "T1"
        self.x.write_answers(doc)
        self.stops("1:1로 같지 않다")

    def test_plan_txt_must_match_the_plan(self):
        self.x.edit_text("harness/settings/fx_v0.json", '"reps":[1]', '"reps":[1,2]')
        self.stops("판·문항·회차·블록·실행 수가 파이썬이 짠 계획과 다르다")

    def test_plan_txt_must_have_no_problems_and_nothing_left(self):
        with open(self.x.stage("plan.txt"), "a", encoding="utf-8") as f:
            f.write("기록 문제: c01-q001-r1-copy.jsonl: 파일 이름이 머리의 실행 ID(c01-q001-r1)와 다르다\n")
        self.stops("기록 문제 1줄")

    def test_plan_txt_counts(self):
        self.x.edit_text(f"{STAGE}/plan.txt".replace(os.sep, "/"), "끝난 것 10, 시작한 것 0, 남은 것 0", "끝난 것 9, 시작한 것 0, 남은 것 1")
        self.stops("plan.txt: 남은 것 1(시작한 것 0). 모두 끝나야 매긴다")

    def test_plan_txt_with_records_outside_the_plan_says_so(self):
        # 하네스 Runner.status는 계획 밖 기록도 끝난 것·시작한 것에 넣는다(8b 2절 가, 상대 8a 검토)
        rel = f"{STAGE}/plan.txt".replace(os.sep, "/")
        self.x.edit_text(rel, "끝난 것 10, 시작한 것 0, 남은 것 0", "끝난 것 11, 시작한 것 0, 남은 것 0")
        self.stops("plan.txt: 끝난 것 11·시작한 것 0가 계획(10)과 맞지 않다. 계획 밖 기록이 있다. 다른 폴더로 옮긴다")
        self.x.edit_text(rel, "끝난 것 11, 시작한 것 0, 남은 것 0", "끝난 것 10, 시작한 것 1, 남은 것 0")
        self.stops("계획 밖 기록이 있다")

    def test_plan_txt_must_be_readable(self):
        self.x.edit_text(f"{STAGE}/plan.txt".replace(os.sep, "/"), "요청 지문:\n", "요청 지문:\n이상한 줄\n")
        self.stops("plan.txt를 읽지 못했다")
        os.remove(self.x.stage("plan.txt"))
        self.stops("plan.txt가 없다")

    def test_the_run_print_comes_from_plan_txt(self):
        with open(self.x.stage("plan.txt"), encoding="utf-8") as f:
            t = f.read()
        p = t.split("  c01 ")[1][:64]
        self.x.edit_text(f"{STAGE}/plan.txt".replace(os.sep, "/"), p, "0" * 64)
        self.stops("실행 지문이 plan.txt의 판 지문으로 셈한 것과 다르다")

    def test_a_copied_record_is_a_problem(self):
        shutil.copy(self.x.stage("c01-q001-r1.jsonl"), self.x.stage("c01-q001-r1-copy.jsonl"))
        self.stops("c01-q001-r1-copy.jsonl: 파일 이름이 머리의 실행 ID와 다르다")

    def test_a_record_from_another_stage_is_a_problem(self):
        lines = self.x.lines("c01-q002-r1.jsonl")
        lines[0]["stage"] = "pilot2"
        self.x.write_lines("c01-q002-r1.jsonl", lines)
        self.stops("c01-q002-r1.jsonl: 머리의 단계·설정 판·나눔이 설정과 다르다")
        lines[0]["stage"] = "pilot"
        lines[0]["settings_version"] = "v1"
        self.x.write_lines("c01-q002-r1.jsonl", lines)
        self.stops("c01-q002-r1.jsonl: 머리의 단계·설정 판·나눔이 설정과 다르다")

    def test_every_planned_run_needs_its_record_and_nothing_else(self):
        os.remove(self.x.stage("c01-q002-r1.jsonl"))
        self.stops("계획의 실행 1개에 기록이 없다(예: c01-q002-r1)")
        self.x.close()
        self.x = Exp()
        lines = self.x.lines("c01-q002-r1.jsonl")
        lines[0]["run_id"] = "c01-q003-r1"
        self.x.write_lines("c01-q003-r1.jsonl", lines)
        self.stops("계획 밖 기록 1개(예: c01-q003-r1.jsonl)")

    def test_unfinished_and_stopped_runs(self):
        lines = self.x.lines("c01-q002-r1.jsonl")
        self.x.write_lines("c01-q002-r1.jsonl", lines[:-1])
        self.stops("c01-q002-r1.jsonl: 끝(end)이 없다")
        lines[-1]["outcome"] = "STOP_OTHER_STOP_REASON"
        self.x.write_lines("c01-q002-r1.jsonl", lines)
        self.stops("끝난 꼴이 STOP_OTHER_STOP_REASON다")

    def test_a_bad_line_stops(self):
        with open(self.x.stage("c01-q002-r1.jsonl"), "ab") as f:
            f.write(b"{not json}\n")
        self.stops("c01-q002-r1.jsonl의 5번째 줄이 JSON이 아니다")

    def test_verdicts_are_tied_to_the_record_bytes(self):
        lines = self.x.lines("c01-q001-r1.jsonl")
        lines[0]["at"] = "2026-01-01T00:00:00Z"
        self.x.write_lines("c01-q001-r1.jsonl", lines)
        self.stops("판정 없는 SUBMITTED 실행 1개(예: c01-q001-r1)")

    def test_one_judge_print_and_one_line_per_run(self):
        v = self.x.lines("judge/verdicts.jsonl")
        self.x.write_lines("judge/verdicts.jsonl", v + [v[0]])
        self.stops("같은 (단계, 실행 ID, 기록 해시)의 줄이 둘이다")
        v[0]["judge"] = "0" * 64
        self.x.write_lines("judge/verdicts.jsonl", v)
        self.stops("판정 지문이 2가지다")

    def test_a_submitted_run_has_one_accepted_answer(self):
        lines = self.x.lines("c01-q001-r1.jsonl")
        acc = next(x for x in lines if x["type"] == "accepted")
        self.x.write_lines("c01-q001-r1.jsonl", lines[:-1] + [acc, lines[-1]])
        self.stops("SUBMITTED인데 받아들여진 답(accepted)이 하나가 아니다")

    def test_files_that_cannot_be_read_end_with_2(self):
        self.stops("매물 파일의 SHA-256이 다르다", code=2, pin="0" * 64)
        os.remove(self.x.p("cards/v0/K10.md"))
        self.stops("카드 K10이 없다", code=2)
        self.x.close()
        self.x = Exp()
        os.remove(self.x.p("prep/out/answers_fx.json"))
        self.stops("정답 파일이 없다", code=2)
        self.x.close()
        self.x = Exp()
        cfg = json.load(open(score.CONFIG, encoding="utf-8"))
        cfg["more"] = 1
        path = self.x.p("prep/bad.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(cfg, f)
        self.stops("채점 설정의 칸은", code=2, config=path)

    def test_a_non_t2_answer_without_trades_is_outside_the_design(self):
        doc = fx_answers(score.sha256_lf(self.x.p("questions/questions_fx.json")))
        doc["answers"][0]["sample"] = 0
        self.x.write_answers(doc)
        self.stops("표본 0인 T1 문항", code=2)


# ---------------------------------------------------------------- 실행 하나(답을 아는 가짜 기록)

def tool(name, inp, result, is_error=False):
    return {"type": "tool", "at": "T", "name": name, "input": inp, "result": json.dumps(result, ensure_ascii=False), "is_error": is_error,
            "error_code": None, "millis": Decimal("0.5")}


def ts(sid, scope, start="202511", end="202610"):
    inp = {"api": "apt_trade", "region_code": "00000", "start_month": start, "end_month": end, "deal": "매매",
           "dong": "" if scope == "시군구" else "가동", "jibun": "12" if scope == "단지" else ""}
    return tool("trade_stats", inp, {"id": sid, "조건": {"기간": f"{start}~{end}", "비교기준": scope}})


def cp(cid, basis):
    return tool("compare_prices", {"listing_evidence": "매물.가격", "basis_evidence": basis}, {"id": cid, "받은 근거": "매물.가격 대 " + basis})


def stat(slot, value, unit, basis, ev, n, start="202511", end="202610", prov=True):
    return {"slot": slot, "value": value, "unit": unit, "basis": basis, "start_month": start, "end_month": end, "sample_size": n,
            "provisional": prov, "evidence": ev}


def val(slot, value, unit, source, ev):
    return {"slot": slot, "value": value, "unit": unit, "source": source, "evidence": ev}


def submitted(text, stats=(), values=(), contexts=(), findings=()):
    return [{"type": "submit", "at": "T", "input": {"text": text, "stat_claims": list(stats), "value_claims": list(values),
                                                     "context_claims": list(contexts)}, "findings": list(findings)},
            {"type": "accepted", "at": "T", "answer": "그린 답", "millis": Decimal("1.0")}]


def answer(qtype="T1", deal="매매", scope="법정동", sample=7, start="202511", end="202610", **kw):
    a = {"id": "q001", "key": "L01", "split": "연습", "qtype": qtype, "months": 12, "scope": scope,
         "call": {"api": "apt_trade", "region_code": "00000", "start_month": start, "end_month": end, "deal": deal, "dong": "", "jibun": ""},
         "sample": sample, "listing_value": "350000000", "diff": "12.5"}
    if deal == "월세":
        a.update(deposit="10000", monthly="50", converted="22000")
    else:
        a["basis"] = "30000"
    a.update(kw)
    return a


TEXT = "같은 법정동 실거래 기준값보다 {가} {가.방향}."
CFG = score.load_config(score.CONFIG)
FORBIDDEN = score.forbidden_words(K10)


def good_t1():
    """T1(매매, 법정동 7건): 기준값 30000만원, 차이 12.5%."""
    return [ts("S1", "법정동"), cp("C1", "S1.기준")] + submitted(TEXT, [stat("나", 30000, "만원", "법정동", "S1.기준", 7)],
                                                                  [val("가", Decimal("12.5"), "%", "계산", "C1.차이")])


def edit(events, fn):
    ev = copy.deepcopy(events)
    sub = next(e for e in ev if e["type"] == "submit")
    fn(sub["input"], sub)
    return ev


def scored(events, a=None, judge=True, outcome="SUBMITTED"):
    return score.score_run(events, a or answer(), outcome, judge, FORBIDDEN, CFG)


class TestScoreRun(unittest.TestCase):
    def codes(self, events, a=None, judge=True):
        return scored(events, a, judge)["codes"]

    def test_the_good_record_is_fully_correct(self):
        r = scored(good_t1())
        self.assertEqual((r["value"], r["rule"], r["full"], r["full_no_numeral"], r["codes"], r["tags"]), (True, True, True, True, [], []))

    def test_one_change_one_code(self):
        cases = [
            (lambda i, s: i["stat_claims"][0].update(basis="단지"), ["SCOPE"]),
            (lambda i, s: i["stat_claims"][0].update(start_month="202512"), ["PERIOD"]),
            (lambda i, s: i["stat_claims"][0].update(sample_size=6), ["SAMPLE"]),
            (lambda i, s: i["stat_claims"][0].update(provisional=False), ["PROVISIONAL"]),
            (lambda i, s: i["stat_claims"][0].update(value=30001), ["BASIS_VALUE"]),
            (lambda i, s: i["value_claims"][0].update(value=Decimal("12.4")), ["DIFF_VALUE"]),
            (lambda i, s: i.update(stat_claims=[]), ["BASIS_MISSING"]),
            (lambda i, s: i.update(value_claims=[]), ["DIFF_MISSING"]),
            (lambda i, s: i.update(text="같은 법정동 기준값보다 {가.방향}."), ["SLOT_HIDDEN"]),
            (lambda i, s: i.update(text="같은 법정동 기준값과 견준 차이는 {가}다."), ["DIRECTION_MISSING"]),
            (lambda i, s: i.update(text=TEXT + " 비싸다고 본다."), ["LITERAL_DIRECTION"]),
            (lambda i, s: i.update(text=TEXT + " 투자를 추천한다."), ["FORBIDDEN"]),
            (lambda i, s: s.update(findings=["value_claims[0] VALUE"]), ["CHECKER:VALUE"]),
        ]
        for fn, want in cases:
            with self.subTest(want=want):
                r = scored(edit(good_t1(), fn))
                self.assertEqual(r["codes"], want)
                self.assertFalse(r["full"])

    def test_claim_kind_and_source(self):
        # 기준값을 value claim으로: 항목은 CLAIM_KIND, S?의 출처는 국토부 실거래가여야 한다
        ev = edit(good_t1(), lambda i, s: (i.update(stat_claims=[]), i["value_claims"].append(val("나", 30000, "만원", "계산", "S1.기준"))))
        self.assertEqual(self.codes(ev), ["CLAIM_KIND", "SOURCE"])
        # 차이의 출처가 계산이 아니면 SOURCE
        self.assertEqual(self.codes(edit(good_t1(), lambda i, s: i["value_claims"][0].update(source="국토부 실거래가"))), ["SOURCE"])

    def test_units_won_and_manwon(self):
        self.assertEqual(self.codes(edit(good_t1(), lambda i, s: i["stat_claims"][0].update(value=300000000, unit="원"))), [])
        self.assertEqual(self.codes(edit(good_t1(), lambda i, s: i["stat_claims"][0].update(value=300000000, unit="만원"))), ["BASIS_VALUE"])

    def test_numeral_only_findings_pass_the_secondary_result(self):
        r = scored(edit(good_t1(), lambda i, s: s.update(findings=["text NUMERAL"])))
        self.assertEqual((r["full"], r["full_no_numeral"], r["codes"]), (False, True, ["CHECKER:NUMERAL"]))
        r = scored(edit(good_t1(), lambda i, s: s.update(findings=["text NUMERAL", "text TEXT_FORM"])))
        self.assertEqual((r["full"], r["full_no_numeral"]), (False, False))

    def test_the_judge(self):
        r = scored(good_t1(), judge=False)
        self.assertEqual((r["full"], r["codes"]), (False, ["JUDGE_NOT_ANSWERED"]))

    def test_not_submitted(self):
        r = scored([], outcome="FAILED_TURN_LIMIT", judge=None)
        self.assertEqual((r["codes"], r["value"], r["full"]), (["FAILED_TURN_LIMIT"], None, False))

    def test_the_representative_claim(self):
        # 값만 맞는 claim(범위 틀림)과 규칙만 맞는 claim(값 틀림)이 있으면 완전 정답이 아니다
        two = edit(good_t1(), lambda i, s: i.update(stat_claims=[stat("나", 30000, "만원", "단지", "S1.기준", 7),
                                                                  stat("다", 30001, "만원", "법정동", "S1.기준", 7)]))
        r = scored(two)
        self.assertEqual((r["items"][0]["claim"], r["codes"], r["full"]), ("stat_claims[0]", ["SCOPE"], False))
        # 둘 다 맞는 claim이 뒤에 있으면 그것을 고른다
        three = edit(two, lambda i, s: i["stat_claims"].append(stat("라", 30000, "만원", "법정동", "S1.기준", 7)))
        r = scored(three)
        self.assertEqual((r["items"][0]["claim"], r["codes"], r["full"]), ("stat_claims[2]", [], True))
        # 차례는 stat_claims 다음 value_claims
        r = scored(edit(good_t1(), lambda i, s: i["value_claims"].insert(0, val("마", 30000, "만원", "국토부 실거래가", "S1.기준"))))
        self.assertEqual(r["items"][0]["claim"], "stat_claims[0]")

    def test_evidence_ids_are_cleaned_like_the_checker(self):
        # NFC로 맞춘다(풀어 쓴 한글도 찾는다)
        nfd = unicodedata.normalize("NFD", "S1.기준")
        self.assertEqual(self.codes(edit(good_t1(), lambda i, s: i["stat_claims"][0].update(evidence=" " + nfd + "\t"))), [])
        # Java 공백이 아닌 U+00A0은 떼지 않는다(검사기도 못 찾는다)
        self.assertEqual(self.codes(edit(good_t1(), lambda i, s: i["stat_claims"][0].update(evidence=" S1.기준"))), ["BASIS_MISSING"])
        # 꼴은 처음부터 끝까지: C1.기준(원)과 S1.보증금기준은 기준값이 아니다
        for ev in ("C1.기준", "S1.보증금기준", "S01.기준", "XS1.기준"):
            with self.subTest(ev=ev):
                self.assertIn("BASIS_MISSING", self.codes(edit(good_t1(), lambda i, s: i["stat_claims"][0].update(evidence=ev))))

    def test_forbidden_words_are_squeezed_and_seen_in_context_claims(self):
        for t in (TEXT + " 오를것이다.", TEXT + " 틀림 없이 그렇다.", TEXT + " 평 균으로 봐도."):
            with self.subTest(t=t):
                self.assertEqual(self.codes(edit(good_t1(), lambda i, s: i.update(text=t))), ["FORBIDDEN"])
        ev = edit(good_t1(), lambda i, s: i["context_claims"].append({"text": "앞으로 오를 것", "evidence": "S1.표본수"}))
        self.assertEqual(self.codes(ev), ["FORBIDDEN"])

    def test_direction_words_are_read_in_the_text_only(self):
        ev = edit(good_t1(), lambda i, s: i["context_claims"].append({"text": "주변보다 비싸다", "evidence": "C1.차이"}))
        self.assertEqual(self.codes(ev), [])
        r = scored(edit(good_t1(), lambda i, s: i.update(text=TEXT + " 시세보다 높은 편이다.")))
        self.assertEqual((r["codes"], r["direction"]["high_low"]), (["LITERAL_DIRECTION"], {"높": [1, 0]}))
        r = scored(edit(good_t1(), lambda i, s: i.update(text=TEXT + " 표본이 적어 신뢰도가 낮을 수 있다.")))
        self.assertEqual((r["codes"], r["direction"]["high_low"]), ([], {"낮": [0, 1]}))


class TestT2(unittest.TestCase):
    """T2는 넓히지 않는다(3.4): 모든 stat·value claim을 단지 S?까지 잇는다. 0건이면 차이가 없어야 한다(d판)."""

    A = answer("T2", scope="단지", sample=3)

    def base(self):
        return [ts("S1", "단지"), cp("C1", "S1.기준")] + submitted("같은 단지 거래로 본 기준값보다 {가} {가.방향}.",
                                                                  [stat("나", 30000, "만원", "단지", "S1.기준", 3)],
                                                                  [val("가", Decimal("12.5"), "%", "계산", "C1.차이")])

    def test_unit_only_answer_passes(self):
        self.assertEqual(scored(self.base(), self.A)["codes"], [])

    def test_anything_traced_to_a_wider_s_is_widened(self):
        wider = self.base()[:2] + [ts("S2", "법정동"), cp("C2", "S2.기준")]
        sub = self.base()[2:]
        cases = [
            lambda i: i["value_claims"].append(val("다", Decimal("3.1"), "%", "계산", "C2.차이")),
            lambda i: i["value_claims"].append(val("다", 28000, "만원", "국토부 실거래가", "S2.기준")),
            lambda i: i["value_claims"].append(val("다", 280000000, "원", "계산", "C2.기준")),
            lambda i: i["stat_claims"].append(stat("다", 9, "건", "법정동", "S2.표본수", 9)),
        ]
        for fn in cases:
            with self.subTest():
                ev = edit(wider + sub, lambda i, s: fn(i))
                self.assertEqual(scored(ev, self.A)["codes"], ["T2_WIDENED"])
        # context claim은 그려지지 않으니 보지 않는다
        ev = edit(wider + sub, lambda i, s: i["context_claims"].append({"text": "법정동도 봤다", "evidence": "S2.표본수"}))
        self.assertEqual(scored(ev, self.A)["codes"], [])

    def zero(self):
        return [ts("S1", "단지")] + submitted("같은 단지에서 최근 거래가 없어 견줄 수 없다.", [stat("가", 0, "건", "단지", "S1.표본수", 0)])

    def test_no_trade_answer(self):
        z = answer("T2", scope="단지", sample=0, basis=None, diff=None)
        r = scored(self.zero(), z)
        self.assertEqual((r["codes"], r["full"], [i["item"] for i in r["items"]]), ([], True, ["거래 없음"]))
        # 같은 단지를 24달로 넓혀 견준 차이: 잇기는 지나고 0건 줄에 걸린다(d판)
        ev = self.zero()[:1] + [ts("S2", "단지", "202411"), cp("C1", "S2.기준")] + edit(self.zero()[1:], lambda i, s: i["value_claims"].append(
            val("나", Decimal("4.2"), "%", "계산", "C1.차이")))
        self.assertEqual(scored(ev, z)["codes"], ["T2_WIDENED"])
        # 거래 없음에 24달 표본수만 냈다
        ev = [ts("S2", "단지", "202411")] + submitted("최근 두 해 거래는 {가}다.", [stat("가", 3, "건", "단지", "S2.표본수", 3, "202411")])
        self.assertEqual(scored(ev, z)["codes"], ["NO_TRADE_VALUE", "PERIOD", "SAMPLE"])
        self.assertEqual(scored(submitted("거래가 없다."), z)["codes"], ["NO_TRADE_MISSING"])


class TestTypes(unittest.TestCase):
    def test_t4_converted_deposit(self):
        a = answer("T4", deal="월세", scope="단지", sample=5, listing_value="390000000")
        good = [ts("S1", "단지"), tool("convert_rent", {}, {"id": "R1"}), cp("C1", "S1.환산기준")] + submitted(
            "이 매물의 환산보증금은 {다}이고 같은 단지 환산 기준값보다 {가} {가.방향}.", [stat("나", 22000, "만원", "단지", "S1.환산기준", 5)],
            [val("다", 390000000, "원", "계산", "R1.환산보증금"), val("가", Decimal("12.5"), "%", "계산", "C1.차이")])
        r = scored(good, a)
        self.assertEqual((r["codes"], [i["item"] for i in r["items"]]), ([], ["환산보증금", "기준값", "차이"]))
        self.assertEqual(scored(edit(good, lambda i, s: i["value_claims"][0].update(value=39000, unit="만원")), a)["codes"], [])
        self.assertEqual(scored(edit(good, lambda i, s: i["value_claims"].pop(0)), a)["codes"], ["CONVERTED_MISSING"])
        self.assertEqual(scored(edit(good, lambda i, s: i.update(text="같은 단지 환산 기준값보다 {가} {가.방향}.")), a)["codes"], ["SLOT_HIDDEN"])
        # 월세의 기준값은 환산 기준값이다(보증금 기준은 아니다)
        self.assertEqual(scored(edit(good, lambda i, s: i["stat_claims"][0].update(evidence="S1.보증금기준")), a)["codes"], ["BASIS_MISSING"])

    def t5(self, n):
        a = answer("T5", scope="법정동", sample=7, t5_count=n)
        ev = [ts("S1", "법정동"), ts("S2", "단지"), cp("C1", "S1.기준")] + submitted(
            "같은 단지 최근 일 년 거래는 {다}이고 법정동 기준값보다 {가} {가.방향}." if n else "같은 단지 거래는 없었고 법정동 기준값보다 {가} {가.방향}.",
            [stat("나", 30000, "만원", "법정동", "S1.기준", 7), stat("다", n, "건", "단지", "S2.표본수", n)],
            [val("가", Decimal("12.5"), "%", "계산", "C1.차이")])
        return a, ev

    def test_t5_count(self):
        a, ev = self.t5(2)
        self.assertEqual(scored(ev, a)["codes"], [])
        self.assertEqual(scored(edit(ev, lambda i, s: i["stat_claims"][1].update(value=3, sample_size=3)), a)["codes"],
                         ["COUNT_VALUE", "SAMPLE"])
        self.assertEqual(scored(edit(ev, lambda i, s: i.update(text="법정동 기준값보다 {가} {가.방향}.")), a)["codes"], ["SLOT_HIDDEN"])
        # 건수가 0이면 claim만으로 된다(글에 {다}가 없어도)
        a0, ev0 = self.t5(0)
        self.assertEqual(scored(ev0, a0)["codes"], [])


class TestFalseNoTrade(unittest.TestCase):
    """진단 꼬리표(d판 3.7): 정답과 같은 범위·기간의 0건 claim인데 정답 표본이 0이 아닐 때. T5는 단지·12달을 t5_count로."""

    def test_zero_claim_at_the_answer_scope_and_period(self):
        # 옛 코드로 부른 법정동 12달이 0건(정답은 7건)
        ev = [ts("S1", "법정동")] + submitted("같은 법정동 거래가 없다.", [stat("가", 0, "건", "법정동", "S1.표본수", 0)])
        r = scored(ev)
        self.assertEqual((r["tags"], r["codes"]), (["FALSE_NO_TRADE"], ["BASIS_MISSING", "DIFF_MISSING"]))

    def test_a_true_unit_zero_in_a_widened_question_gets_no_tag(self):
        ev = [ts("S2", "단지")] + good_t1()
        ev = edit(ev, lambda i, s: i["stat_claims"].append(stat("다", 0, "건", "단지", "S2.표본수", 0)))
        self.assertEqual(scored(ev)["tags"], [])
        # 기간이 다르면(3달) 보지 않는다
        ev = [ts("S3", "법정동", "202608")] + edit(good_t1(), lambda i, s: i["stat_claims"].append(stat("다", 0, "건", "법정동", "S3.표본수", 0, "202608")))
        self.assertEqual(scored(ev)["tags"], [])

    def test_t5_unit_twelve_months_uses_t5_count(self):
        for n, want in ((0, []), (2, ["FALSE_NO_TRADE"])):
            with self.subTest(n=n):
                a = answer("T5", scope="법정동", sample=7, t5_count=n)
                ev = [ts("S1", "법정동"), ts("S2", "단지"), cp("C1", "S1.기준")] + submitted(
                    "같은 단지 거래는 없었고 법정동 기준값보다 {가} {가.방향}.",
                    [stat("나", 30000, "만원", "법정동", "S1.기준", 7), stat("다", 0, "건", "단지", "S2.표본수", 0)],
                    [val("가", Decimal("12.5"), "%", "계산", "C1.차이")])
                self.assertEqual(scored(ev, a)["tags"], want)


class TestDirectionWords(unittest.TestCase):
    """방향 말 v0(c판 3.4). 상대 셈과 같다."""

    def hit(self, text):
        return score.direction(score.blank(text), CFG)[0]

    def test_counted(self):
        for t in ("시세보다 높은 편이다", "비쌀 수 있다", "비쌈", "기준값보다 {가} {가.방향}, 즉 시세보다 높다", "주변 대비 낮은 값이다"):
            with self.subTest(t=t):
                self.assertTrue(self.hit(t))

    def test_not_counted(self):
        for t in ("신뢰도가 낮을 수 있습니다", "정확도가 다소 낮습니다", "기준값보다 {가} {가.방향}, 높은 층이다", "기준값보다 {가} {가.방향}, 층이 낮은 편이다",
                  "기준값보다 {가} {가.방향}, 다만 표본이 적어 신뢰도가 낮을 수 있다", "기준값보다 {가} {가.방향}. 다만 신뢰도는 상대적으로 다소 낮을 수 있다",
                  "낮은 가격대의 단지다", "신뢰도가 기준값보다 낮다"):
            with self.subTest(t=t):
                self.assertFalse(self.hit(t))

    def test_the_known_v0_case(self):
        # d판 3.4 연습에서 볼 것: '신뢰'가 셋째 앞 어절이라 v0에서 걸린다(고치면 이 시험도 함께 고친다)
        self.assertTrue(self.hit("기준값보다 {가} {가.방향}, 다만 신뢰도는 상대적으로 다소 낮을 수 있다"))

    def test_counts_per_word(self):
        hit, c = score.direction(score.blank("비싸다. 비싸고 싼 편. 시세보다 높고 신뢰도가 낮다"), CFG)
        self.assertEqual((hit, c["words"], c["high_low"]), (True, {"비싸": 2, "싼": 1}, {"높": [1, 0], "낮": [0, 1]}))


class TestPieces(unittest.TestCase):
    def test_java_whitespace(self):
        self.assertEqual(len(score.JAVA_SPACE), 25)
        for ch in ("\u0085", " ", " ", " "):
            self.assertNotIn(ch, score.JAVA_SPACE)
            self.assertEqual(score.jstrip(ch + "S1" + ch), ch + "S1" + ch)
            self.assertEqual((ch + "S1" + ch).strip(), "S1")
        self.assertEqual(score.jstrip("　\t S1.기준  "), "S1.기준")

    def test_jsonl_line_rules(self):
        self.assertEqual(score.jsonl(b'{"a":1}\r\n{"b":2}\n{"c":', "x"), [{"a": 1}, {"b": 2}])
        with self.assertRaises(score.Bind):
            score.jsonl(b'{"a":1}\n[1]\n', "x")
        self.assertIsInstance(score.jsonl(b'{"v":12.5}\n', "x")[0]["v"], Decimal)

    def test_the_code_tree_is_the_same_on_any_os(self):
        d = tempfile.mkdtemp()
        try:
            for rel, text in (("prep/b.py", b"x = 1\n"), ("prep/a.py", b"y = 2\n"), ("picker/c.py", b"z = 3\n"),
                              ("prep/tests/t.py", b"no\n"), ("prep/__pycache__/a.py", b"no\n"), ("prep/n.txt", b"no\n")):
                os.makedirs(os.path.join(d, os.path.dirname(rel)), exist_ok=True)
                with open(os.path.join(d, rel), "wb") as f:
                    f.write(text)
            rels = score.code_files(d)
            self.assertEqual(rels, ["picker/c.py", "prep/a.py", "prep/b.py"])
            want = score.sha256("".join(f"{score.sha256(t)}  {p}\n" for p, t in
                                        (("picker/c.py", b"z = 3\n"), ("prep/a.py", b"y = 2\n"), ("prep/b.py", b"x = 1\n"))).encode())
            self.assertEqual(score.tree(d, rels), want)
            self.assertEqual(score.tree(d, list(reversed(rels))), want)
            self.assertEqual(score.tree(d, [r.replace("/", "\\") for r in rels]), want)
            with open(os.path.join(d, "prep", "a.py"), "wb") as f:
                f.write(b"y = 2\r\n")
            self.assertEqual(score.tree(d, rels), want)
            with open(os.path.join(d, "prep", "a.py"), "wb") as f:
                f.write(b"y = 3\n")
            self.assertNotEqual(score.tree(d, rels), want)
        finally:
            shutil.rmtree(d)

    def test_the_fingerprint_lines(self):
        a = score.fingerprint([("code", "A"), ("score", "B")])
        self.assertEqual(a, score.sha256(b"code  A\nscore  B\n"))
        self.assertNotEqual(a, score.fingerprint([("score", "B"), ("code", "A")]))

    def test_the_plan_matches_the_harness_order(self):
        s = {"variants": [], "questions": [], "reps": [1, 2], "split": "연습", "block_size": 3}
        vs = [{"id": "c01"}, {"id": "c02"}]
        qs = [{"id": f"q{n}", "key": "L01", "split": "연습"} for n in (1, 2)] + [{"id": "q9", "key": "L02", "split": "본시험"}]
        metas = {"L01": {"split": "연습"}, "L02": {"split": "본시험"}}
        ids = [r["id"] for r in score.make_plan(s, vs, qs, metas)]
        self.assertEqual(ids, ["c01-q1-r1", "c01-q2-r1", "c01-q1-r2", "c02-q1-r1", "c02-q2-r1", "c02-q1-r2", "c01-q2-r2", "c02-q2-r2"])
        with self.assertRaises(score.Bad):
            score.make_plan(dict(s, questions=["q9"]), vs, qs, metas)


@unittest.skipUnless(all(os.path.exists(os.path.join(score.CODE_ROOT, p)) for p in ("questions/questions_v0.json", "prep/out/answers_v0.json")),
                     "실제 문항·정답 파일이 없다")
class TestRealFiles(unittest.TestCase):
    """실제 파일(있을 때만): 정답 줄마다 항목 수, 거래 없음, 건수 0, AI 틀 뺀 본시험 문항 수(틀 문장 파일이 있을 때)."""

    def test_items_of_the_real_answers(self):
        root = score.CODE_ROOT
        qpath = os.path.join(root, "questions", "questions_v0.json")
        version, questions = score.load_questions(qpath)
        doc = json.load(open(os.path.join(root, "prep", "out", f"answers_{version}.json"), encoding="utf-8"))
        self.assertEqual(doc["questions_sha256"], score.sha256_lf(qpath))
        items, no_trade, zero_count = Counter(), 0, 0
        for a in doc["answers"]:
            its = score.items_for(a)
            items[a["qtype"]] += len(its)
            no_trade += [i.name for i in its] == ["거래 없음"]
            zero_count += any(i.name == "건수" and i.want == 0 for i in its)
        self.assertEqual((len(doc["answers"]), no_trade, zero_count), (225, 7, 4))
        line = (f"채점 항목 검사: 정답 {len(doc['answers'])}줄, 항목 " + "·".join(f"{t} {items[t]}" for t in score.TYPES)
                + f"(모두 {sum(items.values())}), 거래 없음 {no_trade}, 건수 0 {zero_count}")
        listings_path = os.path.join(root, score.LISTINGS)
        if os.path.exists(os.path.join(root, "questions", "templates_v0.json")) and os.path.exists(listings_path):
            tnums = score.template_numbers(root, qpath, version, questions, listings_path)[0]
            test = [q for q in questions if q["split"] == "본시험"]
            line += f", 본시험 {len(test)}문항 가운데 AI 틀(다섯째) 뺀 것 {sum(1 for q in test if tnums[q['id']] != 5)}"
        print("\n" + line)


if __name__ == "__main__":
    unittest.main()
