package com.zipinfo.ai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** strict 제한(01 173줄): 모든 칸 필수, 다른 칸 없음, union·선택 칸 없음. 설명에 사고 과정을 적게 하는 말이 없다. */
class ToolSpecsTest {
    static void walk(JsonNode n, List<String> problems, String where) {
        if (n.has("anyOf") || n.has("oneOf") || n.has("allOf")) {
            problems.add(where + ": union");
        }
        if (n.has("minimum") || n.has("maximum")) {
            problems.add(where + ": 범위");
        }
        if ("object".equals(n.path("type").asText())) {
            List<String> props = new ArrayList<>();
            n.get("properties").fieldNames().forEachRemaining(props::add);
            List<String> req = new ArrayList<>();
            n.get("required").forEach(x -> req.add(x.asText()));
            if (!props.equals(req)) {
                problems.add(where + ": 선택 칸");
            }
            if (!n.path("additionalProperties").isBoolean() || n.get("additionalProperties").asBoolean()) {
                problems.add(where + ": additionalProperties");
            }
            Iterator<String> it = n.get("properties").fieldNames();
            while (it.hasNext()) {
                String f = it.next();
                walk(n.get("properties").get(f), problems, where + "." + f);
            }
        }
        if ("array".equals(n.path("type").asText())) {
            walk(n.get("items"), problems, where + "[]");
        }
    }

    @Test
    void strictSchemasHaveNoOptionalOrUnion() {
        List<String> problems = new ArrayList<>();
        for (ToolSpecs.Spec s : ToolSpecs.all()) {
            walk(s.inputSchema(), problems, s.name());
        }
        assertEquals(List.of(), problems);
        assertEquals(6, ToolSpecs.all().size());
    }

    @Test
    void descriptionsDoNotAskForReasoningOrTellDefaults() {
        for (ToolSpecs.Spec s : ToolSpecs.all()) {
            for (String bad : Set.of("생각", "사고 과정", "단계별", "추론", "12달", "잠정", "기본 기간")) {
                assertFalse(s.description().contains(bad), s.name() + ": " + bad);
            }
        }
        assertTrue(ToolSpecs.all().get(1).description().contains("계약 월 202311~202610"));
    }
}
