# -*- coding: utf-8 -*-
"""순서 11b-2 글 검사 v1(prep/textcheck.py, 설계 b판 5.3). 자바 KoText.check와 같은 예문 묶음(data/text_v1.json의 checks)으로 본다.

- 묶음의 글은 지어낸 것이다. 자바 시험(KoTextTest)이 같은 묶음의 money·display·render·checks를 본다.
- 자바 정규식·strip과 같게 맞춘 곳(빈칸 여섯 자, 끝 $, 자바 공백 25자, 유니코드 숫자)을 따로 본다.
"""
import json
import os
import unittest

from prep import textcheck as T

HERE = os.path.dirname(os.path.abspath(__file__))
SET = os.path.join(HERE, "data", "text_v1.json")


def load():
    with open(SET, encoding="utf-8") as f:
        return json.load(f)


class TestSharedSet(unittest.TestCase):
    def test_every_check_case_gives_the_java_answer(self):
        doc = load()
        self.assertEqual("v1", doc["version"])
        n = 0
        for c in doc["checks"]:
            allowed = T.latin_words(c.get("listing", ""), c.get("question", ""))
            self.assertEqual(c["want"], T.check(c["text"], allowed), c["text"])
            n += 1
        self.assertEqual(54, n)

    def test_the_set_covers_every_code(self):
        codes = {w for c in load()["checks"] for w in c["want"]}
        self.assertEqual(set(T.CODES), codes)
        self.assertEqual(("BRACE", "UNIT_NO_VALUE", "GARBLED", "QMARK", "REGISTER", "FORMAT"), T.CODES)

    def test_money_drawn_by_java_passes(self):
        """money 칸의 그린 돈 글은 글 검사에 걸리지 않는다(자바 KoTextTest.money와 같은 셈)."""
        for c in load()["money"]:
            self.assertEqual([], T.check("기준값은 " + c["want"] + "입니다.", set()), c["want"])


class TestJavaLikeness(unittest.TestCase):
    def test_register_ends(self):
        for w in ("습니다", "입니다", "쌉니다", "됩니다", "합니까", "습니까", "보십시오", "하세요", "안녕하세요", "안녕하십니까"):
            self.assertTrue(T.formal_end(w), w)
        for w in ("", "니다", "아니다", "바뀌다", "해요", "이에요", "다", "요"):
            self.assertFalse(T.formal_end(w), w)

    def test_sentence_ends(self):
        self.assertEqual(["입니다"], T.sentence_ends("차이는 4.1%입니다."))
        self.assertEqual(["쌉니다"], T.sentence_ends("기준보다 쌉니다(차이 41.0%)."))
        self.assertEqual(["값입니다"], T.sentence_ends("면적 ?당 값입니다."), "한글 앞의 ?는 문장 끝이 아니다")
        self.assertEqual(["안녕하세요", "쌉니다"], T.sentence_ends("안녕하세요! 이 매물은 쌉니다."))
        self.assertEqual(["", "쌉니다"], T.sentence_ends("6.8%. 쌉니다"), "끝 한글이 없는 문장은 빈 덩이다")

    def test_java_whitespace(self):
        self.assertEqual("가", T.jstrip("　가 "))
        self.assertEqual(" 가 ", T.jstrip(" 가 "), "자바 strip은 U+00A0을 떼지 않는다")
        self.assertEqual(["REGISTER"], T.check("기준보다 쌉니다 ", set()), "끝의 U+00A0 때문에 끝 한글 덩이가 비어 반말로 셈한다")
        self.assertEqual([], T.check("기준보다 쌉니다　", set()))
        self.assertEqual(["UNIT_NO_VALUE", "REGISTER"], T.check("값은 원 ", set()), "자바 $는 끝의 줄 끝(U+2028) 앞에서도 맞는다")
        self.assertEqual(["GARBLED"], T.check("기준은\t\n 같습니다.", set()), "\\s{3,}는 자바 빈칸 여섯 자로 센다")
        self.assertEqual([], T.check("기준은　　　같습니다.", set()), "U+3000은 자바 \\s가 아니다")

    def test_unicode_digits_are_decimal_points(self):
        self.assertEqual(["입니다"], T.sentence_ends("차이는 ٥.٤%입니다."), "자바 Character.isDigit처럼 유니코드 Nd 숫자 사이 마침표는 소수점이다")

    def test_numeral_word_units_are_not_bare_units(self):
        self.assertEqual([], T.check("그 달은 한 건도 없었습니다.", set()))
        self.assertEqual(["UNIT_NO_VALUE"], T.check("요즘 같이 건도 줄었습니다.", set()), "앞 낱말 끝의 이는 낱말 이가 아니다")
        self.assertEqual(["UNIT_NO_VALUE"], T.check("이 매물은 한 건 원보다 쌉니다.", set()), "수사 뒤가 아닌 단위 말은 센다")

    def test_latin_words(self):
        self.assertEqual({"AI", "C"}, T.latin_words("푸른빌C동", None, "AI에 묻습니다"))
        self.assertEqual(["GARBLED"], T.check("AI가 답합니다.", set()))
        self.assertEqual([], T.check("AI가 답합니다.", {"AI"}))


if __name__ == "__main__":
    unittest.main()
