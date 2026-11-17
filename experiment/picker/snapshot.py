# -*- coding: utf-8 -*-
"""국토부 스냅샷 zip을 풀지 않고 읽는다(규칙 v1 1절, 계획 3절 '읽기 확인').

zip 안 꼴(04 수집기): <top>/raw/<api>/<code>/<YYYYMM>_p<쪽>.xml.gz, 다 받은 묶음마다 <YYYYMM>.done(받은 수 한 줄).
"""
import gzip
import re
import xml.etree.ElementTree as ET
import zipfile

from .common import code_sorted

APIS = ("apt_trade", "apt_rent", "rh_trade", "rh_rent", "offi_trade", "offi_rent")
OK_CODES = ("00", "000")
_PAGE = re.compile(r"raw/([a-z_]+)/(\d{5})/(\d{6})_p(\d+)\.xml\.gz")
_DONE = re.compile(r"raw/([a-z_]+)/(\d{5})/(\d{6})\.done")


def read_codes(path):
    """codes_snapshot.txt를 수집기와 같이 읽는다(utf-8-sig, '#' 뒤는 주석, 쉼표 앞만)."""
    codes, seen = [], set()
    with open(path, "r", encoding="utf-8-sig") as f:
        for line in f:
            line = line.split("#", 1)[0].strip()
            if not line:
                continue
            c = line.split(",")[0].strip()
            if not re.fullmatch(r"\d{5}", c):
                raise ValueError(f"지역 코드 꼴이 아니다: {c!r}")
            if c not in seen:
                seen.add(c)
                codes.append(c)
    return codes


def month_list(frm="202311", to="202610"):
    y, m = int(frm[:4]), int(frm[4:])
    ey, em = int(to[:4]), int(to[4:])
    out = []
    while (y, m) <= (ey, em):
        out.append(f"{y:04d}{m:02d}")
        m += 1
        if m == 13:
            y, m = y + 1, 1
    return out


class Snapshot:
    """zip 하나. 묶음 = (api, code, ym)."""

    def __init__(self, zip_path):
        self.zf = zipfile.ZipFile(zip_path)
        names = self.zf.namelist()
        tops = {n.split("/", 1)[0] for n in names}
        if len(tops) != 1:
            raise ValueError(f"zip 맨 위 폴더가 하나가 아니다: {code_sorted(tops)}")
        self.top = tops.pop()
        self.pages = {}  # (api, code, ym) -> {쪽: zip 안 이름}
        self.done = {}  # (api, code, ym) -> zip 안 이름
        self.other_raw = []  # raw/ 아래에서 두 꼴 밖의 파일
        pre = self.top + "/"
        for n in names:
            if not n.startswith(pre) or n.endswith("/"):
                continue
            inner = n[len(pre):]
            if not inner.startswith("raw/"):
                continue
            m = _PAGE.fullmatch(inner)
            if m:
                api, code, ym, p = m.group(1), m.group(2), m.group(3), int(m.group(4))
                self.pages.setdefault((api, code, ym), {})[p] = n
                continue
            m = _DONE.fullmatch(inner)
            if m:
                self.done[(m.group(1), m.group(2), m.group(3))] = n
                continue
            self.other_raw.append(inner)

    def close(self):
        self.zf.close()

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.close()

    def read_page(self, name):
        """(totalCount, 항목 목록). 항목은 칸 이름 → 글자(없으면 빈 문자열, 앞뒤 공백 그대로)."""
        root = ET.fromstring(gzip.decompress(self.zf.read(name)))
        if root.tag != "response":
            raise ValueError(f"{name}: response가 아니다")
        rc = (root.findtext("./header/resultCode") or "").strip()
        if rc not in OK_CODES:
            raise ValueError(f"{name}: resultCode {rc!r}")
        total = (root.findtext("./body/totalCount") or "").strip()
        if not total.isdigit():
            raise ValueError(f"{name}: totalCount {total!r}")
        items = []
        for it in root.findall("./body/items/item"):
            items.append({c.tag: (c.text or "") for c in it})
        return int(total), items

    def done_count(self, key):
        # 수집기는 글 모드로 썼다. Windows에서 받아 줄끝이 CRLF다.
        text = self.zf.read(self.done[key]).decode("utf-8")
        if not re.fullmatch(r"\d+\r?\n", text):
            raise ValueError(f"{self.done[key]}: .done 꼴 {text!r}")
        return int(text.strip())

    def check(self, codes, months, apis=APIS):
        """읽기 확인. (요약 dict, 문제 목록). 문제 목록이 비어야 한다."""
        want = [(a, c, ym) for a in apis for c in codes for ym in months]
        wanted = set(want)
        problems = []
        n_pages = n_items = 0
        for key in want:
            if key not in self.done:
                problems.append(("완료 표시 없음", key))
            pages = self.pages.get(key)
            if not pages:
                problems.append(("응답 없음", key))
                continue
            nums = sorted(pages)
            if nums != list(range(1, len(nums) + 1)):
                problems.append(("쪽 번호가 이어지지 않음", key))
            totals, got = set(), 0
            for p in nums:
                total, items = self.read_page(pages[p])
                totals.add(total)
                got += len(items)
                n_pages += 1
            n_items += got
            if len(totals) != 1:
                problems.append(("쪽마다 totalCount가 다름", key))
            elif got != totals.pop():
                problems.append(("읽은 거래 수의 합이 totalCount와 다름", key))
            if key in self.done and self.done_count(key) != got:
                problems.append((".done의 수와 다름", key))
        extra_pages = sorted(k for k in self.pages if k not in wanted)
        extra_done = sorted(k for k in self.done if k not in wanted)
        summary = {
            "묶음(기대)": len(want),
            "묶음(응답 있음)": sum(1 for k in want if k in self.pages),
            "묶음(완료 표시)": sum(1 for k in want if k in self.done),
            "응답 쪽": n_pages,
            "거래": n_items,
            "묶음 밖 응답 묶음": len(extra_pages),
            "묶음 밖 완료 표시": len(extra_done),
            "raw 아래 다른 파일": len(self.other_raw),
        }
        return summary, problems

    def items(self, key):
        """묶음의 거래를 쪽 순으로 (쪽, 항목) 차례로 낸다."""
        pages = self.pages.get(key, {})
        for p in sorted(pages):
            _, items = self.read_page(pages[p])
            for it in items:
                yield p, it
