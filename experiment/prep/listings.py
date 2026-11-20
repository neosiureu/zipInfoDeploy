# -*- coding: utf-8 -*-
"""순서 2-3(만들기 설계안 5.4)과 2-4의 검색어(5.3).

- 매물 입력: 얼린 매물 파일에서 06 규칙 6.1의 아홉 칸만 읽는다. 층(layer), 연습·본시험, 단지ID 같은 칸은 넣지 않는다.
  currentFloor(층수)는 아홉 칸이라 넣는다. 매물 키는 파일의 줄 차례로 L01~L75다.
- 근거 ID: 매물.가격(stockSellPrice, 원), 매물.월세(stockFeeMonth, 원), 매물.면적(exclusiveArea, ㎡), 매물.층(currentFloor)
- 주소 검색어: stockAddress(`우편번호^^^도로명주소^^^`)에서 우편번호와 `^^^`를 떼고, 앞뒤·겹 빈칸을 정리하고 NFC로 맞춘 것
"""
import csv
import io
import json
import re
import unicodedata

from picker.common import ensure_dir, read_csv

NINE = ["stockName", "stockAddress", "stockForm", "stockType", "stockSellPrice", "stockFeeMonth",
        "exclusiveArea", "currentFloor", "useApprovalDate"]
EVIDENCE = [("매물.가격", "stockSellPrice"), ("매물.월세", "stockFeeMonth"), ("매물.면적", "exclusiveArea"),
            ("매물.층", "currentFloor")]
QUERY_HEADER = ["key", "query"]
_SPACES = re.compile(r"\s+")


class BadListing(ValueError):
    pass


def clean_query(text):
    """5.3 도구 쪽 다듬기와 같다: NFC, 앞뒤·겹 빈칸 정리."""
    return _SPACES.sub(" ", unicodedata.normalize("NFC", text)).strip()


def address_parts(address):
    """stockAddress를 (우편번호, 도로명주소 글 그대로)로 나눈다. 꼴이 다르면 멈춘다."""
    parts = address.split("^^^")
    if len(parts) != 3 or not re.fullmatch(r"[0-9]{5}", parts[0]) or parts[2] != "":
        raise BadListing("stockAddress가 '우편번호^^^도로명주소^^^' 꼴이 아니다")
    return parts[0], parts[1]


def road_query(address):
    q = clean_query(address_parts(address)[1])
    if not q:
        raise BadListing("도로명주소가 비었다")
    return q


def load(path):
    header, rows = read_csv(path)
    need = NINE + ["complex_id", "deal", "layer", "split"]
    missing = [h for h in need if h not in header]
    if missing:
        raise BadListing("매물 파일에 없는 칸: " + ", ".join(missing))
    idx = {h: i for i, h in enumerate(header)}
    out = []
    for n, r in enumerate(rows, 1):
        out.append(("L%02d" % n, {h: r[idx[h]] for h in header}))
    return out


def model_input(listings):
    items = []
    for key, x in listings:
        ev = {}
        for name, col in EVIDENCE:
            if not re.fullmatch(r"[0-9]+", x[col]):
                raise BadListing(f"{key}의 {col}이 정수 글이 아니다")
            ev[name] = int(x[col])
        items.append({"key": key, "fields": {h: x[h] for h in NINE}, "evidence": ev})
    return items


def queries(listings):
    return [[key, road_query(x["stockAddress"])] for key, x in listings]


def write_model_input(items, path):
    ensure_dir(path)
    text = json.dumps(items, ensure_ascii=False, indent=1, sort_keys=False) + "\n"
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def queries_bytes(rows):
    """검색어 파일의 바이트(UTF-8, LF). 파일로 쓸 때와 주소 얼리기 전 대 볼 때 같은 바이트를 쓴다."""
    buf = io.StringIO(newline="")
    w = csv.writer(buf, lineterminator="\n")
    w.writerow(QUERY_HEADER)
    for r in rows:
        w.writerow(r)
    return buf.getvalue().encode("utf-8")


def write_queries(rows, path):
    ensure_dir(path)
    with open(path, "wb") as f:
        f.write(queries_bytes(rows))
