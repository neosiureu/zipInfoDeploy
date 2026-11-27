package com.zipinfo.ai.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.data.Listing;
import com.zipinfo.ai.data.ListingMeta;
import com.zipinfo.ai.tools.DataSet;
import com.zipinfo.ai.tools.Fixture;
import com.zipinfo.ai.tools.RunState;
import com.zipinfo.ai.tools.Tools;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportTest {
    static final ObjectMapper M = new ObjectMapper();
    static DataSet data;
    static Tools tools;

    @BeforeAll
    static void load(@TempDir Path dir) throws Exception {
        data = Fixture.data(dir);
        tools = new Tools(data);
    }

    /** 시군구 범위 통계 하나와 비교 하나를 한 실행. */
    static RunState ran() {
        return ran(Fixture.run("L01", "0", 350_000_000L, 0, "84"));
    }

    static RunState ran(RunState st) {
        tools.call("trade_stats", M.createObjectNode().put("api", "apt_trade").put("region_code", "41591").put("start_month", "202610")
                .put("end_month", "202610").put("deal", "매매").put("dong", "").put("jibun", ""), st);
        tools.call("compare_prices", M.createObjectNode().put("listing_evidence", "매물.가격").put("basis_evidence", "S1.기준"), st);
        return st;
    }

    /** 매물 이름이 있는 실행(Fixture.run과 같고 이름만 넣는다). */
    static RunState named(String name) {
        RunState base = Fixture.run("L01", "0", 350_000_000L, 0, "84");
        Map<String, String> f = new LinkedHashMap<>(base.listing().fields());
        f.put("stockName", name);
        return new RunState(new Listing("L01", f, base.listing().evidence()), new ListingMeta("L01", "apt|41591|가동|0|12|0", "매매", "화성형", "본시험"));
    }

    static ObjectNode report(String text) {
        ObjectNode r = M.createObjectNode().put("text", text);
        ArrayNode s = r.putArray("stat_claims");
        s.addObject().put("slot", "가").put("value", 33741).put("unit", "만원").put("basis", "시군구").put("start_month", "202610")
                .put("end_month", "202610").put("sample_size", 4).put("provisional", true).put("evidence", "S1.기준");
        ArrayNode v = r.putArray("value_claims");
        v.addObject().put("slot", "나").put("value", 3.7).put("unit", "%").put("source", "계산").put("evidence", "C1.차이");
        v.addObject().put("slot", "다").put("value", 35000).put("unit", "만원").put("source", "매물").put("evidence", "매물.가격");
        r.putArray("context_claims").addObject().put("text", "같은 시군구의 아파트 매매 거래와 견주었습니다").put("evidence", "S1.표본수");
        return r;
    }

    static List<String> codes(ObjectNode r, RunState st) {
        Report p = Report.parse(r);
        return Checker.check(p, st, data.validity(), Renderer.render(p, st)).stream().map(Finding::code).toList();
    }

    @Test
    void goodReportPassesAndRenders() {
        RunState st = ran();
        Report r = Report.parse(report("이 매물은 {다}로 주변 기준값 {가}보다 {나} {나.방향}."));
        String answer = Renderer.render(r, st);
        assertEquals("이 매물은 3억 5,000만원으로 주변 기준값 3억 3,741만원보다 3.7% 비쌉니다.", answer);
        assertEquals(List.of(), Checker.check(r, st, data.validity(), answer));
    }

    @Test
    void mismatchesAreFound() {
        RunState st = ran();
        ObjectNode bad = report("{가} {라} {가.방향} 두 배");
        ObjectNode s0 = (ObjectNode) bad.get("stat_claims").get(0);
        s0.put("basis", "법정동").put("sample_size", 5).put("provisional", false).put("value", 337410000).put("unit", "원");
        assertEquals(List.of("BASIS", "SAMPLE", "PROVISIONAL", "NUMERAL", "PLACEHOLDER", "DIRECTION", "DIFF_HIDDEN", "BRACE", "REGISTER"), codes(bad, st));
        assertEquals("TEXT_FORM", codes(report("1층"), st).get(0));
    }

    @Test
    void noTradeNeedsValidCode() {
        RunState st = Fixture.run("L01", "0", 350_000_000L, 0, "84");
        tools.call("trade_stats", M.createObjectNode().put("api", "apt_trade").put("region_code", "41590").put("start_month", "202610")
                .put("end_month", "202610").put("deal", "매매").put("dong", "가동").put("jibun", ""), st);
        tools.call("trade_stats", M.createObjectNode().put("api", "apt_trade").put("region_code", "41591").put("start_month", "202609")
                .put("end_month", "202609").put("deal", "매매").put("dong", "").put("jibun", ""), st);
        ObjectNode r = M.createObjectNode().put("text", "거래가 {가}입니다.");
        r.putArray("stat_claims");
        r.putArray("value_claims").addObject().put("slot", "가").put("value", 0).put("unit", "건").put("source", "국토부 실거래가").put("evidence", "S1.표본수");
        r.putArray("context_claims");
        Report p = Report.parse(r);
        List<Finding> f = Checker.check(p, st, data.validity(), Renderer.render(p, st));
        assertEquals("ZERO_INVALID", f.get(0).code());
        assertEquals("그 0건은 이 매물 지역에 맞지 않는 코드에서 나왔다", f.get(0).prescription());
        ((ObjectNode) r.get("value_claims").get(0)).put("evidence", "S2.표본수");
        assertEquals(List.of(), codes(r, st));
        assertEquals("거래가 0건입니다.", Renderer.render(Report.parse(r), st));
    }

    @Test
    void duplicateSlotIsFoundAndTheFirstClaimDraws() {
        RunState st = ran(Fixture.run("L01", "0", 300_000_000L, 0, "84"));
        ObjectNode r = report("이 매물은 {가.방향}.");
        ((ObjectNode) r.get("value_claims").get(0)).put("slot", "가").put("value", -11.1);
        assertTrue(codes(r, st).contains("SLOT_DUP"));
        assertEquals("이 매물은 {가.방향}.", Renderer.render(Report.parse(r), st));
        ObjectNode noDiff = report("주변보다 {가.방향}.");
        ((ArrayNode) noDiff.get("value_claims")).remove(0);
        assertEquals("주변보다 {가.방향}.", Renderer.render(Report.parse(noDiff), st));
    }

    @Test
    void directionAndSizeComeFromTheEvidence() {
        RunState st = ran(Fixture.run("L01", "0", 300_000_000L, 0, "84"));
        ObjectNode r = report("이 매물은 기준값 {가}보다 {나} {나.방향}.");
        ((ObjectNode) r.get("value_claims").get(0)).put("value", 11.1);   // 근거는 -11.1(부호 반대 claim)
        ((ObjectNode) r.get("value_claims").get(1)).put("value", 30000);
        assertEquals("이 매물은 기준값 3억 3,741만원보다 11.1% 쌉니다.", Renderer.render(Report.parse(r), st));
        assertEquals(List.of("VALUE"), codes(r, st));
    }

    @Test
    void missingEvidenceLeavesTheSlot() {
        RunState st = ran();
        ObjectNode r = report("이 매물은 {다}이고 기준보다 {나} {나.방향}.");
        ((ObjectNode) r.get("value_claims").get(0)).put("evidence", "C9.차이");
        assertEquals("이 매물은 3억 5,000만원이고 기준보다 {나} {나.방향}.", Renderer.render(Report.parse(r), st));
        List<String> c = codes(r, st);
        assertTrue(c.contains("EVIDENCE_MISSING") && c.contains("BRACE"), c.toString());
    }

    @Test
    void statClaimDiffDrawsDirectionButIsNotStats() {
        RunState st = ran();
        ObjectNode r = report("기준값 {가}보다 {라} {라.방향}.");
        ((ArrayNode) r.get("stat_claims")).addObject().put("slot", "라").put("value", 3.7).put("unit", "%").put("basis", "시군구")
                .put("start_month", "202610").put("end_month", "202610").put("sample_size", 4).put("provisional", true).put("evidence", "C1.차이");
        ((ArrayNode) r.get("value_claims")).remove(0);
        assertEquals("기준값 3억 3,741만원보다 3.7% 비쌉니다.", Renderer.render(Report.parse(r), st));
        assertEquals(List.of("NOT_STATS"), codes(r, st));
    }

    @Test
    void placeholderNamesUpToSixAndBrokenForm() {
        RunState st = ran();
        ObjectNode longName = report("이 매물은 {매물가격}으로 기준보다 {나} {나.방향}.");
        ((ObjectNode) longName.get("value_claims").get(1)).put("slot", "매물가격");
        assertEquals("이 매물은 3억 5,000만원으로 기준보다 3.7% 비쌉니다.", Renderer.render(Report.parse(longName), st));
        assertEquals(List.of(), codes(longName, st));
        ObjectNode broken = report("이 매물은 {다로 기준보다 {나} {나.방향}.");
        List<String> c = codes(broken, st);
        assertTrue(c.contains("PLACEHOLDER_FORM") && c.contains("BRACE"), c.toString());
    }

    @Test
    void periodWordsNeedAStatClaimOfTheSameLength() {
        RunState st = ran();
        ObjectNode r = report("최근 두 달 거래로 보면 기준값 {가}보다 {나} {나.방향}.");
        assertEquals(List.of("PERIOD_WORD"), codes(r, st));
        ObjectNode ok = report("최근 한 달 거래로 보면 기준값 {가}보다 {나} {나.방향}.");
        assertEquals(List.of(), codes(ok, st));
    }

    @Test
    void nameSlotDrawsTheListingNameAndIsReserved() {
        RunState st = ran(named("새솔1"));
        ObjectNode r = report("{매물명}는 기준값 {가}보다 {나} {나.방향}.");
        assertEquals("새솔1은 기준값 3억 3,741만원보다 3.7% 비쌉니다.", Renderer.render(Report.parse(r), st));
        assertEquals(List.of(), codes(r, st));
        ((ObjectNode) r.get("value_claims").get(1)).put("slot", "매물명");
        assertTrue(codes(r, st).contains("SLOT_RESERVED"));
    }

    @Test
    void textChecksSeeTheDrawnAnswer() {
        RunState st = ran();
        assertEquals(List.of("FORMAT"), codes(report("이 매물은 {다}만원만원이고 기준값 {가}보다 {나} {나.방향}."), st).subList(0, 1));
        assertEquals(List.of("REGISTER"), codes(report("이 매물은 기준값 {가}보다 {나} 높다."), st).stream().filter(c -> !c.equals("DIFF_HIDDEN")).toList());
        assertFalse(codes(report("이 매물은 {다}만원입니다. 기준값 {가}보다 {나} {나.방향}."), st).contains("FORMAT"));
    }

    @Test
    void numeralDictionaryV0() {
        assertEquals(List.of("두 배", "세 곳", "삼십평", "삼 개월", "열 건"), Numerals.find("두 배 세 곳 삼십평 삼 개월 열 건"));
        assertEquals(List.of("석 달", "넉 달"), Numerals.find("최근 석 달과 넉 달"));
        assertTrue(Numerals.find("이 곳은 사건이 많다. 단위는 만원이다. 비교한 곳").isEmpty());
    }
}
