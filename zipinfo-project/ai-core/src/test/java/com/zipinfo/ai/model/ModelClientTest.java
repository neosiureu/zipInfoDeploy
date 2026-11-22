package com.zipinfo.ai.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.tools.ToolSpecs;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 순서 6a: 캐시 지점이 요청 꼴에 놓이는 자리, 기록에서 대화를 되살리는 것(다른 프로세스에서 이어 돌리기). */
class ModelClientTest {
    static final ObjectMapper M = new ObjectMapper();
    static final ModelSettings HAIKU = new ModelSettings("claude-haiku-5-5", 4000, ModelSettings.Thinking.ADAPTIVE, "high");
    FakeAnthropic api;

    @BeforeEach
    void up() throws Exception {
        api = new FakeAnthropic();
    }

    @AfterEach
    void down() {
        api.close();
    }

    AnthropicModelClient client(CachePolicy c) {
        return new AnthropicModelClient("test-key", api.url(), HAIKU, Duration.ofSeconds(20), c);
    }

    static JsonNode ephemeral(String ttl) throws Exception {
        return M.readTree("{\"type\":\"ephemeral\",\"ttl\":\"" + ttl + "\"}");
    }

    @Test
    void cacheControlGoesOnlyWhereThePolicySays() throws Exception {
        List<CachePolicy> policies = List.of(CachePolicy.NONE, new CachePolicy(true, false, "5m"), new CachePolicy(false, true, "1h"),
                new CachePolicy(true, true, "1h"));
        for (CachePolicy c : policies) {
            api.then(FakeAnthropic.sse(List.of(FakeAnthropic.text("네")), "end_turn", null));
            client(c).open("시스템 글", ToolSpecs.all()).send(List.of(new UserPart.Text("질문")));
        }
        JsonNode none = api.requests.get(0);
        JsonNode sys = api.requests.get(1);
        JsonNode conv = api.requests.get(2);
        JsonNode both = api.requests.get(3);
        // 끈 판: 순서 3b의 꼴 그대로(시스템 글은 글 하나, cache_control은 어디에도 없음)
        assertFalse(none.toString().contains("cache_control"));
        assertEquals("시스템 글", none.get("system").asText());
        // 시스템 글 지점: 시스템 글이 블록 하나가 되고 그 끝에 지점
        assertEquals(1, sys.get("system").size());
        assertEquals("시스템 글", sys.get("system").get(0).get("text").asText());
        assertEquals(ephemeral("5m"), sys.get("system").get(0).get("cache_control"));
        assertFalse(sys.has("cache_control"));
        // 대화 지점: 요청 맨 위의 cache_control(자동 캐시)
        assertEquals("시스템 글", conv.get("system").asText());
        assertEquals(ephemeral("1h"), conv.get("cache_control"));
        // 둘 다
        assertEquals(ephemeral("1h"), both.get("system").get(0).get("cache_control"));
        assertEquals(ephemeral("1h"), both.get("cache_control"));
        // 도구 정의와 메시지에는 지점을 두지 않는다. 캐시 말고는 요청이 같다.
        for (JsonNode r : api.requests) {
            assertFalse(r.get("tools").toString().contains("cache_control"));
            assertFalse(r.get("messages").toString().contains("cache_control"));
            assertEquals(none.get("tools"), r.get("tools"));
            assertEquals(none.get("messages"), r.get("messages"));
        }
    }

    @Test
    void replayRebuildsTheConversationSoTheNextRequestIsTheStoppedOne() throws Exception {
        ObjectNode usage = M.createObjectNode().put("input_tokens", 1200).put("cache_read_input_tokens", 3000).put("cache_creation_input_tokens", 700)
                .put("output_tokens", 1);
        usage.putObject("cache_creation").put("ephemeral_5m_input_tokens", 500).put("ephemeral_1h_input_tokens", 200);
        usage.put("service_tier", "standard");
        ObjectNode in1 = M.createObjectNode().put("api", "apt_trade").put("region_code", "41591");
        ObjectNode in2 = M.createObjectNode().put("listing_evidence", "매물.가격").put("basis_evidence", "S1.기준");
        List<FakeAnthropic.Reply> replies = List.of(
                FakeAnthropic.sse(List.of(FakeAnthropic.thinking("통계부터 본다", "sig-1"), FakeAnthropic.toolUse("toolu_1", "trade_stats", in1)),
                        "tool_use", null, usage),
                FakeAnthropic.sse(List.of(FakeAnthropic.text("중간 글"), FakeAnthropic.toolUse("toolu_2", "compare_prices", in2)), "tool_use", null),
                FakeAnthropic.sse(List.of(FakeAnthropic.text("끝")), "end_turn", null));
        List<List<UserPart>> sent = List.of(List.of(new UserPart.Text("질문")), List.of(new UserPart.ToolResult("toolu_1", "결과 하나", false)),
                List.of(new UserPart.ToolResult("toolu_2", "결과 둘", true)));
        CachePolicy c = new CachePolicy(true, true, "5m");
        // 끊김 없는 실행
        replies.forEach(api::then);
        ModelSession live = client(c).open("시스템 글", ToolSpecs.all());
        List<ModelTurn> turns = new ArrayList<>();
        for (List<UserPart> parts : sent) {
            turns.add(live.send(parts));
        }
        assertEquals(List.of(500L, 200L), List.of(turns.get(0).raw().get("usage").get("cache_creation").get("ephemeral_5m_input_tokens").asLong(),
                turns.get(0).raw().get("usage").get("cache_creation").get("ephemeral_1h_input_tokens").asLong()));
        // 다른 프로세스: 앞 응답 둘은 기록(파일에 쓰고 다시 읽은 꼴)에서 되살리고, 셋째 요청만 API로 보낸다
        api.then(replies.get(2));
        ModelSession again = client(c).open("시스템 글", ToolSpecs.all());
        for (int i = 0; i < 2; i++) {
            JsonNode raw = M.readTree(M.writeValueAsString(turns.get(i).raw()));
            ModelTurn t = again.replay(sent.get(i), raw, turns.get(i).millis());
            assertEquals(turns.get(i), t);
        }
        assertEquals(turns.get(2).raw(), again.send(sent.get(2)).raw());
        assertEquals(4, api.requests.size());
        assertEquals(api.requests.get(2), api.requests.get(3));
        assertTrue(api.requests.get(3).get("messages").get(1).toString().contains("sig-1"));
    }
}
