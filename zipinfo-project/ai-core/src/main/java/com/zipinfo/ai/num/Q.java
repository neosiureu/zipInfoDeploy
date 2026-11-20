package com.zipinfo.ai.num;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.regex.Pattern;

/** 정확한 분수. 06 규칙 2절과 설계안 9.3의 셈(분수로 셈하고 맨 끝에 한 번 반올림)에 쓴다. */
public final class Q implements Comparable<Q> {
    public static final Q ZERO = new Q(BigInteger.ZERO, BigInteger.ONE);
    public static final Q ONE = new Q(BigInteger.ONE, BigInteger.ONE);
    public static final Q HALF = new Q(BigInteger.ONE, BigInteger.TWO);
    private static final Pattern DEC = Pattern.compile("-?[0-9]+(\\.[0-9]+)?");

    private final BigInteger num;
    private final BigInteger den;

    private Q(BigInteger num, BigInteger den) {
        this.num = num;
        this.den = den;
    }

    public static Q of(BigInteger num, BigInteger den) {
        if (den.signum() == 0) {
            throw new ArithmeticException("분모가 0");
        }
        if (den.signum() < 0) {
            num = num.negate();
            den = den.negate();
        }
        BigInteger g = num.gcd(den);
        if (!g.equals(BigInteger.ONE) && g.signum() != 0) {
            num = num.divide(g);
            den = den.divide(g);
        }
        if (num.signum() == 0) {
            den = BigInteger.ONE;
        }
        return new Q(num, den);
    }

    public static Q of(long num, long den) {
        return of(BigInteger.valueOf(num), BigInteger.valueOf(den));
    }

    public static Q of(long n) {
        return of(BigInteger.valueOf(n), BigInteger.ONE);
    }

    /** "84.97"처럼 받은 소수 글을 그대로 분수로 읽는다(파이썬 Fraction("84.97")과 같다). */
    public static Q parse(String s) {
        if (!DEC.matcher(s).matches()) {
            throw new NumberFormatException("소수 꼴이 아니다");
        }
        BigDecimal d = new BigDecimal(s);
        BigInteger unscaled = d.unscaledValue();
        int scale = d.scale();
        return scale >= 0 ? of(unscaled, BigInteger.TEN.pow(scale)) : of(unscaled.multiply(BigInteger.TEN.pow(-scale)), BigInteger.ONE);
    }

    public Q add(Q o) { return of(num.multiply(o.den).add(o.num.multiply(den)), den.multiply(o.den)); }
    public Q sub(Q o) { return of(num.multiply(o.den).subtract(o.num.multiply(den)), den.multiply(o.den)); }
    public Q mul(Q o) { return of(num.multiply(o.num), den.multiply(o.den)); }
    public Q div(Q o) { return of(num.multiply(o.den), den.multiply(o.num)); }
    public Q neg() { return new Q(num.negate(), den); }
    public Q abs() { return num.signum() < 0 ? neg() : this; }
    public int signum() { return num.signum(); }

    /** 바닥(음수도 아래로). */
    public BigInteger floor() {
        BigInteger[] qr = num.divideAndRemainder(den);
        return qr[1].signum() < 0 ? qr[0].subtract(BigInteger.ONE) : qr[0];
    }

    /** 06 2절: ⌊x / U + 1/2⌋ × U. 음수에도 같은 식이다(−12.25를 0.1로 → −12.2). */
    public Q roundUnit(Q unit) {
        if (unit.signum() <= 0) {
            throw new IllegalArgumentException("단위는 양수");
        }
        return of(this.div(unit).add(HALF).floor(), BigInteger.ONE).mul(unit);
    }

    public boolean isInteger() { return den.equals(BigInteger.ONE); }

    public BigInteger numerator() { return num; }
    public BigInteger denominator() { return den; }

    /** 보이는 글: 소수 scale자리. 값이 그 자리로 딱 떨어져야 한다(반올림을 숨기지 않는다). */
    public BigDecimal toDecimal(int scale) {
        BigDecimal d = new BigDecimal(num).setScale(scale + 30, RoundingMode.UNNECESSARY)
                .divide(new BigDecimal(den), scale + 30, RoundingMode.UNNECESSARY);
        return d.setScale(scale, RoundingMode.UNNECESSARY);
    }

    /** 보이기 위한 근삿값(근거 ID가 붙지 않는 값에만 쓴다). */
    public BigDecimal approx(int scale) {
        return new BigDecimal(num).divide(new BigDecimal(den), scale, RoundingMode.HALF_UP);
    }

    @Override
    public int compareTo(Q o) { return num.multiply(o.den).compareTo(o.num.multiply(den)); }

    @Override
    public boolean equals(Object o) {
        return o instanceof Q q && num.equals(q.num) && den.equals(q.den);
    }

    @Override
    public int hashCode() { return Objects.hash(num, den); }

    @Override
    public String toString() { return isInteger() ? num.toString() : num + "/" + den; }
}
