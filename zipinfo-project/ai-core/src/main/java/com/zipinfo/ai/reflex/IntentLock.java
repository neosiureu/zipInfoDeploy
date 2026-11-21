package com.zipinfo.ai.reflex;

import com.zipinfo.ai.loop.Hook;

/**
 * ⓪ 의도 잠금의 시작 자리(설계안 7.2). 고리는 데이터·계산 도구(QuestionLoop.DATA_TOOLS)와 제출 앞에서 이 자리를 묻는다.
 * declare_intents가 통과하기 전에는 거절하고, 처방은 "먼저 declare_intents로 의도를 적어라"다.
 * 카드 찾기·읽기와 declare_intents는 사슬을 돌지 않으므로 막지 않는다(15.4 1단계 "먼저 읽기").
 * 통과는 도구가 실행 상태에 남기고, 이 부품은 읽기만 한다(7.1).
 */
public final class IntentLock implements Hook {
    @Override
    public String name() {
        return Part.INTENT.symbol();
    }

    @Override
    public boolean at(Point p) {
        return p == Point.START;
    }

    @Override
    public Decision decide(Point p, Context c) {
        return c.state().intentsPassed() ? Decision.PASS : Decision.reject("먼저 declare_intents로 의도를 적어라.");
    }
}
