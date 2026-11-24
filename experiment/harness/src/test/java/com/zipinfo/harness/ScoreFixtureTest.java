package com.zipinfo.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.model.AnthropicModelClient;
import com.zipinfo.ai.reflex.Memory;
import com.zipinfo.ai.reflex.Part;
import com.zipinfo.ai.reflex.Variant;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 순서 8a: 채점기(prep score)의 시험 자료 "실제 꼴의 기록"(순서 8 설계 d판 7절). 가짜 모델 서버와 가짜 배치 서버로 하네스를 끝까지 돌려
 * 단계 폴더(기록, plan.txt, 판정 파일)와 그때 쓴 설정·판·문항 파일을 짓는다. 매물과 거래는 하네스 시험의 지어낸 자료(Fixture)다.
 * <ul>
 * <li>c01-q001-r1: T1, 시군구로 견줘 제출(Fixture.solve). 판정 답함.</li>
 * <li>c01-q002-r1: 거절(refusal)로 FAILED_REFUSAL.</li>
 * <li>c07-q001-r1: ④ 판. 첫 제출은 차이 값이 틀려 막히고, 고쳐 낸 둘째 제출이 받아들여진다. 판정 답하지 않음.</li>
 * <li>c07-q002-r1: T2, 단지로 견주고 시군구 0건 claim을 덧붙여 제출. 판정 답함.</li>
 * </ul>
 * 지은 폴더가 커밋한 시험 자료(experiment/prep/tests/data/score_fx)와 같은 꼴인지(파일, 줄마다 사건의 type·이름과 칸 이름, 설정 파일의 칸) 본다.
 * 다르면 하네스가 쓰는 꼴이 바뀐 것이다. 환경 변수 SCORE_FIXTURE_OUT에 그 폴더를 주고 이 시험을 돌리면 새로 쓴다(그 뒤 파이썬 시험을 다시 돌린다).
 */
class ScoreFixtureTest {
    static final Path FX = Fixture.EXP.resolve("prep/tests/data/score_fx");
    static final Variant GATE = new Variant("c07", "①+④", Fixture.HAIKU, Variant.CardMode.TOOLS, List.of(Part.GATE));
    static final Question T1 = new Question("q001", "L01", "T1", "연습", "시험 글 가");
    static final Question T2 = new Question("q002", "L02", "T2", "연습", "시험 글 나");
    @TempDir
    Path tmp;

    @Test
    void theScorerFixtureHasTheShapeTheHarnessWrites() throws Exception {
        Path root = tmp.resolve("score_fx");
        build(root);
        String out = System.getenv("SCORE_FIXTURE_OUT");
        if (out != null && !out.isBlank()) {
            write(root, Path.of(out));
        }
        Map<String, List<String>> want = shape(FX);
        Map<String, List<String>> got = shape(root);
        assertEquals(want.keySet(), got.keySet(), "채점 시험 자료의 파일이 다르다. SCORE_FIXTURE_OUT으로 다시 짓는다");
        for (String f : got.keySet()) {
            assertEquals(want.get(f), got.get(f), f + "의 꼴이 하네스가 지금 쓰는 꼴과 다르다. SCORE_FIXTURE_OUT으로 다시 짓는다");
        }
        long records = got.keySet().stream().filter(f -> f.startsWith("harness/runs/pilot/") && f.endsWith(".jsonl") && f.indexOf('/', 19) < 0).count();
        System.out.println("채점 시험 자료 검사: 기록 " + records + "개·plan.txt·판정 파일이 하네스가 지금 쓰는 꼴과 같음(파일 " + got.size() + "개)");
    }

    /** 단계 폴더와 설정·판·문항 파일을 root 아래에 experiment 폴더처럼 짓는다. */
    static void build(Path root) throws Exception {
        Path data = Files.createDirectories(root.resolveSibling("data"));
        Inputs in = Fixture.inputs(data, Fixture.digests("CODE", "CARDS"), List.of(T1, T2, Fixture.Q9), List.of(Fixture.B0, GATE), Memory.EMPTY);
        ObjectNode n = (ObjectNode) Fixture.M.readTree(Files.readString(Fixture.EXP.resolve("harness/settings/pilot_v0.json"), StandardCharsets.UTF_8));
        ((ObjectNode) n.get("select")).putArray("reps").add(1);
        Fixture.variants(n, "c01", "c07");
        Fixture.questions(n, "q001", "q002");
        ((ObjectNode) n.get("files")).put("variants", "variants/variants_fx.json").put("questions", "questions/questions_fx.json");
        RunSettings s = RunSettings.parse(n.toString());
        Path stage = root.resolve(s.files().out()).resolve(s.stage());
        try (FakeAnthropic api = new FakeAnthropic(); FakeBatches fb = new FakeBatches()) {
            Fixture.solve().forEach(api::then);
            ObjectNode cyber = Fixture.M.createObjectNode().put("type", "refusal").put("category", "cyber");
            api.then(FakeAnthropic.sse(List.of(FakeAnthropic.text("거절")), "refusal", cyber));
            gateRun().forEach(api::then);
            t2Run().forEach(api::then);
            Runner r = new Runner(in, s, stage, (ms, cache, t) -> new AnthropicModelClient("test-key", api.url(), ms, t, cache), x -> { },
                    new PrintStream(OutputStream.nullOutputStream()));
            assertNull(r.run().stop());
            Files.writeString(stage.resolve("plan.txt"), Main.planText(r, s), StandardCharsets.UTF_8);
            fb.pollsBeforeEnd = 0;
            fb.answer = (b, cid, p) -> Judge.runIdOf(cid).equals("c07-q001-r1") ? FakeBatches.verdict(false, "묻는 값을 답하지 않았다")
                    : FakeBatches.verdict(true, "묻는 값을 답했다");
            Judge.Result j = new Judge(in, s, Fixture.EXP, stage, BatchClient.anthropic("test-key", fb.url(), Duration.ofSeconds(30)), x -> { },
                    new PrintStream(OutputStream.nullOutputStream())).run();
            assertNull(j.stop());
            assertEquals(List.of(3, 3, 2), List.of(j.targets(), j.judged(), j.answered()));
        }
        Files.delete(stage.resolve(".lock"));
        Path settings = root.resolve("harness/settings/fx_v0.json");
        Files.createDirectories(settings.getParent());
        Files.writeString(settings, n.toString() + "\n", StandardCharsets.UTF_8);
        ObjectNode vs = Fixture.M.createObjectNode().put("version", "fx");
        ArrayNode va = vs.putArray("variants");
        va.addObject().put("id", "c01").put("name", "B0").put("model", Fixture.HAIKU).put("cards", "none").putArray("parts");
        va.addObject().put("id", "c07").put("name", "①+④").put("model", Fixture.HAIKU).put("cards", "tools").putArray("parts").add("④");
        Files.createDirectories(root.resolve("variants"));
        Files.writeString(root.resolve("variants/variants_fx.json"), vs.toString() + "\n", StandardCharsets.UTF_8);
        ObjectNode qs = Fixture.M.createObjectNode().put("version", "fx");
        ArrayNode qa = qs.putArray("questions");
        for (Question q : List.of(T1, T2, Fixture.Q9)) {
            qa.addObject().put("id", q.id()).put("key", q.key()).put("qtype", q.qtype()).put("split", q.split()).put("text", q.text());
        }
        Files.createDirectories(root.resolve("questions"));
        Files.writeString(root.resolve("questions/questions_fx.json"), qs.toString() + "\n", StandardCharsets.UTF_8);
    }

    /** ④ 판: 차이 값이 틀린 제출(막힘) 뒤에 맞게 고친 제출. */
    static List<FakeAnthropic.Reply> gateRun() {
        ObjectNode bad = Fixture.report();
        ((ObjectNode) bad.get("value_claims").get(0)).put("value", 3.8);
        return List.of(
                FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_1", "trade_stats", Fixture.statsInput())), "tool_use", null),
                FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_2", "compare_prices",
                        Fixture.M.createObjectNode().put("listing_evidence", "매물.가격").put("basis_evidence", "S1.기준"))), "tool_use", null),
                FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_3", "submit_report", bad)), "tool_use", null),
                FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_4", "submit_report", Fixture.report())), "tool_use", null));
    }

    /** T2: 단지(가동 12)로 견주고, 시군구의 그 전달(0건) claim을 덧붙인다. 값은 도구가 내는 것과 같다(단지 기준값 31770만원, 차이 10.2%). */
    static List<FakeAnthropic.Reply> t2Run() {
        ObjectNode unit = Fixture.statsInput().put("dong", "가동").put("jibun", "12");
        ObjectNode zero = Fixture.statsInput().put("start_month", "202609").put("end_month", "202609");
        ObjectNode r = Fixture.M.createObjectNode().put("text", "이 매물은 같은 단지 기준값보다 {가} {가.방향}. 시군구 전달 거래는 {다}.");
        ArrayNode st = r.putArray("stat_claims");
        st.addObject().put("slot", "나").put("value", 31770).put("unit", "만원").put("basis", "단지").put("start_month", "202610")
                .put("end_month", "202610").put("sample_size", 2).put("provisional", true).put("evidence", "S1.기준");
        st.addObject().put("slot", "다").put("value", 0).put("unit", "건").put("basis", "시군구").put("start_month", "202609")
                .put("end_month", "202609").put("sample_size", 0).put("provisional", true).put("evidence", "S2.표본수");
        r.putArray("value_claims").addObject().put("slot", "가").put("value", 10.2).put("unit", "%").put("source", "계산").put("evidence", "C1.차이");
        r.putArray("context_claims").addObject().put("text", "같은 단지 거래로만 견줬다").put("evidence", "S1.표본수");
        return List.of(
                FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_1", "trade_stats", unit)), "tool_use", null),
                FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_2", "trade_stats", zero)), "tool_use", null),
                FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_3", "compare_prices",
                        Fixture.M.createObjectNode().put("listing_evidence", "매물.가격").put("basis_evidence", "S1.기준"))), "tool_use", null),
                FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_4", "submit_report", r)), "tool_use", null));
    }

    /** 지은 폴더를 out에 쓴다. out은 이름이 score_fx인 폴더여야 하고, 있으면 지우고 새로 쓴다. */
    static void write(Path root, Path out) throws IOException {
        if (!out.getFileName().toString().equals("score_fx")) {
            throw new IllegalArgumentException("SCORE_FIXTURE_OUT은 score_fx 폴더다: " + out);
        }
        if (Files.exists(out)) {
            try (Stream<Path> st = Files.walk(out)) {
                for (Path p : st.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
        for (Path f : files(root)) {
            Path to = out.resolve(root.relativize(f).toString());
            Files.createDirectories(to.getParent());
            Files.copy(f, to);
        }
    }

    static List<Path> files(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> st = Files.walk(dir)) {
            return st.filter(Files::isRegularFile).filter(p -> !p.getFileName().toString().startsWith(".")).sorted().toList();
        }
    }

    /**
     * 꼴: 파일마다 줄 목록. .jsonl은 줄마다 type(도구는 이름, 장치 판정은 지점·부품·판정을 붙임)과 칸 이름을 쓴 차례로,
     * .json은 칸의 길(배열은 첫 원소), plan.txt는 64자 해시와 숫자를 가린 줄이다. 값(시각·시간·해시)은 보지 않는다.
     */
    static Map<String, List<String>> shape(Path dir) throws IOException {
        Map<String, List<String>> out = new TreeMap<>();
        for (Path f : files(dir)) {
            String rel = dir.relativize(f).toString().replace('\\', '/');
            String text = new String(Fingerprint.lf(Files.readAllBytes(f)), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>();
            if (rel.endsWith(".jsonl")) {
                for (String l : text.split("\n")) {
                    if (l.isEmpty()) {
                        continue;
                    }
                    JsonNode node = Fixture.M.readTree(l);
                    String t = node.path("type").asText("-");
                    if (t.equals("tool")) {
                        t += ":" + node.path("name").asText();
                    } else if (t.equals("hook")) {
                        t += ":" + node.path("point").asText() + ":" + node.path("part").asText() + ":" + node.path("decision").asText();
                    }
                    List<String> keys = new ArrayList<>();
                    node.fieldNames().forEachRemaining(keys::add);
                    lines.add(t + " " + keys);
                }
            } else if (rel.endsWith(".json")) {
                paths(Fixture.M.readTree(text), "", lines);
            } else {
                for (String l : text.split("\n")) {
                    lines.add(l.replaceAll("[0-9A-F]{64}", "#").replaceAll("[0-9]+", "9"));
                }
            }
            out.put(rel, lines);
        }
        return out;
    }

    static void paths(JsonNode n, String at, List<String> out) {
        if (n.isObject()) {
            for (Map.Entry<String, JsonNode> e : n.properties()) {
                out.add(at + "/" + e.getKey());
                paths(e.getValue(), at + "/" + e.getKey(), out);
            }
        } else if (n.isArray() && !n.isEmpty()) {
            paths(n.get(0), at + "[]", out);
        }
    }

    @Test
    void theShapeIgnoresValuesButSeesFields() throws Exception {
        Path a = Files.createDirectories(tmp.resolve("a"));
        Path b = Files.createDirectories(tmp.resolve("b"));
        Files.writeString(a.resolve("r.jsonl"), "{\"type\":\"run\",\"at\":\"2026-01-01\",\"x\":1}\n", StandardCharsets.UTF_8);
        Files.writeString(b.resolve("r.jsonl"), "{\"type\":\"run\",\"at\":\"2027-12-31\",\"x\":2}\r\n", StandardCharsets.UTF_8);
        assertEquals(shape(a), shape(b));
        Files.writeString(b.resolve("r.jsonl"), "{\"type\":\"run\",\"at\":\"2027-12-31\",\"y\":2}\n", StandardCharsets.UTF_8);
        assertTrue(!shape(a).equals(shape(b)), "칸 이름이 다르면 꼴이 다르다");
    }
}
