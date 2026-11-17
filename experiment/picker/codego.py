# -*- coding: utf-8 -*-
"""code.go.kr 법정동 이력(규칙 v1 1절·7절): 받은 24쪽에서 이력 표를 뽑고, 그 표를 읽는다.

쪽은 서버가 표를 HTML에 담아 보낸 그대로다(11/10). 한 쪽 300행에 다 들어가 쪽 넘김이 없다.
뽑은 줄 수는 쪽의 '총 N건', 쪽 스크립트의 cnt, 코드 칸 수와 모두 같아야 한다.
"""
import os
import re

from .common import code_sorted, read_csv, write_csv

HISTORY_HEADER = ["code", "name", "created", "abolished", "status", "parent", "page"]

_ROW = re.compile(
    r'<td class="table_left">(\d{10})</td>\s*'
    r'<td class="table_center01">([^<]*)</td>\s*'
    r'<td class="table_center01_11"><center>([^<]*)</center></td>\s*'
    r'<td class="table_center01_11">([^<]*)</td>\s*'
    r'<td class="table_center01_11">([^<]*)</td>\s*'
    r'<td class="table_center01_11">([^<]*)</td>'
)
_CODE_CELL = re.compile(r'<td class="table_left">\d{10}</td>')
_TOTAL = re.compile(r'총 <strong><font color="FF9900">(\d+)</font></strong>건')
_CNT = re.compile(r"var cnt\s*=\s*(\d+);")
_SIDO = re.compile(r'var sidoCd = "(\d{2})";')
_SGG = re.compile(r'name="reqSggCd" value="([^"]*)"')
_UMD = re.compile(r'name="reqUmdCd" value="([^"]*)"')
_DISUSE = re.compile(r'name="disuseAt" value="(\w+)" checked')
_PAGING = re.compile(r"paging-item[^']*'>(\d+)<")


class PageError(ValueError):
    pass


def _cell(raw, what, page):
    v = raw.strip()
    if v in ("&nbsp", "&nbsp;"):
        v = ""
    if "&" in v or "<" in v or ">" in v:
        raise PageError(f"{page}: {what} 칸에 알 수 없는 글자: {v!r}")
    return v


def parse_page(page, text):
    """한 쪽에서 (줄 목록, 쪽 정보)를 낸다. 줄은 (code, name, created, abolished, status, parent)."""
    def one(rx, what):
        found = rx.findall(text)
        if len(found) != 1:
            raise PageError(f"{page}: {what}이 {len(found)}개")
        return found[0]

    sido = one(_SIDO, "시도 코드")
    sgg = one(_SGG, "시군구 코드")
    umd = one(_UMD, "읍면동 코드")
    disuse = one(_DISUSE, "폐지구분 선택")
    total = int(one(_TOTAL, "총 건수"))
    cnt = int(one(_CNT, "cnt"))
    paging = _PAGING.findall(text)
    if paging != ["1"]:
        raise PageError(f"{page}: 쪽 넘김이 있다 {paging}")
    if disuse != "ALL" or umd != "*":
        raise PageError(f"{page}: 조회 조건이 전체가 아니다 (폐지구분 {disuse}, 읍면동 {umd})")
    prefix = sido + ("" if sgg == "*" else sgg)
    if not re.fullmatch(r"\d{2}(\d{3})?", prefix):
        raise PageError(f"{page}: 조회 코드 꼴이 다르다 {prefix!r}")

    raw_rows = _ROW.findall(text)
    n_cells = len(_CODE_CELL.findall(text))
    if not (len(raw_rows) == n_cells == total == cnt):
        raise PageError(f"{page}: 줄 수가 맞지 않는다 (뽑은 줄 {len(raw_rows)}, 코드 칸 {n_cells}, 총 {total}, cnt {cnt})")
    rows = []
    for code, name, status, parent, created, abolished in raw_rows:
        name = _cell(name, "이름", page)
        status = _cell(status, "폐지구분", page)
        parent = _cell(parent, "상위지역코드", page)
        created = _cell(created, "생성일", page)
        abolished = _cell(abolished, "폐지일", page)
        if not code.startswith(prefix):
            raise PageError(f"{page}: 조회 코드 {prefix} 밖의 코드 {code}")
        if not name:
            raise PageError(f"{page}: {code} 이름이 비었다")
        if status not in ("현존", "폐지"):
            raise PageError(f"{page}: {code} 폐지구분 {status!r}")
        for what, v, rx in (("상위지역코드", parent, r"\d{10}"), ("생성일", created, r"\d{8}"), ("폐지일", abolished, r"\d{8}")):
            if v and not re.fullmatch(rx, v):
                raise PageError(f"{page}: {code} {what} 꼴 {v!r}")
        rows.append((code, name, created, abolished, status, parent))
    info = {"page": page, "prefix": prefix, "total": total, "rows": len(rows)}
    return rows, info


def build_history(pages_dir):
    """24쪽을 모두 읽어 (이력 줄 목록, 쪽 정보 목록)을 낸다. 줄은 코드 순."""
    names = code_sorted(n for n in os.listdir(pages_dir) if n.endswith(".html"))
    by_code, infos = {}, []
    for fn in names:
        page = fn[: -len(".html")]
        with open(os.path.join(pages_dir, fn), "r", encoding="utf-8", newline="") as f:
            text = f.read()
        rows, info = parse_page(page, text)
        infos.append(info)
        for code, name, created, abolished, status, parent in rows:
            rec = (name, created, abolished, status, parent)
            if code in by_code:
                if by_code[code][0] != rec:
                    raise PageError(f"{code}가 두 쪽에 다르게 나온다: {by_code[code][1]}, {page}")
                by_code[code][1].append(page)
            else:
                by_code[code] = (rec, [page])
    out = []
    for code in code_sorted(by_code):
        (name, created, abolished, status, parent), pages = by_code[code]
        out.append([code, name, created, abolished, status, parent, ";".join(pages)])
    return out, infos


def write_history(path, rows):
    write_csv(path, HISTORY_HEADER, rows)


def load_history_rows(path):
    header, rows = read_csv(path)
    if header != HISTORY_HEADER:
        raise ValueError(f"이력 표 머리가 다르다: {header}")
    return [dict(zip(header, r)) for r in rows]


# ---------------------------------------------------------------- 이력 읽기

WIN_START = "20231101"  # 받은 창 202311~202610의 첫날
WIN_END = "20261031"  # 받은 창의 끝날
RENAMES = {"사곳리": "사곶리", "오산동": "여울동"}  # 규칙 3.1이 적은 이름 바꾸기(옛 이름 → 현재 이름)


def _next_day(d):
    from datetime import date, timedelta
    y, m, dd = int(d[:4]), int(d[4:6]), int(d[6:])
    return (date(y, m, dd) + timedelta(days=1)).strftime("%Y%m%d")


def rename_path(path):
    """경로의 낱말마다 규칙의 이름 바꾸기를 건다."""
    return " ".join(RENAMES.get(t, t) for t in path.split(" "))


class History:
    """이력 표를 읽어 법정동 경로, 뒤 코드, 현재 코드, 후보 코드를 낸다.

    법정동 경로: 이름에서 그 시군구 행(앞 5자리 + 00000)의 이름을 뗀 나머지("중산동", "남양읍 내리").
    뒤 코드(같은 법정동의 다음 코드) y는 옛 코드 x에 대해 셋을 모두 채운다.
      (가) y의 생성일이 x의 폐지일과 같은 날이거나 다음 날이다(쪽마다 폐지일을 적는 날이 하루 다르다:
           화성 20260131 폐지·20260201 생성, 광주·인천·구례 20260701 폐지·20260701 생성).
      (나) y의 경로가 x의 경로이거나, x의 경로에 규칙의 이름 바꾸기를 건 것이다.
      (다) 두 코드의 시군구가 같거나, 두 시군구가 '짝이 하나뿐인 법정동'을 하나 이상 같이 가진다.
           짝이 하나뿐인 법정동: (가)·(나)를 채우는 y가 x에 하나뿐이고, 그 y를 (가)·(나)로 고르는 x도 하나뿐인 것.
    """

    def __init__(self, rows, win_start=WIN_START, win_end=WIN_END):
        self.win_start, self.win_end = win_start, win_end
        self.rows = {r["code"]: r for r in rows}
        self._path = {}
        for code, r in self.rows.items():
            if code[5:] == "00000":
                continue
            sgg = self.rows.get(code[:5] + "00000")
            if sgg is None or not r["name"].startswith(sgg["name"] + " "):
                raise ValueError(f"{code}: 시군구 행이 없거나 이름이 시군구 이름으로 시작하지 않는다")
            self._path[code] = r["name"][len(sgg["name"]) + 1:]
        self._build_links()
        self._cand = {}
        self._upper = {}

    # 기본
    def in_window(self, code):
        r = self.rows[code]
        return (r["created"] == "" or r["created"] <= self.win_end) and (r["abolished"] == "" or r["abolished"] > self.win_start)

    def path(self, code):
        return self._path[code]

    def is_current(self, code):
        return self.rows[code]["status"] == "현존"

    def dong_codes(self, sgg5=None):
        """법정동 행(시군구 행 빼고) 가운데 창 안에 있던 것."""
        return [c for c in sorted(self._path) if (sgg5 is None or c[:5] == sgg5) and self.in_window(c)]

    def sgg_abolished(self, sgg5):
        r = self.rows.get(sgg5 + "00000")
        return r is not None and r["abolished"] != ""

    def sgg_upper_only(self, sgg5):
        """폐지되지 않은 상위 코드: 시군구 행은 현존인데 그 아래 현존 법정동 행이 없다(41590)."""
        if sgg5 not in self._upper:
            r = self.rows.get(sgg5 + "00000")
            if r is None or r["abolished"] != "":
                self._upper[sgg5] = False
            else:
                self._upper[sgg5] = not any(self.is_current(c) for c in self._path if c[:5] == sgg5)
        return self._upper[sgg5]

    # 잇기
    def _build_links(self):
        olds = [c for c in self.dong_codes() if self.rows[c]["abolished"] != ""]
        news = self.dong_codes()
        cand = {}
        inv = {}
        for x in olds:
            ab = self.rows[x]["abolished"]
            days = {ab, _next_day(ab)}
            want = {self._path[x], rename_path(self._path[x])}
            ys = [y for y in news if y != x and self.rows[y]["created"] in days and self._path[y] in want]
            cand[x] = ys
            for y in ys:
                inv.setdefault(y, []).append(x)
        links = set()
        for x, ys in cand.items():
            if len(ys) == 1 and len(inv[ys[0]]) == 1 and x[:5] != ys[0][:5]:
                links.add((x[:5], ys[0][:5]))
        self.links = links
        self._next = {}
        for x, ys in cand.items():
            self._next[x] = [y for y in ys if y[:5] == x[:5] or (x[:5], y[:5]) in links]
        self._prev = {}
        for x, ys in self._next.items():
            for y in ys:
                self._prev.setdefault(y, []).append(x)

    def next_codes(self, code):
        return list(self._next.get(code, []))

    def current_of(self, code):
        """그 법정동의 현재 10자리 코드들(능동처럼 둘로 갈라지면 둘). 이어지지 않으면 빈 목록."""
        out, stack, seen = set(), [code], set()
        while stack:
            c = stack.pop()
            if c in seen:
                continue
            seen.add(c)
            if self.is_current(c):
                out.add(c)
            for y in self._next.get(c, []):
                stack.append(y)
        return sorted(out)

    def before_codes(self, code):
        """그 법정동의 앞 코드 전부(자기 포함, 창 안에 있던 것만)."""
        out, stack = set(), [code]
        while stack:
            c = stack.pop()
            if c in out:
                continue
            out.add(c)
            stack.extend(self._prev.get(c, []))
        return sorted(c for c in out if self.in_window(c))

    def candidates(self, sgg5):
        """3.3 후보 코드: 영역(현재 시군구의 현재 법정동들)이 창 안에서 속했던 5자리 코드."""
        if sgg5 not in self._cand:
            out = {sgg5}
            for c in self.dong_codes(sgg5):
                if self.is_current(c):
                    out.update(b[:5] for b in self.before_codes(c))
            self._cand[sgg5] = sorted(out)
        return list(self._cand[sgg5])
