# -*- coding: utf-8 -*-
"""순서 2 단위 시험. 스냅샷 없이 돈다."""
import csv
import io
import json
import os
import tempfile
import unittest
from fractions import Fraction

from picker.clean import Rec
from picker.common import write_csv
from picker.juso import FrozenError, JusoError
from prep import answers, cli, juso_road, listings, replay, validity

KEY = "test-key-never-written"


def _page(total, items):
    return 200, json.dumps({"results": {"common": {"errorCode": "0", "totalCount": str(total)}, "juso": items}}).encode("utf-8")


def _item(n):
    return {"admCd": "1111010100", "zipNo": "03000", "roadAddrPart1": f"가 로 {n}", "siNm": "서울특별시", "sggNm": "종로구",
            "emdNm": "가동", "liNm": "", "mtYn": "0", "lnbrMnnm": str(n), "lnbrSlno": "0", "bdMgtSn": "1111010100100010000000001"}


class TestValidity(unittest.TestCase):
    def test_single(self):
        self.assertEqual(validity.judge(["11305"], {"11305": 0}), ("single", {"11305": (1, "")}))

    def test_true_zero(self):
        case, v = validity.judge(["41590", "41591"], {"41590": 0, "41591": 0})
        self.assertEqual(case, "true_zero")
        self.assertEqual(v, {"41590": (1, ""), "41591": (1, "")})

    def test_one(self):
        case, v = validity.judge(["41590", "41591"], {"41590": 0, "41591": 7})
        self.assertEqual(case, "one")
        self.assertEqual(v, {"41590": (0, "41591"), "41591": (1, "")})

    def test_multi_stops(self):
        with self.assertRaises(validity.MultiSource):
            validity.judge(["28110", "28125"], {"28110": 1, "28125": 2})

    def test_area_counts_use_current_sgg(self):
        row = ["apt_trade", "41590", "202601", "1", "0", "5", "매매", "apt|41591|가동|0|1|0"] + [""] * 11
        self.assertEqual(validity.area_counts([row]), {("41591", "apt_trade", "202601", "41590"): 1})


class TestListings(unittest.TestCase):
    def test_road_query(self):
        self.assertEqual(listings.road_query("03000^^^서울특별시  종로구 가로 1 ^^^"), "서울특별시 종로구 가로 1")

    def test_road_query_nfc(self):
        decomposed = "가"  # 가(풀어 쓴 꼴)
        self.assertEqual(listings.road_query(f"03000^^^{decomposed}로 1^^^"), "가로 1")

    def test_bad_address(self):
        for bad in ("서울 종로구", "0300^^^가로 1^^^", "03000^^^가로 1^^^상세", "03000^^^ ^^^"):
            with self.assertRaises(listings.BadListing):
                listings.road_query(bad)

    def test_model_input_drops_internal_fields(self):
        x = {h: "1" for h in listings.NINE}
        x.update({"stockAddress": "03000^^^가로 1^^^", "complex_id": "c", "deal": "매매", "layer": "일반", "split": "연습"})
        items = listings.model_input([("L01", x)])
        self.assertEqual(sorted(items[0]["fields"]), sorted(listings.NINE))
        self.assertEqual(items[0]["evidence"], {"매물.가격": 1, "매물.월세": 1, "매물.면적": 1, "매물.층": 1})


class TestJusoRoad(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.path = os.path.join(self.dir, "juso_road.csv")

    def test_freeze_and_replay(self):
        pages = {1: _page(2, [_item(1)]), 2: _page(2, [_item(2)])}
        J = juso_road.RoadJuso(self.path, "live", key=KEY, fetch=lambda p: pages[int(p["currentPage"])], sleep=0, clock=lambda: "T")
        res = J.lookup("가로 1")
        self.assertEqual([r["lnbrMnnm"] for r in res], ["1", "2"])
        with open(self.path, encoding="utf-8") as f:
            text = f.read()
        self.assertNotIn(KEY, text)
        self.assertTrue(text.startswith(",".join(juso_road.HEADER) + "\n"))
        R = juso_road.RoadJuso(self.path, "replay")
        self.assertEqual(R.lookup("가로 1"), res)
        with self.assertRaises(FrozenError):
            R.lookup("없는 검색어")

    def test_zero_result(self):
        J = juso_road.RoadJuso(self.path, "live", key=KEY, fetch=lambda p: _page(0, []), sleep=0, clock=lambda: "T")
        self.assertEqual(J.lookup("가로 9"), [])
        self.assertEqual(juso_road.load(self.path), {"가로 9": []})

    def test_bad_field_stops_before_writing(self):
        bad = _item(1)
        bad["admCd"] = "12"
        J = juso_road.RoadJuso(self.path, "live", key=KEY, fetch=lambda p: _page(1, [bad]), sleep=0, clock=lambda: "T")
        with self.assertRaises(JusoError):
            J.lookup("가로 1")
        self.assertFalse(os.path.exists(self.path))

    def test_needs_key(self):
        with self.assertRaises(JusoError):
            juso_road.RoadJuso(self.path, "live", key="")

    def test_same_query_called_once(self):
        calls = []

        def fetch(p):
            calls.append(p["keyword"])
            return _page(1, [_item(1)])

        J = juso_road.RoadJuso(self.path, "live", key=KEY, fetch=fetch, sleep=0, clock=lambda: "T")
        J.lookup("가로 1")
        J.lookup("가로 1")
        self.assertEqual(calls, ["가로 1"])
        again = juso_road.RoadJuso(self.path, "live", key=KEY, fetch=fetch, sleep=0, clock=lambda: "T")
        again.lookup("가로 1")
        self.assertEqual((calls, again.queries_new), (["가로 1"], 0))


RENT = Rec("apt_rent", "apt", "월세", "41591", "202601", 1, "4159110100", "41591", "가동", 1, 12, 3, Fraction("84.970"),
           5, 2001, 2026, 1, 9, (20000, 50), "가단지", "S1", "가동", True, False)
TRADE = Rec("rh_trade", "rh", "매매", "28260", "202512", 2, "2827510100", "28275", "가동", 0, 7, 0, Fraction("59.5"),
            None, None, 2025, 12, 31, (31000,), "", "", "가동", False, True)


def _as_text(row):
    """CSV로 썼다가 읽은 꼴(모두 글)."""
    buf = io.StringIO()
    csv.writer(buf, lineterminator="\n").writerow(row)
    return next(csv.reader(io.StringIO(buf.getvalue())))


class TestReplayRow(unittest.TestCase):
    def test_rent_row(self):
        row = replay.trade_row(RENT, {"excluUseAr": "84.970"}, 4)
        self.assertEqual(len(row), len(replay.TRADE_HEADER))
        self.assertEqual(row[:12], ["apt_rent", "41591", "202601", 1, 4, 9, "월세", "apt|41591|가동|1|12|3", "4159110100",
                                    "가동", "산12-3", "가단지"])
        self.assertEqual(row[12:], ["84.970", 5, 2001, "", 20000, 50, "가동"])

    def test_month_must_match(self):
        with self.assertRaises(replay.ReplayError):
            replay.trade_row(RENT._replace(m=2), {"excluUseAr": "84.970"}, 0)

    def test_rec_of_gives_back_what_data_uses(self):
        for r, area in ((RENT, "84.970"), (TRADE, "59.5")):
            back = replay.rec_of(_as_text(replay.trade_row(r, {"excluUseAr": area}, 0)))
            for name in ("api", "kind", "deal", "resp", "ym", "page", "cur10", "sgg", "path", "mtn", "bon", "bu", "area",
                         "floor", "build", "y", "m", "d", "amt", "name", "raw_umd"):
                self.assertEqual(getattr(back, name), getattr(r, name), name)
            self.assertEqual(cli._key(_as_text(replay.trade_row(r, {"excluUseAr": area}, 0))),
                             (r.api, f"{r.kind}|{r.sgg}|{r.path}|{r.mtn}|{r.bon}|{r.bu}", r.deal, r.y, r.m, r.d, r.floor,
                              r.area, r.amt))


class TestFiles(unittest.TestCase):
    def test_queries_bytes_same_as_csv_file(self):
        rows = [["L01", "가로 1"], ["L02", "나로 2, 3"], ["L03", '다로 "4"']]
        d = tempfile.mkdtemp()
        a, b = os.path.join(d, "a.csv"), os.path.join(d, "b.csv")
        write_csv(a, listings.QUERY_HEADER, rows)
        listings.write_queries(rows, b)
        with open(a, "rb") as fa, open(b, "rb") as fb:
            self.assertEqual(fa.read(), fb.read())
        self.assertEqual(listings.queries_bytes(rows), open(a, "rb").read())

    def test_read_expected_takes_crlf(self):
        d = tempfile.mkdtemp()
        p = os.path.join(d, "e.txt")
        lines = ["# 머리", ""] + [f"{'A' * 64}  {name}  {i}" for i, name in enumerate(cli.OUT_FILES.values())]
        with open(p, "w", encoding="utf-8", newline="") as f:
            f.write("\r\n".join(lines) + "\r\n")
        got = cli.read_expected(p)
        self.assertEqual(got["validity_v1.csv"], ("A" * 64, 2))
        with open(p, "a", encoding="utf-8", newline="") as f:
            f.write(f"{'B' * 64}  other.csv  1\r\n")
        with self.assertRaises(cli.Stop):
            cli.read_expected(p)

    def test_address_parts(self):
        self.assertEqual(listings.address_parts("03000^^^서울특별시  종로구 가로 1 ^^^"), ("03000", "서울특별시  종로구 가로 1 "))


class TestAnswers(unittest.TestCase):
    def test_windows_end_at_202610(self):
        self.assertEqual(answers.months_back(1), ["202610"])
        self.assertEqual(answers.months_back(3), ["202608", "202609", "202610"])
        self.assertEqual(answers.months_back(6)[0], "202605")
        self.assertEqual(answers.months_back(12)[0], "202511")

    def test_round_unit_negative_tie(self):
        self.assertEqual(answers.round_unit(Fraction("-12.25"), Fraction(1, 10)), Fraction("-12.2"))
        self.assertEqual(answers.round_unit(Fraction(5, 2), 1), 3)

    def test_assign_shape(self):
        self.assertEqual(answers.assign("apt|41591|가동|0|1|0", "월세")[:2], ["T1", "T4"])
        got = answers.assign("apt|41591|가동|0|1|0", "매매")
        self.assertEqual((got[0], len(set(got)), set(got[1:]) <= {"T2", "T3", "T5"}), ("T1", 3, True))


if __name__ == "__main__":
    unittest.main()
