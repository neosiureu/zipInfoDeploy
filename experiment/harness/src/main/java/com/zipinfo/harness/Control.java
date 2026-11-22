package com.zipinfo.harness;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 실행들이 함께 보는 것: 멈춤 신호, 이 단계에서 쓴 돈과 비용 상한(설계안 10.1), 기다리기(시험에서는 바꿔 낀다).
 * 멈춤 신호는 처음 것 하나만 선다. 서면 어느 실행도 새 요청을 보내지 않는다(돌던 실행은 다음 요청 앞에서 멈춤으로 적고 나온다).
 */
final class Control {
    /** RESUME_LATER: 한도·크레딧(풀리면 다시 run). STOP_AND_LOOK: 모르는 오류·멈춘 까닭·기록 어긋남·예외(보고 고친다). COST_CAP: 비용 상한. */
    enum Kind { RESUME_LATER, STOP_AND_LOOK, COST_CAP }

    record Stop(Kind kind, String message) {}

    /** 멈춤 신호가 서 있어 새 요청을 보내지 않고 나온다. */
    static final class Stopped extends RuntimeException {
        final Stop stop;

        Stopped(Stop stop) {
            super(stop.message());
            this.stop = stop;
        }
    }

    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final AtomicReference<Stop> stop = new AtomicReference<>();
    private final BigDecimal cap;
    private BigDecimal spent;
    final Sleeper sleeper;

    Control(BigDecimal cap, BigDecimal spent, Sleeper sleeper) {
        this.cap = cap;
        this.spent = spent;
        this.sleeper = sleeper;
    }

    /** 처음 멈춤이면 세우고 true. */
    boolean stop(Kind kind, String message) {
        return stop.compareAndSet(null, new Stop(kind, message));
    }

    Stop stop() {
        return stop.get();
    }

    synchronized void spend(BigDecimal usd) {
        spent = spent.add(usd);
    }

    synchronized BigDecimal spent() {
        return spent;
    }

    /** 실제 요청을 보내기 전: 멈춤 신호가 섰거나 쓴 돈이 상한에 닿았으면 Stopped. */
    void beforeLive() {
        Stop s = stop.get();
        if (s == null) {
            synchronized (this) {
                if (spent.compareTo(cap) >= 0) {
                    stop(Kind.COST_CAP, "비용 상한 $" + cap.toPlainString() + "에 닿았다(이 단계 기록의 캐시 있음 비용 $"
                            + spent.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString() + ")");
                }
            }
            s = stop.get();
        }
        if (s != null) {
            throw new Stopped(s);
        }
    }
}
