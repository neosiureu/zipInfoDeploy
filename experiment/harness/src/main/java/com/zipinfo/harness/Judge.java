package com.zipinfo.harness;

import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolChoiceAuto;
import com.anthropic.models.messages.ToolChoiceTool;
import com.anthropic.models.messages.batches.BatchCreateParams;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.loop.RunRecord;
import com.zipinfo.ai.model.ModelError;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 답변 판정 Batch(judge 명령, 순서 6c 설계 c판). 01 219~231줄의 답변 판정을 Message Batches API로 돌린다.
 * <ol>
 * <li>대상: 설정의 계획에 든 실행 가운데 끝났고(end) 끝난 꼴이 SUBMITTED인 것. 입력은 기록 머리의 질문 글과 accepted 사건의 답(그린 답)이다.
 *     판, 모델, 도구 기록, 장치 글은 넣지 않는다(01 220줄). 기록 문제나 요청 지문이 다른 기록이 하나라도 있으면 아무것도 내지 않고 멈춘다(run과 같다).</li>
 * <li>판정은 기록 파일에 묶는다: 요청과 판정 줄마다 그 기록 파일의 SHA-256(줄 끝 LF로 맞춤)을 적고, 지금 기록과 해시가 같은 판정만 쓴다.</li>
 * <li>판정 지문: 자리표시자 질문·답으로 지은 요청 본문 JSON(max_tokens 기본·raised 둘)과 deps를 이은 것의 SHA-256. 실제로 배치에 넣는 요청을
 *     짓는 함수로 짓는다. 판정 파일에 지금과 다른 지문의 줄이 있으면 멈춘다(판정 폴더를 옮기고 처음부터 다시 판정한다, 01 200줄).</li>
 * <li>기다리는 배치가 있으면 그것부터 끝나기를 기다려 결과를 받는다(다시 내지 않는다). 그다음 판정이 없는 대상을 한 배치로 낸다(실행 ID 순).</li>
 * <li>결과 가르기(설계 4.3)와 멈춤(4.4): 멈춤은 이번에 새로 받은 결과를 가를 때만 건다. 다시 치면 걸렸던 실행도 한 번은 다시 낸다.
 *     다만 지금 해시의 마지막 결과가 reasoning_extraction 거절인 실행이 있으면 다시 쳐도 새 배치를 내지 않는다(01 226줄).
 *     낼 때의 max_tokens는 지금 해시의 결과 가운데 max_tokens로 끝난 것이 한 번이라도 있으면 raised, 없으면 기본값이다(내리지 않는다).</li>
 * <li>비용: succeeded 결과의 usage × 판정 모델 단가 × batch_factor. 실행의 비용 상한에 넣지 않고 따로 찍는다.</li>
 * </ol>
 * 판정은 단계의 .lock을 잡는다(run과 같이 돌지 않는다). 멈춤은 모두 Result.stop(3으로 끝남)이고, 설정·파일 읽기 오류만 예외로 Main에 간다(2로 끝남).
 */
final class Judge {
    static final String TOOL = "submit_verdict";
    static final String QUESTION_PLACE = "(질문 자리)";
    static final String ANSWER_PLACE = "(답 자리)";
    static final int MAX_REQUESTS = 100_000;
    static final long MAX_BYTES = 256_000_000L;

    /** 결과의 꼴(설계 4.3 표의 차례). */
    enum Kind { VERDICT, LIMIT, INVALID, REASONING, MAX_TOKENS, NOT_VERDICT, GONE, UNKNOWN }

    /** 결과 하나를 가른 것. answered·reason은 판정일 때만 있다. */
    record Classified(Kind kind, String detail, Boolean answered, String reason) {}

    /** 판정 대상: 실행 ID, 기록 해시, 질문 글, 그린 답. */
    record Target(String runId, String sha256, String question, String answer) {}

    /** 낸 요청 하나. */
    record Req(String customId, String runId, String sha256, long maxTokens) {}

    /** 낸 배치 하나(batches.jsonl의 batch 줄). */
    record Batch(String id, int round, List<Req> requests) {}

    /** targets: 대상 수, judged: 판정이 있는 대상, answered: 그 가운데 답함, unjudged: 판정이 없는 대상. stop이 null이면 모두 판정했다. */
    record Result(int targets, int judged, int answered, int unjudged, Control.Stop stop) {}

    /** 멈춤(3으로 끝남). */
    static final class Halt extends Exception {
        final Control.Stop stop;

        Halt(Control.Kind kind, String message) {
            super(message);
            this.stop = new Control.Stop(kind, message);
        }
    }

    interface ApiCall<T> {
        T call() throws ModelError;
    }

    private final RunSettings s;
    private final JudgeSettings js;
    private final Path dir;
    private final BatchClient client;
    private final Control.Sleeper sleeper;
    private final PrintStream log;
    private final Runner runner;
    private final String instructions;
    private final String print;
    private final Map<String, Object> parts;
    /** (실행 ID, 기록 해시)마다 받은 결과의 꼴, 배치 차례대로. */
    private final Map<String, List<Kind>> history = new HashMap<>();
    /** (실행 ID, 기록 해시) → 판정 줄. */
    private final Map<String, JsonNode> verdicts = new HashMap<>();
    private final Map<Kind, Integer> fresh = new TreeMap<>();
    private BigDecimal spent = BigDecimal.ZERO;
    private BigDecimal spentNow = BigDecimal.ZERO;
    private int submitted;
    private int requested;

    Judge(Inputs in, RunSettings s, Path experiment, Path dir, BatchClient client, Control.Sleeper sleeper, PrintStream log) throws IOException {
        this.s = s;
        this.js = s.judge();
        this.dir = dir;
        this.client = client;
        this.sleeper = sleeper;
        this.log = log;
        this.runner = new Runner(in, s, dir, (ms, c, t) -> {
            throw new IllegalStateException("판정은 실행을 돌리지 않는다");
        }, sleeper, log);
        this.instructions = instructions(experiment, js);
        this.print = print(js, instructions, in.digests().deps());
        this.parts = parts(js, instructions, in.digests().deps());
    }

    String print() {
        return print;
    }

    // ------------------------------------------------------------ 요청 짓기와 판정 지문

    /** 지시문 파일을 읽어 줄 끝은 LF로, 글자는 NFC로 맞춘다(Cards.parse와 같다). 이 글을 그대로 시스템 글로 보낸다. */
    static String instructions(Path experiment, JudgeSettings js) throws IOException {
        Path f = experiment.resolve(js.instructions());
        if (!Files.isRegularFile(f)) {
            throw new IOException("판정 지시문 파일이 없다: " + js.instructions());
        }
        String t = Normalizer.normalize(Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n"), Normalizer.Form.NFC);
        if (t.isBlank()) {
            throw new IllegalArgumentException("판정 지시문이 비었다: " + js.instructions());
        }
        return t;
    }

    /** 사용자 글 하나(설계 2.1). */
    static String userText(String question, String answer) {
        return "[질문]\n" + question + "\n\n[답]\n" + answer;
    }

    /** 판정 도구(설계 2.3): strict, 두 칸 모두 필수, additionalProperties false. */
    static Tool tool() {
        ObjectNode answered = Json.M.createObjectNode().put("type", "boolean").put("description", "답이 질문이 묻는 것에 모두 답했으면 true");
        ObjectNode reason = Json.M.createObjectNode().put("type", "string").put("description", "판정의 근거를 한 문장으로");
        Tool.InputSchema.Properties props = Tool.InputSchema.Properties.builder().putAdditionalProperty("answered", JsonValue.fromJsonNode(answered))
                .putAdditionalProperty("reason", JsonValue.fromJsonNode(reason)).build();
        Tool.InputSchema schema = Tool.InputSchema.builder().properties(props).required(List.of("answered", "reason"))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false)).build();
        return Tool.builder().name(TOOL).description("판정을 낸다. 한 번만 부른다.").inputSchema(schema).strict(true).build();
    }

    /** 배치에 넣는 요청 본문(params). 캐시·fallback·temperature는 두지 않는다. */
    static BatchCreateParams.Request.Params params(JudgeSettings js, String instructions, String question, String answer, long maxTokens) {
        BatchCreateParams.Request.Params.Builder p = BatchCreateParams.Request.Params.builder().model(js.model()).maxTokens(maxTokens)
                .system(instructions).addUserMessage(userText(question, answer)).addTool(tool());
        if (js.toolChoice().equals(JudgeSettings.TOOL)) {
            p.toolChoice(ToolChoiceTool.builder().name(TOOL).build());
        } else {
            p.toolChoice(ToolChoiceAuto.builder().build());
        }
        switch (js.thinking()) {
            case ADAPTIVE -> p.thinking(ThinkingConfigAdaptive.builder().build());
            case DISABLED -> p.thinking(ThinkingConfigDisabled.builder().build());
            case OMIT -> { }
        }
        if (js.effort() != null) {
            p.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(js.effort())).build());
        }
        return p.build();
    }

    /** SDK가 보낼 때와 같은 JSON. */
    static String json(Object sdk) {
        try {
            return ObjectMappers.jsonMapper().writeValueAsString(sdk);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 판정 지문(설계 3절): 자리표시자로 지은 요청 본문 JSON 둘(기본 max_tokens, raised)과 deps. */
    static String print(JudgeSettings js, String instructions, String deps) {
        return print(json(params(js, instructions, QUESTION_PLACE, ANSWER_PLACE, js.maxTokens())),
                json(params(js, instructions, QUESTION_PLACE, ANSWER_PLACE, js.maxTokensRaised())), deps);
    }

    static String print(String baseRequest, String raisedRequest, String deps) {
        return Fingerprint.sha256(baseRequest + "\n" + raisedRequest + "\n" + deps);
    }

    /** 읽기 좋은 칸. 지문이 달라졌을 때 어느 칸이 바뀌었는지 찍는 데만 쓴다. */
    static Map<String, Object> parts(JudgeSettings js, String instructions, String deps) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("model", js.model());
        m.put("max_tokens", js.maxTokens());
        m.put("max_tokens_raised", js.maxTokensRaised());
        m.put("thinking", js.thinking().name().toLowerCase(java.util.Locale.ROOT));
        m.put("effort", js.effort());
        m.put("tool_choice", js.toolChoice());
        m.put("instructions", js.instructions());
        m.put("instructions_sha256", Fingerprint.sha256(instructions));
        m.put("deps", deps);
        return m;
    }

    static String customId(String stage, String runId) {
        return stage + "_" + runId;
    }

    /** custom_id의 실행 ID(마지막 _ 뒤). 단계에는 _가 들 수 있고 실행 ID에는 없다. */
    static String runIdOf(String customId) {
        return customId.substring(customId.lastIndexOf('_') + 1);
    }

    private static String key(String runId, String sha256) {
        return runId + " " + sha256;
    }

    // ------------------------------------------------------------ 결과 가르기(설계 4.3)

    static Classified classify(JsonNode result) {
        String type = result.path("type").asText("");
        switch (type) {
            case "succeeded" -> {
                JsonNode m = result.path("message");
                String stop = m.path("stop_reason").asText("");
                if (stop.equals("max_tokens")) {
                    return new Classified(Kind.MAX_TOKENS, "max_tokens", null, null);
                }
                if (stop.equals("refusal")) {
                    String cat = m.path("stop_details").path("category").asText("");
                    return cat.equals("reasoning_extraction") ? new Classified(Kind.REASONING, "refusal reasoning_extraction", null, null)
                            : new Classified(Kind.NOT_VERDICT, "refusal " + (cat.isEmpty() ? "(범주 없음)" : cat), null, null);
                }
                List<JsonNode> calls = new ArrayList<>();
                for (JsonNode b : m.path("content")) {
                    if ("tool_use".equals(b.path("type").asText())) {
                        calls.add(b);
                    }
                }
                if (!stop.equals("tool_use")) {
                    return new Classified(Kind.NOT_VERDICT, "stop_reason " + (stop.isEmpty() ? "(없음)" : stop), null, null);
                }
                if (calls.size() != 1 || !TOOL.equals(calls.get(0).path("name").asText())) {
                    return new Classified(Kind.NOT_VERDICT, "도구 부르기 " + calls.size() + "번", null, null);
                }
                JsonNode in = calls.get(0).path("input");
                if (!in.path("answered").isBoolean() || !in.path("reason").isTextual()) {
                    return new Classified(Kind.NOT_VERDICT, "submit_verdict 입력 꼴이 다르다", null, null);
                }
                return new Classified(Kind.VERDICT, "판정", in.get("answered").asBoolean(), in.get("reason").asText());
            }
            case "errored" -> {
                JsonNode e = result.path("error").path("error");
                String et = e.path("type").asText("");
                String msg = e.path("message").asText("");
                String code = e.path("details").path("error_code").asText("");
                if (msg.startsWith("You have reached your specified") || msg.startsWith("Your credit balance is too low to access the Anthropic API")
                        || et.equals("billing_error") || code.equals("enforced_spend_limit_reached")) {
                    return new Classified(Kind.LIMIT, "errored " + et + ": " + msg, null, null);
                }
                if (et.equals("invalid_request_error")) {
                    return new Classified(Kind.INVALID, "errored invalid_request_error: " + msg, null, null);
                }
                return new Classified(Kind.GONE, "errored " + (et.isEmpty() ? "(type 없음)" : et), null, null);
            }
            case "canceled", "expired" -> {
                return new Classified(Kind.GONE, type, null, null);
            }
            default -> {
                return new Classified(Kind.UNKNOWN, "모르는 결과 꼴 " + (type.isEmpty() ? "(없음)" : type), null, null);
            }
        }
    }

    /** 새로 받은 판정 아닌 결과로 멈출 까닭(설계 4.3의 3~6). 다시 낼 것이면 null. h는 이 결과까지 더한 그 (실행, 해시)의 결과들이다. */
    private String why(Classified c, Req req, List<Kind> h) {
        return switch (c.kind()) {
            case INVALID -> c.detail() + "(요청을 고친다, 01 223줄)";
            case REASONING -> c.detail() + "(문구를 고친다, 01 226줄)";
            case MAX_TOKENS -> req.maxTokens() >= js.maxTokensRaised() ? "max_tokens " + req.maxTokens() + "(raised)로도 잘렸다" : null;
            case NOT_VERDICT -> h.stream().filter(k -> k == Kind.NOT_VERDICT).count() >= 2 ? "판정 아닌 응답이 되풀이됐다(" + c.detail() + ", 01 228줄)" : null;
            case GONE -> h.size() >= 3 && h.subList(h.size() - 3, h.size()).stream().allMatch(k -> k == Kind.GONE)
                    ? "세 차례 잇달아 판정을 받지 못했다(" + c.detail() + ")" : null;
            case UNKNOWN -> c.detail();
            case VERDICT, LIMIT -> null;
        };
    }

    /** succeeded 결과의 값(usage × 판정 모델 단가 × batch_factor). */
    private BigDecimal cost(JsonNode message) {
        JsonNode u = message.path("usage");
        Prices.Use use = Prices.use(u.path("input_tokens").asLong(), u.path("cache_read_input_tokens").asLong(),
                u.path("cache_creation_input_tokens").asLong(), u.path("output_tokens").asLong(), message, "5m");
        return s.prices().withCache(js.model(), use).multiply(s.prices().batchFactor());
    }

    static String usd(BigDecimal x) {
        return x.signum() == 0 ? "0" : x.stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------ 돌리기

    Result run() throws IOException, InterruptedException {
        Files.createDirectories(dir);
        try (FileChannel ch = FileChannel.open(dir.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock = ch.tryLock();
            if (lock == null) {
                Control.Stop busy = new Control.Stop(Control.Kind.STOP_AND_LOOK, "다른 하네스가 이 단계(" + s.stage() + ")를 돌리고 있다");
                log.println(busy.message());
                return new Result(0, 0, 0, 0, busy);
            }
            try {
                return runLocked();
            } finally {
                lock.release();
            }
        }
    }

    private Result runLocked() throws IOException, InterruptedException {
        Runner.Status st = runner.status();
        if (!st.problems().isEmpty() || !st.stale().isEmpty()) {
            st.problems().forEach(p -> log.println("기록 문제: " + p));
            for (Runner.Stale x : st.stale()) {
                log.println("요청 지문이 다른 기록: " + x.file() + " (판 " + x.variant() + ", 달라진 칸 " + String.join("·", x.changed()) + ")");
            }
            return halt(new Halt(Control.Kind.STOP_AND_LOOK, "기록이 지금 설정·코드와 맞지 않는다. 판정 배치를 내지 않았다. 기록 문제를 고치거나, "
                    + "01 200줄대로 그 판의 기록을 모두 다른 폴더로 옮기고 다시 돌린 뒤에 판정한다"), List.of());
        }
        Set<String> planned = st.plan().stream().map(Plan.RunSpec::id).collect(Collectors.toSet());
        List<Target> targets = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        int notSubmitted = 0;
        int unfinished = 0;
        for (Plan.RunSpec r : st.plan().stream().sorted((a, b) -> a.id().compareTo(b.id())).toList()) {
            if (!st.done().contains(r.id())) {
                unfinished++;
                continue;
            }
            Path f = dir.resolve(r.id() + ".jsonl");
            RunFile rf = RunFile.read(f, false);
            if (!RunRecord.Outcome.SUBMITTED.name().equals(rf.end().path("outcome").asText())) {
                notSubmitted++;
                continue;
            }
            List<JsonNode> acc = rf.loopEvents().stream().filter(e -> "accepted".equals(e.path("type").asText())).toList();
            if (acc.size() != 1 || !acc.get(0).path("answer").isTextual()) {
                problems.add(r.id() + ".jsonl: 제출이 받아들여진 기록인데 받아들여진 답(accepted)이 " + acc.size() + "개다");
                continue;
            }
            targets.add(new Target(r.id(), Fingerprint.file(f), rf.header().path("question").path("text").asText(), acc.get(0).get("answer").asText()));
        }
        int outside = 0;
        for (Path f : records()) {
            JsonNode h = RunFile.read(f, false).header();
            if (h != null && !planned.contains(h.path("run_id").asText())) {
                outside++;
            }
        }
        if (!problems.isEmpty()) {
            problems.forEach(p -> log.println("기록 문제: " + p));
            return halt(new Halt(Control.Kind.STOP_AND_LOOK, "기록이 판정 대상의 꼴과 맞지 않는다. 판정 배치를 내지 않았다"), targets);
        }
        JudgeFiles jf;
        List<Batch> batches;
        Set<String> doneIds = new HashSet<>();
        try {
            jf = JudgeFiles.open(dir.resolve("judge"));
            batches = batches(jf, doneIds);
            checkPrints(jf);
            for (JsonNode v : jf.verdictLines()) {
                if (verdicts.put(key(v.path("run_id").asText(), v.path("record_sha256").asText()), v) != null) {
                    throw new JudgeFiles.Bad("verdicts.jsonl에 같은 (실행 ID, 기록 해시)의 판정 줄이 둘이다: " + v.path("run_id").asText());
                }
            }
            for (Batch b : batches) {
                if (doneIds.contains(b.id())) {
                    if (!jf.hasResults(b.id())) {
                        throw new JudgeFiles.Bad("다 가른 배치(" + b.id() + ")의 결과 원문(results/" + b.id() + ".jsonl)이 없다");
                    }
                    apply(jf, b, jf.readResults(b.id()), false, new ArrayList<>(), new ArrayList<>());
                }
            }
        } catch (JudgeFiles.Bad e) {
            return halt(new Halt(Control.Kind.STOP_AND_LOOK, "판정 파일: " + e.getMessage() + ". 사람이 보고 고친다"), targets);
        }
        long judged = targets.stream().filter(t -> verdicts.containsKey(key(t.runId(), t.sha256()))).count();
        log.println("판정: 단계 " + s.stage() + ", 판정 지문 " + print.substring(0, 12) + "(모델 " + js.model() + ", max_tokens " + js.maxTokens() + "·"
                + js.maxTokensRaised() + ", thinking " + parts.get("thinking") + ", effort " + (js.effort() == null ? "보내지 않음" : js.effort())
                + ", tool_choice " + js.toolChoice() + ", 지시문 " + js.instructions() + "), 대상 " + targets.size() + "개 가운데 판정 있음 " + judged
                + ", 낸 배치 " + batches.size() + "개(기다리는 것 " + batches.stream().filter(b -> !doneIds.contains(b.id())).count() + ")");
        int old = 0;
        Set<String> targetIds = targets.stream().map(Target::runId).collect(Collectors.toSet());
        Set<String> current = targets.stream().map(t -> key(t.runId(), t.sha256())).collect(Collectors.toSet());
        for (JsonNode v : jf.verdictLines()) {
            if (targetIds.contains(v.path("run_id").asText()) && !current.contains(key(v.path("run_id").asText(), v.path("record_sha256").asText()))) {
                old++;
            }
        }
        Counts counts = new Counts(targets.size(), notSubmitted, unfinished, outside, old);
        try {
            while (true) {
                List<String> stops = new ArrayList<>();
                List<String> limits = new ArrayList<>();
                for (Batch b : batches) {
                    if (!doneIds.contains(b.id())) {
                        settle(jf, b, stops, limits);
                        doneIds.add(b.id());
                    }
                }
                if (!stops.isEmpty() || !limits.isEmpty()) {
                    stops.forEach(x -> log.println("멈춘 결과: " + x));
                    limits.forEach(x -> log.println("한도 결과: " + x));
                    throw stops.isEmpty()
                            ? new Halt(Control.Kind.RESUME_LATER, "한도나 크레딧에 닿은 결과가 있어 새 배치를 내지 않았다. 풀리면 다시 judge한다(그 실행을 다시 낸다)")
                            : new Halt(Control.Kind.STOP_AND_LOOK, "판정이 아닌 결과로 멈췄다(실행 " + stops.size() + "개). 새 배치를 내지 않았다. 받은 판정은 남겼다."
                                    + " 고칠 것을 고친 뒤 다시 judge하면 걸린 실행도 한 번 다시 낸다(reasoning_extraction은 다시 내지 않는다)");
                }
                List<Target> todo = targets.stream().filter(t -> !verdicts.containsKey(key(t.runId(), t.sha256()))).toList();
                List<String> refused = todo.stream().filter(t -> {
                    List<Kind> h = history.getOrDefault(key(t.runId(), t.sha256()), List.of());
                    return !h.isEmpty() && h.get(h.size() - 1) == Kind.REASONING;
                }).map(Target::runId).toList();
                if (!refused.isEmpty()) {
                    throw new Halt(Control.Kind.STOP_AND_LOOK, "판정 요청의 문구 때문에 거절된(reasoning_extraction) 실행이 있어 새 배치를 내지 않는다: "
                            + some(refused) + ". 다시 내지 않는다(01 226줄). 지시문이나 입력 틀을 고치면 판정 지문이 바뀐다. 그때 판정 폴더를 옮기고 처음부터 다시 판정한다");
                }
                if (todo.isEmpty()) {
                    return finish(counts, targets, null);
                }
                batches.add(submit(jf, todo, batches.size() + 1));
            }
        } catch (Halt h) {
            return finish(counts, targets, h);
        } catch (JudgeFiles.Bad e) {
            return finish(counts, targets, new Halt(Control.Kind.STOP_AND_LOOK, "판정 파일: " + e.getMessage() + ". 사람이 보고 고친다"));
        }
    }

    private record Counts(int targets, int notSubmitted, int unfinished, int outside, int old) {}

    private Result halt(Halt h, List<Target> targets) {
        log.println("멈춤(" + h.stop.kind() + "): " + h.stop.message());
        return new Result(targets.size(), 0, 0, targets.size(), h.stop);
    }

    private Result finish(Counts c, List<Target> targets, Halt h) {
        int judged = 0;
        int answered = 0;
        for (Target t : targets) {
            JsonNode v = verdicts.get(key(t.runId(), t.sha256()));
            if (v != null) {
                judged++;
                if (v.path("answered").asBoolean()) {
                    answered++;
                }
            }
        }
        if (h != null) {
            log.println("멈춤(" + h.stop.kind() + "): " + h.stop.message());
        }
        log.println("판정 끝: 대상 " + c.targets() + "개(계획 안의 끝난 SUBMITTED 실행) 가운데 판정 있음 " + judged + "개(답함 " + answered + ", 답하지 않음 "
                + (judged - answered) + "), 판정 없음 " + (c.targets() - judged) + "개");
        log.println("  내지 않은 것: 제출 없이 끝난 실행 " + c.notSubmitted() + "개, 끝나지 않은 실행 " + c.unfinished() + "개, 계획 밖 기록 " + c.outside() + "개");
        log.println("  쓰지 않는 옛 판정 줄(지금 기록과 해시가 다름) " + c.old() + "개");
        log.println("  이번에 낸 배치 " + submitted + "개(요청 " + requested + "개), 이번에 받은 결과: "
                + (fresh.isEmpty() ? "없음" : fresh.entrySet().stream().map(e -> label(e.getKey()) + " " + e.getValue()).collect(Collectors.joining(", "))));
        log.println("  쓴 돈 $" + usd(spent) + "(이 판정 폴더의 모든 결과, Batch 배수 " + s.prices().batchFactor().toPlainString() + " 곱함), 이번 $" + usd(spentNow));
        return new Result(c.targets(), judged, answered, c.targets() - judged, h == null ? null : h.stop);
    }

    private static String label(Kind k) {
        return switch (k) {
            case VERDICT -> "판정";
            case LIMIT -> "한도";
            case INVALID -> "invalid_request_error";
            case REASONING -> "reasoning_extraction 거절";
            case MAX_TOKENS -> "max_tokens";
            case NOT_VERDICT -> "판정 아닌 응답";
            case GONE -> "errored·canceled·expired";
            case UNKNOWN -> "모르는 꼴";
        };
    }

    private static String some(List<String> ids) {
        return String.join(" ", ids.subList(0, Math.min(5, ids.size()))) + (ids.size() > 5 ? " … (" + ids.size() + "개)" : "");
    }

    private List<Path> records() throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> x = Files.list(dir)) {
            return x.filter(p -> p.getFileName().toString().endsWith(".jsonl")).sorted().toList();
        }
    }

    /** batches.jsonl의 batch 줄(차례대로). done 줄이 있는 배치 ID는 doneIds에 넣는다. */
    private static List<Batch> batches(JudgeFiles jf, Set<String> doneIds) throws JudgeFiles.Bad {
        Map<String, Batch> out = new LinkedHashMap<>();
        for (JsonNode l : jf.batchLines()) {
            String id = l.path("batch_id").asText("");
            switch (l.path("type").asText("")) {
                case "batch" -> {
                    if (!JudgeFiles.goodBatchId(id) || out.containsKey(id)) {
                        throw new JudgeFiles.Bad("batches.jsonl의 배치 줄 꼴이 다르거나 같은 배치가 둘이다: " + id);
                    }
                    List<Req> rs = new ArrayList<>();
                    for (JsonNode r : l.path("requests")) {
                        if (!r.path("custom_id").isTextual() || !r.path("run_id").isTextual() || !r.path("record_sha256").isTextual()
                                || !r.path("max_tokens").canConvertToLong()) {
                            throw new JudgeFiles.Bad("batches.jsonl의 배치 " + id + "에 꼴이 다른 요청이 있다");
                        }
                        rs.add(new Req(r.get("custom_id").asText(), r.get("run_id").asText(), r.get("record_sha256").asText(), r.get("max_tokens").asLong()));
                    }
                    out.put(id, new Batch(id, l.path("round").asInt(), List.copyOf(rs)));
                }
                case "done" -> {
                    if (!out.containsKey(id) || !doneIds.add(id)) {
                        throw new JudgeFiles.Bad("batches.jsonl의 done 줄에 맞는 배치 줄이 없거나 done 줄이 둘이다: " + id);
                    }
                }
                default -> throw new JudgeFiles.Bad("batches.jsonl에 모르는 줄이 있다: " + l.path("type").asText(""));
            }
        }
        return new ArrayList<>(out.values());
    }

    /** 판정 파일의 줄이 모두 지금 판정 지문인지 본다(기다리는 배치 포함). */
    private void checkPrints(JudgeFiles jf) throws JudgeFiles.Bad {
        int nb = 0;
        int nv = 0;
        Set<String> changed = new TreeSet<>();
        JsonNode now = Json.read(Json.write(parts), "판정 지문 칸");
        for (JsonNode l : jf.batchLines()) {
            if ("batch".equals(l.path("type").asText()) && !print.equals(l.path("judge").asText())) {
                nb++;
                JsonNode was = l.path("judge_parts");
                Set<String> keys = new TreeSet<>();
                was.fieldNames().forEachRemaining(keys::add);
                now.fieldNames().forEachRemaining(keys::add);
                for (String k : keys) {
                    if (!Objects.equals(was.get(k), now.get(k))) {
                        changed.add(k);
                    }
                }
            }
        }
        for (JsonNode v : jf.verdictLines()) {
            if (!print.equals(v.path("judge").asText())) {
                nv++;
            }
        }
        if (nb + nv > 0) {
            throw new JudgeFiles.Bad("판정 지문이 지금(" + print.substring(0, 12) + ")과 다른 줄이 있다(batches.jsonl " + nb + "줄, verdicts.jsonl " + nv
                    + "줄, 달라진 칸 " + (changed.isEmpty() ? "없음: 요청을 짓는 코드나 SDK가 바뀌었다" : String.join("·", changed)) + "). 판정 설정·지시문·"
                    + "요청을 짓는 코드·SDK를 고쳤으면 판정 폴더(judge)를 다른 곳으로 옮기고 처음부터 다시 판정한다(01 200줄)");
        }
    }

    /** 기다리는 배치 하나: 결과 원문이 없으면 끝나기를 기다려 받는다. 그다음 가른다. */
    private void settle(JudgeFiles jf, Batch b, List<String> stops, List<String> limits)
            throws IOException, InterruptedException, Halt, JudgeFiles.Bad {
        if (!jf.hasResults(b.id())) {
            String last = null;
            while (true) {
                BatchClient.Status st = api("배치 " + b.id() + "의 상태 보기", () -> client.retrieve(b.id()));
                String now = st.processing() + "(" + st.counts() + ")";
                if (!now.equals(last)) {
                    log.println("배치 " + b.id() + ": " + now);
                    last = now;
                }
                if (st.ended()) {
                    break;
                }
                sleeper.sleep(js.pollSeconds() * 1000L);
            }
            byte[] raw = api("배치 " + b.id() + "의 결과 받기", () -> client.results(b.id()));
            try {
                JudgeFiles.lines(raw, "받은 결과");
            } catch (JudgeFiles.Bad e) {
                throw new Halt(Control.Kind.STOP_AND_LOOK, "배치 " + b.id() + ": " + e.getMessage() + ". 결과 원문을 남기지 않았다. 다시 치면 다시 받는다");
            }
            jf.saveResults(b.id(), raw);
        }
        apply(jf, b, jf.readResults(b.id()), true, stops, limits);
    }

    /**
     * 배치 하나의 결과를 custom_id로 짝지어 가른다. fresh(이번에 받아 가르는 것)이면 판정 줄(이미 있는 (실행, 해시)는 건너뜀)과 done 줄을 쓰고,
     * 멈춤과 한도를 stops·limits에 더한다. 이미 다 가른 배치(fresh 아님)는 지난 결과로 history만 채운다.
     */
    private void apply(JudgeFiles jf, Batch b, List<JsonNode> lines, boolean isFresh, List<String> stops, List<String> limits) throws IOException {
        Map<String, Req> byId = new LinkedHashMap<>();
        b.requests().forEach(r -> byId.put(r.customId(), r));
        Set<String> seen = new HashSet<>();
        List<String> unknown = new ArrayList<>();
        List<String> twice = new ArrayList<>();
        Map<String, Integer> counts = new TreeMap<>();
        BigDecimal cost = BigDecimal.ZERO;
        for (JsonNode line : lines) {
            String cid = line.path("custom_id").asText("");
            Req req = byId.get(cid);
            if (req == null) {
                unknown.add(cid);
                continue;
            }
            if (!seen.add(cid)) {
                twice.add(cid);
                continue;
            }
            JsonNode result = line.path("result");
            Classified c = classify(result);
            counts.merge(c.kind().name().toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
            BigDecimal one = BigDecimal.ZERO;
            if ("succeeded".equals(result.path("type").asText())) {
                one = cost(result.path("message"));
                cost = cost.add(one);
            }
            String k = key(req.runId(), req.sha256());
            List<Kind> h = history.computeIfAbsent(k, x -> new ArrayList<>());
            h.add(c.kind());
            if (!isFresh) {
                continue;
            }
            fresh.merge(c.kind(), 1, Integer::sum);
            if (c.kind() == Kind.VERDICT) {
                if (!verdicts.containsKey(k)) {
                    JsonNode m = result.path("message");
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("stage", s.stage());
                    v.put("run_id", req.runId());
                    v.put("record_sha256", req.sha256());
                    v.put("custom_id", req.customId());
                    v.put("judge", print);
                    v.put("batch_id", b.id());
                    v.put("round", b.round());
                    v.put("answered", c.answered());
                    v.put("reason", c.reason());
                    v.put("model", m.path("model").asText());
                    v.put("stop_reason", m.path("stop_reason").asText());
                    v.put("usage", m.path("usage"));
                    v.put("cost_usd", usd(one));
                    jf.appendVerdict(v);
                    verdicts.put(k, Json.M.valueToTree(v));
                }
            } else if (c.kind() == Kind.LIMIT) {
                limits.add(req.runId() + ": " + c.detail());
            } else {
                String why = why(c, req, h);
                if (why != null) {
                    stops.add(req.runId() + ": " + why);
                }
            }
        }
        spent = spent.add(cost);
        if (!isFresh) {
            return;
        }
        spentNow = spentNow.add(cost);
        List<String> missing = b.requests().stream().map(Req::customId).filter(x -> !seen.contains(x)).toList();
        if (!unknown.isEmpty() || !twice.isEmpty() || !missing.isEmpty()) {
            stops.add("배치 " + b.id() + ": 결과가 낸 요청과 맞지 않는다(낸 적 없는 custom_id " + unknown.size() + "개, 결과가 둘인 것 " + twice.size()
                    + "개, 결과가 없는 요청 " + missing.size() + "개). 결과가 없는 요청은 다시 치면 다시 낸다");
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("type", "done");
        d.put("batch_id", b.id());
        d.put("counts", counts);
        d.put("unknown_custom_ids", unknown.size());
        d.put("missing", missing.size());
        d.put("cost_usd", usd(cost));
        jf.appendBatch(d);
    }

    /** 판정이 없는 대상을 한 배치로 낸다. 배치 ID를 받자마자 batches.jsonl에 적는다. */
    private Batch submit(JudgeFiles jf, List<Target> todo, int round) throws IOException, InterruptedException, Halt {
        List<Req> reqs = new ArrayList<>();
        List<BatchCreateParams.Request> body = new ArrayList<>();
        int high = 0;
        for (Target t : todo) {
            boolean raise = history.getOrDefault(key(t.runId(), t.sha256()), List.of()).contains(Kind.MAX_TOKENS);
            long mt = raise ? js.maxTokensRaised() : js.maxTokens();
            if (raise) {
                high++;
            }
            String cid = customId(s.stage(), t.runId());
            reqs.add(new Req(cid, t.runId(), t.sha256(), mt));
            body.add(BatchCreateParams.Request.builder().customId(cid).params(params(js, instructions, t.question(), t.answer(), mt)).build());
        }
        if (body.size() > MAX_REQUESTS) {
            throw new Halt(Control.Kind.STOP_AND_LOOK, "낼 요청이 " + body.size() + "개다. 배치 하나는 많아야 " + MAX_REQUESTS + "개다(나눠 내지 않는다)");
        }
        long bytes = json(body).getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_BYTES) {
            throw new Halt(Control.Kind.STOP_AND_LOOK, "낼 요청이 " + bytes + "바이트다. 배치 하나는 많아야 256MB다(나눠 내지 않는다)");
        }
        BatchClient.Status created = api("배치 만들기(요청 " + body.size() + "개)", () -> client.create(body));
        if (!JudgeFiles.goodBatchId(created.id())) {
            throw new Halt(Control.Kind.STOP_AND_LOOK, "받은 배치 ID의 꼴이 다르다(" + created.id() + "). 적지 않았다. 사람이 본다");
        }
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("type", "batch");
        line.put("batch_id", created.id());
        line.put("round", round);
        line.put("judge", print);
        line.put("judge_parts", parts);
        List<Map<String, Object>> rs = new ArrayList<>();
        for (Req r : reqs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("custom_id", r.customId());
            m.put("run_id", r.runId());
            m.put("record_sha256", r.sha256());
            m.put("max_tokens", r.maxTokens());
            rs.add(m);
        }
        line.put("requests", rs);
        jf.appendBatch(line);
        submitted++;
        requested += reqs.size();
        log.println("배치를 냈다: " + created.id() + "(" + round + "번째, 요청 " + reqs.size() + "개, max_tokens " + js.maxTokens() + " " + (reqs.size() - high)
                + "개·" + js.maxTokensRaised() + " " + high + "개)");
        return new Batch(created.id(), round, List.copyOf(reqs));
    }

    /** 배치 API 부르기. RETRY는 설정의 retry대로 기다려 다시 부른다. 한도는 RESUME_LATER, 그 밖은 멈춤이다. */
    private <T> T api(String what, ApiCall<T> f) throws Halt, InterruptedException {
        for (int n = 1; ; n++) {
            try {
                return f.call();
            } catch (ModelError e) {
                boolean last = e.category() != ModelError.Category.RETRY || n >= s.retry().maxAttempts();
                long wait = last ? 0 : waitMillis(e, n);
                log.println(what + ": " + e.category() + " " + e.status() + " " + e.type() + " " + e.getMessage()
                        + (last ? "" : " (" + n + "번째 시도, " + wait / 1000 + "초 뒤 다시)"));
                if (last) {
                    String err = e.status() + " " + e.type() + " " + e.getMessage();
                    throw switch (e.category()) {
                        case RESUME_LATER -> new Halt(Control.Kind.RESUME_LATER, what + ": 한도나 크레딧(" + err + "). 풀리면 다시 judge한다(낸 배치는 다시 내지 않고 잇는다)");
                        case RETRY -> new Halt(Control.Kind.STOP_AND_LOOK, what + ": 재시도를 다 썼다(" + err + "). 다시 judge하면 잇는다");
                        case STOP_AND_LOOK -> new Halt(Control.Kind.STOP_AND_LOOK, what + ": 모르는 오류(" + err + ")");
                    };
                }
                sleeper.sleep(wait);
            }
        }
    }

    /** RunClient.waitMillis와 같다: retry-after가 있으면 그만큼, 없으면 base × 2^(n−1)초(많아야 max). */
    private long waitMillis(ModelError e, int n) {
        if (e.retryAfterSeconds() != null) {
            return Math.max(0, e.retryAfterSeconds()) * 1000;
        }
        return Math.min((long) s.retry().maxSeconds(), (long) s.retry().baseSeconds() << Math.min(n - 1, 20)) * 1000;
    }
}
