# -*- coding: utf-8 -*-
"""순서 8c: 통계·보고 표(순서 8c 설계 2판 12절).

- 지어낸 단계(매물·문항·실행 결과)로 한 곳씩 본다: 반올림(동점은 0에서 먼 쪽), 뽑기 식(0부터 센 자리), 구간 차례, 알려진 답, 짝짓기,
  칸과 빈 뽑기, 보조 결과 둘, 시간, 큰 수 칸, 채점 설정의 bootstrap·comparisons.
- 시험 자료(data/score_fx)로 끝까지: 찍는 줄, stats.tsv·report.md의 꼴과 "-" 자리.
- 뽑기 수는 작게 둔다(시험 시간). B = 10,000은 검산에서 본다.
"""
import hashlib
import os
import random
import unittest
from decimal import Decimal, ROUND_HALF_UP, localcontext
from fractions import Fraction

from picker.common import H, u64
from prep import score, stats
from prep.tests import test_score as TS


def fake(listings, questions, variants, reps, result, split="본시험", cache_off=False, unjudged="stop", draws=200, comparisons=None,
         tnums=None):
    """지어낸 단계(ctx, 실행 결과). listings: [(층, 나눔, 시군구 코드)] → 키 L01부터. questions: [(문항 ID, 키, 유형)](나눔은 매물의 것).
    result(판, 문항, 회차) → (완전 정답, 수사 뺀, 끝난 꼴, 판정, millis)."""
    metas = {"L%02d" % (i + 1): {"complex_id": f"apt|{a}|가동|0|1|0", "deal": "매매", "layer": ly, "split": sp}
             for i, (ly, sp, a) in enumerate(listings)}
    qs = [{"id": q, "key": k, "qtype": t, "split": metas[k]["split"], "text": "글"} for q, k, t in questions]
    vs = [{"id": v, "name": v.upper(), "model": "m", "parts": []} for v in variants]
    plan, rows, records = [], [], {}
    for v in vs:
        for rep in reps:
            for q in qs:
                if q["split"] != split:
                    continue
                rid = f"{v['id']}-{q['id']}-r{rep}"
                full, nn, outcome, judge, ms = result(v["id"], q["id"], rep)
                plan.append({"id": rid, "variant": v, "question": q, "rep": rep})
                rows.append({"full": full, "full_no_numeral": nn, "outcome": outcome, "judge": judge, "value": full, "rule": full})
                records[rid] = {"events": [{"type": "response", "millis": ms}, {"type": "hook", "millis": 1000}]}
    cfg = dict(score.load_config(score.CONFIG), unjudged=unjudged, bootstrap={"draws": draws, "purpose": "boot"},
               comparisons=comparisons or {"main": (variants[-1], variants[0]), "aux": []})
    s = {"stage": "t", "split": split, "version": "v0", "reps": list(reps), "cache": {"system": not cache_off, "conversation": not cache_off}}
    ctx = {"s": s, "cfg": cfg, "metas": metas, "plan": plan, "tnums": tnums, "records": records,
           "env": {"current": {k: m["complex_id"].split("|")[1] for k, m in metas.items()}},
           "print": "A" * 64, "judge_print": None, "qsha": "B" * 64, "asha": "C" * 64}
    return ctx, rows


def run(ctx, rows):
    text, report, line = stats.compute(ctx, rows, score.tally(ctx["plan"], rows), [])
    lines = [x.split("\t") for x in text.rstrip("\n").split("\n")]
    assert lines[0] == list(stats.STATS_HEAD)
    return {tuple(x[:4]): x[4:] for x in lines[1:]}, report, line


def ok(v):
    return (v, v, "SUBMITTED", True, 10)


DASHES = ["-"] * 6


class TestRounding(unittest.TestCase):
    def test_half_up_ties_go_away_from_zero(self):
        f = stats.fmt
        self.assertEqual([f(Fraction(5, 4), 1), f(Fraction(-5, 4), 1), f(Fraction(-1, 25), 1), f(Fraction(1, 20), 1), f(Fraction(-1, 20), 1)],
                         ["1.3", "-1.3", "0.0", "0.1", "-0.1"])
        # 상대의 예: 층 칸(48문항 × 5회)에서 차이 3/240 = 1.25%p
        self.assertEqual([f(Fraction(3, 240) * 100, 1), f(Fraction(-3, 240) * 100, 1)], ["1.3", "-1.3"])
        self.assertEqual([f(Fraction(1, 3), 6), f(Fraction(-2, 3), 6), f(Fraction(0), 6), f(7, 6), f(None, 6)],
                         ["0.333333", "-0.666667", "0.000000", "7.000000", "-"])
        self.assertEqual([f(Fraction(-1, 2_000_000), 6), f(Fraction(-1, 2_000_001), 6), f(Fraction(-12345, 10), 0)], ["-0.000001", "0.000000", "-1235"])

    def test_same_as_decimal_half_up(self):
        def dec(x, places):
            with localcontext() as c:
                c.prec = 60
                d = (Decimal(x.numerator) / Decimal(x.denominator)).quantize(Decimal(1).scaleb(-places), rounding=ROUND_HALF_UP)
            s = format(d, "f")
            return s[1:] if s.startswith("-") and d == 0 else s
        for den in (8, 40, 240, 750, 3000):
            for n in range(-2 * den, 2 * den + 1):
                x = Fraction(n, den)
                self.assertEqual((stats.fmt(x * 100, 1), stats.fmt(x, 6)), (dec(x * 100, 1), dec(x, 6)), x)


class TestDraws(unittest.TestCase):
    def test_draws_follow_the_formula_with_zero_based_places(self):
        strata = [("화성형", [0, 1, 2]), ("일반", [3, 4])]
        got = stats.draw(strata, 4, "boot")
        for b in range(1, 5):
            want = {}
            for L, xs in strata:
                for j in range(1, len(xs) + 1):
                    h = hashlib.sha256(f"zipinfo-example-v1|boot|{b}|{L}|{j}".encode("utf-8")).hexdigest()
                    i = xs[int(h[:16], 16) % len(xs)]  # 키 차례로 세운 목록에서 0부터 센 자리
                    want[i] = want.get(i, 0) + 1
            self.assertEqual(got[b - 1], sorted(want.items()))
            self.assertEqual((sum(m for i, m in got[b - 1] if i < 3), sum(m for i, m in got[b - 1] if i >= 3)), (3, 2))  # 층 안에서 n_L개
        self.assertEqual(u64(H("boot", "1|화성형|1")), int(hashlib.sha256("zipinfo-example-v1|boot|1|화성형|1".encode()).hexdigest()[:16], 16))
        self.assertNotEqual(stats.draw(strata, 4, "boot2"), got)  # purpose가 뽑기에 든다

    def test_one_listing_layer_is_always_drawn(self):
        self.assertEqual(stats.draw([("일반", [7])], 3, "boot"), [[(7, 1)]] * 3)


class TestInterval(unittest.TestCase):
    def test_ranks(self):
        n = list(range(10000))
        random.Random(1).shuffle(n)
        self.assertEqual(stats.interval(n, [1] * 10000, 1, {}, "n"), (249, 9750, 0))  # 250번째·9,751번째(값 = 차례 − 1)
        self.assertEqual(stats.interval(n, [1] * 9998 + [0, 0], 1, {}, "n")[2], 2)
        m = list(range(9998)) + [5, 5]
        self.assertEqual(stats.interval(m, [1] * 9998 + [0, 0], 1, {}, "n"), (249, 9748, 2))  # B′ 9,998 → 250·9,749번째
        self.assertEqual(stats.interval(list(range(41)), [1] * 41, 1, {}, "n"), (1, 39, 0))  # ⌈41/40⌉ = 2
        self.assertEqual(stats.interval([7] * 50, [2] * 50, 1, {}, "n"), (Fraction(7, 2), Fraction(7, 2), 0))  # 모두 같으면 한 점
        self.assertEqual(stats.interval([1, 2], [0, 0], 1, {}, "n"), (None, None, 2))
        # 분모가 다른 뽑기: 열쇠는 최소공배수로 맞춘 정수다. scale은 분모에 곱한다
        self.assertEqual(stats.interval([1, 1, 2, 5, -3], [3, 2, 4, 6, 0], 5, {}, "x"), (Fraction(1, 15), Fraction(1, 6), 1))
        self.assertEqual(stats.interval([-1, -1, -2, -5], [3, 2, 4, 6], 1, {}, "x"), (Fraction(-5, 6), Fraction(-1, 3), 0))

    def test_wide_fields(self):
        big = 1 << 70
        ser = [[big, 1, 0], [3, big * 5, 2]]
        draws = [[(0, 2), (2, 1)], [(1, 3)]]
        self.assertEqual([list(x) for x in stats.packed_sums(ser, draws)], [[2 * big, 3], [8, 15 * big]])
        ser = [[0, 1, 2], [5, 0, 1]]
        self.assertEqual([list(x) for x in stats.packed_sums(ser, draws)], [[2, 3], [11, 0]])


L3 = [("화성형", "본시험", "11")] * 3 + [("일반", "본시험", "22")] * 2


def q10():
    return [(f"q{i}", "L%02d" % (i // 2 + 1), ("T1", "T2")[i % 2]) for i in range(10)]


class TestValues(unittest.TestCase):
    def test_known_answers(self):
        ctx, rows = fake(L3, q10(), ["a", "b", "c"], [1, 2], lambda v, q, r: ok(v == "a"),
                         comparisons={"main": ("a", "b"), "aux": [("c", "b"), ("b", "a")]})
        t, report, line = run(ctx, rows)
        self.assertEqual(t[("판", "a", "완전 정답", "전체")], ["10", "1.000000", "1.000000", "1.000000", "1/1", "0"])
        self.assertEqual(t[("판", "b", "완전 정답", "전체")], ["10", "0.000000", "0.000000", "0.000000", "0/1", "0"])
        self.assertEqual(t[("비교", "a-b", "완전 정답", "전체")], ["10", "1.000000", "1.000000", "1.000000", "1/1", "0"])
        self.assertEqual(t[("비교", "b-a", "수사 뺀", "층 일반")], ["4", "-1.000000", "-1.000000", "-1.000000", "-1/1", "0"])
        self.assertEqual(t[("비교", "c-b", "완전 정답", "유형 T2")], ["5", "0.000000", "0.000000", "0.000000", "0/1", "0"])
        self.assertEqual(len(t), 6 * 4 * 11)  # 이름(판 셋, 비교 셋) × 결과 넷 × 칸(전체, 층 둘, 유형 다섯, ⑤ 둘, AI 틀 뺌)
        self.assertTrue(line.startswith("통계: 매물 5개(화성형 3·일반 2) 뽑기 200번, 판 3개·비교 3개(주 1, 보조 2, 단계에 없는 판이 든 것 0), 빈 뽑기 "))

    def test_pairs_by_question(self):
        rng = random.Random(7)
        res = {(v, q, r): rng.random() < p for v, p in (("a", 0.7), ("b", 0.4)) for q, _, _ in q10() for r in (1, 2, 3)}
        ctx, rows = fake(L3, q10(), ["a", "b"], [1, 2, 3], lambda v, q, r: ok(res[(v, q, r)]), comparisons={"main": ("a", "b"), "aux": []})
        t, _, _ = run(ctx, rows)
        inside = {"전체": lambda k, ty: True, "층 화성형": lambda k, ty: int(k[1:]) <= 3, "층 일반": lambda k, ty: int(k[1:]) > 3,
                  "유형 T1": lambda k, ty: ty == "T1", "유형 T2": lambda k, ty: ty == "T2"}
        for cell, f in inside.items():
            a, b, d = (Fraction(t[k][4]) for k in [("판", "a", "완전 정답", cell), ("판", "b", "완전 정답", cell), ("비교", "a-b", "완전 정답", cell)])
            self.assertEqual(d, a - b, cell)
            # 문항마다 회차 평균의 평균
            qs = [q for q, k, ty in q10() if f(k, ty)]
            self.assertEqual(a, sum(Fraction(sum(res[("a", q, r)] for r in (1, 2, 3)), 3) for q in qs) / len(qs), cell)

    def test_point_estimate_is_the_original_sample_and_draws_are_shared(self):
        rng = random.Random(3)
        res = {(v, q, r): rng.random() < 0.5 for v in "ab" for q, _, _ in q10() for r in (1, 2)}
        ctx, rows = fake(L3, q10(), ["a", "b"], [1, 2], lambda v, q, r: ok(res[(v, q, r)]), draws=97)
        t1, _, _ = run(ctx, rows)
        t2, _, _ = run(ctx, rows)
        self.assertEqual(t1, t2)
        # 같은 결과의 판 둘은 차이가 늘 0이다(같은 뽑기를 쓰니 구간도 0)
        res2 = {k: res[("a",) + k[1:]] for k in res}
        ctx, rows = fake(L3, q10(), ["a", "b"], [1, 2], lambda v, q, r: ok(res2[(v, q, r)]), draws=97)
        t, _, _ = run(ctx, rows)
        self.assertEqual(t[("비교", "b-a", "완전 정답", "전체")][1:5], ["0.000000", "0.000000", "0.000000", "0/1"])


class TestCells(unittest.TestCase):
    def test_area_split_templates_and_empty_draws(self):
        L = [("화성형", "연습", "11")] + [("화성형", "본시험", "11")] * 3 + [("일반", "본시험", "33")] + [("일반", "본시험", "11")] * 3
        Q = [("p1", "L01", "T1")] + [(f"q{i}", "L%02d" % (i + 2), "T1") for i in range(7)] + [("q9", "L05", "T5")]
        tn = {q: (5 if q in ("q0", "q9") else 1) for q, _, _ in Q}
        ctx, rows = fake(L, Q, ["a", "b"], [1], lambda v, q, r: ok(v == "a" and q != "q4"), tnums=tn, draws=300)
        t, report, line = run(ctx, rows)
        self.assertEqual([t[("판", "a", "완전 정답", c)][0] for c in ("전체", "⑤ 있던", "⑤ 없던", "AI 틀 뺌", "유형 T5", "층 화성형")],
                         ["8", "6", "2", "6", "1", "3"])
        # 유형 T5는 L05(일반 넷 가운데 하나)에만 있다. 빈 뽑기 = L05가 뽑히지 않은 뽑기
        draws = stats.draw([("화성형", [0, 1, 2]), ("일반", [3, 4, 5, 6])], 300, "boot")
        empty = sum(1 for d in draws if all(i != 3 for i, _ in d))
        self.assertGreater(empty, 0)
        self.assertEqual(t[("판", "a", "완전 정답", "유형 T5")][5], str(empty))
        self.assertEqual(t[("판", "a", "완전 정답", "⑤ 없던")][5], str(empty))  # ⑤ 없던도 L05 하나
        self.assertIn(f"빈 뽑기 유형 T5 {empty}번, ⑤ 없던 {empty}번", line)
        self.assertEqual(t[("판", "a", "완전 정답", "유형 T3")], ["0", "-", "-", "-", "-", "300"])  # 문항이 없는 칸
        # 틀 문장 파일이 없으면 AI 틀 뺌이 "-", 연습 단계는 ⑤가 "-"
        ctx, rows = fake(L, Q, ["a", "b"], [1], lambda v, q, r: ok(True))
        t, _, _ = run(ctx, rows)
        self.assertEqual(t[("판", "a", "완전 정답", "AI 틀 뺌")], DASHES)
        ctx, rows = fake(L, Q, ["a", "b"], [1], lambda v, q, r: ok(True), split="연습", tnums=tn)
        t, _, line = run(ctx, rows)
        self.assertEqual((t[("판", "a", "완전 정답", "⑤ 있던")], t[("비교", "b-a", "수사 뺀", "⑤ 없던")]), (DASHES, DASHES))
        self.assertEqual(t[("판", "a", "완전 정답", "AI 틀 뺌")][0], "1")
        self.assertTrue(line.startswith("통계: 매물 1개(화성형 1) 뽑기 200번"), line)

    def test_a_comparison_with_a_variant_outside_the_stage_is_dash(self):
        ctx, rows = fake(L3, q10(), ["a", "b"], [1], lambda v, q, r: ok(True), comparisons={"main": ("z", "a"), "aux": [("b", "a"), ("a", "y")]})
        t, report, line = run(ctx, rows)
        self.assertEqual({k: v for k, v in t.items() if k[1] in ("z-a", "a-y") and v != DASHES}, {})
        self.assertNotEqual(t[("비교", "b-a", "완전 정답", "전체")], DASHES)
        self.assertIn("비교 3개(주 1, 보조 2, 단계에 없는 판이 든 것 2)", line)
        self.assertIn("이 단계에 z·a가 다 있지 않아 내지 않았다.", report)


class TestOtherOutcomes(unittest.TestCase):
    def test_numeral_only_runs_pass_only_the_numeral_free_outcome(self):
        ctx, rows = fake(L3, q10(), ["a", "b"], [1], lambda v, q, r: (False, v == "b", "SUBMITTED", True, 10))
        t, _, _ = run(ctx, rows)
        self.assertEqual((t[("비교", "b-a", "완전 정답", "전체")][4], t[("비교", "b-a", "수사 뺀", "전체")][4]), ("0/1", "1/1"))

    def test_unjudged_runs_are_left_out_and_empty_questions_drop_from_the_pair(self):
        # a: q0은 두 회차 모두 판정 없음, q1은 한 회차만. b는 모두 판정 있음
        def res(v, q, r):
            if v == "a" and (q == "q0" or (q == "q1" and r == 1)):
                return False, False, "SUBMITTED", None, 10
            return (r == 2), (r == 2), "SUBMITTED", True, 10
        ctx, rows = fake(L3, q10(), ["a", "b"], [1, 2], res, unjudged="fail")
        t, report, _ = run(ctx, rows)
        # a: q1은 남은 회차 1개(맞음) → 1, 나머지 여덟 문항은 1/2 → (1 + 8/2) / 9 = 5/9
        self.assertEqual(t[("판", "a", "판정 없는 실행 뺀", "전체")][0::4], ["9", "5/9"])
        self.assertEqual(t[("판", "b", "판정 없는 실행 뺀", "전체")][0::4], ["10", "1/2"])
        # b − a: q0을 빼고 아홉 문항. q1은 1/2 − 1, 나머지는 0 → −1/18
        self.assertEqual(t[("비교", "b-a", "판정 없는 실행 뺀", "전체")][0::4], ["9", "-1/18"])
        # 판정 없는 실행은 완전 정답에서는 실패로 센다
        self.assertEqual(t[("판", "a", "완전 정답", "전체")][4], "9/20")
        self.assertNotIn("판정 없는 실행이 없어", report)
        # unjudged가 stop이면(판정 없는 실행이 있을 수 없다) "-"
        ctx, rows = fake(L3, q10(), ["a", "b"], [1, 2], lambda v, q, r: ok(True))
        t, report, _ = run(ctx, rows)
        self.assertEqual(t[("판", "a", "판정 없는 실행 뺀", "전체")], DASHES)
        self.assertIn("이 단계에는 판정 없는 실행이 없어 내지 않았다.", report)

    def test_time_only_when_the_cache_is_off(self):
        ms = lambda v, q, r: Decimal("1500.5") if v == "a" else (1000 * r)
        res = lambda v, q, r: (True, True, "SUBMITTED", True, ms(v, q, r))
        ctx, rows = fake(L3, q10(), ["a", "b"], [1, 2], res)
        t, report, _ = run(ctx, rows)
        self.assertEqual(t[("판", "a", "소요시간(초)", "전체")], DASHES)
        self.assertIn("캐시를 켠 단계라 내지 않았다.", report)
        for half in ({"system": False, "conversation": True}, {"system": True, "conversation": False}):  # 둘 다 꺼야 한다
            ctx["s"]["cache"] = half
            self.assertEqual(run(ctx, rows)[0][("비교", "b-a", "소요시간(초)", "전체")], DASHES)
        ctx, rows = fake(L3, q10(), ["a", "b"], [1, 2], res, cache_off=True)
        t, report, _ = run(ctx, rows)
        self.assertEqual(t[("판", "a", "소요시간(초)", "전체")], ["10", "1.500500", "1.500500", "1.500500", "3001/2000", "0"])
        self.assertEqual(t[("비교", "b-a", "소요시간(초)", "층 일반")], ["4", "-0.000500", "-0.000500", "-0.000500", "-1/2000", "0"])
        self.assertIn("| a | 10 | 1.5 [1.5, 1.5] |", report)
        ctx, rows = fake(L3, q10(), ["a", "b"], [1], lambda v, q, r: (True, True, "SUBMITTED", True, -5), cache_off=True)
        with self.assertRaises(score.Bad):
            run(ctx, rows)

    def test_run_time_is_the_exact_sum_of_the_timed_events(self):
        ev = [{"type": "response", "millis": 3}, {"type": "tool", "millis": Decimal("0.125")}, {"type": "accepted", "millis": Decimal("1.0")},
              {"type": "hook", "millis": 9}, {"type": "tool", "millis": "2"}, {"type": "response"}, {"type": "nudge", "millis": 4}]
        self.assertEqual(stats.run_ms(ev), Fraction(49, 8))


class TestConfig(unittest.TestCase):
    def setUp(self):
        self.x = TS.Exp()

    def tearDown(self):
        self.x.close()

    def stops(self, msg, **kw):
        code, out = self.x.run(config=self.x.write_config("prep/c.json", **kw))
        self.assertEqual(code, 2, out)
        self.assertIn(msg, out[0])

    def test_bootstrap_and_comparisons_are_checked(self):
        self.stops("채점 설정 bootstrap은 draws(1~1000000의 정수)·purpose(글)다", bootstrap={"draws": 0, "purpose": "boot"})
        self.stops("채점 설정 bootstrap은", bootstrap={"draws": 100, "purpose": " "})
        self.stops("채점 설정 bootstrap은", bootstrap={"draws": 100})
        self.stops("채점 설정 comparisons는 main(서로 다른 판 ID 둘)·aux(그런 짝의 목록)다", comparisons={"main": ["c01", "c01"], "aux": []})
        self.stops("채점 설정 comparisons는", comparisons={"main": ["c06", "c01"], "aux": [["c04"]]})
        self.stops("채점 설정 comparisons에 같은 비교가 둘이다", comparisons={"main": ["c06", "c01"], "aux": [["c06", "c01"]]})
        self.stops("채점 설정 comparisons의 판이 판 설정 파일에 없다: c99 c98",
                   comparisons={"main": ["c99", "c01"], "aux": [["c98", "c99"]]})
        self.assertFalse(os.path.exists(self.x.stage("score")))


class TestFixture(unittest.TestCase):
    """하네스가 지은 기록으로 끝까지(시험용 채점 설정: 뽑기 200번, 주 c06 − c01, 보조 c04·c05·c07 − c01)."""

    def setUp(self):
        self.x = TS.Exp()

    def tearDown(self):
        self.x.close()

    def test_lines_stats_and_report(self):
        code, out = self.x.run()
        self.assertEqual(code, 0, out)
        self.assertIn("통계: 매물 2개(화성형 2) 뽑기 200번, 판 5개·비교 4개(주 1, 보조 3, 단계에 없는 판이 든 것 0), 빈 뽑기 유형 T1 54번, 유형 T2 36번, "
                      "stats.tsv 360줄과 report.md를 썼다", out)
        self.assertEqual(out[-1], "harness/runs/pilot/score/runs.jsonl·summary.tsv·metrics.tsv·stats.tsv·report.md를 썼다")
        with open(self.x.stage("score/stats.tsv"), encoding="utf-8") as f:
            t = {tuple(x.rstrip("\n").split("\t")[:4]): x.rstrip("\n").split("\t")[4:] for x in list(f)[1:]}
        self.assertEqual(len(t), (5 + 4) * 4 * 10)  # 칸: 전체, 층 하나, 유형 다섯, ⑤ 둘, AI 틀 뺌
        self.assertEqual(t[("판", "c01", "완전 정답", "전체")], ["2", "0.500000", "0.000000", "1.000000", "1/2", "0"])
        self.assertEqual(t[("비교", "c07-c01", "완전 정답", "전체")], ["2", "-0.500000", "-1.000000", "0.000000", "-1/2", "0"])
        self.assertEqual(t[("판", "c01", "완전 정답", "유형 T1")], ["1", "1.000000", "1.000000", "1.000000", "1/1", "54"])
        self.assertEqual(t[("판", "c06", "완전 정답", "유형 T3")], ["0", "-", "-", "-", "-", "200"])
        for k in [("판", "c01", "완전 정답", "⑤ 있던"), ("판", "c04", "완전 정답", "AI 틀 뺌"), ("비교", "c06-c01", "판정 없는 실행 뺀", "전체"),
                  ("판", "c05", "소요시간(초)", "전체")]:
            self.assertEqual(t[k], DASHES, k)  # 연습 단계, 틀 문장 파일 없음, 판정 없는 실행 없음, 캐시 켬
        with open(self.x.stage("score/report.md"), encoding="utf-8") as f:
            report = f.read()
        for part in ("# 채점 보고: 단계 pilot (연습)", "## 1. 판마다 (전체 칸)", "## 2. 주 가설: c06 − c01", "## 3. 보조 비교 (전체 칸)",
                     "여러 번 견준 보조 결과다.", "## 4. 15.9 지표 (판마다, 구간 없이 기술만)", "## 5. 비용 (판마다)", "같은 모델의 판끼리만 견준다.",
                     "## 6. 소요시간 (캐시를 끈 단계만)", "캐시를 켠 단계라 내지 않았다.",
                     "| c01 | B0 | claude-haiku-5-5 | 2 | 1 | 1 | 1 | 1 | 1 | 50.0 [0.0, 100.0] | 50.0 [0.0, 100.0] |",
                     "| c07 − c01 | 2 | -50.0 [-100.0, 0.0] | -50.0 [-100.0, 0.0] | - |",
                     "| c06 | ③(면적) |  |  | 100.0 (1/1) |", "| c07 | claude-haiku-5-5 | 0.0003 | 0.0003 | - |"):
            self.assertIn(part, report)
        self.assertTrue(report.endswith("\n") and "\r" not in report)

    def test_unjudged_runs_left_out_end_to_end(self):
        v = [x for x in self.x.lines("judge/verdicts.jsonl") if x["run_id"] != "c01-q001-r1"]
        self.x.write_lines("judge/verdicts.jsonl", v)
        code, out = self.x.run(config=self.x.write_config("prep/score_fail.json", unjudged="fail"))
        self.assertEqual(code, 0, out)
        with open(self.x.stage("score/stats.tsv"), encoding="utf-8") as f:
            t = {tuple(x.rstrip("\n").split("\t")[:4]): x.rstrip("\n").split("\t")[4:] for x in list(f)[1:]}
        self.assertEqual(t[("판", "c01", "완전 정답", "전체")][0::4], ["2", "0/1"])  # 판정 없는 실행은 완전 정답이 아님
        self.assertEqual(t[("판", "c01", "판정 없는 실행 뺀", "전체")][0::4], ["1", "0/1"])  # q001이 빠진다
        self.assertEqual(t[("판", "c06", "판정 없는 실행 뺀", "전체")][0::4], ["2", "1/2"])
        self.assertEqual(t[("비교", "c06-c01", "판정 없는 실행 뺀", "전체")][0::4], ["1", "0/1"])  # 짝에서도 q001이 빠진다

    def test_average_time_in_the_report_is_the_harness_summary(self):
        self.assertEqual(self.x.run()[0], 0)
        with open(self.x.stage("summary.tsv"), encoding="utf-8") as f:
            rows = [x.rstrip("\n").split("\t") for x in f]
        i = rows[0].index("평균 소요(초)")
        with open(self.x.stage("score/report.md"), encoding="utf-8") as f:
            report = f.read()
        for r in rows[1:-1]:
            self.assertIn(f" | {r[i]} |\n", [x + "\n" for x in report.split("\n") if x.startswith(f"| {r[0]} | ") and x.count("|") == 7][0])


if __name__ == "__main__":
    unittest.main()
