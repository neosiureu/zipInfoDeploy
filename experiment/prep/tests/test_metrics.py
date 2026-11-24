# -*- coding: utf-8 -*-
"""순서 8b: 15.9 지표·진단·비용과 하네스 summary.tsv 대 보기(순서 8b 설계 2판 10절).

- 실제 꼴의 기록(data/score_fx, 하네스가 지음)으로 끝까지 본다: 받은·자기 거절, 부당 거절의 분모, summary.tsv 대 보기,
  ⓪ 의도 절 표(지어낸 표), 같은 실수 표 얼리기와 재기.
- 지어낸 사건으로 한 곳씩 본다: hook 묶기, 첫 시도 CI, 근거 없는 claim, 부당 거절 ⓪②③④, 비용·시간의 셈, Java 읽기 흉내.
"""
import copy
import json
import os
import unittest
from decimal import Decimal

from prep import metrics as M
from prep import score
from prep.tests import test_score as TS

PRICES = M.load_prices(score.read_json(os.path.join(score.CODE_ROOT, "harness", "settings", "pilot_v0.json"), "설정", exact=True)["prices"],
                       "설정의 prices")
HAIKU = "claude-haiku-5-5"


def hook(point, part, tool, decision):
    return {"type": "hook", "at": "T", "point": point, "part": part, "tool": tool, "decision": decision, "line": ""}


def tool(name, inp=None, result="{}", is_error=False, code="", millis=Decimal("0.5")):
    return {"type": "tool", "at": "T", "name": name, "input": inp if inp is not None else {}, "result": result, "is_error": is_error,
            "error_code": code, "millis": millis}


def rejected(name, inp=None, line="막음"):
    return tool(name, inp, json.dumps({"오류": "REJECTED", "설명": line}, ensure_ascii=False), True, "REJECTED")


def declared(clauses):
    return rejected("declare_intents", {"intents": []}, "이 부분의 의도도 토큰으로 적어라: 「" + "」, 「".join(clauses) + "」")


def resp(i=100, r=80, w=20, o=42, millis=100, stop="tool_use", category="", cc=None):
    return {"type": "response", "turn": 1, "stop": stop, "refusal_category": category, "input_tokens": i, "cache_read_tokens": r,
            "cache_write_tokens": w, "output_tokens": o, "millis": millis, "cache_creation": cc}


def submit(inp, findings=()):
    return {"type": "submit", "at": "T", "input": inp, "findings": list(findings)}


def good_input():
    return copy.deepcopy(next(e for e in TS.good_t1() if e["type"] == "submit")["input"])


def accepted():
    return {"type": "accepted", "at": "T", "answer": "그린 답", "millis": Decimal("1.0")}


def end(outcome="SUBMITTED", turns=3):
    return {"type": "end", "at": "T", "outcome": outcome, "turns": turns, "answer": None}


def run_of(parts, qtype="T1"):
    return {"id": "c09-q001-r1", "variant": {"id": "c09", "parts": list(parts)}, "question": {"id": "q001", "key": "L01", "qtype": qtype}}


def env(**kw):
    e = {"forbidden": TS.FORBIDDEN, "cfg": TS.CFG, "current": {"L01": "00000"}, "areas": {"00000": 1}, "mistakes": None, "same_stage": False,
         "intents": None, "prices": PRICES}
    e.update(kw)
    return e


def metrics_of(events, parts, outcome="SUBMITTED", a=None, codes=(), **kw):
    rec = {"events": events, "outcome": outcome, "head": {"variant": "c09", "model": HAIKU, "ttl": "5m", "prices": PRICES}}
    return M.run_metrics(rec, run_of(parts), a or TS.answer(), {"codes": list(codes), "template": 1}, env(**kw))[0]


class TestGroup(unittest.TestCase):
    """3절: 데이터 도구는 tool 사건 바로 앞의 hook, 제출은 submit 사건 바로 뒤의 hook."""

    def test_two_calls_in_one_response_each_get_their_hooks(self):
        loop = [resp(), hook("START", "⓪", "trade_stats", "PASS"), hook("BEFORE_TOOL", "③", "trade_stats", "PASS"), tool("trade_stats"),
                hook("START", "⓪", "compare_prices", "PASS"), tool("compare_prices")]
        calls = M.group(loop, "r")
        self.assertEqual([(c["name"], len(c["hooks"]), c["received"]) for c in calls], [("trade_stats", 2, None), ("compare_prices", 1, None)])

    def test_received_is_the_first_reject_and_own_rejects_are_all(self):
        # 전부 판: ③·②가 함께 거절하면 받은 것은 ③(사슬 차례 ⓪→③→②→④), 제출에서 ⓪·④가 함께 거절하면 ⓪
        loop = [hook("START", "⓪", "trade_stats", "PASS"), hook("BEFORE_TOOL", "③", "trade_stats", "REJECT"),
                hook("BEFORE_TOOL", "②", "trade_stats", "REJECT"), rejected("trade_stats"),
                submit(good_input()), hook("START", "⓪", "submit_report", "REJECT"), hook("SUBMIT", "④", "submit_report", "REJECT"),
                rejected("submit_report")]
        a, b = M.group(loop, "r")
        self.assertEqual((a["received"], a["own"]), ("③", {"③", "②"}))
        self.assertEqual((b["kind"], b["received"], b["own"], b["accepted"]), ("submit", "⓪", {"⓪", "④"}, False))

    def test_tools_without_hooks_and_a_bad_input_submit(self):
        loop = [tool("read_card"), declared(["가"]), tool("submit_report", {"text": 1}, "{}", True, "BAD_INPUT"), submit(good_input()), accepted()]
        calls = M.group(loop, "r")
        self.assertEqual([(c["kind"], c["name"], c["accepted"]) for c in calls],
                         [("tool", "read_card", False), ("tool", "declare_intents", False), ("tool", "submit_report", False),
                          ("submit", "submit_report", True)])

    def test_notice_hooks_and_harness_events_are_left_out(self):
        # ⑤의 안내 hook은 첫 메시지 앞이고 거절하지 않는다. 이어 돌림·실패한 시도는 고리 사건이 아니라서 hook과 도구 사이에 와도 묶인다
        notice = {"type": "hook", "at": "T", "point": "NOTICE", "part": "⑤", "decision": "NOTICE", "line": "-"}
        ev = [notice, resp(), hook("BEFORE_TOOL", "③", "trade_stats", "REJECT"), {"type": "resume", "at": "T"}, {"type": "attempt", "at": "T"},
              rejected("trade_stats"), end("FAILED_NO_SUBMIT")]
        m = metrics_of(ev, ["③", "⑤"], outcome="FAILED_NO_SUBMIT")
        self.assertEqual((m["received"], m["rejects"]), ({"③": 1}, {"③": 1}))
        with self.assertRaises(score.Bind):
            M.group([hook("BEFORE_TOOL", "③", "trade_stats", "PASS"), notice, tool("trade_stats")], "r")

    def test_shapes_that_do_not_bind_stop(self):
        bad = {
            "남은 hook": [hook("START", "⓪", "trade_stats", "PASS")],
            "도구가 다름": [hook("START", "⓪", "trade_stats", "PASS"), tool("compare_prices")],
            "카드 도구 앞 hook": [hook("START", "⓪", "read_card", "PASS"), tool("read_card")],
            "거는 도구가 아닌데 도구 전": [hook("BEFORE_TOOL", "③", "compare_prices", "PASS"), tool("compare_prices")],
            "거절인데 결과는 통과": [hook("BEFORE_TOOL", "③", "trade_stats", "REJECT"), tool("trade_stats")],
            "hook 없이 REJECTED": [rejected("trade_stats")],
            "제출 뒤 응답": [submit(good_input()), resp()],
            "막혔는데 받아들여짐": [submit(good_input()), hook("SUBMIT", "④", "submit_report", "REJECT"), accepted()],
        }
        for why, loop in bad.items():
            with self.subTest(why=why):
                with self.assertRaises(score.Bind):
                    M.group(loop, "r")


class TestRunMetrics(unittest.TestCase):
    def test_first_ci(self):
        sub = good_input()
        cases = {
            "BAD_INPUT가 먼저": ([tool("submit_report", {}, "{}", True, "BAD_INPUT"), submit(sub), accepted()], "SUBMITTED", False),
            "findings 있음": ([submit(sub, ["value_claims[0] VALUE"]), accepted()], "SUBMITTED", False),
            # ⓪이 막은 첫 제출도 findings로 본다(기록 전용 검사기의 통과)
            "⓪이 막음": ([submit(sub), hook("START", "⓪", "submit_report", "REJECT"), rejected("submit_report"), submit(sub), accepted()],
                       "SUBMITTED", True),
            "제출 없음": ([resp(stop="end_turn")], "FAILED_NO_SUBMIT", None),
        }
        for why, (ev, outcome, want) in cases.items():
            with self.subTest(why=why):
                self.assertEqual(metrics_of(ev + [end(outcome)], ["⓪"], outcome=outcome)["first_ci"], want)

    def test_received_rejects_and_declare(self):
        ev = [hook("START", "⓪", "trade_stats", "REJECT"), rejected("trade_stats"), declared(["가"]), tool("declare_intents"),
              submit(good_input()), hook("START", "⓪", "submit_report", "PASS"), accepted(), end()]
        m = metrics_of(ev, ["⓪"])
        self.assertEqual((m["received"], m["rejects"]), ({"⓪": 1, "⓪선언": 1}, {"⓪": 1, "⓪선언": 1}))

    def test_ungrounded_claims(self):
        fs = ["stat_claims[0] VALUE", "stat_claims[0] BASIS", "value_claims[0] EVIDENCE_MISSING", "context_claims[0] EVIDENCE_MISSING",
              "context_claims[0] TEXT_FORM", "text NUMERAL", "slot SLOT_DUP", "text PLACEHOLDER", "value_claims[1] DIRECTION"]
        m = metrics_of([submit(good_input(), fs), accepted(), end()], [])
        self.assertEqual(m["ungrounded"], 3)  # claim 자리마다 한 번, 근거 쪽 코드만
        self.assertIsNone(metrics_of([resp(stop="end_turn"), end("FAILED_NO_SUBMIT")], [], outcome="FAILED_NO_SUBMIT")["ungrounded"])

    def test_unjust_reflex_counts_calls_with_the_current_code(self):
        cur = {"api": "apt_trade", "region_code": "00000", "deal": "매매"}
        old = dict(cur, region_code="00001")
        ev = [hook("BEFORE_TOOL", "②", "trade_stats", "REJECT"), rejected("trade_stats", old),
              hook("BEFORE_TOOL", "②", "trade_stats", "REJECT"), rejected("trade_stats", cur),
              hook("BEFORE_TOOL", "②", "trade_stats", "PASS"), tool("trade_stats", cur), end("FAILED_NO_SUBMIT")]
        self.assertEqual(metrics_of(ev, ["②"], outcome="FAILED_NO_SUBMIT")["unjust"], {"②": [1, 2]})

    def test_unjust_lock_counts_calls_like_the_answer_call_and_convert_area(self):
        call = {"api": "apt_trade", "region_code": "00000", "deal": "매매", "start_month": "202601", "end_month": "202601"}
        ev = [hook("BEFORE_TOOL", "③", "trade_stats", "REJECT"), rejected("trade_stats", call),
              hook("BEFORE_TOOL", "③", "trade_stats", "REJECT"), rejected("trade_stats", dict(call, deal="전세")),
              hook("BEFORE_TOOL", "③", "trade_stats", "PASS"), tool("trade_stats", call),
              hook("BEFORE_TOOL", "③", "convert_area", "REJECT"), rejected("convert_area"), end("FAILED_NO_SUBMIT")]
        self.assertEqual(metrics_of(ev, ["③"], outcome="FAILED_NO_SUBMIT")["unjust"], {"③": [1, 2], "③면적": [1, 1]})

    def blocked(self, fn, findings):
        good = TS.good_t1()
        bad = TS.edit(good, fn)
        sub = next(e for e in bad if e["type"] == "submit")
        sub["findings"] = list(findings)
        tools = [e for e in good if e["type"] == "tool"]
        return tools + [sub, hook("SUBMIT", "④", "submit_report", "REJECT"), rejected("submit_report")]

    def test_unjust_gate_counts_only_numeral_blocks(self):
        # 2판(상대 안): 값이 맞고 수사 뺀 규칙(rok_nn)을 지나는 제출만 멀쩡하다
        numeral = self.blocked(lambda i, s: i.update(text="같은 법정동 실거래 일곱 건의 기준값보다 {가} {가.방향}."), ["text NUMERAL"])
        missing = self.blocked(lambda i, s: i["value_claims"][0].update(evidence="C2.차이"), ["value_claims[0] EVIDENCE_MISSING"])
        wrong = self.blocked(lambda i, s: i["value_claims"][0].update(value=Decimal("12.6")), ["value_claims[0] VALUE"])
        good = [e for e in TS.good_t1() if e["type"] == "submit"] + [hook("SUBMIT", "④", "submit_report", "PASS"), accepted()]
        ev = numeral + missing[2:] + wrong[2:] + good + [end()]
        self.assertEqual(metrics_of(ev, ["④"])["unjust"], {"④": [1, 2]})

    def test_checker_only_findings_are_not_sound(self):
        # 상대 1판 검토 재현: 값이 맞고 검사기에만 걸린 여섯은 멀쩡하지 않다(1판 정의로는 멀쩡이었다)
        T = TS
        a2 = T.answer(qtype="T2", scope="단지", sample=0)
        cases = {
            "EVIDENCE_MISSING": (T.edit(T.good_t1(), lambda i, s: i["value_claims"][0].update(evidence="C2.차이")), None, ["value_claims[0] EVIDENCE_MISSING"]),
            "VALUE·BASIS·SAMPLE": ([T.ts("S1", "법정동"), T.ts("S2", "시군구"), T.cp("C1", "S1.기준")] + T.submitted(
                T.TEXT, [T.stat("나", 30000, "만원", "법정동", "S2.기준", 7)], [T.val("가", Decimal("12.5"), "%", "계산", "C1.차이")]), None,
                ["stat_claims[0] VALUE", "stat_claims[0] BASIS", "stat_claims[0] SAMPLE"]),
            "PLACEHOLDER": (T.edit(T.good_t1(), lambda i, s: i.update(text=T.TEXT[:-1] + " {다}.")), None, ["text PLACEHOLDER"]),
            "DIRECTION": (T.edit(T.good_t1(), lambda i, s: i.update(text="같은 법정동 실거래 기준값 {나} {나.방향}보다 {가} {가.방향}.")), None,
                          ["text DIRECTION"]),
            "SLOT_DUP": (T.edit(T.good_t1(), lambda i, s: i["stat_claims"][0].update(slot="가")), None, ["slot SLOT_DUP"]),
            "ZERO_INVALID": ([T.ts("S1", "단지")] + T.submitted("같은 단지 거래가 없다.", [T.stat("나", 0, "건", "단지", "S1.표본수", 0)]), a2,
                             ["stat_claims[0] ZERO_INVALID"]),
        }
        for why, (ev, a, fs) in cases.items():
            with self.subTest(why=why):
                sub = next(e for e in ev if e["type"] == "submit")
                i = ev.index(sub)
                sub = dict(sub, findings=fs)
                self.assertFalse(score.sound_submission(sub, ev[:i], a or T.answer(), T.FORBIDDEN, T.CFG))
                self.assertTrue(score.sound_submission(dict(sub, findings=[]), ev[:i], a or T.answer(), T.FORBIDDEN, T.CFG),
                                "findings만 빼면 값과 8a 규칙은 지난다(1판 정의로는 멀쩡)")

    def test_unjust_intent_uses_the_clause_table(self):
        table = {"q001": {"가 절": False, "나 절": True}}
        ev = [declared(["가 절"]), declared(["가 절", "나 절"]), end("FAILED_NO_SUBMIT")]
        self.assertEqual(metrics_of(ev, ["⓪"], outcome="FAILED_NO_SUBMIT", intents=table)["unjust"], {"⓪": [1, 2]})
        self.assertEqual(metrics_of(ev, ["⓪"], outcome="FAILED_NO_SUBMIT")["unjust"], {"⓪": None})  # 표가 없으면 "-"
        with self.assertRaises(score.Bind):
            metrics_of([declared(["다 절"]), end("FAILED_NO_SUBMIT")], ["⓪"], outcome="FAILED_NO_SUBMIT", intents=table)
        with self.assertRaises(score.Bind):  # ⓪ 거절 글의 꼴이 아니다
            metrics_of([rejected("declare_intents"), end("FAILED_NO_SUBMIT")], ["⓪"], outcome="FAILED_NO_SUBMIT", intents=table)

    def test_time_cost_and_diagnostics(self):
        ev = [resp(millis=100, cc={"ephemeral_5m_input_tokens": 5, "ephemeral_1h_input_tokens": 15}), tool("search_address", {}, "{}", True, ""),
              {"type": "attempt", "at": "T"}, {"type": "resume", "at": "T"},
              resp(i=150_000, r=0, w=0, millis=200, stop="refusal"), {"type": "nudge", "at": "T", "text": "재촉"}, end("FAILED_REFUSAL", 2)]
        m = metrics_of(ev, [], outcome="FAILED_REFUSAL")
        self.assertEqual((m["millis"], m["turns"], m["nudges"], m["refusal"], m["tool_errors"]), ("300.5", 2, 1, "(없음)", {"(없음)": 1}))
        # 첫 요청: 5분·1시간 나눔. 둘째: 15만 토큰이라 둘째 단(10만 넘음)
        self.assertEqual(m["cost"], [format(Decimal("0.000035425") + Decimal("0.075105"), "f"), format(Decimal("0.000041") + Decimal("0.075105"), "f")])

    def test_zero_calls(self):
        z = lambda sid, code, n: tool("trade_stats", {"region_code": code}, json.dumps({"id": sid, f"{sid}.표본수": {"값": n, "단위": "건"}}))
        ev = [z("S1", "00000", 0), z("S2", "00001", 0), z("S3", "00000", 4), end("FAILED_NO_SUBMIT")]
        self.assertEqual(metrics_of(ev, [], outcome="FAILED_NO_SUBMIT")["zero_calls"], [2, 1])

    def test_repeat_uses_the_frozen_table(self):
        table = {"codes": {"DIFF_VALUE": 3}, "code_areas": {"DIFF_VALUE|2": 3}}
        ev = [end("FAILED_NO_SUBMIT")]
        m = metrics_of(ev, [], outcome="FAILED_NO_SUBMIT", codes=["DIFF_VALUE", "JUDGE_MISSING"], mistakes=table, areas={"00000": 2})
        self.assertEqual((m["repeat"], m["repeat_area"]), (True, True))
        m = metrics_of(ev, [], outcome="FAILED_NO_SUBMIT", codes=["DIFF_VALUE"], mistakes=table, areas={"00000": 1})
        self.assertEqual((m["repeat"], m["repeat_area"]), (True, False))
        m = metrics_of(ev, [], outcome="FAILED_NO_SUBMIT", codes=["JUDGE_MISSING"], mistakes={"codes": {"JUDGE_MISSING": 1}, "code_areas": {}})
        self.assertEqual(m["repeat"], False)  # 판정 없음은 유형이 아니다
        m = metrics_of(ev, [], outcome="FAILED_NO_SUBMIT", codes=["DIFF_VALUE"], mistakes=table, same_stage=True)
        self.assertEqual((m["repeat"], m["repeat_area"]), (None, None))


class TestPricesAndJava(unittest.TestCase):
    """하네스 Prices·Summary와 같은 셈."""

    def test_tier_by_prompt_tokens(self):
        t1 = M.costs(PRICES, HAIKU, (100_000, 0, 0, 0, 0))
        t2 = M.costs(PRICES, HAIKU, (100_001, 0, 0, 0, 0))
        self.assertEqual((t1[0], t2[0]), (Decimal("0.01"), Decimal("0.0500005")))
        with self.assertRaises(score.Bad):
            M.costs(PRICES, "claude-none", (1, 0, 0, 0, 0))

    def test_cache_write_split(self):
        e = resp(w=20, cc={"ephemeral_5m_input_tokens": 5, "ephemeral_1h_input_tokens": 15})
        self.assertEqual(M.use(e, "5m"), (100, 80, 5, 15, 42))
        e["cache_creation"] = {"ephemeral_5m_input_tokens": 5, "ephemeral_1h_input_tokens": 5}
        self.assertEqual(M.use(e, "5m"), (100, 80, 20, 0, 42))
        e["cache_creation"] = None
        self.assertEqual(M.use(e, "1h"), (100, 80, 0, 20, 42))

    def test_rounding_like_bigdecimal_value_of(self):
        self.assertEqual(M.half_up(61 / 20, 1), "3.1")  # Decimal(float)이면 3.0이다
        self.assertEqual(str(Decimal(61 / 20).quantize(Decimal("0.1"), rounding="ROUND_HALF_UP")), "3.0")
        self.assertEqual((M.half_up(0.0, 1), M.half_up(1e16, 1), M.money(Decimal("0.00005"))), ("0.0", "10000000000000000.0", "0.0001"))

    def test_java_reading(self):
        self.assertEqual([M.as_text(M.get({"a": None}, k)) for k in ("a", "b")], ["null", ""])
        self.assertEqual((M.as_long(Decimal("2.9")), M.as_long("x"), M.as_double(Decimal("0.123"))), (2, 0, 0.123))
        self.assertEqual(M.join({"b": 1, "(없음)": 2, "A": 3}), "(없음)=2;A=3;b=1")
        self.assertEqual(M.java_normalize("  가　 나 \r\n\n 다  "), "가 나\n다")

    def test_prices_compare_by_value(self):
        p = score.read_json(os.path.join(score.CODE_ROOT, "harness", "settings", "pilot_v0.json"), "설정", exact=True)["prices"]
        q = copy.deepcopy(p)
        q["models"][HAIKU][0]["input"] = Decimal("0.100")
        self.assertEqual(M.load_prices(q, "x"), PRICES)
        q["models"][HAIKU][0]["input"] = Decimal("0.2")
        self.assertNotEqual(M.load_prices(q, "x"), PRICES)
        q["models"][HAIKU][1]["up_to_prompt_tokens"] = 5
        with self.assertRaises(score.Bad):
            M.load_prices(q, "x")


class TestFixture(unittest.TestCase):
    """하네스가 지은 기록으로 끝까지."""

    def setUp(self):
        self.x = TS.Exp()

    def tearDown(self):
        self.x.close()

    def metrics(self):
        with open(self.x.stage("score/metrics.tsv"), encoding="utf-8") as f:
            lines = [x.rstrip("\n").split("\t") for x in f]
        self.assertEqual(lines[0], list(M.METRICS_HEAD))
        return {(a, b, c): (d, e) for a, b, c, d, e in lines[1:]}

    def test_rejections_and_unjust_denominators(self):
        code, out = self.x.run()
        self.assertEqual(code, 0, out)
        self.assertIn("지표: 하네스 summary.tsv와 같음(판 5개), 15.9·비용·진단 106줄을 metrics.tsv에 썼다", out)
        self.assertIn('⓪ 부당 거절: 의도 절 표(questions/intent_clauses_v0.json)나 틀 문장 파일이 없어 "-"로 두었다', out)
        rows = {r["run_id"]: r for r in self.x.results()}
        want = {
            "c04-q001-r1": (True, {"⓪": 1, "⓪선언": 1}, {"⓪": None}),     # 의도 적기 전 trade_stats, 덮지 못한 선언
            "c04-q002-r1": (None, {"⓪": 1}, {"⓪": None}),                # search_address도 ⓪이 막는다, 재촉 뒤 제출 없음
            "c05-q001-r1": (True, {"②": 1}, {"②": [0, 1]}),             # 옛 코드 거절은 분모 밖, 현재 코드 부름 하나
            "c05-q002-r1": (False, {}, {"②": [0, 0]}),                   # BAD_INPUT이 먼저
            "c06-q001-r1": (True, {"③": 2}, {"③": [1, 2], "③면적": [1, 1]}),
            "c06-q002-r1": (False, {}, {"③": [0, 0], "③면적": [0, 0]}),  # 옛 코드 부름은 정답 call과 달라 분모 밖
            "c07-q001-r1": (False, {"④": 1}, {"④": [0, 1]}),             # 값이 틀린 첫 제출은 멀쩡하지 않다
            "c07-q002-r1": (True, {}, {"④": [0, 0]}),                    # 받아들인 제출도 T2 넓힘이라 멀쩡하지 않다
        }
        for rid, (ci, rec, unj) in want.items():
            with self.subTest(rid=rid):
                self.assertEqual((rows[rid]["first_ci"], rows[rid]["received"], rows[rid]["unjust"]), (ci, rec, unj))
        r = rows["c06-q002-r1"]
        self.assertEqual((r["ungrounded"], r["zero_calls"], r["tags"]), (1, [1, 1], ["FALSE_NO_TRADE"]))
        self.assertEqual((rows["c05-q001-r1"]["tool_errors"], rows["c04-q002-r1"]["nudges"], rows["c01-q002-r1"]["refusal"]),
                         ({"REJECTED": 1, "SNAPSHOT_MISS": 1}, 1, "cyber"))
        m = self.metrics()
        self.assertEqual(m[("c04", "15.9", "거절 뒤 자력 완료 ⓪")], ("1", "2"))
        self.assertEqual(m[("c06", "15.9", "부당 거절 ③(면적)")], ("1", "1"))
        self.assertEqual(m[("c06", "진단", "받은 답 findings ZERO_INVALID T2")], ("1", "1"))
        self.assertEqual(m[("c06", "비용", "캐시 있음($)")], ("0.075380525", "2"))
        self.assertEqual(m[("c07", "비용", "완전 정답 한 건당 캐시 있음($)")], ("0.0002744", "0"))
        self.assertEqual(m[("c05", "진단", "받은 거절 ②")], ("1", "-"))
        self.assertNotIn(("c07", "진단", "받은 답 findings ZERO_INVALID T2"), m)  # ④ 판은 받은 답 findings를 내지 않는다

    def test_the_harness_summary_must_match(self):
        path = self.x.stage("summary.tsv")
        with open(path, encoding="utf-8") as f:
            lines = f.read().split("\n")
        c = lines[3].split("\t")
        self.assertEqual(c[0], "c05")
        i = list(M.SUMMARY_HEAD).index("평균 소요(초)")
        c[i] = "9.9"
        lines[3] = "\t".join(c)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(lines))
        code, out = self.x.run()
        self.assertEqual(code, 3)
        self.assertIn("하네스 summary.tsv가 같은 셈과 다르다(판 c05, 칸 평균 소요(초))", out[0])
        os.remove(path)
        code, out = self.x.run()
        self.assertEqual(code, 3)
        self.assertIn("하네스 summary.tsv가 없다", out[0])
        self.assertFalse(os.path.exists(self.x.stage("score")))

    def with_templates(self, table):
        """틀 번호와 이름을 지어 준다(시험 자료의 문항은 틀 문장으로 짓지 않았다). 의도 절 표는 그 틀에 맞춰 지은 것."""
        path = self.x.p("questions/intent_clauses_v0.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump({"version": "v0", "templates": table}, f, ensure_ascii=False)
        dummy = self.x.p("questions/questions_fx.json")
        real = score.template_numbers
        score.template_numbers = lambda *a: ({"q001": 1, "q002": 1, "q900": 1}, dummy, {"L01": "가", "L02": "가", "L03": "가"},
                                             {t: 1 for t in score.TYPES})
        try:
            return self.x.run()
        finally:
            score.template_numbers = real

    def test_intent_unjust_with_a_clause_table(self):
        # c04-q001의 덮지 못한 선언은 「시험 글 가」 한 절이다(하네스 IntentTools가 쓴 그대로)
        base = {t: [[{"clause": "그 밖", "intent": True}]] for t in score.TYPES}
        code, out = self.with_templates(dict(base, T1=[[{"clause": "시험 글 가", "intent": False}]]))
        self.assertEqual(code, 0, out)
        rows = {r["run_id"]: r for r in self.x.results()}
        self.assertEqual((rows["c04-q001-r1"]["unjust"], rows["c04-q001-r1"]["template"]), ({"⓪": [1, 1]}, 1))
        m = self.metrics()
        self.assertEqual((m[("c04", "15.9", "부당 거절 ⓪")], m[("c04", "15.9", "부당 거절 ⓪ T1-1")], m[("c04", "15.9", "부당 거절 ⓪ T2-1")]),
                         (("1", "1"), ("1", "1"), ("0", "0")))
        self.assertFalse(any("⓪ 부당 거절:" in x for x in out))
        code, out = self.with_templates(dict(base, T1=[[{"clause": "시험 글 가", "intent": True}]]))
        self.assertEqual(code, 0, out)
        self.assertEqual(self.metrics()[("c04", "15.9", "부당 거절 ⓪")], ("0", "1"))
        code, out = self.with_templates(dict(base, T1=[[{"clause": "다른 글", "intent": False}]]))
        self.assertEqual(code, 3)
        self.assertIn("⓪ 거절의 절이 의도 절 표(T1-1)에 없다(절 1개)", out[0])
        code, out = self.with_templates(dict(base, T1=[]))
        self.assertEqual(code, 2)
        self.assertIn("의도 절 표의 T1 틀 수가 틀 문장 파일과 다르다", out[0])

    def test_freeze_and_use_the_same_mistakes_table(self):
        out = []
        rel = "harness/frozen/mistakes_v0.json"
        self.assertEqual(score.mistakes(self.x.settings, rel, exp=self.x.root, out=out.append), 0, out)
        self.assertTrue(out[0].startswith("같은 실수 표: 단계 pilot, 실행 10개, 코드 "), out[0])
        with open(self.x.p(rel), encoding="utf-8") as f:
            text = f.read()
        doc = json.loads(text)
        self.assertEqual(sorted(doc), sorted(M.MISTAKE_FIELDS))
        self.assertEqual((doc["stage"], doc["runs"], doc["judge"][:12]), ("pilot", 10, "CEA01B2E0224"))
        self.assertEqual(doc["codes"], {"BASIS_MISSING": 1, "CHECKER:ZERO_INVALID": 1, "DIFF_MISSING": 1, "FAILED_MAX_TOKENS": 1,
                                        "FAILED_NO_SUBMIT": 1, "FAILED_REFUSAL": 1, "JUDGE_NOT_ANSWERED": 1, "T2_WIDENED": 1})
        self.assertEqual(set(doc["code_areas"]), {k + "|1" for k in doc["codes"]})
        self.assertNotIn("41591", text)  # 시군구는 번호로만
        self.assertTrue(text.endswith("}\n") and "\r" not in text)
        out = []
        self.assertEqual(score.mistakes(self.x.settings, rel, exp=self.x.root, out=out.append), 2)
        self.assertIn("얼릴 파일이 이미 있다", out[0])
        # 같은 단계를 그 표로 매기면 "-"(제 표로 저를 재지 않는다)
        with open(score.CONFIG, encoding="utf-8") as f:
            cfg = json.load(f)
        cfg["mistakes"] = rel
        path = self.x.p("prep/score_m.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(cfg, f, ensure_ascii=False)
        code, out = self.x.run(config=path)
        self.assertEqual(code, 0, out)
        self.assertIn("같은 실수 재발: 표가 이 단계에서 얼린 것이라 재지 않았다", out)
        self.assertEqual(self.metrics()[("c01", "15.9", "같은 실수 재발")], ("-", "-"))
        # 다른 단계의 표면 잰다(여기서는 단계 이름만 바꾼 표)
        doc["stage"] = "pilot0"
        with open(self.x.p(rel), "w", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps(doc, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
        code, out = self.x.run(config=path)
        self.assertEqual(code, 0, out)
        m = self.metrics()
        self.assertEqual((m[("c01", "15.9", "같은 실수 재발")], m[("c07", "15.9", "같은 실수 재발(코드, 시군구)")]), (("1", "2"), ("2", "2")))
        self.assertEqual(m[("c06", "진단", "같은 실수 코드 CHECKER:ZERO_INVALID")], ("1", "2"))
        os.remove(self.x.p(rel))
        code, out = self.x.run(config=path)
        self.assertEqual(code, 2)
        self.assertIn("채점 설정의 같은 실수 표가 없다", out[0])


if __name__ == "__main__":
    unittest.main()
