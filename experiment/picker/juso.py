# -*- coding: utf-8 -*-
"""도로명주소 검색(규칙 v1 7절)을 얼려 두고 다시 쓴다.

두 모드는 판정까지 같은 길을 탄다.
- 부르기(live): 얼린 파일에 없는 검색어만 부른다. 한 검색어의 쪽을 다 받은 뒤 그 줄들을 한 번에 쓰고 flush 한다.
  판정은 쓴 줄을 다시 읽어서만 한다. 얼린 칸 밖의 응답 값은 어디에도 쓰지 않는다.
- 재생(replay): 얼린 파일만 쓴다. 없는 검색어가 나오면 멈춘다.

얼린 줄: 검색어의 결과 하나에 한 줄(검색어, 쪽, 순번, 부른 시각, 결과 수, 7절 칸 일곱).
0건이면 결과 칸을 비운 줄 하나를 쓰고(순번 빈 값), 그 줄은 결과로 세지 않는다.
"""
import csv
import http.client
import io
import json
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request

from .common import now_iso

FIELDS = ["admCd", "zipNo", "roadAddrPart1", "mtYn", "lnbrMnnm", "lnbrSlno", "bdMgtSn"]
HEADER = ["query", "page", "seq", "called_at", "total"] + FIELDS
URL = "https://business.juso.go.kr/addrlink/addrLinkApi.do"
# 결과를 바꿀 수 있는 요청 값은 기본값에 맡기지 않고 적는다(⑤에서 정함). 키는 여기에 없다.
FIXED = {"countPerPage": "100", "resultType": "json", "hstryYn": "N", "firstSort": "none", "addInfoYn": "N"}
KEY_ENV = "JUSO_KEY"


# 얼리는 칸의 꼴(⑤ 검토). 응답이 이와 다르면 얼리기 전에 멈춘다. "주소를 못 찾음"으로 조용히 건너뛰지 않게.
# 멈춤 글에는 칸 이름만 적고 값은 적지 않는다(값이 주소라서).
FIELD_FORMS = {
    "admCd": re.compile(r"[0-9]{10}"),
    "zipNo": re.compile(r"[0-9]{5}"),
    "roadAddrPart1": None,  # 빈 값이 아니다(공백뿐도 안 됨)
    "mtYn": re.compile(r"[01]"),
    "lnbrMnnm": re.compile(r"[0-9]+"),
    "lnbrSlno": re.compile(r"[0-9]+"),
    "bdMgtSn": re.compile(r"[0-9]+"),
}
# 다시 부르는 통신 오류. 본문을 받다 끊기면 http.client.IncompleteRead(HTTPException)가 나는데 OSError가 아니다.
RETRY_ERRORS = (urllib.error.URLError, TimeoutError, OSError, http.client.HTTPException)


class JusoError(RuntimeError):
    pass


class FrozenError(RuntimeError):
    pass


def bad_fields(values):
    """결과 하나(FIELDS 순서의 값)에서 꼴이 어긋난 칸 이름 목록. 문자열이 아닌 값(null, 숫자)도 어긋남이다."""
    bad = []
    for k, v in zip(FIELDS, values):
        rx = FIELD_FORMS[k]
        if not isinstance(v, str):
            bad.append(k)
        elif rx is None:
            if v.strip() == "":
                bad.append(k)
        elif not rx.fullmatch(v):
            bad.append(k)
    return bad


def jibun_text(mtn, bon, bu):
    """7절 지번의 꼴: 부번이 0이면 본번만, 아니면 본번-부번, 산이면 앞에 '산'."""
    s = str(bon) if bu == 0 else f"{bon}-{bu}"
    return ("산" if mtn else "") + s


def load(path):
    """얼린 파일을 읽어 {검색어: [결과 dict, ...]}를 낸다. 결과 수와 적힌 결과 개수가 다르면 멈춘다."""
    out = {}
    if not os.path.exists(path):
        return out
    with open(path, "rb") as fb:
        data = fb.read()
    if data and not data.endswith(b"\n"):
        raise FrozenError(f"{os.path.basename(path)}: 마지막 줄이 잘려 있다")
    text = data.decode("utf-8")
    rows = list(csv.reader(io.StringIO(text, newline="")))
    if not rows:
        return out
    if rows[0] != HEADER:
        raise FrozenError(f"{os.path.basename(path)}: 머리줄이 다르다")
    totals = {}
    for r in rows[1:]:
        if len(r) != len(HEADER):
            raise FrozenError(f"{os.path.basename(path)}: 칸 수가 다른 줄이 있다")
        q, page, seq, called, total = r[:5]
        if not total.isdigit():
            raise FrozenError(f"{os.path.basename(path)}: 결과 수 꼴")
        if q in totals and totals[q] != int(total):
            raise FrozenError(f"{os.path.basename(path)}: 한 검색어의 결과 수가 줄마다 다르다")
        totals[q] = int(total)
        lst = out.setdefault(q, [])
        if seq == "":
            if any(r[5:]):
                raise FrozenError(f"{os.path.basename(path)}: 0건 줄에 값이 있다")
            continue
        bad = bad_fields(r[5:])
        if bad:
            raise FrozenError(f"{os.path.basename(path)}: 결과 칸의 꼴이 다른 줄이 있다: {', '.join(bad)}")
        lst.append(dict(zip(FIELDS, r[5:])))
    for q, n in totals.items():
        if len(out[q]) != n:
            raise FrozenError(f"{os.path.basename(path)}: 결과 수와 적힌 결과 개수가 다른 검색어가 있다")
    return out


def http_fetch(params, timeout=30):
    """요청 하나. (상태, 본문 bytes). 키가 든 주소는 밖으로 내지 않는다."""
    url = URL + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": "zipinfo-example-picker"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.status, r.read()


class Juso:
    """mode: 'live' 또는 'replay'. fetch는 시험에서 갈아 끼운다(params → (상태, 본문))."""

    def __init__(self, path, mode, key=None, fetch=http_fetch, sleep=0.2, clock=now_iso):
        if mode not in ("live", "replay"):
            raise ValueError("mode는 live 또는 replay")
        if mode == "live" and not key:
            raise JusoError(f"부르기 모드에는 환경 변수 {KEY_ENV}가 있어야 한다")
        self.path, self.mode, self.key = path, mode, key
        self.fetch, self.sleep, self.clock = fetch, sleep, clock
        self.frozen = load(path)
        self.calls = 0  # 이번 실행에서 부른 요청(쪽) 수
        self.queries_new = 0

    def _call(self, query, page):
        params = dict(FIXED, confmKey=self.key, currentPage=str(page), keyword=query)
        for wait in (2, 5, None):
            try:
                status, body = self.fetch(params)
                self.calls += 1
                break
            except urllib.error.HTTPError as e:
                self.calls += 1
                raise JusoError(f"도로명주소 HTTP {e.code}") from None
            except RETRY_ERRORS as e:
                self.calls += 1
                if wait is None:
                    raise JusoError(f"도로명주소 통신 오류: {type(e).__name__}: {e}") from None
                time.sleep(wait)
        if status != 200:
            raise JusoError(f"도로명주소 HTTP {status}")
        try:
            doc = json.loads(body.decode("utf-8"))
            res = doc["results"]
            common = res["common"]
            if not isinstance(res, dict) or not isinstance(common, dict):
                raise TypeError
        except (ValueError, KeyError, TypeError):
            raise JusoError("도로명주소 응답 꼴이 다르다") from None
        code = str(common.get("errorCode", ""))
        if code != "0":
            raise JusoError(f"도로명주소 오류 {code}: {common.get('errorMessage', '')}")
        total = str(common.get("totalCount", ""))
        if not total.isdigit():
            raise JusoError("도로명주소 totalCount 꼴")
        items = res.get("juso") or []
        if not isinstance(items, list):
            raise JusoError("도로명주소 응답 꼴이 다르다")
        out = []
        for i, it in enumerate(items, 1):
            if not isinstance(it, dict):
                raise JusoError("도로명주소 응답 꼴이 다르다")
            vals = [it.get(k) for k in FIELDS]
            bad = bad_fields(vals)
            if bad:
                # 얼리기 전에 멈춘다. 이 검색어는 얼리지 않으니 고친 뒤 다시 부르면 된다.
                raise JusoError(f"도로명주소 결과 칸의 꼴이 다르다({page}쪽 {i}번째 결과): {', '.join(bad)}")
            out.append(vals)
        return int(total), out

    def lookup(self, query):
        """검색어 하나의 결과 전부(dict 목록). 판정은 이 목록으로만 한다."""
        if query in self.frozen:
            return self.frozen[query]
        if self.mode == "replay":
            raise FrozenError("재생 모드인데 얼린 파일에 없는 검색어가 나왔다")
        rows, page, total = [], 1, None
        while True:
            called = self.clock()
            t, items = self._call(query, page)
            if total is None:
                total = t
            elif t != total:
                raise JusoError("도로명주소 쪽마다 결과 수가 다르다")
            for i, vals in enumerate(items):
                rows.append([query, str(page), str(len(rows) + 1), called, str(total)] + vals)
            if len(rows) >= total or not items:
                break
            page += 1
            time.sleep(self.sleep)
        if len(rows) != total:
            raise JusoError("도로명주소 결과 수와 받은 결과 개수가 다르다")
        if total == 0:
            rows = [[query, "1", "", called, "0"] + [""] * len(FIELDS)]
        new = not os.path.exists(self.path) or os.path.getsize(self.path) == 0
        os.makedirs(os.path.dirname(self.path) or ".", exist_ok=True)
        with open(self.path, "a", encoding="utf-8", newline="") as f:
            w = csv.writer(f, lineterminator="\n")
            if new:
                w.writerow(HEADER)
            for r in rows:
                w.writerow(r)
            f.flush()
            os.fsync(f.fileno())
        self.queries_new += 1
        time.sleep(self.sleep)
        # 판정은 쓴 줄을 다시 읽어서만 한다
        self.frozen = load(self.path)
        return self.frozen[query]


def _int(s):
    s = s.strip()
    return int(s) if s.isdigit() else None


def exact(results, admcds, mtn, bon, bu):
    """정확히 맞은 결과(admCd가 admcds 가운데 하나, 산 여부·본번·부번이 같음). bdMgtSn 순."""
    hits = []
    for r in results:
        if r["admCd"] in admcds and _int(r["mtYn"]) == mtn and _int(r["lnbrMnnm"]) == bon and _int(r["lnbrSlno"]) == bu:
            hits.append(r)
    for r in hits:
        if not r["bdMgtSn"].isdigit():
            raise JusoError("bdMgtSn이 숫자가 아니다")
    return sorted(hits, key=lambda r: (len(r["bdMgtSn"]), r["bdMgtSn"]))


def find(juso, full_query, short_query, admcds, mtn, bon, bu):
    """7절 찾는 순서: 검색어로 찾고, 정확히 맞는 결과가 없으면 '법정동 이름 + 지번'으로 한 번 더."""
    hits = exact(juso.lookup(full_query), admcds, mtn, bon, bu)
    if hits:
        return hits, full_query
    hits = exact(juso.lookup(short_query), admcds, mtn, bon, bu)
    return hits, (short_query if hits else None)
