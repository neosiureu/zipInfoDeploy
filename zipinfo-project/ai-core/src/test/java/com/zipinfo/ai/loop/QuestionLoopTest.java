package com.zipinfo.ai.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.data.Listing;
import com.zipinfo.ai.model.AnthropicModelClient;
import com.zipinfo.ai.model.FakeAnthropic;
import com.zipinfo.ai.model.ModelError;
import com.zipinfo.ai.model.ModelSettings;
import com.zipinfo.ai.tools.DataSet;
import com.zipinfo.ai.tools.Fixture;
import com.zipinfo.ai.tools.RunState;
import com.zipinfo.ai.tools.ToolSpecs;
import com.zipinfo.ai.tools.Tools;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 가짜 API 서버로 문항 하나를 끝까지, 멈춘 까닭 갈래, 오류 갈래, 요청 꼴을 본다(계획 3판 순서 3). */
class QuestionLoopTest {
    static final ObjectMapper M = new ObjectMapper();
    static DataSet data;
    FakeAnthropic api;

    @BeforeAll
    static void load(@TempDir Path dir) throws Exception {
        data = Fixture.data(dir);
    }

    @BeforeEach
    void up() throws Exception {
        api = new FakeAnthropic();
    }

    @AfterEach
    void down() {
        api.close();
    }

    QuestionLoop loop(int maxTurns, List<Hook> hooks) {
        return loop(maxTurns, hooks, api.url(), Duration.ofSeconds(20));
    }

    QuestionLoop loop(int maxTurns, List<Hook> hooks, String url, Duration timeout) {
        var client = new AnthropicModelClient("test-key", url, new ModelSettings("claude-haiku-5-5", 4000, ModelSettings.Thinking.ADAPTIVE, "high"),
                timeout);
        return new QuestionLoop(client, new Tools(data), data, new QuestionLoop.Config(maxTurns, hooks, Map.of()));
    }

    static RunState listing() {
        return Fixture.run("L01", "0", 350_000_000L, 0, "84");
    }

    static ObjectNode statsInput() {
        return M.createObjectNode().put("api", "apt_trade").put("region_code", "41591").put("start_month", "202610").put("end_month", "202610")
                .put("deal", "매매").put("dong", "").put("jibun", "");
    }

    static ObjectNode report() {
        ObjectNode r = M.createObjectNode().put("text", "이 매물은 주변보다 {가} {가.방향}.");
        r.putArray("stat_claims").addObject().put("slot", "나").put("value", 33741).put("unit", "만원").put("basis", "시군구")
                .put("start_month", "202610").put("end_month", "202610").put("sample_size", 4).put("provisional", true).put("evidence", "S1.기준");
        r.putArray("value_claims").addObject().put("slot", "가").put("value", 3.7).put("unit", "%").put("source", "계산").put("evidence", "C1.차이");
        r.putArray("context_claims");
        return r;
    }

    static void walk(JsonNode n, List<String> problems, String where) {
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
                walk(n.get("properties").get(f), problems, where + "." + f);
            }
        }
        if ("array".equals(n.path("type").asText())) {
            walk(n.get("items"), problems, where + "[]");
        }
    }

    @Test
    void oneQuestionEndToEndAndRequestShape() throws Exception {
        api.then(FakeAnthropic.sse(List.of(FakeAnthropic.thinking("통계부터 본다", "sig-1"), FakeAnthropic.toolUse("toolu_1", "trade_stats", statsInput())),
                "tool_use", null));
        api.then(FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_2", "compare_prices",
                M.createObjectNode().put("listing_evidence", "매물.가격").put("basis_evidence", "S1.기준"))), "tool_use", null));
        api.then(FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_3", "submit_report", report())), "tool_use", null));
        QuestionLoop q = loop(10, List.of());
        RunRecord rec = q.start(Prompts.SYSTEM, ToolSpecs.all(), "이 매물 가격이 주변보다 비싼가요?", listing());
        assertEquals(RunRecord.Outcome.SUBMITTED, rec.outcome());
        assertEquals("이 매물은 주변보다 3.7% 비싸다.", rec.answer());
        assertEquals(3, rec.turns());
        assertEquals(3, api.requests.size());
        for (JsonNode req : api.requests) {
            for (String banned : List.of("temperature", "top_p", "top_k")) {
                assertFalse(req.has(banned), banned);
            }
            assertEquals("auto", req.get("tool_choice").get("type").asText());
            assertEquals("adaptive", req.get("thinking").get("type").asText());
            assertEquals("high", req.get("output_config").get("effort").asText());
            assertEquals(Prompts.SYSTEM, req.get("system").asText());
            assertTrue(req.get("tools").size() <= 20);
            List<String> problems = new ArrayList<>();
            for (JsonNode t : req.get("tools")) {
                assertTrue(t.get("strict").asBoolean(), t.get("name").asText());
                walk(t.get("input_schema"), problems, t.get("name").asText());
            }
            assertEquals(List.of(), problems);
        }
        JsonNode second = api.requests.get(1).get("messages");
        assertEquals(3, second.size());
        JsonNode echoed = second.get(1).get("content").get(0);
        assertEquals("thinking", echoed.get("type").asText());
        assertEquals("통계부터 본다", echoed.get("thinking").asText());
        assertEquals("sig-1", echoed.get("signature").asText());
        JsonNode result = second.get(2).get("content").get(0);
        assertEquals("tool_result", result.get("type").asText());
        assertEquals("toolu_1", result.get("tool_use_id").asText());
        assertTrue(result.toString().contains("S1.기준"));
        assertTrue(api.requests.get(0).get("messages").get(0).toString().contains("[근거 매물.가격]"));
        assertTrue(rec.events().stream().anyMatch(e -> e.get("type").equals("submit") && ((List<?>) e.get("findings")).isEmpty()));
        // 기록(설계안 10.1): 토큰 넷, 응답 원문(thinking 포함), 도구 시간(01 213줄)
        Map<String, Object> first = rec.events().stream().filter(e -> e.get("type").equals("response")).findFirst().orElseThrow();
        assertEquals(List.of(100L, 80L, 20L, 42L), List.of(first.get("input_tokens"), first.get("cache_read_tokens"), first.get("cache_write_tokens"),
                first.get("output_tokens")));
        assertEquals("sig-1", ((JsonNode) first.get("raw")).get("content").get(0).get("signature").asText());
        assertEquals(2, rec.events().stream().filter(e -> e.get("type").equals("tool") && e.get("millis") instanceof Double).count());
        assertTrue(rec.events().stream().anyMatch(e -> e.get("type").equals("accepted") && e.get("millis") instanceof Double));
    }

    /** 01 209줄: 시스템 글·재촉·매물 블록에 사고 과정을 적게 하는 말이 없다. 카드가 가르치는 기본 기간·잠정도 없다(3a 도구 설명 시험과 같은 낱말). */
    @Test
    void promptsDoNotAskForReasoningOrTellDefaults() {
        Listing l = listing().listing();
        Map<String, String> f = new LinkedHashMap<>(l.fields());
        f.put("stockForm", "1");
        String first = Prompts.firstMessage("?", new Listing(l.key(), f, l.evidence()));
        for (String text : List.of(Prompts.SYSTEM, Prompts.NUDGE, first)) {
            for (String bad : List.of("생각", "사고 과정", "단계별", "추론", "12달", "잠정", "기본 기간")) {
                assertFalse(text.contains(bad), bad);
            }
        }
        assertTrue(first.contains("stockForm(주택 종류): 1 (아파트)") && first.contains("stockType(거래): 0 (매매)"), first);
    }

    @Test
    void endTurnGetsOneNudgeThenFails() throws Exception {
        api.then(FakeAnthropic.sse(List.of(FakeAnthropic.text("비싼 편입니다")), "end_turn", null));
        api.then(FakeAnthropic.sse(List.of(FakeAnthropic.text("네")), "end_turn", null));
        RunRecord rec = loop(10, List.of()).start(Prompts.SYSTEM, ToolSpecs.all(), "비싼가요?", listing());
        assertEquals(RunRecord.Outcome.FAILED_NO_SUBMIT, rec.outcome());
        JsonNode msgs = api.requests.get(1).get("messages");
        assertEquals(Prompts.NUDGE, msgs.get(msgs.size() - 1).get("content").get(0).get("text").asText());
        assertEquals(2, api.requests.size());
    }

    @Test
    void stopReasons() throws Exception {
        api.then(FakeAnthropic.sse(List.of(), "refusal", M.createObjectNode().put("type", "refusal").put("category", "reasoning_extraction")));
        assertEquals(RunRecord.Outcome.STOP_REASONING_EXTRACTION, loop(10, List.of()).start(Prompts.SYSTEM, ToolSpecs.all(), "?", listing()).outcome());
        api.then(FakeAnthropic.sse(List.of(), "refusal", M.createObjectNode().put("type", "refusal").put("category", "cyber")));
        assertEquals(RunRecord.Outcome.FAILED_REFUSAL, loop(10, List.of()).start(Prompts.SYSTEM, ToolSpecs.all(), "?", listing()).outcome());
        api.then(FakeAnthropic.sse(List.of(FakeAnthropic.text("…")), "max_tokens", null));
        assertEquals(RunRecord.Outcome.FAILED_MAX_TOKENS, loop(10, List.of()).start(Prompts.SYSTEM, ToolSpecs.all(), "?", listing()).outcome());
        api.then(FakeAnthropic.sse(List.of(FakeAnthropic.text("…")), "model_context_window_exceeded", null));
        assertEquals(RunRecord.Outcome.FAILED_CONTEXT_WINDOW, loop(10, List.of()).start(Prompts.SYSTEM, ToolSpecs.all(), "?", listing()).outcome());
        api.then(FakeAnthropic.sse(List.of(FakeAnthropic.text("…")), "pause_turn", null));
        assertEquals(RunRecord.Outcome.STOP_OTHER_STOP_REASON, loop(10, List.of()).start(Prompts.SYSTEM, ToolSpecs.all(), "?", listing()).outcome());
        api.then(FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("t1", "trade_stats", statsInput())), "tool_use", null));
        api.then(FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("t2", "trade_stats", statsInput())), "tool_use", null));
        assertEquals(RunRecord.Outcome.FAILED_TURN_LIMIT, loop(2, List.of()).start(Prompts.SYSTEM, ToolSpecs.all(), "?", listing()).outcome());
    }

    static ModelError.Category fail(FakeAnthropic api, QuestionLoopTest t, FakeAnthropic.Reply r) {
        return failure(api, t, r).category();
    }

    static ModelError failure(FakeAnthropic api, QuestionLoopTest t, FakeAnthropic.Reply r) {
        api.then(r);
        return assertThrows(ModelError.class, () -> t.loop(10, List.of()).start(Prompts.SYSTEM, ToolSpecs.all(), "?", listing()));
    }

    @Test
    void errorCategoriesFollow01() throws Exception {
        ModelError slow = failure(api, this, FakeAnthropic.error(429, "rate_limit_error", "slow down", null, Map.of("retry-after", "7")));
        assertEquals(List.of(ModelError.Category.RETRY, 7L), List.of(slow.category(), slow.retryAfterSeconds()));
        assertEquals(ModelError.Category.RESUME_LATER, fail(api, this, FakeAnthropic.error(429, "rate_limit_error", "limit", "enforced_spend_limit_reached")));
        assertEquals(ModelError.Category.RESUME_LATER, fail(api, this, FakeAnthropic.error(400, "invalid_request_error",
                "You have reached your specified workspace API usage limits.", null)));
        assertEquals(ModelError.Category.RESUME_LATER, fail(api, this, FakeAnthropic.error(400, "invalid_request_error",
                "Your credit balance is too low to access the Anthropic API.", null)));
        assertEquals(ModelError.Category.RESUME_LATER, fail(api, this, FakeAnthropic.error(402, "billing_error", "pay", null)));
        assertEquals(ModelError.Category.RETRY, fail(api, this, FakeAnthropic.error(500, "api_error", "oops", null)));
        assertEquals(ModelError.Category.RETRY, fail(api, this, FakeAnthropic.error(529, "overloaded_error", "busy", null)));
        assertEquals(ModelError.Category.STOP_AND_LOOK, fail(api, this, FakeAnthropic.error(400, "invalid_request_error", "tool_choice any is not supported", null)));
        assertEquals(ModelError.Category.RETRY, fail(api, this, FakeAnthropic.sseError("overloaded_error", "busy")));
        assertEquals(ModelError.Category.STOP_AND_LOOK, fail(api, this, FakeAnthropic.sseError("invalid_request_error", "bad")));
        // 상태 코드 없는 연결 오류·시간 초과(01 196줄): 오류 없이 닫혔지만 message_stop이 없는 스트림, 중간에 멈춘 스트림, 닿지 않는 주소
        FakeAnthropic.Reply ok = FakeAnthropic.sse(List.of(FakeAnthropic.text("…")), "end_turn", null);
        ModelError cut = failure(api, this, FakeAnthropic.cut(ok, false));
        assertEquals(List.of(ModelError.Category.RETRY, "incomplete_stream"), List.of(cut.category(), cut.type()));
        api.then(FakeAnthropic.cut(ok, true));
        ModelError stall = assertThrows(ModelError.class,
                () -> loop(10, List.of(), api.url(), Duration.ofSeconds(1)).start(Prompts.SYSTEM, ToolSpecs.all(), "?", listing()));
        assertEquals(List.of(ModelError.Category.RETRY, "connection"), List.of(stall.category(), stall.type()));
        int closed;
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closed = s.getLocalPort();
        }
        ModelError refused = assertThrows(ModelError.class,
                () -> loop(10, List.of(), "http://127.0.0.1:" + closed, Duration.ofSeconds(5)).start(Prompts.SYSTEM, ToolSpecs.all(), "?", listing()));
        assertEquals(List.of(ModelError.Category.RETRY, "connection"), List.of(refused.category(), refused.type()));
    }

    @Test
    void retryResendsTheSameRequestAndDoesNotCountATurn() throws Exception {
        api.then(FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_1", "trade_stats", statsInput())), "tool_use", null));
        api.then(FakeAnthropic.error(529, "overloaded_error", "busy", null));
        api.then(FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_2", "compare_prices",
                M.createObjectNode().put("listing_evidence", "매물.가격").put("basis_evidence", "S1.기준"))), "tool_use", null));
        api.then(FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_3", "submit_report", report())), "tool_use", null));
        QuestionLoop q = loop(3, List.of());
        ModelError e = assertThrows(ModelError.class, () -> q.start(Prompts.SYSTEM, ToolSpecs.all(), "?", listing()));
        assertEquals(ModelError.Category.RETRY, e.category());
        RunRecord rec = q.resume();
        assertEquals(RunRecord.Outcome.SUBMITTED, rec.outcome());
        assertEquals(3, rec.turns());
        assertEquals(api.requests.get(1), api.requests.get(2));
    }

    @Test
    void hookRejectionIsAToolErrorAndIsRecorded() throws Exception {
        Hook deny = new Hook() {
            public String name() { return "시험 부품"; }
            public boolean at(Point p) { return p == Point.BEFORE_TOOL; }
            public Decision decide(Point p, Context c) {
                return c.input().get("region_code").asText().equals("41590") ? Decision.reject("이 매물의 지역 코드는 41591이다") : Decision.PASS;
            }
        };
        ObjectNode old = statsInput().put("region_code", "41590");
        api.then(FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_1", "trade_stats", old)), "tool_use", null));
        api.then(FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_2", "trade_stats", statsInput())), "tool_use", null));
        api.then(FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_3", "compare_prices",
                M.createObjectNode().put("listing_evidence", "매물.가격").put("basis_evidence", "S1.기준"))), "tool_use", null));
        api.then(FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_4", "submit_report", report())), "tool_use", null));
        RunRecord rec = loop(10, List.of(deny)).start(Prompts.SYSTEM, ToolSpecs.all(), "?", listing());
        assertEquals(RunRecord.Outcome.SUBMITTED, rec.outcome());
        JsonNode r1 = api.requests.get(1).get("messages").get(2).get("content").get(0);
        assertTrue(r1.get("is_error").asBoolean());
        assertTrue(r1.toString().contains("이 매물의 지역 코드는 41591이다"));
        assertTrue(rec.events().stream().anyMatch(e -> e.get("type").equals("hook") && "REJECT".equals(e.get("decision"))));
    }
}
