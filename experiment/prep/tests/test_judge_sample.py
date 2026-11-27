# -*- coding: utf-8 -*-
"""순서 11a: 판정 표본 읽기(prep judge-sample·judge-tally, prereg_judge_v0 2.1절).

채점 시험 자료(data/score_fx, 하네스가 가짜 모델·배치 서버로 지은 단계 폴더)를 test_score.Exp로 꾸려 쓴다.
판정 있는 실행은 7개이고, 그 가운데 c07-q001-r1은 값·규칙을 지났는데 판정이 답하지 않음이다(표본 B).
11b-1(설계 4판 4.4): 채점 설정 judge_sample_exclude에 적은 옛 표본(tally.tsv)의 실행을 두 바구니에서 빼는 것도 여기서 본다.
"""
import os
import shutil
import unittest

from picker.common import H
from prep import cli, judge_sample, score
from prep.tests.test_score import Exp

JUDGE_MD = os.path.join(score.CODE_ROOT, "harness", "judge", "judge_v0.md")


class TestJudgeSample(unittest.TestCase):
    def setUp(self):
        self.x = Exp()
        os.makedirs(self.x.p("harness/judge"))
        shutil.copy(JUDGE_MD, self.x.p("harness/judge/judge_v0.md"))
        self.judged = sorted(v["run_id"] for v in self.x.lines("judge/verdicts.jsonl"))

    def tearDown(self):
        self.x.close()

    def sample(self):
        out = []
        code = judge_sample.sample(self.x.settings, exp=self.x.root, config=self.x.config, out=out.append)
        return code, out

    def tally(self):
        out = []
        code = judge_sample.tally(self.x.settings, exp=self.x.root, config=self.x.config, out=out.append)
        return code, out

    def path(self, name=judge_sample.SAMPLE):
        return self.x.stage(os.path.join(judge_sample.DIR, name))

    def text(self, name=judge_sample.SAMPLE):
        with open(self.path(name), "r", encoding="utf-8", newline="") as f:
            return f.read()

    def mark(self, marks, crlf=False, bom=False):
        t = self.text()
        parts = t.split(judge_sample.MARK + " ]")
        self.assertEqual(len(parts) - 1, len(marks))
        t = parts[0] + "".join(judge_sample.MARK + m + "]" + p for m, p in zip(marks, parts[1:]))
        data = (t.replace("\n", "\r\n") if crlf else t).encode("utf-8")
        with open(self.path(), "wb") as f:
            f.write((b"\xef\xbb\xbf" if bom else b"") + data)

    def order(self):
        return sorted(self.judged, key=lambda r: (H(judge_sample.PURPOSE_ORDER, r), r))

    def test_writes_the_sample_without_verdicts(self):
        code, out = self.sample()
        self.assertEqual(code, 0, out)
        self.assertTrue(out[0].startswith("판정 표본: 판정 있는 실행 7개 가운데 A 7개, 값·규칙을 지났는데 판정이 답하지 않음인 실행 1개 가운데 B 1개(겹침 1개), "
                                          "읽을 항목 7개, 표본 지문 "), out[0])
        t = self.text()
        self.assertEqual(t.count(judge_sample.ITEM), 7)
        self.assertEqual(t.count(judge_sample.MARK + " ]"), 7)
        self.assertIn("판정 표본 읽기: 단계 pilot, 판정 지문 CEA01B2E0224, 항목 7개", t)
        # 판정 지시문을 그대로 싣는다
        with open(self.x.p("harness/judge/judge_v0.md"), "rb") as f:
            body = score.lf(f.read()).decode("utf-8").strip("\n")
        self.assertIn(judge_sample.indent(body), t)
        # 실행 ID·판정·판정 이유는 싣지 않는다(가린 채 읽는다)
        self.assertEqual(len(self.judged), 7)
        for rid in self.judged:
            self.assertNotIn(rid, t)
        for line in self.x.lines("judge/verdicts.jsonl"):
            self.assertNotIn(line["reason"], t)
        self.assertNotIn("답하지 않음", t)
        # 항목 글은 판정 모델이 받은 질문과 답이고, 모든 줄이 두 칸 들여 쓰였다
        first = self.order()[0]
        rec = self.x.lines(first + ".jsonl")
        q = rec[0]["question"]["text"]
        ans = next(e["answer"] for e in rec if e.get("type") == "accepted")
        self.assertIn("■ 항목 1\n[질문]\n" + judge_sample.indent(q) + "\n[답]\n" + judge_sample.indent(ans) + "\n■ 내 판단: [ ]\n", t)

    def test_same_bytes_again_and_no_overwrite(self):
        self.assertEqual(self.sample()[0], 0)
        first = self.text()
        code, out = self.sample()
        self.assertEqual(code, 2)
        self.assertIn("이미 있다", out[-1])
        self.assertEqual(self.text(), first)
        os.remove(self.path())
        self.assertEqual(self.sample()[0], 0)
        self.assertEqual(self.text(), first)

    def test_picks_by_hash_and_caps_the_sizes(self):
        rows = [{"run_id": f"c01-q{i:03d}-r1", "outcome": "SUBMITTED", "judge": i % 3 != 0, "value": True, "rule": i % 2 == 0} for i in range(1, 121)]
        rows.append({"run_id": "c01-q999-r1", "outcome": "FAILED_TURN_LIMIT", "judge": None, "value": None, "rule": None})
        a, b, n, pool = judge_sample.pick(rows)
        self.assertEqual((len(a), len(b), n), (40, 20, 120))
        self.assertEqual(pool, sum(1 for x in rows[:120] if x["rule"] and x["judge"] is False))
        want_a = sorted((x["run_id"] for x in rows[:120]), key=lambda r: (H("judge_a", r), r))[:40]
        self.assertEqual([x["run_id"] for x in a], want_a)
        self.assertTrue(all(x["value"] and x["rule"] and x["judge"] is False for x in b))
        items = judge_sample.items_of(a, b)
        self.assertEqual(len(items), len({x["run_id"] for x in a} | {x["run_id"] for x in b}))
        self.assertEqual([i[0] for i in items], sorted((i[0] for i in items), key=lambda r: (H("judge_order", r), r)))
        both = {x["run_id"] for x in a} & {x["run_id"] for x in b}
        self.assertEqual({i[0] for i in items if i[1] == "AB"}, both)

    def test_tally_counts_and_writes_numbers_only(self):
        self.assertEqual(self.sample()[0], 0)
        ids = self.order()
        verdict = {v["run_id"]: v["answered"] for v in self.x.lines("judge/verdicts.jsonl")}
        # 첫 항목만 판정과 다르게, 나머지는 판정과 같게 적는다
        marks = [("X" if verdict[r] else "O") if i == 0 else ("O" if verdict[r] else "X") for i, r in enumerate(ids)]
        self.mark(marks)
        code, out = self.tally()
        self.assertEqual(code, 0, out)
        self.assertTrue(out[0].startswith("판정 표본 대조: 항목 7개(A 7개, B 1개, 겹침 1개), 표본 지문 "), out[0])
        first_no = "판정 답함·내 판단 X 1개(항목 1), 판정 답하지 않음·내 판단 O 0개" if verdict[ids[0]] else \
            "판정 답함·내 판단 X 0개, 판정 답하지 않음·내 판단 O 1개(항목 1)"
        self.assertEqual(out[1], f"표본 A(판정 있는 실행 7개 가운데 7개): 같음 6개, {first_no}")
        b_at = ids.index("c07-q001-r1") + 1
        b_line = "내 판단 X(판정과 같음) 0개, 내 판단 O(판정과 다름) 1개(항목 1)" if b_at == 1 else "내 판단 X(판정과 같음) 1개, 내 판단 O(판정과 다름) 0개"
        self.assertEqual(out[2], f"표본 B(값·규칙을 지났는데 판정이 답하지 않음인 실행 1개 가운데 1개): {b_line}")
        self.assertTrue(out[3].startswith("기준(prereg_judge 2.1): A 같음 7개 이상 못 넘음, B의 O 0개 이하 "), out[3])
        with open(self.path(judge_sample.TALLY), encoding="utf-8") as f:
            tsv = [line.rstrip("\n").split("\t") for line in f]
        self.assertEqual(tsv[0], list(judge_sample.TALLY_HEAD))
        self.assertEqual([r[1] for r in tsv[1:]], ids)
        self.assertEqual([r[5] for r in tsv[1:]], ["0"] + ["1"] * 6)
        self.assertEqual(tsv[1 + ids.index("c07-q001-r1")][2], "AB")
        # 글은 differ.txt에만 있다
        with open(self.path(judge_sample.TALLY), encoding="utf-8") as f:
            blob = f.read()
        for line in self.x.lines("judge/verdicts.jsonl"):
            self.assertNotIn(line["reason"], blob)
        d = self.text(judge_sample.DIFFER)
        self.assertIn("판정과 내 판단이 다른 항목 1개", d)
        reason = next(v["reason"] for v in self.x.lines("judge/verdicts.jsonl") if v["run_id"] == ids[0])
        self.assertIn(judge_sample.indent(reason), d)

    def test_all_agreeing_passes_both_rules(self):
        self.assertEqual(self.sample()[0], 0)
        verdict = {v["run_id"]: v["answered"] for v in self.x.lines("judge/verdicts.jsonl")}
        self.mark(["O" if verdict[r] else "X" for r in self.order()], crlf=True, bom=True)
        code, out = self.tally()
        self.assertEqual(code, 0, out)
        self.assertIn("같음 7개", out[1])
        self.assertEqual(out[3], "기준(prereg_judge 2.1): A 같음 7개 이상 넘음, B의 O 0개 이하 넘음")
        self.assertEqual(self.text(judge_sample.DIFFER).count(judge_sample.ITEM), 0)

    def test_marks_must_be_filled_with_o_or_x(self):
        self.assertEqual(self.sample()[0], 0)
        self.mark(["O", "", "x", "Y", "O", "X", " "])
        code, out = self.tally()
        self.assertEqual(code, 2)
        self.assertEqual(out[-1], "멈춤(설정이나 파일): 적지 않은 항목 2개(2, 7) / O·X가 아닌 항목 1개(4)")
        self.assertFalse(os.path.exists(self.path(judge_sample.TALLY)))

    def test_edited_item_heads_stop(self):
        self.assertEqual(self.sample()[0], 0)
        t = self.text().replace(judge_sample.ITEM + "3\n", judge_sample.ITEM + "9\n")
        with open(self.path(), "w", encoding="utf-8", newline="\n") as f:
            f.write(t)
        code, out = self.tally()
        self.assertEqual(code, 2)
        self.assertIn("항목 머리가 1부터 7까지 차례대로 있지 않다", out[-1])

    def test_a_changed_verdict_changes_the_sample_print(self):
        self.assertEqual(self.sample()[0], 0)
        verdict = {v["run_id"]: v["answered"] for v in self.x.lines("judge/verdicts.jsonl")}
        self.mark(["O" if verdict[r] else "X" for r in self.order()])
        lines = self.x.lines("judge/verdicts.jsonl")
        for v in lines:
            if v["run_id"] == "c01-q001-r1":
                v["answered"] = not v["answered"]
        self.x.write_lines("judge/verdicts.jsonl", lines)
        code, out = self.tally()
        self.assertEqual(code, 3)
        self.assertIn("표본 지문", out[-1])
        self.assertIn("다르다", out[-1])

    def test_without_a_sample_the_tally_stops(self):
        code, out = self.tally()
        self.assertEqual(code, 2)
        self.assertIn("먼저 judge-sample을 친다", out[-1])

    def test_binding_problems_stop_before_writing(self):
        lines = self.x.lines("judge/verdicts.jsonl")
        self.x.write_lines("judge/verdicts.jsonl", [v for v in lines if v["run_id"] != "c01-q001-r1"])
        code, out = self.sample()
        self.assertEqual(code, 3)
        self.assertIn("판정 없는 SUBMITTED 실행 1개", out[-1])
        self.assertFalse(os.path.exists(self.path()))

    def write_old(self, ids, folder="judge_sample_v0", head=judge_sample.TALLY_HEAD):
        """옛 표본 폴더의 tally.tsv(judge-tally가 쓴 꼴)."""
        d = self.x.stage(folder)
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, judge_sample.TALLY), "w", encoding="utf-8", newline="\n") as f:
            f.write("\t".join(head) + "\n" + "".join(f"{n}\t{rid}\tA\t답함\tO\t1\n" for n, rid in enumerate(ids, 1)))

    def exclude(self, ex):
        self.x.config = self.x.write_config("prep/score_ex.json", judge_sample_exclude=ex)

    def test_old_samples_are_left_out_of_both_baskets(self):
        old = ["c01-q001-r1", "c07-q001-r1"]  # 판정 있는 실행 하나와 유일한 B 후보
        self.write_old(old)
        self.exclude({"pilot": ["judge_sample_v0"]})
        code, out = self.sample()
        self.assertEqual(code, 0, out)
        self.assertEqual(out[0], "판정 표본: 뺀 옛 표본 judge_sample_v0 2개(판정 있는 실행에서 2개, B가 될 수 있는 실행에서 1개)")
        self.assertTrue(out[1].startswith("판정 표본: 판정 있는 실행 5개 가운데 A 5개, 값·규칙을 지났는데 판정이 답하지 않음인 실행 0개 가운데 B 0개"
                                          "(겹침 0개), 읽을 항목 5개, 표본 지문 "), out[1])
        t = self.text()
        self.assertIn("뺀 옛 표본 judge_sample_v0 2개(판정 있는 실행에서 2개, B가 될 수 있는 실행에서 1개)\n", t)
        self.assertEqual(t.count(judge_sample.ITEM), 5)
        verdict = {v["run_id"]: v["answered"] for v in self.x.lines("judge/verdicts.jsonl")}
        left = [r for r in self.order() if r not in old]
        self.mark(["O" if verdict[r] else "X" for r in left])
        code, out = self.tally()
        self.assertEqual(code, 0, out)
        self.assertEqual(out[0], "판정 표본 대조: 뺀 옛 표본 judge_sample_v0 2개(판정 있는 실행에서 2개, B가 될 수 있는 실행에서 1개)")
        self.assertEqual(out[1], "판정 표본 대조: 항목 5개(A 5개, B 0개, 겹침 0개), 표본 지문 " + out[1].rsplit(" ", 1)[1])
        with open(self.path(judge_sample.TALLY), encoding="utf-8") as f:
            self.assertEqual([x.split("\t")[1] for x in f.read().split("\n")[1:-1]], left)
        # 빼기를 잊고 대조하면 표본이 달라져 멈춘다
        self.exclude({})
        code, out = self.tally()
        self.assertEqual(code, 3, out)
        self.assertIn("표본 지문", out[-1])

    def test_old_samples_of_another_stage_change_nothing(self):
        self.exclude({"main": ["judge_sample_v0"]})
        code, out = self.sample()
        self.assertEqual(code, 0, out)
        self.assertTrue(out[0].startswith("판정 표본: 판정 있는 실행 7개 가운데 A 7개"), out[0])
        self.assertNotIn("뺀 옛 표본", self.text())

    def test_a_missing_or_bad_old_sample_stops(self):
        self.exclude({"pilot": ["judge_sample_v0"]})
        code, out = self.sample()
        self.assertEqual((code, out[-1]), (2, "멈춤(설정이나 파일): 뺄 옛 표본의 tally.tsv가 없다: harness/runs/pilot/judge_sample_v0/tally.tsv"))
        for ids, head, word in ((["c99-q001-r1"], judge_sample.TALLY_HEAD, "이 단계의 계획에 없다(없는 것 1개)"),
                                (["c01-q001-r1", "c01-q001-r1"], judge_sample.TALLY_HEAD, "겹치거나"),
                                (["c01-q001-r1"], ("항목", "실행"), "judge-tally가 쓴 꼴이 아니다"),
                                ([], judge_sample.TALLY_HEAD, "judge-tally가 쓴 꼴이 아니다")):
            with self.subTest(word=word, ids=ids):
                self.write_old(ids, head=head)
                code, out = self.sample()
                self.assertEqual(code, 2, out)
                self.assertIn(word, out[-1])
                self.assertFalse(os.path.exists(self.path()))

    def test_the_cli_knows_both_commands(self):
        p = cli.build_parser()
        for name in ("judge-sample", "judge-tally"):
            a = p.parse_args([name, "harness/settings/pilot_v0.json"])
            self.assertEqual((a.cmd, a.settings, a.listings), (name, "harness/settings/pilot_v0.json", cli.LISTINGS))


if __name__ == "__main__":
    unittest.main()
