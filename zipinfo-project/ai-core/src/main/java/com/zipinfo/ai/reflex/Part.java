package com.zipinfo.ai.reflex;

/**
 * 장치 부품(설계안 7.2~7.6). ①(카드)은 거는 부품이 아니라 판의 카드 방식이다(Variant.CardMode).
 * 적은 차례가 사슬의 차례다: ⓪ → ③ → ② → ④ → ⑤(7.1). 한 부르기에 걸린 부품을 이 차례로 묻고, 처음 거절을 돌려준다.
 */
public enum Part {
    INTENT("⓪"), LOCK("③"), REFLEX("②"), GATE("④"), MEMORY("⑤");

    private final String symbol;

    Part(String symbol) {
        this.symbol = symbol;
    }

    /** 판 설정 파일과 기록에 쓰는 표시(⓪ 꼴). */
    public String symbol() {
        return symbol;
    }

    public static Part of(String symbol) {
        for (Part p : values()) {
            if (p.symbol.equals(symbol)) {
                return p;
            }
        }
        throw new IllegalArgumentException("없는 부품이다: " + symbol);
    }
}
