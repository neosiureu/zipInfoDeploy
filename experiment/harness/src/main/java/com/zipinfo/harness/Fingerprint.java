package com.zipinfo.harness;

import com.zipinfo.ai.model.CachePolicy;
import com.zipinfo.ai.model.ModelSettings;
import com.zipinfo.ai.reflex.Part;
import com.zipinfo.ai.reflex.Setup;
import com.zipinfo.ai.reflex.Variant;
import com.zipinfo.ai.tools.ToolSpecs;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * 요청 지문(01 200줄, 순서 6a). 판의 요청을 정하는 것을 모두 해시로 적는다. 이어 돌릴 때 지금 지문이 기록 머리의 지문과 다르면 멈춘다.
 * <p>판 지문의 칸(parts): 판(id·이름·모델·카드 방식·부품), 시스템 글, 도구 정의, 모델 설정, 캐시 정책, 턴 상한,
 * 카드(카드를 쓰는 판), ⓪의 목록(⓪이 든 판), ⑤의 기억(⑤가 든 판), 도구 자료(파일 해시), 코드(ai-core의 main 소스),
 * 의존성(하네스 잠금 파일의 runtimeClasspath 판들. 요청 JSON을 짓는 SDK가 여기 든다, 6a b판).
 * 실행 지문은 판 지문에 그 문항의 매물 키와 질문 글을 더한 것이다.
 * <ul>
 * <li>판이 쓰지 않는 것은 넣지 않는다. 그래서 카드를 고쳐도 카드가 없는 판(B0·B1)의 실행은 그대로 쓴다(01 200줄: 그 고침이 닿는 판만).</li>
 * <li>코드: 도구 결과와 장치 글은 ai-core 코드가 만든다. 하네스 코드는 요청을 짓지 않고 값만 넘기는데, 그 값(설정)은 위 칸에 다 들었다.
 *     그래서 하네스 코드의 해시는 지문에 넣지 않고 기록 머리에 따로 남긴다(harness_code).</li>
 * <li>글 파일(소스·카드·목록)은 줄 끝을 LF로 맞춰 셈한다. Windows에서 git이 CRLF로 꺼내도 지문이 같다.</li>
 * </ul>
 */
public final class Fingerprint {
    private Fingerprint() {}

    public static String sha256(byte[] b) {
        try {
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(String s) {
        return sha256(s.getBytes(StandardCharsets.UTF_8));
    }

    /** CR LF를 LF로. 홀로 있는 CR은 그대로 둔다. */
    public static byte[] lf(byte[] b) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(b.length);
        for (int i = 0; i < b.length; i++) {
            if (b[i] == '\r' && i + 1 < b.length && b[i + 1] == '\n') {
                continue;
            }
            out.write(b[i]);
        }
        return out.toByteArray();
    }

    /** 글 파일 하나(줄 끝 LF로 맞춤)의 SHA-256. */
    public static String file(Path p) throws IOException {
        return sha256(lf(Files.readAllBytes(p)));
    }

    /** 폴더 아래 파일들의 목록 "SHA-256  상대 경로"(경로 차례, 구분자 /)의 SHA-256. 파일 해시는 줄 끝을 LF로 맞춘 것이다. */
    static String tree(Path dir, List<Path> files) throws IOException {
        TreeMap<String, String> lines = new TreeMap<>();
        for (Path f : files) {
            lines.put(dir.relativize(f).toString().replace('\\', '/'), file(f));
        }
        StringBuilder sb = new StringBuilder();
        lines.forEach((p, h) -> sb.append(h).append("  ").append(p).append('\n'));
        return sha256(sb.toString());
    }

    /** 소스 폴더(…/src/main/java) 아래 .java 모두. */
    public static String sources(Path javaRoot) throws IOException {
        if (!Files.isDirectory(javaRoot)) {
            throw new IOException("소스 폴더가 없다: " + javaRoot);
        }
        List<Path> files;
        try (Stream<Path> s = Files.walk(javaRoot)) {
            files = s.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".java")).toList();
        }
        return tree(javaRoot, files);
    }

    /**
     * 하네스 잠금 파일(gradle.lockfile)에서 runtimeClasspath에 든 의존성. 줄의 = 앞(group:artifact:version)만 글자 차례로.
     * 시험에만 쓰는 의존성(JUnit 등)이나 같은 줄의 시험 쪽 칸이 바뀌어도 그대로다. 줄 끝(CRLF)은 보지 않는다.
     */
    public static List<String> runtimeDeps(Path lockfile) throws IOException {
        List<String> out = new ArrayList<>();
        for (String line : Files.readAllLines(lockfile, StandardCharsets.UTF_8)) {
            String t = line.strip();
            int eq = t.indexOf('=');
            if (t.isEmpty() || t.startsWith("#") || eq < 0) {
                continue;
            }
            if (List.of(t.substring(eq + 1).split(",")).contains("runtimeClasspath")) {
                out.add(t.substring(0, eq));
            }
        }
        if (out.isEmpty()) {
            throw new IOException("잠금 파일에 runtimeClasspath 의존성이 없다: " + lockfile);
        }
        Collections.sort(out);
        return out;
    }

    /** runtimeDeps를 줄마다 하나씩 적은 글의 SHA-256. */
    public static String deps(Path lockfile) throws IOException {
        return sha256(String.join("\n", runtimeDeps(lockfile)) + "\n");
    }

    /** 카드 폴더 바로 아래 파일 모두. */
    public static String cards(Path dir) throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(Files::isRegularFile).toList();
        }
        return tree(dir, files);
    }

    public static Map<String, Object> json(Variant v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.id());
        m.put("name", v.name());
        m.put("model", v.model());
        m.put("cards", v.cards().name());
        m.put("parts", v.parts().stream().map(Part::symbol).toList());
        return m;
    }

    public static Map<String, Object> json(ModelSettings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("model", s.model());
        m.put("max_tokens", s.maxTokens());
        m.put("thinking", s.thinking().name());
        m.put("effort", s.effort());
        return m;
    }

    public static Map<String, Object> json(CachePolicy c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("system", c.system());
        m.put("conversation", c.conversation());
        m.put("ttl", c.ttl());
        return m;
    }

    /** 도구 정의(보내는 이름·설명·입력 스키마). */
    public static List<Map<String, Object>> json(List<ToolSpecs.Spec> specs) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ToolSpecs.Spec s : specs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.name());
            m.put("description", s.description());
            m.put("input_schema", s.inputSchema());
            out.add(m);
        }
        return out;
    }

    /** 판 하나의 지문 칸. setup은 그 판을 Setup.of로 짠 것이다. */
    public static Map<String, Object> parts(Variant v, Setup setup, ModelSettings model, CachePolicy cache, int maxTurns, Inputs.Digests h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("variant", json(v));
        m.put("system", sha256(setup.system()));
        m.put("tools", sha256(Json.write(json(setup.specs()))));
        m.put("model", json(model));
        m.put("cache", json(cache));
        m.put("max_turns", maxTurns);
        if (v.cards() != Variant.CardMode.NONE) {
            m.put("cards", h.cards());
        }
        if (v.parts().contains(Part.INTENT)) {
            m.put("lists", h.lists());
        }
        if (v.parts().contains(Part.MEMORY)) {
            m.put("memory", h.memory());
        }
        m.put("data", new TreeMap<>(h.data()));
        m.put("code", h.code());
        m.put("deps", h.deps());
        return m;
    }

    public static String of(Map<String, Object> parts) {
        return sha256(Json.write(parts));
    }

    public static String run(String variantPrint, Question q) {
        return sha256(variantPrint + "\n" + q.key() + "\n" + q.text());
    }
}
