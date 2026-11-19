# -*- coding: utf-8 -*-
"""make_insert_sql.py 시험. 입력은 얼린 example_listings_v1.csv(LISTINGS 환경 변수)와 리허설 DDL(칸 길이 확인용)이다.

사용: LISTINGS=<example_listings_v1.csv> python -X utf8 -B -I test_make_insert_sql.py
줄 내용은 화면에 찍지 않는다.
"""
import csv
import difflib
import importlib.util
import os
import re
import shutil
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import make_insert_sql as m  # noqa: E402

LISTINGS = os.environ["LISTINGS"]
DDL = os.path.join(HERE, "..", "rehearsal", "ddl_input", "zipinfoTableDDL.txt")
CALL = re.compile(r"^  add_one\((.*)\);$", re.M)
WANT = re.compile(r"^  SELECT (.*) FROM DUAL(?: UNION ALL)?$", re.M)
TOKEN = re.compile(r"UNISTR\('((?:[^']|'')*)'\)|DATE '([0-9-]{10})'|(-?[0-9]+(?:\.[0-9]+)?)")


def decode(s):
    return re.sub(r"\\([0-9A-F]{4})", lambda mm: chr(int(mm.group(1), 16)), s).replace("''", "'")


def parse_args(text):
    out, pos = [], 0
    while pos < len(text):
        mm = TOKEN.match(text, pos)
        if not mm:
            raise AssertionError(f"인자를 읽지 못했다(자리 {pos})")
        out.append(decode(mm.group(1)) if mm.group(1) is not None else (mm.group(2) or mm.group(3)))
        pos = mm.end()
        if text.startswith(", ", pos):
            pos += 2
    return out


def expected_args(i, r, coords):
    vals = [str(i)] + [r[c] for c, _ in m.META]
    vals += [r[c] if k == "S" else (str(int(r[c])) if k == "N" else r[c]) for c, _, k in m.COLUMNS]
    return vals + list(coords[i])


def ddl_lengths():
    spec = importlib.util.spec_from_file_location("mrs", os.path.join(HERE, "..", "rehearsal", "make_rehearsal_schema.py"))
    mrs = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mrs)
    tables, _ = mrs.build_tables(mrs.read_pinned(DDL))
    lens = {}
    for name, sql in tables:
        for col, n in re.findall(r'"([A-Z_]+)" N?VARCHAR2\(([0-9]+)\)', sql):
            lens[(name, col)] = int(n)
    return lens


class T(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(LISTINGS, encoding="utf-8", newline="") as f:
            cls.rows = list(csv.DictReader(f))
        cls.out, _, cls.targets = m.build(LISTINGS, "FAKE")
        cls.coords = m.fake_coords()

    def real_table(self, tmp, coords=None):
        p = os.path.join(tmp, "coord_wgs84_v1.csv")
        with open(p, "w", encoding="utf-8", newline="") as f:
            w = csv.writer(f, lineterminator="\n")
            w.writerow(m.COORD_HEADER)
            for i in range(1, 76):
                lat, lng = (coords or {}).get(i, (f"{37.0 + i * 0.001:.7f}", f"{127.0 + i * 0.001:.7f}"))
                w.writerow([i, f"{i:025d}", lat, lng])
        return p

    def test_files_and_fake_only_rehearsal(self):
        self.assertEqual(self.targets, ["리허설"])
        self.assertEqual(sorted(self.out), sorted([
            "0_EXAMPLE_STOCK_만들기_리허설.sql", "1_줄수.sql", "2_넣기_리허설.sql", "3_대조.sql", "4_되돌리기_리허설.sql",
            "5_남은줄.sql", "6_EXAMPLE_STOCK_지우기_리허설.sql", "R_가짜회원_리허설.sql", "fake_coord_v1.csv"]))

    def test_insert_calls_equal_frozen_file(self):
        calls = CALL.findall(self.out["2_넣기_리허설.sql"])
        self.assertEqual(len(calls), 75)
        for i, (call, r) in enumerate(zip(calls, self.rows), 1):
            self.assertEqual(parse_args(call), expected_args(i, r, self.coords), f"r{i:02d}")

    def test_verify_want_equals_insert(self):
        want = WANT.findall(self.out["3_대조.sql"])
        calls = CALL.findall(self.out["2_넣기_리허설.sql"])
        self.assertEqual(len(want), 75)
        self.assertEqual([parse_args(w) for w in want], [parse_args(c) for c in calls])

    def test_values_fit_production_columns(self):
        lens = ddl_lengths()

        def fits(table, col, value):
            self.assertLessEqual(len(value.encode("utf-16-le")) // 2, lens[(table, col)], f"{table}.{col}")

        for r in self.rows:
            for c, col, k in m.COLUMNS:
                if k == "S":
                    fits("STOCK_INFO", col, r[c])
        for v in (m.ACCOUNT["email"], m.ACCOUNT["pw"]):
            self.assertLessEqual(len(v), 100)
        fits("MEMBER", "MEMBER_EMAIL", m.ACCOUNT["email"])
        fits("MEMBER", "MEMBER_PW", m.ACCOUNT["pw"])
        fits("MEMBER", "MEMBER_NICKNAME", m.ACCOUNT["nickname"])
        fits("MEMBER", "MEMBER_NAME", m.ACCOUNT["name"])
        fits("MEMBER", "MEMBER_EMAIL", m.FAKE_MEMBER_EMAIL)
        for k, col in (("company_name", "COMPANY_NAME"), ("company_location", "COMPANY_LOCATION"),
                       ("president_name", "PRESIDENT_NAME"), ("broker_no", "BROKER_NO"),
                       ("president_phone", "PRESIDENT_PHONE")):
            fits("BROKER_INFO", col, m.BROKER[k])
        for _, name in m.IMAGES:
            fits("STOCK_IMG", "IMG_URL", m.IMG_URL_PREFIX + name)
            fits("STOCK_IMG", "IMG_ORIGIN_NAME", name)
            fits("STOCK_IMG", "IMG_RENAME", name)
        for r in self.rows:  # EXAMPLE_STOCK(5절): 100, 10, 10
            self.assertLessEqual(len(r["complex_id"]), 100)
            self.assertLessEqual(len(r["layer"]), 10)
            self.assertLessEqual(len(r["split"]), 10)

    def test_ascii_no_parameter_marks_same_bytes(self):
        for name, text in self.out.items():
            self.assertTrue(text.isascii(), name)
            if name.endswith(".sql"):
                self.assertIsNone(re.search(r"[:&?]", text), name)
        self.assertEqual(m.build(LISTINGS, "FAKE")[0], self.out)

    def test_guard_is_first_and_files_differ_only_there(self):
        tmp = tempfile.mkdtemp()
        old = m.REAL_WGS84_SHA
        try:
            p = self.real_table(tmp)
            with open(p, "rb") as f:
                m.REAL_WGS84_SHA = m.sha256(f.read())
            out, _, targets = m.build(LISTINGS, p)
            self.assertEqual(targets, ["리허설", "운영"])
            for base in ("0_EXAMPLE_STOCK_만들기", "2_넣기", "4_되돌리기", "6_EXAMPLE_STOCK_지우기"):
                a, b = out[f"{base}_리허설.sql"], out[f"{base}_운영.sql"]
                diff = [d for d in difflib.unified_diff(a.split("\n"), b.split("\n"), lineterm="", n=0)
                        if d.startswith(("+", "-")) and not d.startswith(("+++", "---"))]
                self.assertEqual(len(diff), 2, base)
                self.assertIn("'ZIP_REHEARSAL'", diff[0])
                self.assertIn("'ADMIN'", diff[1])
                body = b.split("BEGIN\n")[-1] if base != "2_넣기" else b.split("END add_one;\nBEGIN\n")[1]
                self.assertTrue(body.startswith("  IF SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') <> 'ADMIN' THEN"), base)
            calls = CALL.findall(out["2_넣기_운영.sql"])
            self.assertEqual(parse_args(calls[0])[-2:], [f"{37.001:.7f}", f"{127.001:.7f}"])
        finally:
            m.REAL_WGS84_SHA = old
            shutil.rmtree(tmp)

    def test_production_only_from_the_real_table(self):
        tmp = tempfile.mkdtemp()
        old = m.REAL_WGS84_SHA
        try:
            p = self.real_table(tmp)
            m.REAL_WGS84_SHA = None  # ④ 전: 진짜 표 값이 아직 없다
            self.assertEqual(m.build(LISTINGS, p)[2], ["리허설"])
            with open(p, "rb") as f:
                m.REAL_WGS84_SHA = m.sha256(f.read())
            q = self.real_table(tmp, {5: (f"{37.005:.7f}", f"{127.0050001:.7f}")})  # 한 글자 바꾼 표
            self.assertEqual(m.build(LISTINGS, q)[2], ["리허설"])
            self.assertNotIn("2_넣기_운영.sql", m.build(LISTINGS, q)[0])
        finally:
            m.REAL_WGS84_SHA = old
            shutil.rmtree(tmp)

    def test_fake_coords_distinct_in_range_and_bad_tables_stop(self):
        c = m.fake_coords()
        self.assertEqual(len(set(c.values())), 75)
        m.check_coords(c)
        tmp = tempfile.mkdtemp()
        try:
            with self.assertRaises(m.Stop):  # 범위 밖(위도·경도가 바뀐 꼴)
                m.build(LISTINGS, self.real_table(tmp, {3: (f"{127.0:.7f}", f"{37.0:.7f}")}))
            with self.assertRaises(m.Stop):  # 두 줄이 같은 좌표
                m.build(LISTINGS, self.real_table(tmp, {4: (f"{37.003:.7f}", f"{127.003:.7f}")}))
            p = self.real_table(tmp)
            with open(p, encoding="utf-8") as f:
                text = f.read()
            with open(p, "w", encoding="utf-8", newline="") as f:
                f.write(text.replace("row,bdMgtSn,lat,lng", "row,bdMgtSn,lng,lat"))
            with self.assertRaises(m.Stop):  # 머리줄이 다르다
                m.build(LISTINGS, p)
        finally:
            shutil.rmtree(tmp)

    def test_rollback_and_fake_member_shape(self):
        rb = self.out["4_되돌리기_리허설.sql"]
        order = [rb.index(f"DELETE FROM {t} ") for t in ("STOCK_COORD", "SELL_DATE", "STOCK_IMG", "LIKE_STOCK", "STOCK_SAW", "STOCK_INFO")]
        self.assertEqual(order, sorted(order))
        for t in ('"LIKE"', '"COMMENT"', "COMMENT_LIKE", "BOARD", "TOKEN_INFO", "BROKER_INFO", "HELP_MESSAGE", "MESSAGE_FILE"):
            self.assertIn(f"DELETE FROM {t} ", rb)
        self.assertLess(rb.index("DELETE FROM COMMENT_LIKE"), rb.index('DELETE FROM "COMMENT"'))
        self.assertLess(rb.index("DELETE FROM MESSAGE_FILE"), rb.index("DELETE FROM HELP_MESSAGE"))
        self.assertLess(rb.rindex("DELETE FROM BROKER_INFO"), rb.index("DELETE FROM MEMBER "))
        self.assertNotIn("COMMIT", rb.replace("No COMMIT here", "").replace("COMMIT by hand", ""))
        self.assertNotIn("COMMIT", self.out["2_넣기_리허설.sql"].replace("No COMMIT here", ""))
        fake = self.out["R_가짜회원_리허설.sql"]
        self.assertIn("'ZIP_REHEARSAL'", fake)
        self.assertIn("CSV_ROW IN (1, 2)", fake)
        self.assertLess(fake.index("IF n = 0 THEN"), fake.index("INSERT INTO MEMBER"))  # 두 번째 판에서는 있던 가짜 회원을 다시 쓴다
        self.assertIn(f"SELECT MEMBER_NO INTO v_fake FROM MEMBER WHERE MEMBER_EMAIL = '{m.FAKE_MEMBER_EMAIL}';", fake)
        self.assertIn("TO_CHAR(SYSTIMESTAMP, 'YYYY-MM-DD HH24.MI.SS TZH.TZM')", self.out["3_대조.sql"])  # DB 시계의 오프셋


if __name__ == "__main__":
    unittest.main(verbosity=2)
