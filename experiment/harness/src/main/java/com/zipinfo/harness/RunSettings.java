package com.zipinfo.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.zipinfo.ai.model.CachePolicy;
import com.zipinfo.ai.model.ModelSettings;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 하네스 설정 파일(experiment/harness/settings/*.json, 순서 6a). 칸은 정해 둔 것뿐이고, 빠지거나 더 있으면 멈춘다.
 * <ul>
 * <li>stage: 기록 폴더 이름(files.out 아래). 단계(사용자 PC 시험, 시운전, 얼린 뒤 한 번 더, 본실험 주기)마다 따로 둔다.</li>
 * <li>split: 돌릴 문항의 나눔. "연습"이 기본이고, "본시험"은 본실험 설정에서만 쓴다(설계안 557줄: 본시험 문항은 본실험 전까지 모델에 보이지 않는다).</li>
 * <li>files: experiment 폴더에서 본 경로. memory는 null이면 빈 기억(채우기 전: 시운전의 채울 실행), 경로면 memory 명령이 얼린 파일이다(순서 6b).</li>
 * <li>select: 판(variants)·문항(questions)은 "all"이나 ID 목록, 회차(reps)는 1부터의 정수 목록(01 343줄의 두 주기를 나눠 돌릴 수 있게).</li>
 * <li>models: 판 설정 파일의 모델 ID마다 max_tokens·thinking(adaptive·disabled·omit)·effort(null이면 보내지 않음).
 *     max_tokens는 모든 모델에 같아야 한다(01 210줄). 한 모델의 판은 모두 같은 설정이다(01 183줄).</li>
 * <li>max_turns: 턴 상한(모든 판 같음). cache: 캐시 지점과 유지 시간(01 264줄). 이 넷은 시운전에서 고른다(설계안 10.3).</li>
 * <li>retry: 같은 요청을 몇 번까지(max_attempts, 처음 것 포함), 기다림(base_seconds × 2^(n−1), 많아야 max_seconds. retry-after가 있으면 그 값).</li>
 * <li>timeout_seconds: 요청 하나 전체의 시간 초과. concurrency: 동시 실행 수(천천히 올린다, 01 212줄). block_size: 시간 블록 하나의 실행 수(01 341줄).</li>
 * <li>cost_cap_usd: 이 단계 기록 전체의 캐시 있음 비용이 닿으면 멈춘다. prices: 단가표.</li>
 * </ul>
 */
public record RunSettings(String version, String stage, String split, FileSet files, Select select, Map<String, ModelSettings> models,
                          int maxTurns, CachePolicy cache, Retry retry, int timeoutSeconds, int concurrency, int blockSize,
                          BigDecimal costCapUsd, Prices prices) {
    public static final String PRACTICE = "연습";
    public static final String TEST = "본시험";
    private static final Pattern STAGE = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Set<String> EFFORTS = Set.of("low", "medium", "high", "xhigh", "max");

    public record FileSet(String variants, String questions, String cards, String lists, String memory, String out) {}

    /** variants·questions가 비면 전부다. */
    public record Select(List<String> variants, List<String> questions, List<Integer> reps) {}

    public record Retry(int maxAttempts, int baseSeconds, int maxSeconds) {}

    public static RunSettings load(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static RunSettings parse(String text) {
        JsonNode n = Json.read(text, "하네스 설정");
        Json.fields(n, "설정", "version", "stage", "split", "files", "select", "models", "max_turns", "cache", "retry", "timeout_seconds",
                "concurrency", "block_size", "cost_cap_usd", "prices");
        String version = Json.text(n, "version", "설정");
        String stage = Json.text(n, "stage", "설정");
        if (!STAGE.matcher(stage).matches()) {
            throw new IllegalArgumentException("stage는 영문 소문자·숫자·_·- 1~32자다: " + stage);
        }
        String split = Json.text(n, "split", "설정");
        if (!split.equals(PRACTICE) && !split.equals(TEST)) {
            throw new IllegalArgumentException("split은 연습이나 본시험이다: " + split);
        }
        JsonNode f = n.get("files");
        Json.fields(f, "files", "variants", "questions", "cards", "lists", "memory", "out");
        FileSet files = new FileSet(Json.text(f, "variants", "files"), Json.text(f, "questions", "files"), Json.text(f, "cards", "files"),
                Json.text(f, "lists", "files"), Json.textOrNull(f, "memory", "files"), Json.text(f, "out", "files"));
        JsonNode sel = n.get("select");
        Json.fields(sel, "select", "variants", "questions", "reps");
        JsonNode reps = sel.get("reps");
        if (reps == null || !reps.isArray() || reps.isEmpty()) {
            throw new IllegalArgumentException("select.reps는 비지 않은 목록이다");
        }
        List<Integer> repList = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (JsonNode r : reps) {
            if (!r.isIntegralNumber() || !r.canConvertToInt() || r.asInt() < 1 || r.asInt() > 99 || !seen.add(r.asInt())) {
                throw new IllegalArgumentException("select.reps는 겹치지 않는 1~99의 정수다");
            }
            repList.add(r.asInt());
        }
        Select select = new Select(Json.allOrList(sel, "variants", "select"), Json.allOrList(sel, "questions", "select"), List.copyOf(repList));
        Map<String, ModelSettings> models = models(n.get("models"));
        int maxTurns = Json.integer(n, "max_turns", "설정", 1, 200);
        JsonNode c = n.get("cache");
        Json.fields(c, "cache", "system", "conversation", "ttl");
        CachePolicy cache = new CachePolicy(Json.bool(c, "system", "cache"), Json.bool(c, "conversation", "cache"), Json.text(c, "ttl", "cache"));
        JsonNode r = n.get("retry");
        Json.fields(r, "retry", "max_attempts", "base_seconds", "max_seconds");
        Retry retry = new Retry(Json.integer(r, "max_attempts", "retry", 1, 50), Json.integer(r, "base_seconds", "retry", 1, 3600),
                Json.integer(r, "max_seconds", "retry", 1, 3600));
        if (retry.maxSeconds() < retry.baseSeconds()) {
            throw new IllegalArgumentException("retry.max_seconds는 base_seconds 이상이다");
        }
        Prices prices = Prices.parse(n.get("prices"));
        for (String m : models.keySet()) {
            prices.tier(m, 0);
        }
        return new RunSettings(version, stage, split, files, select, models, maxTurns, cache, retry,
                Json.integer(n, "timeout_seconds", "설정", 1, 3600), Json.integer(n, "concurrency", "설정", 1, 16),
                Json.integer(n, "block_size", "설정", 1, 100_000), Json.positive(n, "cost_cap_usd", "설정"), prices);
    }

    private static Map<String, ModelSettings> models(JsonNode ms) {
        if (ms == null || !ms.isObject() || ms.isEmpty()) {
            throw new IllegalArgumentException("models가 비었다");
        }
        Map<String, ModelSettings> out = new LinkedHashMap<>();
        Long maxTokens = null;
        for (Map.Entry<String, JsonNode> e : ms.properties()) {
            String where = "models." + e.getKey();
            JsonNode m = e.getValue();
            Json.fields(m, where, "max_tokens", "thinking", "effort");
            long mt = Json.integer(m, "max_tokens", where, 1, 1_000_000);
            if (maxTokens != null && maxTokens != mt) {
                throw new IllegalArgumentException("max_tokens는 모든 모델에 같다(01 210줄)");
            }
            maxTokens = mt;
            ModelSettings.Thinking thinking = switch (Json.text(m, "thinking", where)) {
                case "adaptive" -> ModelSettings.Thinking.ADAPTIVE;
                case "disabled" -> ModelSettings.Thinking.DISABLED;
                case "omit" -> ModelSettings.Thinking.OMIT;
                default -> throw new IllegalArgumentException(where + ".thinking은 adaptive·disabled·omit 가운데 하나다");
            };
            String effort = Json.textOrNull(m, "effort", where);
            if (effort != null && !EFFORTS.contains(effort)) {
                throw new IllegalArgumentException(where + ".effort는 null이나 low·medium·high·xhigh·max다");
            }
            out.put(e.getKey(), new ModelSettings(e.getKey(), mt, thinking, effort));
        }
        return Map.copyOf(out);
    }

    /** 판의 모델 설정. 없으면 멈춘다. */
    public ModelSettings model(String id) {
        ModelSettings m = models.get(id);
        if (m == null) {
            throw new IllegalArgumentException("설정 models에 없는 모델이다: " + id);
        }
        return m;
    }
}
