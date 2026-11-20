package com.zipinfo.ai.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.tools.DataSet;
import com.zipinfo.ai.tools.Fixture;
import com.zipinfo.ai.tools.RunState;
import com.zipinfo.ai.tools.Tools;
import java.nio.file.Path;
import java.util.List;
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
        RunState st = Fixture.run("L01", "0", 350_000_000L, 0, "84");
        tools.call("trade_stats", M.createObjectNode().put("api", "apt_trade").put("region_code", "41591").put("start_month", "202610")
                .put("end_month", "202610").put("deal", "매매").put("dong", "").put("jibun", ""), st);
        tools.call("compare_prices", M.createObjectNode().put("listing_evidence", "매물.가격").put("basis_evidence", "S1.기준"), st);
        return st;
    }

    static ObjectNode report(String text) {
        ObjectNode r = M.createObjectNode().put("text", text);
        ArrayNode s = r.putArray("stat_claims");
        s.addObject().put("slot", "가").put("value", 33741).put("unit", "만원").put("basis", "시군구").put("start_month", "202610")
                .put("end_month", "202610").put("sample_size", 4).put("provisional", true).put("evidence", "S1.기준");
        ArrayNode v = r.putArray("value_claims");
        v.addObject().put("slot", "나").put("value", 3.7).put("unit", "%").put("source", "계산").put("evidence", "C1.차이");
        v.addObject().put("slot", "다").put("value", 35000).put("unit", "만원").put("source", "매물").put("evidence", "매물.가격");
        r.putArray("context_claims").addObject().put("text", "같은 시군구의 아파트 매매 거래와 견주었다").put("evidence", "S1.표본수");
        return r;
    }

    @Test
    void goodReportPassesAndRenders() {
        RunState st = ran();
        Report r = Report.parse(report("이 매물은 {다}로 주변 기준값 {가}보다 {나} {나.방향}."));
        assertEquals(List.of(), Checker.check(r, st, data.validity()));
        assertEquals("이 매물은 35,000만원로 주변 기준값 33,741만원보다 3.7% 비싸다.", Renderer.render(r, st));
    }

    @Test
    void mismatchesAreFound() {
        RunState st = ran();
        ObjectNode bad = report("{가} {라} {가.방향} 두 배");
        ObjectNode s0 = (ObjectNode) bad.get("stat_claims").get(0);
        s0.put("basis", "법정동").put("sample_size", 5).put("provisional", false).put("value", 337410000).put("unit", "원");
        List<String> codes = Checker.check(Report.parse(bad), st, data.validity()).stream().map(Finding::code).toList();
        assertEquals(List.of("BASIS", "SAMPLE", "PROVISIONAL", "NUMERAL", "PLACEHOLDER", "DIRECTION"), codes);
        ObjectNode digits = report("1층");
        assertEquals("TEXT_FORM", Checker.check(Report.parse(digits), st, data.validity()).get(0).code());
    }

    @Test
    void noTradeNeedsValidCode() {
        RunState st = Fixture.run("L01", "0", 350_000_000L, 0, "84");
        tools.call("trade_stats", M.createObjectNode().put("api", "apt_trade").put("region_code", "41590").put("start_month", "202610")
                .put("end_month", "202610").put("deal", "매매").put("dong", "가동").put("jibun", ""), st);
        tools.call("trade_stats", M.createObjectNode().put("api", "apt_trade").put("region_code", "41591").put("start_month", "202609")
                .put("end_month", "202609").put("deal", "매매").put("dong", "").put("jibun", ""), st);
        ObjectNode r = M.createObjectNode().put("text", "거래가 {가}.");
        r.putArray("stat_claims");
        r.putArray("value_claims").addObject().put("slot", "가").put("value", 0).put("unit", "건").put("source", "국토부 실거래가").put("evidence", "S1.표본수");
        r.putArray("context_claims");
        List<Finding> f = Checker.check(Report.parse(r), st, data.validity());
        assertEquals("ZERO_INVALID", f.get(0).code());
        assertEquals("그 0건은 이 매물 지역에 맞지 않는 코드에서 나왔다", f.get(0).prescription());
        ((ObjectNode) r.get("value_claims").get(0)).put("evidence", "S2.표본수");
        assertEquals(List.of(), Checker.check(Report.parse(r), st, data.validity()));
    }

    @Test
    void duplicateSlotIsFoundAndDirectionComesOnlyFromDiff() {
        RunState st = Fixture.run("L01", "0", 300_000_000L, 0, "84");
        tools.call("trade_stats", M.createObjectNode().put("api", "apt_trade").put("region_code", "41591").put("start_month", "202610")
                .put("end_month", "202610").put("deal", "매매").put("dong", "").put("jibun", ""), st);
        tools.call("compare_prices", M.createObjectNode().put("listing_evidence", "매물.가격").put("basis_evidence", "S1.기준"), st);
        ObjectNode r = report("이 매물은 {가.방향}.");
        ((ObjectNode) r.get("value_claims").get(0)).put("slot", "가").put("value", -11.1);
        List<String> codes = Checker.check(Report.parse(r), st, data.validity()).stream().map(Finding::code).toList();
        assertTrue(codes.contains("SLOT_DUP"), codes.toString());
        assertEquals("이 매물은 싸다.", Renderer.render(Report.parse(r), st));
        ObjectNode noDiff = report("주변보다 {가.방향}.");
        ((ArrayNode) noDiff.get("value_claims")).remove(0);
        assertEquals("주변보다 {가.방향}.", Renderer.render(Report.parse(noDiff), st));
    }

    @Test
    void noTradeAsStatClaimIsCheckedToo() {
        RunState st = Fixture.run("L01", "0", 350_000_000L, 0, "84");
        tools.call("trade_stats", M.createObjectNode().put("api", "apt_trade").put("region_code", "41590").put("start_month", "202610")
                .put("end_month", "202610").put("deal", "매매").put("dong", "가동").put("jibun", ""), st);
        ObjectNode r = M.createObjectNode().put("text", "거래가 {가}.");
        r.putArray("stat_claims").addObject().put("slot", "가").put("value", 0).put("unit", "건").put("basis", "법정동")
                .put("start_month", "202610").put("end_month", "202610").put("sample_size", 0).put("provisional", true).put("evidence", "S1.표본수");
        r.putArray("value_claims");
        r.putArray("context_claims");
        List<String> codes = Checker.check(Report.parse(r), st, data.validity()).stream().map(Finding::code).toList();
        assertEquals(List.of("ZERO_INVALID"), codes);
    }

    @Test
    void numeralDictionaryV0() {
        assertEquals(List.of("두 배", "세 곳", "삼십평", "삼 개월", "열 건"), Numerals.find("두 배 세 곳 삼십평 삼 개월 열 건"));
        assertEquals(List.of("석 달", "넉 달"), Numerals.find("최근 석 달과 넉 달"));
        assertTrue(Numerals.find("이 곳은 사건이 많다. 단위는 만원이다. 비교한 곳").isEmpty());
    }
}
