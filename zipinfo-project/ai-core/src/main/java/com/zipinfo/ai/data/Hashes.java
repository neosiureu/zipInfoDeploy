package com.zipinfo.ai.data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 읽는 파일을 SHA-256으로 대 본다(설계안 4.3·5절). */
public final class Hashes {
    private Hashes() {}

    public static String sha256(Path p) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 20];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            return HexFormat.of().withUpperCase().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void require(Path p, String want) throws IOException {
        String got = sha256(p);
        if (!got.equalsIgnoreCase(want)) {
            throw new IOException(p.getFileName() + "의 SHA-256이 다르다: " + got.substring(0, 8) + "… (와야 할 것 " + want.substring(0, 8) + "…)");
        }
    }
}
