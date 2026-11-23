package com.zipinfo.harness;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

/**
 * 순서 6b 시험: 다른 JVM에서 같은 기록으로 얼린다. MemoryFileTest.anotherJvmWritesTheSameBytes가 띄운다.
 * 인자: 자료 폴더, 기록 폴더, 얼릴 파일. 입력과 설정은 MemoryFileTest와 같은 것을 짓는다. 실패하면 0이 아닌 값으로 끝난다.
 */
final class MemoryFreezeChild {
    private MemoryFreezeChild() {}

    public static void main(String[] args) throws Exception {
        Inputs in = MemoryFileTest.memoryInputs(Path.of(args[0]));
        RunSettings s = MemoryFileTest.memorySettings(n -> { });
        Runner r = new Runner(in, s, Path.of(args[1]), (ms, cache, timeout) -> {
            throw new IllegalStateException("요청을 보내지 않는다");
        }, x -> { }, new PrintStream(OutputStream.nullOutputStream()));
        MemoryFile.freeze(r, in, s, "c08", Path.of(args[2]));
    }
}
