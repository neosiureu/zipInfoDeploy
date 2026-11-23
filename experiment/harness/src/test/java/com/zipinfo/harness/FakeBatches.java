package com.zipinfo.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * 가짜 Message Batches API(순서 6c 시험, JDK 내장 HTTP 서버). 배치 세 길(만들기, 상태 보기, 결과 받기)만 있다.
 * <ul>
 * <li>만들기: 받은 본문을 남기고 배치 ID(msgbatch_fake1, …)를 준다.</li>
 * <li>상태 보기: 배치마다 pollsBeforeEnd번은 in_progress, 그다음 ended.</li>
 * <li>결과 받기: 낸 요청마다 answer가 지은 result로 한 줄. 기본은 거꾸로 된 차례다(결과는 낸 차례로 오지 않을 수 있다).</li>
 * <li>길마다 정해 둔 오류 응답을 먼저 준다(FakeAnthropic.error 꼴).</li>
 * </ul>
 */
final class FakeBatches implements AutoCloseable {
    static final ObjectMapper M = new ObjectMapper();

    /** 결과 하나: 배치 차례(1부터), custom_id, 그 요청의 params → result 객체. */
    interface Answer {
        ObjectNode result(int batch, String customId, JsonNode params);
    }

    private final HttpServer server;
    /** 만들기 본문, 받은 차례대로. */
    final List<JsonNode> created = Collections.synchronizedList(new ArrayList<>());
    /** 부른 길("POST batches", "GET batch", "GET results"), 차례대로. */
    final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    final Deque<FakeAnthropic.Reply> createErrors = new ArrayDeque<>();
    final Deque<FakeAnthropic.Reply> retrieveErrors = new ArrayDeque<>();
    final Deque<FakeAnthropic.Reply> resultErrors = new ArrayDeque<>();
    final Map<String, byte[]> served = new HashMap<>();
    volatile int pollsBeforeEnd = 1;
    volatile boolean reversed = true;
    volatile Answer answer = (b, id, p) -> verdict(true, "묻는 값을 답했다");
    /** 결과 원문을 보내기 전에 고친다(시험: 꼴이 다른 결과). */
    volatile UnaryOperator<String> mangle = UnaryOperator.identity();
    private final Map<String, Integer> polls = new HashMap<>();
    private final Map<String, Integer> numbers = new HashMap<>();

    FakeBatches() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages/batches", this::handle);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private synchronized void handle(HttpExchange ex) throws IOException {
        byte[] in = ex.getRequestBody().readAllBytes();
        String path = ex.getRequestURI().getPath();
        String rest = path.substring("/v1/messages/batches".length());
        if (ex.getRequestMethod().equals("POST") && rest.isEmpty()) {
            calls.add("POST batches");
            if (!createErrors.isEmpty()) {
                send(ex, createErrors.pollFirst());
                return;
            }
            JsonNode body = M.readTree(in);
            created.add(body);
            String id = "msgbatch_fake" + created.size();
            numbers.put(id, created.size());
            send(ex, json(batch(id, "in_progress", body.path("requests").size())));
        } else if (ex.getRequestMethod().equals("GET") && rest.endsWith("/results")) {
            calls.add("GET results");
            if (!resultErrors.isEmpty()) {
                send(ex, resultErrors.pollFirst());
                return;
            }
            String id = rest.substring(1, rest.length() - "/results".length());
            Integer n = numbers.get(id);
            if (n == null) {
                send(ex, FakeAnthropic.error(404, "not_found_error", "없는 배치", null));
                return;
            }
            List<JsonNode> reqs = new ArrayList<>();
            created.get(n - 1).path("requests").forEach(reqs::add);
            if (reversed) {
                Collections.reverse(reqs);
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode r : reqs) {
                ObjectNode line = M.createObjectNode().put("custom_id", r.path("custom_id").asText());
                line.set("result", answer.result(n, r.path("custom_id").asText(), r.path("params")));
                sb.append(line).append('\n');
            }
            byte[] out = mangle.apply(sb.toString()).getBytes(StandardCharsets.UTF_8);
            served.put(id, out);
            ex.getResponseHeaders().add("content-type", "application/x-jsonl");
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        } else if (ex.getRequestMethod().equals("GET")) {
            calls.add("GET batch");
            if (!retrieveErrors.isEmpty()) {
                send(ex, retrieveErrors.pollFirst());
                return;
            }
            String id = rest.substring(1);
            Integer n = numbers.get(id);
            if (n == null) {
                send(ex, FakeAnthropic.error(404, "not_found_error", "없는 배치", null));
                return;
            }
            int seen = polls.merge(id, 1, Integer::sum);
            send(ex, json(batch(id, seen > pollsBeforeEnd ? "ended" : "in_progress", created.get(n - 1).path("requests").size())));
        } else {
            send(ex, FakeAnthropic.error(404, "not_found_error", "없는 길", null));
        }
    }

    private static FakeAnthropic.Reply json(ObjectNode n) {
        return new FakeAnthropic.Reply(200, Map.of(), n.toString(), false, 0);
    }

    private static void send(HttpExchange ex, FakeAnthropic.Reply r) throws IOException {
        byte[] out = r.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("content-type", "application/json");
        r.headers().forEach((k, v) -> ex.getResponseHeaders().add(k, v));
        ex.sendResponseHeaders(r.status(), out.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(out);
        }
    }

    static ObjectNode batch(String id, String status, int n) {
        boolean ended = status.equals("ended");
        ObjectNode b = M.createObjectNode().put("id", id).put("type", "message_batch").put("processing_status", status);
        b.putObject("request_counts").put("processing", ended ? 0 : n).put("succeeded", ended ? n : 0).put("errored", 0).put("canceled", 0).put("expired", 0);
        if (ended) {
            b.put("ended_at", "2026-11-23T01:00:00Z");
        } else {
            b.putNull("ended_at");
        }
        b.put("created_at", "2026-11-23T00:00:00Z").put("expires_at", "2026-11-24T00:00:00Z");
        b.putNull("archived_at");
        b.putNull("cancel_initiated_at");
        if (ended) {
            b.put("results_url", "https://example.invalid/v1/messages/batches/" + id + "/results");
        } else {
            b.putNull("results_url");
        }
        return b;
    }

    // ------------------------------------------------------------ 결과 만들기

    /** usage: 입력 1000, 출력 200(Haiku 0.10·0.50, Batch 0.5배면 $0.0001). */
    static ObjectNode usage() {
        return M.createObjectNode().put("input_tokens", 1000).put("output_tokens", 200).put("cache_read_input_tokens", 0)
                .put("cache_creation_input_tokens", 0);
    }

    static ObjectNode succeeded(ArrayNode content, String stopReason, ObjectNode stopDetails) {
        ObjectNode m = M.createObjectNode().put("id", "msg_fake").put("type", "message").put("role", "assistant").put("model", "claude-haiku-5-5");
        m.set("content", content);
        m.put("stop_reason", stopReason);
        m.putNull("stop_sequence");
        if (stopDetails != null) {
            m.set("stop_details", stopDetails);
        }
        m.set("usage", usage());
        ObjectNode r = M.createObjectNode().put("type", "succeeded");
        r.set("message", m);
        return r;
    }

    static ObjectNode call(boolean answered, String reason) {
        ObjectNode in = M.createObjectNode().put("answered", answered).put("reason", reason);
        return FakeAnthropic.toolUse("toolu_v", "submit_verdict", in);
    }

    static ObjectNode verdict(boolean answered, String reason) {
        ArrayNode c = M.createArrayNode();
        c.add(FakeAnthropic.thinking("질문이 무엇을 묻는지 본다", "sig-v"));
        c.add(call(answered, reason));
        return succeeded(c, "tool_use", null);
    }

    static ObjectNode maxTokens() {
        ArrayNode c = M.createArrayNode();
        c.add(FakeAnthropic.thinking("길게 생각하다 잘렸다", "sig-m"));
        return succeeded(c, "max_tokens", null);
    }

    static ObjectNode refusal(String category) {
        ObjectNode d = M.createObjectNode().put("type", "refusal");
        if (category == null) {
            d.putNull("category");
        } else {
            d.put("category", category);
        }
        d.putNull("explanation");
        return succeeded(M.createArrayNode(), "refusal", d);
    }

    static ObjectNode noTool() {
        ArrayNode c = M.createArrayNode();
        c.add(FakeAnthropic.text("답한 것 같다"));
        return succeeded(c, "end_turn", null);
    }

    static ObjectNode twoCalls() {
        ArrayNode c = M.createArrayNode();
        c.add(call(true, "하나"));
        c.add(call(false, "둘"));
        return succeeded(c, "tool_use", null);
    }

    static ObjectNode errored(String type, String message, String errorCode) {
        ObjectNode inner = M.createObjectNode().put("type", type).put("message", message);
        if (errorCode != null) {
            inner.putObject("details").put("error_code", errorCode);
        }
        ObjectNode e = M.createObjectNode().put("type", "error");
        e.set("error", inner);
        ObjectNode r = M.createObjectNode().put("type", "errored");
        r.set("error", e);
        return r;
    }

    static ObjectNode gone(String type) {
        return M.createObjectNode().put("type", type);
    }
}
