package com.zipinfo.ai.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 모델에 넘기지 않는 매물 정보(얼린 매물 파일의 단지ID·거래·층·나눔). 키는 파일의 줄 차례로 L01~L75다(순서 2 listings.load와 같다). */
public record ListingMeta(String key, String complexId, String deal, String layer, String split) {

    public String area() {
        return complexId.split("\\|")[1];
    }

    public String kind() {
        return complexId.split("\\|")[0];
    }

    /** 매물의 실거래 API: 종류 뒤에 매매면 _trade, 전세·월세면 _rent(피커 clean.py의 API_OF와 같다). ⑤의 열쇠에 쓴다(5c). */
    public String api() {
        return kind() + (deal.equals("매매") ? "_trade" : "_rent");
    }

    public static List<ListingMeta> load(Path csv) throws IOException {
        List<ListingMeta> out = new ArrayList<>();
        try (var r = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            List<String> h = Csv.next(r);
            if (h == null) {
                throw new IOException("빈 매물 파일");
            }
            int ci = h.indexOf("complex_id"), di = h.indexOf("deal"), li = h.indexOf("layer"), si = h.indexOf("split");
            if (ci < 0 || di < 0 || li < 0 || si < 0) {
                throw new IOException("매물 파일에 없는 칸이 있다");
            }
            List<String> row;
            int n = 0;
            while ((row = Csv.next(r)) != null) {
                n++;
                out.add(new ListingMeta(String.format("L%02d", n), row.get(ci), row.get(di), row.get(li), row.get(si)));
            }
        }
        return out;
    }
}
