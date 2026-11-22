package com.zipinfo.ai.reflex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.cards.Card;
import com.zipinfo.ai.cards.Cards;
import com.zipinfo.ai.data.Listing;
import com.zipinfo.ai.data.ListingMeta;
import com.zipinfo.ai.loop.Hook;
import com.zipinfo.ai.loop.QuestionLoop;
import com.zipinfo.ai.loop.RunRecord;
import com.zipinfo.ai.model.AnthropicModelClient;
import com.zipinfo.ai.model.FakeAnthropic;
import com.zipinfo.ai.model.ModelSettings;
import com.zipinfo.ai.reflex.IntentCheck.Status;
import com.zipinfo.ai.reflex.IntentCheck.Token;
import com.zipinfo.ai.tools.DataSet;
import com.zipinfo.ai.tools.Fixture;
import com.zipinfo.ai.tools.RunState;
import com.zipinfo.ai.tools.ToolResult;
import com.zipinfo.ai.tools.Tools;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 순서 5b: ⓪ 의도 잠금(설계안 7.2). 절 나누기, 분모, 세기, 통과선, 도구와 시작 자리, 고리에 걸었을 때. 글은 시험용으로 지어낸 것이다. */
class IntentTest {
    static final ObjectMapper M = new ObjectMapper();
    static final String LISTS = """
            {"version": "t", "connective_endings": ["고", "면", "는데"], "connective_words": ["그리고"],
             "particles_endings": ["은", "는", "이", "가", "을", "나요", "고", "면", "요"], "fillers": ["좀", "알려 주세요"],
             "word_exceptions": ["최고"]}
            """;
    static final String Q1 = "사과 값은 좀 얼마고 배는 몇 개 남았나요? 알려 주세요.";

    static String card(String id, String title, String en, String ko) {
        return "# " + id + " " + title + "\n\n- 영어 태그: " + en + "\n- 한글 낱말: " + ko + "\n\n## 핵심\n\n- " + title + "의 핵심.\n\n## 사용법\n\n1. 한다.\n\n## 기록\n\n";
    }

    static Cards cards() {
        return new Cards(List.of(Cards.parse(card("K01", "사과", "apple", "사과")), Cards.parse(card("K02", "배", "pear", "배")),
                Cards.parse(card("K03", "값", "price, cost", "값, 가격"))));
    }

    static IntentCheck check() {
        return new IntentCheck(IntentLists.parse(LISTS), Set.of("장안면"), cards());
    }

    static Listing listing(String name) {
        Map<String, String> f = new LinkedHashMap<>();
        for (String n : Listing.NINE) {
            f.put(n, "");
        }
        f.put("stockName", name);
        f.put("stockAddress", "12345^^^가나시 다라로 1^^^");
        return new Listing("L01", f, Map.of("매물.가격", 1L, "매물.월세", 0L, "매물.면적", 84L, "매물.층", 1L));
    }

    static IntentCheck.Result run(String q, String name, Token... tokens) {
        return check().check(q, listing(name), List.of(tokens));
    }

    static Token t(String source, String corrected, String expansion, String english) {
        return new Token(source, corrected, expansion, english);
    }

    static final Token APPLE = t("사과 값은 좀 얼마고", "사과 값은 얼마고", "사과 가격", "apple price");
    static final Token PEAR = t("배는 몇 개 남았나요", "배는 몇 개 남았나요", "배 개수", "pear count");

    // ---------------------------------------------------------------- 절

    @Test
    void clausesSplitAtSentenceEndsEndingsAndWords() {
        assertEquals(List.of("가나.", "다라?", "마바!", "사아", "자차"), run("가나. 다라?  마바! 사아\n\n자차", "").clauses());
        assertEquals(List.of("사과는 있고,", "배는 없다"), run("사과는 있고, 배는 없다", "").clauses());
        assertEquals(List.of("사과는 있다", "그리고 배는 없다"), run("사과는 있다 그리고 배는 없다", "").clauses(), "이음 낱말 앞에서 나눈다");
        assertEquals(List.of("사과면", "배는데", "귤"), run("사과면 배는데 귤", "").clauses());
        assertEquals(List.of("최고 사과는 장안면 배다"), run("최고 사과는 장안면 배다", "").clauses(), "낱말 예외와 법정동 이름에서는 나누지 않는다");
        assertEquals(List.of("고구마는 고 사과"), run("고구마는 고 사과", "").clauses(), "어절 끝이 아니거나 어절이 이음 말 자체면 나누지 않는다");
        assertEquals(List.of("해뜰.마을고 사과는 있다"), run("해뜰.마을고 사과는 있다", "해뜰.마을고").clauses(), "매물 이름 안에서는 나누지 않는다");
        assertEquals(List.of("해뜰.마을고이고", "사과는 있다"), run("해뜰.마을고이고 사과는 있다", "해뜰.마을고").clauses(), "이름 뒤에 붙은 이음 말에서는 나눈다");
    }

    // ---------------------------------------------------------------- 분모

    @Test
    void denominatorDropsSpacesMarksParticlesFillersWordsAndListingText() {
        IntentCheck.Result r = run(Q1, "");
        assertEquals(10, r.total(), "사과2 값1 얼마2 배1 몇1 개1 남았2");
        assertEquals(List.of("사과 값은 좀 얼마고", "배는 몇 개 남았나요?", "알려 주세요."), r.clauses());
        assertEquals(List.of("사과 값은 좀 얼마고", "배는 몇 개 남았나요?"), r.uncovered(), "군말뿐인 절은 처방에 나오지 않는다");
        assertEquals(2, run("해뜰.마을고 사과는", "해뜰.마을고").total(), "매물 이름은 분모에 넣지 않는다");
        assertEquals(2, run("가나시 다라로 1 사과", "").total(), "매물 도로명주소도 넣지 않는다");
        assertEquals(4, run("사과 3개", "").total(), "숫자는 센다");
        assertEquals(1, run("그리고 배", "").total(), "이음 낱말은 분모에서 뺀다");
        assertEquals(2, run("최고 좀", "").total(), "낱말 예외는 어미를 떼지 않는다");
        assertEquals(0, run("좀 알려 주세요", "").total());
        assertTrue(run("좀 알려 주세요", "").passed(), "분모가 0이면 통과다");
    }

    // ---------------------------------------------------------------- 세기와 통과선

    @Test
    void onlyVerbatimTokensInOneClauseThatFindACardCount() {
        IntentCheck.Result both = run(Q1, "", APPLE, PEAR);
        assertTrue(both.passed());
        assertEquals(10, both.covered());
        assertEquals(List.of(Status.COUNTED, Status.COUNTED), both.tokens().stream().map(IntentCheck.TokenResult::status).toList());
        assertEquals(List.of("K01", "K03"), both.tokens().get(0).cards().stream().map(Card::id).toList());
        IntentCheck.Result one = run(Q1, "", APPLE);
        assertFalse(one.passed());
        assertEquals(5, one.covered());
        assertEquals(List.of("배는 몇 개 남았나요?"), one.uncovered());
        assertEquals(Status.NOT_IN_QUESTION, run(Q1, "", t("사과 가격은 얼마", "사과", "", "apple")).tokens().get(0).status());
        assertEquals(Status.CROSSES_CLAUSES, run(Q1, "", t("얼마고 배는", "배", "", "pear")).tokens().get(0).status());
        assertEquals(Status.NO_CARD, run(Q1, "", t("배는 몇 개 남았나요", "몇 개", "남은 것", "count")).tokens().get(0).status());
        assertEquals(Status.NOT_IN_QUESTION, run(Q1, "", t("  ", "사과", "", "apple")).tokens().get(0).status());
        assertEquals(5, run(Q1, "", APPLE, t("사과 값은", "사과", "", "")).covered(), "겹친 글자는 한 번 센다");
        Token nfd = t(Normalizer.normalize("사과  값은\t좀 얼마고", Normalizer.Form.NFD), "사과", "", "");
        assertEquals(Status.COUNTED, run(Q1, "", nfd).tokens().get(0).status(), "NFC·빈칸 정리 뒤 대 본다");
        assertEquals(Status.COUNTED, run(Q1, "", t("배는 몇 개", "", "배 몇 개", "")).tokens().get(0).status(), "의도보강으로도 찾는다");
    }

    @Test
    void passLineIsSeventySevenPercent() {
        String q77 = "쿠".repeat(77) + " " + "푸".repeat(23);
        assertTrue(run(q77, "", t("쿠".repeat(77), "사과", "", "")).passed());
        String q76 = "쿠".repeat(76) + " " + "푸".repeat(24);
        IntentCheck.Result r = run(q76, "", t("쿠".repeat(76), "사과", "", ""));
        assertEquals(76, r.covered());
        assertEquals(100, r.total());
        assertFalse(r.passed());
    }

    // ---------------------------------------------------------------- 도구와 시작 자리

    static RunState state(String question) {
        RunState st = new RunState(listing(""), new ListingMeta("L01", "apt|11680|가동|0|12|0", "매매", "일반", "본시험"));
        st.question(question);
        return st;
    }

    static ObjectNode input(Token... tokens) {
        ObjectNode in = M.createObjectNode();
        var a = in.putArray("intents");
        for (Token x : tokens) {
            a.addObject().put("source_text", x.source()).put("corrected_text", x.corrected()).put("intent_expansion", x.expansion())
                    .put("english_intent", x.english());
        }
        return in;
    }

    @Test
    void declareIntentsPassesOrRejectsAndOnlyThenMarksTheRun() throws Exception {
        var h = IntentTools.handler(check());
        RunState st = state(Q1);
        ToolResult fail = h.apply(input(APPLE), st);
        assertTrue(fail.isError());
        assertEquals("REJECTED", fail.errorCode());
        assertEquals("이 부분의 의도도 토큰으로 적어라: 「배는 몇 개 남았나요?」", M.readTree(fail.text()).get("설명").asText());
        assertFalse(st.intentsPassed());
        ToolResult ok = h.apply(input(APPLE, PEAR), st);
        assertFalse(ok.isError());
        JsonNode out = M.readTree(ok.text());
        assertTrue(out.get("통과").asBoolean());
        assertEquals("K01", out.get("토큰").get(0).get("찾은 카드").get(0).get("카드").asText());
        assertEquals("배", out.get("토큰").get(1).get("찾은 카드").get(0).get("제목").asText());
        assertTrue(st.intentsPassed());
        assertEquals("BAD_INPUT", h.apply(M.createObjectNode(), state(Q1)).errorCode());
        ObjectNode short3 = input(APPLE);
        ((ObjectNode) short3.get("intents").get(0)).remove("english_intent");
        assertEquals("BAD_INPUT", h.apply(short3, state(Q1)).errorCode());
    }

    @Test
    void lockWaitsForThePass() {
        IntentLock l = new IntentLock();
        RunState st = state(Q1);
        Hook.Decision d = l.decide(Hook.Point.START, new Hook.Context(st, "trade_stats", M.createObjectNode(), null, List.of()));
        assertEquals(Hook.Decision.Kind.REJECT, d.kind());
        assertEquals("먼저 declare_intents로 의도를 적어라.", d.line());
        st.markIntentsPassed();
        assertEquals(Hook.Decision.Kind.PASS, l.decide(Hook.Point.START, new Hook.Context(st, "trade_stats", M.createObjectNode(), null, List.of())).kind());
        assertTrue(l.at(Hook.Point.START));
        assertFalse(l.at(Hook.Point.BEFORE_TOOL) || l.at(Hook.Point.SUBMIT) || l.at(Hook.Point.NOTICE));
        assertEquals("⓪", l.name());
    }

    static void strict(JsonNode n, List<String> problems, String where) {
        if (n.has("anyOf") || n.has("oneOf") || n.has("allOf") || n.has("minimum") || n.has("maximum")) {
            problems.add(where);
        }
        if ("object".equals(n.path("type").asText())) {
            List<String> props = new ArrayList<>();
            n.get("properties").fieldNames().forEachRemaining(props::add);
            List<String> req = new ArrayList<>();
            n.get("required").forEach(x -> req.add(x.asText()));
            if (!props.equals(req) || n.path("additionalProperties").asBoolean(true)) {
                problems.add(where);
            }
            Iterator<String> it = n.get("properties").fieldNames();
            while (it.hasNext()) {
                String f = it.next();
                strict(n.get("properties").get(f), problems, where + "." + f);
            }
        }
        if ("array".equals(n.path("type").asText())) {
            strict(n.get("items"), problems, where + "[]");
        }
    }

    @Test
    void toolSpecIsStrict() {
        List<String> problems = new ArrayList<>();
        strict(IntentTools.SPEC.inputSchema(), problems, "declare_intents");
        assertEquals(List.of(), problems);
        assertEquals(List.of("source_text", "corrected_text", "intent_expansion", "english_intent"),
                M.convertValue(IntentTools.SPEC.inputSchema().get("properties").get("intents").get("items").get("required"), List.class));
        assertTrue(IntentTools.SPEC.description().contains("77%"));
    }

    @Test
    void listsFileFormIsChecked() {
        IntentLists l = IntentLists.parse(LISTS);
        assertEquals(List.of("좀", "알려 주세요"), l.fillers());
        List<String> bad = List.of(
                LISTS.replace("\"version\": \"t\", ", ""),
                LISTS.replace("\"connective_words\": [\"그리고\"]", "\"connective_words\": []"),
                LISTS.replace("[\"고\", \"면\", \"는데\"]", "[\"고\", \"고\"]"),
                LISTS.replace("[\"고\", \"면\", \"는데\"]", "[\" 고\"]"),
                LISTS.replace("[\"최고\"]", "[\"" + Normalizer.normalize("최고", Normalizer.Form.NFD) + "\"]"),
                LISTS.replace("\"알려 주세요\"", "\"알려  주세요\""),
                LISTS.replace("[\"최고\"]", "[\"최 고\"]"),
                LISTS.replace("\"word_exceptions\": [\"최고\"]", "\"word_exceptions\": [\"최고\"], \"more\": []"),
                "[]");
        for (String b : bad) {
            assertThrows(IllegalArgumentException.class, () -> IntentLists.parse(b), b);
        }
    }

    // ---------------------------------------------------------------- 고리에 걸었을 때(가짜 API 서버)

    static FakeAnthropic.Reply tool(String id, String name, ObjectNode in) {
        return FakeAnthropic.sse(List.of(FakeAnthropic.toolUse(id, name, in)), "tool_use", null);
    }

    @Test
    void inTheLoopDataToolsAndSubmitWaitForTheDeclaration(@TempDir Path dir) throws Exception {
        DataSet data = Fixture.data(dir);
        Setup s = Setup.of(new Variant("c04", "①+⓪", "m", Variant.CardMode.TOOLS, List.of(Part.INTENT)), data, cards(), IntentLists.parse(LISTS), null, 12);
        ObjectNode stats = M.createObjectNode().put("api", "apt_trade").put("region_code", "41591").put("start_month", "202610")
                .put("end_month", "202610").put("deal", "매매").put("dong", "").put("jibun", "");
        try (FakeAnthropic api = new FakeAnthropic()) {
            api.then(tool("t1", "trade_stats", stats));
            ObjectNode empty = M.createObjectNode().put("text", "끝");
            empty.putArray("stat_claims");
            empty.putArray("value_claims");
            empty.putArray("context_claims");
            api.then(tool("t2", "submit_report", empty));
            api.then(tool("t3", "read_card", M.createObjectNode().put("card_id", "K01")));
            api.then(tool("t4", "declare_intents", input(APPLE)));
            api.then(tool("t5", "declare_intents", input(APPLE, PEAR)));
            api.then(tool("t6", "trade_stats", stats));
            api.then(FakeAnthropic.sse(List.of(FakeAnthropic.text("끝")), "end_turn", null));
            api.then(FakeAnthropic.sse(List.of(FakeAnthropic.text("끝")), "end_turn", null));
            var client = new AnthropicModelClient("test-key", api.url(), new ModelSettings("claude-haiku-5-5", 4000, ModelSettings.Thinking.ADAPTIVE, "high"),
                    Duration.ofSeconds(20));
            QuestionLoop loop = new QuestionLoop(client, new Tools(data), data, s.config());
            RunRecord rec = loop.start(s.system(), s.specs(), Q1, Fixture.run("L01", "0", 350_000_000L, 0, "84"));
            assertEquals(RunRecord.Outcome.FAILED_NO_SUBMIT, rec.outcome());
            List<Map<String, Object>> tools = rec.events().stream().filter(e -> e.get("type").equals("tool")).toList();
            assertEquals(List.of("trade_stats", "submit_report", "read_card", "declare_intents", "declare_intents", "trade_stats"),
                    tools.stream().map(e -> e.get("name")).toList());
            assertTrue(((String) tools.get(0).get("result")).contains("먼저 declare_intents로 의도를 적어라."));
            assertEquals("REJECTED", tools.get(1).get("error_code"), "제출도 막는다");
            assertEquals(false, tools.get(2).get("is_error"), "카드 읽기는 막지 않는다");
            assertEquals("REJECTED", tools.get(3).get("error_code"));
            assertEquals(false, tools.get(4).get("is_error"));
            assertEquals(false, tools.get(5).get("is_error"));
            List<String> hooks = rec.events().stream().filter(e -> e.get("type").equals("hook")).map(e -> e.get("point") + " " + e.get("part") + " " + e.get("decision")).toList();
            assertEquals(List.of("START ⓪ REJECT", "START ⓪ REJECT", "START ⓪ PASS"), hooks);
            assertEquals(9, api.requests.get(0).get("tools").size(), "도구 여섯, 카드 둘, 의도 하나");
        }
    }
}
