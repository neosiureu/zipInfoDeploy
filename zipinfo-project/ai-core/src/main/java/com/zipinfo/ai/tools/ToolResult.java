package com.zipinfo.ai.tools;

/** 모델에 돌려줄 도구 결과 글(짧은 JSON)과 오류 여부. */
public record ToolResult(String text, boolean isError, String errorCode) {}
