package com.zipinfo.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.data.ListingMeta;
import com.zipinfo.ai.reflex.Variant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 순서 6a: 계획의 차례(블록마다 판을 번갈아, 01 341줄)와 나눔 막기(설계안 557줄), 고르기(판·문항·회차). */
class PlanTest {
    static final Variant C2 = new Variant("c02", "B0+", Fixture.HAIKU, Variant.CardMode.PROMPT, List.of());
    static final List<Variant> VS = List.of(Fixture.B0, C2, Fixture.ONE);

    static Map<String, ListingMeta> metas(String... splits) {
        Map<String, ListingMeta> m = new LinkedHashMap<>();
        for (int i = 0; i < splits.length; i++) {
            String k = String.format("L%02d", i + 1);
            m.put(k, new ListingMeta(k, "apt|41591|가동|0|12|0", "매매", "화성형", splits[i]));
        }
        return m;
    }

    static List<Question> qs(int n) {
        return java.util.stream.IntStream.rangeClosed(1, n)
                .mapToObj(i -> new Question(String.format("q%03d", i), String.format("L%02d", i), "T1", "연습", "글 " + i)).toList();
    }

    @Test
    void blocksAlternateBetweenVariants() throws Exception {
        RunSettings s = Fixture.settings(n -> {
            ((ObjectNode) n.get("select")).putArray("reps").add(1).add(2);
            n.put("block_size", 3);
        });
        List<Plan.RunSpec> plan = Plan.of(s, VS, qs(4), metas("연습", "연습", "연습", "연습"));
        assertEquals(24, plan.size());
        List<String> heads = new java.util.ArrayList<>();
        String last = null;
        for (Plan.RunSpec r : plan) {
            String b = r.variant().id() + "#" + r.block();
            if (!b.equals(last)) {
                heads.add(b + ":" + r.id());
                last = b;
            }
        }
        assertEquals(List.of("c01#1:c01-q001-r1", "c02#1:c02-q001-r1", "c03#1:c03-q001-r1", "c01#2:c01-q004-r1", "c02#2:c02-q004-r1",
                "c03#2:c03-q004-r1", "c01#3:c01-q003-r2", "c02#3:c02-q003-r2", "c03#3:c03-q003-r2"), heads);
        assertEquals("c03-q004-r2", plan.get(plan.size() - 1).id());
    }

    @Test
    void choosingVariantsQuestionsAndRepsKeepsTheFileOrder() throws Exception {
        RunSettings s = Fixture.settings(n -> {
            Fixture.variants(n, "c03", "c01");
            Fixture.questions(n, "q002");
            ((ObjectNode) n.get("select")).putArray("reps").add(3);
        });
        assertEquals(List.of("c01-q002-r3", "c03-q002-r3"), Plan.of(s, VS, qs(3), metas("연습", "연습", "연습")).stream().map(Plan.RunSpec::id).toList());
        RunSettings bad = Fixture.settings(n -> Fixture.variants(n, "c99"));
        assertThrows(IllegalArgumentException.class, () -> Plan.of(bad, VS, qs(1), metas("연습")));
    }

    @Test
    void testQuestionsStayOutUntilTheMainExperiment() throws Exception {
        List<Question> q = List.of(new Question("q001", "L01", "T1", "연습", "가"), new Question("q002", "L02", "T1", "본시험", "나"));
        Map<String, ListingMeta> m = metas("연습", "본시험");
        RunSettings practice = Fixture.settings(n -> { });
        assertEquals(List.of("c01-q001-r1"), Plan.of(practice, List.of(Fixture.B0), q, m).stream().map(Plan.RunSpec::id).toList());
        RunSettings asks = Fixture.settings(n -> Fixture.questions(n, "q002"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Plan.of(asks, List.of(Fixture.B0), q, m));
        assertTrue(e.getMessage().contains("본실험 설정"), e.getMessage());
        RunSettings main = Fixture.settings(n -> n.put("split", "본시험"));
        assertEquals(List.of("c01-q002-r1"), Plan.of(main, List.of(Fixture.B0), q, m).stream().map(Plan.RunSpec::id).toList());
        // 문항의 나눔이 매물과 다르면 멈춘다
        List<Question> wrong = List.of(new Question("q001", "L02", "T1", "연습", "가"));
        assertThrows(IllegalArgumentException.class, () -> Plan.of(practice, List.of(Fixture.B0), wrong, m));
        // 매물이 없는 문항
        List<Question> none = List.of(new Question("q001", "L09", "T1", "연습", "가"));
        assertThrows(IllegalArgumentException.class, () -> Plan.of(practice, List.of(Fixture.B0), none, m));
    }

    @Test
    void questionFile() {
        String ok = "{\"version\":\"v0\",\"questions\":[{\"id\":\"q001\",\"key\":\"L01\",\"qtype\":\"T2\",\"split\":\"연습\",\"text\":\"가\"}]}";
        assertEquals(List.of(new Question("q001", "L01", "T2", "연습", "가")), Question.parse(ok));
        for (String bad : List.of(ok.replace("q001", "Q-1"), ok.replace("T2", "T6"), ok.replace("연습", "dev"), ok.replace(",\"text\":\"가\"", ""),
                ok.replace("\"가\"", "\"\""), ok.replace("\"가\"", "\"가\""))) {
            assertThrows(IllegalArgumentException.class, () -> Question.parse(bad), bad);
        }
        String twice = ok.replace("]}", ",{\"id\":\"q001\",\"key\":\"L02\",\"qtype\":\"T1\",\"split\":\"연습\",\"text\":\"나\"}]}");
        assertThrows(IllegalArgumentException.class, () -> Question.parse(twice));
    }
}
