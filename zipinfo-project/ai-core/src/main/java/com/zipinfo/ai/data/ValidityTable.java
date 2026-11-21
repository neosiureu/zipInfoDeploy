package com.zipinfo.ai.data;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** 유효성 표(설계안 5.2). 영역(매물의 현재 시군구) × API × 달마다 후보 코드의 판정과 처방. 후보가 아닌 코드는 늘 무효, 처방은 영역 코드다. */
public final class ValidityTable {
    public static final List<String> HEADER = List.of("area", "api", "ym", "code", "count", "valid", "prescription", "case");

    public record Cell(String code, int count, boolean valid, String prescription, String kase) {}

    private final Map<String, List<Cell>> cells = new HashMap<>();
    private final Map<String, Set<String>> candidates = new HashMap<>();

    public static ValidityTable load(Path csv) throws IOException {
        ValidityTable t = new ValidityTable();
        Csv.read(csv, HEADER, r -> {
            t.cells.computeIfAbsent(r.get(0) + "|" + r.get(1) + "|" + r.get(2), k -> new ArrayList<>())
                    .add(new Cell(r.get(3), Integer.parseInt(r.get(4)), r.get(5).equals("1"), r.get(6), r.get(7)));
            t.candidates.computeIfAbsent(r.get(0), k -> new TreeSet<>()).add(r.get(3));
        });
        return t;
    }

    /** 영역의 후보 코드(모든 API·달을 합침). 둘 이상이면 개편 지역이다(③, 설계안 7.4). 표에 없는 영역이면 빈 모음. */
    public Set<String> candidates(String area) {
        return Collections.unmodifiableSet(candidates.getOrDefault(area, Set.of()));
    }

    /** (유효, 처방). 처방은 무효일 때만 쓴다. */
    public record Verdict(boolean valid, String prescription) {}

    public Verdict judge(String area, String api, String ym, String code) {
        List<Cell> list = cells.get(area + "|" + api + "|" + ym);
        if (list == null) {
            throw new IllegalArgumentException("유효성 표에 없는 영역·API·달");
        }
        for (Cell c : list) {
            if (c.code().equals(code)) {
                return new Verdict(c.valid(), c.valid() ? "" : c.prescription());
            }
        }
        return new Verdict(false, area);
    }
}
