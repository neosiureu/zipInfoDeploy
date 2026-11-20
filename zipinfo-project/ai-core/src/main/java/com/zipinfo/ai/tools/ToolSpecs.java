package com.zipinfo.ai.tools;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * 모든 판이 같이 쓰는 도구 여섯의 이름·설명·strict 입력 스키마(설계안 6.1~6.3, 순서 3 메모 2·3절).
 * 설명에는 사고 과정을 적게 하는 말을 넣지 않는다(01 209줄). 기본 12달·잠정 규칙은 카드 5의 몫이라 넣지 않는다(6판 6.2).
 */
public final class ToolSpecs {
    public record Spec(String name, String description, ObjectNode inputSchema) {}

    public static final String TEXT_PATTERN = "^[가-힣A-Za-z .,!?·(){}~-]*$";
    public static final String SLOT_PATTERN = "^[가-힣]{1,3}$";
    public static final List<String> UNITS = List.of("원", "만원", "㎡", "평", "%", "건", "개월", "년", "층");
    public static final List<String> BASES = List.of("단지", "법정동", "시군구");
    public static final List<String> SOURCES = List.of("매물", "국토부 실거래가", "계산");
    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private ToolSpecs() {}

    private static ObjectNode str(String description) {
        ObjectNode n = F.objectNode().put("type", "string");
        return description == null ? n : n.put("description", description);
    }

    private static ObjectNode pattern(String p, String description) {
        return str(description).put("pattern", p);
    }

    private static ObjectNode enumOf(List<String> values, String description) {
        ObjectNode n = str(description);
        ArrayNode a = n.putArray("enum");
        values.forEach(a::add);
        return n;
    }

    /** 모든 칸이 필수이고 다른 칸은 받지 않는 object. */
    private static ObjectNode object(Object... nameAndSchema) {
        ObjectNode o = F.objectNode().put("type", "object");
        ObjectNode props = o.putObject("properties");
        ArrayNode req = o.putArray("required");
        for (int i = 0; i < nameAndSchema.length; i += 2) {
            props.set((String) nameAndSchema[i], (ObjectNode) nameAndSchema[i + 1]);
            req.add((String) nameAndSchema[i]);
        }
        o.put("additionalProperties", false);
        return o;
    }

    private static ObjectNode array(ObjectNode items, String description) {
        ObjectNode n = F.objectNode().put("type", "array").put("description", description);
        n.set("items", items);
        return n;
    }

    public static List<Spec> all() {
        return List.of(
                new Spec("search_address",
                        "도로명주소를 검색해 결과마다 도로명주소, 우편번호, 법정동코드(admCd), 시도·시군구·읍면동·리 이름, 산·본번·부번, 건물관리번호를 돌려준다. 결과마다 근거 ID(J1.1 꼴)가 붙는다.",
                        object("query", str("검색할 주소 글"))),
                new Spec("trade_stats",
                        "국토교통부 아파트·연립다세대·오피스텔 실거래 자료(계약 월 202311~202610)에서 한 시군구 코드의 거래를 골라 표본 수와 기준값을 돌려준다. "
                                + "api: apt 아파트, rh 연립다세대, offi 오피스텔 / trade 매매, rent 전세·월세. "
                                + "dong을 쓰면 그 법정동만 고르고(리는 '읍면 리' 꼴), jibun까지 쓰면 그 지번만 고른다. 둘 다 빈 글이면 시군구 전체다. "
                                + "기준값은 고른 거래마다 전용면적 ㎡당 금액을 내어 그 중앙값에 매물 전용면적을 곱한 값(만원)이다. 월세는 보증금·월세·환산 기준값을 낸다. "
                                + "값마다 근거 ID(S1.기준 꼴)가 붙는다. 달별 건수는 그 코드로 받은 전체 건수다.",
                        object("api", enumOf(Tools.APIS, "실거래 자료 종류"),
                                "region_code", pattern("^[0-9]{5}$", "시군구 코드(숫자 다섯 자리)"),
                                "start_month", pattern("^[0-9]{6}$", "시작 계약 월(YYYYMM)"),
                                "end_month", pattern("^[0-9]{6}$", "끝 계약 월(YYYYMM)"),
                                "deal", enumOf(List.of("매매", "전세", "월세"), "거래"),
                                "dong", str("법정동 이름. 빈 글이면 시군구 전체"),
                                "jibun", str("지번(예: 12-3, 산 12-3). 빈 글이면 법정동 전체"))),
                new Spec("compare_prices",
                        "매물 값과 기준값을 원 단위로 맞춰 차이(%)를 낸다. 차이 = (매물 − 기준) ÷ 기준 × 100, 소수 첫째 자리. "
                                + "매물 쪽은 매물.가격·매물.월세·R?.환산보증금, 기준 쪽은 S?.기준·S?.보증금기준·S?.월세기준·S?.환산기준 가운데 하나다.",
                        object("listing_evidence", str("매물 쪽 근거 ID"), "basis_evidence", str("기준 쪽 근거 ID"))),
                new Spec("convert_rent",
                        "월세 매물의 보증금과 월세를 정해 둔 전환율로 환산보증금(원)으로 바꾼다. 환산보증금 = 보증금 + 월세 × 12 ÷ 전환율.",
                        object("deposit_evidence", str("보증금 근거 ID(매물.가격)"), "monthly_evidence", str("월세 근거 ID(매물.월세)"))),
                new Spec("convert_area",
                        "매물 전용면적(매물.면적)을 ㎡나 평으로 바꾼다.",
                        object("area_evidence", str("면적 근거 ID(매물.면적)"), "to_unit", enumOf(List.of("㎡", "평"), "바꿀 단위"))),
                new Spec("submit_report",
                        "최종 답을 낸다. text에는 숫자를 쓰지 않는다. 값은 {가}처럼 자리표시자로 쓰고, 자리표시자마다 같은 slot의 claim을 하나 낸다. "
                                + "비싸다·싸다 같은 방향 말은 쓰지 않고 {가.방향}으로 쓴다. 차이 claim(근거 C?.차이)의 {가}는 부호 없는 크기로, {가.방향}은 비싸다·싸다·같다 가운데 하나로 그려진다. "
                                + "stat_claims는 trade_stats 결과의 값이고, value_claims는 값 하나(매물 칸, 실거래 값, 계산 결과)이며, context_claims는 숫자 없는 근거 문장이다. "
                                + "모든 claim에 근거 ID를 적는다.",
                        object("text", pattern(TEXT_PATTERN, "숫자 없는 답 글"),
                                "stat_claims", array(object(
                                        "slot", pattern(SLOT_PATTERN, "자리표시자 이름"),
                                        "value", F.objectNode().put("type", "number"),
                                        "unit", enumOf(UNITS, "단위"),
                                        "basis", enumOf(BASES, "비교 기준"),
                                        "start_month", pattern("^[0-9]{6}$", "기간 시작(YYYYMM)"),
                                        "end_month", pattern("^[0-9]{6}$", "기간 끝(YYYYMM)"),
                                        "sample_size", F.objectNode().put("type", "integer"),
                                        "provisional", F.objectNode().put("type", "boolean"),
                                        "evidence", str("근거 ID")), "통계 claim"),
                                "value_claims", array(object(
                                        "slot", pattern(SLOT_PATTERN, "자리표시자 이름"),
                                        "value", F.objectNode().put("type", "number"),
                                        "unit", enumOf(UNITS, "단위"),
                                        "source", enumOf(SOURCES, "출처"),
                                        "evidence", str("근거 ID")), "단일값 claim"),
                                "context_claims", array(object(
                                        "text", pattern(TEXT_PATTERN, "숫자 없는 근거 문장"),
                                        "evidence", str("근거 ID")), "문맥 claim"))));
    }
}
