package com.zipinfo.ai.data;

import com.zipinfo.ai.num.Q;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 거래 재생 저장소(설계안 5.1). (API, 코드, 달) 묶음마다 받은 건수와 다듬은 거래를 든다. 목록에 없는 묶음은 SNAPSHOT_MISS다. */
public final class TradeStore {
    public static final List<String> TRADE_HEADER = List.of("api", "resp", "ym", "page", "pos", "day", "deal",
            "complex_id", "cur10", "path", "jibun", "name", "area", "floor", "build_year", "amount", "deposit",
            "monthly_rent", "raw_umd");
    public static final List<String> BUNDLE_HEADER = List.of("api", "resp", "ym", "items", "kept", "dropped",
            "unresolved");

    private final Map<String, Integer> items = new HashMap<>();
    private final Map<String, List<Trade>> trades = new HashMap<>();

    public static String key(String api, String code, String ym) {
        return api + "|" + code + "|" + ym;
    }

    public static TradeStore load(Path tradesCsv, Path bundlesCsv) throws IOException {
        TradeStore s = new TradeStore();
        Csv.read(bundlesCsv, BUNDLE_HEADER, r -> s.items.put(key(r.get(0), r.get(1), r.get(2)), Integer.parseInt(r.get(3))));
        Csv.read(tradesCsv, TRADE_HEADER, r -> {
            String k = key(r.get(0), r.get(1), r.get(2));
            s.trades.computeIfAbsent(k, x -> new ArrayList<>()).add(s.parse(r));
        });
        for (String k : s.trades.keySet()) {
            if (!s.items.containsKey(k)) {
                throw new IOException("묶음 목록에 없는 거래 묶음이 있다");
            }
        }
        return s;
    }

    /** 같은 글(API, 코드, 달, 단지ID 등)을 한 벌만 두어 메모리를 줄인다. */
    private final Map<String, String> pool = new HashMap<>();

    private String in(String s) {
        String t = pool.putIfAbsent(s, s);
        return t == null ? s : t;
    }

    Trade parse(List<String> r) {
        String[] id = r.get(7).split("\\|", -1);
        return new Trade(in(r.get(0)), in(r.get(1)), in(r.get(2)), Integer.parseInt(r.get(5)), in(r.get(6)), in(r.get(7)), in(r.get(8)),
                in(r.get(9)), Integer.parseInt(id[3]), Integer.parseInt(id[4]), Integer.parseInt(id[5]), Q.parse(r.get(12)),
                r.get(13).isEmpty() ? null : Integer.valueOf(r.get(13)),
                r.get(15).isEmpty() ? null : Long.valueOf(r.get(15)),
                r.get(16).isEmpty() ? null : Long.valueOf(r.get(16)),
                r.get(17).isEmpty() ? null : Long.valueOf(r.get(17)));
    }

    /** 묶음이 스냅샷에 있으면 받은 건수(manifest totalCount), 없으면 null. */
    public Integer received(String api, String code, String ym) {
        return items.get(key(api, code, ym));
    }

    public List<Trade> trades(String api, String code, String ym) {
        return trades.getOrDefault(key(api, code, ym), List.of());
    }

    public int bundleCount() {
        return items.size();
    }
}
