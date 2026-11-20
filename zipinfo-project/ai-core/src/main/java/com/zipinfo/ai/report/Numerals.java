package com.zipinfo.ai.report;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 한글 수사 사전 v0(01 172줄, 순서 3 메모 4절 6). "수사+단위"를 센다. 모든 판에서 기록하고 ④ 판에서만 거절한다.
 * ⓪ 목록처럼 연습 전 판을 두고 연습에서만 고치며, 관문 3에 얼린다(상대 3a 검토 4).
 * 잘못 거는 것을 줄이려고 한 글자 한자어 수(삼·사·오 …)는 띄어 쓴 단위 앞에서만, '이'·'일'은 넣지 않는다(지시어·조사와 겹쳐서).
 */
public final class Numerals {
    static final String NATIVE = "한|두|세|네|석|넉|다섯|여섯|일곱|여덟|아홉|열|스무|스물|서른|마흔|쉰|반";
    static final String SINO_COMPOUND = "[일이삼사오육칠팔구][십백천만억][일이삼사오육칠팔구십백천만억]*|[십백천만억][일이삼사오육칠팔구십백천만억]+";
    static final String SINO_SINGLE = "삼|사|오|육|칠|팔|구";
    static final String UNIT = "배|곳|건|개월|개|달|년|층|평|퍼센트|프로|만원|억원|원|채|가구|세대";
    public static final Pattern PATTERN = Pattern.compile(
            "(?<![가-힣])(?:(?:" + NATIVE + "|" + SINO_COMPOUND + ")\\s?|(?:" + SINO_SINGLE + ")\\s)(?:" + UNIT + ")");

    private Numerals() {}

    public static List<String> find(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = PATTERN.matcher(text);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }
}
