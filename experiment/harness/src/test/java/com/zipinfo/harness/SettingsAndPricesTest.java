package com.zipinfo.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.model.CachePolicy;
import com.zipinfo.ai.model.ModelSettings;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** 순서 6a: 설정 파일(pilot_v0.json)과 단가표(01 333·342줄). */
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
        bad(n -> ((ObjectNode) n.get("files")).put("memory", "frozen/memory_v1.json"));
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
