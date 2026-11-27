# -*- coding: utf-8 -*-
"""순서 8a·8b·8c 채점기(순서 8 설계 d판 2·3·6·8절, 8b 설계 2판, 8c 설계 2판): 한 단계의 기록을 묶기 검사한 뒤 실행마다 값·규칙·판정을 매기고,
15.9 지표·진단·비용(8b, metrics.py)과 통계·보고 표(8c, stats.py)를 낸다.
순서 11b-1(설계 4판 4.2)의 채점 v1: 수사·기간 말·자리표시자 꼴은 기록의 findings 대신 글에서 다시 센다(numerals.py). 완전 정답은 정의 셋
(주 (다), 비교 (가)·(나))으로 내고, 판정을 넣을지는 채점 설정 full_with_judge로 정한다.
순서 11b-2(설계 b판 5.4)의 채점 v2: 규칙 v2 = 규칙 v1 + 5.3(그린 글의 여섯 코드는 제출 사건의 rendered에서 textcheck로 다시 세고,
DIFF_HIDDEN은 기록의 것). 기록의 검사기 판은 하네스 설정 version으로 고른다(v0 시운전 → 검사기 v0, 그 밖 → 검사기 v1).
채점 설정을 주지 않으면 검사기 v0 기록은 score_v1.json, 검사기 v1 기록은 score_v2.json으로 매긴다.

experiment 폴더에서 `python -X utf8 -B -m prep score harness/settings/<설정>.json`으로 돌린다.
같은 실수 표는 `python -X utf8 -B -m prep mistakes harness/settings/<설정>.json harness/frozen/mistakes_v0.json`으로 얼린다(8b 4.3).

- 묶기 검사(2.2)에 하나라도 걸리면 아무것도 쓰지 않고 3으로 끝난다. 설정·파일 읽기 오류는 2, 다 매기면 0이다.
  하네스 summary.tsv가 없거나 같은 셈과 다르면 그것도 묶기 검사다(8b 7절).
- 결과는 <files.out>/<stage>/score/에 runs.jsonl·summary.tsv·metrics.tsv·stats.tsv·report.md로 쓴다(runs 아래라 git 밖).
  질문·답·판정 이유·매물 이름은 쓰지 않는다.
- 화면에는 셈한 수, 실행 ID, 코드, 해시만 낸다.
- 표준 라이브러리만 쓴다. 값은 JSON 수를 Decimal로 읽어 분수로 견준다. 하네스 Summary와 같은 셈(시간)만 double을 그대로 따라 한다.
"""
import hashlib
import json
import os
import re
import shutil
import unicodedata
from decimal import Decimal
from fractions import Fraction

from picker.common import read_csv

from . import answers as answers_mod
from . import listings as listings_mod
from . import numerals as numerals_mod
from . import questions as questions_mod
from . import textcheck as textcheck_mod

# 채점기 자신의 코드가 있는 experiment 폴더(채점 지문의 code는 지금 도는 코드로 센다)
CODE_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONFIG = os.path.join(CODE_ROOT, "prep", "score_v1.json")
CONFIG_V2 = os.path.join(CODE_ROOT, "prep", "score_v2.json")
LISTINGS = "frozen/example_listings_v1.csv"
OUT_DIR = "prep/out"
LISTINGS_INPUT = "listings_input_v1.json"
TYPES = ("T1", "T2", "T3", "T4", "T5")
SPLITS = ("연습", "본시험")
QUESTION_ID = re.compile(r"[a-z0-9]{1,8}")
RECENT = (answers_mod.months_back(12)[0], answers_mod.months_back(12)[-1])

# Java Character.isWhitespace가 참인 25자(String.strip이 떼는 것). 파이썬 str.strip은 U+0085·U+00A0·U+2007·U+202F를 더 뗀다.
JAVA_SPACE = frozenset(chr(c) for c in (*range(0x09, 0x0E), *range(0x1C, 0x20), 0x20, 0x1680, *range(0x2000, 0x2007),
                                        *range(0x2008, 0x200B), 0x2028, 0x2029, 0x205F, 0x3000))

# 근거 ID 꼴(처음부터 끝까지 맞춘다, 3.2)
P_BASIS = re.compile(r"S[1-9][0-9]*\.기준")
P_CONV_BASIS = re.compile(r"S[1-9][0-9]*\.환산기준")
P_DIFF = re.compile(r"C[1-9][0-9]*\.차이")
P_CONVERTED = re.compile(r"R[1-9][0-9]*\.환산보증금")
P_SAMPLE = re.compile(r"S[1-9][0-9]*\.표본수")
P_S = re.compile(r"(S[1-9][0-9]*)\.")
P_C = re.compile(r"(C[1-9][0-9]*)\.")
P_LISTING = re.compile(r"매물\.")
P_CALC = re.compile(r"[CRA][1-9][0-9]*\.")
PLACEHOLDER = re.compile(r"\{([가-힣]{1,3})(\.방향)?\}")
SENTENCE_END = re.compile(r"[.?!\n]")
SPACES = re.compile(r"\s+")

CONFIG_FIELDS = ("version", "unjudged", "full_with_judge", "direction", "mistakes", "intent_clauses", "judge_sample_exclude", "bootstrap",
                 "comparisons")
DIRECTION_FIELDS = ("words", "high_low")
HIGH_LOW_FIELDS = ("chars", "markers", "except", "window", "next_not")
UNJUDGED = ("stop", "fail")
MAX_DRAWS = 1_000_000
SAMPLE_DIR = re.compile(r"[A-Za-z0-9_][A-Za-z0-9_.-]*")  # 빼는 판정 표본 폴더 이름(단계 폴더 바로 아래)
# 채점 v1이 글에서 다시 세는 검사기 코드(순서 11b 설계 4판 4.2). 기록의 이 코드는 버리고 numerals.scan의 셈으로 채운다.
RECOUNTED = frozenset(("NUMERAL", "PERIOD_WORD", "PLACEHOLDER_FORM"))
# 채점 v2(순서 11b-2 설계 b판 5.4): 규칙 v2 = 규칙 v1 + 5.3의 코드. 그린 글의 여섯은 제출 사건의 rendered에서 다시 세고(textcheck),
# DIFF_HIDDEN은 기록의 것을 쓴다(claim과 원래 글로 보는 코드라 PLACEHOLDER·DIRECTION처럼). 규칙 v1은 기록의 5.3 코드를 보지 않는다.
TEXT_CODES = textcheck_mod.CODES
V2_CODES = frozenset(TEXT_CODES) | {"DIFF_HIDDEN"}
RULES = ("v1", "v2")


class Bad(Exception):
    """설정·파일을 읽지 못함(끝 코드 2)."""


class Bind(Exception):
    """묶기 검사에 걸림(끝 코드 3)."""


# ---------------------------------------------------------------- 작은 셈

def sha256(b):
    return hashlib.sha256(b).hexdigest().upper()


def lf(b):
    """CR LF를 LF로(홀로 있는 CR은 그대로). 하네스 Fingerprint.lf와 같다."""
    return b.replace(b"\r\n", b"\n")


def sha256_lf(path):
    """줄 끝을 LF로 맞춘 SHA-256(대문자). 하네스 Fingerprint.file·record_sha256과 같다."""
    with open(path, "rb") as f:
        return sha256(lf(f.read()))


def jstrip(s):
    i, j = 0, len(s)
    while i < j and s[i] in JAVA_SPACE:
        i += 1
    while j > i and s[j - 1] in JAVA_SPACE:
        j -= 1
    return s[i:j]


def nid(s):
    """근거 ID 정리: RunState.find처럼 NFC로 맞추고 앞뒤에서 Java 공백 25자만 뗀다."""
    return jstrip(unicodedata.normalize("NFC", s)) if isinstance(s, str) else ""


def number(v):
    """JSON 수(int·Decimal)를 분수로. 수가 아니면 None(참·거짓도 수가 아니다)."""
    if isinstance(v, bool) or not isinstance(v, (int, Decimal)):
        return None
    return Fraction(v)


def integer(v):
    return v if isinstance(v, int) and not isinstance(v, bool) else None


def provisional(start, end):
    """기간에 202609·202610이 들면 잠정이다(검사기와 같은 식)."""
    return start <= "202610" and end >= "202609"


def tree(root, rels):
    """하네스 Fingerprint.tree와 같은 꼴: "SHA-256(LF)  상대 경로(/)" 줄을 경로 차례로 이은 글의 SHA-256. 경로의 '\\'는 '/'로 맞춘다."""
    lines = sorted((rel, sha256_lf(os.path.join(root, *rel.split("/")))) for rel in (r.replace("\\", "/") for r in rels))
    return sha256("".join(f"{h}  {p}\n" for p, h in lines).encode("utf-8"))


def code_files(root=CODE_ROOT):
    """채점 지문의 코드: experiment/prep과 experiment/picker의 .py 모두(tests 폴더와 __pycache__는 뺀다). experiment에서 본 경로."""
    out = []
    for top in ("prep", "picker"):
        base = os.path.join(root, top)
        for d, dirs, files in os.walk(base):
            dirs[:] = [x for x in dirs if x not in ("tests", "__pycache__")]
            for f in files:
                if f.endswith(".py"):
                    out.append(os.path.relpath(os.path.join(d, f), root).replace(os.sep, "/"))
    return sorted(out)


def fingerprint(parts):
    """채점 지문: "이름  해시" 줄(LF)을 정한 차례로 이은 글의 SHA-256."""
    return sha256("".join(f"{k}  {v}\n" for k, v in parts).encode("utf-8"))


# ---------------------------------------------------------------- 읽기

def read_json(path, what, exact=False):
    """exact면 소수를 Decimal로 읽는다(단가표)."""
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.loads(f.read(), parse_float=Decimal) if exact else json.loads(f.read())
    except FileNotFoundError:
        raise Bad(f"{what}이 없다: {path}") from None
    except (OSError, ValueError) as e:
        raise Bad(f"{what}을 읽지 못했다: {path} ({type(e).__name__})") from None


def jsonl(raw, what):
    """기록·판정 파일의 줄 규칙(RunFile·JudgeFiles): 줄 끝 LF가 없는 마지막 줄은 없는 것으로 본다. 줄 끝 CR은 뗀다. 그 밖의 줄은 JSON 객체여야 한다."""
    end = raw.rfind(b"\n") + 1
    out = []
    for i, line in enumerate(raw[:end].split(b"\n"), 1):
        if line.endswith(b"\r"):
            line = line[:-1]
        if not line:
            continue
        try:
            n = json.loads(line.decode("utf-8"), parse_float=Decimal)
        except (UnicodeDecodeError, ValueError):
            raise Bind(f"{what}의 {i}번째 줄이 JSON이 아니다") from None
        if not isinstance(n, dict):
            raise Bind(f"{what}의 {i}번째 줄이 JSON 객체가 아니다")
        out.append(n)
    return out


def need(d, keys, what):
    if not isinstance(d, dict):
        raise Bad(f"{what}이 객체가 아니다")
    miss = [k for k in keys if k not in d]
    if miss:
        raise Bad(f"{what}에 없는 칸: {', '.join(miss)}")
    return d


def all_or_list(v, what):
    if v == "all":
        return []
    if not isinstance(v, list) or not v or not all(isinstance(x, str) for x in v):
        raise Bad(f"{what}는 \"all\"이나 ID 목록이다")
    return list(v)


def load_settings(path):
    """하네스 설정 파일에서 채점이 쓰는 칸만 읽는다(칸이 더 있어도 된다. 하네스가 따로 엄격히 본다)."""
    s = need(read_json(path, "하네스 설정", exact=True), ("version", "stage", "split", "files", "select", "block_size", "cache", "prices"), "하네스 설정")
    need(s["files"], ("variants", "questions", "cards", "out"), "설정의 files")
    sel = need(s["select"], ("variants", "questions", "reps"), "설정의 select")
    reps = sel["reps"]
    if not isinstance(reps, list) or not reps or any(integer(r) is None or not 1 <= r <= 99 for r in reps) or len(set(reps)) != len(reps):
        raise Bad("select.reps는 겹치지 않는 1~99의 정수 목록이다")
    if s["split"] not in SPLITS or integer(s["block_size"]) is None or s["block_size"] < 1:
        raise Bad("설정의 split이나 block_size가 맞지 않다")
    need(s["cache"], ("system", "conversation", "ttl"), "설정의 cache")
    from . import metrics as metrics_mod
    return {"version": s["version"], "stage": s["stage"], "split": s["split"], "files": s["files"], "block_size": s["block_size"],
            "variants": all_or_list(sel["variants"], "select.variants"), "questions": all_or_list(sel["questions"], "select.questions"),
            "reps": list(reps), "cache": s["cache"], "prices": metrics_mod.load_prices(s["prices"], "설정의 prices")}


def load_config(path):
    c = read_json(path, "채점 설정")
    if not isinstance(c, dict) or sorted(c) != sorted(CONFIG_FIELDS):
        raise Bad(f"채점 설정의 칸은 {'·'.join(CONFIG_FIELDS)}다")
    if c["version"] not in RULES:
        raise Bad("채점 설정 version은 v1이나 v2다(규칙 v1, 규칙 v2 = v1 + 5.3)")
    d = c["direction"]
    if not isinstance(d, dict) or sorted(d) != sorted(DIRECTION_FIELDS):
        raise Bad("채점 설정 direction의 칸은 words·high_low다")
    hl = d["high_low"]
    if not isinstance(hl, dict) or sorted(hl) != sorted(HIGH_LOW_FIELDS):
        raise Bad("채점 설정 direction.high_low의 칸이 다르다")
    texts = [d["words"], hl["chars"], hl["markers"], hl["except"]]
    if not all(isinstance(t, list) and t and all(isinstance(w, str) and w for w in t) for t in texts) \
            or integer(hl["window"]) is None or hl["window"] < 0 or not isinstance(hl["next_not"], str):
        raise Bad("채점 설정 direction의 낱말 목록이 맞지 않다")
    if c["unjudged"] not in UNJUDGED:
        raise Bad("채점 설정 unjudged는 stop이나 fail이다")
    if c["mistakes"] is not None and not (isinstance(c["mistakes"], str) and c["mistakes"]):
        raise Bad("채점 설정 mistakes는 null이나 같은 실수 표의 자리다")
    if not (isinstance(c["intent_clauses"], str) and c["intent_clauses"]):
        raise Bad("채점 설정 intent_clauses는 의도 절 표의 자리다")
    # 11b-1: 완전 정답에 판정을 넣는지(prereg_judge 2.1을 못 넘으면 false), 판정 표본에서 뺄 옛 표본(단계 → 그 단계 폴더 아래 폴더 이름)
    if not isinstance(c["full_with_judge"], bool):
        raise Bad("채점 설정 full_with_judge는 true나 false다")
    ex = c["judge_sample_exclude"]
    folder = lambda x: isinstance(x, str) and SAMPLE_DIR.fullmatch(x) is not None and x != "judge_sample"
    if not isinstance(ex, dict) or not all(isinstance(k, str) and k and isinstance(v, list) and v and all(folder(x) for x in v)
                                           and len(set(v)) == len(v) for k, v in ex.items()):
        raise Bad("채점 설정 judge_sample_exclude는 {단계: [옛 판정 표본 폴더 이름, …]}다(이름은 영문·숫자·_·.·-, judge_sample은 쓰지 않는다)")
    # 8c: 부트스트랩과 비교 목록(판 ID가 판 설정 파일에 있는지는 prepare에서 본다)
    b = c["bootstrap"]
    if not isinstance(b, dict) or sorted(b) != ["draws", "purpose"] or integer(b["draws"]) is None or not 1 <= b["draws"] <= MAX_DRAWS \
            or not isinstance(b["purpose"], str) or not b["purpose"].strip():
        raise Bad(f"채점 설정 bootstrap은 draws(1~{MAX_DRAWS}의 정수)·purpose(글)다")
    cp = c["comparisons"]
    pair = lambda p: isinstance(p, list) and len(p) == 2 and all(isinstance(x, str) and x for x in p) and p[0] != p[1]
    if not isinstance(cp, dict) or sorted(cp) != ["aux", "main"] or not pair(cp["main"]) or not isinstance(cp["aux"], list) \
            or not all(pair(p) for p in cp["aux"]):
        raise Bad("채점 설정 comparisons는 main(서로 다른 판 ID 둘)·aux(그런 짝의 목록)다")
    pairs = [tuple(cp["main"])] + [tuple(p) for p in cp["aux"]]
    if len(set(pairs)) != len(pairs):
        raise Bad("채점 설정 comparisons에 같은 비교가 둘이다")
    nfc = lambda xs: [unicodedata.normalize("NFC", x) for x in xs]
    return {"version": c["version"], "unjudged": c["unjudged"], "full_with_judge": c["full_with_judge"],
            "judge_sample_exclude": {k: list(v) for k, v in ex.items()}, "words": nfc(d["words"]), "chars": nfc(hl["chars"]),
            "markers": nfc(hl["markers"]), "except": nfc(hl["except"]), "window": hl["window"],
            "next_not": unicodedata.normalize("NFC", hl["next_not"]), "mistakes": c["mistakes"], "intent_clauses": c["intent_clauses"],
            "bootstrap": {"draws": b["draws"], "purpose": b["purpose"]}, "comparisons": {"main": pairs[0], "aux": pairs[1:]}}


def checker_of(settings_version):
    """기록의 검사기 판(11b-1 상대 검토 (3)): 하네스 설정 version이 v0이면 검사기 v0(시운전), 아니면 검사기 v1(11c부터).
    검사기 v1 기록은 자리표시자 이름이 1~6자이고, 제출 사건에 그린 글(rendered)과 5.3의 코드가 있다."""
    return "v0" if settings_version == "v0" else "v1"


def default_config(settings_version):
    """채점 설정을 주지 않았을 때: 검사기 v0 기록은 채점 v1(score_v1.json), 검사기 v1 기록은 채점 v2(score_v2.json)."""
    return CONFIG if checker_of(settings_version) == "v0" else CONFIG_V2


def listing_latin(path, questions):
    """문항마다 글 검사가 받는 로마자 낱말: 매물 입력 파일(하네스가 읽는 것)의 stockName·stockAddress와 문항 글(자바 Checker와 같다)."""
    d = read_json(path, "매물 입력 파일")
    fields = {}
    for x in d if isinstance(d, list) else []:
        if isinstance(x, dict) and isinstance(x.get("key"), str) and isinstance(x.get("fields"), dict):
            fields[x["key"]] = x["fields"]
    out = {}
    for q in questions:
        f = fields.get(q["key"])
        if f is None:
            raise Bad(f"매물 입력 파일에 문항 {q['id']}의 매물 {q['key']}이 없다")
        out[q["id"]] = textcheck_mod.latin_words(f.get("stockName"), f.get("stockAddress"), q["text"])
    return out


def load_variants(path):
    d = need(read_json(path, "판 설정 파일"), ("version", "variants"), "판 설정 파일")
    if not isinstance(d["variants"], list) or not d["variants"]:
        raise Bad("판 설정 파일에 판이 없다")
    out = []
    for v in d["variants"]:
        need(v, ("id", "name", "model", "parts"), "판")
        if not isinstance(v["parts"], list) or not all(p in ("⓪", "②", "③", "④", "⑤") for p in v["parts"]) or len(set(v["parts"])) != len(v["parts"]):
            raise Bad("판의 parts는 ⓪·②·③·④·⑤ 가운데 겹치지 않는 목록이다")
        out.append({"id": v["id"], "name": v["name"], "model": v["model"], "parts": list(v["parts"])})
    if len({v["id"] for v in out}) != len(out):
        raise Bad("판 설정 파일에 같은 판 ID가 둘이다")
    return out


def load_questions(path):
    """문항 파일(하네스 Question.parse와 같은 검사)."""
    d = read_json(path, "문항 파일")
    if not isinstance(d, dict) or sorted(d) != ["questions", "version"] or not isinstance(d["questions"], list) or not d["questions"]:
        raise Bad("문항 파일의 꼴이 아니다")
    out, ids = [], set()
    for q in d["questions"]:
        if not isinstance(q, dict) or sorted(q) != sorted(("id", "key", "qtype", "split", "text")):
            raise Bad("문항의 칸이 다르다")
        if not isinstance(q["id"], str) or not QUESTION_ID.fullmatch(q["id"]) or q["id"] in ids:
            raise Bad("문항 id는 영문 소문자·숫자 1~8자이고 겹치지 않는다")
        ids.add(q["id"])
        if q["qtype"] not in TYPES or q["split"] not in SPLITS or not isinstance(q["text"], str) \
                or unicodedata.normalize("NFC", q["text"]) != q["text"]:
            raise Bad(f"문항 {q['id']}의 유형·나눔·글이 맞지 않다")
        out.append(dict(q))
    return d["version"], out


def load_metas(path, pin):
    """매물 파일: 키(L01부터 줄 차례)마다 단지ID·거래·층·나눔. pin이 있으면 SHA-256을 대 본다."""
    if not os.path.exists(path):
        raise Bad(f"매물 파일이 없다: {path}")
    if pin is not None:
        with open(path, "rb") as f:
            got = sha256(f.read())
        if got != pin:
            raise Bad(f"매물 파일의 SHA-256이 다르다: {got[:8]}… (와야 할 것 {pin[:8]}…)")
    try:
        header, rows = read_csv(path)
    except (OSError, ValueError) as e:
        raise Bad(f"매물 파일을 읽지 못했다 ({type(e).__name__})") from None
    cols = ("complex_id", "deal", "layer", "split")
    if any(c not in header for c in cols):
        raise Bad("매물 파일에 없는 칸이 있다")
    idx = {h: i for i, h in enumerate(header)}
    return {"L%02d" % n: {c: r[idx[c]] for c in cols} for n, r in enumerate(rows, 1)}


def forbidden_words(path):
    """K10 사용법 1의 말(순서 4). 카드 줄을 그대로 읽는다."""
    try:
        with open(path, "r", encoding="utf-8") as f:
            text = unicodedata.normalize("NFC", f.read())
    except OSError:
        raise Bad(f"카드 K10이 없다: {path}") from None
    part = text.split("## 사용법", 1)
    m = re.search(r"^1\. 쓰지 않는 말: (.+?)\s*$", part[1], re.M) if len(part) == 2 else None
    if not m:
        raise Bad("K10 사용법 1의 줄(쓰지 않는 말)을 찾지 못했다")
    words = [w.strip() for w in m.group(1).split(",") if w.strip()]
    if not words:
        raise Bad("K10 사용법 1의 말이 비었다")
    return words


# ---------------------------------------------------------------- 계획(하네스 Plan.of와 같은 셈)

def make_plan(s, variants, questions, metas):
    for q in questions:
        m = metas.get(q["key"])
        if m is None:
            raise Bad(f"문항 {q['id']}의 매물이 매물 파일에 없다")
        if m["split"] != q["split"]:
            raise Bad(f"문항 {q['id']}의 나눔이 매물의 나눔과 다르다")
    ids = [v["id"] for v in variants]
    for v in s["variants"]:
        if v not in ids:
            raise Bad(f"판 설정 파일에 없는 판이다: {v}")
    vs = [v for v in variants if not s["variants"] or v["id"] in s["variants"]]
    by = {q["id"]: q for q in questions}
    for qid in s["questions"]:
        if qid not in by:
            raise Bad(f"문항 파일에 없는 문항이다: {qid}")
        if by[qid]["split"] != s["split"]:
            raise Bad(f"설정의 나눔과 다른 문항이다: {qid}")
    qs = [q for q in questions if q["split"] == s["split"] and (not s["questions"] or q["id"] in s["questions"])]
    if not qs:
        raise Bad(f"돌릴 문항이 없다(나눔 {s['split']})")
    chunks = []
    for v in vs:
        runs = []
        for rep in s["reps"]:
            for q in qs:
                runs.append({"id": f"{v['id']}-{q['id']}-r{rep}", "variant": v, "question": q, "rep": rep,
                             "block": len(runs) // s["block_size"] + 1})
        chunks.append([runs[i:i + s["block_size"]] for i in range(0, len(runs), s["block_size"])])
    out = []
    for b in range(max(len(c) for c in chunks)):
        for c in chunks:
            if b < len(c):
                out.extend(c[b])
    return out


# ---------------------------------------------------------------- plan.txt

PLAN_L1 = re.compile(r"단계 (\S+), 나눔 (\S+), 설정 (\S+)")
PLAN_L2 = re.compile(r"판 (\d+)개\(([^()]*)\), 문항 (\d+)개, 회차 \[([0-9, ]*)\], 블록 (\d+), 동시 (\d+)")
PLAN_L5 = re.compile(r"실행 (\d+)개: 끝난 것 (\d+), 시작한 것 (\d+), 남은 것 (\d+)\. 쓴 돈 \$.*")
PLAN_PRINT = re.compile(r"  (\S+) ([0-9A-F]{64})")


def read_plan_txt(path):
    """하네스 plan이 쓴 plan.txt(Main.planText)를 엄격하게 읽는다. 못 읽으면 Bind."""
    if not os.path.exists(path):
        raise Bind("plan.txt가 없다. 채점 바로 전에 하네스 plan을 그 설정으로 친다")
    with open(path, "rb") as f:
        raw = lf(f.read())
    try:
        lines = raw.decode("utf-8").split("\n")
    except UnicodeDecodeError:
        raise Bind("plan.txt가 UTF-8이 아니다") from None
    while lines and lines[-1] == "":
        lines.pop()
    bad = Bind("plan.txt를 읽지 못했다(하네스 plan이 쓴 꼴이 아니다)")
    if len(lines) < 7:
        raise bad
    m1, m2, m5 = PLAN_L1.fullmatch(lines[0]), PLAN_L2.fullmatch(lines[1]), PLAN_L5.fullmatch(lines[4])
    if not (m1 and m2 and m5 and lines[2].startswith("모델: ") and lines[3].startswith("캐시: ") and lines[5].startswith("블록 차례: ")
            and lines[6] == "요청 지문:"):
        raise bad
    prints, problems, stale = {}, 0, 0
    i = 7
    while i < len(lines) and PLAN_PRINT.fullmatch(lines[i]):
        m = PLAN_PRINT.fullmatch(lines[i])
        prints[m.group(1)] = m.group(2)
        i += 1
    for line in lines[i:]:
        if line.startswith("기록 문제: "):
            problems += 1
        elif line.startswith("요청 지문이 다른 기록: "):
            stale += 1
        else:
            raise bad
    return {"stage": m1.group(1), "split": m1.group(2), "version": m1.group(3), "n_variants": int(m2.group(1)),
            "variants": m2.group(2).split(" ") if m2.group(2) else [], "n_questions": int(m2.group(3)), "reps": m2.group(4),
            "block": int(m2.group(5)), "runs": int(m5.group(1)), "done": int(m5.group(2)), "started": int(m5.group(3)),
            "remaining": int(m5.group(4)), "prints": prints, "problems": problems, "stale": stale}


def check_plan_txt(p, s, plan):
    vs = []
    for r in plan:
        if r["variant"]["id"] not in vs:
            vs.append(r["variant"]["id"])
    nq = len({r["question"]["id"] for r in plan})
    if (p["stage"], p["split"], p["version"]) != (s["stage"], s["split"], s["version"]):
        raise Bind("plan.txt의 단계·나눔·설정 판이 설정과 다르다")
    if p["variants"] != vs or p["n_variants"] != len(vs) or p["n_questions"] != nq or p["reps"] != ", ".join(map(str, s["reps"])) \
            or p["block"] != s["block_size"] or p["runs"] != len(plan):
        raise Bind("plan.txt의 판·문항·회차·블록·실행 수가 파이썬이 짠 계획과 다르다")
    if p["problems"] or p["stale"]:
        raise Bind(f"plan.txt에 기록 문제 {p['problems']}줄, 요청 지문이 다른 기록 {p['stale']}줄이 있다")
    if p["remaining"]:
        raise Bind(f"plan.txt: 남은 것 {p['remaining']}(시작한 것 {p['started']}). 모두 끝나야 매긴다")
    if p["done"] != len(plan) or p["started"]:
        # 하네스 Runner.status는 폴더의 모든 기록을 끝난 것·시작한 것에 넣는다(8b 2절 가)
        raise Bind(f"plan.txt: 끝난 것 {p['done']}·시작한 것 {p['started']}가 계획({len(plan)})과 맞지 않다. 계획 밖 기록이 있다. 다른 폴더로 옮긴다")
    miss = [v for v in vs if v not in p["prints"]]
    if miss:
        raise Bind("plan.txt에 요청 지문이 없는 판이 있다: " + " ".join(miss))


# ---------------------------------------------------------------- 기록과 판정

def run_print(variant_print, q):
    """하네스 Fingerprint.run과 같은 셈."""
    return sha256((variant_print + "\n" + q["key"] + "\n" + q["text"]).encode("utf-8"))


RESPONSE_KEEP = ("type", "turn", "stop", "refusal_category", "input_tokens", "cache_read_tokens", "cache_write_tokens", "output_tokens", "millis")


def slim(e):
    """응답 원문은 크고 채점에 쓰지 않는다. 8b가 쓰는 칸(토큰 넷, 시간, 멈춘 까닭, refusal 범주)과 원문 usage.cache_creation만 남긴다.
    첫 메시지는 읽은 뒤 버린다."""
    if e.get("type") != "response":
        return e
    out = {k: e[k] for k in RESPONSE_KEEP if k in e}
    raw = e.get("raw")
    usage = raw.get("usage") if isinstance(raw, dict) else None
    cc = usage.get("cache_creation") if isinstance(usage, dict) else None
    out["cache_creation"] = cc if isinstance(cc, dict) else None
    return out


def head_of(h):
    """기록 머리에서 8b가 쓰는 것: 판 ID·모델(Summary가 머리로 단가를 고른다), 캐시 유지 시간(없으면 5m), 머리의 단가표(못 읽으면 None)."""
    from . import metrics as metrics_mod
    v = h.get("variant") if isinstance(h.get("variant"), dict) else {}
    ttl = (h.get("cache") if isinstance(h.get("cache"), dict) else {}).get("ttl")
    try:
        prices = metrics_mod.load_prices(h.get("prices"), "기록 머리의 단가표")
    except Bad:
        prices = None
    return {"variant": metrics_mod.as_text(v.get("id", metrics_mod._MISSING)), "model": metrics_mod.as_text(v.get("model", metrics_mod._MISSING)),
            "ttl": ttl if isinstance(ttl, str) else "5m", "prices": prices}


def read_records(stage_dir, s, plan, prints):
    """기록 폴더 바로 아래 *.jsonl을 읽어 계획과 묶는다(2.2의 3). 실행 ID → (기록 해시, 사건들)."""
    by = {r["id"]: r for r in plan}
    names = sorted(f for f in os.listdir(stage_dir) if f.endswith(".jsonl") and os.path.isfile(os.path.join(stage_dir, f)))
    problems, outside, got = [], [], {}
    for name in names:
        with open(os.path.join(stage_dir, name), "rb") as f:
            raw = f.read()
        try:
            lines = jsonl(raw, name)
        except Bind as e:
            problems.append(str(e))
            continue
        if not lines or lines[0].get("type") != "run":
            problems.append(f"{name}: 첫 줄이 머리(type run)가 아니다")
            continue
        h = lines[0]
        rid = h.get("run_id")
        if not isinstance(rid, str) or name != rid + ".jsonl":
            problems.append(f"{name}: 파일 이름이 머리의 실행 ID와 다르다")
            continue
        r = by.get(rid)
        if r is None:
            outside.append(rid)
            continue
        q = r["question"]
        hq = h.get("question") if isinstance(h.get("question"), dict) else {}
        if h.get("stage") != s["stage"] or h.get("settings_version") != s["version"] or h.get("split") != s["split"]:
            problems.append(f"{name}: 머리의 단계·설정 판·나눔이 설정과 다르다")
        elif {k: hq.get(k) for k in ("id", "key", "qtype", "split", "text")} != {k: q[k] for k in ("id", "key", "qtype", "split", "text")}:
            problems.append(f"{name}: 머리의 문항이 문항 파일의 것과 다르다")
        elif (h.get("variant") if isinstance(h.get("variant"), dict) else {}).get("id") != r["variant"]["id"] or h.get("rep") != r["rep"]:
            problems.append(f"{name}: 머리의 판·회차가 실행 ID와 다르다")
        elif h.get("fingerprint") != run_print(prints[r["variant"]["id"]], q):
            problems.append(f"{name}: 실행 지문이 plan.txt의 판 지문으로 셈한 것과 다르다")
        else:
            ends = [e for e in lines[1:] if e.get("type") == "end"]
            if not ends:
                problems.append(f"{name}: 끝(end)이 없다")
            elif not isinstance(ends[0].get("outcome"), str) or ends[0]["outcome"].startswith("STOP_"):
                problems.append(f"{name}: 끝난 꼴이 {ends[0].get('outcome')}다. 문구를 고쳐 그 판을 다시 돌릴 일이다")
            else:
                got[rid] = {"sha": sha256(lf(raw)), "events": [slim(e) for e in lines[1:] if e.get("type") != "first_message"],
                            "outcome": ends[0]["outcome"], "head": head_of(h)}
    if outside:
        problems.append(f"계획 밖 기록 {len(outside)}개(예: {outside[0]}.jsonl). 다른 폴더로 옮긴다")
    missing = [r["id"] for r in plan if r["id"] not in got and not any(p.startswith(r["id"] + ".jsonl") for p in problems)]
    if missing:
        problems.append(f"계획의 실행 {len(missing)}개에 기록이 없다(예: {missing[0]})")
    if problems:
        raise Bind(f"기록 문제 {len(problems)}가지: " + " / ".join(problems[:5]) + (" …" if len(problems) > 5 else ""))
    return got


def read_verdicts(stage_dir, s, records, unjudged):
    """판정 파일(judge/verdicts.jsonl)을 (단계, 실행 ID, 기록 해시)로 기록에 묶는다(2.2의 4)."""
    path = os.path.join(stage_dir, "judge", "verdicts.jsonl")
    lines = []
    if os.path.exists(path):
        with open(path, "rb") as f:
            lines = jsonl(f.read(), "judge/verdicts.jsonl")
    want = {(s["stage"], rid, r["sha"]): rid for rid, r in records.items() if r["outcome"] == "SUBMITTED"}
    used, old, other = {}, 0, 0
    for v in lines:
        k = (v.get("stage"), v.get("run_id"), v.get("record_sha256"))
        if k in want:
            if k[1] in used:
                raise Bind(f"판정 파일에 같은 (단계, 실행 ID, 기록 해시)의 줄이 둘이다: {k[1]}")
            if not isinstance(v.get("answered"), bool) or not isinstance(v.get("judge"), str):
                raise Bind(f"판정 줄의 answered·judge 꼴이 아니다: {k[1]}")
            used[k[1]] = v
        elif v.get("stage") == s["stage"] and v.get("run_id") in records:
            old += 1
        else:
            other += 1
    judges = sorted({v["judge"] for v in used.values()})
    if len(judges) > 1:
        raise Bind(f"쓰는 판정 줄의 판정 지문이 {len(judges)}가지다")
    missing = [rid for rid in want.values() if rid not in used]
    if missing and unjudged == "stop":
        raise Bind(f"판정 없는 SUBMITTED 실행 {len(missing)}개(예: {sorted(missing)[0]}). 하네스 judge를 다시 친다")
    return {rid: v["answered"] for rid, v in used.items()}, (judges[0] if judges else None), old, other, len(missing)


# ---------------------------------------------------------------- 실행 하나 매기기(3절)

def accepted_submission(events):
    """받아들여진 제출: accepted 앞의 마지막 submit 사건(그 input·findings)."""
    last, acc = None, []
    for e in events:
        t = e.get("type")
        if t == "submit":
            last = e
        elif t == "accepted":
            acc.append(last)
    if len(acc) != 1 or acc[0] is None or not isinstance(acc[0].get("input"), dict):
        return None
    return acc[0]


def tool_index(events):
    """도구 사건(3.1): trade_stats는 결과의 id와 조건.비교기준, 입력의 start_month·end_month를, compare_prices는 결과의 id와 입력의 basis_evidence를 읽는다.
    오류인 부름은 근거를 만들지 않는다."""
    stats, compares = {}, {}
    for e in events:
        if e.get("type") != "tool" or e.get("is_error") is not False or not isinstance(e.get("result"), str):
            continue
        try:
            res = json.loads(e["result"], parse_float=Decimal)
        except ValueError:
            continue
        inp = e.get("input") if isinstance(e.get("input"), dict) else {}
        rid = res.get("id") if isinstance(res, dict) else None
        if e.get("name") == "trade_stats" and isinstance(rid, str):
            cond = res.get("조건") if isinstance(res.get("조건"), dict) else {}
            stats[rid] = {"scope": cond.get("비교기준"), "start": inp.get("start_month"), "end": inp.get("end_month")}
        elif e.get("name") == "compare_prices" and isinstance(rid, str):
            compares[rid] = nid(inp.get("basis_evidence"))
    return stats, compares


def claims_of(inp):
    """(종류, 차례 이름, claim) 목록. stat_claims 다음 value_claims다."""
    out = []
    for kind, key in (("stat", "stat_claims"), ("value", "value_claims")):
        cs = inp.get(key) if isinstance(inp.get(key), list) else []
        for i, c in enumerate(cs):
            if isinstance(c, dict):
                out.append((kind, f"{key}[{i}]", c))
    return out


def scope_of(eid, stats, compares):
    """claim 근거 → 그 S?의 비교기준. S?는 trade_stats로, C?는 basis_evidence를 거쳐 잇는다. 이을 수 없으면 None."""
    m = P_S.match(eid)
    if m:
        st = stats.get(m.group(1))
        return st["scope"] if st else None
    m = P_C.match(eid)
    if m and m.group(1) in compares:
        return scope_of(compares[m.group(1)], stats, compares) if P_S.match(compares[m.group(1)]) else None
    return None


class Item:
    def __init__(self, name, pattern, want, unit, kind, code, fields=None, show=()):
        self.name, self.pattern, self.want, self.unit, self.kind, self.code = name, pattern, want, unit, kind, code
        self.fields, self.show = fields, show


def items_for(a):
    """정답이 묻는 것(3.2): 유형·거래·표본으로 정한다. 월세 매물의 기준값은 환산 기준값이다."""
    q, call = a["qtype"], a["call"]
    period = (call["start_month"], call["end_month"])
    if q == "T2" and a["sample"] == 0:
        return [Item("거래 없음", P_SAMPLE, Fraction(0), "건", "stat", "NO_TRADE",
                     {"basis": "단지", "period": period, "sample": 0, "provisional": provisional(*period)})]
    mon = call["deal"] == "월세"
    basis = Item("기준값", P_CONV_BASIS if mon else P_BASIS, Fraction(a["converted" if mon else "basis"]), "만원", "stat", "BASIS",
                 {"basis": a["scope"], "period": period, "sample": a["sample"], "provisional": provisional(*period)})
    diff = Item("차이", P_DIFF, Fraction(a["diff"]), "%", "value", "DIFF", show=("slot", "direction"))
    out = []
    if q == "T4":
        out.append(Item("환산보증금", P_CONVERTED, Fraction(a["listing_value"]), "원", "value", "CONVERTED", show=("slot",)))
    out += [basis, diff]
    if q == "T5":
        n = a["t5_count"]
        out.append(Item("건수", P_SAMPLE, Fraction(n), "건", "stat", "COUNT",
                        {"basis": "단지", "period": RECENT, "sample": n, "provisional": provisional(*RECENT)}, ("slot",) if n else ()))
    return out


def value_ok(item, c):
    v, u = number(c.get("value")), c.get("unit")
    if v is None:
        return False
    if item.unit == "만원":
        return (u == "만원" and v == item.want) or (u == "원" and v == item.want * 10000)
    if item.unit == "원":
        return (u == "원" and v == item.want) or (u == "만원" and v * 10000 == item.want)
    return u == item.unit and v == item.want


def marks_of(text, ph=PLACEHOLDER):
    """글의 자리표시자: (slot, 방향인가). ph는 기록의 검사기 판의 자리표시자 꼴이다."""
    return {(m.group(1), m.group(2) is not None) for m in ph.finditer(text)}


def rule_codes(item, kind, c, marks):
    """항목의 규칙(3.4). 걸린 코드 목록."""
    if kind != item.kind:
        return ["CLAIM_KIND"]
    codes = []
    if item.kind == "stat":
        f = item.fields
        if c.get("basis") != f["basis"]:
            codes.append("SCOPE")
        if (c.get("start_month"), c.get("end_month")) != f["period"]:
            codes.append("PERIOD")
        if integer(c.get("sample_size")) != f["sample"]:
            codes.append("SAMPLE")
        if c.get("provisional") is not f["provisional"]:
            codes.append("PROVISIONAL")
    elif c.get("source") != "계산":
        codes.append("SOURCE")
    slot = c.get("slot")
    if "slot" in item.show and (slot, False) not in marks:
        codes.append("SLOT_HIDDEN")
    if "direction" in item.show and (slot, True) not in marks:
        codes.append("DIRECTION_MISSING")
    return codes


def score_item(item, cs, marks):
    """대표 claim(3.2): 값과 규칙을 다 지나는 첫 claim, 없으면 값이 맞는 첫 claim, 그것도 없으면 첫 claim."""
    cands = [(kind, where, c) for kind, where, c in cs if item.pattern.fullmatch(nid(c.get("evidence")))]
    if not cands:
        return {"item": item.name, "claim": None, "value": False, "rule": False, "codes": [item.code + "_MISSING"]}
    done = [(kind, where, value_ok(item, c), rule_codes(item, kind, c, marks)) for kind, where, c in cands]
    pick = next((d for d in done if d[2] and not d[3]), None) or next((d for d in done if d[2]), None) or done[0]
    _, where, vok, rc = pick
    return {"item": item.name, "claim": where, "value": vok, "rule": not rc, "codes": ([] if vok else [item.code + "_VALUE"]) + rc}


def blank(text, ph=PLACEHOLDER):
    """볼 글: NFC로 맞추고 자리표시자를 빈칸으로 바꾼 글."""
    return ph.sub(" ", unicodedata.normalize("NFC", text))


def squeeze(text):
    return SPACES.sub("", unicodedata.normalize("NFC", text))


def direction(text, cfg):
    """방향 말(3.4): 어디서나 세는 말은 걸린 수, 높·낮은 셋을 모두 채울 때만 센다(나머지는 세지 않은 수로 남긴다).
    어디서나 세는 말은 겹치지 않게 왼쪽부터, 같은 곳에서는 긴 말부터 센다("비싸다"는 '비싸' 하나로, 그 안의 '싸다'는 세지 않는다)."""
    found = {}
    for m in re.finditer("|".join(re.escape(w) for w in sorted(cfg["words"], key=len, reverse=True)), text):
        found[m.group()] = found.get(m.group(), 0) + 1
    words = {w: found[w] for w in cfg["words"] if w in found}
    hl = {ch: [0, 0] for ch in cfg["chars"]}
    for sent in SENTENCE_END.split(text):
        toks, pos, spans = sent.split(), 0, []
        for t in toks:
            at = sent.index(t, pos)
            spans.append(at)
            pos = at + len(t)
        for i, t in enumerate(toks):
            for k, ch in enumerate(t):
                if ch not in hl:
                    continue
                at = spans[i] + k
                marker = any(m in sent[:at] for m in cfg["markers"])
                window = " ".join(toks[max(0, i - cfg["window"]):i + 1])
                excepted = any(x in window for x in cfg["except"])
                floor = bool(cfg["next_not"]) and i + 1 < len(toks) and toks[i + 1].startswith(cfg["next_not"])
                hl[ch][0 if marker and not excepted and not floor else 1] += 1
    hit = bool(words) or any(v[0] for v in hl.values())
    return hit, {"words": words, "high_low": {ch: v for ch, v in hl.items() if v != [0, 0]}}


def judge_submission(sub, events, a, forbidden, cfg):
    """제출 하나를 매긴다(3.2~3.6). events는 그 제출까지의 사건이다(근거를 잇는 도구 결과). 판정은 보지 않는다."""
    inp = sub["input"]
    text = inp.get("text") if isinstance(inp.get("text"), str) else ""
    n = cfg.get("placeholder", 3)
    ph = numerals_mod.PLACEHOLDERS[n]
    cs = claims_of(inp)
    marks = marks_of(text, ph)
    stats, compares = tool_index(events)
    items = [score_item(it, cs, marks) for it in items_for(a)]
    report = []
    if a["qtype"] == "T2":
        widened = any(scope_of(nid(c.get("evidence")), stats, compares) not in (None, "단지") for _, _, c in cs)
        if a["sample"] == 0 and any(P_DIFF.fullmatch(nid(c.get("evidence"))) for _, _, c in cs):
            widened = True
        if widened:
            report.append("T2_WIDENED")
    look = [blank(text, ph)] + [x.get("text") for x in (inp.get("context_claims") or []) if isinstance(x, dict) and isinstance(x.get("text"), str)]
    if any(squeeze(w) in squeeze(t) for w in forbidden for t in look):
        report.append("FORBIDDEN")
    hit, counts = direction(blank(text, ph), cfg)
    if hit:
        report.append("LITERAL_DIRECTION")
    for kind, _, c in cs:
        if kind != "value":
            continue
        e = nid(c.get("evidence"))
        want = "매물" if P_LISTING.match(e) else "국토부 실거래가" if P_S.match(e) else "계산" if P_CALC.match(e) else None
        if want is not None and c.get("source") != want:
            report.append("SOURCE")
            break
    # 검사기 깨끗(11b-1, 4.2): 기록의 findings에서 다시 세는 코드를 버리고, 글에서 센 수사 v1·기간 말·자리표시자 꼴로 채운다.
    # 주 (다): 같은 길이의 통계 claim이 있는 기간 말만 받는다. (가): 기간 말까지 모든 수사가 실패. (나): 수사를 모두 뺀다. 셋 다 자리표시자 꼴을 본다.
    # 채점 v2(11b-2, 5.4): 기록의 5.3 코드는 규칙 v1에 넣지 않는다. 검사기 v1 기록이면 그린 글을 textcheck로 다시 세어 기록과 대 보고,
    # 규칙 v2면 그 여섯과 기록의 DIFF_HIDDEN을 더한다. 셋(주·(가)·(나)) 모두에 더한다.
    findings = [f.rsplit(" ", 1)[-1] for f in (sub.get("findings") or []) if isinstance(f, str)]
    t = numerals_mod.scan(text, inp, n)
    t["v0_record"] = findings.count("NUMERAL")
    kept = [f for f in findings if f not in RECOUNTED and f not in V2_CODES] + (["PLACEHOLDER_FORM"] if t["form"] else [])
    main = kept + (["NUMERAL"] if t["numerals"] else []) + (["PERIOD_WORD"] if any(not ok for _, ok in t["periods"]) else [])
    every = kept + (["NUMERAL"] if t["numerals"] or t["periods"] else [])
    v2 = []
    if cfg.get("checker") == "v1":
        rendered = sub.get("rendered")
        if not isinstance(rendered, str):
            raise Bind("검사기 v1 기록의 제출 사건에 그린 글(rendered)이 없다")
        tc = textcheck_mod.check(rendered, cfg["latin"].get(a.get("id"), set()))
        t["text_codes"] = tc
        t["diff_hidden"] = "DIFF_HIDDEN" in findings
        t["record_diff"] = record_diff(findings, t, tc)
        if cfg["version"] == "v2":
            v2 = tc + (["DIFF_HIDDEN"] if t["diff_hidden"] else [])
    checker = sorted({"CHECKER:" + f for f in main + v2})
    items_rule = all(x["rule"] for x in items)
    vok = all(x["value"] for x in items)
    base = items_rule and not report and not v2
    return {"items": items, "report": report, "checker": checker, "vok": vok, "rok": base and not main, "rok_all": base and not every,
            "rok_nn": base and not kept, "cs": cs, "stats": stats, "counts": counts, "text_check": t}


def record_diff(findings, t, tc):
    """검사기 v1 기록의 대조(11b-1 상대 검토 (1)): 기록 findings와 글에서 다시 센 것이 다른 갈래. 같으면 빈 목록.
    NUMERAL은 기간 말 밖 수사의 수, PERIOD_WORD는 같은 길이의 통계 claim이 없는 기간 말의 수(자바는 걸린 말마다 하나씩 낸다),
    PLACEHOLDER_FORM은 있고 없음, TEXT는 그린 글의 여섯 코드(기록의 차례 그대로)다."""
    out = []
    if findings.count("NUMERAL") != t["numerals"]:
        out.append("NUMERAL")
    if findings.count("PERIOD_WORD") != sum(1 for _, ok in t["periods"] if not ok):
        out.append("PERIOD_WORD")
    if ("PLACEHOLDER_FORM" in findings) != t["form"]:
        out.append("PLACEHOLDER_FORM")
    if [f for f in findings if f in TEXT_CODES] != tc:
        out.append("TEXT")
    return out


def sound_submission(sub, events, a, forbidden, cfg):
    """8b 4.4 ④의 멀쩡한 제출: 받아들였다고 치고 그 제출까지의 사건으로 매겨, 값이 맞고 주 정의((다))의 규칙을 지나는 제출(11b-1, 4.2의 4.4 맞춤).
    판정은 보지 않는다."""
    if not isinstance(sub.get("input"), dict):
        return False
    j = judge_submission(sub, events, a, forbidden, cfg)
    return j["vok"] and j["rok"]


def score_run(events, a, outcome, judge, forbidden, cfg):
    """실행 하나(3.1~3.7). a는 정답 줄, judge는 판정(답함 True·답하지 않음 False·없음 None)."""
    if outcome != "SUBMITTED":
        return {"outcome": outcome, "items": [], "value": None, "rule": None, "judge": None, "full": False, "full_all_numerals": False,
                "full_no_numeral": False, "codes": [outcome], "tags": [], "direction": None, "text_check": None}
    sub = accepted_submission(events)
    if sub is None:
        raise Bind("SUBMITTED 기록의 받아들여진 제출을 찾지 못했다")
    if cfg.get("checker") == "v1":
        acc = next(e for e in events if e.get("type") == "accepted")
        if acc.get("answer") != sub.get("rendered"):
            raise Bind("받아들여진 답(accepted)이 그 제출 사건의 그린 글(rendered)과 다르다")
    j = judge_submission(sub, events, a, forbidden, cfg)
    items, vok, rok, cs, stats, counts = j["items"], j["vok"], j["rok"], j["cs"], j["stats"], j["counts"]
    codes = {code for x in items for code in x["codes"]} | set(j["report"]) | set(j["checker"])
    # 판정을 완전 정답에서 뺐으면(full_with_judge false) 판정은 실패 까닭이 아니다(코드에 넣지 않는다, 4.5)
    with_judge = cfg["full_with_judge"]
    if with_judge and judge is False:
        codes.add("JUDGE_NOT_ANSWERED")
    elif with_judge and judge is None:
        codes.add("JUDGE_MISSING")
    jok = judge is True or not with_judge
    return {"outcome": outcome, "items": items, "value": vok, "rule": rok, "judge": judge, "full": vok and rok and jok,
            "full_all_numerals": vok and j["rok_all"] and jok, "full_no_numeral": vok and j["rok_nn"] and jok, "codes": sorted(codes),
            "tags": false_no_trade(cs, a, stats), "direction": counts, "text_check": j["text_check"]}


def false_no_trade(cs, a, stats):
    """진단 꼬리표 FALSE_NO_TRADE(3.7, d판): 정답에 거래가 있는데 0건이라고 함. 규칙 실패로 세지 않는다."""
    for _, _, c in cs:
        v = number(c.get("value"))
        e = nid(c.get("evidence"))
        if v != 0 or c.get("unit") != "건" or not P_SAMPLE.fullmatch(e):
            continue
        st = stats.get(P_S.match(e).group(1))
        if st is None:
            continue
        period = (st["start"], st["end"])
        if st["scope"] == a["scope"] and period == (a["call"]["start_month"], a["call"]["end_month"]) and a["sample"] != 0:
            return ["FALSE_NO_TRADE"]
        if a["qtype"] == "T5" and st["scope"] == "단지" and period == RECENT and a["t5_count"] != 0:
            return ["FALSE_NO_TRADE"]
    return []


# ---------------------------------------------------------------- 틀 번호(있을 때만)

def template_numbers(exp, qpath, qversion, questions, listings_path):
    """틀 문장 파일이 있으면 questions.build로 문항마다 틀 번호(1부터)를 다시 낸다. 다시 지은 글이 문항 파일과 다르면 Bind.
    (틀 번호, 틀 문장 파일 자리, 매물 키 → 이름, 유형마다 틀 수). 파일이 없으면 모두 None."""
    tpath = os.path.join(os.path.dirname(qpath), f"templates_{qversion}.json")
    if not os.path.exists(tpath):
        return None, None, None, None
    try:
        _, templates = questions_mod.load_templates(tpath)
        L = listings_mod.load(listings_path)
        names = questions_mod.names(os.path.join(exp, OUT_DIR, LISTINGS_INPUT), [k for k, _ in L])
        rows = questions_mod.build(L, names, templates)
    except (questions_mod.BadTemplates, listings_mod.BadListing, OSError, ValueError, KeyError) as e:
        raise Bad(f"틀 문장 파일로 문항을 다시 짓지 못했다({type(e).__name__})") from None
    rebuilt = [{k: r[k] for k in ("id", "key", "qtype", "split", "text")} for r in rows]
    if rebuilt != [{k: q[k] for k in ("id", "key", "qtype", "split", "text")} for q in questions]:
        raise Bind("틀 문장 파일로 다시 지은 문항이 문항 파일과 다르다")
    return {r["id"]: r["template"] + 1 for r in rows}, tpath, names, {t: len(templates[t]) for t in TYPES}


def text_line(rows):
    """글 검사 v1(4.2 진단)을 모든 판에 걸쳐 한 줄로: 받아들여진 제출의 수만. 검사기 v1 기록(11b-2)이면 기록 대조와 그린 글 코드의 수를 더한다."""
    ts = [x["text_check"] for x in rows if x["text_check"] is not None]
    periods = [p for t in ts for p in t["periods"]]
    form = sum(t["form"] for t in ts)
    head = (f"글 검사 v1(받아들여진 제출 {len(ts)}개): PLACEHOLDER_FORM {form}개(잘린 이름 {sum(t['form'] and t['truncated'] for t in ts)}), "
            f"기간 말 맞음 {sum(1 for p in periods if p[1])}·안 맞음 {sum(1 for p in periods if not p[1])}, 그 밖의 수사 {sum(t['numerals'] for t in ts)}, ")
    v1 = [t for t in ts if "record_diff" in t]
    if not v1:
        return head + f"수사 v0 {sum(t['v0'] for t in ts)}(기록 findings의 NUMERAL과 수가 다른 제출 {sum(t['v0'] != t['v0_record'] for t in ts)})"
    kinds = ("NUMERAL", "PERIOD_WORD", "PLACEHOLDER_FORM", "TEXT")
    diff = "·".join(f"{k} {sum(k in t['record_diff'] for t in v1)}" for k in kinds)
    codes = "·".join(f"{c} {sum(c in t['text_codes'] for t in v1)}" for c in TEXT_CODES)
    return (head + f"기록 대조(검사기 v1)가 다른 제출 {sum(bool(t['record_diff']) for t in v1)}({diff}), 그린 글 코드가 든 제출 {codes}, "
            f"DIFF_HIDDEN {sum(t['diff_hidden'] for t in v1)}")


# ---------------------------------------------------------------- 쓰기

SUMMARY_HEAD = ("판", "이름", "계획", "제출", "값", "규칙", "판정 답함", "판정 없음", "완전 정답", "수사 모두 실패 완전 정답", "수사 뺀 완전 정답",
                "채점 지문", "판정 지문")


def tally(plan, rows):
    out = {}
    for r, x in zip(plan, rows):
        t = out.setdefault(r["variant"]["id"], {"variant": r["variant"], "plan": 0, "submitted": 0, "value": 0, "rule": 0, "answered": 0,
                                                 "unjudged": 0, "full": 0, "full_all_numerals": 0, "full_no_numeral": 0})
        t["plan"] += 1
        if x["outcome"] == "SUBMITTED":
            t["submitted"] += 1
            t["value"] += x["value"]
            t["rule"] += x["rule"]
            t["answered"] += x["judge"] is True
            t["unjudged"] += x["judge"] is None
        t["full"] += x["full"]
        t["full_all_numerals"] += x["full_all_numerals"]
        t["full_no_numeral"] += x["full_no_numeral"]
    return list(out.values())


def write_outputs(stage_dir, files):
    """다시 쓰기(6절, 8b 2절 나): 남은 score.tmp·score.old를 지움 → tmp에 씀 → 옛 score를 old로 → tmp를 score로 → old를 지움.
    바꾸기가 OSError로 실패하면 tmp를 지우고(할 수 있으면), 이번에 score를 old로 옮긴 때만 old를 score로 되돌린 뒤 Bad.
    마지막 old 지우기만 실패하면 결과는 제자리이므로 한 줄을 돌려준다(끝 코드 0)."""
    final = os.path.join(stage_dir, "score")
    tmp, old = final + ".tmp", final + ".old"
    moved = False
    try:
        for d in (tmp, old):
            if os.path.exists(d):
                shutil.rmtree(d)
        os.makedirs(tmp)
        for name, text in files:
            with open(os.path.join(tmp, name), "w", encoding="utf-8", newline="\n") as f:
                f.write(text)
        if os.path.exists(final):
            os.rename(final, old)
            moved = True
        os.rename(tmp, final)
    except OSError:
        try:
            if os.path.exists(tmp):
                shutil.rmtree(tmp)
        except OSError:
            pass
        if moved and not os.path.exists(final):
            try:
                os.rename(old, final)
            except OSError:
                pass
        raise Bad("결과 폴더를 바꾸지 못했다. score 폴더의 파일을 연 프로그램(엑셀 등)을 닫고 다시 친다") from None
    if os.path.exists(old):
        try:
            shutil.rmtree(old)
        except OSError:
            return "score.old를 지우지 못했다(다음에 칠 때 지운다)"
    return None


# ---------------------------------------------------------------- 명령

def run(settings_path, exp=".", listings=LISTINGS, pin=None, config=None, out=print):
    """채점(8a·8b). 끝 코드: 0 다 매김, 3 묶기 검사에 걸림, 2 설정·파일 읽기 오류. config가 None이면 기록의 검사기 판으로 고른다(default_config)."""
    try:
        return _run(settings_path, exp, listings, pin, config, out)
    except Bad as e:
        out(f"멈춤(설정이나 파일): {e}")
        return 2
    except Bind as e:
        out(f"멈춤(묶기 검사): {e}. 아무것도 쓰지 않았다")
        return 3


def prepare(settings_path, exp, listings, pin, config):
    """설정·파일을 읽고 묶기 검사(2.2)를 한다. 채점과 같은 실수 표 얼리기가 같이 쓴다."""
    from . import metrics as metrics_mod
    s = load_settings(settings_path)
    if config is None:
        config = default_config(s["version"])
    cfg = load_config(config)
    checker = checker_of(s["version"])
    if cfg["version"] == "v2" and checker == "v0":
        raise Bad("채점 v2는 검사기 v1 기록(하네스 설정 version이 v0이 아닌 단계, 11c부터)에만 쓴다. 시운전은 채점 v1로 매긴다")
    at = lambda p: p if os.path.isabs(p) else os.path.join(exp, *p.split("/"))
    variants = load_variants(at(s["files"]["variants"]))
    qpath = at(s["files"]["questions"])
    qversion, questions = load_questions(qpath)
    metas = load_metas(at(listings), pin)
    k10 = at(s["files"]["cards"] + "/K10.md")
    forbidden = forbidden_words(k10)
    apath = at(f"{OUT_DIR}/answers_{qversion}.json")
    ans = read_json(apath, "정답 파일")
    if not isinstance(ans, dict) or not isinstance(ans.get("answers"), list):
        raise Bad("정답 파일의 꼴이 아니다")
    plan = make_plan(s, variants, questions, metas)
    # 8c: 비교 목록의 판은 판 설정 파일에 있어야 한다(그 단계에 없는 판이 든 비교는 "-")
    known = {v["id"] for v in variants}
    miss = [x for p in (cfg["comparisons"]["main"], *cfg["comparisons"]["aux"]) for x in p if x not in known]
    if miss:
        raise Bad(f"채점 설정 comparisons의 판이 판 설정 파일에 없다: {' '.join(dict.fromkeys(miss))}")
    current = metrics_mod.current_codes(metas)
    # 2.2의 1: 정답 ↔ 문항 파일
    qsha = sha256_lf(qpath)
    if ans.get("questions_sha256") != qsha or ans.get("version") != qversion:
        raise Bind("정답 파일의 questions_sha256·version이 문항 파일과 다르다")
    by_answer = {}
    for x in ans["answers"]:
        if not isinstance(x, dict) or x.get("id") in by_answer:
            raise Bind("정답 파일의 줄이 꼴이 아니거나 같은 문항이 둘이다")
        by_answer[x.get("id")] = x
        if x.get("sample") == 0 and x.get("qtype") != "T2":
            raise Bad(f"정답 파일에 표본 0인 {x.get('qtype')} 문항이 있다(거래 없음은 T2만 본다): {x.get('id')}")
    if [(q["id"], q["key"], q["qtype"], q["split"]) for q in questions] != \
            [(x.get("id"), x.get("key"), x.get("qtype"), x.get("split")) for x in ans["answers"]]:
        raise Bind("정답 파일의 문항 ID·키·유형·나눔이 문항 파일과 1:1로 같지 않다")
    tnums, tpath, names, counts = template_numbers(exp, qpath, qversion, questions, at(listings))
    # 11b-2: 검사기 v1 기록은 자리표시자 이름 1~6자, 그린 글 검사의 로마자는 매물 입력 파일과 문항 글에서 받는다
    lipath = at(f"{OUT_DIR}/{LISTINGS_INPUT}") if checker == "v1" else None
    cfg = dict(cfg, checker=checker, placeholder=6 if checker == "v1" else 3, latin=listing_latin(lipath, questions) if lipath else {})
    # 8b의 표: ⓪ 의도 절(git 밖, 없으면 ⓪ 부당 거절만 "-"), 같은 실수(설정에 자리를 적었으면 꼭 있어야 한다)
    ipath = at(cfg["intent_clauses"])
    intents = None
    if os.path.exists(ipath) and tnums is not None:
        intents = metrics_mod.intent_tables(metrics_mod.load_intent_clauses(ipath, counts), questions, tnums, names)
    mpath = at(cfg["mistakes"]) if cfg["mistakes"] is not None else None
    mtable = None
    if mpath is not None:
        if not os.path.exists(mpath):
            raise Bad(f"채점 설정의 같은 실수 표가 없다: {cfg['mistakes']}")
        mtable = metrics_mod.load_mistakes(mpath)
    # 2.2의 2·3·4
    stage_dir = at(s["files"]["out"] + "/" + s["stage"])
    if not os.path.isdir(stage_dir):
        raise Bind(f"기록 폴더가 없다: {s['files']['out']}/{s['stage']}")
    p = read_plan_txt(os.path.join(stage_dir, "plan.txt"))
    check_plan_txt(p, s, plan)
    records = read_records(stage_dir, s, plan, p["prints"])
    for rid, r in records.items():
        if r["outcome"] == "SUBMITTED" and sum(1 for e in r["events"] if e.get("type") == "accepted") != 1:
            raise Bind(f"{rid}.jsonl: SUBMITTED인데 받아들여진 답(accepted)이 하나가 아니다")
    verdicts, judge_print, old, other, unjudged = read_verdicts(stage_dir, s, records, cfg["unjudged"])
    # 채점 지문(8절): 쓴 표는 이름 차례로 뒤에 더한다(8b 11절)
    asha = sha256_lf(apath)
    parts = [("code", tree(CODE_ROOT, code_files())), ("score", sha256_lf(config)), ("answers", asha), ("questions", qsha),
             ("k10", sha256_lf(k10))]
    if tpath:
        parts.append(("templates", sha256_lf(tpath)))
    if intents is not None:
        parts.append(("intent_clauses", sha256_lf(ipath)))
    if mtable is not None:
        parts.append(("mistakes", sha256_lf(mpath)))
    if lipath is not None:
        parts.append(("listings_input", sha256_lf(lipath)))
    env = {"forbidden": forbidden, "cfg": cfg, "current": current, "areas": metrics_mod.area_numbers(current), "mistakes": mtable,
           "same_stage": mtable is not None and mtable["stage"] == s["stage"], "intents": intents, "prices": s["prices"]}
    return {"s": s, "cfg": cfg, "at": at, "variants": variants, "questions": questions, "metas": metas, "forbidden": forbidden, "plan": plan,
            "by_answer": by_answer, "qsha": qsha, "asha": asha, "tnums": tnums, "stage_dir": stage_dir, "records": records, "verdicts": verdicts,
            "judge_print": judge_print, "old": old, "other": other, "unjudged": unjudged, "print": fingerprint(parts), "env": env,
            "intents_missing": intents is None}


def score_rows(ctx):
    """3절: 실행마다(8a 칸)."""
    rows = []
    for r in ctx["plan"]:
        rec = ctx["records"][r["id"]]
        q = r["question"]
        res = score_run(rec["events"], ctx["by_answer"][q["id"]], rec["outcome"], ctx["verdicts"].get(r["id"]), ctx["forbidden"], ctx["cfg"])
        head = {"run_id": r["id"], "variant": r["variant"]["id"], "question": q["id"], "key": q["key"], "qtype": q["qtype"], "split": q["split"],
                "rep": r["rep"], "layer": ctx["metas"][q["key"]]["layer"], "template": ctx["tnums"].get(q["id"]) if ctx["tnums"] else None}
        head.update(res)
        rows.append(head)
    return rows


def _run(settings_path, exp, listings, pin, config, out):
    from . import metrics as metrics_mod
    from . import stats as stats_mod
    ctx = prepare(settings_path, exp, listings, pin, config)
    s, cfg, print_, judge_print = ctx["s"], ctx["cfg"], ctx["print"], ctx["judge_print"]
    rows = score_rows(ctx)
    n_variants, extra, metrics_text, mlines = metrics_mod.compute(ctx, rows)
    runs_text = "".join(json.dumps(dict(x, **y), ensure_ascii=False, separators=(",", ":")) + "\n" for x, y in zip(rows, extra))
    t = tally(ctx["plan"], rows)
    lines = ["\t".join(SUMMARY_HEAD)]
    for x in t:
        lines.append("\t".join([x["variant"]["id"], x["variant"]["name"]] + [str(x[k]) for k in
                               ("plan", "submitted", "value", "rule", "answered", "unjudged", "full", "full_all_numerals", "full_no_numeral")]
                     + [print_, judge_print or "-"]))
    stats_text, report_text, stats_line = stats_mod.compute(ctx, rows, t, mlines)
    note = write_outputs(ctx["stage_dir"], [("runs.jsonl", runs_text), ("summary.tsv", "\n".join(lines) + "\n"), ("metrics.tsv", metrics_text),
                                            ("stats.tsv", stats_text), ("report.md", report_text)])
    records, unjudged = ctx["records"], ctx["unjudged"]
    n_sub = sum(1 for r in records.values() if r["outcome"] == "SUBMITTED")
    jp = judge_print[:12] if judge_print else "-"
    out(f"채점 검사: 문항 파일 {ctx['qsha'][:8]}… = 정답 파일, 기록 {len(records)}개가 이 문항 파일과 plan.txt 지문으로 돎(단계·설정 판 같음), "
        f"판정 {n_sub - unjudged}개가 기록 해시에 묶임(판정 지문 {jp}, 옛 줄 {ctx['old']}, 다른 줄 {ctx['other']}"
        + (f", 판정 없음 {unjudged}" if cfg["unjudged"] == "fail" else "") + f"), 채점 지문 {print_[:12]}, 완전 정답 {cfg['version']}"
        + f"(판정 {'넣음' if cfg['full_with_judge'] else '뺌'})")
    for x in t:
        out(f"{x['variant']['id']} {x['variant']['name']}: 계획 {x['plan']}, 제출 {x['submitted']}, 값 {x['value']}, 규칙 {x['rule']}, "
            f"판정 답함 {x['answered']}, 완전 정답 {x['full']}, 수사 모두 실패 {x['full_all_numerals']}, 수사 뺀 {x['full_no_numeral']}")
    out(text_line(rows))
    out(f"지표: 하네스 summary.tsv와 같음(판 {n_variants}개), 15.9·비용·진단 {len(mlines)}줄을 metrics.tsv에 썼다")
    out(stats_line)
    if ctx["intents_missing"] and any("⓪" in r["variant"]["parts"] for r in ctx["plan"]):
        out(f"⓪ 부당 거절: 의도 절 표({cfg['intent_clauses']})나 틀 문장 파일이 없어 \"-\"로 두었다")
    if ctx["env"]["same_stage"]:
        out("같은 실수 재발: 표가 이 단계에서 얼린 것이라 재지 않았다")
    if note:
        out(note)
    out(f"{s['files']['out']}/{s['stage']}/score/runs.jsonl·summary.tsv·metrics.tsv·stats.tsv·report.md를 썼다")
    return 0


def mistakes(settings_path, table_path, exp=".", listings=LISTINGS, pin=None, config=None, out=print):
    """같은 실수 표 얼리기(8b 4.3). 그 단계를 채점과 같은 함수로 묶기 검사하고 매긴 뒤 표를 쓴다. 이미 있으면 쓰지 않는다(끝 코드 2)."""
    try:
        from . import metrics as metrics_mod
        ctx = prepare(settings_path, exp, listings, pin, config)
        path = ctx["at"](table_path)
        if os.path.exists(path):
            raise Bad(f"얼릴 파일이 이미 있다. 얼린 것은 덮어쓰지 않는다: {table_path}")
        doc = metrics_mod.freeze_mistakes(ctx, score_rows(ctx))
        try:
            os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
            with open(path, "x", encoding="utf-8", newline="\n") as f:
                f.write(json.dumps(doc, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
        except OSError as e:
            raise Bad(f"같은 실수 표를 쓰지 못했다({type(e).__name__})") from None
        out(f"같은 실수 표: 단계 {doc['stage']}, 실행 {doc['runs']}개, 코드 {len(doc['codes'])}가지, (코드, 시군구) {len(doc['code_areas'])}가지, "
            f"채점 지문 {doc['score'][:12]}, SHA-256 {sha256_lf(path)[:12]}. {table_path}에 얼렸다")
        return 0
    except Bad as e:
        out(f"멈춤(설정이나 파일): {e}")
        return 2
    except Bind as e:
        out(f"멈춤(묶기 검사): {e}. 아무것도 쓰지 않았다")
        return 3
