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
import org.junit.jupiter.api.Test;

/**
 * 순서 8b(설계 2판 9절): ⓪ 의도 절 표 {@code questions/intent_clauses_v0.json}은 관문 3까지 git 밖이라, 표·틀 문장·문항·매물 입력 파일이 있을 때만 돈다.
 * 채점기는 ⓪ 거절 결과의 「…」 절을 이 표로 의도·의도 아님으로 가른다(부당 거절). 그래서 표의 절이 ⓪이 나누는 절과 같아야 한다.
 * 절 글은 시험에 넣지 않고, 걸려도 글을 찍지 않는다(지문과 수만).
 * <ul>
 *   <li>표 지문(줄 끝을 LF로 맞춘 SHA-256)이 사용자·상대와 맞춘 값과 같다. 표를 고치면 여기서 멈추고, 사람이 다시 본 뒤 이 값을 고친다.</li>
 *   <li>틀마다 지어낸 이름으로 채운 글을 ⓪(IntentCheck)이 나눈 절이 표의 절과 같다. 의도 절이 없는 틀은 없다.</li>
 *   <li>실제 문항마다 틀이 하나 찾히고, 매물 이름(stockName)으로 채운 표의 절이 ⓪이 그 문항을 실제 매물로 나눈 절과 같다.
 *       지어낸 이름만 보면 실제 이름에서 절이 달라지는 경우를 못 본다.</li>
 * </ul>
 */
class IntentClausesCheckTest {
    static final Path EXP = Path.of("..").toAbsolutePath().normalize();
    static final String PRINT = "CAAE9F30BEE0677B0EE77B6A53477CEECAE06083EB26F7F298EFB45D2591A2E8";
    static final String NAME = "가나다라마";
    static final ObjectMapper M = new ObjectMapper();

    static List<String> clauses(JsonNode template, String name) {
        List<String> out = new ArrayList<>();
        for (JsonNode c : template) {
            out.add(IntentCheck.normalize(c.get("clause").asText().replace("{이름}", name)));
        }
        return out;
    }

    @Test
    void theClauseTableMatchesWhatIntentCheckSplits() throws Exception {
        Path file = EXP.resolve("questions/intent_clauses_v0.json");
        Path tfile = EXP.resolve("questions/templates_v0.json");
        Path qfile = EXP.resolve("questions/questions_v0.json");
        Path lfile = EXP.resolve("prep/out/listings_input_v1.json");
        assumeTrue(Files.exists(file) && Files.exists(tfile) && Files.exists(qfile) && Files.exists(lfile),
                "의도 절 표·틀 문장·문항·매물 입력 파일이 있을 때만 돈다(관문 3까지 git 밖)");
        assertEquals(PRINT, Fingerprint.file(file), "의도 절 표가 맞춘 것과 다르다. 고쳤으면 사람이 다시 보고 이 값을 고친다");
        JsonNode table = M.readTree(Files.readString(file, StandardCharsets.UTF_8)).get("templates");
        JsonNode templates = M.readTree(Files.readString(tfile, StandardCharsets.UTF_8)).get("templates");
        IntentCheck check = new IntentCheck(IntentLists.load(EXP.resolve("intents/intent_lists_v0.json")),
                History.load(EXP.resolve("frozen/codego/codego_history_v1.csv")).names(), Cards.load(EXP.resolve("cards/v0")));
        Map<String, String> fields = new LinkedHashMap<>();
        for (String k : Listing.NINE) {
            fields.put(k, "0");
        }
        fields.put("stockName", NAME);
        fields.put("stockAddress", "00000^^^가나시 가나구 가나로 1^^^");
        Listing fake = new Listing("L00", fields, Map.of());
        int nt = 0;
        int nc = 0;
        int ni = 0;
        for (String type : List.of("T1", "T2", "T3", "T4", "T5")) {
            assertEquals(templates.get(type).size(), table.get(type).size(), type + "의 틀 수가 틀 문장 파일과 다르다");
            for (int i = 0; i < templates.get(type).size(); i++) {
                JsonNode tpl = table.get(type).get(i);
                IntentCheck.Result r = check.check(templates.get(type).get(i).asText().replace("{이름}", NAME), fake, List.of());
                assertTrue(clauses(tpl, NAME).equals(r.clauses()), type + "의 " + (i + 1) + "번째 틀: 표의 절이 ⓪이 나눈 절과 다르다(지어낸 이름)");
                int intents = 0;
                for (JsonNode c : tpl) {
                    intents += c.get("intent").asBoolean() ? 1 : 0;
                }
                assertTrue(intents > 0, type + "의 " + (i + 1) + "번째 틀에 의도 절이 없다");
                nt++;
                nc += tpl.size();
                ni += intents;
            }
        }
        Map<String, Listing> listings = new LinkedHashMap<>();
        for (Listing l : Listing.load(lfile)) {
            listings.put(l.key(), l);
        }
        int nq = 0;
        for (Question q : Question.load(qfile)) {
            Listing l = listings.get(q.key());
            assertTrue(l != null, "문항 " + q.id() + "의 매물이 매물 입력에 없다");
            String name = l.fields().getOrDefault("stockName", "");
            JsonNode ts = templates.get(q.qtype());
            int found = -1;
            for (int i = 0; i < ts.size(); i++) {
                if (ts.get(i).asText().replace("{이름}", name).equals(q.text())) {
                    assertEquals(-1, found, "문항 " + q.id() + "에 맞는 틀이 둘이다");
                    found = i;
                }
            }
            assertTrue(found >= 0, "문항 " + q.id() + "의 틀을 찾지 못했다");
            assertTrue(clauses(table.get(q.qtype()).get(found), name).equals(check.check(q.text(), l, List.of()).clauses()),
                    "문항 " + q.id() + ": 표의 절이 ⓪이 실제 매물로 나눈 절과 다르다");
            nq++;
        }
        System.out.println("의도 절 표 검사: 틀 " + nt + "개, 절 " + nc + "개(의도 " + ni + "), 실제 문항 " + nq + "개의 절이 표와 같음, 지문 " + PRINT.substring(0, 12));
    }
}
