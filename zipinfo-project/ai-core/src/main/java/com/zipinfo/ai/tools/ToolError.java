package com.zipinfo.ai.tools;

/** 도구 오류(모든 판에 같은 글). 오류는 빈 결과가 아니다(설계안 6.1). */
public final class ToolError extends Exception {
    private final String code;

    public ToolError(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
