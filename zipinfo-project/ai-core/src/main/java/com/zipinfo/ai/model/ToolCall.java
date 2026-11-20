package com.zipinfo.ai.model;

import com.fasterxml.jackson.databind.JsonNode;

/** 모델이 부른 도구 하나. */
public record ToolCall(String id, String name, JsonNode input) {}
