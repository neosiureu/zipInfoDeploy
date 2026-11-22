package com.zipinfo.harness;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 단가표(01 333·342·349줄, 순서 6a). 100만 토큰당 US$이고, 받은 날을 적는다. 기록 머리에도 그대로 남긴다.
 * - 단은 요청마다 고른다. 그 요청의 프롬프트 토큰(입력 + 캐시 읽기 + 캐시 쓰기)이 단의 상한 이하이면 그 단이다. 마지막 단은 상한이 없다.
 *   그래서 Haiku 5.5는 10만을 넘는 요청 하나가 통째로 비싼 단으로 매겨진다(01 333줄).
 * - 캐시 있음: 입력·캐시 읽기·캐시 쓰기(5분·1시간)·출력을 각 단가로 더한다.
 * - 캐시 없음(01 342줄): 프롬프트 토큰 모두를 입력 단가로, 출력을 출력 단가로 더한다. 단은 같은 프롬프트 길이로 고른다.
 *   실행 순서와 무관한 비교 기준이다.
 * - batch_factor: 판정 Batch(6c)의 할인 배수(01 349줄, 절반).
 */
public record Prices(String date, BigDecimal batchFactor, Map<String, List<Tier>> models) {
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    /** upToPromptTokens가 null이면 위 끝이 없다. */
    public record Tier(Long upToPromptTokens, BigDecimal input, BigDecimal output, BigDecimal cacheRead, BigDecimal cacheWrite5m,
                       BigDecimal cacheWrite1h) {}

    /** 요청 하나의 토큰. 캐시 쓰기는 유지 시간마다 나눈다. */
    public record Use(long input, long cacheRead, long write5m, long write1h, long output) {
        public long cacheWrite() {
            return write5m + write1h;
        }

        public long prompt() {
            return input + cacheRead + cacheWrite();
        }
    }

    public static Prices parse(JsonNode n) {
        Json.fields(n, "prices", "date", "batch_factor", "models");
        String date = Json.text(n, "date", "prices");
        if (!DATE.matcher(date).matches()) {
            throw new IllegalArgumentException("prices.date는 2026-11-22 꼴이다");
        }
        BigDecimal batch = Json.positive(n, "batch_factor", "prices");
        if (batch.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("prices.batch_factor는 1 이하다");
        }
        JsonNode ms = n.get("models");
        if (ms == null || !ms.isObject() || ms.isEmpty()) {
            throw new IllegalArgumentException("prices.models가 비었다");
        }
        Map<String, List<Tier>> models = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : ms.properties()) {
            String where = "prices.models." + e.getKey();
            if (!e.getValue().isArray() || e.getValue().isEmpty()) {
                throw new IllegalArgumentException(where + "는 비지 않은 단 목록이다");
            }
            List<Tier> tiers = new ArrayList<>();
            for (JsonNode t : e.getValue()) {
                Json.fields(t, where, "up_to_prompt_tokens", "input", "output", "cache_read", "cache_write_5m", "cache_write_1h");
                JsonNode up = t.get("up_to_prompt_tokens");
                Long upTo = null;
                if (!up.isNull()) {
                    if (!up.isIntegralNumber() || up.asLong() <= 0) {
                        throw new IllegalArgumentException(where + ".up_to_prompt_tokens는 양의 정수나 null이다");
                    }
                    upTo = up.asLong();
                }
                tiers.add(new Tier(upTo, Json.positive(t, "input", where), Json.positive(t, "output", where), Json.positive(t, "cache_read", where),
                        Json.positive(t, "cache_write_5m", where), Json.positive(t, "cache_write_1h", where)));
            }
            for (int i = 0; i < tiers.size(); i++) {
                Long u = tiers.get(i).upToPromptTokens();
                boolean last = i == tiers.size() - 1;
                if (last != (u == null) || (i > 0 && !last && u <= tiers.get(i - 1).upToPromptTokens())) {
                    throw new IllegalArgumentException(where + ": 단은 상한이 커지는 차례이고, 마지막 단만 상한이 없다");
                }
            }
            models.put(e.getKey(), List.copyOf(tiers));
        }
        return new Prices(date, batch, Map.copyOf(models));
    }

    public Tier tier(String model, long promptTokens) {
        List<Tier> tiers = models.get(model);
        if (tiers == null) {
            throw new IllegalArgumentException("단가표에 없는 모델이다: " + model);
        }
        for (Tier t : tiers) {
            if (t.upToPromptTokens() == null || promptTokens <= t.upToPromptTokens()) {
                return t;
            }
        }
        throw new IllegalStateException("마지막 단에 상한이 있다");
    }

    public BigDecimal withCache(String model, Use u) {
        Tier t = tier(model, u.prompt());
        return times(u.input(), t.input()).add(times(u.cacheRead(), t.cacheRead())).add(times(u.write5m(), t.cacheWrite5m()))
                .add(times(u.write1h(), t.cacheWrite1h())).add(times(u.output(), t.output())).divide(MILLION);
    }

    public BigDecimal withoutCache(String model, Use u) {
        Tier t = tier(model, u.prompt());
        return times(u.prompt(), t.input()).add(times(u.output(), t.output())).divide(MILLION);
    }

    private static BigDecimal times(long tokens, BigDecimal perMillion) {
        return perMillion.multiply(BigDecimal.valueOf(tokens));
    }

    /**
     * 기록의 response 사건 하나의 토큰. 캐시 쓰기의 5분·1시간 나눔은 응답 원문의 usage.cache_creation에서 읽는다.
     * 그것이 없거나 합이 맞지 않으면 실행의 유지 시간(ttl) 하나로 본다.
     */
    public static Use use(JsonNode response, String ttl) {
        return use(response.path("input_tokens").asLong(), response.path("cache_read_tokens").asLong(), response.path("cache_write_tokens").asLong(),
                response.path("output_tokens").asLong(), response.path("raw"), ttl);
    }

    /** 토큰 넷과 응답 원문(raw)으로. */
    public static Use use(long in, long read, long write, long out, JsonNode raw, String ttl) {
        JsonNode cc = raw == null ? null : raw.path("usage").path("cache_creation");
        if (cc != null && cc.isObject()) {
            long w5 = cc.path("ephemeral_5m_input_tokens").asLong(0);
            long w1 = cc.path("ephemeral_1h_input_tokens").asLong(0);
            if (w5 + w1 == write) {
                return new Use(in, read, w5, w1, out);
            }
        }
        return "1h".equals(ttl) ? new Use(in, read, 0, write, out) : new Use(in, read, write, 0, out);
    }

    /** 기록 머리에 남길 꼴(설정 파일과 같은 칸). */
    public Map<String, Object> toJson() {
        Map<String, Object> ms = new LinkedHashMap<>();
        models.keySet().stream().sorted().forEach(m -> {
            List<Map<String, Object>> tiers = new ArrayList<>();
            for (Tier t : models.get(m)) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("up_to_prompt_tokens", t.upToPromptTokens());
                x.put("input", t.input());
                x.put("output", t.output());
                x.put("cache_read", t.cacheRead());
                x.put("cache_write_5m", t.cacheWrite5m());
                x.put("cache_write_1h", t.cacheWrite1h());
                tiers.add(x);
            }
            ms.put(m, tiers);
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", date);
        out.put("batch_factor", batchFactor);
        out.put("models", ms);
        return out;
    }
}
