package com.zipinfo.ai.report;

import com.zipinfo.ai.num.Q;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 한글 수사 사전 v1과 기간 말(순서 11b 설계 4판 4.2·5.1). experiment/prep/numerals.py의 v1과 같은 규칙이고,
 * 같은 예문 묶음(experiment/prep/tests/data/numerals_v1.json)으로 시험한다.
 * <ul>
 * <li>바른 자리표시자를 빈칸 하나로 바꾼 글에서 센다(자리표시자 이름은 글이 아니다).</li>
 * <li>기간 말(달·개월·년·해)은 길이(달 수)를 함께 낸다. 셀 수 없으면(어림수 등) 길이가 없다.</li>
 * <li>Numerals(v0)는 11a 기록과 잇는 데만 남긴다.</li>
 * </ul>
 */
public final class NumeralsV1 {
    private NumeralsV1() {}

    static final Map<String, Integer> ONES = new LinkedHashMap<>();
    static final Map<String, Integer> TENS = new LinkedHashMap<>();
    static final List<String> APPROX = List.of("한두", "두세", "두어", "서너", "너덧", "너댓", "네댓", "대여섯", "예닐곱");
    static final Map<Character, Integer> SINO_DIGIT = new LinkedHashMap<>();
    static final Map<Character, Integer> SINO_SMALL = Map.of('십', 10, '백', 100, '천', 1000);
    static final Map<Character, Integer> SINO_BIG = Map.of('만', 10_000, '억', 100_000_000);
    static final List<String> UNITS = List.of("배", "곳", "건", "개월", "개", "달", "년", "해", "층", "평", "제곱미터", "㎡", "퍼센트", "프로",
            "만원", "억원", "원", "채", "가구", "세대");
    static final Map<String, Integer> PERIOD_UNITS = Map.of("달", 1, "개월", 1, "년", 12, "해", 12);

    static {
        String[] ones = {"한", "1", "두", "2", "세", "3", "석", "3", "네", "4", "넉", "4", "다섯", "5", "여섯", "6", "일곱", "7", "여덟", "8", "아홉", "9"};
        for (int i = 0; i < ones.length; i += 2) {
            ONES.put(ones[i], Integer.parseInt(ones[i + 1]));
        }
        String[] tens = {"열", "10", "스무", "20", "스물", "20", "서른", "30", "마흔", "40", "쉰", "50", "예순", "60", "일흔", "70", "여든", "80", "아흔", "90"};
        for (int i = 0; i < tens.length; i += 2) {
            TENS.put(tens[i], Integer.parseInt(tens[i + 1]));
        }
        String digits = "일이삼사오육칠팔구";
        for (int i = 0; i < digits.length(); i++) {
            SINO_DIGIT.put(digits.charAt(i), i + 1);
        }
    }

    /** 긴 말부터(같은 곳에서 짧은 말이 먼저 걸려 단위를 자르지 않게: 개월·개). 같은 길이는 글자 차례. */
    static String alt(java.util.Collection<String> words) {
        List<String> w = new ArrayList<>(words);
        w.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));
        return String.join("|", w);
    }

    static final String SINO_COMPOUND = Numerals.SINO_COMPOUND;
    static final String SINO_SINGLE = Numerals.SINO_SINGLE;
    static final String NATIVE = "(?:" + alt(TENS.keySet()) + ")(?:" + alt(APPROX) + "|" + alt(ONES.keySet()) + ")?|" + alt(APPROX) + "|"
            + alt(ONES.keySet()) + "|반";
    static final String UNIT = alt(UNITS);
    public static final Pattern V1 = Pattern.compile("(?<![가-힣])(?:(?:" + NATIVE + "|" + SINO_COMPOUND + "|[십백천])\\s?(?:" + UNIT + ")|(?:"
            + SINO_SINGLE + ")\\s(?:" + UNIT + ")|일\\s?년)");

    /** 걸린 말 하나: 수사 낱말, 단위, 기간 말인가, 길이(달, 셀 수 없으면 null). */
    public record Hit(String word, String token, String unit, boolean period, Integer months) {}

    static long sinoValue(String t) {
        long total = 0;
        long section = 0;
        long digit = 0;
        for (char ch : t.toCharArray()) {
            if (SINO_DIGIT.containsKey(ch)) {
                digit = SINO_DIGIT.get(ch);
            } else if (SINO_SMALL.containsKey(ch)) {
                section += (digit == 0 ? 1 : digit) * SINO_SMALL.get(ch);
                digit = 0;
            } else {
                long s = section + digit;
                total += (s == 0 ? 1 : s) * SINO_BIG.get(ch);
                section = 0;
                digit = 0;
            }
        }
        return total + section + digit;
    }

    /** 수사 낱말의 값. 어림수는 null. */
    static Q valueOf(String token) {
        if (APPROX.contains(token)) {
            return null;
        }
        if (token.equals("반")) {
            return Q.HALF;
        }
        if (ONES.containsKey(token)) {
            return Q.of(ONES.get(token));
        }
        List<String> tens = new ArrayList<>(TENS.keySet());
        tens.sort(Comparator.comparingInt(String::length).reversed());
        for (String t : tens) {
            if (token.startsWith(t)) {
                String rest = token.substring(t.length());
                if (rest.isEmpty()) {
                    return Q.of(TENS.get(t));
                }
                if (ONES.containsKey(rest)) {
                    return Q.of(TENS.get(t) + ONES.get(rest));
                }
                return null;
            }
        }
        return Q.of(sinoValue(token));
    }

    /** text는 바른 자리표시자를 빈칸으로 바꾼 글이다. */
    public static List<Hit> scan(String text) {
        List<Hit> out = new ArrayList<>();
        List<String> units = new ArrayList<>(UNITS);
        units.sort(Comparator.comparingInt(String::length).reversed());
        Matcher m = V1.matcher(text);
        while (m.find()) {
            String s = m.group();
            String unit = units.stream().filter(s::endsWith).findFirst().orElseThrow();
            String token = s.substring(0, s.length() - unit.length()).replaceAll("\\s", "");
            if (!PERIOD_UNITS.containsKey(unit)) {
                out.add(new Hit(s, token, unit, false, null));
                continue;
            }
            Q v = valueOf(token);
            Integer months = null;
            if (v != null) {
                Q n = v.mul(Q.of(PERIOD_UNITS.get(unit)));
                if (n.isInteger() && n.compareTo(Q.ONE) >= 0) {
                    months = n.numerator().intValueExact();
                }
            }
            out.add(new Hit(s, token, unit, true, months));
        }
        return out;
    }

    /** YYYYMM 두 끝을 넣어 센 달 수(202511~202610 = 12). 꼴이 아니거나 끝이 앞서면 null. */
    public static Integer monthsOf(String start, String end) {
        if (start == null || end == null || !start.matches("[0-9]{4}(0[1-9]|1[0-2])") || !end.matches("[0-9]{4}(0[1-9]|1[0-2])")) {
            return null;
        }
        int a = Integer.parseInt(start.substring(0, 4)) * 12 + Integer.parseInt(start.substring(4));
        int b = Integer.parseInt(end.substring(0, 4)) * 12 + Integer.parseInt(end.substring(4));
        return b >= a ? b - a + 1 : null;
    }
}
