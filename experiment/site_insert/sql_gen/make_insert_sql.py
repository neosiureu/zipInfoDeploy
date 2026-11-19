# -*- coding: utf-8 -*-
"""예시 매물 75개를 넣는 SQL을 만든다(4단계 계획 5~8절, ⑤).

입력은 얼린 파일뿐이다: example_listings_v1.csv(못박음)와 WGS84 좌표 표(utmk_to_wgs84.py가 낸 꼴).
좌표 표 대신 FAKE를 주면 줄마다 다른 가짜 좌표로 리허설용만 만든다(계획 6절, 상대 조건).

- 내놓는 파일(차례대로 돌린다). "_리허설"·"_운영" 둘이 있는 것은 맨 앞의 스키마 확인 한 줄만 다르다.
  - 0_EXAMPLE_STOCK_만들기: 표시 표(5절). DDL이라 넣기 전에 따로 돌린다.
  - 1_줄수: 넣기 전과 뒤에 돌려 표마다 줄 수와 우리가 넣은 줄 수를 본다(SELECT만).
  - 2_넣기: PL/SQL 블록 하나. 스키마 확인 → 두 번 넣기 막기 → 예시 계정·TOKEN_INFO·BROKER_INFO → 매물 75개.
    매물 한 줄의 일곱 INSERT는 블록 안 작은 프로시저 하나에 적고 75번 부른다. COMMIT은 없다. 오류면 ROLLBACK 하고 다시 낸다.
  - 3_대조: 기대 값을 SQL 안에 넣고 다른 줄 수를 센다(SELECT만). BAD가 모두 0이어야 한다(7절).
  - 4_되돌리기: PL/SQL 블록 하나(8절). COMMIT은 없다. 지운 뒤 5_남은줄로 보고 손으로 COMMIT 한다.
  - 5_남은줄: 되돌린 뒤 남은 줄을 센다(SELECT만). 모두 0이어야 한다(리허설의 가짜 회원 한 줄은 남는다).
  - 6_EXAMPLE_STOCK_지우기: 되돌리기를 COMMIT 한 뒤 표시 표를 지운다.
  - R_가짜회원_리허설: 리허설에서만. 가짜 회원과, 그 회원의 LIKE_STOCK·STOCK_SAW 줄 둘씩을 만든다(9절 5).
- 글자 값은 모두 UNISTR('\\XXXX')로 쓰고, SQL 파일은 ASCII만이다. DBeaver가 매개변수로 볼 수 있는 ':' '&' '?'는
  한 글자도 쓰지 않는다(PL/SQL의 ':='도 쓰지 않고 SELECT INTO·RETURNING INTO만 쓴다).
- 운영용은 좌표 표의 SHA-256이 REAL_WGS84_SHA와 같을 때만 만든다. 그 값은 ④가 끝나면 적는다. 그 전에는 운영용이 나오지 않는다.

사용: python -X utf8 -B -I make_insert_sql.py <example_listings_v1.csv> <coord_wgs84_v1.csv 또는 FAKE> <내놓을 폴더>
종료 코드: 0 끝, 1 멈춤, 2 명령 꼴이 틀림.
"""
import csv
import hashlib
import io
import os
import re
import sys

LISTINGS_SHA = "FFB838C797E7EE4CEAFD15096701565E501B5392F5A290690FF2F3C4B8C1EBF4"
REAL_WGS84_SHA = "7513F19222F2DD64DF3504D536263A18584C3D1C5694A15EBD3FAD9FB38F5DAB"  # ④(11/19 SGIS) 뒤 진짜 WGS84 표 coord_wgs84_v1.csv
N_ROWS = 75
SCHEMAS = {"리허설": "ZIP_REHEARSAL", "운영": "ADMIN"}
COORD_HEADER = ["row", "bdMgtSn", "lat", "lng"]
LAT_RANGE, LNG_RANGE = (33.0, 39.0), (124.0, 132.0)

ACCOUNT = {  # 4절
    "email": "example-listings@example.invalid",
    "pw": "LOGIN-DISABLED:EXAMPLE-ACCOUNT",
    "nickname": "집인포 예시 매물",
    "name": "예시",
}
BROKER = {
    "company_name": "집인포 예시 매물(실제 중개사 아님)",
    "company_location": "00000^^^예시 매물 안내용(실제 사무소 아님)^^^",
    "president_name": "예시",
    "broker_no": "없음(예시 계정)",
    "president_phone": "000-0000-0000",
}
IMAGES = [(0, "zipinfo_example_v2_0_thumb.png"), (1, "zipinfo_example_v2_1_floorplan.png"),
          (2, "zipinfo_example_v2_2_photo.png")]  # 2절. IMG_URL은 /myPage/stockImg/<이름>
IMG_URL_PREFIX = "/myPage/stockImg/"
FAKE_MEMBER_EMAIL = "rehearsal-fake-member@example.invalid"  # 리허설에서만
# CSV 19칸 → STOCK_INFO 칸, 꼴(S 글자, N 정수, D 날짜)
COLUMNS = [
    ("stockName", "STOCK_NAME", "S"), ("stockSellPrice", "STOCK_SELL_PRICE", "N"),
    ("stockManageFee", "STOCK_MANAGE_FEE", "N"), ("stockFeeMonth", "STOCK_FEE_MONTH", "N"),
    ("stockInfo", "STOCK_INFO", "S"), ("stockType", "STOCK_TYPE", "N"), ("stockForm", "STOCK_FORM", "N"),
    ("exclusiveArea", "EXCLUSIVE_AREA", "N"), ("supplyArea", "SUPPLY_AREA", "N"),
    ("currentFloor", "CURRENT_FLOOR", "N"), ("floorTotalCount", "FLOOR_TOTAL_COUNT", "N"),
    ("roomCount", "ROOM_COUNT", "N"), ("bathCount", "BATH_COUNT", "N"), ("stockDirection", "STOCK_DIRECTION", "S"),
    ("ableDate", "ABLE_DATE", "D"), ("useApprovalDate", "USE_APPROVAL_DATE", "D"),
    ("stockDetail", "STOCK_DETAIL", "S"), ("stockAddress", "STOCK_ADDRESS", "S"), ("regionNo", "REGION_NO", "N"),
]
META = [("complex_id", "COMPLEX_ID"), ("layer", "LAYER"), ("split", "SPLIT")]  # EXAMPLE_STOCK 칸
SAFE = re.compile(r"[A-Za-z0-9 ._,()\[\]^/@+=%*#$~!-]")


class Stop(RuntimeError):
    pass


def sha256(b):
    return hashlib.sha256(b).hexdigest().upper()


def u(text):
    """NVARCHAR2 값 글. ASCII 안전 글자만 그대로, 나머지(한글, 따옴표, 역슬래시, ':' '&' '?')는 \\XXXX로."""
    out = []
    for ch in text:
        if ord(ch) > 0xFFFF:
            raise Stop("BMP 밖의 글자가 있다")
        out.append(ch if SAFE.fullmatch(ch) else f"\\{ord(ch):04X}")
    return "UNISTR('" + "".join(out) + "')"


def num(v, label):
    if not re.fullmatch(r"-?[0-9]+", v):
        raise Stop(f"{label}: 정수가 아니다")
    return str(int(v))


def day(v, label):
    if not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", v):
        raise Stop(f"{label}: 날짜 꼴이 아니다")
    return f"DATE '{v}'"


def read_listings(path):
    with open(path, "rb") as f:
        data = f.read()
    if os.path.basename(path) != "example_listings_v1.csv" or sha256(data) != LISTINGS_SHA:
        raise Stop("example_listings_v1.csv의 이름이나 SHA-256이 얼린 값과 다르다")
    rows = list(csv.DictReader(io.StringIO(data.decode("utf-8"), newline="")))
    if len(rows) != N_ROWS:
        raise Stop("예시 매물이 75줄이 아니다")
    return rows


def fake_coords():
    """줄마다 다른 가짜 좌표(범위 안). 실제 위치가 아니다."""
    return {i: (f"{33.5 + i * 0.05:.7f}", f"{126.0 + i * 0.05:.7f}") for i in range(1, N_ROWS + 1)}


def read_coords(path):
    with open(path, "rb") as f:
        data = f.read()
    rows = list(csv.reader(io.StringIO(data.decode("utf-8"), newline="")))
    if not rows or rows[0] != COORD_HEADER:
        raise Stop("좌표 표의 머리줄이 다르다")
    body = rows[1:]
    if [r[0] for r in body] != [str(i) for i in range(1, N_ROWS + 1)]:
        raise Stop("좌표 표가 1~75줄 차례가 아니다")
    out = {}
    for r in body:
        if len(r) != 4 or not all(re.fullmatch(r"[0-9]+\.[0-9]{7}", x) for x in r[2:]):
            raise Stop(f"r{int(r[0]):02d}: 좌표 표 칸 꼴이 다르다")
        out[int(r[0])] = (r[2], r[3])
    return out, sha256(data)


def check_coords(coords):
    for i, (lat, lng) in coords.items():
        if not (LAT_RANGE[0] <= float(lat) <= LAT_RANGE[1] and LNG_RANGE[0] <= float(lng) <= LNG_RANGE[1]):
            raise Stop(f"r{i:02d}: 좌표가 범위 밖이다")
    if len(set(coords.values())) != N_ROWS:
        raise Stop("좌표가 줄마다 다르지 않다")


def guard(schema, what):
    return (f"  IF SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') <> '{schema}' THEN "
            f"RAISE_APPLICATION_ERROR(-20001, 'Schema is not {schema}. Nothing was {what}.'); END IF;")


def pw_expr():
    a, b = ACCOUNT["pw"].split(":")
    return f"'{a}' || CHR(58) || '{b}'"  # ':'를 SQL 글에 쓰지 않는다


def sql_example_stock(schema):
    return f"""DECLARE
  -- 0. marker table EXAMPLE_STOCK (plan section 5). DDL commits by itself, so run it before the insert block.
BEGIN
{guard(schema, "created")}
  EXECUTE IMMEDIATE 'CREATE TABLE EXAMPLE_STOCK (STOCK_NO NUMBER NOT NULL, CSV_ROW NUMBER NOT NULL, COMPLEX_ID NVARCHAR2(100) NOT NULL, LAYER NVARCHAR2(10) NOT NULL, SPLIT NVARCHAR2(10) NOT NULL, MEMBER_NO NUMBER NOT NULL, CONSTRAINT PK_EXAMPLE_STOCK PRIMARY KEY (STOCK_NO), CONSTRAINT UQ_EXAMPLE_STOCK_ROW UNIQUE (CSV_ROW))';
END;
"""


def sql_drop_example_stock(schema):
    return f"""DECLARE
  -- 6. drop the marker table after the rollback has been committed.
BEGIN
{guard(schema, "dropped")}
  EXECUTE IMMEDIATE 'DROP TABLE EXAMPLE_STOCK';
END;
"""


def sql_insert(schema, rows, coords):
    lines = [
        "DECLARE",
        "  -- 2. example account and 75 example listings (plan section 6). No COMMIT here.",
        "  v_member NUMBER;",
        "  n NUMBER;",
        "  PROCEDURE add_one(p_row NUMBER, p_complex NVARCHAR2, p_layer NVARCHAR2, p_split NVARCHAR2,",
        "                    " + ", ".join(f"p_{c.lower()} {'NVARCHAR2' if k == 'S' else ('DATE' if k == 'D' else 'NUMBER')}"
                                       for _, c, k in COLUMNS) + ",",
        "                    p_lat NUMBER, p_lng NUMBER) IS",
        "    v_no NUMBER;",
        "  BEGIN",
        "    INSERT INTO STOCK_INFO (STOCK_NO, MEMBER_NO, " + ", ".join(c for _, c, _ in COLUMNS) + ", REGIST_DATE, SELL_YN)",
        "    VALUES (SEQ_STOCK_INFO.NEXTVAL, v_member, " + ", ".join(f"p_{c.lower()}" for _, c, _ in COLUMNS)
        + ", SYSDATE, 'N')",
        "    RETURNING STOCK_NO INTO v_no;",
        "    INSERT INTO SELL_DATE (STOCK_NO, SELL_DATE) VALUES (v_no, NULL);",
        "    INSERT INTO STOCK_COORD (STOCK_NO, LAT, LNG) VALUES (v_no, p_lat, p_lng);",
    ]
    for order, name in IMAGES:
        lines.append(f"    INSERT INTO STOCK_IMG (IMG_NO, STOCK_NO, IMG_ORDER, IMG_URL, IMG_ORIGIN_NAME, IMG_RENAME) "
                     f"VALUES (SEQ_STOCK_IMG.NEXTVAL, v_no, {order}, '{IMG_URL_PREFIX}{name}', '{name}', '{name}');")
    lines += [
        "    INSERT INTO EXAMPLE_STOCK (STOCK_NO, CSV_ROW, COMPLEX_ID, LAYER, SPLIT, MEMBER_NO)",
        "    VALUES (v_no, p_row, p_complex, p_layer, p_split, v_member);",
        "  END add_one;",
        "BEGIN",
        guard(schema, "inserted"),
        f"  SELECT COUNT(*) INTO n FROM MEMBER WHERE MEMBER_EMAIL = '{ACCOUNT['email']}';",
        "  IF n > 0 THEN RAISE_APPLICATION_ERROR(-20002, 'The example account already exists. Nothing was inserted.'); END IF;",
        "  SELECT COUNT(*) INTO n FROM EXAMPLE_STOCK;",
        "  IF n > 0 THEN RAISE_APPLICATION_ERROR(-20003, 'EXAMPLE_STOCK is not empty. Nothing was inserted.'); END IF;",
        "  INSERT INTO MEMBER (MEMBER_NO, MEMBER_EMAIL, MEMBER_PW, MEMBER_IMG, ENROLL_DATE, MEMBER_DEL_FL, MEMBER_LOGIN,",
        "                      MEMBER_NICKNAME, MEMBER_NAME, MEMBER_AUTH, ACCESS_TOKEN, MEMBER_LOCATION, MEMBER_WITHDRAW_DATE)",
        f"  VALUES (SEQ_MEMBER.NEXTVAL, '{ACCOUNT['email']}', {pw_expr()}, NULL, SYSDATE, 'N', 'E',",
        f"          {u(ACCOUNT['nickname'])}, {u(ACCOUNT['name'])}, 3, NULL, NULL, NULL)",
        "  RETURNING MEMBER_NO INTO v_member;",
        "  INSERT INTO TOKEN_INFO (MEMBER_NO, TOKEN_INFO) VALUES (v_member, NULL);",
        "  INSERT INTO BROKER_INFO (MEMBER_NO, COMPANY_NAME, COMPANY_LOCATION, PRESIDENT_NAME, BROKER_NO, PRESIDENT_PHONE)",
        f"  VALUES (v_member, {u(BROKER['company_name'])}, {u(BROKER['company_location'])}, {u(BROKER['president_name'])},",
        f"          {u(BROKER['broker_no'])}, {u(BROKER['president_phone'])});",
    ]
    for i, r in enumerate(rows, 1):
        lines.append("  add_one(" + ", ".join(call_args(i, r, coords)) + ");")
    lines += [
        "EXCEPTION",
        "  WHEN OTHERS THEN",
        "    ROLLBACK;",
        "    RAISE;",
        "END;",
    ]
    return "\n".join(lines) + "\n"


def value_sql(v, kind, label):
    return u(v) if kind == "S" else (num(v, label) if kind == "N" else day(v, label))


def call_args(i, r, coords):
    lab = f"r{i:02d}"
    args = [str(i)] + [u(r[c]) for c, _ in META]
    args += [value_sql(r[c], k, f"{lab} {c}") for c, _, k in COLUMNS]
    args += list(coords[i])
    return args


def want_cte(rows, coords):
    """기대 값 75줄(대조 SQL의 WITH 절)."""
    cols = ["CSV_ROW"] + [c for _, c in META] + [c for _, c, _ in COLUMNS] + ["LAT", "LNG"]
    lines = [f"WITH want ({', '.join(cols)}) AS ("]
    for i, r in enumerate(rows, 1):
        vals = call_args(i, r, coords)
        sep = " UNION ALL" if i < N_ROWS else ""
        lines.append(f"  SELECT {', '.join(vals)} FROM DUAL{sep}")
    lines.append(")")
    return "\n".join(lines)


def eq(a, b):
    """빈 값끼리도 같다고 보는 비교(DECODE)."""
    return f"DECODE({a}, {b}, 0, 1) = 0"


def sql_counts():
    acc = f"(SELECT MEMBER_NO FROM MEMBER WHERE MEMBER_EMAIL = '{ACCOUNT['email']}')"
    ours = "(SELECT STOCK_NO FROM EXAMPLE_STOCK)"
    parts = [
        ("STOCK_INFO", f"STOCK_NO IN {ours}", 75), ("SELL_DATE", f"STOCK_NO IN {ours}", 75),
        ("STOCK_COORD", f"STOCK_NO IN {ours}", 75), ("STOCK_IMG", f"STOCK_NO IN {ours}", 225),
        ("MEMBER", f"MEMBER_NO IN {acc}", 1), ("TOKEN_INFO", f"MEMBER_NO IN {acc}", 1),
        ("BROKER_INFO", f"MEMBER_NO IN {acc}", 1),
    ]
    q = ["-- 1. row counts. Run before the insert (EXAMPLE_STOCK just created, empty) and after it.",
         "-- After the insert, OURS must equal EXPECTED, and OTHERS must equal ROWS_NOW of the run before the insert."]
    sel = []
    for t, cond, exp in parts:
        # 집계 함수 안에 부분 쿼리를 두지 않는다(ORA-22818을 피한다). 스칼라 부분 쿼리만 쓴다.
        alln, ourn = f"(SELECT COUNT(*) FROM {t})", f"(SELECT COUNT(*) FROM {t} WHERE {cond})"
        sel.append(f"SELECT '{t}' AS TABLE_NAME, {alln} AS ROWS_NOW, {ourn} AS OURS, {alln} - {ourn} AS OTHERS, "
                   f"{exp} AS EXPECTED FROM DUAL")
    sel.append("SELECT 'EXAMPLE_STOCK', (SELECT COUNT(*) FROM EXAMPLE_STOCK), (SELECT COUNT(*) FROM EXAMPLE_STOCK), 0, 75 FROM DUAL")
    q.append("\nUNION ALL\n".join(sel) + ";")
    return "\n".join(q) + "\n"


def sql_verify(rows, coords):
    acc = f"(SELECT MEMBER_NO FROM MEMBER WHERE MEMBER_EMAIL = '{ACCOUNT['email']}')"
    cmp = []
    for _, c, k in COLUMNS:
        cmp.append(eq(f"s.{c}", f"w.{c}"))
    meta = " AND ".join(eq(f"e.{c}", f"w.{c}") for _, c in META)
    img_ok = " AND ".join(
        f"EXISTS (SELECT 1 FROM STOCK_IMG i WHERE i.STOCK_NO = e.STOCK_NO AND i.IMG_ORDER = {o} "
        f"AND i.IMG_URL = '{IMG_URL_PREFIX}{n}' AND i.IMG_ORIGIN_NAME = '{n}' AND i.IMG_RENAME = '{n}')"
        for o, n in IMAGES)
    checks = [
        ("ROWS_EXAMPLE_STOCK", "SELECT ABS(COUNT(*) - 75) FROM EXAMPLE_STOCK"),
        ("ROWS_MISSING", "SELECT COUNT(*) FROM want w WHERE NOT EXISTS (SELECT 1 FROM EXAMPLE_STOCK e WHERE e.CSV_ROW = w.CSV_ROW)"),
        ("META_EXAMPLE_STOCK", f"SELECT COUNT(*) FROM want w JOIN EXAMPLE_STOCK e ON e.CSV_ROW = w.CSV_ROW "
                               f"WHERE NOT ({meta} AND e.MEMBER_NO = {acc})"),
        ("STOCK_INFO_19_COLUMNS", "SELECT COUNT(*) FROM want w JOIN EXAMPLE_STOCK e ON e.CSV_ROW = w.CSV_ROW "
                                  "LEFT JOIN STOCK_INFO s ON s.STOCK_NO = e.STOCK_NO WHERE s.STOCK_NO IS NULL OR NOT ("
                                  + " AND ".join(cmp) + ")"),
        ("STOCK_INFO_OTHERS", f"SELECT COUNT(*) FROM EXAMPLE_STOCK e LEFT JOIN STOCK_INFO s ON s.STOCK_NO = e.STOCK_NO "
                              f"WHERE s.STOCK_NO IS NULL OR NOT (s.MEMBER_NO = {acc} AND s.SELL_YN = 'N' "
                              f"AND s.REGIST_DATE IS NOT NULL AND s.ABLE_DATE = TRUNC(s.ABLE_DATE) "
                              f"AND s.USE_APPROVAL_DATE = TRUNC(s.USE_APPROVAL_DATE))"),
        ("SELL_DATE_NULL", "SELECT COUNT(*) FROM EXAMPLE_STOCK e WHERE NOT EXISTS (SELECT 1 FROM SELL_DATE d "
                           "WHERE d.STOCK_NO = e.STOCK_NO AND d.SELL_DATE IS NULL) OR (SELECT COUNT(*) FROM SELL_DATE d "
                           "WHERE d.STOCK_NO = e.STOCK_NO) <> 1"),
        ("STOCK_COORD", "SELECT COUNT(*) FROM want w JOIN EXAMPLE_STOCK e ON e.CSV_ROW = w.CSV_ROW "
                        "WHERE NOT EXISTS (SELECT 1 FROM STOCK_COORD c WHERE c.STOCK_NO = e.STOCK_NO "
                        "AND c.LAT = w.LAT AND c.LNG = w.LNG) OR (SELECT COUNT(*) FROM STOCK_COORD c "
                        "WHERE c.STOCK_NO = e.STOCK_NO) <> 1"),
        ("STOCK_IMG", f"SELECT COUNT(*) FROM EXAMPLE_STOCK e WHERE NOT ({img_ok}) OR (SELECT COUNT(*) FROM STOCK_IMG i "
                      f"WHERE i.STOCK_NO = e.STOCK_NO) <> 3"),
        ("MEMBER_ACCOUNT", f"SELECT ABS(COUNT(*) - 1) FROM MEMBER m WHERE m.MEMBER_EMAIL = '{ACCOUNT['email']}' "
                           f"AND m.MEMBER_PW = {pw_expr()} AND m.MEMBER_IMG IS NULL AND m.MEMBER_DEL_FL = 'N' "
                           f"AND m.MEMBER_LOGIN = 'E' AND {eq('m.MEMBER_NICKNAME', u(ACCOUNT['nickname']))} "
                           f"AND {eq('m.MEMBER_NAME', u(ACCOUNT['name']))} AND m.MEMBER_AUTH = 3 "
                           f"AND m.ACCESS_TOKEN IS NULL AND m.MEMBER_LOCATION IS NULL AND m.MEMBER_WITHDRAW_DATE IS NULL "
                           f"AND m.ENROLL_DATE IS NOT NULL"),
        ("TOKEN_INFO", f"SELECT ABS(COUNT(*) - 1) FROM TOKEN_INFO t WHERE t.MEMBER_NO IN {acc} AND t.TOKEN_INFO IS NULL"),
        ("BROKER_INFO", f"SELECT ABS(COUNT(*) - 1) FROM BROKER_INFO b WHERE b.MEMBER_NO IN {acc} "
                        f"AND {eq('b.COMPANY_NAME', u(BROKER['company_name']))} "
                        f"AND {eq('b.COMPANY_LOCATION', u(BROKER['company_location']))} "
                        f"AND {eq('b.PRESIDENT_NAME', u(BROKER['president_name']))} "
                        f"AND {eq('b.BROKER_NO', u(BROKER['broker_no']))} "
                        f"AND {eq('b.PRESIDENT_PHONE', u(BROKER['president_phone']))}"),
    ]
    out = ["-- 3. compare what is in the database with the frozen values. Every BAD must be 0.",
           "-- REGIST_DATE and ENROLL_DATE are DB clock values. UTC_TIME shows them as stored and KST_TIME adds 9 hours.",
           "-- That is right on production (RDS clock is UTC). DB_CLOCK_NOW shows the clock offset, the local XE may already be KST.",
           want_cte(rows, coords)]
    out.append("\nUNION ALL\n".join(f"SELECT '{name}' AS CHECK_NAME, ({q}) AS BAD, CAST(NULL AS VARCHAR2(60)) AS UTC_TIME, "
                                    f"CAST(NULL AS VARCHAR2(60)) AS KST_TIME FROM DUAL" for name, q in checks))
    out.append("UNION ALL")
    out.append("SELECT 'REGIST_DATE_RANGE', 0, (SELECT TO_CHAR(MIN(s.REGIST_DATE), 'YYYY-MM-DD HH24.MI.SS') || ' ~ ' || "
               "TO_CHAR(MAX(s.REGIST_DATE), 'YYYY-MM-DD HH24.MI.SS') FROM STOCK_INFO s JOIN EXAMPLE_STOCK e ON e.STOCK_NO = s.STOCK_NO), "
               "(SELECT TO_CHAR(MIN(s.REGIST_DATE) + 9/24, 'YYYY-MM-DD HH24.MI.SS') || ' ~ ' || "
               "TO_CHAR(MAX(s.REGIST_DATE) + 9/24, 'YYYY-MM-DD HH24.MI.SS') FROM STOCK_INFO s JOIN EXAMPLE_STOCK e ON e.STOCK_NO = s.STOCK_NO) FROM DUAL")
    out.append("UNION ALL")
    out.append("SELECT 'DB_CLOCK_NOW', 0, TO_CHAR(SYSTIMESTAMP, 'YYYY-MM-DD HH24.MI.SS TZH.TZM'), CAST(NULL AS VARCHAR2(60)) FROM DUAL")
    out.append("UNION ALL")
    out.append(f"SELECT 'ENROLL_DATE', 0, (SELECT TO_CHAR(MAX(m.ENROLL_DATE), 'YYYY-MM-DD HH24.MI.SS') FROM MEMBER m "
               f"WHERE m.MEMBER_EMAIL = '{ACCOUNT['email']}'), (SELECT TO_CHAR(MAX(m.ENROLL_DATE) + 9/24, 'YYYY-MM-DD HH24.MI.SS') "
               f"FROM MEMBER m WHERE m.MEMBER_EMAIL = '{ACCOUNT['email']}') FROM DUAL;")
    return "\n".join(out) + "\n"


def sql_rollback(schema):
    em = ACCOUNT["email"]
    return f"""DECLARE
  -- 4. remove the example listings and the example account (plan section 8). No COMMIT here.
  -- After this, run 5 and look at the remaining rows, then COMMIT by hand, then run 6.
  v_member NUMBER;
  n NUMBER;
BEGIN
{guard(schema, "deleted")}
  SELECT COUNT(*) INTO n FROM MEMBER WHERE MEMBER_EMAIL = '{em}';
  IF n <> 1 THEN RAISE_APPLICATION_ERROR(-20004, 'There is not exactly one example account. Nothing was deleted.'); END IF;
  SELECT MEMBER_NO INTO v_member FROM MEMBER WHERE MEMBER_EMAIL = '{em}';
  -- listings. The same tables the site deletes for a listing, by the numbers kept in EXAMPLE_STOCK
  DELETE FROM STOCK_COORD WHERE STOCK_NO IN (SELECT STOCK_NO FROM EXAMPLE_STOCK);
  DELETE FROM SELL_DATE WHERE STOCK_NO IN (SELECT STOCK_NO FROM EXAMPLE_STOCK);
  DELETE FROM STOCK_IMG WHERE STOCK_NO IN (SELECT STOCK_NO FROM EXAMPLE_STOCK);
  DELETE FROM LIKE_STOCK WHERE STOCK_NO IN (SELECT STOCK_NO FROM EXAMPLE_STOCK);
  DELETE FROM STOCK_SAW WHERE STOCK_NO IN (SELECT STOCK_NO FROM EXAMPLE_STOCK);
  DELETE FROM STOCK_INFO WHERE STOCK_NO IN (SELECT STOCK_NO FROM EXAMPLE_STOCK);
  SELECT COUNT(*) INTO n FROM STOCK_INFO WHERE MEMBER_NO = v_member;
  IF n > 0 THEN RAISE_APPLICATION_ERROR(-20005, 'The example account has listings not in EXAMPLE_STOCK. Rolled back.'); END IF;
  -- account. The tables the site clears when a member is deleted, children first
  DELETE FROM MESSAGE_FILE WHERE MESSAGE_NO IN (SELECT MESSAGE_NO FROM HELP_MESSAGE WHERE SENDER_NO = v_member OR RECEIVER_NO = v_member);
  DELETE FROM HELP_MESSAGE WHERE SENDER_NO = v_member OR RECEIVER_NO = v_member;
  DELETE FROM STOCK_SAW WHERE MEMBER_NO = v_member;
  DELETE FROM LIKE_STOCK WHERE MEMBER_NO = v_member;
  DELETE FROM COMMENT_LIKE WHERE MEMBER_NO = v_member;
  DELETE FROM "LIKE" WHERE MEMBER_NO = v_member;
  DELETE FROM "COMMENT" WHERE MEMBER_NO = v_member;
  DELETE FROM BOARD WHERE MEMBER_NO = v_member;
  DELETE FROM TOKEN_INFO WHERE MEMBER_NO = v_member;
  DELETE FROM BROKER_INFO WHERE MEMBER_NO = v_member;
  SELECT (SELECT COUNT(*) FROM ANNOUNCE WHERE MEMBER_NO = v_member) + (SELECT COUNT(*) FROM ADVERTISE_IMG WHERE MEMBER_NO = v_member)
       + (SELECT COUNT(*) FROM SALE_INFO WHERE MEMBER_NO = v_member) INTO n FROM DUAL;
  IF n > 0 THEN RAISE_APPLICATION_ERROR(-20006, 'The example account has ANNOUNCE, ADVERTISE_IMG or SALE_INFO rows. Rolled back.'); END IF;
  DELETE FROM MEMBER WHERE MEMBER_NO = v_member;
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK;
    RAISE;
END;
"""


def sql_remaining():
    acc = "(SELECT DISTINCT MEMBER_NO FROM EXAMPLE_STOCK)"
    ours = "(SELECT STOCK_NO FROM EXAMPLE_STOCK)"
    parts = [("STOCK_INFO", f"STOCK_NO IN {ours}"), ("SELL_DATE", f"STOCK_NO IN {ours}"),
             ("STOCK_COORD", f"STOCK_NO IN {ours}"), ("STOCK_IMG", f"STOCK_NO IN {ours}"),
             ("LIKE_STOCK", f"STOCK_NO IN {ours} OR MEMBER_NO IN {acc}"),
             ("STOCK_SAW", f"STOCK_NO IN {ours} OR MEMBER_NO IN {acc}"),
             ("MEMBER", f"MEMBER_NO IN {acc} OR MEMBER_EMAIL = '{ACCOUNT['email']}'"),
             ("TOKEN_INFO", f"MEMBER_NO IN {acc}"), ("BROKER_INFO", f"MEMBER_NO IN {acc}"),
             ("HELP_MESSAGE", f"SENDER_NO IN {acc} OR RECEIVER_NO IN {acc}")]
    sel = [f"SELECT '{t}' AS TABLE_NAME, COUNT(*) AS REMAINING FROM {t} WHERE {c}" for t, c in parts]
    sel.append(f"SELECT 'REHEARSAL_FAKE_MEMBER (rehearsal only, should stay 1)', COUNT(*) FROM MEMBER "
               f"WHERE MEMBER_EMAIL = '{FAKE_MEMBER_EMAIL}'")
    return ("-- 5. rows left after the rollback (before COMMIT). Every REMAINING must be 0,\n"
            "-- except the rehearsal fake member line, which stays 1 in the rehearsal and 0 in production.\n"
            + "\nUNION ALL\n".join(sel) + ";\n")


def sql_fake_member():
    em = FAKE_MEMBER_EMAIL
    return f"""DECLARE
  -- R. rehearsal only. A fake member who likes and has seen two example listings (plan section 9 step 5).
  -- Then the rollback has real LIKE_STOCK and STOCK_SAW rows to delete. COMMIT by hand after this block.
  -- If the fake member is already there (second round of the rehearsal), it is used again, not made twice.
  v_fake NUMBER;
  n NUMBER;
BEGIN
{guard(SCHEMAS["리허설"], "inserted")}
  SELECT COUNT(*) INTO n FROM MEMBER WHERE MEMBER_EMAIL = '{em}';
  IF n = 0 THEN
    INSERT INTO MEMBER (MEMBER_NO, MEMBER_EMAIL, MEMBER_PW, MEMBER_IMG, ENROLL_DATE, MEMBER_DEL_FL, MEMBER_LOGIN,
                        MEMBER_NICKNAME, MEMBER_NAME, MEMBER_AUTH, ACCESS_TOKEN, MEMBER_LOCATION, MEMBER_WITHDRAW_DATE)
    VALUES (SEQ_MEMBER.NEXTVAL, '{em}', NULL, NULL, SYSDATE, 'N', 'E', 'rehearsal-fake', NULL, 1, NULL, NULL, NULL)
    RETURNING MEMBER_NO INTO v_fake;
    INSERT INTO TOKEN_INFO (MEMBER_NO, TOKEN_INFO) VALUES (v_fake, NULL);
  ELSE
    SELECT MEMBER_NO INTO v_fake FROM MEMBER WHERE MEMBER_EMAIL = '{em}';
  END IF;
  INSERT INTO LIKE_STOCK (STOCK_NO, MEMBER_NO) SELECT STOCK_NO, v_fake FROM EXAMPLE_STOCK WHERE CSV_ROW IN (1, 2);
  INSERT INTO STOCK_SAW (STOCK_NO, MEMBER_NO) SELECT STOCK_NO, v_fake FROM EXAMPLE_STOCK WHERE CSV_ROW IN (1, 2);
END;
"""


def build(listings_path, coord_arg):
    rows = read_listings(listings_path)
    if coord_arg == "FAKE":
        coords, coord_sha, targets = fake_coords(), None, ["리허설"]
    else:
        coords, coord_sha = read_coords(coord_arg)
        targets = ["리허설"]
        if REAL_WGS84_SHA is not None and coord_sha == REAL_WGS84_SHA:
            targets.append("운영")
    check_coords(coords)
    out = {"1_줄수.sql": sql_counts(), "3_대조.sql": sql_verify(rows, coords), "5_남은줄.sql": sql_remaining()}
    for tgt in targets:
        schema = SCHEMAS[tgt]
        out[f"0_EXAMPLE_STOCK_만들기_{tgt}.sql"] = sql_example_stock(schema)
        out[f"2_넣기_{tgt}.sql"] = sql_insert(schema, rows, coords)
        out[f"4_되돌리기_{tgt}.sql"] = sql_rollback(schema)
        out[f"6_EXAMPLE_STOCK_지우기_{tgt}.sql"] = sql_drop_example_stock(schema)
    out["R_가짜회원_리허설.sql"] = sql_fake_member()
    if coord_arg == "FAKE":
        buf = io.StringIO()
        w = csv.writer(buf, lineterminator="\n")
        w.writerow(COORD_HEADER)
        for i in range(1, N_ROWS + 1):
            w.writerow([i, "FAKE", *coords[i]])
        out["fake_coord_v1.csv"] = buf.getvalue()
    for name, text in out.items():
        if not text.isascii():
            raise Stop(f"{name}: ASCII가 아닌 글자가 있다")
        if name.endswith(".sql") and re.search(r"[:&?]", text):
            raise Stop(f"{name}: DBeaver가 매개변수로 볼 글자(: & ?)가 있다")
    return out, coord_sha, targets


def main(argv):
    if len(argv) != 4:
        print(__doc__)
        return 2
    try:
        out, coord_sha, targets = build(argv[1], argv[2])
    except Stop as e:
        print(f"멈춤: {e}")
        return 1
    os.makedirs(argv[3], exist_ok=True)
    for name in sorted(out):
        data = out[name].encode("ascii")
        with open(os.path.join(argv[3], name), "wb") as f:
            f.write(data)
        print(f"{name} {len(data)}바이트 SHA-256 {sha256(data)}")
    kind = "가짜 좌표" if argv[2] == "FAKE" else f"좌표 표 SHA-256 {coord_sha}"
    print(f"끝: {kind}, 만든 것: {', '.join(targets)}" + ("" if "운영" in targets else " (운영용은 진짜 좌표 표에서만 나온다)"))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
