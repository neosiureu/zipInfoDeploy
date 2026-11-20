package com.zipinfo.ai.model;

/**
 * 판별 모델 설정(01 모델 설정, 사전 등록). temperature·top_p·top_k는 두지 않는다(보내지 않는다).
 * thinking: Opus 5.5는 늘 켜져 있어 DISABLED를 보내면 400이므로 OMIT을 쓴다. Haiku 5.5는 ADAPTIVE나 DISABLED.
 * effort: null이면 보내지 않는다(low·medium·high·xhigh·max).
 */
public record ModelSettings(String model, long maxTokens, Thinking thinking, String effort) {
    public enum Thinking { ADAPTIVE, DISABLED, OMIT }
}
