# -*- coding: utf-8 -*-
"""규칙 v1 3.1(거래 다듬기)과 3.2(단지ID).

한 거래를 읽어 셋 가운데 하나를 낸다.
- Rec: 다듬은 거래(현재 법정동까지 정해짐)
- Pending: 도로명주소가 있어야 정해지는 거래(겹치는 리, 41590으로 온 능동). 7절로 정한다.
- Drop: 빼는 거래. 까닭(reason)을 단다. 규칙이 갈래를 정하지 않은 상태도 여기에 따로 까닭을 붙여 센다.
"""
import re
from collections import namedtuple
from fractions import Fraction

from .codego import rename_path
from .common import norm

KIND = {"apt_trade": "apt", "apt_rent": "apt", "rh_trade": "rh", "rh_rent": "rh", "offi_trade": "offi", "offi_rent": "offi"}
TRADE_APIS = ("apt_trade", "rh_trade", "offi_trade")
NAME_FIELD = {"apt": "aptNm", "rh": "mhouseNm", "offi": "offiNm"}
API_OF = {("apt", "매매"): "apt_trade", ("apt", "전세"): "apt_rent", ("apt", "월세"): "apt_rent",
          ("rh", "매매"): "rh_trade", ("rh", "전세"): "rh_rent", ("rh", "월세"): "rh_rent",
          ("offi", "매매"): "offi_trade", ("offi", "전세"): "offi_rent", ("offi", "월세"): "offi_rent"}
NEUNG = "능동"
NEUNG_CODES = ("4159510300", "4159710100")  # 병점구 능동, 동탄구 능동(05)
UPPER_HWASEONG = "41590"

_INT = re.compile(r"-?[0-9]+")
_DEC = re.compile(r"-?[0-9]+(?:\.[0-9]+)?")
_JIBUN = re.compile(r"([0-9]+)(?:-([0-9]+))?")

# 다듬은 거래. area는 Fraction, floor·build는 int 또는 None, amt는 (거래금액,) 또는 (보증금, 월세).
Rec = namedtuple("Rec", "api kind deal resp ym page cur10 sgg path mtn bon bu area floor build y m d amt name seq raw_umd old_name ri_split")
Pending = namedtuple("Pending", "why api resp ym page item fields choices")
Drop = namedtuple("Drop", "reason api resp ym page")


def cid(r):
    """3.2 단지ID: 종류|현재 시군구 코드|현재 법정동 이름|산 여부|본번|부번"""
    return f"{r.kind}|{r.sgg}|{r.path}|{r.mtn}|{r.bon}|{r.bu}"


def read_int(s):
    return int(s) if _INT.fullmatch(s) else None


def read_amount(s):
    """금액: 쉼표를 지우고 정수로 읽는다. 아니면 None."""
    return read_int(s.replace(",", ""))


def read_area(s):
    """전용면적: 소수로 읽는다(정확한 분수). 읽을 수 없거나 0 이하면 None."""
    if not _DEC.fullmatch(s):
        return None
    v = Fraction(s)
    return v if v > 0 else None


def read_jibun(s):
    """(산 여부, 본번, 부번) 또는 None. 앞의 '산'을 떼고 나머지 '본번-부번'을 정수로 바꾼다."""
    mtn = 0
    if s.startswith("산"):
        mtn = 1
        s = s[1:].strip()
    m = _JIBUN.fullmatch(s)
    if not m:
        return None
    return mtn, int(m.group(1)), int(m.group(2) or "0")


def fields(item):
    """칸 값마다 NFC로 맞추고 앞뒤 공백을 지운다. 없으면 빈 값."""
    return {k: norm(v) for k, v in item.items()}


class Cleaner:
    """이력(codego.History)을 들고 거래를 다듬는다. 응답 코드마다 법정동 이름 찾기 표를 만들어 둔다."""

    def __init__(self, history, umdcd_base="sgg"):
        self.H = history
        self.umdcd_base = umdcd_base  # 'sgg': 거래의 sggCd+umdCd, 'resp': 응답 코드+umdCd (⑤에서 정한다)
        self._idx = {}

    def index(self, resp):
        """응답 코드 resp의 (전체 경로 → 현재 코드들, 리 이름 → [(전체 경로, 현재 코드들)])."""
        if resp in self._idx:
            return self._idx[resp]
        full, ri = {}, {}
        for c in self.H.dong_codes(resp):
            p = rename_path(self.H.path(c))
            cur = tuple(self.H.current_of(c))
            full.setdefault(p, set()).update(cur)
            toks = p.split(" ")
            if len(toks) == 2:
                ri.setdefault(toks[1], {}).setdefault(p, set()).update(cur)
        self._idx[resp] = (full, ri)
        return self._idx[resp]

    def resolve_name(self, api, resp, f):
        """(상태, 값). 상태: ok(현재 10자리 코드), split(현재 코드 둘 이상), ri(겹치는 리: {경로: 현재 코드}), 그 밖은 갈래 없음."""
        umd = f.get("umdNm", "")
        if umd == "":
            return "법정동 이름 빔", None
        full, ri = self.index(resp)
        u = rename_path(umd)
        toks = u.split(" ")
        if len(toks) == 1 and u.endswith("리") and u not in full:
            # 읍면 없이 온 리
            if api == "apt_trade":
                # 아파트 매매는 읍면동 코드로 정한다. 10자리는 (거래의 sggCd 또는 응답 코드) + umdCd.
                ucd = f.get("umdCd", "")
                if ucd == "":
                    return "읍면 없는 리, umdCd 빔", None
                base = f.get("sggCd", "") if self.umdcd_base == "sgg" else resp
                code = base + ucd
                if code not in self.H.rows or code[5:] == "00000":
                    return "읍면 없는 리, umdCd가 이력에 없음", None
                cur = self.H.current_of(code)
                if len(cur) != 1:
                    return "읍면 없는 리, umdCd의 현재 코드가 하나가 아님", None
                return "ok", cur[0]
            hits = ri.get(u)
            if not hits:
                return "법정동 이름 맞지 않음", None
            curs = set()
            for cs in hits.values():
                curs.update(cs)
            if len(curs) == 1:
                return "ok", curs.pop()
            return "ri", {p: sorted(cs) for p, cs in sorted(hits.items())}
        cur = full.get(u)
        if not cur:
            return "법정동 이름 맞지 않음", None
        if len(cur) == 1:
            return "ok", next(iter(cur))
        return "split", sorted(cur)

    def clean(self, api, resp, ym, page, item):
        f = fields(item)
        kind = KIND[api]
        trade = api in TRADE_APIS
        # 숫자
        if trade:
            a = read_amount(f.get("dealAmount", ""))
            if a is None:
                return Drop("금액을 못 읽음", api, resp, ym, page)
            amt = (a,)
            deal = "매매"
        else:
            dep = read_amount(f.get("deposit", ""))
            mon = read_amount(f.get("monthlyRent", ""))
            if dep is None or mon is None:
                return Drop("금액을 못 읽음", api, resp, ym, page)
            if mon < 0:
                return Drop("월세가 0보다 작음", api, resp, ym, page)
            amt = (dep, mon)
            deal = "전세" if mon == 0 else "월세"
        area = read_area(f.get("excluUseAr", ""))
        if area is None:
            return Drop("면적을 못 읽거나 0 이하", api, resp, ym, page)
        y, m, d = (read_int(f.get(k, "")) for k in ("dealYear", "dealMonth", "dealDay"))
        if y is None or m is None or d is None:
            return Drop("계약일을 못 읽음", api, resp, ym, page)
        floor = read_int(f.get("floor", ""))
        build = read_int(f.get("buildYear", ""))
        if build is not None and build < 1900:
            build = None
        # 해제
        if trade:
            ct = f.get("cdealType", "")
            if ct == "O":
                return Drop("해제(O)", api, resp, ym, page)
            if ct != "":
                return Drop("해제 여부 값을 모름", api, resp, ym, page)
        # 지번
        j = read_jibun(f.get("jibun", ""))
        if j is None:
            return Drop("지번을 못 읽음", api, resp, ym, page)
        mtn, bon, bu = j
        if api == "apt_trade":
            lc = f.get("landCd", "")
            if lc == "1":
                mtn = 0
            elif lc == "2":
                mtn = 1
            elif lc != "":
                return Drop("landCd 값을 모름", api, resp, ym, page)
        # 법정동
        umd = f.get("umdNm", "")
        old_name = rename_path(umd) != umd
        state, val = self.resolve_name(api, resp, f)
        base = (api, kind, deal, resp, ym, page)
        if state == "ok":
            cur10 = val
            return Rec(*base, cur10, cur10[:5], self.H.path(cur10), mtn, bon, bu, area, floor, build, y, m, d, amt,
                       f.get(NAME_FIELD[kind], ""), f.get("aptSeq", ""), umd, old_name, False)
        partial = (mtn, bon, bu, area, floor, build, y, m, d, amt, f.get(NAME_FIELD[kind], ""), f.get("aptSeq", ""), umd, old_name)
        if state == "split":
            if resp == UPPER_HWASEONG and rename_path(umd) == NEUNG and tuple(val) == NEUNG_CODES:
                return Pending("능동 구 정하기", api, resp, ym, page, f, partial, tuple(val))
            return Drop("현재 코드가 둘 이상", api, resp, ym, page)
        if state == "ri":
            return Pending("리 겹침", api, resp, ym, page, f, partial, val)
        return Drop(state, api, resp, ym, page)

    def finish(self, p, cur10, ri_split):
        """Pending을 도로명주소로 정한 현재 코드로 Rec을 만든다."""
        mtn, bon, bu, area, floor, build, y, m, d, amt, name, seq, umd, old_name = p.fields
        kind = KIND[p.api]
        deal = "매매" if p.api in TRADE_APIS else ("전세" if amt[1] == 0 else "월세")
        return Rec(p.api, kind, deal, p.resp, p.ym, p.page, cur10, cur10[:5], self.H.path(cur10), mtn, bon, bu, area,
                   floor, build, y, m, d, amt, name, seq, umd, old_name, ri_split)
