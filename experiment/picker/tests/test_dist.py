# -*- coding: utf-8 -*-
import os
import tempfile
import unittest

from picker import dist, snapshot
from picker.snapshot import APIS
from picker.tests.helpers import history, item, write_zip

CODES = ["11111", "28110", "28155", "41590", "41597", "12730", "46730"]
MONTHS = ["202401", "202402"]


def counts(rows, item_name):
    return {(r[1], r[2], r[3]): r[4] for r in rows if r[0] == item_name}


class TestDist(unittest.TestCase):
    def run_dist(self, bundles):
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "s.zip")
            write_zip(p, bundles, APIS, CODES, MONTHS)
            with snapshot.Snapshot(p) as s:
                rows, summary, problems = dist.count_all(s, history(), CODES, MONTHS)
        self.assertEqual(problems, [])
        return rows

    def test_duplicates(self):
        a = item("apt_rent", "여울동", "1", dealYear="2024", dealMonth="1")
        b = item("apt_rent", "오산동", "1", dealYear="2024", dealMonth="1")  # 옛 이름으로 다시 온 같은 거래
        c = item("rh_rent", "구례읍 봉동리", "2", dealYear="2024", dealMonth="1")
        e = item("rh_rent", "봉동리", "2", dealYear="2024", dealMonth="1")  # 읍면이 빠져 온 같은 거래
        g1 = item("apt_trade", "가동", "12", landCd="1", dealYear="2024", dealMonth="1")
        g2 = item("apt_trade", "가동", "12", landCd="2", dealYear="2024", dealMonth="1")  # 지번 글자는 같고 landCd가 다름
        k = item("offi_rent", "가동", "9", dealYear="2024", dealMonth="1")
        x = item("apt_rent", "중산동", "7", dealYear="2024", dealMonth="1")
        bundles = {
            ("apt_rent", "41597", "202401"): [[a, b]],
            ("rh_rent", "12730", "202401"): [[c, e]],
            ("apt_trade", "11111", "202401"): [[g1, g2]],
            ("offi_rent", "11111", "202401"): [[k], [dict(k)]],  # 쪽이 겹쳐 온 같은 줄
            ("apt_rent", "28110", "202401"): [[x]],
            ("apt_rent", "28155", "202401"): [[dict(x)]],  # 코드 사이 같은 거래
        }
        rows = self.run_dist(bundles)
        dup = counts(rows, "9 한 코드 안의 같은 거래")
        self.assertEqual(dup[("apt_rent", "41597", "a 열쇠가 같은 묶음")], 1)
        self.assertEqual(dup[("apt_rent", "41597", "c 그 가운데 응답 칸까지 같은 거래")], 0)
        self.assertEqual(dup[("apt_rent", "41597", "d 이름이 달리 온 묶음")], 1)
        self.assertEqual(dup[("rh_rent", "12730", "a 열쇠가 같은 묶음")], 1)
        self.assertNotIn(("apt_trade", "11111", "a 열쇠가 같은 묶음"), dup)  # landCd가 다르면 다른 단지
        self.assertEqual(dup[("offi_rent", "11111", "e 같은 묶음의 다른 쪽에 나뉜 묶음")], 1)
        self.assertEqual(dup[("offi_rent", "11111", "f 그 가운데 응답 칸까지 같은 거래가 다른 쪽에 나뉜 묶음")], 1)
        cross = counts(rows, "10 코드 사이 같은 거래")
        self.assertEqual(cross[("apt_rent", "28110+28155", "열쇠")], 1)
        summ = counts(rows, "12 요약")
        self.assertEqual(summ[("", "", "옛 이름(사곳리·오산동)으로 온 거래")], 1)
        self.assertEqual(summ[("", "", "읍면 없는 리(모두)")], 1)
        self.assertEqual(summ[("", "", "코드 사이 같은 거래(지우는 거래)")], 1)

    def test_page_edge(self):
        """다른 쪽에 나뉜 같은 줄: 앞 쪽 끝과 다음 쪽 처음에 있으면 g로 센다."""
        def mk(n, day):
            return [item("apt_rent", "가동", str(100 + i), dealYear="2024", dealMonth="1", dealDay=str(day)) for i in range(n)]
        same = item("apt_rent", "가동", "1", dealYear="2024", dealMonth="1")
        far = item("apt_rent", "나동", "1", dealYear="2024", dealMonth="1")
        p1 = [dict(far)] + mk(100, 2) + [dict(same)]  # same은 1쪽 맨 끝, far는 1쪽 맨 앞
        p2 = [dict(same)] + mk(100, 3) + [dict(far)]  # same은 2쪽 맨 앞, far는 2쪽 맨 끝
        rows = self.run_dist({("apt_rent", "11111", "202401"): [p1, p2]})
        dup = counts(rows, "9 한 코드 안의 같은 거래")
        self.assertEqual(dup[("apt_rent", "11111", "f 그 가운데 응답 칸까지 같은 거래가 다른 쪽에 나뉜 묶음")], 2)
        self.assertEqual(dup[("apt_rent", "11111", "g 그 가운데 앞 쪽 끝 50줄과 다음 쪽 처음 50줄에 같이 있는 묶음")], 1)

    def test_no_values_of_complexes(self):
        """지번·단지 이름 값은 표에 나오지 않는다(꼴만)."""
        it = item("apt_trade", "가동", "1234-56", name="비밀아파트", dealYear="2024", dealMonth="1")
        rows = self.run_dist({("apt_trade", "11111", "202401"): [[it]]})
        text = "\n".join(",".join(map(str, r)) for r in rows)
        self.assertNotIn("1234", text)
        self.assertNotIn("비밀아파트", text)

    def test_value_checks(self):
        """금액·층·건축년도·계약일 값의 건수만 센다(규칙은 이런 거래를 빼지 않는다)."""
        ym = dict(dealYear="2024", dealMonth="1")
        its = [item("apt_trade", "가동", "1", dealAmount="0", **ym),
               item("apt_rent", "가동", "2", deposit="0", monthlyRent="0", **ym),
               item("apt_rent", "가동", "3", deposit="0", monthlyRent="50", **ym),
               item("apt_rent", "가동", "4", deposit="-5", **ym),
               item("apt_rent", "가동", "5", floor="-1", buildYear="2027", dealDay="32", **ym)]
        b = {("apt_trade", "11111", "202401"): [its[:1]], ("apt_rent", "11111", "202401"): [its[1:]]}
        summ = counts(self.run_dist(b), "12 요약")
        want = {"매매 거래금액이 0 이하": 1, "보증금과 월세가 모두 0(전세로 침)": 1, "보증금이 0인 월세": 1,
                "보증금이 0보다 작음": 1, "층이 0 이하(거래는 둠, 층 고르기에서만 빠짐)": 1,
                "건축년도가 2026보다 큼(거래는 둠)": 1, "계약일이 1~31 밖": 1, "계약월이 1~12 밖": 0, "거래(다듬음)": 5}
        for k, v in want.items():
            self.assertEqual(summ[("", "", k)], v, k)

    def test_deterministic(self):
        it = item("apt_rent", "가동", "1", dealYear="2024", dealMonth="1")
        b = {("apt_rent", "11111", "202401"): [[it, dict(it)]]}
        self.assertEqual(self.run_dist(b), self.run_dist(b))


if __name__ == "__main__":
    unittest.main()
