# -*- coding: utf-8 -*-
import gzip
import os
import tempfile
import unittest
import zipfile

from picker import snapshot


def xml(total, items, rc="000"):
    body = "".join("<item>" + "".join(f"<{k}>{v}</{k}>" for k, v in it.items()) + "</item>" for it in items)
    return (f"<response><header><resultCode>{rc}</resultCode><resultMsg>OK</resultMsg></header>"
            f"<body><items>{body}</items><numOfRows>1000</numOfRows><pageNo>1</pageNo><totalCount>{total}</totalCount></body></response>").encode("utf-8")


def make_zip(path, bundles, extra=()):
    """bundles: {(api, code, ym): ([(total, n_items), ...], done 수 또는 None)}"""
    with zipfile.ZipFile(path, "w") as zf:
        for (api, code, ym), (pages, done) in bundles.items():
            for i, (total, n) in enumerate(pages, 1):
                items = [{"umdNm": "가동", "jibun": str(k)} for k in range(n)]
                zf.writestr(f"snapshot_T/raw/{api}/{code}/{ym}_p{i}.xml.gz", gzip.compress(xml(total, items)))
            if done is not None:
                zf.writestr(f"snapshot_T/raw/{api}/{code}/{ym}.done", f"{done}\r\n")
        for name in extra:
            zf.writestr(name, b"x")


class TestCheck(unittest.TestCase):
    def run_check(self, bundles, extra=(), apis=("apt_trade",), codes=("11111",), months=("202311", "202312")):
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "s.zip")
            make_zip(p, bundles, extra)
            with snapshot.Snapshot(p) as s:
                return s.check(list(codes), list(months), apis)

    def test_ok_multi_page(self):
        b = {("apt_trade", "11111", "202311"): ([(1500, 1000), (1500, 500)], 1500),
             ("apt_trade", "11111", "202312"): ([(0, 0)], 0)}
        summary, problems = self.run_check(b)
        self.assertEqual(problems, [])
        self.assertEqual(summary["거래"], 1500)
        self.assertEqual(summary["응답 쪽"], 3)

    def test_missing_bundle(self):
        b = {("apt_trade", "11111", "202311"): ([(0, 0)], 0)}
        _, problems = self.run_check(b)
        self.assertIn(("응답 없음", ("apt_trade", "11111", "202312")), problems)
        self.assertIn(("완료 표시 없음", ("apt_trade", "11111", "202312")), problems)

    def test_sum_differs(self):
        b = {("apt_trade", "11111", "202311"): ([(1500, 1000), (1500, 499)], 1499),
             ("apt_trade", "11111", "202312"): ([(0, 0)], 0)}
        _, problems = self.run_check(b)
        self.assertEqual(problems, [("읽은 거래 수의 합이 totalCount와 다름", ("apt_trade", "11111", "202311"))])

    def test_total_differs_between_pages(self):
        # 마지막 쪽 값과만 대면 합이 맞아 버리는 경우
        b = {("apt_trade", "11111", "202311"): ([(1501, 1000), (1500, 500)], 1500),
             ("apt_trade", "11111", "202312"): ([(0, 0)], 0)}
        _, problems = self.run_check(b)
        self.assertEqual(problems, [("쪽마다 totalCount가 다름", ("apt_trade", "11111", "202311"))])

    def test_done_differs(self):
        b = {("apt_trade", "11111", "202311"): ([(3, 3)], 2), ("apt_trade", "11111", "202312"): ([(0, 0)], 0)}
        _, problems = self.run_check(b)
        self.assertEqual(problems, [(".done의 수와 다름", ("apt_trade", "11111", "202311"))])

    def test_extra_files(self):
        b = {("apt_trade", "11111", "202311"): ([(0, 0)], 0), ("apt_trade", "11111", "202312"): ([(0, 0)], 0),
             ("apt_trade", "22222", "202311"): ([(0, 0)], 0)}
        summary, problems = self.run_check(b, extra=["snapshot_T/raw/apt_trade/11111/notes.txt"])
        self.assertEqual(problems, [])
        self.assertEqual(summary["묶음 밖 응답 묶음"], 1)
        self.assertEqual(summary["묶음 밖 완료 표시"], 1)
        self.assertEqual(summary["raw 아래 다른 파일"], 1)

    def test_page_gap(self):
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "s.zip")
            with zipfile.ZipFile(p, "w") as zf:
                zf.writestr("snapshot_T/raw/apt_trade/11111/202311_p1.xml.gz", gzip.compress(xml(2, [{"a": "1"}])))
                zf.writestr("snapshot_T/raw/apt_trade/11111/202311_p3.xml.gz", gzip.compress(xml(2, [{"a": "1"}])))
                zf.writestr("snapshot_T/raw/apt_trade/11111/202311.done", "2\n")
            with snapshot.Snapshot(p) as s:
                _, problems = s.check(["11111"], ["202311"], ("apt_trade",))
            self.assertIn(("쪽 번호가 이어지지 않음", ("apt_trade", "11111", "202311")), problems)


class TestCodes(unittest.TestCase):
    def test_read_codes_bom_and_comments(self):
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "c.txt")
            with open(p, "wb") as f:
                f.write("﻿# 머리\n11305\n41590  # 옛 코드\n11305\n12730,구례\n".encode("utf-8"))
            self.assertEqual(snapshot.read_codes(p), ["11305", "41590", "12730"])

    def test_months(self):
        m = snapshot.month_list()
        self.assertEqual((len(m), m[0], m[-1]), (36, "202311", "202610"))


if __name__ == "__main__":
    unittest.main()
