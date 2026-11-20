package com.zipinfo.ai.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 매물 입력(설계안 5.4, 순서 2 listings_input_v1.json): 아홉 칸과 근거 넷. 단지ID·층·나눔은 여기에 없다. */
public record Listing(String key, Map<String, String> fields, Map<String, Long> evidence) {
    public static final List<String> NINE = List.of("stockName", "stockAddress", "stockForm", "stockType",
            "stockSellPrice", "stockFeeMonth", "exclusiveArea", "currentFloor", "useApprovalDate");
    public static final List<String> EVIDENCE = List.of("매물.가격", "매물.월세", "매물.면적", "매물.층");

    public static List<Listing> load(Path json) throws IOException {
        JsonNode root = new ObjectMapper().readTree(Files.readString(json));
        List<Listing> out = new ArrayList<>();
        for (JsonNode it : root) {
            Map<String, String> f = new LinkedHashMap<>();
            JsonNode fields = it.get("fields");
            if (fields.size() != NINE.size()) {
                throw new IOException("매물 칸이 아홉이 아니다");
            }
            for (String name : NINE) {
                f.put(name, fields.get(name).asText());
            }
            Map<String, Long> ev = new LinkedHashMap<>();
            for (String name : EVIDENCE) {
                ev.put(name, it.get("evidence").get(name).asLong());
            }
            out.add(new Listing(it.get("key").asText(), f, ev));
        }
        return out;
    }

    /** 거래 갈래: stockType 0 매매, 1 전세, 2 월세(06 규칙 6.1). */
    public String deal() {
        return switch (fields.get("stockType")) {
            case "0" -> "매매";
            case "1" -> "전세";
            case "2" -> "월세";
            default -> throw new IllegalStateException("stockType 값");
        };
    }
}
