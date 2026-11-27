# -*- coding: utf-8 -*-
"""순서 8c: 통계와 보고 표(순서 8c 설계 2판 2~7·11절). score가 8b 지표 뒤에 부른다.

- 2절: 결과 y는 실행마다 0·1이다. 판의 값은 문항마다 회차 평균의 평균이고, 짝지은 차이 A − B는 문항마다 뺀 것의 평균이다.
  점 추정값은 원래 표본으로 낸다.
- 3절: 층 안 매물 묶음 부트스트랩. 뽑기 b(1부터)·층 L·j(1부터)마다, 그 층의 매물을 키 차례로 세운 목록에서
  0부터 센 u64(H(purpose, "b|L|j")) mod n_L 자리의 매물을 뽑는다(H·u64는 picker.common). 모든 판·비교·칸·결과가 같은 뽑기를 쓴다.
  칸이 빈 뽑기는 빼고 그 수를 적는다. 구간은 남은 B′개 값의 ⌈B′/40⌉번째와 B′ + 1 − 그 차례번째다(1부터, 보간 없음).
- 셈은 정수와 분수다. 계열마다 매물마다의 수를 64비트 칸에 이어 붙인 큰 정수로 두고, 뽑기마다 뽑힌 수를 곱해 더한 뒤 칸을 푼다.
  구간의 차례는 칸의 분모 최소공배수로 맞춘 정수 열쇠로 정렬해 고른다. 쓸 때만 반올림한다(HALF_UP, 동점은 0에서 먼 쪽).
- 글은 쓰지 않는다. 판 ID·이름, 칸 이름, 수, 해시만 쓴다.
- 11b-1(설계 4판 4.2): 결과는 완전 정답(주 (다)), 수사 모두 실패((가)), 수사 뺀((나)), 판정 없는 실행 뺀(주 정의), 소요시간이다.
  판정을 완전 정답에서 뺐으면(full_with_judge false) 판정 없는 실행 뺀 결과는 내지 않는다.
"""
import math
import sys
from array import array
from decimal import Decimal
from fractions import Fraction

from picker.common import H, u64

from . import metrics as M
from . import score as S

OUTCOMES = ("완전 정답", "수사 모두 실패", "수사 뺀", "판정 없는 실행 뺀", "소요시간(초)")
STATS_HEAD = ("갈래", "이름", "결과", "칸", "문항", "값", "하한", "상한", "값(분수)", "빈 뽑기")
AI_TEMPLATE = 5                               # 다섯째 틀(AI 틀)을 뺀 칸
TIMED = frozenset(("response", "tool", "accepted"))  # 실행 시간의 사건(8b와 같음)
DASH = "-"


# ---------------------------------------------------------------- 수 꼴

def fmt(x, places):
    """분수를 소수 places자리 글로. HALF_UP: 절댓값을 반올림한 뒤 부호를 붙인다(동점은 0에서 먼 쪽, −1.25 → −1.3).
    0으로 반올림되면 부호를 붙이지 않는다. None은 "-"."""
    if x is None:
        return DASH
    a = abs(Fraction(x)) * 10 ** places
    n = (2 * a.numerator + a.denominator) // (2 * a.denominator)  # floor(a + 1/2), a ≥ 0
    t = str(n).rjust(places + 1, "0")
    if places:
        t = t[:-places] + "." + t[-places:]
    return "-" + t if x < 0 and n else t


def frac(x):
    return DASH if x is None else f"{x.numerator}/{x.denominator}"


def pct(x):
    return DASH if x is None else fmt(x * 100, 1)


def sec(x):
    return DASH if x is None else fmt(x, 1)


# ---------------------------------------------------------------- 모으기

def run_ms(events):
    """실행 시간(ms, 분수): response·tool·accepted 사건 millis의 합. 기록의 수는 그대로 더한다(Decimal → 분수).
    수가 아닌 칸은 하네스(Jackson asDouble)처럼 읽는다(없으면 0)."""
    t = Fraction(0)
    for e in events:
        if M.as_text(M.get(e, "type")) in TIMED:
            v = M.get(e, "millis")
            t += Fraction(v) if isinstance(v, (int, Decimal)) and not isinstance(v, bool) else Fraction(M.as_double(v))
    return t


def gather(ctx, rows):
    """채점이 넘긴 것에서 통계에 쓰는 것만 모은다. 매물 차례는 매물 파일의 줄 차례(L01부터, 키 차례)다."""
    s, metas, cfg = ctx["s"], ctx["metas"], ctx["cfg"]
    cur = ctx["env"]["current"]
    variants, questions = {}, {}
    for r in ctx["plan"]:
        variants.setdefault(r["variant"]["id"], r["variant"])
        questions.setdefault(r["question"]["id"], r["question"])
    planned = {q["key"] for q in questions.values()}
    listings = [k for k in metas if k in planned]
    at = {k: i for i, k in enumerate(listings)}
    layers = []
    for m in metas.values():
        if m["layer"] not in layers:
            layers.append(m["layer"])
    strata = [(L, [at[k] for k in listings if metas[k]["layer"] == L]) for L in layers]
    main = s["split"] == "본시험"
    seen = {cur[k] for k, m in metas.items() if m["split"] == "연습"}
    tn = ctx["tnums"]
    qs = [{"id": q["id"], "qtype": q["qtype"], "layer": metas[q["key"]]["layer"], "listing": at[q["key"]],
           "template": tn.get(q["id"]) if tn else None, "area": (cur[q["key"]] in seen) if main else None} for q in questions.values()]
    ids = lambda f: {q["id"] for q in qs if f(q)}
    cells = [("전체", ids(lambda q: True), True)]
    cells += [(f"층 {L}", ids(lambda q, L=L: q["layer"] == L), True) for L in layers]
    cells += [(f"유형 {T}", ids(lambda q, T=T: q["qtype"] == T), True) for T in S.TYPES]
    cells += [("⑤ 있던", ids(lambda q: q["area"] is True), main), ("⑤ 없던", ids(lambda q: q["area"] is False), main)]
    cells.append(("AI 틀 뺌", ids(lambda q: q["template"] != AI_TEMPLATE), tn is not None))
    runs = [(r["variant"]["id"], r["question"]["id"], bool(x["full"]), bool(x["full_no_numeral"]),
             x["outcome"] == "SUBMITTED" and x["judge"] is None, run_ms(ctx["records"][r["id"]]["events"]), bool(x["full_all_numerals"]))
            for r, x in zip(ctx["plan"], rows)]
    c = s["cache"]
    cp, bs = cfg["comparisons"], cfg["bootstrap"]
    return {"R": len(s["reps"]), "variants": list(variants.values()), "questions": qs, "listings": listings, "layers": layers,
            "strata": [(L, xs) for L, xs in strata if xs], "cells": cells, "runs": runs,
            "timed": c.get("system") is False and c.get("conversation") is False,
            "unjudged": cfg["unjudged"] == "fail" and cfg["full_with_judge"] and any(x[4] for x in runs),
            "comparisons": [("주", *cp["main"])] + [("보조", *p) for p in cp["aux"]], "B": bs["draws"], "purpose": bs["purpose"]}


# ---------------------------------------------------------------- 뽑기와 셈

def draw(strata, B, purpose):
    """뽑기 b = 1..B. strata는 [(층, [매물 차례…](키 차례))]이다. 뽑기마다 [(매물 차례, 뽑힌 수)](매물 차례 순)."""
    out = []
    for b in range(1, B + 1):
        got = {}
        for L, xs in strata:
            n = len(xs)
            for j in range(1, n + 1):
                i = xs[u64(H(purpose, f"{b}|{L}|{j}")) % n]
                got[i] = got.get(i, 0) + 1
        out.append(sorted(got.items()))
    return out


def packed_sums(ser, draws):
    """ser는 계열마다 매물마다의 음 아닌 정수 목록이다. 뽑기마다 Σ 뽑힌 수 × 값을 계열마다 낸다([계열][뽑기]).
    계열을 64비트 w칸에 이어 붙인 큰 정수로 두고 한 번에 더한다. w는 한 뽑기에서 나올 수 있는 가장 큰 합이 들어가게 고른다."""
    ns, nl = len(ser), len(ser[0])
    top = max(max(v) for v in ser) * nl  # 뽑기마다 뽑힌 수의 합은 매물 수다
    w = max(1, -(-top.bit_length() // 64))
    width = 8 * w
    packed = [int.from_bytes(b"".join(v[i].to_bytes(width, "little") for v in ser), "little") for i in range(nl)]
    buf = bytearray()
    for d in draws:
        t = 0
        for i, m in d:
            t += m * packed[i]
        buf += t.to_bytes(width * ns, "little")
    a = array("Q")
    if a.itemsize != 8:
        raise S.Bad("이 파이썬의 array('Q')가 64비트가 아니다")
    a.frombytes(bytes(buf))
    if sys.byteorder == "big":
        a.byteswap()
    if w == 1:
        return [a[k::ns] for k in range(ns)]
    step = w * ns
    return [[sum(a[b * step + k * w + t] << (64 * t) for t in range(w)) for b in range(len(draws))] for k in range(ns)]


def interval(nums, dens, scale, cache, key):
    """뽑기마다 값 nums[b] / (scale × dens[b])의 구간. 빈 뽑기(dens 0)는 뺀다. (하한, 상한, 빈 뽑기 수).
    차례는 분모의 최소공배수 lc로 맞춘 정수 열쇠(값 × lc × scale)로 고른다. 열쇠가 같으면 값이 같다."""
    if key not in cache:
        ds = set(dens)
        ds.discard(0)
        if not ds:
            cache[key] = None
        else:
            lc = math.lcm(*ds)
            by = {d: lc // d for d in ds}
            by[0] = 0
            cache[key] = (lc, [by[d] for d in dens])
    c = cache[key]
    if c is None:
        return None, None, len(dens)
    lc, mult = c
    keys = sorted(n * m for n, m in zip(nums, mult) if m)
    b = len(keys)
    lo = -(-b // 40)
    return Fraction(keys[lo - 1], lc * scale), Fraction(keys[b - lo], lc * scale), len(dens) - b


def estimate(D, draws):
    """판·비교 × 결과 × 칸마다 (문항, 값, 하한, 상한, 빈 뽑기). 낼 수 없는 것(없는 판이 든 비교, 연습의 ⑤, 틀 문장 파일 없음,
    판정 없는 실행 없음, 캐시를 켠 단계의 시간)은 넣지 않는다("-")."""
    R = D["R"]
    lr = math.lcm(*range(1, R + 1))  # 판정 없는 실행 뺀 결과: 문항마다 k × lr / r (r이 1..R이라 정수)
    vids = [v["id"] for v in D["variants"]]
    live = [(a, b) for _, a, b in D["comparisons"] if a in vids and b in vids]
    nl = len(D["listings"])
    per = {}
    for vid, qid, full, nn, unj, ms, fa in D["runs"]:
        x = per.setdefault((vid, qid), [0, 0, 0, 0, Fraction(0), 0])
        x[0] += full
        x[1] += nn
        x[2] += not unj
        x[3] += full and not unj
        x[4] += ms
        x[5] += fa
    ts = 1
    if D["timed"]:
        if any(x[4] < 0 for x in per.values()):
            raise S.Bad("소요시간(millis의 합)이 음수인 실행이 있다")
        ts = math.lcm(*(x[4].denominator for x in per.values()))
    res = {}
    for cell, members, ok in D["cells"]:
        if not ok:
            continue
        qs = [q for q in D["questions"] if q["id"] in members]
        ser, at = [], {}

        def add(key, f):
            v = [0] * nl
            for q in qs:
                v[q["listing"]] += f(q)
            at[key] = len(ser)
            ser.append(v)

        add("n", lambda q: 1)
        for v in vids:
            add(("full", v), lambda q: per[(v, q["id"])][0])
            add(("fa", v), lambda q: per[(v, q["id"])][5])
            add(("nn", v), lambda q: per[(v, q["id"])][1])
            if D["timed"]:
                add(("time", v), lambda q: int(per[(v, q["id"])][4] * ts))
            if D["unjudged"]:
                add(("bs", v), lambda q: per[(v, q["id"])][3] * lr // per[(v, q["id"])][2] if per[(v, q["id"])][2] else 0)
                add(("bn", v), lambda q: 1 if per[(v, q["id"])][2] else 0)
        if D["unjudged"]:
            for a, b in live:
                both = lambda q: per[(a, q["id"])][2] and per[(b, q["id"])][2]
                add(("bsa", a, b), lambda q: per[(a, q["id"])][3] * lr // per[(a, q["id"])][2] if both(q) else 0)
                add(("bsb", a, b), lambda q: per[(b, q["id"])][3] * lr // per[(b, q["id"])][2] if both(q) else 0)
                add(("bn", a, b), lambda q: 1 if both(q) else 0)
        sums = packed_sums(ser, draws)
        cache = {}

        def put(kind, name, outcome, plus, minus, den, scale):
            n0 = sum(ser[at[den]])
            p0 = sum(ser[at[plus]]) - (sum(ser[at[minus]]) if minus is not None else 0)
            nums = sums[at[plus]] if minus is None else [x - y for x, y in zip(sums[at[plus]], sums[at[minus]])]
            lo, hi, empty = interval(nums, sums[at[den]], scale, cache, den)
            res[(kind, name, outcome, cell)] = (n0, Fraction(p0, scale * n0) if n0 else None, lo, hi, empty)

        for v in vids:
            put("판", v, "완전 정답", ("full", v), None, "n", R)
            put("판", v, "수사 모두 실패", ("fa", v), None, "n", R)
            put("판", v, "수사 뺀", ("nn", v), None, "n", R)
            if D["unjudged"]:
                put("판", v, "판정 없는 실행 뺀", ("bs", v), None, ("bn", v), lr)
            if D["timed"]:
                put("판", v, "소요시간(초)", ("time", v), None, "n", R * ts * 1000)
        for a, b in live:
            name = f"{a}-{b}"
            put("비교", name, "완전 정답", ("full", a), ("full", b), "n", R)
            put("비교", name, "수사 모두 실패", ("fa", a), ("fa", b), "n", R)
            put("비교", name, "수사 뺀", ("nn", a), ("nn", b), "n", R)
            if D["unjudged"]:
                put("비교", name, "판정 없는 실행 뺀", ("bsa", a, b), ("bsb", a, b), ("bn", a, b), lr)
            if D["timed"]:
                put("비교", name, "소요시간(초)", ("time", a), ("time", b), "n", R * ts * 1000)
    return res


# ---------------------------------------------------------------- stats.tsv

def names_of(D):
    return [("판", v["id"]) for v in D["variants"]] + [("비교", f"{a}-{b}") for _, a, b in D["comparisons"]]


def stats_text(D, res):
    lines = ["\t".join(STATS_HEAD)]
    for kind, name in names_of(D):
        for o in OUTCOMES:
            for cell, _, _ in D["cells"]:
                x = res.get((kind, name, o, cell))
                if x is None:
                    lines.append("\t".join([kind, name, o, cell] + [DASH] * 6))
                else:
                    n0, v, lo, hi, empty = x
                    lines.append("\t".join([kind, name, o, cell, str(n0), fmt(v, 6), fmt(lo, 6), fmt(hi, 6), frac(v), str(empty)]))
    return "".join(x + "\n" for x in lines), len(lines) - 1


# ---------------------------------------------------------------- report.md

def ci(x, f):
    """값 [하한, 상한]. 낼 수 없으면 "-"."""
    if x is None or x[1] is None:
        return DASH
    return f"{f(x[1])} [{f(x[2])}, {f(x[3])}]"


def table(head, rows):
    out = ["| " + " | ".join(head) + " |", "|" + " --- |" * len(head)]
    out += ["| " + " | ".join(r) + " |" for r in rows]
    return out


def ratio(nd, per=100):
    """metrics.tsv의 분자/분모를 비율로: "62.5 (5/8)". 분자가 "-"이면 "-", 분모가 0이면 "- (0/0)"."""
    if nd is None or nd[0] == DASH or nd[1] == DASH:
        return DASH
    a, b = Fraction(nd[0]), int(nd[1])
    return f"{DASH if b == 0 else fmt(a * per / b, 1)} ({nd[0]}/{nd[1]})"


def report_text(ctx, D, res, tally, mlines):
    s, cfg = ctx["s"], ctx["cfg"]
    B = D["B"]
    lo = -(-B // 40)
    vinfo = {v["id"]: v for v in D["variants"]}
    t = {x["variant"]["id"]: x for x in tally}
    mm = {(vid, name): (a, b) for vid, _, name, a, b in mlines}
    get = lambda kind, name, o, cell="전체": res.get((kind, name, o, cell))
    jp = ctx["judge_print"][:12] if ctx["judge_print"] else DASH
    out = [f"# 채점 보고: 단계 {s['stage']} ({s['split']})", "",
           f"- 설정 판 {s['version']}, 완전 정답 {cfg['version']}(판정을 {'넣음' if cfg['full_with_judge'] else '뺌. 판정 답함은 보조로 1절 표에만 낸다'}). "
           f"채점 지문 {ctx['print'][:12]}, 판정 지문 {jp}",
           "- 완전 정답은 주 정의 (다)다(같은 길이의 통계 claim이 있는 기간 말만 받음). 비교 정의 둘은 (가) 수사 모두 실패(기간 말까지), "
           "(나) 수사 뺀이다. 셋 다 수사 사전 v1로 세고 자리표시자 꼴을 본다.",
           f"- 문항 파일 {ctx['qsha'][:12]}, 정답 파일 {ctx['asha'][:12]}(SHA-256 앞 12자)",
           f"- 판 {len(vinfo)}개, 문항 {len(D['questions'])}개, 회차 {D['R']}번({', '.join(map(str, s['reps']))}), 실행 {len(D['runs'])}개",
           f"- 부트스트랩: 층 안 매물 묶음. 매물 {len(D['listings'])}개(" + "·".join(f"{L} {len(xs)}" for L, xs in D["strata"])
           + f"), 뽑기 {B}번(purpose {D['purpose']}). 95% 구간은 빈 뽑기가 없으면 {lo}번째·{B + 1 - lo}번째 값이다. "
             "빈 뽑기가 있으면 남은 B′로 같은 식이다.",
           "- 비율은 %, 차이는 %p다. 소수 첫째에서 반올림했다(동점은 0에서 먼 쪽). 소수 여섯째와 분수는 stats.tsv에 있다.", ""]
    # 1. 판마다
    out += ["## 1. 판마다 (전체 칸)", ""]
    rows = []
    for vid, v in vinfo.items():
        x = t[vid]
        rows.append([vid, v["name"], v["model"]] + [str(x[k]) for k in ("plan", "submitted", "value", "rule", "answered", "full")]
                    + [ci(get("판", vid, "완전 정답"), pct), ci(get("판", vid, "수사 모두 실패"), pct), ci(get("판", vid, "수사 뺀"), pct)])
    out += table(("판", "이름", "모델", "계획", "제출", "값", "규칙", "판정 답함", "완전 정답", "완전 정답률(%) [95% 구간]",
                  "(가) 수사 모두 실패(%) [95% 구간]", "(나) 수사 뺀(%) [95% 구간]"), rows) + [""]
    # 2. 주 가설
    _, a, b = D["comparisons"][0]
    name = f"{a}-{b}"
    out += [f"## 2. 주 가설: {a} − {b}", ""]
    if a not in vinfo or b not in vinfo:
        out += [f"이 단계에 {a}·{b}가 다 있지 않아 내지 않았다.", ""]
    else:
        out += [f"{a}({vinfo[a]['name']}) − {b}({vinfo[b]['name']}), 같은 문항끼리 짝지은 차이(%p)다.", ""]
        for o, title in (("완전 정답", "완전 정답"), ("수사 모두 실패", "(가) 수사 모두 실패 완전 정답"), ("수사 뺀", "(나) 수사 뺀 완전 정답"),
                         ("판정 없는 실행 뺀", "판정 없는 실행 뺀 완전 정답")):
            out += [f"### {title}", ""]
            if o == "판정 없는 실행 뺀" and not D["unjudged"]:
                out += ["판정을 완전 정답에서 빼서 내지 않았다." if not cfg["full_with_judge"] else "이 단계에는 판정 없는 실행이 없어 내지 않았다.", ""]
                continue
            rows = []
            for cell, _, _ in D["cells"]:
                x = get("비교", name, o, cell)
                rows.append([cell, DASH if x is None else str(x[0]), ci(x, pct), DASH if x is None else str(x[4])])
            out += table(("칸", "문항", "차이(%p) [95% 구간]", "빈 뽑기"), rows) + [""]
    # 3. 보조 비교
    out += ["## 3. 보조 비교 (전체 칸)", "", "여러 번 견준 보조 결과다.", ""]
    rows = []
    for _, a, b in D["comparisons"][1:]:
        name = f"{a}-{b}"
        x = get("비교", name, "완전 정답")
        rows.append([f"{a} − {b}", DASH if x is None else str(x[0]), ci(x, pct), ci(get("비교", name, "수사 모두 실패"), pct),
                     ci(get("비교", name, "수사 뺀"), pct), ci(get("비교", name, "판정 없는 실행 뺀"), pct)])
    out += table(("비교", "문항", "완전 정답 차이(%p) [95% 구간]", "(가) 수사 모두 실패(%p) [95% 구간]", "(나) 수사 뺀(%p) [95% 구간]",
                  "판정 없는 실행 뺀(%p) [95% 구간]"), rows) + [""]
    # 4. 15.9 지표
    out += ["## 4. 15.9 지표 (판마다, 구간 없이 기술만)", "", "metrics.tsv의 분자/분모를 비율로 썼다.", ""]
    rows = []
    for vid in vinfo:
        ms = mm.get((vid, "소요시간(ms)"))
        avg = DASH if ms is None or ms[1] in (DASH, "0") else M.half_up(float(ms[0]) / 1000.0 / int(ms[1]), 1)
        rows.append([vid, ratio(mm.get((vid, "첫 시도 CI 통과"))), ratio(mm.get((vid, "같은 실수 재발"))),
                     ratio(mm.get((vid, "같은 실수 재발(코드, 시군구)"))), ratio(mm.get((vid, "근거 없는 완료 보고(claim)"))), avg])
    out += table(("판", "첫 시도 CI 통과(%)", "같은 실수 재발(%)", "같은 실수 재발(코드, 시군구)(%)", "근거 없는 완료 보고(제출 100개당 claim)",
                  "평균 소요(초)"), rows) + [""]
    rows = []
    for vid, v in vinfo.items():
        for p in ("⓪", "③", "③면적", "②", "④"):
            if p.rstrip("면적") not in v["parts"]:
                continue
            if p == "③면적":
                rows.append([vid, "③(면적)", "", "", ratio(mm.get((vid, "부당 거절 ③(면적)")))])
            else:
                rows.append([vid, p, ratio(mm.get((vid, f"거절 뒤 자력 완료 {p}"))), ratio(mm.get((vid, f"거절 뒤 완전 정답 {p}"))),
                             ratio(mm.get((vid, f"부당 거절 {p}")))])
    if rows:
        out += table(("판", "부품", "거절 뒤 자력 완료(%)", "거절 뒤 완전 정답(%)", "부당 거절(%)"), rows) + [""]
    else:
        out += ["부품이 든 판이 없다.", ""]
    # 5. 비용
    out += ["## 5. 비용 (판마다)", "", "같은 모델의 판끼리만 견준다.", ""]
    rows = []
    for vid, v in vinfo.items():
        w, wo, per = mm.get((vid, "캐시 있음($)")), mm.get((vid, "캐시 없음($)")), mm.get((vid, "완전 정답 한 건당 캐시 있음($)"))
        one = DASH if per is None or per[1] in (DASH, "0") else fmt(Fraction(Decimal(per[0])) / int(per[1]), 4)
        rows.append([vid, v["model"], DASH if w is None else fmt(Fraction(Decimal(w[0])), 4), DASH if wo is None else fmt(Fraction(Decimal(wo[0])), 4),
                     one])
    out += table(("판", "모델", "캐시 있음($)", "캐시 없음($)", "완전 정답 한 건당 캐시 있음($)"), rows) + [""]
    # 6. 시간
    out += ["## 6. 소요시간 (캐시를 끈 단계만)", ""]
    if not D["timed"]:
        out += ["캐시를 켠 단계라 내지 않았다.", ""]
    else:
        out += ["판은 문항마다 회차 평균 시간의 평균(초), 비교는 같은 문항끼리 짝지은 차이(초)다.", ""]
        rows = [[vid, str(get("판", vid, "소요시간(초)")[0]), ci(get("판", vid, "소요시간(초)"), sec)] for vid in vinfo]
        for kind, a, b in D["comparisons"]:
            x = get("비교", f"{a}-{b}", "소요시간(초)")
            rows.append([f"{a} − {b}({kind})", DASH if x is None else str(x[0]), ci(x, sec)])
        out += table(("이름", "문항", "초 [95% 구간]"), rows) + [""]
    return "\n".join(out)


# ---------------------------------------------------------------- 모두

def compute(ctx, rows, tally, mlines):
    """(stats.tsv 글, report.md 글, 찍을 줄)."""
    D = gather(ctx, rows)
    draws = draw(D["strata"], D["B"], D["purpose"])
    res = estimate(D, draws)
    text, n = stats_text(D, res)
    report = report_text(ctx, D, res, tally, mlines)
    vids = {v["id"] for v in D["variants"]}
    gone = sum(1 for _, a, b in D["comparisons"] if a not in vids or b not in vids)
    first = D["variants"][0]["id"]
    # 빈 뽑기가 있는 칸(문항이 있는 칸만. 완전 정답의 분모로 센다)
    empty = [(cell, x[4]) for cell, _, ok in D["cells"] if ok for x in [res[("판", first, "완전 정답", cell)]] if x[0] and x[4]]
    line = (f"통계: 매물 {len(D['listings'])}개(" + "·".join(f"{L} {len(xs)}" for L, xs in D["strata"]) + f") 뽑기 {D['B']}번, "
            f"판 {len(vids)}개·비교 {len(D['comparisons'])}개(주 1, 보조 {len(D['comparisons']) - 1}, 단계에 없는 판이 든 것 {gone}), "
            + ("빈 뽑기 " + ", ".join(f"{c} {k}번" for c, k in empty) if empty else "빈 뽑기 없음")
            + f", stats.tsv {n}줄과 report.md를 썼다")
    return text, report, line
