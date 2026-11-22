package com.zipinfo.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.zipinfo.ai.loop.QuestionLoop;
import com.zipinfo.ai.loop.RunRecord;
import com.zipinfo.ai.model.CachePolicy;
import com.zipinfo.ai.model.ModelClient;
import com.zipinfo.ai.model.ModelError;
import com.zipinfo.ai.model.ModelSettings;
import com.zipinfo.ai.reflex.Setup;
import com.zipinfo.ai.reflex.Variant;
import com.zipinfo.ai.tools.RunState;
import com.zipinfo.ai.tools.Tools;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 하네스의 돌리기(설계안 10.1, 01 하네스 규칙 190~213줄). 기록 폴더 하나(files.out/stage)가 단계 하나다.
 * <ol>
 * <li>계획(Plan)을 짜고 폴더의 기록과 대 본다. 기록의 요청 지문이 지금 지문과 다르면(01 200줄) 아무 요청도 보내지 않고 멈춘다.
 *     끝난 실행도 본다. 그 판의 실행을 모두 처음부터 다시 돌려야 하기 때문이다(옛 기록은 사람이 다른 폴더로 옮긴다).</li>
 * <li>끝나지 않은 실행을 계획 차례대로 concurrency개씩 돌린다. 기록이 있는 실행은 기록으로 대화를 되살려 멈춘 요청부터 잇는다.</li>
 * <li>멈춤: RESUME_LATER(한도·크레딧), STOP_AND_LOOK(모르는 오류, reasoning_extraction·모르는 멈춘 까닭, 재시도를 다 쓴 요청이 나중에도
 *     같은 오류, 기록 어긋남, 예외), COST_CAP(비용 상한). 서면 새 요청을 보내지 않는다. 다시 run하면 이어 돈다.</li>
 * <li>재시도를 다 쓴 실행은 멈춤으로 적고 줄 맨 뒤에 다시 넣는다(01 196줄 "나중에 멈춘 요청부터"). 진전 없이 또 같은 오류로 다 쓰면 멈추고 본다.
 *     오류가 바뀌어도 진전 없이 세 번째로 다 쓰면 멈춘다(끝없이 돌지 않게).</li>
 * </ol>
 * 실패로 세는 것은 고리가 끝낸 꼴(FAILED_…)뿐이다. 모델 오류로 멈춘 실행은 실패가 아니라 끝나지 않은 실행이다(01 196줄).
 */
public final class Runner {
    /** 모델마다 실제 부르기를 만든다(Main은 Anthropic, 시험은 가짜 서버). */
    public interface Clients {
        ModelClient of(ModelSettings model, CachePolicy cache, Duration timeout);
    }

    /** 지문이 다른 기록: 파일, 판, 달라진 칸. */
    public record Stale(String file, String variant, List<String> changed) {}

    /** 계획과 기록을 대 본 것. problems: 판·문항이 없거나 나눔이 다른 기록, 읽지 못한 파일. */
    public record Status(List<Plan.RunSpec> plan, Set<String> done, Set<String> started, List<Stale> stale, List<String> problems,
                         BigDecimal spent) {
        public int remaining() {
            return (int) plan.stream().filter(r -> !done.contains(r.id())).count();
        }
    }

    public record Result(Status before, int done, int remaining, Control.Stop stop, BigDecimal spent) {}

    private final Inputs in;
    private final RunSettings s;
    private final Path dir;
    private final Clients clients;
    private final Control.Sleeper sleeper;
    private final PrintStream log;
    private final Map<String, Variant> variants = new LinkedHashMap<>();
    private final Map<String, Question> questions = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> parts = new LinkedHashMap<>();
    private final Map<String, String> prints = new LinkedHashMap<>();
    private final Map<String, ModelClient> live = new HashMap<>();

    public Runner(Inputs in, RunSettings s, Path dir, Clients clients, Control.Sleeper sleeper, PrintStream log) {
        this.in = in;
        this.s = s;
        this.dir = dir;
        this.clients = clients;
        this.sleeper = sleeper;
        this.log = log;
        for (Variant v : in.variants()) {
            variants.put(v.id(), v);
            Map<String, Object> p = Fingerprint.parts(v, setup(v), s.model(v.model()), s.cache(), s.maxTurns(), in.digests());
            parts.put(v.id(), p);
            prints.put(v.id(), Fingerprint.of(p));
        }
        in.questions().forEach(q -> questions.put(q.id(), q));
    }

    private Setup setup(Variant v) {
        return Setup.of(v, in.data(), in.cards(), in.lists(), in.memory(), s.maxTurns());
    }

    /** 판 ID → 요청 지문(계획 글에 찍는다). */
    public Map<String, String> prints() {
        return prints;
    }

    public Path dir() {
        return dir;
    }

    public Status status() throws IOException {
        List<Plan.RunSpec> plan = Plan.of(s, in.variants(), in.questions(), in.metas());
        Set<String> done = new HashSet<>();
        Set<String> started = new HashSet<>();
        List<Stale> stale = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        BigDecimal spent = BigDecimal.ZERO;
        for (Path f : files()) {
            String name = f.getFileName().toString();
            RunFile rf;
            try {
                rf = RunFile.read(f, false);
            } catch (IOException e) {
                problems.add(name + ": " + e.getMessage());
                continue;
            }
            JsonNode h = rf.header();
            if (h == null) {
                continue;
            }
            String vid = h.path("variant").path("id").asText();
            Variant v = variants.get(vid);
            Question q = questions.get(h.path("question").path("id").asText());
            if (v == null || q == null) {
                problems.add(name + ": 판이나 문항이 지금 파일에 없다");
            } else if (!s.split().equals(h.path("split").asText())) {
                problems.add(name + ": 나눔(" + h.path("split").asText() + ")이 설정(" + s.split() + ")과 다르다");
            } else if (!Fingerprint.run(prints.get(vid), q).equals(h.path("fingerprint").asText())) {
                stale.add(new Stale(name, vid, changed(h, parts.get(vid), q)));
            }
            String model = h.path("variant").path("model").asText();
            String ttl = h.path("cache").path("ttl").asText("5m");
            for (JsonNode e : rf.loopEvents()) {
                if ("response".equals(e.path("type").asText())) {
                    spent = spent.add(s.prices().withCache(model, Prices.use(e, ttl)));
                }
            }
            String id = h.path("run_id").asText();
            (rf.complete() ? done : started).add(id);
        }
        started.removeAll(done);
        return new Status(plan, done, started, stale, problems, spent);
    }

    private List<Path> files() throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> st = Files.list(dir)) {
            return st.filter(p -> p.getFileName().toString().endsWith(".jsonl")).sorted().toList();
        }
    }

    private static List<String> changed(JsonNode header, Map<String, Object> now, Question q) throws IOException {
        List<String> out = new ArrayList<>();
        JsonNode was = header.path("fingerprint_parts");
        JsonNode cur = Json.M.readTree(Json.write(now));
        Set<String> keys = new java.util.TreeSet<>();
        was.fieldNames().forEachRemaining(keys::add);
        cur.fieldNames().forEachRemaining(keys::add);
        for (String k : keys) {
            if (!Objects.equals(was.get(k), cur.get(k))) {
                out.add(k);
            }
        }
        JsonNode hq = header.path("question");
        if (!hq.path("key").asText().equals(q.key()) || !hq.path("text").asText().equals(q.text())) {
            out.add("question");
        }
        return out;
    }

    public Result run() throws IOException, InterruptedException {
        Files.createDirectories(dir);
        try (FileChannel ch = FileChannel.open(dir.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock = ch.tryLock();
            if (lock == null) {
                Control.Stop busy = new Control.Stop(Control.Kind.STOP_AND_LOOK, "다른 하네스가 이 단계(" + s.stage() + ")를 돌리고 있다");
                log.println(busy.message());
                return new Result(null, 0, 0, busy, BigDecimal.ZERO);
            }
            try {
                return runLocked();
            } finally {
                lock.release();
            }
        }
    }

    private Result runLocked() throws IOException, InterruptedException {
        Status st = status();
        if (!st.problems().isEmpty() || !st.stale().isEmpty()) {
            st.problems().forEach(p -> log.println("기록 문제: " + p));
            for (Stale x : st.stale()) {
                log.println("요청 지문이 다른 기록: " + x.file() + " (판 " + x.variant() + ", 달라진 칸 " + String.join("·", x.changed()) + ")");
            }
            Control.Stop stop = new Control.Stop(Control.Kind.STOP_AND_LOOK,
                    "기록이 지금 설정·코드와 맞지 않는다. 아무 요청도 보내지 않았다. 01 200줄대로 그 판의 기록을 모두 다른 폴더로 옮기고 처음부터 다시 돌린다");
            log.println(stop.message());
            return new Result(st, st.done().size(), st.remaining(), stop, st.spent());
        }
        if (st.spent().compareTo(s.costCapUsd()) >= 0 && st.remaining() > 0) {
            Control.Stop stop = new Control.Stop(Control.Kind.COST_CAP, "이 단계 기록의 비용 $" + usd(st.spent()) + "이 상한 $"
                    + s.costCapUsd().toPlainString() + "에 닿아 있다. 상한을 올리면 이어 돈다");
            log.println(stop.message());
            return new Result(st, st.done().size(), st.remaining(), stop, st.spent());
        }
        Control control = new Control(s.costCapUsd(), st.spent(), sleeper);
        Deque<Plan.RunSpec> queue = new ArrayDeque<>();
        for (Plan.RunSpec r : st.plan()) {
            if (!st.done().contains(r.id())) {
                queue.addLast(r);
            }
        }
        int total = st.plan().size();
        AtomicInteger finished = new AtomicInteger(st.done().size());
        log.println("돌리기: 단계 " + s.stage() + ", 실행 " + total + "개 가운데 끝난 것 " + st.done().size() + ", 남은 것 " + queue.size()
                + ", 쓴 돈 $" + usd(st.spent()) + " / 상한 $" + s.costCapUsd().toPlainString() + ", 동시 " + s.concurrency());
        ExecutorService pool = Executors.newFixedThreadPool(s.concurrency());
        for (int i = 0; i < s.concurrency(); i++) {
            pool.submit(() -> {
                while (control.stop() == null) {
                    Plan.RunSpec r;
                    synchronized (queue) {
                        r = queue.pollFirst();
                    }
                    if (r == null) {
                        return;
                    }
                    try {
                        if (runOne(r, control, finished, total)) {
                            synchronized (queue) {
                                queue.addLast(r);
                            }
                        }
                    } catch (Throwable t) {
                        control.stop(Control.Kind.STOP_AND_LOOK, r.id() + ": 하네스 오류 " + t);
                    }
                }
            });
        }
        pool.shutdown();
        while (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
            // 실행이 길면 기다린다
        }
        Status after = status();
        Control.Stop stop = control.stop();
        if (stop != null) {
            log.println("멈춤(" + stop.kind() + "): " + stop.message());
        }
        log.println("끝: 실행 " + total + "개 가운데 끝난 것 " + after.done().size() + ", 남은 것 " + after.remaining() + ", 쓴 돈 $" + usd(control.spent()));
        return new Result(st, after.done().size(), after.remaining(), stop, control.spent());
    }

    static String usd(BigDecimal x) {
        return x.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private ModelClient liveClient(ModelSettings ms) {
        synchronized (live) {
            return live.computeIfAbsent(ms.model(), m -> clients.of(ms, s.cache(), Duration.ofSeconds(s.timeoutSeconds())));
        }
    }

    /** 실행 하나를 끝이나 멈춤까지 돌린다. 재시도를 다 써서 줄 뒤에서 다시 돌릴 것이면 true(기록 파일을 닫은 뒤에 넣는다). */
    private boolean runOne(Plan.RunSpec r, Control control, AtomicInteger finished, int total) throws IOException {
        if (!r.question().split().equals(s.split())) {
            throw new IllegalStateException("나눔이 다른 문항을 돌리려 했다: " + r.id());
        }
        Path f = dir.resolve(r.id() + ".jsonl");
        RunFile rf = RunFile.read(f, true);
        if (rf.complete()) {
            return false;
        }
        boolean again = false;
        Variant v = r.variant();
        Setup setup = setup(v);
        ModelSettings ms = s.model(v.model());
        try (RunFile.Writer w = new RunFile.Writer(f)) {
            if (rf.header() == null) {
                w.write(header(r, setup, ms));
            } else {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "resume");
                m.put("replay_responses", rf.responses().size());
                m.put("replay_events", rf.loopEvents().size());
                w.write(m);
            }
            Replayer rp = new Replayer(rf.loopEvents(), w::write);
            RunClient rc = new RunClient(liveClient(ms), rf.responses(), rp, control, s.retry(), w::write, s.prices(), v.model(), s.cache().ttl());
            QuestionLoop loop = new QuestionLoop(rc, new Tools(in.data()), in.data(), setup.config());
            loop.record().onEvent(rp);
            String key = r.question().key();
            RunState state = new RunState(in.listings().get(key), in.metas().get(key));
            try {
                RunRecord rec = loop.start(setup.system(), setup.specs(), r.question().text(), state);
                if (!rp.done() || !rc.replayDone()) {
                    throw new Replayer.Mismatch("실행이 끝났는데 기록에 사건이 더 있다");
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "end");
                m.put("outcome", rec.outcome().name());
                m.put("turns", rec.turns());
                m.put("answer", rec.answer());
                w.write(m);
                log.println("[" + finished.incrementAndGet() + "/" + total + "] " + r.id() + " " + rec.outcome() + " (턴 " + rec.turns() + ", 쓴 돈 $"
                        + usd(control.spent()) + ")");
                if (rec.outcome() == RunRecord.Outcome.STOP_REASONING_EXTRACTION || rec.outcome() == RunRecord.Outcome.STOP_OTHER_STOP_REASON) {
                    control.stop(Control.Kind.STOP_AND_LOOK, r.id() + ": " + rec.outcome() + ". 문구나 멈춘 까닭을 보고 고친다(01 206·208줄)");
                }
            } catch (ModelError e) {
                switch (e.category()) {
                    case RESUME_LATER -> {
                        w.write(pause("resume_later", e, null));
                        control.stop(Control.Kind.RESUME_LATER, r.id() + ": " + e.getMessage() + ". 한도나 크레딧이 풀리면 다시 run한다(멈춘 요청부터 잇는다)");
                    }
                    case STOP_AND_LOOK -> {
                        w.write(pause("stop_and_look", e, null));
                        control.stop(Control.Kind.STOP_AND_LOOK, r.id() + ": 모르는 오류 " + e.status() + " " + e.type() + " " + e.getMessage());
                    }
                    case RETRY -> {
                        List<JsonNode> prev = rc.liveResponses() == 0 ? rf.exhaustedSinceLastResponse() : List.of();
                        boolean same = !prev.isEmpty() && signature(prev.get(prev.size() - 1).path("error")).equals(e.status() + " " + e.type());
                        w.write(pause("retry_exhausted", e, null));
                        if (same || prev.size() >= 2) {
                            control.stop(Control.Kind.STOP_AND_LOOK, r.id() + ": 재시도를 다 쓴 요청을 나중에 다시 보냈는데 또 다 썼다(" + e.status() + " "
                                    + e.type() + ", 01 196줄)");
                        } else {
                            log.println(r.id() + ": 재시도를 다 썼다(" + e.status() + " " + e.type() + "). 줄 뒤에서 멈춘 요청부터 다시 잇는다");
                            again = true;
                        }
                    }
                }
            } catch (Control.Stopped e) {
                w.write(pause("stopped", null, e.stop.kind().name()));
            } catch (Replayer.Mismatch e) {
                w.write(pause("replay_mismatch", null, e.getMessage()));
                control.stop(Control.Kind.STOP_AND_LOOK, r.id() + ": 기록으로 되살리다 어긋났다. " + e.getMessage());
            } catch (RuntimeException e) {
                w.write(pause("exception", null, e.toString()));
                control.stop(Control.Kind.STOP_AND_LOOK, r.id() + ": 예외 " + e);
            }
        }
        return again;
    }

    private static String signature(JsonNode error) {
        return error.path("status").asInt() + " " + error.path("type").asText();
    }

    private static Map<String, Object> pause(String reason, ModelError e, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "pause");
        m.put("reason", reason);
        if (e != null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("category", e.category().name());
            err.put("status", e.status());
            err.put("type", e.type());
            err.put("code", e.code());
            err.put("message", e.getMessage());
            m.put("error", err);
        }
        if (note != null) {
            m.put("note", note);
        }
        return m;
    }

    private Map<String, Object> header(Plan.RunSpec r, Setup setup, ModelSettings ms) {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("type", "run");
        h.put("run_id", r.id());
        h.put("stage", s.stage());
        h.put("split", s.split());
        h.put("variant", Fingerprint.json(r.variant()));
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("id", r.question().id());
        q.put("key", r.question().key());
        q.put("qtype", r.question().qtype());
        q.put("split", r.question().split());
        q.put("text", r.question().text());
        h.put("question", q);
        h.put("rep", r.rep());
        h.put("block", r.block());
        h.put("fingerprint", Fingerprint.run(prints.get(r.variant().id()), r.question()));
        h.put("fingerprint_parts", parts.get(r.variant().id()));
        h.put("system", setup.system());
        h.put("tools", Fingerprint.json(setup.specs()));
        h.put("tool_choice", "auto");
        h.put("model_settings", Fingerprint.json(ms));
        h.put("cache", Fingerprint.json(s.cache()));
        h.put("max_turns", s.maxTurns());
        h.put("prices", s.prices().toJson());
        h.put("settings_version", s.version());
        h.put("harness_code", in.digests().harnessCode());
        return h;
    }
}
