package com.zipinfo.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.zipinfo.ai.loop.RunRecord;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 단계 하나의 기록을 판마다 모은다(순서 6a). 시운전에서 고를 것을 보는 숫자다(설계안 10.3).
 * <ul>
 * <li>계획·끝·남음(지금 설정의 계획 기준), 끝난 꼴(outcome)마다 수, refusal 범주마다 수(01 207줄)</li>
 * <li>도구 오류 코드마다 수(장치 거절 REJECTED, 주소 SNAPSHOT_MISS 등. 설계안 10.3 "SNAPSHOT_MISS가 얼마나 나는지")</li>
 * <li>요청 수, 요청당 최대 프롬프트 토큰(입력 + 캐시 읽기 + 캐시 쓰기)과 10만을 넘은 요청의 수·비율(01 335줄)</li>
 * <li>토큰 넷의 합, 비용 캐시 있음·없음(01 342줄, 지금 설정의 단가표로)</li>
 * <li>끝난 실행의 평균 소요시간(성공한 요청과 도구 실행만, 01 213줄)과 평균 턴</li>
 * <li>실패한 시도 수, 멈춤 까닭마다 수</li>
 * </ul>
 * 끝난 꼴·토큰·비용은 폴더에 있는 그 판의 기록 모두를 센다. 문항 글이나 답 글은 넣지 않는다(ID와 수만).
 */
public final class Summary {
    static final long LINE = 100_000;

    /** 판 하나(또는 합)의 줄. */
    public static final class Row {
        public final String variant;
        public final String name;
        public int planned;
        public int done;
        public final Map<String, Integer> outcomes = new TreeMap<>();
        public final Map<String, Integer> refusals = new TreeMap<>();
        public final Map<String, Integer> toolErrors = new TreeMap<>();
        public final Map<String, Integer> pauses = new TreeMap<>();
        public int attempts;
        public long requests;
        public long maxPrompt;
        public long over;
        public long input;
        public long cacheRead;
        public long cacheWrite;
        public long output;
        public BigDecimal withCache = BigDecimal.ZERO;
        public BigDecimal withoutCache = BigDecimal.ZERO;
        public int ended;
        public double millis;
        public long turns;

        Row(String variant, String name) {
            this.variant = variant;
            this.name = name;
        }

        void add(Row o) {
            planned += o.planned;
            done += o.done;
            o.outcomes.forEach((k, v) -> outcomes.merge(k, v, Integer::sum));
            o.refusals.forEach((k, v) -> refusals.merge(k, v, Integer::sum));
            o.toolErrors.forEach((k, v) -> toolErrors.merge(k, v, Integer::sum));
            o.pauses.forEach((k, v) -> pauses.merge(k, v, Integer::sum));
            attempts += o.attempts;
            requests += o.requests;
            maxPrompt = Math.max(maxPrompt, o.maxPrompt);
            over += o.over;
            input += o.input;
            cacheRead += o.cacheRead;
            cacheWrite += o.cacheWrite;
            output += o.output;
            withCache = withCache.add(o.withCache);
            withoutCache = withoutCache.add(o.withoutCache);
            ended += o.ended;
            millis += o.millis;
            turns += o.turns;
        }
    }

    private Summary() {}

    /** 판 차례(판 설정 파일)대로, 마지막 줄은 합. */
    public static List<Row> rows(Runner.Status st, RunSettings s, List<com.zipinfo.ai.reflex.Variant> variants, Path dir) throws IOException {
        Map<String, Row> by = new LinkedHashMap<>();
        for (var v : variants) {
            by.put(v.id(), new Row(v.id(), v.name()));
        }
        for (Plan.RunSpec r : st.plan()) {
            Row row = by.get(r.variant().id());
            row.planned++;
            if (st.done().contains(r.id())) {
                row.done++;
            }
        }
        List<Path> files = List.of();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> x = Files.list(dir)) {
                files = x.filter(p -> p.getFileName().toString().endsWith(".jsonl")).sorted().toList();
            }
        }
        for (Path f : files) {
            RunFile rf = RunFile.read(f, false);
            JsonNode h = rf.header();
            if (h == null) {
                continue;
            }
            Row row = by.get(h.path("variant").path("id").asText());
            if (row == null) {
                continue;
            }
            String model = h.path("variant").path("model").asText();
            String ttl = h.path("cache").path("ttl").asText("5m");
            double runMillis = 0;
            for (JsonNode e : rf.events()) {
                switch (e.path("type").asText()) {
                    case "response" -> {
                        Prices.Use u = Prices.use(e, ttl);
                        row.requests++;
                        row.maxPrompt = Math.max(row.maxPrompt, u.prompt());
                        if (u.prompt() > LINE) {
                            row.over++;
                        }
                        row.input += u.input();
                        row.cacheRead += u.cacheRead();
                        row.cacheWrite += u.cacheWrite();
                        row.output += u.output();
                        row.withCache = row.withCache.add(s.prices().withCache(model, u));
                        row.withoutCache = row.withoutCache.add(s.prices().withoutCache(model, u));
                        if ("refusal".equals(e.path("stop").asText())) {
                            String c = e.path("refusal_category").asText();
                            row.refusals.merge(c.isEmpty() ? "(없음)" : c, 1, Integer::sum);
                        }
                        runMillis += e.path("millis").asDouble();
                    }
                    case "tool" -> {
                        runMillis += e.path("millis").asDouble();
                        if (e.path("is_error").asBoolean()) {
                            String c = e.path("error_code").asText();
                            row.toolErrors.merge(c.isEmpty() ? "(없음)" : c, 1, Integer::sum);
                        }
                    }
                    case "accepted" -> runMillis += e.path("millis").asDouble();
                    case "attempt" -> row.attempts++;
                    case "pause" -> row.pauses.merge(e.path("reason").asText(), 1, Integer::sum);
                    case "end" -> {
                        row.outcomes.merge(e.path("outcome").asText(), 1, Integer::sum);
                        row.ended++;
                        row.turns += e.path("turns").asLong();
                    }
                    default -> { }
                }
            }
            if (rf.complete()) {
                row.millis += runMillis;
            }
        }
        List<Row> out = new ArrayList<>(by.values());
        Row total = new Row("합", "");
        out.forEach(total::add);
        out.add(total);
        return out;
    }

    public static String format(List<Row> rows) {
        List<String> outcomes = Stream.of(RunRecord.Outcome.values()).map(Enum::name).toList();
        List<String> head = new ArrayList<>(List.of("판", "이름", "계획", "끝", "남음"));
        head.addAll(outcomes);
        head.addAll(List.of("refusal", "도구 오류", "요청", "최대 프롬프트", "10만 넘음", "10만 넘음(%)", "input", "cache_read", "cache_write", "output",
                "비용 캐시 있음($)", "비용 캐시 없음($)", "평균 소요(초)", "평균 턴", "실패한 시도", "멈춤"));
        StringBuilder sb = new StringBuilder(String.join("\t", head)).append('\n');
        for (Row r : rows) {
            List<String> c = new ArrayList<>(List.of(r.variant, r.name, "" + r.planned, "" + r.done, "" + (r.planned - r.done)));
            for (String o : outcomes) {
                c.add("" + r.outcomes.getOrDefault(o, 0));
            }
            c.add(join(r.refusals));
            c.add(join(r.toolErrors));
            c.add("" + r.requests);
            c.add("" + r.maxPrompt);
            c.add("" + r.over);
            c.add(r.requests == 0 ? "" : BigDecimal.valueOf(r.over * 100.0 / r.requests).setScale(1, RoundingMode.HALF_UP).toPlainString());
            c.addAll(List.of("" + r.input, "" + r.cacheRead, "" + r.cacheWrite, "" + r.output));
            c.add(r.withCache.setScale(4, RoundingMode.HALF_UP).toPlainString());
            c.add(r.withoutCache.setScale(4, RoundingMode.HALF_UP).toPlainString());
            c.add(r.ended == 0 ? "" : BigDecimal.valueOf(r.millis / 1000.0 / r.ended).setScale(1, RoundingMode.HALF_UP).toPlainString());
            c.add(r.ended == 0 ? "" : BigDecimal.valueOf((double) r.turns / r.ended).setScale(1, RoundingMode.HALF_UP).toPlainString());
            c.add("" + r.attempts);
            c.add(join(r.pauses));
            sb.append(String.join("\t", c)).append('\n');
        }
        return sb.toString();
    }

    private static String join(Map<String, Integer> m) {
        return m.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(";"));
    }
}
