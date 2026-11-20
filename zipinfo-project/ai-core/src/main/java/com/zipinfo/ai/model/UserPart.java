package com.zipinfo.ai.model;

/** 사용자 쪽 글 조각: 글, 또는 도구 결과. */
public sealed interface UserPart {
    record Text(String text) implements UserPart {}

    record ToolResult(String toolUseId, String text, boolean isError) implements UserPart {}
}
