package com.zipinfo.ai.loop;

import com.zipinfo.ai.data.Listing;

/**
 * 시스템 글, 매물 블록, 재촉 한 줄(설계안 6.4). 모든 판에 같은 글이다. B0+만 시스템 글 뒤에 얼린 카드 전문을 붙인다(순서 4).
 * 시스템 글은 할 일과 제출 도구만 말한다. 사고 과정을 적게 하는 말을 넣지 않는다(01 209줄).
 * 카드가 가르치는 것(일의 차례, 지역 코드, API 고르기, 기본 12달·잠정, 보고서 쓰는 법 등)도 넣지 않는다. 제출 꼴은 submit_report 설명에 있다.
 */
public final class Prompts {
    public static final String SYSTEM = String.join("\n",
            "당신은 부동산 매물 사이트 집인포의 도우미다. 사용자가 매물 하나에 대해 묻는 것에 국토교통부 실거래 자료로 답한다.",
            "자료는 도구로 얻고, 답은 submit_report 도구로 낸다.");

    public static final String NUDGE = "답을 submit_report 도구로 내 주세요.";

    private Prompts() {}

    /** 첫 사용자 글: 질문과 매물 블록(아홉 칸과 근거 ID). 코드 값(stockForm·stockType)에는 뜻을 곁들인다. */
    public static String firstMessage(String question, Listing l) {
        var f = l.fields();
        String form = switch (f.get("stockForm")) {
            case "1" -> "아파트";
            case "2" -> "연립다세대";
            case "3" -> "오피스텔";
            default -> "";
        };
        return String.join("\n",
                question,
                "",
                "[매물]",
                "stockName(이름): " + f.get("stockName"),
                "stockAddress(주소, 우편번호^^^도로명주소^^^): " + f.get("stockAddress"),
                "stockForm(주택 종류): " + f.get("stockForm") + " (" + form + ")",
                "stockType(거래): " + f.get("stockType") + " (" + l.deal() + ")",
                "stockSellPrice(매매가·전세금·보증금, 원) [근거 매물.가격]: " + f.get("stockSellPrice"),
                "stockFeeMonth(월세, 원) [근거 매물.월세]: " + f.get("stockFeeMonth"),
                "exclusiveArea(전용면적, ㎡) [근거 매물.면적]: " + f.get("exclusiveArea"),
                "currentFloor(층) [근거 매물.층]: " + f.get("currentFloor"),
                "useApprovalDate(사용승인일): " + f.get("useApprovalDate"));
    }
}
