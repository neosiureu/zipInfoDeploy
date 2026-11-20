package com.zipinfo.ai.report;

import com.zipinfo.ai.data.ValidityTable;
import com.zipinfo.ai.num.Q;
import com.zipinfo.ai.tools.Evidence;
import com.zipinfo.ai.tools.RunState;
import com.zipinfo.ai.tools.StatsCall;
import com.zipinfo.ai.tools.ToolSpecs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 대조 코드(설계안 7.5). ④(막음)와 기록 전용 검사기(모든 판)가 같은 코드를 쓴다. 걸린 것을 모두 차례대로 낸다.
 * ④는 첫째 것의 처방 한 줄만 돌려준다. 범위를 옳게 골랐는지, 꼭 있어야 할 claim이 있는지는 보지 않는다(채점의 몫).
 */
public final class Checker {
    static final Pattern PLACEHOLDER = Pattern.compile("\\{([가-힣]{1,3})(\\.방향)?\\}");
    static final Pattern TEXT = Pattern.compile(ToolSpecs.TEXT_PATTERN);
    static final Q MANWON = Q.of(10000);
    static final Q PYEONG = Q.of(121, 400);

    private Checker() {}

    public static List<Finding> check(Report r, RunState st, ValidityTable validity) {
        List<Finding> out = new ArrayList<>();
        Set<String> slots = new HashSet<>();
        Set<String> diffSlots = new HashSet<>();
        // 같은 slot을 두 claim에 쓰면 그린 값·방향이 갈린다(상대 3a 검토 3). 모든 판에서 기록하고 ④ 판은 거절한다.
        Set<String> seenSlot = new HashSet<>();
        for (String slot : allSlots(r)) {
            if (!seenSlot.add(slot)) {
                out.add(new Finding("slot", "SLOT_DUP", "자리표시자 이름 " + slot + "를 두 claim에 썼다. claim마다 다른 이름을 쓴다"));
            }
        }
        for (int i = 0; i < r.stats().size(); i++) {
            Report.StatClaim c = r.stats().get(i);
            String w = "stat_claims[" + i + "]";
            slots.add(c.slot());
            Evidence e = st.find(c.evidence());
            if (e == null) {
                out.add(new Finding(w, "EVIDENCE_MISSING", "근거 ID " + c.evidence() + "는 이 실행의 도구 결과나 매물에 없다"));
                continue;
            }
            if (!e.kind().fromTradeStats()) {
                out.add(new Finding(w, "NOT_STATS", "통계 claim의 근거는 trade_stats 결과(S?)여야 한다"));
                continue;
            }
            StatsCall call = e.stats();
            // "거래 없음"(값 0, 단위 건)은 claim 종류와 상관없이 같은 검사를 한다(7.5, 상대 3a 검토 4).
            if (c.value().signum() == 0 && c.unit().equals("건")) {
                if (e.kind() != Evidence.Kind.S_SAMPLE || e.value().signum() != 0) {
                    out.add(new Finding(w, "ZERO_EVIDENCE", "거래 없음의 근거는 표본 수가 0인 trade_stats 결과(S?.표본수)여야 한다"));
                } else if (!allValid(call, st, validity)) {
                    out.add(new Finding(w, "ZERO_INVALID", "그 0건은 이 매물 지역에 맞지 않는 코드에서 나왔다"));
                }
            }
            if (e.value() == null) {
                out.add(new Finding(w, "NO_VALUE", "근거 " + e.id() + "에는 값이 없다(표본 0건)"));
            } else if (!sameValue(c.value(), c.unit(), e)) {
                out.add(new Finding(w, "VALUE", "값·단위가 근거 " + e.id() + "(" + e.value().toDecimal(0) + e.unit() + ")와 다르다"));
            }
            if (!c.basis().equals(call.scope())) {
                out.add(new Finding(w, "BASIS", "비교 기준은 근거 부르기의 것(" + call.scope() + ")이어야 한다"));
            }
            if (!c.start().equals(call.start()) || !c.end().equals(call.end())) {
                out.add(new Finding(w, "PERIOD", "기간은 근거 부르기의 것(" + call.start() + "~" + call.end() + ")이어야 한다"));
            }
            if (c.sample() != call.sample()) {
                out.add(new Finding(w, "SAMPLE", "표본 수는 근거 부르기의 것(" + call.sample() + "건)이어야 한다"));
            }
            boolean prov = c.start().compareTo("202610") <= 0 && c.end().compareTo("202609") >= 0;
            if (c.provisional() != prov) {
                out.add(new Finding(w, "PROVISIONAL", prov ? "기간에 202609나 202610이 들면 잠정이다" : "기간에 202609·202610이 없으면 잠정이 아니다"));
            }
        }
        for (int i = 0; i < r.values().size(); i++) {
            Report.ValueClaim c = r.values().get(i);
            String w = "value_claims[" + i + "]";
            slots.add(c.slot());
            Evidence e = st.find(c.evidence());
            if (e == null) {
                out.add(new Finding(w, "EVIDENCE_MISSING", "근거 ID " + c.evidence() + "는 이 실행의 도구 결과나 매물에 없다"));
                continue;
            }
            if (e.kind() == Evidence.Kind.C_DIFF) {
                diffSlots.add(c.slot());
            }
            boolean noTrade = c.value().signum() == 0 && c.unit().equals("건");
            if (noTrade) {
                if (e.kind() != Evidence.Kind.S_SAMPLE || e.value().signum() != 0) {
                    out.add(new Finding(w, "ZERO_EVIDENCE", "거래 없음의 근거는 표본 수가 0인 trade_stats 결과(S?.표본수)여야 한다"));
                } else if (!allValid(e.stats(), st, validity)) {
                    out.add(new Finding(w, "ZERO_INVALID", "그 0건은 이 매물 지역에 맞지 않는 코드에서 나왔다"));
                }
                continue;
            }
            if (e.value() == null) {
                out.add(new Finding(w, "NO_VALUE", "근거 " + e.id() + "에는 값이 없다"));
            } else if (!sameValue(c.value(), c.unit(), e)) {
                out.add(new Finding(w, "VALUE", "값·단위가 근거 " + e.id() + "와 다르다"));
            }
        }
        for (int i = 0; i < r.contexts().size(); i++) {
            Report.ContextClaim c = r.contexts().get(i);
            if (st.find(c.evidence()) == null) {
                out.add(new Finding("context_claims[" + i + "]", "EVIDENCE_MISSING", "근거 ID " + c.evidence() + "는 이 실행의 도구 결과나 매물에 없다"));
            }
            if (!TEXT.matcher(c.text()).matches()) {
                out.add(new Finding("context_claims[" + i + "]", "TEXT_FORM", "문맥 claim 글에는 숫자나 허용되지 않은 글자를 쓰지 않는다"));
            }
        }
        if (!TEXT.matcher(r.text()).matches()) {
            out.add(new Finding("text", "TEXT_FORM", "답 글에는 숫자나 허용되지 않은 글자를 쓰지 않는다"));
        }
        for (String n : Numerals.find(r.text())) {
            out.add(new Finding("text", "NUMERAL", "답 글에 수를 낱말로 쓰지 않는다(" + n + "). 값은 자리표시자와 claim으로 쓴다"));
        }
        Matcher m = PLACEHOLDER.matcher(r.text());
        while (m.find()) {
            String slot = m.group(1);
            if (!slots.contains(slot)) {
                out.add(new Finding("text", "PLACEHOLDER", "자리표시자 {" + slot + "}의 claim이 없다"));
            } else if (m.group(2) != null && !diffSlots.contains(slot)) {
                out.add(new Finding("text", "DIRECTION", "{" + slot + ".방향}은 근거가 차이(C?.차이)인 claim에만 쓴다"));
            }
        }
        return out;
    }

    static List<String> allSlots(Report r) {
        List<String> s = new ArrayList<>();
        r.stats().forEach(c -> s.add(c.slot()));
        r.values().forEach(c -> s.add(c.slot()));
        return s;
    }

    /** 단위 환산을 넣어 값이 같은지: 만원↔원은 정확히, ㎡↔평은 근거가 A?일 때만(그 값과 같아야 한다). */
    static boolean sameValue(Q v, String unit, Evidence e) {
        if (unit.equals(e.unit())) {
            return v.equals(e.value());
        }
        if (unit.equals("원") && e.unit().equals("만원")) {
            return v.equals(e.value().mul(MANWON));
        }
        if (unit.equals("만원") && e.unit().equals("원")) {
            return v.mul(MANWON).equals(e.value());
        }
        return false;
    }

    static boolean allValid(StatsCall call, RunState st, ValidityTable validity) {
        String area = st.meta().area();
        for (String ym : call.months()) {
            if (!validity.judge(area, call.api(), ym, call.code()).valid()) {
                return false;
            }
        }
        return true;
    }
}
