package com.zipinfo.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.model.CachePolicy;
import com.zipinfo.ai.model.ModelSettings;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * 순서 6a: 설정 파일(pilot_v0.json)과 단가표(01 333·342줄). 6b: files.memory가 얼린 기억 파일의 경로를 받는다. 6c: 판정 설정(judge 칸).
 * 11b-1: 판정 v1 설정(pilot_v0_judge_v1.json)은 pilot_v0.json에서 judge.instructions만 다르다.
 * 11b-2: 11c 설정(pilot2_v1.json)은 판정 v1 설정에서 version·stage·카드·⓪ 목록·기억·회차·동시 수·상한만 다르다.
 */
class SettingsAndPricesTest {
    static void bad(Consumer<ObjectNode> edit) {
        assertThrows(IllegalArgumentException.class, () -> Fixture.settings(edit));
    }

    @Test
    void theCommittedPilotSettingsRead() throws Exception {
        RunSettings s = RunSettings.load(Fixture.EXP.resolve("harness/settings/pilot_v0.json"));
        assertEquals(List.of("pilot", "연습", "harness/runs"), List.of(s.stage(), s.split(), s.files().out()));
        assertEquals(List.of(1, 2), s.select().reps());
        assertEquals(List.of(), s.select().variants());
        assertEquals(new ModelSettings("claude-haiku-5-5", 16000, ModelSettings.Thinking.ADAPTIVE, null), s.model("claude-haiku-5-5"));
        assertEquals(new ModelSettings("claude-opus-5-5", 16000, ModelSettings.Thinking.OMIT, null), s.model("claude-opus-5-5"));
        assertEquals(new CachePolicy(true, true, "5m"), s.cache());
        assertEquals(new RunSettings.Retry(8, 2, 120), s.retry());
        assertEquals(List.of(20, 600, 1, 25), List.of(s.maxTurns(), s.timeoutSeconds(), s.concurrency(), s.blockSize()));
        assertEquals(0, new BigDecimal("20").compareTo(s.costCapUsd()));
        assertEquals(List.of("2026-11-22", "0.5"), List.of(s.prices().date(), s.prices().batchFactor().toPlainString()));
        assertEquals(null, s.files().memory(), "시운전은 빈 기억으로 돈다");
        RunSettings frozen = Fixture.settings(n -> ((ObjectNode) n.get("files")).put("memory", "harness/frozen/memory_v0.json"));
        assertEquals("harness/frozen/memory_v0.json", frozen.files().memory(), "순서 6b: 얼린 기억 파일의 경로를 받는다");
        assertEquals(new JudgeSettings("claude-haiku-5-5", 8000, 16000, ModelSettings.Thinking.ADAPTIVE, null, "auto", "harness/judge/judge_v0.md", 60),
                s.judge(), "순서 6c: 판정 설정 v0");
    }

    @Test
    void theJudgeV1SettingsDifferOnlyInTheInstructions() throws Exception {
        // 순서 11b 설계 4판 4.3·2.4의 1: 판정만 v1 지시문으로 다시 낸다. version은 v0 그대로라 기록·plan.txt·채점이 같은 단계로 읽는다
        ObjectNode a = (ObjectNode) Fixture.M.readTree(Files.readString(Fixture.EXP.resolve("harness/settings/pilot_v0.json"), StandardCharsets.UTF_8));
        ObjectNode b = (ObjectNode) Fixture.M.readTree(Files.readString(Fixture.EXP.resolve("harness/settings/pilot_v0_judge_v1.json"), StandardCharsets.UTF_8));
        assertEquals("harness/judge/judge_v1.md", b.get("judge").get("instructions").asText());
        judge(a).remove("instructions");
        judge(b).remove("instructions");
        assertEquals(a, b, "judge.instructions 밖의 칸은 pilot_v0.json과 같다");
        RunSettings s = RunSettings.load(Fixture.EXP.resolve("harness/settings/pilot_v0_judge_v1.json"));
        assertEquals("v0", s.version());
        assertEquals(new JudgeSettings("claude-haiku-5-5", 8000, 16000, ModelSettings.Thinking.ADAPTIVE, null, "auto", "harness/judge/judge_v1.md", 60),
                s.judge());
        assertTrue(Files.exists(Fixture.EXP.resolve("harness/judge/judge_v1.md")));
    }

    @Test
    void the11cSettingsDifferOnlyWhereTheDesignSays() throws Exception {
        // 순서 11b-2(설계 b판 8절, 4판 7절): 검사기 v1 기록(version v1), 시운전 뒤 카드 v1, ⓪ 목록 v1, 얼린 기억, 연습 문항 한 번, 동시 2, 상한 $10
        ObjectNode a = (ObjectNode) Fixture.M.readTree(Files.readString(Fixture.EXP.resolve("harness/settings/pilot_v0_judge_v1.json"), StandardCharsets.UTF_8));
        ObjectNode b = (ObjectNode) Fixture.M.readTree(Files.readString(Fixture.EXP.resolve("harness/settings/pilot2_v1.json"), StandardCharsets.UTF_8));
        RunSettings s = RunSettings.load(Fixture.EXP.resolve("harness/settings/pilot2_v1.json"));
        assertEquals(List.of("v1", "pilot2", "연습"), List.of(s.version(), s.stage(), s.split()));
        assertEquals(List.of("cards/v1", "intents/intent_lists_v1.json", "harness/frozen/memory_v0.json"),
                List.of(s.files().cards(), s.files().lists(), s.files().memory()));
        assertEquals(List.of(1), s.select().reps());
        assertEquals(List.of(20, 2), List.of(s.maxTurns(), s.concurrency()));
        assertEquals(0, new BigDecimal("10").compareTo(s.costCapUsd()));
        for (String k : List.of("version", "stage", "select", "concurrency", "cost_cap_usd")) {
            a.remove(k);
            b.remove(k);
        }
        for (String k : List.of("cards", "lists", "memory")) {
            ((ObjectNode) a.get("files")).remove(k);
            ((ObjectNode) b.get("files")).remove(k);
        }
        assertEquals(a, b, "그 밖의 칸(모델·턴 상한·캐시·재시도·단가표·판정 v1)은 판정 v1 설정과 같다");
    }

    static ObjectNode judge(ObjectNode n) {
        return (ObjectNode) n.get("judge");
    }

    static String refused(Consumer<ObjectNode> edit) {
        return assertThrows(IllegalArgumentException.class, () -> Fixture.settings(edit)).getMessage();
    }

    @Test
    void theJudgeSectionIsCheckedWhenRead() throws Exception {
        bad(n -> n.remove("judge"));
        bad(n -> judge(n).put("extra", 1));
        bad(n -> judge(n).put("poll_seconds", 9));
        bad(n -> judge(n).put("poll_seconds", 3601));
        bad(n -> judge(n).put("instructions", ""));
        bad(n -> judge(n).put("thinking", "enabled"));
        bad(n -> judge(n).put("effort", "highest"));
        bad(n -> judge(n).put("tool_choice", "any"));
        assertTrue(refused(n -> judge(n).put("model", "claude-sonnet-5-5")).contains("단가표에 없는 모델"));
        assertTrue(refused(n -> judge(n).put("max_tokens_raised", 8000)).contains("max_tokens보다 크다"));
        // 강제 호출은 thinking이 disabled일 때만(강제하면 thinking을 건너뛴다)
        assertTrue(refused(n -> judge(n).put("tool_choice", "tool")).contains("thinking은 disabled다"));
        assertTrue(refused(n -> judge(n).put("tool_choice", "tool").put("thinking", "omit")).contains("thinking은 disabled다"));
        assertEquals("tool", Fixture.settings(n -> judge(n).put("tool_choice", "tool").put("thinking", "disabled")).judge().toolChoice());
        // disabled이면 effort는 null·low·medium·high(xhigh·max면 400)
        assertTrue(refused(n -> judge(n).put("thinking", "disabled").put("effort", "xhigh")).contains("null·low·medium·high"));
        assertTrue(refused(n -> judge(n).put("thinking", "disabled").put("effort", "max")).contains("null·low·medium·high"));
        assertEquals("high", Fixture.settings(n -> judge(n).put("thinking", "disabled").put("effort", "high")).judge().effort());
        assertNull(Fixture.settings(n -> judge(n).put("thinking", "disabled")).judge().effort());
        // Opus는 disabled와 tool을 받지 않는다(400)
        assertTrue(refused(n -> judge(n).put("model", "claude-opus-5-5").put("thinking", "disabled")).contains("Opus 판정은 thinking disabled"));
        assertTrue(refused(n -> judge(n).put("model", "claude-opus-5-5").put("tool_choice", "tool")).contains("Opus 판정은 tool_choice auto뿐"));
        assertEquals("claude-opus-5-5", Fixture.settings(n -> judge(n).put("model", "claude-opus-5-5").put("thinking", "omit").put("effort", "max"))
                .judge().model());
    }

    @Test
    void wrongSettingsStop() {
        bad(n -> n.put("extra", 1));
        bad(n -> n.remove("cache"));
        bad(n -> n.put("split", "dev"));
        bad(n -> n.put("stage", "Pilot"));
        bad(n -> ((ObjectNode) n.get("cache")).put("ttl", "10m"));
        bad(n -> ((ObjectNode) n.get("models").get("claude-opus-5-5")).put("max_tokens", 32000));
        bad(n -> ((ObjectNode) n.get("models").get("claude-opus-5-5")).put("thinking", "enabled"));
        bad(n -> ((ObjectNode) n.get("models").get("claude-haiku-5-5")).put("effort", "highest"));
        bad(n -> ((ObjectNode) n.get("select")).putArray("reps").add(1).add(1));
        bad(n -> ((ObjectNode) n.get("files")).put("memory", ""));
        bad(n -> ((ObjectNode) n.get("retry")).put("max_seconds", 1));
        bad(n -> ((ObjectNode) n.get("prices").get("models")).remove("claude-opus-5-5"));
        bad(n -> ((ArrayNode) n.get("prices").get("models").get("claude-haiku-5-5")).remove(1));
        bad(n -> n.put("cost_cap_usd", 0));
    }

    static Prices prices() throws Exception {
        return Fixture.settings(n -> { }).prices();
    }

    static BigDecimal usd(String s) {
        return new BigDecimal(s);
    }

    @Test
    void haikuPricesTheWholeRequestAtTheHigherTierPast100k() throws Exception {
        Prices p = prices();
        String h = "claude-haiku-5-5";
        // 10만 이하: 입력 0.10, 캐시 읽기 0.01, 5분 쓰기 0.125, 출력 0.50
        Prices.Use low = new Prices.Use(1000, 50_000, 20_000, 0, 2000);
        assertEquals(0, usd("0.0041").compareTo(p.withCache(h, low)));
        assertEquals(0, usd("0.0081").compareTo(p.withoutCache(h, low)));
        // 딱 10만은 아래 단
        Prices.Use edge = new Prices.Use(0, 100_000, 0, 0, 0);
        assertEquals(0, usd("0.001").compareTo(p.withCache(h, edge)));
        // 10만을 넘으면 그 요청 전체가 0.50·2.50·캐시 읽기 0.05(01 333줄). 캐시 읽기 토큰도 프롬프트에 든다
        Prices.Use high = new Prices.Use(1000, 100_000, 0, 0, 2000);
        assertEquals(0, usd("0.0105").compareTo(p.withCache(h, high)));
        assertEquals(0, usd("0.0555").compareTo(p.withoutCache(h, high)));
        // Opus: 4·20, 캐시 읽기 0.20(0.05배), 1시간 쓰기 8
        Prices.Use opus = new Prices.Use(1000, 2000, 0, 10_000, 500);
        assertEquals(0, usd("0.0944").compareTo(p.withCache("claude-opus-5-5", opus)));
        assertThrows(IllegalArgumentException.class, () -> p.tier("claude-sonnet-5-5", 1));
    }

    @Test
    void cacheWritesSplitByTimeToLive() throws Exception {
        ObjectNode e = Fixture.M.createObjectNode().put("input_tokens", 10).put("cache_read_tokens", 20).put("cache_write_tokens", 70).put("output_tokens", 5);
        assertEquals(new Prices.Use(10, 20, 70, 0, 5), Prices.use(e, "5m"));
        assertEquals(new Prices.Use(10, 20, 0, 70, 5), Prices.use(e, "1h"));
        e.putObject("raw").putObject("usage").putObject("cache_creation").put("ephemeral_5m_input_tokens", 30).put("ephemeral_1h_input_tokens", 40);
        assertEquals(new Prices.Use(10, 20, 30, 40, 5), Prices.use(e, "5m"));
        ((ObjectNode) e.get("raw").get("usage").get("cache_creation")).put("ephemeral_1h_input_tokens", 41);
        assertEquals(new Prices.Use(10, 20, 70, 0, 5), Prices.use(e, "5m"));
    }
}
