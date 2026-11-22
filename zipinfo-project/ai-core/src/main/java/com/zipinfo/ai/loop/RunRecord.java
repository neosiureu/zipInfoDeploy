package com.zipinfo.ai.loop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 실행 하나의 기록. 하네스(순서 6)가 JSONL로 쓴다. 사건마다 종류와 칸 몇 개.
 * 하네스는 onEvent로 사건이 생길 때마다 받아 바로 파일에 쓴다(실행 중에 멈춰도 그때까지의 기록이 남는다).
 */
public final class RunRecord {
    public enum Outcome {
        SUBMITTED, FAILED_NO_SUBMIT, FAILED_MAX_TOKENS, FAILED_CONTEXT_WINDOW, FAILED_REFUSAL, FAILED_TURN_LIMIT,
        STOP_REASONING_EXTRACTION, STOP_OTHER_STOP_REASON
    }

    private final List<Map<String, Object>> events = new ArrayList<>();
    private Outcome outcome;
    private String answer;
    private int turns;
    private Consumer<Map<String, Object>> listener = e -> { };

    /** 사건이 더해질 때마다 부른다(더한 뒤에). 던진 예외는 고리 밖으로 그대로 나간다. */
    public void onEvent(Consumer<Map<String, Object>> l) {
        this.listener = l;
    }

    void add(String type, Object... kv) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("type", type);
        for (int i = 0; i < kv.length; i += 2) {
            e.put((String) kv[i], kv[i + 1]);
        }
        events.add(e);
        listener.accept(e);
    }

    void end(Outcome o, String answer) {
        this.outcome = o;
        this.answer = answer;
    }

    void turn() { turns++; }

    public List<Map<String, Object>> events() { return events; }
    public Outcome outcome() { return outcome; }
    public String answer() { return answer; }
    public int turns() { return turns; }
}
