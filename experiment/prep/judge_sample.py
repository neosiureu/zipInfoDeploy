# -*- coding: utf-8 -*-
"""순서 11a 판정 표본 읽기(prereg_judge_v0 2.1절, 01 219줄): 한 단계의 판정에서 사람이 읽을 표본을 뽑고, 사람이 적은 판단을 판정과 댄다.

experiment 폴더에서 친다.
- `python -X utf8 -B -m prep judge-sample harness/settings/pilot_v0.json`: 표본을 뽑아 <files.out>/<단계>/judge_sample/sample.txt에 쓴다.
- `python -X utf8 -B -m prep judge-tally harness/settings/pilot_v0.json`: sample.txt에 적은 판단을 판정과 대 tally.tsv·differ.txt를 쓴다.

- 채점(prep score)과 같은 함수로 묶기 검사를 하고 실행마다 매긴다. 걸리면 아무것도 쓰지 않는다(끝 코드 3).
- 표본 A: 판정 있는 실행을 H("judge_a", 실행 ID)가 작은 순으로 세워 앞 40개. 판정과 사람이 맞는 비율을 잰다.
- 표본 B: 값·규칙을 다 지났는데 판정이 "답하지 않음"인 실행(판정 하나로 완전 정답이 못 된 실행)을 H("judge_b", 실행 ID) 순으로 앞 20개.
- 읽는 차례는 H("judge_order", 실행 ID) 순이다. 어느 항목이 A인지 B인지, 판정이 무엇인지는 sample.txt에 없다(가린 채 읽는다).
- 순서 11b-1(설계 4판 4.4, prereg_judge 2.1): 채점 설정 judge_sample_exclude에 그 단계의 옛 표본 폴더가 있으면, 그 폴더 tally.tsv의 실행을
  두 바구니에서 뺀 뒤 같은 법으로 뽑는다. 표본과 대조 모두 뺀다(대조는 표본을 다시 뽑아 지문을 댄다).
- sample.txt·differ.txt에는 질문 글과 답이 들어 있다. 보내지 않는다(runs 아래라 git 밖). 화면과 tally.tsv에는 수, 항목 번호, 실행 ID만 낸다.
- 표준 라이브러리만 쓴다.
"""
import hashlib
import os

from picker.common import H

from . import score

N_A = 40
N_B = 20
PURPOSE_A = "judge_a"
PURPOSE_B = "judge_b"
PURPOSE_ORDER = "judge_order"
DIR = "judge_sample"
SAMPLE = "sample.txt"
TALLY = "tally.tsv"
DIFFER = "differ.txt"
ITEM = "■ 항목 "
MARK = "■ 내 판단: ["
PRINT = "■ 표본 지문: "
TALLY_HEAD = ("항목", "실행 ID", "표본", "판정", "내 판단", "같음")


def pick(rows, skip=frozenset()):
    """(표본 A, 표본 B, 판정 있는 실행 수, B가 될 수 있는 실행 수). 줄은 score_rows의 줄이다. skip의 실행(옛 표본)은 두 바구니에서 뺀다."""
    judged = [x for x in rows if x["outcome"] == "SUBMITTED" and x["judge"] is not None and x["run_id"] not in skip]
    a = sorted(judged, key=lambda x: (H(PURPOSE_A, x["run_id"]), x["run_id"]))[:N_A]
    pool = [x for x in judged if x["value"] and x["rule"] and x["judge"] is False]
    b = sorted(pool, key=lambda x: (H(PURPOSE_B, x["run_id"]), x["run_id"]))[:N_B]
    return a, b, len(judged), len(pool)


def items_of(a, b):
    """읽을 항목: A와 B를 합쳐(겹치면 하나) 읽는 차례로 세운다. 항목마다 (실행 ID, 표본 "A"·"B"·"AB", 판정)."""
    tag = {}
    for x in a:
        tag[x["run_id"]] = ["A", x["judge"]]
    for x in b:
        tag.setdefault(x["run_id"], ["", x["judge"]])[0] += "B"
    ids = sorted(tag, key=lambda rid: (H(PURPOSE_ORDER, rid), rid))
    return [(rid, tag[rid][0], tag[rid][1]) for rid in ids]


def texts(ctx, rid):
    """판정 모델이 받은 것과 같은 질문 글과 받아들여진 답(그린 답)."""
    q = next(r["question"]["text"] for r in ctx["plan"] if r["id"] == rid)
    acc = [e for e in ctx["records"][rid]["events"] if e.get("type") == "accepted"]
    if len(acc) != 1 or not isinstance(acc[0].get("answer"), str):
        raise score.Bind(f"{rid}.jsonl: 받아들여진 답(accepted)을 찾지 못했다")
    return q, acc[0]["answer"]


def sample_print(ctx, items):
    """표본 지문: 판정 지문, 항목마다 (실행 ID, 표본, 기록 해시)를 이은 SHA-256 앞 12자. 기록이나 판정이 바뀌면 달라진다."""
    parts = [ctx["judge_print"] or "-"] + [f"{rid}|{t}|{ctx['records'][rid]['sha']}" for rid, t, _ in items]
    return hashlib.sha256("\n".join(parts).encode("utf-8")).hexdigest()[:12].upper()


def indent(text):
    return "".join("  " + line + "\n" for line in score.lf(text.encode("utf-8")).decode("utf-8").split("\n"))


def instructions(settings_path, ctx):
    """설정 judge.instructions의 지시문(줄 끝 LF)과 그 경로. 채점은 judge 칸을 읽지 않아 설정 파일을 다시 읽는다."""
    raw = score.read_json(settings_path, "하네스 설정", exact=True)
    rel = raw.get("judge", {}).get("instructions") if isinstance(raw.get("judge"), dict) else None
    if not isinstance(rel, str) or not os.path.exists(ctx["at"](rel)):
        raise score.Bad("설정의 판정 지시문 파일이 없다")
    with open(ctx["at"](rel), "rb") as f:
        return score.lf(f.read()).decode("utf-8").strip("\n"), rel


def sample_text(settings_path, ctx, items, print_):
    body, rel = instructions(settings_path, ctx)
    out = [f"판정 표본 읽기: 단계 {ctx['s']['stage']}, 판정 지문 {(ctx['judge_print'] or '-')[:12]}, 항목 {len(items)}개\n"]
    if skip_text(ctx):
        out.append(skip_text(ctx) + "\n")
    out += ["이 파일은 보내지 않는다. 질문 글과 답이 들어 있다.\n\n",
            "읽는 법\n",
            "- 항목마다 [질문]과 [답]을 읽고, 아래 지시문의 잣대로 답이 질문에 답했는지만 본다. 판정 모델이 받은 지시문 그대로다.\n",
            "- 항목 끝의 \"내 판단\" 줄 괄호 안에, 답했으면 O, 답하지 않았으면 X를 적는다(영문 대문자 O·X). 다른 줄은 고치지 않고 저장한다.\n",
            "- 판정 모델의 판정은 일부러 보이지 않는다. 모두 적은 뒤 judge-tally를 친다.\n\n",
            f"판정 지시문({rel})\n", indent(body), "\n", f"{PRINT}{print_}\n"]
    for n, (rid, _, _) in enumerate(items, 1):
        q, ans = texts(ctx, rid)
        out += [f"\n{ITEM}{n}\n", "[질문]\n", indent(q), "\n[답]\n", indent(ans), f"\n{MARK} ]\n"]
    return "".join(out)


def sample_lines(path):
    """sample.txt의 줄(BOM은 떼고 줄 끝은 LF로)."""
    with open(path, "rb") as f:
        raw = f.read()
    if raw.startswith(b"\xef\xbb\xbf"):
        raw = raw[3:]
    try:
        return score.lf(raw).decode("utf-8").split("\n")
    except UnicodeDecodeError:
        raise score.Bad(f"{SAMPLE}가 UTF-8이 아니다. 메모장에서 인코딩을 UTF-8로 골라 다시 저장한다") from None


def printed_of(path):
    """sample.txt에 찍힌 표본 지문(그 줄이 꼭 하나일 때만, 아니면 None)."""
    printed = [x[len(PRINT):].strip() for x in sample_lines(path) if x.startswith(PRINT)]
    return printed[0] if len(printed) == 1 else None


def read_marks(path, n):
    """sample.txt에서 항목마다 적은 판단("O"·"X")을 읽는다. 꼴이 틀리면 Bad(항목 번호만 적는다)."""
    lines = sample_lines(path)
    printed = [x[len(PRINT):].strip() for x in lines if x.startswith(PRINT)]
    heads, marks, cur = [], {}, None
    for x in lines:
        if x.startswith(ITEM):
            cur = x[len(ITEM):].strip()
            heads.append(cur)
        elif x.startswith(MARK):
            if cur is None or cur in marks:
                raise score.Bad(f"\"■ 내 판단\" 줄이 항목 머리 없이 있거나 한 항목에 둘이다(항목 {cur})")
            body = x[len(MARK):]
            if not body.rstrip().endswith("]"):
                raise score.Bad(f"항목 {cur}의 \"■ 내 판단\" 줄이 ]로 끝나지 않는다")
            marks[cur] = body.rstrip()[:-1].strip().upper()
    if heads != [str(i) for i in range(1, n + 1)]:
        raise score.Bad(f"항목 머리가 1부터 {n}까지 차례대로 있지 않다(읽은 머리 {len(heads)}개)")
    empty = [h for h in heads if h not in marks or marks[h] == ""]
    bad = [h for h in heads if h in marks and marks[h] not in ("", "O", "X")]
    if empty or bad:
        msg = []
        if empty:
            msg.append(f"적지 않은 항목 {len(empty)}개(" + ", ".join(empty[:10]) + (" …" if len(empty) > 10 else "") + ")")
        if bad:
            msg.append(f"O·X가 아닌 항목 {len(bad)}개(" + ", ".join(bad[:10]) + (" …" if len(bad) > 10 else "") + ")")
        raise score.Bad(" / ".join(msg))
    return [marks[str(i)] for i in range(1, n + 1)], (printed[0] if len(printed) == 1 else None)


def reasons(ctx):
    """지금 쓰는 판정 줄의 이유(실행 ID → 글). differ.txt에만 쓴다."""
    path = os.path.join(ctx["stage_dir"], "judge", "verdicts.jsonl")
    with open(path, "rb") as f:
        lines = score.jsonl(f.read(), "judge/verdicts.jsonl")
    want = {(ctx["s"]["stage"], rid, r["sha"]) for rid, r in ctx["records"].items()}
    return {v["run_id"]: v.get("reason") for v in lines if (v.get("stage"), v.get("run_id"), v.get("record_sha256")) in want}


def excluded(ctx):
    """뺄 옛 표본(채점 설정 judge_sample_exclude의 그 단계 폴더들): 실행 ID 모음과 [(폴더, 실행 수)]. tally.tsv의 실행 ID 칸을 읽는다."""
    where = f"{ctx['s']['files']['out']}/{ctx['s']['stage']}"
    ids, per = set(), []
    for name in ctx["cfg"]["judge_sample_exclude"].get(ctx["s"]["stage"], []):
        path = os.path.join(ctx["stage_dir"], name, TALLY)
        if not os.path.exists(path):
            raise score.Bad(f"뺄 옛 표본의 {TALLY}가 없다: {where}/{name}/{TALLY}")
        with open(path, "rb") as f:
            raw = f.read()
        try:
            lines = score.lf(raw).decode("utf-8").split("\n")
        except UnicodeDecodeError:
            raise score.Bad(f"{where}/{name}/{TALLY}가 UTF-8이 아니다") from None
        while lines and lines[-1] == "":
            lines.pop()
        rows = [x.split("\t") for x in lines[1:]]
        if not lines or lines[0].split("\t") != list(TALLY_HEAD) or not rows or any(len(r) != len(TALLY_HEAD) for r in rows):
            raise score.Bad(f"{where}/{name}/{TALLY}가 judge-tally가 쓴 꼴이 아니다")
        got = [r[1] for r in rows]
        miss = [x for x in got if x not in ctx["records"]]
        if len(set(got)) != len(got) or miss:
            raise score.Bad(f"{where}/{name}/{TALLY}의 실행 ID가 겹치거나 이 단계의 계획에 없다(없는 것 {len(miss)}개)")
        ids |= set(got)
        per.append((name, len(got)))
    return ids, per


def _prepare(settings_path, exp, listings, pin, config):
    ctx = score.prepare(settings_path, exp, listings, pin, config)
    rows = score.score_rows(ctx)
    skip, per = excluded(ctx)
    a, b, n_judged, n_pool = pick(rows, skip)
    items = items_of(a, b)
    _, _, all_judged, all_pool = pick(rows)
    ctx["skip"] = {"per": per, "judged": all_judged - n_judged, "pool": all_pool - n_pool}
    return ctx, a, b, n_judged, n_pool, items


def skip_text(ctx):
    """화면·sample.txt 머리에 낼 뺀 옛 표본의 수. 없으면 빈 글."""
    k = ctx["skip"]
    if not k["per"]:
        return ""
    return ("뺀 옛 표본 " + ", ".join(f"{name} {n}개" for name, n in k["per"]) + f"(판정 있는 실행에서 {k['judged']}개, "
            f"B가 될 수 있는 실행에서 {k['pool']}개)")


def _guard(fn, out):
    try:
        return fn()
    except score.Bad as e:
        out(f"멈춤(설정이나 파일): {e}")
        return 2
    except score.Bind as e:
        out(f"멈춤(묶기 검사): {e}. 아무것도 쓰지 않았다")
        return 3


def sample(settings_path, exp=".", listings=score.LISTINGS, pin=None, config=score.CONFIG, out=print):
    """표본을 뽑아 sample.txt에 쓴다. 이미 있으면 적은 판단을 지우지 않으려고 쓰지 않는다(끝 코드 2)."""
    def go():
        ctx, a, b, n_judged, n_pool, items = _prepare(settings_path, exp, listings, pin, config)
        if not items:
            raise score.Bad("판정 있는 실행이 없다")
        d = os.path.join(ctx["stage_dir"], DIR)
        path = os.path.join(d, SAMPLE)
        if os.path.exists(path):
            raise score.Bad(f"{ctx['s']['files']['out']}/{ctx['s']['stage']}/{DIR}/{SAMPLE}가 이미 있다. 적은 판단을 지우지 않으려고 덮어쓰지 않는다")
        print_ = sample_print(ctx, items)
        text = sample_text(settings_path, ctx, items, print_)
        os.makedirs(d, exist_ok=True)
        with open(path, "x", encoding="utf-8", newline="\n") as f:
            f.write(text)
        both = sum(1 for _, t, _ in items if t == "AB")
        if skip_text(ctx):
            out("판정 표본: " + skip_text(ctx))
        out(f"판정 표본: 판정 있는 실행 {n_judged}개 가운데 A {len(a)}개, 값·규칙을 지났는데 판정이 답하지 않음인 실행 {n_pool}개 가운데 B {len(b)}개"
            f"(겹침 {both}개), 읽을 항목 {len(items)}개, 표본 지문 {print_}")
        out(f"{ctx['s']['files']['out']}/{ctx['s']['stage']}/{DIR}/{SAMPLE}에 썼다. 질문 글과 답이 들어 있어 보내지 않는다")
        return 0
    return _guard(go, out)


def tally(settings_path, exp=".", listings=score.LISTINGS, pin=None, config=score.CONFIG, out=print):
    """sample.txt에 적은 판단을 판정과 대 화면과 tally.tsv(수·번호·실행 ID), differ.txt(다른 항목의 글과 판정 이유)에 쓴다."""
    def go():
        ctx, a, b, n_judged, n_pool, items = _prepare(settings_path, exp, listings, pin, config)
        d = os.path.join(ctx["stage_dir"], DIR)
        path = os.path.join(d, SAMPLE)
        if not os.path.exists(path):
            raise score.Bad(f"{ctx['s']['files']['out']}/{ctx['s']['stage']}/{DIR}/{SAMPLE}가 없다. 먼저 judge-sample을 친다")
        print_ = sample_print(ctx, items)
        printed = printed_of(path)  # 표본이 다르면(판정을 다시 냈거나 빼기 설정이 다름) 판단을 읽기 전에 멈춘다
        if printed != print_:
            raise score.Bind(f"{SAMPLE}의 표본 지문({printed})이 지금 기록·판정으로 뽑은 표본({print_})과 다르다. 판정을 다시 냈으면 그 폴더를 옮기고 "
                             "judge-sample부터 다시 한다")
        marks, _ = read_marks(path, len(items))
        rows, diff = [], []
        for n, ((rid, t, judge), m) in enumerate(zip(items, marks), 1):
            same = (m == "O") == judge
            rows.append((n, rid, t, judge, m, same))
            if not same:
                diff.append(n)
        A = [r for r in rows if "A" in r[2]]
        B = [r for r in rows if "B" in r[2]]
        same_a = sum(1 for r in A if r[5])
        a_yes_x = [r[0] for r in A if r[3] is True and r[4] == "X"]
        a_no_o = [r[0] for r in A if r[3] is False and r[4] == "O"]
        b_o = [r[0] for r in B if r[4] == "O"]
        nums = lambda xs: "(항목 " + ", ".join(map(str, xs)) + ")" if xs else ""
        ok_a = same_a * 10 >= len(A) * 9
        ok_b = len(b_o) * 4 <= len(B)
        tsv = ["\t".join(TALLY_HEAD)] + ["\t".join([str(n), rid, t, "답함" if j else "답하지 않음", m, "1" if s else "0"]) for n, rid, t, j, m, s in rows]
        dtext = [f"판정과 내 판단이 다른 항목 {len(diff)}개: 단계 {ctx['s']['stage']}, 표본 지문 {print_}\n",
                 "이 파일은 보내지 않는다. 질문 글과 답, 판정 이유가 들어 있다.\n"]
        why = reasons(ctx)
        for n in diff:
            rid, t, judge = items[n - 1]
            q, ans = texts(ctx, rid)
            dtext += [f"\n{ITEM}{n} (표본 {t}, 판정 {'답함' if judge else '답하지 않음'}, 내 판단 {marks[n - 1]})\n", "[질문]\n", indent(q),
                      "\n[답]\n", indent(ans), "\n[판정 이유]\n", indent(why.get(rid) or "")]
        with open(os.path.join(d, TALLY), "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(tsv) + "\n")
        with open(os.path.join(d, DIFFER), "w", encoding="utf-8", newline="\n") as f:
            f.write("".join(dtext))
        if skip_text(ctx):
            out("판정 표본 대조: " + skip_text(ctx))
        out(f"판정 표본 대조: 항목 {len(items)}개(A {len(A)}개, B {len(B)}개, 겹침 {sum(1 for r in rows if r[2] == 'AB')}개), 표본 지문 {print_}")
        out(f"표본 A(판정 있는 실행 {n_judged}개 가운데 {len(A)}개): 같음 {same_a}개, 판정 답함·내 판단 X {len(a_yes_x)}개{nums(a_yes_x)}, "
            f"판정 답하지 않음·내 판단 O {len(a_no_o)}개{nums(a_no_o)}")
        out(f"표본 B(값·규칙을 지났는데 판정이 답하지 않음인 실행 {n_pool}개 가운데 {len(B)}개): 내 판단 X(판정과 같음) {len(B) - len(b_o)}개, "
            f"내 판단 O(판정과 다름) {len(b_o)}개{nums(b_o)}")
        out(f"기준(prereg_judge 2.1): A 같음 {-(-len(A) * 9 // 10)}개 이상 {'넘음' if ok_a else '못 넘음'}, "
            f"B의 O {len(B) // 4}개 이하 {'넘음' if ok_b else '못 넘음'}")
        out(f"{ctx['s']['files']['out']}/{ctx['s']['stage']}/{DIR}/{TALLY}와 {DIFFER}를 썼다. {DIFFER}는 글이 있어 보내지 않는다")
        return 0
    return _guard(go, out)
