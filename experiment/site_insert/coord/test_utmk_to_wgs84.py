# -*- coding: utf-8 -*-
"""utmk_to_wgs84.py 시험. pyproj가 있어야 한다.

사용: python -I test_utmk_to_wgs84.py
"""
import csv
import io
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import utmk_to_wgs84 as uw  # noqa: E402
from pyproj import Transformer  # noqa: E402

FWD = Transformer.from_crs("EPSG:4326", "EPSG:5179", always_xy=True)


def table(points):
    """(entX, entY) 목록으로 utmk 표 bytes를 만든다."""
    buf = io.StringIO()
    w = csv.writer(buf, lineterminator="\n")
    w.writerow(uw.IN_HEADER)
    for i, (x, y) in enumerate(points, 1):
        w.writerow([str(i), f"{i:025d}", "1111010100", "111103100001", "0", "1", "0", "", x, y])
    return buf.getvalue().encode("utf-8")


def korea_points():
    """서울·경기·인천·광주·전남 언저리의 위경도 75곳을 UTM-K 글자로."""
    pts = []
    for i in range(75):
        lat = 34.2 + (i * 0.0613) % 3.7
        lng = 125.9 + (i * 0.0871) % 2.6
        x, y = FWD.transform(lng, lat)
        pts.append((f"{x:.10f}", f"{y:.10f}"))
    return pts


class T(unittest.TestCase):
    def test_origin(self):
        lat, lng = uw.kruger_inverse(1000000.0, 2000000.0)
        self.assertAlmostEqual(lat, 38.0, delta=1e-12)
        self.assertAlmostEqual(lng, 127.5, delta=1e-12)
        lng2, lat2 = uw.pyproj_transformer().transform(1000000.0, 2000000.0)
        self.assertAlmostEqual(lat2, 38.0, delta=1e-12)
        self.assertAlmostEqual(lng2, 127.5, delta=1e-12)

    def test_grid_two_methods_agree(self):
        t = uw.pyproj_transformer()
        worst = 0.0
        for i in range(61):
            for j in range(81):
                lat, lng = 33.0 + i * 0.1, 124.0 + j * 0.1
                x, y = FWD.transform(lng, lat)
                plng, plat = t.transform(x, y)
                klat, klng = uw.kruger_inverse(x, y)
                worst = max(worst, abs(plat - klat), abs(plng - klng))
                self.assertLess(abs(plat - lat), 1e-9)
                self.assertLess(abs(plng - lng), 1e-9)
        self.assertLess(worst, 1e-10)
        print(f"\n격자 4941곳: 두 방법의 가장 큰 차 {worst:.3e}도")

    def test_convert_rows(self):
        data, rep = uw.convert(table(korea_points()))
        rows = list(csv.reader(io.StringIO(data.decode("utf-8"))))
        self.assertEqual(rows[0], uw.OUT_HEADER)
        self.assertEqual(len(rows), 76)
        for r in rows[1:]:
            for v in r[2:]:
                self.assertRegex(v, r"^[0-9]{2,3}\.[0-9]{7}$")
            self.assertTrue(33 <= float(r[2]) <= 39 and 124 <= float(r[3]) <= 132)
        self.assertLess(max(rep["max_dlat"], rep["max_dlng"]), 1e-10)
        self.assertIn("tmerc", rep["definition"])
        # 같은 입력이면 같은 바이트
        self.assertEqual(uw.convert(table(korea_points()))[0], data)

    def test_swapped_axes_caught(self):
        pts = [(y, x) for x, y in korea_points()]
        with self.assertRaises(uw.Stop) as cm:
            uw.convert(table(pts))
        self.assertIn("범위", str(cm.exception))

    def test_authority_axis_order_would_be_caught(self):
        """always_xy 없이 만든 변환기를 쓰면 (위도, 경도)가 (경도, 위도) 자리에 들어가 범위에서 걸린다."""
        bad = Transformer.from_crs("EPSG:5179", "EPSG:4326")
        with self.assertRaises(uw.Stop):
            uw.convert(table(korea_points()), transformer=bad)

    def test_disagreement_caught(self):
        def off(x, y):
            lat, lng = uw.kruger_inverse(x, y)
            return lat + 2e-8, lng
        with self.assertRaises(uw.Stop) as cm:
            uw.convert(table(korea_points()), independent=off)
        self.assertIn("두 방법의 차", str(cm.exception))

    def test_bad_inputs(self):
        good = table(korea_points())
        with self.assertRaises(uw.Stop):
            uw.convert(good.replace(b"coord_bdMgtSn", b"coord_bd", 1))
        lines = good.decode("utf-8").splitlines()
        with self.assertRaises(uw.Stop):
            uw.convert(("\n".join(lines[:-1]) + "\n").encode("utf-8"))
        lines[5] = lines[5].rsplit(",", 1)[0] + ",1e6"
        with self.assertRaises(uw.Stop):
            uw.convert(("\n".join(lines) + "\n").encode("utf-8"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
