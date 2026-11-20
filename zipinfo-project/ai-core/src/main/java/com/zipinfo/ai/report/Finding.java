package com.zipinfo.ai.report;

/** 대조에서 걸린 것 하나: 어느 claim(갈래와 차례, 글이면 text), 무엇, 처방 한 줄. */
public record Finding(String where, String code, String prescription) {}
