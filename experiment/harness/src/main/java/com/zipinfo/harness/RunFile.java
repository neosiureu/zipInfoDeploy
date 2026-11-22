package com.zipinfo.harness;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 실행 하나의 기록 파일(JSONL, 설계안 10.1). 한 줄이 사건 하나이고 모든 줄에 시각(at, UTC)이 있다. 첫 줄은 머리(type run)다.
 * <ul>
 * <li>머리: 실행 ID, 판, 문항, 회차, 요청 지문과 그 칸, 시스템 글, 도구 정의, 모델 설정, 캐시 정책, 턴 상한, 단가표, 하네스 코드 해시.</li>
 * <li>고리(QuestionLoop)의 사건: first_message, hook, response(토큰 넷·시간·응답 원문), tool, submit, accepted, nudge.
 *     보낸 요청은 머리와 이 사건들로 다시 지을 수 있다(이어 돌리기가 그렇게 대화를 되살린다).</li>
 * <li>하네스의 사건: resume(이어 돌림), attempt(실패한 시도: 갈래·상태·type·기다린 시간), pause(멈춤과 까닭), end(끝난 꼴·턴·글).</li>
 * </ul>
 * 쓰다가 끊겨 마지막 줄이 덜 쓰였으면(줄 끝 LF가 없으면) 그 줄은 없는 것으로 본다(이어 돌릴 때는 잘라 낸다). 그 밖의 줄이 JSON이 아니면 멈춘다.
 */
public final class RunFile {
    public static final Set<String> HARNESS = Set.of("run", "resume", "attempt", "pause", "end");

    /** 되살릴 응답 하나: 응답 원문과 그 요청의 시간. */
    public record Recorded(JsonNode raw, long millis) {}

    private final List<JsonNode> lines;

    private RunFile(List<JsonNode> lines) {
        this.lines = List.copyOf(lines);
    }

    /** cut이면 덜 쓰인 마지막 줄을 파일에서 잘라 낸다. 파일이 없으면 빈 기록이다. */
    public static RunFile read(Path p, boolean cut) throws IOException {
        if (!Files.exists(p)) {
            return new RunFile(List.of());
        }
        byte[] b = Files.readAllBytes(p);
        int end = b.length;
        while (end > 0 && b[end - 1] != '\n') {
            end--;
        }
        if (cut && end < b.length) {
            try (FileChannel ch = FileChannel.open(p, StandardOpenOption.WRITE)) {
                ch.truncate(end);
            }
        }
        List<JsonNode> lines = new ArrayList<>();
        String[] parts = new String(b, 0, end, StandardCharsets.UTF_8).split("\n");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            try {
                lines.add(Json.M.readTree(parts[i]));
            } catch (IOException e) {
                throw new IOException(p.getFileName() + "의 " + (i + 1) + "번째 줄이 JSON이 아니다", e);
            }
        }
        if (!lines.isEmpty() && !"run".equals(lines.get(0).path("type").asText())) {
            throw new IOException(p.getFileName() + "의 첫 줄이 머리(type run)가 아니다");
        }
        return new RunFile(lines);
    }

    /** 머리. 빈 기록이면 null. */
    public JsonNode header() {
        return lines.isEmpty() ? null : lines.get(0);
    }

    public List<JsonNode> events() {
        return lines.isEmpty() ? List.of() : lines.subList(1, lines.size());
    }

    public boolean complete() {
        return end() != null;
    }

    public JsonNode end() {
        for (JsonNode e : events()) {
            if ("end".equals(e.path("type").asText())) {
                return e;
            }
        }
        return null;
    }

    /** 고리의 사건(하네스 사건을 뺀 것), 쓴 차례대로. */
    public List<JsonNode> loopEvents() {
        return events().stream().filter(e -> !HARNESS.contains(e.path("type").asText())).toList();
    }

    public List<Recorded> responses() {
        return loopEvents().stream().filter(e -> "response".equals(e.path("type").asText()))
                .map(e -> new Recorded(e.get("raw"), e.path("millis").asLong())).toList();
    }

    /** 마지막 응답 뒤에 남은 "재시도를 다 써서 멈춤"(같은 요청이 몇 번 그렇게 멈췄나). */
    public List<JsonNode> exhaustedSinceLastResponse() {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode e : events()) {
            String t = e.path("type").asText();
            if (t.equals("response")) {
                out.clear();
            } else if (t.equals("pause") && "retry_exhausted".equals(e.path("reason").asText())) {
                out.add(e);
            }
        }
        return out;
    }

    /** 기록 파일에 한 줄씩 덧붙인다. 줄마다 type 다음에 시각(at)을 넣고 바로 내보낸다. */
    static final class Writer implements Closeable {
        private final BufferedWriter w;

        Writer(Path p) throws IOException {
            this.w = Files.newBufferedWriter(p, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        synchronized void write(Map<String, Object> e) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", e.get("type"));
            m.put("at", Instant.now().toString());
            e.forEach((k, v) -> {
                if (!k.equals("type") && !k.equals("at")) {
                    m.put(k, v);
                }
            });
            try {
                w.write(Json.write(m));
                w.write('\n');
                w.flush();
            } catch (IOException x) {
                throw new UncheckedIOException(x);
            }
        }

        @Override
        public void close() throws IOException {
            w.close();
        }
    }
}
