package com.zipinfo.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.cards.Cards;
import com.zipinfo.ai.data.AddressBook;
import com.zipinfo.ai.data.DataFiles;
import com.zipinfo.ai.data.History;
import com.zipinfo.ai.data.Listing;
import com.zipinfo.ai.data.ListingMeta;
import com.zipinfo.ai.loop.Hook;
import com.zipinfo.ai.loop.Hook.Decision;
import com.zipinfo.ai.loop.RunRecord;
import com.zipinfo.ai.reflex.IntentCheck;
import com.zipinfo.ai.reflex.IntentLists;
import com.zipinfo.ai.reflex.LearningLock;
import com.zipinfo.ai.reflex.Memory;
import com.zipinfo.ai.reflex.Part;
import com.zipinfo.ai.reflex.ReflexRule;
import com.zipinfo.ai.reflex.Setup;
import com.zipinfo.ai.reflex.Variant;
import com.zipinfo.ai.reflex.Variants;
import com.zipinfo.ai.tools.DataSet;
import com.zipinfo.ai.tools.RunState;
import com.zipinfo.ai.tools.ToolResult;
import com.zipinfo.ai.tools.Tools;
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
 * 순서 5의 장치를 실제 파일에 대 본다(설계안 7.2~7.4·7.7).
 * - 판 설정 파일이 설계안 7.7(01의 판 10개)과 같다.
 * - ③이 읽으라고 할 카드 둘이 연습 전 카드에 그 제목으로 있다.
 * - 정답 대조의 부르기는 ②를 모두 지난다. 지역 코드만 다른 코드로 바꾸면 모두 거절되고, 처방은 매물의 현재 코드와 그 부르기의 모든 달이다.
 * - ③은 개편 지역(후보 코드 둘 이상) 매물에서만 지역 코드 카드를 묻는다. 면적 환산은 모든 매물에서 묻는다.
 * - (5b) ⓪의 목록 파일을 읽는다. 매물 75개의 이름·도로명주소 글은 질문 안에서 통째로 남는다(나누지 않고 분모에도 넣지 않음).
 * - (5b·5c) 판 설정대로 짜면 판마다 도구와 사슬이 맞다. ⑤가 든 판은 채우기 전의 빈 기억으로 짠다.
 * - (5c) ⑤의 기억 뽑기가 실제 유효성 표·도구 결과와 맞는다(memoryOnTheRealData).
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

        // 판 설정대로 짜 본다(5b·5c): 도구 이름과 사슬. ⑤가 든 판(c08 ①+⑤, c09 전부)은 채우기 전의 빈 기억으로 짠다.
        Cards cards = Cards.load(EXP.resolve("cards/v0"));
        IntentLists lists = IntentLists.load(EXP.resolve("intents/intent_lists_v0.json"));
        Map<String, String> got = new java.util.TreeMap<>();
        for (Variant v : Variants.load(EXP.resolve("variants/variants_v0.json"))) {
            Setup s = Setup.of(v, data, cards, lists, Memory.EMPTY, 12);
            got.put(v.id(), s.specs().size() + " " + s.config().hooks().stream().map(Hook::name).collect(Collectors.joining()) + " "
                    + s.system().contains("[카드]"));
        }
        assertEquals(Map.of("c01", "6  false", "c02", "6  true", "c03", "8  false", "c04", "9 ⓪ false", "c05", "8 ② false",
                "c06", "8 ③ false", "c07", "8 ④ false", "c08", "8 ⑤ false", "c09", "9 ⓪③②④⑤ false", "c10", "6  false"), got);
    }

    static ObjectNode statsEvent(JsonNode in, boolean error, String code, String result) {
        ObjectNode e = M.createObjectNode().put("type", "tool").put("name", "trade_stats");
        e.set("input", in);
        return e.put("result", result).put("is_error", error).put("error_code", code);
    }

    /**
     * (5c) ⑤를 실제 파일에 대 본다. 매물마다 API가 도구의 API이고, 영역 코드(매물의 현재 코드)가 그 API로 창의 모든 달에 유효하다.
     * 그래서 ⑤는 막힘을 닫은 부르기를 영역 코드로 보고, 지역 코드 길은 같은 시군구의 모든 매물에 보인다(5c 메모 4절 (B)).
     * 정답 부르기 293건마다 다른 후보 코드로 막힌 기록을 지어 넣으면, 거절 뒤에는 늘 (거절, 옛 코드 → 영역 코드) 하나가 나오고,
     * 0건 뒤에는 정답 부르기에 표본이 있을 때만 (0건, 옛 코드 → 영역 코드) 하나가 나온다. 풀린 부르기의 결과는 실제 도구가 낸 것이다.
     */
    @Test
    void memoryOnTheRealData() throws Exception {
        Path out = EXP.resolve("prep/out");
        Path answers = out.resolve("answers_check_v1.json");
        Path listingsCsv = EXP.resolve("frozen/example_listings_v1.csv");
        assumeTrue(Files.exists(answers) && Files.exists(listingsCsv), "prep build·answers 결과와 얼린 매물 파일이 있을 때만 돈다");
        DataSet data = DataFiles.load(EXP);
        Map<String, Listing> listing = new HashMap<>();
        for (Listing l : Listing.load(out.resolve("listings_input_v1.json"))) {
            listing.put(l.key(), l);
        }
        Map<String, ListingMeta> meta = new java.util.TreeMap<>();
        Set<String> codes = new TreeSet<>();
        Set<String> areas = new TreeSet<>();
        Set<String> keys = new TreeSet<>();
        List<String> bad = new ArrayList<>();
        for (ListingMeta m : ListingMeta.load(listingsCsv)) {
            meta.put(m.key(), m);
            codes.addAll(data.validity().candidates(m.area()));
            areas.add(m.area());
            keys.add(m.area() + " " + m.api());
            if (!Tools.APIS.contains(m.api())) {
                bad.add(m.key() + ": 도구에 없는 API");
                continue;
            }
            for (String ym : Tools.months(Tools.FIRST_MONTH, Tools.LAST_MONTH)) {
                if (!data.validity().judge(m.area(), m.api(), ym, m.area()).valid()) {
                    bad.add(m.key() + " " + ym + ": 영역 코드가 무효");
                }
            }
        }
        Tools tools = new Tools(data);
        String zero = "{\"id\":\"S9\",\"S9.표본수\":{\"값\":0,\"단위\":\"건\"}}";
        int runs = 0;
        int afterReject = 0;
        int afterZero = 0;
        int emptyAnswers = 0;
        for (JsonNode c : M.readTree(Files.readString(answers))) {
            String key = c.get("key").asText();
            String tag = key + " " + c.get("qtype").asText() + " " + c.get("months").asInt() + "달";
            ListingMeta m = meta.get(key);
            String area = m.area();
            JsonNode call = c.get("call");
            ToolResult r = tools.call("trade_stats", call, new RunState(listing.get(key), m));
            if (r.isError()) {
                bad.add(tag + ": 정답 부르기가 오류다");
                continue;
            }
            JsonNode res = M.readTree(r.text());
            boolean found = res.get(res.get("id").asText() + ".표본수").get("값").asLong() > 0;
            emptyAnswers += found ? 0 : 1;
            for (String other : codes) {
                if (other.equals(area)) {
                    continue;
                }
                runs++;
                JsonNode wrong = ((ObjectNode) call).deepCopy().put("region_code", other);
                List<Memory.Item> got = Memory.extract(new Memory.Run(m, RunRecord.Outcome.FAILED_NO_SUBMIT,
                        List.of(statsEvent(wrong, true, "REJECTED", "{}"), statsEvent(call, false, "", r.text()))));
                if (!got.equals(List.of(new Memory.Item("거절", "region_code=" + other, "region_code=" + area, "")))) {
                    bad.add(tag + " " + other + ": 거절 뒤 " + got);
                } else {
                    afterReject++;
                }
                List<Memory.Item> z = Memory.extract(new Memory.Run(m, RunRecord.Outcome.FAILED_NO_SUBMIT,
                        List.of(statsEvent(wrong, false, "", zero), statsEvent(call, false, "", r.text()))));
                List<Memory.Item> want = found ? List.of(new Memory.Item("0건", "region_code=" + other, "region_code=" + area, "")) : List.of();
                if (!z.equals(want)) {
                    bad.add(tag + " " + other + ": 0건 뒤 " + z);
                } else {
                    afterZero += z.size();
                }
            }
        }
        System.out.println("해마 검사: 매물 " + meta.size() + "개의 시군구 " + areas.size() + "가지·(시군구, API) " + keys.size() + "가지, 영역 코드가 모든 달에 유효, 거절 뒤 풀림 " + afterReject + "/" + runs
                + ", 0건 뒤 풀림 " + afterZero + "/" + runs + "(표본 없는 정답 부르기 " + emptyAnswers + "건은 뽑지 않음), 어긋난 것 " + bad.size());
        bad.stream().limit(20).forEach(System.out::println);
        assertEquals(List.of(), bad);
    }

    @Test
    void intentListsAndListingTextOnTheRealFiles() throws Exception {
        Path out = EXP.resolve("prep/out");
        Path history = EXP.resolve("frozen/codego/codego_history_v1.csv");
        assumeTrue(Files.exists(out.resolve("listings_input_v1.json")) && Files.exists(history), "매물 입력과 이력 표가 있을 때만 돈다");
        IntentLists lists = IntentLists.load(EXP.resolve("intents/intent_lists_v0.json"));
        assertEquals("v0", lists.version());
        Set<String> names = History.load(history).names();
        IntentCheck check = new IntentCheck(lists, names, Cards.load(EXP.resolve("cards/v0")));
        List<String> bad = new ArrayList<>();
        int n = 0;
        for (Listing l : Listing.load(out.resolve("listings_input_v1.json"))) {
            n++;
            String name = l.fields().get("stockName");
            String road = AddressBook.clean(l.fields().get("stockAddress"));
            for (String q : List.of(name + " 가격", road + " 가격", "가격 " + name + " " + road)) {
                IntentCheck.Result r = check.check(q, l, List.of());
                if (r.clauses().size() != 1 || r.total() != 2) {
                    bad.add(l.key() + ": 절 " + r.clauses().size() + "개, 분모 " + r.total());
                }
            }
        }
        System.out.println("의도 검사: 매물 " + n + "개의 이름·주소 글이 통째로 남음, 목록 " + lists.version() + "(이음 말 " + lists.connectiveEndings().size()
                + ", 이음 낱말 " + lists.connectiveWords().size() + ", 조사·어미 " + lists.particlesEndings().size() + ", 군말 " + lists.fillers().size()
                + ", 낱말 예외 " + lists.wordExceptions().size() + "), 법정동 이름 " + names.size() + ", 어긋난 것 " + bad.size());
        bad.stream().limit(20).forEach(System.out::println);
        assertEquals(List.of(), bad);
    }
}
