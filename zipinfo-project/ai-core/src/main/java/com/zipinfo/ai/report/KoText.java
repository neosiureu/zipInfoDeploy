package com.zipinfo.ai.report;

import com.zipinfo.ai.num.Q;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 그리기 v1과 글 검사 v1이 같이 쓰는 한국어 글 규칙(순서 11b-2 설계 b판 5.1·5.3, 상대 b판 검토).
 * 예문 묶음은 experiment/prep/tests/data/text_v1.json이고, 파이썬 prep/textcheck.py가 같은 묶음의 checks를 같은 결과로 낸다.
 * <ul>
 * <li>그리기는 자기가 넣는 말(값·단위·방향 말·값과 매물 이름 바로 뒤 조사)만 맞춘다. 모델이 쓴 문장 끝은 고치지 않는다.</li>
 * <li>글 검사는 그린 글을 본다. 찾은 것마다 코드 하나(같은 코드는 한 번)를 정해 둔 차례로 낸다.</li>
 * </ul>
 */
public final class KoText {
    private KoText() {}

    /** 그리기 앞의 값 하나: 값, 단위, 차이(근거 C?.차이)인지, 그릴 수 있는지(근거가 없거나 단위가 안 맞으면 false → 자리를 남긴다). */
    public record Slot(Q value, String unit, boolean diff, boolean ok) {}

    // ---------------------------------------------------------------- 한글 소리

    static boolean hangul(char c) {
        return c >= '가' && c <= '힣';
    }

    /** 받침 번호(0 없음, 8 ㄹ, 17 ㅂ …). 한글 음절이 아니면 -1. */
    static int batchim(char c) {
        return hangul(c) ? (c - '가') % 28 : -1;
    }

    /** 끝소리: 'c' 받침(ㄹ 밖), 'r' ㄹ, 'v' 받침 없음, 'x' 모름. 숫자는 읽는 소리(영·일·이·삼·사·오·육·칠·팔·구)로 본다. */
    static char endSound(String s) {
        String t = s;
        while (!t.isEmpty() && t.charAt(t.length() - 1) == ')') {
            int i = t.lastIndexOf('(');
            if (i < 0) {
                break;
            }
            t = t.substring(0, i);
        }
        if (t.isEmpty()) {
            return 'x';
        }
        char c = t.charAt(t.length() - 1);
        if (c == '%' || c == '㎡') {
            return 'v';
        }
        if (c >= '0' && c <= '9') {
            return switch (c) {
                case '1', '7', '8' -> 'r';      // 일·칠·팔
                case '0', '3', '6' -> 'c';      // 영·삼·육
                default -> 'v';                 // 이·사·오·구
            };
        }
        int b = batchim(c);
        if (b < 0) {
            return 'x';
        }
        return b == 0 ? 'v' : (b == 8 ? 'r' : 'c');
    }

    // ---------------------------------------------------------------- 값 그리기

    private static String group(BigDecimal d) {
        BigDecimal x = d.stripTrailingZeros();
        if (x.scale() < 0) {
            x = x.setScale(0, RoundingMode.UNNECESSARY);
        }
        DecimalFormat f = new DecimalFormat("#,##0" + (x.scale() > 0 ? "." + "0".repeat(x.scale()) : ""),
                DecimalFormatSymbols.getInstance(Locale.ROOT));
        return f.format(x);
    }

    private static BigDecimal dec(Q v) {
        return v.approx(4);
    }

    /** 원 값: 1만 원 아래는 원 그대로, 그 위는 억·만·(만 아래 나머지)원. */
    public static String moneyWon(Q v) {
        BigDecimal d = dec(v.abs());
        String sign = v.signum() < 0 ? "-" : "";
        if (d.compareTo(BigDecimal.valueOf(10000)) < 0) {
            return sign + group(d) + "원";
        }
        BigDecimal[] eok = d.divideAndRemainder(BigDecimal.valueOf(100_000_000));
        BigDecimal[] man = eok[1].divideAndRemainder(BigDecimal.valueOf(10000));
        List<String> parts = new ArrayList<>();
        if (eok[0].signum() > 0) {
            parts.add(group(eok[0]) + "억");
        }
        if (man[0].signum() > 0) {
            parts.add(group(man[0]) + "만");
        }
        if (man[1].signum() > 0) {
            parts.add(group(man[1]));
        }
        return sign + String.join(" ", parts) + "원";
    }

    /** 만원 값: 1억(1만 만원) 아래는 만원 그대로, 그 위는 억과 나머지 만원. 나머지가 0이면 "억원". 소수는 만 자리에 둔다. */
    public static String moneyMan(Q v) {
        BigDecimal d = dec(v.abs());
        String sign = v.signum() < 0 ? "-" : "";
        if (d.compareTo(BigDecimal.valueOf(10000)) < 0) {
            return sign + group(d) + "만원";
        }
        BigDecimal[] eok = d.divideAndRemainder(BigDecimal.valueOf(10000));
        if (eok[1].signum() == 0) {
            return sign + group(eok[0]) + "억원";
        }
        return sign + group(eok[0]) + "억 " + group(eok[1]) + "만원";
    }

    /** 값 하나의 보이는 글. 차이는 부호 없는 크기로 그린다(방향은 방향 말이 맡는다). */
    public static String display(Q value, String unit, boolean diff) {
        Q v = diff ? value.abs() : value;
        return switch (unit) {
            case "원" -> moneyWon(v);
            case "만원" -> moneyMan(v);
            case "%" -> v.approx(1).toPlainString() + "%";
            case "평" -> v.approx(2).toPlainString() + "평";
            default -> group(dec(v)) + unit;
        };
    }

    // ---------------------------------------------------------------- 그리기

    public static final Pattern PLACEHOLDER = Pattern.compile("\\{([가-힣]{1,6})(\\.방향)?\\}");
    public static final String NAME_SLOT = "매물명";
    private static final Pattern DIR_PAREN_COPULA = Pattern.compile("(\\{[가-힣]{1,6}\\.방향\\})(\\([^()]*\\))(입니다|이다)");
    private static final Pattern DOUBLE_SPACE = Pattern.compile("(?<=[.!?]) {2}(?=\\S)");

    static final Map<String, List<String>> DUP = new LinkedHashMap<>();
    static {
        List<String> money = List.of("억원", "만원", "만 원", "원");
        DUP.put("원", money);
        DUP.put("만원", money);
        DUP.put("%", List.of("%", "퍼센트", "프로"));
        DUP.put("㎡", List.of("㎡", "제곱미터"));
        DUP.put("평", List.of("평"));
        DUP.put("건", List.of("건"));
        DUP.put("층", List.of("층"));
        DUP.put("개월", List.of("개월", "달"));
        DUP.put("년", List.of("년"));
    }

    /** 지운 단위 말 뒤에 와도 되는 말(조사·이음). 이것이 아닌 한글이 오면 다른 낱말(원래·건물 등)이라 지우지 않는다. */
    static final List<String> FOLLOW = List.of("으로", "로", "을", "를", "이었", "이다", "입니다", "이고", "이며", "이지만", "이라", "이면", "이나",
            "인", "이", "가", "은", "는", "과", "와", "의", "에", "도", "만", "보다", "까지", "부터", "씩", "당", "정도", "가량", "쯤", "나", "면", "였", "짜리");

    private static boolean followOk(String after) {
        if (after.isEmpty() || !hangul(after.charAt(0))) {
            return true;
        }
        for (String f : FOLLOW) {
            if (after.startsWith(f)) {
                return true;
            }
        }
        return false;
    }

    /** 그린 값 바로 뒤에서 지울 글자 수(같은 단위·읽는 말, 빈칸 하나까지). */
    static int dupLength(String rest, String unit) {
        for (String w : DUP.getOrDefault(unit, List.of())) {
            for (String sp : new String[] {"", " "}) {
                String t = sp + w;
                if (rest.startsWith(t) && followOk(rest.substring(t.length()))) {
                    return t.length();
                }
            }
        }
        return 0;
    }

    static final String[][] PARTICLES = {{"으로", "로"}, {"을", "를"}, {"이", "가"}, {"은", "는"}, {"과", "와"}};

    /** rest 맨 앞의 조사를 끝소리에 맞춘 것. 바꿀 것이 없으면 null. 뒤가 한글이면(이다·이고·이었 등) 건드리지 않는다. */
    static String[] particleFix(String rest, char sound) {
        if (sound == 'x') {
            return null;
        }
        for (String[] pair : PARTICLES) {
            for (String p : pair) {
                if (rest.startsWith(p) && (rest.length() == p.length() || !hangul(rest.charAt(p.length())))) {
                    boolean vowelForm = sound == 'v' || (sound == 'r' && pair[0].equals("으로"));
                    String want = vowelForm ? pair[1] : pair[0];
                    return want.equals(p) ? null : new String[] {p, want};
                }
            }
        }
        return null;
    }

    record Dir(String fin, String go, String myeo, String adn, String base) {}

    static final Dir UP = new Dir("비쌉니다", "비싸고", "비싸며", "비싼", "비싸다");
    static final Dir DOWN = new Dir("쌉니다", "싸고", "싸며", "싼", "싸다");
    static final Dir SAME = new Dir("같습니다", "같고", "같으며", "비슷한", "같다");
    private static final Pattern PYEON = Pattern.compile("^ ?편(입니다|이다)?");

    /** 방향 말과, 뒤 글에서 그 말이 먹은 글자 수(5.1-5 표). */
    static Object[] direction(int sign, String rest) {
        Dir f = sign > 0 ? UP : (sign < 0 ? DOWN : SAME);
        for (String[] w : new String[][] {{"입니다", "fin"}, {"이다", "fin"}, {"이고", "go"}, {"이며", "myeo"}}) {
            if (rest.startsWith(w[0])) {
                return new Object[] {pick(f, w[1]), w[0].length()};
            }
        }
        if (rest.startsWith("다") && (rest.length() == 1 || ".!?".indexOf(rest.charAt(1)) >= 0)) {
            return new Object[] {f.fin(), 1};
        }
        Matcher m = PYEON.matcher(rest);
        if (m.find()) {
            return new Object[] {f.adn() + " 편" + (m.group(1) != null ? "입니다" : ""), m.end()};
        }
        if (rest.startsWith("(")) {
            int j = rest.indexOf(')');
            boolean comma = j > 0 && rest.startsWith(",", j + 1);
            return new Object[] {comma ? f.go() : f.fin(), 0};
        }
        if (rest.startsWith("고")) {
            return new Object[] {f.base(), 0};
        }
        if (rest.startsWith(",") || (rest.length() > 1 && rest.charAt(0) == ' ' && hangul(rest.charAt(1)))) {
            return new Object[] {f.go(), 0};
        }
        return new Object[] {f.fin(), 0};
    }

    private static String pick(Dir f, String form) {
        return switch (form) {
            case "go" -> f.go();
            case "myeo" -> f.myeo();
            default -> f.fin();
        };
    }

    /**
     * 원래 글(자리표시자)을 그린다. slots는 자리 이름 → 값(그리기가 claim과 근거로 지은 것), name은 매물 칸의 이름(없으면 null).
     * 그릴 수 없는 자리(claim이 없음, 근거가 없음, 단위가 안 맞음, 차이가 아닌 자리의 방향)는 그대로 남긴다(검사의 BRACE가 잡는다).
     * <ul>
     * <li>값: 돈은 만·억(display), 바로 뒤의 같은 단위·읽는 말은 지운다(dupLength), 바로 뒤 조사는 끝소리에 맞춘다(particleFix).</li>
     * <li>방향 말: 뒤 말에 맞춰 합니다체로 활용한다(direction). 앞 글이 빈칸·여는 괄호가 아니면 빈칸 하나를 띄운다("{가}{가.방향}").</li>
     * <li>{매물명}: 매물 이름을 넣고 바로 뒤 조사를 맞춘다.</li>
     * <li>끝으로 빈 괄호를 지우고, 문장부호 뒤 두 칸을 한 칸으로 줄이고, 끝에 문장부호가 없으면 마침표를 붙인다.</li>
     * </ul>
     */
    public static String render(String text, Map<String, Slot> slots, String name) {
        String t = DIR_PAREN_COPULA.matcher(text).replaceAll("$1$2");
        StringBuilder out = new StringBuilder();
        Matcher m = PLACEHOLDER.matcher(t);
        int pos = 0;
        while (m.find()) {
            if (m.start() < pos) {
                continue;
            }
            out.append(t, pos, m.start());
            pos = m.end();
            String slot = m.group(1);
            boolean dir = m.group(2) != null;
            String rest = t.substring(m.end());
            if (slot.equals(NAME_SLOT) && !dir) {
                if (name == null || name.isBlank()) {
                    out.append(m.group());
                    continue;
                }
                out.append(name);
                String[] fix = particleFix(rest, endSound(name));
                if (fix != null) {
                    out.append(fix[1]);
                    pos += fix[0].length();
                }
                continue;
            }
            Slot s = slots.get(slot);
            if (s == null || !s.ok()) {
                out.append(m.group());
                continue;
            }
            if (dir) {
                if (!s.diff()) {
                    out.append(m.group());
                    continue;
                }
                Object[] d = direction(s.value().signum(), rest);
                // 방향 말은 낱말이다. 앞에 붙은 글(값 자리 등, "{가}{가.방향}")과 띄운다. 빈칸이나 여는 괄호 뒤에는 띄우지 않는다.
                if (!out.isEmpty() && !Character.isWhitespace(out.charAt(out.length() - 1)) && out.charAt(out.length() - 1) != '(') {
                    out.append(' ');
                }
                out.append((String) d[0]);
                pos += (Integer) d[1];
                continue;
            }
            String disp = display(s.value(), s.unit(), s.diff());
            out.append(disp);
            int skip = dupLength(rest, s.unit());
            pos += skip;
            String[] fix = particleFix(t.substring(pos), endSound(disp));
            if (fix != null) {
                out.append(fix[1]);
                pos += fix[0].length();
            }
        }
        out.append(t.substring(pos));
        String s = out.toString().replace(" ()", "").replace("()", "");
        s = DOUBLE_SPACE.matcher(s).replaceAll(" ");
        String trimmed = s.stripTrailing();
        if (!trimmed.isEmpty() && ".!?".indexOf(trimmed.charAt(trimmed.length() - 1)) < 0) {
            s = trimmed + ".";
        }
        return s;
    }

    // ---------------------------------------------------------------- 글 검사(그린 글)

    /** 검사 하나: 코드와 처방 한 줄. */
    public record Hit(String code, String prescription) {}

    /** 값 없는 단위 말: 빈칸·여는 괄호나 글 처음 뒤에 단위 말이 홀로 있다. 낱말 "이"·"그" 뒤(이 건, 그 건)는 뺀다(앞 낱말 끝의 이는 빼지 않는다). */
    private static final Pattern UNIT_NO_VALUE = Pattern.compile(
            "(?:^|(?<=[\\s(]))(?<![^가-힣]이 )(?<!^이 )(?<![^가-힣]그 )(?<!^그 )(?:만원|억원|원|퍼센트|프로|%|㎡|제곱미터|평|개월|개|건)"
                    + "(?=$|[\\s,.)!?]|보다|과|와|을|를|이|가|은|는|으로|로|의|에|도|만)");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]+");
    private static final Pattern SPACES = Pattern.compile("\\s{3,}");
    private static final Pattern QMARK = Pattern.compile("\\?(?=[가-힣])");
    private static final Pattern FORMAT = Pattern.compile(String.join("|",
            "만원\\s?만원", "억원\\s?원", "만원\\s?원(?![가-힣])", "(?<=[0-9])원\\s?원", "(?<=[0-9])건\\s?건", "(?<=[0-9])층\\s?층",
            "(?<=[0-9])평\\s?평", "%\\s?(?:%|퍼센트|프로)", "㎡\\s?(?:㎡|제곱미터)", "퍼센트\\s?퍼센트", "개월\\s?(?:개월|달)",
            "(?<![0-9,])(?:[0-9]{2,3},[0-9]{3}|[0-9]{1,3}(?:,[0-9]{3}){2,})(?:\\.[0-9]+)?만원",
            "(?<![0-9,])(?:[0-9]{2,3},[0-9]{3}|[0-9]{1,3}(?:,[0-9]{3}){2,})원",
            "(?<=[0-9])(?:만원|억원|원|건|층|평|년)(?:로|를|가|는|와)(?![가-힣])",
            "(?<=[0-9])(?:%|㎡)(?:으로|을|은|과|이)(?![가-힣])",
            "(?<=[0-9])개월으로(?![가-힣])",
            "(?:비싸다|싸다|같다|비쌉니다|쌉니다|같습니다)(?:이고|입니다|이다|이며|\\s?편)"));

    /**
     * 값 없는 단위 말이 있는가. 수사 낱말 뒤의 단위(한 건, 다섯 건)는 값이 말로 있는 것이라 세지 않는다(그것은 NUMERAL이 잡는다).
     * 수사는 수사 사전 v1(NumeralsV1.V1)이 그린 글에서 찾은 말이다.
     */
    static boolean unitWithoutValue(String rendered) {
        List<int[]> numerals = new ArrayList<>();
        Matcher nm = NumeralsV1.V1.matcher(rendered);
        while (nm.find()) {
            numerals.add(new int[] {nm.start(), nm.end()});
        }
        Matcher um = UNIT_NO_VALUE.matcher(rendered);
        while (um.find()) {
            int a = um.start();
            int b = um.end();
            if (numerals.stream().noneMatch(n -> n[0] <= a && b <= n[1])) {
                return true;
            }
        }
        return false;
    }

    /** 로마자 낱말(매물 칸·질문 글에 있는 것은 받는다). */
    public static Set<String> latinWords(String... texts) {
        Set<String> out = new java.util.HashSet<>();
        for (String t : texts) {
            if (t == null) {
                continue;
            }
            Matcher m = LATIN.matcher(t);
            while (m.find()) {
                out.add(m.group());
            }
        }
        return out;
    }

    /** 합니다체 끝인가(5.3 REGISTER의 셈). w는 문장 끝의 한글 덩이. */
    static boolean formalEnd(String w) {
        if (w.isEmpty()) {
            return false;
        }
        if ((w.endsWith("니다") || w.endsWith("니까")) && w.length() >= 3) {
            return batchim(w.charAt(w.length() - 3)) == 17;
        }
        return w.endsWith("십시오") || w.endsWith("세요");
    }

    /** 문장마다 끝 한글 덩이(끝의 괄호 묶음은 뗀다). 소수점(숫자 사이 마침표)은 문장 끝이 아니다. */
    static List<String> sentenceEnds(String text) {
        List<String> ends = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean decimal = c == '.' && i > 0 && i + 1 < text.length() && Character.isDigit(text.charAt(i - 1))
                    && Character.isDigit(text.charAt(i + 1));
            // 한글 바로 앞의 ?는 문장 끝이 아니라 깨진 글자다(QMARK가 잡는다).
            boolean glyph = c == '?' && i + 1 < text.length() && hangul(text.charAt(i + 1));
            if ((c == '.' || c == '!' || c == '?') && !decimal && !glyph) {
                addEnd(ends, cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        addEnd(ends, cur.toString());
        return ends;
    }

    private static void addEnd(List<String> ends, String sentence) {
        String s = sentence.strip();
        if (s.isEmpty()) {
            return;
        }
        while (s.endsWith(")")) {
            int i = s.lastIndexOf('(');
            if (i < 0) {
                break;
            }
            s = s.substring(0, i).strip();
        }
        int j = s.length();
        while (j > 0 && hangul(s.charAt(j - 1))) {
            j--;
        }
        ends.add(s.substring(j));
    }

    private static final Pattern CLAUSE_PLAIN = Pattern.compile("([가-힣]+다),");

    /** 그린 글의 검사(5.3): BRACE, UNIT_NO_VALUE, GARBLED, QMARK, REGISTER, FORMAT 차례로, 코드마다 많아야 하나. */
    public static List<Hit> check(String rendered, Set<String> allowedLatin) {
        List<Hit> out = new ArrayList<>();
        if (rendered.indexOf('{') >= 0 || rendered.indexOf('}') >= 0) {
            out.add(new Hit("BRACE", "그리지 못한 자리표시자가 남았다. 자리마다 근거가 있는 claim을 낸다"));
        }
        if (unitWithoutValue(rendered)) {
            out.add(new Hit("UNIT_NO_VALUE", "값 없이 단위 말만 남았다. 값은 자리표시자와 claim으로 쓴다"));
        }
        boolean junk = false;
        Matcher lm = LATIN.matcher(rendered);
        while (lm.find()) {
            if (!allowedLatin.contains(lm.group())) {
                junk = true;
                break;
            }
        }
        String tail = rendered.stripTrailing();
        if (junk || SPACES.matcher(rendered).find() || tail.endsWith("...") || tail.endsWith("…") || rendered.indexOf('�') >= 0) {
            out.add(new Hit("GARBLED", "글이 깨졌다. 답 글을 처음부터 다시 쓴다"));
        }
        if (QMARK.matcher(rendered).find()) {
            out.add(new Hit("QMARK", "물음표를 단위 자리에 썼다. 단위는 ㎡·% 그대로 쓴다"));
        }
        boolean plain = false;
        for (String w : sentenceEnds(rendered)) {
            if (!formalEnd(w)) {
                plain = true;
                break;
            }
        }
        if (!plain) {
            Matcher cm = CLAUSE_PLAIN.matcher(rendered);
            while (cm.find()) {
                if (!formalEnd(cm.group(1))) {
                    plain = true;
                    break;
                }
            }
        }
        if (plain) {
            out.add(new Hit("REGISTER", "모든 문장을 합니다체(-습니다·-ㅂ니다)로 끝낸다"));
        }
        if (FORMAT.matcher(rendered).find()) {
            out.add(new Hit("FORMAT", "그린 글의 꼴이 틀렸다(단위 되풀이·돈 표기·조사). 자리표시자 뒤에 단위를 쓰지 않는다"));
        }
        return out;
    }
}
