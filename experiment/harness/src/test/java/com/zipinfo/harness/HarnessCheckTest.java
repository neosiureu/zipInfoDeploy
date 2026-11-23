package com.zipinfo.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.data.ListingMeta;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 순서 6a: 실제 파일로 하네스를 짜 본다(요청은 보내지 않는다). 저장소의 pilot_v0.json과 판 설정 파일의 판 10개로 plan을 돌린다.
 * 문항 파일은 순서 7이 만든다. 여기서는 연습 매물 둘로 시험용 문항 파일을 임시로 만든다(질문 글은 지어낸 짧은 글이다).
 * 찍는 줄(하네스 검사)의 해시는 줄 끝을 LF로 맞춰 셈하므로 사용자 PC(Windows, git이 CRLF로 꺼냄)에서도 같아야 한다.
 * 순서 6b: 설정에 얼린 기억 파일을 걸면 ⑤가 든 판(c08·c09)의 지문만 바뀌는지도 본다.
 * 자료 쪽은 순서 2 결과와 얼린 매물·주소 파일이 있을 때만 돈다.
 */
class HarnessCheckTest {
    @Test
    void planOnTheRealFiles(@TempDir Path tmp) throws Exception {
        Path exp = Fixture.EXP;
        assumeTrue(Files.exists(exp.resolve("prep/out/replay_trades_v1.csv")) && Files.exists(exp.resolve("frozen/juso_road_v1.csv"))
                && Files.exists(exp.resolve("frozen/example_listings_v1.csv")));
        List<String> keys = ListingMeta.load(exp.resolve("frozen/example_listings_v1.csv")).stream().filter(m -> m.split().equals("연습"))
                .map(ListingMeta::key).limit(2).toList();
        ObjectNode qf = Fixture.M.createObjectNode().put("version", "check");
        ArrayNode qs = qf.putArray("questions");
        for (int i = 0; i < keys.size(); i++) {
            qs.addObject().put("id", "t" + (i + 1)).put("key", keys.get(i)).put("qtype", "T1").put("split", "연습").put("text", "시험용 글 " + (i + 1));
        }
        Path qfile = tmp.resolve("questions_check.json");
        Files.writeString(qfile, qf.toString(), StandardCharsets.UTF_8);
        ObjectNode n = (ObjectNode) Fixture.M.readTree(Files.readString(exp.resolve("harness/settings/pilot_v0.json"), StandardCharsets.UTF_8));
        ((ObjectNode) n.get("files")).put("questions", qfile.toString()).put("out", tmp.resolve("runs").toString());
        Path sfile = tmp.resolve("pilot_check.json");
        Files.writeString(sfile, n.toString(), StandardCharsets.UTF_8);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        assertEquals(0, Main.run(new String[] {"plan", sfile.toString()}, new PrintStream(b, true, StandardCharsets.UTF_8), exp, null));
        String out = b.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("판 10개(c01 c02 c03 c04 c05 c06 c07 c08 c09 c10), 문항 2개, 회차 [1, 2], 블록 25, 동시 1"), out);
        assertTrue(out.contains("실행 40개: 끝난 것 0, 시작한 것 0, 남은 것 40. 쓴 돈 $0.0000 / 상한 $20 (단가표 2026-11-22)"), out);
        assertTrue(out.contains("블록 차례: c01#1(4) c02#1(4) c03#1(4) c04#1(4) c05#1(4) c06#1(4) c07#1(4) c08#1(4) c09#1(4) c10#1(4)\n"), out);
        assertTrue(Files.exists(tmp.resolve("runs/pilot/plan.txt")));
        RunSettings s = RunSettings.load(sfile);
        Inputs in = Inputs.load(exp, s);
        Runner r = new Runner(in, s, tmp.resolve("runs/pilot"), (ms, c, t) -> {
            throw new IllegalStateException("요청을 보내지 않는다");
        }, x -> { }, new PrintStream(OutputStream.nullOutputStream()));
        assertEquals(10, r.prints().size());
        assertEquals("EMPTY", in.digests().memory(), "시운전 설정은 빈 기억이다");
        String all = Fingerprint.sha256(String.join("\n", r.prints().values()));
        // 순서 6b: 얼린 기억 파일을 걸면 ⑤가 든 판의 지문만 바뀐다(지문 memory 칸 = 그 파일의 해시)
        Path mem = tmp.resolve("memory_check.json");
        Files.writeString(mem, MemoryFile.text(new MemoryFile.Source("pilot", "c09", "연습", List.of("c09-t1-r1")), com.zipinfo.ai.reflex.Memory.EMPTY),
                StandardCharsets.UTF_8);
        ((ObjectNode) n.get("files")).put("memory", mem.toString());
        Path sfile2 = tmp.resolve("pilot_memory_check.json");
        Files.writeString(sfile2, n.toString(), StandardCharsets.UTF_8);
        RunSettings s2 = RunSettings.load(sfile2);
        Inputs in2 = new Inputs(in.data(), in.listings(), in.metas(), in.variants(), in.questions(), in.cards(), in.lists(), MemoryFile.read(mem).memory(),
                Inputs.digests(exp, s2));
        assertEquals(Fingerprint.file(mem), in2.digests().memory());
        Runner r2 = new Runner(in2, s2, tmp.resolve("runs/pilot"), (ms, c, t) -> {
            throw new IllegalStateException("요청을 보내지 않는다");
        }, x -> { }, new PrintStream(OutputStream.nullOutputStream()));
        List<String> moved = r.prints().keySet().stream().filter(k -> !r.prints().get(k).equals(r2.prints().get(k))).toList();
        assertEquals(List.of("c08", "c09"), moved);
        System.out.println("하네스 검사: pilot_v0 설정으로 판 10개 요청 지문(합) " + all.substring(0, 12) + ", 코드 " + in.digests().code().substring(0, 12)
                + ", 카드 " + in.digests().cards().substring(0, 12) + ", 목록 " + in.digests().lists().substring(0, 12)
                + ", 의존성 " + Fingerprint.runtimeDeps(exp.resolve("harness/gradle.lockfile")).size() + "개 " + in.digests().deps().substring(0, 12)
                + ", 시험 계획 2문항 × 10판 × 2회 = 40실행, 얼린 기억을 걸면 지문이 바뀌는 판 " + String.join(" ", moved));
    }
}
