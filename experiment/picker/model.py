# -*- coding: utf-8 -*-
"""다듬은 거래에서 단지·쌍·층을 만들고(3.1 중복, 3.2, 3.3, 4장), 뽑고(5장), 매물 칸을 만든다(6장)."""
import math
from collections import Counter, defaultdict
from fractions import Fraction

from .clean import API_OF, NEUNG, NEUNG_CODES, cid
from .common import H, TWO64, code_sorted, pick_weighted, round_unit, u64

LAYERS = ("능동", "리", "화성형", "광주형", "일반")
NONE_LAYER = "뽑지 않음"  # 어긋난 달이 있는데 3·4 어디에도 들지 않음
BASE_TARGET = {"능동": 6, "리": 6, "화성형": 15, "광주형": 24, "일반": 24}
MAIN_MIN = 4
KINDS = ("apt", "rh", "offi")
KIND_SHARE = {"apt": Fraction(1, 2), "rh": Fraction(1, 4), "offi": Fraction(1, 4)}
DEALS = ("매매", "전세", "월세")
RI_NAMES = ("내리", "덕우리", "사곶리", "여울동")
GURYE = "12730"
RECENT = (202511, 202610)
STOCK_FORM = {"apt": 1, "rh": 2, "offi": 3}
STOCK_TYPE = {"매매": 0, "전세": 1, "월세": 2}
SUPPLY = {"apt": Fraction(133, 100), "rh": Fraction(120, 100), "offi": Fraction(190, 100)}
MARKS = ("옛 코드 전 기간 0건", "옛 코드 전환 뒤 0건", "새 코드 쪽 0건", "둘 다 건 있고 수가 다름")


class Complex:
    __slots__ = ("cid", "kind", "sgg", "path", "cur10", "mtn", "bon", "bu", "names", "builds", "seqs", "max_floor", "deals")

    def __init__(self, r):
        self.cid = cid(r)
        self.kind, self.sgg, self.path, self.cur10 = r.kind, r.sgg, r.path, r.cur10
        self.mtn, self.bon, self.bu = r.mtn, r.bon, r.bu
        self.names = Counter()  # 중복을 지우기 전 줄 전부(모든 거래)
        self.builds = Counter()
        self.seqs = set()
        self.max_floor = None
        self.deals = {}  # 거래 -> 중복을 지운 거래 Counter((y, m, d, 층, 면적, 금액) -> 건수). finish()에서 채운다

    def name(self):
        """가장 많이 나온 이름. 빈 값은 세지 않는다. 같으면 문자열 순 앞. 없으면 None."""
        if not self.names:
            return None
        top = max(self.names.values())
        return code_sorted([n for n, c in self.names.items() if c == top])[0]

    def build(self):
        """가장 많이 나온 건축년도. 같으면 작은 해. 없으면 None."""
        if not self.builds:
            return None
        top = max(self.builds.values())
        return min(y for y, c in self.builds.items() if c == top)


class Data:
    """거래를 모은다. add(rec)를 다 부른 뒤 finish()."""

    def __init__(self, history):
        self.H = history
        self.cx = {}
        self.area = defaultdict(Counter)  # (현재 시군구, api, ym) -> {응답 코드: 건수} (4.1: 중복 지우기 전)
        self.keys = defaultdict(Counter)  # 중복 열쇠 -> {응답 코드: 건수}
        self.neung = defaultdict(list)  # api -> [41590으로 온 능동 거래의 구(None은 구 미정)]
        self.ri_overlap = defaultdict(list)  # (api, 응답 코드) -> [(리 이름, 후보 현재 코드 묶음)]
        self.old_name = defaultdict(set)  # (api, 응답 코드) -> {옛 이름으로 온 거래의 현재 코드}
        self.done = False

    def add(self, r):
        c = self.cx.get(cid(r))
        if c is None:
            c = self.cx[cid(r)] = Complex(r)
        if r.name:
            c.names[r.name] += 1
        if r.build is not None:
            c.builds[r.build] += 1
        if r.seq:
            c.seqs.add(r.seq)
        if r.floor is not None and (c.max_floor is None or r.floor > c.max_floor):
            c.max_floor = r.floor
        self.area[(r.sgg, r.api, r.ym)][r.resp] += 1
        self.keys[(r.api, cid(r), r.deal, r.y, r.m, r.d, r.floor, r.area, r.amt)][r.resp] += 1
        if r.old_name:
            self.old_name[(r.api, r.resp)].add(r.cur10)

    def note_neung(self, api, gu):
        self.neung[api].append(gu)

    def note_ri_overlap(self, api, resp, name, choices):
        self.ri_overlap[(api, resp)].append((name, frozenset(choices)))

    def finish(self):
        """코드 사이 중복을 지운다: 열쇠마다 코드별 건수 가운데 큰 값."""
        for (api, c, deal, y, m, d, floor, area, amt), per in self.keys.items():
            self.cx[c].deals.setdefault(deal, Counter())[(y, m, d, floor, area, amt)] += max(per.values())
        for cx in self.cx.values():
            for deal in DEALS:
                cx.deals.setdefault(deal, Counter())
        self.done = True


# ---------------------------------------------------------------- 4장

def misaligned_months(data, sgg, api, cands, months):
    """4.1: 후보 코드별 영역 거래 건수가 서로 다른 달 목록."""
    if len(cands) < 2:
        return []
    out = []
    for ym in months:
        per = data.area.get((sgg, api, ym), {})
        vals = [per.get(c, 0) for c in cands]
        if len(set(vals)) > 1:
            out.append(ym)
    return out


def marks(data, sgg, api, cands, months):
    """4.3 부속 표시. 옛 코드 = 폐지된 코드나 상위 코드, 새 코드 = 나머지 후보. 옛·새는 코드들의 합으로 본다."""
    H = data.H
    old = [c for c in cands if H.sgg_abolished(c) or H.sgg_upper_only(c)]
    new = [c for c in cands if c not in old]
    if not old or not new:
        return []
    seq = []
    for ym in months:
        per = data.area.get((sgg, api, ym), {})
        seq.append((sum(per.get(c, 0) for c in old), sum(per.get(c, 0) for c in new)))
    out = []
    active = [(o, n) for o, n in seq if o + n > 0]
    if active and all(o == 0 for o, _ in active):
        out.append(MARKS[0])
    first_old = next((i for i, (o, _) in enumerate(seq) if o > 0), None)
    if first_old is not None and any(o == 0 and n > 0 for o, n in seq[first_old + 1:]):
        out.append(MARKS[1])
    if any(n == 0 and o > 0 for o, n in seq):
        out.append(MARKS[2])
    if any(o > 0 and n > 0 and o != n for o, n in seq):
        out.append(MARKS[3])
    return out


def area_view(data, sgg, api, months):
    """(후보 코드, 어긋난 달, 부속 표시). (현재 시군구, api)마다 한 번 셈한다."""
    key = (sgg, api)
    cache = data.__dict__.setdefault("_view", {})
    if key not in cache:
        cands = data.H.candidates(sgg)
        cache[key] = (cands, misaligned_months(data, sgg, api, cands, months), marks(data, sgg, api, cands, months))
    return cache[key]


def layer_of(data, c, deal, months):
    """4.2. (층, 어긋난 달 수, 부속 표시)."""
    H = data.H
    api = API_OF[(c.kind, deal)]
    cands, bad, mk = area_view(data, c.sgg, api, months)
    if c.path == NEUNG and c.cur10 in NEUNG_CODES and any(g != c.sgg for g in data.neung.get(api, [])):
        return "능동", len(bad), mk
    last = c.path.split(" ")[-1]
    if last in RI_NAMES:
        for resp in cands:
            if any(name == last and c.cur10 in ch for name, ch in data.ri_overlap.get((api, resp), [])):
                return "리", len(bad), mk
            if c.cur10 in data.old_name.get((api, resp), set()):
                return "리", len(bad), mk
    if bad:
        if any(H.sgg_upper_only(x) for x in cands):
            return "화성형", len(bad), mk
        if any(H.sgg_abolished(x) for x in cands):
            return "광주형", len(bad), mk
        return NONE_LAYER, len(bad), mk
    return "일반", 0, mk


# ---------------------------------------------------------------- 5장

def pair_ok(c, deal):
    """5.1. (통과 여부, 중복을 지운 건수)."""
    n = sum(c.deals[deal].values())
    if n < 3 or c.name() is None:
        return False, n
    if not any(k[3] is not None and k[3] >= 1 for k in c.deals[deal]):
        return False, n
    b = c.build()
    if b is None or b > 2026:
        return False, n
    return True, n


def bundles(data):
    """5.3 aptSeq 묶음: 같은 aptSeq로 이어진 단지ID를 끝까지 잇는다. 이름은 묶음 안 문자열 순 첫 단지ID."""
    parent = {c: c for c in data.cx}

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    by_seq = defaultdict(list)
    for c in code_sorted(data.cx):
        for s in code_sorted(data.cx[c].seqs):
            by_seq[s].append(c)
    for s in code_sorted(by_seq):
        cs = by_seq[s]
        for other in cs[1:]:
            a, b = find(cs[0]), find(other)
            if a != b:
                parent[max(a, b)] = min(a, b)
    groups = defaultdict(list)
    for c in data.cx:
        groups[find(c)].append(c)
    name = {}
    for members in groups.values():
        head = code_sorted(members)[0]
        for m in members:
            name[m] = head
    return name


def largest_remainder(total, shares, order):
    """정수 몫을 최대 나머지 방식으로 나눈다. 나머지가 같으면 order 순으로 준다."""
    raw = {k: Fraction(total) * shares[k] for k in order}
    base = {k: math.floor(v) for k, v in raw.items()}
    left = total - sum(base.values())
    rank = sorted(order, key=lambda k: (-(raw[k] - base[k]), order.index(k)))
    for k in rank[:left]:
        base[k] += 1
    return base


def cell_quota(target):
    """5.2 칸 몫: {(종류, 거래): 수}."""
    out = {}
    per_kind = largest_remainder(target, KIND_SHARE, KINDS)
    for k in KINDS:
        per_deal = largest_remainder(per_kind[k], {d: Fraction(1, 3) for d in DEALS}, DEALS)
        for d in DEALS:
            out[(k, d)] = per_deal[d]
    return out


def targets(avail):
    """5.2 모자란 층 넘기기. avail: 층 -> 후보 묶음 수. (층 -> (기본, 넘겨받음, 바뀐 목표, 넘김))."""
    out, carry = {}, 0
    for L in LAYERS:
        t = BASE_TARGET[L] + carry
        give = t - avail[L] if avail[L] < t else 0
        out[L] = (BASE_TARGET[L], carry, t - give, give)
        carry = give
    return out


def hash_pick(c, deal):
    return H("pick", f"{c}|{deal}")


class Picker:
    """5.3 뽑는 순서. address(c)는 도로명주소 결과(None이면 못 찾음)를 낸다."""

    def __init__(self, data, months, address):
        self.data, self.months, self.address = data, months, address
        self.bundle = bundles(data)
        self.pairs = []  # (해시, 열쇠, 단지ID, 거래, 층, 건수, 부속 표시, 어긋난 달 수)
        self.none_pairs = 0
        for c in code_sorted(data.cx):
            cx = data.cx[c]
            for d in DEALS:
                ok, n = pair_ok(cx, d)
                if not ok:
                    continue
                L, nbad, mk = layer_of(data, cx, d, months)
                if L == NONE_LAYER:
                    self.none_pairs += 1
                self.pairs.append((hash_pick(c, d), f"{c}|{d}", c, d, L, n, mk, nbad))
        self.pairs.sort(key=lambda p: (p[0], p[1]))
        self.avail = {L: len({self.bundle[p[2]] for p in self.pairs if p[4] == L}) for L in LAYERS}
        self.target = targets(self.avail)
        self.quota = {L: cell_quota(self.target[L][2]) for L in LAYERS}
        self.skipped_addr = []  # 주소를 못 찾아 건너뛴 쌍

    def run(self):
        used = set()
        left = {L: dict(self.quota[L]) for L in LAYERS}
        got = {L: [] for L in LAYERS}
        tried = {}

        def addr(c):
            if c not in tried:
                tried[c] = self.address(c)
            return tried[c]

        def take(p, stage):
            h, key, c, d, L, n, mk, nbad = p
            a = addr(c)
            if a is None:
                if key not in {k for _, k in self.skipped_addr}:
                    self.skipped_addr.append((stage, key))
                return False
            used.add(self.bundle[c])
            got[L].append({"pair": p, "address": a, "stage": stage})
            return True

        def eligible(p, use_quota=True):
            h, key, c, d, L, n, mk, nbad = p
            if L not in LAYERS or self.bundle[c] in used:
                return False
            if use_quota and left[L][(self.data.cx[c].kind, d)] <= 0:
                return False
            return True

        # 1단계: 구례 먼저, 3개에서 멈춘다
        gurye = 0
        for p in self.pairs:
            if gurye >= 3:
                break
            if self.data.cx[p[2]].sgg != GURYE or not eligible(p):
                continue
            if take(p, "1 구례"):
                left[p[4]][(self.data.cx[p[2]].kind, p[3])] -= 1
                gurye += 1
        # 2단계: 모든 쌍
        for p in self.pairs:
            if not eligible(p):
                continue
            if take(p, "2 전체"):
                left[p[4]][(self.data.cx[p[2]].kind, p[3])] -= 1
        # 3단계: 넘기기(칸 몫과 상관없이)
        for L in LAYERS:
            want = self.target[L][2]
            if len(got[L]) >= want:
                continue
            for p in self.pairs:
                if len(got[L]) >= want:
                    break
                if p[4] != L or not eligible(p, use_quota=False):
                    continue
                take(p, "3 넘기기")
        self.got = got
        return got


def split(got):
    """5.4: 층마다 H(split, 단지ID) 순으로 세워 앞의 floor(n/3)개는 연습, 나머지는 본시험."""
    out = {}
    for L in LAYERS:
        items = sorted(got[L], key=lambda g: (H("split", g["pair"][2]), g["pair"][2]))
        k = len(items) // 3
        for i, g in enumerate(items):
            g["split"] = "연습" if i < k else "본시험"
            g["hash_split"] = H("split", g["pair"][2])
        out[L] = (k, len(items) - k, len(items) - k < MAIN_MIN)
    return out


# ---------------------------------------------------------------- 6장

def _median(vals):
    vals = sorted(vals)
    n = len(vals)
    if n % 2:
        return vals[n // 2]
    return (vals[n // 2 - 1] + vals[n // 2]) / 2


def _expand(counter, pred=lambda k: True):
    for k in sorted(counter, key=lambda k: (k[0], k[1], k[2], -1 if k[3] is None else k[3], k[4], k[5])):
        if pred(k):
            for _ in range(counter[k]):
                yield k


def price_factor(n):
    """6.2 배율 f = (800 + floor(401 n / 2^64)) / 1000. n은 0 이상 2^64 미만이라 f는 0.800~1.200."""
    if not 0 <= n < TWO64:
        raise ValueError("n은 0 이상 2^64 미만")
    return Fraction(800 + (401 * n) // TWO64, 1000)


def listing(c, deal, address, rule_text):
    """6.1·6.2·6.3 매물 칸. c는 Complex, address는 정확히 맞은 도로명주소 결과(dict)."""
    tx = c.deals[deal]
    # 면적: 값별로 세어 작은 면적부터 누적, H(area)
    areas = Counter()
    for k, n in tx.items():
        areas[k[4]] += n
    alist = sorted(areas)
    a_pick = alist[pick_weighted([areas[a] for a in alist], u64(H("area", c.cid)))]
    A = round_unit(a_pick, 1)
    # 층: 1 이상만, 낮은 층부터 누적, H(floor)
    floors = Counter()
    for k, n in tx.items():
        if k[3] is not None and k[3] >= 1:
            floors[k[3]] += n
    flist = sorted(floors)
    floor = flist[pick_weighted([floors[f] for f in flist], u64(H("floor", c.cid)))]
    # 가격
    recent = [k for k in _expand(tx, lambda k: RECENT[0] <= k[0] * 100 + k[1] <= RECENT[1])]
    base = recent if len(recent) >= 3 else list(_expand(tx))
    f = price_factor(u64(H("price", c.cid)))
    if deal in ("매매", "전세"):
        med = _median([Fraction(k[5][0]) / k[4] for k in base])
        sell, fee = round_unit(med * A * f, 100) * 10000, 0
    else:
        md = _median([Fraction(k[5][0]) / k[4] for k in base])
        mm = _median([Fraction(k[5][1]) / k[4] for k in base])
        sell, fee = round_unit(md * A * f, 10) * 10000, round_unit(mm * A * f, 1) * 10000
    if A < 40:
        rooms = (1, 1)
    elif A < 60:
        rooms = (2, 1)
    elif A < 85:
        rooms = (3, 2)
    else:
        rooms = (4, 2)
    build = c.build()
    return {
        "stockName": c.name(),
        "stockAddress": f"{address['zipNo']}^^^{address['roadAddrPart1']}^^^",
        "stockForm": STOCK_FORM[c.kind],
        "stockType": STOCK_TYPE[deal],
        "stockSellPrice": sell,
        "stockFeeMonth": fee,
        "exclusiveArea": A,
        "currentFloor": floor,
        "useApprovalDate": f"{build:04d}-01-01",
        "supplyArea": round_unit(A * SUPPLY[c.kind], 1),
        "roomCount": rooms[0],
        "bathCount": rooms[1],
        "stockDirection": "남향",
        "floorTotalCount": "" if c.max_floor is None else c.max_floor,
        "stockManageFee": round_unit(Fraction(A) * 2000, 10000),
        "ableDate": "2027-01-01",
        "stockInfo": "예시 매물(실제 매물 아님)",
        "stockDetail": f"{rule_text} 규칙 example_listing_rule_v1, 단지ID {c.cid}",
        "regionNo": c.sgg,
        "_f": f,
        "_area_value": a_pick,
        "_base_count": len(base),
    }
