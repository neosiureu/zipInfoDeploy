# -*- coding: utf-8 -*-
"""순서 8b: 15.9 지표·진단·비용과 하네스 summary.tsv 대 보기(순서 8b 설계 2판 3~7·9절). score가 부른다.

- 3절: 기록의 고리 사건(하네스 사건을 뺀 것)에서 부름과 hook을 묶는다. 데이터 도구는 tool 사건 바로 앞의 hook들,
  제출은 submit 사건 바로 뒤의 hook들이다. 받은 거절은 묶음에서 처음 REJECT인 부품(모델에 간 것), 자기 거절은 부품마다의 REJECT다.
  ⓪의 declare_intents 거절(도구 결과 REJECTED)도 받은 거절·자기 거절이고 "⓪선언"으로 따로 센다.
- 4·5·6절: 실행마다 지표 칸을 내고(runs.jsonl), 판마다 분자·분모로 모은다(metrics.tsv). 비율은 나누지 않는다(8c의 report.md가 나눈다).
- 7절: 하네스 Summary와 같은 셈으로 summary.tsv를 다시 지어 글자까지 대 본다. 시간은 double을 사건 차례로 실행마다 더한 뒤
  끝난 실행만 판 합에 더하고(파일 이름 차례), 반올림은 BigDecimal.valueOf → HALF_UP을 repr → Decimal로 따라 한다. 비용은 Decimal로 정확히 센다.
- 글은 쓰지 않는다. ⓪의 절 글은 의도 절 표와 대 보기에만 쓰고 수로 남긴다.
"""
import json
import os
import re
import unicodedata
from collections import Counter
from decimal import Decimal, Inexact, ROUND_HALF_UP, localcontext

from . import score as S

PART_ORDER = ("⓪", "③", "②", "④", "⑤")           # 사슬 차례(Part)
REJECTING = ("⓪", "③", "②", "④")                  # 거절하는 부품(⑤는 안내만)
DECLARE = "⓪선언"                                   # ⓪의 declare_intents 거절
DATA_TOOLS = frozenset(("search_address", "trade_stats", "compare_prices", "convert_rent", "convert_area"))
GUARDED_TOOLS = frozenset(("trade_stats", "convert_area"))
HARNESS = frozenset(("run", "resume", "attempt", "pause", "end"))
EVIDENCE_CODES = frozenset(("EVIDENCE_MISSING", "NOT_STATS", "ZERO_EVIDENCE", "ZERO_INVALID", "NO_VALUE", "VALUE", "BASIS", "PERIOD",
                            "SAMPLE", "PROVISIONAL"))
CLAIM_WHERE = re.compile(r"(?:stat_claims|value_claims|context_claims)\[[0-9]+\]")
OUTCOMES = ("SUBMITTED", "FAILED_NO_SUBMIT", "FAILED_MAX_TOKENS", "FAILED_CONTEXT_WINDOW", "FAILED_REFUSAL", "FAILED_TURN_LIMIT",
            "STOP_REASONING_EXTRACTION", "STOP_OTHER_STOP_REASON")
SUMMARY_HEAD = ("판", "이름", "계획", "끝", "남음") + OUTCOMES + (
    "refusal", "도구 오류", "요청", "최대 프롬프트", "10만 넘음", "10만 넘음(%)", "input", "cache_read", "cache_write", "output",
    "비용 캐시 있음($)", "비용 캐시 없음($)", "평균 소요(초)", "평균 턴", "실패한 시도", "멈춤")
METRICS_HEAD = ("판", "갈래", "이름", "분자", "분모")
INTENT_HEAD = "이 부분의 의도도 토큰으로 적어라: 「"
INTENT_SEP = "」, 「"
LINE = 100_000
MILLION = Decimal(1_000_000)
TIER_FIELDS = ("up_to_prompt_tokens", "input", "output", "cache_read", "cache_write_5m", "cache_write_1h")
MISTAKE_FIELDS = ("version", "stage", "score", "judge", "runs", "codes", "code_areas")

# IntentCheck.normalize: \R로 줄을 나누고, 줄마다 \s+(UNICODE_CHARACTER_CLASS, White_Space)를 빈칸 하나로, String.strip으로 앞뒤를 뗀다
JAVA_LINES = re.compile("\r\n|[\n\x0b\x0c\r\x85  ]")
JAVA_WS = re.compile("[\t\n\x0b\x0c\r \x85\xa0  -     　]+")

_MISSING = object()


# ---------------------------------------------------------------- Java(Jackson) 읽기 흉내

def get(e, k):
    return e.get(k, _MISSING) if isinstance(e, dict) else _MISSING


def as_text(v):
    """JsonNode.asText(): 없는 칸은 빈 글, null은 "null"."""
    if v is _MISSING:
        return ""
    if v is None:
        return "null"
    if isinstance(v, bool):
        return "true" if v else "false"
    return v if isinstance(v, str) else str(v)


def as_long(v):
    """JsonNode.asLong(0): 수는 0 쪽으로 자르고, 수가 아니면 0."""
    if isinstance(v, bool):
        return int(v)
    if isinstance(v, (int, Decimal)):
        return int(v)
    if isinstance(v, str):
        try:
            return int(v.strip())
        except ValueError:
            return 0
    return 0


def as_double(v):
    """JsonNode.asDouble(): 기록은 Decimal로 읽었으니 float로 바꾼다(가장 가까운 double, BigDecimal.doubleValue와 같다)."""
    if isinstance(v, bool):
        return 1.0 if v else 0.0
    if isinstance(v, (int, Decimal)):
        return float(v)
    if isinstance(v, str):
        try:
            return float(v.strip())
        except ValueError:
            return 0.0
    return 0.0


def as_bool(v):
    if isinstance(v, bool):
        return v
    if isinstance(v, int):
        return v != 0
    return isinstance(v, str) and v.strip() == "true"


def half_up(x, places):
    """BigDecimal.valueOf(x).setScale(places, HALF_UP).toPlainString(). valueOf는 Double.toString(가장 짧은 꼴, JDK 21)이라 repr와 값이 같다."""
    with localcontext() as c:
        c.prec = 100
        q = Decimal(repr(x)).quantize(Decimal(1).scaleb(-places), rounding=ROUND_HALF_UP)
    return format(q.copy_abs() if q == 0 else q, "f")


def money(d):
    """BigDecimal.setScale(4, HALF_UP).toPlainString()."""
    with localcontext() as c:
        c.prec = 100
        q = d.quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP)
    return format(q, "f")


def utf16(s):
    """Java String.compareTo 차례(UTF-16 낱자 차례). TreeMap의 열쇠 차례다."""
    return s.encode("utf-16-be")


def join(m):
    """Summary.join: 열쇠=수를 열쇠 차례로 ;로 잇는다."""
    return ";".join(f"{k}={m[k]}" for k in sorted(m, key=utf16))


def java_normalize(text):
    out = []
    for line in JAVA_LINES.split(unicodedata.normalize("NFC", text)):
        x = S.jstrip(JAVA_WS.sub(" ", line))
        if x:
            out.append(x)
    return "\n".join(out)


def num_text(v):
    if v is None:
        return "-"
    if isinstance(v, float):
        return repr(v)
    if isinstance(v, Decimal):
        return format(v, "f")
    return str(v)


# ---------------------------------------------------------------- 단가표(하네스 Prices)

def load_prices(p, what):
    """Prices.parse와 같은 꼴만 받는다. 값은 Decimal, 상한은 정수나 None."""
    bad = S.Bad(f"{what}의 꼴이 아니다(하네스 Prices와 같은 칸)")
    if not isinstance(p, dict) or sorted(p) != ["batch_factor", "date", "models"] or not isinstance(p["date"], str) \
            or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", p["date"]) or not isinstance(p["models"], dict) or not p["models"]:
        raise bad
    bf = p["batch_factor"]
    if isinstance(bf, bool) or not isinstance(bf, (int, Decimal)) or not 0 < bf <= 1:
        raise bad
    models = {}
    for m, tiers in p["models"].items():
        if not isinstance(tiers, list) or not tiers:
            raise bad
        ts = []
        for t in tiers:
            if not isinstance(t, dict) or sorted(t) != sorted(TIER_FIELDS):
                raise bad
            up = t["up_to_prompt_tokens"]
            if up is not None and (S.integer(up) is None or up <= 0):
                raise bad
            row = {"up": up}
            for k in TIER_FIELDS[1:]:
                v = t[k]
                if isinstance(v, bool) or not isinstance(v, (int, Decimal)) or v <= 0:
                    raise bad
                row[k] = Decimal(v)
            ts.append(row)
        for i, t in enumerate(ts):
            last = i == len(ts) - 1
            if last != (t["up"] is None) or (0 < i and not last and t["up"] <= ts[i - 1]["up"]):
                raise bad
        models[m] = ts
    return {"date": p["date"], "batch_factor": Decimal(bf), "models": models}


def use(e, ttl):
    """Prices.use: 토큰 넷과 캐시 쓰기의 5분·1시간 나눔(원문 usage.cache_creation, 없거나 합이 안 맞으면 ttl 하나)."""
    i, r, w, o = (as_long(get(e, k)) for k in ("input_tokens", "cache_read_tokens", "cache_write_tokens", "output_tokens"))
    cc = e.get("cache_creation")
    if isinstance(cc, dict):
        w5, w1 = as_long(cc.get("ephemeral_5m_input_tokens")), as_long(cc.get("ephemeral_1h_input_tokens"))
        if w5 + w1 == w:
            return i, r, w5, w1, o
    return (i, r, 0, w, o) if ttl == "1h" else (i, r, w, 0, o)


def costs(prices, model, u):
    """요청 하나의 비용(캐시 있음, 캐시 없음). 단은 그 요청의 프롬프트 토큰으로 고른다(Prices.tier)."""
    tiers = prices["models"].get(model)
    if tiers is None:
        raise S.Bad(f"단가표에 없는 모델이다: {model}")
    i, r, w5, w1, o = u
    prompt = i + r + w5 + w1
    t = next(t for t in tiers if t["up"] is None or prompt <= t["up"])
    try:
        with localcontext() as c:
            c.prec = 60
            c.traps[Inexact] = True
            withc = (t["input"] * i + t["cache_read"] * r + t["cache_write_5m"] * w5 + t["cache_write_1h"] * w1 + t["output"] * o) / MILLION
            without = (t["input"] * prompt + t["output"] * o) / MILLION
    except Inexact:
        raise S.Bad("비용을 정확히 셈하지 못했다(정밀도 60)") from None
    return withc, without


def add(a, b):
    with localcontext() as c:
        c.prec = 60
        c.traps[Inexact] = True
        return a + b


# ---------------------------------------------------------------- 3절: 부름과 hook 묶기

def group(loop, rid):
    """고리 사건을 부름으로 묶는다. 부름: kind(tool·submit), at(고리 사건 차례), name, input, hooks, tool(도구 사건), submit, accepted."""
    bad = lambda why: S.Bind(f"{rid}.jsonl: hook 사건을 부름과 묶지 못했다({why})")
    out, pending, sub = [], [], None
    for i, e in enumerate(loop):
        t = e.get("type")
        if t == "hook":
            point = e.get("point")
            if point == "NOTICE":
                if pending or sub is not None:
                    raise bad("안내 hook이 부름 사이에 있다")
                continue
            if sub is not None:
                if point not in ("START", "SUBMIT") or e.get("tool") != "submit_report":
                    raise bad("제출 뒤 hook의 자리·도구가 다르다")
                sub["hooks"].append(e)
            else:
                if point not in ("START", "BEFORE_TOOL"):
                    raise bad("도구 앞 hook의 자리가 다르다")
                pending.append(e)
        elif t == "submit":
            if pending or sub is not None:
                raise bad("제출 앞에 묶이지 않은 hook이 있다")
            sub = {"kind": "submit", "at": i, "name": "submit_report", "input": e.get("input"), "hooks": [], "tool": None, "submit": e,
                   "accepted": False}
        elif t == "tool":
            name = e.get("name")
            if sub is not None:
                if name != "submit_report" or e.get("error_code") != "REJECTED":
                    raise bad("막힌 제출 뒤의 도구 사건이 아니다")
                sub["tool"] = e
                out.append(sub)
                sub = None
                continue
            if pending and (name not in DATA_TOOLS or any(h.get("tool") != name for h in pending)):
                raise bad("hook의 도구가 tool 사건과 다르다")
            if any(h.get("point") == "BEFORE_TOOL" for h in pending) and name not in GUARDED_TOOLS:
                raise bad("거는 도구가 아닌데 도구 전 hook이 있다")
            out.append({"kind": "tool", "at": i, "name": name, "input": e.get("input"), "hooks": pending, "tool": e, "submit": None,
                        "accepted": False})
            pending = []
        elif t == "accepted":
            if sub is None or pending:
                raise bad("받아들여진 답 앞에 제출이 없다")
            sub["accepted"] = True
            out.append(sub)
            sub = None
        elif pending or sub is not None:
            raise bad(f"{t} 사건이 hook과 그 부름 사이에 있다")
    if pending or sub is not None:
        raise bad("끝에 묶이지 않은 hook이나 제출이 있다")
    for c in out:
        rej = [h.get("part") for h in c["hooks"] if h.get("decision") == "REJECT"]
        c["received"] = rej[0] if rej else None
        c["own"] = set(rej)
        if c["kind"] == "submit":
            ok = (c["received"] is None) == c["accepted"]
        else:
            got = as_bool(get(c["tool"], "is_error")) and c["tool"].get("error_code") == "REJECTED"
            ok = got == (c["received"] is not None) or (c["received"] is None and c["name"] == "declare_intents")
        if not ok:
            raise bad("장치 판정과 도구 결과가 맞지 않다")
    return out


def declare_rejected(c):
    return c["kind"] == "tool" and c["name"] == "declare_intents" and as_bool(get(c["tool"], "is_error")) \
        and c["tool"].get("error_code") == "REJECTED"


def uncovered(c, rid):
    """⓪ 거절 결과 설명의 「…」 목록(IntentTools: "이 부분의 의도도 토큰으로 적어라: 「…」, 「…」")."""
    try:
        d = json.loads(c["tool"].get("result"))
    except (TypeError, ValueError):
        d = None
    line = d.get("설명") if isinstance(d, dict) else None
    if not isinstance(line, str) or d.get("오류") != "REJECTED" or not line.startswith(INTENT_HEAD) or not line.endswith("」") \
            or len(line) <= len(INTENT_HEAD) + 1:
        raise S.Bind(f"{rid}.jsonl: declare_intents 거절 글이 ⓪의 꼴이 아니다")
    return line[len(INTENT_HEAD):-1].split(INTENT_SEP)


# ---------------------------------------------------------------- 표(⓪ 의도 절, 같은 실수)

def load_intent_clauses(path, counts):
    """⓪ 의도 절 표(9절). counts: 유형마다 틀 문장 수(틀 문장 파일)."""
    d = S.read_json(path, "의도 절 표")
    bad = S.Bad("의도 절 표의 꼴이 아니다({version, templates: {유형: [[{clause, intent}…] 틀마다]}})")
    if not isinstance(d, dict) or sorted(d) != ["templates", "version"] or not isinstance(d["templates"], dict) \
            or sorted(d["templates"]) != sorted(S.TYPES):
        raise bad
    for t in S.TYPES:
        tpls = d["templates"][t]
        if not isinstance(tpls, list) or len(tpls) != counts[t]:
            raise S.Bad(f"의도 절 표의 {t} 틀 수가 틀 문장 파일과 다르다")
        for tpl in tpls:
            if not isinstance(tpl, list) or not tpl or not all(isinstance(c, dict) and sorted(c) == ["clause", "intent"] and isinstance(
                    c["clause"], str) and c["clause"] and isinstance(c["intent"], bool) for c in tpl):
                raise bad
    return d


def intent_tables(doc, questions, tnums, names):
    """문항마다 {정리한 절 글: 의도인가}. 절의 {이름}을 매물 이름으로 바꾸고 IntentCheck.normalize처럼 맞춘다."""
    out = {}
    for q in questions:
        t = tnums[q["id"]]
        m = {}
        for c in doc["templates"][q["qtype"]][t - 1]:
            k = java_normalize(c["clause"].replace("{이름}", names[q["key"]]))
            if m.get(k, c["intent"]) != c["intent"]:
                raise S.Bind(f"의도 절 표의 {q['qtype']}-{t}에 같은 절이 의도·의도 아님으로 둘이다")
            m[k] = c["intent"]
        out[q["id"]] = m
    return out


def load_mistakes(path):
    d = S.read_json(path, "같은 실수 표")
    if not isinstance(d, dict) or sorted(d) != sorted(MISTAKE_FIELDS) or d["version"] != "v0" or not isinstance(d["stage"], str) \
            or not isinstance(d["codes"], dict) or not isinstance(d["code_areas"], dict) \
            or not all(isinstance(k, str) and S.integer(v) is not None and v > 0 for k, v in list(d["codes"].items()) + list(d["code_areas"].items())) \
            or not all(re.fullmatch(r"[^|]+\|[1-9][0-9]*", k) for k in d["code_areas"]):
        raise S.Bad("같은 실수 표의 꼴이 아니다")
    return d


def current_codes(metas):
    """매물의 현재 시군구 코드: 매물 파일 complex_id의 둘째 칸(ListingMeta.area)."""
    out = {}
    for k, m in metas.items():
        f = m["complex_id"].split("|")
        if len(f) < 2 or not f[1]:
            raise S.Bad("매물 파일의 complex_id에 시군구 칸이 없다")
        out[k] = f[1]
    return out


def area_numbers(cur):
    """시군구 번호: 매물 파일의 현재 시군구 코드를 오름차순으로 세운 차례(1부터). 코드 값은 쓰지 않는다."""
    return {c: i + 1 for i, c in enumerate(sorted(set(cur.values())))}


def freeze_mistakes(ctx, rows):
    """4.3: 연습 단계의 실패 코드로 같은 실수 표를 짓는다. 유형은 codes(판정 없음은 뺌), 보조는 (코드, 시군구 번호)."""
    cur = current_codes(ctx["metas"])
    nums = area_numbers(cur)
    codes, areas = Counter(), Counter()
    for r, x in zip(ctx["plan"], rows):
        n = nums[cur[r["question"]["key"]]]
        for c in sorted(set(x["codes"]) - {"JUDGE_MISSING"}):
            codes[c] += 1
            areas[f"{c}|{n}"] += 1
    return {"version": "v0", "stage": ctx["s"]["stage"], "score": ctx["print"], "judge": ctx["judge_print"], "runs": len(ctx["plan"]),
            "codes": dict(sorted(codes.items())), "code_areas": dict(sorted(areas.items()))}


# ---------------------------------------------------------------- 실행 하나(4·5·6절)

def zero_counts(loop, cur):
    """trade_stats 성공 가운데 표본 0(결과의 "S?.표본수".값), 그 가운데 region_code가 매물의 현재 코드가 아닌 것."""
    ok = zero = other = 0
    for e in loop:
        if e.get("type") != "tool" or e.get("name") != "trade_stats" or as_bool(get(e, "is_error")):
            continue
        try:
            res = json.loads(e.get("result"))
        except (TypeError, ValueError):
            continue
        rid = res.get("id") if isinstance(res, dict) else None
        n = res.get(f"{rid}.표본수") if isinstance(rid, str) else None
        if not isinstance(n, dict) or S.integer(n.get("값")) is None:
            continue
        ok += 1
        if n["값"] == 0:
            zero += 1
            inp = e.get("input") if isinstance(e.get("input"), dict) else {}
            other += inp.get("region_code") != cur
    return ok, zero, other


def run_metrics(rec, r, a, x, env):
    """실행 하나의 8b 칸(runs.jsonl)과 판마다 모을 값."""
    rid, parts, q = r["id"], r["variant"]["parts"], r["question"]
    events = rec["events"]
    loop = [e for e in events if e.get("type") not in HARNESS]
    calls = group(loop, rid)
    received, rejects = Counter(), Counter()
    declared = []
    for c in calls:
        if c["received"]:
            received[c["received"]] += 1
        for p in c["own"]:
            rejects[p] += 1
        if declare_rejected(c):
            received[DECLARE] += 1
            rejects[DECLARE] += 1
            declared.append(uncovered(c, rid))
    first = next((c for c in calls if c["name"] == "submit_report"), None)
    first_ci = None if first is None else (first["kind"] == "submit" and first["submit"].get("findings") == [])
    # 4.4 부당 거절(부품 자신의 REJECT로)
    unjust = {}
    cur = env["current"][q["key"]]
    tools = [c for c in calls if c["kind"] == "tool"]
    if "⓪" in parts:
        table = env["intents"].get(q["id"]) if env["intents"] is not None else None
        if table is None:
            unjust["⓪"] = None
        else:
            n = 0
            for cl in declared:
                miss = [k for k in cl if k not in table]
                if miss:
                    raise S.Bind(f"{rid}.jsonl: ⓪ 거절의 절이 의도 절 표({q['qtype']}-{x['template']})에 없다(절 {len(miss)}개). 표와 ⓪이 어긋났다")
                n += not any(table[k] for k in cl)
            unjust["⓪"] = [n, len(declared)]
    if "③" in parts:
        call = a["call"]
        hit = [c for c in tools if c["name"] == "trade_stats" and isinstance(c["input"], dict)
               and all(c["input"].get(k) == call[k] for k in ("api", "region_code", "deal"))]
        area = [c for c in tools if c["name"] == "convert_area"]
        unjust["③"] = [sum("③" in c["own"] for c in hit), len(hit)]
        unjust["③면적"] = [sum("③" in c["own"] for c in area), len(area)]
    if "②" in parts:
        hit = [c for c in tools if c["name"] == "trade_stats" and isinstance(c["input"], dict) and c["input"].get("region_code") == cur]
        unjust["②"] = [sum("②" in c["own"] for c in hit), len(hit)]
    if "④" in parts:
        sound = [c for c in calls if c["kind"] == "submit" and S.sound_submission(c["submit"], loop[:c["at"]], a, env["forbidden"], env["cfg"])]
        unjust["④"] = [sum("④" in c["own"] for c in sound), len(sound)]
    # 4절 근거 없는 완료 보고, ④ 없는 판의 받은 답 findings
    ungrounded, found = None, []
    if rec["outcome"] == "SUBMITTED":
        sub = S.accepted_submission(events)
        fs = [f for f in (sub.get("findings") or []) if isinstance(f, str)]
        ungrounded = len({f.rsplit(" ", 1)[0] for f in fs if f.rsplit(" ", 1)[-1] in EVIDENCE_CODES
                          and CLAIM_WHERE.fullmatch(f.rsplit(" ", 1)[0])})
        found = sorted({f.rsplit(" ", 1)[-1] for f in fs})
    # 소요시간·비용(Summary와 같은 셈), 진단
    millis, withc, without = 0.0, Decimal(0), Decimal(0)
    refusal, tool_errors, nudges, turns, ended = None, Counter(), 0, 0, False
    head = rec["head"]
    for e in events:
        t = as_text(get(e, "type"))
        if t in ("response", "tool", "accepted"):
            millis += as_double(get(e, "millis"))
        if t == "response":
            w, wo = costs(env["prices"], head["model"], use(e, head["ttl"]))
            withc, without = add(withc, w), add(without, wo)
            if as_text(get(e, "stop")) == "refusal":
                refusal = as_text(get(e, "refusal_category")) or "(없음)"
        elif t == "tool" and as_bool(get(e, "is_error")):
            tool_errors[as_text(get(e, "error_code")) or "(없음)"] += 1
        elif t == "nudge":
            nudges += 1
        elif t == "end":
            turns += as_long(get(e, "turns"))
            ended = True
    ok, zero, other = zero_counts(loop, cur)
    repeat = repeat_area = None
    if env["mistakes"] is not None and not env["same_stage"]:
        cs = [c for c in x["codes"] if c != "JUDGE_MISSING"]
        n = env["areas"][cur]
        repeat = any(c in env["mistakes"]["codes"] for c in cs)
        repeat_area = any(f"{c}|{n}" in env["mistakes"]["code_areas"] for c in cs)
    order = lambda m: {k: m[k] for k in [p for p in PART_ORDER + (DECLARE,) if p in m] + sorted(k for k in m if k not in PART_ORDER + (DECLARE,))}
    out = {"first_ci": first_ci, "received": order(received), "rejects": order(rejects),
           "unjust": {k: unjust[k] for k in ("⓪", "③", "③면적", "②", "④") if k in unjust}, "ungrounded": ungrounded,
           "millis": repr(millis), "cost": [format(withc, "f"), format(without, "f")], "turns": turns, "nudges": nudges, "refusal": refusal,
           "tool_errors": {k: tool_errors[k] for k in sorted(tool_errors, key=utf16)}, "zero_calls": [zero, other],
           "repeat": repeat, "repeat_area": repeat_area}
    agg = {"millis": millis, "ended": ended, "with": withc, "without": without, "stats_ok": ok, "found": found,
           "prices_same": head["prices"] is not None and head["prices"] == env["prices"]}
    return out, agg


# ---------------------------------------------------------------- 판마다(metrics.tsv)

def metrics_rows(ctx, rows, extra, aggs):
    """판·갈래·이름·분자·분모 줄. 판은 계획 차례, 판 안에서는 정한 차례다."""
    env = ctx["env"]
    out = []
    by = {}
    for r, x, o, g in zip(ctx["plan"], rows, extra, aggs):
        by.setdefault(r["variant"]["id"], (r["variant"], []))[1].append((r, x, o, g))
    for vid, (v, runs) in by.items():
        parts = v["parts"]
        line = lambda kind, name, a, b: out.append((vid, kind, name, num_text(a), num_text(b)))
        n = len(runs)
        sub = [t for t in runs if t[1]["outcome"] == "SUBMITTED"]
        full = sum(t[1]["full"] for t in runs)
        cis = [t[2]["first_ci"] for t in runs if t[2]["first_ci"] is not None]
        line("15.9", "첫 시도 CI 통과", sum(cis), len(cis))
        for p in REJECTING:
            if p not in parts:
                continue
            got = [t for t in runs if t[2]["received"].get(p) or (p == "⓪" and t[2]["received"].get(DECLARE))]
            line("15.9", f"거절 뒤 자력 완료 {p}", sum(t[1]["outcome"] == "SUBMITTED" for t in got), len(got))
            line("15.9", f"거절 뒤 완전 정답 {p}", sum(t[1]["full"] for t in got), len(got))
        if env["mistakes"] is None or env["same_stage"]:
            line("15.9", "같은 실수 재발", None, None)
            line("15.9", "같은 실수 재발(코드, 시군구)", None, None)
        else:
            line("15.9", "같은 실수 재발", sum(t[2]["repeat"] for t in runs), n)
            line("15.9", "같은 실수 재발(코드, 시군구)", sum(t[2]["repeat_area"] for t in runs), n)
        line("15.9", "근거 없는 완료 보고(claim)", sum(t[2]["ungrounded"] for t in sub), len(sub))
        ms = 0.0
        for t in sorted(runs, key=lambda t: t[0]["id"] + ".jsonl"):
            if t[3]["ended"]:
                ms += t[3]["millis"]
        line("15.9", "소요시간(ms)", ms, sum(t[3]["ended"] for t in runs))
        for p in ("⓪", "③", "③면적", "②", "④"):
            if p.rstrip("면적") not in parts:
                continue
            name = "부당 거절 ③(면적)" if p == "③면적" else f"부당 거절 {p}"
            vals = [t[2]["unjust"].get(p) for t in runs]
            if any(u is None for u in vals):
                line("15.9", name, None, None)
            else:
                line("15.9", name, sum(u[0] for u in vals), sum(u[1] for u in vals))
            if p == "⓪":
                for key in sorted({(t[0]["question"]["qtype"], t[1]["template"]) for t in runs if t[1]["template"] is not None}):
                    us = [t[2]["unjust"]["⓪"] for t in runs if (t[0]["question"]["qtype"], t[1]["template"]) == key]
                    if any(u is None for u in us):
                        line("15.9", f"부당 거절 ⓪ {key[0]}-{key[1]}", None, None)
                    else:
                        line("15.9", f"부당 거절 ⓪ {key[0]}-{key[1]}", sum(u[0] for u in us), sum(u[1] for u in us))
        w = wo = Decimal(0)
        for t in runs:
            w, wo = add(w, t[3]["with"]), add(wo, t[3]["without"])
        line("비용", "캐시 있음($)", w, n)
        line("비용", "캐시 없음($)", wo, n)
        line("비용", "완전 정답 한 건당 캐시 있음($)", w, full)
        line("판", "모델", v["model"], None)
        c = ctx["s"]["cache"]
        line("판", "캐시", f"system={as_text(c.get('system'))};conversation={as_text(c.get('conversation'))};ttl={as_text(c.get('ttl'))}", None)
        ok = sum(t[3]["stats_ok"] for t in runs)
        zero = sum(t[2]["zero_calls"][0] for t in runs)
        line("진단", "trade_stats 표본 0", zero, ok)
        line("진단", "표본 0 가운데 현재 코드 아님", sum(t[2]["zero_calls"][1] for t in runs), zero)
        errs = Counter()
        refs = Counter()
        for t in runs:
            errs.update(t[2]["tool_errors"])
            if t[2]["refusal"] is not None:
                refs[t[2]["refusal"]] += 1
        for k in sorted(errs, key=utf16):
            line("진단", f"도구 오류 {k}", errs[k], None)
        for k in sorted(refs, key=utf16):
            line("진단", f"refusal {k}", refs[k], None)
        line("진단", "재촉", sum(t[2]["nudges"] for t in runs), n)
        line("진단", "턴", sum(t[2]["turns"] for t in runs), sum(t[3]["ended"] for t in runs))
        for p in [p for p in PART_ORDER if p in parts and p in REJECTING] + ([DECLARE] if "⓪" in parts else []):
            line("진단", f"받은 거절 {p}", sum(t[2]["received"].get(p, 0) for t in runs), None)
            line("진단", f"자기 거절 {p}", sum(t[2]["rejects"].get(p, 0) for t in runs), None)
        if "④" not in parts:
            per = Counter()
            dens = Counter()
            for t in sub:
                dens[t[0]["question"]["qtype"]] += 1
                for code in t[3]["found"]:
                    per[(code, t[0]["question"]["qtype"])] += 1
            for code, qt in sorted(per):
                line("진단", f"받은 답 findings {code} {qt}", per[(code, qt)], dens[qt])
        if env["mistakes"] is not None and not env["same_stage"]:
            for code in sorted(env["mistakes"]["codes"]):
                line("진단", f"같은 실수 코드 {code}", sum(code in t[1]["codes"] for t in runs), n)
        line("진단", "단가표가 다른 기록", sum(not t[3]["prices_same"] for t in runs), n)
    return out


# ---------------------------------------------------------------- 7절: 하네스 summary.tsv

def summary_text(ctx):
    """하네스 Summary.rows·format과 같은 셈. 판은 판 설정 파일의 판 모두(계획 0인 판도), 마지막 줄은 합."""
    rows = {}
    blank = lambda vid, name: {"variant": vid, "name": name, "planned": 0, "done": 0, "outcomes": Counter(), "refusals": Counter(),
                               "tool_errors": Counter(), "pauses": Counter(), "attempts": 0, "requests": 0, "max_prompt": 0, "over": 0,
                               "input": 0, "cache_read": 0, "cache_write": 0, "output": 0, "with": Decimal(0), "without": Decimal(0),
                               "ended": 0, "millis": 0.0, "turns": 0}
    for v in ctx["variants"]:
        rows[v["id"]] = blank(v["id"], v["name"])
    records = ctx["records"]
    for r in ctx["plan"]:
        row = rows[r["variant"]["id"]]
        row["planned"] += 1
        row["done"] += any(e.get("type") == "end" for e in records[r["id"]]["events"])
    for name in sorted(rid + ".jsonl" for rid in records):
        rec = records[name[:-len(".jsonl")]]
        row = rows.get(rec["head"]["variant"])
        if row is None:
            continue
        model, ttl = rec["head"]["model"], rec["head"]["ttl"]
        run_ms, complete = 0.0, False
        for e in rec["events"]:
            t = as_text(get(e, "type"))
            if t == "response":
                u = use(e, ttl)
                prompt = u[0] + u[1] + u[2] + u[3]
                row["requests"] += 1
                row["max_prompt"] = max(row["max_prompt"], prompt)
                row["over"] += prompt > LINE
                row["input"] += u[0]
                row["cache_read"] += u[1]
                row["cache_write"] += u[2] + u[3]
                row["output"] += u[4]
                w, wo = costs(ctx["env"]["prices"], model, u)
                row["with"], row["without"] = add(row["with"], w), add(row["without"], wo)
                if as_text(get(e, "stop")) == "refusal":
                    row["refusals"][as_text(get(e, "refusal_category")) or "(없음)"] += 1
                run_ms += as_double(get(e, "millis"))
            elif t == "tool":
                run_ms += as_double(get(e, "millis"))
                if as_bool(get(e, "is_error")):
                    row["tool_errors"][as_text(get(e, "error_code")) or "(없음)"] += 1
            elif t == "accepted":
                run_ms += as_double(get(e, "millis"))
            elif t == "attempt":
                row["attempts"] += 1
            elif t == "pause":
                row["pauses"][as_text(get(e, "reason"))] += 1
            elif t == "end":
                row["outcomes"][as_text(get(e, "outcome"))] += 1
                row["ended"] += 1
                row["turns"] += as_long(get(e, "turns"))
                complete = True
        if complete:
            row["millis"] += run_ms
    out = list(rows.values())
    total = blank("합", "")
    for o in out:
        for k in ("planned", "done", "attempts", "requests", "over", "input", "cache_read", "cache_write", "output", "ended", "turns"):
            total[k] += o[k]
        for k in ("outcomes", "refusals", "tool_errors", "pauses"):
            total[k].update(o[k])
        total["max_prompt"] = max(total["max_prompt"], o["max_prompt"])
        total["with"], total["without"] = add(total["with"], o["with"]), add(total["without"], o["without"])
        total["millis"] += o["millis"]
    out.append(total)
    lines = ["\t".join(SUMMARY_HEAD)]
    for r in out:
        c = [r["variant"], r["name"], str(r["planned"]), str(r["done"]), str(r["planned"] - r["done"])]
        c += [str(r["outcomes"].get(o, 0)) for o in OUTCOMES]
        c += [join(r["refusals"]), join(r["tool_errors"]), str(r["requests"]), str(r["max_prompt"]), str(r["over"])]
        c.append("" if r["requests"] == 0 else half_up(r["over"] * 100.0 / r["requests"], 1))
        c += [str(r["input"]), str(r["cache_read"]), str(r["cache_write"]), str(r["output"]), money(r["with"]), money(r["without"])]
        c.append("" if r["ended"] == 0 else half_up(r["millis"] / 1000.0 / r["ended"], 1))
        c.append("" if r["ended"] == 0 else half_up(float(r["turns"]) / r["ended"], 1))
        c += [str(r["attempts"]), join(r["pauses"])]
        lines.append("\t".join(c))
    return "".join(x + "\n" for x in lines)


def check_summary(ctx):
    """기록 폴더의 하네스 summary.tsv가 같은 셈과 글자까지 같은가. 없거나 다르면 Bind."""
    path = os.path.join(ctx["stage_dir"], "summary.tsv")
    if not os.path.exists(path):
        raise S.Bind("하네스 summary.tsv가 없다. 채점 바로 전에 하네스 summary를 그 설정으로 친다")
    with open(path, "rb") as f:
        try:
            got = S.lf(f.read()).decode("utf-8")
        except UnicodeDecodeError:
            raise S.Bind("하네스 summary.tsv가 UTF-8이 아니다") from None
    want = summary_text(ctx)
    if got != want:
        g, w = [x.split("\t") for x in got.split("\n")], [x.split("\t") for x in want.split("\n")]
        where = "줄 수"
        for i in range(min(len(g), len(w))):
            if g[i] != w[i]:
                col = next((j for j in range(min(len(g[i]), len(w[i]))) if g[i][j] != w[i][j]), None)
                where = f"판 {w[i][0]}, 칸 " + (SUMMARY_HEAD[col] if col is not None and col < len(SUMMARY_HEAD) else "수")
                break
        raise S.Bind(f"하네스 summary.tsv가 같은 셈과 다르다({where}). 채점 바로 전에 하네스 summary를 그 설정으로 다시 친다")
    return len(ctx["variants"])


# ---------------------------------------------------------------- 모두

def compute(ctx, rows):
    """8b: summary.tsv를 대 본 뒤 실행마다 칸을 더하고 metrics.tsv의 글을 낸다. (판 수, 더한 칸들, metrics 글, 줄들(8c 보고가 씀))."""
    n = check_summary(ctx)
    extra, aggs = [], []
    for r, x in zip(ctx["plan"], rows):
        rec = ctx["records"][r["id"]]
        o, g = run_metrics(rec, r, ctx["by_answer"][r["question"]["id"]], x, ctx["env"])
        extra.append(o)
        aggs.append(g)
    lines = metrics_rows(ctx, rows, extra, aggs)
    text = "".join("\t".join(x) + "\n" for x in [METRICS_HEAD] + lines)
    return n, extra, text, lines
