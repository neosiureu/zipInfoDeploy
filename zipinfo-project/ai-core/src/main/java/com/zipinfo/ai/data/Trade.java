package com.zipinfo.ai.data;

import com.zipinfo.ai.num.Q;

/** 재생 저장소 한 줄(순서 2 replay_trades_v1.csv). 금액은 만원 정수, 면적은 받은 소수 글을 분수로. */
public record Trade(String api, String resp, String ym, int day, String deal, String complexId, String cur10,
                    String path, int mtn, int bon, int bu, Q area, Integer floor, Long amount, Long deposit,
                    Long monthlyRent) {

    public String sgg() {
        return complexId.split("\\|")[1];
    }
}
