package com.zipinfo.harness;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 문항 하나(설계안 9.2, 순서 7이 만든다). 문항 파일 꼴:
 * {"version": "v0", "questions": [{"id": "q001", "key": "L01", "qtype": "T1", "split": "연습", "text": "…"}, …]}
 * - id: 영문 소문자·숫자 1~8자(실행 ID와 Batch custom_id에 넣는다, 01 229줄). 겹치지 않는다.
 * - key: 매물 키(L01~L75). qtype: T1~T5. split: 연습·본시험(그 매물의 나눔과 같아야 한다. 하네스가 대 본다).
 * - text: 모델에 보내는 질문 글. NFC여야 한다.
 */
public record Question(String id, String key, String qtype, String split, String text) {
    private static final Pattern ID = Pattern.compile("[a-z0-9]{1,8}");
    private static final Set<String> TYPES = Set.of("T1", "T2", "T3", "T4", "T5");

    public static List<Question> load(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static List<Question> parse(String text) {
        JsonNode root = Json.read(text, "문항 파일");
        Json.fields(root, "문항 파일", "version", "questions");
        Json.text(root, "version", "문항 파일");
        JsonNode list = root.get("questions");
        if (list == null || !list.isArray() || list.isEmpty()) {
            throw new IllegalArgumentException("문항 파일에 questions가 없다");
        }
        List<Question> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode q : list) {
            Json.fields(q, "문항", "id", "key", "qtype", "split", "text");
            String id = Json.text(q, "id", "문항");
            String where = "문항 " + id;
            if (!ID.matcher(id).matches() || !ids.add(id)) {
                throw new IllegalArgumentException(where + ": id는 영문 소문자·숫자 1~8자이고 겹치지 않는다");
            }
            String qtype = Json.text(q, "qtype", where);
            if (!TYPES.contains(qtype)) {
                throw new IllegalArgumentException(where + ": qtype은 T1~T5다");
            }
            String split = Json.text(q, "split", where);
            if (!split.equals(RunSettings.PRACTICE) && !split.equals(RunSettings.TEST)) {
                throw new IllegalArgumentException(where + ": split은 연습이나 본시험이다");
            }
            String t = Json.text(q, "text", where);
            if (!Normalizer.isNormalized(t, Normalizer.Form.NFC)) {
                throw new IllegalArgumentException(where + ": 질문 글이 NFC가 아니다");
            }
            out.add(new Question(id, Json.text(q, "key", where), qtype, split, t));
        }
        return List.copyOf(out);
    }
}
