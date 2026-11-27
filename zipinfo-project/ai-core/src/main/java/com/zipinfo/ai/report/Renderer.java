package com.zipinfo.ai.report;

import com.zipinfo.ai.num.Q;
import com.zipinfo.ai.tools.Evidence;
import com.zipinfo.ai.tools.RunState;
import java.util.HashMap;
import java.util.Map;

/**
 * 글 그리기 v1(순서 11b-2 설계 b판 5.1): 자리표시자를 값·단위와 방향 말로 바꾼 글이 사용자와 판정 모델이 보는 답이다.
 * <ul>
 * <li>값과 차이의 크기·방향은 근거(도구 결과·매물 칸)에서 그린다. claim과 근거가 다르면 검사기의 VALUE가 따로 잡는다.</li>
 * <li>근거가 없거나 근거 단위가 claim 단위와 맞지 않으면(원·만원끼리는 맞음) 자리를 그대로 남긴다. 검사기의 BRACE가 잡는다.</li>
 * <li>방향 자리는 근거가 C?.차이이면 claim의 갈래(stat·value)를 가리지 않고 그린다.</li>
 * <li>같은 slot의 claim이 둘이면 앞의 것(stat 먼저)으로 그린다. 검사기의 SLOT_DUP가 잡는다.</li>
 * <li>{매물명}은 매물 칸의 이름이다. 글 규칙(돈 표기·겹침·조사·방향 말·빈 괄호·빈칸·끝 마침표)은 KoText에 있다.</li>
 * </ul>
 */
public final class Renderer {
    private Renderer() {}

    public static String render(Report r, RunState st) {
        Map<String, KoText.Slot> slots = new HashMap<>();
        for (Report.StatClaim c : r.stats()) {
            slots.putIfAbsent(c.slot(), slot(c.value(), c.unit(), c.evidence(), st));
        }
        for (Report.ValueClaim c : r.values()) {
            slots.putIfAbsent(c.slot(), slot(c.value(), c.unit(), c.evidence(), st));
        }
        return KoText.render(r.text(), slots, st.listing().fields().get("stockName"));
    }

    static KoText.Slot slot(Q claimValue, String claimUnit, String evidence, RunState st) {
        Evidence e = st.find(evidence);
        if (e == null || e.value() == null) {
            return new KoText.Slot(claimValue, claimUnit, false, false);
        }
        boolean diff = e.kind() == Evidence.Kind.C_DIFF;
        boolean money = isMoney(claimUnit) && isMoney(e.unit());
        if (!claimUnit.equals(e.unit()) && !money) {
            return new KoText.Slot(claimValue, claimUnit, diff, false);
        }
        return new KoText.Slot(e.value(), e.unit(), diff, true);
    }

    private static boolean isMoney(String unit) {
        return unit.equals("원") || unit.equals("만원");
    }
}
