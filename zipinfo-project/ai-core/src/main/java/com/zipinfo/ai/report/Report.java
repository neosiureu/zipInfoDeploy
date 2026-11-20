package com.zipinfo.ai.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.zipinfo.ai.num.Q;
import java.util.ArrayList;
import java.util.List;

/** submit_report 입력(설계안 6.3). strict 스키마가 꼴을 보장하지만, 여기서도 칸이 빠지면 읽지 않는다. */
public record Report(String text, List<StatClaim> stats, List<ValueClaim> values, List<ContextClaim> contexts) {

    public record StatClaim(String slot, Q value, String unit, String basis, String start, String end, long sample,
                            boolean provisional, String evidence) {}

    public record ValueClaim(String slot, Q value, String unit, String source, String evidence) {}

    public record ContextClaim(String text, String evidence) {}

    public static Report parse(JsonNode in) {
        List<StatClaim> s = new ArrayList<>();
        for (JsonNode c : req(in, "stat_claims")) {
            s.add(new StatClaim(txt(c, "slot"), num(c, "value"), txt(c, "unit"), txt(c, "basis"), txt(c, "start_month"),
                    txt(c, "end_month"), req(c, "sample_size").asLong(), req(c, "provisional").asBoolean(), txt(c, "evidence")));
        }
        List<ValueClaim> v = new ArrayList<>();
        for (JsonNode c : req(in, "value_claims")) {
            v.add(new ValueClaim(txt(c, "slot"), num(c, "value"), txt(c, "unit"), txt(c, "source"), txt(c, "evidence")));
        }
        List<ContextClaim> x = new ArrayList<>();
        for (JsonNode c : req(in, "context_claims")) {
            x.add(new ContextClaim(txt(c, "text"), txt(c, "evidence")));
        }
        return new Report(txt(in, "text"), s, v, x);
    }

    private static JsonNode req(JsonNode n, String f) {
        JsonNode v = n.get(f);
        if (v == null || v.isNull()) {
            throw new IllegalArgumentException("submit_report에 " + f + " 칸이 없다");
        }
        return v;
    }

    private static String txt(JsonNode n, String f) {
        return req(n, f).asText();
    }

    private static Q num(JsonNode n, String f) {
        JsonNode v = req(n, f);
        if (!v.isNumber()) {
            throw new IllegalArgumentException(f + "는 수다");
        }
        java.math.BigDecimal d = v.decimalValue();
        return d.scale() >= 0 ? Q.of(d.unscaledValue(), java.math.BigInteger.TEN.pow(d.scale()))
                : Q.of(d.unscaledValue().multiply(java.math.BigInteger.TEN.pow(-d.scale())), java.math.BigInteger.ONE);
    }
}
