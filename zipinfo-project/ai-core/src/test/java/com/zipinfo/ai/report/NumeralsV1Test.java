package com.zipinfo.ai.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zipinfo.ai.num.Q;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 수사 사전 v1과 기간 말: 파이썬 채점 v1(prep/numerals.py)과 같은 예문 묶음(experiment/prep/tests/data/numerals_v1.json).
 * 묶음의 기대값 가운데 periods와 numerals를 댄다. 자바는 자리표시자 이름을 1~6자로 지우지만, 묶음 74개는 1~3자로 지운 것과 수가 같다(순서 11b-2에서 셈).
 */
class NumeralsV1Test {
    static final ObjectMapper M = new ObjectMapper();
    static final Path SET = Path.of("..", "..", "experiment", "prep", "tests", "data", "numerals_v1.json");

    @Test
    void everyCaseGivesTheSameAnswerAsPython() throws Exception {
        assertTrue(Files.exists(SET), "예문 묶음이 없다: " + SET.toAbsolutePath());
        JsonNode doc = M.readTree(Files.readString(SET));
        int n = 0;
        for (JsonNode c : doc.get("cases")) {
            Set<Integer> lengths = new HashSet<>();
            for (JsonNode s : c.get("stat_claims")) {
                Integer m = NumeralsV1.monthsOf(s.path("start_month").asText(null), s.path("end_month").asText(null));
                if (m != null) {
                    lengths.add(m);
                }
            }
            List<List<Object>> periods = new ArrayList<>();
            int numerals = 0;
            for (NumeralsV1.Hit h : NumeralsV1.scan(KoText.PLACEHOLDER.matcher(c.get("text").asText()).replaceAll(" "))) {
                if (!h.period()) {
                    numerals++;
                } else {
                    List<Object> p = new ArrayList<>();
                    p.add(h.months());
                    p.add(h.months() != null && lengths.contains(h.months()));
                    periods.add(p);
                }
            }
            List<List<Object>> want = new ArrayList<>();
            for (JsonNode p : c.get("want").get("periods")) {
                List<Object> w = new ArrayList<>();
                w.add(p.get(0).isNull() ? null : p.get(0).asInt());
                w.add(p.get(1).asBoolean());
                want.add(w);
            }
            assertEquals(want, periods, c.get("name").asText());
            assertEquals(c.get("want").get("numerals").asInt(), numerals, c.get("name").asText());
            n++;
        }
        assertEquals(74, n);
    }

    @Test
    void values() {
        String[][] cases = {{"한", "1"}, {"석", "3"}, {"넉", "4"}, {"열", "10"}, {"열두", "12"}, {"스무", "20"}, {"스물두", "22"}, {"아흔아홉", "99"},
            {"일", "1"}, {"십", "10"}, {"십이", "12"}, {"이십", "20"}, {"삼백오십", "350"}, {"천", "1000"}, {"백만", "1000000"}, {"이억삼천만", "230000000"}};
        for (String[] c : cases) {
            assertEquals(Q.parse(c[1]), NumeralsV1.valueOf(c[0]), c[0]);
        }
        assertEquals(Q.HALF, NumeralsV1.valueOf("반"));
        for (String a : NumeralsV1.APPROX) {
            assertNull(NumeralsV1.valueOf(a));
        }
        assertNull(NumeralsV1.valueOf("열두세"));
        assertEquals(12, NumeralsV1.monthsOf("202511", "202610"));
        assertNull(NumeralsV1.monthsOf("202610", "202511"));
    }
}
