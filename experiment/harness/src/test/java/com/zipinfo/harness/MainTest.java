package com.zipinfo.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MainTest {
    @Test
    void withoutACommandItPrintsHowToUseIt() throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int code = Main.run(new String[0], new PrintStream(b, true, StandardCharsets.UTF_8), Path.of(".."), null);
        assertEquals(2, code);
        String out = b.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("plan|run|summary"), out);
    }

    @Test
    void aMissingSettingsFileIsReportedNotThrown() throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int code = Main.run(new String[] {"plan", "settings/없는_설정.json"}, new PrintStream(b, true, StandardCharsets.UTF_8), Path.of(".."), null);
        assertEquals(2, code);
        assertTrue(b.toString(StandardCharsets.UTF_8).startsWith("멈춤(설정이나 파일): "));
    }
}
