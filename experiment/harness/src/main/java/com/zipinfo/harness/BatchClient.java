package com.zipinfo.harness;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.HttpResponseFor;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.batches.BatchCreateParams;
import com.anthropic.models.messages.batches.MessageBatch;
import com.anthropic.models.messages.batches.MessageBatchIndividualResponse;
import com.anthropic.models.messages.batches.MessageBatchRequestCounts;
import com.fasterxml.jackson.databind.JsonNode;
import com.zipinfo.ai.model.ModelError;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;

/**
 * 판정 Batch의 세 부르기(순서 6c 설계 6절): 만들기, 상태 보기, 결과 받기. Message Batches API(Java SDK 3.11.0)를 쓴다.
 * ai-core의 AnthropicModelClient에 넣지 않고 하네스에 둔다(ai-core를 고치면 요청 지문의 코드 칸이 바뀐다).
 * <ul>
 * <li>SDK 자동 재시도는 끈다(max_retries 0, 01 201줄). 재시도는 하네스가 설정의 retry로 한다.</li>
 * <li>HTTP 오류는 01 하네스 규칙의 세 갈래로 가른다. 오류 칸(type·message·details.error_code·retry-after)을 뽑는 몇 줄은
 *     AnthropicModelClient.error(패키지 안이라 부를 수 없다)를 옮겨 적었고, 갈래는 ModelError.classify로 나눈다.
 *     상태 코드 없는 연결 오류·시간 초과는 RETRY, 그 밖의 SDK 오류는 STOP_AND_LOOK이다.</li>
 * <li>결과는 받은 원문(JSONL 바이트) 그대로 돌려준다. 판정 파일에 원문으로 남긴다(서버에서는 29일 뒤 사라진다).</li>
 * <li>timeout은 부르기 하나 전체에 걸린다(설정의 timeout_seconds).</li>
 * </ul>
 */
public interface BatchClient {
    /** 배치 하나의 상태: ID, 처리 상태(in_progress·canceling·ended), 요청 수(처리 중·succeeded·errored·canceled·expired). */
    record Status(String id, String processing, long inProgress, long succeeded, long errored, long canceled, long expired) {
        public boolean ended() {
            return "ended".equals(processing);
        }

        String counts() {
            return "처리 중 " + inProgress + ", succeeded " + succeeded + ", errored " + errored + ", canceled " + canceled + ", expired " + expired;
        }
    }

    Status create(List<BatchCreateParams.Request> requests) throws ModelError;

    Status retrieve(String batchId) throws ModelError;

    /** 끝난 배치의 결과 원문(JSONL). */
    byte[] results(String batchId) throws ModelError;

    /** Anthropic API. baseUrl이 null이면 기본 주소다(시험은 가짜 서버 주소를 준다). */
    static BatchClient anthropic(String apiKey, String baseUrl, Duration timeout) {
        AnthropicOkHttpClient.Builder b = AnthropicOkHttpClient.builder().apiKey(apiKey).maxRetries(0).timeout(timeout);
        if (baseUrl != null) {
            b.baseUrl(baseUrl);
        }
        AnthropicClient client = b.build();
        return new BatchClient() {
            @Override
            public Status create(List<BatchCreateParams.Request> requests) throws ModelError {
                return call(() -> status(client.messages().batches().create(BatchCreateParams.builder().requests(requests).build())));
            }

            @Override
            public Status retrieve(String batchId) throws ModelError {
                return call(() -> status(client.messages().batches().retrieve(batchId)));
            }

            @Override
            public byte[] results(String batchId) throws ModelError {
                return call(() -> {
                    try (HttpResponseFor<StreamResponse<MessageBatchIndividualResponse>> r = client.messages().batches().withRawResponse()
                            .resultsStreaming(batchId)) {
                        return r.body().readAllBytes();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        };
    }

    private static Status status(MessageBatch b) {
        MessageBatchRequestCounts c = b.requestCounts();
        return new Status(b.id(), b.processingStatus().asString(), c.processing(), c.succeeded(), c.errored(), c.canceled(), c.expired());
    }

    interface Call<T> {
        T get();
    }

    private static <T> T call(Call<T> f) throws ModelError {
        try {
            return f.get();
        } catch (AnthropicServiceException e) {
            throw error(e);
        } catch (AnthropicIoException | UncheckedIOException e) {
            throw new ModelError(ModelError.Category.RETRY, 0, "connection", "", "연결 오류: " + e.getMessage(), null, e);
        } catch (AnthropicException e) {
            throw new ModelError(ModelError.Category.STOP_AND_LOOK, 0, e.getClass().getSimpleName(), "", String.valueOf(e.getMessage()), null, e);
        }
    }

    /** AnthropicModelClient.error(…, false)와 같다. */
    static ModelError error(AnthropicServiceException e) {
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
        int status = e.statusCode();
        ModelError.Category c = ModelError.classify(status, type, message, code, false);
        return new ModelError(c, status, type, code, message, retryAfter, e);
    }
}
