package com.zipinfo.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zipinfo.ai.cards.Cards;
import com.zipinfo.ai.data.AddressBook;
import com.zipinfo.ai.data.History;
import com.zipinfo.ai.data.Listing;
import com.zipinfo.ai.data.ListingMeta;
import com.zipinfo.ai.data.TradeStore;
import com.zipinfo.ai.data.ValidityTable;
import com.zipinfo.ai.reflex.IntentLists;
import com.zipinfo.ai.reflex.Memory;
import com.zipinfo.ai.reflex.Part;
import com.zipinfo.ai.reflex.Variant;
import com.zipinfo.ai.tools.DataSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 하네스 시험용 작은 자료. 도구 자료는 ai-core 시험의 Fixture와 같다(41591·41590, 값은 지어낸 것).
 * 매물 셋(L01·L02 연습, L03 본시험), 판 셋(c01 B0, c03 ①, c10 B1, ⑤ 시험은 c08 ①+⑤를 더함), 문항 셋(q001·q002 연습, q900 본시험).
 * 카드와 ⓪의 목록은 저장소의 것(experiment/cards/v0, intents)을 쓴다. 지문의 해시는 지어낸 글이다.
 */
final class Fixture {
    static final Path EXP = Path.of("..").toAbsolutePath().normalize();
    static final ObjectMapper M = new ObjectMapper();
    static final String HAIKU = "claude-haiku-5-5";
    static final String OPUS = "claude-opus-5-5";
    static final Variant B0 = new Variant("c01", "B0", HAIKU, Variant.CardMode.NONE, List.of());
    static final Variant ONE = new Variant("c03", "①", HAIKU, Variant.CardMode.TOOLS, List.of());
    static final Variant B1 = new Variant("c10", "B1", OPUS, Variant.CardMode.NONE, List.of());
    static final Variant MEM = new Variant("c08", "①+⑤", HAIKU, Variant.CardMode.TOOLS, List.of(Part.MEMORY));
    static final Question Q1 = new Question("q001", "L01", "T1", "연습", "시험 글 가");
    static final Question Q2 = new Question("q002", "L02", "T1", "연습", "시험 글 나");
    static final Question Q9 = new Question("q900", "L03", "T1", "본시험", "본시험 글 다");

    private Fixture() {}

    static void write(Path p, String... lines) throws IOException {
        Files.writeString(p, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    static DataSet data(Path dir) throws IOException {
        write(dir.resolve("bundles.csv"), String.join(",", TradeStore.BUNDLE_HEADER),
                "apt_trade,41591,202609,0,0,0,0", "apt_trade,41591,202610,4,4,0,0", "apt_trade,41590,202610,0,0,0,0",
                "apt_rent,41591,202610,3,3,0,0", "apt_rent,41591,202609,0,0,0,0", "rh_trade,41591,202610,1,1,0,0");
        String h = String.join(",", TradeStore.TRADE_HEADER);
        write(dir.resolve("trades.csv"), h,
                "apt_trade,41591,202610,1,0,3,매매,apt|41591|가동|0|12|0,4159110100,가동,12,가단지,84.97,5,2001,30000,,,가동",
                "apt_trade,41591,202610,1,1,9,매매,apt|41591|가동|0|12|0,4159110100,가동,12,가단지,59.5,3,2001,24000,,,가동",
                "apt_trade,41591,202610,1,2,11,매매,apt|41591|나동|1|7|3,4159110200,나동,산7-3,나단지,100,10,2010,52000,,,나동",
                "apt_trade,41591,202610,1,3,20,매매,apt|41591|가동|0|20|0,4159110100,가동,20,다단지,84,7,1999,33600,,,가동",
                "apt_rent,41591,202610,1,0,5,월세,apt|41591|가동|0|12|0,4159110100,가동,12,가단지,84.97,5,2001,,10000,50,가동",
                "apt_rent,41591,202610,1,1,6,전세,apt|41591|가동|0|12|0,4159110100,가동,12,가단지,59.5,2,2001,,30000,0,가동",
                "apt_rent,41591,202610,1,2,7,월세,apt|41591|가동|0|12|0,4159110100,가동,12,가단지,84,8,2001,,5000,100,가동",
                "rh_trade,41591,202610,1,0,4,매매,rh|41591|봉담읍 와우리|0|3|0,4159125021,봉담읍 와우리,3,라빌라,50,2,2015,20000,,,봉담읍 와우리");
        write(dir.resolve("history.csv"), String.join(",", History.HEADER),
                "4159000000,경기도 화성시,,,현존,4100000000,41_590",
                "4159010100,경기도 화성시 가동,,20260131,폐지,4159000000,41_590",
                "4159100000,경기도 화성시 만세구,20260201,,현존,4100000000,41_591",
                "4159110100,경기도 화성시 만세구 가동,20260201,,현존,4159100000,41_591",
                "4159110200,경기도 화성시 만세구 나동,20260201,,현존,4159100000,41_591",
                "4159125000,경기도 화성시 만세구 봉담읍,20260201,,현존,4159100000,41_591",
                "4159125021,경기도 화성시 만세구 봉담읍 와우리,20260201,,현존,4159125000,41_591");
        write(dir.resolve("validity.csv"), String.join(",", ValidityTable.HEADER),
                "41591,apt_trade,202609,41590,0,1,,true_zero", "41591,apt_trade,202609,41591,0,1,,true_zero",
                "41591,apt_trade,202610,41590,0,0,41591,one", "41591,apt_trade,202610,41591,4,1,,one");
        write(dir.resolve("juso.csv"), String.join(",", AddressBook.HEADER),
                "경기도 화성시 만세구 가로 1,1,1,T,1,4159110100,18000,경기도 화성시 만세구 가로 1,경기도,화성시 만세구,가동,,0,12,0,4159110100100120000000001");
        return new DataSet(TradeStore.load(dir.resolve("trades.csv"), dir.resolve("bundles.csv")),
                ValidityTable.load(dir.resolve("validity.csv")), History.load(dir.resolve("history.csv")),
                AddressBook.load(dir.resolve("juso.csv")));
    }

    static Listing listing(String key, long price) {
        Map<String, String> f = new LinkedHashMap<>();
        for (String n : Listing.NINE) {
            f.put(n, "");
        }
        f.put("stockForm", "1");
        f.put("stockType", "0");
        f.put("stockSellPrice", Long.toString(price));
        f.put("stockFeeMonth", "0");
        f.put("exclusiveArea", "84");
        f.put("currentFloor", "5");
        f.put("stockAddress", "18000^^^경기도 화성시 만세구 가로 1^^^");
        Map<String, Long> ev = new LinkedHashMap<>();
        ev.put("매물.가격", price);
        ev.put("매물.월세", 0L);
        ev.put("매물.면적", 84L);
        ev.put("매물.층", 5L);
        return new Listing(key, f, ev);
    }

    static Inputs.Digests digests(String code, String cards) {
        return digests(code, cards, "DEPS");
    }

    static Inputs.Digests digests(String code, String cards, String deps) {
        return digests(code, cards, deps, "EMPTY");
    }

    static Inputs.Digests digests(String code, String cards, String deps, String memory) {
        return new Inputs.Digests(code, "HARNESS", cards, "LISTS", memory, deps, Map.of("trades", "T1", "juso", "J1"));
    }

    static Inputs inputs(Path dir, Inputs.Digests d, List<Question> questions) throws IOException {
        return inputs(dir, d, questions, List.of(B0, ONE, B1), Memory.EMPTY);
    }

    static Inputs inputs(Path dir, Inputs.Digests d, List<Question> questions, List<Variant> variants, Memory memory) throws IOException {
        Map<String, Listing> listings = new LinkedHashMap<>();
        Map<String, ListingMeta> metas = new LinkedHashMap<>();
        String[][] rows = {{"L01", "연습"}, {"L02", "연습"}, {"L03", "본시험"}};
        for (String[] r : rows) {
            listings.put(r[0], listing(r[0], 350_000_000L));
            metas.put(r[0], new ListingMeta(r[0], "apt|41591|가동|0|12|0", "매매", "화성형", r[1]));
        }
        return new Inputs(data(dir), listings, metas, variants, questions, Cards.load(EXP.resolve("cards/v0")),
                IntentLists.load(EXP.resolve("intents/intent_lists_v0.json")), memory, d);
    }

    static Inputs inputs(Path dir) throws IOException {
        return inputs(dir, digests("CODE", "CARDS"), List.of(Q1, Q2, Q9));
    }

    /** 저장소의 pilot_v0.json을 바탕으로 고친 설정. */
    static RunSettings settings(Consumer<ObjectNode> edit) throws IOException {
        ObjectNode n = (ObjectNode) M.readTree(Files.readString(EXP.resolve("harness/settings/pilot_v0.json"), StandardCharsets.UTF_8));
        ObjectNode sel = (ObjectNode) n.get("select");
        sel.putArray("reps").add(1);
        edit.accept(n);
        return RunSettings.parse(n.toString());
    }

    static void variants(ObjectNode n, String... ids) {
        var a = ((ObjectNode) n.get("select")).putArray("variants");
        for (String id : ids) {
            a.add(id);
        }
    }

    static void questions(ObjectNode n, String... ids) {
        var a = ((ObjectNode) n.get("select")).putArray("questions");
        for (String id : ids) {
            a.add(id);
        }
    }

    static ObjectNode statsInput() {
        return M.createObjectNode().put("api", "apt_trade").put("region_code", "41591").put("start_month", "202610").put("end_month", "202610")
                .put("deal", "매매").put("dong", "").put("jibun", "");
    }

    static ObjectNode report() {
        ObjectNode r = M.createObjectNode().put("text", "이 매물은 주변보다 {가} {가.방향}.");
        r.putArray("stat_claims").addObject().put("slot", "나").put("value", 33741).put("unit", "만원").put("basis", "시군구")
                .put("start_month", "202610").put("end_month", "202610").put("sample_size", 4).put("provisional", true).put("evidence", "S1.기준");
        r.putArray("value_claims").addObject().put("slot", "가").put("value", 3.7).put("unit", "%").put("source", "계산").put("evidence", "C1.차이");
        r.putArray("context_claims");
        return r;
    }

    /** 문항 하나를 푸는 응답 셋: trade_stats → compare_prices → submit_report. */
    static List<FakeAnthropic.Reply> solve() {
        return List.of(
                FakeAnthropic.sse(List.of(FakeAnthropic.thinking("먼저 통계", "sig-1"), FakeAnthropic.toolUse("toolu_1", "trade_stats", statsInput())),
                        "tool_use", null),
                FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_2", "compare_prices",
                        M.createObjectNode().put("listing_evidence", "매물.가격").put("basis_evidence", "S1.기준"))), "tool_use", null),
                FakeAnthropic.sse(List.of(FakeAnthropic.toolUse("toolu_3", "submit_report", report())), "tool_use", null));
    }
}
