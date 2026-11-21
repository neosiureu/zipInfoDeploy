package com.zipinfo.ai.reflex;

import com.zipinfo.ai.data.ValidityTable;
import com.zipinfo.ai.loop.Hook;

/**
 * ③ 학습 잠금(설계안 7.4, 01 116줄: 개편 지역 조회, 면적 환산). 도구 전 자리.
 * - trade_stats: 그 매물의 후보 코드가 둘 이상(개편 지역)이면 지역 코드 카드
 * - convert_area: 단위·면적 카드
 * 그 실행에서 read_card로 그 카드를 읽지 않았으면 거절한다. 처방은 "먼저 카드 ○○를 읽어라" 한 줄이다.
 * 판정만 하고 실행 상태를 바꾸지 않는다(7.1).
 */
public final class LearningLock implements Hook {
    /** 지역 코드 카드(K02 지역 코드 정하기)와 단위·면적 카드(K06 단위와 면적). 연습 전 판 v0의 ID다. 하네스 시험이 제목을 대 본다. */
    public static final String REGION_CARD = "K02";
    public static final String AREA_CARD = "K06";

    private final ValidityTable validity;

    public LearningLock(ValidityTable validity) {
        this.validity = validity;
    }

    @Override
    public String name() {
        return Part.LOCK.symbol();
    }

    @Override
    public boolean at(Point p) {
        return p == Point.BEFORE_TOOL;
    }

    @Override
    public Decision decide(Point p, Context c) {
        if ("trade_stats".equals(c.tool())) {
            if (validity.candidates(c.state().meta().area()).size() >= 2 && !c.state().cardsRead().contains(REGION_CARD)) {
                return Decision.reject("먼저 카드 K02를 읽어라.");
            }
        } else if ("convert_area".equals(c.tool()) && !c.state().cardsRead().contains(AREA_CARD)) {
            return Decision.reject("먼저 카드 K06을 읽어라.");
        }
        return Decision.PASS;
    }
}
