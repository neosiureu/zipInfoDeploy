package com.zipinfo.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * 가짜 Messages API(JDK 내장 HTTP 서버). 정해 둔 응답을 차례로 돌려주고(SSE 스트리밍 또는 오류), 받은 요청 본문을 모두 남긴다.
 * 순서 3b 시험이 SDK의 주소를 이곳으로 돌려 인터넷 없이 문항 하나를 끝까지 돌린다.
 */
public final class FakeAnthropic implements AutoCloseable {
    static final ObjectMapper M = new ObjectMapper();

    /** missingBytes가 0보다 크면 본문 길이를 그만큼 길게 알리고 덜 보낸 채 멈춘다(응답이 멈춘 연결, 받는 쪽은 시간 초과). */
    public record Reply(int status, Map<String, String> headers, String body, boolean sse, int missingBytes) {}

    private final HttpServer server;
    private final Deque<Reply> script = new ArrayDeque<>();
    public final List<JsonNode> requests = new ArrayList<>();

    public FakeAnthropic() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", ex -> {
            byte[] in = ex.getRequestBody().readAllBytes();
            synchronized (requests) {
                requests.add(M.readTree(in));
            }
            Reply r;
            synchronized (script) {
                r = script.pollFirst();
            }
            if (r == null) {
                r = error(500, "api_error", "가짜 서버에 남은 응답이 없다", null);
            }
            byte[] out = r.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("content-type", r.sse() ? "text/event-stream" : "application/json");
            r.headers().forEach((k, v) -> ex.getResponseHeaders().add(k, v));
            ex.sendResponseHeaders(r.status(), out.length + r.missingBytes());
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            } catch (IOException cut) {
                // missingBytes: 덜 쓴 채로 둔다
            }
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void then(Reply r) {
        synchronized (script) {
            script.addLast(r);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ------------------------------------------------------------ 응답 만들기

    public static ObjectNode thinking(String text, String signature) {
        return M.createObjectNode().put("type", "thinking").put("thinking", text).put("signature", signature);
    }

    public static ObjectNode text(String s) {
        return M.createObjectNode().put("type", "text").put("text", s);
    }

    public static ObjectNode toolUse(String id, String name, ObjectNode input) {
        ObjectNode n = M.createObjectNode().put("type", "tool_use").put("id", id).put("name", name);
        n.set("input", input);
        return n;
    }

    private static String event(String name, JsonNode data) {
        return "event: " + name + "\ndata: " + data.toString() + "\n\n";
    }

    /** 블록들을 SSE 사건으로(블록마다 시작·조각·끝). stopDetails는 null이면 없다. */
    public static Reply sse(List<ObjectNode> blocks, String stopReason, ObjectNode stopDetails) {
        StringBuilder sb = new StringBuilder();
        ObjectNode msg = M.createObjectNode().put("id", "msg_fake").put("type", "message").put("role", "assistant").put("model", "claude-haiku-5-5");
        msg.putArray("content");
        msg.putNull("stop_reason");
        msg.putNull("stop_sequence");
        msg.putObject("usage").put("input_tokens", 100).put("cache_read_input_tokens", 80).put("cache_creation_input_tokens", 20).put("output_tokens", 1);
        ObjectNode start = M.createObjectNode().put("type", "message_start");
        start.set("message", msg);
        sb.append(event("message_start", start));
        int i = 0;
        for (ObjectNode b : blocks) {
            ObjectNode cbs = M.createObjectNode().put("type", "content_block_start").put("index", i);
            String type = b.get("type").asText();
            ObjectNode empty = M.createObjectNode().put("type", type);
            switch (type) {
                case "thinking" -> empty.put("thinking", "").put("signature", "");
                case "text" -> empty.put("text", "");
                case "tool_use" -> {
                    empty.put("id", b.get("id").asText()).put("name", b.get("name").asText());
                    empty.putObject("input");
                }
                default -> throw new IllegalArgumentException(type);
            }
            cbs.set("content_block", empty);
            sb.append(event("content_block_start", cbs));
            ObjectNode delta = M.createObjectNode().put("type", "content_block_delta").put("index", i);
            switch (type) {
                case "thinking" -> {
                    delta.putObject("delta").put("type", "thinking_delta").put("thinking", b.get("thinking").asText());
                    sb.append(event("content_block_delta", delta));
                    ObjectNode sig = M.createObjectNode().put("type", "content_block_delta").put("index", i);
                    sig.putObject("delta").put("type", "signature_delta").put("signature", b.get("signature").asText());
                    sb.append(event("content_block_delta", sig));
                }
                case "text" -> {
                    delta.putObject("delta").put("type", "text_delta").put("text", b.get("text").asText());
                    sb.append(event("content_block_delta", delta));
                }
                default -> {
                    delta.putObject("delta").put("type", "input_json_delta").put("partial_json", b.get("input").toString());
                    sb.append(event("content_block_delta", delta));
                }
            }
            sb.append(event("content_block_stop", M.createObjectNode().put("type", "content_block_stop").put("index", i)));
            i++;
        }
        ObjectNode md = M.createObjectNode().put("type", "message_delta");
        ObjectNode d = md.putObject("delta").put("stop_reason", stopReason);
        d.putNull("stop_sequence");
        if (stopDetails != null) {
            d.set("stop_details", stopDetails);
        }
        md.putObject("usage").put("output_tokens", 42);
        sb.append(event("message_delta", md));
        sb.append(event("message_stop", M.createObjectNode().put("type", "message_stop")));
        return new Reply(200, Map.of(), sb.toString(), true, 0);
    }

    /** 스트림 중간에 오류 사건이 오는 응답. */
    public static Reply sseError(String type, String message) {
        ObjectNode msg = M.createObjectNode().put("id", "msg_fake").put("type", "message").put("role", "assistant").put("model", "claude-haiku-5-5");
        msg.putArray("content");
        msg.putNull("stop_reason");
        msg.putNull("stop_sequence");
        msg.putObject("usage").put("input_tokens", 100).put("cache_read_input_tokens", 80).put("cache_creation_input_tokens", 20).put("output_tokens", 1);
        ObjectNode start = M.createObjectNode().put("type", "message_start");
        start.set("message", msg);
        ObjectNode err = M.createObjectNode().put("type", "error");
        err.putObject("error").put("type", type).put("message", message);
        return new Reply(200, Map.of(), event("message_start", start) + event("error", err), true, 0);
    }

    /** 스트림을 message_delta 앞에서 자른다. stall이면 덜 보낸 채 멈추고(받는 쪽 시간 초과), 아니면 오류 없이 닫힌다(message_stop 없음). */
    public static Reply cut(Reply r, boolean stall) {
        String b = r.body().substring(0, r.body().indexOf("event: message_delta"));
        return new Reply(r.status(), r.headers(), b, true, stall ? 1000 : 0);
    }

    public static Reply error(int status, String type, String message, String errorCode, Map<String, String> headers) {
        ObjectNode b = M.createObjectNode().put("type", "error");
        ObjectNode e = b.putObject("error").put("type", type).put("message", message);
        if (errorCode != null) {
            e.putObject("details").put("error_code", errorCode);
        }
        return new Reply(status, headers, b.toString(), false, 0);
    }

    public static Reply error(int status, String type, String message, String errorCode) {
        return error(status, type, message, errorCode, Map.of());
    }

    public static ArrayNode arr() {
        return M.createArrayNode();
    }
}
