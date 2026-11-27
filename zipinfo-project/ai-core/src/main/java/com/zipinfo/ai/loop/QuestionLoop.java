package com.zipinfo.ai.loop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zipinfo.ai.model.ModelClient;
import com.zipinfo.ai.model.ModelError;
import com.zipinfo.ai.model.ModelSession;
import com.zipinfo.ai.model.ModelTurn;
import com.zipinfo.ai.model.ToolCall;
import com.zipinfo.ai.model.UserPart;
import com.zipinfo.ai.report.Checker;
import com.zipinfo.ai.report.Finding;
import com.zipinfo.ai.report.Renderer;
import com.zipinfo.ai.report.Report;
import com.zipinfo.ai.tools.DataSet;
import com.zipinfo.ai.tools.RunState;
import com.zipinfo.ai.tools.ToolResult;
import com.zipinfo.ai.tools.ToolSpecs;
import com.zipinfo.ai.tools.Tools;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * 문항 하나를 푸는 고리(설계안 4.2 loop, 01 하네스 규칙). 대화는 덧붙이기만 한다.
 * - tool_use: 거는 자리를 돌고 도구를 돌려 결과를 덧붙인다. submit_report는 글을 먼저 그리고(그린 글도 검사가 본다), 대조 코드를 모든 판에서
 *   기록 전용으로 돌리고, 제출 자리(④)를 지나면 그린 글로 끝낸다(순서 11b-2). 제출 사건에는 그린 글(rendered)도 남긴다(막힌 제출의 글을 채점·읽기가 본다).
 * - end_turn: 제출이 없으면 재촉 한 줄을 한 번 보낸다(모든 판 같음). 또 없으면 실패다.
 * - max_tokens, model_context_window_exceeded, refusal: 실패. refusal 가운데 reasoning_extraction은 멈추고 본다. 그 밖의 멈춘 까닭도 멈추고 본다.
 * - 턴 상한(모델 응답 수)을 넘으면 실패다. 모델 오류는 그대로 던지고, 하네스가 갈래대로 resume()으로 같은 요청을 다시 보낸다(턴으로 세지 않음).
 * - 기록(설계안 10.1): 응답마다 토큰 넷·시간·원문, 도구마다 입력·결과·시간(장치 판정 포함), 장치 판정, 대조 결과.
 * - 장치: 앞 자리에서 거절이 나와도 뒤 자리까지 모든 부품을 묻고 판정을 남긴다. 돌려주는 것은 처음 거절 하나다(7.1).
 *   그래서 부품의 decide는 판정만 하고 실행 상태를 바꾸지 않는다.
 */
public final class QuestionLoop {
    /** 데이터·계산 도구: 시작 자리(⓪)가 거는 도구. */
    public static final Set<String> DATA_TOOLS = Set.of("search_address", "trade_stats", "compare_prices", "convert_rent", "convert_area");
    /** 도구 전 자리(②③)가 거는 도구. */
    public static final Set<String> GUARDED_TOOLS = Set.of("trade_stats", "convert_area");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** maxTurns: 턴 상한. hooks: 판이 켠 부품(차례대로, 7.1). extra: 판에 따라 더하는 도구(카드·의도, 순서 4·5). */
    public record Config(int maxTurns, List<Hook> hooks, Map<String, BiFunction<JsonNode, RunState, ToolResult>> extra) {}

    private final ModelClient client;
    private final Tools tools;
    private final DataSet data;
    private final Config cfg;
    private final RunRecord rec = new RunRecord();
    private ModelSession session;
    private RunState st;
    private List<UserPart> pending;
    private boolean nudged;

    public QuestionLoop(ModelClient client, Tools tools, DataSet data, Config cfg) {
        this.client = client;
        this.tools = tools;
        this.data = data;
        this.cfg = cfg;
    }

    public RunRecord record() {
        return rec;
    }

    /** 세션을 열고 첫 사용자 글(안내 자리의 줄 + 질문 + 매물 블록)을 보낸다. */
    public RunRecord start(String system, List<ToolSpecs.Spec> specs, String question, RunState state) throws ModelError {
        this.st = state;
        state.question(question);
        this.session = client.open(system, specs);
        List<String> notices = new ArrayList<>();
        for (Hook h : cfg.hooks()) {
            if (h.at(Hook.Point.NOTICE)) {
                Hook.Decision d = h.decide(Hook.Point.NOTICE, new Hook.Context(state, "", null, null, List.of()));
                rec.add("hook", "point", "NOTICE", "part", h.name(), "decision", d.kind().name(), "line", d.line());
                if (d.kind() == Hook.Decision.Kind.NOTICE) {
                    notices.add(d.line());
                }
            }
        }
        String first = Prompts.firstMessage(question, state.listing());
        if (!notices.isEmpty()) {
            first = String.join("\n", notices) + "\n\n" + first;
        }
        rec.add("first_message", "text", first);
        pending = List.of(new UserPart.Text(first));
        return run(false);
    }

    /** 모델 오류 뒤 같은 기록 그대로 멈춘 요청을 다시 보낸다. */
    public RunRecord resume() throws ModelError {
        return run(true);
    }

    private RunRecord run(boolean resend) throws ModelError {
        while (true) {
            ModelTurn t = resend ? session.resend() : session.send(pending);
            resend = false;
            rec.turn();
            List<String> names = t.calls().stream().map(ToolCall::name).toList();
            rec.add("response", "turn", rec.turns(), "stop", t.rawStopReason(), "refusal_category", t.refusalCategory(), "tools", names,
                    "text_chars", t.text().length(), "input_tokens", t.inputTokens(), "cache_read_tokens", t.cacheReadTokens(),
                    "cache_write_tokens", t.cacheWriteTokens(), "output_tokens", t.outputTokens(), "millis", t.millis(), "raw", t.raw());
            switch (t.stop()) {
                case TOOL_USE -> {
                    List<UserPart> results = new ArrayList<>();
                    for (ToolCall c : t.calls()) {
                        long t0 = System.nanoTime();
                        if (c.name().equals("submit_report")) {
                            String done = submit(c, results, t0);
                            if (done != null) {
                                rec.end(RunRecord.Outcome.SUBMITTED, done);
                                return rec;
                            }
                        } else {
                            results.add(callTool(c, t0));
                        }
                    }
                    if (rec.turns() >= cfg.maxTurns()) {
                        rec.end(RunRecord.Outcome.FAILED_TURN_LIMIT, null);
                        return rec;
                    }
                    pending = results;
                }
                case END_TURN -> {
                    if (nudged || rec.turns() >= cfg.maxTurns()) {
                        rec.end(nudged ? RunRecord.Outcome.FAILED_NO_SUBMIT : RunRecord.Outcome.FAILED_TURN_LIMIT, null);
                        return rec;
                    }
                    nudged = true;
                    rec.add("nudge", "text", Prompts.NUDGE);
                    pending = List.of(new UserPart.Text(Prompts.NUDGE));
                }
                case MAX_TOKENS -> {
                    rec.end(RunRecord.Outcome.FAILED_MAX_TOKENS, null);
                    return rec;
                }
                case CONTEXT_WINDOW -> {
                    rec.end(RunRecord.Outcome.FAILED_CONTEXT_WINDOW, null);
                    return rec;
                }
                case REFUSAL -> {
                    rec.end("reasoning_extraction".equals(t.refusalCategory()) ? RunRecord.Outcome.STOP_REASONING_EXTRACTION
                            : RunRecord.Outcome.FAILED_REFUSAL, null);
                    return rec;
                }
                default -> {
                    rec.end(RunRecord.Outcome.STOP_OTHER_STOP_REASON, null);
                    return rec;
                }
            }
        }
    }

    /** 사슬을 돈다: 걸린 부품마다 판정을 기록하고, 처음 거절한 것을 돌려준다(7.1). */
    private Hook.Decision chain(Hook.Point p, Hook.Context ctx) {
        Hook.Decision first = Hook.Decision.PASS;
        for (Hook h : cfg.hooks()) {
            if (!h.at(p)) {
                continue;
            }
            Hook.Decision d = h.decide(p, ctx);
            rec.add("hook", "point", p.name(), "part", h.name(), "tool", ctx.tool(), "decision", d.kind().name(), "line", d.line());
            if (d.kind() == Hook.Decision.Kind.REJECT && first.kind() != Hook.Decision.Kind.REJECT) {
                first = d;
            }
        }
        return first;
    }

    private static Hook.Decision firstReject(Hook.Decision a, Hook.Decision b) {
        return a.kind() == Hook.Decision.Kind.REJECT ? a : b;
    }

    private static String rejected(String line) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("오류", "REJECTED");
        m.put("설명", line);
        try {
            return JSON.writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 도구 하나를 받은 때부터 결과를 낼 때까지(장치 판정 포함)의 시간. 소요시간(01 213줄)에 더한다. */
    private static double millisSince(long t0) {
        return Math.round((System.nanoTime() - t0) / 1_000.0) / 1_000.0;
    }

    private UserPart callTool(ToolCall c, long t0) {
        Hook.Context ctx = new Hook.Context(st, c.name(), c.input(), null, List.of());
        Hook.Decision d = Hook.Decision.PASS;
        if (DATA_TOOLS.contains(c.name())) {
            d = chain(Hook.Point.START, ctx);
            if (GUARDED_TOOLS.contains(c.name())) {
                d = firstReject(d, chain(Hook.Point.BEFORE_TOOL, ctx));
            }
        }
        ToolResult r;
        if (d.kind() == Hook.Decision.Kind.REJECT) {
            r = new ToolResult(rejected(d.line()), true, "REJECTED");
        } else if (cfg.extra().containsKey(c.name())) {
            r = cfg.extra().get(c.name()).apply(c.input(), st);
        } else {
            r = tools.call(c.name(), c.input(), st);
        }
        rec.add("tool", "name", c.name(), "input", c.input(), "result", r.text(), "is_error", r.isError(), "error_code", r.errorCode(),
                "millis", millisSince(t0));
        return new UserPart.ToolResult(c.id(), r.text(), r.isError());
    }

    /** 받으면 그린 글, 거절이면 null(결과는 results에 덧붙임). */
    private String submit(ToolCall c, List<UserPart> results, long t0) {
        Report r;
        try {
            r = Report.parse(c.input());
        } catch (IllegalArgumentException e) {
            String text = rejected(e.getMessage());
            rec.add("tool", "name", c.name(), "input", c.input(), "result", text, "is_error", true, "error_code", "BAD_INPUT",
                    "millis", millisSince(t0));
            results.add(new UserPart.ToolResult(c.id(), text, true));
            return null;
        }
        String answer = Renderer.render(r, st);
        List<Finding> findings = Checker.check(r, st, data.validity(), answer);
        rec.add("submit", "input", c.input(), "rendered", answer, "findings", findings.stream().map(f -> f.where() + " " + f.code()).toList());
        Hook.Context ctx = new Hook.Context(st, c.name(), c.input(), r, findings);
        Hook.Decision d = firstReject(chain(Hook.Point.START, ctx), chain(Hook.Point.SUBMIT, ctx));
        if (d.kind() == Hook.Decision.Kind.REJECT) {
            String text = rejected(d.line());
            rec.add("tool", "name", c.name(), "input", c.input(), "result", text, "is_error", true, "error_code", "REJECTED",
                    "millis", millisSince(t0));
            results.add(new UserPart.ToolResult(c.id(), text, true));
            return null;
        }
        rec.add("accepted", "answer", answer, "millis", millisSince(t0));
        return answer;
    }
}
