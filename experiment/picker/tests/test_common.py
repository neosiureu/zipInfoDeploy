# -*- coding: utf-8 -*-
import hashlib
import os
import tempfile
import unicodedata
import unittest
from fractions import Fraction

from picker import common
from picker.common import H, Scrubber, pick_weighted, round_unit, u64


class TestHash(unittest.TestCase):
    def test_value(self):
        want = hashlib.sha256("zipinfo-example-v1|pick|apt|11111|가동|0|123|0|매매".encode("utf-8")).hexdigest()
        self.assertEqual(H("pick", "apt|11111|가동|0|123|0|매매"), want)

    def test_nfc_and_strip(self):
        nfd = unicodedata.normalize("NFD", "가동")
        self.assertNotEqual(nfd, "가동")
        self.assertEqual(H("pick", f"  apt|{nfd}  "), H("pick", "apt|가동"))
        self.assertEqual(H(" pick ", "x"), H("pick", "x"))

    def test_u64(self):
        h = "0123456789abcdef" + "0" * 48
        self.assertEqual(u64(h), 0x0123456789ABCDEF)
        with self.assertRaises(ValueError):
            u64("ABCDEF" + "0" * 58)


class TestWeighted(unittest.TestCase):
    def test_boundary(self):
        # n x T < C_j x 2^64. T=2, C=[1,2] -> n < 2^63 이면 첫째
        self.assertEqual(pick_weighted([1, 1], (1 << 63) - 1), 0)
        self.assertEqual(pick_weighted([1, 1], 1 << 63), 1)
        self.assertEqual(pick_weighted([3, 1], 0), 0)
        self.assertEqual(pick_weighted([3, 1], (1 << 64) - 1), 1)
        # 3/4 경계: n x 4 < 3 x 2^64
        self.assertEqual(pick_weighted([3, 1], 3 * (1 << 62) - 1), 0)
        self.assertEqual(pick_weighted([3, 1], 3 * (1 << 62)), 1)

    def test_bad(self):
        with self.assertRaises(ValueError):
            pick_weighted([0, 1], 5)
        with self.assertRaises(ValueError):
            pick_weighted([1], 1 << 64)


class TestRound(unittest.TestCase):
    def test_half_up(self):
        self.assertEqual(round_unit(Fraction(169, 2), 1), 85)  # round(84.5)는 84
        self.assertEqual(round(84.5), 84)
        self.assertEqual(round_unit(Fraction(169, 2) - Fraction(1, 10 ** 9), 1), 84)
        self.assertEqual(round_unit(Fraction(150), 100), 200)
        self.assertEqual(round_unit(Fraction(149), 100), 100)
        self.assertEqual(round_unit(Fraction(25), 10), 30)
        self.assertEqual(round_unit(15000, 10000), 20000)
        with self.assertRaises(TypeError):
            round_unit(84.5, 1)


class TestFiles(unittest.TestCase):
    def test_csv_lf_no_bom(self):
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "a.csv")
            common.write_csv(p, ["a", "b"], [["1", "가,나"], ["2", 'x"y']])
            with open(p, "rb") as f:
                data = f.read()
            self.assertFalse(data.startswith(b"\xef\xbb\xbf"))
            self.assertNotIn(b"\r", data)
            self.assertEqual(common.read_csv(p), (["a", "b"], [["1", "가,나"], ["2", 'x"y']]))
            q = os.path.join(d, "b.txt")
            common.write_text(q, "한\n둘\n")
            with open(q, "rb") as f:
                self.assertEqual(f.read(), "한\n둘\n".encode("utf-8"))

    def test_time(self):
        self.assertTrue(common.now_iso().endswith("+09:00"))

    def test_rel(self):
        old = os.getcwd()
        with tempfile.TemporaryDirectory() as d:
            os.makedirs(os.path.join(d, "work", "frozen"))
            try:
                os.chdir(os.path.join(d, "work"))
                self.assertEqual(common.rel(os.path.join(d, "work", "frozen", "x.csv")), "frozen/x.csv")
                self.assertEqual(common.rel(os.path.join("frozen", "x.csv")), "frozen/x.csv")
                # 작업 폴더 밖은 이름만(위 폴더 이름이 찍히지 않게)
                self.assertEqual(common.rel(os.path.join(d, "someone", "Downloads", "y.zip")), "y.zip")
            finally:
                os.chdir(old)

    def test_code_sorted(self):
        self.assertEqual(common.code_sorted(["나", "가", "B", "a"]), ["B", "a", "가", "나"])


class TestScrub(unittest.TestCase):
    def check(self, cwd, home, text, banned):
        s = Scrubber(cwd=cwd, home=home, secrets=["SECRETKEY123"])
        out = s(text)
        for b in banned:
            self.assertNotIn(b.lower(), out.lower())
        return out

    def test_cwd_above_home(self):
        cwd, home = r"C:\Users", r"C:\Users\juwon"
        out = self.check(cwd, home, r"C:\Users\juwon\AppData\x.py and C:\Users\other", ["juwon"])
        self.assertIn(r"~\AppData", out)
        self.assertIn(r".\other", out)

    def test_cwd_under_home(self):
        cwd, home = r"C:\Users\juwon\work\experiment", r"C:\Users\juwon"
        text = r"open 'C:\\Users\\juwon\\work\\experiment\\frozen\\a.csv' and C:/Users/juwon/x and c:\users\JUWON\y"
        out = self.check(cwd, home, text, ["juwon"])
        self.assertIn(r"'.\\frozen\\a.csv'", out)
        self.assertIn("~/x", out)

    def test_other_place(self):
        out = self.check(r"D:\work", r"C:\Users\juwon", r"C:\Users\juwon\AppData\Local\Programs\Python\zipfile.py", ["juwon"])
        self.assertTrue(out.startswith("~"))

    def test_short_temp(self):
        """Windows의 TEMP가 짧은 이름(8.3)이면 홈 폴더의 긴 이름과 글자가 달라 따로 바꾼다(⑤ 검토)."""
        s = Scrubber(cwd=r"C:\work\zipInfoDeploy\experiment", home=r"C:\Users\Abcdefgh Ijk",
                     temp=r"C:\Users\ABCDEF~1\AppData\Local\Temp")
        self.assertEqual(s(r"C:\Users\ABCDEF~1\AppData\Local\Temp\tmpab12\z.bin"), r"<TEMP>\tmpab12\z.bin")
        self.assertEqual(s(r"'C:\\Users\\ABCDEF~1\\AppData\\Local\\Temp\\tmp1'"), r"'<TEMP>\\tmp1'")
        self.assertEqual(s("c:/users/abcdef~1/appdata/local/temp/x"), "<TEMP>/x")
        self.assertEqual(s(r"C:\Users\Abcdefgh Ijk\AppData\Local\Temp\tmp"), r"~\AppData\Local\Temp\tmp")
        for out in (s(r"C:\Users\ABCDEF~1\AppData\Local\Temp\a"), s(r"C:\Users\Abcdefgh Ijk\b")):
            self.assertNotIn("abcdef", out.lower())

    def test_path_boundary(self):
        """경로 뒤에 이름이 이어지면 다른 폴더라 바꾸지 않는다. 키는 어디에 붙어 있든 바꾼다."""
        s = Scrubber(cwd="/work/exp", home="/home/ju", temp="/tmp", secrets=["KEY123"])
        self.assertEqual(s("/tmp/tmpab12/x and /tmpfoo"), "<TEMP>/tmpab12/x and /tmpfoo")
        self.assertEqual(s("/home/ju/a /home/jun/b /home/ju.old/c"), "~/a /home/jun/b ~.old/c")
        self.assertEqual(s("/work/exp에서 '/work/exp'"), ".에서 '.'")
        self.assertEqual(s("xKEY123y"), "x***y")

    def test_default_temp(self):
        s = Scrubber(cwd=os.path.join(os.sep, "nowhere", "work"), home=os.path.join(os.sep, "nowhere", "home"))
        self.assertEqual(s(os.path.join(tempfile.gettempdir(), "tmpx", "y.csv")), os.path.join("<TEMP>", "tmpx", "y.csv"))

    def test_key(self):
        s = Scrubber(cwd="/x", home="/home/u", secrets=["SECRETKEY123"])
        self.assertEqual(s("url?confmKey=abc&k=1"), "url?confmKey=***&k=1")
        self.assertEqual(s("key SECRETKEY123 here"), "key *** here")


if __name__ == "__main__":
    unittest.main()
