package com.zipinfo.ai.data;

import java.io.IOException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 주소 재생 표(설계안 5.3): 순서 2 freeze-juso가 얼린 juso_road_v1.csv. 얼린 검색어 밖은 SNAPSHOT_MISS다. */
public final class AddressBook {
    public static final List<String> FIELDS = List.of("admCd", "zipNo", "roadAddrPart1", "siNm", "sggNm", "emdNm", "liNm",
            "mtYn", "lnbrMnnm", "lnbrSlno", "bdMgtSn");
    public static final List<String> HEADER;
    // 파이썬 re의 \s처럼 유니코드 빈칸을 모두 본다(순서 2 listings.clean_query와 같게).
    private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern ZIP_FORM = Pattern.compile("^[0-9]{5}\\^\\^\\^(.*?)\\^\\^\\^$");

    static {
        List<String> h = new ArrayList<>(List.of("query", "page", "seq", "called_at", "total"));
        h.addAll(FIELDS);
        HEADER = List.copyOf(h);
    }

    private final Map<String, List<Map<String, String>>> frozen = new LinkedHashMap<>();

    public static AddressBook load(Path csv) throws IOException {
        AddressBook b = new AddressBook();
        Map<String, Integer> totals = new LinkedHashMap<>();
        Csv.read(csv, HEADER, r -> {
            String q = r.get(0);
            totals.put(q, Integer.parseInt(r.get(4)));
            List<Map<String, String>> list = b.frozen.computeIfAbsent(q, k -> new ArrayList<>());
            if (!r.get(2).isEmpty()) {
                Map<String, String> m = new LinkedHashMap<>();
                for (int i = 0; i < FIELDS.size(); i++) {
                    m.put(FIELDS.get(i), r.get(5 + i));
                }
                list.add(m);
            }
        });
        for (Map.Entry<String, Integer> e : totals.entrySet()) {
            if (b.frozen.get(e.getKey()).size() != e.getValue()) {
                throw new IOException("결과 수와 적힌 결과 개수가 다른 검색어가 있다");
            }
        }
        return b;
    }

    /** 주소 얼린 파일이 아직 없을 때(순서 2-4 전) 쓰는 빈 표. 모든 검색이 SNAPSHOT_MISS다. */
    public static AddressBook empty() {
        return new AddressBook();
    }

    /** 5.3 도구 쪽 다듬기: NFC, 앞뒤·겹 빈칸 정리, 앞의 "우편번호^^^"와 뒤의 "^^^"를 뗀다. */
    public static String clean(String query) {
        String s = SPACES.matcher(Normalizer.normalize(query, Normalizer.Form.NFC)).replaceAll(" ").strip();
        var m = ZIP_FORM.matcher(s);
        if (m.matches()) {
            s = SPACES.matcher(m.group(1)).replaceAll(" ").strip();
        }
        return s;
    }

    /** 얼린 결과 목록. 얼리지 않은 검색어면 null. */
    public List<Map<String, String>> lookup(String query) {
        return frozen.get(clean(query));
    }

    public int size() {
        return frozen.size();
    }
}
