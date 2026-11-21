package com.zipinfo.ai.reflex;

import com.fasterxml.jackson.databind.JsonNode;
import com.zipinfo.ai.data.ValidityTable;
import com.zipinfo.ai.loop.Hook;
import com.zipinfo.ai.tools.DataSet;
import com.zipinfo.ai.tools.Tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * ② 반사 규칙(설계안 7.3). 도구 전 자리, trade_stats. (지역 코드, 달, API)마다 유효성 표(5.2)로 보고, 범위 안의 모든 달이 유효해야 통과다.
 * - 무효면 거절하고 처방 한 줄을 준다: 맞는 코드와 그 달. 맞는 코드는 표의 처방이다. 1:N도 표가 매물의 영역으로 이미 정해 두었다.
 * - 후보가 아닌 코드도 무효다. 처방은 매물의 현재 코드다(표의 judge가 그렇게 돌려준다).
 * - 정말 0건 달은 모든 후보가 유효라서 막지 않는다.
 * - 입력 꼴이 틀리거나 스냅샷 밖 부르기면 판정하지 않고 통과시킨다. 도구가 BAD_INPUT·SNAPSHOT_MISS로 돌려준다.
 * 판정만 하고 실행 상태를 바꾸지 않는다(7.1).
 */
public final class ReflexRule implements Hook {
    private static final Pattern MONTH = Pattern.compile("[0-9]{4}(0[1-9]|1[0-2])");
    private static final Pattern CODE = Pattern.compile("[0-9]{5}");

    private final DataSet data;

    public ReflexRule(DataSet data) {
        this.data = data;
    }

    @Override
    public String name() {
        return Part.REFLEX.symbol();
    }

    @Override
    public boolean at(Point p) {
        return p == Point.BEFORE_TOOL;
    }

    @Override
    public Decision decide(Point p, Context c) {
        if (!"trade_stats".equals(c.tool())) {
            return Decision.PASS;
        }
        String api = text(c.input(), "api");
        String code = text(c.input(), "region_code");
        String start = text(c.input(), "start_month");
        String end = text(c.input(), "end_month");
        if (!Tools.APIS.contains(api) || !CODE.matcher(code).matches() || !MONTH.matcher(start).matches()
                || !MONTH.matcher(end).matches() || start.compareTo(end) > 0) {
            return Decision.PASS;
        }
        List<String> months = Tools.months(start, end);
        for (String m : months) {
            if (m.compareTo(Tools.FIRST_MONTH) < 0 || m.compareTo(Tools.LAST_MONTH) > 0 || data.trades().received(api, code, m) == null) {
                return Decision.PASS;
            }
        }
        String area = c.state().meta().area();
        Map<String, List<String>> wrong = new LinkedHashMap<>();
        for (String m : months) {
            ValidityTable.Verdict v = data.validity().judge(area, api, m, code);
            if (!v.valid()) {
                wrong.computeIfAbsent(v.prescription(), k -> new ArrayList<>()).add(m);
            }
        }
        if (wrong.isEmpty()) {
            return Decision.PASS;
        }
        List<String> fix = new ArrayList<>();
        wrong.forEach((right, ms) -> fix.add(right + "(" + spans(ms) + ")"));
        return Decision.reject("지역 코드가 이 매물에 맞지 않는다. 맞는 코드: " + String.join(", ", fix));
    }

    /** 칸이 없거나 글이 아니면 빈 글이다. 그러면 꼴이 틀려 통과하고, 도구가 BAD_INPUT으로 돌려준다. */
    private static String text(JsonNode in, String field) {
        JsonNode v = in == null ? null : in.get(field);
        return v != null && v.isTextual() ? v.asText() : "";
    }

    /** 이어진 달은 "처음~끝"으로, 떨어진 묶음은 "·"로 잇는다. months는 차례대로 온다. */
    static String spans(List<String> months) {
        List<String> out = new ArrayList<>();
        String first = months.get(0);
        String last = first;
        for (String m : months.subList(1, months.size())) {
            if (m.equals(next(last))) {
                last = m;
                continue;
            }
            out.add(first.equals(last) ? first : first + "~" + last);
            first = m;
            last = m;
        }
        out.add(first.equals(last) ? first : first + "~" + last);
        return String.join("·", out);
    }

    private static String next(String ym) {
        int y = Integer.parseInt(ym.substring(0, 4));
        int m = Integer.parseInt(ym.substring(4)) + 1;
        if (m == 13) {
            y++;
            m = 1;
        }
        return String.format("%04d%02d", y, m);
    }
}
