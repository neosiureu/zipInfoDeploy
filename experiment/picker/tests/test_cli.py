# -*- coding: utf-8 -*-
import os
import tempfile
import unittest

from picker import cli
from picker.common import Scrubber, sha256_file, write_csv

EXPERIMENT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


class TestPins(unittest.TestCase):
    def setUp(self):
        self.old = os.getcwd()
        os.chdir(EXPERIMENT)
        self.tmp = tempfile.TemporaryDirectory()

    def tearDown(self):
        os.chdir(self.old)
        self.tmp.cleanup()

    def pins(self, extra=(), drop=None, change=None):
        z = os.path.join(self.tmp.name, "z.bin")
        with open(z, "wb") as f:
            f.write(b"zip")
        rows = [[p, sha256_file(p)] for p in cli.picker_files() + cli.codego_files()]
        rows += [["in:snapshot_T.zip", sha256_file(z)]]
        rows = [r for r in rows if r[0] != drop]
        if change:
            rows = [[n, "0" * 64 if n == change else h] for n, h in rows]
        p = os.path.join(self.tmp.name, "pins.csv")
        write_csv(p, ["name", "sha256"], rows + list(extra))
        return cli.load_pins(p), {"in:snapshot_T.zip": z}

    def test_ok(self):
        lines = []
        pins, zips = self.pins()
        cli.check_pins(pins, zips, lines.append)
        self.assertTrue(any("picker/common.py" in x for x in lines))
        self.assertNotIn("__pycache__", " ".join(cli.picker_files()))

    def test_changed_or_missing(self):
        for kw in ({"change": "picker/model.py"}, {"drop": "picker/clean.py"}, {"change": "in:snapshot_T.zip"},
                   {"extra": [["picker/없는파일.py", "0" * 64]]}):
            pins, zips = self.pins(**kw)
            with self.assertRaises(cli.Stop):
                cli.check_pins(pins, zips, lambda x: None)

    def test_missing_pins_file(self):
        with self.assertRaises(cli.Stop):
            cli.load_pins(os.path.join(self.tmp.name, "없음.csv"))


class TestRunStart(unittest.TestCase):
    def test_live_without_key_stops_first(self):
        old = os.environ.pop("JUSO_KEY", None)
        try:
            with tempfile.TemporaryDirectory() as d:
                args = cli.build_parser().parse_args(["run", "--mode", "live", "--zip", "z", "--first-zip", "z1", "--codes", "c",
                                                      "--outdir", d, "--pins", os.path.join(d, "없음.csv")])
                lines = []
                with self.assertRaises(cli.Stop) as cm:
                    cli.cmd_run(args, lines.append)
                self.assertIn("JUSO_KEY", str(cm.exception))  # 고정 해시 파일을 찾기도 전에 멈춘다
        finally:
            if old is not None:
                os.environ["JUSO_KEY"] = old


class TestLog(unittest.TestCase):
    def test_scrubbed_and_lf(self):
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "log.txt")
            log = cli.Log(p, Scrubber(cwd=d, home="/home/someone", secrets=["SECRET1234"]), echo=False)
            log(f"{d}/frozen/x.csv 키 SECRET1234 confmKey=abc")
            with open(p, "rb") as f:
                self.assertEqual(f.read(), "./frozen/x.csv 키 *** confmKey=***\n".encode("utf-8"))  # 줄마다 바로 쓴다
            log("둘째")
            with open(p, "rb") as f:
                self.assertEqual(f.read().count(b"\n"), 2)


if __name__ == "__main__":
    unittest.main()
