package com.zipinfo.ai.tools;

import com.zipinfo.ai.data.Listing;
import com.zipinfo.ai.data.ListingMeta;
import com.zipinfo.ai.num.Q;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** 실행 하나의 상태: 매물, 결과 ID 차례, 근거 ID 목록. 고리가 실행마다 새로 만든다. */
public final class RunState {
    private final Listing listing;
    private final ListingMeta meta;
    private final Map<String, Integer> counters = new HashMap<>();
    private final Map<String, Evidence> evidence = new LinkedHashMap<>();

    public RunState(Listing listing, ListingMeta meta) {
        if (!listing.key().equals(meta.key())) {
            throw new IllegalArgumentException("매물 키가 다르다");
        }
        this.listing = listing;
        this.meta = meta;
        Map<String, Long> ev = listing.evidence();
        put(new Evidence("매물.가격", Evidence.Kind.LISTING_PRICE, Q.of(ev.get("매물.가격")), "원", null));
        put(new Evidence("매물.월세", Evidence.Kind.LISTING_MONTHLY, Q.of(ev.get("매물.월세")), "원", null));
        put(new Evidence("매물.면적", Evidence.Kind.LISTING_AREA, Q.of(ev.get("매물.면적")), "㎡", null));
        put(new Evidence("매물.층", Evidence.Kind.LISTING_FLOOR, Q.of(ev.get("매물.층")), "층", null));
    }

    public Listing listing() { return listing; }
    public ListingMeta meta() { return meta; }

    /** 다음 결과 ID(J1, S1, C1, R1, A1 …). */
    String nextId(String prefix) {
        int n = counters.merge(prefix, 1, Integer::sum);
        return prefix + n;
    }

    void put(Evidence e) {
        evidence.put(e.id(), e);
    }

    /** 근거 ID를 NFC·앞뒤 빈칸 정리 뒤 찾는다. 없으면 null. */
    public Evidence find(String id) {
        return evidence.get(Normalizer.normalize(id, Normalizer.Form.NFC).strip());
    }

    public Map<String, Evidence> evidence() {
        return java.util.Collections.unmodifiableMap(evidence);
    }
}
