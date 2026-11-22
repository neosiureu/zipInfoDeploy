package com.zipinfo.harness;

import com.zipinfo.ai.model.AnthropicModelClient;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 실험 하네스(설계안 4.1·10.1). experiment/harness에서 돈다(gradlew run --args="명령 설정 파일").
 * <ul>
 * <li>plan: 계획과 기록을 대 본다(요청을 보내지 않는다). 기록 폴더에 plan.txt로도 쓴다.</li>
 * <li>run: 돌린다. ANTHROPIC_API_KEY 환경 변수를 쓴다(값은 찍지 않는다). 찍는 줄은 기록 폴더의 run.log에도 쓴다.</li>
 * <li>summary: 판마다 모은 숫자를 찍고 기록 폴더에 summary.tsv로 쓴다.</li>
 * </ul>
 * 기록 폴더는 experiment/(files.out)/(stage)다. 찍는 글은 UTF-8이다.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        System.exit(run(args, out, Path.of("..").toAbsolutePath().normalize(), System.getenv("ANTHROPIC_API_KEY")));
    }

    static String usage() {
        return "쓰는 법: plan|run|summary <설정 파일>  (예: plan settings/pilot_v0.json)";
    }

    static int run(String[] args, PrintStream out, Path experiment, String apiKey) throws Exception {
        if (args.length != 2 || !List.of("plan", "run", "summary").contains(args[0])) {
            out.println(usage());
            return 2;
        }
        try {
            return command(args, out, experiment, apiKey);
        } catch (IllegalArgumentException | IOException e) {
            out.println("멈춤(설정이나 파일): " + e);
            return 2;
        }
    }

    private static int command(String[] args, PrintStream out, Path experiment, String apiKey) throws Exception {
        RunSettings s = RunSettings.load(Path.of(args[1]));
        Inputs in = Inputs.load(experiment, s);
        Path dir = experiment.resolve(s.files().out()).resolve(s.stage());
        Runner.Clients clients = (ms, cache, timeout) -> new AnthropicModelClient(apiKey, null, ms, timeout, cache);
        switch (args[0]) {
            case "plan" -> {
                Runner r = new Runner(in, s, dir, clients, Thread::sleep, out);
                String t = planText(r, s);
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("plan.txt"), t, StandardCharsets.UTF_8);
                out.print(t);
                return 0;
            }
            case "summary" -> {
                Runner r = new Runner(in, s, dir, clients, Thread::sleep, out);
                String t = Summary.format(Summary.rows(r.status(), s, in.variants(), dir));
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("summary.tsv"), t, StandardCharsets.UTF_8);
                out.print(t);
                return 0;
            }
            default -> {
                if (apiKey == null || apiKey.isBlank()) {
                    out.println("ANTHROPIC_API_KEY 환경 변수가 없다");
                    return 2;
                }
                Files.createDirectories(dir);
                try (OutputStream file = Files.newOutputStream(dir.resolve("run.log"), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                     PrintStream log = tee(out, file)) {
                    Runner.Result res = new Runner(in, s, dir, clients, Thread::sleep, log).run();
                    return res.stop() == null && res.remaining() == 0 ? 0 : 3;
                }
            }
        }
    }

    /** 계획 글: 설정, 실행 수, 블록 차례(앞쪽), 판마다 요청 지문, 기록과 대 본 것. */
    static String planText(Runner r, RunSettings s) throws IOException {
        Runner.Status st = r.status();
        StringBuilder sb = new StringBuilder();
        sb.append("단계 ").append(s.stage()).append(", 나눔 ").append(s.split()).append(", 설정 ").append(s.version()).append('\n');
        List<String> vs = st.plan().stream().map(x -> x.variant().id()).distinct().toList();
        long qs = st.plan().stream().map(x -> x.question().id()).distinct().count();
        sb.append("판 ").append(vs.size()).append("개(").append(String.join(" ", vs)).append("), 문항 ").append(qs).append("개, 회차 ")
                .append(s.select().reps()).append(", 블록 ").append(s.blockSize()).append(", 동시 ").append(s.concurrency()).append('\n');
        sb.append("모델: ").append(s.models().values().stream().sorted((a, b) -> a.model().compareTo(b.model()))
                .map(m -> m.model() + "(max_tokens " + m.maxTokens() + ", thinking " + m.thinking() + ", effort " + (m.effort() == null ? "보내지 않음" : m.effort()) + ")")
                .collect(Collectors.joining(", "))).append(", 턴 상한 ").append(s.maxTurns()).append('\n');
        sb.append("캐시: 시스템 글 ").append(s.cache().system() ? "켬" : "끔").append(", 대화 ").append(s.cache().conversation() ? "켬" : "끔")
                .append(", 유지 ").append(s.cache().ttl()).append("; 재시도 ").append(s.retry().maxAttempts()).append("번(").append(s.retry().baseSeconds())
                .append("초부터 두 배, 많아야 ").append(s.retry().maxSeconds()).append("초), 시간 초과 ").append(s.timeoutSeconds()).append("초\n");
        sb.append("실행 ").append(st.plan().size()).append("개: 끝난 것 ").append(st.done().size()).append(", 시작한 것 ").append(st.started().size())
                .append(", 남은 것 ").append(st.remaining()).append(". 쓴 돈 $").append(Runner.usd(st.spent())).append(" / 상한 $")
                .append(s.costCapUsd().toPlainString()).append(" (단가표 ").append(s.prices().date()).append(")\n");
        List<String> labels = new ArrayList<>();
        String last = null;
        int n = 0;
        for (Plan.RunSpec x : st.plan()) {
            String b = x.variant().id() + "#" + x.block();
            if (!b.equals(last)) {
                if (last != null) {
                    labels.add(last + "(" + n + ")");
                }
                last = b;
                n = 0;
            }
            n++;
        }
        if (last != null) {
            labels.add(last + "(" + n + ")");
        }
        sb.append("블록 차례: ").append(String.join(" ", labels.subList(0, Math.min(24, labels.size()))))
                .append(labels.size() > 24 ? " … (블록 " + labels.size() + "개)" : "").append('\n');
        sb.append("요청 지문:\n");
        for (Map.Entry<String, String> e : r.prints().entrySet()) {
            sb.append("  ").append(e.getKey()).append(' ').append(e.getValue()).append('\n');
        }
        st.problems().forEach(p -> sb.append("기록 문제: ").append(p).append('\n'));
        st.stale().forEach(x -> sb.append("요청 지문이 다른 기록: ").append(x.file()).append(" (판 ").append(x.variant()).append(", 달라진 칸 ")
                .append(String.join("·", x.changed())).append(")\n"));
        return sb.toString();
    }

    /** 찍는 줄을 화면과 파일에 함께 쓴다. */
    static PrintStream tee(PrintStream screen, OutputStream file) {
        return new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                screen.write(b);
                file.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                screen.write(b, off, len);
                file.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                screen.flush();
                file.flush();
            }
        }, true, StandardCharsets.UTF_8);
    }
}
