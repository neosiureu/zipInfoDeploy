# -*- coding: utf-8 -*-
"""얼린 UTM-K 좌표 표(utmk_v1.csv)를 WGS84 위경도로 바꿔 얼린다(4단계 계획 3절, 11/18 16:18 상대와 확정).

- 얼리는 값: pyproj(EPSG:5179 → EPSG:4326, always_xy=True)의 값만 소수 7자리로 반올림한다(format(x, ".7f")).
  always_xy=True가 없으면 EPSG:4326의 축 순서가 (위도, 경도)라 둘이 뒤바뀌기 쉽다.
- 대조: 직접 짠 공식(크뤼거 6차 급수 + 등각위도에서 위도로는 뉴턴 반복, GRS80)으로도 바꾼다.
  반올림 전 값의 차가 위도·경도 모두 1e-8도(약 1mm) 안이어야 한다. 반올림한 값끼리는 견주지 않는다.
- 범위: 위도 33~39, 경도 124~132 안이어야 한다. 위도와 경도가 바뀌면 반드시 벗어난다.
- 어긋나면 표를 쓰지 않고 멈춘다.

사용: python -I utmk_to_wgs84.py <utmk_v1.csv 또는 utmk_sgis_v1.csv> <내놓을 coord_wgs84_v1.csv>
입력은 행안부 표(entX, entY)나 SGIS 표(X, Y) 가운데 하나다. 둘 다 UTM-K(EPSG:5179)다.
"""
import csv
import hashlib
import io
import math
import os
import re
import sys

# UTM-K(EPSG:5179): 횡메르카토르, GRS80, 원점 위도 38, 중앙 경선 127.5, 축척 0.9996, 가산값 (1000000, 2000000)
A_GRS80 = 6378137.0
F_GRS80 = 1 / 298.257222101
K0 = 0.9996
LAT0, LON0 = 38.0, 127.5
FE, FN = 1000000.0, 2000000.0
TOL_DEG = 1e-8
LAT_RANGE = (33.0, 39.0)
LNG_RANGE = (124.0, 132.0)
IN_HEADER = ["row", "bdMgtSn", "admCd", "rnMgtSn", "udrtYn", "buldMnnm", "buldSlno",
             "coord_bdMgtSn", "entX", "entY"]  # 행안부 좌표제공 API 표(moiscoord.py)
IN_HEADER_SGIS = ["row", "bdMgtSn", "form", "dong", "by", "addr_type", "matching", "X", "Y"]  # SGIS 표(sgiscoord.py)
XY_COLUMNS = {tuple(IN_HEADER): ("entX", "entY"), tuple(IN_HEADER_SGIS): ("X", "Y")}
OUT_HEADER = ["row", "bdMgtSn", "lat", "lng"]
NUM = re.compile(r"[0-9]+(\.[0-9]+)?")
N_ROWS = 75


class Stop(RuntimeError):
    pass


def _series():
    n = F_GRS80 / (2 - F_GRS80)
    n2, n3, n4, n5, n6 = n ** 2, n ** 3, n ** 4, n ** 5, n ** 6
    big_a = A_GRS80 / (1 + n) * (1 + n2 / 4 + n4 / 64 + n6 / 256)
    alpha = [0.0,
             n / 2 - 2 * n2 / 3 + 5 * n3 / 16 + 41 * n4 / 180 - 127 * n5 / 288 + 7891 * n6 / 37800,
             13 * n2 / 48 - 3 * n3 / 5 + 557 * n4 / 1440 + 281 * n5 / 630 - 1983433 * n6 / 1935360,
             61 * n3 / 240 - 103 * n4 / 140 + 15061 * n5 / 26880 + 167603 * n6 / 181440,
             49561 * n4 / 161280 - 179 * n5 / 168 + 6601661 * n6 / 7257600,
             34729 * n5 / 80640 - 3418889 * n6 / 1995840,
             212378941 * n6 / 319334400]
    beta = [0.0,
            n / 2 - 2 * n2 / 3 + 37 * n3 / 96 - n4 / 360 - 81 * n5 / 512 + 96199 * n6 / 604800,
            n2 / 48 + n3 / 15 - 437 * n4 / 1440 + 46 * n5 / 105 - 1118711 * n6 / 3870720,
            17 * n3 / 480 - 37 * n4 / 840 - 209 * n5 / 4480 + 5569 * n6 / 90720,
            4397 * n4 / 161280 - 11 * n5 / 504 - 830251 * n6 / 7257600,
            4583 * n5 / 161280 - 108847 * n6 / 3991680,
            20648693 * n6 / 638668800]
    e2 = F_GRS80 * (2 - F_GRS80)
    return big_a, alpha, beta, e2


def _conformal_tan(tau, e):
    """tan(위도) → tan(등각위도)."""
    sig = math.sinh(e * math.atanh(e * tau / math.hypot(1.0, tau)))
    return tau * math.hypot(1.0, sig) - sig * math.hypot(1.0, tau)


def _geodetic_tan(taup, e2):
    """tan(등각위도) → tan(위도). 뉴턴 반복(Karney 2011)."""
    e = math.sqrt(e2)
    tau = taup
    for _ in range(10):
        tp = _conformal_tan(tau, e)
        d = (taup - tp) * (1 + (1 - e2) * tau * tau) / ((1 - e2) * math.hypot(1.0, tau) * math.hypot(1.0, tp))
        tau += d
        if abs(d) <= 1e-15 * max(1.0, abs(tau)):
            break
    return tau


def kruger_inverse(east, north):
    """직접 짠 공식. (위도, 경도) 도."""
    big_a, alpha, beta, e2 = _series()
    e = math.sqrt(e2)
    # 원점 위도의 ξ0(정방향 급수, 경도차 0)
    xi0p = math.atan(_conformal_tan(math.tan(math.radians(LAT0)), e))
    xi0 = xi0p + sum(alpha[j] * math.sin(2 * j * xi0p) for j in range(1, 7))
    xi = (north - FN) / (K0 * big_a) + xi0
    eta = (east - FE) / (K0 * big_a)
    xip = xi - sum(beta[j] * math.sin(2 * j * xi) * math.cosh(2 * j * eta) for j in range(1, 7))
    etap = eta - sum(beta[j] * math.cos(2 * j * xi) * math.sinh(2 * j * eta) for j in range(1, 7))
    taup = math.sin(xip) / math.hypot(math.sinh(etap), math.cos(xip))
    tau = _geodetic_tan(taup, e2)
    lat = math.degrees(math.atan(tau))
    lng = LON0 + math.degrees(math.atan2(math.sinh(etap), math.cos(xip)))
    return lat, lng


def pyproj_transformer():
    from pyproj import Transformer
    return Transformer.from_crs("EPSG:5179", "EPSG:4326", always_xy=True)


def convert(table_bytes, transformer=None, independent=kruger_inverse):
    """(내놓을 표 bytes, 보고 dict). 어긋나면 Stop."""
    t = transformer or pyproj_transformer()
    rows = list(csv.reader(io.StringIO(table_bytes.decode("utf-8"), newline="")))
    if not rows or tuple(rows[0]) not in XY_COLUMNS:
        raise Stop("utmk 표의 머리줄이 다르다")
    header = rows[0]
    xcol, ycol = XY_COLUMNS[tuple(header)]
    body = rows[1:]
    if len(body) != N_ROWS or [r[0] for r in body] != [str(i) for i in range(1, N_ROWS + 1)]:
        raise Stop("utmk 표가 1~75줄 차례가 아니다")
    out = []
    max_dlat = max_dlng = 0.0
    for r in body:
        if len(r) != len(header):
            raise Stop(f"r{int(r[0]):02d}: 칸 수가 다르다")
        rec = dict(zip(header, r))
        label = f"r{int(rec['row']):02d}"
        for k in (xcol, ycol):
            if not NUM.fullmatch(rec[k]):
                raise Stop(f"{label}: {k} 꼴이 다르다")
        east, north = float(rec[xcol]), float(rec[ycol])
        lng, lat = t.transform(east, north)  # always_xy=True: (경도, 위도)
        lat2, lng2 = independent(east, north)
        dlat, dlng = abs(lat - lat2), abs(lng - lng2)
        max_dlat, max_dlng = max(max_dlat, dlat), max(max_dlng, dlng)
        if not (dlat <= TOL_DEG and dlng <= TOL_DEG):
            raise Stop(f"{label}: 두 방법의 차가 1e-8도를 넘는다(위도 {dlat:.3e}, 경도 {dlng:.3e})")
        if not (LAT_RANGE[0] <= lat <= LAT_RANGE[1] and LNG_RANGE[0] <= lng <= LNG_RANGE[1]):
            raise Stop(f"{label}: 위경도가 범위(위도 33~39, 경도 124~132) 밖이다")
        out.append([rec["row"], rec["bdMgtSn"], format(lat, ".7f"), format(lng, ".7f")])
    buf = io.StringIO()
    w = csv.writer(buf, lineterminator="\n")
    w.writerow(OUT_HEADER)
    w.writerows(out)
    data = buf.getvalue().encode("utf-8")
    report = {"max_dlat": max_dlat, "max_dlng": max_dlng, "rows": len(out),
              "transformer": getattr(t, "description", ""), "definition": getattr(t, "definition", "")}
    return data, report


def sha(b):
    return hashlib.sha256(b).hexdigest().upper()


def main(argv):
    if len(argv) != 3:
        print(__doc__)
        return 2
    src, dst = argv[1], argv[2]
    with open(src, "rb") as f:
        inp = f.read()
    try:
        data, rep = convert(inp)
    except Stop as e:
        print(f"멈춤: {e}")
        return 1
    if os.path.exists(dst):
        with open(dst, "rb") as f:
            if f.read() != data:
                print(f"멈춤: {os.path.basename(dst)}가 이미 있는데 다시 만든 것과 바이트가 다르다")
                return 1
    else:
        with open(dst, "wb") as f:
            f.write(data)
    import pyproj
    print(f"python {sys.version.split()[0]}, pyproj {pyproj.__version__}, PROJ {pyproj.proj_version_str}")
    print(f"변환: {rep['transformer']}")
    print(f"파이프라인: {rep['definition']}")
    print(f"입력 {os.path.basename(src)} SHA-256 {sha(inp)}")
    print(f"두 방법의 가장 큰 차: 위도 {rep['max_dlat']:.3e}도, 경도 {rep['max_dlng']:.3e}도 (허용 1e-8도)")
    print(f"끝: {rep['rows']}줄. {os.path.basename(dst)} SHA-256 {sha(data)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
