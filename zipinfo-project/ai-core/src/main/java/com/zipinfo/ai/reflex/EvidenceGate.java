package com.zipinfo.ai.reflex;

import com.zipinfo.ai.loop.Hook;
import com.zipinfo.ai.report.Finding;

/**
 * ④ 근거 게이트(설계안 7.5). 제출 자리.
 * 대조 코드(report.Checker)는 고리가 모든 판에서 기록 전용으로 이미 돌렸고, 그 결과가 Context.findings다. 같은 코드를 모드만 바꿔 쓴다(7.1).
 * 걸린 것이 있으면 거절하고, 처음 걸린 것의 자리와 처방을 한 줄로 돌려준다.
 */
public final class EvidenceGate implements Hook {
    @Override
    public String name() {
        return Part.GATE.symbol();
    }

    @Override
    public boolean at(Point p) {
        return p == Point.SUBMIT;
    }

    @Override
    public Decision decide(Point p, Context c) {
        if (c.findings().isEmpty()) {
            return Decision.PASS;
        }
        Finding f = c.findings().get(0);
        return Decision.reject(f.where() + ": " + f.prescription());
    }
}
