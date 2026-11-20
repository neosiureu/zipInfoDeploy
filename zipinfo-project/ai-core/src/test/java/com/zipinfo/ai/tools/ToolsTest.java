package com.zipinfo.ai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.num.Q;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolsTest {
    static final ObjectMapper M = new ObjectMapper();
    static Tools tools;

    @BeforeAll
    static void load(@TempDir Path dir) throws Exception {
        tools = new Tools(Fixture.data(dir));
    }

    static ObjectNode stats(String api, String code, String s, String e, String deal, String dong, String jibun) {
        return M.createObjectNode().put("api", api).put("region_code", code).put("start_month", s).put("end_month", e)
                .put("deal", deal).put("dong", dong).put("jibun", jibun);
    }

    static JsonNode ok(ToolResult r) throws Exception {
        assertFalse(r.isError(), r.text());
        return M.readTree(r.text());
    }

    static String err(ToolResult r) {
        assertTrue(r.isError(), r.text());
        return r.errorCode();
    }

    @Test
    void scopesAndBasisFollow93() throws Exception {
        RunState st = Fixture.run("L01", "0", 350_000_000L, 0, "84");
        JsonNode a = ok(tools.call("trade_stats", stats("apt_trade", "41591", "202610", "202610", "매매", "", ""), st));
        assertEquals("S1", a.get("id").asText());
        assertEquals("시군구", a.get("조건").get("비교기준").asText());
        assertEquals(4, a.get("S1.표본수").get("값").asInt());
        assertEquals(33741, a.get("S1.기준").get("값").asInt());
        assertEquals(4, a.get("달별건수(거르기 전)").get("202610").asInt());
        JsonNode b = ok(tools.call("trade_stats", stats("apt_trade", "41591", "202610", "202610", "매매", " 가동 ", ""), st));
        assertEquals("법정동", b.get("조건").get("비교기준").asText());
        assertEquals(33600, b.get("S2.기준").get("값").asInt());
        JsonNode c = ok(tools.call("trade_stats", stats("apt_trade", "41591", "202610", "202610", "매매", "가동", "12-0"), st));
        assertEquals("단지", c.get("조건").get("비교기준").asText());
        assertEquals(2, c.get("S3.표본수").get("값").asInt());
        assertEquals(31770, c.get("S3.기준").get("값").asInt());
        JsonNode d = ok(tools.call("trade_stats", stats("apt_trade", "41591", "202610", "202610", "매매", "나동", "산 7-3"), st));
        assertEquals(43680, d.get("S4.기준").get("값").asInt());
        assertEquals(Q.of(43680), st.find("S4.기준").value());
    }

    @Test
    void zeroSampleHasNoBasisAndOldCodeGivesZero() throws Exception {
        RunState st = Fixture.run("L01", "0", 350_000_000L, 0, "84");
        JsonNode a = ok(tools.call("trade_stats", stats("apt_trade", "41591", "202609", "202609", "매매", "", ""), st));
        assertEquals(0, a.get("S1.표본수").get("값").asInt());
        assertTrue(a.get("S1.기준").get("값").isNull());
        JsonNode b = ok(tools.call("trade_stats", stats("apt_trade", "41590", "202610", "202610", "매매", "가동", ""), st));
        assertEquals(0, b.get("S2.표본수").get("값").asInt());
    }

    @Test
    void riAndDealMismatchAreErrorsNotZero() throws Exception {
        RunState st = Fixture.run("L01", "0", 350_000_000L, 0, "84");
        assertEquals("BAD_INPUT", err(tools.call("trade_stats", stats("apt_trade", "41591", "202610", "202610", "전세", "", ""), st)));
        assertEquals("BAD_INPUT", err(tools.call("trade_stats", stats("apt_rent", "41591", "202610", "202610", "매매", "", ""), st)));
        assertEquals("BAD_INPUT", err(tools.call("trade_stats", stats("rh_trade", "41591", "202610", "202610", "매매", "봉담읍", ""), st)));
        assertEquals("UNKNOWN_DONG", err(tools.call("trade_stats", stats("rh_trade", "41591", "202610", "202610", "매매", "와우리", ""), st)));
        JsonNode a = ok(tools.call("trade_stats", stats("rh_trade", "41591", "202610", "202610", "매매", "봉담읍  와우리", "3"), st));
        assertEquals(1, a.get("S1.표본수").get("값").asInt());
        assertEquals("단지", a.get("조건").get("비교기준").asText());
    }

    @Test
    void errorsAreNotEmptyResults() {
        RunState st = Fixture.run("L01", "0", 350_000_000L, 0, "84");
        assertEquals("SNAPSHOT_MISS", err(tools.call("trade_stats", stats("apt_trade", "41591", "202610", "202611", "매매", "", ""), st)));
        assertEquals("SNAPSHOT_MISS", err(tools.call("trade_stats", stats("offi_trade", "41591", "202610", "202610", "매매", "", ""), st)));
        assertEquals("UNKNOWN_DONG", err(tools.call("trade_stats", stats("apt_trade", "41591", "202610", "202610", "매매", "다동", ""), st)));
        assertEquals("UNKNOWN_DONG", err(tools.call("trade_stats", stats("apt_trade", "41590", "202610", "202610", "매매", "나동", ""), st)));
        assertEquals("BAD_INPUT", err(tools.call("trade_stats", stats("apt_trade", "41591", "202610", "202609", "매매", "", ""), st)));
        assertEquals("BAD_INPUT", err(tools.call("trade_stats", stats("apt_trade", "41591", "202610", "202610", "매매", "", "12"), st)));
        assertEquals("BAD_INPUT", err(tools.call("trade_stats", stats("apt_trade", "41591", "202610", "202610", "매매", "가동", "12번지"), st)));
        // 법정동코드처럼 긴 숫자를 jibun에 넣은 실수: 예외로 멈추지도, 0건이 나오지도 않고 BAD_INPUT(상대 3b·4 검토 가)
        assertEquals("BAD_INPUT", err(tools.call("trade_stats", stats("apt_trade", "41591", "202610", "202610", "매매", "가동", "4159110100"), st)));
        assertEquals("BAD_INPUT", err(tools.call("trade_stats", stats("apt_trade", "41591", "202610", "202610", "매매", "가동", "12-34567"), st)));
        assertEquals("UNKNOWN_EVIDENCE", err(tools.call("compare_prices", M.createObjectNode().put("listing_evidence", "매물.가격").put("basis_evidence", "S9.기준"), st)));
        assertEquals(null, st.find("S1.기준"));
    }

    @Test
    void rentBasisUsesShownValues() throws Exception {
        RunState st = Fixture.run("L02", "2", 100_000_000L, 500_000L, "84");
        JsonNode a = ok(tools.call("trade_stats", stats("apt_rent", "41591", "202610", "202610", "월세", "가동", "12"), st));
        assertEquals(7443, a.get("S1.보증금기준").get("값").asInt());
        assertEquals(75, a.get("S1.월세기준").get("값").asInt());
        assertEquals(25443, a.get("S1.환산기준").get("값").asInt());
        JsonNode r = ok(tools.call("convert_rent", M.createObjectNode().put("deposit_evidence", "매물.가격").put("monthly_evidence", "매물.월세"), st));
        assertEquals("5.0", r.get("R1.전환율").get("값").asText());
        assertEquals(220_000_000L, r.get("R1.환산보증금").get("값").asLong());
        JsonNode c = ok(tools.call("compare_prices", M.createObjectNode().put("listing_evidence", "R1.환산보증금").put("basis_evidence", "S1.환산기준"), st));
        assertEquals(254_430_000L, c.get("C1.기준").get("값").asLong());
        assertEquals(Q.of(-135, 10), st.find("C1.차이").value());
        assertEquals("BAD_EVIDENCE", err(tools.call("convert_rent", M.createObjectNode().put("deposit_evidence", "S1.보증금기준").put("monthly_evidence", "매물.월세"), st)));
        JsonNode j = ok(tools.call("trade_stats", stats("apt_rent", "41591", "202610", "202610", "전세", "", ""), st));
        assertEquals(42353, j.get("S2.기준").get("값").asInt());
    }

    @Test
    void compareRoundsNegativeTieTowardPlusInfinity() throws Exception {
        assertEquals(Q.of(37, 10), Tools.diffPercent(Q.of(350_000_000L), Q.of(33741L * 10000)));
        assertEquals(Q.of(-122, 10), Tools.diffPercent(Q.of(877_500_000L), Q.of(1_000_000_000L)));
    }

    @Test
    void areaAndAddress() throws Exception {
        RunState st = Fixture.run("L01", "0", 350_000_000L, 0, "84");
        JsonNode a = ok(tools.call("convert_area", M.createObjectNode().put("area_evidence", "매물.면적").put("to_unit", "평"), st));
        assertEquals("25.41", a.get("A1.값").get("값").asText());
        JsonNode j = ok(tools.call("search_address", M.createObjectNode().put("query", "18000^^^경기도  화성시 만세구 가로 1^^^"), st));
        assertEquals(1, j.get("결과수").asInt());
        assertEquals("J1.1", j.get("결과").get(0).get("근거").asText());
        assertEquals("4159110100", j.get("결과").get(0).get("법정동코드").asText());
        JsonNode k = ok(tools.call("search_address", M.createObjectNode().put("query", "경기도 화성시 만세구 가로 1"), st));
        assertEquals("J2.1", k.get("결과").get(0).get("근거").asText());
        assertEquals("SNAPSHOT_MISS", err(tools.call("search_address", M.createObjectNode().put("query", "경기도 화성시 가로 1"), st)));
    }
}
