package com.zipinfo.ai.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.loop.Hook;
import com.zipinfo.ai.loop.Prompts;
import com.zipinfo.ai.loop.QuestionLoop;
import com.zipinfo.ai.loop.RunRecord;
import com.zipinfo.ai.model.AnthropicModelClient;
import com.zipinfo.ai.model.FakeAnthropic;
import com.zipinfo.ai.model.ModelSettings;
import com.zipinfo.ai.tools.DataSet;
import com.zipinfo.ai.tools.Fixture;
import com.zipinfo.ai.tools.RunState;
import com.zipinfo.ai.tools.ToolResult;
import com.zipinfo.ai.tools.ToolSpecs;
import com.zipinfo.ai.tools.Tools;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 카드 꼴 읽기, 찾기 규칙(search_cards와 ⓪이 같이 씀), 카드 글, 카드 도구 둘, 고리에 붙였을 때(순서 4). */
class CardsTest {
    static final ObjectMapper M = new ObjectMapper();

    static String card(String id, String title, String en, String ko, String core, String log) {
        return "# " + id + " " + title + "\n\n- 영어 태그: " + en + "\n- 한글 낱말: " + ko + "\n\n## 핵심\n\n" + core
                + "\n\n## 사용법\n\n1. 한다.\n\n## 기록\n\n" + log + "\n";
    }

    static Cards sample() {
        return new Cards(List.of(
                Cards.parse(card("K02", "지역 코드", "region, code", "지역 코드, 주소", "- 코드는 주소에서 얻는다.", "")),
                Cards.parse(card("K01", "차례", "order, steps", "차례", "- 주소부터 한다.\n- 그다음 통계다.", "")),
                Cards.parse(card("K08", "거래 건수", "count, number", "몇 건, 건수", "- 건수는 표본 수다.", "- 연습에서 더한 줄"))));
    }

    @Test
    void parsesTheCardForm() {
        Card c = Cards.parse(card("K01", "차례", "order, steps", "차례, 무엇부터", "- 한 줄\n- 두 줄", "").replace("\n", "\r\n"));
        assertEquals("K01", c.id());
        assertEquals("차례", c.title());
        assertEquals(List.of("order", "steps"), c.englishTags());
        assertEquals(List.of("차례", "무엇부터"), c.koreanWords());
        assertEquals("- 한 줄\n- 두 줄", c.core());
        assertEquals("1. 한다.", c.usage());
        assertEquals("", c.log());
    }

    @Test
    void rejectsBrokenCards() {
        String ok = card("K01", "차례", "order", "차례", "- 한 줄", "");
        for (String bad : List.of(
                ok.replace("# K01 차례", "# 차례"),
                ok.replace("- 영어 태그: order\n", ""),
                ok.replace("order", "Order"),
                ok.replace("- 한 줄", "- 하나\n- 둘\n- 셋\n- 넷"),
                ok.replace("- 한 줄", "한 줄"),
                ok.replace("## 사용법\n\n1. 한다.", "## 사용법\n"),
                ok.replace("## 기록", "## 메모"),
                ok.replace("\n- 한글 낱말: 차례\n", "\n- 한글 낱말: 차례\n덧붙인 줄\n"))) {
            assertThrows(IllegalArgumentException.class, () -> Cards.parse(bad), bad);
        }
    }

    @Test
    void loadsFilesInIdOrderAndChecksNames(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("K02.md"), card("K02", "둘", "two", "둘", "- 둘", ""));
        Files.writeString(dir.resolve("K01.md"), card("K01", "하나", "one", "하나", "- 하나", ""));
        Files.writeString(dir.resolve("README.md"), "카드가 아니다");
        assertEquals(List.of("K01", "K02"), Cards.load(dir).all().stream().map(Card::id).toList());
        Files.writeString(dir.resolve("K03.md"), card("K04", "넷", "four", "넷", "- 넷", ""));
        assertThrows(IOException.class, () -> Cards.load(dir));
    }

    @Test
    void findRules() {
        Cards cs = sample();
        // 영어: 낱말이 태그와 같아야 한다(대소문자 무시, 낱말의 일부는 아님)
        assertEquals(List.of("K02"), ids(cs.find("What is the REGION code?", "")));
        assertEquals(List.of(), ids(cs.find("regional coding", "")));
        // 한글: 카드 낱말이 글 안에 있으면(빈칸 무시)
        assertEquals(List.of("K08"), ids(cs.find("", "이 단지 거래가 몇건이야")));
        assertEquals(List.of("K02"), ids(cs.find("", "지역코드를 알려 줘")));
        // 둘 다, 카드 ID 순
        assertEquals(List.of("K01", "K02", "K08"), ids(cs.search("order 주소 건수")));
        assertEquals(List.of(), ids(cs.search("월세")));
    }

    static List<String> ids(List<Card> cs) {
        return cs.stream().map(Card::id).toList();
    }

    @Test
    void renderDropsTagsAndEmptyLog() {
        Cards cs = sample();
        String k01 = Cards.render(cs.get("K01"));
        assertEquals("# K01 차례\n\n## 핵심\n\n- 주소부터 한다.\n- 그다음 통계다.\n\n## 사용법\n\n1. 한다.", k01);
        assertTrue(Cards.render(cs.get("K08")).endsWith("## 기록\n\n- 연습에서 더한 줄"));
        assertFalse(cs.fullText().contains("영어 태그"));
        assertEquals("[카드]\n\n" + k01 + "\n\n" + Cards.render(cs.get("K02")) + "\n\n" + Cards.render(cs.get("K08")), cs.fullText());
    }

    @Test
    void cardToolsAndTheirErrors(@TempDir Path dir) throws Exception {
        Cards cs = sample();
        RunState st = Fixture.run("L01", "0", 350_000_000L, 0, "84");
        var h = CardTools.handlers(cs);
        ToolResult found = h.get("search_cards").apply(M.createObjectNode().put("query", "region 건수"), st);
        assertFalse(found.isError());
        JsonNode f = M.readTree(found.text());
        assertEquals(2, f.get("결과수").asInt());
        assertEquals("K02", f.get("카드").get(0).get("카드").asText());
        assertEquals("지역 코드", f.get("카드").get(0).get("제목").asText());
        ToolResult none = h.get("search_cards").apply(M.createObjectNode().put("query", "월세"), st);
        assertFalse(none.isError());
        assertEquals(0, M.readTree(none.text()).get("결과수").asInt());
        ToolResult blank = h.get("search_cards").apply(M.createObjectNode().put("query", " "), st);
        assertEquals(List.of(true, "BAD_INPUT"), List.of(blank.isError(), blank.errorCode()));
        ToolResult unknown = h.get("read_card").apply(M.createObjectNode().put("card_id", "K09"), st);
        assertEquals(List.of(true, "UNKNOWN_CARD"), List.of(unknown.isError(), unknown.errorCode()));
        assertTrue(st.cardsRead().isEmpty());
        ToolResult read = h.get("read_card").apply(M.createObjectNode().put("card_id", "K02"), st);
        assertEquals(Cards.render(cs.get("K02")), M.readTree(read.text()).get("글").asText());
        assertEquals(List.of("K02"), List.copyOf(st.cardsRead()));
        // 카드는 근거가 아니다(설계안 6.1): 근거 ID를 만들지 않는다
        assertEquals(4, st.evidence().size());
    }

    @Test
    void cardToolSchemasAreStrictAndPlain() {
        assertEquals(List.of("search_cards", "read_card"), CardTools.SPECS.stream().map(ToolSpecs.Spec::name).toList());
        for (ToolSpecs.Spec s : CardTools.SPECS) {
            JsonNode in = s.inputSchema();
            List<String> props = new ArrayList<>();
            in.get("properties").fieldNames().forEachRemaining(props::add);
            List<String> req = new ArrayList<>();
            in.get("required").forEach(x -> req.add(x.asText()));
            assertEquals(props, req, s.name());
            assertFalse(in.get("additionalProperties").asBoolean(true), s.name());
            for (String bad : List.of("생각", "사고 과정", "단계별", "추론", "12달", "잠정", "기본 기간")) {
                assertFalse(s.description().contains(bad), s.name() + ": " + bad);
            }
        }
    }

    /** 고리에 Config.extra로 붙인다. 시작 자리가 모두 거절해도 카드 도구는 돈다(설계안 7.2 "카드 찾기·읽기는 막지 않는다"). */
    @Test
    void cardToolsRunInTheLoopWithoutHooks(@TempDir Path dir) throws Exception {
        DataSet data = Fixture.data(dir);
        Hook denyAll = new Hook() {
            public String name() { return "모두 거절"; }
            public boolean at(Point p) { return p == Point.START; }
            public Decision decide(Point p, Context c) { return Decision.reject("먼저 다른 것을 하라"); }
        };
        try (FakeAnthropic api = new FakeAnthropic()) {
            ObjectNode q = M.createObjectNode().put("query", "지역 코드");
            ObjectNode r = M.createObjectNode().put("card_id", "K02");
            api.then(FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("t1", "search_cards", q), FakeAnthropic.toolUse("t2", "read_card", r)), "tool_use", null));
            api.then(FakeAnthropic.sse(List.of(FakeAnthropic.text("끝")), "end_turn", null));
            api.then(FakeAnthropic.sse(List.of(FakeAnthropic.text("끝")), "end_turn", null));
            var client = new AnthropicModelClient("test-key", api.url(), new ModelSettings("claude-haiku-5-5", 4000, ModelSettings.Thinking.ADAPTIVE, "high"),
                    Duration.ofSeconds(20));
            Cards cs = sample();
            QuestionLoop loop = new QuestionLoop(client, new Tools(data), data, new QuestionLoop.Config(10, List.of(denyAll), CardTools.handlers(cs)));
            List<ToolSpecs.Spec> specs = new ArrayList<>(ToolSpecs.all());
            specs.addAll(CardTools.SPECS);
            RunState st = Fixture.run("L01", "0", 350_000_000L, 0, "84");
            RunRecord rec = loop.start(Prompts.SYSTEM, specs, "?", st);
            assertEquals(RunRecord.Outcome.FAILED_NO_SUBMIT, rec.outcome());
            JsonNode results = api.requests.get(1).get("messages").get(2).get("content");
            assertFalse(results.get(0).has("is_error") && results.get(0).get("is_error").asBoolean());
            assertTrue(results.get(1).toString().contains("코드는 주소에서 얻는다"));
            assertEquals(List.of("K02"), List.copyOf(st.cardsRead()));
            assertTrue(rec.events().stream().noneMatch(e -> e.get("type").equals("hook")));
            assertEquals(8, api.requests.get(0).get("tools").size());
        }
    }
}
