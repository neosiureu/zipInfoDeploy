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
python -X utf8 -B -m prep score harness/settings/<설정>.json
python -X utf8 -B -m prep mistakes harness/settings/<설정>.json harness/frozen/mistakes_v0.json
python -X utf8 -B -m prep judge-sample harness/settings/<설정>.json
python -X utf8 -B -m prep judge-tally harness/settings/<설정>.json
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

## score (순서 8a·8b·8c, 채점 v1은 순서 11b-1)

순서 8 설계 d판의 채점기(`score.py`), 8b 설계 2판의 지표(`metrics.py`), 8c 설계 2판의 통계(`stats.py`)다. 하네스가 한 단계를 다 돌고 판정(judge)까지 0으로 끝난 뒤에 친다.
순서 11b-1(설계 4판 4.2)부터 채점 v1이다(채점 설정 `score_v1.json`, 글 검사 `numerals.py`). `score_v0.json`은 11a의 기록으로 남긴다(지금 코드는 v0 설정을 읽지 않는다).

```
python -X utf8 -B -m prep score harness/settings/pilot_v0.json
```

- **채점 바로 전에 하네스 `plan`과 `summary`를 같은 설정으로 친다.** 채점기는 plan.txt의 판 지문으로 기록을 묶고, summary.tsv를 같은 셈으로 대 본다.
- **묶기 검사(2.2):** 하나라도 걸리면 아무것도 쓰지 않고 3으로 끝난다. 설정·파일을 못 읽으면 2다.
    - 정답 파일(`prep/out/answers_<문항 파일 version>.json`)의 `questions_sha256`과 문항 ID·키·유형·나눔이 문항 파일과 같다.
    - plan.txt의 단계·나눔·설정 판, 판·문항·회차·블록·실행 수가 파이썬이 짠 계획(하네스 `Plan`과 같은 셈)과 같다. 기록 문제·지문이 다른 기록·남은 실행이 없다.
    - 기록마다: 파일 이름 = 실행 ID, 머리의 stage·settings_version·문항이 설정·문항 파일과 같다, 실행 지문 = SHA-256(plan.txt의 판 지문 + 키 + 질문 글), 끝이 있고 STOP_*가 아니다.
    - 계획의 실행이 모두 기록에 있고, 계획 밖 기록이 없다.
    - SUBMITTED 실행마다 (단계, 실행 ID, 기록 해시)가 같은 판정 줄이 꼭 하나다. 기록 해시는 줄 끝을 LF로 맞춘 SHA-256이다. 판정 지문은 하나다.
    - plan.txt의 끝난 것·시작한 것이 계획과 맞지 않으면(남은 것 0인데) 계획 밖 기록이 있다고 말한다(하네스 Runner.status는 폴더의 모든 기록을 센다).
    - **8b:** 하네스 `summary.tsv`가 있고, 채점기가 같은 셈(하네스 Summary와 같은 double 덧셈 차례, `BigDecimal.valueOf → HALF_UP`, 비용은 Decimal)으로 지은 것과 글자까지 같다.
- **매기기(3절):** 항목(유형·거래·표본으로 정함)마다 대표 claim의 값과 규칙, 답 전체의 규칙(T2 넓히지 않음·금지 표현·방향 말·출처·검사기 깨끗), 판정.
    - 완전 정답 v1 = SUBMITTED, 값 통과, 규칙 v1 통과, (채점 설정 `full_with_judge`가 true이면) 판정 "답함".
    - **글 검사 v1(`numerals.py`):** 검사기 깨끗은 기록의 findings에서 NUMERAL·PERIOD_WORD·PLACEHOLDER_FORM을 버리고, 받아들여진 제출의 원래 글(그리기 전)에서 다시 센 것으로 채운다. 나머지 findings는 기록 그대로다.
        - PLACEHOLDER_FORM: 바른 자리표시자 `{한글 1~3자}`·`{한글 1~3자.방향}`을 다 지운 뒤 `{`나 `}`가 남음
        - 수사 사전 v1: v0(자바 `Numerals` v0)에 고유어 십 단위(열·스물·…·아흔, 뒤에 한~아홉이 붙는 꼴도), 어림수(한두·두세·서너 등), 십·백·천 홑자, 단위 제곱미터·㎡·해, 일 년·일년을 더했다. 바른 자리표시자는 빈칸으로 바꾼 글에서 센다.
        - 기간 말(수사 + 달·개월·년·해, 반년, 일 년)은 같은 길이(달 수, 양 끝 달을 넣어 셈)의 통계 claim이 있으면 받고, 없으면 PERIOD_WORD다. 그 밖의 수사는 NUMERAL이다.
    - 정의 셋(모두 사전 v1, 모두 PLACEHOLDER_FORM을 본다): 주 (다) = 위와 같다(`full`), 비교 (가) = 기간 말까지 모든 수사가 실패(`full_all_numerals`), 비교 (나) = 수사를 모두 뺀다(`full_no_numeral`).
    - 판정을 완전 정답에서 빼면(`full_with_judge` false) 판정 코드(JUDGE_NOT_ANSWERED·JUDGE_MISSING)를 실패 코드에 넣지 않는다. 판정 답함 수는 그대로 낸다.
    - 받아들여진 제출마다 `text_check`(자리표시자 꼴·잘린 이름·기간 말 [길이, 맞음]·그 밖의 수사 수·v0 수와 기록 findings의 NUMERAL 수·방향 자리 뒤 말 갈래)를 runs.jsonl에 수로만 남긴다. 글은 남기지 않는다.
    - 같은 예문 묶음 `tests/data/numerals_v1.json`(손으로 정한 기대값)은 11b-2의 자바 검사기도 쓴다. 시험은 `tests/test_numerals.py`다.
    - 실패 코드와 진단 꼬리표(FALSE_NO_TRADE, 규칙 실패로 세지 않음)를 실행마다 남긴다.
- **8b 지표(`metrics.py`, 8b 설계 2판 3~7절)**
    - 기록의 고리 사건에서 부름과 hook을 묶는다. 데이터 도구는 tool 사건 바로 앞의 hook, 제출은 submit 사건 바로 뒤의 hook이다(하네스 사건 resume·attempt·pause·end는 뺀다). 묶이지 않으면 멈춘다.
    - 받은 거절은 묶음에서 처음 REJECT인 부품(모델에 간 것), 자기 거절은 부품마다의 REJECT다. ⓪의 declare_intents 거절은 `⓪선언`으로 따로 센다.
    - 15.9 지표: 첫 시도 CI 통과, 거절 뒤 자력 완료·완전 정답(부품마다), 같은 실수 재발(표가 있을 때), 근거 없는 완료 보고(claim), 소요시간, 부당 거절(⓪②③④, ③은 면적 따로, ⓪은 틀마다).
    - ④의 부당 거절은 멀쩡한 제출(그 submit까지의 사건으로 매겨 값이 맞고 주 정의 (다)의 규칙을 지남, 11b-1) 가운데 ④가 막은 것이다. ⓪은 거절 결과의 「…」 절이 모두 의도 절 표의 '의도 아님'일 때다(표에 없는 절이 나오면 멈춘다).
    - 비용은 설정의 단가표로 실행마다 캐시 있음·없음을 센다(하네스 Prices와 같은 식). 진단은 표본 0 부름, 도구 오류 코드, refusal 범주, 재촉, 턴, 받은·자기 거절, ④ 없는 판의 받은 답 findings, 머리의 단가표가 다른 기록이다.
    - 11b-1 진단(글 검사 v1, 판마다): PLACEHOLDER_FORM 제출과 그 가운데 잘린 이름, 기간 말 맞음·안 맞음(안 맞은 길이마다), PERIOD_WORD·NUMERAL 제출, 수사 v0 수와 기록 findings와 수가 다른 제출, 방향 자리 뒤 말 갈래(이다·입니다·이고·이며·편·고·문장부호·끝·표에 없음).
- **8c 통계(`stats.py`, 8c 설계 2판 2~7절)**
    - 판의 값은 문항마다 회차 평균의 평균이고, 비교는 같은 문항끼리 짝지은 차이다. 점 추정값은 원래 표본으로 낸다.
    - 구간은 층(매물 파일의 `layer`) 안 매물 묶음 부트스트랩의 95%다. 뽑기 b·층 L·j마다 그 층의 매물을 키 차례로 세운 목록에서 0부터 센 `u64(H(purpose, "b|L|j")) mod n_L` 자리를 뽑는다(`picker.common`의 H·u64). 모든 판·비교·칸·결과가 같은 뽑기를 쓴다.
    - 칸이 빈 뽑기는 빼고 센다. 구간은 남은 B′개 값의 ⌈B′/40⌉번째와 B′ + 1 − 그 차례번째다(B′ = 10,000이면 250·9,751번째).
    - 칸: 전체, 층, 유형, ⑤ 나눔(본시험 단계만), AI 틀 뺌(틀 문장 파일이 있을 때). 결과: 완전 정답(주 (다)), 수사 모두 실패((가)), 수사 뺀((나)), 판정 없는 실행 뺀 결과(`unjudged`가 `fail`이고 판정을 완전 정답에 넣고 그런 실행이 있을 때). 소요시간 비교는 캐시를 모두 끈 단계에서만 낸다.
    - 모두 정수와 분수로 센다. 쓸 때만 반올림한다(HALF_UP, 동점은 0에서 먼 쪽). 본시험 크기(7,500실행, 뽑기 1만 번)에서 몇 초다.
    - 사전 등록 글 초안은 `prereg_score_v0.md`다. [ ] 칸은 관문 3에서 채운다.
- **채점 설정 `score_v1.json`:** 판정 없는 SUBMITTED의 처리(`unjudged`: `stop`이 기본, `fail`은 완전 정답이 아님으로 셈), 완전 정답에 판정을 넣는지(`full_with_judge`, 처음은 true. prereg_judge 2.1을 못 넘으면 false), 방향 말 낱말, 같은 실수 표 자리(`mistakes`, 관문 3까지 null), ⓪ 의도 절 표 자리(`intent_clauses`), 판정 표본에서 뺄 옛 표본(`judge_sample_exclude`: {단계: [그 단계 폴더 아래 폴더 이름]}), 부트스트랩(`bootstrap`: 뽑기 수 10000, purpose `boot`), 비교 목록(`comparisons`: 주 가설 c09 − c02와 보조 일곱). 하네스 설정 파일에는 칸을 더할 수 없어 따로 둔다.
    - 비교 목록의 판 ID가 판 설정 파일에 없으면 2로 멈춘다. 판 설정 파일에는 있지만 그 단계에 없는 판이 든 비교는 "-"다.
    - 의도 절 표(`questions/intent_clauses_v0.json`)는 틀 문장 파일처럼 관문 3까지 git 밖이다. 표나 틀 문장 파일이 없으면 ⓪ 부당 거절만 "-"이고 한 줄을 찍는다.
    - 같은 실수 표의 단계가 매기는 단계와 같으면 재발은 "-"이고 한 줄을 찍는다(제 표로 저를 재지 않는다).
- **채점 지문:** "이름  해시" 줄(code, score, answers, questions, k10, templates, 쓴 표 intent_clauses·mistakes)을 이은 글의 SHA-256이다. code는 prep·picker의 `.py`(tests 빼고)를 하네스 `Fingerprint.tree`와 같은 꼴로 센 값이다. 줄 끝을 LF로 맞춰 세므로 Windows와 리눅스에서 같다.
- **남기는 것:** `<files.out>/<stage>/score/`의 `runs.jsonl`(계획의 실행마다 한 줄), `summary.tsv`(판마다 한 줄, 채점 지문·판정 지문), `metrics.tsv`(판·갈래·이름·분자·분모), `stats.tsv`(갈래·이름·결과·칸·문항·값·하한·상한·값(분수)·빈 뽑기), `report.md`(사람이 읽는 표). runs 아래라 git 밖이다.
    - 질문·답·판정 이유·매물 이름·⓪의 절 글은 쓰지 않는다. 실행 ID, 문항 ID·키, 코드, 수만 쓴다.
    - `score.tmp/`에 다 쓴 뒤 옛 `score/`와 바꾼다. 같은 입력이면 같은 바이트다(파이썬 3.11·3.12·3.13, CRLF로 꺼낸 기록에서도).
    - 바꾸기가 실패하면(엑셀이 파일을 연 때 등) 2로 멈추고, 이번에 옮긴 옛 `score/`만 되돌린다. 마지막 `score.old` 지우기만 실패하면 0으로 끝내고 한 줄을 찍는다.
- **틀 문장 파일이 있으면** 문항마다 틀 번호를 `questions.build`로 다시 낸다(다시 지은 글이 문항 파일과 다르면 멈춘다).
- **시험(`tests/test_score.py`, 8b 지표는 `tests/test_metrics.py`, 8c 통계는 `tests/test_stats.py`)**
    - 시험 자료로 매길 때는 시험용 채점 설정(`test_score.fx_config`: 뽑기 200번, 비교는 시험 자료의 판)을 쓴다. 뽑기 1만 번은 검산에서 본다.
    - `tests/data/score_fx`는 하네스 시험 `ScoreFixtureTest`가 가짜 모델·배치 서버로 지은 단계 폴더다(기록 10개: ⓪·②·③·④ 판 포함, plan.txt, summary.tsv, 판정 파일). 매물·거래는 하네스 시험의 지어낸 자료다. 여기에 지어낸 정답·매물 파일과 K10을 더해 끝까지 매기고, 한 곳씩 바꿔 묶기 검사를 본다.
    - 하네스가 쓰는 꼴이 바뀌면 `ScoreFixtureTest`가 걸린다. 그때는 `experiment/harness`에서 환경 변수 `SCORE_FIXTURE_OUT`에 이 폴더(`…/experiment/prep/tests/data/score_fx`)를 주고 그 시험을 다시 돌려 새로 짓는다.
    - 실행 하나 매기기는 같은 꼴의 사건을 지어 한 곳씩 바꿔 본다.
    - 실제 문항·정답 파일이 있으면 항목 수를 찍는다(`채점 항목 검사: …`). 의도 절 표는 하네스 시험 `IntentClausesCheckTest`가 ⓪이 나누는 절과 대 본다(표가 있을 때만).

## mistakes (순서 8b)

같은 실수 표를 얼린다(8b 설계 2판 4.3). 관문 3에 연습 단계(시운전)로 한 번 친다.

```
python -X utf8 -B -m prep mistakes harness/settings/pilot_v0.json harness/frozen/mistakes_v0.json
```

- 그 단계를 score와 같은 함수로 묶기 검사하고 매긴 뒤 표를 쓴다. 이미 있으면 쓰지 않고 2로 끝난다(얼린 것은 덮어쓰지 않는다).
- 꼴: `{"version": "v0", "stage", "score"(채점 지문), "judge"(판정 지문), "runs", "codes": {코드: 실행 수}, "code_areas": {"코드|시군구 번호": 실행 수}}`. UTF-8, LF, 키 차례.
    - 유형은 runs.jsonl의 codes다(판정 없음 JUDGE_MISSING은 뺀다). 진단 꼬리표는 넣지 않는다. 판정을 완전 정답에서 뺀 채점(`full_with_judge` false)이면 판정 코드는 codes에 없어 표에도 들지 않는다(11b-1).
    - 시군구 번호는 매물 파일의 현재 시군구 코드를 오름차순으로 세운 차례(1부터)다. 코드 값은 쓰지 않는다.
- 쓰려면 채점 설정 `mistakes`에 그 자리를 적는다. 표 파일의 해시가 채점 지문에 든다.

## judge-sample·judge-tally (순서 11a)

판정 표본을 사람이 읽어 판정과 맞는 비율을 잰다(`harness/prereg_judge_v0.md` 2.1절). 판정과 채점이 끝난 단계에 친다.

```
python -X utf8 -B -m prep judge-sample harness/settings/pilot_v0.json
python -X utf8 -B -m prep judge-tally harness/settings/pilot_v0.json
```

- 둘 다 score와 같은 함수로 묶기 검사하고 매긴다. 걸리면 아무것도 쓰지 않고 3으로 끝난다.
- judge-sample: 표본 A(판정 있는 실행을 `H("judge_a", 실행 ID)` 순으로 앞 40개)와 표본 B(값·규칙을 지났는데 판정이 답하지 않음인 실행을 `H("judge_b", 실행 ID)` 순으로 앞 20개)를 합쳐 `H("judge_order", 실행 ID)` 순으로 세운다. `<files.out>/<단계>/judge_sample/sample.txt`에 판정 지시문과, 항목마다 판정 모델이 받은 질문과 답을 쓴다.
    - 실행 ID, 어느 표본인지, 판정과 판정 이유는 쓰지 않는다(가린 채 읽는다). 머리에 표본 지문(판정 지문과 항목마다의 실행 ID·표본·기록 해시를 이은 SHA-256 앞 12자)을 적는다.
    - 이미 있으면 적은 판단을 지우지 않으려고 쓰지 않고 2로 끝난다.
- 사람은 항목 끝 `■ 내 판단: [ ]`의 괄호 안에 O(답했다)나 X(답하지 않았다)를 적고 저장한다. 메모장의 CRLF·BOM도 읽는다.
- **옛 표본 빼기(순서 11b-1, 2.1절):** 채점 설정 `judge_sample_exclude`에 그 단계의 폴더(예: `{"pilot": ["judge_sample_v0"]}`)가 있으면, 그 폴더 `tally.tsv`의 실행을 두 바구니에서 뺀 뒤 같은 법으로 뽑는다. judge-sample과 judge-tally 모두 뺀다. 뺀 수를 화면과 sample.txt 머리에 낸다. 폴더나 tally.tsv가 없거나 꼴이 아니면 2로 끝난다.
- judge-tally: 표본을 다시 뽑아 표본 지문이 sample.txt와 같은지 먼저 본 뒤(다르면 3), 판단을 판정과 댄다. 빈칸이나 O·X가 아닌 칸이 있으면 항목 번호를 찍고 2로 끝난다.
    - 화면: A의 같음 수와 다른 꼴 둘(판정 답함·사람 X, 판정 답하지 않음·사람 O), B에서 사람이 O라 한 수, 2.1절 기준을 넘는지
    - `tally.tsv`: 항목, 실행 ID, 표본, 판정, 내 판단, 같음. 글이 없다.
    - `differ.txt`: 판정과 판단이 다른 항목의 질문·답·판정 이유. 글이 있어 보내지 않는다.
- `sample.txt`·`differ.txt`에는 질문 글과 답이 들어 있다. runs 아래라 git 밖이고, 보내지 않는다.
