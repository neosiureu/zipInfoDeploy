# 5단계 얼림 (시운전 전, 2026-11-25)

06 규칙(`experiment/example_listing_rule_v1.md`) 7절 5와 만들기 설계안 11절의 5단계다. 시운전(연습 75문항) 전에 매물, 연습·본시험 나눔, 문항 배정, 연습 전 카드와 ⓪ 목록을 이 커밋으로 남긴다. 앞 커밋은 `be513352491ab5caa22a27e04bc7b932b0b2ae35`(순서 9b)다. 이 파일과 두 얼린 파일의 raw 주소를 Wayback에 남기고, 그 주소는 실험 기록(velog)에 적는다.

## 1. 이 커밋에서 처음 올리는 파일

| 파일 | 바이트 | 줄 | 담는 것 |
|---|---:|---:|---|
| `experiment/frozen/example_listings_v1.csv` | 51,322 | 76 | 예시 매물 75개: 사이트 칸, 단지ID, 층, 연습·본시험, 해시(06 규칙 7절) |
| `experiment/frozen/juso_road_v1.csv` | 34,634 | 148 | 주소 얼린 파일(설계안 5.3): 검색어 75개의 도로명주소 결과 147줄 |

- 매물 파일은 3단계(11/18)에 만들었다. 그 SHA-256은 3단계 커밋의 `experiment/frozen/run_log_v1.txt` 81줄에 이미 있다(11/18 Wayback).
- 주소 얼린 파일은 순서 2의 `python -m prep freeze-juso`(커밋 `559a702`)를 11/21에 한 번 돌려(검색 75번) 만들었다. `search_address`는 이 파일로만 답하고, 얼린 검색어 밖은 SNAPSHOT_MISS다.
- 두 파일은 그동안 `.git/info/exclude`로 git 밖에 두었다. `.gitattributes`의 `experiment/frozen/** -text`라 줄 끝은 만든 그대로(LF)다.

## 2. 연습·본시험 나눔과 시드

- 시드와 해시는 06 규칙 2절이다(`SEED = zipinfo-example-v1`, `H(목적, 열쇠)`).
- 나눔은 06 규칙 5.4다. 층마다 `H(split, 단지ID)`가 작은 순으로 세워 앞 ⌊n/3⌋개가 연습이다. 매물 파일의 `split` 칸이 그 결과이고, 시드로 다시 셈하면 75줄이 모두 같다.

| | 화성형 | 광주형 | 일반 | 합 |
|---|---:|---:|---:|---:|
| 연습 | 9 | 8 | 8 | 25 |
| 본시험 | 18 | 16 | 16 | 50 |

- 본시험이 층 최소(4)에 못 미치는 층은 없다.

## 3. 문항 배정 (설계안 9.2)

- 매물마다 3문항이다. 연습 75, 본시험 150이다.
    - 매매·전세: T1과, T2·T3·T5 가운데 `H(qtype, 단지ID|유형)`가 작은 둘
    - 월세: T1, T4, 그리고 T2·T3 가운데 그 해시가 작은 하나
    - 유형은 `T2`처럼 적는다. 정의는 `experiment/prep/answers.py`의 `assign`이다(순서 3a 커밋 `1fca8d0`부터 그대로).
- T2의 범위는 단지다(순서 7b에서 정함).
- 아래는 이 배정을 매물 파일에 적용해 센 층별 문항 수와 유형 비율이다.

| 나눔 | 층 | T1 | T2 | T3 | T4 | T5 | 합 |
|---|---|---:|---:|---:|---:|---:|---:|
| 연습 | 화성형 | 9 | 3 | 6 | 4 | 5 | 27 |
| 연습 | 광주형 | 8 | 8 | 2 | 3 | 3 | 24 |
| 연습 | 일반 | 8 | 4 | 5 | 3 | 4 | 24 |
| 연습 | 합 | 25 | 15 | 13 | 10 | 12 | 75 |
| 본시험 | 화성형 | 18 | 11 | 12 | 4 | 9 | 54 |
| 본시험 | 광주형 | 16 | 14 | 5 | 5 | 8 | 48 |
| 본시험 | 일반 | 16 | 13 | 4 | 5 | 10 | 48 |
| 본시험 | 합 | 50 | 38 | 21 | 14 | 27 | 150 |

- "거래 없음"이 맞는 문항: T2(단지 0건) 연습 1·본시험 6, T5(단지 건수 0) 연습 0·본시험 4. `prep answers`의 정답으로 셌다.

## 4. 연습 전 카드와 ⓪ 목록

- 카드 v0(`experiment/cards/v0/`의 열 장)과 ⓪ 목록 v0(`experiment/intents/intent_lists_v0.json`)을 순서 7b 커밋(`6e9256c`)의 것으로 얼린다. 그 뒤로 바뀌지 않았다.
- 시운전의 첫 돌리기는 이 판으로 한다. 연습 뒤에 고친 판은 새 판으로 두고 관문 3에 얼린다.
- 하네스 시험 `HarnessCheckTest`가 찍는 지문은 카드 `C2EBC4C8128D`, 목록 `528BEC8905C6`이다.

## 5. 파일별 SHA-256

저장소에 든 바이트의 값이다(이 파일 자신은 뺀다). raw 주소로 받은 파일과 같다. Windows에서 줄 끝을 CRLF로 꺼낸 카드·목록·글 파일은 값이 다르다.

```
FFB838C797E7EE4CEAFD15096701565E501B5392F5A290690FF2F3C4B8C1EBF4  experiment/frozen/example_listings_v1.csv
C766D77153D06235096C6E5CCDA459DC926DE7400F4C8413133C60D8FEED4B0E  experiment/frozen/juso_road_v1.csv
8893D79F8D755628B4DAF5D5BD6720230A08BF1976624BF070D58170948C088F  experiment/example_listing_rule_v1.md
6691FABDF724A84F39CFB5EA83359577C22AE1DA238E51607AF31FFE8DC3E793  experiment/prep/answers.py
528BEC8905C65D26BCB22B3E32C220905A22F960B08EA4403164E2E93C8C2C4E  experiment/intents/intent_lists_v0.json
3DC2AEF952C4158A38FE20BFA988A2959D008D59206D2E99E85F839F97BA26EB  experiment/cards/v0/K01.md
B669AA7B8E787953C5D0C5C08C0C6DF5FA037A34700DCC8BBC9AEF852C29D485  experiment/cards/v0/K02.md
A9C944A41F74406AA09CFD9CA1DD8D02708E6D2C34B93C7AF4DF9A817360AF2D  experiment/cards/v0/K03.md
1CF8C12744C0DAA58F6C7250F4B70DBE93DC43EF5718CC160C626062592367D7  experiment/cards/v0/K04.md
63EBC13A05CCBC5BE1C2941DFC9B5CADC12212A5DF63183F9A2616D442818161  experiment/cards/v0/K05.md
A4CD4AE2ED2DADB42F774BD011057B66D335490297F476B6E30DF3F231929DFC  experiment/cards/v0/K06.md
7B5DB3B562EA230A91DEA6DDABA18A4293779BCA59377A16DC90393C3288FF9D  experiment/cards/v0/K07.md
4D2A2C61FD6F6CA7341677F8C9CB91786C78C00DA2B80FE8A4C1440169D29C5B  experiment/cards/v0/K08.md
16EEBC36C9046F80BD921B534E27274CFBC6E558B99BE0361EEA3BBB79377EB9  experiment/cards/v0/K09.md
A849093925D09852156321EB526517E2668786BB15945273C3E0C45D97E02906  experiment/cards/v0/K10.md
0AD77C8D72AF0BBCCDD1C9E943A244F2DFEA5E9C2A4FE676EEB138E07103E25D  experiment/site_insert/README.md
```

- `experiment/site_insert/README.md`에는 4단계 ⑨에서 미룬 두 줄을 이 커밋에서 더했다. 앞 값은 `experiment/site_insert/해시목록_4단계.txt`의 `0F1E735E…`다.
