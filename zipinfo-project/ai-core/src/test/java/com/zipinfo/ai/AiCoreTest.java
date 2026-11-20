package com.zipinfo.ai;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.anthropic.client.AnthropicClient;
import org.junit.jupiter.api.Test;

class AiCoreTest {
    @Test
    void buildsClientWithoutNetwork() {
        AnthropicClient client = AiCore.client("not-a-real-key");
        try {
            assertNotNull(client.messages());
        } finally {
            client.close();
        }
    }
}
