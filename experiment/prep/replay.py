# -*- coding: utf-8 -*-
"""순서 2-1(만들기 설계안 5.1): 거래 재생 저장소.

snapshot_T.zip을 picker(3단계)와 같은 차례로 읽고, 06 규칙 3.1대로 다듬은 거래를
(API, 응답 코드, 달)마다 받은 차례 그대로 적는다. 다듬기에서 빠진 거래는 적지 않는다.
그래서 도구와 정답이 같은 거래를 본다.

같은 입력이면 Windows와 Linux에서 같은 바이트가 나온다: 줄 차례는 받은 차례(API, 코드, 달, 쪽, 쪽 안 차례),
글은 UTF-8(BOM 없음)·LF, 숫자는 정수 글, 면적은 받은 소수 글 그대로다.

ym 칸은 받은 달이다. 계약 달(dealYear·dealMonth)이 받은 달과 다른 거래가 나오면 멈춘다.
그래서 ym이 곧 계약 달이다.
"""
from fractions import Fraction

from picker import juso, snapshot
from picker.clean import TRADE_APIS, Cleaner, Drop, Pending, Rec, cid, fields
from picker.cli import resolve_pending
from picker.common import code_sorted, write_csv

TRADE_HEADER = ["api", "resp", "ym", "page", "pos", "day", "deal", "complex_id", "cur10", "path", "jibun", "name",
                "area", "floor", "build_year", "amount", "deposit", "monthly_rent", "raw_umd"]
BUNDLE_HEADER = ["api", "resp", "ym", "items", "kept", "dropped", "unresolved"]


class ReplayError(RuntimeError):
    pass


def _blank(v):
    return "" if v is None else v


def _int_or_none(s):
    return None if s == "" else int(s)


def trade_row(r, f, pos):
    """다듬은 거래 하나를 저장소 한 줄로. f는 받은 칸(NFC·앞뒤 공백 지움)이다. pos는 쪽 안 차례(0부터)."""
    if r.y * 100 + r.m != int(r.ym):
        raise ReplayError("계약 달이 받은 달과 다른 거래가 있다")
    if r.api in TRADE_APIS:
        amount, deposit, monthly = r.amt[0], "", ""
    else:
        amount, deposit, monthly = "", r.amt[0], r.amt[1]
    return [r.api, r.resp, r.ym, r.page, pos, r.d, r.deal, cid(r), r.cur10, r.path,
            juso.jibun_text(r.mtn, r.bon, r.bu), r.name, f.get("excluUseAr", ""), _blank(r.floor),
            _blank(r.build), amount, deposit, monthly, r.raw_umd]


def rec_of(row):
    """저장소 한 줄(글)을 다시 Rec으로. 저장소에 없는 칸(aptSeq, 옛 이름 표시, 리 나눔 표시)은 빈 값이다.
    picker model.Data가 단지 칸(이름·건축년도·층·거래)을 채우는 데 쓰는 칸은 모두 돌아온다."""
    kind, sgg, path, mtn, bon, bu = row[7].split("|")
    ym = row[2]
    amt = (int(row[15]),) if row[15] != "" else (int(row[16]), int(row[17]))
    return Rec(row[0], kind, row[6], row[1], ym, int(row[3]), row[8], sgg, path, int(mtn), int(bon), int(bu),
               Fraction(row[12]), _int_or_none(row[13]), _int_or_none(row[14]), int(ym[:4]), int(ym[4:]),
               int(row[5]), amt, row[11], "", row[18], False, False)


def build(snap, hist, codes, months, J):
    """(거래 줄 목록, 묶음 줄 목록)"""
    cl = Cleaner(hist)
    trades, bundles = [], []
    for api in code_sorted(snapshot.APIS):
        for code in codes:
            for ym in months:
                items = kept = dropped = unresolved = 0
                pos_of = {}
                for page, item in snap.items((api, code, ym)):
                    items += 1
                    pos = pos_of.get(page, 0)
                    pos_of[page] = pos + 1
                    res = cl.clean(api, code, ym, page, item)
                    if isinstance(res, Drop):
                        dropped += 1
                        continue
                    if isinstance(res, Pending):
                        rec, _gu = resolve_pending(res, hist, J, cl)
                        if rec is None:
                            unresolved += 1
                            continue
                        res = rec
                    kept += 1
                    trades.append(trade_row(res, fields(item), pos))
                bundles.append([api, code, ym, items, kept, dropped, unresolved])
    return trades, bundles


def write(trades, bundles, trade_path, bundle_path):
    write_csv(trade_path, TRADE_HEADER, trades)
    write_csv(bundle_path, BUNDLE_HEADER, bundles)
