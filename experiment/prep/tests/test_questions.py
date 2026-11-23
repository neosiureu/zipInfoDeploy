# -*- coding: utf-8 -*-
"""순서 7a: 틀 문장 검사, 고르기(고루 나누기), 채우기, 문항 ID, 문항별 정답, 같은 바이트.

틀 문장은 과일 이야기로 지어낸 것이다(실제 질문과 닮지 않게). 매물·단지ID도 지어낸 것이다.
"""
import copy
import json
import os
import tempfile
import unicodedata
import unittest
from collections import Counter

from picker.common import H

from prep import answers, questions

GOOD = {
    "version": "v0",
    "templates": {
        "T1": ["{이름} 가게 사과는 옆 가게보다 비싼 편이에요?", "이 가게 귤 값 옆집이랑 견주면 어때", "{이름}의 배는 시장보다 싼가요?",
               "{이름}에서 파는 감, 다른 데보다 비싸?"],
        "T2": ["{이름} 꿀단지 꿀하고만 견주면 비싼가요?", "같은 꿀단지 안 꿀로만 따지면 이 꿀 값 어때?", "{이름}도 같은 단지 꿀끼리만 보면 싼 편이야?",
               "이 꿀은 단지 안 꿀이랑만 견줘 줘"],
        "T3": ["최근 1개월 동안 {이름} 귤 값 어땠어?", "최근 3개월 사과 값이랑 견주면 이 사과 어때요?", "최근 6개월로 보면 {이름}인데 배가 비싼가요?",
               "최근 3개월 동안 이 감 값은 싼 편인가"],
        "T4": ["{이름}의 수박을 참외로 바꾸면 몇 통인지, 그걸로 보면 비싼지 알려줘", "이 포도를 귤로 셈하면 얼마고 옆 가게랑 견주면 어때요?",
               "{이름}까지 와서 산 자두를 사과로 치면 얼마인지와 그 값이 싼지 궁금해", "이 망고를 바나나로 환산하면 얼마이고, 시장과 견주면 어떤가요?"],
        "T5": ["{이름} 꿀단지에서 최근 1년 동안 꿀이 몇 번 팔렸는지랑 옆 가게보다 비싼지 알려줘", "이 단지 꿀이 최근 1년 동안 몇 번 팔렸고 시장보다 싼지 궁금해요",
               "최근 1년 이 꿀단지 판매 횟수와 값이 비싼 편인지 둘 다 알려 주세요", "{이름}만 놓고 최근 1년 동안 단지 꿀이 몇 번 팔렸는지, 그리고 값은 어떤지"],
    },
}


def with_one(qtype, text, n=0):
    """GOOD의 qtype n번째를 text로 바꾼 것."""
    d = copy.deepcopy(GOOD)
    d["templates"][qtype][n] = text
    return d


class TestTemplateCheck(unittest.TestCase):
    def problems(self, doc):
        with self.assertRaises(questions.BadTemplates) as cm:
            questions.check(doc)
        return cm.exception.problems

    def assertStops(self, doc, words):
        got = " / ".join(self.problems(doc))
        self.assertIn(words, got)

    def test_good_file_reads(self):
        version, tp = questions.check(GOOD)
        self.assertEqual(version, "v0")
        self.assertEqual([len(tp[t]) for t in questions.TYPES], [4, 4, 4, 4, 4])

    def test_only_unchanging_words_may_follow_the_slot(self):
        # 받침과 상관없는 말, 끝, 빈칸, 문장부호는 지난다
        for tail in ("의 귤", "에서 귤", "에게 귤", "에 귤", "한테 귤", "도 귤", "만 귤", "보다 귤", "까지 귤", "부터 귤", "하고 귤", "처럼 귤",
                     "마다 귤", "인데 귤", "인가요", "인지 궁금", "에서는 귤", "에도 귤", "까지는 귤", "", " 귤", "?", "!", ".", ", 귤"):
            questions.check(with_one("T1", "{이름}" + tail if tail else "옆 가게보다 비싼 {이름}"))
        # 받침에 따라 바뀌는 말과 목록에 없는 말은 멈춘다
        for tail in ("은 비싸?", "는 비싸?", "이 비싸?", "가 비싸?", "을 볼래", "를 볼래", "와 견줘", "과 견줘", "로 보면", "으로 보면", "랑 견줘",
                     "이랑 견줘", "야?", "이야?", "여서 비싸?", "였어?", "라는 가게", "이라는 가게", "예요?", "이에요?", "에요?", "에여?", "면 비싸?",
                     "이면 비싸?", "나 볼래", "고 비싸?", "다", "지만 비싸", "죠?", "든 볼래", "며 비싸", "란 가게", "시세", "엔 비싸?", "서 사면"):
            self.assertStops(with_one("T1", "{이름}" + tail), "{이름} 바로 뒤")

    def test_slot_and_braces(self):
        self.assertStops(with_one("T1", "{이름} 사과와 {이름} 배"), "두 번 이상")
        self.assertStops(with_one("T1", "{이름} 사과는 {가게}보다 비싸?"), "중괄호")
        self.assertStops(with_one("T1", "(이름) 사과는 비싸?"), "'이름'")
        self.assertStops(with_one("T1", "[이름]의 사과는 비싸?"), "'이름'")

    def test_periods(self):
        self.assertStops(with_one("T1", "최근 3개월 사과 값은 비싸?"), "기간 말이 있다")
        self.assertStops(with_one("T2", "최근 1년 꿀단지 안에서만 보면?"), "기간 말이 있다")
        self.assertStops(with_one("T4", "최근 6개월 귤로 바꾸면 얼마야?"), "기간 말이 있다")
        self.assertStops(with_one("T1", "최근 사과 값보다 비싸?"), "'최근'이 기간 말 밖")
        self.assertStops(with_one("T1", "사과 2개 값보다 비싸?"), "숫자가 기간 말 밖")
        for w in questions.PERIOD_LIKE:
            self.assertStops(with_one("T1", f"{w} 사과 값보다 비싸?"), f"'{w}'")
        self.assertStops(with_one("T3", "이 귤 값 어때?"), "꼭 하나만")
        self.assertStops(with_one("T3", "최근 1개월과 최근 3개월 귤 값 어때?"), "꼭 하나만")
        self.assertStops(with_one("T3", "최근 1년 귤 값 어때?"), "꼭 하나만")
        self.assertStops(with_one("T3", "최근 3개월 동안 {이름} 귤 값 어땠어?"), "없는 것: '최근 1개월'")
        self.assertStops(with_one("T5", "이 꿀단지 꿀이 몇 번 팔렸는지랑 값 알려줘", 1), "'최근 1년'만")
        self.assertStops(with_one("T5", "최근 1년과 최근 3개월 꿀단지 꿀이 몇 번 팔렸는지", 1), "'최근 1년'만")
        # 기간 말 안의 숫자와 "최근"은 된다(GOOD이 그렇다)

    def test_scope_word(self):
        self.assertStops(with_one("T2", "같은 가게 꿀로만 견주면 비싼가요?"), "'단지'가 없다")
        self.assertStops(with_one("T5", "최근 1년 동안 이 가게 꿀이 몇 번 팔렸는지", 1), "'단지'가 없다")
        for t in ("T1", "T4"):
            self.assertStops(with_one(t, "이 단지 사과 값 어때?"), "'단지'가 있다")
        self.assertStops(with_one("T3", "최근 1개월 이 단지 귤 값 어때?"), "'단지'가 있다")

    def test_shape(self):
        d = copy.deepcopy(GOOD)
        d["templates"]["T1"] = d["templates"]["T1"][:3]
        self.assertStops(d, "4~6개")
        d = copy.deepcopy(GOOD)
        d["templates"]["T1"] = d["templates"]["T1"] + ["사과 하나 더 비싸?", "귤 하나 더 비싸?", "배 하나 더 비싸?"]
        self.assertStops(d, "4~6개")
        self.assertStops(with_one("T1", GOOD["templates"]["T1"][1], 0), "겹친다")
        self.assertStops(with_one("T1", "사과\n비싸?"), "줄바꿈")
        self.assertStops(with_one("T1", " 사과 비싸?"), "앞뒤에 빈칸")
        self.assertStops(with_one("T1", unicodedata.normalize("NFD", "사과 값 비싸?")), "NFC")
        self.assertStops(with_one("T1", ""), "비었거나")
        d = copy.deepcopy(GOOD)
        d["extra"] = 1
        self.assertStops(d, "version·templates뿐")
        d = copy.deepcopy(GOOD)
        del d["templates"]["T5"]
        self.assertStops(d, "T1~T5 다섯뿐")
        d = copy.deepcopy(GOOD)
        d["version"] = "판0"
        self.assertStops(d, "v0 꼴")

    def test_all_problems_are_listed_at_once(self):
        d = with_one("T1", "{이름}은 요즘 비싸?")
        d["templates"]["T2"][0] = "같은 가게 꿀로만"
        got = self.problems(d)
        self.assertEqual(3, len(got), got)
        self.assertTrue(got[0].startswith("T1의 1번째: "), got)

    def test_load_from_file(self):
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "templates_v0.json")
            with open(p, "w", encoding="utf-8", newline="\n") as f:
                f.write(json.dumps(GOOD, ensure_ascii=False))
            self.assertEqual(questions.load_templates(p)[0], "v0")
            with open(p, "w", encoding="utf-8") as f:
                f.write("\ufeff" + json.dumps(GOOD, ensure_ascii=False))
            with self.assertRaises(questions.BadTemplates):
                questions.load_templates(p)
            with open(p, "w", encoding="utf-8") as f:
                f.write("{")
            with self.assertRaises(questions.BadTemplates):
                questions.load_templates(p)


def listing(cid, deal, split):
    return {"complex_id": cid, "deal": deal, "split": split}


# 지어낸 매물 여덟: 매매·전세·월세와 연습·본시험을 섞는다
L = [("L%02d" % i, listing(f"apt|11111|가동|0|{i}|0", deal, split))
     for i, (deal, split) in enumerate([("매매", "연습"), ("전세", "본시험"), ("월세", "연습"), ("월세", "본시험"), ("매매", "본시험"),
                                         ("전세", "연습"), ("월세", "연습"), ("매매", "연습")], 1)]
NAMES = {"L01": "사과마을", "L02": "수박농장", "L03": "배나무(2)", "L04": "귤밭 3", "L05": "감나무집", "L06": "포도원Ⅱ", "L07": "자두숲",
         "L08": "망고길B"}


class TestBuild(unittest.TestCase):
    def setUp(self):
        self.version, self.tp = questions.check(GOOD)
        self.rows = questions.build(L, NAMES, self.tp)

    def test_ids_follow_listing_then_type_order(self):
        self.assertEqual([r["id"] for r in self.rows], ["q%03d" % n for n in range(1, 25)])
        self.assertEqual([r["key"] for r in self.rows], [k for k, _ in L for _ in range(3)])
        for k, x in L:
            got = [r["qtype"] for r in self.rows if r["key"] == k]
            self.assertEqual(got, sorted(answers.assign(x["complex_id"], x["deal"])))
            self.assertEqual(got[0], "T1")
            self.assertEqual("T4" in got, x["deal"] == "월세")
            self.assertFalse(x["deal"] == "월세" and "T5" in got)

    def test_templates_go_round_in_hash_order(self):
        for (split, qtype), n in Counter((r["split"], r["qtype"]) for r in self.rows).items():
            g = sorted((r for r in self.rows if (r["split"], r["qtype"]) == (split, qtype)),
                       key=lambda r: (H("qtext", r["cid"] + "|" + qtype), r["key"]))
            self.assertEqual([r["template"] for r in g], [i % 4 for i in range(n)])

    def test_uses_are_even_and_extras_go_first(self):
        tp = {t: list(GOOD["templates"][t]) for t in questions.TYPES}
        tp["T1"] = tp["T1"][:3]
        rows = questions.build(L, NAMES, tp)
        # 연습의 T1은 매물 다섯(L01·L03·L06·L07·L08) → 틀 문장 셋에 2·2·1
        use = Counter(r["template"] for r in rows if r["qtype"] == "T1" and r["split"] == "연습")
        self.assertEqual([use[0], use[1], use[2]], [2, 2, 1])

    def test_fill_and_window(self):
        for r in self.rows:
            tpl = self.tp[r["qtype"]][r["template"]]
            self.assertEqual(r["text"], tpl.replace("{이름}", NAMES[r["key"]]))
            self.assertNotIn("{", r["text"])
            want = 12 if r["qtype"] != "T3" else {"최근 1개월": 1, "최근 3개월": 3, "최근 6개월": 6}[next(p for p in questions.T3_PERIODS if p in tpl)]
            self.assertEqual(r["months"], want)

    def test_questions_file_shape_and_bytes(self):
        doc = questions.questions_doc(self.rows, self.version)
        self.assertEqual(list(doc), ["version", "questions"])
        self.assertEqual(list(doc["questions"][0]), ["id", "key", "qtype", "split", "text"])
        text = questions.dumps(doc)
        self.assertTrue(text.endswith("}\n") and "\r" not in text)
        self.assertIn("사과마을", text)
        self.assertEqual(text, questions.dumps(questions.questions_doc(questions.build(L, NAMES, self.tp), self.version)))
        with tempfile.TemporaryDirectory() as d:
            a, b = os.path.join(d, "a.json"), os.path.join(d, "b.json")
            questions.write_json(doc, a)
            with open(b, "wb") as f:
                f.write(text.replace("\n", "\r\n").encode("utf-8"))
            self.assertEqual(questions.sha256_lf(a), questions.sha256_lf(b))
            with open(a, "rb") as f:
                self.assertEqual(f.read(), text.encode("utf-8"))

    def test_names_must_match_the_listing_keys(self):
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "in.json")
            with open(p, "w", encoding="utf-8") as f:
                json.dump([{"key": k, "fields": {"stockName": NAMES[k]}, "evidence": {}} for k, _ in L], f, ensure_ascii=False)
            self.assertEqual(questions.names(p, [k for k, _ in L]), NAMES)
            with self.assertRaises(ValueError):
                questions.names(p, [k for k, _ in L][:-1])


def case(key, qtype, months, sample=7, **kw):
    c = {"key": key, "qtype": qtype, "months": months, "scope": "법정동", "call": {"api": "apt_trade"}, "sample": sample,
         "listing_value": "100", "diff": "1.5"}
    c.update(kw)
    return c


class TestAnswers(unittest.TestCase):
    def setUp(self):
        _, self.tp = questions.check(GOOD)
        self.rows = questions.build(L, NAMES, self.tp)
        self.cases = []
        for r in self.rows:
            for m in ((1, 3, 6) if r["qtype"] == "T3" else (12,)):
                extra = {"t5_count": 0} if r["qtype"] == "T5" else {}
                self.cases.append(case(r["key"], r["qtype"], m, sample=m, basis="50", **extra))

    def test_each_question_takes_its_own_window(self):
        ans = questions.answers_for(self.rows, self.cases, json.loads(json.dumps(self.cases)))
        self.assertEqual([a["id"] for a in ans], [r["id"] for r in self.rows])
        for a, r in zip(ans, self.rows):
            self.assertEqual((a["months"], a["sample"], a["split"]), (r["months"], r["months"], r["split"]))
            self.assertEqual(list(a)[:5], ["id", "key", "split", "qtype", "months"])
            self.assertEqual("t5_count" in a, r["qtype"] == "T5")

    def test_it_stops_when_the_check_file_differs(self):
        old = json.loads(json.dumps(self.cases))
        old[0]["diff"] = "9.9"
        with self.assertRaises(ValueError):
            questions.answers_for(self.rows, self.cases, old)
        with self.assertRaises(ValueError):
            questions.answers_for(self.rows, self.cases[1:], self.cases[1:])
        with self.assertRaises(ValueError):
            questions.answers_for(self.rows, self.cases + self.cases[:1], self.cases + self.cases[:1])


if __name__ == "__main__":
    unittest.main()
