package com.zipinfo.ai.cards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.tools.RunState;
import com.zipinfo.ai.tools.ToolResult;
import com.zipinfo.ai.tools.ToolSpecs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * ①이 든 판의 카드 도구 둘(설계안 6.2): search_cards와 read_card. 고리의 Config.extra로 붙고, 거는 자리 사슬을 돌지 않는다.
 * 카드는 근거가 아니라서 결과에 근거 ID가 없다(6.1). 읽은 카드는 실행 상태에 남긴다(③이 본다, 7.4).
 */
public final class CardTools {
    private static final JsonNodeFactory F = JsonNodeFactory.instance;
    private static final ObjectMapper JSON = new ObjectMapper();

    public static final List<ToolSpecs.Spec> SPECS = List.of(
            new ToolSpecs.Spec("search_cards",
                    "일의 규칙을 적은 카드를 낱말로 찾아 카드 ID와 제목을 돌려준다. 영어 낱말은 카드의 영어 태그와 같으면 찾고, "
                            + "한글 낱말은 카드의 낱말이 검색어 안에 있으면 찾는다(빈칸 무시). 낱말 여럿을 한 번에 넣어도 된다.",
                    object("query", F.objectNode().put("type", "string").put("description", "찾을 낱말(한글이나 영어)"))),
            new ToolSpecs.Spec("read_card",
                    "카드 ID(K01 꼴)로 카드 글을 돌려준다.",
                    object("card_id", F.objectNode().put("type", "string").put("pattern", "^K[0-9]{2}$").put("description", "카드 ID"))));

    private CardTools() {}

    private static ObjectNode object(String name, ObjectNode schema) {
        ObjectNode o = F.objectNode().put("type", "object");
        o.putObject("properties").set(name, schema);
        o.putArray("required").add(name);
        o.put("additionalProperties", false);
        return o;
    }

    /** 고리의 Config.extra에 넣는 처리기 둘. */
    public static Map<String, BiFunction<JsonNode, RunState, ToolResult>> handlers(Cards cards) {
        Map<String, BiFunction<JsonNode, RunState, ToolResult>> m = new LinkedHashMap<>();
        m.put("search_cards", (in, st) -> search(cards, text(in, "query")));
        m.put("read_card", (in, st) -> read(cards, text(in, "card_id"), st));
        return m;
    }

    static ToolResult search(Cards cards, String query) {
        if (query.isBlank()) {
            return error("BAD_INPUT", "검색어가 비었다");
        }
        List<Map<String, Object>> found = new ArrayList<>();
        for (Card c : cards.search(query)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("카드", c.id());
            row.put("제목", c.title());
            found.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("검색어", query.strip());
        out.put("결과수", found.size());
        out.put("카드", found);
        return new ToolResult(write(out), false, "");
    }

    static ToolResult read(Cards cards, String id, RunState st) {
        Card c = cards.get(id.strip());
        if (c == null) {
            return error("UNKNOWN_CARD", "없는 카드 ID다. 카드 ID는 " + cards.all().get(0).id() + "~" + cards.all().get(cards.all().size() - 1).id() + "이다");
        }
        st.cardRead(c.id());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("카드", c.id());
        out.put("글", Cards.render(c));
        return new ToolResult(write(out), false, "");
    }

    private static String text(JsonNode in, String field) {
        JsonNode v = in == null ? null : in.get(field);
        return v == null || !v.isTextual() ? "" : v.asText();
    }

    private static ToolResult error(String code, String line) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("오류", code);
        m.put("설명", line);
        return new ToolResult(write(m), true, code);
    }

    private static String write(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
