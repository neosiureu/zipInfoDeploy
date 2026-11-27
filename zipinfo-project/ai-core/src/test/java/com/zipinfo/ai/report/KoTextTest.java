package com.zipinfo.ai.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zipinfo.ai.num.Q;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 그리기 v1·글 검사 v1의 예문 묶음(experiment/prep/tests/data/text_v1.json, 순서 11b-2). 파이썬 prep/textcheck.py가 같은 묶음의 checks를 본다.
 * 시험은 ai-core 폴더에서 돈다(gradle의 시험 작업 폴더). 묶음이 없으면 실패한다.
 */
class KoTextTest {
    static final ObjectMapper M = new ObjectMapper();
    static final Path SET = Path.of("..", "..", "experiment", "prep", "tests", "data", "text_v1.json");

    static JsonNode load() throws Exception {
        assertTrue(Files.exists(SET), "예문 묶음이 없다: " + SET.toAbsolutePath());
        return M.readTree(Files.readString(SET));
    }

    @Test
    void money() throws Exception {
        for (JsonNode c : load().get("money")) {
            Q v = Q.parse(c.get("value").asText());
            String got = c.get("unit").asText().equals("원") ? KoText.moneyWon(v) : KoText.moneyMan(v);
            assertEquals(c.get("want").asText(), got, c.toString());
            assertEquals(List.of(), KoText.check("기준값은 " + got + "입니다.", Set.of()), got);
        }
    }

    @Test
    void display() throws Exception {
        for (JsonNode c : load().get("display")) {
            assertEquals(c.get("want").asText(), KoText.display(Q.parse(c.get("value").asText()), c.get("unit").asText(), c.get("diff").asBoolean()),
                    c.toString());
        }
    }

    @Test
    void render() throws Exception {
        for (JsonNode c : load().get("render")) {
            Map<String, KoText.Slot> slots = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = c.get("slots").fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                JsonNode s = e.getValue();
                slots.put(e.getKey(), new KoText.Slot(Q.parse(s.get("value").asText()), s.get("unit").asText(), s.path("diff").asBoolean(false),
                        !s.path("missing").asBoolean(false)));
            }
            String name = c.has("name") ? c.get("name").asText() : null;
            assertEquals(c.get("want").asText(), KoText.render(c.get("text").asText(), slots, name), c.get("text").asText());
        }
    }

    @Test
    void checks() throws Exception {
        for (JsonNode c : load().get("checks")) {
            Set<String> allowed = KoText.latinWords(c.path("listing").asText(""), c.path("question").asText(""));
            List<String> want = new ArrayList<>();
            c.get("want").forEach(w -> want.add(w.asText()));
            List<String> got = KoText.check(c.get("text").asText(), allowed).stream().map(KoText.Hit::code).toList();
            assertEquals(want, got, c.get("text").asText());
        }
    }

    @Test
    void theSetCoversEveryCode() throws Exception {
        Set<String> codes = new java.util.TreeSet<>();
        load().get("checks").forEach(c -> c.get("want").forEach(w -> codes.add(w.asText())));
        assertEquals(Set.of("BRACE", "UNIT_NO_VALUE", "GARBLED", "QMARK", "REGISTER", "FORMAT"), codes);
    }

    @Test
    void endSoundReadsDigitsAndUnits() {
        assertEquals('r', KoText.endSound("새솔1"));
        assertEquals('v', KoText.endSound("8.3%"));
        assertEquals('v', KoText.endSound("84㎡"));
        assertEquals('c', KoText.endSound("1억 2,300만원"));
        assertEquals('r', KoText.endSound("12개월"));
        assertEquals('c', KoText.endSound("푸른하임(102동)"));
        assertEquals('x', KoText.endSound("ABC"));
    }
}
