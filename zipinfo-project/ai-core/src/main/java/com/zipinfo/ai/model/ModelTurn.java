package com.zipinfo.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 모델 응답 하나에서 고리가 쓰는 것. 원래 블록(thinking 포함)은 세션이 그대로 들고 다음 요청에 되돌린다.
 * 토큰 넷은 input·cache_read·cache_write·output이다(설계안 10.1). millis는 성공한 요청 하나의 시간이다(01 213줄).
 * raw는 SDK가 모은 최종 메시지의 JSON이다(기록용, 설계안 10.1 "받은 응답(원문)").
 */
public record ModelTurn(StopKind stop, String rawStopReason, String refusalCategory, List<ToolCall> calls, String text,
                        long inputTokens, long cacheReadTokens, long cacheWriteTokens, long outputTokens, long millis, JsonNode raw) {}
