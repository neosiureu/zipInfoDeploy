package com.zipinfo.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zipinfo.ai.cards.Cards;
import com.zipinfo.ai.data.History;
import com.zipinfo.ai.data.Listing;
import com.zipinfo.ai.reflex.IntentCheck;
import com.zipinfo.ai.reflex.IntentLists;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * 순서 7b(설계 8절의 6): 틀 문장 파일 {@code questions/templates_v0.json}은 관문 3까지 git 밖이라, 있을 때만 돈다.
 * 틀 문장 글은 시험에 넣지 않고 지문과 수·성질로만 지킨다.
 * <ul>
 *   <li>파일 지문(줄 끝을 LF로 맞춘 SHA-256)이 사용자·상대와 맞춘 값과 같다. 틀을 고치면 여기서 멈추고, 사람이 다시 본 뒤 이 값을 고친다.</li>
 *   <li>틀 문장마다 ⓪의 절에서 유형의 카드를 한글 낱말로 찾는다(T1·T2 K04, T3 K04·K05, T4 K04·K07, T5 K04·K05·K08).</li>
 *   <li>틀 문장마다 ⓪의 분모와 절 수를 찍는다(수만).</li>
 * </ul>
 * 이름 자리에는 카드 낱말이 없는 지어낸 이름을 넣는다. 실제 이름은 보호되어 분모에 들지 않으므로 수가 같다(7b 검토에서 75개 모두 봄).
 */
class TemplatesCheckTest {
    static final Path EXP = Path.of("..").toAbsolutePath().normalize();
    static final String PRINT = "B7605248DE6E8F3E8147BA27275565026B73D56AAC424DFFD192BD0FAA9E32D7";
    static final String NAME = "가나다라마";
    static final Map<String, List<String>> NEED = Map.of(
            "T1", List.of("K04"),
            "T2", List.of("K04"),
            "T3", List.of("K04", "K05"),
            "T4", List.of("K04", "K07"),
            "T5", List.of("K04", "K05", "K08"));

    @Test
    void templatesKeepTheirPrintAndFindTheirCards() throws Exception {
        Path file = EXP.resolve("questions/templates_v0.json");
        assumeTrue(Files.exists(file), "틀 문장 파일이 있을 때만 돈다(관문 3까지 git 밖)");
        assertEquals(PRINT, Fingerprint.file(file), "틀 문장 파일이 맞춘 것과 다르다. 고쳤으면 사람이 다시 보고 이 값을 고친다");
        JsonNode templates = new ObjectMapper().readTree(Files.readString(file, StandardCharsets.UTF_8)).get("templates");
        Cards cards = Cards.load(EXP.resolve("cards/v0"));
        IntentCheck check = new IntentCheck(IntentLists.load(EXP.resolve("intents/intent_lists_v0.json")),
                History.load(EXP.resolve("frozen/codego/codego_history_v1.csv")).names(), cards);
        Map<String, String> fields = new LinkedHashMap<>();
        for (String k : Listing.NINE) {
            fields.put(k, "0");
        }
        fields.put("stockName", NAME);
        fields.put("stockAddress", "00000^^^가나시 가나구 가나로 1^^^");
        Listing listing = new Listing("L00", fields, Map.of());
        List<String> counts = new ArrayList<>();
        int n = 0;
        for (String type : List.of("T1", "T2", "T3", "T4", "T5")) {
            List<String> each = new ArrayList<>();
            int i = 0;
            for (JsonNode t : templates.get(type)) {
                i++;
                IntentCheck.Result r = check.check(t.asText().replace("{이름}", NAME), listing, List.of());
                Set<String> found = new TreeSet<>();
                for (String clause : r.clauses()) {
                    cards.find("", clause).forEach(c -> found.add(c.id()));
                }
                for (String need : NEED.get(type)) {
                    assertTrue(found.contains(need), type + "의 " + i + "번째 틀 문장에서 " + need + "를 한글 낱말로 못 찾는다");
                }
                each.add(r.total() + "/" + r.clauses().size());
                n++;
            }
            counts.add(type + " " + String.join("·", each));
        }
        System.out.println("틀 문장 검사: " + n + "개, 지문 " + PRINT.substring(0, 12) + ", 유형 카드를 모두 찾음, 분모/절 " + String.join(", ", counts));
    }
}
