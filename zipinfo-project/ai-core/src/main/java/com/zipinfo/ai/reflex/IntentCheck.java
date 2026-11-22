package com.zipinfo.ai.reflex;

import com.zipinfo.ai.cards.Card;
import com.zipinfo.ai.cards.CardTools;
import com.zipinfo.ai.cards.Cards;
import com.zipinfo.ai.data.AddressBook;
import com.zipinfo.ai.data.Listing;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * ⓪이 의도토큰을 세는 법(설계안 7.2의 세는 법 1~5). 판정만 하고 실행 상태를 바꾸지 않는다.
 * 1. 원어는 질문의 연속된 조각 그대로다(NFC·빈칸 정리 뒤 글자로 대조). 보정원어는 따로 둔다.
 * 2. 원어는 절 하나 안에 있어야 한다. 절은 문장 끝(. ? ! 줄바꿈), 어절 끝의 이음 말 뒤, 이음 낱말 앞에서 나눈다.
 *    낱말 예외와 법정동 이름에서는 나누지 않는다. 질문 안의 매물 이름·주소 글은 통째로 두고 그 안에서 나누지 않는다.
 * 3. 카드를 찾은 토큰의 원어만 센다. 찾기는 영어의도의 낱말이 카드 영어 태그와 같거나, 보정원어나 의도보강에 카드의 한글 낱말이 있는 것이다(Cards.find).
 * 4. 분모는 질문의 글자에서 빈칸·문장부호·조사·어미·군말·이음 낱말과 매물 칸 글(이름·주소)을 뺀 것이다. 분자는 센 원어들이 덮는 그 글자다(겹치면 한 번).
 * 5. 덮은 비율이 77% 이상이면 통과다. 처방에 쓰라고 덮이지 않은 글자가 남은 절을 돌려준다.
 */
public final class IntentCheck {
    public static final int PASS_PERCENT = 77;
    private static final Pattern WS = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern LINES = Pattern.compile("\\R");

    /** 의도토큰 하나(15.3의 네 칸): 원어, 보정원어, 의도보강, 영어의도. */
    public record Token(String source, String corrected, String expansion, String english) {}

    public enum Status { COUNTED, NOT_IN_QUESTION, CROSSES_CLAUSES, NO_CARD }

    public record TokenResult(Token token, Status status, List<Card> cards) {}

    /** covered/total이 분자/분모다. clauses는 나눈 절, uncovered는 덮이지 않은 글자가 남은 절(차례대로). */
    public record Result(boolean passed, int covered, int total, List<TokenResult> tokens, List<String> clauses, List<String> uncovered) {}

    private final IntentLists lists;
    private final Set<String> names;
    private final Cards cards;
    private final Comparator<Card> order;
    private final List<String> suffixes;
    private final List<String> fillers;

    /** names: 법정동 이름(History.names). 토큰마다 찾은 카드는 ID 순이다. */
    public IntentCheck(IntentLists lists, Set<String> names, Cards cards) {
        this(lists, names, cards, CardTools.ID_ORDER);
    }

    /** 찾은 카드를 order 차례로 낸다. ⑤가 켜진 판은 좋아요 순이다(설계안 7.6, 순서 5c). */
    public IntentCheck(IntentLists lists, Set<String> names, Cards cards, Comparator<Card> order) {
        this.lists = lists;
        this.names = names;
        this.cards = cards;
        this.order = order;
        this.suffixes = lists.particlesEndings().stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
        this.fillers = lists.fillers().stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
    }

    /** 질문 정리: NFC, 줄마다 겹 빈칸을 하나로 하고 앞뒤를 뗀다. 빈 줄은 빼고 줄바꿈은 "\n" 하나로 남긴다(문장 끝이다). */
    public static String normalize(String text) {
        List<String> out = new ArrayList<>();
        for (String line : LINES.split(Normalizer.normalize(text, Normalizer.Form.NFC))) {
            String l = WS.matcher(line).replaceAll(" ").strip();
            if (!l.isEmpty()) {
                out.add(l);
            }
        }
        return String.join("\n", out);
    }

    /** 원어·매물 칸 정리: NFC, 모든 빈칸을 하나로, 앞뒤를 뗀다. */
    static String flat(String s) {
        return WS.matcher(Normalizer.normalize(s, Normalizer.Form.NFC)).replaceAll(" ").strip();
    }

    public Result check(String question, Listing listing, List<Token> tokens) {
        String q = normalize(question);
        int n = q.length();
        boolean[] prot = protect(q, listing);
        List<int[]> words = words(q);
        List<int[]> clauses = clauses(q, prot, words);
        boolean[] count = countable(q, prot, words);
        boolean[] cov = new boolean[n];
        List<TokenResult> results = new ArrayList<>();
        for (Token t : tokens) {
            String src = flat(t.source());
            boolean any = false;
            List<int[]> inside = new ArrayList<>();
            for (int i = src.isEmpty() ? -1 : q.indexOf(src); i >= 0; i = q.indexOf(src, i + 1)) {
                any = true;
                int s = i;
                int e = i + src.length();
                if (clauses.stream().anyMatch(c -> c[0] <= s && e <= c[1])) {
                    inside.add(new int[] {s, e});
                }
            }
            List<Card> found = cardsFor(t);
            Status st = !any ? Status.NOT_IN_QUESTION : inside.isEmpty() ? Status.CROSSES_CLAUSES : found.isEmpty() ? Status.NO_CARD : Status.COUNTED;
            if (st == Status.COUNTED) {
                for (int[] o : inside) {
                    Arrays.fill(cov, o[0], o[1], true);
                }
            }
            results.add(new TokenResult(t, st, found));
        }
        int total = 0;
        int covered = 0;
        for (int k = 0; k < n; k++) {
            if (count[k]) {
                total++;
                covered += cov[k] ? 1 : 0;
            }
        }
        List<String> texts = new ArrayList<>();
        List<String> uncovered = new ArrayList<>();
        for (int[] c : clauses) {
            String text = q.substring(c[0], c[1]);
            texts.add(text);
            for (int k = c[0]; k < c[1]; k++) {
                if (count[k] && !cov[k]) {
                    uncovered.add(text);
                    break;
                }
            }
        }
        return new Result(covered * 100 >= total * PASS_PERCENT, covered, total, List.copyOf(results), List.copyOf(texts), List.copyOf(uncovered));
    }

    /** 질문 안의 매물 이름과 도로명주소 글자. 그 안에서는 나누지 않고, 분모에도 넣지 않는다. */
    private static boolean[] protect(String q, Listing l) {
        boolean[] p = new boolean[q.length()];
        String name = flat(l.fields().getOrDefault("stockName", ""));
        String road = flat(AddressBook.clean(l.fields().getOrDefault("stockAddress", "")));
        for (String s : List.of(name, road)) {
            for (int i = s.isEmpty() ? -1 : q.indexOf(s); i >= 0; i = q.indexOf(s, i + 1)) {
                Arrays.fill(p, i, i + s.length(), true);
            }
        }
        return p;
    }

    /** 어절: 빈칸·줄바꿈 사이의 글자 묶음 [시작, 끝). */
    private static List<int[]> words(String q) {
        List<int[]> out = new ArrayList<>();
        int i = 0;
        while (i < q.length()) {
            if (q.charAt(i) == ' ' || q.charAt(i) == '\n') {
                i++;
                continue;
            }
            int s = i;
            while (i < q.length() && q.charAt(i) != ' ' && q.charAt(i) != '\n') {
                i++;
            }
            out.add(new int[] {s, i});
        }
        return out;
    }

    /** 어절에서 앞뒤 문장부호를 뗀 몸 [시작, 끝). 글자나 숫자가 없으면 시작 == 끝. */
    private static int[] core(String q, int[] w) {
        int s = w[0];
        int e = w[1];
        while (s < e && !Character.isLetterOrDigit(q.charAt(s))) {
            s++;
        }
        while (e > s && !Character.isLetterOrDigit(q.charAt(e - 1))) {
            e--;
        }
        return new int[] {s, e};
    }

    private static boolean open(boolean[] prot, int from, int to) {
        for (int k = from; k < to; k++) {
            if (prot[k]) {
                return false;
            }
        }
        return true;
    }

    private List<int[]> clauses(String q, boolean[] prot, List<int[]> words) {
        TreeSet<Integer> cuts = new TreeSet<>();
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            if (c == '\n') {
                cuts.add(i);
            } else if ((c == '.' || c == '?' || c == '!') && !prot[i]) {
                cuts.add(i + 1);
            }
        }
        for (int[] w : words) {
            int[] b = core(q, w);
            if (b[0] == b[1]) {
                continue;
            }
            String s = q.substring(b[0], b[1]);
            if (lists.connectiveWords().contains(s) && open(prot, b[0], b[1])) {
                cuts.add(w[0]);
                continue;
            }
            if (lists.wordExceptions().contains(s) || names.contains(s)) {
                continue;
            }
            for (String end : lists.connectiveEndings()) {
                if (s.length() > end.length() && s.endsWith(end) && open(prot, b[1] - end.length(), b[1])) {
                    cuts.add(w[1]);
                    break;
                }
            }
        }
        cuts.add(q.length());
        List<int[]> out = new ArrayList<>();
        int a = 0;
        for (int cut : cuts) {
            int s = a;
            int e = cut;
            while (s < e && (q.charAt(s) == ' ' || q.charAt(s) == '\n')) {
                s++;
            }
            while (e > s && (q.charAt(e - 1) == ' ' || q.charAt(e - 1) == '\n')) {
                e--;
            }
            if (s < e) {
                out.add(new int[] {s, e});
            }
            a = cut;
        }
        return out;
    }

    private boolean[] countable(String q, boolean[] prot, List<int[]> words) {
        int n = q.length();
        boolean[] c = new boolean[n];
        for (int k = 0; k < n; k++) {
            c[k] = Character.isLetterOrDigit(q.charAt(k)) && !prot[k];
        }
        for (int[] w : words) {
            for (String f : fillers) {
                int e = w[0] + f.length();
                if (q.startsWith(f, w[0]) && (e == n || !Character.isLetterOrDigit(q.charAt(e)))) {
                    Arrays.fill(c, w[0], e, false);
                    break;
                }
            }
        }
        for (int[] w : words) {
            int[] b = core(q, w);
            if (b[0] == b[1]) {
                continue;
            }
            String s = q.substring(b[0], b[1]);
            if (lists.connectiveWords().contains(s)) {
                Arrays.fill(c, b[0], b[1], false);
                continue;
            }
            if (lists.wordExceptions().contains(s) || names.contains(s)) {
                continue;
            }
            for (String p : suffixes) {
                if (s.length() > p.length() && s.endsWith(p)) {
                    Arrays.fill(c, b[1] - p.length(), b[1], false);
                    break;
                }
            }
        }
        return c;
    }

    /** 영어의도는 영어 태그와, 보정원어·의도보강은 저마다 한글 낱말과 대 본다(둘을 이어 붙여 생기는 낱말은 찾지 않는다). order 차례. */
    private List<Card> cardsFor(Token t) {
        Set<String> ids = new HashSet<>();
        cards.find(t.english(), t.corrected()).forEach(c -> ids.add(c.id()));
        cards.find("", t.expansion()).forEach(c -> ids.add(c.id()));
        return cards.all().stream().filter(c -> ids.contains(c.id())).sorted(order).toList();
    }
}
