package com.zipinfo.ai.reflex;

import java.util.List;

/**
 * 판 하나(설계안 7.7, 01의 판 10개).
 * - id: 기록과 Batch custom_id에 넣는 짧은 영문 ID(c01 꼴, 01 229줄). name: 01의 판 이름(B0, ①+⓪ 등).
 * - cards: 카드를 주지 않음(NONE), 시스템 글 뒤에 전문(PROMPT, B0+), 도구로 읽기(TOOLS, ①이 든 판).
 * - parts: 켠 부품. 사슬 차례(Part의 차례)로 놓는다.
 */
public record Variant(String id, String name, String model, CardMode cards, List<Part> parts) {
    public enum CardMode { NONE, PROMPT, TOOLS }
}
