# -*- coding: utf-8 -*-
"""순서 11b 채점 v1의 글 검사(순서 11b 설계 4판 4.2): 한글 수사 사전 v1, 기간 말, 자리표시자 꼴, 방향 자리 뒤 말의 갈래.

- score가 제출의 원래 글(그리기 전 text)로 부른다. 기록의 findings는 어느 말이 걸렸는지 남기지 않아 글에서 다시 센다.
- 11b-2의 자바 검사기(Checker·Numerals v1)도 같은 규칙을 같은 예문 묶음(tests/data/numerals_v1.json)으로 시험한다.
- 글은 NFC로 바꾸지 않는다(검사기와 그리기가 원래 글을 그대로 보듯이). 빈칸은 자바 정규식 \\s(유니코드 꼴 아님)와 같은 여섯 자다.
- 수사 v1은 바른 자리표시자를 빈칸 하나로 바꾼 글에서 센다(자리표시자 이름은 글이 아니다). v0은 자바 v0처럼 원래 글에서 센다.
- 바른 자리표시자의 이름 길이는 기록의 검사기 판을 따른다(순서 11b-2): 검사기 v0 기록(시운전)은 한글 1~3자, 검사기 v1 기록(11c부터)은 1~6자다.
- 돌려주는 것은 수와 길이뿐이다. 걸린 말(답 글의 조각)은 돌려주지 않는다.
"""
import re
from fractions import Fraction

SP = "[ \\t\\n\\x0b\\f\\r]"
PLACEHOLDER = re.compile(r"\{([가-힣]{1,3})(\.방향)?\}")  # 검사기 v0(score.PLACEHOLDER)
PLACEHOLDERS = {3: PLACEHOLDER, 6: re.compile(r"\{([가-힣]{1,6})(\.방향)?\}")}  # 이름 길이 상한 → 꼴(6은 자바 KoText.PLACEHOLDER와 같다)

# ---------------------------------------------------------------- v0: 자바 Numerals v0 그대로(11a의 수와 잇는 데만 쓴다)
V0_NATIVE = "한|두|세|네|석|넉|다섯|여섯|일곱|여덟|아홉|열|스무|스물|서른|마흔|쉰|반"
V0_SINO_COMPOUND = "[일이삼사오육칠팔구][십백천만억][일이삼사오육칠팔구십백천만억]*|[십백천만억][일이삼사오육칠팔구십백천만억]+"
V0_SINO_SINGLE = "삼|사|오|육|칠|팔|구"
V0_UNIT = "배|곳|건|개월|개|달|년|층|평|퍼센트|프로|만원|억원|원|채|가구|세대"
V0 = re.compile(f"(?<![가-힣])(?:(?:{V0_NATIVE}|{V0_SINO_COMPOUND}){SP}?|(?:{V0_SINO_SINGLE}){SP})(?:{V0_UNIT})")

# ---------------------------------------------------------------- v1 사전
ONES = {"한": 1, "두": 2, "세": 3, "석": 3, "네": 4, "넉": 4, "다섯": 5, "여섯": 6, "일곱": 7, "여덟": 8, "아홉": 9}
TENS = {"열": 10, "스무": 20, "스물": 20, "서른": 30, "마흔": 40, "쉰": 50, "예순": 60, "일흔": 70, "여든": 80, "아흔": 90}
APPROX = ("한두", "두세", "두어", "서너", "너덧", "너댓", "네댓", "대여섯", "예닐곱")  # 어림수: 값이 하나로 정해지지 않는다
SINO_DIGIT = {"일": 1, "이": 2, "삼": 3, "사": 4, "오": 5, "육": 6, "칠": 7, "팔": 8, "구": 9}
SINO_SMALL = {"십": 10, "백": 100, "천": 1000}
SINO_BIG = {"만": 10_000, "억": 100_000_000}
UNITS = ("배", "곳", "건", "개월", "개", "달", "년", "해", "층", "평", "제곱미터", "㎡", "퍼센트", "프로", "만원", "억원", "원", "채", "가구", "세대")
PERIOD_UNITS = {"달": 1, "개월": 1, "년": 12, "해": 12}  # 기간 말의 단위와 한 단위의 달 수


def _alt(words):
    """긴 말부터(같은 곳에서 짧은 말이 먼저 걸려 단위를 자르지 않게: 개월·개)."""
    return "|".join(sorted(words, key=lambda w: (-len(w), w)))


NATIVE = f"(?:{_alt(TENS)})(?:{_alt(APPROX)}|{_alt(ONES)})?|{_alt(APPROX)}|{_alt(ONES)}|반"
UNIT = _alt(UNITS)
V1 = re.compile(f"(?<![가-힣])(?:(?:{NATIVE}|{V0_SINO_COMPOUND}|[십백천]){SP}?(?:{UNIT})|(?:{V0_SINO_SINGLE}){SP}(?:{UNIT})|일{SP}?년)")
SPACE = re.compile(SP)
BRACES = re.compile(r"\{([^{}]*)\}")
YM = re.compile(r"[0-9]{4}(?:0[1-9]|1[0-2])")
HANGUL = re.compile(r"[가-힣]+")

# 방향 자리 바로 뒤 말의 갈래(순서 11b 설계 4판 5.2 표). 차례대로 본다.
AFTER_KINDS = ("이다", "입니다", "이고", "이며", "편", "고", "문장부호·끝", "표에 없음")
AFTER_WORDS = ("입니다", "이다", "이고", "이며")
END_MARKS = frozenset(".,!?·()~-\n\r")


def sino_value(t):
    """한자어 수(일·이·…·십·백·천·만·억을 이은 것)의 값."""
    total = section = digit = 0
    for ch in t:
        if ch in SINO_DIGIT:
            digit = SINO_DIGIT[ch]
        elif ch in SINO_SMALL:
            section += (digit or 1) * SINO_SMALL[ch]
            digit = 0
        else:
            total += ((section + digit) or 1) * SINO_BIG[ch]
            section = digit = 0
    return total + section + digit


def value_of(token):
    """수사 낱말의 값(분수). 어림수는 None."""
    if token in APPROX:
        return None
    if token == "반":
        return Fraction(1, 2)
    if token in ONES:
        return Fraction(ONES[token])
    for t in sorted(TENS, key=len, reverse=True):
        if token.startswith(t):
            rest = token[len(t):]
            if rest == "":
                return Fraction(TENS[t])
            if rest in ONES:
                return Fraction(TENS[t] + ONES[rest])
            return None  # 열두세 같은 어림
    return Fraction(sino_value(token))


def split_match(m):
    """걸린 말을 (수사 낱말, 단위)로 가른다. 단위는 끝에 오는 가장 긴 단위다."""
    s = m.group()
    unit = next(u for u in sorted(UNITS, key=len, reverse=True) if s.endswith(u))
    return SPACE.sub("", s[:-len(unit)]), unit


def months_of(start, end):
    """YYYYMM 두 끝을 넣어 센 달 수(202511~202610 = 12). 꼴이 아니거나 끝이 앞서면 None."""
    if not (isinstance(start, str) and isinstance(end, str) and YM.fullmatch(start) and YM.fullmatch(end)):
        return None
    a = int(start[:4]) * 12 + int(start[4:])
    b = int(end[:4]) * 12 + int(end[4:])
    return b - a + 1 if b >= a else None


def claim_months(inp):
    """제출의 통계 claim(stat_claims)마다의 기간 길이(달) 모음."""
    out = set()
    cs = inp.get("stat_claims") if isinstance(inp, dict) else None
    for c in cs if isinstance(cs, list) else []:
        if isinstance(c, dict):
            n = months_of(c.get("start_month"), c.get("end_month"))
            if n is not None:
                out.add(n)
    return out


def claim_slots(inp):
    out = set()
    for key in ("stat_claims", "value_claims"):
        cs = inp.get(key) if isinstance(inp, dict) else None
        for c in cs if isinstance(cs, list) else []:
            if isinstance(c, dict) and isinstance(c.get("slot"), str):
                out.add(c["slot"])
    return out


def after_kind(rest):
    """방향 자리 바로 뒤 글의 갈래(5.2 표)."""
    if rest.strip(" \t") == "" or rest[0] in END_MARKS:
        return "문장부호·끝"
    for w in AFTER_WORDS:
        if rest.startswith(w):
            return w
    if rest.startswith(" 편") or rest.startswith("편"):
        return "편"
    if rest.startswith("고"):
        return "고"
    return "표에 없음"


def scan(text, inp, n=3):
    """원래 글 하나를 본다. inp는 그 제출의 input(claim의 기간과 slot을 쓴다). n은 바른 자리표시자 이름의 길이 상한(3이나 6)이다.

    - form: 바른 자리표시자를 다 지운 뒤 '{'나 '}'가 남음(PLACEHOLDER_FORM)
    - truncated: 남은 중괄호 묶음 가운데, 안의 이름(끝 '.방향'은 뗌)이 n+1자 이상 한글이고 앞 1~n자가 그 제출의 claim slot과 같은 것이 있음
    - periods: 기간 말마다 [길이(달, 셀 수 없으면 None), 같은 길이의 통계 claim이 있는가]
    - numerals: 기간 말이 아닌 수사 v1의 수
    - v0: 수사 v0(자바 v0과 같은 식, 원래 글)의 수
    - after: 방향 자리 바로 뒤 말의 갈래 → 수
    """
    if not isinstance(text, str):
        text = ""
    ph = PLACEHOLDERS[n]
    rest = ph.sub("", text)
    form = "{" in rest or "}" in rest
    slots = claim_slots(inp)
    truncated = False
    for g in BRACES.findall(rest):
        name = g[:-3] if g.endswith(".방향") else g
        if len(name) > n and HANGUL.fullmatch(name) and any(name.startswith(s) for s in slots if 1 <= len(s) <= n):
            truncated = True
    lengths = claim_months(inp)
    periods, numerals = [], 0
    for m in V1.finditer(ph.sub(" ", text)):
        token, unit = split_match(m)
        if unit not in PERIOD_UNITS:
            numerals += 1
            continue
        v = value_of(token)
        n = v * PERIOD_UNITS[unit] if v is not None else None
        n = int(n) if n is not None and n.denominator == 1 and n >= 1 else None
        periods.append([n, n is not None and n in lengths])
    after = {}
    for m in ph.finditer(text):
        if m.group(2):
            k = after_kind(text[m.end():])
            after[k] = after.get(k, 0) + 1
    return {"form": form, "truncated": truncated, "periods": periods, "numerals": numerals, "v0": len(V0.findall(text)),
            "after": {k: after[k] for k in AFTER_KINDS if k in after}}
