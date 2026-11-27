package com.zipinfo.ai.reflex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.cards.Cards;
import com.zipinfo.ai.data.AddressBook;
import com.zipinfo.ai.data.History;
import com.zipinfo.ai.data.Listing;
import com.zipinfo.ai.data.ListingMeta;
import com.zipinfo.ai.data.TradeStore;
import com.zipinfo.ai.data.ValidityTable;
import com.zipinfo.ai.loop.Hook;
import com.zipinfo.ai.loop.Hook.Decision;
import com.zipinfo.ai.loop.Prompts;
import com.zipinfo.ai.loop.QuestionLoop;
import com.zipinfo.ai.loop.RunRecord;
import com.zipinfo.ai.model.AnthropicModelClient;
import com.zipinfo.ai.model.FakeAnthropic;
import com.zipinfo.ai.model.ModelSettings;
import com.zipinfo.ai.report.Finding;
import com.zipinfo.ai.tools.DataSet;
import com.zipinfo.ai.tools.Fixture;
import com.zipinfo.ai.tools.RunState;
import com.zipinfo.ai.tools.ToolSpecs;
import com.zipinfo.ai.tools.Tools;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 순서 5a: ② 반사 규칙, ③ 학습 잠금, ④ 근거 게이트, 판 설정과 사슬 차례(설계안 7.1·7.3~7.5·7.7). */
class ReflexTest {
    static final ObjectMapper M = new ObjectMapper();
    static DataSet small;
    static DataSet fixture;

    static void write(Path p, List<String> lines) throws IOException {
        Files.writeString(p, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    /**
     * 작은 자료: 영역 41591(개편, 후보 41590·41591)과 11680(후보 하나), apt_trade 202607~202610.
     * 41591에서 옛 코드 41590은 202607·202608·202610에 무효이고, 202609는 정말 0건이라 둘 다 유효다. 값은 지어낸 것이다.
     */
    @BeforeAll
    static void load(@TempDir Path dir) throws IOException {
        List<String> b = new ArrayList<>(List.of(String.join(",", TradeStore.BUNDLE_HEADER)));
        for (String code : List.of("41590", "41591", "11680")) {
            for (String ym : List.of("202607", "202608", "202609", "202610")) {
                b.add("apt_trade," + code + "," + ym + ",0,0,0,0");
            }
        }
        write(dir.resolve("bundles.csv"), b);
        write(dir.resolve("trades.csv"), List.of(String.join(",", TradeStore.TRADE_HEADER)));
        write(dir.resolve("history.csv"), List.of(String.join(",", History.HEADER)));
        List<String> v = new ArrayList<>(List.of(String.join(",", ValidityTable.HEADER)));
        for (String ym : List.of("202607", "202608", "202610")) {
            v.add("41591,apt_trade," + ym + ",41590,0,0,41591,one");
            v.add("41591,apt_trade," + ym + ",41591,3,1,,one");
            v.add("11680,apt_trade," + ym + ",11680,2,1,,single");
        }
        v.add("41591,apt_trade,202609,41590,0,1,,true_zero");
        v.add("41591,apt_trade,202609,41591,0,1,,true_zero");
        v.add("11680,apt_trade,202609,11680,0,1,,true_zero");
        write(dir.resolve("validity.csv"), v);
        small = new DataSet(TradeStore.load(dir.resolve("trades.csv"), dir.resolve("bundles.csv")), ValidityTable.load(dir.resolve("validity.csv")),
                History.load(dir.resolve("history.csv")), AddressBook.empty());
        Path f = Files.createDirectory(dir.resolve("fixture"));
        fixture = Fixture.data(f);
    }

    static RunState state(String area) {
        Map<String, String> f = new LinkedHashMap<>();
        for (String n : Listing.NINE) {
            f.put(n, "");
        }
        f.put("exclusiveArea", "84");
        Map<String, Long> ev = new LinkedHashMap<>();
        ev.put("매물.가격", 350_000_000L);
        ev.put("매물.월세", 0L);
        ev.put("매물.면적", 84L);
        ev.put("매물.층", 5L);
        return new RunState(new Listing("L01", f, ev), new ListingMeta("L01", "apt|" + area + "|가동|0|12|0", "매매", "일반", "본시험"));
    }

    static ObjectNode stats(String code, String start, String end) {
        return M.createObjectNode().put("api", "apt_trade").put("region_code", code).put("start_month", start).put("end_month", end)
                .put("deal", "매매").put("dong", "").put("jibun", "");
    }

    static Decision decide(Hook h, RunState st, String tool, ObjectNode in) {
        return h.decide(Hook.Point.BEFORE_TOOL, new Hook.Context(st, tool, in, null, List.of()));
    }

    static String card(String id, String title) {
        return "# " + id + " " + title + "\n\n- 영어 태그: code\n- 한글 낱말: " + title + "\n\n## 핵심\n\n- " + title + "의 핵심.\n\n## 사용법\n\n1. 한다.\n\n## 기록\n\n";
    }

    static Cards cards() {
        return new Cards(List.of(Cards.parse(card("K02", "지역 코드")), Cards.parse(card("K06", "단위와 면적"))));
    }

    // ---------------------------------------------------------------- ②

    @Test
    void reflexJudgesEveryMonthOfTheRange() {
        ReflexRule r = new ReflexRule(small);
        RunState st = state("41591");
        assertEquals(Decision.Kind.PASS, decide(r, st, "trade_stats", stats("41591", "202607", "202610")).kind());
        assertEquals(Decision.Kind.PASS, decide(r, st, "trade_stats", stats("41590", "202609", "202609")).kind(), "정말 0건 달은 막지 않는다");
        Decision d = decide(r, st, "trade_stats", stats("41590", "202607", "202610"));
        assertEquals(Decision.Kind.REJECT, d.kind());
        assertEquals("지역 코드가 이 매물에 맞지 않는다. 맞는 코드: 41591(202607~202608·202610)", d.line());
        assertEquals("지역 코드가 이 매물에 맞지 않는다. 맞는 코드: 41591(202610)",
                decide(r, st, "trade_stats", stats("11680", "202610", "202610")).line(), "후보가 아닌 코드는 매물의 현재 코드로 처방한다");
        assertEquals(Decision.Kind.PASS, decide(r, state("11680"), "trade_stats", stats("11680", "202607", "202610")).kind());
        assertEquals("지역 코드가 이 매물에 맞지 않는다. 맞는 코드: 11680(202607~202610)",
                decide(r, state("11680"), "trade_stats", stats("41591", "202607", "202610")).line());
    }

    @Test
    void reflexLeavesBadInputAndSnapshotMissToTheTool() {
        ReflexRule r = new ReflexRule(small);
        RunState st = state("41591");
        List<ObjectNode> pass = new ArrayList<>(List.of(
                stats("41590", "202606", "202610"),   // 묶음이 없는 달
                stats("41590", "202310", "202610"),   // 스냅샷 창 밖
                stats("12345", "202610", "202610"),   // 묶음이 없는 코드
                stats("4159", "202610", "202610"),
                stats("41590", "202610", "202607"),
                stats("41590", "2026-10", "202610"),
                stats("41590", "202610", "202610").put("api", "apt"),
                stats("41590", "202610", "202610").put("region_code", 41590)));
        ObjectNode noCode = stats("41590", "202610", "202610");
        noCode.remove("region_code");
        pass.add(noCode);
        for (ObjectNode in : pass) {
            assertEquals(Decision.Kind.PASS, decide(r, st, "trade_stats", in).kind(), in.toString());
        }
        assertEquals(Decision.Kind.PASS, decide(r, st, "compare_prices", stats("41590", "202610", "202610")).kind());
        assertTrue(r.at(Hook.Point.BEFORE_TOOL));
        assertFalse(r.at(Hook.Point.START) || r.at(Hook.Point.SUBMIT) || r.at(Hook.Point.NOTICE));
        assertEquals("②", r.name());
    }

    @Test
    void spansJoinRunsOfMonths() {
        assertEquals("202610", ReflexRule.spans(List.of("202610")));
        assertEquals("202412~202502·202504", ReflexRule.spans(List.of("202412", "202501", "202502", "202504")));
        assertEquals("202311·202401~202402", ReflexRule.spans(List.of("202311", "202401", "202402")));
    }

    // ---------------------------------------------------------------- ③

    @Test
    void lockAsksForTheCardUntilItIsRead() {
        assertEquals(Set.of("41590", "41591"), small.validity().candidates("41591"));
        assertEquals(Set.of("11680"), small.validity().candidates("11680"));
        assertTrue(small.validity().candidates("99999").isEmpty());
        LearningLock l = new LearningLock(small.validity());
        RunState st = state("41591");
        Decision d = decide(l, st, "trade_stats", stats("41591", "202610", "202610"));
        assertEquals(Decision.Kind.REJECT, d.kind());
        assertEquals("먼저 카드 K02를 읽어라.", d.line());
        assertTrue(st.cardsRead().isEmpty(), "판정은 실행 상태를 바꾸지 않는다");
        st.cardRead("K02");
        assertEquals(Decision.Kind.PASS, decide(l, st, "trade_stats", stats("41591", "202610", "202610")).kind());
        assertEquals(Decision.Kind.PASS, decide(l, state("11680"), "trade_stats", stats("11680", "202610", "202610")).kind(), "후보가 하나면 묻지 않는다");
        RunState one = state("11680");
        ObjectNode area = M.createObjectNode().put("area_evidence", "매물.면적").put("to_unit", "평");
        assertEquals("먼저 카드 K06을 읽어라.", decide(l, one, "convert_area", area).line());
        one.cardRead("K06");
        assertEquals(Decision.Kind.PASS, decide(l, one, "convert_area", area).kind());
        for (String tool : List.of("search_address", "compare_prices", "convert_rent")) {
            assertEquals(Decision.Kind.PASS, decide(l, state("41591"), tool, M.createObjectNode()).kind());
        }
        assertTrue(l.at(Hook.Point.BEFORE_TOOL));
        assertFalse(l.at(Hook.Point.START) || l.at(Hook.Point.SUBMIT) || l.at(Hook.Point.NOTICE));
        assertEquals("③", l.name());
    }

    // ---------------------------------------------------------------- ④

    @Test
    void gateReturnsTheFirstFinding() {
        EvidenceGate g = new EvidenceGate();
        RunState st = state("41591");
        List<Finding> two = List.of(new Finding("stat_claims[0]", "SAMPLE", "표본 수는 근거 부르기의 것(4건)이어야 한다"),
                new Finding("text", "NUMERAL", "답 글에 수를 낱말로 쓰지 않는다(두 배)"));
        Decision d = g.decide(Hook.Point.SUBMIT, new Hook.Context(st, "submit_report", null, null, two));
        assertEquals(Decision.Kind.REJECT, d.kind());
        assertEquals("stat_claims[0]: 표본 수는 근거 부르기의 것(4건)이어야 한다", d.line());
        assertEquals(Decision.Kind.PASS, g.decide(Hook.Point.SUBMIT, new Hook.Context(st, "submit_report", null, null, List.of())).kind());
        assertTrue(g.at(Hook.Point.SUBMIT));
        assertFalse(g.at(Hook.Point.START) || g.at(Hook.Point.BEFORE_TOOL) || g.at(Hook.Point.NOTICE));
        assertEquals("④", g.name());
    }

    // ---------------------------------------------------------------- 판 설정과 사슬

    static String variant(String id, String name, String cards, String parts) {
        return "{\"id\": \"" + id + "\", \"name\": \"" + name + "\", \"model\": \"m\", \"cards\": \"" + cards + "\", \"parts\": [" + parts + "]}";
    }

    static String file(String... variants) {
        return "{\"version\": \"v0\", \"variants\": [" + String.join(", ", variants) + "]}";
    }

    @Test
    void variantsFileFormIsChecked() {
        List<Variant> vs = Variants.parse(file(variant("c01", "B0", "none", ""), variant("c02", "B0+", "prompt", ""),
                variant("c09", "전부", "tools", "\"⑤\", \"④\", \"③\", \"②\", \"⓪\"")));
        assertEquals(3, vs.size());
        assertEquals(new Variant("c02", "B0+", "m", Variant.CardMode.PROMPT, List.of()), vs.get(1));
        assertEquals(List.of(Part.INTENT, Part.LOCK, Part.REFLEX, Part.GATE, Part.MEMORY), vs.get(2).parts(), "부품은 사슬 차례로 놓는다");
        List<String> bad = List.of(
                "{\"variants\": [" + variant("c01", "B0", "none", "") + "]}",
                file(),
                file(variant("c01", "B0", "none", ""), variant("c01", "B1", "none", "")),
                file(variant("c01", "B0", "none", ""), variant("c02", "B0", "none", "")),
                file(variant("C01", "B0", "none", "")),
                file(variant("c01-a", "B0", "none", "")),
                file(variant("c01", "B0", "file", "")),
                file(variant("c01", "①+⑥", "tools", "\"⑥\"")),
                file(variant("c01", "①+②", "tools", "\"②\", \"②\"")),
                file(variant("c01", "B0+②", "prompt", "\"②\"")),
                file(variant("c01", "B0", "none", "").replace("}", ", \"turns\": 9}")),
                file(variant("c01", "B0", "none", "").replace("\"model\": \"m\", ", "")),
                "not json");
        for (String b : bad) {
            assertThrows(IllegalArgumentException.class, () -> Variants.parse(b), b);
        }
    }

    @Test
    void setupFollowsTheVariant() {
        Cards cs = cards();
        Setup b0 = Setup.of(new Variant("c01", "B0", "m", Variant.CardMode.NONE, List.of()), small, cs, null, null, 12);
        assertEquals(Prompts.SYSTEM, b0.system());
        assertEquals(ToolSpecs.all(), b0.specs());
        assertTrue(b0.config().hooks().isEmpty() && b0.config().extra().isEmpty());
        assertEquals(12, b0.config().maxTurns());
        Setup plus = Setup.of(new Variant("c02", "B0+", "m", Variant.CardMode.PROMPT, List.of()), small, cs, null, null, 12);
        assertEquals(Prompts.SYSTEM + "\n\n" + cs.fullText(), plus.system());
        assertEquals(ToolSpecs.all(), plus.specs());
        assertTrue(plus.config().extra().isEmpty());
        Setup one = Setup.of(new Variant("c03", "①", "m", Variant.CardMode.TOOLS, List.of()), small, cs, null, null, 12);
        assertEquals(Prompts.SYSTEM, one.system());
        assertEquals(List.of("search_address", "trade_stats", "compare_prices", "convert_rent", "convert_area", "submit_report", "search_cards", "read_card"),
                one.specs().stream().map(ToolSpecs.Spec::name).toList());
        assertEquals(Set.of("search_cards", "read_card"), one.config().extra().keySet());
        assertTrue(one.config().hooks().isEmpty());
        assertEquals(List.of("③", "②", "④"), Setup.hooks(EnumSet.of(Part.GATE, Part.REFLEX, Part.LOCK), small, null).stream().map(Hook::name).toList());
        assertEquals(List.of("⓪"), Setup.hooks(List.of(Part.INTENT), small, null).stream().map(Hook::name).toList());
        assertEquals(List.of("⑤"), Setup.hooks(List.of(Part.MEMORY), small, Memory.EMPTY).stream().map(Hook::name).toList());
        assertThrows(IllegalArgumentException.class, () -> Setup.hooks(List.of(Part.MEMORY), small, null), "⑤는 기억이 있어야 한다");
    }

    // ---------------------------------------------------------------- 고리에 걸었을 때(가짜 API 서버)

    static ObjectNode report(int sample) {
        ObjectNode r = M.createObjectNode().put("text", "이 매물은 주변보다 {가} {가.방향}.");
        r.putArray("stat_claims").addObject().put("slot", "나").put("value", 33741).put("unit", "만원").put("basis", "시군구")
                .put("start_month", "202610").put("end_month", "202610").put("sample_size", sample).put("provisional", true).put("evidence", "S1.기준");
        r.putArray("value_claims").addObject().put("slot", "가").put("value", 3.7).put("unit", "%").put("source", "계산").put("evidence", "C1.차이");
        r.putArray("context_claims");
        return r;
    }

    static ObjectNode compare() {
        return M.createObjectNode().put("listing_evidence", "매물.가격").put("basis_evidence", "S1.기준");
    }

    static FakeAnthropic.Reply tool(String id, String name, ObjectNode in) {
        return FakeAnthropic.sse(List.of(FakeAnthropic.toolUse(id, name, in)), "tool_use", null);
    }

    static FakeAnthropic.Reply done() {
        return FakeAnthropic.sse(List.of(FakeAnthropic.text("끝")), "end_turn", null);
    }

    static RunRecord run(FakeAnthropic api, Setup s) throws Exception {
        var client = new AnthropicModelClient("test-key", api.url(), new ModelSettings("claude-haiku-5-5", 4000, ModelSettings.Thinking.ADAPTIVE, "high"),
                Duration.ofSeconds(20));
        QuestionLoop loop = new QuestionLoop(client, new Tools(fixture), fixture, s.config());
        return loop.start(s.system(), s.specs(), "이 매물 가격이 주변보다 비싼가요?", Fixture.run("L01", "0", 350_000_000L, 0, "84"));
    }

    static List<Map<String, Object>> events(RunRecord rec, String type) {
        return rec.events().stream().filter(e -> e.get("type").equals(type)).toList();
    }

    static List<String> decisions(RunRecord rec) {
        return events(rec, "hook").stream().map(e -> e.get("point") + " " + e.get("part") + " " + e.get("decision")).toList();
    }

    static Setup setup(Part... parts) {
        return Setup.of(new Variant("t", "시험", "m", Variant.CardMode.TOOLS, List.of(parts)), fixture, cards(), null, null, 10);
    }

    @Test
    void reflexRejectsInTheLoopAndTheModelFixesTheCode() throws Exception {
        try (FakeAnthropic api = new FakeAnthropic()) {
            api.then(tool("t1", "trade_stats", stats("41590", "202610", "202610")));
            api.then(tool("t2", "trade_stats", stats("41591", "202610", "202610")));
            api.then(tool("t3", "compare_prices", compare()));
            api.then(tool("t4", "submit_report", report(4)));
            RunRecord rec = run(api, setup(Part.REFLEX));
            assertEquals(RunRecord.Outcome.SUBMITTED, rec.outcome());
            assertEquals("이 매물은 주변보다 3.7% 비쌉니다.", rec.answer());
            List<Map<String, Object>> tools = events(rec, "tool");
            assertEquals(true, tools.get(0).get("is_error"));
            assertEquals("REJECTED", tools.get(0).get("error_code"));
            assertTrue(((String) tools.get(0).get("result")).contains("맞는 코드: 41591(202610)"));
            assertTrue(((String) tools.get(1).get("result")).contains("\"id\":\"S1\""), "거절된 부르기는 결과 ID를 쓰지 않는다");
            assertEquals(List.of("BEFORE_TOOL ② REJECT", "BEFORE_TOOL ② PASS"), decisions(rec));
        }
    }

    @Test
    void lockRejectsUntilTheCardIsRead() throws Exception {
        try (FakeAnthropic api = new FakeAnthropic()) {
            api.then(tool("t1", "trade_stats", stats("41591", "202610", "202610")));
            api.then(tool("t2", "read_card", M.createObjectNode().put("card_id", "K02")));
            api.then(tool("t3", "trade_stats", stats("41591", "202610", "202610")));
            api.then(done());
            api.then(done());
            RunRecord rec = run(api, setup(Part.LOCK));
            assertEquals(RunRecord.Outcome.FAILED_NO_SUBMIT, rec.outcome());
            List<Map<String, Object>> tools = events(rec, "tool");
            assertTrue(((String) tools.get(0).get("result")).contains("먼저 카드 K02를 읽어라."));
            assertTrue(((String) tools.get(1).get("result")).contains("지역 코드의 핵심"));
            assertEquals(false, tools.get(2).get("is_error"));
            assertEquals(List.of("BEFORE_TOOL ③ REJECT", "BEFORE_TOOL ③ PASS"), decisions(rec));
            assertEquals(8, api.requests.get(0).get("tools").size());
        }
    }

    @Test
    void gateRejectsAWrongReportAndAcceptsTheFix() throws Exception {
        try (FakeAnthropic api = new FakeAnthropic()) {
            api.then(tool("t1", "trade_stats", stats("41591", "202610", "202610")));
            api.then(tool("t2", "compare_prices", compare()));
            api.then(tool("t3", "submit_report", report(5)));
            api.then(tool("t4", "submit_report", report(4)));
            RunRecord rec = run(api, setup(Part.GATE));
            assertEquals(RunRecord.Outcome.SUBMITTED, rec.outcome());
            List<Map<String, Object>> tools = events(rec, "tool");
            assertEquals("REJECTED", tools.get(2).get("error_code"));
            assertTrue(((String) tools.get(2).get("result")).contains("stat_claims[0]: 표본 수는 근거 부르기의 것(4건)이어야 한다"));
            assertEquals(2, events(rec, "submit").size());
            assertEquals(List.of("SUBMIT ④ REJECT", "SUBMIT ④ PASS"), decisions(rec));
        }
    }

    @Test
    void withoutTheGateTheCheckerOnlyRecords() throws Exception {
        try (FakeAnthropic api = new FakeAnthropic()) {
            api.then(tool("t1", "trade_stats", stats("41591", "202610", "202610")));
            api.then(tool("t2", "compare_prices", compare()));
            api.then(tool("t3", "submit_report", report(5)));
            RunRecord rec = run(api, setup());
            assertEquals(RunRecord.Outcome.SUBMITTED, rec.outcome());
            assertEquals(List.of("stat_claims[0] SAMPLE"), events(rec, "submit").get(0).get("findings"));
            assertTrue(decisions(rec).isEmpty());
        }
    }

    @Test
    void allPartsAreAskedAndTheFirstRejectionIsReturned() throws Exception {
        try (FakeAnthropic api = new FakeAnthropic()) {
            api.then(tool("t1", "trade_stats", stats("41590", "202610", "202610")));
            api.then(done());
            api.then(done());
            RunRecord rec = run(api, setup(Part.REFLEX, Part.LOCK));
            assertTrue(((String) events(rec, "tool").get(0).get("result")).contains("먼저 카드 K02를 읽어라."));
            assertEquals(List.of("BEFORE_TOOL ③ REJECT", "BEFORE_TOOL ② REJECT"), decisions(rec));
        }
    }
}
