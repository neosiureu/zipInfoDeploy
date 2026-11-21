package com.zipinfo.ai.reflex;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 판 설정 파일(experiment/variants/variants_v0.json)을 읽는다. 어느 부품을 켤지는 이 파일이 정한다(설계안 7.1).
 * 꼴: {"version": "v0", "variants": [{"id": "c01", "name": "B0", "model": "…", "cards": "none|prompt|tools", "parts": ["②", …]}, …]}
 * 지키는 것(틀리면 멈춘다)
 * - 판마다 칸은 id·name·model·cards·parts 다섯뿐이다.
 * - id는 영문 소문자·숫자 1~8자다(custom_id에 넣는다, 01 229줄). id와 name은 겹치지 않는다.
 * - 부품은 겹치지 않는다. 부품이 있는 판은 카드를 도구로 읽는다(7.7의 ①+⓪ … 전부).
 */
public final class Variants {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern ID = Pattern.compile("[a-z0-9]{1,8}");
    private static final List<String> FIELDS = List.of("id", "name", "model", "cards", "parts");

    private Variants() {}

    public static List<Variant> load(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static List<Variant> parse(String json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("판 설정이 JSON이 아니다", e);
        }
        if (root == null || !root.path("version").isTextual()) {
            throw new IllegalArgumentException("판 설정에 version이 없다");
        }
        JsonNode list = root.get("variants");
        if (list == null || !list.isArray() || list.isEmpty()) {
            throw new IllegalArgumentException("판 설정에 variants가 없다");
        }
        List<Variant> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (JsonNode v : list) {
            List<String> keys = new ArrayList<>();
            Iterator<String> it = v.fieldNames();
            it.forEachRemaining(keys::add);
            if (!keys.equals(FIELDS)) {
                throw new IllegalArgumentException("판의 칸은 " + String.join("·", FIELDS) + "이다: " + keys);
            }
            String id = text(v, "id");
            String name = text(v, "name");
            String model = text(v, "model");
            if (!ID.matcher(id).matches()) {
                throw new IllegalArgumentException("판 id는 영문 소문자·숫자 1~8자다: " + id);
            }
            if (!ids.add(id) || !names.add(name)) {
                throw new IllegalArgumentException("판 id나 name이 겹친다: " + id + " " + name);
            }
            if (name.isBlank() || model.isBlank()) {
                throw new IllegalArgumentException("판 name·model이 비었다: " + id);
            }
            Variant.CardMode cards;
            try {
                cards = Variant.CardMode.valueOf(text(v, "cards").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("cards는 none·prompt·tools 가운데 하나다: " + id, e);
            }
            JsonNode ps = v.get("parts");
            if (!ps.isArray()) {
                throw new IllegalArgumentException("parts는 목록이다: " + id);
            }
            EnumSet<Part> parts = EnumSet.noneOf(Part.class);
            for (JsonNode p : ps) {
                if (!p.isTextual() || !parts.add(Part.of(p.asText()))) {
                    throw new IllegalArgumentException("부품이 겹치거나 글이 아니다: " + id);
                }
            }
            if (!parts.isEmpty() && cards != Variant.CardMode.TOOLS) {
                throw new IllegalArgumentException("부품이 있는 판은 카드를 도구로 읽는다: " + id);
            }
            out.add(new Variant(id, name, model, cards, List.copyOf(parts)));
        }
        return List.copyOf(out);
    }

    private static String text(JsonNode v, String field) {
        JsonNode x = v.get(field);
        if (x == null || !x.isTextual()) {
            throw new IllegalArgumentException(field + "는 글이다");
        }
        return x.asText();
    }
}
