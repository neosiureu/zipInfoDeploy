# -*- coding: utf-8 -*-
"""sgiscoord.py 시험. 진짜 SGIS 대신 이 파일 안의 가짜 서버를 쓴다.

가짜 서버는 SGIS 새 안내서의 응답 꼴을 흉내 낸다(11/18 21:40 상대 지적).
- 좌표 칸은 소문자 x·y, 빈 값은 "null"이라는 글자, matching은 완전 매칭이 "0"이다.
- fmt "2023": 새 안내서의 지오코딩(WGS84) 예시 꼴. leg_cd·ri_cd가 10자리이고 sido_cd·sgg_cd는 SGIS 코드다.
  도로명으로 찾으면 지번 칸이 "null"이다.
- fmt "2014": 새 안내서의 지오코딩(UTM-K) 예시 꼴. sido_cd 2자리 + sgg_cd 3자리 + leg_cd 3자리로 나뉜다.
- data "new": 2026년 개편이 반영된 자료(옛 이름은 모른다). data "old": 개편 전 자료(새 이름은 모르고 코드도 옛것).
- 결과가 없으면 errCd -100이다(공개 프로젝트 기록). -401은 토큰, -1은 서버 처리 오류다.

사용: LISTINGS=<example_listings_v1.csv> JUSO=<juso_jibun_v1.csv> python -I test_sgiscoord.py
가짜 서버의 좌표는 지어낸 값이다. 실제 좌표가 아니다.
"""
import csv
import http.server
import importlib.util
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import unittest
import urllib.parse

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import sgiscoord as sc  # noqa: E402

LISTINGS = os.environ["LISTINGS"]
JUSO = os.environ["JUSO"]
KEY = "TEST-SGIS-KEY-0123456789"
SECRET = "TEST-SGIS-SECRET+/=한글"
TARGETS = sc.load_targets(LISTINGS, JUSO)
NEW_SIDO = "전남광주통합특별시"
SIDO_XY = {"서울특별시": (953000, 1952000), "경기도": (960000, 1920000),
           "인천광역시": (920000, 1940000), NEW_SIDO: (935000, 1685000)}
ROAD_KINDS = ("full", "nosido", "old")
SEOUL = [t for t in TARGETS if "old" not in t["forms"]]
SAN = [t for t in TARGETS if t["san"]]
JG = [t for t in TARGETS if t["forms"]["full"].startswith(NEW_SIDO)]
HI = [t for t in TARGETS if "old" in t["forms"] and not t["forms"]["full"].startswith(NEW_SIDO)]
N_CALLS = sum(len(t["forms"]) for t in TARGETS)


def rb(p):
    with open(p, "rb") as f:
        return f.read()


def wb(p, data):
    with open(p, "wb") as f:
        f.write(data)


def road_parts(address):
    """도로명주소 → (시도, 시군구 이름, 읍면 또는 None)."""
    w = address.split(" ")
    prefix = w[:-2]
    emd = prefix[-1] if len(prefix) >= 3 and prefix[-1].endswith(("읍", "면")) else None
    return prefix[0], " ".join(prefix[1:-1] if emd else prefix[1:]), emd


def base_xy(t):
    x0, y0 = SIDO_XY[t["forms"]["full"].split()[0]]
    return x0 + t["row"] * 137.123456789, y0 + t["row"] * 91.987654321


class Mock:
    def __init__(self, data="new", fmt="2023"):
        self.data, self.fmt = data, fmt
        self.by_addr = {a: (k, t) for t in TARGETS for k, a in t["forms"].items()}
        assert len(self.by_addr) == N_CALLS  # 꼴 글이 줄끼리 겹치지 않는다
        self.faults = {}  # (꼴, 줄) -> 이름. 끝이 '!'면 한 번만
        self.left = {}  # expire_twice가 남은 횟수
        self.requests = []
        self.tokens = []
        self.expired = set()
        srv = self

        class H(http.server.BaseHTTPRequestHandler):
            def log_message(self, *a):
                pass

            def do_GET(self):
                u = urllib.parse.urlparse(self.path)
                q = dict(urllib.parse.parse_qsl(u.query, keep_blank_values=True))
                srv.requests.append((u.path, q))
                status, body = srv.answer(u.path, q)
                data = body if isinstance(body, bytes) else json.dumps(body, ensure_ascii=False).encode("utf-8")
                self.send_response(status)
                self.send_header("Content-Type", "application/json;charset=UTF-8")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

        self.httpd = http.server.ThreadingHTTPServer(("127.0.0.1", 0), H)
        self.base = f"http://127.0.0.1:{self.httpd.server_address[1]}"
        threading.Thread(target=self.httpd.serve_forever, daemon=True).start()

    def close(self):
        self.httpd.shutdown()
        self.httpd.server_close()

    def fault(self, kind, row):
        f = self.faults.get((kind, row))
        if f and f.endswith("!"):
            del self.faults[(kind, row)]
            f = f[:-1]
        return f

    def knows(self, address):
        if self.data == "new":
            return self.by_addr[address][0] not in ("old", "old_jibun")
        return not (set(address.split(" ")) & sc.NEW_ONLY)

    def item(self, kind, t):
        reorganized = "old" in t["forms"]
        old = self.data == "old" and reorganized
        sido, sgg, emd = road_parts(t["forms"]["old" if old else "full"])
        code = t["admCd"]
        if old:
            code = f"{int(code[:5]) + 7:05d}" + code[5:]  # 개편 전 자료의 옛 코드(얼린 것과 다르다)
        ri_row = t["ri_nm"] is not None
        it = {"sido_nm": sido, "sgg_nm": sgg, "adm_nm": "가짜동", "leg_nm": t["leg_nm"],
              "ri_nm": t["ri_nm"] if ri_row else "null", "bd_main_nm": "null", "bd_sub_nm": "null",
              "addr_type": "6"}
        if self.fmt == "2023":
            it.update({"sido_cd": "31", "sgg_cd": "31010", "adm_cd": "31010550",
                       "leg_cd": code[:8] + "00" if ri_row else code, "ri_cd": code,
                       "bd_matches": "1", "origin_xy": "BUILD"})
        else:
            it.update({"sido_cd": code[:2], "sgg_cd": code[2:5], "adm_cd": "null", "leg_cd": code[5:8],
                       "ri_cd": code[8:] if ri_row else "null"})
        road = {"road_nm": t["road_name"], "road_cd": "1234567", "road_nm_main_no": str(t["bd_main"]),
                "road_nm_sub_no": str(t["bd_sub"])}
        jibun = {"jibun_main_no": str(t["lnbrMnnm"]),
                 "jibun_sub_no": str(t["lnbrSlno"]) if t["lnbrSlno"] else ("null" if self.fmt == "2023" else "0")}
        nulls = lambda d: {k: "null" for k in d}  # noqa: E731
        if kind in ROAD_KINDS:  # 2023: 도로명만(안내서 예시), 2014: 둘 다
            it.update(road, **(nulls(jibun) if self.fmt == "2023" else jibun))
        else:  # 2023: 둘 다, 2014: 지번만
            it.update(jibun, **(road if self.fmt == "2023" else nulls(road)))
        x, y = base_xy(t)
        if kind not in ROAD_KINDS:
            x, y = x + 12.5, y - 8.25
        it["x"], it["y"] = f"{x:.6f}", f"{y:.6f}"
        return it

    def answer(self, path, q):
        if path == sc.AUTH_PATH:
            if q.get("consumer_key") != KEY or q.get("consumer_secret") != SECRET:
                return 200, {"id": "API_0101", "errMsg": "인증 정보가 존재하지 않습니다", "errCd": -401, "trId": "x"}
            tok = f"TOKEN-{len(self.tokens) + 1:04d}-abcdef"
            self.tokens.append(tok)
            return 200, {"id": "API_0101", "result": {"accessTimeout": "1795000000000", "accessToken": tok},
                         "errMsg": "Success", "errCd": 0, "trId": "x"}
        if path != sc.GEOCODE_PATH:
            return 404, b"no"
        tok = q.get("accessToken")
        if tok not in self.tokens or tok in self.expired:
            return 200, {"id": "API_0702", "errMsg": "인증 정보가 존재하지 않습니다.", "errCd": -401, "trId": "x"}
        for k, v in sc.GEOCODE_FIXED.items():
            assert q.get(k) == v, k
        address = q.get("address")
        kind, t = self.by_addr[address]
        f = self.fault(kind, t["row"])
        none = {"id": "API_0702", "errMsg": "검색결과가 존재하지 않습니다", "errCd": -100, "trId": "x"}
        if f == "http500":
            return 500, b"err"
        if f == "http412":
            return 412, b"<html>412</html>"
        if f == "srverr":
            return 200, {"id": "API_0702", "errMsg": "서버에서 처리 중 에러가 발생하였습니다.", "errCd": -1, "trId": "x"}
        if f == "srverrleak":
            return 200, {"id": "API_0702", "errMsg": "처리 오류 " + tok, "errCd": -1, "trId": "x"}
        if f == "expire":
            self.expired.add(tok)
            return 200, {"id": "API_0702", "errMsg": "인증 정보가 존재하지 않습니다.", "errCd": -401, "trId": "x"}
        if f == "expire_twice":
            n = self.left.get((kind, t["row"]), 2)
            if n > 0:
                self.left[(kind, t["row"])] = n - 1
                self.expired.add(tok)
                return 200, {"id": "API_0702", "errMsg": "인증 정보가 존재하지 않습니다.", "errCd": -401, "trId": "x"}
        if f == "expire2":
            self.faults[(kind, t["row"])] = "expire2"
            self.expired.add(tok)
            return 200, {"id": "API_0702", "errMsg": "인증 정보가 존재하지 않습니다.", "errCd": -401, "trId": "x"}
        if f == "apierr":
            return 200, {"id": "API_0702", "errMsg": "파라미터 오류", "errCd": -200, "trId": "x"}
        if f == "leakfrozen":
            return 200, dict(none, errMsg="결과 없음 " + tok)
        if not self.knows(address):
            return 200, none
        it = self.item(kind, t)
        items = [it]
        matching = "0"
        if f == "two":  # 다른 땅·건물 하나가 앞에 섞여 온 꼴. 맞는 것을 골라야 한다
            other = dict(it, x="1.0", y="2.0")
            if other["road_nm"] != "null":
                other["road_nm_main_no"] = str(t["bd_main"] + 2)
            if other["jibun_main_no"] != "null":
                other["jibun_main_no"] = str(t["lnbrMnnm"] + 1)
            items = [other, it]
        if f == "two_same":  # 같은 땅이 둘
            items = [it, dict(it, x="1.0", y="2.0")]
        if f == "countmismatch":
            items = [it, dict(it, addr_type="3")]
        if f == "adjacent":  # 상대가 짚은 꼴: 부번만 다른 옆 땅 한 줄(r08의 85-18 대신 85-3)
            it.update({"jibun_main_no": str(t["lnbrMnnm"]), "jibun_sub_no": "3",
                       "road_nm": "null", "road_nm_main_no": "null", "road_nm_sub_no": "null"})
        if f in ("legdiff", "legdiff_sgg", "legdiff_noroad", "legdiff_code"):  # SGIS 기록의 법정동이 얼린 것과 다른 꼴(r21)
            old = t["bdMgtSn"][:10]  # 옛 기록의 코드는 건물관리번호 앞 10자리(처음 등록 때 법정동)다
            if f == "legdiff_code":  # 코드가 그것과도 다르다(다른 시도의 같은 이름 구 같은 꼴)
                old = "9999999999"
            if self.fmt == "2023":
                it.update({"leg_cd": old, "ri_cd": old})
            else:
                it.update({"sido_cd": old[:2], "sgg_cd": old[2:5], "leg_cd": old[5:8],
                           "ri_cd": old[8:] if old[8:] != "00" else "null"})
            it.update({"leg_nm": "다른동", "ri_nm": "null"})
        if f == "legdiff_sgg":  # 시군구 이름까지 다르다
            it["sgg_nm"] = "다른구"
        if f == "legdiff_noroad":  # 도로명 없이 지번만 온다
            it.update({"road_nm": "null", "road_nm_main_no": "null", "road_nm_sub_no": "null",
                       "jibun_main_no": str(t["lnbrMnnm"]), "jibun_sub_no": str(t["lnbrSlno"])})
        if f == "dong":
            it["addr_type"] = "3"
        if f == "roaddiff":
            it["road_nm"], it["road_nm_main_no"] = t["road_name"], str(t["bd_main"] + 2)
        if f == "noid":
            it.update({k: "null" for k in ("road_nm", "road_nm_main_no", "road_nm_sub_no",
                                           "jibun_main_no", "jibun_sub_no")})
        if f == "far":
            it["x"] = f"{float(it['x']) + 5000:.6f}"
        if f == "upperxy":
            it["X"], it["Y"] = it.pop("x"), it.pop("y")
        if f == "bothxy":
            it["X"], it["Y"] = it["x"], it["y"]
        if f == "xyconflict":
            it["X"], it["Y"] = it["x"], "1.0"
        if f == "match1":
            matching = "1"
        if f == "leak":
            it["bd_main_nm"] = tok
        res = {"totalcount": str(len(items)), "pagenum": "0", "returncount": str(len(items)),
               "matching": matching, "resultdata": items}
        if f == "countmismatch":
            res["totalcount"] = "3"
        body = {"id": "API_0702", "result": res, "errMsg": "Success", "errCd": 0, "trId": "x"}
        if f == "numbers":  # 좌표가 JSON 숫자로 온 꼴
            text = json.dumps(body, ensure_ascii=False)
            text = text.replace(f'"x": "{it["x"]}"', f'"x": {it["x"]}').replace(f'"y": "{it["y"]}"', f'"y": {it["y"]}')
            return 200, text.encode("utf-8")
        return 200, body


def run_cli(mode, out, base, keys=True, extra_env=None):
    env = {k: v for k, v in os.environ.items() if k not in (sc.KEY_ENV, sc.SECRET_ENV)}
    env[sc.TEST_BASE_ENV] = base
    if keys:
        env[sc.KEY_ENV] = KEY
        env[sc.SECRET_ENV] = SECRET
    env.update(extra_env or {})
    env["PYTHONIOENCODING"] = "utf-8"
    p = subprocess.run([sys.executable, "-I", os.path.join(HERE, "sgiscoord.py"), mode, LISTINGS, JUSO, out],
                       env=env, capture_output=True, text=True, encoding="utf-8")
    return p.returncode, p.stdout + p.stderr


def files(out):
    res = {}
    for dp, _, fs in os.walk(out):
        for n in fs:
            p = os.path.join(dp, n)
            res[os.path.relpath(p, out).replace("\\", "/")] = rb(p)
    return res


def report(out):
    return {r["row"]: r for r in csv.DictReader(io.StringIO(rb(os.path.join(out, sc.REPORT)).decode("utf-8")))}


def table(out):
    return list(csv.reader(io.StringIO(rb(os.path.join(out, sc.TABLE)).decode("utf-8"))))


class T(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.out = os.path.join(self.tmp, "frozen_sgiscoord")
        self.mock = Mock()

    def tearDown(self):
        self.mock.close()
        shutil.rmtree(self.tmp)

    def use(self, **kw):
        self.mock.close()
        self.mock = Mock(**kw)

    def geocode_calls(self):
        return sum(1 for p, _ in self.mock.requests if p == sc.GEOCODE_PATH)

    def set_all(self, row, fault):
        for k in TARGETS[row - 1]["forms"]:
            self.mock.faults[(k, row)] = fault

    def reference_table(self, **kw):
        ref = Mock(**kw)
        try:
            out = os.path.join(self.tmp, f"ref_{kw.get('data', 'new')}_{kw.get('fmt', '2023')}")
            code, text = run_cli("fetch", out, ref.base)
            self.assertEqual(code, 0, text)
            return rb(os.path.join(out, sc.TABLE))
        finally:
            ref.close()

    def test_forms_and_old_names(self):
        self.assertEqual(N_CALLS, 327)
        self.assertEqual((len(SEOUL), len(JG), len(HI)), (24, 16, 35))
        olds = {}
        for t in TARGETS:
            if "old" in t["forms"]:
                head = t["forms"]["old"].split(" ")[0]
                olds[head] = olds.get(head, 0) + 1
                self.assertTrue(t["forms"]["old_jibun"].startswith(" ".join(t["forms"]["old"].split(" ")[:-2])))
                self.assertFalse(set(t["forms"]["old"].split(" ")) & sc.NEW_ONLY)
            self.assertEqual(t["forms"]["nosido"], " ".join(t["forms"]["full"].split(" ")[1:]))
        self.assertEqual(olds, {"경기도": 27, "인천광역시": 8, "광주광역시": 13, "전라남도": 3})
        self.assertTrue(TARGETS[35]["forms"]["old"].startswith("인천광역시 중구 "))  # r36 도원동
        self.assertEqual(sum(1 for t in TARGETS if t["ri_nm"]), 12)

    def test_new_data_2023_normal_replay_no_leak(self):
        code, text = run_cli("fetch", self.out, self.mock.base)
        self.assertEqual(code, 0, text)
        self.assertIn("75줄 모두 통과(full 75, nosido 0, old 0, jibun 0, old_jibun 0; 법정동 코드 75, 이름 0, 다름 0; "
                      "errCd 0 아닌 꼴 102)", text)
        self.assertIn(f"새로 얼린 응답 327(errCd 0 아님 102), 요청 328번(인증 1번 포함)", text)
        self.assertEqual(self.geocode_calls(), 327)
        fs = files(self.out)
        self.assertEqual(sum(1 for k in fs if k.endswith(".json")), 327)
        for name, data in fs.items():
            for k in (KEY, SECRET, urllib.parse.quote(SECRET, safe=""), urllib.parse.quote_plus(SECRET), *self.mock.tokens):
                self.assertNotIn(k.encode("utf-8"), data, name)
        for k in (KEY, SECRET, *self.mock.tokens):
            self.assertNotIn(k, text)
        for t in TARGETS:
            for a in t["forms"].values():
                self.assertNotIn(a, text)
        rows = table(self.out)
        self.assertEqual(rows[0], sc.TABLE_HEADER)
        self.assertEqual(len(rows), 76)
        self.assertTrue(all(r[2] == "full" and r[3] == "코드" and r[4] == "도로명" for r in rows[1:]))
        rep = report(self.out)
        for t in SEOUL:
            self.assertEqual((rep[str(t["row"])]["old"], rep[str(t["row"])]["old_jibun"]), ("-", "-"))
        for t in HI:
            r = rep[str(t["row"])]
            self.assertEqual((r["full"], r["nosido"], r["old"], r["jibun"]), ("통과", "통과", "errCd(-100)", "통과"))
            self.assertEqual(r["spread_m"], "15")  # 도로명 꼴과 지번 꼴이 15m 떨어진 가짜 값
        n = len(self.mock.requests)
        code, rtext = run_cli("replay", self.out, self.mock.base, keys=False)
        self.assertEqual(code, 0, rtext)
        self.assertEqual(len(self.mock.requests), n)
        self.assertIn(sc.sha256_bytes(fs[sc.TABLE]), rtext)
        code, text2 = run_cli("fetch", self.out, self.mock.base)  # 다시 돌려도 부르지 않는다
        self.assertEqual(code, 0, text2)
        self.assertEqual(len(self.mock.requests), n)

    def test_old_data_2014_uses_names_and_old_forms(self):
        self.use(data="old", fmt="2014")
        code, text = run_cli("fetch", self.out, self.mock.base)
        self.assertEqual(code, 0, text)
        nz = 2 * len(JG) + 3 * len(HI)
        self.assertIn(f"75줄 모두 통과(full {len(SEOUL)}, nosido {len(JG)}, old {len(HI)}, jibun 0, old_jibun 0; "
                      f"법정동 코드 {len(SEOUL)}, 이름 {len(JG) + len(HI)}, 다름 0; errCd 0 아닌 꼴 {nz})", text)
        rows = {r[0]: r for r in table(self.out)[1:]}
        for t in HI:
            self.assertEqual(rows[str(t["row"])][2:5], ["old", "이름", "지번+도로명"])
        rep = report(self.out)
        for t in HI:
            r = rep[str(t["row"])]
            self.assertEqual((r["full"], r["nosido"], r["jibun"], r["old_jibun"]),
                             ("errCd(-100)", "errCd(-100)", "errCd(-100)", "통과"))

    def test_old_data_2023_too(self):
        self.use(data="old", fmt="2023")
        code, text = run_cli("fetch", self.out, self.mock.base)
        self.assertEqual(code, 0, text)
        self.assertIn(f"법정동 코드 {len(SEOUL)}, 이름 {len(JG) + len(HI)}", text)

    def test_adjacent_land_and_identity_failures(self):
        self.set_all(7, "legdiff_sgg")
        self.set_all(8, "adjacent")  # 상대 2-2 d: r08(85-18)에 85-3 한 줄
        self.set_all(9, "two_same")
        self.set_all(10, "dong")
        self.set_all(11, "roaddiff")
        self.set_all(12, "noid")
        code, text = run_cli("fetch", self.out, self.mock.base)
        self.assertEqual(code, 1)
        self.assertIn("판정을 통과하지 못한 줄 6개: r07, r08, r09, r10, r11, r12", text)
        fs = files(self.out)
        self.assertNotIn(sc.TABLE, fs)
        self.assertEqual(sum(1 for k in fs if k.endswith(".json")), 327)  # 응답은 모두 얼려 둔다
        rep = report(self.out)
        self.assertEqual(rep["7"]["full"], "맞는 줄(0)[법정동 1]")
        self.assertEqual(rep["8"]["full"], "맞는 줄(0)[지번 1]")
        self.assertEqual(rep["8"]["jibun"], "맞는 줄(0)[지번 1]")
        self.assertEqual(rep["9"]["full"], "맞는 줄(2)")
        self.assertEqual(rep["10"]["full"], "맞는 줄(0)[addr_type 1]")
        self.assertEqual(rep["11"]["full"], "맞는 줄(0)[도로명 1]")
        self.assertEqual(rep["12"]["full"], "맞는 줄(0)[근거없음 1]")
        for r in ("7", "8", "9", "10", "11", "12"):
            self.assertEqual((rep[r]["used"], rep[r]["why"]), ("없음", "통과한 꼴 없음"))
        self.assertEqual(rep["13"]["used"], "full")
        code, rtext = run_cli("replay", self.out, self.mock.base, keys=False)  # 재생도 같은 판정
        self.assertEqual(code, 1)
        self.assertIn("r07, r08, r09, r10, r11, r12", rtext)

    def test_stale_dong_passes_only_with_road(self):
        """SGIS의 그 건물 기록이 옛 법정동인 꼴(11/19 r21): 시군구 이름이 맞고, 코드가 건물관리번호 앞 10자리와 같고,
        도로명·건물번호가 맞으면 '다름'으로 통과한다."""
        t = TARGETS[20]
        self.assertNotEqual(t["bdMgtSn"][:10], t["admCd"])  # r21은 처음 등록 때 법정동과 지금 법정동이 다르다
        for fmt, by in (("2023", "도로명"), ("2014", "지번+도로명")):
            self.use(fmt=fmt)
            self.set_all(21, "legdiff")
            out = os.path.join(self.tmp, f"legdiff_{fmt}")
            code, text = run_cli("fetch", out, self.mock.base)
            self.assertEqual(code, 0, text)
            self.assertIn("법정동 코드 74, 이름 0, 다름 1", text)
            row = {r[0]: r for r in table(out)[1:]}["21"]
            self.assertEqual(row[2:5], ["full", "다름", by])
        self.use()
        self.set_all(21, "legdiff_noroad")  # 도로명이 없으면 법정동이 다를 때 통과시키지 않는다
        out = os.path.join(self.tmp, "noroad")
        code, text = run_cli("fetch", out, self.mock.base)
        self.assertEqual(code, 1)
        self.assertEqual(report(out)["21"]["full"], "맞는 줄(0)[법정동 1]")

    def test_stale_dong_needs_first_registration_code(self):
        """'다름'은 응답의 코드가 건물관리번호 앞 10자리와 같아야 한다(11/19 10:20 상대).
        시군구 이름만으로는 다른 시도의 같은 이름 구(북구·서구 등)를 가르지 못한다."""
        for fmt in ("2023", "2014"):
            self.use(fmt=fmt)
            self.set_all(21, "legdiff_code")
            out = os.path.join(self.tmp, f"legcode_{fmt}")
            code, text = run_cli("fetch", out, self.mock.base)
            self.assertEqual(code, 1, text)
            self.assertIn("판정을 통과하지 못한 줄 1개: r21", text)
            rep = report(out)["21"]
            self.assertEqual(rep["full"], "맞는 줄(0)[법정동 1]")
            self.assertEqual((rep["used"], rep["why"]), ("없음", "통과한 꼴 없음"))

    def test_rejudge_writes_elsewhere_and_calls_nothing(self):
        code, text = run_cli("fetch", self.out, self.mock.base)
        self.assertEqual(code, 0, text)
        before = files(self.out)
        n = len(self.mock.requests)
        out2 = os.path.join(self.tmp, "rejudged")
        env = {k: v for k, v in os.environ.items() if k not in (sc.KEY_ENV, sc.SECRET_ENV)}
        env.update({sc.TEST_BASE_ENV: self.mock.base, "PYTHONIOENCODING": "utf-8"})
        p = subprocess.run([sys.executable, "-I", os.path.join(HERE, "sgiscoord.py"), "rejudge", LISTINGS, JUSO, self.out, out2],
                           env=env, capture_output=True, text=True, encoding="utf-8")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        self.assertEqual(files(self.out), before)  # 얼린 폴더는 그대로
        self.assertEqual(len(self.mock.requests), n)  # 부르지 않는다
        self.assertEqual(rb(os.path.join(out2, sc.TABLE)), before[sc.TABLE])
        p = subprocess.run([sys.executable, "-I", os.path.join(HERE, "sgiscoord.py"), "rejudge", LISTINGS, JUSO, self.out, out2],
                           env=env, capture_output=True, text=True, encoding="utf-8")
        self.assertEqual(p.returncode, 1)
        self.assertIn("rejudge의 내놓을 폴더가 비어 있지 않다", p.stdout)

    def test_other_land_in_results_is_skipped(self):
        """'12'로 찾았더니 다른 땅·건물도 함께 온 꼴. 표는 고장 없을 때와 바이트까지 같아야 한다."""
        for fmt in ("2023", "2014"):
            ref = self.reference_table(fmt=fmt)
            self.use(fmt=fmt)
            for r in (22, 29, 32):
                self.set_all(r, "two")
            out = os.path.join(self.tmp, f"two_{fmt}")
            code, text = run_cli("fetch", out, self.mock.base)
            self.assertEqual(code, 0, text)
            self.assertEqual(rb(os.path.join(out, sc.TABLE)), ref)

    def test_san_row_needs_road_match(self):
        """산 지번 줄(r24)은 응답에 산 여부가 없어, 지번만 맞은 꼴로는 통과시키지 않는다(11/18 23시 상대)."""
        self.assertEqual([t["row"] for t in SAN], [24])
        r = SAN[0]["row"]
        for fmt, used, why in (("2014", "없음", "산 지번은 도로명까지 맞아야 한다"), ("2023", "jibun", "")):
            self.use(fmt=fmt)
            for k in SAN[0]["forms"]:
                if k in ROAD_KINDS:
                    self.mock.faults[(k, r)] = "roaddiff"  # 도로명 꼴은 모두 다른 건물
            out = os.path.join(self.tmp, f"san_{fmt}")
            code, text = run_cli("fetch", out, self.mock.base)
            rep = report(out)[str(r)]
            self.assertEqual((rep["full"], rep["jibun"]), ("맞는 줄(0)[도로명 1]", "통과"))  # 2014 지번 꼴은 지번만 온다
            self.assertEqual((rep["used"], rep["why"]), (used, why))
            self.assertEqual(code, 1 if why else 0, text)

    def test_not_all_received_fails(self):
        self.set_all(5, "countmismatch")
        code, text = run_cli("fetch", self.out, self.mock.base)
        self.assertEqual(code, 1)
        self.assertEqual(report(self.out)["5"]["full"], "건수(3/2)")

    def test_forms_far_apart_fail_the_row(self):
        self.mock.faults[("jibun", 14)] = "far"
        code, text = run_cli("fetch", self.out, self.mock.base)
        self.assertEqual(code, 1)
        r = report(self.out)["14"]
        self.assertEqual((r["full"], r["jibun"], r["used"]), ("통과", "통과", "없음"))
        self.assertTrue(r["why"].startswith("꼴 사이 ") and r["why"].endswith("m"), r["why"])
        self.assertGreater(int(r["spread_m"]), 1000)

    def test_xy_variants_and_matching(self):
        self.mock.faults[("full", 13)] = "upperxy"
        self.mock.faults[("full", 14)] = "bothxy"
        self.mock.faults[("full", 15)] = "numbers"
        self.mock.faults[("full", 16)] = "match1"
        self.mock.faults[("full", 17)] = "xyconflict"
        code, text = run_cli("fetch", self.out, self.mock.base)
        self.assertEqual(code, 0, text)
        rows = {r[0]: r for r in table(self.out)[1:]}
        for row in (13, 14, 15):
            it = self.mock.item("full", TARGETS[row - 1])
            self.assertEqual(rows[str(row)][2], "full")
            self.assertEqual(rows[str(row)][7:], [it["x"], it["y"]])  # 숫자로 와도 글자 그대로
        self.assertEqual(rows["16"][2:7], ["full", "코드", "도로명", "6", "1"])  # matching은 적기만 한다
        self.assertEqual(rows["17"][2], "nosido")
        self.assertEqual(report(self.out)["17"]["full"], "맞는 줄(0)[좌표 1]")

    def test_api_error_is_frozen_and_judged(self):
        self.mock.faults[("nosido", 30)] = "apierr"
        code, text = run_cli("fetch", self.out, self.mock.base)
        self.assertEqual(code, 0, text)
        self.assertIn(os.path.join("nosido", "r30.json").replace("\\", "/"), files(self.out))
        self.assertEqual(report(self.out)["30"]["nosido"], "errCd(-200)")

    def test_server_error_retried_then_stops_and_resumes(self):
        self.mock.faults[("full", 31)] = "srverr!"
        self.mock.faults[("nosido", 33)] = "srverr"
        code, text = run_cli("fetch", self.out, self.mock.base)
        self.assertEqual(code, 1)
        self.assertIn("r33 nosido: 서버 처리 오류 -1가 3번 났다", text)
        fs = files(self.out)
        self.assertIn("full/r31.json", fs)  # 한 번 난 -1은 다시 불러 얼렸다
        self.assertIn("full/r33.json", fs)
        self.assertNotIn("nosido/r33.json", fs)  # -1은 얼리지 않는다
        n = self.geocode_calls()
        del self.mock.faults[("nosido", 33)]
        code, text = run_cli("fetch", self.out, self.mock.base)
        self.assertEqual(code, 0, text)
        done_before = sum(len(t["forms"]) for t in TARGETS[:32]) + 1  # r01~r32 모두와 r33 full
        self.assertEqual(self.geocode_calls() - n, N_CALLS - done_before)

    def test_token_expiry_reauth_once_then_stop(self):
        self.mock.faults[("full", 20)] = "expire!"
        code, text = run_cli("fetch", self.out, self.mock.base)
        self.assertEqual(code, 0, text)
        self.assertEqual(len(self.mock.tokens), 2)
        self.assertEqual(self.geocode_calls(), 328)
        self.assertIn("요청 330번(인증 2번 포함)", text)
        self.use()
        self.mock.faults[("full", 22)] = "expire_twice"  # -401이 몰려서 두 번 나도 쉬었다가 다시 인증해 잇는다
        out = os.path.join(self.tmp, "expire_twice")
        code, text = run_cli("fetch", out, self.mock.base)
        self.assertEqual(code, 0, text)
        self.assertEqual(len(self.mock.tokens), 3)
        self.use()
        self.mock.faults[("jibun", 21)] = "expire2"
        out = os.path.join(self.tmp, "expire2")
        code, text = run_cli("fetch", out, self.mock.base)
        self.assertEqual(code, 1)
        self.assertIn("r21 jibun: 다시 인증해도 토큰 오류 -401가 3번 났다", text)
        self.assertNotIn("jibun/r21.json", files(out))

    def test_http_412_retried_500_stops(self):
        self.mock.faults[("full", 2)] = "http412!"
        self.mock.faults[("nosido", 3)] = "http500"
        code, text = run_cli("fetch", self.out, self.mock.base)
        self.assertEqual(code, 1)
        self.assertIn("r03 nosido: HTTP 500(시도 3번)", text)
        man = {(r[0], r[1]): r for r in csv.reader(io.StringIO(rb(os.path.join(self.out, sc.MANIFEST)).decode("utf-8")))}
        self.assertEqual(man[("r02", "full")][3], "2")  # 412 한 번 뒤 다시 불러 얼렸다

    def test_wrong_secret_and_missing_key(self):
        code, text = run_cli("fetch", self.out, self.mock.base, extra_env={sc.SECRET_ENV: "WRONG"})
        self.assertEqual(code, 1)
        self.assertIn("인증: API 오류 -401", text)
        self.assertNotIn("WRONG", text)
        self.assertEqual(self.geocode_calls(), 0)
        self.assertFalse(any(k.endswith(".json") for k in files(self.out)))
        code, text = run_cli("fetch", self.out, self.mock.base, keys=False)
        self.assertEqual(code, 1)
        self.assertIn("환경 변수 SGIS_KEY가 비어 있다", text)

    def test_token_in_frozen_file_is_caught(self):
        self.mock.faults[("full", 3)] = "leak"
        self.mock.faults[("old", HI[0]["row"])] = "leakfrozen"  # 결과 없음(-100) 글에 토큰이 되돌아온 꼴. 얼린 뒤에 잡는다
        code, text = run_cli("fetch", self.out, self.mock.base)
        self.assertEqual(code, 1)
        self.assertIn(": 키·Secret·토큰 글자가 들어 있다", text)
        self.assertNotIn("TOKEN-", text)

    def test_server_message_with_token_is_masked(self):
        self.mock.faults[("full", 1)] = "srverrleak"
        code, text = run_cli("fetch", self.out, self.mock.base)
        self.assertEqual(code, 1)
        for tok in self.mock.tokens:
            self.assertNotIn(tok, text)
            self.assertNotIn(tok, rb(os.path.join(self.out, sc.LOG)).decode("utf-8"))
        self.assertIn("<키>로 가렸다", text)

    def test_tamper_and_stray(self):
        code, _ = run_cli("fetch", self.out, self.mock.base)
        self.assertEqual(code, 0)
        p = os.path.join(self.out, "full", "r40.json")
        data = rb(p)
        wb(p, data.replace(b'"6"', b'"3"', 1))
        code, text = run_cli("replay", self.out, self.mock.base, keys=False)
        self.assertEqual(code, 1)
        self.assertIn("full/r40.json: 목록의 크기·해시와 다르다", text.replace("\\", "/"))
        wb(p, data)
        wb(os.path.join(self.out, "nosido", "r99.json"), b"{}")
        code, text = run_cli("replay", self.out, self.mock.base, keys=False)
        self.assertEqual(code, 1)
        self.assertIn("목록(calls_v1.csv)에 없는 파일이 있다", text)
        os.remove(os.path.join(self.out, "nosido", "r99.json"))
        code, text = run_cli("replay", self.out, self.mock.base, keys=False)
        self.assertEqual(code, 0, text)
        seoul = SEOUL[0]["row"]  # 옛 이름 꼴이 없는 줄에 old 응답이 적혀 있으면 멈춘다
        os.makedirs(os.path.join(self.out, "old"), exist_ok=True)
        wb(os.path.join(self.out, "old", f"r{seoul:02d}.json"), b"{}")
        with open(os.path.join(self.out, sc.MANIFEST), "a", encoding="utf-8", newline="") as f:
            f.write(f"r{seoul:02d},old,x,1,200,2,{sc.sha256_bytes(b'{}')}\n")
        code, text = run_cli("replay", self.out, self.mock.base, keys=False)
        self.assertEqual(code, 1)
        self.assertIn("이 매물에 부르지 않는 꼴이 적혀 있다", text)

    def test_pinned_inputs(self):
        bad = os.path.join(self.tmp, "juso_jibun_v1.csv")
        shutil.copy(JUSO, bad)
        with open(bad, "ab") as f:
            f.write(b"x")
        env = dict(os.environ, **{sc.KEY_ENV: KEY, sc.SECRET_ENV: SECRET, sc.TEST_BASE_ENV: self.mock.base,
                                  "PYTHONIOENCODING": "utf-8"})
        p = subprocess.run([sys.executable, "-I", os.path.join(HERE, "sgiscoord.py"), "fetch", LISTINGS, bad, self.out],
                           env=env, capture_output=True, text=True, encoding="utf-8")
        self.assertEqual(p.returncode, 1)
        self.assertIn("juso_jibun_v1.csv의 SHA-256이 얼린 값과 다르다", p.stdout)
        self.assertEqual(self.mock.requests, [])

    @unittest.skipUnless(importlib.util.find_spec("pyproj"), "pyproj가 없다(바꾸기는 내 쪽에서만 돈다)")
    def test_converter_reads_sgis_table(self):
        code, _ = run_cli("fetch", self.out, self.mock.base)
        self.assertEqual(code, 0)
        import utmk_to_wgs84 as uw
        data, rep = uw.convert(rb(os.path.join(self.out, sc.TABLE)))
        rows = list(csv.reader(io.StringIO(data.decode("utf-8"))))
        self.assertEqual(rows[0], uw.OUT_HEADER)
        self.assertEqual(len(rows), 76)
        self.assertLess(max(rep["max_dlat"], rep["max_dlng"]), 1e-10)


if __name__ == "__main__":
    unittest.main(verbosity=2)
