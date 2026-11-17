# -*- coding: utf-8 -*-
import unittest
from collections import Counter
from fractions import Fraction

from picker import codego, model
from picker.clean import Cleaner, Rec, cid
from picker.common import H, TWO64, round_unit, u64
from picker.tests.helpers import history, history_rows, item

MONTHS = ["202401", "202402", "202403", "202404"]


def feed(data, cl, api, resp, ym, it, n=1):
    for _ in range(n):
        r = cl.clean(api, resp, ym, 1, it)
        assert isinstance(r, Rec), r
        data.add(r)
    return r


class Base(unittest.TestCase):
    def setUp(self):
        self.H = history()
        self.cl = Cleaner(self.H)
        self.d = model.Data(self.H)


class TestDedupe(Base):
    def test_cross_code_max_and_within_both(self):
        it = item("apt_rent", "중산동", "7", dealYear="2024", dealMonth="1")
        feed(self.d, self.cl, "apt_rent", "28110", "202401", it, 1)
        feed(self.d, self.cl, "apt_rent", "28155", "202401", it, 2)  # 한 코드 안의 같은 두 건은 둘 다
        self.d.finish()
        c = self.d.cx["apt|28155|중산동|0|7|0"]
        self.assertEqual(sum(c.deals["전세"].values()), 2)  # 열쇠마다 코드별 건수 가운데 큰 값
        self.assertEqual(sum(c.names.values()), 3)  # 이름 최빈은 지우기 전 줄로

    def test_count_basis(self):
        it = item("apt_rent", "중산동", "7", name="가", buildYear="2001")
        feed(self.d, self.cl, "apt_rent", "28110", "202401", it, 2)
        feed(self.d, self.cl, "apt_rent", "28155", "202401", it, 2)
        feed(self.d, self.cl, "apt_rent", "28155", "202402", item("apt_rent", "중산동", "7", name="나", buildYear="2002", dealMonth="2"), 3)
        self.d.finish()
        c = self.d.cx["apt|28155|중산동|0|7|0"]
        self.assertEqual(c.name(), "가")  # 가 4, 나 3 (지우기 전)
        self.assertEqual(c.build(), 2001)
        self.assertEqual(sum(c.deals["전세"].values()), 5)  # 2(지운 뒤) + 3


class TestMode(Base):
    def test_name_rules(self):
        for nm, n in (("나", 2), ("가", 2), ("", 5)):
            feed(self.d, self.cl, "apt_trade", "11111", "202401", item("apt_trade", "가동", "1", name=nm), n)
        self.d.finish()
        self.assertEqual(self.d.cx["apt|11111|가동|0|1|0"].name(), "가")  # 빈 값은 빼고, 같으면 문자열 순 앞
        d2 = model.Data(self.H)
        feed(d2, self.cl, "apt_trade", "11111", "202401", item("apt_trade", "가동", "1", name=""), 3)
        d2.finish()
        self.assertIsNone(d2.cx["apt|11111|가동|0|1|0"].name())

    def test_complex_level_unit(self):
        # 매매 API와 전월세 API의 값이 다른 단지: 최빈은 단지마다 모든 거래로
        feed(self.d, self.cl, "apt_trade", "11111", "202401", item("apt_trade", "가동", "1", name="매매이름", buildYear="2000"), 2)
        feed(self.d, self.cl, "apt_rent", "11111", "202401", item("apt_rent", "가동", "1", name="전월세이름", buildYear="2001"), 3)
        self.d.finish()
        c = self.d.cx["apt|11111|가동|0|1|0"]
        self.assertEqual((c.name(), c.build()), ("전월세이름", 2001))

    def test_build_tie(self):
        feed(self.d, self.cl, "apt_trade", "11111", "202401", item("apt_trade", "가동", "1", buildYear="2010"), 2)
        feed(self.d, self.cl, "apt_trade", "11111", "202401", item("apt_trade", "가동", "1", buildYear="2009", floor="3"), 2)
        self.d.finish()
        self.assertEqual(self.d.cx["apt|11111|가동|0|1|0"].build(), 2009)


class TestLayers(Base):
    def layer(self, c, deal):
        return model.layer_of(self.d, self.d.cx[c], deal, MONTHS)

    def test_misaligned_forms(self):
        it = item("apt_rent", "중산동", "7")
        feed(self.d, self.cl, "apt_rent", "28110", "202401", it, 1)  # 한 후보만 건
        feed(self.d, self.cl, "apt_rent", "28110", "202402", it, 2)
        feed(self.d, self.cl, "apt_rent", "28155", "202402", it, 1)  # 둘 다 건, 수가 다름
        feed(self.d, self.cl, "apt_rent", "28110", "202404", it, 1)
        feed(self.d, self.cl, "apt_rent", "28155", "202404", it, 1)  # 같은 수(중복이어도 지우기 전에 셈)
        self.d.finish()
        cands = self.H.candidates("28155")
        self.assertEqual(model.misaligned_months(self.d, "28155", "apt_rent", cands, MONTHS), ["202401", "202402"])
        self.assertEqual(model.misaligned_months(self.d, "11111", "apt_rent", ["11111"], MONTHS), [])

    def test_general_and_gwangju(self):
        feed(self.d, self.cl, "apt_rent", "11111", "202401", item("apt_rent", "가동", "1"), 3)
        feed(self.d, self.cl, "apt_rent", "28155", "202401", item("apt_rent", "중산동", "7"), 3)
        self.d.finish()
        self.assertEqual(self.layer("apt|11111|가동|0|1|0", "전세")[0], "일반")
        L, nbad, mk = self.layer("apt|28155|중산동|0|7|0", "전세")
        self.assertEqual((L, nbad), ("광주형", 1))
        self.assertIn("옛 코드 전 기간 0건", mk)
        # 다른 API는 따로 본다: 매매 쪽은 건이 없어 어긋나지 않음
        self.assertEqual(self.layer("apt|28155|중산동|0|7|0", "매매")[0], "일반")

    def test_hwaseong(self):
        feed(self.d, self.cl, "apt_rent", "41595", "202401", item("apt_rent", "진안동", "1"), 3)
        self.d.finish()
        self.assertEqual(self.layer("apt|41595|진안동|0|1|0", "전세")[0], "화성형")

    def test_neung(self):
        feed(self.d, self.cl, "apt_rent", "41595", "202401", item("apt_rent", "능동", "1"), 3)
        feed(self.d, self.cl, "apt_rent", "41597", "202401", item("apt_rent", "능동", "2"), 3)
        self.d.note_neung("apt_rent", "41597")
        self.d.finish()
        self.assertEqual(self.layer("apt|41595|능동|0|1|0", "전세")[0], "능동")  # 다른 구
        self.assertEqual(self.layer("apt|41597|능동|0|2|0", "전세")[0], "화성형")  # 제 구뿐
        self.d.note_neung("apt_rent", None)  # 구 미정
        self.assertEqual(self.layer("apt|41597|능동|0|2|0", "전세")[0], "능동")
        self.assertEqual(self.layer("apt|41597|능동|0|2|0", "매매")[0], "일반")  # API가 다르면 보지 않음(매매 쪽은 건도 없다)

    def test_ri_overlap(self):
        feed(self.d, self.cl, "rh_rent", "41593", "202401", item("rh_rent", "봉담읍 내리", "1"), 3)
        feed(self.d, self.cl, "rh_rent", "41593", "202401", item("rh_rent", "정남면 내리", "2"), 3)
        self.d.note_ri_overlap("rh_rent", "41593", "내리", ["4159325022", "4159333036"])
        self.d.finish()
        self.assertEqual(self.layer("rh|41593|봉담읍 내리|0|1|0", "전세")[0], "리")
        self.assertEqual(self.layer("rh|41593|정남면 내리|0|2|0", "전세")[0], "리")
        self.assertEqual(self.layer("rh|41593|봉담읍 내리|0|1|0", "매매")[0], "일반")  # 매매 API에는 겹침이 없고 건도 없다

    def test_ri_old_name(self):
        feed(self.d, self.cl, "apt_rent", "41597", "202401", item("apt_rent", "오산동", "1"), 1)
        feed(self.d, self.cl, "apt_rent", "41597", "202402", item("apt_rent", "여울동", "1", dealMonth="2"), 2)
        self.d.finish()
        self.assertEqual(self.layer("apt|41597|여울동|0|1|0", "전세")[0], "리")
        d2 = model.Data(self.H)
        feed(d2, self.cl, "apt_rent", "41597", "202402", item("apt_rent", "여울동", "1"), 3)
        d2.finish()
        self.assertEqual(model.layer_of(d2, d2.cx["apt|41597|여울동|0|1|0"], "전세", MONTHS)[0], "화성형")

    def test_none_layer(self):
        rows = history_rows() + [
            {"code": "5550000000", "name": "가도 가시", "created": "", "abolished": "", "status": "현존", "parent": "", "page": "t"},
            {"code": "5550010100", "name": "가도 가시 갑동", "created": "", "abolished": "", "status": "현존", "parent": "", "page": "t"},
            {"code": "5550010200", "name": "가도 가시 을동", "created": "", "abolished": "20250101", "status": "폐지", "parent": "", "page": "t"},
            {"code": "5551000000", "name": "가도 가시 새구", "created": "20250101", "abolished": "", "status": "현존", "parent": "", "page": "t"},
            {"code": "5551010100", "name": "가도 가시 새구 을동", "created": "20250101", "abolished": "", "status": "현존", "parent": "", "page": "t"},
            {"code": "5551010200", "name": "가도 가시 새구 병동", "created": "20250101", "abolished": "", "status": "현존", "parent": "", "page": "t"},
        ]
        h = codego.History(rows)
        self.assertEqual(h.candidates("55510"), ["55500", "55510"])
        cl, d = Cleaner(h), model.Data(h)
        feed(d, cl, "apt_rent", "55510", "202401", item("apt_rent", "을동", "1"), 3)
        d.finish()
        self.assertEqual(model.layer_of(d, d.cx["apt|55510|을동|0|1|0"], "전세", MONTHS)[0], model.NONE_LAYER)

    def test_marks(self):
        it = item("apt_rent", "중산동", "7")
        feed(self.d, self.cl, "apt_rent", "28110", "202401", it, 2)
        feed(self.d, self.cl, "apt_rent", "28110", "202402", it, 1)
        feed(self.d, self.cl, "apt_rent", "28155", "202402", it, 3)
        feed(self.d, self.cl, "apt_rent", "28155", "202403", it, 1)
        self.d.finish()
        mk = model.marks(self.d, "28155", "apt_rent", ["28110", "28155"], MONTHS)
        self.assertEqual(mk, ["옛 코드 전환 뒤 0건", "새 코드 쪽 0건", "둘 다 건 있고 수가 다름"])


class TestPairs(Base):
    def test_conditions(self):
        feed(self.d, self.cl, "apt_trade", "11111", "202401", item("apt_trade", "가동", "1"), 2)
        feed(self.d, self.cl, "apt_trade", "11111", "202401", item("apt_trade", "가동", "2", floor="0"), 3)
        feed(self.d, self.cl, "apt_trade", "11111", "202401", item("apt_trade", "가동", "3", buildYear="2027"), 3)
        feed(self.d, self.cl, "apt_trade", "11111", "202401", item("apt_trade", "가동", "4", name=""), 3)
        feed(self.d, self.cl, "apt_trade", "11111", "202401", item("apt_trade", "가동", "5"), 3)
        self.d.finish()
        ok = {c: model.pair_ok(self.d.cx[c], "매매")[0] for c in self.d.cx}
        self.assertEqual(ok, {"apt|11111|가동|0|1|0": False, "apt|11111|가동|0|2|0": False,
                              "apt|11111|가동|0|3|0": False, "apt|11111|가동|0|4|0": False, "apt|11111|가동|0|5|0": True})


class TestTargets(unittest.TestCase):
    def test_quota_table(self):
        q = model.cell_quota(6)
        self.assertEqual([q[("apt", d)] for d in model.DEALS], [1, 1, 1])
        self.assertEqual([q[("rh", d)] for d in model.DEALS], [1, 1, 0])
        self.assertEqual([q[("offi", d)] for d in model.DEALS], [1, 0, 0])
        q = model.cell_quota(15)
        self.assertEqual([q[(k, d)] for k in model.KINDS for d in model.DEALS], [3, 2, 2, 2, 1, 1, 2, 1, 1])
        q = model.cell_quota(24)
        self.assertEqual([q[(k, d)] for k in model.KINDS for d in model.DEALS], [4, 4, 4, 2, 2, 2, 2, 2, 2])
        q = model.cell_quota(27)  # 능동 6·리 6이 넘어온 화성형
        self.assertEqual(sum(q.values()), 27)
        self.assertEqual([q[(k, d)] for k in model.KINDS for d in model.DEALS], [5, 4, 4, 3, 2, 2, 3, 2, 2])

    def test_cascade(self):
        t = model.targets({"능동": 0, "리": 3, "화성형": 100, "광주형": 100, "일반": 100})
        self.assertEqual(t["능동"], (6, 0, 0, 6))
        self.assertEqual(t["리"], (6, 6, 3, 9))
        self.assertEqual(t["화성형"], (15, 9, 24, 0))
        t = model.targets({"능동": 0, "리": 0, "화성형": 20, "광주형": 100, "일반": 10})
        self.assertEqual(t["화성형"], (15, 12, 20, 7))
        self.assertEqual(t["광주형"], (24, 7, 31, 0))
        self.assertEqual(t["일반"], (24, 0, 10, 14))  # 일반에서 모자란 만큼은 버린다

    def test_avail_counts_bundles(self):
        h = history()
        cl, d = Cleaner(h), model.Data(h)
        feed(d, cl, "apt_rent", "11111", "202401", item("apt_rent", "가동", "1", aptSeq="11111-1"), 3)
        feed(d, cl, "apt_rent", "11111", "202401", item("apt_rent", "가동", "2", aptSeq="11111-1"), 3)
        feed(d, cl, "apt_rent", "11111", "202401", item("apt_rent", "나동", "3"), 3)
        d.finish()
        pk = model.Picker(d, MONTHS, lambda c: None)
        self.assertEqual(pk.avail["일반"], 2)  # 단지 셋, 묶음 둘


class TestBundles(Base):
    def test_chain(self):
        feed(self.d, self.cl, "apt_rent", "11111", "202401", item("apt_rent", "가동", "1", aptSeq="s1"))
        feed(self.d, self.cl, "apt_rent", "11111", "202401", item("apt_rent", "가동", "2", aptSeq="s1"))
        feed(self.d, self.cl, "apt_rent", "11111", "202401", item("apt_rent", "가동", "2", aptSeq="s2"))
        feed(self.d, self.cl, "apt_rent", "11111", "202401", item("apt_rent", "가동", "3", aptSeq="s2"))
        feed(self.d, self.cl, "apt_rent", "11111", "202401", item("apt_rent", "가동", "4", aptSeq=""))
        feed(self.d, self.cl, "apt_rent", "11111", "202401", item("apt_rent", "가동", "5", aptSeq=""))
        self.d.finish()
        b = model.bundles(self.d)
        a = "apt|11111|가동|0|1|0"
        self.assertEqual({b[a], b["apt|11111|가동|0|2|0"], b["apt|11111|가동|0|3|0"]}, {a})
        self.assertEqual(b["apt|11111|가동|0|4|0"], "apt|11111|가동|0|4|0")  # 빈 aptSeq는 잇지 않는다
        self.assertEqual(b["apt|11111|가동|0|5|0"], "apt|11111|가동|0|5|0")


class FakeCx:
    def __init__(self, kind, sgg):
        self.kind, self.sgg = kind, sgg


class FakeData:
    def __init__(self, cx):
        self.cx = cx


def make_picker(pairs, cx, bundle, target, quota, bad_addr=()):
    pk = model.Picker.__new__(model.Picker)
    pk.data = FakeData(cx)
    pk.bundle = bundle
    pk.pairs = sorted(pairs, key=lambda p: (p[0], p[1]))
    pk.target = {L: (0, 0, target.get(L, 0), 0) for L in model.LAYERS}
    pk.quota = {L: {(k, d): quota.get(L, {}).get((k, d), 0) for k in model.KINDS for d in model.DEALS} for L in model.LAYERS}
    pk.address = lambda c: None if c in bad_addr else ({"zipNo": "1", "roadAddrPart1": "길"}, "q")
    pk.skipped_addr = []
    return pk


def P(h, c, d, L):
    return (h, f"{c}|{d}", c, d, L, 3, [], 0)


class TestPick(unittest.TestCase):
    def test_gurye_first_and_stop_at_three(self):
        cx = {f"g{i}": FakeCx("apt", "12730") for i in range(5)}
        cx.update({f"x{i}": FakeCx("apt", "11111") for i in range(5)})
        pairs = [P(f"{i}0", f"x{i}", "매매", "일반") for i in range(5)] + [P(f"{i}9", f"g{i}", "매매", "광주형") for i in range(5)]
        pk = make_picker(pairs, cx, {c: c for c in cx}, {"일반": 1, "광주형": 4}, {"일반": {("apt", "매매"): 1}, "광주형": {("apt", "매매"): 4}})
        got = pk.run()
        self.assertEqual([g["pair"][2] for g in got["광주형"]], ["g0", "g1", "g2", "g3"])
        self.assertEqual([g["stage"] for g in got["광주형"]], ["1 구례", "1 구례", "1 구례", "2 전체"])
        self.assertEqual([g["pair"][2] for g in got["일반"]], ["x0"])

    def test_bundle_used_and_addr_skip_and_overflow(self):
        cx = {c: FakeCx("apt", "11111") for c in ("a", "b", "c", "d", "e")}
        bundle = {"a": "a", "b": "a", "c": "c", "d": "d", "e": "e"}  # a와 b는 한 묶음
        pairs = [P("01", "a", "매매", "일반"), P("02", "b", "전세", "일반"), P("03", "c", "매매", "일반"),
                 P("04", "d", "월세", "일반"), P("05", "e", "월세", "일반")]
        pk = make_picker(pairs, cx, bundle, {"일반": 3}, {"일반": {("apt", "매매"): 2, ("apt", "전세"): 1}}, bad_addr={"c"})
        got = pk.run()
        # b는 a와 한 묶음이라 건너뛰고, c는 주소를 못 찾아 건너뛰고, d·e는 칸 몫이 없어 넘기기로 들어간다
        self.assertEqual([(g["pair"][2], g["stage"]) for g in got["일반"]], [("a", "2 전체"), ("d", "3 넘기기"), ("e", "3 넘기기")])
        self.assertEqual(pk.skipped_addr, [("2 전체", "c|매매")])
        # 넘기기는 목표에 닿으면 멈춘다
        pk2 = make_picker(pairs, cx, bundle, {"일반": 2}, {"일반": {("apt", "매매"): 1, ("apt", "전세"): 1}}, bad_addr={"c"})
        got2 = pk2.run()
        self.assertEqual([g["pair"][2] for g in got2["일반"]], ["a", "d"])

    def test_none_layer_not_picked(self):
        cx = {"a": FakeCx("apt", "11111")}
        pk = make_picker([P("01", "a", "매매", model.NONE_LAYER)], cx, {"a": "a"}, {"일반": 1}, {"일반": {("apt", "매매"): 1}})
        got = pk.run()
        self.assertEqual(sum(len(v) for v in got.values()), 0)


class TestHashUse(unittest.TestCase):
    def test_pick_key_and_factor(self):
        self.assertEqual(model.hash_pick("apt|11111|가동|0|1|0", "매매"), H("pick", "apt|11111|가동|0|1|0|매매"))
        self.assertEqual(model.price_factor(0), Fraction(800, 1000))
        self.assertEqual(model.price_factor(TWO64 - 1), Fraction(1200, 1000))
        self.assertEqual(model.price_factor(TWO64 // 2), Fraction(1000, 1000))  # floor(401/2) = 200
        for bad in (-1, TWO64):
            with self.assertRaises(ValueError):
                model.price_factor(bad)


class TestSplit(unittest.TestCase):
    def test_counts(self):
        for n, want in ((6, (2, 4)), (5, (1, 4)), (4, (1, 3)), (2, (0, 2))):
            got = {L: [] for L in model.LAYERS}
            got["일반"] = [{"pair": P("0", f"c{i}", "매매", "일반")} for i in range(n)]
            sp = model.split(got)
            self.assertEqual(sp["일반"][:2], want)
            self.assertEqual(sp["일반"][2], want[1] < 4)
            order = sorted(got["일반"], key=lambda g: (H("split", g["pair"][2]), g["pair"][2]))
            self.assertEqual([g["split"] for g in order], ["연습"] * want[0] + ["본시험"] * want[1])


class TestListing(Base):
    def cx(self, rows):
        for it, n in rows:
            feed(self.d, self.cl, it[0], "11111", f"{int(it[1].get('dealYear')):04d}{int(it[1].get('dealMonth')):02d}", it[1], n)
        self.d.finish()
        return self.d.cx["apt|11111|가동|0|1|0"]

    def test_fields(self):
        rows = [(("apt_trade", item("apt_trade", "가동", "1", excluUseAr="84.5", floor="3", dealAmount="84,500",
                                     dealYear="2026", dealMonth="1")), 2),
                (("apt_trade", item("apt_trade", "가동", "1", excluUseAr="84.5", floor="7", dealAmount="84,500",
                                     dealYear="2026", dealMonth="2")), 1),
                (("apt_rent", item("apt_rent", "가동", "1", floor="20", dealYear="2024", dealMonth="1")), 1)]
        c = self.cx(rows)
        f = model.listing(c, "매매", {"zipNo": "12345", "roadAddrPart1": "서울 가길 1"}, "글")
        self.assertEqual(f["exclusiveArea"], 85)  # 84.5 -> 85 (0.5 올림)
        self.assertEqual(f["stockAddress"], "12345^^^서울 가길 1^^^")
        self.assertEqual((f["stockForm"], f["stockType"], f["stockFeeMonth"]), (1, 0, 0))
        self.assertEqual(f["useApprovalDate"], "2010-01-01")
        self.assertEqual(f["floorTotalCount"], 20)  # 모든 거래의 가장 높은 층
        # 층: 3이 2건, 7이 1건. 낮은 층부터 누적해 H(floor)로
        n = u64(H("floor", c.cid))
        self.assertEqual(f["currentFloor"], 3 if n * 3 < 2 * TWO64 else 7)
        # 가격: 최근 12달 3건. ㎡당 1000만원 x 85 x f를 100만원 단위로
        nf = u64(H("price", c.cid))
        fx = Fraction(800 + (401 * nf) // TWO64, 1000)
        self.assertTrue(Fraction(4, 5) <= fx <= Fraction(6, 5))
        self.assertEqual(f["stockSellPrice"], round_unit(Fraction(1000) * 85 * fx, 100) * 10000)
        self.assertEqual(f["supplyArea"], round_unit(Fraction(85) * Fraction(133, 100), 1))
        self.assertEqual((f["roomCount"], f["bathCount"]), (4, 2))
        self.assertEqual(f["stockManageFee"], 170000)
        self.assertEqual(f["regionNo"], "11111")

    def test_recent_fallback_and_even_median(self):
        rows = [(("apt_rent", item("apt_rent", "가동", "1", excluUseAr="10", deposit="1,000", monthlyRent="10",
                                    dealYear="2025", dealMonth="1")), 1),
                (("apt_rent", item("apt_rent", "가동", "1", excluUseAr="10", deposit="3,000", monthlyRent="30",
                                    dealYear="2025", dealMonth="2")), 1),
                (("apt_rent", item("apt_rent", "가동", "1", excluUseAr="10", deposit="2,000", monthlyRent="20",
                                    dealYear="2026", dealMonth="1")), 1),
                (("apt_rent", item("apt_rent", "가동", "1", excluUseAr="10", deposit="6,000", monthlyRent="60",
                                    dealYear="2026", dealMonth="2")), 1)]
        c = self.cx(rows)
        f = model.listing(c, "월세", {"zipNo": "1", "roadAddrPart1": "길"}, "글")
        self.assertEqual(f["_base_count"], 4)  # 최근 12달 2건이라 창 전체
        nf = u64(H("price", c.cid))
        fx = Fraction(800 + (401 * nf) // TWO64, 1000)
        dep = Fraction(250)  # ㎡당 보증금 100·200·300·600: 짝수 개라 가운데 둘(200, 300)의 평균
        self.assertEqual(f["stockSellPrice"], round_unit(dep * 10 * fx, 10) * 10000)
        self.assertEqual(f["stockFeeMonth"], round_unit(Fraction(25, 10) * 10 * fx, 1) * 10000)
        self.assertEqual((f["stockType"], f["roomCount"], f["bathCount"]), (2, 1, 1))

    def test_area_weighted_pick(self):
        """면적 값이 셋: 작은 면적부터 누적, 코드 사이 같은 거래는 한 번, 다른 거래(매매)의 면적은 세지 않는다.
        이 단지ID의 u는 약 0.788이라 2·2·1이면 가운데(84.5)다. 내림차순이면 59.5, 가중 없이면 101.4,
        중복을 안 지우면(2·2·2) 101.4, 매매 면적까지 세면(2·2·1·1) 101.4가 나온다."""
        c28 = "apt|28155|중산동|0|7|0"
        u = Fraction(u64(H("area", c28)), TWO64)
        self.assertTrue(Fraction(4, 5) > u >= Fraction(2, 3))  # 설계한 대로인지
        for day, ar in ((1, "59.5"), (2, "59.5"), (3, "84.5"), (4, "84.5")):
            feed(self.d, self.cl, "apt_rent", "28155", "202401", item("apt_rent", "중산동", "7", excluUseAr=ar, dealDay=str(day),
                                                                       dealYear="2024", dealMonth="1"))
        big = item("apt_rent", "중산동", "7", excluUseAr="101.4", dealDay="5", dealYear="2024", dealMonth="1")
        feed(self.d, self.cl, "apt_rent", "28110", "202401", big)
        feed(self.d, self.cl, "apt_rent", "28155", "202401", big)
        feed(self.d, self.cl, "apt_trade", "28155", "202401", item("apt_trade", "중산동", "7", excluUseAr="120", dealYear="2024", dealMonth="1"))
        self.d.finish()
        c = self.d.cx[c28]
        f = model.listing(c, "전세", {"zipNo": "1", "roadAddrPart1": "길"}, "글")
        self.assertEqual(f["_area_value"], Fraction("84.5"))
        self.assertEqual(f["exclusiveArea"], 85)

    def test_rooms(self):
        for A, want in ((39, (1, 1)), (40, (2, 1)), (59, (2, 1)), (60, (3, 2)), (84, (3, 2)), (85, (4, 2))):
            d = model.Data(self.H)
            feed(d, self.cl, "apt_trade", "11111", "202603", item("apt_trade", "가동", "1", excluUseAr=str(A)), 3)
            d.finish()
            f = model.listing(d.cx["apt|11111|가동|0|1|0"], "매매", {"zipNo": "1", "roadAddrPart1": "길"}, "글")
            self.assertEqual((f["roomCount"], f["bathCount"]), want, A)


if __name__ == "__main__":
    unittest.main()
