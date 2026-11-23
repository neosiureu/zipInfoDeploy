package com.zipinfo.ai.reflex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zipinfo.ai.cards.Card;
import com.zipinfo.ai.data.ListingMeta;
import com.zipinfo.ai.loop.RunRecord;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * ⑤의 기억과 좋아요(설계안 7.6). 정해 둔 연습 실행의 기록으로 만들고, 얼린 뒤에는 읽기만 한다. 같은 입력이면 늘 같은 결과다.
 * <p>기억
 * <ul>
 * <li>항목은 (막힌 꼴, 막힌 값, 풀린 값)이고, 매물의 현재 시군구 아래에 둔다. trade_stats만 본다.</li>
 * <li>막힌 꼴은 셋이다: 거절(장치), 오류(도구 오류), 0건(받아들여졌으나 표본 0).</li>
 * <li>막힘은 매물의 부르기(매물의 API, 매물의 현재 코드)가 와야 닫힌다(순서 6b L). 거절·오류는 그 부르기에서 닫히고(0건이어도),
 *     0건은 그 부르기에 표본이 있어야 닫힌다. 다른 부르기는 받아들여져도 막힘을 닫지 않는다. 닫힐 때 api나 region_code가 막힌 부르기와 다르면 뽑는다.</li>
 * <li>값은 api·region_code 가운데 바뀐 칸만 "칸=값"으로 적는다. 다른 칸(기간, deal, dong, jibun)은 바뀌어도 세지 않는다.
 *     같은 시군구의 다른 매물에도 맞는 값은 이 둘뿐이라서다(열린 결정 5 (가)(나), 순서 5c 메모).</li>
 * <li>찾기(5c 메모 4절 (B)): api가 바뀌지 않은 항목(지역 코드 길)은 같은 시군구의 모든 매물에 보인다.
 *     api가 바뀐 항목은 풀린 api가 매물의 API와 같을 때만 보인다.</li>
 * <li>같은 길(막힌 값, 풀린 값, api)은 시군구마다 항목 하나다(순서 6b D). 낸 실행 수는 그 길을 낸 실행마다 한 번 센다.
 *     꼴은 그 길을 낸 실행에서 본 꼴마다 실행 수를 세어 가장 많은 것이고, 같으면 글자 순으로 앞선 것이다.</li>
 * <li>항목의 정한 차례: 낸 실행이 많은 순, 같으면 (막힌 꼴, 막힌 값, 풀린 값, api)의 글자 순. 매물마다 보일 수 있는 항목을 이 차례로 K개 보인다
 *     (열린 결정 5 (다)). 얼린 파일도 이 차례로 적는다.</li>
 * </ul>
 * <p>좋아요: 받아들여진 제출로 끝났고 그 제출의 기록 전용 대조에 걸린 것이 없는 실행에서, read_card로 읽은 카드마다 1을 더한다(한 실행에 한 번).
 * 정답과 채점은 쓰지 않는다.
 */
public final class Memory {
    /** 안내 자리에서 보이는 항목 수(7.6 v0). */
    public static final int K = 2;
    /** 막힌 꼴 셋. */
    public static final Set<String> KINDS = Set.of("거절", "오류", "0건");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> FIELDS = List.of("api", "region_code");
    private static final String REJECTED = "거절";
    private static final String ERROR = "오류";
    private static final String ZERO = "0건";

    /**
     * 기억 항목 하나. 값은 바뀐 칸만 "api=…, region_code=…" 꼴로 적는다.
     * api는 api가 바뀐 항목이면 풀린 api(매물의 API)이고, 그 API의 매물에만 보인다. 지역 코드 길이면 빈 글이고, 같은 시군구의 모든 매물에 보인다.
     */
    public record Item(String kind, String blocked, String solved, String api) {}

    /** 연습 실행 하나: 매물, 끝난 꼴, 기록 사건(RunRecord의 사건을 JSON으로 바꾼 것. 하네스가 쓰는 JSONL 한 줄과 같은 꼴). */
    public record Run(ListingMeta meta, RunRecord.Outcome outcome, List<JsonNode> events) {
        public static Run of(ListingMeta meta, RunRecord rec) {
            List<JsonNode> ev = new ArrayList<>();
            for (Map<String, Object> e : rec.events()) {
                ev.add(JSON.valueToTree(e));
            }
            return new Run(meta, rec.outcome(), List.copyOf(ev));
        }
    }

    private record Blocked(String kind, JsonNode input) {}

    /** 길(D): 막힌 값, 풀린 값, api. 꼴은 길의 대표 값이다. */
    private record Way(String blocked, String solved, String api) {
        static Way of(Item i) {
            return new Way(i.blocked(), i.solved(), i.api());
        }
    }

    private static final Comparator<Map.Entry<Item, Integer>> SHOW = Comparator
            .comparing((Map.Entry<Item, Integer> e) -> -e.getValue())
            .thenComparing(e -> e.getKey().kind())
            .thenComparing(e -> e.getKey().blocked())
            .thenComparing(e -> e.getKey().solved())
            .thenComparing(e -> e.getKey().api());

    /** 채우기 전의 기억: 안내가 없고, 카드는 ID 순이다. */
    public static final Memory EMPTY = new Memory(Map.of(), Map.of());

    private final Map<String, Map<Item, Integer>> items;
    private final Map<String, Integer> likes;

    /** 시군구는 글자 순, 항목은 정한 차례로 다시 놓는다. 그래서 만든 차례·읽은 차례와 상관없이 items()가 같다. */
    private Memory(Map<String, Map<Item, Integer>> items, Map<String, Integer> likes) {
        Map<String, Map<Item, Integer>> copy = new TreeMap<>();
        items.forEach((area, m) -> {
            Map<Item, Integer> sorted = new LinkedHashMap<>();
            m.entrySet().stream().sorted(SHOW).forEach(e -> sorted.put(e.getKey(), e.getValue()));
            copy.put(area, Collections.unmodifiableMap(sorted));
        });
        this.items = Collections.unmodifiableMap(copy);
        this.likes = Collections.unmodifiableMap(new TreeMap<>(likes));
    }

    /** 정해 둔 연습 실행들로 기억과 좋아요를 만든다. 같은 길은 여기서 한 항목으로 합친다(D). */
    public static Memory build(List<Run> runs) {
        Map<String, Map<Way, Integer>> runsOf = new TreeMap<>();
        Map<String, Map<Way, Map<String, Integer>>> kindsOf = new TreeMap<>();
        Map<String, Integer> likes = new TreeMap<>();
        for (Run r : runs) {
            Map<Way, Set<String>> seen = new LinkedHashMap<>();
            for (Item it : extract(r)) {
                seen.computeIfAbsent(Way.of(it), k -> new TreeSet<>()).add(it.kind());
            }
            String area = r.meta().area();
            seen.forEach((w, kinds) -> {
                runsOf.computeIfAbsent(area, k -> new LinkedHashMap<>()).merge(w, 1, Integer::sum);
                Map<String, Integer> byKind = kindsOf.computeIfAbsent(area, k -> new LinkedHashMap<>()).computeIfAbsent(w, k -> new TreeMap<>());
                kinds.forEach(k -> byKind.merge(k, 1, Integer::sum));
            });
            for (String c : likedCards(r)) {
                likes.merge(c, 1, Integer::sum);
            }
        }
        Map<String, Map<Item, Integer>> items = new TreeMap<>();
        runsOf.forEach((area, ways) -> {
            Map<Item, Integer> m = new LinkedHashMap<>();
            ways.forEach((w, n) -> m.put(new Item(kind(kindsOf.get(area).get(w)), w.blocked(), w.solved(), w.api()), n));
            items.put(area, m);
        });
        return new Memory(items, likes);
    }

    /** 길의 꼴: 꼴마다 센 실행 수가 가장 많은 것, 같으면 글자 순으로 앞선 것(byKind는 글자 순 TreeMap). */
    private static String kind(Map<String, Integer> byKind) {
        String best = null;
        int most = 0;
        for (Map.Entry<String, Integer> e : byKind.entrySet()) {
            if (e.getValue() > most) {
                best = e.getKey();
                most = e.getValue();
            }
        }
        return best;
    }

    /**
     * 얼린 파일에서 읽은 기억과 좋아요(하네스가 읽는다). build가 낸 꼴이어야 한다.
     * 꼴은 셋 가운데 하나, 막힌 값·풀린 값은 비지 않은 글, 한 시군구에 같은 길은 하나, 실행 수와 좋아요는 1 이상이다.
     */
    public static Memory of(Map<String, Map<Item, Integer>> items, Map<String, Integer> likes) {
        items.forEach((area, m) -> {
            if (area == null || area.isBlank() || m.isEmpty()) {
                throw new IllegalArgumentException("기억의 시군구가 비었거나 항목이 없다: " + area);
            }
            Set<Way> ways = new HashSet<>();
            m.forEach((i, n) -> {
                if (!KINDS.contains(i.kind()) || i.blocked() == null || i.blocked().isBlank() || i.solved() == null || i.solved().isBlank()
                        || i.api() == null || n == null || n < 1) {
                    throw new IllegalArgumentException("기억 항목이 틀렸다(" + area + "): " + i + " " + n);
                }
                if (!ways.add(Way.of(i))) {
                    throw new IllegalArgumentException("한 시군구에 같은 길이 둘이다(" + area + "): " + i);
                }
            });
        });
        likes.forEach((c, n) -> {
            if (c == null || c.isBlank() || n == null || n < 1) {
                throw new IllegalArgumentException("좋아요가 틀렸다: " + c + " " + n);
            }
        });
        return new Memory(items, likes);
    }

    /** 시군구별 항목과 그 항목을 낸 실행 수(얼릴 때 쓴다). 시군구는 글자 순, 항목은 정한 차례다. */
    public Map<String, Map<Item, Integer>> items() {
        return items;
    }

    /** 카드별 좋아요(얼릴 때 쓴다). 카드 ID 순이다. */
    public Map<String, Integer> likes() {
        return likes;
    }

    /** 안내 자리: 매물의 현재 시군구와 API로 보일 수 있는 항목, 정한 차례로 많아야 K개. */
    public List<Item> lookup(String area, String api) {
        return items.getOrDefault(area, Map.of()).keySet().stream()
                .filter(i -> i.api().isEmpty() || i.api().equals(api))
                .limit(K).toList();
    }

    public int likes(String cardId) {
        return likes.getOrDefault(cardId, 0);
    }

    /** ⑤가 켜진 판의 카드 차례: 좋아요 많은 순, 같으면 카드 ID 순. */
    public Comparator<Card> cardOrder() {
        return Comparator.comparingInt((Card c) -> -likes(c.id())).thenComparing(Card::id);
    }

    /**
     * 실행 하나에서 뽑은 항목(찾은 차례, 같은 항목은 한 번). 막힘을 닫는 부르기는 매물의 API와 매물의 현재 코드(영역 코드)다(L).
     * 영역 코드는 모든 매물의 API로 창의 모든 달에 유효하고, 옛 코드가 유효로 나오는 칸은 정말 0건 달뿐이다(5c 메모 4절).
     * 그래서 "유효한 코드"가 아니라 "현재 코드"로 본다. 정말 0건 달에만 부른 옛 코드로 "풀린" 길이 뽑히지 않는다.
     * 다른 API나 다른 코드의 받아들여진 부르기는 막힘을 닫지 않는다. 그래서 그 뒤에 온 매물의 부르기로 길을 잃지 않고 뽑는다.
     */
    public static List<Item> extract(Run run) {
        String area = run.meta().area();
        String api = run.meta().api();
        List<Blocked> open = new ArrayList<>();
        Set<Item> out = new LinkedHashSet<>();
        for (JsonNode e : run.events()) {
            if (!"tool".equals(e.path("type").asText()) || !"trade_stats".equals(e.path("name").asText())) {
                continue;
            }
            JsonNode in = e.path("input");
            if (e.path("is_error").asBoolean()) {
                open.add(new Blocked("REJECTED".equals(e.path("error_code").asText()) ? REJECTED : ERROR, in));
                continue;
            }
            boolean found = sample(e.path("result").asText()) > 0;
            boolean right = value(in, "api").equals(api) && value(in, "region_code").equals(area);
            if (right) {
                List<Blocked> still = new ArrayList<>();
                for (Blocked b : open) {
                    if (b.kind().equals(ZERO) && !found) {
                        still.add(b);
                        continue;
                    }
                    List<String> changed = FIELDS.stream().filter(f -> !value(b.input(), f).equals(value(in, f))).toList();
                    if (!changed.isEmpty()) {
                        out.add(new Item(b.kind(), show(b.input(), changed), show(in, changed), changed.contains("api") ? api : ""));
                    }
                }
                open = still;
            }
            if (!found) {
                open.add(new Blocked(ZERO, in));
            }
        }
        return List.copyOf(out);
    }

    /** 실행 하나에서 좋아요를 받을 카드(읽은 차례). 조건을 갖추지 못한 실행이면 비어 있다. */
    public static Set<String> likedCards(Run run) {
        if (run.outcome() != RunRecord.Outcome.SUBMITTED) {
            return Set.of();
        }
        JsonNode last = null;
        for (JsonNode e : run.events()) {
            if ("submit".equals(e.path("type").asText())) {
                last = e;
            }
        }
        if (last == null || !last.path("findings").isArray() || !last.path("findings").isEmpty()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (JsonNode e : run.events()) {
            if ("tool".equals(e.path("type").asText()) && "read_card".equals(e.path("name").asText()) && !e.path("is_error").asBoolean()) {
                out.add(parse(e.path("result").asText()).path("카드").asText());
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** trade_stats 결과의 표본 수. 받아들여진 결과에는 늘 있다. */
    private static long sample(String result) {
        JsonNode r = parse(result);
        JsonNode v = r.path(r.path("id").asText() + ".표본수").path("값");
        if (v.isMissingNode() || v.isNull()) {
            throw new IllegalArgumentException("trade_stats 결과에 표본 수가 없다");
        }
        return new BigDecimal(v.asText()).longValueExact();
    }

    private static JsonNode parse(String text) {
        try {
            return JSON.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("기록의 결과 글이 JSON이 아니다", e);
        }
    }

    /** 칸 값: 글이면 그 글, 없으면 "(없음)", 그 밖이면 JSON 글. */
    private static String value(JsonNode in, String field) {
        JsonNode v = in == null ? null : in.get(field);
        if (v == null || v.isNull()) {
            return "(없음)";
        }
        return v.isTextual() ? v.asText() : v.toString();
    }

    private static String show(JsonNode in, List<String> fields) {
        List<String> out = new ArrayList<>();
        for (String f : fields) {
            out.add(f + "=" + value(in, f));
        }
        return String.join(", ", out);
    }
}
