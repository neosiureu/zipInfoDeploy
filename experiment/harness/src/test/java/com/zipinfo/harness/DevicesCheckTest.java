package com.zipinfo.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.cards.Cards;
import com.zipinfo.ai.data.DataFiles;
import com.zipinfo.ai.data.Listing;
import com.zipinfo.ai.data.ListingMeta;
import com.zipinfo.ai.loop.Hook;
import com.zipinfo.ai.loop.Hook.Decision;
import com.zipinfo.ai.reflex.LearningLock;
import com.zipinfo.ai.reflex.Part;
import com.zipinfo.ai.reflex.ReflexRule;
import com.zipinfo.ai.reflex.Variants;
import com.zipinfo.ai.tools.DataSet;
import com.zipinfo.ai.tools.RunState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 순서 5a의 장치를 실제 파일에 대 본다(설계안 7.3·7.4·7.7).
 * - 판 설정 파일이 설계안 7.7(01의 판 10개)과 같다.
 * - ③이 읽으라고 할 카드 둘이 연습 전 카드에 그 제목으로 있다.
 * - 정답 대조의 부르기는 ②를 모두 지난다. 지역 코드만 다른 코드로 바꾸면 모두 거절되고, 처방은 매물의 현재 코드와 그 부르기의 모든 달이다.
 * - ③은 개편 지역(후보 코드 둘 이상) 매물에서만 지역 코드 카드를 묻는다. 면적 환산은 모든 매물에서 묻는다.
 * 자료 쪽은 순서 2 build·answers 결과와 얼린 매물 파일이 있을 때만 돈다.
 */
class DevicesCheckTest {
    static final Path EXP = Path.of("..").toAbsolutePath().normalize();
    static final ObjectMapper M = new ObjectMapper();

    @Test
    void variantsFileIsTheTableOfTheDesign() throws Exception {
        String h = "claude-haiku-5-5";
        List<String> got = Variants.load(EXP.resolve("variants/variants_v0.json")).stream()
                .map(v -> v.id() + " " + v.name() + " " + v.model() + " " + v.cards() + " "
                        + v.parts().stream().map(Part::symbol).collect(Collectors.joining()))
                .toList();
        assertEquals(List.of("c01 B0 " + h + " NONE ", "c02 B0+ " + h + " PROMPT ", "c03 ① " + h + " TOOLS ",
                "c04 ①+⓪ " + h + " TOOLS ⓪", "c05 ①+② " + h + " TOOLS ②", "c06 ①+③ " + h + " TOOLS ③", "c07 ①+④ " + h + " TOOLS ④",
                "c08 ①+⑤ " + h + " TOOLS ⑤", "c09 전부 " + h + " TOOLS ⓪③②④⑤", "c10 B1 claude-opus-5-5 NONE "), got);
    }

    @Test
    void lockPointsAtTheseCards() throws Exception {
        Cards cards = Cards.load(EXP.resolve("cards/v0"));
        assertEquals("지역 코드 정하기", cards.get(LearningLock.REGION_CARD).title());
        assertEquals("단위와 면적", cards.get(LearningLock.AREA_CARD).title());
    }

    static Decision decide(Hook h, RunState st, String tool, JsonNode in) {
        return h.decide(Hook.Point.BEFORE_TOOL, new Hook.Context(st, tool, in, null, List.of()));
    }

    @Test
    void reflexAndLockOnTheRealData() throws Exception {
        Path out = EXP.resolve("prep/out");
        Path answers = out.resolve("answers_check_v1.json");
        Path listingsCsv = EXP.resolve("frozen/example_listings_v1.csv");
        assumeTrue(Files.exists(answers) && Files.exists(listingsCsv), "prep build·answers 결과와 얼린 매물 파일이 있을 때만 돈다");
        DataSet data = DataFiles.load(EXP);
        Map<String, Listing> listing = new HashMap<>();
        for (Listing l : Listing.load(out.resolve("listings_input_v1.json"))) {
            listing.put(l.key(), l);
        }
        Map<String, ListingMeta> meta = new HashMap<>();
        Set<String> codes = new TreeSet<>();
        for (ListingMeta m : ListingMeta.load(listingsCsv)) {
            meta.put(m.key(), m);
            codes.addAll(data.validity().candidates(m.area()));
        }
        ReflexRule reflex = new ReflexRule(data);
        LearningLock lock = new LearningLock(data.validity());
        List<String> bad = new ArrayList<>();
        int calls = 0;
        int rejected = 0;
        for (JsonNode c : M.readTree(Files.readString(answers))) {
            String key = c.get("key").asText();
            String tag = key + " " + c.get("qtype").asText() + " " + c.get("months").asInt() + "달";
            String area = meta.get(key).area();
            JsonNode call = c.get("call");
            RunState st = new RunState(listing.get(key), meta.get(key));
            calls++;
            if (!call.get("region_code").asText().equals(area) || decide(reflex, st, "trade_stats", call).kind() != Decision.Kind.PASS) {
                bad.add(tag + ": 정답 부르기가 ②를 지나지 못한다");
            }
            String start = call.get("start_month").asText();
            String end = call.get("end_month").asText();
            String want = "지역 코드가 이 매물에 맞지 않는다. 맞는 코드: " + area + "(" + (start.equals(end) ? start : start + "~" + end) + ")";
            for (String other : codes) {
                if (other.equals(area)) {
                    continue;
                }
                Decision d = decide(reflex, st, "trade_stats", ((ObjectNode) call).deepCopy().put("region_code", other));
                if (d.kind() != Decision.Kind.REJECT || !d.line().equals(want)) {
                    bad.add(tag + " " + other + ": " + d.kind() + " " + d.line());
                } else {
                    rejected++;
                }
            }
        }
        int asks = 0;
        for (Map.Entry<String, ListingMeta> e : new java.util.TreeMap<>(meta).entrySet()) {
            RunState st = new RunState(listing.get(e.getKey()), e.getValue());
            JsonNode any = M.createObjectNode();
            boolean reorganized = data.validity().candidates(e.getValue().area()).size() >= 2;
            Decision d = decide(lock, st, "trade_stats", any);
            if (d.kind() != (reorganized ? Decision.Kind.REJECT : Decision.Kind.PASS)) {
                bad.add(e.getKey() + ": ③의 지역 코드 카드 판정이 다르다");
            }
            asks += reorganized ? 1 : 0;
            if (decide(lock, st, "convert_area", any).kind() != Decision.Kind.REJECT) {
                bad.add(e.getKey() + ": ③이 면적 카드를 묻지 않는다");
            }
        }
        System.out.println("장치 검사: 정답 부르기 " + calls + "건이 ②를 지남, 코드만 바꾼 부르기 " + rejected + "건 거절(코드 " + codes.size()
                + "가지), ③이 지역 코드 카드를 묻는 매물 " + asks + "/" + meta.size() + ", 어긋난 것 " + bad.size());
        bad.stream().limit(20).forEach(System.out::println);
        assertEquals(List.of(), bad);
    }
}
