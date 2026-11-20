package com.zipinfo.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

/** Entry point of the shared AI module. Build order 1 keeps only this skeleton. */
public final class AiCore {
    private AiCore() {
    }

    /** Builds an SDK client for the given API key. Building it makes no network call. */
    public static AnthropicClient client(String apiKey) {
        return AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }
}
