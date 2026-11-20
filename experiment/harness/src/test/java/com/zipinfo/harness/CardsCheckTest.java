package com.zipinfo.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.zipinfo.ai.cards.Card;
import com.zipinfo.ai.cards.Cards;
import com.zipinfo.ai.data.AddressBook;
import com.zipinfo.ai.data.Listing;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 연습 전 카드(experiment/cards/v0, 설계안 8절)의 글을 본다(계획 3판 순서 4 시험).
 * 카드는 장치를 말하지 않고, 사고 과정을 적게 하지 않으며(01 209줄), 달 숫자와 매물 값이 없다. 찾기 낱말이 질문의 흔한 뜻을 잡는지도 본다.
 */
class CardsCheckTest {
    static final Path EXP = Path.of("..").toAbsolutePath().normalize();
    static final Pattern MONTH = Pattern.compile("20[0-9]{2}(0[1-9]|1[0-2])");
    static final Pattern DEVICE_MARK = Pattern.compile("[⓪①②③④⑤]");
    static final List<String> BANNED = List.of(
            "생각", "사고 과정", "단계별", "추론",
            "장치", "거절", "의도", "잠금", "게이트", "해마", "좋아요", "반사", "declare_intents", "search_cards", "read_card");

    static Cards cards() throws Exception {
        return Cards.load(EXP.resolve("cards/v0"));
    }

    static String raw(Card c) throws Exception {
        return Files.readString(EXP.resolve("cards/v0/" + c.id() + ".md"), StandardCharsets.UTF_8);
    }

    @Test
    void tenCardsInTheDesignedOrder() throws Exception {
        Cards cs = cards();
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            ids.add(String.format("K%02d", i));
        }
        assertEquals(ids, cs.all().stream().map(Card::id).toList());
        // ③이 읽으라고 할 카드(설계안 7.4): 지역 코드 카드와 단위·면적 카드
        assertTrue(cs.get("K02").title().contains("지역 코드"));
        assertTrue(cs.get("K06").title().contains("면적"));
        for (Card c : cs.all()) {
            assertTrue(c.log().isEmpty(), c.id() + ": 연습 전 판의 기록 층은 비어 있다");
        }
    }

    @Test
    void cardsDoNotTalkAboutDevicesOrReasoningOrMonths() throws Exception {
        for (Card c : cards().all()) {
            String t = raw(c);
            for (String bad : BANNED) {
                assertFalse(t.contains(bad), c.id() + ": " + bad);
            }
            assertFalse(DEVICE_MARK.matcher(t).find(), c.id() + ": 부품 표시");
            assertFalse(MONTH.matcher(t).find(), c.id() + ": 달 숫자(YYYYMM)");
        }
    }

    /** 매물 이름·주소·가격이 카드에 없다. 실패해도 매물 키만 보인다. */
    @Test
    void cardsCarryNoListingValues() throws Exception {
        Path in = EXP.resolve("prep/out/listings_input_v1.json");
        assumeTrue(Files.exists(in), "prep build 결과가 있을 때만 돈다");
        List<String> texts = new ArrayList<>();
        for (Card c : cards().all()) {
            texts.add(raw(c).replace(" ", ""));
        }
        int checked = 0;
        for (Listing l : Listing.load(in)) {
            List<String> needles = new ArrayList<>();
            needles.add(l.fields().get("stockName").replace(" ", ""));
            needles.add(AddressBook.clean(l.fields().get("stockAddress")).replace(" ", ""));
            for (String f : List.of("stockSellPrice", "stockFeeMonth")) {
                String v = l.fields().get(f);
                if (v.length() >= 6) {
                    needles.add(v);
                }
            }
            for (String n : needles) {
                if (n.length() < 2) {
                    continue;
                }
                for (String t : texts) {
                    assertFalse(t.contains(n), "매물 " + l.key() + "의 칸 글이 카드에 있다");
                }
                checked++;
            }
        }
        assertTrue(checked >= 150, "대 본 글 수 " + checked);
        System.out.println("카드 검사: 카드 " + texts.size() + "장, 매물 칸 글 " + checked + "개와 대 봄, 겹친 것 0");
    }

    /** 찾기 낱말이 질문의 흔한 뜻을 잡는다(⓪과 search_cards가 같은 규칙을 쓴다). */
    @Test
    void tagsCatchCommonIntents() throws Exception {
        Cards cs = cards();
        Object[][] cases = {
                {"compare the price with nearby trades", "", "K04"},
                {"", "주변 실거래보다 비싼지", "K04"},
                {"", "같은 단지 거래로만", "K04"},
                {"", "최근 3개월 거래로", "K05"},
                {"recent six months", "", "K05"},
                {"", "보증금으로 환산하면", "K07"},
                {"convert monthly rent to deposit", "", "K07"},
                {"", "거래가 몇 건", "K08"},
                {"count transactions", "", "K08"},
                {"convert area to pyeong", "", "K06"},
                {"region code", "", "K02"},
                {"", "아파트 매매", "K03"},
                // 상대 3b·4 검토(물음 2): 받침이 붙은 꼴과 다른 말, 영어 복수·파생
                {"", "비싼 편이야", "K04"},
                {"", "싼 편인가요", "K04"},
                {"", "비쌌던", "K04"},
                {"", "견준", "K04"},
                {"", "저렴한", "K04"},
                {"", "같은 동 거래로만", "K04"},
                {"", "몇 번 거래", "K08"},
                {"how many trades", "", "K08"},
                {"is it overpriced", "", "K04"},
                {"prices nearby", "", "K04"},
        };
        for (Object[] c : cases) {
            List<String> ids = cs.find((String) c[0], (String) c[1]).stream().map(Card::id).toList();
            assertTrue(ids.contains((String) c[2]), c[0] + "|" + c[1] + " → " + ids);
        }
        // 보고서 카드가 "알아보고"·"비교해 보고"를 잡지 않는다
        assertFalse(cs.find("", "알아보고 비교해 보고").stream().anyMatch(c -> c.id().equals("K09")));
    }
}
