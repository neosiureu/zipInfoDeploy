# 4단계: 예시 매물을 사이트에 넣은 기록 (2026-11-18 ~ 11-19)

예시 매물 규칙(`experiment/example_listing_rule_v1.md`, 사이트 칸은 v2)으로 만든 예시 매물 75개를 집인포 운영 DB에 넣었다. 이 폴더는 그 좌표, SQL, 결과다.

좌표: 국가데이터처 SGIS 지오코딩(공공누리 제1유형)

## 순서와 커밋

| | 한 일 | 커밋 |
|---|---|---|
| ⑦ | 규칙 v2, 그림 셋(`images/`), v1→v2 diff | `2669aa8` |
| ④ | SGIS 지오코딩으로 좌표 받기(11/19 09:08~09:12 KST), 판정, WGS84로 바꾸기 | 이 커밋 |
| ⑤⑥ | 넣기·대조·되돌리기 SQL 만들기, 로컬 Oracle에서 리허설 | 이 커밋 |
| ⑧ | 운영 DB에 넣기: 14:42:26 KST 넣고 14:44:39 KST 커밋 | 이 커밋 |

## 폴더

- `coord/`: 좌표
    - `frozen_sgiscoord/`: SGIS 응답 327개(꼴 다섯: full, nosido, old, jibun, old_jibun)를 받은 그대로 얼린 것. `calls_v1.csv`에 응답마다 크기와 SHA-256이 있다. `judge_v1.csv`는 그때의 판정(한 줄 실패)이다.
    - `rejudge_6b/`: 법정동 "다름" 덧붙임(응답 코드가 건물관리번호 앞 10자리와 같고 도로명·건물번호가 맞음)으로 다시 판정한 표. 다시 부르지 않았다. 75줄 통과.
    - `coord_wgs84_v1.csv`: UTM-K(EPSG:5179)를 WGS84(EPSG:4326)로 바꾼 표. 넣는 SQL은 이 표의 SHA-256이 맞을 때만 나온다. 바꾼 기록은 `변환기록.txt`.
    - `sgiscoord.py`, `utmk_to_wgs84.py`와 시험
- `sql_gen/`: SQL
    - `make_insert_sql.py`와 시험. 같은 입력이면 같은 바이트의 SQL이 나온다.
    - `out_real/`: 만든 SQL 12개. 운영에 쓴 것은 `_운영` 넷과 `1_줄수`, `3_대조`, `5_남은줄`이다. `_리허설`은 로컬 리허설에 쓴 것이고, 짝마다 스키마 확인 한 줄만 다르다(`out_real_리허설_운영_diff.txt`).
- `results/`: 결과
    - `운영_결과_1119.txt`: 운영에 넣을 때의 순서, 시각, 줄 수, 대조, 화면
    - `리허설_결과_1119.txt`: 리허설 결과
    - `example_stock_map_1119.tsv`: 넣은 뒤의 대응표(사이트 매물 번호 STOCK_NO와 CSV 줄 번호). 운영 DB의 EXAMPLE_STOCK을 내보낸 것이다.
- `해시목록_4단계.txt`: 이 커밋의 파일마다 SHA-256

## 다시 해 보기

입력은 `experiment/frozen/juso_jibun_v1.csv`(3단계 커밋)와 `experiment/frozen/example_listings_v1.csv`(5단계에 커밋)다.

```
python coord/sgiscoord.py rejudge <example_listings_v1.csv> <juso_jibun_v1.csv> coord/frozen_sgiscoord <빈 폴더>
python coord/utmk_to_wgs84.py <빈 폴더>/utmk_sgis_v1.csv <wgs84.csv>
python sql_gen/make_insert_sql.py <example_listings_v1.csv> <wgs84.csv> <빈 폴더>
```

- 셋 모두 이 폴더의 파일과 같은 바이트가 나와야 한다.
- 시험은 환경 변수 `LISTINGS`(예시 매물 파일)와 `JUSO`(도로명주소 파일)를 쓴다.
- `sql_gen/test_make_insert_sql.py`의 칸 길이 확인(`test_values_fit_production_columns`)은 운영 DB의 DDL과 리허설 스키마 스크립트를 읽는다. 둘은 저장소에 넣지 않았으므로, 이 저장소만으로는 그 하나가 오류로 끝나고 나머지 여덟은 통과한다. 그 확인은 넣기 전에 했다(9개 통과).

## 5단계에 더한 것 (2026-11-25)

- `results/운영_결과_1119.txt` 62줄은 지도에서 시군구 묶음 이름이 "null"로 보이는 까닭을 운영 DB의 지역 표로 적었다. 실제 까닭은 프런트엔드의 지역 표다. 지도는 `zipinfo-project/frontend/src/components/stock/StockPageCopy.jsx`의 `getRegionName`으로 `zipinfo-project/frontend/src/components/common/Gonggong.jsx`의 `TOWN`에서 시군구 이름을 찾는데, 예시 매물이 있는 시군구 16곳 가운데 2026년에 새로 생긴 12곳(예시 매물 51개)의 코드가 이 표에 없다. 결과 파일은 그때 적은 그대로 둔다.
- `coord/frozen_sgiscoord/`의 응답은 `sgiscoord.py` 3e 판(SHA-256 `0FB921F6614A719F9C6AFEB44E7647144FD720700FDDE952A363A9928CC22A73`)으로 받았다. 저장소의 `coord/sgiscoord.py`는 6b 판이다. 두 판이 다른 곳은 판정(`check_item`), `run`·`main`의 rejudge 갈래와 끝 줄의 "다름" 수, 파일 머리의 설명뿐이고, API를 부르는 부분은 같다(AST로 대 봄).
