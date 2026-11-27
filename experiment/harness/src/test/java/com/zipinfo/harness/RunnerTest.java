package com.zipinfo.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.loop.Prompts;
import com.zipinfo.ai.model.AnthropicModelClient;
import com.zipinfo.ai.model.ModelClient;
import com.zipinfo.ai.model.ModelSession;
import com.zipinfo.ai.model.ModelTurn;
import com.zipinfo.ai.model.UserPart;
import com.zipinfo.ai.tools.ToolSpecs;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 순서 6a: 하네스 돌리기를 가짜 API 서버로 본다(01 하네스 규칙 190~213줄, 설계안 10.1).
 * 끝까지 돌기와 기록 꼴, 재시도와 기다림, 재시도를 다 쓴 뒤, 한도에서 멈추고 다른 프로세스에서 멈춘 요청부터 잇기, 지문이 다르면 잇지 않기,
 * 비용 상한, reasoning_extraction, 예외, 덜 쓴 줄, 기록이 어긋난 것, 동시 실행.
 */
class RunnerTest {
    static final BigDecimal ONE_REPLY = new BigDecimal("0.0000343"); // 100×0.10 + 80×0.01 + 20×0.125 + 42×0.50, 100만 토큰당
    FakeAnthropic api;
    final List<Long> waits = new ArrayList<>();
    final ByteArrayOutputStream logged = new ByteArrayOutputStream();
    @TempDir
    Path tmp;

    @BeforeEach
    void up() throws Exception {
        api = new FakeAnthropic();
    }

    @AfterEach
    void down() {
        api.close();
    }

    Runner runner(Inputs in, RunSettings s) {
        return runner(in, s, (ms, cache, timeout) -> new AnthropicModelClient("test-key", api.url(), ms, timeout, cache));
    }

    Runner runner(Inputs in, RunSettings s, Runner.Clients clients) {
        return new Runner(in, s, tmp.resolve("runs"), clients, waits::add, new PrintStream(logged, true, StandardCharsets.UTF_8));
    }

    Inputs inputs() throws Exception {
        return Fixture.inputs(Files.createTempDirectory(tmp, "data"));
    }

    static RunSettings settings(Consumer<ObjectNode> edit) throws Exception {
        return Fixture.settings(edit);
    }

    RunFile file(String id) throws Exception {
        return RunFile.read(tmp.resolve("runs").resolve(id + ".jsonl"), false);
    }

    static List<String> types(RunFile f) {
        return f.events().stream().map(e -> e.path("type").asText()).toList();
    }

    void solveOnce() {
        Fixture.solve().forEach(api::then);
    }

    @Test
    void runsToTheEndAndWritesTheRecord() throws Exception {
        Inputs in = inputs();
        RunSettings s = settings(n -> {
            Fixture.variants(n, "c01", "c03");
            Fixture.questions(n, "q001");
        });
        solveOnce();
        solveOnce();
        Runner.Result res = runner(in, s).run();
        assertNull(res.stop());
        assertEquals(List.of(2, 0), List.of(res.done(), res.remaining()));
        assertEquals(0, ONE_REPLY.multiply(BigDecimal.valueOf(6)).compareTo(res.spent()), res.spent().toPlainString());
        for (String id : List.of("c01-q001-r1", "c03-q001-r1")) {
            RunFile f = file(id);
            JsonNode h = f.header();
            assertEquals(id, h.get("run_id").asText());
            assertEquals(64, h.get("fingerprint").asText().length());
            assertTrue(h.get("system").asText().startsWith(Prompts.SYSTEM));
            assertEquals(16000, h.get("model_settings").get("max_tokens").asInt());
            assertTrue(h.get("cache").get("system").asBoolean() && h.get("cache").get("conversation").asBoolean());
            assertEquals("2026-11-22", h.get("prices").get("date").asText());
            assertEquals("연습", h.get("question").get("split").asText());
            assertEquals(List.of("first_message", "response", "tool", "response", "tool", "response", "submit", "accepted", "end"), types(f));
            assertTrue(f.events().stream().allMatch(e -> e.has("at")));
            assertEquals("SUBMITTED", f.end().get("outcome").asText());
            assertEquals(3, f.end().get("turns").asInt());
            assertEquals("이 매물은 주변보다 3.7% 비쌉니다.", f.end().get("answer").asText());
            assertEquals("sig-1", f.responses().get(0).raw().get("content").get(0).get("signature").asText());
        }
        assertEquals(ToolSpecs.all().size(), file("c01-q001-r1").header().get("tools").size());
        assertTrue(file("c03-q001-r1").header().get("tools").size() > ToolSpecs.all().size());
        // 요청: 캐시 지점 둘(설정), 본시험 문항 글은 어디에도 없다
        assertEquals(6, api.requests.size());
        for (JsonNode r : api.requests) {
            assertEquals("5m", r.get("cache_control").get("ttl").asText());
            assertEquals("5m", r.get("system").get(0).get("cache_control").get("ttl").asText());
            assertFalse(r.toString().contains(Fixture.Q9.text()));
        }
        // 다시 돌려도 할 일이 없다
        Runner.Result again = runner(in, s).run();
        assertEquals(List.of(2, 0), List.of(again.done(), again.remaining()));
        assertEquals(6, api.requests.size());
        // 모은 숫자
        List<Summary.Row> rows = Summary.rows(runner(in, s).status(), s, in.variants(), tmp.resolve("runs"));
        Summary.Row c01 = rows.get(0);
        assertEquals(List.of(1, 1, 1, 3L, 200L, 0L), List.of(c01.planned, c01.done, c01.outcomes.get("SUBMITTED"), c01.requests, c01.maxPrompt, c01.over));
        assertEquals(List.of(300L, 240L, 60L, 126L), List.of(c01.input, c01.cacheRead, c01.cacheWrite, c01.output));
        assertEquals(0, new BigDecimal("0.0001029").compareTo(c01.withCache));
        assertEquals(0, new BigDecimal("0.000123").compareTo(c01.withoutCache)); // 3 × (200×0.10 + 42×0.50)
        assertEquals(Map.of(), c01.toolErrors);
        Summary.Row total = rows.get(rows.size() - 1);
        assertEquals(List.of("합", 2, 6L), List.of(total.variant, total.done, total.requests));
        String tsv = Summary.format(rows);
        assertTrue(tsv.startsWith("판\t이름\t계획\t끝\t남음\tSUBMITTED"), tsv);
        assertFalse(tsv.contains(Fixture.Q1.text()));
    }

    @Test
    void retryWaitsAndResendsTheSameRequestWithoutCountingATurn() throws Exception {
        Inputs in = inputs();
        RunSettings s = settings(n -> {
            Fixture.variants(n, "c01");
            Fixture.questions(n, "q001");
        });
        List<FakeAnthropic.Reply> r = Fixture.solve();
        api.then(r.get(0));
        api.then(FakeAnthropic.error(529, "overloaded_error", "busy", null));
        api.then(FakeAnthropic.error(429, "rate_limit_error", "slow down", null, Map.of("retry-after", "7")));
        api.then(r.get(1));
        api.then(r.get(2));
        Runner.Result res = runner(in, s).run();
        assertNull(res.stop());
        assertEquals(List.of(2000L, 7000L), waits);
        assertEquals(5, api.requests.size());
        assertEquals(api.requests.get(1), api.requests.get(2));
        assertEquals(api.requests.get(1), api.requests.get(3));
        RunFile f = file("c01-q001-r1");
        assertEquals(List.of("first_message", "response", "tool", "attempt", "attempt", "response", "tool", "response", "submit", "accepted", "end"),
                types(f));
        List<JsonNode> attempts = f.events().stream().filter(e -> e.path("type").asText().equals("attempt")).toList();
        assertEquals(List.of(529, 429), attempts.stream().map(e -> e.get("status").asInt()).toList());
        assertEquals(List.of(2.0, 7.0), attempts.stream().map(e -> e.get("wait_seconds").asDouble()).toList());
        assertEquals(List.of(2, 2), attempts.stream().map(e -> e.get("turn").asInt()).toList());
        assertEquals(3, f.end().get("turns").asInt());
    }

    @Test
    void exhaustedRetriesPauseAndStopWhenTheSameErrorComesBackLater() throws Exception {
        Inputs in = inputs();
        RunSettings s = settings(n -> {
            Fixture.variants(n, "c01");
            Fixture.questions(n, "q001");
            ((ObjectNode) n.get("retry")).put("max_attempts", 2);
        });
        List<FakeAnthropic.Reply> r = Fixture.solve();
        api.then(r.get(0));
        for (int i = 0; i < 4; i++) {
            api.then(FakeAnthropic.error(529, "overloaded_error", "busy", null));
        }
        Runner.Result res = runner(in, s).run();
        assertEquals(Control.Kind.STOP_AND_LOOK, res.stop().kind());
        assertTrue(res.stop().message().contains("또 다 썼다"), res.stop().message());
        assertEquals(5, api.requests.size());
        for (int i = 2; i < 5; i++) {
            assertEquals(api.requests.get(1), api.requests.get(i));
        }
        assertEquals(List.of(2000L, 2000L), waits);
        RunFile f = file("c01-q001-r1");
        assertEquals(2, f.exhaustedSinceLastResponse().size());
        assertFalse(f.complete());
        assertTrue(types(f).contains("resume"));
        // 사람이 보고 다시 돌린다: 멈춘 요청부터 잇는다
        api.then(r.get(1));
        api.then(r.get(2));
        Runner.Result next = runner(in, s).run();
        assertNull(next.stop());
        assertEquals(7, api.requests.size());
        assertEquals(api.requests.get(1), api.requests.get(5));
        assertEquals("SUBMITTED", file("c01-q001-r1").end().get("outcome").asText());
    }

    @Test
    void differentErrorsPauseAgainButAThirdExhaustionStops() throws Exception {
        Inputs in = inputs();
        RunSettings s = settings(n -> {
            Fixture.variants(n, "c01");
            Fixture.questions(n, "q001");
            ((ObjectNode) n.get("retry")).put("max_attempts", 1);
        });
        api.then(Fixture.solve().get(0));
        api.then(FakeAnthropic.error(529, "overloaded_error", "busy", null));
        api.then(FakeAnthropic.error(500, "api_error", "oops", null));
        api.then(FakeAnthropic.error(529, "overloaded_error", "busy", null));
        Runner.Result res = runner(in, s).run();
        assertEquals(Control.Kind.STOP_AND_LOOK, res.stop().kind());
        assertEquals(4, api.requests.size());
        assertEquals(3, file("c01-q001-r1").exhaustedSinceLastResponse().size());
        assertEquals(List.of(), waits);
    }

    @Test
    void spendLimitStopsAndTheNextProcessSendsTheStoppedRequestAgain() throws Exception {
        Inputs in = inputs();
        RunSettings s = settings(n -> {
            Fixture.variants(n, "c01", "c03");
            Fixture.questions(n, "q001");
        });
        List<FakeAnthropic.Reply> r = Fixture.solve();
        api.then(r.get(0));
        api.then(FakeAnthropic.error(429, "rate_limit_error", "limit", "enforced_spend_limit_reached"));
        Runner.Result res = runner(in, s).run();
        assertEquals(Control.Kind.RESUME_LATER, res.stop().kind());
        assertEquals(List.of(0, 2), List.of(res.done(), res.remaining()));
        assertFalse(Files.exists(tmp.resolve("runs/c03-q001-r1.jsonl")));
        assertEquals(List.of("first_message", "response", "tool", "attempt", "pause"), types(file("c01-q001-r1")));
        assertEquals(2, api.requests.size());
        // 다른 프로세스(새 Runner): 응답 하나는 기록에서 되살리고, 멈춘 요청을 그대로 다시 보낸다
        api.then(r.get(1));
        api.then(r.get(2));
        solveOnce();
        Runner.Result next = runner(in, s).run();
        assertNull(next.stop());
        assertEquals(List.of(2, 0), List.of(next.done(), next.remaining()));
        assertEquals(7, api.requests.size());
        assertEquals(api.requests.get(1), api.requests.get(2));
        RunFile f = file("c01-q001-r1");
        assertEquals(List.of("first_message", "response", "tool", "attempt", "pause", "resume", "response", "tool", "response", "submit", "accepted",
                "end"), types(f));
        assertEquals(1, f.events().get(5).get("replay_responses").asInt());
        assertEquals(file("c03-q001-r1").end().get("answer"), f.end().get("answer"));
        assertEquals(3, f.end().get("turns").asInt());
    }

    @Test
    void recordsWithAnotherRequestAreNotResumed() throws Exception {
        Inputs in = inputs();
        RunSettings s = settings(n -> {
            Fixture.variants(n, "c01", "c03");
            Fixture.questions(n, "q001");
        });
        solveOnce();
        solveOnce();
        assertNull(runner(in, s).run().stop());
        // 턴 상한을 바꾸면 두 판 모두 다르다
        RunSettings turns = settings(n -> {
            Fixture.variants(n, "c01", "c03");
            Fixture.questions(n, "q001");
            n.put("max_turns", 30);
        });
        Runner.Result res = runner(in, turns).run();
        assertEquals(Control.Kind.STOP_AND_LOOK, res.stop().kind());
        Runner.Status st = runner(in, turns).status();
        assertEquals(List.of("c01 [max_turns]", "c03 [max_turns]"), st.stale().stream().map(x -> x.variant() + " " + x.changed()).toList());
        // 코드가 바뀌어도
        Inputs code = Fixture.inputs(Files.createTempDirectory(tmp, "d2"), Fixture.digests("CODE2", "CARDS"), List.of(Fixture.Q1, Fixture.Q2));
        assertEquals(List.of("[code]", "[code]"), runner(code, s).status().stale().stream().map(x -> x.changed().toString()).toList());
        // 의존성 판(SDK 등)이 바뀌어도 두 판 모두
        Inputs deps = Fixture.inputs(Files.createTempDirectory(tmp, "d5"), Fixture.digests("CODE", "CARDS", "DEPS2"), List.of(Fixture.Q1, Fixture.Q2));
        assertEquals(List.of("[deps]", "[deps]"), runner(deps, s).status().stale().stream().map(x -> x.changed().toString()).toList());
        // 카드가 바뀌면 카드를 쓰는 판(c03)만
        Inputs cards = Fixture.inputs(Files.createTempDirectory(tmp, "d3"), Fixture.digests("CODE", "CARDS2"), List.of(Fixture.Q1, Fixture.Q2));
        assertEquals(List.of("c03 [cards]"), runner(cards, s).status().stale().stream().map(x -> x.variant() + " " + x.changed()).toList());
        // 질문 글이 바뀌면
        Inputs text = Fixture.inputs(Files.createTempDirectory(tmp, "d4"), Fixture.digests("CODE", "CARDS"),
                List.of(new Question("q001", "L01", "T1", "연습", "시험 글 가 고침"), Fixture.Q2));
        assertEquals(List.of("[question]", "[question]"), runner(text, s).status().stale().stream().map(x -> x.changed().toString()).toList());
        assertEquals(6, api.requests.size());
        // 그대로면 문제없다
        assertNull(runner(in, s).run().stop());
        assertTrue(logged.toString(StandardCharsets.UTF_8).contains("요청 지문이 다른 기록: c01-q001-r1.jsonl (판 c01, 달라진 칸 max_turns)"));
    }

    @Test
    void costCapStopsBeforeTheNextRequestAndRaisingItResumes() throws Exception {
        Inputs in = inputs();
        RunSettings low = settings(n -> {
            Fixture.variants(n, "c01");
            Fixture.questions(n, "q001");
            n.put("cost_cap_usd", new BigDecimal("0.00005"));
        });
        solveOnce();
        Runner.Result res = runner(in, low).run();
        assertEquals(Control.Kind.COST_CAP, res.stop().kind());
        assertEquals(2, api.requests.size());
        RunFile f = file("c01-q001-r1");
        JsonNode pause = f.events().get(f.events().size() - 1);
        assertEquals(List.of("pause", "stopped", "COST_CAP"), List.of(pause.get("type").asText(), pause.get("reason").asText(), pause.get("note").asText()));
        // 상한 그대로면 파일을 건드리지 않고 멈춘다
        long size = Files.size(tmp.resolve("runs/c01-q001-r1.jsonl"));
        assertEquals(Control.Kind.COST_CAP, runner(in, low).run().stop().kind());
        assertEquals(size, Files.size(tmp.resolve("runs/c01-q001-r1.jsonl")));
        // 올리면 잇는다
        RunSettings high = settings(n -> {
            Fixture.variants(n, "c01");
            Fixture.questions(n, "q001");
            n.put("cost_cap_usd", 1);
        });
        Runner.Result next = runner(in, high).run();
        assertNull(next.stop());
        assertEquals(3, api.requests.size());
        assertEquals(0, ONE_REPLY.multiply(BigDecimal.valueOf(3)).compareTo(next.spent()));
    }

    @Test
    void reasoningExtractionEndsTheRunAndStopsEverything() throws Exception {
        Inputs in = inputs();
        RunSettings s = settings(n -> {
            Fixture.variants(n, "c01", "c03");
            Fixture.questions(n, "q001");
        });
        api.then(FakeAnthropic.sse(List.of(), "refusal", Fixture.M.createObjectNode().put("type", "refusal").put("category", "reasoning_extraction")));
        Runner.Result res = runner(in, s).run();
        assertEquals(Control.Kind.STOP_AND_LOOK, res.stop().kind());
        assertEquals("STOP_REASONING_EXTRACTION", file("c01-q001-r1").end().get("outcome").asText());
        assertFalse(Files.exists(tmp.resolve("runs/c03-q001-r1.jsonl")));
        Summary.Row c01 = Summary.rows(runner(in, s).status(), s, in.variants(), tmp.resolve("runs")).get(0);
        assertEquals(Map.of("reasoning_extraction", 1), c01.refusals);
        assertEquals(Map.of("STOP_REASONING_EXTRACTION", 1), c01.outcomes);
    }

    @Test
    void anExceptionPausesTheRunAndStops() throws Exception {
        Inputs in = inputs();
        RunSettings s = settings(n -> {
            Fixture.variants(n, "c01", "c03");
            Fixture.questions(n, "q001");
        });
        ModelClient broken = (system, tools) -> new ModelSession() {
            public ModelTurn send(List<UserPart> parts) {
                throw new IllegalStateException("고장");
            }

            public ModelTurn resend() {
                throw new IllegalStateException("고장");
            }

            public ModelTurn replay(List<UserPart> parts, JsonNode raw, long millis) {
                throw new IllegalStateException("고장");
            }
        };
        Runner.Result res = runner(in, s, (ms, cache, t) -> broken).run();
        assertEquals(Control.Kind.STOP_AND_LOOK, res.stop().kind());
        assertTrue(res.stop().message().contains("고장"));
        RunFile f = file("c01-q001-r1");
        assertEquals("exception", f.events().get(f.events().size() - 1).get("reason").asText());
        assertFalse(Files.exists(tmp.resolve("runs/c03-q001-r1.jsonl")));
    }

    @Test
    void aHalfWrittenLastLineIsCutAndTheRunResumes() throws Exception {
        Inputs in = inputs();
        RunSettings s = settings(n -> {
            Fixture.variants(n, "c01");
            Fixture.questions(n, "q001");
        });
        List<FakeAnthropic.Reply> r = Fixture.solve();
        api.then(r.get(0));
        api.then(FakeAnthropic.error(402, "billing_error", "pay", null));
        assertEquals(Control.Kind.RESUME_LATER, runner(in, s).run().stop().kind());
        Path p = tmp.resolve("runs/c01-q001-r1.jsonl");
        Files.writeString(p, "{\"type\":\"tool\",\"na", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        assertEquals(5, file("c01-q001-r1").events().size());
        api.then(r.get(1));
        api.then(r.get(2));
        assertNull(runner(in, s).run().stop());
        assertTrue(Files.readString(p, StandardCharsets.UTF_8).lines().allMatch(l -> l.startsWith("{") && l.endsWith("}")));
        assertEquals("SUBMITTED", file("c01-q001-r1").end().get("outcome").asText());
    }

    @Test
    void aRecordThatNoLongerMatchesIsCaughtWhenReplaying() throws Exception {
        Inputs in = inputs();
        RunSettings s = settings(n -> {
            Fixture.variants(n, "c01");
            Fixture.questions(n, "q001");
        });
        api.then(Fixture.solve().get(0));
        api.then(FakeAnthropic.error(402, "billing_error", "pay", null));
        runner(in, s).run();
        Path p = tmp.resolve("runs/c01-q001-r1.jsonl");
        String t = Files.readString(p, StandardCharsets.UTF_8);
        assertTrue(t.contains("S1.표본수"));
        Files.writeString(p, t.replace("S1.표본수", "S1.표본"), StandardCharsets.UTF_8);
        Runner.Result res = runner(in, s).run();
        assertEquals(Control.Kind.STOP_AND_LOOK, res.stop().kind());
        assertTrue(res.stop().message().contains("3번째 고리 사건(tool)이 기록과 다르다"), res.stop().message());
        assertEquals(2, api.requests.size());
        RunFile f = file("c01-q001-r1");
        assertEquals("replay_mismatch", f.events().get(f.events().size() - 1).get("reason").asText());
    }

    @Test
    void toolErrorsAreCountedByCode() throws Exception {
        Inputs in = inputs();
        RunSettings s = settings(n -> {
            Fixture.variants(n, "c01");
            Fixture.questions(n, "q001");
        });
        api.then(FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_0", "compare_prices",
                Fixture.M.createObjectNode().put("listing_evidence", "매물.가격").put("basis_evidence", "S9.기준"))), "tool_use", null));
        solveOnce();
        assertNull(runner(in, s).run().stop());
        Summary.Row c01 = Summary.rows(runner(in, s).status(), s, in.variants(), tmp.resolve("runs")).get(0);
        assertEquals(Map.of("UNKNOWN_EVIDENCE", 1), c01.toolErrors);
        assertEquals(Map.of("SUBMITTED", 1), c01.outcomes);
        assertTrue(Summary.format(List.of(c01)).contains("\tUNKNOWN_EVIDENCE=1\t"));
    }

    @Test
    void severalRunsAtOnce() throws Exception {
        Inputs in = inputs();
        RunSettings s = settings(n -> {
            Fixture.variants(n, "c01", "c03");
            ((ObjectNode) n.get("select")).putArray("reps").add(1).add(2);
            n.put("concurrency", 3);
            n.put("block_size", 2);
        });
        api.always(FakeAnthropic.sse(List.of(FakeAnthropic.text("네")), "end_turn", null));
        Runner.Result res = runner(in, s).run();
        assertNull(res.stop());
        assertEquals(List.of(8, 0), List.of(res.done(), res.remaining()));
        assertEquals(16, api.requestCount());
        assertEquals(0, ONE_REPLY.multiply(BigDecimal.valueOf(16)).compareTo(res.spent()));
        Summary.Row total = Summary.rows(runner(in, s).status(), s, in.variants(), tmp.resolve("runs")).get(3);
        assertEquals(Map.of("FAILED_NO_SUBMIT", 8), total.outcomes);
    }
}
