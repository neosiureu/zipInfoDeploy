package com.zipinfo.ai.reflex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.cards.Card;
import com.zipinfo.ai.tools.RunState;
import com.zipinfo.ai.tools.ToolResult;
import com.zipinfo.ai.tools.ToolSpecs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * ⓪이 든 판의 도구 `declare_intents`(설계안 6.2, 7.2). 고리의 Config.extra로 붙고, 거는 자리 사슬을 돌지 않는다.
 * 통과하면 토큰마다 찾은 카드를 돌려주고 실행 상태에 통과를 남긴다(⓪의 시작 자리가 본다). 아니면 거절과 처방 한 줄이다.
 * 처방은 덮이지 않은 글자가 남은 절을 그대로 보이고 "이 부분의 의도도 토큰으로 적어라"라고 한다.
 */
public final class IntentTools {
    public static final String NAME = "declare_intents";
    private static final JsonNodeFactory F = JsonNodeFactory.instance;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> FIELDS = List.of("source_text", "corrected_text", "intent_expansion", "english_intent");

    public static final ToolSpecs.Spec SPEC = new ToolSpecs.Spec(NAME,
            "질문을 의도토큰으로 옮겨 적는다. 의도가 여럿이면 의도마다 적는다. 원어는 절 하나 안이어야 한다. "
                    + "절은 문장 끝과 이음 말('-고'·'-면'·'-서'·'그리고' 등)에서 나뉜다. 한 의도가 여러 절에 걸치면 절마다 토큰을 하나씩 적는다. "
                    + "카드는 영어의도의 낱말과, 보정원어·의도보강 안의 낱말로 찾는다. "
                    + "카드를 찾은 토큰의 원어가 질문의 대부분(77% 이상)을 덮으면 통과이고, 통과하면 토큰마다 찾은 카드를 돌려준다. "
                    + "통과하기 전에는 다른 자료·계산 도구와 submit_report를 쓸 수 없다.",
            object(List.of("intents"), List.of(array(object(FIELDS, List.of(
                    str("원어: 질문에서 이 부분을 글자 그대로 옮긴 것. 절을 넘지 않는다(문장 끝, '-고'·'-면'·'-서'·'그리고' 같은 이음 말)."),
                    str("보정원어: 원어의 맞춤법과 띄어쓰기를 바로잡은 것"),
                    str("의도보강: 이 의도를 풀어 쓴 한국어"),
                    str("영어의도: 이 의도를 영어 낱말 몇 개로 적은 것")))))));

    private IntentTools() {}

    private static ObjectNode str(String description) {
        return F.objectNode().put("type", "string").put("description", description);
    }

    private static ObjectNode array(ObjectNode items) {
        ObjectNode a = F.objectNode().put("type", "array").put("description", "의도토큰 목록(의도마다 하나)");
        a.set("items", items);
        return a;
    }

    /** 모든 칸이 필수이고 다른 칸은 받지 않는 object(strict 스키마, 01 173줄). */
    private static ObjectNode object(List<String> names, List<ObjectNode> schemas) {
        ObjectNode o = F.objectNode().put("type", "object");
        ObjectNode props = o.putObject("properties");
        var req = o.putArray("required");
        for (int i = 0; i < names.size(); i++) {
            props.set(names.get(i), schemas.get(i));
            req.add(names.get(i));
        }
        o.put("additionalProperties", false);
        return o;
    }

    public static BiFunction<JsonNode, RunState, ToolResult> handler(IntentCheck check) {
        return (in, st) -> {
            List<IntentCheck.Token> tokens;
            try {
                tokens = tokens(in);
            } catch (IllegalArgumentException e) {
                return error("BAD_INPUT", e.getMessage());
            }
            IntentCheck.Result r = check.check(st.question(), st.listing(), tokens);
            if (!r.passed()) {
                return error("REJECTED", "이 부분의 의도도 토큰으로 적어라: 「" + String.join("」, 「", r.uncovered()) + "」");
            }
            st.markIntentsPassed();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (IntentCheck.TokenResult t : r.tokens()) {
                List<Map<String, Object>> found = new ArrayList<>();
                for (Card c : t.cards()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("카드", c.id());
                    m.put("제목", c.title());
                    found.add(m);
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("원어", t.token().source());
                row.put("찾은 카드", found);
                rows.add(row);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("통과", true);
            out.put("토큰", rows);
            return new ToolResult(write(out), false, "");
        };
    }

    static List<IntentCheck.Token> tokens(JsonNode in) {
        JsonNode list = in == null ? null : in.get("intents");
        if (list == null || !list.isArray()) {
            throw new IllegalArgumentException("intents 칸이 없거나 목록이 아니다");
        }
        List<IntentCheck.Token> out = new ArrayList<>();
        for (JsonNode t : list) {
            List<String> v = new ArrayList<>();
            for (String f : FIELDS) {
                JsonNode x = t.get(f);
                if (x == null || !x.isTextual()) {
                    throw new IllegalArgumentException("의도토큰마다 " + String.join("·", FIELDS) + " 네 칸이 글이다");
                }
                v.add(x.asText());
            }
            out.add(new IntentCheck.Token(v.get(0), v.get(1), v.get(2), v.get(3)));
        }
        return out;
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
