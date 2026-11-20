package com.zipinfo.ai.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 파이썬 csv 모듈이 쓴 파일(UTF-8, LF, 큰따옴표)을 읽는다. 머리줄을 대 본 뒤 줄마다 칸 목록을 넘긴다. */
public final class Csv {
    private Csv() {}

    public static void read(Path path, List<String> header, Consumer<List<String>> each) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<String> h = next(r);
            if (h == null || !h.equals(header)) {
                throw new IOException(path.getFileName() + ": 머리줄이 다르다");
            }
            List<String> row;
            while ((row = next(r)) != null) {
                if (row.size() != header.size()) {
                    throw new IOException(path.getFileName() + ": 칸 수가 다른 줄이 있다");
                }
                each.accept(row);
            }
        }
    }

    /** 다음 줄 하나(따옴표 안의 줄바꿈 포함). 끝이면 null. */
    static List<String> next(Reader r) throws IOException {
        List<String> out = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        int c;
        while ((c = r.read()) != -1) {
            any = true;
            char ch = (char) c;
            if (quoted) {
                if (ch == '"') {
                    r.mark(1);
                    int d = r.read();
                    if (d == '"') {
                        cell.append('"');
                    } else {
                        quoted = false;
                        if (d != -1) {
                            r.reset();
                        }
                    }
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                out.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n') {
                out.add(cell.toString());
                return out;
            } else if (ch != '\r') {
                cell.append(ch);
            }
        }
        if (!any) {
            return null;
        }
        out.add(cell.toString());
        return out;
    }
}
