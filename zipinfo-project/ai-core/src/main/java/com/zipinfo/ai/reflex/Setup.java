package com.zipinfo.ai.reflex;

import com.fasterxml.jackson.databind.JsonNode;
import com.zipinfo.ai.cards.CardTools;
import com.zipinfo.ai.cards.Cards;
import com.zipinfo.ai.loop.Hook;
import com.zipinfo.ai.loop.Prompts;
import com.zipinfo.ai.loop.QuestionLoop;
import com.zipinfo.ai.tools.DataSet;
import com.zipinfo.ai.tools.RunState;
import com.zipinfo.ai.tools.ToolResult;
import com.zipinfo.ai.tools.ToolSpecs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 판 하나를 고리에 넣는 꼴로 만든다(설계안 7.7): 시스템 글, 도구 설명, 고리 설정(턴 상한, 사슬, 더하는 도구).
 * - 시스템 글은 모든 판이 같다. B0+(PROMPT)만 그 뒤에 빈 줄 하나를 두고 카드 전문을 붙인다(6.4, 순서 4 메모).
 * - 카드를 도구로 읽는 판은 search_cards·read_card를 더한다. ⓪이 든 판은 declare_intents를 더한다(순서 5b).
 * - 사슬은 부품의 차례(⓪ → ③ → ② → ④ → ⑤)로 놓는다(7.1).
 * - 턴 상한은 시운전에서 고른다(10.3). 그래서 받는다. 모델 설정도 하네스가 따로 준다.
 */
public record Setup(String system, List<ToolSpecs.Spec> specs, QuestionLoop.Config config) {

    public static Setup of(Variant v, DataSet data, Cards cards, int maxTurns) {
        String system = Prompts.SYSTEM;
        List<ToolSpecs.Spec> specs = new ArrayList<>(ToolSpecs.all());
        Map<String, BiFunction<JsonNode, RunState, ToolResult>> extra = new LinkedHashMap<>();
        if (v.cards() == Variant.CardMode.PROMPT) {
            system = Prompts.SYSTEM + "\n\n" + cards.fullText();
        } else if (v.cards() == Variant.CardMode.TOOLS) {
            specs.addAll(CardTools.SPECS);
            extra.putAll(CardTools.handlers(cards));
        }
        return new Setup(system, List.copyOf(specs), new QuestionLoop.Config(maxTurns, hooks(v.parts(), data), Map.copyOf(extra)));
    }

    /** 켠 부품을 사슬 차례로 만든다. ⓪은 순서 5b, ⑤는 순서 5c에서 더한다. */
    public static List<Hook> hooks(Collection<Part> parts, DataSet data) {
        List<Hook> out = new ArrayList<>();
        for (Part p : Part.values()) {
            if (!parts.contains(p)) {
                continue;
            }
            out.add(switch (p) {
                case INTENT -> throw new UnsupportedOperationException("⓪ 의도 잠금은 순서 5b에서 만든다");
                case LOCK -> new LearningLock(data.validity());
                case REFLEX -> new ReflexRule(data);
                case GATE -> new EvidenceGate();
                case MEMORY -> throw new UnsupportedOperationException("⑤ 해마·좋아요는 순서 5c에서 만든다");
            });
        }
        return List.copyOf(out);
    }
}
