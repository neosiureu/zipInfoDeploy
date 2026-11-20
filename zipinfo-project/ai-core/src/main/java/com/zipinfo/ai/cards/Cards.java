package com.zipinfo.ai.cards;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 카드 묶음(설계안 8절). 파일은 카드마다 하나(`K01.md` 꼴)이고, 이 꼴이다.
 * <pre>
 * # K01 제목
 *
 * - 영어 태그: a, b
 * - 한글 낱말: 가, 나
 *
 * ## 핵심      (세 줄 안, 줄마다 "- ")
 * ## 사용법
 * ## 기록      (연습 전 판에서는 비어 있다)
 * </pre>
 * 찾기(search_cards, 그리고 ⓪이 같이 쓴다, 설계안 7.2): 영어 글의 낱말이 카드의 영어 태그와 같거나(대소문자 무시),
 * 카드의 한글 낱말이 한글 글 안에 들어 있으면(빈칸 무시) 찾는다. 차례는 카드 ID 순이다(⑤가 켜진 판의 좋아요 순은 순서 5).
 */
public final class Cards {
    private static final Pattern HEAD = Pattern.compile("^# (K[0-9]{2}) (\\S.*)$");
    private static final Pattern FILE = Pattern.compile("K[0-9]{2}\\.md");
    private static final Pattern TAG = Pattern.compile("[a-z]+");
    private static final Pattern NOT_LETTER = Pattern.compile("[^a-z]+");
    private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern COMMA = Pattern.compile("\\s*,\\s*");
    private static final String EN = "- 영어 태그: ";
    private static final String KO = "- 한글 낱말: ";
    private static final List<String> SECTIONS = List.of("핵심", "사용법", "기록");

    private final List<Card> cards;
    private final Map<String, Card> byId = new LinkedHashMap<>();

    public Cards(List<Card> cards) {
        List<Card> sorted = new ArrayList<>(cards);
        sorted.sort((a, b) -> a.id().compareTo(b.id()));
        for (Card c : sorted) {
            if (byId.put(c.id(), c) != null) {
                throw new IllegalArgumentException("카드 ID가 겹친다: " + c.id());
            }
        }
        this.cards = List.copyOf(sorted);
    }

    /** 폴더의 `K??.md`를 모두 읽는다. 파일 이름과 카드 ID가 같아야 한다. */
    public static Cards load(Path dir) throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> FILE.matcher(p.getFileName().toString()).matches()).sorted().toList();
        }
        if (files.isEmpty()) {
            throw new IOException("카드 파일이 없다: " + dir);
        }
        List<Card> out = new ArrayList<>();
        for (Path f : files) {
            Card c;
            try {
                c = parse(Files.readString(f, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                throw new IOException(f.getFileName() + ": " + e.getMessage(), e);
            }
            if (!f.getFileName().toString().equals(c.id() + ".md")) {
                throw new IOException("파일 이름과 카드 ID가 다르다: " + f.getFileName());
            }
            out.add(c);
        }
        return new Cards(out);
    }

    /** 카드 글 하나를 읽는다. 줄 끝은 LF로, 글자는 NFC로 맞춘다. */
    public static Card parse(String text) {
        String t = Normalizer.normalize(text.replace("\r\n", "\n"), Normalizer.Form.NFC);
        List<String> lines = Arrays.asList(t.split("\n", -1));
        Matcher h = HEAD.matcher(lines.get(0));
        if (!h.matches()) {
            throw new IllegalArgumentException("첫 줄은 '# K01 제목' 꼴이다");
        }
        List<String> en = null;
        List<String> ko = null;
        int i = 1;
        for (; i < lines.size() && !lines.get(i).startsWith("## "); i++) {
            String l = lines.get(i);
            if (l.startsWith(EN) && en == null) {
                en = List.of(COMMA.split(l.substring(EN.length()).strip()));
            } else if (l.startsWith(KO) && ko == null) {
                ko = List.of(COMMA.split(l.substring(KO.length()).strip()));
            } else if (!l.isBlank()) {
                throw new IllegalArgumentException("제목과 핵심 사이에는 영어 태그·한글 낱말 두 줄만 둔다: " + l);
            }
        }
        if (en == null || ko == null) {
            throw new IllegalArgumentException("영어 태그와 한글 낱말 줄이 있어야 한다");
        }
        for (String w : en) {
            if (!TAG.matcher(w).matches()) {
                throw new IllegalArgumentException("영어 태그는 소문자 낱말 하나씩이다: " + w);
            }
        }
        for (String w : ko) {
            if (w.isBlank()) {
                throw new IllegalArgumentException("빈 한글 낱말");
            }
        }
        Map<String, String> body = new LinkedHashMap<>();
        while (i < lines.size()) {
            String name = lines.get(i).substring(3).strip();
            int j = i + 1;
            while (j < lines.size() && !lines.get(j).startsWith("## ")) {
                j++;
            }
            if (body.put(name, String.join("\n", lines.subList(i + 1, j)).strip()) != null) {
                throw new IllegalArgumentException("층이 겹친다: " + name);
            }
            i = j;
        }
        if (!List.copyOf(body.keySet()).equals(SECTIONS)) {
            throw new IllegalArgumentException("층은 핵심·사용법·기록 차례로 셋이다: " + body.keySet());
        }
        List<String> core = body.get("핵심").lines().toList();
        if (core.isEmpty() || core.size() > 3 || core.stream().anyMatch(l -> !l.startsWith("- "))) {
            throw new IllegalArgumentException("핵심은 '- '로 시작하는 세 줄 안이다");
        }
        if (body.get("사용법").isEmpty()) {
            throw new IllegalArgumentException("사용법이 비었다");
        }
        return new Card(h.group(1), h.group(2).strip(), en, ko, body.get("핵심"), body.get("사용법"), body.get("기록"));
    }

    public List<Card> all() {
        return cards;
    }

    /** 없으면 null. */
    public Card get(String id) {
        return byId.get(id);
    }

    /** ⓪이 쓰는 꼴: 영어 글의 낱말이 영어 태그와 같거나, 한글 글 안에 한글 낱말이 있는 카드(ID 순). */
    public List<Card> find(String english, String korean) {
        Set<String> words = new HashSet<>(Arrays.asList(NOT_LETTER.split(english.toLowerCase(Locale.ROOT))));
        String ko = squeeze(korean);
        List<Card> out = new ArrayList<>();
        for (Card c : cards) {
            if (c.englishTags().stream().anyMatch(words::contains) || c.koreanWords().stream().anyMatch(w -> ko.contains(squeeze(w)))) {
                out.add(c);
            }
        }
        return out;
    }

    /** search_cards: 검색어 하나에 두 규칙을 함께 건다. */
    public List<Card> search(String query) {
        return find(query, query);
    }

    /** read_card와 B0+ 시스템 글에 넣는 카드 글. 태그 줄은 빼고, 빈 기록 층도 뺀다. */
    public static String render(Card c) {
        StringBuilder b = new StringBuilder();
        b.append("# ").append(c.id()).append(' ').append(c.title()).append("\n\n## 핵심\n\n").append(c.core())
                .append("\n\n## 사용법\n\n").append(c.usage());
        if (!c.log().isEmpty()) {
            b.append("\n\n## 기록\n\n").append(c.log());
        }
        return b.toString();
    }

    /** B0+가 시스템 글 뒤에 붙이는 카드 전문(설계안 6.4, ID 순). */
    public String fullText() {
        return "[카드]\n\n" + cards.stream().map(Cards::render).collect(Collectors.joining("\n\n"));
    }

    private static String squeeze(String s) {
        return SPACES.matcher(Normalizer.normalize(s, Normalizer.Form.NFC)).replaceAll("");
    }
}
