package com.zipinfo.ai.tools;

import com.zipinfo.ai.num.Q;

/** 근거 ID 하나가 가리키는 값(설계안 6.1). 주소 결과(J)는 값이 없다. 통계 값(S)은 그 부르기를 든다. */
public record Evidence(String id, Kind kind, Q value, String unit, StatsCall stats) {

    public enum Kind {
        LISTING_PRICE, LISTING_MONTHLY, LISTING_AREA, LISTING_FLOOR,
        S_SAMPLE, S_BASIS, S_DEPOSIT, S_MONTHLY, S_CONVERTED,
        C_LISTING, C_BASIS, C_DIFF,
        R_RATE, R_CONVERTED,
        A_VALUE,
        J_RESULT;

        public boolean fromTradeStats() {
            return this == S_SAMPLE || this == S_BASIS || this == S_DEPOSIT || this == S_MONTHLY || this == S_CONVERTED;
        }
    }
}
