package com.zipinfo.ai.num;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class QTest {
    @Test
    void parseKeepsReceivedDecimal() {
        assertEquals(Q.of(8497, 100), Q.parse("84.97"));
        assertEquals(Q.of(8497, 100), Q.parse("84.970"));
        assertThrows(NumberFormatException.class, () -> Q.parse("1e3"));
    }

    @Test
    void roundUnitIsFloorPlusHalfEvenForNegatives() {
        Q tenth = Q.of(1, 10);
        assertEquals(Q.of(-122, 10), Q.of(-1225, 100).roundUnit(tenth));
        assertEquals(Q.of(123, 10), Q.of(1225, 100).roundUnit(tenth));
        assertEquals(Q.of(3), Q.of(5, 2).roundUnit(Q.ONE));
        assertEquals(Q.of(-2), Q.of(-5, 2).roundUnit(Q.ONE));
    }

    @Test
    void decimalShowsExactValuesOnly() {
        assertEquals(new BigDecimal("25.41"), Q.of(2541, 100).toDecimal(2));
        assertThrows(ArithmeticException.class, () -> Q.of(1, 3).toDecimal(2));
    }
}
