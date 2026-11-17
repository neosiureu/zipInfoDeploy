# -*- coding: utf-8 -*-
"""값 분포(계획 3절). 뽑기 전에 값별 건수만 센다. 단지나 거래 줄은 찍지 않는다.

도로명주소에 기대지 않는다. 도로명주소가 있어야 정해지는 거래(겹치는 리, 41590으로 온 능동)는 따로 건수만 적는다.
지번처럼 단지를 가리킬 수 있는 값은 값 대신 꼴(숫자를 9로 바꾼 것)만 적는다.
"""
import hashlib
import re
import unicodedata
from collections import Counter, defaultdict

from .clean import KIND, TRADE_APIS, Cleaner, Drop, Pending, Rec, cid
from .codego import RENAMES
from .common import code_sorted, norm, write_csv
from .snapshot import APIS

HEADER = ["item", "api", "code", "value", "count"]
EMPTY = "(빈 값)"
EDGE = 50  # 쪽 경계로 보는 줄 수
OLD_NEW = ("사곳리", "사곶리", "오산동", "여울동")
# 개편 달: 이 달부터 새 코드다(이력의 생성일). 화성 2026-02, 광주·인천·구례 2026-07.
REORG_MONTH = {"41591": "202602", "41593": "202602", "41595": "202602", "41597": "202602",
               "12210": "202607", "12240": "202607", "12270": "202607", "12300": "202607", "12330": "202607",
               "12730": "202607", "28125": "202607", "28155": "202607", "28275": "202607", "28290": "202607"}


def _v(s):
    return s if s != "" else EMPTY


def jibun_form(s):
    if s == "":
        return EMPTY
    if "*" in s:
        return "가린 값(*)"
    if re.fullmatch(r"[0-9]+", s):
        return "본번"
    if re.fullmatch(r"[0-9]+-[0-9]+", s):
        return "본번-부번"
    if re.fullmatch(r"산[0-9]+", s):
        return "산+본번"
    if re.fullmatch(r"산[0-9]+-[0-9]+", s):
        return "산+본번-부번"
    return "그 밖: " + re.sub(r"[0-9]", "9", s)


def umd_form(s):
    if s == "":
        return EMPTY
    t = s.split(" ")
    if len(t) == 1:
        if t[0].endswith("리"):
            return "리 한 낱말(읍면 없음)"
        if t[0].endswith("읍") or t[0].endswith("면"):
            return "읍·면 한 낱말"
        return "동·가 한 낱말"
    if len(t) == 2 and (t[0].endswith("읍") or t[0].endswith("면")) and t[1].endswith("리"):
        return "읍면 리 두 낱말"
    return "그 밖"


def dup_key(r):
    return (r.api, cid(r), r.y, r.m, r.d, r.floor, r.area, r.amt)


SUMMARY_KEYS = (
    "거래(다듬음)", "뺌(합)", "도로명주소로 정할 거래: 리 겹침", "도로명주소로 정할 거래: 능동 구 정하기",
    "법정동 이름 빔", "법정동 이름 맞지 않음", "읍면 없는 리(모두)", "읍면 없는 리, 아파트 매매 umdCd 빔",
    "읍면 없는 리, umdCd가 이력에 없음", "현재 코드가 둘 이상(능동 밖)", "읍·면 이름만 온 거래(읍·면 행에 맞춤)",
    "옛 이름(사곳리·오산동)으로 온 거래", "해제 여부 그 밖의 값", "landCd 그 밖의 값", "landCd와 지번 앞 산이 다름(1·2)",
    "계약 연월이 묶음 달과 다름", "층을 못 읽음(거래는 둠)", "건축년도를 못 읽거나 1900 미만(거래는 둠)",
    "월세가 0보다 작음", "코드 사이 같은 거래(지우는 거래)", "현재 시군구가 응답 코드와 다름(개편 없는 코드·새 코드)",
    "매매 거래금액이 0 이하", "보증금이 0보다 작음", "보증금과 월세가 모두 0(전세로 침)", "보증금이 0인 월세",
    "층이 0 이하(거래는 둠, 층 고르기에서만 빠짐)", "건축년도가 2026보다 큼(거래는 둠)", "계약월이 1~12 밖", "계약일이 1~31 밖",
)


def count_all(snap, H, codes, months, umdcd_base="sgg"):
    """값 분포 표의 줄 목록(정렬됨)과 읽기 확인 결과를 낸다."""
    cl = Cleaner(H, umdcd_base=umdcd_base)
    rows = Counter()
    summ = Counter({k: 0 for k in SUMMARY_KEYS})

    def add(item, api, code, value, n=1):
        rows[(item, api, code, str(value))] += n

    summary, problems = snap.check(codes, months)
    for k, v in summary.items():
        add("0 읽기 확인", "", "", k, v)
    add("0 읽기 확인", "", "", "문제", len(problems))
    for why, key in problems:
        add("0 읽기 확인 문제", key[0], key[1], f"{key[2]} {why}")

    cand_of = {}
    for c in codes:
        cand_of[c] = set(H.candidates(c)) if H.rows.get(c + "00000") and H.is_current(c + "00000") else {c}
    old_codes = {c for c in codes if H.sgg_abolished(c) or H.sgg_upper_only(c)}

    within = defaultdict(list)  # (api, resp, 열쇠) -> [(쪽, ym, 원문 해시, 원래 이름, 쪽 안 순번)]
    page_size = Counter()  # (api, resp, ym, 쪽) -> 줄 수
    across = defaultdict(Counter)  # (api, 열쇠) -> {resp: 건수}
    nfc_changed = 0
    for api in code_sorted(APIS):
        trade = api in TRADE_APIS
        for code in codes:
            add("1 코드별 거래 수", api, code, "거래", 0)
            for ym in months:
                for page, item in snap.items((api, code, ym)):
                    pos = page_size[(api, code, ym, page)]
                    page_size[(api, code, ym, page)] += 1
                    add("1 코드별 거래 수", api, code, "거래", 1)
                    if any(unicodedata.normalize("NFC", v) != v for v in item.values()):
                        nfc_changed += 1
                    f = {k: norm(v) for k, v in item.items()}
                    if trade:
                        ct = f.get("cdealType", "")
                        add("2 해제 여부(cdealType)", api, "", _v(ct))
                        if ct not in ("", "O"):
                            summ["해제 여부 그 밖의 값"] += 1
                    jf = jibun_form(f.get("jibun", ""))
                    if api == "apt_trade":
                        lc = f.get("landCd", "")
                        add("3 landCd", api, "", _v(lc))
                        add("3 landCd x 지번 꼴", api, "", f"{_v(lc)} / {jf}")
                        if lc not in ("", "1", "2"):
                            summ["landCd 그 밖의 값"] += 1
                    add("4 지번 꼴", api, "", jf)
                    umd = f.get("umdNm", "")
                    uf = umd_form(umd)
                    add("5 읍면동 이름 꼴", api, code, uf)
                    if uf == "읍·면 한 낱말":
                        add("5 읍·면 한 낱말로 온 이름", api, code, umd)
                    if uf == "리 한 낱말(읍면 없음)":
                        summ["읍면 없는 리(모두)"] += 1
                    toks = umd.split(" ")
                    for nm in OLD_NEW:
                        if nm in toks:
                            add("6 옛 이름·새 이름(달별)", api, code, f"{ym} {nm}")
                    if any(t in RENAMES for t in toks):
                        summ["옛 이름(사곳리·오산동)으로 온 거래"] += 1
                    sgg = f.get("sggCd", "")
                    if code in REORG_MONTH:
                        when = "개편 전 달" if ym < REORG_MONTH[code] else "개편 뒤 달"
                        if sgg == code:
                            s = "sggCd = 응답 코드"
                        elif sgg in cand_of[code] and sgg in old_codes:
                            s = "sggCd = 옛 후보 코드"
                        else:
                            s = "sggCd 그 밖: " + _v(sgg)
                        add("7 다시 묶인 거래의 응답 코드", api, code, f"{when} / {s}")
                        if api in ("apt_trade", "apt_rent"):
                            seq = f.get("aptSeq", "")
                            pre = seq.split("-", 1)[0] if seq else ""
                            if pre == code:
                                a = "aptSeq 앞 = 응답 코드"
                            elif pre in cand_of[code] and pre in old_codes:
                                a = "aptSeq 앞 = 옛 후보 코드"
                            else:
                                a = "aptSeq 앞 그 밖: " + _v(pre)
                            add("7 다시 묶인 거래의 응답 코드", api, code, f"{when} / {a}")
                        if api == "apt_trade":
                            ucd = f.get("umdCd", "")
                            for base, label in ((sgg, "sggCd+umdCd"), (code, "응답 코드+umdCd")):
                                c10 = base + ucd
                                if c10 in H.rows and c10[5:] != "00000":
                                    st = "현존" if H.is_current(c10) else "폐지"
                                    hit = f"{label} 이력에 있음({st})"
                                else:
                                    hit = f"{label} 이력에 없음"
                                add("7 다시 묶인 거래의 응답 코드", api, code, f"{when} / {hit}")
                    elif sgg != code:
                        add("7 응답 코드와 다른 sggCd(개편 없는 코드)", api, code, _v(sgg))
                    res = cl.clean(api, code, ym, page, item)
                    if isinstance(res, Drop):
                        add("8 다듬기 결과", api, code, "뺌: " + res.reason)
                        summ["뺌(합)"] += 1
                        if res.reason == "법정동 이름 맞지 않음":
                            add("8 이력에 없는 법정동 이름", api, code, umd)
                            summ["법정동 이름 맞지 않음"] += 1
                        elif res.reason == "법정동 이름 빔":
                            summ["법정동 이름 빔"] += 1
                        elif res.reason == "읍면 없는 리, umdCd 빔":
                            summ["읍면 없는 리, 아파트 매매 umdCd 빔"] += 1
                        elif res.reason == "읍면 없는 리, umdCd가 이력에 없음":
                            summ["읍면 없는 리, umdCd가 이력에 없음"] += 1
                        elif res.reason == "현재 코드가 둘 이상":
                            summ["현재 코드가 둘 이상(능동 밖)"] += 1
                        elif res.reason == "월세가 0보다 작음":
                            summ["월세가 0보다 작음"] += 1
                        continue
                    if isinstance(res, Pending):
                        add("8 다듬기 결과", api, code, "도로명주소로 정함: " + res.why)
                        add("8 도로명주소로 정할 거래(이름)", api, code, f"{res.why} {res.fields[12]}")
                        summ["도로명주소로 정할 거래: " + res.why] += 1
                        continue
                    add("8 다듬기 결과", api, code, "다듬음")
                    summ["거래(다듬음)"] += 1
                    if res.sgg != code and code not in old_codes:
                        summ["현재 시군구가 응답 코드와 다름(개편 없는 코드·새 코드)"] += 1
                    if len(res.path.split(" ")) == 1 and (res.path.endswith("읍") or res.path.endswith("면")):
                        summ["읍·면 이름만 온 거래(읍·면 행에 맞춤)"] += 1
                    if res.old_name:
                        add("8 옛 이름을 현재 이름으로 바꿈", api, code, f"{res.raw_umd} -> {res.path}")
                    if f"{res.y:04d}{res.m:02d}" != ym:
                        summ["계약 연월이 묶음 달과 다름"] += 1
                    if res.floor is None:
                        summ["층을 못 읽음(거래는 둠)"] += 1
                    elif res.floor <= 0:
                        summ["층이 0 이하(거래는 둠, 층 고르기에서만 빠짐)"] += 1
                    if res.deal == "매매":
                        if res.amt[0] <= 0:
                            summ["매매 거래금액이 0 이하"] += 1
                    else:
                        if res.amt[0] < 0:
                            summ["보증금이 0보다 작음"] += 1
                        if res.amt == (0, 0):
                            summ["보증금과 월세가 모두 0(전세로 침)"] += 1
                        if res.amt[0] == 0 and res.amt[1] > 0:
                            summ["보증금이 0인 월세"] += 1
                    if res.build is not None and res.build > 2026:
                        summ["건축년도가 2026보다 큼(거래는 둠)"] += 1
                    if not 1 <= res.m <= 12:
                        summ["계약월이 1~12 밖"] += 1
                    if not 1 <= res.d <= 31:
                        summ["계약일이 1~31 밖"] += 1
                    if res.build is None:
                        add("8 건축년도를 못 읽거나 1900 미만(거래는 둠)", api, code, "건수")
                        summ["건축년도를 못 읽거나 1900 미만(거래는 둠)"] += 1
                    if api == "apt_trade" and f.get("landCd", "") in ("1", "2") and f.get("jibun", "").startswith("산") != (res.mtn == 1):
                        summ["landCd와 지번 앞 산이 다름(1·2)"] += 1
                    k = dup_key(res)
                    raw = hashlib.sha256(repr(sorted(item.items())).encode("utf-8")).digest()[:16]
                    within[(api, code, k)].append((page, ym, raw, res.raw_umd, pos))
                    across[(api, k)][code] += 1
    add("0 읽기 확인", "", "", "NFC로 바뀐 값이 있는 거래", nfc_changed)

    # 한 코드 안의 같은 거래(다섯째)
    for (api, code, _k), lst in within.items():
        if len(lst) < 2:
            continue
        add("9 한 코드 안의 같은 거래", api, code, "a 열쇠가 같은 묶음", 1)
        add("9 한 코드 안의 같은 거래", api, code, "b 그 묶음의 거래", len(lst))
        raws = Counter(x[2] for x in lst)
        same_raw = sum(n for n in raws.values() if n > 1)
        add("9 한 코드 안의 같은 거래", api, code, "c 그 가운데 응답 칸까지 같은 거래", same_raw)
        if len({x[3] for x in lst}) > 1:
            add("9 한 코드 안의 같은 거래", api, code, "d 이름이 달리 온 묶음", 1)
        if len({x[0] for x in lst}) > 1:
            add("9 한 코드 안의 같은 거래", api, code, "e 같은 묶음의 다른 쪽에 나뉜 묶음", 1)
            split_raw = Counter(x[2] for x in lst)
            spread = [h for h, n in split_raw.items() if n > 1 and len({y[0] for y in lst if y[2] == h}) > 1]
            if spread:
                add("9 한 코드 안의 같은 거래", api, code, "f 그 가운데 응답 칸까지 같은 거래가 다른 쪽에 나뉜 묶음", 1)
                # 쪽이 겹쳐 온 것이면 앞 쪽 끝과 다음 쪽 처음에 같이 있다(쪽마다 1000줄, 경계 50줄로 본다)
                edge = False
                for h in spread:
                    at = [(y[1], y[0], y[4]) for y in lst if y[2] == h]
                    for ym1, p1, i1 in at:
                        for ym2, p2, i2 in at:
                            if ym1 == ym2 and p2 == p1 + 1 and i1 >= page_size[(api, code, ym1, p1)] - EDGE and i2 < EDGE:
                                edge = True
                if edge:
                    add("9 한 코드 안의 같은 거래", api, code, f"g 그 가운데 앞 쪽 끝 {EDGE}줄과 다음 쪽 처음 {EDGE}줄에 같이 있는 묶음", 1)
    # 코드 사이 같은 거래
    for (api, _k), per in across.items():
        if len(per) < 2:
            continue
        key = "+".join(code_sorted(per))
        add("10 코드 사이 같은 거래", api, key, "열쇠", 1)
        gone = sum(per.values()) - max(per.values())
        add("10 코드 사이 같은 거래", api, key, "지우는 거래(합 - 코드별 큰 값)", gone)
        summ["코드 사이 같은 거래(지우는 거래)"] += gone
    # 41590
    for api in code_sorted(APIS):
        add("11 41590 거래 수(36달)", api, "41590", "거래", rows.get(("1 코드별 거래 수", api, "41590", "거래"), 0))
    for k, v in summ.items():
        add("12 요약", "", "", k, v)

    out = [[it, api, code, value, n] for (it, api, code, value), n in rows.items()]
    out = code_sorted(out, key=lambda r: f"{r[0]}\x00{r[1]}\x00{r[2]}\x00{r[3]}")
    return out, summary, problems


def write(path, rows):
    write_csv(path, HEADER, rows)
