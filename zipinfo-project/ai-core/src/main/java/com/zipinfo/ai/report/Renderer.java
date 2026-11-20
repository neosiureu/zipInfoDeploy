package com.zipinfo.ai.report;

import com.zipinfo.ai.num.Q;
import com.zipinfo.ai.tools.Evidence;
import com.zipinfo.ai.tools.RunState;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

/** 글 그리기(설계안 6.3, 순서 3 메모 5절): 자리표시자를 값·단위와 방향 말로 바꾼 글이 사용자와 판정 모델이 보는 답이다. */
public final class Renderer {
    private Renderer() {}

    public static String render(Report r, RunState st) {
        Map<String, Q> value = new HashMap<>();
        Map<String, String> unit = new HashMap<>();
        Map<String, Boolean> isDiff = new HashMap<>();
        Map<String, Q> diff = new HashMap<>();  // {X.방향}은 근거가 C?.차이인 claim에서만 그린다(상대 3a 검토 3)
        for (Report.StatClaim c : r.stats()) {
            value.putIfAbsent(c.slot(), c.value());
            unit.putIfAbsent(c.slot(), c.unit());
            isDiff.putIfAbsent(c.slot(), false);
        }
        for (Report.ValueClaim c : r.values()) {
            Evidence e = st.find(c.evidence());
            boolean d = e != null && e.kind() == Evidence.Kind.C_DIFF;
            value.putIfAbsent(c.slot(), c.value());
            unit.putIfAbsent(c.slot(), c.unit());
            isDiff.putIfAbsent(c.slot(), d);
            if (d) {
                diff.putIfAbsent(c.slot(), c.value());
            }
        }
        Matcher m = Checker.PLACEHOLDER.matcher(r.text());
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String slot = m.group(1);
            String rep;
            if (m.group(2) != null) {
                if (!diff.containsKey(slot)) {
                    rep = m.group();
                } else {
                    int s = diff.get(slot).signum();
                    rep = s > 0 ? "비싸다" : (s < 0 ? "싸다" : "같다");
                }
            } else if (!value.containsKey(slot)) {
                rep = m.group();
            } else {
                Q v = isDiff.getOrDefault(slot, false) ? value.get(slot).abs() : value.get(slot);
                rep = format(v, unit.get(slot));
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String format(Q v, String unit) {
        int scale = switch (unit) {
            case "%" -> 1;
            case "평" -> 2;
            default -> -1;
        };
        BigDecimal d = scale >= 0 ? v.approx(scale) : v.approx(4).stripTrailingZeros();
        if (d.scale() < 0) {
            d = d.setScale(0, RoundingMode.UNNECESSARY);
        }
        DecimalFormat f = new DecimalFormat("#,##0" + (d.scale() > 0 ? "." + "0".repeat(d.scale()) : ""),
                DecimalFormatSymbols.getInstance(Locale.ROOT));
        return f.format(d) + unit;
    }
}
