# -*- coding: utf-8 -*-
"""순서 2·3·7·8 명령: build, check, freeze-juso, answers, questions, score, mistakes, test.

experiment 폴더에서 `python -X utf8 -B -m prep <명령>`으로 돌린다.
화면에는 셈한 수, 코드, 해시만 낸다. 거래 줄, 매물 이름·주소, 단지ID, 키는 내지 않는다.
"""
import argparse
import csv
import hashlib
import json
import os
import sys
import unittest
from collections import Counter, defaultdict
from fractions import Fraction

from picker import codego, model, snapshot
from picker import juso as picker_juso
from picker.clean import API_OF
from picker.cli import DETAIL, HISTORY, LISTING_FIELDS, pick_all
from picker.common import code_sorted, sha256_file

from . import answers, juso_road, listings, questions, replay, score, validity

# 들어가는 파일의 SHA-256(설계안 5.1: 547be6d의 값과 대조, 매물 파일은 3단계에서 얼린 것)
PINS = {
    "snap": "0EB3F0BE2A7AA137340AA1554D2D86EE8C2887C8EB6FA92CA76FB4ACC8D30AC1",
    "codes": "0BC23755F11C3E02F1E7FCFA8B201F7267C80FDB5819117B68582A6EDB93E496",
    "listings": "FFB838C797E7EE4CEAFD15096701565E501B5392F5A290690FF2F3C4B8C1EBF4",
}
JUSO_JIBUN = "frozen/juso_jibun_v1.csv"
MANIFEST_T = "frozen/manifest_T.csv"
LISTINGS = "frozen/example_listings_v1.csv"
JUSO_ROAD = "frozen/juso_road_v1.csv"
OUT_DIR = "prep/out"
EXPECTED = "prep/expected_v1.txt"
OUT_FILES = {
    "trades": "replay_trades_v1.csv",
    "bundles": "replay_bundles_v1.csv",
    "validity": "validity_v1.csv",
    "input": "listings_input_v1.json",
    "queries": "juso_queries_v1.csv",
}
MANIFEST = "prep_manifest_v1.txt"
ANSWERS = "answers_check_v1.json"
TEMPLATES = "questions/templates_v0.json"
RECENT = ("202511", "202610")
N_BASE = 5
T1_WANT = (35, 38, 2)


class Stop(RuntimeError):
    pass


def check_pins(args, names):
    for name in names:
        path = getattr(args, name)
        got = sha256_file(path)
        if got != PINS[name]:
            raise Stop(f"{name} 파일의 SHA-256이 다르다: {got[:8]}… (와야 할 것 {PINS[name][:8]}…)")
        print(f"들어감 {name}: {got[:8]}… 맞음")


def read_expected(path=EXPECTED):
    """{파일 이름: (SHA-256, 바이트)}. '#'로 시작하는 줄과 빈 줄은 건너뛴다(줄끝은 LF·CRLF 모두 된다)."""
    out = {}
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            h, name, size = line.split()
            out[name] = (h, int(size))
    if sorted(out) != sorted(OUT_FILES.values()):
        raise Stop(f"{os.path.basename(path)}에 든 파일이 다르다")
    return out


def _inputs(args):
    codes = code_sorted(snapshot.read_codes(args.codes))  # picker cli와 같은 차례
    months = snapshot.month_list()
    hist = codego.History(codego.load_history_rows(HISTORY))
    return codes, months, hist


def cmd_build(args):
    check_pins(args, ("snap", "codes", "listings"))
    codes, months, hist = _inputs(args)
    J = picker_juso.Juso(JUSO_JIBUN, "replay")
    with snapshot.Snapshot(args.snap) as snap:
        trades, bundles = replay.build(snap, hist, codes, months, J)
    out = {k: os.path.join(args.out, v) for k, v in OUT_FILES.items()}
    replay.write(trades, bundles, out["trades"], out["bundles"])
    L = listings.load(args.listings)
    areas = sorted({x["complex_id"].split("|")[1] for _, x in L})
    validity.write(validity.build(trades, hist, areas, months), out["validity"])
    listings.write_model_input(listings.model_input(L), out["input"])
    listings.write_queries(listings.queries(L), out["queries"])
    lines = []
    for k in OUT_FILES:
        p = out[k]
        lines.append(f"{sha256_file(p)}  {OUT_FILES[k]}  {os.path.getsize(p)}")
    with open(os.path.join(args.out, MANIFEST), "w", encoding="utf-8", newline="\n") as f:
        f.write("# 순서 2 결과: SHA-256, 파일, 바이트\n" + "\n".join(lines) + "\n")
    print(f"거래 {len(trades)}줄, 묶음 {len(bundles)}줄, 영역 {len(areas)}, 매물 {len(L)}")
    for line in lines:
        print(line)
    return 0


def _read_rows(path, header):
    with open(path, "r", encoding="utf-8", newline="") as f:
        rd = csv.reader(f)
        if next(rd) != header:
            raise Stop(f"{os.path.basename(path)} 머리줄이 다르다")
        for r in rd:
            yield r


def _key(r):
    """picker Data와 같은 중복 열쇠: (api, 단지ID, 거래, 연, 월, 일, 층, 면적, 금액)"""
    ym = int(r[2])
    floor = int(r[13]) if r[13] != "" else None
    amt = (int(r[15]),) if r[15] != "" else (int(r[16]), int(r[17]))
    return (r[0], r[7], r[6], ym // 100, ym % 100, int(r[5]), floor, Fraction(r[12]), amt)


def _recent(k):
    return RECENT[0] <= "%04d%02d" % (k[0], k[1]) <= RECENT[1]


def one_to_many(hist, codes):
    """옛 코드 가운데 법정동이 지금 둘 이상의 시군구로 갈라진 것: {옛 코드: [현재 시군구, ...]}"""
    out = {}
    for c in codes:
        owners = sorted({cur[:5] for d in hist.dong_codes(c) for cur in hist.current_of(d)})
        if len(owners) >= 2:
            out[c] = owners
    return out


def cmd_check(args):
    """build 결과를 스냅샷의 manifest, picker, 얼린 매물 파일과 견준다. 하나라도 어긋나면 1을 낸다."""
    check_pins(args, ("snap", "codes", "listings"))
    codes, months, hist = _inputs(args)
    out = {k: os.path.join(args.out, v) for k, v in OUT_FILES.items()}
    for k, p in out.items():
        if not os.path.exists(p):
            raise Stop(f"{OUT_FILES[k]}가 없다. 먼저 build를 돌린다")
    ok = True

    def say(cond, text):
        nonlocal ok
        ok = ok and cond
        print(("맞음 " if cond else "어긋남 ") + text)

    # 0. 결과 바이트가 기대 해시(expected_v1.txt)와 같은가. 다른 OS·파이썬 판에서 만들어도 같아야 한다(설계안 5.1)
    want = read_expected()
    for k in OUT_FILES:
        h, size = want[OUT_FILES[k]]
        got = sha256_file(out[k])
        say(got == h and os.path.getsize(out[k]) == size, f"{OUT_FILES[k]} SHA-256 {got[:8]}… (기대 {h[:8]}…)")
    # 1. 묶음: 스냅샷 manifest와 묶음마다 같은가
    rows = list(_read_rows(out["bundles"], replay.BUNDLE_HEADER))
    tot = defaultdict(set)
    got_items = Counter()
    with open(MANIFEST_T, "r", encoding="utf-8", newline="") as f:
        for m in csv.DictReader(f):
            k = (m["api"], m["lawd_cd"], m["deal_ymd"])
            tot[k].add(int(m["total_count"]))
            got_items[k] += int(m["items"])
    keys_b = [(b[0], b[1], b[2]) for b in rows]
    say(len(rows) == len(snapshot.APIS) * len(codes) * len(months) and set(keys_b) == set(tot) and len(set(keys_b)) == len(keys_b),
        f"묶음 {len(rows)}줄, manifest 묶음 {len(tot)}개와 하나씩 짝이 맞음")
    bad_m = sum(1 for b in rows if tot.get((b[0], b[1], b[2])) != {int(b[3])} or got_items[(b[0], b[1], b[2])] != int(b[3]))
    say(bad_m == 0, f"묶음마다 받은 건수가 manifest의 totalCount·items와 다른 묶음 {bad_m}개")
    bad_bal = sum(1 for b in rows if int(b[3]) != int(b[4]) + int(b[5]) + int(b[6]))
    t = [sum(int(b[i]) for b in rows) for i in range(3, 7)]
    say(bad_bal == 0, f"받은 = 남긴 + 뺀 + 못 정함이 어긋난 묶음 {bad_bal}개 (모두: 받은 {t[0]}, 남긴 {t[1]}, 뺀 {t[2]}, 못 정함 {t[3]})")
    # 2. 저장소를 한 번 읽어 picker 꼴로 모은다
    L = listings.load(args.listings)
    want_cx = {x["complex_id"] for _, x in L}
    keys = defaultdict(Counter)
    attrs = {}
    store_data = model.Data(hist)  # 매물 단지만 picker 코드로 다시 모은다
    n = 0
    for r in _read_rows(out["trades"], replay.TRADE_HEADER):
        n += 1
        keys[_key(r)][r[1]] += 1
        if r[7] not in attrs:
            kind, sgg = r[7].split("|")[:2]
            attrs[r[7]] = (kind, r[8], sgg)
        if r[7] in want_cx:
            store_data.add(replay.rec_of(r))
    store_data.finish()
    say(n == t[1], f"거래 파일 {n}줄 = 남긴 거래")
    rebuilt = defaultdict(Counter)
    for (api, c, deal, y, m, d, floor, area, amt), per in keys.items():
        rebuilt[(c, deal)][(y, m, d, floor, area, amt)] += max(per.values())
    # 3. picker와 단지·거래마다 같은가
    J = picker_juso.Juso(JUSO_JIBUN, "replay")
    with snapshot.Snapshot(args.snap) as snap:
        data = pick_all(snap, hist, codes, months, J, lambda *_: None)["data"]
    diff = sum(1 for c, cx in data.cx.items() for deal, cnt in cx.deals.items() if +cnt != +rebuilt.get((c, deal), Counter()))
    extra = sum(1 for k in rebuilt if k[0] not in data.cx)
    pairs = sum(1 for cx in data.cx.values() for cnt in cx.deals.values() if cnt)
    say(diff == 0 and extra == 0,
        f"picker와 단지 {len(data.cx)}개의 매매·전세·월세 거래가 모두 같음(거래가 있는 쌍 {pairs}개, 다름 {diff}, 저장소에만 {extra})")
    # 4. 저장소만으로 매물을 06 규칙(picker model.listing)대로 다시 만들어 얼린 매물 파일과 견준다
    fields = [h for h in LISTING_FIELDS if h != "stockAddress"]
    bad_l = 0
    for _, x in L:
        cx = store_data.cx.get(x["complex_id"])
        if cx is None:  # 단지ID를 화면에 내지 않으려고 KeyError 대신 센다
            bad_l += len(fields)
            continue
        zip_no, road = listings.address_parts(x["stockAddress"])
        f = model.listing(cx, x["deal"], {"zipNo": zip_no, "roadAddrPart1": road}, DETAIL)
        bad_l += sum(1 for h in fields if str(f[h]) != x[h])
    say(bad_l == 0, f"저장소만으로 다시 만든 매물 {len(L)}개 × {len(fields)}칸(주소 칸 빼고) 가운데 다른 칸 {bad_l}개")
    # 5. T1 범위(12달, N 5): 저장소만으로
    by_dong, by_sgg = defaultdict(list), defaultdict(list)
    for c, (kind, cur10, sgg) in attrs.items():
        by_dong[(kind, cur10)].append(c)
        by_sgg[(kind, sgg)].append(c)

    def count(cs, deal):
        return sum(k_n for c in cs for k, k_n in rebuilt.get((c, deal), Counter()).items() if _recent(k))

    dist = Counter()
    for _, x in L:
        if x["complex_id"] not in attrs:
            dist["없음"] += 1
            continue
        kind, cur10, sgg = attrs[x["complex_id"]]
        for name, cs in (("단지", [x["complex_id"]]), ("법정동", by_dong[(kind, cur10)]), ("시군구", by_sgg[(kind, sgg)])):
            if count(cs, x["deal"]) >= N_BASE or name == "시군구":
                dist[name] += 1
                break
    got_t1 = (dist["단지"], dist["법정동"], dist["시군구"])
    say(got_t1 == T1_WANT, f"T1 범위(12달, N {N_BASE}) 단지 {got_t1[0]}·법정동 {got_t1[1]}·시군구 {got_t1[2]}")
    # 6. 유효성 표
    vrows = list(_read_rows(out["validity"], validity.HEADER))
    areas = sorted({x["complex_id"].split("|")[1] for _, x in L})
    by = defaultdict(list)
    for r in vrows:
        by[(r[0], r[1], r[2])].append(r)
    shape = all([r[3] for r in by[(a, api, ym)]] == hist.candidates(a)
                for a in areas for api in code_sorted(snapshot.APIS) for ym in months)
    say(shape and len(by) == len(areas) * len(snapshot.APIS) * len(months),
        f"유효성 표 {len(vrows)}줄: 영역 {len(areas)} × API {len(snapshot.APIS)} × {len(months)}달마다 후보 코드가 차례대로 하나씩")
    cases = Counter(r[7] for r in vrows if r[3] == r[0])  # 칸(영역, API, 달)마다 한 번
    print("  칸 갈래: " + ", ".join(f"{k} {cases[k]}" for k in ("single", "one", "true_zero")))
    bad = 0
    for _, x in L:
        kind, area = x["complex_id"].split("|")[:2]
        api = API_OF[(kind, x["deal"])]
        for ym in months:
            for r in by[(area, api, ym)]:
                if r[7] == "true_zero" or (r[3] != area and r[5] == "1") or (r[3] == area and r[5] == "0"):
                    bad += 1
    say(bad == 0, f"매물의 API에서 정말 0건·옛 코드 유효·현재 코드 무효인 칸 {bad}개")
    presc = sum(1 for r in vrows if r[5] == "0" and r[6] != r[0])
    say(presc == 0, f"무효 칸 가운데 처방이 영역 코드(매물의 현재 시군구)가 아닌 것 {presc}개")
    split = one_to_many(hist, sorted({c for a in areas for c in hist.candidates(a) if c != a}))
    for c, owners in sorted(split.items()):
        seen = sorted({(r[0], r[6]) for r in vrows if r[3] == c and r[5] == "0"})
        say(all(a == p for a, p in seen) and len(seen) >= 1,
            f"1:N 옛 코드 {c}(지금 {'·'.join(owners)}): 무효 칸의 처방 " + ", ".join(f"영역 {a} → {p}" for a, p in seen))
    # 7. 매물 입력과 검색어
    with open(out["input"], "r", encoding="utf-8") as f:
        text = f.read()
    items = json.loads(text)
    keys_ok = [it["key"] for it in items] == ["L%02d" % i for i in range(1, len(L) + 1)]
    fields_ok = all(list(it["fields"]) == listings.NINE and list(it) == ["key", "fields", "evidence"] for it in items)
    ev_ok = all(it["evidence"] == {name: int(x[col]) for name, col in listings.EVIDENCE} and it["fields"] == {h: x[h] for h in listings.NINE}
                for it, (_, x) in zip(items, L))
    say(keys_ok and fields_ok and ev_ok and len(items) == len(L),
        f"매물 입력 {len(items)}개: 키 L01~L{len(L):02d}, 칸은 아홉 칸뿐(layer·split·complex_id 없음), 칸 값과 근거 ID 넷이 매물 파일과 같음")
    q = list(_read_rows(out["queries"], listings.QUERY_HEADER))
    say(len(q) == len(L) and len({r[1] for r in q}) == len(q), f"검색어 {len(q)}개, 겹치는 것 {len(q) - len({r[1] for r in q})}개")
    print("모두 맞음" if ok else "어긋난 것이 있다")
    return 0 if ok else 1


def cmd_freeze_juso(args):
    """사용자 PC: 매물 주소 칸의 검색어마다 도로명주소 API를 한 번씩 불러 얼린다. 키는 환경 변수에서만 읽는다."""
    key = os.environ.get(picker_juso.KEY_ENV, "")
    if not key:
        raise Stop(f"환경 변수 {picker_juso.KEY_ENV}가 없다")
    check_pins(args, ("listings",))
    rows = listings.queries(listings.load(args.listings))
    h = hashlib.sha256(listings.queries_bytes(rows)).hexdigest().upper()
    want = read_expected()[OUT_FILES["queries"]][0]
    if h != want:
        raise Stop(f"검색어 목록의 SHA-256이 다르다: {h[:8]}… (와야 할 것 {want[:8]}…)")
    print(f"검색어 {len(rows)}개: {h[:8]}… 맞음")
    J = juso_road.RoadJuso(args.out, "live", key=key)
    before = len(J.frozen)
    for i, (_, q) in enumerate(rows, 1):
        J.lookup(q)
        if i % 10 == 0 or i == len(rows):
            print(f"{i}/{len(rows)}")
    frozen = juso_road.load(args.out)
    n_res = Counter(min(len(frozen[q]), 2) for _, q in rows)
    print(f"이번에 부른 검색어 {J.queries_new}개(전에 얼린 것 {before}개), 부른 쪽 {J.calls}개")
    print(f"얼린 검색어 {len(frozen)}개, 결과 0건 {n_res[0]}개, 1건 {n_res[1]}개, 2건 이상 {n_res[2]}개")
    if set(frozen) != {q for _, q in rows}:
        raise Stop("얼린 파일의 검색어가 목록과 다르다")
    print(f"{sha256_file(args.out)}  {os.path.basename(args.out)}  {os.path.getsize(args.out)}")
    return 0


def cmd_answers(args):
    """순서 3 대조용(설계안 9.3 "같은 식을 두 곳에"): 문항마다 맞는 부르기와 정답 값을 answers_check_v1.json에 쓴다."""
    check_pins(args, ("listings",))
    trades = os.path.join(args.out, OUT_FILES["trades"])
    want = read_expected()[OUT_FILES["trades"]][0]
    if not os.path.exists(trades) or sha256_file(trades) != want:
        raise Stop(f"{OUT_FILES['trades']}가 없거나 기대 해시와 다르다. 먼저 build를 돌린다")
    L = listings.load(args.listings)
    cases = answers.build(answers.Store(trades), L, answers.load_metas(args.listings))
    path = os.path.join(args.out, ANSWERS)
    answers.write(cases, path)
    by = Counter(c["qtype"] for c in cases)
    print("문항(T3는 창 셋을 따로 셈): " + ", ".join(f"{k} {by[k]}" for k in sorted(by)) + f", 모두 {len(cases)}")
    print("범위: " + ", ".join(f"{k} {v}" for k, v in sorted(Counter(c["scope"] for c in cases).items())))
    print(f"{sha256_file(path)}  {ANSWERS}  {os.path.getsize(path)}")
    return 0


def cmd_questions(args):
    """순서 7a(순서 7 설계 c판): 틀 문장 파일로 문항 파일(questions/questions_<판>.json)과 문항별 정답(prep/out/answers_<판>.json)을 쓴다.

    틀 문장 검사에 걸리거나 들어가는 파일이 기대와 다르면 아무것도 쓰지 않고 멈춘다. 이름·주소·단지ID·질문 글은 찍지 않는다.
    """
    check_pins(args, ("listings",))
    want = read_expected()
    for k in ("trades", "input"):
        path = os.path.join(args.out, OUT_FILES[k])
        if not os.path.exists(path) or sha256_file(path) != want[OUT_FILES[k]][0]:
            raise Stop(f"{OUT_FILES[k]}가 없거나 기대 해시와 다르다. 먼저 build를 돌린다")
    check_path = os.path.join(args.out, ANSWERS)
    if not os.path.exists(check_path):
        raise Stop(f"{ANSWERS}가 없다. 먼저 answers를 돌린다")
    if not os.path.exists(args.templates):
        raise Stop(f"틀 문장 파일({args.templates})이 없다")
    version, templates = questions.load_templates(args.templates)
    L = listings.load(args.listings)
    cases = answers.build(answers.Store(os.path.join(args.out, OUT_FILES["trades"])), L, answers.load_metas(args.listings))
    with open(check_path, "r", encoding="utf-8") as f:
        check_cases = json.load(f)
    try:
        rows = questions.build(L, questions.names(os.path.join(args.out, OUT_FILES["input"]), [k for k, _ in L]), templates)
        ans = questions.answers_for(rows, cases, check_cases)
    except ValueError as e:
        raise Stop(str(e)) from e
    qpath = os.path.join(os.path.dirname(args.templates), f"questions_{version}.json")
    apath = os.path.join(args.out, f"answers_{version}.json")
    questions.write_json(questions.questions_doc(rows, version), qpath)
    questions.write_json({"version": version, "questions_sha256": questions.sha256_lf(qpath), "answers": ans}, apath)
    for line in questions.summary(rows, ans, templates):
        print(line)
    for path in (qpath, apath):
        print(f"{sha256_file(path)}  {os.path.basename(path)}  {os.path.getsize(path)}")
    return 0


def cmd_score(args):
    """순서 8a·8b(순서 8 설계 d판, 8b 설계 2판): 한 단계의 기록을 묶기 검사하고 실행마다 값·규칙·판정을 매긴 뒤 15.9 지표·진단·비용을 낸다.
    끝 코드 0 다 매김, 3 묶기 검사, 2 설정·파일."""
    return score.run(args.settings, listings=args.listings, pin=PINS["listings"])


def cmd_mistakes(args):
    """순서 8b(설계 2판 4.3): 연습 단계의 실패 코드로 같은 실수 표를 얼린다. 이미 있으면 쓰지 않는다(끝 코드 2)."""
    return score.mistakes(args.settings, args.out, listings=args.listings, pin=PINS["listings"])


def cmd_test(args):
    here = os.path.dirname(os.path.abspath(__file__))
    suite = unittest.defaultTestLoader.discover(os.path.join(here, "tests"), top_level_dir=os.path.dirname(here))
    res = unittest.TextTestRunner(verbosity=1).run(suite)
    return 0 if res.wasSuccessful() else 1


def build_parser():
    p = argparse.ArgumentParser(prog="prep")
    sub = p.add_subparsers(dest="cmd", required=True)
    for name in ("build", "check"):
        s = sub.add_parser(name)
        s.add_argument("--snap", required=True, help="snapshot_T.zip")
        s.add_argument("--codes", required=True, help="codes_snapshot.txt")
        s.add_argument("--listings", default=LISTINGS)
        s.add_argument("--out", default=OUT_DIR)
    s = sub.add_parser("freeze-juso")
    s.add_argument("--listings", default=LISTINGS)
    s.add_argument("--out", default=JUSO_ROAD)
    s = sub.add_parser("answers")
    s.add_argument("--listings", default=LISTINGS)
    s.add_argument("--out", default=OUT_DIR)
    s = sub.add_parser("questions")
    s.add_argument("--listings", default=LISTINGS)
    s.add_argument("--out", default=OUT_DIR)
    s.add_argument("--templates", default=TEMPLATES)
    s = sub.add_parser("score")
    s.add_argument("settings", help="하네스 설정 파일(experiment 폴더에서 본 경로, 예: harness/settings/pilot_v0.json)")
    s.add_argument("--listings", default=LISTINGS)
    s = sub.add_parser("mistakes")
    s.add_argument("settings", help="얼릴 단계의 하네스 설정 파일(예: harness/settings/pilot_v0.json)")
    s.add_argument("out", help="얼릴 파일(experiment 폴더에서 본 경로, 예: harness/frozen/mistakes_v0.json)")
    s.add_argument("--listings", default=LISTINGS)
    sub.add_parser("test")
    return p


def main(argv=None):
    args = build_parser().parse_args(argv)
    try:
        return {"build": cmd_build, "check": cmd_check, "freeze-juso": cmd_freeze_juso, "answers": cmd_answers,
                "questions": cmd_questions, "score": cmd_score, "mistakes": cmd_mistakes, "test": cmd_test}[args.cmd](args)
    except questions.BadTemplates as e:
        print("멈춤: 틀 문장 파일이 검사에 걸렸다. 아무것도 쓰지 않았다", file=sys.stderr)
        for p in e.problems:
            print("  - " + p, file=sys.stderr)
        return 2
    except (Stop, picker_juso.JusoError, picker_juso.FrozenError, listings.BadListing, validity.MultiSource,
            replay.ReplayError) as e:
        print(f"멈춤: {e}", file=sys.stderr)
        return 2
