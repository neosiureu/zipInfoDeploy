# -*- coding: utf-8 -*-
import http.client
import json
import os
import tempfile
import unittest
from unittest import mock

from picker import juso


def result(adm, mt="0", mn="123", sl="0", bd="1", road="길 1", zipno="12345"):
    return {"admCd": adm, "zipNo": zipno, "roadAddrPart1": road, "mtYn": mt, "lnbrMnnm": mn, "lnbrSlno": sl,
            "bdMgtSn": bd, "roadAddr": "쓰지 않는 칸", "jibunAddr": "쓰지 않는 칸"}


class FakeFetch:
    """검색어 -> 결과 목록. 100개씩 쪽으로 나눠 준다."""

    def __init__(self, table, error=None):
        self.table, self.error, self.calls = table, error, []

    def __call__(self, params):
        self.calls.append(dict(params))
        if self.error:
            body = {"results": {"common": {"errorCode": self.error, "errorMessage": "오류", "totalCount": "0"}, "juso": None}}
            return 200, json.dumps(body).encode("utf-8")
        res = self.table.get(params["keyword"], [])
        page, per = int(params["currentPage"]), int(params["countPerPage"])
        chunk = res[(page - 1) * per: page * per]
        body = {"results": {"common": {"errorCode": "0", "errorMessage": "정상", "totalCount": str(len(res))},
                            "juso": chunk or None}}
        return 200, json.dumps(body, ensure_ascii=False).encode("utf-8")


def clock():
    return "2026-11-18T20:00:00+09:00"


class TestJuso(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = os.path.join(self.tmp.name, "juso_jibun_v1.csv")

    def tearDown(self):
        self.tmp.cleanup()

    def J(self, fetch, mode="live"):
        return juso.Juso(self.path, mode, key="SECRETKEY123", fetch=fetch, sleep=0, clock=clock)

    def test_jibun_text(self):
        self.assertEqual(juso.jibun_text(0, 123, 0), "123")
        self.assertEqual(juso.jibun_text(0, 123, 4), "123-4")
        self.assertEqual(juso.jibun_text(1, 123, 4), "산123-4")

    def test_pages_and_reload(self):
        many = [result("1111110100", bd=str(10 ** 24 + i)) for i in range(150)]
        f = FakeFetch({"서울특별시 가구 가동 123": many})
        j = self.J(f)
        got = j.lookup("서울특별시 가구 가동 123")
        self.assertEqual(len(got), 150)
        self.assertEqual([c["currentPage"] for c in f.calls], ["1", "2"])
        self.assertEqual({c["hstryYn"] for c in f.calls}, {"N"})
        with open(self.path, encoding="utf-8") as fh:
            text = fh.read()
        self.assertNotIn("SECRETKEY123", text)
        self.assertNotIn("쓰지 않는 칸", text)  # 얼린 칸 밖의 값은 쓰지 않는다
        self.assertEqual(len(juso.load(self.path)["서울특별시 가구 가동 123"]), 150)
        # 다시 만들면 부르지 않는다
        f2 = FakeFetch({})
        self.assertEqual(len(self.J(f2).lookup("서울특별시 가구 가동 123")), 150)
        self.assertEqual(f2.calls, [])

    def test_zero_and_quotes(self):
        f = FakeFetch({"가동 1": [result("1111110100", road='가길 1, "나동"')]})
        j = self.J(f)
        self.assertEqual(j.lookup("없는 곳 9"), [])
        self.assertEqual(j.lookup("가동 1")[0]["roadAddrPart1"], '가길 1, "나동"')
        frozen = juso.load(self.path)
        self.assertEqual(frozen["없는 곳 9"], [])
        with open(self.path, encoding="utf-8") as fh:
            lines = fh.read().splitlines()
        self.assertEqual(lines[1].split(",")[2], "")  # 0건 줄은 순번이 비었다
        self.assertEqual(lines[1].split(",")[4], "0")

    def test_replay(self):
        f = FakeFetch({"가동 1": [result("1111110100")]})
        self.J(f).lookup("가동 1")
        r = juso.Juso(self.path, "replay", fetch=FakeFetch({}), clock=clock)
        self.assertEqual(len(r.lookup("가동 1")), 1)
        with self.assertRaises(juso.FrozenError):
            r.lookup("가동 2")

    def test_truncated_and_count(self):
        f = FakeFetch({"가동 1": [result("1111110100"), result("1111110100", bd="2")]})
        self.J(f).lookup("가동 1")
        with open(self.path, "rb") as fh:
            data = fh.read()
        with open(self.path, "wb") as fh:
            fh.write(data[:-5])
        with self.assertRaises(juso.FrozenError):
            juso.load(self.path)
        lines = data.decode("utf-8").splitlines(True)
        with open(self.path, "wb") as fh:
            fh.write("".join(lines[:-1]).encode("utf-8"))  # 결과 둘 가운데 하나만 적힘
        with self.assertRaises(juso.FrozenError):
            juso.load(self.path)

    def test_error_is_not_zero(self):
        with self.assertRaises(juso.JusoError):
            self.J(FakeFetch({}, error="E0001")).lookup("가동 1")
        self.assertFalse(os.path.exists(self.path))
        with self.assertRaises(juso.JusoError):
            juso.Juso(self.path, "live", key="")

    def test_bad_fields_stop_before_freezing(self):
        """결과 칸의 꼴이 다르면 얼리기 전에 멈춘다. 멈춤 글에는 칸 이름만 있고 값은 없다(⑤ 검토)."""
        cases = [("lnbrSlno", ""), ("lnbrSlno", None), ("admCd", "111111010"), ("zipNo", "1234"),
                 ("roadAddrPart1", "  "), ("mtYn", "2"), ("lnbrMnnm", 123), ("bdMgtSn", "12a")]
        for k, v in cases:
            path = os.path.join(self.tmp.name, f"bad_{k}_{v!r}.csv")
            bad = result("1111110100", road="비밀길 99")
            bad[k] = v
            f = FakeFetch({"가동 1": [result("1111110100", bd=str(10 ** 24 + i)) for i in range(100)] + [bad]})
            j = juso.Juso(path, "live", key="K", fetch=f, sleep=0, clock=clock)
            with self.assertRaises(juso.JusoError) as cm:
                j.lookup("가동 1")
            msg = str(cm.exception)
            self.assertIn(k, msg)
            self.assertIn("2쪽 1번째", msg)  # 둘째 쪽의 결과라 첫 쪽 100개도 얼리지 않았다
            self.assertNotIn("비밀길", msg)
            self.assertFalse(os.path.exists(path), (k, v))

    def test_load_checks_fields(self):
        f = FakeFetch({"가동 1": [result("1111110100", zipno="12345")]})
        self.J(f).lookup("가동 1")
        with open(self.path, encoding="utf-8", newline="") as fh:
            text = fh.read()
        with open(self.path, "w", encoding="utf-8", newline="") as fh:
            fh.write(text.replace(",12345,", ",1234,"))
        with self.assertRaises(juso.FrozenError) as cm:
            juso.load(self.path)
        self.assertIn("zipNo", str(cm.exception))

    def test_retry_on_incomplete_read(self):
        class Flaky:
            def __init__(self, fails):
                self.fails, self.n = fails, 0
                self.ok = FakeFetch({"가동 1": [result("1111110100")]})

            def __call__(self, params):
                self.n += 1
                if self.n <= self.fails:
                    raise http.client.IncompleteRead(b"{")
                return self.ok(params)

        with mock.patch("picker.juso.time.sleep") as sl:
            j = self.J(Flaky(2))
            self.assertEqual(len(j.lookup("가동 1")), 1)
            self.assertEqual(j.calls, 3)
            self.assertEqual([c.args[0] for c in sl.call_args_list[:2]], [2, 5])
            j2 = juso.Juso(os.path.join(self.tmp.name, "c.csv"), "live", key="K", fetch=Flaky(99), sleep=0, clock=clock)
            with self.assertRaises(juso.JusoError) as cm:
                j2.lookup("가동 1")  # 세 번 다 끊기면 종료 코드 2가 아니라 도로명주소 오류(4)로 멈춘다
            self.assertIn("IncompleteRead", str(cm.exception))

    def test_exact_and_order(self):
        rs = [dict(result("1111110100", bd="30")), dict(result("1111110100", bd="4")), dict(result("1111110100", mn="124")),
              dict(result("1111110200", bd="1")), dict(result("1111110100", mt="1", bd="2"))]
        for r in rs:
            for k in ("roadAddr", "jibunAddr"):
                r.pop(k)
        hits = juso.exact(rs, {"1111110100"}, 0, 123, 0)
        self.assertEqual([h["bdMgtSn"] for h in hits], ["4", "30"])

    def test_find_retries_short(self):
        f = FakeFetch({"서울특별시 가구 가동 123": [result("9999999999")], "가동 123": [result("1111110100", bd="7")]})
        hits, q = juso.find(self.J(f), "서울특별시 가구 가동 123", "가동 123", {"1111110100"}, 0, 123, 0)
        self.assertEqual((hits[0]["bdMgtSn"], q), ("7", "가동 123"))
        f2 = FakeFetch({"서울특별시 가구 가동 5": [result("1111110100", mn="5")]})
        hits, q = juso.find(juso.Juso(os.path.join(self.tmp.name, "b.csv"), "live", key="K", fetch=f2, sleep=0, clock=clock),
                            "서울특별시 가구 가동 5", "가동 5", {"1111110100"}, 0, 5, 0)
        self.assertEqual(q, "서울특별시 가구 가동 5")
        self.assertEqual([c["keyword"] for c in f2.calls], ["서울특별시 가구 가동 5"])  # 맞으면 다시 찾지 않는다


if __name__ == "__main__":
    unittest.main()
