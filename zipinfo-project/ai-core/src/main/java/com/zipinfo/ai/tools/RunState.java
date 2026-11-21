package com.zipinfo.ai.tools;

import com.zipinfo.ai.data.Listing;
import com.zipinfo.ai.data.ListingMeta;
import com.zipinfo.ai.num.Q;
import java.text.Normalizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 실행 하나의 상태: 매물, 질문, 결과 ID 차례, 근거 ID 목록, 읽은 카드, 의도 통과. 고리가 실행마다 새로 만든다. */
public final class RunState {
    private final Listing listing;
    private final ListingMeta meta;
    private final Map<String, Integer> counters = new HashMap<>();
    private final Map<String, Evidence> evidence = new LinkedHashMap<>();
    private final Set<String> cardsRead = new LinkedHashSet<>();
    private String question = "";
    private boolean intentsPassed;

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
        return Collections.unmodifiableMap(evidence);
    }

    /** read_card로 카드를 읽었다(설계안 7.4의 ③이 본다). */
    public void cardRead(String id) {
        cardsRead.add(id);
    }

    /** 이 실행에서 읽은 카드 ID(읽은 차례). */
    public Set<String> cardsRead() {
        return Collections.unmodifiableSet(cardsRead);
    }

    /** 이 실행의 질문 글. 고리가 첫 사용자 글을 만들 때 넣는다. ⓪이 의도토큰의 원어를 이 글과 대 본다(설계안 7.2). */
    public void question(String q) {
        this.question = q;
    }

    public String question() {
        return question;
    }

    /** declare_intents가 통과했다(⓪, 7.2). 한 번 통과하면 그 실행 끝까지 간다. 부품이 아니라 도구가 바꾼다(7.1). */
    public void markIntentsPassed() {
        intentsPassed = true;
    }

    public boolean intentsPassed() {
        return intentsPassed;
    }
}
