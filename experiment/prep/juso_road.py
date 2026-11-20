# -*- coding: utf-8 -*-
"""순서 2-4(만들기 설계안 5.3): 매물 주소 칸으로 도로명주소 API를 한 번씩 불러 얼린다.

얼리기 꼴은 3단계 picker/juso.py와 같다(검색어, 쪽, 차례, 부른 시각, 결과 수, 칸). 요청 값(FIXED), 주소, 키 환경 변수,
다시 부르기도 그 모듈의 것을 그대로 쓴다. 다른 것은 얼리는 칸뿐이다: 에이전트에게 보일 칸
(admCd, 우편번호, 도로명주소, 시도·시군구·읍면동·리 이름, 산·본번·부번, 건물관리번호)을 둔다.

- live: 얼린 파일에 없는 검색어만 부른다. 한 검색어의 쪽을 다 받은 뒤 그 줄들을 한 번에 쓰고 flush 한다.
- replay: 얼린 파일만 쓴다. 없는 검색어는 멈춘다.
- 키는 환경 변수에서만 읽고, 어디에도 쓰거나 찍지 않는다.
"""
import csv
import io
import json
import os
import re
import time
import urllib.error

from picker.common import now_iso
from picker.juso import FIXED, KEY_ENV, RETRY_ERRORS, FrozenError, JusoError, http_fetch

FIELDS = ["admCd", "zipNo", "roadAddrPart1", "siNm", "sggNm", "emdNm", "liNm", "mtYn", "lnbrMnnm", "lnbrSlno", "bdMgtSn"]
HEADER = ["query", "page", "seq", "called_at", "total"] + FIELDS
# 칸의 꼴. None은 "비어 있지 않은 글", "" 허용은 빈 값도 되는 칸(시군구가 없는 곳, 리가 없는 곳).
FORMS = {
    "admCd": re.compile(r"[0-9]{10}"),
    "zipNo": re.compile(r"[0-9]{5}"),
    "roadAddrPart1": None,
    "siNm": None,
    "sggNm": "",
    "emdNm": None,
    "liNm": "",
    "mtYn": re.compile(r"[01]"),
    "lnbrMnnm": re.compile(r"[0-9]+"),
    "lnbrSlno": re.compile(r"[0-9]+"),
    "bdMgtSn": re.compile(r"[0-9]+"),
}


def bad_fields(values):
    """꼴이 다른 칸 이름 목록(값은 내지 않는다)."""
    bad = []
    for name, v in zip(FIELDS, values):
        form = FORMS[name]
        if not isinstance(v, str):
            bad.append(name)
        elif form == "":
            continue
        elif form is None:
            if not v.strip():
                bad.append(name)
        elif not form.fullmatch(v):
            bad.append(name)
    return bad


def load(path):
    """{검색어: [결과 dict, ...]}. 결과 수와 적힌 결과 개수가 다르면 멈춘다."""
    out = {}
    if not os.path.exists(path):
        return out
    with open(path, "rb") as fb:
        data = fb.read()
    if data and not data.endswith(b"\n"):
        raise FrozenError(f"{os.path.basename(path)}: 마지막 줄이 잘려 있다")
    rows = list(csv.reader(io.StringIO(data.decode("utf-8"), newline="")))
    if not rows:
        return out
    if rows[0] != HEADER:
        raise FrozenError(f"{os.path.basename(path)}: 머리줄이 다르다")
    totals = {}
    for r in rows[1:]:
        if len(r) != len(HEADER):
            raise FrozenError(f"{os.path.basename(path)}: 칸 수가 다른 줄이 있다")
        q, _page, seq, _called, total = r[:5]
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


class RoadJuso:
    """mode: 'live' 또는 'replay'. fetch는 시험에서 갈아 끼운다(params → (상태, 본문))."""

    def __init__(self, path, mode, key=None, fetch=http_fetch, sleep=0.2, clock=now_iso):
        if mode not in ("live", "replay"):
            raise ValueError("mode는 live 또는 replay")
        if mode == "live" and not key:
            raise JusoError(f"부르기 모드에는 환경 변수 {KEY_ENV}가 있어야 한다")
        self.path, self.mode, self.key = path, mode, key
        self.fetch, self.sleep, self.clock = fetch, sleep, clock
        self.frozen = load(path)
        self.calls = 0
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
                raise JusoError(f"도로명주소 결과 칸의 꼴이 다르다({page}쪽 {i}번째 결과): {', '.join(bad)}")
            out.append(vals)
        return int(total), out

    def lookup(self, query):
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
            for vals in items:
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
        self.frozen = load(self.path)
        return self.frozen[query]
