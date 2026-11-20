package com.zipinfo.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MainTest {
    @Test
    void seesAiCore() {
        assertEquals("harness skeleton with com.zipinfo.ai.AiCore", Main.describe());
    }
}
