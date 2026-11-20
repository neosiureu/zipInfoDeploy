package com.zipinfo.ai.model;

/** 멈춘 까닭(01 하네스 규칙의 갈래). OTHER는 모르는 값이다(멈추고 본다). */
public enum StopKind {
    END_TURN, TOOL_USE, MAX_TOKENS, STOP_SEQUENCE, PAUSE_TURN, REFUSAL, CONTEXT_WINDOW, OTHER;

    public static StopKind of(String raw) {
        return switch (raw == null ? "" : raw) {
            case "end_turn" -> END_TURN;
            case "tool_use" -> TOOL_USE;
            case "max_tokens" -> MAX_TOKENS;
            case "stop_sequence" -> STOP_SEQUENCE;
            case "pause_turn" -> PAUSE_TURN;
            case "refusal" -> REFUSAL;
            case "model_context_window_exceeded" -> CONTEXT_WINDOW;
            default -> OTHER;
        };
    }
}
