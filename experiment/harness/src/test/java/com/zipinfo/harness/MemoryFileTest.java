package com.zipinfo.harness;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.model.AnthropicModelClient;
import com.zipinfo.ai.reflex.Memory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

/**
 * 순서 6b: ⑤ 얼리기(MemoryFile, memory 명령).
 * 채울 실행 고르기(b판: 계획에 든 그 판의 실행이 모두 끝나고 계획 밖 기록이 없을 때만, 그 판의 지문이 다르면 멈춤, 한 판·연습·빈 기억만),
 * 파일 꼴(정한 차례, LF), 읽기, 덮어쓰지 않기,
 * 지문의 memory 칸, 얼린 기억이 ⑤가 든 판의 안내 자리에 닿는 것, 다른 JVM에서 만든 파일과 바이트까지 같은 것.
 * 기록은 가짜 API 서버로 c08(①+⑤)을 돌려 만든다: 옛 코드 41590(0건) → 매물의 코드 41591(표본 4) → 카드 K01 읽기 → 비교 → 제출.
 */
class MemoryFileTest {
    static final String TEXT = String.join("\n",
            "{",
            "  \"format\": \"zipinfo-memory-v1\",",
            "  \"stage\": \"pilot\",",
            "  \"variant\": \"c08\",",
            "  \"split\": \"연습\",",
            "  \"run_count\": 2,",
            "  \"run_ids\": [",
            "    \"c08-q001-r1\",",
            "    \"c08-q002-r1\"",
            "  ],",
            "  \"items\": [",
            "    {\"area\":\"41591\",\"kind\":\"0건\",\"blocked\":\"region_code=41590\",\"solved\":\"region_code=41591\",\"api\":\"\",\"runs\":2}",
            "  ],",
            "  \"likes\": [",
            "    {\"card\":\"K01\",\"likes\":2}",
            "  ]",
            "}",
            "");
    static final String NOTICE = "지난 실행에서 막혔다 풀린 길(같은 시군구, 0건): region_code=41590 → region_code=41591";

    FakeAnthropic api;
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

    /** 이 시험과 MemoryFreezeChild(다른 JVM)가 같은 입력을 짓는다. */
    static Inputs memoryInputs(Path data) throws IOException {
        return Fixture.inputs(data, Fixture.digests("CODE", "CARDS"), List.of(Fixture.Q1, Fixture.Q2),
                List.of(Fixture.B0, Fixture.ONE, Fixture.MEM, Fixture.B1), Memory.EMPTY);
    }

    static RunSettings memorySettings(Consumer<ObjectNode> more) throws IOException {
        return Fixture.settings(n -> {
            Fixture.variants(n, "c08");
            Fixture.questions(n, "q001", "q002");
            more.accept(n);
        });
    }

    Runner runner(Inputs in, RunSettings s, Path dir) {
        return new Runner(in, s, dir, (ms, cache, timeout) -> new AnthropicModelClient("test-key", api.url(), ms, timeout, cache), x -> { },
                new PrintStream(logged, true, StandardCharsets.UTF_8));
    }

    Runner runner(Inputs in, RunSettings s) {
        return runner(in, s, tmp.resolve("runs"));
    }

    static ObjectNode stats(String code) {
        return Fixture.statsInput().put("region_code", code);
    }

    /** c08 실행 하나를 푸는 응답 다섯. */
    static List<FakeAnthropic.Reply> fillReplies() {
        ObjectNode report = Fixture.report();
        ((ObjectNode) report.get("stat_claims").get(0)).put("evidence", "S2.기준");
        List<FakeAnthropic.Reply> out = new ArrayList<>();
        out.add(FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_1", "trade_stats", stats("41590"))), "tool_use", null));
        out.add(FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_2", "trade_stats", stats("41591"))), "tool_use", null));
        out.add(FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_3", "read_card", Fixture.M.createObjectNode().put("card_id", "K01"))), "tool_use", null));
        out.add(FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_4", "compare_prices",
                Fixture.M.createObjectNode().put("listing_evidence", "매물.가격").put("basis_evidence", "S2.기준"))), "tool_use", null));
        out.add(FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_5", "submit_report", report)), "tool_use", null));
        return out;
    }

    void fill() {
        fillReplies().forEach(api::then);
    }

    /** c08로 q001·q002를 한 번씩 끝까지 돌린 기록을 만든다. */
    Inputs twoFillRuns() throws Exception {
        Inputs in = memoryInputs(Files.createDirectories(tmp.resolve("data")));
        fill();
        fill();
        assertNull(runner(in, memorySettings(n -> { })).run().stop());
        return in;
    }

    static String refused(Executable e) {
        return assertThrows(IllegalArgumentException.class, e).getMessage();
    }

    @Test
    void finishedFillRunsFreezeIntoAFixedFileThatReadsBack() throws Exception {
        Inputs in = twoFillRuns();
        RunSettings s = memorySettings(n -> { });
        // 다른 판(c01)의 끝난 실행은 쓰지 않는다
        Fixture.solve().forEach(api::then);
        assertNull(runner(in, Fixture.settings(n -> {
            Fixture.variants(n, "c01");
            Fixture.questions(n, "q001");
        })).run().stop());
        Path out = tmp.resolve("frozen/memory_v0.json");
        MemoryFile.Result res = MemoryFile.freeze(runner(in, s), in, s, "c08", out);
        String text = Files.readString(out, StandardCharsets.UTF_8);
        assertEquals(TEXT, text, "칸 차례가 정해져 있고, 항목·좋아요는 한 줄에 하나, 줄 끝은 LF다");
        assertEquals(TEXT, res.text());
        assertFalse(text.contains("\r"));
        assertEquals(Fingerprint.sha256(TEXT), res.sha256());
        assertEquals("얼린 기억: 단계 pilot, 판 c08, 계획의 실행 2개를 모두 씀\n"
                + "  시군구 1가지, 항목 1개, 좋아요를 받은 카드 1장\n"
                + "  파일 frozen/memory_v0.json, SHA-256(줄 끝 LF로 맞춤) " + res.sha256() + "\n", MemoryFile.report(res, "frozen/memory_v0.json"));
        // 읽기: 같은 머리, 같은 기억. 다시 쓰면 같은 글
        MemoryFile.Frozen f = MemoryFile.read(out);
        assertEquals(new MemoryFile.Source("pilot", "c08", "연습", List.of("c08-q001-r1", "c08-q002-r1")), f.source());
        assertEquals(List.copyOf(res.memory().items().get("41591").entrySet()), List.copyOf(f.memory().items().get("41591").entrySet()));
        assertEquals(Map.of("K01", 2), f.memory().likes());
        assertEquals(TEXT, MemoryFile.text(f.source(), f.memory()));
        // 이미 있으면 덮어쓰지 않는다
        assertTrue(refused(() -> MemoryFile.freeze(runner(in, s), in, s, "c08", out)).contains("덮어쓰지 않는다"));
        assertEquals(TEXT, Files.readString(out, StandardCharsets.UTF_8));
        // 빈 목록의 꼴
        assertEquals("{\n  \"format\": \"zipinfo-memory-v1\",\n  \"stage\": \"pilot\",\n  \"variant\": \"c01\",\n  \"split\": \"연습\",\n  \"run_count\": 1,\n"
                + "  \"run_ids\": [\n    \"c01-q001-r1\"\n  ],\n  \"items\": [],\n  \"likes\": []\n}\n",
                MemoryFile.text(new MemoryFile.Source("pilot", "c01", "연습", List.of("c01-q001-r1")), Memory.EMPTY));
        // 틀린 파일은 읽지 않는다
        Path broken = tmp.resolve("broken.json");
        Files.writeString(broken, TEXT.replace("\"run_count\": 2", "\"run_count\": 3"), StandardCharsets.UTF_8);
        assertTrue(refused(() -> MemoryFile.read(broken)).contains("run_count"));
        Files.writeString(broken, TEXT.replace("\"likes\":2", "\"likes\":0"), StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> MemoryFile.read(broken));
    }

    @Test
    void itFreezesOnlyWhenEveryPlannedRunHasEnded() throws Exception {
        Inputs in = memoryInputs(Files.createDirectories(tmp.resolve("data")));
        RunSettings s = memorySettings(n -> ((ObjectNode) n.get("select")).putArray("reps").add(1).add(2));
        fill();
        List<FakeAnthropic.Reply> second = fillReplies();
        api.then(second.get(0));
        api.then(FakeAnthropic.error(429, "rate_limit_error", "limit", "enforced_spend_limit_reached"));
        assertEquals(Control.Kind.RESUME_LATER, runner(in, s).run().stop().kind());
        Path out = tmp.resolve("m.json");
        assertEquals("판 c08: 계획의 실행 4개가 모두 끝나고 계획 밖 기록이 없어야 얼린다. 끝나지 않은 실행 1개(c08-q002-r1), "
                + "기록이 없는 실행 2개(c08-q001-r2 c08-q002-r2), 계획 밖 기록 0개", refused(() -> MemoryFile.freeze(runner(in, s), in, s, "c08", out)),
                "덜 끝난 시운전은 얼리지 않는다(얼린 파일은 덮어쓰지 않으니 한 번 잘못 치면 남는다)");
        assertFalse(Files.exists(out));
        // 다른 프로세스에서 이어 돌려 모두 끝나면 얼린다(기록의 사건은 한 번씩만 있다)
        second.subList(1, second.size()).forEach(api::then);
        fill();
        fill();
        assertNull(runner(in, s).run().stop());
        MemoryFile.Picked all = MemoryFile.pick(runner(in, s), in, s, "c08");
        assertEquals(List.of("c08-q001-r1", "c08-q001-r2", "c08-q002-r1", "c08-q002-r2"), all.source().runIds());
        Memory m = Memory.build(all.runs());
        assertEquals(Map.of("41591", Map.of(new Memory.Item("0건", "region_code=41590", "region_code=41591", ""), 4)), m.items());
        assertEquals(Map.of("K01", 4), m.likes());
    }

    @Test
    void itDoesNotFreezeWhatItCannotTrust() throws Exception {
        Inputs in = twoFillRuns();
        RunSettings s = memorySettings(n -> { });
        Path out = tmp.resolve("m.json");
        // 코드가 바뀐 뒤: 그 판의 기록이 지금 지문과 다르면 빼고 만들지 않고 멈춘다
        Inputs changed = Fixture.inputs(tmp.resolve("data"), Fixture.digests("CODE2", "CARDS"), List.of(Fixture.Q1, Fixture.Q2),
                List.of(Fixture.B0, Fixture.ONE, Fixture.MEM, Fixture.B1), Memory.EMPTY);
        String stale = refused(() -> MemoryFile.freeze(runner(changed, s), changed, s, "c08", out));
        assertTrue(stale.contains("c08-q001-r1.jsonl(달라진 칸 code)") && stale.contains("c08-q002-r1.jsonl"), stale);
        RunSettings test = Fixture.settings(n -> n.put("split", "본시험"));
        assertTrue(refused(() -> MemoryFile.freeze(runner(in, test), in, test, "c08", out)).contains("연습 문항만"));
        RunSettings withMemory = memorySettings(n -> ((ObjectNode) n.get("files")).put("memory", "harness/frozen/memory_v0.json"));
        assertTrue(refused(() -> MemoryFile.freeze(runner(in, withMemory), in, withMemory, "c08", out)).contains("files.memory가 null"));
        assertTrue(refused(() -> MemoryFile.freeze(runner(in, s), in, s, "c99", out)).contains("없는 판"));
        assertTrue(refused(() -> MemoryFile.freeze(runner(in, s), in, s, "c03", out)).contains("계획에 판 c03의 실행이 없다"));
        // 계획 밖 기록: 설정의 select를 q001로 좁히면 q002의 기록이 계획 밖이다
        RunSettings narrow = Fixture.settings(n -> {
            Fixture.variants(n, "c08");
            Fixture.questions(n, "q001");
        });
        assertTrue(refused(() -> MemoryFile.freeze(runner(in, narrow), in, narrow, "c08", out)).endsWith("계획 밖 기록 1개(c08-q002-r1)"));
        // 실행 ID가 같은 기록이 둘(손으로 복사한 파일)
        Path copy = tmp.resolve("runs/c08-q001-r1-copy.jsonl");
        Files.copy(tmp.resolve("runs/c08-q001-r1.jsonl"), copy);
        assertTrue(refused(() -> MemoryFile.freeze(runner(in, s), in, s, "c08", out)).contains("실행 ID가 같은 기록이 둘이다: c08-q001-r1"));
        Files.delete(copy);
        assertFalse(Files.exists(out));
        // 다른 판의 지문이 다른 기록은 막지 않는다: c10(Opus) 기록을 만든 뒤 Opus 설정만 바꾼다
        Fixture.solve().forEach(api::then);
        assertNull(runner(in, Fixture.settings(n -> {
            Fixture.variants(n, "c10");
            Fixture.questions(n, "q001");
        })).run().stop());
        RunSettings opus = memorySettings(n -> ((ObjectNode) n.get("models").get("claude-opus-5-5")).put("thinking", "disabled"));
        assertEquals(List.of("c10"), runner(in, opus).status().stale().stream().map(Runner.Stale::variant).toList());
        assertEquals(2, MemoryFile.freeze(runner(in, opus), in, opus, "c08", out).picked().runs().size());
    }

    @Test
    void theFrozenFileFeedsOnlyTheMemoryVariants() throws Exception {
        Inputs in = twoFillRuns();
        RunSettings s = memorySettings(n -> { });
        Path out = tmp.resolve("frozen/memory_v0.json");
        MemoryFile.freeze(runner(in, s), in, s, "c08", out);
        // git이 CRLF로 꺼내도 해시가 같고 읽힌다
        Path crlf = tmp.resolve("crlf.json");
        Files.writeString(crlf, Files.readString(out, StandardCharsets.UTF_8).replace("\n", "\r\n"), StandardCharsets.UTF_8);
        assertEquals(Fingerprint.file(out), Fingerprint.file(crlf));
        assertEquals(MemoryFile.read(out).memory().items(), MemoryFile.read(crlf).memory().items());
        // 지문: ⑤가 든 판(c08)만 바뀐다
        Inputs after = Fixture.inputs(tmp.resolve("data"), Fixture.digests("CODE", "CARDS", "DEPS", Fingerprint.file(out)), List.of(Fixture.Q1, Fixture.Q2),
                List.of(Fixture.B0, Fixture.ONE, Fixture.MEM, Fixture.B1), MemoryFile.read(out).memory());
        RunSettings frozen = Fixture.settings(n -> {
            n.put("stage", "frozen");
            Fixture.variants(n, "c08");
            Fixture.questions(n, "q001");
            ((ObjectNode) n.get("files")).put("memory", "frozen/memory_v0.json");
        });
        Map<String, String> was = runner(in, frozen).prints();
        Map<String, String> now = runner(after, frozen).prints();
        assertEquals(List.of("c08"), was.keySet().stream().filter(k -> !was.get(k).equals(now.get(k))).toList());
        // 얼린 기억이 안내 자리에 닿는다
        Fixture.solve().forEach(api::then);
        Path dir = tmp.resolve("runs-frozen");
        assertNull(runner(after, frozen, dir).run().stop());
        JsonNode first = api.requests.get(api.requests.size() - 3).get("messages").get(0).get("content").get(0);
        assertTrue(first.get("text").asText().startsWith(NOTICE + "\n\n" + Fixture.Q1.text()), first.toString());
        assertEquals(Fingerprint.file(out), RunFile.read(dir.resolve("c08-q001-r1.jsonl"), false).header().get("fingerprint_parts").get("memory").asText());
    }

    @Test
    void anotherJvmWritesTheSameBytes() throws Exception {
        Inputs in = twoFillRuns();
        RunSettings s = memorySettings(n -> { });
        Path one = tmp.resolve("one/memory.json");
        MemoryFile.Result res = MemoryFile.freeze(runner(in, s), in, s, "c08", one);
        Path two = tmp.resolve("two/memory.json");
        String java = ProcessHandle.current().info().command().orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        Path log = tmp.resolve("child.log");
        Process p = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), MemoryFreezeChild.class.getName(),
                tmp.resolve("data").toString(), tmp.resolve("runs").toString(), two.toString())
                .directory(new File(System.getProperty("user.dir"))).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "다른 JVM이 2분 안에 끝나야 한다");
        assertEquals(0, p.exitValue(), Files.readString(log, StandardCharsets.UTF_8));
        assertArrayEquals(Files.readAllBytes(one), Files.readAllBytes(two));
        assertEquals(TEXT, res.text());
        System.out.println("얼린 기억 검사: 다른 JVM에서 같은 기록으로 얼린 파일이 바이트까지 같음(실행 " + res.picked().runs().size() + "개, 항목 "
                + res.memory().items().values().stream().mapToInt(Map::size).sum() + "개, 좋아요를 받은 카드 " + res.memory().likes().size() + "장, SHA-256 "
                + res.sha256().substring(0, 12) + ")");
    }
}
