package com.zipinfo.ai.reflex;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * ⓪이 쓰는 정해 둔 목록(설계안 7.2, 열린 결정 2). 연습 전에 파일(experiment/intents/intent_lists_v0.json)로 얼리고, 고치는 것은 연습에서만 한다.
 * - connective_endings(이음 말): 어절 끝에 오면 그 뒤에서 절을 나눈다.
 * - connective_words(이음 낱말): 그 앞에서 절을 나눈다. 분모에서도 뺀다.
 * - particles_endings(조사·어미): 어절 끝에서 가장 긴 것 하나를 분모에서 뺀다.
 * - fillers(군말): 어절째(여러 어절도 됨) 분모에서 뺀다.
 * - word_exceptions(낱말 예외): 이 낱말에서는 절을 나누지 않고 조사·어미도 떼지 않는다.
 * 지키는 것(틀리면 멈춘다): 칸은 version과 목록 다섯을 이 차례로 둔다. 목록은 비지 않고, 글은 NFC이고 앞뒤 빈칸이 없으며, 한 목록 안에서 겹치지 않는다.
 * 군말만 어절 사이 빈칸 하나를 둘 수 있다.
 */
public record IntentLists(String version, List<String> connectiveEndings, List<String> connectiveWords,
                          List<String> particlesEndings, List<String> fillers, List<String> wordExceptions) {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> KEYS = List.of("version", "connective_endings", "connective_words", "particles_endings",
            "fillers", "word_exceptions");

    public static IntentLists load(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static IntentLists parse(String json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("⓪ 목록이 JSON이 아니다", e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("⓪ 목록이 object가 아니다");
        }
        List<String> keys = new ArrayList<>();
        Iterator<String> it = root.fieldNames();
        it.forEachRemaining(keys::add);
        if (!keys.equals(KEYS)) {
            throw new IllegalArgumentException("⓪ 목록의 칸은 " + String.join("·", KEYS) + "이다: " + keys);
        }
        if (!root.get("version").isTextual() || root.get("version").asText().isBlank()) {
            throw new IllegalArgumentException("⓪ 목록에 version이 없다");
        }
        return new IntentLists(root.get("version").asText(), list(root, "connective_endings", false), list(root, "connective_words", false),
                list(root, "particles_endings", false), list(root, "fillers", true), list(root, "word_exceptions", false));
    }

    private static List<String> list(JsonNode root, String key, boolean spaces) {
        JsonNode a = root.get(key);
        if (!a.isArray() || a.isEmpty()) {
            throw new IllegalArgumentException(key + "는 비지 않은 목록이다");
        }
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode x : a) {
            String s = x.isTextual() ? x.asText() : "";
            boolean form = !s.isEmpty() && s.equals(s.strip()) && Normalizer.isNormalized(s, Normalizer.Form.NFC)
                    && (spaces ? !s.contains("  ") && s.chars().noneMatch(c -> Character.isWhitespace(c) && c != ' ') : s.chars().noneMatch(Character::isWhitespace));
            if (!form || !seen.add(s)) {
                throw new IllegalArgumentException(key + "에 꼴이 틀리거나 겹친 글이 있다: " + s);
            }
            out.add(s);
        }
        return List.copyOf(out);
    }
}
