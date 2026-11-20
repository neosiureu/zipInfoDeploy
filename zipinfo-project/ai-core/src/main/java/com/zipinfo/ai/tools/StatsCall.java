package com.zipinfo.ai.tools;

import java.util.List;

/** trade_stats 부르기 하나: 받은 칸, 비교 기준(단지·법정동·시군구), 표본 수. ④가 통계 claim과 견준다. */
public record StatsCall(String id, String api, String code, String start, String end, String deal, String dong,
                        String jibun, String scope, int sample, List<String> months) {}
