# 순서 2 데이터 준비 (`experiment/prep`)

만들기 설계안 4판 5절(5.1~5.4)의 파이썬 쪽이다.

- 3단계 `experiment/picker`의 읽기·다듬기 코드를 그대로 불러 쓴다(06 규칙 3.1). 그래서 도구와 정답이 picker와 같은 거래를 본다.
- 표준 라이브러리만 쓴다. 파이썬 3.11·3.12·3.13에서 시험했고, 세 판의 결과 바이트가 같았다(사용자 PC는 3.12).
- 화면에는 셈한 수, 지역 코드, 해시만 낸다. 거래 줄, 매물 이름·주소, 단지ID, 키는 내지 않는다.

## 명령

`experiment` 폴더에서 돌린다.

```
python -X utf8 -B -m prep test
python -X utf8 -B -m prep build --snap <snapshot_T.zip> --codes <codes_snapshot.txt>
python -X utf8 -B -m prep check --snap <snapshot_T.zip> --codes <codes_snapshot.txt>
python -X utf8 -B -m prep freeze-juso
python -X utf8 -B -m prep answers
python -X utf8 -B -m prep questions
```

- 매물 파일(`--listings`)의 기본값은 `frozen/example_listings_v1.csv`다. 3단계에서 얼린 것이고, 5단계에 커밋한다.
- 들어가는 파일 셋은 SHA-256으로 못박았다(`cli.py`의 `PINS`). 다르면 아무것도 쓰지 않고 멈춘다.
    - `snapshot_T.zip`: `0EB3F0BE…0AC1`(릴리스 `snapshot-v1`)
    - `codes_snapshot.txt`: `0BC23755…E496`
    - `example_listings_v1.csv`: `FFB838C7…EBF4`

## build가 만드는 것

`prep/out/`에 쓴다. 이 폴더는 커밋하지 않는다(`.gitignore`).

| 파일 | 설계안 | 내용 |
|---|---|---|
| `replay_trades_v1.csv` | 5.1 | 다듬은 거래 한 줄씩(767,597줄, 115,625,213바이트) |
| `replay_bundles_v1.csv` | 5.1 | (API, 코드, 달) 6,048묶음. 0건 묶음도 있다. 목록에 없는 부르기는 SNAPSHOT_MISS다 |
| `validity_v1.csv` | 5.2 | 영역 × API × 달 × 후보 코드마다 건수, 판정, 처방 |
| `listings_input_v1.json` | 5.4 | 매물 75개의 아홉 칸과 근거 ID |
| `juso_queries_v1.csv` | 5.3 | 주소 얼리기의 검색어 75개 |

- 모두 UTF-8(BOM 없음)과 LF로 쓴다. 같은 입력이면 같은 바이트가 나온다.
- 기대 해시는 `expected_v1.txt`에 있다. `check`가 이것과 견준다.

### 거래 파일의 칸

| 칸 | 뜻 |
|---|---|
| `api`, `resp`, `ym` | 부른 API, 부른 코드(응답 코드), 받은 달. 계약 달이 받은 달과 다른 거래가 나오면 멈추므로 `ym`이 곧 계약 달이다 |
| `page`, `pos` | 쪽, 쪽 안 차례(0부터). 줄 차례는 받은 차례(API, 코드, 달, 쪽, 쪽 안 차례)다 |
| `day`, `deal` | 계약일, 거래(매매·전세·월세) |
| `complex_id`, `cur10`, `path` | 단지ID(종류\|현재 시군구\|현재 법정동\|산\|본번\|부번), 현재 법정동 코드, 현재 법정동 이름(리는 "읍면 리") |
| `jibun`, `name` | 지번 글, 단지 이름 |
| `area`, `floor`, `build_year` | 전용면적(받은 소수 글 그대로), 층, 건축년도 |
| `amount` / `deposit`, `monthly_rent` | 만원 정수. 매매는 거래금액, 전월세는 보증금과 월세 |
| `raw_umd` | 받은 법정동 이름 |

- 다듬기에서 빠진 거래는 없다. 코드 사이 중복은 지우지 않았다. 지우는 법은 picker와 같다(코드별 건수 가운데 큰 값).

### 유효성 표의 칸

| 칸 | 뜻 |
|---|---|
| `area` | 영역. 매물의 현재 시군구다 |
| `api`, `ym` | API, 달 |
| `code` | 후보 코드. 영역의 현재 법정동들이 창 안에서 속했던 5자리 코드다(picker `History.candidates`) |
| `count` | 그 코드로 받은 영역 거래 수. 코드 사이 중복을 지우기 전이다 |
| `valid`, `prescription` | 유효 1, 무효 0. 무효면 쓸 코드 |
| `case` | `single`(후보 하나), `one`(건이 있는 후보 하나: 그것만 유효), `true_zero`(모두 0건: 모두 유효) |

- 건이 있는 후보가 둘 이상인 달이 나오면 멈춘다.
- 후보가 아닌 코드는 표에 없다. 그 매물에는 늘 무효이고, 처방은 영역 코드다(01 298줄). 도구와 ②가 그렇게 읽는다.

## check가 보는 것

하나라도 어긋나면 종료 코드 1을 낸다.

1. 결과 다섯의 SHA-256과 바이트 수가 `expected_v1.txt`와 같다.
2. 묶음마다 받은 건수가 스냅샷의 `frozen/manifest_T.csv`(totalCount와 items)와 같다. 받은 = 남긴 + 뺀 + 못 정함이다.
3. picker(`pick_all`)와 단지마다 매매·전세·월세 거래가 같다.
4. 저장소만으로 매물 75개를 picker `model.listing`(06 규칙 6장)대로 다시 만들면, 얼린 매물 파일과 주소 칸 빼고 18칸이 같다.
5. 저장소만으로 센 T1 범위(12달, N 5)가 단지 35, 법정동 38, 시군구 2다.
6. 유효성 표
    - 칸(영역, API, 달)마다 후보 코드가 차례대로 하나씩 있다.
    - 매물의 API에서는 정말 0건인 달이 없고, 옛 코드는 무효, 현재 코드는 유효다.
    - 무효 칸의 처방은 모두 영역 코드다.
    - 1:N 옛 코드(28110, 28260, 41590)는 영역마다 그 영역 코드로 처방한다. 매물의 법정동을 따른다는 뜻이다(설계안 5.2).
7. 매물 입력은 아홉 칸과 근거 ID 넷뿐이고 값이 매물 파일과 같다. 검색어는 75개이고 겹치지 않는다.

## freeze-juso

사용자 PC에서 한 번 돌린다(설계안 5.3).

- 매물 75개의 검색어마다 도로명주소 API를 한 번씩 부르고, 받은 결과 전부를 `frozen/juso_road_v1.csv`에 얼린다.
    - 검색어는 주소 칸에서 우편번호와 `^^^`를 떼고, 앞뒤·겹 빈칸을 정리하고 NFC로 맞춘 것이다.
    - 부르기 전에 검색어 목록의 SHA-256을 `expected_v1.txt`와 대 본다.
- 얼리기 꼴은 `picker/juso.py`와 같다(검색어, 쪽, 차례, 부른 시각, 결과 수, 칸).
    - 요청 값, 주소, 다시 부르기도 그 모듈의 것을 쓴다.
    - 칸만 다르다. 에이전트에 보일 열한 칸이다: admCd, 우편번호, 도로명주소, 시도·시군구·읍면동·리 이름, 산·본번·부번, 건물관리번호.
- 끊기면 같은 명령으로 이어 부른다. 얼린 검색어는 다시 부르지 않는다.
- 키는 환경 변수 `JUSO_KEY`에서만 읽고, 어디에도 쓰거나 찍지 않는다.
- 결과 파일은 매물 파일과 함께 5단계에 커밋한다. 그때까지는 `.git/info/exclude`로 뺀다.

## answers (순서 3)

설계안 9.3 "같은 식을 두 곳에"의 파이썬 쪽이다(`answers.py`).

- 문항마다(9.2 배정, T3는 1·3·6달을 모두) 맞는 부르기(API, 코드, 달, 거래, 법정동, 지번)와 정답 값(표본 수, 기준값, 환산, 차이, T5 건수)을 `prep/out/answers_check_v1.json`에 쓴다.
- 하네스 시험 `AnswersCrossCheckTest`가 이 파일의 부르기를 Java 도구에 그대로 넣어 값이 같은지 본다.
- build를 먼저 돌려야 한다(재생 저장소의 해시를 대 본다).

## questions (순서 7)

순서 7 설계 c판의 문항 만들기다(`questions.py`). 꼴과 규칙은 `experiment/questions/README.md`에 있다.

- 틀 문장 파일(`questions/templates_v0.json`)을 읽어 검사한다. 하나라도 걸리면 걸린 것을 모두 찍고 아무것도 쓰지 않는다.
- 문항 파일 `questions/questions_v0.json`과 문항별 정답 `prep/out/answers_v0.json`을 쓴다.
    - 정답은 대조용 정답(`answers_check_v1.json`)의 줄이다. 지금 셈이 그 파일과 다르면 멈춘다.
    - 정답 파일에는 문항 파일의 SHA-256(줄 끝 LF로 맞춤)을 적는다.
- build와 answers를 먼저 돌려야 한다(해시를 대 본다).
- 시험(`tests/test_questions.py`)은 지어낸 틀 문장과 매물로 본다.
