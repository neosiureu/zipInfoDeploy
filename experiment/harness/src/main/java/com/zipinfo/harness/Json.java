package com.zipinfo.harness;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 설정·문항·기록 파일을 읽고 쓰는 JSON. 소수는 BigDecimal로 읽는다(단가표). 칸을 엄격하게 본다. */
final class Json {
    static final ObjectMapper M = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private Json() {}

    static JsonNode read(String text, String what) {
        try {
            JsonNode n = M.readTree(text);
            if (n == null || n.isMissingNode()) {
                throw new IllegalArgumentException(what + "이(가) 비었다");
            }
            return n;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(what + "이(가) JSON이 아니다", e);
        }
    }

    static String write(Object o) {
        try {
            return M.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 칸이 정확히 이것들이어야 한다(차례는 보지 않는다). */
    static void fields(JsonNode n, String where, String... want) {
        if (n == null || !n.isObject()) {
            throw new IllegalArgumentException(where + "는 객체다");
        }
        Set<String> got = new TreeSet<>();
        Iterator<String> it = n.fieldNames();
        it.forEachRemaining(got::add);
        Set<String> w = new TreeSet<>(List.of(want));
        if (!got.equals(w)) {
            throw new IllegalArgumentException(where + "의 칸은 " + String.join("·", w) + "이다: " + got);
        }
    }

    static String text(JsonNode n, String field, String where) {
        JsonNode x = n.get(field);
        if (x == null || !x.isTextual() || x.asText().isBlank()) {
            throw new IllegalArgumentException(where + "." + field + "는 비지 않은 글이다");
        }
        return x.asText();
    }

    /** null이면 null. */
    static String textOrNull(JsonNode n, String field, String where) {
        JsonNode x = n.get(field);
        if (x == null || x.isNull()) {
            return null;
        }
        return text(n, field, where);
    }

    static int integer(JsonNode n, String field, String where, int min, int max) {
        JsonNode x = n.get(field);
        if (x == null || !x.isIntegralNumber() || !x.canConvertToInt() || x.asInt() < min || x.asInt() > max) {
            throw new IllegalArgumentException(where + "." + field + "는 " + min + "~" + max + "의 정수다");
        }
        return x.asInt();
    }

    static boolean bool(JsonNode n, String field, String where) {
        JsonNode x = n.get(field);
        if (x == null || !x.isBoolean()) {
            throw new IllegalArgumentException(where + "." + field + "는 true나 false다");
        }
        return x.asBoolean();
    }

    /** 0보다 큰 수. */
    static BigDecimal positive(JsonNode n, String field, String where) {
        JsonNode x = n.get(field);
        if (x == null || !x.isNumber() || x.decimalValue().signum() <= 0) {
            throw new IllegalArgumentException(where + "." + field + "는 0보다 큰 수다");
        }
        return x.decimalValue();
    }

    /** "all"이면 빈 목록(전부), 아니면 겹치지 않는 글 목록. */
    static List<String> allOrList(JsonNode n, String field, String where) {
        JsonNode x = n.get(field);
        if (x != null && x.isTextual() && x.asText().equals("all")) {
            return List.of();
        }
        if (x == null || !x.isArray() || x.isEmpty()) {
            throw new IllegalArgumentException(where + "." + field + "는 \"all\"이나 비지 않은 목록이다");
        }
        List<String> out = new ArrayList<>();
        for (JsonNode e : x) {
            if (!e.isTextual() || out.contains(e.asText())) {
                throw new IllegalArgumentException(where + "." + field + "의 값이 글이 아니거나 겹친다");
            }
            out.add(e.asText());
        }
        return List.copyOf(out);
    }
}
