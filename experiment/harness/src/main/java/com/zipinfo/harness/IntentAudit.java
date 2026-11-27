package com.zipinfo.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.zipinfo.ai.cards.Cards;
import com.zipinfo.ai.data.Listing;
import com.zipinfo.ai.reflex.IntentCheck;
import com.zipinfo.ai.reflex.IntentLists;
import com.zipinfo.ai.reflex.Part;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 순서 11b-1(설계 4판 4.6): ⓪ 감사와 ⓪ 목록 v1 뽑기(intent-audit 명령). 요청을 보내지 않고, ai-core는 바꾸지 않는다(공개된 IntentCheck.check로 센다).
 * <ul>
 * <li>읽는 것: 설정의 문항·카드·⓪ 목록(v0)·기록 폴더, 문항 파일과 같은 폴더의 틀 문장 파일과 의도 절 표(관문 3까지 git 밖), 매물 입력, 법정동 이름.
 *     IntentClausesCheckTest와 같은 길로 문항마다 틀을 찾고, 표의 절이 ⓪이 나눈 절과 같은지 본다.</li>
 * <li>감사 1(기록): ⓪이 든 판의 declare_intents 부름을 설정의 목록으로 다시 세어, 토큰 상태(셈·질문에 없음·절을 넘음·카드 없음)마다 수를 낸다.
 *     줄 셋: 제출 없이 끝난 실행, 6틀(v0에서 의도 절만 덮어 통과하지 못하는 문항이 있는 틀) 실행, 나머지 틀 실행.</li>
 * <li>감사 2(틀): 틀마다 v0·v1의 의도 절만 덮은 %, 통과에 필요한 의도 글자 %, 빠뜨림 (가)·(나)의 거절 수.
 *     (가)는 나머지 절(의도 아님 절 포함)을 다 덮고 의도 절 하나를 뺀 선언, (나)는 의도 절만 덮고 그 가운데 하나를 뺀 선언이다.</li>
 * <li>뽑기: 의도 아님 절에서 매물 이름이 든 어절을 빼고 남은 이어진 어절을 묶어(토씨 그대로, 끝 문장부호는 뗌) 후보로 한다.
 *     ② 어느 문항의 의도 절에도 들지 않는 후보 가운데, 혼자 v0에 더해 어느 문항에서든 센 글자를 덜어내는 것을 모두 더한다(다 덜어냄).</li>
 * <li>받는 검사(문항 모두): ① 의도 절만 덮으면 모든 문항이 통과, ② 더한 군말이 어느 의도 절에도 들지 않음, ③ v0이 (가)에서 거절하던 빠뜨림을 v1도 거절.
 *     하나라도 못 넘으면 쓰지 않고 못 넘은 틀과 수만 낸다. 덧보기: v0에서 ①이 서던 문항의 (나)를 v0과 견준다.</li>
 * <li>쓰기: v0 목록에 군말을 더하고 version을 "v1"로 한 파일(v0 파일과 같은 꼴, 줄 끝 LF). 이미 있으면 쓰지 않는다(MemoryFile과 같다).</li>
 * </ul>
 * 화면에는 틀 번호·개수·%·해시만 낸다. 질문·절·군말의 글은 내지 않는다.
 */
public final class IntentAudit {
    static final List<String> TYPES = List.of("T1", "T2", "T3", "T4", "T5");

    /** 문항 하나: 틀 번호(1부터), 매물, 표의 의도 여부(절 차례). */
    public record Q(String id, String type, int template, String text, Listing listing, List<Boolean> intent) {
        String key() {
            return type + "-" + template;
        }
    }

    /** 감사 2의 틀 한 줄(목록 하나). */
    record Row(int questions, int[] cover, int[] need, int[] a, int[] b) {}

    /** 뽑기 결과: 후보 수, ②를 지난 수, 효과 있는 후보(뽑은 틀과 함께, 뽑은 차례). */
    record Extraction(int candidates, int passed, Map<String, String> effective) {}

    private IntentAudit() {}

    // ---------------------------------------------------------------- 읽기

    /** 문항마다 틀을 찾고 표의 절이 ⓪이 나눈 절과 같은지 본다(IntentClausesCheckTest와 같은 길). */
    static List<Q> questions(List<Question> qs, Map<String, Listing> listings, JsonNode templates, JsonNode table, IntentCheck check)
            throws IOException {
        List<Q> out = new ArrayList<>();
        for (Question q : qs) {
            Listing l = listings.get(q.key());
            if (l == null) {
                throw new IOException("문항 " + q.id() + "의 매물이 매물 입력에 없다");
            }
            String name = l.fields().getOrDefault("stockName", "");
            JsonNode ts = templates.path(q.qtype());
            int found = -1;
            for (int i = 0; i < ts.size(); i++) {
                if (ts.get(i).asText().replace("{이름}", name).equals(q.text())) {
                    if (found >= 0) {
                        throw new IOException("문항 " + q.id() + "에 맞는 틀이 둘이다");
                    }
                    found = i;
                }
            }
            if (found < 0) {
                throw new IOException("문항 " + q.id() + "의 틀을 찾지 못했다");
            }
            JsonNode tpl = table.path(q.qtype()).path(found);
            List<String> want = new ArrayList<>();
            List<Boolean> intent = new ArrayList<>();
            for (JsonNode c : tpl) {
                want.add(IntentCheck.normalize(c.path("clause").asText().replace("{이름}", name)));
                intent.add(c.path("intent").asBoolean());
            }
            if (want.isEmpty() || !want.equals(check.check(q.text(), l, List.of()).clauses())) {
                throw new IOException("문항 " + q.id() + ": 의도 절 표의 절이 ⓪이 나눈 절과 다르다");
            }
            out.add(new Q(q.id(), q.qtype(), found + 1, q.text(), l, List.copyOf(intent)));
        }
        return out;
    }

    // ---------------------------------------------------------------- 세기

    /** 절 차례 idx의 절을 덮는 토큰(영어의도는 카드가 찾히는 태그 하나). */
    static List<IntentCheck.Token> tokens(List<String> clauses, List<Integer> idx, String english) {
        List<IntentCheck.Token> out = new ArrayList<>();
        for (int i : idx) {
            out.add(new IntentCheck.Token(clauses.get(i), "", "", english));
        }
        return out;
    }

    static List<Integer> intents(Q q) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < q.intent().size(); i++) {
            if (q.intent().get(i)) {
                out.add(i);
            }
        }
        return out;
    }

    /** a/b를 백분율로(정수, 0.5는 올림). */
    static int pct(long a, long b) {
        return (int) ((200 * a + b) / (2 * b));
    }

    /** 의도 절만 덮었을 때 통과하는가. */
    static boolean intentOnly(IntentCheck c, Q q, String english) {
        List<String> cl = c.check(q.text(), q.listing(), List.of()).clauses();
        return c.check(q.text(), q.listing(), tokens(cl, intents(q), english)).passed();
    }

    /** 빠뜨림 하나(문항, 뺀 의도 절)를 거절하는가. all이면 (가)(나머지 절 모두 덮음), 아니면 (나)(의도 절만 덮음). */
    static boolean caught(IntentCheck c, Q q, int j, boolean all, String english) {
        List<String> cl = c.check(q.text(), q.listing(), List.of()).clauses();
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < cl.size(); i++) {
            if (i != j && (all || q.intent().get(i))) {
                idx.add(i);
            }
        }
        return !c.check(q.text(), q.listing(), tokens(cl, idx, english)).passed();
    }

    /** 감사 2: 틀마다 한 줄(틀 차례). cover·need는 [최소, 최대] %, a·b는 [거절, 빠뜨림]. */
    static Map<String, Row> rows(List<Q> qs, IntentCheck c, String english) {
        Map<String, Row> out = new TreeMap<>();
        for (Q q : qs) {
            List<String> cl = c.check(q.text(), q.listing(), List.of()).clauses();
            IntentCheck.Result r = c.check(q.text(), q.listing(), tokens(cl, intents(q), english));
            int cover = pct(r.covered(), Math.max(1, r.total()));
            long need = (77L * r.total() + 99) / 100;
            int needPct = r.covered() == 0 ? 999 : pct(need, r.covered());
            Row row = out.computeIfAbsent(q.key(), k -> new Row(0, new int[] {999, -1}, new int[] {9999, -1}, new int[2], new int[2]));
            int[] a = row.a().clone();
            int[] b = row.b().clone();
            for (int j : intents(q)) {
                a[0] += caught(c, q, j, true, english) ? 1 : 0;
                a[1]++;
                b[0] += caught(c, q, j, false, english) ? 1 : 0;
                b[1]++;
            }
            out.put(q.key(), new Row(row.questions() + 1, new int[] {Math.min(row.cover()[0], cover), Math.max(row.cover()[1], cover)},
                    new int[] {Math.min(row.need()[0], needPct), Math.max(row.need()[1], needPct)}, a, b));
        }
        return out;
    }

    // ---------------------------------------------------------------- 뽑기

    /** 의도 아님 절의 어절 묶음: 매물 이름 자리({이름})가 든 어절에서 끊고, 묶음 끝의 글자·숫자가 아닌 것을 뗀다. 후보 → 뽑은 틀(처음 것). */
    static Map<String, String> candidates(JsonNode table) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String type : TYPES) {
            JsonNode ts = table.path(type);
            for (int t = 0; t < ts.size(); t++) {
                for (JsonNode c : ts.get(t)) {
                    if (c.path("intent").asBoolean()) {
                        continue;
                    }
                    List<String> run = new ArrayList<>();
                    List<String> words = new ArrayList<>(List.of(IntentCheck.normalize(c.path("clause").asText()).split("[ \\n]")));
                    words.add("{이름}");  // 끝 표시
                    for (String w : words) {
                        if (!w.contains("{이름}") && !w.isEmpty()) {
                            run.add(w);
                            continue;
                        }
                        String s = String.join(" ", run);
                        int e = s.length();
                        while (e > 0 && !Character.isLetterOrDigit(s.charAt(e - 1))) {
                            e--;
                        }
                        if (e > 0) {
                            out.putIfAbsent(s.substring(0, e), type + "-" + (t + 1));
                        }
                        run.clear();
                    }
                }
            }
        }
        return out;
    }

    /** 문항들의 의도 절 글(⓪이 실제 매물로 나눈 것). */
    static Set<String> intentTexts(List<Q> qs, IntentCheck c) {
        Set<String> out = new LinkedHashSet<>();
        for (Q q : qs) {
            List<String> cl = c.check(q.text(), q.listing(), List.of()).clauses();
            for (int i : intents(q)) {
                out.add(cl.get(i));
            }
        }
        return out;
    }

    static IntentLists withFillers(IntentLists base, String version, List<String> more) {
        List<String> f = new ArrayList<>(base.fillers());
        f.addAll(more);
        return new IntentLists(version, base.connectiveEndings(), base.connectiveWords(), base.particlesEndings(), List.copyOf(f),
                base.wordExceptions());
    }

    static Extraction extract(List<Q> qs, JsonNode table, IntentLists v0, Set<String> names, Cards cards) {
        IntentCheck c0 = new IntentCheck(v0, names, cards);
        Map<String, String> cand = candidates(table);
        Set<String> inIntent = intentTexts(qs, c0);
        Map<String, Integer> base = new LinkedHashMap<>();
        for (Q q : qs) {
            base.put(q.id(), c0.check(q.text(), q.listing(), List.of()).total());
        }
        int passed = 0;
        Map<String, String> effective = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : cand.entrySet()) {
            String s = e.getKey();
            if (inIntent.stream().anyMatch(x -> x.contains(s))) {
                continue;
            }
            passed++;
            if (v0.fillers().contains(s)) {
                continue;
            }
            IntentCheck c1 = new IntentCheck(withFillers(v0, "x", List.of(s)), names, cards);
            if (qs.stream().anyMatch(q -> c1.check(q.text(), q.listing(), List.of()).total() < base.get(q.id()))) {
                effective.put(s, e.getValue());
            }
        }
        return new Extraction(cand.size(), passed, effective);
    }

    // ---------------------------------------------------------------- 파일 꼴

    /** v0 파일과 같은 꼴: 두 칸 들여 쓰기, 목록은 한 줄에 하나, 줄 끝 LF. */
    static String format(IntentLists l) throws IOException {
        StringBuilder sb = new StringBuilder("{\n  \"version\": ").append(Json.M.writeValueAsString(l.version()));
        Map<String, List<String>> lists = new LinkedHashMap<>();
        lists.put("connective_endings", l.connectiveEndings());
        lists.put("connective_words", l.connectiveWords());
        lists.put("particles_endings", l.particlesEndings());
        lists.put("fillers", l.fillers());
        lists.put("word_exceptions", l.wordExceptions());
        for (Map.Entry<String, List<String>> e : lists.entrySet()) {
            sb.append(",\n  \"").append(e.getKey()).append("\": [\n");
            List<String> xs = new ArrayList<>();
            for (String x : e.getValue()) {
                xs.add("    " + Json.M.writeValueAsString(x));
            }
            sb.append(String.join(",\n", xs)).append("\n  ]");
        }
        return sb.append("\n}\n").toString();
    }

    // ---------------------------------------------------------------- 감사 1

    /** 감사 1의 한 줄: 실행, 부름, 읽지 못한 부름, 통과한 부름, 기록과 통과가 다른 부름, 토큰 상태 수. */
    static final class Tally {
        int runs;
        int calls;
        int bad;
        int passed;
        int differ;
        final Map<IntentCheck.Status, Integer> status = new EnumMap<>(IntentCheck.Status.class);

        String line(String name) {
            int tokens = status.values().stream().mapToInt(Integer::intValue).sum();
            return name + ": 실행 " + runs + "개, declare_intents " + calls + "번(통과 " + passed + ", 읽지 못함 " + bad + ", 기록과 통과가 다름 " + differ
                    + "), 토큰 " + tokens + "개: 셈 " + status.getOrDefault(IntentCheck.Status.COUNTED, 0)
                    + ", 질문에 없음 " + status.getOrDefault(IntentCheck.Status.NOT_IN_QUESTION, 0)
                    + ", 절을 넘음 " + status.getOrDefault(IntentCheck.Status.CROSSES_CLAUSES, 0)
                    + ", 카드 없음 " + status.getOrDefault(IntentCheck.Status.NO_CARD, 0);
        }
    }

    /** declare_intents 입력의 토큰(IntentTools와 같은 네 칸). 꼴이 아니면 null. */
    static List<IntentCheck.Token> declared(JsonNode in) {
        JsonNode list = in == null ? null : in.get("intents");
        if (list == null || !list.isArray()) {
            return null;
        }
        List<IntentCheck.Token> out = new ArrayList<>();
        for (JsonNode t : list) {
            List<String> v = new ArrayList<>();
            for (String f : List.of("source_text", "corrected_text", "intent_expansion", "english_intent")) {
                JsonNode x = t.get(f);
                if (x == null || !x.isTextual()) {
                    return null;
                }
                v.add(x.asText());
            }
            out.add(new IntentCheck.Token(v.get(0), v.get(1), v.get(2), v.get(3)));
        }
        return out;
    }

    /** ⓪이 든 판의 기록을 다시 센다. [제출 없이 끝난 실행, 6틀 실행, 나머지 틀 실행]. 기록이 없거나 덜 끝난 실행은 센 수에 넣지 않는다. */
    static Tally[] audit1(List<Plan.RunSpec> plan, Map<String, Q> byId, Set<String> six, IntentCheck c, Path dir) throws IOException {
        Tally[] t = {new Tally(), new Tally(), new Tally()};
        for (Plan.RunSpec r : plan) {
            if (!r.variant().parts().contains(Part.INTENT)) {
                continue;
            }
            RunFile rf = RunFile.read(dir.resolve(r.id() + ".jsonl"), false);
            if (!rf.complete()) {
                continue;
            }
            Q q = byId.get(r.question().id());
            List<Tally> to = new ArrayList<>();
            if (!"SUBMITTED".equals(rf.end().path("outcome").asText())) {
                to.add(t[0]);
            }
            to.add(six.contains(q.key()) ? t[1] : t[2]);
            to.forEach(x -> x.runs++);
            for (JsonNode e : rf.loopEvents()) {
                if (!"tool".equals(e.path("type").asText()) || !"declare_intents".equals(e.path("name").asText())) {
                    continue;
                }
                boolean recorded = !e.path("is_error").asBoolean();
                List<IntentCheck.Token> tokens = declared(e.get("input"));
                IntentCheck.Result res = tokens == null ? null : c.check(q.text(), q.listing(), tokens);
                for (Tally x : to) {
                    x.calls++;
                    if (res == null) {
                        x.bad++;
                        x.differ += recorded ? 1 : 0;
                        continue;
                    }
                    x.passed += res.passed() ? 1 : 0;
                    x.differ += res.passed() != recorded ? 1 : 0;
                    for (IntentCheck.TokenResult tr : res.tokens()) {
                        x.status.merge(tr.status(), 1, Integer::sum);
                    }
                }
            }
        }
        return t;
    }

    // ---------------------------------------------------------------- 명령

    /** 끝 코드: 0 썼다, 3 받는 검사에 걸려 쓰지 않았다. 설정·파일 문제(이미 있음 포함)는 IOException·IllegalArgumentException(Main이 2로 끝낸다). */
    static int run(Inputs in, RunSettings s, Path experiment, Path dir, String outRel, PrintStream out) throws IOException {
        Path target = experiment.resolve(outRel);
        if (Files.exists(target)) {
            throw new IOException("쓸 ⓪ 목록 파일이 이미 있다. 얼린 것은 덮어쓰지 않는다: " + outRel);
        }
        Path qfile = experiment.resolve(s.files().questions());
        String qv = Json.M.readTree(Files.readString(qfile, StandardCharsets.UTF_8)).path("version").asText();
        Path tfile = qfile.resolveSibling("templates_" + qv + ".json");
        Path cfile = qfile.resolveSibling("intent_clauses_" + qv + ".json");
        if (!Files.exists(tfile) || !Files.exists(cfile)) {
            throw new IOException("틀 문장 파일(templates_" + qv + ".json)이나 의도 절 표(intent_clauses_" + qv + ".json)가 문항 파일 옆에 없다(관문 3까지 git 밖)");
        }
        JsonNode templates = Json.M.readTree(Files.readString(tfile, StandardCharsets.UTF_8)).path("templates");
        JsonNode table = Json.M.readTree(Files.readString(cfile, StandardCharsets.UTF_8)).path("templates");
        Set<String> names = in.data().history().names();
        String english = in.cards().all().get(0).englishTags().get(0);
        IntentLists v0 = in.lists();
        IntentCheck c0 = new IntentCheck(v0, names, in.cards());
        List<Q> qs = questions(in.questions(), in.listings(), templates, table, c0);
        Map<String, Q> byId = new LinkedHashMap<>();
        qs.forEach(q -> byId.put(q.id(), q));
        out.println("⓪ 감사: 문항 " + qs.size() + "개, 틀 " + qs.stream().map(Q::key).distinct().count() + "개, 목록 " + v0.version() + "(군말 "
                + v0.fillers().size() + "개), 의도 절 표 " + Fingerprint.file(cfile).substring(0, 12) + ", 틀 문장 " + Fingerprint.file(tfile).substring(0, 12));

        Set<String> six = six(qs, c0, english);
        out.println("6틀(" + v0.version() + "에서 의도 절만 덮어 통과하지 못하는 문항이 있는 틀) " + six.size() + "개: " + String.join(" ", six));

        // 감사 1
        Tally[] t = audit1(Plan.of(s, in.variants(), in.questions(), in.metas()), byId, six, c0, dir);
        out.println("감사 1(단계 " + s.stage() + ", ⓪이 든 판의 끝난 실행, 목록 " + v0.version() + "으로 다시 셈)");
        out.println("  " + t[0].line("제출 없이 끝난 실행"));
        out.println("  " + t[1].line("6틀 실행"));
        out.println("  " + t[2].line("나머지 틀 실행"));

        IntentLists v1 = decide(qs, table, v0, names, in.cards(), english, out);
        if (v1 == null) {
            return 3;
        }
        write(target, v1, outRel);
        out.println(outRel + "에 썼다(version v1, 군말 " + v1.fillers().size() + "개). SHA-256(줄 끝 LF) " + Fingerprint.file(target));
        return 0;
    }

    /** 6틀: 이 목록에서 의도 절만 덮어 통과하지 못하는 문항이 있는 틀. */
    static Set<String> six(List<Q> qs, IntentCheck c, String english) {
        Set<String> out = new TreeSet<>();
        for (Q q : qs) {
            if (!intentOnly(c, q, english)) {
                out.add(q.key());
            }
        }
        return out;
    }

    /** 뽑기, 감사 2, 받는 검사. 다 지나면 v1 목록, 아니면 null(쓰지 않는다). */
    static IntentLists decide(List<Q> qs, JsonNode table, IntentLists v0, Set<String> names, Cards cards, String english, PrintStream out) {
        IntentCheck c0 = new IntentCheck(v0, names, cards);
        Extraction x = extract(qs, table, v0, names, cards);
        IntentLists v1 = withFillers(v0, "v1", List.copyOf(x.effective().keySet()));
        IntentCheck c1 = new IntentCheck(v1, names, cards);
        Map<String, Integer> from = new TreeMap<>();
        x.effective().values().forEach(k -> from.merge(k, 1, Integer::sum));
        out.println("뽑기: 후보 " + x.candidates() + "개, ② 지남 " + x.passed() + "개, 그 가운데 센 글자를 덜어내는 것 " + x.effective().size() + "개("
                + from.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", ")) + "), 군말 "
                + v0.fillers().size() + " → " + v1.fillers().size());

        // 감사 2
        Map<String, Row> r0 = rows(qs, c0, english);
        Map<String, Row> r1 = rows(qs, c1, english);
        out.println("감사 2(틀마다, " + v0.version() + "·v1)");
        for (String k : r0.keySet()) {
            Row a = r0.get(k);
            Row b = r1.get(k);
            out.println("  " + k + " 문항 " + a.questions() + ": 의도만 덮음 " + range(a.cover()) + "·" + range(b.cover()) + ", 필요한 의도 글자 "
                    + range(a.need()) + "·" + range(b.need()) + ", (가) 거절 " + a.a()[0] + "/" + a.a()[1] + "·" + b.a()[0] + "/" + b.a()[1]
                    + ", (나) 거절 " + a.b()[0] + "/" + a.b()[1] + "·" + b.b()[0] + "/" + b.b()[1]);
        }
        for (Object[] row : new Object[][] {{v0.version(), r0, c0}, {"v1", r1, c1}}) {
            @SuppressWarnings("unchecked")
            Map<String, Row> rs = (Map<String, Row>) row[1];
            IntentCheck c = (IntentCheck) row[2];
            long pass = qs.stream().filter(q -> intentOnly(c, q, english)).count();
            out.println("  " + row[0] + ": ① 의도 절만 덮어 통과 " + pass + "/" + qs.size() + ", (가) 거절 " + sum(rs, 0, true) + "/" + sum(rs, 1, true)
                    + ", (나) 거절 " + sum(rs, 0, false) + "/" + sum(rs, 1, false));
        }
        int n162 = 0;
        int m = 0;
        int miss0 = 0;
        int miss1 = 0;
        for (Q q : qs) {
            if (!intentOnly(c0, q, english)) {
                continue;
            }
            n162++;
            for (int j : intents(q)) {
                m++;
                miss0 += caught(c0, q, j, false, english) ? 0 : 1;
                miss1 += caught(c1, q, j, false, english) ? 0 : 1;
            }
        }
        out.println("  덧보기: " + v0.version() + "에서 ①이 서던 문항 " + n162 + "개의 빠뜨림 " + m + "개 가운데 (나) 못 잡음 " + v0.version() + " " + miss0
                + "·v1 " + miss1);

        // 받는 검사
        Map<String, Integer> fail1 = new TreeMap<>();
        for (Q q : qs) {
            if (!intentOnly(c1, q, english)) {
                fail1.merge(q.key(), 1, Integer::sum);
            }
        }
        Set<String> inIntent = intentTexts(qs, c0);
        Map<String, Integer> fail2 = new TreeMap<>();
        x.effective().forEach((f, k) -> {
            if (inIntent.stream().anyMatch(y -> y.contains(f))) {
                fail2.merge(k, 1, Integer::sum);
            }
        });
        Map<String, Integer> fail3 = new TreeMap<>();
        int caught0 = 0;
        for (Q q : qs) {
            for (int j : intents(q)) {
                if (caught(c0, q, j, true, english)) {
                    caught0++;
                    if (!caught(c1, q, j, true, english)) {
                        fail3.merge(q.key() + "#" + (j + 1), 1, Integer::sum);
                    }
                }
            }
        }
        out.println("받는 검사: ① v1에서 의도 절만 덮어 통과 " + (qs.size() - fail1.values().stream().mapToInt(Integer::intValue).sum()) + "/" + qs.size()
                + ", ② 더한 군말 " + x.effective().size() + "개 가운데 의도 절에 든 것 " + fail2.values().stream().mapToInt(Integer::intValue).sum()
                + ", ③ " + v0.version() + "이 (가)에서 거절한 " + caught0 + "개 가운데 v1이 통과시킨 것 " + fail3.values().stream().mapToInt(Integer::intValue).sum());
        if (!fail1.isEmpty() || !fail2.isEmpty() || !fail3.isEmpty()) {
            out.println("멈춤(받는 검사): 쓰지 않았다. ① 못 지난 틀 " + fail1 + ", ② 군말을 뽑은 틀 " + fail2 + ", ③ 틀#절 " + fail3);
            return null;
        }
        return v1;
    }

    /** v1 목록을 쓴다. 이미 있으면 쓰지 않는다(CREATE_NEW). 쓴 것을 다시 읽어 같은지 본다. */
    static void write(Path target, IntentLists v1, String outRel) throws IOException {
        String text = format(v1);
        IntentLists.parse(text);
        if (target.toAbsolutePath().getParent() != null) {
            Files.createDirectories(target.toAbsolutePath().getParent());
        }
        try {
            Files.writeString(target, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            throw new IOException("쓸 ⓪ 목록 파일이 이미 있다. 얼린 것은 덮어쓰지 않는다: " + outRel, e);
        }
        if (!IntentLists.load(target).equals(v1)) {
            throw new IOException("쓴 ⓪ 목록을 다시 읽은 것이 쓴 것과 다르다: " + outRel);
        }
    }

    static String range(int[] r) {
        return r[0] == r[1] ? r[0] + "%" : r[0] + "~" + r[1] + "%";
    }

    static int sum(Map<String, Row> rs, int i, boolean a) {
        return rs.values().stream().mapToInt(r -> (a ? r.a() : r.b())[i]).sum();
    }
}
