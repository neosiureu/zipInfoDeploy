# -*- coding: utf-8 -*-
"""예시 매물 75개의 좌표를 SGIS 지오코딩 API로 받아 얼린다(4단계 계획 3절, 11/18 밤 상대와 정함).

행안부 좌표제공 API가 심사 중이라, 테스트키를 바로 받는 SGIS(국가데이터처 통계지리정보서비스)로 받는다.
주소는 SGIS 새 안내서(newOpenApi)의 sgisapi.mods.go.kr다(11/18 21:40 상대 검토).

- 매물마다 정해진 땅과 건물: example_listings_v1.csv의 stockAddress 둘째 토막이 juso_jibun_v1.csv의
  roadAddrPart1 하나와 정확히 같다(우편번호도 같다). 그 줄의 admCd(법정동 10자리)와 지번(산·본번·부번),
  도로명주소 끝의 도로명과 건물 본번·부번이 그 매물의 땅과 건물이다.
- 부르는 꼴(매물마다 이 차례로 부르고, 판정도 이 차례로 쓴다)
  - full: 도로명주소 그대로. nosido: 첫 낱말(시도)을 뺀 것.
  - old: 2026년 개편 지역(51줄)만, 앞토막을 옛 이름으로 바꾼 도로명주소(OLD_NAMES).
  - jibun: juso_query(지번 주소) 그대로. old_jibun: 51줄만, 앞토막을 옛 이름으로 바꾼 지번 주소.
  모두 75×3 + 51×2 = 327번(인증 따로)이다. SGIS 자료가 개편 전이든 뒤든 다시 부르지 않고 정할 수 있게 다 받아 둔다.
- 부르기(fetch, 사용자 PC)
  - 인증 API로 accessToken을 받는다. 인증 응답은 얼리지 않는다(토큰이 들어 있다). 인증 단계의 오류는 멈춘다.
  - 지오코딩 응답이 JSON이고 errCd가 숫자이면, 0이 아니어도(예: -100 결과 없음) 받은 바이트 그대로 얼리고
    다음으로 간다. 판정에서 그 꼴은 실패다. 다만 둘은 얼리지 않는다.
    - -401(토큰, 잠깐씩 몰려서 나는 오류): 2초, 5초 쉬고 다시 인증해 두 번 더 부른다. 세 번 다 -401이면 멈춘다.
    - -1(서버 처리 오류, 잠깐 나는 오류): 2초, 5초 쉬고 다시 부른다. 세 번 다 -1이면 멈춘다.
  - 통신 오류와 HTTP 412·429·5xx는 2초, 5초 쉬고 두 번 더 부른다. 그 밖의 HTTP 오류는 멈춘다.
  - 이미 얼린 응답은 다시 부르지 않는다. 멈춘 뒤 다시 돌리면 멈춘 곳부터 잇는다.
- 판정(fetch 끝과 replay, 얼린 파일만 읽음). 값이 "null"이라는 글자면 빈 값으로 본다.
  - 꼴 하나: errCd 0, totalcount가 받은 건수와 같고 50 이하, 아래를 모두 만족하는 줄이 정확히 하나.
    1) addr_type 6(지번+건물).
    2) 좌표: x·y(안내서 예시) 또는 X·Y(안내서 표)가 숫자 글자다. 둘 다 있으면 값이 같아야 한다.
    3) 법정동: 응답의 10자리 코드가 얼린 admCd와 같다. 10자리 코드는 leg_cd·ri_cd가 10자리면 그것,
       leg_cd가 3자리 꼴이면 sido_cd+sgg_cd+leg_cd+ri_cd(없으면 00)다.
       아니면(개편 전 자료) 시군구 이름이 새 이름이나 옛 이름이고, 읍면동·리 이름이 얼린 주소와 같다.
       그것도 아니고 시군구 이름이 새·옛 이름에 맞고 응답의 10자리 코드가 얼린 bdMgtSn 앞 10자리(건물을 처음
       등록할 때의 법정동)와 같으면 "다름"으로 두고, 5의 도로명이 반드시 맞아야 한다(11/19 r21, 코드 조건은 상대 10:20).
       SGIS의 그 건물 기록이 개편·지번 정리 전 것이라 법정동만 다른 경우다. 도로명·건물번호는 시군구 안에서 하나뿐이다.
    4) 응답에 지번 본번이 있으면 본번·부번이 얼린 지번과 같다(부번 없음은 "null"이나 "0").
       산 여부는 따로 칸이 없다(산 지번은 r24 하나). 실제 응답은 본번 칸에 "산"이 붙어 와서 숫자 꼴이 아니라 걸린다.
    5) 응답에 도로명이 있으면 도로명과 건물 본번·부번이 얼린 도로명주소와 같다.
    6) 4와 5 가운데 적어도 하나는 응답에 있다.
    matching은 안내서마다 뜻이 반대라 조건에서 빼고 표에 적기만 한다.
  - 줄 하나: 통과한 꼴이 하나라도 있고, 통과한 꼴끼리 모두 1km 안이면 통과다. 차례에서 처음 통과한 꼴을 쓴다.
    산 지번 줄(r24)은 판정이 산 여부를 보지 않아, 도로명까지 맞은 꼴만 쓴다(지번만 맞은 꼴은 쓰지 않는다, 11/18 23시 상대).
  - 한 줄이라도 안 되면 표를 쓰지 않고, 줄 번호와 걸린 조건 이름만 낸다(얼린 응답은 남아 나중에 정할 수 있다).
- 키(SGIS_KEY=서비스 ID, SGIS_SECRET=서비스 Secret)와 토큰은 환경 변수와 메모리에만 둔다.
  끝에 얼린 폴더의 모든 파일에 키·Secret·토큰 글자가 없는지 본다. 멈춤 글에는 주소 값을 쓰지 않는다.
  화면·기록 줄에는 서버가 준 오류 글이 들어갈 수 있어, 쓰고 찍기 전에 키·Secret·토큰을 <키>로 가리고 가린 것이 있으면 멈춘다.

사용(저장소 맨 위 폴더에서):
  python -X utf8 -B -I <이 파일> fetch  <example_listings_v1.csv> <juso_jibun_v1.csv> <얼릴 폴더>
  python -X utf8 -B -I <이 파일> replay <example_listings_v1.csv> <juso_jibun_v1.csv> <얼린 폴더>
  python -X utf8 -B -I <이 파일> rejudge <example_listings_v1.csv> <juso_jibun_v1.csv> <얼린 폴더> <내놓을 폴더>
  (rejudge: 판정 규칙을 바꾼 뒤, 얼린 폴더는 건드리지 않고 판정 표·좌표 표를 새 폴더에 쓴다. 부르지 않는다)
종료 코드: 0 끝, 1 멈춤(부르기 또는 판정), 2 명령 꼴이 틀림.
"""
import collections
import csv
import hashlib
import http.client
import io
import json
import math
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone

KST = timezone(timedelta(hours=9))
BASE = "https://sgisapi.mods.go.kr"
AUTH_PATH = "/OpenAPI3/auth/authentication.json"
GEOCODE_PATH = "/OpenAPI3/addr/geocode.json"
GEOCODE_FIXED = {"pagenum": "0", "resultcount": "50"}  # 50이 최대다. 후보를 다 받았는지 본다
KEY_ENV = "SGIS_KEY"
SECRET_ENV = "SGIS_SECRET"
TEST_BASE_ENV = "SGISCOORD_TEST_BASE"  # 시험에서만 쓴다. 있으면 크게 알린다.

PIN = {
    "example_listings_v1.csv": "FFB838C797E7EE4CEAFD15096701565E501B5392F5A290690FF2F3C4B8C1EBF4",
    "juso_jibun_v1.csv": "5F910E797795B69342D75390467964B6B0E6F7C2B4DE3D85B9BA0280B971FFDE",
}
JUSO_HEADER = ["query", "page", "seq", "called_at", "total", "admCd", "zipNo", "roadAddrPart1",
               "mtYn", "lnbrMnnm", "lnbrSlno", "bdMgtSn"]
N_ROWS = 75
FORMS_ORDER = ("full", "nosido", "old", "jibun", "old_jibun")
# 2026년 개편 지역의 옛 이름. 주소 앞토막만 바꾼다(11/18 21:40 상대 표). 앞에서부터 처음 맞는 줄을 쓴다.
OLD_NAMES = [
    (("경기도", "화성시", "만세구"), ("경기도", "화성시")),
    (("경기도", "화성시", "효행구"), ("경기도", "화성시")),
    (("경기도", "화성시", "병점구"), ("경기도", "화성시")),
    (("경기도", "화성시", "동탄구"), ("경기도", "화성시")),
    (("인천광역시", "서해구"), ("인천광역시", "서구")),
    (("인천광역시", "검단구"), ("인천광역시", "서구")),
    (("인천광역시", "제물포구"), ("인천광역시", "중구")),  # r36 도원동은 옛 중구다
    (("전남광주통합특별시", "구례군"), ("전라남도", "구례군")),
    # 그 밖은 광주광역시다. 우리 줄은 광주의 다섯 구뿐이라 다섯만 적는다(다른 시군이 오면 load_targets가 멈춘다).
    (("전남광주통합특별시", "동구"), ("광주광역시", "동구")),
    (("전남광주통합특별시", "서구"), ("광주광역시", "서구")),
    (("전남광주통합특별시", "남구"), ("광주광역시", "남구")),
    (("전남광주통합특별시", "북구"), ("광주광역시", "북구")),
    (("전남광주통합특별시", "광산구"), ("광주광역시", "광산구")),
]
NEW_ONLY = {"만세구", "효행구", "병점구", "동탄구", "서해구", "검단구", "제물포구", "영종구", "전남광주통합특별시"}
NUM = re.compile(r"-?[0-9]+(\.[0-9]+)?")
DIGITS = re.compile(r"[0-9]+")
CODE10 = re.compile(r"[0-9]{10}")
ERRCD = re.compile(r"-?[0-9]+")
MANIFEST = "calls_v1.csv"
MANIFEST_HEADER = ["row", "kind", "called_at", "attempts", "http_status", "bytes", "sha256"]
TABLE = "utmk_sgis_v1.csv"
TABLE_HEADER = ["row", "bdMgtSn", "form", "dong", "by", "addr_type", "matching", "X", "Y"]
REPORT = "judge_v1.csv"
REPORT_HEADER = ["row", *FORMS_ORDER, "used", "spread_m", "why"]
LOG = "run_log_v1.txt"
RETRY_ERRORS = (urllib.error.URLError, TimeoutError, OSError, http.client.HTTPException)
RETRY_WAITS = (2, 5)
SLEEP = 0.3
TOKEN_ERR = "-401"
SERVER_ERR = "-1"
MAX_SPREAD_M = 1000.0


class Stop(RuntimeError):
    """멈춤. 글에는 줄 번호와 칸·조건 이름만 쓴다."""


def now_iso():
    return datetime.now(KST).isoformat(timespec="seconds")


def sha256_bytes(b):
    return hashlib.sha256(b).hexdigest().upper()


def read_pinned(path):
    name = os.path.basename(path)
    want = PIN.get(name)
    if want is None:
        raise Stop(f"입력 파일 이름이 다르다: {name}")
    with open(path, "rb") as f:
        data = f.read()
    if sha256_bytes(data) != want:
        raise Stop(f"{name}의 SHA-256이 얼린 값과 다르다")
    return data.decode("utf-8")


def nospace(s):
    return None if s is None else re.sub(r"\s+", "", s)


def to_old(words):
    """앞토막을 옛 이름으로 바꾼 낱말 목록. 개편 지역이 아니면 None."""
    for new, old in OLD_NAMES:
        if tuple(words[:len(new)]) == new:
            return list(old) + words[len(new):]
    return None


def load_targets(listings_path, juso_path):
    """매물 75줄마다 dict. 부를 꼴(forms)과 판정에 쓸 얼린 값."""
    listings = list(csv.DictReader(io.StringIO(read_pinned(listings_path), newline="")))
    jrows = list(csv.reader(io.StringIO(read_pinned(juso_path), newline="")))
    if not jrows or jrows[0] != JUSO_HEADER:
        raise Stop("juso_jibun_v1.csv 머리줄이 다르다")
    juso = [dict(zip(JUSO_HEADER, r)) for r in jrows[1:]]
    if len(listings) != N_ROWS:
        raise Stop("예시 매물이 75줄이 아니다")
    out = []
    for i, r in enumerate(listings, 1):
        lab = f"r{i:02d}"
        parts = r["stockAddress"].split("^^^")
        if len(parts) != 3:
            raise Stop(f"{lab}: stockAddress가 세 토막이 아니다")
        zipc, road = parts[0], parts[1]
        hits = [j for j in juso if j["query"] == r["juso_query"] and j["roadAddrPart1"] == road]
        if len(hits) != 1:
            raise Stop(f"{lab}: 얼린 도로명주소에서 이 매물의 줄이 정확히 하나가 아니다({len(hits)})")
        h = hits[0]
        if h["zipNo"] != zipc:
            raise Stop(f"{lab}: 우편번호가 얼린 도로명주소와 다르다")
        if not (CODE10.fullmatch(h["admCd"]) and h["mtYn"] in ("0", "1")
                and DIGITS.fullmatch(h["lnbrMnnm"]) and DIGITS.fullmatch(h["lnbrSlno"])):
            raise Stop(f"{lab}: 얼린 admCd·지번의 꼴이 다르다")
        w = road.split(" ")
        m = re.fullmatch(r"([0-9]+)(?:-([0-9]+))?", w[-1]) if len(w) >= 3 else None
        if not m or not re.search(r"(로|길)$", w[-2]):
            raise Stop(f"{lab}: 도로명주소 끝이 '도로명 건물번호' 꼴이 아니다")
        prefix = w[:-2]
        q = r["juso_query"].split(" ")
        if q[:len(prefix)] != prefix or len(q) != len(prefix) + 2:
            raise Stop(f"{lab}: juso_query가 도로명주소의 앞토막 + 법정동 + 지번 꼴이 아니다")
        dong, jib = q[-2], q[-1]
        jm = re.fullmatch(r"(산)?([0-9]+)(?:-([0-9]+))?", jib)
        if not jm or (("1" if jm.group(1) else "0"), int(jm.group(2)), int(jm.group(3) or 0)) != (
                h["mtYn"], int(h["lnbrMnnm"]), int(h["lnbrSlno"])):
            raise Stop(f"{lab}: juso_query의 지번이 얼린 지번과 다르다")
        emd = prefix[-1] if len(prefix) >= 3 and re.search(r"(읍|면)$", prefix[-1]) else None
        if not re.search(r"리$" if emd else r"(동|가)$", dong):
            raise Stop(f"{lab}: 법정동 낱말의 꼴이 다르다")
        old_prefix = to_old(prefix)
        if old_prefix is None and NEW_ONLY & set(prefix):
            raise Stop(f"{lab}: 개편 지역인데 옛 이름 대응이 없다")
        forms = {"full": road, "nosido": " ".join(w[1:]), "jibun": r["juso_query"]}
        sgg_names = {nospace("".join(prefix[1:-1] if emd else prefix[1:]))}
        if old_prefix is not None:
            forms["old"] = " ".join(old_prefix + w[-2:])
            forms["old_jibun"] = " ".join(old_prefix + [dong, jib])
            sgg_names.add(nospace("".join(old_prefix[1:-1] if emd else old_prefix[1:])))
        if len(set(forms.values())) != len(forms):
            raise Stop(f"{lab}: 부를 꼴이 서로 다르지 않다")
        out.append({"row": i, "bdMgtSn": h["bdMgtSn"], "admCd": h["admCd"], "san": h["mtYn"] == "1",
                    "lnbrMnnm": int(h["lnbrMnnm"]), "lnbrSlno": int(h["lnbrSlno"]),
                    "road_name": w[-2], "bd_main": int(m.group(1)), "bd_sub": int(m.group(2) or 0),
                    "sgg_names": sgg_names, "leg_nm": emd or dong, "ri_nm": dong if emd else None,
                    "forms": {k: forms[k] for k in FORMS_ORDER if k in forms}})
    if len({t["bdMgtSn"] for t in out}) != N_ROWS:
        raise Stop("얼린 bdMgtSn 75개가 서로 다르지 않다")
    return out


def http_get(url, params, timeout=30):
    """요청 하나. (상태, 본문 bytes). 키·토큰이 든 주소는 밖으로 내지 않는다."""
    full = url + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(full, headers={"User-Agent": "zipinfo-example-coord"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.status, r.read()


def retryable_http(code):
    return code in (412, 429) or 500 <= code <= 599


def call(url, params, label, fetch, stats):
    """통신 오류와 HTTP 412·429·5xx는 2초, 5초 쉬고 두 번 더 부른다. 그 밖의 HTTP 오류는 멈춘다. 실패한 시도도 센다."""
    attempts = 0
    for wait in RETRY_WAITS + (None,):
        attempts += 1
        stats["attempts"] += 1
        try:
            status, body = fetch(url, params)
            return status, body, attempts
        except urllib.error.HTTPError as e:
            code = e.code
            try:
                e.close()
            except Exception:  # noqa: BLE001  닫기 실패는 상관없다
                pass
            if wait is None or not retryable_http(code):
                raise Stop(f"{label}: HTTP {code}(시도 {attempts}번)") from None
            time.sleep(wait)
        except RETRY_ERRORS as e:
            if wait is None:
                raise Stop(f"{label}: 통신 오류 {type(e).__name__}(시도 {attempts}번)") from None
            time.sleep(wait)


def parse(body, label):
    """API 수준만 본다. (errCd 글자, result dict, errMsg). 숫자도 글자 그대로 읽는다."""
    try:
        doc = json.loads(body.decode("utf-8"), parse_float=str, parse_int=str)
        if not isinstance(doc, dict):
            raise TypeError
    except (ValueError, TypeError):
        raise Stop(f"{label}: 응답 꼴이 다르다(JSON이 아니다)") from None
    code = doc.get("errCd")
    if not isinstance(code, str) or not ERRCD.fullmatch(code):
        raise Stop(f"{label}: 응답 꼴이 다르다(errCd가 없거나 숫자가 아니다)")
    result = doc.get("result")
    if code == "0" and not isinstance(result, dict):
        raise Stop(f"{label}: 응답 꼴이 다르다(result가 없다)")
    return code, result if isinstance(result, dict) else {}, str(doc.get("errMsg", ""))


def authenticate(base, keys, fetch, stats):
    stats["auth"] += 1
    status, body, _ = call(base + AUTH_PATH, {"consumer_key": keys[KEY_ENV], "consumer_secret": keys[SECRET_ENV]},
                           "인증", fetch, stats)
    if status != 200:
        raise Stop(f"인증: HTTP {status}")
    code, result, msg = parse(body, "인증")
    if code != "0":
        raise Stop(f"인증: API 오류 {code}: {msg}")
    token = result.get("accessToken")
    if not isinstance(token, str) or not token.strip():
        raise Stop("인증: accessToken이 없다")
    return token


def val(d, k):
    """칸 값. 없거나 빈 글자나 "null"이면 None."""
    v = d.get(k)
    if v is None:
        return None
    v = str(v).strip()
    return None if v in ("", "null") else v


def codes10(it):
    """응답 한 줄에서 나오는 10자리 법정동 코드들."""
    out = {v for v in (val(it, "leg_cd"), val(it, "ri_cd")) if v and CODE10.fullmatch(v)}
    s, g, leg, ri = val(it, "sido_cd"), val(it, "sgg_cd"), val(it, "leg_cd"), val(it, "ri_cd")
    if (s and g and leg and re.fullmatch(r"[0-9]{2}", s) and re.fullmatch(r"[0-9]{3}", g)
            and re.fullmatch(r"[0-9]{3}", leg)):
        out.add(s + g + leg + (ri if ri and re.fullmatch(r"[0-9]{2}", ri) else "00"))
    return out


def check_item(it, t):
    """응답 한 줄의 판정. (걸린 조건 이름, None) 또는 (None, dict)."""
    if val(it, "addr_type") != "6":
        return "addr_type", None
    xy = []
    for lo, up in (("x", "X"), ("y", "Y")):
        a, b = val(it, lo), val(it, up)
        if a is not None and b is not None and a != b:
            return "좌표", None
        v = a if a is not None else b
        if v is None or not NUM.fullmatch(v):
            return "좌표", None
        xy.append(v)
    if t["admCd"] in codes10(it):
        dong = "코드"
    elif (nospace(val(it, "sgg_nm")) in t["sgg_names"] and val(it, "leg_nm") == t["leg_nm"]
          and val(it, "ri_nm") == t["ri_nm"]):
        dong = "이름"
    elif nospace(val(it, "sgg_nm")) in t["sgg_names"] and t["bdMgtSn"][:10] in codes10(it):
        dong = "다름"  # 옛 기록: 코드가 건물관리번호 앞 10자리와 같다. 아래에서 도로명·건물번호가 반드시 맞아야 한다
    else:
        return "법정동", None
    by = []
    jm, js = val(it, "jibun_main_no"), val(it, "jibun_sub_no")
    if jm is not None:
        if not DIGITS.fullmatch(jm) or (js is not None and not DIGITS.fullmatch(js)):
            return "지번", None
        if (int(jm), int(js or 0)) != (t["lnbrMnnm"], t["lnbrSlno"]):
            return "지번", None
        by.append("지번")
    rn = val(it, "road_nm")
    if rn is not None:
        rm, rs = val(it, "road_nm_main_no"), val(it, "road_nm_sub_no")
        if (nospace(rn) != t["road_name"] or rm is None or not DIGITS.fullmatch(rm)
                or (rs is not None and not DIGITS.fullmatch(rs))):
            return "도로명", None
        if (int(rm), int(rs or 0)) != (t["bd_main"], t["bd_sub"]):
            return "도로명", None
        by.append("도로명")
    if not by:
        return "근거없음", None
    if dong == "다름" and "도로명" not in by:
        return "법정동", None
    return None, {"x": xy[0], "y": xy[1], "dong": dong, "by": "+".join(by)}


def judge_one(body, t):
    """얼린 응답 하나(꼴 하나)의 판정. (통과한 값 dict 또는 None, 걸린 까닭)."""
    code, result, _ = parse(body, "판정")
    if code != "0":
        return None, f"errCd({code})"
    items = result.get("resultdata")
    if not isinstance(items, list) or not all(isinstance(x, dict) for x in items):
        items = []
    total = str(result.get("totalcount", ""))
    if not total.isdigit() or int(total) != len(items) or len(items) > int(GEOCODE_FIXED["resultcount"]):
        return None, f"건수({total}/{len(items)})"
    hits, why = [], collections.Counter()
    for it in items:
        bad, ok = check_item(it, t)
        if ok:
            hits.append((it, ok))
        else:
            why[bad] += 1
    if len(hits) != 1:
        detail = ", ".join(f"{k} {v}" for k, v in sorted(why.items()))
        return None, f"맞는 줄({len(hits)})" + (f"[{detail}]" if detail and not hits else "")
    it, ok = hits[0]
    ok["matching"] = val(it, "matching") or val(result, "matching") or ""
    return ok, ""


class Frozen:
    """얼린 폴더. 응답 파일(<꼴>/rNN.json)과 목록(calls_v1.csv)."""

    def __init__(self, root):
        self.root = root
        self.manifest = {}
        p = os.path.join(root, MANIFEST)
        if os.path.exists(p):
            with open(p, "rb") as f:
                data = f.read()
            if data and not data.endswith(b"\n"):
                raise Stop(f"{MANIFEST}: 마지막 줄이 잘려 있다")
            rows = list(csv.reader(io.StringIO(data.decode("utf-8"), newline="")))
            if rows and rows[0] != MANIFEST_HEADER:
                raise Stop(f"{MANIFEST}: 머리줄이 다르다")
            for r in rows[1:]:
                if len(r) != len(MANIFEST_HEADER):
                    raise Stop(f"{MANIFEST}: 칸 수가 다른 줄이 있다")
                key = (r[0], r[1])
                if key in self.manifest:
                    raise Stop(f"{MANIFEST}: 같은 응답이 두 번 적혀 있다({r[1]} {r[0]})")
                self.manifest[key] = dict(zip(MANIFEST_HEADER, r))
        for kind in FORMS_ORDER:
            d = os.path.join(root, kind)
            if not os.path.isdir(d):
                continue
            for name in sorted(os.listdir(d)):
                if name.endswith(".tmp"):
                    raise Stop(f"{kind}/{name}: 쓰다 만 파일이 있다. 그 파일만 지우고 다시 돌린다")
                m = re.fullmatch(r"(r[0-9]{2})\.json", name)
                if not m or (m.group(1), kind) not in self.manifest:
                    raise Stop(f"{kind}/{name}: 목록({MANIFEST})에 없는 파일이 있다")

    def path(self, kind, row):
        return os.path.join(self.root, kind, f"r{row:02d}.json")

    def get(self, kind, row):
        m = self.manifest.get((f"r{row:02d}", kind))
        if m is None:
            return None
        p = self.path(kind, row)
        if not os.path.exists(p):
            raise Stop(f"{kind}/r{row:02d}.json: 목록에는 있는데 파일이 없다")
        with open(p, "rb") as f:
            body = f.read()
        if str(len(body)) != m["bytes"] or sha256_bytes(body) != m["sha256"]:
            raise Stop(f"{kind}/r{row:02d}.json: 목록의 크기·해시와 다르다")
        return body

    def put(self, kind, row, body, called_at, attempts, status):
        d = os.path.join(self.root, kind)
        os.makedirs(d, exist_ok=True)
        p = self.path(kind, row)
        tmp = p + ".tmp"
        with open(tmp, "wb") as f:
            f.write(body)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, p)
        mp = os.path.join(self.root, MANIFEST)
        new = not os.path.exists(mp) or os.path.getsize(mp) == 0
        rec = [f"r{row:02d}", kind, called_at, str(attempts), str(status), str(len(body)), sha256_bytes(body)]
        with open(mp, "a", encoding="utf-8", newline="") as f:
            w = csv.writer(f, lineterminator="\n")
            if new:
                w.writerow(MANIFEST_HEADER)
            w.writerow(rec)
            f.flush()
            os.fsync(f.fileno())
        self.manifest[(rec[0], kind)] = dict(zip(MANIFEST_HEADER, rec))


def fetch_all(targets, fz, keys, base, fetch, sleep, stats, tokens):
    """매물마다 꼴을 차례로 부른다. 이미 얼린 것은 건너뛴다."""
    token = None
    for t in targets:
        for kind, address in t["forms"].items():
            if fz.get(kind, t["row"]) is not None:
                continue
            label = f"r{t['row']:02d} {kind}"
            if token is None:
                token = authenticate(base, keys, fetch, stats)
                tokens.append(token)
            reauth, server_tries, att_total = 0, 0, 0
            while True:
                params = dict(GEOCODE_FIXED, accessToken=token, address=address)
                called = now_iso()
                status, raw, att = call(base + GEOCODE_PATH, params, label, fetch, stats)
                att_total += att
                if status != 200:
                    raise Stop(f"{label}: HTTP {status}")
                code, _, msg = parse(raw, label)
                if code == TOKEN_ERR:  # 토큰이 끝났거나 잠깐 안 되면 쉬었다가 다시 인증한다(두 번까지)
                    if reauth == len(RETRY_WAITS):
                        raise Stop(f"{label}: 다시 인증해도 토큰 오류 {code}가 {reauth + 1}번 났다: {msg}")
                    time.sleep(RETRY_WAITS[reauth])
                    reauth += 1
                    token = authenticate(base, keys, fetch, stats)
                    tokens.append(token)
                    continue
                if code == SERVER_ERR:
                    if server_tries == len(RETRY_WAITS):
                        raise Stop(f"{label}: 서버 처리 오류 {code}가 {server_tries + 1}번 났다: {msg}")
                    time.sleep(RETRY_WAITS[server_tries])
                    server_tries += 1
                    continue
                break
            fz.put(kind, t["row"], raw, called, att_total, status)
            stats["new"] += 1
            if code != "0":
                stats["nonzero"] += 1
            print(f"{label} 얼림(시도 {att_total}{'' if code == '0' else ', errCd ' + code})")
            time.sleep(sleep)


def judge_all(targets, fz):
    """(표 줄 목록, 보고 줄 목록, 실패한 줄 번호 목록, 셈). 얼린 파일만 읽는다."""
    allowed = {(f"r{t['row']:02d}", k) for t in targets for k in t["forms"]}
    extra = sorted(set(fz.manifest) - allowed)
    if extra:
        raise Stop(f"{MANIFEST}: 이 매물에 부르지 않는 꼴이 적혀 있다({extra[0][1]} {extra[0][0]})")
    table, report, failed = [], [], []
    count = collections.Counter()
    for t in targets:
        verdict, passed = {}, []
        for kind in FORMS_ORDER:
            if kind not in t["forms"]:
                verdict[kind] = "-"
                continue
            body = fz.get(kind, t["row"])
            if body is None:
                raise Stop(f"r{t['row']:02d}: 얼린 {kind} 응답이 없다")
            ok, why = judge_one(body, t)
            verdict[kind] = "통과" if ok else why
            if ok:
                passed.append((kind, ok))
            elif why.startswith("errCd("):
                count["errCd"] += 1
        spread, why_row = "", ""
        if not passed:
            why_row = "통과한 꼴 없음"
        else:
            pts = [(float(o["x"]), float(o["y"])) for _, o in passed]
            d = max(math.dist(a, b) for a in pts for b in pts)
            spread = str(int(round(d)))
            if d > MAX_SPREAD_M:
                why_row = f"꼴 사이 {spread}m"
        usable = [(k, o) for k, o in passed if not t["san"] or "도로명" in o["by"].split("+")]
        if not why_row and not usable:
            why_row = "산 지번은 도로명까지 맞아야 한다"
        if why_row:
            failed.append(t["row"])
            used = "없음"
        else:
            used, o = usable[0]
            table.append([str(t["row"]), t["bdMgtSn"], used, o["dong"], o["by"], "6", o["matching"], o["x"], o["y"]])
            count[used] += 1
            count[o["dong"]] += 1
        report.append([str(t["row"]), *[verdict[k] for k in FORMS_ORDER], used, spread, why_row])
    return table, report, failed, count


def csv_bytes(header, rows):
    buf = io.StringIO()
    w = csv.writer(buf, lineterminator="\n")
    w.writerow(header)
    w.writerows(rows)
    return buf.getvalue().encode("utf-8")


def key_forms(secrets):
    """키 글자의 세 꼴: 그대로, 퍼센트 인코딩, + 인코딩. 긴 것부터."""
    forms = set()
    for v in secrets:
        if v:
            forms.update({v, urllib.parse.quote(v, safe=""), urllib.parse.quote_plus(v)})
    return sorted(forms, key=len, reverse=True)


def mask_keys(text, secrets):
    """(가린 글, 가린 것이 있었는지)."""
    masked = False
    for f in key_forms(secrets):
        if f in text:
            text = text.replace(f, "<키>")
            masked = True
    return text, masked


def leak_check(root, secrets):
    needles = {f.encode("utf-8") for f in key_forms(secrets)}
    for dp, _, files in os.walk(root):
        for name in files:
            with open(os.path.join(dp, name), "rb") as f:
                data = f.read()
            if any(n in data for n in needles):
                rel = os.path.relpath(os.path.join(dp, name), root)
                raise Stop(f"{rel}: 키·Secret·토큰 글자가 들어 있다. 이 폴더를 보내지 말고 알려 달라")


def write_or_compare(path, data, mode):
    if os.path.exists(path):
        with open(path, "rb") as f:
            if f.read() != data:
                raise Stop(f"{os.path.basename(path)}가 얼린 응답에서 다시 만든 것과 바이트가 다르다")
    elif mode == "fetch":
        with open(path, "wb") as f:
            f.write(data)
    else:
        raise Stop(f"{os.path.basename(path)}가 없다")


def run(mode, listings_path, juso_path, root, fetch=http_get, sleep=SLEEP, out_dir=None):
    base = os.environ.get(TEST_BASE_ENV) or BASE
    if base != BASE:
        print(f"*** 시험용 주소로 부른다({TEST_BASE_ENV}). 실제 좌표가 아니다 ***")
        sleep = 0
    targets = load_targets(listings_path, juso_path)
    keys = {}
    if mode == "fetch":
        for env in (KEY_ENV, SECRET_ENV):
            v = os.environ.get(env, "")
            if not v.strip():
                raise Stop(f"환경 변수 {env}가 비어 있다")
            keys[env] = v
    elif not os.path.isdir(root):
        raise Stop("얼린 폴더가 없다")
    os.makedirs(root, exist_ok=True)
    dest = root
    if mode == "rejudge":
        dest = out_dir
        if os.path.exists(dest) and os.listdir(dest):
            raise Stop("rejudge의 내놓을 폴더가 비어 있지 않다")
        os.makedirs(dest, exist_ok=True)
    wmode = "fetch" if mode == "rejudge" else mode
    stats = {"new": 0, "nonzero": 0, "attempts": 0, "auth": 0}
    tokens = []
    error, data = None, None
    try:
        fz = Frozen(root)
        if mode == "fetch":
            fetch_all(targets, fz, keys, base, fetch, sleep, stats, tokens)
        table, report, failed, count = judge_all(targets, fz)
        write_or_compare(os.path.join(dest, REPORT), csv_bytes(REPORT_HEADER, report), wmode)
        if failed:
            raise Stop(f"판정을 통과하지 못한 줄 {len(failed)}개: {', '.join(f'r{n:02d}' for n in failed)} "
                       f"(까닭은 {REPORT}). 얼린 응답은 남아 있다")
        data = csv_bytes(TABLE_HEADER, table)
        write_or_compare(os.path.join(dest, TABLE), data, wmode)
        forms = ", ".join(f"{k} {count[k]}" for k in FORMS_ORDER)
        result = (f"끝: 75줄 모두 통과({forms}; 법정동 코드 {count['코드']}, 이름 {count['이름']}, 다름 {count['다름']}; "
                  f"errCd 0 아닌 꼴 {count['errCd']}). {TABLE} SHA-256 {sha256_bytes(data)}")
    except Stop as e:
        error, result = e, f"멈춤: {e}"
    if keys:
        try:
            leak_check(root, list(keys.values()) + tokens)
        except Stop as e:
            error = error or e
            result += f" | 멈춤: {e}"
    line = (f"{now_iso()} {mode} python {sys.version.split()[0]} | 새로 얼린 응답 {stats['new']}"
            f"(errCd 0 아님 {stats['nonzero']}), 요청 {stats['attempts']}번(인증 {stats['auth']}번 포함) | {result}")
    # 줄에는 서버가 준 오류 글이 들어갈 수 있다. 쓰고 찍기 전에 키·Secret·토큰을 가리고, 가린 것이 있으면 멈춤으로 친다.
    line, masked = mask_keys(line, list(keys.values()) + tokens)
    if masked:
        line += " | 멈춤: 화면·기록에 쓸 글에 키 글자가 있어 <키>로 가렸다. 알려 달라"
        error = Stop("화면·기록에 쓸 글에 키 글자가 있어 가렸다")
    if mode in ("fetch", "rejudge"):
        with open(os.path.join(dest, LOG), "a", encoding="utf-8", newline="") as f:
            f.write(line + "\n")
    print(line)
    if error:
        error.printed = True
        raise error
    return data


def main(argv):
    if not ((len(argv) == 5 and argv[1] in ("fetch", "replay")) or (len(argv) == 6 and argv[1] == "rejudge")):
        print(__doc__)
        return 2
    try:
        run(argv[1], argv[2], argv[3], argv[4], out_dir=argv[5] if len(argv) == 6 else None)
    except Stop as e:
        if not getattr(e, "printed", False):
            print(f"멈춤: {e}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
