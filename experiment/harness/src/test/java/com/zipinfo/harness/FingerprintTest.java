package com.zipinfo.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zipinfo.ai.reflex.Memory;
import com.zipinfo.ai.reflex.Part;
import com.zipinfo.ai.reflex.Setup;
import com.zipinfo.ai.reflex.Variant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 순서 6a: 요청 지문(01 200줄). 줄 끝이 CRLF여도 같고, 판이 쓰는 것만 든다. */
class FingerprintTest {
    @TempDir
    Path tmp;

    @Test
    void lineEndsDoNotChangeTheHash() throws Exception {
        Path a = Files.createDirectories(tmp.resolve("a/com/x"));
        Path b = Files.createDirectories(tmp.resolve("b/com/x"));
        Files.writeString(a.resolve("A.java"), "class A {\n    // 가\n}\n", StandardCharsets.UTF_8);
        Files.writeString(b.resolve("A.java"), "class A {\r\n    // 가\r\n}\r\n", StandardCharsets.UTF_8);
        Files.writeString(a.resolve("B.java"), "class B {}\n", StandardCharsets.UTF_8);
        Files.writeString(b.resolve("B.java"), "class B {}\n", StandardCharsets.UTF_8);
        Files.writeString(a.resolve("note.txt"), "소스가 아니다", StandardCharsets.UTF_8);
        assertEquals(Fingerprint.sources(tmp.resolve("a")), Fingerprint.sources(tmp.resolve("b")));
        assertEquals(Fingerprint.file(a.resolve("A.java")), Fingerprint.file(b.resolve("A.java")));
        Files.writeString(b.resolve("B.java"), "class B { }\n", StandardCharsets.UTF_8);
        assertNotEquals(Fingerprint.sources(tmp.resolve("a")), Fingerprint.sources(tmp.resolve("b")));
        assertEquals("a\rb\n", new String(Fingerprint.lf("a\rb\r\n".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
    }

    /** 6a b판(상대): 요청 JSON을 짓는 SDK의 판이 지문에 든다. 시험에만 쓰는 의존성은 들지 않는다. */
    @Test
    void runtimeDependencyVersionsAreInThePrint() throws Exception {
        String head = "# This is a Gradle generated file for dependency locking.\n";
        String sdk = "com.anthropic:anthropic-java-core:3.11.0=compileClasspath,runtimeClasspath,testCompileClasspath,testRuntimeClasspath\n";
        String json = "com.fasterxml.jackson.core:jackson-databind:2.19.4=compileClasspath,runtimeClasspath\n";
        String junit = "org.junit.jupiter:junit-jupiter-api:5.14.4=testCompileClasspath,testRuntimeClasspath\n";
        String empty = "empty=annotationProcessor,testAnnotationProcessor\n";
        Path f = tmp.resolve("gradle.lockfile");
        Files.writeString(f, head + sdk + json + junit + empty, StandardCharsets.UTF_8);
        String base = Fingerprint.deps(f);
        assertEquals(List.of("com.anthropic:anthropic-java-core:3.11.0", "com.fasterxml.jackson.core:jackson-databind:2.19.4"), Fingerprint.runtimeDeps(f));
        // 실행에 쓰는 판이 바뀌면 지문이 바뀐다
        Files.writeString(f, head + sdk.replace("3.11.0", "3.12.0") + json + junit + empty, StandardCharsets.UTF_8);
        assertNotEquals(base, Fingerprint.deps(f));
        // 시험에만 쓰는 판, 같은 줄의 시험 쪽 칸, 줄 끝은 지문을 바꾸지 않는다
        Files.writeString(f, head + sdk + json + junit.replace("5.14.4", "5.15.0") + empty, StandardCharsets.UTF_8);
        assertEquals(base, Fingerprint.deps(f));
        Files.writeString(f, head + sdk + json.replace("runtimeClasspath", "runtimeClasspath,testRuntimeClasspath") + junit + empty, StandardCharsets.UTF_8);
        assertEquals(base, Fingerprint.deps(f));
        Files.writeString(f, (head + sdk + json + junit + empty).replace("\n", "\r\n"), StandardCharsets.UTF_8);
        assertEquals(base, Fingerprint.deps(f));
        // 저장소의 하네스 잠금 파일: runtimeClasspath 판 30개, SDK 3.11.0이 든다
        List<String> real = Fingerprint.runtimeDeps(Fixture.EXP.resolve("harness/gradle.lockfile"));
        assertEquals(30, real.size());
        assertTrue(real.contains("com.anthropic:anthropic-java-core:3.11.0"));
        assertTrue(real.stream().noneMatch(d -> d.startsWith("org.junit")));
    }

    @Test
    void aVariantPrintHoldsOnlyWhatThatVariantUses() throws Exception {
        Inputs in = Fixture.inputs(tmp);
        RunSettings s = Fixture.settings(n -> { });
        Variant intent = new Variant("c04", "①+⓪", Fixture.HAIKU, Variant.CardMode.TOOLS, List.of(Part.INTENT));
        Variant memory = new Variant("c08", "①+⑤", Fixture.HAIKU, Variant.CardMode.TOOLS, List.of(Part.MEMORY));
        Map<String, List<String>> want = Map.of(
                "c01", List.of("variant", "system", "tools", "model", "cache", "max_turns", "data", "code", "deps"),
                "c03", List.of("variant", "system", "tools", "model", "cache", "max_turns", "cards", "data", "code", "deps"),
                "c04", List.of("variant", "system", "tools", "model", "cache", "max_turns", "cards", "lists", "data", "code", "deps"),
                "c08", List.of("variant", "system", "tools", "model", "cache", "max_turns", "cards", "memory", "data", "code", "deps"));
        for (Variant v : List.of(Fixture.B0, Fixture.ONE, intent, memory)) {
            Setup setup = Setup.of(v, in.data(), in.cards(), in.lists(), Memory.EMPTY, s.maxTurns());
            Map<String, Object> p = Fingerprint.parts(v, setup, s.model(v.model()), s.cache(), s.maxTurns(), in.digests());
            assertEquals(want.get(v.id()), List.copyOf(p.keySet()), v.id());
            assertEquals(Fingerprint.of(p), Fingerprint.of(Fingerprint.parts(v, setup, s.model(v.model()), s.cache(), s.maxTurns(), in.digests())));
        }
        // 실행 지문은 매물 키와 질문 글로 갈린다
        String vp = "V";
        assertNotEquals(Fingerprint.run(vp, Fixture.Q1), Fingerprint.run(vp, new Question("q001", "L01", "T1", "연습", "시험 글 가!")));
        assertEquals(Fingerprint.run(vp, Fixture.Q1), Fingerprint.run(vp, new Question("q001", "L01", "T5", "연습", "시험 글 가")));
        assertTrue(Fingerprint.sha256("").matches("[0-9A-F]{64}"));
        assertFalse(Fingerprint.of(Map.of("a", 1)).equals(Fingerprint.of(Map.of("a", 2))));
    }
}
