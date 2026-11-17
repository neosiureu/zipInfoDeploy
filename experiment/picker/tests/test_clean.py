# -*- coding: utf-8 -*-
import unittest
from fractions import Fraction

from picker.clean import Cleaner, Drop, Pending, Rec, cid, read_jibun
from picker.tests.helpers import history, item


class TestClean(unittest.TestCase):
    def setUp(self):
        self.cl = Cleaner(history())

    def c(self, api, resp, it, ym="202603"):
        return self.cl.clean(api, resp, ym, 1, it)

    def test_ok_and_id(self):
        r = self.c("apt_trade", "11111", item("apt_trade", "가동", "12-3", dealAmount=" 1,234 "))
        self.assertIsInstance(r, Rec)
        self.assertEqual(cid(r), "apt|11111|가동|0|12|3")
        self.assertEqual(r.amt, (1234,))
        self.assertEqual(r.area, Fraction("84.97"))
        self.assertEqual(r.deal, "매매")

    def test_cdeal(self):
        self.assertEqual(self.c("rh_trade", "11111", item("rh_trade", "가동", cdealType="O")).reason, "해제(O)")
        self.assertIsInstance(self.c("rh_trade", "11111", item("rh_trade", "가동", cdealType="")), Rec)
        self.assertEqual(self.c("rh_trade", "11111", item("rh_trade", "가동", cdealType="X")).reason, "해제 여부 값을 모름")

    def test_landcd(self):
        r = self.c("apt_trade", "11111", item("apt_trade", "가동", "산12-3", landCd="2"))
        self.assertEqual((r.mtn, r.bon, r.bu), (1, 12, 3))
        r = self.c("apt_trade", "11111", item("apt_trade", "가동", "12-3", landCd="2"))
        self.assertEqual(r.mtn, 1)  # landCd로 본다
        r = self.c("apt_trade", "11111", item("apt_trade", "가동", "산12", landCd="1"))
        self.assertEqual((r.mtn, r.bon, r.bu), (0, 12, 0))
        r = self.c("apt_trade", "11111", item("apt_trade", "가동", "산12", landCd=""))
        self.assertEqual(r.mtn, 1)  # 비었으면 지번 문자열
        self.assertEqual(self.c("apt_trade", "11111", item("apt_trade", "가동", "12", landCd="3")).reason, "landCd 값을 모름")
        # 아파트 매매 밖은 지번 문자열
        r = self.c("rh_rent", "11111", item("rh_rent", "가동", "산 7"))
        self.assertEqual((r.mtn, r.bon, r.bu), (1, 7, 0))

    def test_jibun(self):
        self.assertEqual(read_jibun("0012-03"), (0, 12, 3))
        for bad in ("", "1**", "BL-9", "가-", "12-", "12-3-4"):
            self.assertIsNone(read_jibun(bad), bad)
        self.assertEqual(self.c("apt_rent", "11111", item("apt_rent", "가동", "1**")).reason, "지번을 못 읽음")

    def test_numbers(self):
        self.assertEqual(self.c("apt_trade", "11111", item("apt_trade", "가동", dealAmount="1.5")).reason, "금액을 못 읽음")
        self.assertEqual(self.c("apt_trade", "11111", item("apt_trade", "가동", excluUseAr="0")).reason, "면적을 못 읽거나 0 이하")
        self.assertEqual(self.c("apt_trade", "11111", item("apt_trade", "가동", excluUseAr="x")).reason, "면적을 못 읽거나 0 이하")
        self.assertEqual(self.c("apt_trade", "11111", item("apt_trade", "가동", dealDay="")).reason, "계약일을 못 읽음")
        r = self.c("apt_trade", "11111", item("apt_trade", "가동", floor="", buildYear="0"))
        self.assertIsInstance(r, Rec)  # 거래는 두고 값만 없음
        self.assertIsNone(r.floor)
        self.assertIsNone(r.build)
        r = self.c("apt_trade", "11111", item("apt_trade", "가동", buildYear="1899"))
        self.assertIsNone(r.build)
        r = self.c("apt_trade", "11111", item("apt_trade", "가동", floor="-1"))
        self.assertEqual(r.floor, -1)

    def test_rent(self):
        self.assertEqual(self.c("offi_rent", "11111", item("offi_rent", "가동", monthlyRent="0")).deal, "전세")
        r = self.c("offi_rent", "11111", item("offi_rent", "가동", monthlyRent="50", deposit="1,000"))
        self.assertEqual((r.deal, r.amt), ("월세", (1000, 50)))
        self.assertEqual(self.c("offi_rent", "11111", item("offi_rent", "가동", monthlyRent="-1")).reason, "월세가 0보다 작음")

    def test_renames(self):
        r = self.c("apt_rent", "41597", item("apt_rent", "오산동"))
        self.assertEqual((r.cur10, r.path, r.old_name), ("4159711500", "여울동", True))
        r = self.c("apt_rent", "41591", item("apt_rent", "서신면 사곳리"))
        self.assertEqual((r.cur10, r.path, r.old_name), ("4159135033", "서신면 사곶리", True))
        r = self.c("apt_rent", "41590", item("apt_rent", "서신면 사곳리"), ym="202401")
        self.assertEqual((r.cur10, r.sgg), ("4159135033", "41591"))

    def test_ri_three_ways(self):
        # 읍면이 붙어 오면 그 경로
        r = self.c("rh_rent", "41593", item("rh_rent", "봉담읍 내리"))
        self.assertEqual(r.cur10, "4159325022")
        # 아파트 매매는 umdCd
        r = self.c("apt_trade", "41593", item("apt_trade", "내리", sggCd="41593", umdCd="33036"))
        self.assertEqual(r.cur10, "4159333036")
        self.assertEqual(self.c("apt_trade", "41593", item("apt_trade", "내리", sggCd="41593", umdCd="")).reason, "읍면 없는 리, umdCd 빔")
        self.assertEqual(self.c("apt_trade", "41593", item("apt_trade", "내리", sggCd="41593", umdCd="99999")).reason, "읍면 없는 리, umdCd가 이력에 없음")
        # 그 밖은 응답 코드 안에 하나뿐이면 그 리
        r = self.c("rh_rent", "12730", item("rh_rent", "봉동리"))
        self.assertEqual(r.cur10, "1273025021")
        # 둘 이상이면 도로명주소
        p = self.c("rh_rent", "41593", item("rh_rent", "내리"))
        self.assertIsInstance(p, Pending)
        self.assertEqual(p.why, "리 겹침")
        self.assertEqual(p.choices, {"봉담읍 내리": ["4159325022"], "정남면 내리": ["4159333036"]})

    def test_neung_from_upper(self):
        p = self.c("apt_rent", "41590", item("apt_rent", "능동"), ym="202312")
        self.assertIsInstance(p, Pending)
        self.assertEqual((p.why, p.choices), ("능동 구 정하기", ("4159510300", "4159710100")))
        r = self.c("apt_rent", "41595", item("apt_rent", "능동"))
        self.assertEqual(r.cur10, "4159510300")

    def test_name_states(self):
        self.assertEqual(self.c("apt_rent", "11111", item("apt_rent", "")).reason, "법정동 이름 빔")
        self.assertEqual(self.c("apt_rent", "11111", item("apt_rent", "도봉동")).reason, "법정동 이름 맞지 않음")
        # 영종 동이 제물포 응답에 오면 맞지 않음
        self.assertEqual(self.c("apt_rent", "28125", item("apt_rent", "중산동")).reason, "법정동 이름 맞지 않음")

    def test_old_code_to_current(self):
        r = self.c("apt_rent", "28110", item("apt_rent", "중산동"), ym="202401")
        self.assertEqual((r.cur10, r.sgg, r.path), ("2815510100", "28155", "중산동"))
        r = self.c("apt_rent", "28260", item("apt_rent", "금곡동"), ym="202401")
        self.assertEqual(r.sgg, "28290")

    def test_finish(self):
        p = self.c("rh_rent", "41593", item("rh_rent", "내리", "5"))
        r = self.cl.finish(p, "4159333036", True)
        self.assertEqual((cid(r), r.ri_split, r.deal), ("rh|41593|정남면 내리|0|5|0", True, "전세"))


if __name__ == "__main__":
    unittest.main()
