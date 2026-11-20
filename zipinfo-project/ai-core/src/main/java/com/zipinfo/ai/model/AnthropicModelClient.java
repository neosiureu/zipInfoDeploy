package com.zipinfo.ai.model;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.SseException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolChoiceAuto;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.Usage;
import com.fasterxml.jackson.databind.JsonNode;
import com.zipinfo.ai.tools.ToolSpecs;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Anthropic 구현(Java SDK 3.11.0, API 판 2026-10-21). 01 하네스 규칙대로: SDK 자동 재시도 끔(max_retries 0),
 * 스트리밍으로 받아 SDK가 모은 최종 메시지를 쓰고, 받은 assistant 메시지는 toParam()으로 그대로 되돌린다(thinking 보존).
 * temperature·top_p·top_k는 보내지 않는다. tool_choice는 모든 판에서 auto다.
 */
public final class AnthropicModelClient implements ModelClient {
    private final AnthropicClient client;
    private final ModelSettings settings;

    public AnthropicModelClient(String apiKey, String baseUrl, ModelSettings settings, Duration timeout) {
        AnthropicOkHttpClient.Builder b = AnthropicOkHttpClient.builder().apiKey(apiKey).maxRetries(0).timeout(timeout);
        if (baseUrl != null) {
            b.baseUrl(baseUrl);
        }
        this.client = b.build();
        this.settings = settings;
    }

    @Override
    public ModelSession open(String system, List<ToolSpecs.Spec> tools) {
        List<ToolUnion> sdkTools = new ArrayList<>();
        for (ToolSpecs.Spec s : tools) {
            sdkTools.add(ToolUnion.ofTool(toTool(s)));
        }
        return new Session(system, sdkTools);
    }

    /** 우리 strict 스키마(ToolSpecs)를 SDK Tool로. 모든 칸 필수, additionalProperties false, strict true. */
    static Tool toTool(ToolSpecs.Spec s) {
        JsonNode schema = s.inputSchema();
        Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        for (Map.Entry<String, JsonNode> e : schema.get("properties").properties()) {
            props.putAdditionalProperty(e.getKey(), JsonValue.fromJsonNode(e.getValue()));
        }
        List<String> required = new ArrayList<>();
        schema.get("required").forEach(n -> required.add(n.asText()));
        Tool.InputSchema in = Tool.InputSchema.builder().properties(props.build()).required(required)
                .putAdditionalProperty("additionalProperties", JsonValue.from(false)).build();
        return Tool.builder().name(s.name()).description(s.description()).inputSchema(in).strict(true).build();
    }

    private final class Session implements ModelSession {
        private final String system;
        private final List<ToolUnion> tools;
        private final List<MessageParam> history = new ArrayList<>();

        Session(String system, List<ToolUnion> tools) {
            this.system = system;
            this.tools = tools;
        }

        @Override
        public ModelTurn send(List<UserPart> parts) throws ModelError {
            List<ContentBlockParam> blocks = new ArrayList<>();
            for (UserPart p : parts) {
                if (p instanceof UserPart.Text t) {
                    blocks.add(ContentBlockParam.ofText(com.anthropic.models.messages.TextBlockParam.builder().text(t.text()).build()));
                } else if (p instanceof UserPart.ToolResult r) {
                    blocks.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder().toolUseId(r.toolUseId()).content(r.text())
                            .isError(r.isError()).build()));
                }
            }
            history.add(MessageParam.ofUserOfContentBlockParams(blocks));
            return resend();
        }

        @Override
        public ModelTurn resend() throws ModelError {
            MessageCreateParams.Builder p = MessageCreateParams.builder().model(settings.model()).maxTokens(settings.maxTokens())
                    .system(system).messages(history).tools(tools).toolChoice(ToolChoiceAuto.builder().build());
            switch (settings.thinking()) {
                case ADAPTIVE -> p.thinking(ThinkingConfigAdaptive.builder().build());
                case DISABLED -> p.thinking(ThinkingConfigDisabled.builder().build());
                case OMIT -> { }
            }
            if (settings.effort() != null) {
                p.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(settings.effort())).build());
            }
            long t0 = System.nanoTime();
            Message m;
            try {
                MessageAccumulator acc = MessageAccumulator.create();
                boolean stopped = false;
                try (StreamResponse<RawMessageStreamEvent> s = client.messages().createStreaming(p.build())) {
                    Iterator<RawMessageStreamEvent> it = s.stream().iterator();
                    while (it.hasNext()) {
                        RawMessageStreamEvent ev = it.next();
                        acc.accumulate(ev);
                        stopped = stopped || ev.isMessageStop();
                    }
                }
                if (!stopped) {
                    // 오류 없이 연결이 닫혔는데 message_stop이 오지 않았다. 상태 코드 없는 연결 오류로 본다(01 196줄).
                    throw new ModelError(ModelError.Category.RETRY, 0, "incomplete_stream", "", "스트림이 message_stop 없이 끝났다", null, null);
                }
                m = acc.message();
            } catch (SseException e) {
                throw error(e, true);
            } catch (AnthropicServiceException e) {
                throw error(e, false);
            } catch (AnthropicIoException e) {
                // 연결 실패, 시간 초과, 스트림 중간에 끊긴 연결(SDK가 "Stream failed"로 감쌈)
                throw new ModelError(ModelError.Category.RETRY, 0, "connection", "", "연결 오류: " + e.getMessage(), null, e);
            } catch (AnthropicException e) {
                // 그 밖의 SDK 오류(받은 사건의 꼴이 맞지 않는 것 등)는 멈추고 본다
                throw new ModelError(ModelError.Category.STOP_AND_LOOK, 0, e.getClass().getSimpleName(), "", String.valueOf(e.getMessage()), null, e);
            }
            long millis = (System.nanoTime() - t0) / 1_000_000;
            history.add(m.toParam());
            List<ToolCall> calls = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            for (ContentBlock cb : m.content()) {
                cb.toolUse().ifPresent(tu -> calls.add(new ToolCall(tu.id(), tu.name(), tu._input().convert(JsonNode.class))));
                cb.text().ifPresent(t -> text.append(t.text()));
            }
            String reason = m.stopReason().map(r -> r.asString()).orElse("");
            String category = m.stopDetails().flatMap(d -> d.category()).map(c -> c.asString()).orElse("");
            Usage u = m.usage();
            return new ModelTurn(StopKind.of(reason), reason, category, calls, text.toString(), u.inputTokens(), u.cacheReadInputTokens().orElse(0L),
                    u.cacheCreationInputTokens().orElse(0L), u.outputTokens(), millis, ObjectMappers.jsonMapper().valueToTree(m));
        }
    }

    static ModelError error(AnthropicServiceException e, boolean streamEvent) {
        String type = "", message = "", code = "";
        try {
            JsonNode body = e.body().convert(JsonNode.class);
            JsonNode err = body == null ? null : body.path("error");
            if (err != null && !err.isMissingNode()) {
                type = err.path("type").asText("");
                message = err.path("message").asText("");
                code = err.path("details").path("error_code").asText("");
            }
        } catch (RuntimeException ignored) {
            // 본문이 JSON이 아니면 상태 코드만으로 가른다
        }
        Long retryAfter = null;
        List<String> ra = e.headers().values("retry-after");
        if (!ra.isEmpty()) {
            try {
                retryAfter = Long.parseLong(ra.get(0).trim());
            } catch (NumberFormatException ignored) {
                // 날짜 꼴은 쓰지 않는다
            }
        }
        int status = streamEvent ? 0 : e.statusCode();
        ModelError.Category c = ModelError.classify(status, type, message, code, streamEvent);
        return new ModelError(c, status, type, code, message, retryAfter, e);
    }
}
