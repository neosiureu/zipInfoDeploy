package com.zipinfo.harness;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.model.AnthropicModelClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 순서 6c: 답변 판정 Batch(judge)를 가짜 배치 서버로 본다(설계 c판 8절). 기록은 가짜 모델 서버로 하네스를 끝까지 돌려 만든다.
 * 요청 꼴, 결과 가르기(4.3 표의 줄마다), 기록에 묶기(가), 다시 치기(나), 판정 지문(다), 파일 줄 규칙과 잇기, 배치 API 오류, 비용.
 */
class JudgeTest {
    static final String ANSWER = "이 매물은 주변보다 3.7% 비싸다.";
    FakeAnthropic api;
    FakeBatches fb;
    Inputs in;
    final List<Long> waits = new ArrayList<>();
    final ByteArrayOutputStream logged = new ByteArrayOutputStream();
    final Map<String, Deque<ObjectNode>> script = new HashMap<>();
    @TempDir
    Path tmp;

    @BeforeEach
    void up() throws Exception {
        api = new FakeAnthropic();
        fb = new FakeBatches();
        fb.pollsBeforeEnd = 0;
        fb.answer = (b, cid, p) -> {
            Deque<ObjectNode> d = script.get(Judge.runIdOf(cid));
            return d == null || d.isEmpty() ? FakeBatches.verdict(true, "묻는 값을 답했다") : d.pollFirst();
        };
        in = Fixture.inputs(Files.createTempDirectory(tmp, "data"));
    }

    @AfterEach
    void down() {
        api.close();
        fb.close();
    }

    Path runs() {
        return tmp.resolve("runs");
    }

    /** 판 c01, 문항 q001·q002, 회차 1(실행 둘). */
    static RunSettings settings(Consumer<ObjectNode> edit) throws Exception {
        return Fixture.settings(n -> {
            Fixture.variants(n, "c01");
            Fixture.questions(n, "q001", "q002");
            edit.accept(n);
        });
    }

    static ObjectNode judgeOf(ObjectNode n) {
        return (ObjectNode) n.get("judge");
    }

    /** 가짜 모델로 계획을 끝까지 돌린다. 실행마다 풀이 셋(Fixture.solve)을 쓴다. extra는 그 뒤에 줄 응답이다. */
    void run(RunSettings s, int solves, FakeAnthropic.Reply... extra) throws Exception {
        for (int i = 0; i < solves; i++) {
            Fixture.solve().forEach(api::then);
        }
        for (FakeAnthropic.Reply r : extra) {
            api.then(r);
        }
        Runner.Result r = new Runner(in, s, runs(), (ms, cache, t) -> new AnthropicModelClient("test-key", api.url(), ms, t, cache), x -> { },
                new PrintStream(OutputStream.nullOutputStream())).run();
        assertNull(r.stop());
    }

    Judge judgeOnly(RunSettings s) throws Exception {
        return new Judge(in, s, Fixture.EXP, runs(), BatchClient.anthropic("test-key", fb.url(), Duration.ofSeconds(30)), waits::add,
                new PrintStream(logged, true, StandardCharsets.UTF_8));
    }

    Judge.Result judge(RunSettings s) throws Exception {
        logged.reset();
        return judgeOnly(s).run();
    }

    String out() {
        return logged.toString(StandardCharsets.UTF_8);
    }

    void script(String runId, ObjectNode... results) {
        script.computeIfAbsent(runId, k -> new ArrayDeque<>()).addAll(List.of(results));
    }

    Path judgeDir() {
        return runs().resolve("judge");
    }

    List<JsonNode> lines(String name) throws IOException {
        List<JsonNode> out = new ArrayList<>();
        for (String l : Files.readString(judgeDir().resolve(name), StandardCharsets.UTF_8).split("\n")) {
            if (!l.isEmpty()) {
                out.add(Fixture.M.readTree(l));
            }
        }
        return out;
    }

    List<String> customIds(int batch) {
        List<String> ids = new ArrayList<>();
        fb.created.get(batch - 1).path("requests").forEach(r -> ids.add(r.path("custom_id").asText()));
        return ids;
    }

    JsonNode params(int batch, int i) {
        return fb.created.get(batch - 1).path("requests").get(i).path("params");
    }

    JsonNode verdictOf(String runId) throws IOException {
        return lines(JudgeFiles.VERDICTS).stream().filter(v -> v.path("run_id").asText().equals(runId)).findFirst().orElse(null);
    }

    static void ok(Judge.Result r) {
        assertNull(r.stop(), () -> r.stop().message());
        assertEquals(0, r.unjudged());
    }

    static void stopped(Judge.Result r, Control.Kind kind) {
        assertTrue(r.stop() != null && r.stop().kind() == kind, () -> String.valueOf(r.stop()));
    }

    // ------------------------------------------------------------ 시험

    @Test
    void judgesTheFinishedSubmittedRunsInOneBatch() throws Exception {
        RunSettings s = settings(n -> Fixture.variants(n, "c01", "c03"));
        ObjectNode cyber = Fixture.M.createObjectNode().put("type", "refusal").put("category", "cyber");
        run(s, 3, FakeAnthropic.sse(List.of(FakeAnthropic.text("거절")), "refusal", cyber));
        script("c01-q002-r1", FakeBatches.verdict(false, "비교를 하지 않았다"));
        fb.pollsBeforeEnd = 2;
        Judge j = judgeOnly(s);
        Judge.Result r = j.run();
        ok(r);
        assertEquals(List.of(3, 3, 2), List.of(r.targets(), r.judged(), r.answered()));
        // 요청: 한 배치, 실행 ID 순, custom_id = 단계_실행 ID
        assertEquals(1, fb.created.size());
        assertEquals(List.of("pilot_c01-q001-r1", "pilot_c01-q002-r1", "pilot_c03-q001-r1"), customIds(1));
        String instructions = Judge.instructions(Fixture.EXP, s.judge());
        JsonNode p = params(1, 0);
        assertEquals("claude-haiku-5-5", p.path("model").asText());
        assertEquals(8000, p.path("max_tokens").asInt());
        assertEquals(instructions, p.path("system").asText());
        assertTrue(instructions.startsWith("너는 부동산 시세 질문에 대한 답을 판정한다."));
        assertEquals(1, p.path("messages").size());
        assertEquals("user", p.path("messages").get(0).path("role").asText());
        assertEquals("[질문]\n시험 글 가\n\n[답]\n" + ANSWER, p.path("messages").get(0).path("content").asText());
        assertEquals("[질문]\n시험 글 나\n\n[답]\n" + ANSWER, params(1, 1).path("messages").get(0).path("content").asText());
        JsonNode tool = p.path("tools").get(0);
        assertEquals(1, p.path("tools").size());
        assertEquals(List.of("submit_verdict", "true", "false"), List.of(tool.path("name").asText(), tool.path("strict").asText(),
                tool.path("input_schema").path("additionalProperties").asText()));
        assertEquals("[\"answered\",\"reason\"]", tool.path("input_schema").path("required").toString());
        assertEquals("boolean", tool.path("input_schema").path("properties").path("answered").path("type").asText());
        assertEquals("auto", p.path("tool_choice").path("type").asText());
        assertEquals("adaptive", p.path("thinking").path("type").asText());
        for (String no : List.of("output_config", "cache_control", "stream", "temperature", "metadata")) {
            assertFalse(fb.created.get(0).toString().contains("\"" + no + "\""), no);
        }
        assertFalse(p.toString().contains("매물]") || p.toString().contains("c01") || p.toString().contains("trade_stats"), "판·도구 기록·매물 블록은 넣지 않는다");
        assertEquals(Fixture.M.readTree(Judge.json(Judge.params(s.judge(), instructions, "시험 글 가", ANSWER, 8000))), p, "보낸 요청 = 지문을 짓는 함수의 요청");
        // 결과는 거꾸로 왔다. custom_id로 짝짓는다
        assertEquals(List.of("GET batch", "GET batch", "GET batch", "GET results"), fb.calls.subList(1, 5));
        assertEquals(List.of(60_000L, 60_000L), waits);
        JsonNode v = verdictOf("c01-q002-r1");
        assertFalse(v.path("answered").asBoolean());
        assertEquals("비교를 하지 않았다", v.path("reason").asText());
        assertEquals(Fingerprint.file(runs().resolve("c01-q002-r1.jsonl")), v.path("record_sha256").asText());
        assertEquals(List.of("pilot", "pilot_c01-q002-r1", j.print(), "msgbatch_fake1", "1", "claude-haiku-5-5", "tool_use", "1000", "0.0001"),
                List.of(v.path("stage").asText(), v.path("custom_id").asText(), v.path("judge").asText(), v.path("batch_id").asText(),
                        v.path("round").asText(), v.path("model").asText(), v.path("stop_reason").asText(), v.path("usage").path("input_tokens").asText(),
                        v.path("cost_usd").asText()));
        assertTrue(verdictOf("c01-q001-r1").path("answered").asBoolean());
        assertEquals(3, lines(JudgeFiles.VERDICTS).size());
        List<JsonNode> b = lines(JudgeFiles.BATCHES);
        assertEquals(List.of("batch", "done"), List.of(b.get(0).path("type").asText(), b.get(1).path("type").asText()));
        assertEquals(j.print(), b.get(0).path("judge").asText());
        assertEquals("claude-haiku-5-5", b.get(0).path("judge_parts").path("model").asText());
        assertEquals(8000, b.get(0).path("requests").get(0).path("max_tokens").asInt());
        assertEquals(Fingerprint.file(runs().resolve("c01-q001-r1.jsonl")), b.get(0).path("requests").get(0).path("record_sha256").asText());
        assertEquals("{\"verdict\":3}", b.get(1).path("counts").toString());
        assertEquals("0.0003", b.get(1).path("cost_usd").asText());
        assertArrayEquals(fb.served.get("msgbatch_fake1"), Files.readAllBytes(judgeDir().resolve("results/msgbatch_fake1.jsonl")), "받은 원문 그대로");
        String o = logged.toString(StandardCharsets.UTF_8);
        assertTrue(o.contains("판정 끝: 대상 3개(계획 안의 끝난 SUBMITTED 실행) 가운데 판정 있음 3개(답함 2, 답하지 않음 1), 판정 없음 0개"), o);
        assertTrue(o.contains("내지 않은 것: 제출 없이 끝난 실행 1개, 끝나지 않은 실행 0개, 계획 밖 기록 0개"), o);
        assertTrue(o.contains("쓴 돈 $0.0003(이 판정 폴더의 모든 결과, Batch 배수 0.5 곱함), 이번 $0.0003"), o);
        // 다시 치면 낼 것이 없다(부르지 않는다)
        int calls = fb.calls.size();
        ok(judge(s));
        assertEquals(calls, fb.calls.size());
        assertTrue(out().contains("판정 있음 3개(답함 2, 답하지 않음 1)"), out());
        // 계획 밖 기록은 내지 않고 센다
        ok(judge(settings(n -> {
            Fixture.variants(n, "c01", "c03");
            Fixture.questions(n, "q001");
        })));
        assertTrue(out().contains("대상 2개(계획 안의 끝난 SUBMITTED 실행) 가운데 판정 있음 2개"), out());
        assertTrue(out().contains("제출 없이 끝난 실행 0개, 끝나지 않은 실행 0개, 계획 밖 기록 2개"), out());
    }

    @Test
    void theJudgeSettingsShapeTheRequest() throws Exception {
        String text = "판정한다.\n- 하나만 본다.\n";
        Path crlf = tmp.resolve("judge_crlf.md");
        Files.writeString(crlf, Normalizer.normalize(text, Normalizer.Form.NFD).replace("\n", "\r\n"), StandardCharsets.UTF_8);
        Path lf = tmp.resolve("judge_lf.md");
        Files.writeString(lf, text, StandardCharsets.UTF_8);
        Consumer<ObjectNode> forced = n -> judgeOf(n).put("tool_choice", "tool").put("thinking", "disabled").put("effort", "low");
        RunSettings s = settings(n -> {
            forced.accept(n);
            judgeOf(n).put("instructions", crlf.toString());
        });
        run(s, 2);
        ok(judge(s));
        JsonNode p = params(1, 0);
        assertEquals(text, p.path("system").asText(), "CRLF·NFD가 LF·NFC로 맞춰져 나간다");
        assertEquals("{\"name\":\"submit_verdict\",\"type\":\"tool\"}", p.path("tool_choice").toString());
        assertEquals("disabled", p.path("thinking").path("type").asText());
        assertEquals("low", p.path("output_config").path("effort").asText());
        RunSettings sLf = settings(n -> {
            forced.accept(n);
            judgeOf(n).put("instructions", lf.toString());
        });
        assertEquals(judgeOnly(s).print(), judgeOnly(sLf).print(), "보내는 글이 같으면 지문이 같다");
        assertNotEquals(judgeOnly(settings(n -> { })).print(), judgeOnly(s).print());
        // thinking omit이면 thinking 칸이 없다
        RunSettings omit = settings(n -> judgeOf(n).put("thinking", "omit"));
        assertFalse(Judge.json(Judge.params(omit.judge(), text, "q", "a", 8000)).contains("thinking"));
    }

    @Test
    void thePrintFollowsWhatIsSent() throws Exception {
        String inst = Judge.instructions(Fixture.EXP, settings(n -> { }).judge());
        String base = Judge.print(settings(n -> { }).judge(), inst, "DEPS");
        List<Consumer<ObjectNode>> edits = List.of(
                n -> judgeOf(n).put("model", "claude-opus-5-5"),
                n -> judgeOf(n).put("max_tokens", 8001),
                n -> judgeOf(n).put("max_tokens_raised", 16001),
                n -> judgeOf(n).put("thinking", "omit"),
                n -> judgeOf(n).put("effort", "medium"),
                n -> judgeOf(n).put("tool_choice", "tool").put("thinking", "disabled"));
        for (Consumer<ObjectNode> e : edits) {
            assertNotEquals(base, Judge.print(settings(e).judge(), inst, "DEPS"));
        }
        JudgeSettings js = settings(n -> { }).judge();
        assertNotEquals(base, Judge.print(js, inst + "- 한 줄 더.\n", "DEPS"), "지시문");
        assertNotEquals(base, Judge.print(js, inst, "DEPS2"), "deps(SDK 판)");
        assertEquals(base, Judge.print(js, inst, "DEPS"));
        // 요청을 짓는 코드의 출력이 바뀌면(시험용으로 바꾼 요청) 지문이 바뀐다
        String b = Judge.json(Judge.params(js, inst, Judge.QUESTION_PLACE, Judge.ANSWER_PLACE, 8000));
        String r = Judge.json(Judge.params(js, inst, Judge.QUESTION_PLACE, Judge.ANSWER_PLACE, 16000));
        assertEquals(base, Judge.print(b, r, "DEPS"));
        assertNotEquals(base, Judge.print(b.replace("\"strict\":true", "\"strict\":false"), r, "DEPS"));
        assertNotEquals(base, Judge.print(b, r.replace("[질문]", "[물음]"), "DEPS"));
    }

    @Test
    void theCustomIdIsStageUnderscoreRunId() {
        // 단계에는 _가 들 수 있고(영문 소문자·숫자·_·- 1~32자) 실행 ID에는 없다. 그래서 마지막 _에서 가른다
        String stage = "user_pc_" + "x".repeat(24);
        String run = Plan.runId("abcdefgh", "q1234567", 99);
        String id = Judge.customId(stage, run);
        assertEquals(32 + 1 + 21, id.length());
        assertTrue(id.matches("[a-zA-Z0-9_-]{1,64}"), id);
        assertEquals(run, Judge.runIdOf(id));
        assertEquals("c01-q001-r1", Judge.runIdOf(Judge.customId("pilot", "c01-q001-r1")));
    }

    @Test
    void limitsWaitForLaterAndARerunSendsThemAgain() throws Exception {
        RunSettings s = settings(n -> Fixture.variants(n, "c01", "c03"));
        run(s, 4);
        // 직접 건 한도는 invalid_request_error로 온다. 01 223줄(멈춤)보다 먼저 본다
        script("c01-q001-r1", FakeBatches.errored("invalid_request_error", "You have reached your specified API usage limits. You will regain access on 2026-12-01 at 00:00 UTC.", null));
        script("c01-q002-r1", FakeBatches.errored("rate_limit_error", "Rate limited", "enforced_spend_limit_reached"));
        script("c03-q001-r1", FakeBatches.errored("invalid_request_error", "Your credit balance is too low to access the Anthropic API. Please go to Plans & Billing.", null));
        script("c03-q002-r1", FakeBatches.errored("billing_error", "결제 문제", null));
        Judge.Result r = judge(s);
        stopped(r, Control.Kind.RESUME_LATER);
        assertEquals(1, fb.created.size(), "새 배치를 내지 않는다");
        assertEquals(List.of(0, 4), List.of(r.judged(), r.unjudged()));
        assertTrue(out().contains("한도 결과: c01-q001-r1: errored invalid_request_error: You have reached your specified"), out());
        assertTrue(out().contains("쓴 돈 $0(이 판정 폴더"), "errored는 요금이 없다");
        ok(judge(s));
        assertEquals(2, fb.created.size());
        assertEquals(4, customIds(2).size(), "다시 치면 다시 낸다");
    }

    @Test
    void anInvalidRequestStopsAndEachRerunSendsItOnce() throws Exception {
        RunSettings s = settings(n -> { });
        run(s, 2);
        ObjectNode bad = FakeBatches.errored("invalid_request_error", "tools.0: 꼴이 틀렸다", null);
        script("c01-q001-r1", bad, bad.deepCopy());
        Judge.Result r = judge(s);
        stopped(r, Control.Kind.STOP_AND_LOOK);
        assertTrue(out().contains("멈춘 결과: c01-q001-r1: errored invalid_request_error: tools.0: 꼴이 틀렸다(요청을 고친다, 01 223줄)"), out());
        assertEquals(List.of(1, 1), List.of(r.judged(), fb.created.size()), "받은 판정은 남기고 새 배치는 내지 않는다");
        stopped(judge(s), Control.Kind.STOP_AND_LOOK);
        assertEquals(List.of("pilot_c01-q001-r1"), customIds(2));
        ok(judge(s));
        assertEquals(3, fb.created.size());
    }

    @Test
    void reasoningExtractionIsNeverSentAgain() throws Exception {
        RunSettings s = settings(n -> { });
        run(s, 2);
        script("c01-q001-r1", FakeBatches.refusal("reasoning_extraction"));
        Judge.Result r = judge(s);
        stopped(r, Control.Kind.STOP_AND_LOOK);
        assertTrue(out().contains("c01-q001-r1: refusal reasoning_extraction(문구를 고친다, 01 226줄)"), out());
        assertTrue(out().contains("쓴 돈 $0.0002"), "거절도 usage대로 센다: " + out());
        int calls = fb.calls.size();
        Judge.Result again = judge(s);
        stopped(again, Control.Kind.STOP_AND_LOOK);
        assertTrue(again.stop().message().contains("새 배치를 내지 않는다: c01-q001-r1. 다시 내지 않는다(01 226줄)"), again.stop().message());
        assertEquals(calls, fb.calls.size(), "다시 쳐도 배치를 내지 않는다");
    }

    @Test
    void maxTokensIsRaisedOnceAndNeverLowered() throws Exception {
        RunSettings s = settings(n -> { });
        run(s, 2);
        script("c01-q001-r1", FakeBatches.maxTokens(), FakeBatches.maxTokens(), FakeBatches.maxTokens());
        Judge.Result r = judge(s);
        stopped(r, Control.Kind.STOP_AND_LOOK);
        assertEquals(2, fb.created.size(), "기본값에서 잘리면 같은 차례에 raised로 다시 낸다");
        assertEquals(List.of("pilot_c01-q001-r1"), customIds(2));
        assertEquals(List.of(8000, 16000), List.of(params(1, 0).path("max_tokens").asInt(), params(2, 0).path("max_tokens").asInt()));
        assertTrue(out().contains("c01-q001-r1: max_tokens 16000(raised)로도 잘렸다"), out());
        assertEquals(16000, lines(JudgeFiles.BATCHES).get(2).path("requests").get(0).path("max_tokens").asInt());
        stopped(judge(s), Control.Kind.STOP_AND_LOOK);
        assertEquals(16000, params(3, 0).path("max_tokens").asInt(), "다시 칠 때도 raised다(내리지 않는다)");
        ok(judge(s));
        assertEquals(16000, params(4, 0).path("max_tokens").asInt());
        assertEquals(4, verdictOf("c01-q001-r1").path("round").asInt());
    }

    @Test
    void aNonVerdictIsSentAgainOnceThenStops() throws Exception {
        RunSettings s = settings(n -> { });
        run(s, 2);
        script("c01-q001-r1", FakeBatches.noTool(), FakeBatches.refusal(null), FakeBatches.twoCalls());
        stopped(judge(s), Control.Kind.STOP_AND_LOOK);
        assertEquals(2, fb.created.size());
        assertTrue(out().contains("c01-q001-r1: 판정 아닌 응답이 되풀이됐다(refusal (범주 없음), 01 228줄)"), out());
        assertTrue(out().contains("이번에 받은 결과: 판정 1, 판정 아닌 응답 2"), out());
        stopped(judge(s), Control.Kind.STOP_AND_LOOK);
        assertEquals(3, fb.created.size(), "다시 치면 한 번 다시 낸다");
        assertTrue(out().contains("(도구 부르기 2번, 01 228줄)"), out());
        ok(judge(s));
        assertEquals(4, fb.created.size());
    }

    @Test
    void threeLostResultsInARowStop() throws Exception {
        RunSettings s = settings(n -> { });
        run(s, 2);
        script("c01-q001-r1", FakeBatches.gone("expired"), FakeBatches.gone("canceled"), FakeBatches.errored("overloaded_error", "Overloaded", null));
        // 등급 상한 표지(details.error_code)가 없는 rate_limit_error는 6의 갈래다(그대로 다시 낸다)
        script("c01-q002-r1", FakeBatches.errored("rate_limit_error", "Rate limited", null));
        Judge.Result r = judge(s);
        stopped(r, Control.Kind.STOP_AND_LOOK);
        assertEquals(3, fb.created.size());
        assertEquals(List.of("pilot_c01-q001-r1", "pilot_c01-q002-r1"), customIds(2));
        assertTrue(out().contains("c01-q001-r1: 세 차례 잇달아 판정을 받지 못했다(errored overloaded_error)"), out());
        assertEquals(1, r.judged());
        ok(judge(s));
        assertEquals(4, fb.created.size());
        assertTrue(out().contains("쓴 돈 $0.0002"), "errored·canceled·expired는 0: " + out());
    }

    @Test
    void verdictsAreTiedToTheRecordFile() throws Exception {
        RunSettings s = settings(n -> { });
        run(s, 2);
        ok(judge(s));
        String old = Fingerprint.file(runs().resolve("c01-q001-r1.jsonl"));
        // 6a 규칙대로 옛 기록을 옮기고 다시 돌린다(같은 실행 ID, 새 기록)
        Files.createDirectories(tmp.resolve("moved"));
        Files.move(runs().resolve("c01-q001-r1.jsonl"), tmp.resolve("moved/c01-q001-r1.jsonl"));
        run(s, 1);
        String now = Fingerprint.file(runs().resolve("c01-q001-r1.jsonl"));
        assertNotEquals(old, now);
        ok(judge(s));
        assertEquals(List.of("pilot_c01-q001-r1"), customIds(2), "기록이 바뀐 실행만 다시 낸다");
        assertTrue(out().contains("쓰지 않는 옛 판정 줄(지금 기록과 해시가 다름) 1개"), out());
        assertEquals(now, lines(JudgeFiles.BATCHES).get(2).path("requests").get(0).path("record_sha256").asText());
        assertEquals(3, lines(JudgeFiles.VERDICTS).size());
        // 같은 (실행, 해시)의 판정 줄이 둘이면 멈춘다
        String last = Files.readString(judgeDir().resolve(JudgeFiles.VERDICTS), StandardCharsets.UTF_8).lines().reduce((a, b) -> b).orElseThrow();
        Files.writeString(judgeDir().resolve(JudgeFiles.VERDICTS), last + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        Judge.Result r = judge(s);
        stopped(r, Control.Kind.STOP_AND_LOOK);
        assertTrue(r.stop().message().contains("같은 (실행 ID, 기록 해시)의 판정 줄이 둘이다: c01-q001-r1"), r.stop().message());
    }

    @Test
    void aPendingBatchIsAwaitedNotSentAgain() throws Exception {
        RunSettings s = settings(n -> { });
        run(s, 2);
        fb.retrieveErrors.add(FakeAnthropic.error(404, "not_found_error", "잠깐 못 찾음", null));
        Judge.Result r = judge(s);
        stopped(r, Control.Kind.STOP_AND_LOOK);
        assertTrue(r.stop().message().contains("배치 msgbatch_fake1의 상태 보기: 모르는 오류(404 not_found_error 잠깐 못 찾음)"), r.stop().message());
        assertEquals(1, lines(JudgeFiles.BATCHES).size(), "배치 ID는 받자마자 적었다");
        assertFalse(Files.exists(judgeDir().resolve(JudgeFiles.VERDICTS)));
        fb.calls.clear();
        ok(judge(s));
        assertEquals(List.of("GET batch", "GET results"), fb.calls, "기다리는 배치를 다시 내지 않고 그것을 기다린다");
        assertEquals(1, fb.created.size());
        assertEquals(2, lines(JudgeFiles.VERDICTS).size());
    }

    @Test
    void aCutLineOrAKilledProcessGivesTheSameVerdicts() throws Exception {
        RunSettings s = settings(n -> { });
        run(s, 2);
        ok(judge(s));
        Path bf = judgeDir().resolve(JudgeFiles.BATCHES);
        Path vf = judgeDir().resolve(JudgeFiles.VERDICTS);
        String verdicts = Files.readString(vf, StandardCharsets.UTF_8);
        String batches = Files.readString(bf, StandardCharsets.UTF_8);
        // 결과 파일까지만 쓰고 끊긴 꼴: done 줄과 판정 줄이 없고, batches.jsonl 끝에 덜 쓴 줄이 있다
        Files.writeString(bf, batches.lines().findFirst().orElseThrow() + "\n{\"type\":\"do", StandardCharsets.UTF_8);
        Files.delete(vf);
        fb.calls.clear();
        ok(judge(s));
        assertEquals(List.of(), fb.calls, "결과 원문으로 가른다(부르지 않는다)");
        assertEquals(verdicts, Files.readString(vf, StandardCharsets.UTF_8), "같은 판정 줄");
        List<JsonNode> b = lines(JudgeFiles.BATCHES);
        assertEquals(List.of("batch", "done"), List.of(b.get(0).path("type").asText(), b.get(1).path("type").asText()), "덜 쓴 줄은 잘라 냈다");
        // 판정 줄 하나를 덜 쓴 채 끊겨도 같다
        Files.writeString(vf, verdicts.lines().findFirst().orElseThrow() + "\n" + verdicts.lines().skip(1).findFirst().orElseThrow().substring(0, 20),
                StandardCharsets.UTF_8);
        Files.writeString(bf, batches.lines().findFirst().orElseThrow() + "\n", StandardCharsets.UTF_8);
        ok(judge(s));
        assertEquals(verdicts, Files.readString(vf, StandardCharsets.UTF_8));
        // 가운데 줄이 JSON이 아니면 멈춘다(3)
        Files.writeString(vf, "{깨진 줄\n" + verdicts, StandardCharsets.UTF_8);
        Judge.Result r = judge(s);
        stopped(r, Control.Kind.STOP_AND_LOOK);
        assertTrue(r.stop().message().contains("verdicts.jsonl의 1번째 줄이 JSON 객체가 아니다"), r.stop().message());
    }

    @Test
    void aDifferentJudgePrintStops() throws Exception {
        RunSettings s = settings(n -> { });
        run(s, 2);
        ok(judge(s));
        Path other = tmp.resolve("judge_v1.md");
        Files.writeString(other, Judge.instructions(Fixture.EXP, s.judge()) + "- 한 줄 더.\n", StandardCharsets.UTF_8);
        RunSettings changed = settings(n -> judgeOf(n).put("instructions", other.toString()));
        int calls = fb.calls.size();
        Judge.Result r = judge(changed);
        stopped(r, Control.Kind.STOP_AND_LOOK);
        assertTrue(r.stop().message().contains("과 다른 줄이 있다(batches.jsonl 1줄, verdicts.jsonl 2줄, 달라진 칸 instructions·instructions_sha256)"),
                r.stop().message());
        assertEquals(calls, fb.calls.size());
        // 기다리는 배치의 지문이 달라도 멈춘다
        Files.move(judgeDir(), tmp.resolve("judge_old"));
        fb.retrieveErrors.add(FakeAnthropic.error(404, "not_found_error", "잠깐 못 찾음", null));
        stopped(judge(s), Control.Kind.STOP_AND_LOOK);
        Judge.Result p = judge(settings(n -> judgeOf(n).put("max_tokens", 9000)));
        stopped(p, Control.Kind.STOP_AND_LOOK);
        assertTrue(p.stop().message().contains("(batches.jsonl 1줄, verdicts.jsonl 0줄, 달라진 칸 max_tokens)"), p.stop().message());
    }

    @Test
    void batchApiErrorsAreRetriedOrStop() throws Exception {
        RunSettings s = settings(n -> { });
        run(s, 2);
        fb.createErrors.add(FakeAnthropic.error(400, "invalid_request_error", "requests.0: 틀림", null));
        Judge.Result r = judge(s);
        stopped(r, Control.Kind.STOP_AND_LOOK);
        assertTrue(r.stop().message().contains("배치 만들기(요청 2개): 모르는 오류(400 invalid_request_error requests.0: 틀림)"), r.stop().message());
        assertFalse(Files.exists(judgeDir().resolve(JudgeFiles.BATCHES)), "만들지 못한 배치는 적지 않는다");
        fb.createErrors.add(FakeAnthropic.error(429, "rate_limit_error", "등급 상한", "enforced_spend_limit_reached"));
        stopped(judge(s), Control.Kind.RESUME_LATER);
        // 재시도: 529는 base 2초, retry-after가 있으면 그 값, 500은 다시 2초
        fb.createErrors.add(FakeAnthropic.error(529, "overloaded_error", "Overloaded", null));
        fb.retrieveErrors.add(FakeAnthropic.error(429, "rate_limit_error", "천천히", null, Map.of("retry-after", "7")));
        fb.resultErrors.add(FakeAnthropic.error(500, "api_error", "잠깐", null));
        waits.clear();
        ok(judge(s));
        assertEquals(List.of(2000L, 7000L, 2000L), waits);
        assertEquals(1, fb.created.size());
        // 재시도를 다 쓰면 멈추고, 다시 치면 잇는다
        RunSettings two = settings(n -> ((ObjectNode) n.get("retry")).put("max_attempts", 2));
        Files.move(judgeDir(), tmp.resolve("judge_old"));
        fb.resultErrors.add(FakeAnthropic.error(500, "api_error", "잠깐", null));
        fb.resultErrors.add(FakeAnthropic.error(503, "api_error", "잠깐", null));
        Judge.Result e = judge(two);
        stopped(e, Control.Kind.STOP_AND_LOOK);
        assertTrue(e.stop().message().contains("배치 msgbatch_fake2의 결과 받기: 재시도를 다 썼다(503 api_error 잠깐). 다시 judge하면 잇는다"), e.stop().message());
        ok(judge(two));
        assertEquals(2, fb.created.size());
    }

    @Test
    void resultsMustMatchTheBatch() throws Exception {
        RunSettings s = settings(n -> { });
        run(s, 2);
        fb.mangle = t -> "이것은 JSON이 아니다\n" + t;
        Judge.Result r = judge(s);
        stopped(r, Control.Kind.STOP_AND_LOOK);
        assertTrue(r.stop().message().contains("받은 결과의 1번째 줄이 JSON 객체가 아니다. 결과 원문을 남기지 않았다. 다시 치면 다시 받는다"), r.stop().message());
        assertFalse(Files.exists(judgeDir().resolve("results/msgbatch_fake1.jsonl")));
        fb.mangle = t -> t.lines().filter(l -> !l.contains("pilot_c01-q001-r1")).map(l -> l + "\n").reduce("", String::concat)
                + "{\"custom_id\":\"pilot_c09-q001-r1\",\"result\":{\"type\":\"expired\"}}\n";
        Judge.Result m = judge(s);
        stopped(m, Control.Kind.STOP_AND_LOOK);
        assertTrue(out().contains("배치 msgbatch_fake1: 결과가 낸 요청과 맞지 않는다(낸 적 없는 custom_id 1개, 결과가 둘인 것 0개, 결과가 없는 요청 1개)"), out());
        assertEquals(List.of(1, 1), List.of(m.judged(), fb.created.size()));
        fb.mangle = t -> t;
        ok(judge(s));
        assertEquals(List.of("pilot_c01-q001-r1"), customIds(2), "결과가 없던 요청을 다시 낸다");
    }

    @Test
    void recordsThatDoNotMatchStopBeforeAnyCall() throws Exception {
        RunSettings s = settings(n -> { });
        run(s, 2);
        Judge.Result stale = judge(settings(n -> n.put("max_turns", 19)));
        stopped(stale, Control.Kind.STOP_AND_LOOK);
        assertTrue(out().contains("요청 지문이 다른 기록: c01-q001-r1.jsonl (판 c01, 달라진 칸 ") && out().contains("max_turns"), out());
        assertTrue(stale.stop().message().contains("기록이 지금 설정·코드와 맞지 않는다. 판정 배치를 내지 않았다"), stale.stop().message());
        Files.copy(runs().resolve("c01-q001-r1.jsonl"), runs().resolve("c01-q001-r1-copy.jsonl"));
        stopped(judge(s), Control.Kind.STOP_AND_LOOK);
        assertTrue(out().contains("기록 문제: c01-q001-r1-copy.jsonl: 파일 이름이 머리의 실행 ID(c01-q001-r1)와 다르다"), out());
        assertEquals(List.of(), fb.calls);
    }
}
