# -*- coding: utf-8 -*-
"""순서 7a: 틀 문장 파일로 문항 파일과 문항별 정답을 만든다(순서 7 설계 c판 2~4절, 만들기 설계안 9.2·9.3).

- 틀 문장 파일(questions/templates_v0.json): {"version": "v0", "templates": {"T1": [...], ..., "T5": [...]}}.
  사용자가 쓴 글 그대로다(앞뒤 빈칸만 떼고 NFC). 읽을 때 검사(check)에 하나라도 걸리면 아무것도 쓰지 않고 멈춘다.
- 유형 배정은 9.2 그대로(answers.assign). 틀 문장은 나눔·유형마다 H(qtext, 단지ID|유형) 순으로 세워 돌아가며 준다(고루 나누기).
- {이름}은 매물 블록의 stockName(listings_input_v1.json)으로 바꾼다. T3의 창은 고른 틀 문장의 기간 말이고, 나머지는 12달이다.
- 문항 ID는 매물 키 차례, 그 안에서 유형 차례로 q001부터다(틀 문장과 상관없다).
- 문항별 정답은 대조용 정답(answers.build)의 같은 (매물, 유형, 창) 줄에 id·split을 더한 것이다. 대조용 파일과 같아야 한다.
- 두 파일 모두 UTF-8(BOM 없음), LF, 정한 칸 차례로 쓴다. 같은 입력이면 같은 바이트다.
"""
import hashlib
import json
import re
import unicodedata
from collections import defaultdict

from picker.common import H, write_text

from . import answers

TYPES = ("T1", "T2", "T3", "T4", "T5")
SLOT = "{이름}"
PERIODS = {"최근 1개월": 1, "최근 3개월": 3, "최근 6개월": 6, "최근 1년": 12}
T3_PERIODS = ("최근 1개월", "최근 3개월", "최근 6개월")
# {이름} 바로 뒤에 붙여도 되는 말(받침과 상관없는 말). 그 밖은 멈추고 묻는다(c판 2.2의 3).
AFTER_OK = ("의", "에서", "에게", "에", "한테", "도", "만", "보다", "까지", "부터", "하고", "처럼", "마다", "인")
AFTER_BAD = ("에요", "에여")  # "에"로 시작해도 이다의 꼴이라 걸린다
# 허용된 기간 말을 뺀 글에 있으면 멈추는 말(c판 2.2의 4)
PERIOD_LIKE = ("요즘", "요새", "근래", "올해", "작년", "지난", "개월", "분기", "최신")
MIN_PER_TYPE, MAX_PER_TYPE = 4, 6
VERSION = re.compile(r"v[0-9]{1,3}")
ANSWER_KEYS = ("id", "key", "split", "qtype", "months", "scope", "call", "sample", "listing_value", "basis", "deposit", "monthly",
               "converted", "diff", "t5_count")


class BadTemplates(ValueError):
    """틀 문장 파일이 2.2의 검사에 걸렸다. problems는 걸린 것 모두다."""

    def __init__(self, problems):
        super().__init__("; ".join(problems))
        self.problems = list(problems)


def _after_ok(rest):
    """{이름} 바로 뒤 글(rest)이 받침과 상관없으면 True."""
    if rest == "" or rest[0].isspace() or unicodedata.category(rest[0]).startswith("P"):
        return True
    if rest.startswith(AFTER_BAD):
        return False
    return rest.startswith(AFTER_OK)


def check_one(qtype, text):
    """틀 문장 하나의 걸린 까닭들(없으면 빈 목록)과 기간 말(없으면 None)."""
    why = []
    if not isinstance(text, str) or text.strip() == "":
        return ["비었거나 글이 아니다"], None
    if text != text.strip():
        why.append("앞뒤에 빈칸이 있다")
    if "\n" in text or "\r" in text:
        why.append("줄바꿈이 있다")
    if unicodedata.normalize("NFC", text) != text:
        why.append("NFC가 아니다")
    if text.count(SLOT) > 1:
        why.append("{이름}이 두 번 이상 있다")
    rest = text.replace(SLOT, "")
    if "{" in rest or "}" in rest:
        why.append("{이름} 밖에 중괄호가 있다")
    if "이름" in rest:
        why.append("{이름} 밖에 '이름'이 있다(자리 표시를 다른 꼴로 적은 것일 수 있다)")
    i = text.find(SLOT)
    if i >= 0 and not _after_ok(text[i + len(SLOT):]):
        why.append("{이름} 바로 뒤에 붙은 말이 받침과 상관없는 말의 목록에 없다")
    found = [p for p in PERIODS if p in text]
    left = text
    for p in PERIODS:
        left = left.replace(p, " ")
    if "최근" in left:
        why.append("'최근'이 기간 말 밖에 있다")
    if any(ch.isdigit() for ch in left):
        why.append("숫자가 기간 말 밖에 있다")
    for w in PERIOD_LIKE:
        if w in left:
            why.append(f"기간을 뜻하는 말이 있다: '{w}'")
    count = sum(text.count(p) for p in PERIODS)
    period = None
    if qtype in ("T1", "T2", "T4"):
        if found:
            why.append("기간 말이 있다(이 유형은 기간을 말하지 않는다)")
    elif qtype == "T3":
        t3 = [p for p in T3_PERIODS if p in text]
        if len(t3) != 1 or count != 1:
            why.append("'최근 1개월'·'최근 3개월'·'최근 6개월' 가운데 꼭 하나만 있어야 한다")
        else:
            period = t3[0]
    elif qtype == "T5":
        if found != ["최근 1년"] or text.count("최근 1년") != 1:
            why.append("'최근 1년'만 꼭 한 번 있어야 한다")
        else:
            period = "최근 1년"
    if qtype in ("T2", "T5") and "단지" not in rest:
        why.append("'단지'가 없다")
    if qtype in ("T1", "T3", "T4") and "단지" in rest:
        why.append("'단지'가 있다(이 유형은 범위를 말하지 않는다)")
    return why, period


def check(doc):
    """틀 문장 파일(JSON을 읽은 것)을 본다. 걸리면 BadTemplates. 맞으면 (판, {유형: [틀 문장]})."""
    problems = []
    if not isinstance(doc, dict) or sorted(doc) != ["templates", "version"]:
        raise BadTemplates(["칸은 version·templates뿐이어야 한다"])
    version = doc["version"]
    if not isinstance(version, str) or not VERSION.fullmatch(version):
        problems.append("version은 v0 꼴이어야 한다")
    tp = doc["templates"]
    if not isinstance(tp, dict) or sorted(tp) != list(TYPES):
        raise BadTemplates(problems + ["templates는 T1~T5 다섯뿐이어야 한다"])
    seen = set()
    for qtype in TYPES:
        items = tp[qtype]
        if not isinstance(items, list) or not MIN_PER_TYPE <= len(items) <= MAX_PER_TYPE:
            problems.append(f"{qtype}: 틀 문장은 {MIN_PER_TYPE}~{MAX_PER_TYPE}개여야 한다")
            continue
        periods = set()
        for n, text in enumerate(items, 1):
            why, period = check_one(qtype, text)
            if isinstance(text, str):
                if text in seen:
                    why.append("겹친다")
                seen.add(text)
            problems.extend(f"{qtype}의 {n}번째: {w}" for w in why)
            if period:
                periods.add(period)
        if qtype == "T3" and periods != set(T3_PERIODS):
            missing = "·".join(f"'{p}'" for p in T3_PERIODS if p not in periods)
            problems.append(f"T3: 세 기간 말을 모두 한 번 이상 써야 한다(없는 것: {missing})")
    if problems:
        raise BadTemplates(problems)
    return version, {t: list(tp[t]) for t in TYPES}


def load_templates(path):
    with open(path, "r", encoding="utf-8") as f:
        text = f.read()
    if text.startswith("\ufeff"):
        raise BadTemplates(["파일 앞에 BOM이 있다"])
    try:
        doc = json.loads(text)
    except json.JSONDecodeError as e:
        raise BadTemplates([f"JSON이 아니다({e.lineno}번째 줄)"]) from e
    return check(doc)


def period_of(qtype, template):
    """그 틀 문장이 정하는 창(달 수). T3은 기간 말, 나머지는 12달."""
    if qtype != "T3":
        return 12
    return next(PERIODS[p] for p in T3_PERIODS if p in template)


def names(listings_input_path, keys):
    """매물 블록의 이름(listings_input_v1.json의 stockName). 키가 매물 파일과 같아야 한다."""
    with open(listings_input_path, "r", encoding="utf-8") as f:
        items = json.load(f)
    out = {x["key"]: x["fields"]["stockName"] for x in items}
    if list(out) != list(keys):
        raise ValueError("매물 입력의 키가 매물 파일과 다르다")
    return out


def build(L, name_of, templates):
    """문항 줄들(문항 파일 차례). L은 listings.load의 [(키, 칸)], name_of는 키 → 이름, templates는 check의 둘째 값."""
    rows = []
    for key, x in L:
        for qtype in sorted(answers.assign(x["complex_id"], x["deal"])):
            rows.append({"key": key, "qtype": qtype, "split": x["split"], "cid": x["complex_id"]})
    for n, r in enumerate(rows, 1):
        r["id"] = "q%03d" % n
    groups = defaultdict(list)
    for r in rows:
        groups[(r["split"], r["qtype"])].append(r)
    for (_, qtype), g in groups.items():
        g.sort(key=lambda r: (H("qtext", r["cid"] + "|" + qtype), r["key"]))
        for i, r in enumerate(g):
            r["template"] = i % len(templates[qtype])
    for r in rows:
        tpl = templates[r["qtype"]][r["template"]]
        text = tpl.replace(SLOT, name_of[r["key"]])
        if unicodedata.normalize("NFC", text) != text or "{" in text or "}" in text:
            raise ValueError(f"{r['id']}: 채운 질문이 NFC가 아니거나 중괄호가 남았다")
        r["text"] = text
        r["months"] = period_of(r["qtype"], tpl)
    return rows


def questions_doc(rows, version):
    """하네스가 읽는 문항 파일(6a 메모 3절 12의 꼴)."""
    return {"version": version, "questions": [{"id": r["id"], "key": r["key"], "qtype": r["qtype"], "split": r["split"], "text": r["text"]}
                                              for r in rows]}


def answers_for(rows, cases, check_cases):
    """문항마다 정답(대조용 정답의 같은 (매물, 유형, 창) 줄 + id·split). 지금 셈(cases)이 대조용 파일(check_cases)과 다르면 ValueError."""
    def by(cs):
        out = {}
        for c in cs:
            k = (c["key"], c["qtype"], c["months"])
            if k in out:
                raise ValueError(f"대조용 정답에 같은 (매물, 유형, 창)이 둘이다: {k[1]} {k[2]}달")
            out[k] = c
        return out
    now, old = by(cases), by(check_cases)
    if now != old:
        raise ValueError("대조용 정답 파일이 지금 셈과 다르다. prep answers를 다시 돌리고 하네스 시험(대조한 문항)을 다시 본다")
    out = []
    for r in rows:
        c = now.get((r["key"], r["qtype"], r["months"]))
        if c is None:
            raise ValueError(f"{r['id']}: 대조용 정답에 그 (매물, 유형, 창)이 없다")
        a = dict(c)
        a["id"] = r["id"]
        a["split"] = r["split"]
        out.append({k: a[k] for k in ANSWER_KEYS if k in a})
    return out


def dumps(obj):
    return json.dumps(obj, ensure_ascii=False, indent=1) + "\n"


def write_json(obj, path):
    write_text(path, dumps(obj))


def sha256_lf(path):
    """줄 끝을 LF로 맞춘 SHA-256(대문자). 하네스의 Fingerprint.file·record_sha256과 같은 셈이다."""
    with open(path, "rb") as f:
        b = f.read()
    return hashlib.sha256(b.replace(b"\r\n", b"\n")).hexdigest().upper()


def summary(rows, ans, templates):
    """찍을 줄들. 이름·주소·단지ID·질문 글은 넣지 않는다."""
    lines = []
    tcount = {t: len(templates[t]) for t in TYPES}
    t3 = defaultdict(int)
    for tpl in templates["T3"]:
        t3[period_of("T3", tpl)] += 1
    lines.append("틀 문장: " + "·".join(f"{t} {tcount[t]}" for t in TYPES) + f" (T3의 창: 1개월 {t3[1]}·3개월 {t3[3]}·6개월 {t3[6]})")
    by = defaultdict(int)
    for r in rows:
        by[(r["split"], r["qtype"])] += 1
    splits = ("연습", "본시험")
    total = {s: sum(by[(s, t)] for t in TYPES) for s in splits}
    lines.append(f"문항: {len(rows)}개(" + "·".join(f"{s} {total[s]}" for s in splits) + "), "
                 + ", ".join(f"{s} " + "·".join(f"{t} {by[(s, t)]}" for t in TYPES) for s in splits))
    win = defaultdict(int)
    for r in rows:
        if r["qtype"] == "T3":
            win[(r["split"], r["months"])] += 1
    lines.append("T3 창: " + ", ".join(f"{s} " + "·".join(f"{m}개월 {win[(s, m)]}" for m in (1, 3, 6)) for s in splits))
    use = defaultdict(int)
    for r in rows:
        use[(r["split"], r["qtype"], r["template"])] += 1
    lines.append("틀 문장마다 쓴 수: " + "; ".join(f"{s} " + ", ".join(f"{t} " + "·".join(str(use[(s, t, i)]) for i in range(tcount[t]))
                                                                for t in TYPES) for s in splits))
    none2 = defaultdict(int)
    none5 = defaultdict(int)
    for a in ans:
        if a["qtype"] == "T2" and a["sample"] == 0:
            none2[a["split"]] += 1
        if a["qtype"] == "T5" and a.get("t5_count") == 0:
            none5[a["split"]] += 1
    lines.append("거래 없음: T2 0건 " + "·".join(f"{s} {none2[s]}" for s in splits) + ", T5 건수 0 " + "·".join(f"{s} {none5[s]}" for s in splits))
    sc = defaultdict(int)
    for a in ans:
        sc[a["scope"]] += 1
    lines.append("범위: " + "·".join(f"{k} {sc[k]}" for k in ("단지", "법정동", "시군구")))
    return lines
