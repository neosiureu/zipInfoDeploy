package com.zipinfo.ai.reflex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.cards.Card;
import com.zipinfo.ai.cards.CardTools;
import com.zipinfo.ai.cards.Cards;
import com.zipinfo.ai.data.ListingMeta;
import com.zipinfo.ai.loop.Hook;
import com.zipinfo.ai.loop.QuestionLoop;
import com.zipinfo.ai.loop.RunRecord;
import com.zipinfo.ai.model.AnthropicModelClient;
import com.zipinfo.ai.model.FakeAnthropic;
import com.zipinfo.ai.model.ModelSettings;
import com.zipinfo.ai.reflex.Memory.Item;
import com.zipinfo.ai.tools.DataSet;
import com.zipinfo.ai.tools.Fixture;
import com.zipinfo.ai.tools.RunState;
import com.zipinfo.ai.tools.ToolResult;
import com.zipinfo.ai.tools.Tools;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 순서 5c: ⑤ 해마·좋아요(설계안 7.6, 열린 결정 5). 기억 뽑기, 찾기와 차례, 좋아요, 안내 자리, 카드 차례, 판 짜기, 고리에 걸었을 때.
 * 자료는 Fixture(영역 41591, 옛 코드 41590은 202610에 무효, 202609는 정말 0건)다. 값은 지어낸 것이다.
 */
class MemoryTest {
    static final ObjectMapper M = new ObjectMapper();
    static DataSet data;
    /** Fixture 매물과 같은 꼴: 영역(현재 시군구) 41591, 아파트 매매 → API apt_trade. */
    static final ListingMeta TRADE = new ListingMeta("L01", "apt|41591|가동|0|12|0", "매매", "화성형", "연습");
    static final Item CODE = new Item("거절", "region_code=41590", "region_code=41591", "");
    static final Item API = new Item("오류", "api=apt", "api=apt_trade", "apt_trade");
    static final Item ZERO = new Item("0건", "region_code=41590", "region_code=41591", "");

    @BeforeAll
    static void load(@TempDir Path dir) throws IOException {
        data = Fixture.data(dir);
    }

    static ObjectNode stats(String api, String code, String start, String end) {
        return M.createObjectNode().put("api", api).put("region_code", code).put("start_month", start).put("end_month", end)
                .put("deal", "매매").put("dong", "").put("jibun", "");
    }

    static ObjectNode stats(String code) {
        return stats("apt_trade", code, "202610", "202610");
    }

    static ObjectNode tool(String name, JsonNode in, boolean error, String code, String result) {
        ObjectNode e = M.createObjectNode().put("type", "tool").put("name", name);
        e.set("input", in);
        return e.put("result", result).put("is_error", error).put("error_code", code);
    }

    static ObjectNode rejected(ObjectNode in) {
        return tool("trade_stats", in, true, "REJECTED", "{\"오류\":\"REJECTED\",\"설명\":\"거절\"}");
    }

    static ObjectNode failed(ObjectNode in, String code) {
        return tool("trade_stats", in, true, code, "{\"오류\":\"" + code + "\",\"설명\":\"오류\"}");
    }

    static ObjectNode ok(ObjectNode in, int sample) {
        return tool("trade_stats", in, false, "", "{\"id\":\"S1\",\"S1.표본수\":{\"값\":" + sample + ",\"단위\":\"건\"}}");
    }

    static ObjectNode read(String card) {
        return tool("read_card", M.createObjectNode().put("card_id", card), false, "", "{\"카드\":\"" + card + "\",\"글\":\"카드 글\"}");
    }

    static ObjectNode submit(String... findings) {
        ObjectNode e = M.createObjectNode().put("type", "submit");
        var a = e.putArray("findings");
        for (String f : findings) {
            a.add(f);
        }
        return e;
    }

    static List<JsonNode> seq(List<?>... parts) {
        List<JsonNode> out = new ArrayList<>();
        for (List<?> p : parts) {
            p.forEach(x -> out.add((JsonNode) x));
        }
        return out;
    }

    static Memory.Run run(RunRecord.Outcome o, List<JsonNode> events) {
        return new Memory.Run(TRADE, o, events);
    }

    static List<Item> extract(JsonNode... events) {
        return Memory.extract(run(RunRecord.Outcome.FAILED_NO_SUBMIT, List.of(events)));
    }

    static final List<JsonNode> CODE_RUN = List.of(rejected(stats("41590")), ok(stats("41591"), 4));
    static final List<JsonNode> API_RUN = List.of(failed(stats("apt", "41591", "202610", "202610"), "BAD_INPUT"), ok(stats("41591"), 4));
    static final List<JsonNode> ZERO_RUN = List.of(ok(stats("41590"), 0), ok(stats("41591"), 4));

    // ---------------------------------------------------------------- 기억 뽑기(열린 결정 5 (가)(나))

    @Test
    void extractsOnlyApiAndCodePathsThatEndInTheListingsCall() {
        assertEquals(List.of(CODE), extract(rejected(stats("41590")), ok(stats("41591"), 4)), "② 거절 뒤 매물의 코드로 풀림");
        assertEquals(List.of(), extract(rejected(stats("41591")), ok(stats("41591"), 4)), "⓪·③ 거절 뒤 같은 인자로 다시 부른 것은 뽑지 않는다");
        assertEquals(List.of(), extract(rejected(stats("41591").put("dong", "가동").put("jibun", "12").put("deal", "전세")),
                ok(stats("apt_trade", "41591", "202609", "202610"), 4)), "기간·deal·dong·jibun이 바뀐 것은 세지 않는다");
        assertEquals(List.of(API), extract(failed(stats("apt", "41591", "202610", "202610"), "BAD_INPUT"), ok(stats("41591"), 4)));
        assertEquals(List.of(), extract(failed(stats("apt", "41591", "202610", "202610"), "BAD_INPUT"), ok(stats("rh_trade", "41591", "202610", "202610"), 1)),
                "매물의 API가 아닌 부르기로 풀린 것은 뽑지 않는다");
        assertEquals(List.of(), extract(rejected(stats("41591")), ok(stats("41590"), 0)), "그 달에 무효인 코드로 풀린 것도 뽑지 않는다");
        assertEquals(List.of(), extract(rejected(stats("apt_trade", "41591", "202609", "202609")), ok(stats("apt_trade", "41590", "202609", "202609"), 0)),
                "정말 0건 달에만 부른 옛 코드는 표에서 유효여도 매물의 현재 코드가 아니라 풀린 것이 아니다(거꾸로 된 길을 막는다)");
        assertEquals(List.of(ZERO), extract(ok(stats("41590"), 0), ok(stats("41591"), 4)), "0건 뒤 매물의 코드로 풀림");
        assertEquals(List.of(), extract(ok(stats("41591").put("dong", "가동").put("jibun", "12"), 0), ok(stats("41591"), 4)), "0건 뒤 넓히기는 뽑지 않는다");
        assertEquals(List.of(ZERO), extract(ok(stats("41590"), 0), ok(stats("apt_trade", "41591", "202609", "202609"), 0), ok(stats("41591"), 4)),
                "0건은 표본이 있는 부르기가 올 때까지 열려 있다");
        assertEquals(List.of(), extract(ok(stats("41590"), 0), ok(stats("apt_trade", "41591", "202609", "202609"), 0)), "표본이 없으면 풀린 것이 아니다");
        assertEquals(List.of(CODE, new Item("오류", "api=apt, region_code=41590", "api=apt_trade, region_code=41591", "apt_trade")),
                extract(rejected(stats("41590")), failed(stats("apt", "41590", "202610", "202610"), "BAD_INPUT"), ok(stats("41591"), 4),
                        rejected(stats("41590")), ok(stats("41591"), 4)),
                "받아들여진 부르기 하나가 앞의 막힘을 모두 닫고, 한 실행에서 같은 항목은 한 번만 낸다");
        assertEquals(List.of(), extract(tool("convert_area", M.createObjectNode(), true, "BAD_INPUT", "{}"), ok(stats("41591"), 4)), "trade_stats만 본다");
        assertThrows(IllegalArgumentException.class, () -> extract(rejected(stats("41590")), tool("trade_stats", stats("41591"), false, "", "{}")),
                "받아들여진 결과에 표본 수가 없으면 기록이 틀린 것이다");
    }

    // ---------------------------------------------------------------- 찾기와 차례(열린 결정 5 (다))

    @Test
    void lookupKeepsKPerListingByRunsThenText() {
        List<Memory.Run> runs = List.of(run(RunRecord.Outcome.FAILED_NO_SUBMIT, CODE_RUN), run(RunRecord.Outcome.FAILED_NO_SUBMIT, seq(CODE_RUN, API_RUN)),
                run(RunRecord.Outcome.FAILED_NO_SUBMIT, seq(API_RUN, ZERO_RUN)), run(RunRecord.Outcome.FAILED_NO_SUBMIT, API_RUN));
        Memory m = Memory.build(runs);
        assertEquals(Map.of("41591", Map.of(API, 3, CODE, 2, ZERO, 1)), m.items(), "항목은 매물의 현재 시군구 아래에 둔다");
        assertEquals(List.of(API, CODE), m.lookup("41591", "apt_trade"), "낸 실행이 많은 순으로 K개");
        assertEquals(2, Memory.K);
        assertEquals(m.lookup("41591", "apt_trade"), Memory.build(runs.reversed()).lookup("41591", "apt_trade"), "실행 차례와 상관없다");
        assertEquals(List.of(CODE, ZERO), m.lookup("41591", "apt_rent"), "지역 코드 길은 같은 시군구면 API가 달라도 보이고, api가 바뀐 길은 그 API에만 보인다");
        assertEquals(List.of(), m.lookup("11680", "apt_trade"));
        Memory tie = Memory.build(List.of(run(RunRecord.Outcome.FAILED_NO_SUBMIT, CODE_RUN), run(RunRecord.Outcome.FAILED_NO_SUBMIT, ZERO_RUN)));
        assertEquals(List.of(ZERO, CODE), tie.lookup("41591", "apt_trade"), "같으면 (막힌 꼴, 막힌 값, 풀린 값)의 글자 순");
        assertTrue(Memory.EMPTY.items().isEmpty() && Memory.EMPTY.likes().isEmpty());
    }

    // ---------------------------------------------------------------- 좋아요

    static Cards six() {
        List<Card> cs = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            cs.add(Cards.parse(IntentTest.card("K0" + i, "카드" + i, "tag", "낱말" + i)));
        }
        return new Cards(cs);
    }

    @Test
    void likesCountCardsReadInCleanAcceptedRuns() {
        ObjectNode badRead = tool("read_card", M.createObjectNode().put("card_id", "K09"), true, "UNKNOWN_CARD", "{\"오류\":\"UNKNOWN_CARD\",\"설명\":\"없다\"}");
        Memory m = Memory.build(List.of(
                run(RunRecord.Outcome.SUBMITTED, List.of(read("K02"), read("K02"), read("K04"), submit())),
                run(RunRecord.Outcome.SUBMITTED, List.of(read("K05"), submit("stat_claims[0] SAMPLE"))),
                run(RunRecord.Outcome.FAILED_NO_SUBMIT, List.of(read("K06"))),
                run(RunRecord.Outcome.SUBMITTED, List.of(badRead, read("K04"), submit("text NUMERAL"), submit()))));
        assertEquals(Map.of("K02", 1, "K04", 2), m.likes(), "한 실행에 한 번, 받아들여진 제출의 대조에 걸린 것이 없을 때만, 오류 난 읽기는 빼고");
        assertEquals(List.of("K04", "K02", "K01", "K03", "K05", "K06"), six().all().stream().sorted(m.cardOrder()).map(Card::id).toList());
        assertEquals(List.of("K01", "K02", "K03", "K04", "K05", "K06"), six().all().stream().sorted(Memory.EMPTY.cardOrder()).map(Card::id).toList());
    }

    // ---------------------------------------------------------------- 안내 자리

    static Hook.Decision notice(Hook h, RunState st) {
        return h.decide(Hook.Point.NOTICE, new Hook.Context(st, "", null, null, List.of()));
    }

    @Test
    void noticeShowsUpToKLinesForTheListing() {
        Memory m = Memory.build(List.of(run(RunRecord.Outcome.FAILED_NO_SUBMIT, seq(CODE_RUN, API_RUN, ZERO_RUN)),
                run(RunRecord.Outcome.FAILED_NO_SUBMIT, seq(API_RUN, CODE_RUN))));
        MemoryNotice n = new MemoryNotice(m);
        Hook.Decision d = notice(n, Fixture.run("L01", "0", 350_000_000L, 0, "84"));
        assertEquals(Hook.Decision.Kind.NOTICE, d.kind());
        assertEquals("지난 실행에서 막혔다 풀린 길(같은 시군구, 거절): region_code=41590 → region_code=41591\n"
                + "지난 실행에서 막혔다 풀린 길(같은 시군구·API, 오류): api=apt → api=apt_trade", d.line());
        Hook.Decision rent = notice(n, Fixture.run("L02", "1", 300_000_000L, 0, "84"));
        assertEquals("지난 실행에서 막혔다 풀린 길(같은 시군구, 거절): region_code=41590 → region_code=41591\n"
                + "지난 실행에서 막혔다 풀린 길(같은 시군구, 0건): region_code=41590 → region_code=41591", rent.line(),
                "전세 매물(apt_rent)에는 지역 코드 길만 보인다");
        assertEquals(Hook.Decision.Kind.PASS, notice(new MemoryNotice(Memory.EMPTY), Fixture.run("L01", "0", 350_000_000L, 0, "84")).kind());
        assertTrue(n.at(Hook.Point.NOTICE));
        assertFalse(n.at(Hook.Point.START) || n.at(Hook.Point.BEFORE_TOOL) || n.at(Hook.Point.SUBMIT));
        assertEquals("⑤", n.name());
    }

    // ---------------------------------------------------------------- 카드 차례와 판 짜기

    static List<String> found(ToolResult r) throws Exception {
        List<String> ids = new ArrayList<>();
        M.readTree(r.text()).get("카드").forEach(c -> ids.add(c.get("카드").asText()));
        return ids;
    }

    @Test
    void cardsComeInLikesOrderOnlyWhereMemoryIsOn() throws Exception {
        Cards cs = IntentTest.cards();
        Memory m = Memory.build(List.of(run(RunRecord.Outcome.SUBMITTED, List.of(read("K03"), read("K02"), submit())),
                run(RunRecord.Outcome.SUBMITTED, List.of(read("K03"), submit()))));
        ObjectNode q = M.createObjectNode().put("query", "apple pear price");
        RunState st = Fixture.run("L01", "0", 350_000_000L, 0, "84");
        assertEquals(List.of("K03", "K02", "K01"), found(CardTools.handlers(cs, m.cardOrder()).get("search_cards").apply(q, st)));
        assertEquals(List.of("K01", "K02", "K03"), found(CardTools.handlers(cs).get("search_cards").apply(q, st)));
        IntentLists lists = IntentLists.parse(IntentTest.LISTS);
        IntentCheck.Result r = new IntentCheck(lists, Set.of(), cs, m.cardOrder()).check(IntentTest.Q1, IntentTest.listing(""), List.of(IntentTest.APPLE));
        assertEquals(List.of("K03", "K01"), r.tokens().get(0).cards().stream().map(Card::id).toList(), "⓪이 돌려주는 카드도 좋아요 순");

        List<Part> all = List.of(Part.INTENT, Part.LOCK, Part.REFLEX, Part.GATE, Part.MEMORY);
        Setup nine = Setup.of(new Variant("c09", "전부", "m", Variant.CardMode.TOOLS, all), data, cs, lists, m, 12);
        assertEquals(9, nine.specs().size());
        assertEquals(List.of("⓪", "③", "②", "④", "⑤"), nine.config().hooks().stream().map(Hook::name).toList());
        assertEquals(List.of("K03", "K02", "K01"), found(nine.config().extra().get("search_cards").apply(q, st)));
        RunState passed = IntentTest.state(IntentTest.Q1);
        JsonNode out = M.readTree(nine.config().extra().get("declare_intents").apply(IntentTest.input(IntentTest.APPLE, IntentTest.PEAR), passed).text());
        assertEquals("K03", out.get("토큰").get(0).get("찾은 카드").get(0).get("카드").asText());
        Setup eight = Setup.of(new Variant("c08", "①+⑤", "m", Variant.CardMode.TOOLS, List.of(Part.MEMORY)), data, cs, null, m, 12);
        assertEquals(8, eight.specs().size());
        assertEquals(List.of("⑤"), eight.config().hooks().stream().map(Hook::name).toList());
        Setup three = Setup.of(new Variant("c03", "①", "m", Variant.CardMode.TOOLS, List.of()), data, cs, null, m, 12);
        assertEquals(List.of("K01", "K02", "K03"), found(three.config().extra().get("search_cards").apply(q, st)), "⑤가 꺼진 판은 ID 순");
        assertThrows(IllegalArgumentException.class,
                () -> Setup.of(new Variant("c08", "①+⑤", "m", Variant.CardMode.TOOLS, List.of(Part.MEMORY)), data, cs, null, null, 12));
    }

    // ---------------------------------------------------------------- 고리에 걸었을 때(가짜 API 서버)

    static RunRecord loop(FakeAnthropic api, Setup s, RunState st) throws Exception {
        var client = new AnthropicModelClient("test-key", api.url(), new ModelSettings("claude-haiku-5-5", 4000, ModelSettings.Thinking.ADAPTIVE, "high"),
                Duration.ofSeconds(20));
        return new QuestionLoop(client, new Tools(data), data, s.config()).start(s.system(), s.specs(), "이 매물 가격이 주변보다 비싼가요?", st);
    }

    static List<String> decisions(RunRecord rec) {
        return rec.events().stream().filter(e -> e.get("type").equals("hook")).map(e -> e.get("point") + " " + e.get("part") + " " + e.get("decision")).toList();
    }

    @Test
    void inTheLoopARunRecordFillsTheMemoryAndTheNextRunGetsTheNotice() throws Exception {
        try (FakeAnthropic api = new FakeAnthropic()) {
            api.then(ReflexTest.tool("t1", "read_card", M.createObjectNode().put("card_id", "K02")));
            api.then(ReflexTest.tool("t2", "trade_stats", ReflexTest.stats("41590", "202610", "202610")));
            api.then(ReflexTest.tool("t3", "trade_stats", ReflexTest.stats("41591", "202610", "202610")));
            api.then(ReflexTest.tool("t4", "compare_prices", ReflexTest.compare()));
            api.then(ReflexTest.tool("t5", "submit_report", ReflexTest.report(4)));
            Setup fill = Setup.of(new Variant("t", "②+⑤", "m", Variant.CardMode.TOOLS, List.of(Part.REFLEX, Part.MEMORY)), data, ReflexTest.cards(), null,
                    Memory.EMPTY, 10);
            RunState st = Fixture.run("L01", "0", 350_000_000L, 0, "84");
            RunRecord rec = loop(api, fill, st);
            assertEquals(RunRecord.Outcome.SUBMITTED, rec.outcome());
            assertEquals(List.of("NOTICE ⑤ PASS", "BEFORE_TOOL ② REJECT", "BEFORE_TOOL ② PASS"), decisions(rec));
            Memory m = Memory.build(List.of(Memory.Run.of(st.meta(), rec)));
            assertEquals(List.of(CODE), m.lookup("41591", "apt_trade"));
            assertEquals(Map.of("K02", 1), m.likes());

            api.then(ReflexTest.done());
            api.then(ReflexTest.done());
            Setup next = Setup.of(new Variant("c08", "①+⑤", "m", Variant.CardMode.TOOLS, List.of(Part.MEMORY)), data, ReflexTest.cards(), null, m, 10);
            RunRecord rec2 = loop(api, next, Fixture.run("L02", "0", 330_000_000L, 0, "84"));
            assertEquals(RunRecord.Outcome.FAILED_NO_SUBMIT, rec2.outcome());
            assertEquals(List.of("NOTICE ⑤ NOTICE"), decisions(rec2));
            String first = api.requests.get(5).get("messages").get(0).get("content").get(0).get("text").asText();
            assertTrue(first.startsWith(MemoryNotice.line(CODE) + "\n\n이 매물 가격이 주변보다 비싼가요?\n\n[매물]"), first);
            assertFalse(api.requests.get(0).get("messages").get(0).toString().contains("지난 실행"), "빈 기억이면 안내가 없다");
        }
    }
}
