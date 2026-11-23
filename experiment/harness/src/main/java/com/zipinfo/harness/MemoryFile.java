package com.zipinfo.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.zipinfo.ai.data.ListingMeta;
import com.zipinfo.ai.loop.RunRecord;
import com.zipinfo.ai.reflex.Memory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * ⑤의 얼린 기억·좋아요 파일(순서 6b, 설계안 7.6·10.3·11절). 시운전이 끝나면 memory 명령으로 한 번 만들고, 그 뒤에는 읽기만 한다.
 * <p>채울 실행(상대와 정한 잣대, b판)
 * <ul>
 * <li>판은 하나다(v0는 c09 전부, 명령에 적는다). 설정의 나눔은 연습이고 files.memory는 null이다(채울 실행은 빈 기억으로 돈다).</li>
 * <li>설정의 계획(select·split)에 든 그 판의 실행 모두를 쓴다. 그 가운데 하나라도 끝나지 않았거나(기록에 end가 없음) 기록이 없으면,
 *     또는 계획 밖의 그 판 기록이 폴더에 있으면 만들지 않고 수를 찍고 멈춘다. 그래서 쓴 실행은 늘 "계획에 든 그 판의 실행 모두"다.
 *     이어 돌려 끝난 실행도 끝난 실행이다. 실행 ID가 같은 기록이 둘이어도 멈춘다.</li>
 * <li>그 판의 기록 가운데 요청 지문이 지금과 다른 것이 하나라도 있으면 만들지 않는다(plan·run과 같은 잣대). 건너뛰면 코드를 고친 뒤
 *     모든 기록이 달라졌을 때 빈 기억이 말없이 만들어진다. 읽지 못한 기록, 지금 파일에 없는 판·문항, 나눔이 다른 기록이 있어도 만들지 않는다.
 *     다른 판의 지문이 다른 기록은 막지 않는다(기억에 쓰지 않는다).</li>
 * <li>쓴 실행 ID(글자 순)와 그 수를 파일에 적는다. 사전 등록에도 같은 것을 적는다.</li>
 * </ul>
 * <p>파일: JSON, UTF-8, 줄 끝 LF, 끝에 줄바꿈 하나. 칸 차례가 정해져 있다. 항목은 시군구 글자 순, 그 안은 Memory의 정한 차례이고 한 줄에 하나다.
 * 좋아요는 카드 ID 순이다. 같은 기록이면 어느 JVM·OS에서 만들어도 바이트까지 같다(관문 3에서 해시를 다시 만들 수 있게).
 * 이미 있는 파일은 덮어쓰지 않는다(얼린 것은 고치지 않는다, 설계안 7.6).
 * <p>⑤가 든 판의 요청 지문 memory 칸은 이 파일의 SHA-256(줄 끝 LF로 맞춤)이다(Inputs.digests). 채우기 전은 EMPTY다.
 */
final class MemoryFile {
    static final String FORMAT = "zipinfo-memory-v1";

    /** 얼린 파일의 머리: 단계, 판, 나눔, 쓴 실행 ID(글자 순). */
    record Source(String stage, String variant, String split, List<String> runIds) {}

    /** 고른 채울 실행: 머리, 실행(실행 ID 차례). 계획에 든 그 판의 실행 모두다. */
    record Picked(Source source, List<Memory.Run> runs) {}

    record Frozen(Source source, Memory memory) {}

    /** 얼린 결과: 파일 글, 그 SHA-256(줄 끝 LF로 맞춤, 지문의 memory 칸과 같다), 고른 것, 기억. */
    record Result(String text, String sha256, Picked picked, Memory memory) {}

    private MemoryFile() {}

    /** 채울 실행을 고른다. 고를 수 없으면 까닭을 적은 IllegalArgumentException이다. */
    static Picked pick(Runner r, Inputs in, RunSettings s, String variant) throws IOException {
        if (!RunSettings.PRACTICE.equals(s.split())) {
            throw new IllegalArgumentException("채울 실행은 연습 문항만이다. 설정의 split이 " + s.split() + "이다");
        }
        if (s.files().memory() != null) {
            throw new IllegalArgumentException("채울 실행은 빈 기억으로 돈 실행이다. 설정의 files.memory가 null이어야 한다");
        }
        if (in.variants().stream().noneMatch(v -> v.id().equals(variant))) {
            throw new IllegalArgumentException("판 설정 파일에 없는 판이다: " + variant);
        }
        Runner.Status st = r.status();
        if (!st.problems().isEmpty()) {
            throw new IllegalArgumentException("기록 문제가 있어 얼리지 않는다: " + String.join("; ", st.problems()));
        }
        List<String> stale = st.stale().stream().filter(x -> x.variant().equals(variant))
                .map(x -> x.file() + "(달라진 칸 " + String.join("·", x.changed()) + ")").toList();
        if (!stale.isEmpty()) {
            throw new IllegalArgumentException("판 " + variant + "의 기록 가운데 요청 지문이 지금과 다른 것이 있어 얼리지 않는다: " + String.join(", ", stale));
        }
        List<String> planned = st.plan().stream().filter(x -> x.variant().id().equals(variant)).map(x -> x.id()).sorted().toList();
        if (planned.isEmpty()) {
            throw new IllegalArgumentException("이 설정의 계획에 판 " + variant + "의 실행이 없다");
        }
        Map<String, RunFile> recs = new TreeMap<>();
        for (Path f : records(r.dir())) {
            RunFile rf = RunFile.read(f, false);
            JsonNode h = rf.header();
            if (h == null || !variant.equals(h.path("variant").path("id").asText())) {
                continue;
            }
            if (recs.put(h.path("run_id").asText(), rf) != null) {
                throw new IllegalArgumentException("실행 ID가 같은 기록이 둘이다: " + h.path("run_id").asText());
            }
        }
        Set<String> inPlan = new HashSet<>(planned);
        List<String> unfinished = planned.stream().filter(id -> recs.containsKey(id) && !recs.get(id).complete()).toList();
        List<String> notStarted = planned.stream().filter(id -> !recs.containsKey(id)).toList();
        List<String> outside = recs.keySet().stream().filter(id -> !inPlan.contains(id)).toList();
        if (!unfinished.isEmpty() || !notStarted.isEmpty() || !outside.isEmpty()) {
            throw new IllegalArgumentException("판 " + variant + ": 계획의 실행 " + planned.size() + "개가 모두 끝나고 계획 밖 기록이 없어야 얼린다. 끝나지 않은 실행 "
                    + unfinished.size() + "개" + some(unfinished) + ", 기록이 없는 실행 " + notStarted.size() + "개" + some(notStarted)
                    + ", 계획 밖 기록 " + outside.size() + "개" + some(outside));
        }
        List<Memory.Run> runs = new ArrayList<>();
        for (String id : planned) {
            RunFile rf = recs.get(id);
            ListingMeta meta = in.metas().get(rf.header().path("question").path("key").asText());
            if (meta == null) {
                throw new IllegalArgumentException(id + ": 문항의 매물이 지금 파일에 없다");
            }
            runs.add(new Memory.Run(meta, RunRecord.Outcome.valueOf(rf.end().path("outcome").asText()), rf.loopEvents()));
        }
        return new Picked(new Source(s.stage(), variant, s.split(), planned), List.copyOf(runs));
    }

    /** 실행 ID 앞 다섯까지(멈출 때 찍는 글). */
    private static String some(List<String> ids) {
        if (ids.isEmpty()) {
            return "";
        }
        return "(" + String.join(" ", ids.subList(0, Math.min(5, ids.size()))) + (ids.size() > 5 ? " …" : "") + ")";
    }

    private static List<Path> records(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> st = Files.list(dir)) {
            return st.filter(p -> p.getFileName().toString().endsWith(".jsonl")).sorted().toList();
        }
    }

    /** 채울 실행을 골라 기억을 만들고 out에 얼린다. out이 이미 있으면 쓰지 않는다. */
    static Result freeze(Runner r, Inputs in, RunSettings s, String variant, Path out) throws IOException {
        Picked p = pick(r, in, s, variant);
        Memory m = Memory.build(p.runs());
        String text = text(p.source(), m);
        if (Files.exists(out)) {
            throw new IllegalArgumentException("얼릴 파일이 이미 있다. 얼린 것은 덮어쓰지 않는다: " + out.getFileName());
        }
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.writeString(out, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return new Result(text, Fingerprint.file(out), p, m);
    }

    /** 얼린 파일의 글. 같은 머리·기억이면 늘 같은 글이다. */
    static String text(Source src, Memory m) {
        List<String> items = new ArrayList<>();
        m.items().forEach((area, its) -> its.forEach((i, n) -> {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("area", area);
            o.put("kind", i.kind());
            o.put("blocked", i.blocked());
            o.put("solved", i.solved());
            o.put("api", i.api());
            o.put("runs", n);
            items.add(Json.write(o));
        }));
        List<String> likes = new ArrayList<>();
        m.likes().forEach((c, n) -> {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("card", c);
            o.put("likes", n);
            likes.add(Json.write(o));
        });
        return "{\n"
                + "  \"format\": " + Json.write(FORMAT) + ",\n"
                + "  \"stage\": " + Json.write(src.stage()) + ",\n"
                + "  \"variant\": " + Json.write(src.variant()) + ",\n"
                + "  \"split\": " + Json.write(src.split()) + ",\n"
                + "  \"run_count\": " + src.runIds().size() + ",\n"
                + "  \"run_ids\": " + array(src.runIds().stream().map(Json::write).toList()) + ",\n"
                + "  \"items\": " + array(items) + ",\n"
                + "  \"likes\": " + array(likes) + "\n"
                + "}\n";
    }

    private static String array(List<String> xs) {
        return xs.isEmpty() ? "[]" : "[\n    " + String.join(",\n    ", xs) + "\n  ]";
    }

    /** 얼린 파일을 읽는다. 칸이 빠지거나 더 있거나 값이 틀리면 멈춘다. 줄 끝이 CRLF여도 읽는다. */
    static Frozen read(Path p) throws IOException {
        String where = "얼린 기억 파일(" + p.getFileName() + ")";
        JsonNode n = Json.read(Files.readString(p, StandardCharsets.UTF_8), where);
        Json.fields(n, where, "format", "stage", "variant", "split", "run_count", "run_ids", "items", "likes");
        if (!FORMAT.equals(n.path("format").asText())) {
            throw new IllegalArgumentException(where + "의 format이 " + FORMAT + "가 아니다");
        }
        JsonNode ids = n.get("run_ids");
        List<String> runIds = new ArrayList<>();
        if (ids == null || !ids.isArray()) {
            throw new IllegalArgumentException(where + "의 run_ids는 목록이다");
        }
        for (JsonNode x : ids) {
            if (!x.isTextual() || x.asText().isBlank() || (!runIds.isEmpty() && runIds.get(runIds.size() - 1).compareTo(x.asText()) >= 0)) {
                throw new IllegalArgumentException(where + "의 run_ids는 겹치지 않는 글을 글자 순으로 적는다");
            }
            runIds.add(x.asText());
        }
        if (Json.integer(n, "run_count", where, 1, 1_000_000) != runIds.size()) {
            throw new IllegalArgumentException(where + "의 run_count가 run_ids의 수와 다르다");
        }
        Map<String, Map<Memory.Item, Integer>> items = new TreeMap<>();
        for (JsonNode x : list(n, "items", where)) {
            Json.fields(x, where + "의 항목", "area", "kind", "blocked", "solved", "api", "runs");
            JsonNode api = x.get("api");
            if (!api.isTextual()) {
                throw new IllegalArgumentException(where + "의 항목 api는 글이다(지역 코드 길이면 빈 글)");
            }
            Memory.Item i = new Memory.Item(Json.text(x, "kind", where), Json.text(x, "blocked", where), Json.text(x, "solved", where), api.asText());
            if (items.computeIfAbsent(Json.text(x, "area", where), k -> new LinkedHashMap<>()).put(i, Json.integer(x, "runs", where, 1, 1_000_000)) != null) {
                throw new IllegalArgumentException(where + "에 같은 항목이 둘이다: " + i);
            }
        }
        Map<String, Integer> likes = new TreeMap<>();
        for (JsonNode x : list(n, "likes", where)) {
            Json.fields(x, where + "의 좋아요", "card", "likes");
            if (likes.put(Json.text(x, "card", where), Json.integer(x, "likes", where, 1, 1_000_000)) != null) {
                throw new IllegalArgumentException(where + "에 같은 카드가 둘이다");
            }
        }
        Source src = new Source(Json.text(n, "stage", where), Json.text(n, "variant", where), Json.text(n, "split", where), List.copyOf(runIds));
        return new Frozen(src, Memory.of(items, likes));
    }

    private static JsonNode list(JsonNode n, String field, String where) {
        JsonNode x = n.get(field);
        if (x == null || !x.isArray()) {
            throw new IllegalArgumentException(where + "의 " + field + "는 목록이다");
        }
        return x;
    }

    /** memory 명령이 찍는 글. shown은 명령에 적은 경로다. */
    static String report(Result res, String shown) {
        Picked p = res.picked();
        int items = res.memory().items().values().stream().mapToInt(Map::size).sum();
        return "얼린 기억: 단계 " + p.source().stage() + ", 판 " + p.source().variant() + ", 계획의 실행 " + p.source().runIds().size() + "개를 모두 씀\n"
                + "  시군구 " + res.memory().items().size() + "가지, 항목 " + items + "개, 좋아요를 받은 카드 " + res.memory().likes().size() + "장\n"
                + "  파일 " + shown + ", SHA-256(줄 끝 LF로 맞춤) " + res.sha256() + "\n";
    }
}
