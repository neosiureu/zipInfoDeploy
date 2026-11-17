# -*- coding: utf-8 -*-
"""규칙 v1 2절(시드, 해시, 가중 고르기, 반올림)과 파일 쓰기, 화면·기록 글 고치기.

표준 라이브러리만 쓴다. 부동소수, round(), random은 쓰지 않는다(2절).
"""
import csv
import hashlib
import math
import os
import re
import tempfile
import unicodedata
from datetime import datetime, timedelta, timezone
from fractions import Fraction

SEED = "zipinfo-example-v1"
RULE = "example_listing_rule_v1"
KST = timezone(timedelta(hours=9))
TWO64 = 1 << 64


# ---------------------------------------------------------------- 2절

def norm(s):
    """NFC로 맞추고 앞뒤 공백을 지운다."""
    return unicodedata.normalize("NFC", s).strip()


def H(purpose, key):
    """H(목적, 열쇠) = SHA-256(SEED|목적|열쇠). 문자열마다 NFC·앞뒤 공백 지우기를 한 뒤 잇는다."""
    data = norm(SEED) + "|" + norm(purpose) + "|" + norm(key)
    return hashlib.sha256(data.encode("utf-8")).hexdigest()


def u64(h):
    """16진 앞 16자(8바이트)를 부호 없는 정수로 읽는다. 0 <= n < 2^64."""
    if not re.fullmatch(r"[0-9a-f]{64}", h):
        raise ValueError("해시는 64자 소문자 16진이어야 한다")
    return int(h[:16], 16)


def pick_weighted(counts, n):
    """정해진 순서의 건수 c1..ck에서 n x T < C_j x 2^64를 만족하는 첫 j(0부터)를 낸다."""
    if not 0 <= n < TWO64:
        raise ValueError("n은 0 이상 2^64 미만")
    if any((not isinstance(c, int)) or c <= 0 for c in counts):
        raise ValueError("건수는 양의 정수")
    total = sum(counts)
    if total <= 0:
        raise ValueError("건수 합이 0")
    acc = 0
    for j, c in enumerate(counts):
        acc += c
        if n * total < acc * TWO64:
            return j
    raise AssertionError("닿지 않는 곳")


def round_unit(x, unit):
    """단위 U로 반올림: floor(x / U + 1/2) x U. x는 Fraction이나 int, U는 양의 정수(0.5는 올림)."""
    if not isinstance(x, (int, Fraction)) or isinstance(x, bool):
        raise TypeError("x는 int나 Fraction")
    if not isinstance(unit, int) or unit <= 0:
        raise ValueError("unit은 양의 정수")
    return math.floor(Fraction(x) / unit + Fraction(1, 2)) * unit


def code_sorted(items, key=None):
    """유니코드 코드 값 순(NFC로 맞춘 뒤)으로 정렬한다. 로캘 정렬은 쓰지 않는다."""
    if key is None:
        return sorted(items, key=lambda s: unicodedata.normalize("NFC", s))
    return sorted(items, key=lambda it: unicodedata.normalize("NFC", key(it)))


# ---------------------------------------------------------------- 파일

def ensure_dir(path):
    d = os.path.dirname(path)
    if d:
        os.makedirs(d, exist_ok=True)


def write_csv(path, header, rows):
    """UTF-8(BOM 없음), 줄끝 LF로 쓴다."""
    ensure_dir(path)
    with open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(header)
        for r in rows:
            w.writerow(r)


def read_csv(path):
    """(header, rows). UTF-8로 읽는다."""
    with open(path, "r", encoding="utf-8", newline="") as f:
        rd = csv.reader(f)
        rows = list(rd)
    if not rows:
        raise ValueError(f"빈 csv: {path}")
    return rows[0], rows[1:]


def write_text(path, text):
    """UTF-8(BOM 없음), 줄끝 LF로 쓴다."""
    ensure_dir(path)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest().upper()


def now_iso():
    """+09:00을 붙인 ISO 시각(초까지)."""
    return datetime.now(KST).isoformat(timespec="seconds")


def rel(path):
    """출력에 적는 경로: '/'로 쓴 상대 경로. 작업 폴더 밖(위로 올라가거나 다른 드라이브)이면 파일 이름만."""
    try:
        r = os.path.relpath(path)
    except ValueError:  # Windows에서 드라이브가 다르면
        return os.path.basename(path)
    r = r.replace(os.sep, "/")
    if r == ".." or r.startswith("../"):
        return os.path.basename(path)
    return r


# ---------------------------------------------------------------- 화면·기록 글 고치기(계획 4절)

def _path_forms(p):
    p = p.rstrip("\\/")
    if not p:
        return set()
    return {p, p.replace("\\", "\\\\"), p.replace("\\", "/")}


class Scrubber:
    """작업 폴더는 '.', 홈 폴더는 '~', 임시 폴더는 '<TEMP>', confmKey 값은 '***'로 바꾼다.

    경로마다 그대로·repr 꼴(역슬래시 둘)·'/' 꼴 셋을 바꾸고, 긴 문자열부터 바꾼다.
    대소문자는 가리지 않는다(Windows).
    임시 폴더는 tempfile.gettempdir()의 글자 그대로다. Windows의 TEMP가 짧은 이름(8.3, 예: ABCDEF~1)이면
    홈 폴더의 긴 이름과 글자가 달라 홈 규칙에 걸리지 않으므로 따로 바꾼다(⑤ 검토).
    """

    def __init__(self, cwd=None, home=None, secrets=(), temp=None):
        cwd = os.getcwd() if cwd is None else cwd
        home = os.path.expanduser("~") if home is None else home
        temp = tempfile.gettempdir() if temp is None else temp
        table = {}
        for p, rep in ((cwd, "."), (home, "~"), (temp, "<TEMP>")):
            for form in _path_forms(p):
                # 같은 글자의 경로가 둘이면(작업 폴더 = 홈) 먼저 것을 둔다
                table.setdefault(form.lower(), rep)
        self.table = table
        forms = sorted(table, key=lambda s: (-len(s), s))
        # 경로는 이름이 이어지는 글자(영문·숫자·_~-) 앞에서는 바꾸지 않는다('/tmp'가 '/tmpab12'의 앞을 바꾸지 않게)
        self.pat = (re.compile("(?:" + "|".join(re.escape(f) for f in forms) + ")(?![A-Za-z0-9_~-])", re.I)
                    if forms else None)
        self.secrets = sorted({s for s in secrets if s}, key=lambda s: (-len(s), s))
        self.sec_pat = re.compile("|".join(re.escape(s) for s in self.secrets)) if self.secrets else None

    def __call__(self, text):
        text = str(text)
        if self.sec_pat is not None:  # 키는 어디에 붙어 있든 바꾼다
            text = self.sec_pat.sub("***", text)
        if self.pat is not None:
            text = self.pat.sub(lambda m: self.table[m.group(0).lower()], text)
        return re.sub(r"(confmKey=)[^&\s'\"]+", r"\1***", text)
