package com.zipinfo.ai.model;

/**
 * 프롬프트 캐시 정책(01 264·342줄, 설계안 10.3: 시운전에서 고른다). 캐시 지점 둘과 유지 시간 하나다.
 * - system: 시스템 글 블록 끝에 지점을 둔다. 앞부분(도구 정의와 시스템 글)이 같은 판의 다른 실행에서도 읽힌다.
 * - conversation: 요청 맨 위에 cache_control을 둔다(자동 캐시). 지점이 대화의 마지막 블록에 놓이고, 대화가 자라면 따라 옮겨 간다.
 * - ttl: "5m"이나 "1h". 두 지점이 같은 값을 쓴다(1시간 지점은 5분 지점보다 앞에 와야 하는데, 같은 값이면 그 걱정이 없다).
 * NONE이면 cache_control을 보내지 않고, 시스템 글도 글 하나로 보낸다(순서 3b의 요청 꼴 그대로).
 */
public record CachePolicy(boolean system, boolean conversation, String ttl) {
    public static final CachePolicy NONE = new CachePolicy(false, false, "5m");

    public CachePolicy {
        if (!"5m".equals(ttl) && !"1h".equals(ttl)) {
            throw new IllegalArgumentException("캐시 유지 시간은 5m이나 1h다: " + ttl);
        }
    }
}
