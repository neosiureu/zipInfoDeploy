# -*- coding: utf-8 -*-
"""순서 11b-2 글 검사 v1(설계 b판 5.3): 그린 글(제출 사건의 rendered)을 본다. 채점 v2가 쓴다.

- 자바 ai-core KoText.check와 같은 규칙이다. 같은 예문 묶음(tests/data/text_v1.json의 checks)으로 둘 다 시험한다.
- 코드는 BRACE, UNIT_NO_VALUE, GARBLED, QMARK, REGISTER, FORMAT 차례로, 코드마다 많아야 하나다. 처방 글은 내지 않는다(코드만).
- 자바 정규식과 같게 맞춘 곳
    - 빈칸 \\s는 자바 기본(유니코드 꼴 아님)과 같은 여섯 자다. 정규식 $는 자바처럼 끝이나 끝의 줄 끝 하나 앞이다.
    - strip·stripTrailing은 자바 Character.isWhitespace의 25자만 뗀다. 숫자(소수점 셈)는 자바 Character.isDigit처럼 유니코드 Nd다.
    - 자바는 UTF-16 단위로, 파이썬은 코드 포인트로 센다. BMP 밖 글자(그림 글자 등)가 든 글은 소수점 셈이 다를 수 있다.
      제출 text는 TEXT_PATTERN(한글·로마자·문장부호)이라 그 글자는 매물 이름 자리에서만 들어온다.
- 로마자 낱말은 매물 칸(stockName·stockAddress)과 질문 글에 있는 것을 받는다(상대 b판 검토 (2)).
- UNIT_NO_VALUE는 수사 낱말 뒤의 단위(수사 사전 v1이 그린 글에서 찾은 말 안)를 세지 않는다. 값이 말로 있어 NUMERAL이 잡는다.
- 글은 NFC로 바꾸지 않는다(검사기가 그린 글을 그대로 보듯이).
"""
import re

from . import numerals as numerals_mod

SP = "[ \\t\\n\\x0b\\f\\r]"  # 자바 \s
JEND = "(?:\\r\\n|[\\n\\r\\x85\\u2028\\u2029])?\\Z"  # 자바 $ (MULTILINE 없음)
# 자바 Character.isWhitespace가 참인 25자(String.strip이 떼는 것). score.JAVA_SPACE와 같다.
JAVA_SPACE = frozenset(chr(c) for c in (*range(0x09, 0x0E), *range(0x1C, 0x20), 0x20, 0x1680, *range(0x2000, 0x2007),
                                        *range(0x2008, 0x200B), 0x2028, 0x2029, 0x205F, 0x3000))
CODES = ("BRACE", "UNIT_NO_VALUE", "GARBLED", "QMARK", "REGISTER", "FORMAT")

UNIT_NO_VALUE = re.compile(
    f"(?:^|(?<=[ \\t\\n\\x0b\\f\\r(]))(?<![^가-힣]이 )(?<!^이 )(?<![^가-힣]그 )(?<!^그 )(?:만원|억원|원|퍼센트|프로|%|㎡|제곱미터|평|개월|개|건)"
    f"(?={JEND}|[ \\t\\n\\x0b\\f\\r,.)!?]|보다|과|와|을|를|이|가|은|는|으로|로|의|에|도|만)")
LATIN = re.compile("[A-Za-z]+")
SPACES = re.compile(f"{SP}{{3,}}")
QMARK = re.compile("\\?(?=[가-힣])")
FORMAT = re.compile("|".join((
    f"만원{SP}?만원", f"억원{SP}?원", f"만원{SP}?원(?![가-힣])", f"(?<=[0-9])원{SP}?원", f"(?<=[0-9])건{SP}?건", f"(?<=[0-9])층{SP}?층",
    f"(?<=[0-9])평{SP}?평", f"%{SP}?(?:%|퍼센트|프로)", f"㎡{SP}?(?:㎡|제곱미터)", f"퍼센트{SP}?퍼센트", f"개월{SP}?(?:개월|달)",
    "(?<![0-9,])(?:[0-9]{2,3},[0-9]{3}|[0-9]{1,3}(?:,[0-9]{3}){2,})(?:\\.[0-9]+)?만원",
    "(?<![0-9,])(?:[0-9]{2,3},[0-9]{3}|[0-9]{1,3}(?:,[0-9]{3}){2,})원",
    "(?<=[0-9])(?:만원|억원|원|건|층|평|년)(?:로|를|가|는|와)(?![가-힣])",
    "(?<=[0-9])(?:%|㎡)(?:으로|을|은|과|이)(?![가-힣])",
    "(?<=[0-9])개월으로(?![가-힣])",
    f"(?:비싸다|싸다|같다|비쌉니다|쌉니다|같습니다)(?:이고|입니다|이다|이며|{SP}?편)")))
CLAUSE_PLAIN = re.compile("([가-힣]+다),")


def jstrip(s):
    i, j = 0, len(s)
    while i < j and s[i] in JAVA_SPACE:
        i += 1
    while j > i and s[j - 1] in JAVA_SPACE:
        j -= 1
    return s[i:j]


def jrstrip(s):
    j = len(s)
    while j > 0 and s[j - 1] in JAVA_SPACE:
        j -= 1
    return s[:j]


def hangul(c):
    return "가" <= c <= "힣"


def batchim(c):
    """받침 번호(0 없음, 8 ㄹ, 17 ㅂ …). 한글 음절이 아니면 -1."""
    return (ord(c) - 0xAC00) % 28 if hangul(c) else -1


def unit_without_value(rendered):
    """값 없는 단위 말이 있는가. 수사 낱말 뒤의 단위(한 건, 다섯 건)는 값이 말로 있는 것이라 세지 않는다(NUMERAL이 잡는다).
    수사는 수사 사전 v1(numerals.V1)이 그린 글에서 찾은 말이다."""
    spans = [m.span() for m in numerals_mod.V1.finditer(rendered)]
    for m in UNIT_NO_VALUE.finditer(rendered):
        a, b = m.span()
        if not any(x <= a and b <= y for x, y in spans):
            return True
    return False


def latin_words(*texts):
    """로마자 낱말 모음(매물 칸·질문 글에 있는 것은 받는다). None은 건너뛴다."""
    out = set()
    for t in texts:
        if isinstance(t, str):
            out.update(LATIN.findall(t))
    return out


def formal_end(w):
    """합니다체 끝인가(5.3 REGISTER의 셈). w는 문장 끝의 한글 덩이."""
    if not w:
        return False
    if (w.endswith("니다") or w.endswith("니까")) and len(w) >= 3:
        return batchim(w[-3]) == 17
    return w.endswith("십시오") or w.endswith("세요")


def _add_end(ends, sentence):
    s = jstrip(sentence)
    if not s:
        return
    while s.endswith(")"):
        i = s.rfind("(")
        if i < 0:
            break
        s = jstrip(s[:i])
    j = len(s)
    while j > 0 and hangul(s[j - 1]):
        j -= 1
    ends.append(s[j:])


def sentence_ends(text):
    """문장마다 끝 한글 덩이(끝의 괄호 묶음은 뗀다). 소수점(숫자 사이 마침표)과 한글 바로 앞의 ?는 문장 끝이 아니다."""
    ends, cur = [], []
    n = len(text)
    for i, c in enumerate(text):
        decimal = c == "." and 0 < i and i + 1 < n and text[i - 1].isdecimal() and text[i + 1].isdecimal()
        glyph = c == "?" and i + 1 < n and hangul(text[i + 1])
        if c in ".!?" and not decimal and not glyph:
            _add_end(ends, "".join(cur))
            cur = []
        else:
            cur.append(c)
    _add_end(ends, "".join(cur))
    return ends


def check(rendered, allowed_latin):
    """그린 글 하나의 코드 목록(CODES 차례, 코드마다 많아야 하나)."""
    out = []
    if "{" in rendered or "}" in rendered:
        out.append("BRACE")
    if unit_without_value(rendered):
        out.append("UNIT_NO_VALUE")
    junk = any(w not in allowed_latin for w in LATIN.findall(rendered))
    tail = jrstrip(rendered)
    if junk or SPACES.search(rendered) or tail.endswith("...") or tail.endswith("…") or "�" in rendered:
        out.append("GARBLED")
    if QMARK.search(rendered):
        out.append("QMARK")
    plain = any(not formal_end(w) for w in sentence_ends(rendered))
    if not plain:
        plain = any(not formal_end(m.group(1)) for m in CLAUSE_PLAIN.finditer(rendered))
    if plain:
        out.append("REGISTER")
    if FORMAT.search(rendered):
        out.append("FORMAT")
    return out
