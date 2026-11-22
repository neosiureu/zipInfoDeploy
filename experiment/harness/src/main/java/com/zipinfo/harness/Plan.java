package com.zipinfo.harness;

import com.zipinfo.ai.data.ListingMeta;
import com.zipinfo.ai.reflex.Variant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 돌릴 차례(설계안 10.1, 01 341줄). 판 × 문항 × 회차를 판마다 [회차 → 문항 파일 차례]로 늘어놓고 block_size개씩 블록으로 자른 뒤,
 * 블록 번호마다 판 설정 파일의 판 차례로 번갈아 놓는다(c01의 1블록, c02의 1블록, …, c01의 2블록, …).
 * 그래서 모든 판이 같은 문항을 같은 때쯤 돈다. 같은 판을 몰아 돌리지 않는다.
 * <p>나눔 막기(설계안 557줄): 설정의 split과 같은 문항만 고른다. ID로 고른 문항의 나눔이 다르면 멈춘다.
 * 문항 파일의 모든 문항은 매물이 있고 나눔이 그 매물의 나눔과 같아야 한다(다르면 멈춘다).
 */
public final class Plan {
    /** 실행 하나: 실행 ID(c05-q117-r3 꼴, 01 229줄), 판, 문항, 회차, 그 판 안의 블록 번호(1부터). */
    public record RunSpec(String id, Variant variant, Question question, int rep, int block) {}

    private Plan() {}

    public static String runId(String variant, String question, int rep) {
        return variant + "-" + question + "-r" + rep;
    }

    public static List<RunSpec> of(RunSettings s, List<Variant> variants, List<Question> questions, Map<String, ListingMeta> metas) {
        for (Question q : questions) {
            ListingMeta m = metas.get(q.key());
            if (m == null) {
                throw new IllegalArgumentException("문항 " + q.id() + "의 매물이 없다: " + q.key());
            }
            if (!m.split().equals(q.split())) {
                throw new IllegalArgumentException("문항 " + q.id() + "의 나눔(" + q.split() + ")이 매물의 나눔(" + m.split() + ")과 다르다");
            }
        }
        List<Variant> vs = new ArrayList<>();
        for (Variant v : variants) {
            if (s.select().variants().isEmpty() || s.select().variants().contains(v.id())) {
                vs.add(v);
            }
        }
        for (String id : s.select().variants()) {
            if (variants.stream().noneMatch(v -> v.id().equals(id))) {
                throw new IllegalArgumentException("판 설정 파일에 없는 판이다: " + id);
            }
        }
        Map<String, Question> byId = new LinkedHashMap<>();
        questions.forEach(q -> byId.put(q.id(), q));
        for (String id : s.select().questions()) {
            Question q = byId.get(id);
            if (q == null) {
                throw new IllegalArgumentException("문항 파일에 없는 문항이다: " + id);
            }
            if (!q.split().equals(s.split())) {
                throw new IllegalArgumentException(q.split().equals(RunSettings.TEST)
                        ? "본시험 문항은 본실험 설정(split 본시험)에서만 돈다: " + id
                        : "이 설정은 본시험만 돈다. 연습 문항이다: " + id);
            }
        }
        List<Question> qs = questions.stream().filter(q -> q.split().equals(s.split()))
                .filter(q -> s.select().questions().isEmpty() || s.select().questions().contains(q.id())).toList();
        if (qs.isEmpty()) {
            throw new IllegalArgumentException("돌릴 문항이 없다(나눔 " + s.split() + ")");
        }
        List<List<List<RunSpec>>> blocks = new ArrayList<>();
        for (Variant v : vs) {
            List<RunSpec> runs = new ArrayList<>();
            for (int rep : s.select().reps()) {
                for (Question q : qs) {
                    runs.add(new RunSpec(runId(v.id(), q.id(), rep), v, q, rep, runs.size() / s.blockSize() + 1));
                }
            }
            List<List<RunSpec>> chunks = new ArrayList<>();
            for (int i = 0; i < runs.size(); i += s.blockSize()) {
                chunks.add(runs.subList(i, Math.min(runs.size(), i + s.blockSize())));
            }
            blocks.add(chunks);
        }
        List<RunSpec> out = new ArrayList<>();
        int most = blocks.stream().mapToInt(List::size).max().orElse(0);
        for (int b = 0; b < most; b++) {
            for (List<List<RunSpec>> chunks : blocks) {
                if (b < chunks.size()) {
                    out.addAll(chunks.get(b));
                }
            }
        }
        return List.copyOf(out);
    }
}
