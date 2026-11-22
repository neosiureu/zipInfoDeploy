package com.zipinfo.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.zipinfo.ai.model.ModelClient;
import com.zipinfo.ai.model.ModelError;
import com.zipinfo.ai.model.ModelSession;
import com.zipinfo.ai.model.ModelTurn;
import com.zipinfo.ai.model.UserPart;
import com.zipinfo.ai.tools.ToolSpecs;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 실행 하나의 모델 부르기(01 194~202줄). 고리(QuestionLoop)와 실제 모델 사이에 선다.
 * <ul>
 * <li>되살리기: 기록에 남은 응답을 차례대로 replay로 덧붙인다(API에 묻지 않는다). 다 쓴 뒤에야 실제로 보낸다.
 *     실제로 보내기 전에 기록에 남은 고리 사건을 다 맞춰 봤는지 본다(아니면 기록이 어긋났다).</li>
 * <li>재시도(RETRY 갈래): retry-after가 있으면 그만큼, 없으면 base × 2^(n−1)초(많아야 max)를 기다려 같은 요청을 resend로 다시 보낸다.
 *     실패한 시도마다 attempt 사건을 남긴다. max_attempts번째 시도도 실패하면 그 오류를 던진다(하네스가 멈춤으로 적고 나중에 이어 돈다).
 *     RESUME_LATER·STOP_AND_LOOK 갈래는 attempt를 남기고 바로 던진다.</li>
 * <li>실제로 보내기 전마다 멈춤 신호와 비용 상한을 본다(Control.beforeLive).</li>
 * <li>받은 응답마다 캐시 있음 비용을 셈해 쓴 돈에 더한다.</li>
 * </ul>
 * 다시 보낸 요청과 기다린 시간은 응답의 millis에 들지 않는다(01 213줄). millis는 성공한 시도 하나의 시간이다.
 */
final class RunClient implements ModelClient {
    private final ModelClient live;
    private final List<RunFile.Recorded> recorded;
    private final Replayer replayer;
    private final Control control;
    private final RunSettings.Retry retry;
    private final Consumer<Map<String, Object>> out;
    private final Prices prices;
    private final String model;
    private final String ttl;
    private int next;
    private int liveResponses;

    RunClient(ModelClient live, List<RunFile.Recorded> recorded, Replayer replayer, Control control, RunSettings.Retry retry,
              Consumer<Map<String, Object>> out, Prices prices, String model, String ttl) {
        this.live = live;
        this.recorded = recorded;
        this.replayer = replayer;
        this.control = control;
        this.retry = retry;
        this.out = out;
        this.prices = prices;
        this.model = model;
        this.ttl = ttl;
    }

    @Override
    public ModelSession open(String system, List<ToolSpecs.Spec> tools) {
        return new Session(live.open(system, tools));
    }

    /** 이번에 실제로 받은 응답 수(되살린 것은 빼고). */
    int liveResponses() {
        return liveResponses;
    }

    /** 기록에 남은 응답을 다 되살렸다. */
    boolean replayDone() {
        return next == recorded.size();
    }

    /** 실패한 다음 기다릴 시간(밀리초). n은 몇 번째 시도가 실패했는지(1부터). */
    long waitMillis(ModelError e, int n) {
        if (e.retryAfterSeconds() != null) {
            return Math.max(0, e.retryAfterSeconds()) * 1000;
        }
        long s = Math.min((long) retry.maxSeconds(), (long) retry.baseSeconds() << Math.min(n - 1, 20));
        return s * 1000;
    }

    private final class Session implements ModelSession {
        private final ModelSession in;

        Session(ModelSession in) {
            this.in = in;
        }

        @Override
        public ModelTurn send(List<UserPart> parts) throws ModelError {
            if (next < recorded.size()) {
                RunFile.Recorded r = recorded.get(next++);
                return in.replay(parts, r.raw(), r.millis());
            }
            goingLive();
            try {
                return got(in.send(parts));
            } catch (ModelError e) {
                return again(e);
            }
        }

        @Override
        public ModelTurn resend() throws ModelError {
            goingLive();
            try {
                return got(in.resend());
            } catch (ModelError e) {
                return again(e);
            }
        }

        @Override
        public ModelTurn replay(List<UserPart> parts, JsonNode raw, long millis) {
            throw new UnsupportedOperationException("고리는 replay를 부르지 않는다");
        }

        private ModelTurn again(ModelError first) throws ModelError {
            ModelError e = first;
            for (int n = 1; ; n++) {
                boolean last = e.category() != ModelError.Category.RETRY || n >= retry.maxAttempts();
                long wait = last ? 0 : waitMillis(e, n);
                attempt(e, n, wait, last);
                if (last) {
                    throw e;
                }
                try {
                    control.sleeper.sleep(wait);
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("기다리다 끼어들었다", x);
                }
                control.beforeLive();
                try {
                    return got(in.resend());
                } catch (ModelError x) {
                    e = x;
                }
            }
        }
    }

    private void goingLive() {
        if (!replayer.done()) {
            throw new Replayer.Mismatch("기록에 고리 사건이 남았는데 새 요청을 보내려 한다");
        }
        control.beforeLive();
    }

    private ModelTurn got(ModelTurn t) {
        liveResponses++;
        control.spend(prices.withCache(model, Prices.use(t.inputTokens(), t.cacheReadTokens(), t.cacheWriteTokens(), t.outputTokens(), t.raw(), ttl)));
        return t;
    }

    private void attempt(ModelError e, int n, long wait, boolean last) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "attempt");
        m.put("turn", next + liveResponses + 1);
        m.put("attempt", n);
        m.put("category", e.category().name());
        m.put("status", e.status());
        m.put("error_type", e.type());
        m.put("code", e.code());
        m.put("message", e.getMessage());
        m.put("retry_after", e.retryAfterSeconds());
        m.put("wait_seconds", last ? null : wait / 1000.0);
        out.accept(m);
    }
}
