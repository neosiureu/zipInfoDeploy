# -*- coding: utf-8 -*-
"""순서 8a 채점기(순서 8 설계 d판 2·3·6·8절): 한 단계의 기록을 묶기 검사한 뒤 실행마다 값·규칙·판정을 매긴다.

experiment 폴더에서 `python -X utf8 -B -m prep score harness/settings/<설정>.json`으로 돌린다.

- 묶기 검사(2.2)에 하나라도 걸리면 아무것도 쓰지 않고 3으로 끝난다. 설정·파일 읽기 오류는 2, 다 매기면 0이다.
- 결과는 <files.out>/<stage>/score/에 runs.jsonl·summary.tsv로 쓴다(runs 아래라 git 밖). 질문·답·판정 이유·매물 이름은 쓰지 않는다.
- 화면에는 셈한 수, 실행 ID, 코드, 해시만 낸다.
- 표준 라이브러리만 쓴다. 값은 JSON 수를 Decimal로 읽어 분수로 견준다(부동소수점을 쓰지 않는다).
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
from . import questions as questions_mod

# 채점기 자신의 코드가 있는 experiment 폴더(채점 지문의 code는 지금 도는 코드로 센다)
CODE_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONFIG = os.path.join(CODE_ROOT, "prep", "score_v0.json")
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

CONFIG_FIELDS = ("version", "unjudged", "direction")
DIRECTION_FIELDS = ("words", "high_low")
HIGH_LOW_FIELDS = ("chars", "markers", "except", "window", "next_not")
UNJUDGED = ("stop", "fail")


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

def read_json(path, what):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.loads(f.read())
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
    s = need(read_json(path, "하네스 설정"), ("version", "stage", "split", "files", "select", "block_size"), "하네스 설정")
    need(s["files"], ("variants", "questions", "cards", "out"), "설정의 files")
    sel = need(s["select"], ("variants", "questions", "reps"), "설정의 select")
    reps = sel["reps"]
    if not isinstance(reps, list) or not reps or any(integer(r) is None or not 1 <= r <= 99 for r in reps) or len(set(reps)) != len(reps):
        raise Bad("select.reps는 겹치지 않는 1~99의 정수 목록이다")
    if s["split"] not in SPLITS or integer(s["block_size"]) is None or s["block_size"] < 1:
        raise Bad("설정의 split이나 block_size가 맞지 않다")
    return {"version": s["version"], "stage": s["stage"], "split": s["split"], "files": s["files"], "block_size": s["block_size"],
            "variants": all_or_list(sel["variants"], "select.variants"), "questions": all_or_list(sel["questions"], "select.questions"),
            "reps": list(reps)}


def load_config(path):
    c = read_json(path, "채점 설정")
    if not isinstance(c, dict) or sorted(c) != sorted(CONFIG_FIELDS):
        raise Bad(f"채점 설정의 칸은 {'·'.join(CONFIG_FIELDS)}다")
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
    nfc = lambda xs: [unicodedata.normalize("NFC", x) for x in xs]
    return {"version": c["version"], "unjudged": c["unjudged"], "words": nfc(d["words"]), "chars": nfc(hl["chars"]),
            "markers": nfc(hl["markers"]), "except": nfc(hl["except"]), "window": hl["window"],
            "next_not": unicodedata.normalize("NFC", hl["next_not"])}


def load_variants(path):
    d = need(read_json(path, "판 설정 파일"), ("version", "variants"), "판 설정 파일")
    if not isinstance(d["variants"], list) or not d["variants"]:
        raise Bad("판 설정 파일에 판이 없다")
    out = []
    for v in d["variants"]:
        need(v, ("id", "name", "model"), "판")
        out.append({"id": v["id"], "name": v["name"], "model": v["model"]})
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
    if p["done"] != len(plan) or p["started"] or p["remaining"]:
        raise Bind(f"plan.txt: 끝난 것 {p['done']}, 시작한 것 {p['started']}, 남은 것 {p['remaining']}(모두 끝나야 매긴다)")
    miss = [v for v in vs if v not in p["prints"]]
    if miss:
        raise Bind("plan.txt에 요청 지문이 없는 판이 있다: " + " ".join(miss))


# ---------------------------------------------------------------- 기록과 판정

def run_print(variant_print, q):
    """하네스 Fingerprint.run과 같은 셈."""
    return sha256((variant_print + "\n" + q["key"] + "\n" + q["text"]).encode("utf-8"))


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
                # 응답 원문(response)과 첫 메시지는 크고 채점에 쓰지 않아 남기지 않는다
                got[rid] = {"sha": sha256(lf(raw)), "events": [e for e in lines[1:] if e.get("type") not in ("response", "first_message")],
                            "outcome": ends[0]["outcome"]}
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


def marks_of(text):
    """글의 자리표시자: (slot, 방향인가)."""
    return {(m.group(1), m.group(2) is not None) for m in PLACEHOLDER.finditer(text)}


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


def blank(text):
    """볼 글: NFC로 맞추고 자리표시자를 빈칸으로 바꾼 글."""
    return PLACEHOLDER.sub(" ", unicodedata.normalize("NFC", text))


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


def score_run(events, a, outcome, judge, forbidden, cfg):
    """실행 하나(3.1~3.7). a는 정답 줄, judge는 판정(답함 True·답하지 않음 False·없음 None)."""
    if outcome != "SUBMITTED":
        return {"outcome": outcome, "items": [], "value": None, "rule": None, "judge": None, "full": False, "full_no_numeral": False,
                "codes": [outcome], "tags": [], "direction": None}
    sub = accepted_submission(events)
    if sub is None:
        raise Bind("SUBMITTED 기록의 받아들여진 제출을 찾지 못했다")
    inp = sub["input"]
    text = inp.get("text") if isinstance(inp.get("text"), str) else ""
    cs = claims_of(inp)
    marks = marks_of(text)
    stats, compares = tool_index(events)
    items = [score_item(it, cs, marks) for it in items_for(a)]
    report = []
    if a["qtype"] == "T2":
        widened = any(scope_of(nid(c.get("evidence")), stats, compares) not in (None, "단지") for _, _, c in cs)
        if a["sample"] == 0 and any(P_DIFF.fullmatch(nid(c.get("evidence"))) for _, _, c in cs):
            widened = True
        if widened:
            report.append("T2_WIDENED")
    look = [blank(text)] + [x.get("text") for x in (inp.get("context_claims") or []) if isinstance(x, dict) and isinstance(x.get("text"), str)]
    if any(squeeze(w) in squeeze(t) for w in forbidden for t in look):
        report.append("FORBIDDEN")
    hit, counts = direction(blank(text), cfg)
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
    findings = [f.rsplit(" ", 1)[-1] for f in (sub.get("findings") or []) if isinstance(f, str)]
    checker = sorted({"CHECKER:" + f for f in findings})
    items_rule = all(x["rule"] for x in items)
    vok = all(x["value"] for x in items)
    rok = items_rule and not report and not checker
    rok_nn = items_rule and not report and all(f == "NUMERAL" for f in findings)
    codes = {code for x in items for code in x["codes"]} | set(report) | set(checker)
    if judge is False:
        codes.add("JUDGE_NOT_ANSWERED")
    elif judge is None:
        codes.add("JUDGE_MISSING")
    return {"outcome": outcome, "items": items, "value": vok, "rule": rok, "judge": judge, "full": vok and rok and judge is True,
            "full_no_numeral": vok and rok_nn and judge is True, "codes": sorted(codes), "tags": false_no_trade(cs, a, stats),
            "direction": counts}


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
    """틀 문장 파일이 있으면 questions.build로 문항마다 틀 번호(1부터)를 다시 낸다. 다시 지은 글이 문항 파일과 다르면 Bind."""
    tpath = os.path.join(os.path.dirname(qpath), f"templates_{qversion}.json")
    if not os.path.exists(tpath):
        return None, None
    try:
        _, templates = questions_mod.load_templates(tpath)
        L = listings_mod.load(listings_path)
        rows = questions_mod.build(L, questions_mod.names(os.path.join(exp, OUT_DIR, LISTINGS_INPUT), [k for k, _ in L]), templates)
    except (questions_mod.BadTemplates, listings_mod.BadListing, OSError, ValueError, KeyError) as e:
        raise Bad(f"틀 문장 파일로 문항을 다시 짓지 못했다({type(e).__name__})") from None
    rebuilt = [{k: r[k] for k in ("id", "key", "qtype", "split", "text")} for r in rows]
    if rebuilt != [{k: q[k] for k in ("id", "key", "qtype", "split", "text")} for q in questions]:
        raise Bind("틀 문장 파일로 다시 지은 문항이 문항 파일과 다르다")
    return {r["id"]: r["template"] + 1 for r in rows}, tpath


# ---------------------------------------------------------------- 쓰기

SUMMARY_HEAD = ("판", "이름", "계획", "제출", "값", "규칙", "판정 답함", "판정 없음", "완전 정답", "수사 뺀 완전 정답", "채점 지문", "판정 지문")


def tally(plan, rows):
    out = {}
    for r, x in zip(plan, rows):
        t = out.setdefault(r["variant"]["id"], {"variant": r["variant"], "plan": 0, "submitted": 0, "value": 0, "rule": 0, "answered": 0,
                                                 "unjudged": 0, "full": 0, "full_no_numeral": 0})
        t["plan"] += 1
        if x["outcome"] == "SUBMITTED":
            t["submitted"] += 1
            t["value"] += x["value"]
            t["rule"] += x["rule"]
            t["answered"] += x["judge"] is True
            t["unjudged"] += x["judge"] is None
        t["full"] += x["full"]
        t["full_no_numeral"] += x["full_no_numeral"]
    return list(out.values())


def write_outputs(stage_dir, runs_text, summary_text):
    """다시 쓰기(6절): 남은 score.tmp·score.old를 지움 → tmp에 씀 → 옛 score를 old로 → tmp를 score로 → old를 지움."""
    final = os.path.join(stage_dir, "score")
    tmp, old = final + ".tmp", final + ".old"
    for d in (tmp, old):
        if os.path.exists(d):
            shutil.rmtree(d)
    os.makedirs(tmp)
    for name, text in (("runs.jsonl", runs_text), ("summary.tsv", summary_text)):
        with open(os.path.join(tmp, name), "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
    if os.path.exists(final):
        os.rename(final, old)
    os.rename(tmp, final)
    if os.path.exists(old):
        shutil.rmtree(old)


# ---------------------------------------------------------------- 명령

def run(settings_path, exp=".", listings=LISTINGS, pin=None, config=CONFIG, out=print):
    """채점(8a). 끝 코드: 0 다 매김, 3 묶기 검사에 걸림, 2 설정·파일 읽기 오류."""
    try:
        return _run(settings_path, exp, listings, pin, config, out)
    except Bad as e:
        out(f"멈춤(설정이나 파일): {e}")
        return 2
    except Bind as e:
        out(f"멈춤(묶기 검사): {e}. 아무것도 쓰지 않았다")
        return 3


def _run(settings_path, exp, listings, pin, config, out):
    s = load_settings(settings_path)
    cfg = load_config(config)
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
    tnums, tpath = template_numbers(exp, qpath, qversion, questions, at(listings))
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
    # 채점 지문(8절)
    parts = [("code", tree(CODE_ROOT, code_files())), ("score", sha256_lf(config)), ("answers", sha256_lf(apath)), ("questions", qsha),
             ("k10", sha256_lf(k10))]
    if tpath:
        parts.append(("templates", sha256_lf(tpath)))
    print_ = fingerprint(parts)
    # 3절: 실행마다
    rows = []
    for r in plan:
        rec = records[r["id"]]
        q = r["question"]
        res = score_run(rec["events"], by_answer[q["id"]], rec["outcome"], verdicts.get(r["id"]), forbidden, cfg)
        head = {"run_id": r["id"], "variant": r["variant"]["id"], "question": q["id"], "key": q["key"], "qtype": q["qtype"], "split": q["split"],
                "rep": r["rep"], "layer": metas[q["key"]]["layer"], "template": tnums.get(q["id"]) if tnums else None}
        head.update(res)
        rows.append(head)
    runs_text = "".join(json.dumps(x, ensure_ascii=False, separators=(",", ":")) + "\n" for x in rows)
    t = tally(plan, rows)
    lines = ["\t".join(SUMMARY_HEAD)]
    for x in t:
        lines.append("\t".join([x["variant"]["id"], x["variant"]["name"]] + [str(x[k]) for k in
                               ("plan", "submitted", "value", "rule", "answered", "unjudged", "full", "full_no_numeral")] + [print_, judge_print or "-"]))
    write_outputs(stage_dir, runs_text, "\n".join(lines) + "\n")
    n_sub = sum(1 for r in records.values() if r["outcome"] == "SUBMITTED")
    jp = judge_print[:12] if judge_print else "-"
    out(f"채점 검사: 문항 파일 {qsha[:8]}… = 정답 파일, 기록 {len(records)}개가 이 문항 파일과 plan.txt 지문으로 돎(단계·설정 판 같음), "
        f"판정 {n_sub - unjudged}개가 기록 해시에 묶임(판정 지문 {jp}, 옛 줄 {old}, 다른 줄 {other}"
        + (f", 판정 없음 {unjudged}" if cfg["unjudged"] == "fail" else "") + f"), 채점 지문 {print_[:12]}, 완전 정답 {cfg['version']}")
    for x in t:
        out(f"{x['variant']['id']} {x['variant']['name']}: 계획 {x['plan']}, 제출 {x['submitted']}, 값 {x['value']}, 규칙 {x['rule']}, "
            f"판정 답함 {x['answered']}, 완전 정답 {x['full']}, 수사 뺀 완전 정답 {x['full_no_numeral']}")
    out(f"{s['files']['out']}/{s['stage']}/score/runs.jsonl·summary.tsv를 썼다")
    return 0
