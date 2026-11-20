package com.zipinfo.ai.data;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 법정동 이력(3단계 codego_history_v1.csv). 받은 창(2023-11-01~2026-10-31) 안에 그 시군구 코드 아래 있었던 법정동 경로를 낸다(picker History와 같은 뜻). */
public final class History {
    public static final List<String> HEADER = List.of("code", "name", "created", "abolished", "status", "parent", "page");
    public static final String WIN_START = "20231101";
    public static final String WIN_END = "20261031";

    private final Map<String, Set<String>> paths = new HashMap<>();
    private final Map<String, Set<String>> parents = new HashMap<>();

    public static History load(Path csv) throws IOException {
        Map<String, String> sggName = new HashMap<>();
        Map<String, String[]> dongs = new HashMap<>();
        History h = new History();
        Csv.read(csv, HEADER, r -> {
            String code = r.get(0);
            if (code.endsWith("00000")) {
                sggName.put(code.substring(0, 5), r.get(1));
            } else {
                dongs.put(code, new String[] {r.get(1), r.get(2), r.get(3)});
            }
        });
        for (Map.Entry<String, String[]> e : dongs.entrySet()) {
            String code = e.getKey();
            String[] v = e.getValue();
            String sgg = sggName.get(code.substring(0, 5));
            if (sgg == null || !v[0].startsWith(sgg + " ")) {
                throw new IOException("시군구 행이 없거나 이름이 시군구 이름으로 시작하지 않는 법정동이 있다");
            }
            boolean inWindow = (v[1].isEmpty() || v[1].compareTo(WIN_END) <= 0) && (v[2].isEmpty() || v[2].compareTo(WIN_START) > 0);
            if (inWindow) {
                h.paths.computeIfAbsent(code.substring(0, 5), k -> new HashSet<>()).add(v[0].substring(sgg.length() + 1));
            }
        }
        for (Map.Entry<String, Set<String>> e : h.paths.entrySet()) {
            for (String p : e.getValue()) {
                int sp = p.indexOf(' ');
                if (sp > 0) {
                    h.parents.computeIfAbsent(e.getKey(), k -> new HashSet<>()).add(p.substring(0, sp));
                }
            }
        }
        return h;
    }

    /** 창 안에서 그 아래에 리("읍면 리")가 있었던 읍·면 이름인가(순서 3a b판, 상대 검토 1). */
    public boolean hasRiUnder(String sgg5, String path) {
        return parents.getOrDefault(sgg5, Set.of()).contains(path);
    }

    public boolean hasDong(String sgg5, String path) {
        return paths.getOrDefault(sgg5, Set.of()).contains(path);
    }
}
