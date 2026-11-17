# -*- coding: utf-8 -*-
"""가짜 스냅샷으로 끝까지: 부르기(가짜 도로명주소) → 재생. 출력이 바이트까지 같은지 본다."""
import csv
import hashlib
import os
import tempfile
import unittest

from picker import cli, juso, snapshot
from picker.snapshot import APIS
from picker.tests.helpers import history, item, write_zip
from picker.tests.test_juso import FakeFetch, clock, result

CODES = ["11111", "12730", "28110", "28155", "41590", "41593", "41595", "41597", "46730"]
MONTHS = ["202401", "202402", "202403"]


def tx(api, umd, jb, m, **kw):
    return item(api, umd, jb, dealYear="2024", dealMonth=str(m), **kw)


def bundles():
    b = {}

    def put(api, code, ym, *its):
        b.setdefault((api, code, ym), [[]])[0].extend(its)

    for m in (1, 2, 3):
        ym = f"20240{m}"
        put("apt_trade", "11111", ym, tx("apt_trade", "가동", "1", m, dealAmount="80,000"))
        put("apt_rent", "11111", ym, tx("apt_rent", "가동", "1", m, deposit="40,000"))
        put("apt_rent", "28155", ym, tx("apt_rent", "중산동", "7", m))
        put("rh_trade", "12730", ym, tx("rh_trade", "구례읍 봉동리", "3", m, mhouseNm="봉동빌라"))
        put("apt_rent", "41595", ym, tx("apt_rent", "능동", "9", m))
    put("apt_rent", "41590", "202401", tx("apt_rent", "능동", "9", 1))  # 41590으로 온 능동
    put("rh_rent", "41593", "202401", tx("rh_rent", "내리", "5", 1, mhouseNm="내리빌라"))  # 읍면 없이 온 겹치는 리
    put("rh_rent", "41593", "202402", tx("rh_rent", "정남면 내리", "5", 2, mhouseNm="내리빌라"))
    put("rh_rent", "41593", "202403", tx("rh_rent", "정남면 내리", "5", 3, mhouseNm="내리빌라"))
    return b


def juso_table():
    t = {
        "경기도 화성시 능동 9": [result("4159710100", mn="9")],
        "경기도 화성시 효행구 봉담읍 내리 5": [],
        "봉담읍 내리 5": [],
        "경기도 화성시 효행구 정남면 내리 5": [result("4159333036", mn="5", bd="20")],
        "서울특별시 가구 가동 1": [result("1111110100", mn="1", bd="11", road="서울 가길 1")],
        "인천광역시 영종구 중산동 7": [result("2815510100", mn="7", bd="12", road="영종 길 7")],
        "전남광주통합특별시 구례군 구례읍 봉동리 3": [result("1273025021", mn="3", bd="13", road="구례 길 3")],
        "경기도 화성시 병점구 능동 9": [result("4159510300", mn="9", bd="14", road="능동 길 9")],
    }
    return t


def digest(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


class TestPipeline(unittest.TestCase):
    def run_once(self, d, zpath, mode, fetch=None, juso_path=None):
        out = os.path.join(d, mode)
        os.makedirs(out, exist_ok=True)
        jp = juso_path or os.path.join(out, "juso_jibun_v1.csv")
        J = juso.Juso(jp, mode, key="K" if mode == "live" else None, fetch=fetch or FakeFetch({}), sleep=0, clock=clock)
        lines = []
        with snapshot.Snapshot(zpath) as snap:
            res = cli.pick_all(snap, history(), CODES, MONTHS, J, lines.append)
            cli.write_outputs(res, out, lines.append)
        return out, jp, lines, res

    def test_live_then_replay(self):
        with tempfile.TemporaryDirectory() as d:
            z = os.path.join(d, "s.zip")
            write_zip(z, bundles(), APIS, CODES, MONTHS)
            f = FakeFetch(juso_table())
            out1, jp, lines, res = self.run_once(d, z, "live", fetch=f)
            jh = digest(jp)
            # 층: 일반(가동), 광주형(중산동·구례), 능동(병점구 능동: 41590 능동이 동탄구로), 리(정남면 내리)
            with open(os.path.join(out1, "complex_candidates_v1.csv"), encoding="utf-8", newline="") as fh:
                cand = list(csv.DictReader(fh))
            layers = {(r["complex_id"], r["deal"]): r["layer"] for r in cand if r["row"] == "pair"}
            self.assertEqual(layers[("apt|11111|가동|0|1|0", "매매")], "일반")
            self.assertEqual(layers[("apt|28155|중산동|0|7|0", "전세")], "광주형")
            self.assertEqual(layers[("rh|12730|구례읍 봉동리|0|3|0", "매매")], "광주형")
            self.assertEqual(layers[("apt|41595|능동|0|9|0", "전세")], "능동")
            self.assertEqual(layers[("rh|41593|정남면 내리|0|5|0", "전세")], "리")
            with open(os.path.join(out1, "example_listings_v1.csv"), encoding="utf-8", newline="") as fh:
                lst = list(csv.DictReader(fh))
            # 묶음 다섯(가동은 매매·전세 둘이지만 한 단지라 하나)
            self.assertEqual(len(lst), 5)
            self.assertEqual(len({r["bundle"] for r in lst}), 5)
            neung = [r for r in lst if r["layer"] == "능동"][0]
            self.assertEqual(neung["stage"], "3 넘기기")  # 목표 1의 칸 몫은 아파트 매매 하나라 전세 쌍은 넘기기로
            gurye = [r for r in lst if r["regionNo"] == "12730"]
            self.assertEqual(gurye[0]["stage"], "1 구례")
            self.assertEqual(gurye[0]["stockAddress"], "12345^^^구례 길 3^^^")
            # 재생: 같은 얼린 파일로 다시 돌리면 출력이 같고 얼린 파일은 그대로
            out2, _, _, _ = self.run_once(d, z, "replay", juso_path=jp)
            for name in ("complex_candidates_v1.csv", "example_listings_v1.csv"):
                self.assertEqual(digest(os.path.join(out1, name)), digest(os.path.join(out2, name)), name)
            self.assertEqual(digest(jp), jh)
            # 이미 얼린 검색어는 다시 부르지 않는다
            f2 = FakeFetch({})
            self.run_once(d, z, "live", fetch=f2, juso_path=jp)
            self.assertEqual(f2.calls, [])


if __name__ == "__main__":
    unittest.main()
