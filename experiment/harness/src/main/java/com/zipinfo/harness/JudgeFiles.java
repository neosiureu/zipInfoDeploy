package com.zipinfo.harness;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 판정 폴더(<기록 폴더>/judge, 순서 6c 설계 5절)의 파일. status·Summary·MemoryFile은 기록 폴더 바로 아래 *.jsonl만 보므로 이 폴더를 읽지 않는다.
 * <ul>
 * <li>batches.jsonl: 낸 배치마다 {"type":"batch",…}, 결과를 다 가른 뒤 {"type":"done",…}.</li>
 * <li>results/(배치 ID).jsonl: 받은 결과 원문. 임시 파일에 받은 뒤 이름을 바꾼다. 모든 줄이 JSON일 때만 남긴다.</li>
 * <li>verdicts.jsonl: 판정마다 한 줄(순서 8 채점기가 읽는다).</li>
 * </ul>
 * 줄 규칙은 기록 파일(RunFile)과 같다. 줄 끝 LF가 없는 마지막 줄은 없는 것으로 보고 열 때 잘라 낸다(판정은 단계의 .lock을 잡고 연다).
 * 그 밖의 줄이 JSON이 아니면 Bad다(판정이 멈추고 3으로 끝난다).
 */
final class JudgeFiles {
    static final String BATCHES = "batches.jsonl";
    static final String VERDICTS = "verdicts.jsonl";
    private static final Pattern BATCH_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    /** 판정 파일이 줄 규칙에 맞지 않는다. */
    static final class Bad extends Exception {
        Bad(String message) {
            super(message);
        }
    }

    private final Path dir;
    private final List<JsonNode> batchLines;
    private final List<JsonNode> verdictLines;

    private JudgeFiles(Path dir, List<JsonNode> batchLines, List<JsonNode> verdictLines) {
        this.dir = dir;
        this.batchLines = batchLines;
        this.verdictLines = verdictLines;
    }

    static JudgeFiles open(Path dir) throws IOException, Bad {
        return new JudgeFiles(dir, read(dir.resolve(BATCHES)), read(dir.resolve(VERDICTS)));
    }

    /** 덜 쓴 마지막 줄은 잘라 낸다. 파일이 없으면 빈 목록. */
    private static List<JsonNode> read(Path p) throws IOException, Bad {
        List<JsonNode> out = new ArrayList<>();
        if (!Files.exists(p)) {
            return out;
        }
        byte[] b = Files.readAllBytes(p);
        int end = b.length;
        while (end > 0 && b[end - 1] != '\n') {
            end--;
        }
        if (end < b.length) {
            try (FileChannel ch = FileChannel.open(p, StandardOpenOption.WRITE)) {
                ch.truncate(end);
            }
        }
        String[] parts = new String(b, 0, end, StandardCharsets.UTF_8).split("\n");
        for (int i = 0; i < parts.length; i++) {
            String line = parts[i].endsWith("\r") ? parts[i].substring(0, parts[i].length() - 1) : parts[i];
            if (line.isEmpty()) {
                continue;
            }
            try {
                JsonNode n = Json.M.readTree(line);
                if (n == null || !n.isObject()) {
                    throw new IOException("객체가 아니다");
                }
                out.add(n);
            } catch (IOException e) {
                throw new Bad(p.getFileName() + "의 " + (i + 1) + "번째 줄이 JSON 객체가 아니다");
            }
        }
        return out;
    }

    List<JsonNode> batchLines() {
        return batchLines;
    }

    List<JsonNode> verdictLines() {
        return verdictLines;
    }

    static boolean goodBatchId(String id) {
        return id != null && BATCH_ID.matcher(id).matches();
    }

    private Path results(String batchId) {
        if (!goodBatchId(batchId)) {
            throw new IllegalStateException("배치 ID 꼴이 아니다: " + batchId);
        }
        return dir.resolve("results").resolve(batchId + ".jsonl");
    }

    boolean hasResults(String batchId) {
        return Files.exists(results(batchId));
    }

    /** 결과 원문의 줄(빈 줄은 뺀다). 받을 때 모든 줄이 JSON인지 보았으니 여기서 어긋나면 사람이 고친 것이다. */
    List<JsonNode> readResults(String batchId) throws IOException, Bad {
        return lines(Files.readAllBytes(results(batchId)), "results/" + batchId + ".jsonl");
    }

    /** 결과 원문 바이트를 줄로. 줄이 JSON 객체가 아니면 Bad. */
    static List<JsonNode> lines(byte[] raw, String what) throws Bad {
        List<JsonNode> out = new ArrayList<>();
        String[] parts = new String(raw, StandardCharsets.UTF_8).split("\n");
        for (int i = 0; i < parts.length; i++) {
            String line = parts[i].endsWith("\r") ? parts[i].substring(0, parts[i].length() - 1) : parts[i];
            if (line.isBlank()) {
                continue;
            }
            try {
                JsonNode n = Json.M.readTree(line);
                if (n == null || !n.isObject()) {
                    throw new IOException("객체가 아니다");
                }
                out.add(n);
            } catch (IOException e) {
                throw new Bad(what + "의 " + (i + 1) + "번째 줄이 JSON 객체가 아니다");
            }
        }
        return out;
    }

    /** 받은 결과 원문을 임시 파일에 쓴 뒤 이름을 바꾼다. */
    void saveResults(String batchId, byte[] raw) throws IOException {
        Path f = results(batchId);
        Files.createDirectories(f.getParent());
        Path tmp = f.resolveSibling(batchId + ".jsonl.tmp");
        Files.write(tmp, raw);
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    void appendBatch(Map<String, Object> line) throws IOException {
        batchLines.add(append(dir.resolve(BATCHES), line, true));
    }

    void appendVerdict(Map<String, Object> line) throws IOException {
        verdictLines.add(append(dir.resolve(VERDICTS), line, false));
    }

    /** 한 줄을 덧붙이고 바로 내보낸다. stamp면 type 다음에 시각(at, UTC)을 넣는다. */
    private static JsonNode append(Path p, Map<String, Object> line, boolean stamp) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        if (stamp) {
            m.put("type", line.get("type"));
            m.put("at", Instant.now().toString());
        }
        line.forEach((k, v) -> {
            if (!stamp || !k.equals("type") && !k.equals("at")) {
                m.put(k, v);
            }
        });
        String text = Json.write(m);
        Files.createDirectories(p.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(text);
            w.write('\n');
        }
        return Json.M.readTree(text);
    }
}
