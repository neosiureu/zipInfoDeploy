# -*- coding: utf-8 -*-
import os
import unittest

from picker import codego
from picker.tests import helpers

HERE = os.path.dirname(os.path.abspath(__file__))
FROZEN = os.path.join(HERE, "..", "..", "frozen", "codego", "codego_history_v1.csv")


def page(rows, total=None, cnt=None, sido="41", sgg="590", paging="<span class='paging-item active'>1</span>"):
    body = ""
    for code, name, status, parent, created, abolished in rows:
        body += ('<tr> <td class="table_left">%s</td>\t\t <td class="table_center01">%s</td>\t '
                 '<td class="table_center01_11"><center>%s</center></td>\t<td class="table_center01_11">%s</td>\t'
                 '<td class="table_center01_11">%s</td>\t<td class="table_center01_11">%s</td>\t' % (code, name, status, parent, created, abolished))
    n = len(rows)
    return (f'<script>var cnt \t= {cnt if cnt is not None else n};var sidoCd = "{sido}";</script>'
            f'<input type="hidden" name="reqSggCd" value="{sgg}"><input type="hidden" name="reqUmdCd" value="*">'
            f'<input id="disuseAt01" type="radio" name="disuseAt" value="ALL" checked>'
            f'<td class="table_left">헤더</td>{body}{paging}'
            f'총 <strong><font color="FF9900">{total if total is not None else n}</font></strong>건')


ROWS = [("4159000000", "경기도 화성시", "현존", "4100000000", "20010321", "&nbsp"),
        ("4159011800", "경기도 화성시 능동", "폐지", "&nbsp", "&nbsp", "20260131")]


class TestParse(unittest.TestCase):
    def test_ok(self):
        rows, info = codego.parse_page("41_590", page(ROWS))
        self.assertEqual(info["rows"], 2)
        self.assertEqual(rows[1], ("4159011800", "경기도 화성시 능동", "", "20260131", "폐지", ""))

    def test_count_mismatch(self):
        with self.assertRaises(codego.PageError):
            codego.parse_page("p", page(ROWS, total=3))
        with self.assertRaises(codego.PageError):
            codego.parse_page("p", page(ROWS, cnt=1))

    def test_paging(self):
        with self.assertRaises(codego.PageError):
            codego.parse_page("p", page(ROWS, paging="<span class='paging-item active'>1</span><span class='paging-item'>2</span>"))

    def test_prefix(self):
        with self.assertRaises(codego.PageError):
            codego.parse_page("p", page(ROWS, sgg="591"))
        rows, info = codego.parse_page("29_all", page([("2900000000", "광주광역시", "폐지", "&nbsp", "&nbsp", "20260701")], sido="29", sgg="*"))
        self.assertEqual(info["prefix"], "29")


class TestHistory(unittest.TestCase):
    def setUp(self):
        self.H = helpers.history()

    def test_paths(self):
        self.assertEqual(self.H.path("4159325022"), "봉담읍 내리")
        self.assertEqual(self.H.path("2815510100"), "중산동")

    def test_window(self):
        self.assertNotIn("1111110300", self.H.dong_codes("11111"))

    def test_links_by_unique_path(self):
        # 금곡동은 옛 동구·서구 둘, 새 제물포·검단 둘이라 그것만으로는 잇지 않는다. 시군구 짝으로 가른다.
        self.assertEqual(self.H.current_of("2814010600"), ["2812510600"])
        self.assertEqual(self.H.current_of("2826011800"), ["2829010700"])
        self.assertNotIn(("28260", "28125"), self.H.links)
        self.assertNotIn(("28140", "28290"), self.H.links)

    def test_split_and_renames(self):
        self.assertEqual(self.H.current_of("4159011800"), ["4159510300", "4159710100"])  # 능동 1:2
        self.assertEqual(self.H.current_of("4159012900"), ["4159711500"])  # 오산동 -> 동탄구 오산동 -> 여울동
        self.assertEqual(self.H.current_of("4159035028"), ["4159135033"])  # 사곳리 -> 사곶리 -> 만세구 사곶리
        self.assertEqual(self.H.before_codes("4159711500"), ["4159012900", "4159710400", "4159711500"])

    def test_candidates_01_examples(self):
        # 01 295·296줄: 영종 28110·28155, 제물포 28110·28140·28125. 형제 코드는 넣지 않는다.
        self.assertEqual(self.H.candidates("28155"), ["28110", "28155"])
        self.assertEqual(self.H.candidates("28125"), ["28110", "28125", "28140"])
        self.assertEqual(self.H.candidates("41595"), ["41590", "41595"])
        self.assertEqual(self.H.candidates("12730"), ["12730", "46730"])
        self.assertEqual(self.H.candidates("11111"), ["11111"])

    def test_kinds(self):
        self.assertTrue(self.H.sgg_upper_only("41590"))
        self.assertFalse(self.H.sgg_abolished("41590"))
        self.assertTrue(self.H.sgg_abolished("46730"))
        self.assertFalse(self.H.sgg_upper_only("41597"))


@unittest.skipUnless(os.path.exists(FROZEN), "얼린 이력 표가 없다")
class TestFrozenHistory(unittest.TestCase):
    """얼린 이력으로 01 295·296줄의 예와 03 대응표(제3자)가 나오는지 본다."""

    def setUp(self):
        self.H = codego.History(codego.load_history_rows(FROZEN))

    def test_examples(self):
        self.assertEqual(self.H.candidates("28155"), ["28110", "28155"])
        self.assertEqual(self.H.candidates("28125"), ["28110", "28125", "28140"])

    def test_links(self):
        want = {("28110", "28125"), ("28110", "28155"), ("28140", "28125"), ("28260", "28275"), ("28260", "28290"),
                ("29110", "12210"), ("29140", "12240"), ("29155", "12270"), ("29170", "12300"), ("29200", "12330"),
                ("41590", "41591"), ("41590", "41593"), ("41590", "41595"), ("41590", "41597"), ("46730", "12730")}
        self.assertEqual(self.H.links, want)

    def test_only_neung_splits(self):
        many = [c for c in self.H.dong_codes() if self.H.rows[c]["abolished"] and len(self.H.next_codes(c)) != 1]
        self.assertEqual(many, ["4159011800"])


if __name__ == "__main__":
    unittest.main()
