package com.zipinfo.ai.cards;

import java.util.List;

/**
 * 카드 한 장(설계안 8절): ID(K01 꼴), 제목, 찾기용 영어 태그와 한글 낱말, 층 셋(핵심·사용법·기록).
 * 기록 층은 연습에서 더한 줄이다. 연습 전 판에서는 비어 있다.
 */
public record Card(String id, String title, List<String> englishTags, List<String> koreanWords, String core, String usage, String log) {}
