# -*- coding: utf-8 -*-
"""순서 2-2(만들기 설계안 5.2, 01 291~305줄): 유효성 표.

영역은 매물의 현재 시군구다. (영역, API, 달)마다 후보 코드별로 영역 거래 건수를 세고 판정한다.
- 후보 코드: 영역의 현재 법정동들이 창 안에서 속했던 5자리 코드(picker History.candidates, 1:N 형제 코드 빼고).
- 건수: 재생 저장소에서 응답 코드가 그 코드이고, 현재 시군구가 영역인 거래 수(코드 사이 중복을 지우기 전).
- 판정
    - single: 후보가 하나뿐이다. 0건이어도 유효다.
    - true_zero: 후보가 모두 0건이다(정말 0건). 모두 유효다.
    - one: 건이 있는 후보가 하나다. 그 코드만 유효이고, 나머지는 무효이며 처방은 그 코드다.
    - 건이 있는 후보가 둘 이상인 달은 이번 스냅샷에 없다고 본다. 나오면 멈춘다(설계안 0절: 깨지면 멈추게 짠다).
- 후보가 아닌 코드는 표에 없다. 그 매물에 늘 무효이고, 처방은 영역 코드다(01 298줄). 도구와 ②가 그렇게 읽는다.
"""
from collections import Counter

from picker import snapshot
from picker.common import code_sorted, write_csv

HEADER = ["area", "api", "ym", "code", "count", "valid", "prescription", "case"]


class MultiSource(RuntimeError):
    pass


def area_counts(trade_rows):
    """(영역, API, 달, 응답 코드) -> 건수. trade_rows는 replay.TRADE_HEADER 차례의 줄이다."""
    cnt = Counter()
    for r in trade_rows:
        area = r[7].split("|")[1]
        cnt[(area, r[0], str(r[2]), r[1])] += 1
    return cnt


def judge(cands, counts):
    """(case, {코드: (유효 0/1, 처방)})"""
    has = [c for c in cands if counts[c] > 0]
    if len(cands) == 1:
        return "single", {cands[0]: (1, "")}
    if not has:
        return "true_zero", {c: (1, "") for c in cands}
    if len(has) == 1:
        return "one", {c: ((1, "") if c == has[0] else (0, has[0])) for c in cands}
    raise MultiSource("건이 있는 후보가 둘 이상인 달이 있다")


def build(trade_rows, hist, areas, months):
    cnt = area_counts(trade_rows)
    rows = []
    for area in sorted(areas):
        cands = hist.candidates(area)
        for api in code_sorted(snapshot.APIS):
            for ym in months:
                counts = {c: cnt.get((area, api, ym, c), 0) for c in cands}
                case, verdict = judge(cands, counts)
                for c in cands:
                    valid, presc = verdict[c]
                    rows.append([area, api, ym, c, counts[c], valid, presc, case])
    return rows


def write(rows, path):
    write_csv(path, HEADER, rows)
