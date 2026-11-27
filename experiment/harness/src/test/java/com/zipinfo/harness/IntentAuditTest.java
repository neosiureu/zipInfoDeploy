package com.zipinfo.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.zipinfo.ai.cards.Card;
import com.zipinfo.ai.cards.Cards;
import com.zipinfo.ai.data.History;
import com.zipinfo.ai.data.Listing;
import com.zipinfo.ai.reflex.IntentCheck;
import com.zipinfo.ai.reflex.IntentLists;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 순서 11b-1(설계 4판 4.6): ⓪ 감사와 목록 v1 뽑기(IntentAudit).
 * <ul>
 * <li>지어낸 틀·목록·카드로 한 곳씩 본다: 후보(이름 어절에서 끊고 끝 문장부호를 뗌), ②(의도 절에 든 후보를 뺌), 효과(이미 군말인 것은 뺌),
 *     받는 검사 ①(못 넘으면 쓰지 않음), (가)·(나)의 뜻, 덮어쓰지 않기, 파일 꼴.</li>
 * <li>목록 파일 꼴은 v0 파일을 글자까지 다시 짓는다.</li>
 * <li>git 밖 파일(틀 문장·의도 절 표·문항·매물 입력)이 있으면 실제로 뽑아 설계 4판 2.4의 수와 v1 파일의 지문을 댄다.</li>
 * </ul>
 */
class IntentAuditTest {
    static final String NAME = "가나다라";
    static final IntentLists LISTS = new IntentLists("t0", List.of("고"), List.of("그리고"), List.of("는", "에", "을", "가"), List.of("안녕하세요"),
            List.of("단지"));
    static final Cards CARDS = new Cards(List.of(new Card("K01", "시세", List.of("price"), List.of("시세"), "핵심", "사용법", "기록")));

    static Listing listing() {
        Map<String, String> f = new LinkedHashMap<>();
        for (String k : Listing.NINE) {
            f.put(k, "0");
        }
        f.put("stockName", NAME);
        f.put("stockAddress", "00000^^^가나시 가나구 가나로 1^^^");
        return new Listing("L01", f, Map.of());
    }

    static JsonNode json(String s) throws IOException {
        return Json.M.readTree(s);
    }

    /** 지어낸 틀과 표로 문항을 짓는다(IntentAudit.questions가 표의 절과 ⓪이 나눈 절을 댄다). */
    static List<IntentAudit.Q> build(String templates, String table) throws IOException {
        JsonNode t = json(templates);
        List<Question> qs = new java.util.ArrayList<>();
        int n = 0;
        for (String type : List.of("T1", "T2")) {
            for (JsonNode x : t.path(type)) {
                n++;
                qs.add(new Question("q" + n, "L01", type, "연습", x.asText().replace("{이름}", NAME)));
            }
        }
        return IntentAudit.questions(qs, Map.of("L01", listing()), t, json(table), new IntentCheck(LISTS, Set.of(), CARDS));
    }

    static String run(List<IntentAudit.Q> qs, String table, IntentLists[] got) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        got[0] = IntentAudit.decide(qs, json(table), LISTS, Set.of(), CARDS, "price", new PrintStream(b, true, StandardCharsets.UTF_8));
        return b.toString(StandardCharsets.UTF_8);
    }

    static final String T_FAIL = "{\"T1\": [\"{이름} 시세 알려줘. 참고로 저는 {이름} 근처에 살아요.\", \"{이름} 근처에 살아요 시세 알려줘.\", "
            + "\"안녕하세요. {이름} 시세 알려줘.\"]}";
    static final String C_FAIL = "{\"T1\": [[{\"clause\": \"{이름} 시세 알려줘.\", \"intent\": true}, {\"clause\": \"참고로 저는 {이름} 근처에 살아요.\", \"intent\": false}], "
            + "[{\"clause\": \"{이름} 근처에 살아요 시세 알려줘.\", \"intent\": true}], "
            + "[{\"clause\": \"안녕하세요.\", \"intent\": false}, {\"clause\": \"{이름} 시세 알려줘.\", \"intent\": true}]]}";

    @Test
    void candidatesSecondCheckAndEffect() throws Exception {
        List<IntentAudit.Q> qs = build(T_FAIL, C_FAIL);
        assertEquals(List.of("T1-1", "T1-2", "T1-3"), qs.stream().map(IntentAudit.Q::key).toList());
        // 이름 어절에서 끊고 끝 문장부호를 뗀다. 처음 나온 틀을 적는다
        assertEquals(Map.of("참고로 저는", "T1-1", "근처에 살아요", "T1-1", "안녕하세요", "T1-3"), IntentAudit.candidates(json(C_FAIL)));
        IntentAudit.Extraction x = IntentAudit.extract(qs, json(C_FAIL), LISTS, Set.of(), CARDS);
        // ②: '근처에 살아요'는 T1-2의 의도 절에 든다. '안녕하세요'는 이미 군말이라 효과가 없다
        assertEquals(3, x.candidates());
        assertEquals(2, x.passed());
        assertEquals(Map.of("참고로 저는", "T1-1"), x.effective());
    }

    @Test
    void aFailedAcceptanceCheckWritesNothing() throws Exception {
        // T1-1은 '근처에 살아요'가 남아 v1에서도 의도 절만으로 통과하지 못한다(받는 검사 ①)
        IntentLists[] got = new IntentLists[1];
        String out = run(build(T_FAIL, C_FAIL), C_FAIL, got);
        assertNull(got[0], out);
        assertTrue(out.contains("받는 검사: ① v1에서 의도 절만 덮어 통과 2/3, ② 더한 군말 1개 가운데 의도 절에 든 것 0, ③ "), out);
        assertTrue(out.contains("멈춤(받는 검사): 쓰지 않았다. ① 못 지난 틀 {T1-1=1}, ② 군말을 뽑은 틀 {}, ③ 틀#절 {}"), out);
        assertFalse(out.contains("가나다라") || out.contains("참고로") || out.contains("시세"), "글은 내지 않는다");
    }

    static final String T_OK = "{\"T1\": [\"안녕 친구. {이름} 시세 알려줘.\", \"{이름} 시세 알려줘. {이름} 전세는 얼마야?\"]}";
    static final String C_OK = "{\"T1\": [[{\"clause\": \"안녕 친구.\", \"intent\": false}, {\"clause\": \"{이름} 시세 알려줘.\", \"intent\": true}], "
            + "[{\"clause\": \"{이름} 시세 알려줘.\", \"intent\": true}, {\"clause\": \"{이름} 전세는 얼마야?\", \"intent\": true}]]}";

    @Test
    void allChecksPassAndTheFileIsWrittenOnce(@TempDir Path tmp) throws Exception {
        List<IntentAudit.Q> qs = build(T_OK, C_OK);
        IntentLists[] got = new IntentLists[1];
        String out = run(qs, C_OK, got);
        assertNotNull(got[0], out);
        assertEquals(List.of("안녕하세요", "안녕 친구"), got[0].fillers());
        assertEquals("v1", got[0].version());
        assertTrue(out.contains("뽑기: 후보 1개, ② 지남 1개, 그 가운데 센 글자를 덜어내는 것 1개(T1-1 1), 군말 1 → 2"), out);
        // T1-1: v0에서 의도 절만 덮으면 5/9(56%), v1은 5/5. 통과에 필요한 의도 글자는 ⌈0.77×9⌉=7 → 7/5(140%), v1 ⌈0.77×5⌉=4 → 80%
        assertTrue(out.contains("  T1-1 문항 1: 의도만 덮음 56%·100%, 필요한 의도 글자 140%·80%, (가) 거절 1/1·1/1, (나) 거절 1/1·1/1"), out);
        // T1-2: 의도 절 둘. 하나를 빼면 (가)·(나) 모두 거절한다
        assertTrue(out.contains("  T1-2 문항 1: 의도만 덮음 100%·100%"), out);
        assertTrue(out.contains("  t0: ① 의도 절만 덮어 통과 1/2, (가) 거절 3/3, (나) 거절 3/3"), out);
        assertTrue(out.contains("  v1: ① 의도 절만 덮어 통과 2/2, (가) 거절 3/3, (나) 거절 3/3"), out);
        assertTrue(out.contains("받는 검사: ① v1에서 의도 절만 덮어 통과 2/2, ② 더한 군말 1개 가운데 의도 절에 든 것 0, ③ t0이 (가)에서 거절한 3개 가운데 v1이 통과시킨 것 0"),
                out);
        Path f = tmp.resolve("sub/lists_v1.json");
        IntentAudit.write(f, got[0], "sub/lists_v1.json");
        assertEquals(got[0], IntentLists.load(f));
        String text = Files.readString(f, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("{\n  \"version\": \"v1\",\n  \"connective_endings\": [\n    \"고\"\n  ],\n") && text.endsWith("\n  ]\n}\n"), text);
        assertFalse(text.contains("\r"));
        IOException e = assertThrows(IOException.class, () -> IntentAudit.write(f, got[0], "sub/lists_v1.json"));
        assertTrue(e.getMessage().contains("이미 있다"), e.getMessage());
        assertEquals(text, Files.readString(f, StandardCharsets.UTF_8));
    }

    @Test
    void omissionsOfBothKinds() throws Exception {
        // (가)는 나머지 절을 다 덮고 의도 절 하나를 뺀다, (나)는 의도 절만 덮고 하나를 뺀다. 짧은 의도 절은 (나)에서도 77%를 넘어 못 잡는다
        String t = "{\"T1\": [\"{이름} 매매 시세와 전세 시세를 비교해 줘. {이름} 층?\"]}";
        String c = "{\"T1\": [[{\"clause\": \"{이름} 매매 시세와 전세 시세를 비교해 줘.\", \"intent\": true}, {\"clause\": \"{이름} 층?\", \"intent\": true}]]}";
        IntentAudit.Q q = build(t, c).get(0);
        IntentCheck check = new IntentCheck(LISTS, Set.of(), CARDS);
        assertTrue(IntentAudit.caught(check, q, 0, true, "price"));
        assertTrue(IntentAudit.caught(check, q, 0, false, "price"));
        assertFalse(IntentAudit.caught(check, q, 1, true, "price"), "한 글자 절은 빠져도 통과한다");
        assertFalse(IntentAudit.caught(check, q, 1, false, "price"));
    }

    @Test
    void theFileFormIsTheV0Form() throws Exception {
        Path v0 = Fixture.EXP.resolve("intents/intent_lists_v0.json");
        String want = new String(Fingerprint.lf(Files.readAllBytes(v0)), StandardCharsets.UTF_8);
        assertEquals(want, IntentAudit.format(IntentLists.load(v0)), "v0 파일을 글자까지 다시 짓는다(줄 끝 LF)");
    }

    @Test
    void declaredTokensAreReadLikeTheTool() throws Exception {
        assertEquals(List.of(new IntentCheck.Token("가", "나", "다", "라")), IntentAudit.declared(json(
                "{\"intents\": [{\"source_text\": \"가\", \"corrected_text\": \"나\", \"intent_expansion\": \"다\", \"english_intent\": \"라\"}]}")));
        assertNull(IntentAudit.declared(json("{\"intents\": [{\"source_text\": \"가\"}]}")));
        assertNull(IntentAudit.declared(json("{}")));
        assertNull(IntentAudit.declared(null));
    }

    @Test
    void theRealFilesGiveTheDesignNumbers(@TempDir Path tmp) throws Exception {
        Path exp = Fixture.EXP;
        Path cfile = exp.resolve("questions/intent_clauses_v0.json");
        Path tfile = exp.resolve("questions/templates_v0.json");
        Path qfile = exp.resolve("questions/questions_v0.json");
        Path lfile = exp.resolve("prep/out/listings_input_v1.json");
        assumeTrue(Files.exists(cfile) && Files.exists(tfile) && Files.exists(qfile) && Files.exists(lfile),
                "의도 절 표·틀 문장·문항·매물 입력 파일이 있을 때만 돈다(관문 3까지 git 밖)");
        JsonNode table = Json.M.readTree(Files.readString(cfile, StandardCharsets.UTF_8)).path("templates");
        JsonNode templates = Json.M.readTree(Files.readString(tfile, StandardCharsets.UTF_8)).path("templates");
        IntentLists v0 = IntentLists.load(exp.resolve("intents/intent_lists_v0.json"));
        Set<String> names = History.load(exp.resolve("frozen/codego/codego_history_v1.csv")).names();
        Cards cards = Cards.load(exp.resolve("cards/v0"));
        Map<String, Listing> listings = new LinkedHashMap<>();
        for (Listing l : Listing.load(lfile)) {
            listings.put(l.key(), l);
        }
        String english = cards.all().get(0).englishTags().get(0);
        List<IntentAudit.Q> qs = IntentAudit.questions(Question.load(qfile), listings, templates, table, new IntentCheck(v0, names, cards));
        assertEquals(Set.of("T1-1", "T1-4", "T2-3", "T3-1", "T4-4", "T5-2"), IntentAudit.six(qs, new IntentCheck(v0, names, cards), english));
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        IntentLists v1 = IntentAudit.decide(qs, table, v0, names, cards, english, new PrintStream(b, true, StandardCharsets.UTF_8));
        String out = b.toString(StandardCharsets.UTF_8);
        assertNotNull(v1, out);
        // println은 그 PC의 줄 끝을 쓴다(윈도에서는 CRLF). 줄로 나눠 댄다
        List<String> got = out.lines().toList();
        for (String line : List.of("뽑기: 후보 19개, ② 지남 18개, 그 가운데 센 글자를 덜어내는 것 13개(T1-1 2, T1-4 5, T2-3 2, T3-1 1, T4-4 1, T5-2 2), 군말 104 → 117",
                "  v0: ① 의도 절만 덮어 통과 162/225, (가) 거절 308/357, (나) 거절 317/357",
                "  v1: ① 의도 절만 덮어 통과 225/225, (가) 거절 308/357, (나) 거절 308/357",
                "  덧보기: v0에서 ①이 서던 문항 162개의 빠뜨림 268개 가운데 (나) 못 잡음 v0 40·v1 40",
                "받는 검사: ① v1에서 의도 절만 덮어 통과 225/225, ② 더한 군말 13개 가운데 의도 절에 든 것 0, ③ v0이 (가)에서 거절한 308개 가운데 v1이 통과시킨 것 0")) {
            assertTrue(got.contains(line), line + "\n--- 나온 줄 ---\n" + out);
        }
        Path f = tmp.resolve("intent_lists_v1.json");
        IntentAudit.write(f, v1, "intent_lists_v1.json");
        System.out.println("⓪ 감사 검사: 6틀 " + IntentAudit.six(qs, new IntentCheck(v0, names, cards), english).size() + "개, 군말 " + v0.fillers().size()
                + " → " + v1.fillers().size() + ", v1 목록 지문 " + Fingerprint.file(f).substring(0, 12));
    }
}
