package com.zipinfo.ai.model;

/**
 * 모델 부르기 오류와 그 갈래(01 하네스 규칙). 상태 코드만으로 가르지 않고 문서가 정한 표지로 가른다.
 * RESUME_LATER: 한도가 풀리면 멈춘 요청부터 이어 돌린다. RETRY: 같은 요청을 다시 보낸다. STOP_AND_LOOK: 멈추고 본다.
 */
public final class ModelError extends Exception {
    public enum Category { RESUME_LATER, RETRY, STOP_AND_LOOK }

    private final Category category;
    private final int status;
    private final String type;
    private final String code;
    private final Long retryAfterSeconds;

    public ModelError(Category category, int status, String type, String code, String message, Long retryAfterSeconds, Throwable cause) {
        super(message, cause);
        this.category = category;
        this.status = status;
        this.type = type;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Category category() { return category; }
    public int status() { return status; }
    public String type() { return type; }
    public String code() { return code; }
    public Long retryAfterSeconds() { return retryAfterSeconds; }

    /** 01 하네스 규칙의 세 갈래. status 0은 상태 코드가 없는 오류(연결·시간 초과)다. streamEvent는 스트리밍 중에 온 오류 이벤트다. */
    public static Category classify(int status, String type, String message, String code, boolean streamEvent) {
        String m = message == null ? "" : message;
        if (status == 429 && "enforced_spend_limit_reached".equals(code)) {
            return Category.RESUME_LATER;
        }
        if (status == 400 && m.startsWith("You have reached your specified")) {
            return Category.RESUME_LATER;
        }
        if (m.startsWith("Your credit balance is too low to access the Anthropic API")) {
            return Category.RESUME_LATER;
        }
        if (status == 402 && "billing_error".equals(type)) {
            return Category.RESUME_LATER;
        }
        if (streamEvent) {
            return isRetryType(type) ? Category.RETRY : Category.STOP_AND_LOOK;
        }
        if (status == 0 || status >= 500) {
            return Category.RETRY;
        }
        if (status == 429 && "rate_limit_error".equals(type)) {
            return Category.RETRY;
        }
        return Category.STOP_AND_LOOK;
    }

    /** 스트리밍 중 오류 이벤트에서 재시도할 type: 429·5xx에 해당하는 것. */
    static boolean isRetryType(String type) {
        return "rate_limit_error".equals(type) || "overloaded_error".equals(type) || "api_error".equals(type)
                || "timeout_error".equals(type);
    }
}
