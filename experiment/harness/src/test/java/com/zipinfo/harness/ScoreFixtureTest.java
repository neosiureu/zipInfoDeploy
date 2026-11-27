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
 * 순서 8a·8b: 채점기(prep score)의 시험 자료 "실제 꼴의 기록"(순서 8 설계 d판 7절, 8b 설계 2판 10절). 가짜 모델 서버와 가짜 배치 서버로 하네스를 끝까지 돌려
 * 단계 폴더(기록, plan.txt, 하네스 summary.tsv, 판정 파일)와 그때 쓴 설정·판·문항 파일을 짓는다. 매물과 거래는 하네스 시험의 지어낸 자료(Fixture)다.
 * <ul>
 * <li>c01-q001-r1: T1, 시군구로 견줘 제출(Fixture.solve). 판정 답함.</li>
 * <li>c01-q002-r1: 거절(refusal)로 FAILED_REFUSAL.</li>
 * <li>c04-q001-r1: ⓪ 판. 의도를 적기 전 trade_stats가 막히고(IntentLock), 덮지 못한 선언이 거절된 뒤 통과한다. 한 응답에 부름 둘. 제출.</li>
 * <li>c04-q002-r1: ⓪ 판. search_address가 막히고, 재촉 뒤에도 제출이 없어 FAILED_NO_SUBMIT.</li>
 * <li>c05-q001-r1: ② 판. 한 응답에 search_address(SNAPSHOT_MISS)와 옛 코드 trade_stats(② 거절), 현재 코드로 다시 불러 제출.</li>
 * <li>c05-q002-r1: ② 판. 서버 오류 한 번(실패한 시도) 뒤 꼴이 틀린 제출(BAD_INPUT), 그다음 max_tokens로 FAILED_MAX_TOKENS.</li>
 * <li>c06-q001-r1: ③ 판. K02를 읽기 전 trade_stats가 막히고, 읽은 뒤 통과. K06을 읽지 않은 convert_area가 막힘. 캐시 쓰기 5분·1시간 나눔, 10만 넘는 요청. 제출.</li>
 * <li>c06-q002-r1: ③ 판. K02를 읽고 옛 코드로 단지를 불러 0건을 받고 "거래 없음"으로 제출(ZERO_INVALID가 기록되지만 ④가 없어 받아들여짐).</li>
 * <li>c07-q001-r1: ④ 판. 첫 제출은 차이 값이 틀려 막히고, 고쳐 낸 둘째 제출이 받아들여진다. 판정 답하지 않음.</li>
 * <li>c07-q002-r1: T2, 단지로 견주고 시군구 0건 claim을 덧붙여 제출. 판정 답함.</li>
 * </ul>
 * 지은 폴더가 커밋한 시험 자료(experiment/prep/tests/data/score_fx_v1)와 같은 꼴인지(파일, 줄마다 사건의 type·이름과 칸 이름, 설정 파일의 칸) 본다.
 * 다르면 하네스가 쓰는 꼴이 바뀐 것이다. 환경 변수 SCORE_FIXTURE_OUT에 그 폴더를 주고 이 시험을 돌리면 새로 쓴다(그 뒤 파이썬 시험을 다시 돌린다).
 * 순서 11b-2: 검사기 v1·그리기 v1의 기록이다(설정 version v1, 제출 사건의 rendered). 시운전 꼴(검사기 v0, 설정 version v0)의 옛 자료
 * score_fx는 그대로 두고(이 시험은 다시 짓지 않는다), 파이썬 시험이 시운전 기록의 채점 v1을 보는 데 쓴다.
 */
class ScoreFixtureTest {
    static final Path FX = Fixture.EXP.resolve("prep/tests/data/score_fx_v1");
    static final Variant INTENT = new Variant("c04", "①+⓪", Fixture.HAIKU, Variant.CardMode.TOOLS, List.of(Part.INTENT));
    static final Variant REFLEX = new Variant("c05", "①+②", Fixture.HAIKU, Variant.CardMode.TOOLS, List.of(Part.REFLEX));
    static final Variant LOCK = new Variant("c06", "①+③", Fixture.HAIKU, Variant.CardMode.TOOLS, List.of(Part.LOCK));
    static final Variant GATE = new Variant("c07", "①+④", Fixture.HAIKU, Variant.CardMode.TOOLS, List.of(Part.GATE));
    static final Question T1 = new Question("q001", "L01", "T1", "연습", "시험 글 가");
    static final Question T2 = new Question("q002", "L02", "T2", "연습", "시험 글 나");
    @TempDir
    Path tmp;

    @Test
    void theScorerFixtureHasTheShapeTheHarnessWrites() throws Exception {
        Path root = tmp.resolve("score_fx_v1");
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
        System.out.println("채점 시험 자료 검사: 기록 " + records + "개·plan.txt·summary.tsv·판정 파일이 하네스가 지금 쓰는 꼴과 같음(파일 " + got.size() + "개)");
    }

    /** 단계 폴더와 설정·판·문항 파일을 root 아래에 experiment 폴더처럼 짓는다. */
    static void build(Path root) throws Exception {
        Path data = Files.createDirectories(root.resolveSibling("data"));
        Inputs in = Fixture.inputs(data, Fixture.digests("CODE", "CARDS"), List.of(T1, T2, Fixture.Q9), List.of(Fixture.B0, INTENT, REFLEX, LOCK, GATE),
                Memory.EMPTY);
        ObjectNode n = (ObjectNode) Fixture.M.readTree(Files.readString(Fixture.EXP.resolve("harness/settings/pilot_v0.json"), StandardCharsets.UTF_8));
        n.put("version", "v1");
        ((ObjectNode) n.get("select")).putArray("reps").add(1);
        Fixture.variants(n, "c01", "c04", "c05", "c06", "c07");
        Fixture.questions(n, "q001", "q002");
        ((ObjectNode) n.get("files")).put("variants", "variants/variants_fx.json").put("questions", "questions/questions_fx.json");
        RunSettings s = RunSettings.parse(n.toString());
        Path stage = root.resolve(s.files().out()).resolve(s.stage());
        try (FakeAnthropic api = new FakeAnthropic(); FakeBatches fb = new FakeBatches()) {
            Fixture.solve().forEach(api::then);
            ObjectNode cyber = Fixture.M.createObjectNode().put("type", "refusal").put("category", "cyber");
            api.then(FakeAnthropic.sse(List.of(FakeAnthropic.text("거절")), "refusal", cyber));
            intentRun().forEach(api::then);
            intentNoSubmit().forEach(api::then);
            reflexRun().forEach(api::then);
            reflexBadInput().forEach(api::then);
            lockRun().forEach(api::then);
            lockZero().forEach(api::then);
            gateRun().forEach(api::then);
            t2Run().forEach(api::then);
            Runner r = new Runner(in, s, stage, (ms, cache, t) -> new AnthropicModelClient("test-key", api.url(), ms, t, cache), x -> { },
                    new PrintStream(OutputStream.nullOutputStream()));
            assertNull(r.run().stop());
            Files.writeString(stage.resolve("plan.txt"), Main.planText(r, s), StandardCharsets.UTF_8);
            Files.writeString(stage.resolve("summary.tsv"), Summary.format(Summary.rows(r.status(), s, in.variants(), stage)), StandardCharsets.UTF_8);
            fb.pollsBeforeEnd = 0;
            fb.answer = (b, cid, p) -> Judge.runIdOf(cid).equals("c07-q001-r1") ? FakeBatches.verdict(false, "묻는 값을 답하지 않았다")
                    : FakeBatches.verdict(true, "묻는 값을 답했다");
            Judge.Result j = new Judge(in, s, Fixture.EXP, stage, BatchClient.anthropic("test-key", fb.url(), Duration.ofSeconds(30)), x -> { },
                    new PrintStream(OutputStream.nullOutputStream())).run();
            assertNull(j.stop());
            assertEquals(List.of(7, 7, 6), List.of(j.targets(), j.judged(), j.answered()));
        }
        Files.delete(stage.resolve(".lock"));
        Path settings = root.resolve("harness/settings/fx_v1.json");
        Files.createDirectories(settings.getParent());
        Files.writeString(settings, n.toString() + "\n", StandardCharsets.UTF_8);
        ObjectNode vs = Fixture.M.createObjectNode().put("version", "fx");
        ArrayNode va = vs.putArray("variants");
        va.addObject().put("id", "c01").put("name", "B0").put("model", Fixture.HAIKU).put("cards", "none").putArray("parts");
        va.addObject().put("id", "c04").put("name", "①+⓪").put("model", Fixture.HAIKU).put("cards", "tools").putArray("parts").add("⓪");
        va.addObject().put("id", "c05").put("name", "①+②").put("model", Fixture.HAIKU).put("cards", "tools").putArray("parts").add("②");
        va.addObject().put("id", "c06").put("name", "①+③").put("model", Fixture.HAIKU).put("cards", "tools").putArray("parts").add("③");
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

    static ObjectNode intents(String source) {
        ObjectNode in = Fixture.M.createObjectNode();
        in.putArray("intents").addObject().put("source_text", source).put("corrected_text", "").put("intent_expansion", "").put("english_intent", "region");
        return in;
    }

    static FakeAnthropic.Reply one(String id, String tool, ObjectNode input) {
        return FakeAnthropic.sse(List.of(FakeAnthropic.toolUse(id, tool, input)), "tool_use", null);
    }

    static ObjectNode compare() {
        return Fixture.M.createObjectNode().put("listing_evidence", "매물.가격").put("basis_evidence", "S1.기준");
    }

    static ObjectNode usage(long in, long read, long write, Long w5, Long w1) {
        ObjectNode u = Fixture.M.createObjectNode().put("input_tokens", in).put("cache_read_input_tokens", read).put("cache_creation_input_tokens", write)
                .put("output_tokens", 1);
        if (w5 != null) {
            u.putObject("cache_creation").put("ephemeral_5m_input_tokens", w5).put("ephemeral_1h_input_tokens", w1);
        }
        return u;
    }

    /** ⓪ 판: 의도를 적기 전 trade_stats(IntentLock 거절) → 질문에 없는 원어(덮지 못해 거절) → 질문을 덮는 원어(통과) → 한 응답에 trade_stats·compare_prices → 제출. */
    static List<FakeAnthropic.Reply> intentRun() {
        return List.of(
                one("toolu_1", "trade_stats", Fixture.statsInput()),
                one("toolu_2", "declare_intents", intents("없는 글")),
                one("toolu_3", "declare_intents", intents(T1.text())),
                FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_4", "trade_stats", Fixture.statsInput()),
                        FakeAnthropic.toolUse("toolu_5", "compare_prices", compare())), "tool_use", null),
                one("toolu_6", "submit_report", Fixture.report()));
    }

    /** ⓪ 판: search_address가 막히고(IntentLock), 글만 두 번 내 재촉 뒤 FAILED_NO_SUBMIT. */
    static List<FakeAnthropic.Reply> intentNoSubmit() {
        return List.of(
                one("toolu_1", "search_address", Fixture.M.createObjectNode().put("query", "없는 길 1")),
                FakeAnthropic.sse(List.of(FakeAnthropic.text("모르겠다")), "end_turn", null),
                FakeAnthropic.sse(List.of(FakeAnthropic.text("그래도 모르겠다")), "end_turn", null));
    }

    /** ② 판: 한 응답에 search_address(없는 주소)와 옛 코드 41590의 trade_stats(② 거절) → 현재 코드로 다시 → compare_prices → 제출. */
    static List<FakeAnthropic.Reply> reflexRun() {
        return List.of(
                FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_1", "search_address", Fixture.M.createObjectNode().put("query", "없는 길 2")),
                        FakeAnthropic.toolUse("toolu_2", "trade_stats", Fixture.statsInput().put("region_code", "41590"))), "tool_use", null),
                one("toolu_3", "trade_stats", Fixture.statsInput()),
                one("toolu_4", "compare_prices", compare()),
                one("toolu_5", "submit_report", Fixture.report()));
    }

    /** ② 판: 서버 오류(재시도 한 번) → stat_claims가 없는 제출(BAD_INPUT) → max_tokens. */
    static List<FakeAnthropic.Reply> reflexBadInput() {
        ObjectNode bad = Fixture.report();
        bad.remove("stat_claims");
        return List.of(
                FakeAnthropic.error(529, "overloaded_error", "잠시 뒤 다시", null),
                one("toolu_1", "submit_report", bad),
                FakeAnthropic.sse(List.of(FakeAnthropic.text("길다")), "max_tokens", null));
    }

    /** ③ 판: K02 전 trade_stats(③ 거절) → read_card K02 → trade_stats → K06 전 convert_area(③ 거절) → compare_prices → 제출. 토큰은 5분·1시간 나눔과 10만 넘음을 본다. */
    static List<FakeAnthropic.Reply> lockRun() {
        return List.of(
                FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_1", "trade_stats", Fixture.statsInput())), "tool_use", null, usage(100, 80, 20, 5L, 15L)),
                FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_2", "read_card", Fixture.M.createObjectNode().put("card_id", "K02"))), "tool_use", null,
                        usage(100, 80, 20, 5L, 5L)),
                FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_3", "trade_stats", Fixture.statsInput())), "tool_use", null,
                        usage(150_000, 0, 0, null, null)),
                one("toolu_4", "convert_area", Fixture.M.createObjectNode().put("area_evidence", "매물.면적").put("to_unit", "평")),
                one("toolu_5", "compare_prices", compare()),
                one("toolu_6", "submit_report", Fixture.report()));
    }

    /** ③ 판, T2: read_card K02 → 옛 코드 41590으로 단지를 불러 0건 → 그 0건으로 "거래 없음" 제출(검사기는 ZERO_INVALID, ④가 없어 받아들여짐). */
    static List<FakeAnthropic.Reply> lockZero() {
        ObjectNode r = Fixture.M.createObjectNode().put("text", "같은 단지 거래는 {다}.");
        r.putArray("stat_claims").addObject().put("slot", "다").put("value", 0).put("unit", "건").put("basis", "단지").put("start_month", "202610")
                .put("end_month", "202610").put("sample_size", 0).put("provisional", true).put("evidence", "S1.표본수");
        r.putArray("value_claims");
        r.putArray("context_claims");
        return List.of(
                one("toolu_1", "read_card", Fixture.M.createObjectNode().put("card_id", "K02")),
                one("toolu_2", "trade_stats", Fixture.statsInput().put("region_code", "41590").put("dong", "가동").put("jibun", "12")),
                one("toolu_3", "submit_report", r));
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
        ObjectNode r = Fixture.M.createObjectNode().put("text", "이 매물은 같은 단지 기준값보다 {가} {가.방향}. 시군구 전달 거래는 {다}입니다.");
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

    /** 지은 폴더를 out에 쓴다. out은 이름이 score_fx_v1인 폴더여야 하고, 있으면 지우고 새로 쓴다. */
    static void write(Path root, Path out) throws IOException {
        if (!out.getFileName().toString().equals("score_fx_v1")) {
            throw new IllegalArgumentException("SCORE_FIXTURE_OUT은 score_fx_v1 폴더다: " + out);
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
