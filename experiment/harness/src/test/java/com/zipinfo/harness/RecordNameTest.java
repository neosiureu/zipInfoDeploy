package com.zipinfo.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zipinfo.ai.model.AnthropicModelClient;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 순서 6c: 기록 파일 이름 검사(상대 09:5x). 파일 이름이 머리의 run_id + ".jsonl"이 아닌 기록(손으로 복사한 파일, 이름을 바꾼 파일)은 기록 문제다.
 * 세지 않고(쓴 돈·끝난 실행), plan은 찍기만 하고, run·memory는 멈추고, summary는 멈춘다(Main이 2로 끝낸다). judge는 JudgeTest가 본다.
 */
class RecordNameTest {
    FakeAnthropic api;
    @TempDir
    Path tmp;

    @BeforeEach
    void up() throws Exception {
        api = new FakeAnthropic();
    }

    @AfterEach
    void down() {
        api.close();
    }

    static String line(String text, String start) {
        return text.lines().filter(l -> l.startsWith(start)).findFirst().orElse("");
    }

    @Test
    void aCopiedOrRenamedRecordIsARecordProblem() throws Exception {
        Inputs in = Fixture.inputs(Files.createTempDirectory(tmp, "data"));
        RunSettings s = Fixture.settings(n -> {
            Fixture.variants(n, "c01");
            Fixture.questions(n, "q001", "q002");
        });
        Path runs = tmp.resolve("runs");
        Fixture.solve().forEach(api::then);
        Fixture.solve().forEach(api::then);
        Runner r = new Runner(in, s, runs, (ms, cache, t) -> new AnthropicModelClient("test-key", api.url(), ms, t, cache), x -> { },
                new PrintStream(OutputStream.nullOutputStream()));
        assertNull(r.run().stop());
        String before = Main.planText(r, s);
        assertTrue(line(before, "실행 ").startsWith("실행 2개: 끝난 것 2, 시작한 것 0, 남은 것 0. 쓴 돈 $"), before);
        Path copy = runs.resolve("c01-q001-r1-copy.jsonl");
        Files.copy(runs.resolve("c01-q001-r1.jsonl"), copy);
        String plan = Main.planText(r, s);
        assertTrue(plan.contains("기록 문제: c01-q001-r1-copy.jsonl: 파일 이름이 머리의 실행 ID(c01-q001-r1)와 다르다. 손으로 복사했거나 이름을 바꾼 기록이다. "
                + "다른 폴더로 옮긴다\n"), plan);
        assertEquals(line(before, "실행 "), line(plan, "실행 "), "복사본을 세지 않는다(쓴 돈·끝난 실행이 그대로)");
        // run: 아무 요청도 보내지 않고 멈춘다
        int sent = api.requestCount();
        Runner.Result res = r.run();
        assertEquals(Control.Kind.STOP_AND_LOOK, res.stop().kind());
        assertEquals(sent, api.requestCount());
        // summary: 복사본이 끝난 꼴과 비용을 부풀리지 않게 멈춘다
        IOException e = assertThrows(IOException.class, () -> Summary.rows(r.status(), s, in.variants(), runs));
        assertTrue(e.getMessage().startsWith("기록 문제: c01-q001-r1-copy.jsonl: 파일 이름이 머리의 실행 ID(c01-q001-r1)와 다르다"), e.getMessage());
        // memory: 얼리지 않는다
        IllegalArgumentException m = assertThrows(IllegalArgumentException.class, () -> MemoryFile.freeze(r, in, s, "c01", tmp.resolve("m.json")));
        assertTrue(m.getMessage().startsWith("기록 문제가 있어 얼리지 않는다: c01-q001-r1-copy.jsonl: 파일 이름이"), m.getMessage());
        // 이름을 바꾼 기록도 같다. 그 실행은 끝난 것으로 세지 않는다
        Files.delete(copy);
        Files.move(runs.resolve("c01-q002-r1.jsonl"), runs.resolve("c01-q002.jsonl"));
        String renamed = Main.planText(r, s);
        assertTrue(renamed.contains("기록 문제: c01-q002.jsonl: 파일 이름이 머리의 실행 ID(c01-q002-r1)와 다르다"), renamed);
        assertTrue(line(renamed, "실행 ").startsWith("실행 2개: 끝난 것 1, 시작한 것 0, 남은 것 1."), renamed);
        // 고치면(제 이름으로 돌려 놓으면) 문제가 없다
        Files.move(runs.resolve("c01-q002.jsonl"), runs.resolve("c01-q002-r1.jsonl"));
        assertEquals(before, Main.planText(r, s));
        assertEquals(4, Summary.rows(r.status(), s, in.variants(), runs).size(), "판 셋과 합");
    }
}
