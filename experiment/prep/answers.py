# -*- coding: utf-8 -*-
"""순서 3 대조용 정답 식(설계안 6판 9.3, 9.2 배정). 문항마다 맞는 부르기와 그 값을 낸다.

Java 도구(ai-core tools)와 따로 짠 같은 식이다(9.3 "같은 식을 두 곳에"). 하네스 시험이 이 결과를 읽어
같은 부르기를 Java 도구에 넣고 표본 수·기준값·환산·차이가 같은지 본다.

- 거래: 재생 저장소에서 매물의 API·거래, 매물의 현재 시군구 코드만 쓴다(옛 코드는 이번 스냅샷에서 모두 0건).
- 범위: 단지(같은 단지ID) → 법정동(같은 현재 법정동) → 시군구. T2는 단지로 고정하고 넓히지 않는다.
- 넓히기: 표본이 N 5 이상인 가장 좁은 범위. 시군구도 N 미만이면 시군구.
- 기간: 계약 월. 기본 12달(202511~202610). T3는 202610을 끝으로 1·3·6달을 모두 낸다(틀 문장이 아직 없어서).
- 기준값: 거래마다 금액 ÷ 면적, 중앙값 × 매물 면적, 만원으로 한 번 반올림. 월세는 보증금·월세 따로, 환산은 보인 두 값으로.
- 차이: (매물 − 기준) ÷ 기준 × 100을 ⌊x/0.1 + 1/2⌋ × 0.1로. 월세 매물은 환산보증금끼리 견준다.
"""
import json
import math
from collections import defaultdict
from fractions import Fraction

from picker.clean import API_OF
from picker.common import H
from picker.juso import jibun_text

from . import listings, replay

RATE = Fraction(5, 100)
N_BASE = 5
END = (2026, 10)
WINDOWS = {"T3": (1, 3, 6)}


def round_unit(x, unit):
    """06 2절: ⌊x / U + 1/2⌋ × U (U는 양의 분수, 음수에도 같은 식)."""
    return math.floor(Fraction(x) / unit + Fraction(1, 2)) * unit


def median(values):
    v = sorted(values)
    n = len(v)
    return v[n // 2] if n % 2 else (v[n // 2 - 1] + v[n // 2]) / 2


def months_back(n):
    y, m = END
    out = []
    for _ in range(n):
        out.append("%04d%02d" % (y, m))
        m -= 1
        if m == 0:
            y, m = y - 1, 12
    return sorted(out)


def assign(cid, deal):
    """9.2: 월세는 T1·T4 + (T2·T3 가운데 하나), 매매·전세는 T1 + (T2·T3·T5 가운데 둘). H(qtype, 단지ID|유형)가 작은 순."""
    if deal == "월세":
        rest = sorted(["T2", "T3"], key=lambda t: H("qtype", f"{cid}|{t}"))
        return ["T1", "T4", rest[0]]
    rest = sorted(["T2", "T3", "T5"], key=lambda t: H("qtype", f"{cid}|{t}"))
    return ["T1"] + rest[:2]


class Store:
    def __init__(self, trade_path):
        self.by = defaultdict(list)
        with open(trade_path, "r", encoding="utf-8", newline="") as f:
            import csv
            rd = csv.reader(f)
            if next(rd) != replay.TRADE_HEADER:
                raise ValueError("거래 파일 머리줄이 다르다")
            for r in rd:
                _, _, path, mtn, bon, bu = r[7].split("|")
                self.by[(r[0], r[1], r[2])].append((r[6], path, int(mtn), int(bon), int(bu), Fraction(r[12]),
                                                     r[15], r[16], r[17]))

    def pick(self, api, code, months, deal, dong, jib):
        out = []
        for ym in months:
            for t in self.by.get((api, code, ym), ()):
                if t[0] != deal or (dong and t[1] != dong) or (jib and t[2:5] != jib):
                    continue
                out.append(t)
        return out


def values(rows, api, deal, area):
    """표본 수와 기준값(만원). 표본이 0이면 기준값은 None."""
    n = len(rows)
    if api.endswith("_trade") or deal == "전세":
        col = 6 if api.endswith("_trade") else 7
        b = round_unit(median([Fraction(int(t[col])) / t[5] for t in rows]) * area, 1) if n else None
        return {"sample": n, "basis": b}
    d = round_unit(median([Fraction(int(t[7])) / t[5] for t in rows]) * area, 1) if n else None
    m = round_unit(median([Fraction(int(t[8])) / t[5] for t in rows]) * area, 1) if n else None
    c = round_unit(d + m * 12 / RATE, 1) if n else None
    return {"sample": n, "deposit": d, "monthly": m, "converted": c}


def build(store, L, metas):
    cases = []
    for (key, x), meta in zip(L, metas):
        kind, sgg, path, mtn, bon, bu = meta["complex_id"].split("|")
        deal = meta["deal"]
        api = API_OF[(kind, deal)]
        area = Fraction(int(x["exclusiveArea"]))
        price, fee = int(x["stockSellPrice"]), int(x["stockFeeMonth"])
        listing_value = price if deal != "월세" else round_unit(Fraction(price, 10000) + Fraction(fee, 10000) * 12 / RATE, 1) * 10000
        jib = (int(mtn), int(bon), int(bu))
        scopes = [("단지", path, jib), ("법정동", path, None), ("시군구", "", None)]
        for qtype in assign(meta["complex_id"], deal):
            for n in WINDOWS.get(qtype, (12,)):
                months = months_back(n)
                if qtype == "T2":
                    chosen = scopes[0]
                    rows = store.pick(api, sgg, months, deal, path, jib)
                else:
                    for chosen in scopes:
                        rows = store.pick(api, sgg, months, deal, chosen[1], chosen[2])
                        if len(rows) >= N_BASE or chosen[0] == "시군구":
                            break
                v = values(rows, api, deal, area)
                b = v.get("basis") if deal != "월세" else v.get("converted")
                diff = round_unit((listing_value - b * 10000) / (b * 10000) * 100, Fraction(1, 10)) if v["sample"] else None
                case = {
                    "key": key, "qtype": qtype, "months": n, "scope": chosen[0],
                    "call": {"api": api, "region_code": sgg, "start_month": months[0], "end_month": months[-1], "deal": deal,
                             "dong": chosen[1], "jibun": jibun_text(*chosen[2]) if chosen[2] else ""},
                    "sample": v["sample"], "listing_value": str(listing_value), "diff": None if diff is None else str(diff),
                }
                for k in ("basis", "deposit", "monthly", "converted"):
                    if k in v:
                        case[k] = None if v[k] is None else str(v[k])
                if qtype == "T5":
                    case["t5_count"] = len(store.pick(api, sgg, months_back(12), deal, path, jib))
                cases.append(case)
    return cases


def write(cases, path):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(cases, ensure_ascii=False, indent=1) + "\n")


def load_metas(listing_csv):
    from picker.common import read_csv
    header, rows = read_csv(listing_csv)
    idx = {h: i for i, h in enumerate(header)}
    return [{"complex_id": r[idx["complex_id"]], "deal": r[idx["deal"]], "split": r[idx["split"]]} for r in rows]
