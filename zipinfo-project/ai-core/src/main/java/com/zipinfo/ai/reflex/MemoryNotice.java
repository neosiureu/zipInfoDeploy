package com.zipinfo.ai.reflex;

import com.zipinfo.ai.data.ListingMeta;
import com.zipinfo.ai.loop.Hook;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ⑤ 해마의 안내 자리(설계안 7.6). 첫 사용자 글을 만들 때 매물의 현재 시군구와 API로 기억을 찾아, 많아야 K개를 한 줄씩 질문 앞에 둔다.
 * 지역 코드 길은 "(같은 시군구, …)", api가 바뀐 길은 "(같은 시군구·API, …)"로 적는다(5c 메모 4절 (B)).
 * 찾은 것이 없으면 통과다(안내 없음). 판정만 하고 실행 상태를 바꾸지 않는다(7.1). 좋아요 차례는 Setup이 카드 도구와 ⓪에 넣는다.
 */
public final class MemoryNotice implements Hook {
    private final Memory memory;

    public MemoryNotice(Memory memory) {
        this.memory = memory;
    }

    @Override
    public String name() {
        return Part.MEMORY.symbol();
    }

    @Override
    public boolean at(Point p) {
        return p == Point.NOTICE;
    }

    @Override
    public Decision decide(Point p, Context c) {
        if (p != Point.NOTICE) {
            return Decision.PASS;
        }
        ListingMeta m = c.state().meta();
        List<Memory.Item> found = memory.lookup(m.area(), m.api());
        if (found.isEmpty()) {
            return Decision.PASS;
        }
        return Decision.notice(found.stream().map(MemoryNotice::line).collect(Collectors.joining("\n")));
    }

    /** 안내 한 줄. */
    static String line(Memory.Item i) {
        String scope = i.api().isEmpty() ? "같은 시군구" : "같은 시군구·API";
        return "지난 실행에서 막혔다 풀린 길(" + scope + ", " + i.kind() + "): " + i.blocked() + " → " + i.solved();
    }
}
