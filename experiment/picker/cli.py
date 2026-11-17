# -*- coding: utf-8 -*-
"""명령: test, history, dist, pins, run.

experiment 폴더에서 `python -X utf8 -B -m picker <명령>`으로 돌린다.
화면과 기록에 내는 글은 모두 Scrubber를 거친다(작업 폴더 '.', 홈 '~', 키 '***').
"""
import argparse
import io
import os
import platform
import sys
import traceback
import unittest
import zipfile

from . import codego, dist, juso, model, snapshot
from .clean import Cleaner, Drop, NEUNG_CODES, Pending, UPPER_HWASEONG
from .common import RULE, Scrubber, code_sorted, now_iso, read_csv, rel, sha256_file, write_csv, write_text

PINS = "picker_pins_v1.csv"
HISTORY = "frozen/codego/codego_history_v1.csv"
CODEGO_DIR = "frozen/codego"
OUT = {
    "dist": "value_distribution_v1.csv",
    "juso": "juso_jibun_v1.csv",
    "cands": "complex_candidates_v1.csv",
    "listings": "example_listings_v1.csv",
    "log": "run_log_v1.txt",
    "man_T": "manifest_T.csv",
    "man_1": "manifest_1st_run.csv",
}
DETAIL = "국토부 실거래가 공개자료로 만든 예시 매물입니다. 실제로 거래할 수 있는 매물이 아닙니다."
LISTING_FIELDS = ["stockName", "stockAddress", "stockForm", "stockType", "stockSellPrice", "stockFeeMonth",
                  "exclusiveArea", "currentFloor", "useApprovalDate", "supplyArea", "roomCount", "bathCount",
                  "stockDirection", "floorTotalCount", "stockManageFee", "ableDate", "stockInfo", "stockDetail", "regionNo"]
LISTING_EXTRA = ["complex_id", "bundle", "layer", "deal", "split", "stage", "hash_pick", "hash_split", "marks", "juso_query"]
CAND_HEADER = ["row", "layer", "complex_id", "bundle", "deal", "api", "count", "marks", "misaligned_months",
               "hash_pick", "name", "build_year", "bundles", "target_base", "carry_in", "target", "carry_out", "quota"]


class Stop(RuntimeError):
    """멈춘다. 까닭을 기록과 화면에 낸다."""


class Log:
    """화면과 실행 기록에 같은 글을 낸다. 기록은 줄마다 바로 덧붙인다(끊겨도 그때까지는 남는다)."""

    def __init__(self, path, scrub, echo=True):
        self.path, self.scrub, self.echo = path, scrub, echo
        self.lines = []
        if self.path:
            os.makedirs(os.path.dirname(self.path) or ".", exist_ok=True)

    def __call__(self, text=""):
        t = self.scrub(text)
        if self.echo:
            print(t, flush=True)
        self.lines.append(t)
        if self.path:
            with open(self.path, "a", encoding="utf-8", newline="\n") as f:
                f.write(t + "\n")


# ---------------------------------------------------------------- 고정 해시

def picker_files():
    base = os.path.dirname(os.path.abspath(__file__))
    out = []
    for root, dirs, files in os.walk(base):
        dirs[:] = [d for d in dirs if d != "__pycache__"]
        for fn in files:
            if fn.endswith(".pyc"):
                continue
            full = os.path.join(root, fn)
            out.append(os.path.relpath(full, os.path.dirname(base)).replace(os.sep, "/"))
    return code_sorted(out)


def codego_files():
    names = [n for n in os.listdir(CODEGO_DIR) if n.endswith(".html")]
    return [f"{CODEGO_DIR}/{n}" for n in code_sorted(names)] + [HISTORY]


def load_pins(path):
    if not os.path.exists(path):
        raise Stop(f"고정 해시 파일 {path}가 없다. ⑤에서 합의한 파일을 같은 폴더에 둔다")
    header, rows = read_csv(path)
    if header != ["name", "sha256"]:
        raise Stop(f"{path} 머리줄이 다르다")
    return {n: h for n, h in rows}


def check_pins(pins, zips, log):
    """코드·이력·24쪽·zip·코드 목록의 해시를 고정 값과 대 본다. 하나라도 다르면 멈춘다."""
    bad = []
    have = {}
    for p in picker_files() + codego_files():
        have[p] = sha256_file(p)
    for label, p in zips.items():
        have[label] = sha256_file(p)
    want_files = {n for n in pins if not n.startswith("out:")}
    for n in code_sorted(set(have) | want_files):
        h = have.get(n)
        w = pins.get(n)
        if h is None:
            bad.append(f"없는 파일: {n}")
        elif w is None:
            bad.append(f"고정 값에 없는 파일: {n}")
        elif h != w.upper():
            bad.append(f"해시가 다름: {n}")
        else:
            log(f"해시 같음: {n} {h}")
    if bad:
        for b in bad:
            log(b)
        raise Stop("해시가 고정 값과 다르다. 도로명주소를 부르기 전에 멈춘다")


# ---------------------------------------------------------------- 명령

def cmd_test(args, log):
    here = os.path.dirname(os.path.abspath(__file__))
    suite = unittest.defaultTestLoader.discover(os.path.join(here, "tests"), top_level_dir=os.path.dirname(here))
    buf = io.StringIO()
    res = unittest.TextTestRunner(stream=buf, verbosity=1).run(suite)
    for line in buf.getvalue().splitlines():
        log(line)
    if not res.wasSuccessful():
        raise Stop(f"시험 실패 {len(res.failures)}, 오류 {len(res.errors)}")
    log(f"시험 통과 {res.testsRun}개")
    return 0


def cmd_history(args, log):
    rows, infos = codego.build_history(CODEGO_DIR)
    for i in infos:
        log(f"쪽 {i['page']}: 조회 {i['prefix']}, 총 {i['total']}건, 뽑은 줄 {i['rows']}")
    out = args.out or HISTORY
    codego.write_history(out, rows)
    log(f"이력 표 {rel(out)}: {len(rows)}줄, SHA-256 {sha256_file(out)}")
    return 0


def _inputs(args):
    codes = code_sorted(snapshot.read_codes(args.codes))
    months = snapshot.month_list()
    hist = codego.History(codego.load_history_rows(HISTORY))
    return codes, months, hist


def cmd_dist(args, log):
    codes, months, hist = _inputs(args)
    with snapshot.Snapshot(args.zip) as snap:
        rows, summary, problems = dist.count_all(snap, hist, codes, months)
    out = args.out or os.path.join(args.outdir, OUT["dist"])
    dist.write(out, rows)
    for k, v in summary.items():
        log(f"읽기 확인: {k} {v}")
    log(f"값 분포 표 {rel(out)}: {len(rows)}줄, SHA-256 {sha256_file(out)}")
    if problems:
        raise Stop(f"읽기 확인 문제 {len(problems)}개")
    return 0


def cmd_pins(args, log):
    """⑤에서 합의한 뒤 내가 돌린다. 고정 해시 파일을 쓴다."""
    rows = []
    for p in picker_files() + codego_files():
        rows.append([p, sha256_file(p)])
    rows.append(["in:snapshot_T.zip", sha256_file(args.zip)])
    rows.append(["in:snapshot_1st_run.zip", sha256_file(args.first_zip)])
    rows.append(["in:codes_snapshot.txt", sha256_file(args.codes)])
    rows.append(["out:" + OUT["dist"], sha256_file(args.dist)])
    write_csv(args.out or PINS, ["name", "sha256"], rows)
    log(f"고정 해시 {len(rows)}개를 {rel(args.out or PINS)}에 썼다")
    return 0


def _copy_member(zpath, member, out):
    with zipfile.ZipFile(zpath) as zf:
        data = zf.read(member)
    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    with open(out, "wb") as f:
        f.write(data)


def cmd_run(args, log):
    log(f"규칙: {RULE}, 모드: {args.mode}, 파이썬 {platform.python_version()}")
    if args.mode == "live" and not os.environ.get(juso.KEY_ENV, ""):
        raise Stop(f"부르기 모드에는 환경 변수 {juso.KEY_ENV}가 있어야 한다. 아무것도 돌리지 않고 멈춘다")
    req = " ".join(f"{k}={v}" for k, v in juso.FIXED.items())
    log(f"도로명주소 요청 값(키 뺌): {req}")
    pins = load_pins(args.pins)
    zips = {"in:snapshot_T.zip": args.zip, "in:snapshot_1st_run.zip": args.first_zip, "in:codes_snapshot.txt": args.codes}
    check_pins(pins, zips, log)
    cmd_test(args, log)
    outdir = args.outdir
    # 값 분포: 도로명주소를 부르기 전에 내고 고정 값과 대 본다
    codes, months, hist = _inputs(args)
    with snapshot.Snapshot(args.zip) as snap:
        return _run_with(args, log, pins, outdir, codes, months, hist, snap)


def _run_with(args, log, pins, outdir, codes, months, hist, snap):
    rows, summary, problems = dist.count_all(snap, hist, codes, months)
    dpath = os.path.join(outdir, OUT["dist"])
    dist.write(dpath, rows)
    dh = sha256_file(dpath)
    log(f"값 분포 표 {rel(dpath)} SHA-256 {dh}")
    if problems:
        raise Stop(f"읽기 확인 문제 {len(problems)}개")
    if dh != pins.get("out:" + OUT["dist"], "").upper():
        raise Stop("값 분포 표의 해시가 고정 값과 다르다. 도로명주소를 부르기 전에 멈춘다")
    log("값 분포 표가 고정 값과 같다")
    _copy_member(args.zip, snap.top + "/manifest.csv", os.path.join(outdir, OUT["man_T"]))
    with zipfile.ZipFile(args.first_zip) as zf:
        top1 = {n.split("/", 1)[0] for n in zf.namelist()}
    if len(top1) != 1:
        raise Stop("1차 run zip의 맨 위 폴더가 하나가 아니다")
    _copy_member(args.first_zip, top1.pop() + "/manifest.csv", os.path.join(outdir, OUT["man_1"]))

    key = os.environ.get(juso.KEY_ENV, "") if args.mode == "live" else None
    jpath = args.juso or os.path.join(outdir, OUT["juso"])
    J = juso.Juso(jpath, args.mode, key=key)
    log(f"얼린 도로명주소: {rel(jpath)}, 이미 얼린 검색어 {len(J.frozen)}개")

    result = pick_all(snap, hist, codes, months, J, log)
    write_outputs(result, outdir, log)
    log(f"도로명주소: 이번에 부른 요청 {J.calls}개, 새 검색어 {J.queries_new}개, 얼린 검색어 모두 {len(J.frozen)}개")
    for k in ("dist", "juso", "cands", "listings", "man_T", "man_1"):
        p = jpath if k == "juso" else os.path.join(outdir, OUT[k])
        if os.path.exists(p):
            log(f"출력 {rel(p)} SHA-256 {sha256_file(p)}")
    return 0


# ---------------------------------------------------------------- 뽑기

def resolve_pending(p, hist, J, cl):
    """Pending을 도로명주소로 정한다. (Rec 또는 None, 41590 능동이면 정한 구(None은 구 미정))."""
    mtn, bon, bu = p.fields[0], p.fields[1], p.fields[2]
    jt = juso.jibun_text(mtn, bon, bu)
    if p.why == "능동 구 정하기":
        full = f"{hist.rows[UPPER_HWASEONG + '00000']['name']} 능동 {jt}"
        hits, _ = juso.find(J, full, f"능동 {jt}", set(NEUNG_CODES), mtn, bon, bu)
        got = sorted({h["admCd"] for h in hits})
        if len(got) == 1:
            return cl.finish(p, got[0], False), got[0][:5]
        return None, None
    # 리 겹침: 같은 이름의 리마다 현재 전체 경로와 지번으로
    matched = []
    for path, curs in sorted(p.choices.items()):
        for c in curs:
            full = f"{hist.rows[c]['name']} {jt}"
            hits, _ = juso.find(J, full, f"{hist.path(c)} {jt}", {c}, mtn, bon, bu)
            if hits:
                matched.append(c)
    if len(set(matched)) == 1:
        return cl.finish(p, matched[0], True), None
    return None, None


def pick_all(snap, hist, codes, months, J, log):
    cl = Cleaner(hist)
    data = model.Data(hist)
    n_rec = n_drop = n_pend = 0
    pend_left = {"능동 구 미정": 0, "리 미정": 0}
    for api in code_sorted(snapshot.APIS):
        for code in codes:
            for ym in months:
                for page, item in snap.items((api, code, ym)):
                    res = cl.clean(api, code, ym, page, item)
                    if isinstance(res, Drop):
                        n_drop += 1
                        continue
                    if isinstance(res, Pending):
                        n_pend += 1
                        if res.why == "리 겹침":
                            name = res.fields[12].split(" ")[-1]
                            data.note_ri_overlap(api, code, name, [c for cs in res.choices.values() for c in cs])
                        rec, gu = resolve_pending(res, hist, J, cl)
                        if res.why == "능동 구 정하기":
                            data.note_neung(api, gu)
                        if rec is None:
                            pend_left["능동 구 미정" if res.why == "능동 구 정하기" else "리 미정"] += 1
                            continue
                        res = rec
                    n_rec += 1
                    data.add(res)
    data.finish()
    log(f"다듬은 거래 {n_rec}, 뺀 거래 {n_drop}, 도로명주소로 정한 거래 {n_pend} (구 미정 {pend_left['능동 구 미정']}, 리 미정 {pend_left['리 미정']})")
    log(f"단지 {len(data.cx)}개")

    def address(c):
        cx = data.cx[c]
        jt = juso.jibun_text(cx.mtn, cx.bon, cx.bu)
        full = f"{hist.rows[cx.cur10]['name']} {jt}"
        hits, q = juso.find(J, full, f"{cx.path} {jt}", {cx.cur10}, cx.mtn, cx.bon, cx.bu)
        return (hits[0], q) if hits else None

    pk = model.Picker(data, months, address)
    for L in model.LAYERS:
        base, cin, t, cout = pk.target[L]
        log(f"층 {L}: 후보 묶음 {pk.avail[L]}, 목표 {base}, 넘겨받음 {cin}, 바뀐 목표 {t}, 넘김 {cout}")
    log(f"어긋난 달이 있는데 3·4 어디에도 들지 않는 쌍: {pk.none_pairs}")
    got = pk.run()
    sp = model.split(got)
    for L in model.LAYERS:
        k, m, short = sp[L]
        log(f"층 {L}: 뽑음 {len(got[L])} (연습 {k}, 본시험 {m}){' 최소 미달' if short else ''}")
    log(f"주소를 못 찾아 건너뛴 쌍: {len(pk.skipped_addr)}")
    return {"data": data, "picker": pk, "got": got, "split": sp}


def write_outputs(result, outdir, log):
    data, pk, got, sp = result["data"], result["picker"], result["got"], result["split"]
    order = {L: i for i, L in enumerate(model.LAYERS)}
    rows = []
    for h, key, c, d, L, n, mk, nbad in sorted(pk.pairs, key=lambda p: (order.get(p[4], 9), p[0], p[1])):
        cx = data.cx[c]
        rows.append(["pair", L, c, pk.bundle[c], d, model.API_OF[(cx.kind, d)], n, ";".join(mk), nbad, h,
                     cx.name(), cx.build(), "", "", "", "", "", ""])
    for L in model.LAYERS:
        base, cin, t, cout = pk.target[L]
        q = "; ".join(f"{k} {d} {pk.quota[L][(k, d)]}" for k in model.KINDS for d in model.DEALS)
        rows.append(["layer", L, "", "", "", "", "", "", "", "", "", "", pk.avail[L], base, cin, t, cout, q])
    rows.append(["none", model.NONE_LAYER, "", "", "", "", pk.none_pairs, "", "", "", "", "", "", "", "", "", "", ""])
    write_csv(os.path.join(outdir, OUT["cands"]), CAND_HEADER, rows)

    lrows = []
    for L in model.LAYERS:
        for g in sorted(got[L], key=lambda g: (g["hash_split"], g["pair"][2])):
            h, key, c, d, L2, n, mk, nbad = g["pair"]
            cx = data.cx[c]
            addr, q = g["address"]
            f = model.listing(cx, d, addr, DETAIL)
            lrows.append([f[k] for k in LISTING_FIELDS] + [c, pk.bundle[c], L, d, g["split"], g["stage"], h, g["hash_split"], ";".join(mk), q])
    write_csv(os.path.join(outdir, OUT["listings"]), LISTING_FIELDS + LISTING_EXTRA, lrows)
    log(f"후보 쌍 {len(pk.pairs)}개, 매물 {len(lrows)}개를 썼다")


# ---------------------------------------------------------------- 맨 바깥

def build_parser():
    ap = argparse.ArgumentParser(prog="picker", description="예시 매물 뽑기(example_listing_rule_v1)")
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("test", help="가짜 자료 시험")
    sp = sub.add_parser("history", help="24쪽에서 이력 표를 다시 뽑는다")
    sp.add_argument("--out")
    for name in ("dist", "pins", "run"):
        sp = sub.add_parser(name)
        sp.add_argument("--zip", required=True, help="snapshot_T.zip")
        sp.add_argument("--codes", required=True, help="codes_snapshot.txt")
        sp.add_argument("--outdir", default="frozen")
        if name == "dist":
            sp.add_argument("--out")
        if name == "pins":
            sp.add_argument("--first-zip", dest="first_zip", required=True)
            sp.add_argument("--dist", required=True, help="합의한 값 분포 표")
            sp.add_argument("--out")
        if name == "run":
            sp.add_argument("--first-zip", dest="first_zip", required=True, help="snapshot_1st_run.zip")
            sp.add_argument("--mode", choices=("live", "replay"), required=True)
            sp.add_argument("--juso", help="얼린 도로명주소 파일(기본: outdir/juso_jibun_v1.csv)")
            sp.add_argument("--pins", default=PINS)
    return ap


def main(argv=None):
    scrub = Scrubber(secrets=[os.environ.get(juso.KEY_ENV, "")])
    args = build_parser().parse_args(argv)
    logpath = os.path.join(args.outdir, OUT["log"]) if args.cmd == "run" else None
    log = Log(logpath, scrub)
    if args.cmd == "run":
        log(f"== 시작 {now_iso()}")
    code = 0
    try:
        code = {"test": cmd_test, "history": cmd_history, "dist": cmd_dist, "pins": cmd_pins, "run": cmd_run}[args.cmd](args, log)
    except Stop as e:
        log(f"멈춤: {e}")
        code = 3
    except (juso.JusoError, juso.FrozenError) as e:
        log(f"멈춤(도로명주소): {e}")
        code = 4
    except KeyboardInterrupt:
        log("멈춤: 사용자가 멈췄다. 같은 명령으로 다시 돌리면 얼린 검색어는 다시 부르지 않는다")
        code = 130
    except Exception as e:  # 맨 바깥: 문구와 traceback을 고쳐서 화면에만 낸다
        print(scrub(traceback.format_exc()), file=sys.stderr)
        log(f"멈춤(오류): {type(e).__name__}: {e}")
        code = 2
    finally:
        if args.cmd == "run":
            log(f"== 끝 {now_iso()} (종료 코드 {code})")
    return code
