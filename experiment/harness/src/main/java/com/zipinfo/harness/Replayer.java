package com.zipinfo.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 이어 돌릴 때 고리의 사건을 받는다(RunRecord.onEvent). 기록에 이미 있는 사건이면 기록과 대 보고, 새 사건이면 파일에 쓴다.
 * 대 볼 때는 시각(at)과 시간(millis)만 뺀다. 도구 결과·장치 판정·응답 원문까지 같아야 한다. 다르면 Mismatch로 멈춘다
 * (자료나 코드가 지문 밖에서 달라졌거나, 되살리기가 틀린 것이다).
 */
final class Replayer implements Consumer<Map<String, Object>> {
    static final class Mismatch extends RuntimeException {
        Mismatch(String message) {
            super(message);
        }
    }

    private final List<JsonNode> recorded;
    private final Consumer<Map<String, Object>> out;
    private int i;

    Replayer(List<JsonNode> recorded, Consumer<Map<String, Object>> out) {
        this.recorded = recorded;
        this.out = out;
    }

    @Override
    public void accept(Map<String, Object> e) {
        if (i < recorded.size()) {
            JsonNode want = strip(recorded.get(i));
            JsonNode got;
            try {
                got = strip(Json.M.readTree(Json.write(e)));
            } catch (IOException x) {
                throw new IllegalStateException(x);
            }
            if (!want.equals(got)) {
                throw new Mismatch((i + 1) + "번째 고리 사건(" + want.path("type").asText() + ")이 기록과 다르다");
            }
            i++;
            return;
        }
        out.accept(e);
    }

    /** 기록에 남은 고리 사건을 다 맞춰 봤다. */
    boolean done() {
        return i == recorded.size();
    }

    private static JsonNode strip(JsonNode n) {
        ObjectNode c = n.deepCopy();
        c.remove("at");
        c.remove("millis");
        return c;
    }
}
