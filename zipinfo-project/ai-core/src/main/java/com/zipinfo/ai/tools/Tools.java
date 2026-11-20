package com.zipinfo.ai.tools;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zipinfo.ai.data.AddressBook;
import com.zipinfo.ai.data.Trade;
import com.zipinfo.ai.num.Q;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 도구 다섯의 실행(설계안 6.2, 순서 3 메모 3절). 장치는 여기에 없다. 고리가 부르기 전에 거는 자리를 돈다. */
public final class Tools {
    public static final List<String> APIS = List.of("apt_rent", "apt_trade", "offi_rent", "offi_trade", "rh_rent", "rh_trade");
    public static final Set<String> DEALS = Set.of("매매", "전세", "월세");
    public static final String FIRST_MONTH = "202311";
    public static final String LAST_MONTH = "202610";
    /** 전환율 v0 = 5.0%(계획 3절, 설계안 9.3). */
    public static final Q RATE = Q.of(5, 100);
    private static final Q MANWON = Q.of(10000);
    private static final Q TENTH = Q.of(1, 10);
    private static final Q HUNDREDTH = Q.of(1, 100);
    private static final Q PYEONG = Q.of(121, 400);
    private static final Pattern MONTH = Pattern.compile("[0-9]{4}(0[1-9]|1[0-2])");
    private static final Pattern CODE = Pattern.compile("[0-9]{5}");
    /** 본번·부번은 넉 자리까지다(재생 저장소의 최대 3106·4161). 더 긴 숫자(법정동코드 등)는 BAD_INPUT(상대 3b·4 검토 가). */
    private static final Pattern JIBUN = Pattern.compile("(산)?\\s*([0-9]{1,4})(?:-([0-9]{1,4}))?");
    private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    static final ObjectMapper JSON = new ObjectMapper().configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);

    private final DataSet data;

    public Tools(DataSet data) {
        this.data = data;
    }

    /** 도구 하나를 돌린다. 오류는 is_error 결과로 돌려준다(빈 결과가 아니다). */
    public ToolResult call(String name, JsonNode input, RunState st) {
        try {
            Map<String, Object> out = switch (name) {
                case "search_address" -> searchAddress(text(input, "query"), st);
                case "trade_stats" -> tradeStats(text(input, "api"), text(input, "region_code"), text(input, "start_month"),
                        text(input, "end_month"), text(input, "deal"), text(input, "dong"), text(input, "jibun"), st);
                case "compare_prices" -> comparePrices(text(input, "listing_evidence"), text(input, "basis_evidence"), st);
                case "convert_rent" -> convertRent(text(input, "deposit_evidence"), text(input, "monthly_evidence"), st);
                case "convert_area" -> convertArea(text(input, "area_evidence"), text(input, "to_unit"), st);
                default -> throw new ToolError("BAD_INPUT", "없는 도구다: " + name);
            };
            return new ToolResult(write(out), false, "");
        } catch (ToolError e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("오류", e.code());
            err.put("설명", e.getMessage());
            return new ToolResult(write(err), true, e.code());
        }
    }

    static String write(Map<String, Object> m) {
        try {
            return JSON.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(JsonNode in, String field) throws ToolError {
        JsonNode v = in == null ? null : in.get(field);
        if (v == null || !v.isTextual()) {
            throw new ToolError("BAD_INPUT", field + " 칸이 없거나 글이 아니다");
        }
        return v.asText();
    }

    static String norm(String s) {
        return SPACES.matcher(Normalizer.normalize(s, Normalizer.Form.NFC)).replaceAll(" ").strip();
    }

    private static Map<String, Object> val(Q v, String unit, int scale) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("값", v == null ? null : v.toDecimal(scale));
        m.put("단위", unit);
        return m;
    }

    // ---------------------------------------------------------------- search_address

    Map<String, Object> searchAddress(String query, RunState st) throws ToolError {
        List<Map<String, String>> res = data.addresses().lookup(query);
        if (res == null) {
            throw new ToolError("SNAPSHOT_MISS", "스냅샷에 없는 주소 검색어다. 얼린 검색어는 문항 매물의 주소 칸뿐이다");
        }
        String id = st.nextId("J");
        List<Object> rows = new ArrayList<>();
        int k = 0;
        for (Map<String, String> r : res) {
            k++;
            String eid = id + "." + k;
            st.put(new Evidence(eid, Evidence.Kind.J_RESULT, null, "", null));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("근거", eid);
            m.put("도로명주소", r.get("roadAddrPart1"));
            m.put("우편번호", r.get("zipNo"));
            m.put("법정동코드", r.get("admCd"));
            m.put("시도", r.get("siNm"));
            m.put("시군구", r.get("sggNm"));
            m.put("읍면동", r.get("emdNm"));
            m.put("리", r.get("liNm"));
            m.put("산", r.get("mtYn"));
            m.put("본번", r.get("lnbrMnnm"));
            m.put("부번", r.get("lnbrSlno"));
            m.put("건물관리번호", r.get("bdMgtSn"));
            rows.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("검색어", AddressBook.clean(query));
        out.put("결과수", res.size());
        out.put("결과", rows);
        return out;
    }

    // ---------------------------------------------------------------- trade_stats

    /** 9.3: 거래마다 금액 ÷ 면적을 분수로, 중앙값(짝수면 가운데 둘의 평균) × 매물 면적을 만원으로 한 번 반올림. */
    public static Q basis(List<Q> perArea, Q listingArea) {
        List<Q> v = new ArrayList<>(perArea);
        v.sort(null);
        int n = v.size();
        Q med = n % 2 == 1 ? v.get(n / 2) : v.get(n / 2 - 1).add(v.get(n / 2)).div(Q.of(2));
        return med.mul(listingArea).roundUnit(Q.ONE);
    }

    /** 9.3: 환산보증금 = 보증금 + 월세 × 12 ÷ 전환율. 만원 정수를 넣어 분수로 셈하고 만원으로 한 번 반올림. */
    public static Q converted(Q depositManwon, Q monthlyManwon) {
        return depositManwon.add(monthlyManwon.mul(Q.of(12)).div(RATE)).roundUnit(Q.ONE);
    }

    /** 9.3: (매물 − 기준) ÷ 기준 × 100을 ⌊x/0.1 + 1/2⌋ × 0.1로(음수도 같은 식). */
    public static Q diffPercent(Q listing, Q basis) {
        return listing.sub(basis).div(basis).mul(Q.of(100)).roundUnit(TENTH);
    }

    static List<String> months(String start, String end) {
        List<String> out = new ArrayList<>();
        int y = Integer.parseInt(start.substring(0, 4)), m = Integer.parseInt(start.substring(4));
        int ey = Integer.parseInt(end.substring(0, 4)), em = Integer.parseInt(end.substring(4));
        while (y < ey || (y == ey && m <= em)) {
            out.add(String.format("%04d%02d", y, m));
            if (++m == 13) {
                y++;
                m = 1;
            }
        }
        return out;
    }

    Map<String, Object> tradeStats(String api, String code, String start, String end, String deal, String dongIn,
                                   String jibunIn, RunState st) throws ToolError {
        if (!APIS.contains(api)) {
            throw new ToolError("BAD_INPUT", "api는 " + String.join("·", APIS) + " 가운데 하나다");
        }
        if (!CODE.matcher(code).matches()) {
            throw new ToolError("BAD_INPUT", "region_code는 다섯 자리 숫자다");
        }
        if (!MONTH.matcher(start).matches() || !MONTH.matcher(end).matches()) {
            throw new ToolError("BAD_INPUT", "start_month·end_month는 YYYYMM 꼴이다");
        }
        if (start.compareTo(end) > 0) {
            throw new ToolError("BAD_INPUT", "end_month가 start_month보다 앞이다");
        }
        if (!DEALS.contains(deal)) {
            throw new ToolError("BAD_INPUT", "deal은 매매·전세·월세 가운데 하나다");
        }
        // 실제 API에 없는 0건을 도구가 만들지 않는다(01 309줄, 상대 3a 검토 2): trade는 매매만, rent는 전세·월세만.
        if (api.endsWith("_trade") != deal.equals("매매")) {
            throw new ToolError("BAD_INPUT", "trade API는 매매만, rent API는 전세·월세만 받는다");
        }
        List<String> ms = months(start, end);
        for (String m : ms) {
            if (m.compareTo(FIRST_MONTH) < 0 || m.compareTo(LAST_MONTH) > 0 || data.trades().received(api, code, m) == null) {
                throw new ToolError("SNAPSHOT_MISS", "스냅샷에 없는 부르기다: " + api + " " + code + " " + m);
            }
        }
        String dong = norm(dongIn);
        String jibun = norm(jibunIn);
        if (dong.isEmpty() && !jibun.isEmpty()) {
            throw new ToolError("BAD_INPUT", "jibun을 쓰려면 dong도 써야 한다");
        }
        if (!dong.isEmpty() && !data.history().hasDong(code, dong)) {
            throw new ToolError("UNKNOWN_DONG", "그 코드 아래에서 받은 창 안에 그 이름의 법정동이 없었다: " + dong);
        }
        // 리가 있는 읍·면 이름만 주면 리의 거래가 빠진 결과가 된다(상대 3a 검토 1). 0건 대신 오류로 돌려준다.
        if (!dong.isEmpty() && data.history().hasRiUnder(code, dong)) {
            throw new ToolError("BAD_INPUT", "읍·면 아래에 리가 있으면 dong은 '읍면 리' 꼴로 쓴다: " + dong);
        }
        int[] jb = null;
        if (!jibun.isEmpty()) {
            Matcher jm = JIBUN.matcher(jibun);
            if (!jm.matches()) {
                throw new ToolError("BAD_INPUT", "jibun은 '12', '12-3', '산 12-3' 꼴이다");
            }
            jb = new int[] {jm.group(1) != null ? 1 : 0, Integer.parseInt(jm.group(2)), jm.group(3) == null ? 0 : Integer.parseInt(jm.group(3))};
        }
        boolean rent = api.endsWith("_rent");
        Map<String, Object> monthly = new LinkedHashMap<>();
        List<Trade> picked = new ArrayList<>();
        for (String m : ms) {
            monthly.put(m, data.trades().received(api, code, m));
            for (Trade t : data.trades().trades(api, code, m)) {
                if (!t.deal().equals(deal)) {
                    continue;
                }
                if (!dong.isEmpty() && !t.path().equals(dong)) {
                    continue;
                }
                if (jb != null && (t.mtn() != jb[0] || t.bon() != jb[1] || t.bu() != jb[2])) {
                    continue;
                }
                picked.add(t);
            }
        }
        String scope = jb != null ? "단지" : (!dong.isEmpty() ? "법정동" : "시군구");
        String id = st.nextId("S");
        StatsCall call = new StatsCall(id, api, code, start, end, deal, dong, jibun, scope, picked.size(), List.copyOf(ms));
        Q area = Q.parse(st.listing().fields().get("exclusiveArea"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        Map<String, Object> cond = new LinkedHashMap<>();
        cond.put("api", api);
        cond.put("region_code", code);
        cond.put("기간", start + "~" + end);
        cond.put("deal", deal);
        cond.put("dong", dong);
        cond.put("jibun", jibun);
        cond.put("비교기준", scope);
        cond.put("매물면적", area.toDecimal(0) + "㎡");
        out.put("조건", cond);
        out.put("달별건수(거르기 전)", monthly);
        Q sample = Q.of(picked.size());
        st.put(new Evidence(id + ".표본수", Evidence.Kind.S_SAMPLE, sample, "건", call));
        out.put(id + ".표본수", val(sample, "건", 0));
        if (!rent || deal.equals("전세")) {
            List<Q> per = new ArrayList<>();
            for (Trade t : picked) {
                per.add(Q.of(rent ? t.deposit() : t.amount()).div(t.area()));
            }
            Q b = picked.isEmpty() ? null : basis(per, area);
            if (!picked.isEmpty()) {
                out.put("㎡당중앙값(근거 아님)", median(per).approx(2) + "만원/㎡");
            }
            st.put(new Evidence(id + ".기준", Evidence.Kind.S_BASIS, b, "만원", call));
            out.put(id + ".기준", val(b, "만원", 0));
        } else {
            List<Q> dep = new ArrayList<>(), mon = new ArrayList<>();
            for (Trade t : picked) {
                dep.add(Q.of(t.deposit()).div(t.area()));
                mon.add(Q.of(t.monthlyRent()).div(t.area()));
            }
            Q d = picked.isEmpty() ? null : basis(dep, area);
            Q mo = picked.isEmpty() ? null : basis(mon, area);
            Q c = picked.isEmpty() ? null : converted(d, mo);
            st.put(new Evidence(id + ".보증금기준", Evidence.Kind.S_DEPOSIT, d, "만원", call));
            st.put(new Evidence(id + ".월세기준", Evidence.Kind.S_MONTHLY, mo, "만원", call));
            st.put(new Evidence(id + ".환산기준", Evidence.Kind.S_CONVERTED, c, "만원", call));
            out.put(id + ".보증금기준", val(d, "만원", 0));
            out.put(id + ".월세기준", val(mo, "만원", 0));
            out.put(id + ".환산기준", val(c, "만원", 0));
            out.put("환산식", "보증금기준 + 월세기준 × 12 ÷ 전환율 " + RATE.mul(Q.of(100)).toDecimal(1) + "%");
        }
        return out;
    }

    static Q median(List<Q> values) {
        List<Q> v = new ArrayList<>(values);
        v.sort(null);
        int n = v.size();
        return n % 2 == 1 ? v.get(n / 2) : v.get(n / 2 - 1).add(v.get(n / 2)).div(Q.of(2));
    }

    // ---------------------------------------------------------------- compare_prices

    private static Evidence need(RunState st, String id) throws ToolError {
        Evidence e = st.find(id);
        if (e == null) {
            throw new ToolError("UNKNOWN_EVIDENCE", "이 실행에 없는 근거 ID다: " + norm(id));
        }
        return e;
    }

    Map<String, Object> comparePrices(String listingId, String basisId, RunState st) throws ToolError {
        Evidence le = need(st, listingId);
        Evidence be = need(st, basisId);
        if (!Set.of(Evidence.Kind.LISTING_PRICE, Evidence.Kind.LISTING_MONTHLY, Evidence.Kind.R_CONVERTED).contains(le.kind())) {
            throw new ToolError("BAD_EVIDENCE", "listing_evidence는 매물.가격, 매물.월세, R?.환산보증금 가운데 하나다");
        }
        if (!Set.of(Evidence.Kind.S_BASIS, Evidence.Kind.S_DEPOSIT, Evidence.Kind.S_MONTHLY, Evidence.Kind.S_CONVERTED).contains(be.kind())) {
            throw new ToolError("BAD_EVIDENCE", "basis_evidence는 trade_stats의 기준값(S?.기준, S?.보증금기준, S?.월세기준, S?.환산기준) 가운데 하나다");
        }
        if (be.value() == null || be.value().signum() == 0) {
            throw new ToolError("BAD_EVIDENCE", "기준값이 없거나 0이다(표본 0건)");
        }
        Q lv = le.value();
        Q bv = be.value().mul(MANWON);
        Q d = diffPercent(lv, bv);
        String id = st.nextId("C");
        st.put(new Evidence(id + ".매물", Evidence.Kind.C_LISTING, lv, "원", null));
        st.put(new Evidence(id + ".기준", Evidence.Kind.C_BASIS, bv, "원", null));
        st.put(new Evidence(id + ".차이", Evidence.Kind.C_DIFF, d, "%", null));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("받은 근거", le.id() + " 대 " + be.id());
        out.put(id + ".매물", val(lv, "원", 0));
        out.put(id + ".기준", val(bv, "원", 0));
        out.put(id + ".차이", val(d, "%", 1));
        out.put("식", "(매물 − 기준) ÷ 기준 × 100, 소수 첫째 자리");
        return out;
    }

    // ---------------------------------------------------------------- convert_rent

    Map<String, Object> convertRent(String depositId, String monthlyId, RunState st) throws ToolError {
        Evidence de = need(st, depositId);
        Evidence me = need(st, monthlyId);
        if (de.kind() != Evidence.Kind.LISTING_PRICE || me.kind() != Evidence.Kind.LISTING_MONTHLY) {
            throw new ToolError("BAD_EVIDENCE", "deposit_evidence는 매물.가격, monthly_evidence는 매물.월세만 받는다");
        }
        Q c = converted(de.value().div(MANWON), me.value().div(MANWON)).mul(MANWON);
        Q rate = RATE.mul(Q.of(100));
        String id = st.nextId("R");
        st.put(new Evidence(id + ".전환율", Evidence.Kind.R_RATE, rate, "%", null));
        st.put(new Evidence(id + ".환산보증금", Evidence.Kind.R_CONVERTED, c, "원", null));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put(id + ".전환율", val(rate, "%", 1));
        out.put(id + ".환산보증금", val(c, "원", 0));
        out.put("식", "보증금 + 월세 × 12 ÷ 전환율, 만원 단위로 반올림");
        return out;
    }

    // ---------------------------------------------------------------- convert_area

    Map<String, Object> convertArea(String areaId, String toUnit, RunState st) throws ToolError {
        Evidence ae = need(st, areaId);
        if (ae.kind() != Evidence.Kind.LISTING_AREA) {
            throw new ToolError("BAD_EVIDENCE", "area_evidence는 매물.면적만 받는다");
        }
        Q v;
        int scale;
        if (toUnit.equals("평")) {
            v = ae.value().mul(PYEONG).roundUnit(HUNDREDTH);
            scale = 2;
        } else if (toUnit.equals("㎡")) {
            v = ae.value();
            scale = 0;
        } else {
            throw new ToolError("BAD_INPUT", "to_unit은 ㎡·평 가운데 하나다");
        }
        String id = st.nextId("A");
        st.put(new Evidence(id + ".값", Evidence.Kind.A_VALUE, v, toUnit, null));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put(id + ".값", val(v, toUnit, scale));
        out.put("식", toUnit.equals("평") ? "㎡ × 121 ÷ 400(1평 = 400/121㎡), 소수 둘째 자리" : "바꾸지 않음");
        return out;
    }

    /** 숫자 보이기용(BigDecimal) — 시험에서 쓴다. */
    static BigDecimal show(Q q, int scale) {
        return q.toDecimal(scale);
    }
}
