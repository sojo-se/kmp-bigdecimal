/*
 * Copyright (c) 2026 Sören Johansson
 * SPDX-License-Identifier: MIT
 */

package io.github.sojose.bigdecimal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BigDecimalTest {

    @Test
    fun parsesPlainAndExponentNotation() {
        assertEquals("42", BigDecimal.parse("42").toPlainString())
        assertEquals("-0.125", BigDecimal.parse("-0.125").toPlainString())
        assertEquals("0.5", BigDecimal.parse(".5").toPlainString())
        assertEquals("1", BigDecimal.parse("1.").toPlainString())
        assertEquals("1200", BigDecimal.parse("1.2e3").toPlainString())
        assertEquals("0.0012", BigDecimal.parse("1.2e-3").toPlainString())
        assertEquals("-0.00100", BigDecimal.parse("-0.00100").toPlainString())
    }

    @Test
    fun addsDecimalsExactly() {
        val result = BigDecimal.parse("0.1").add(BigDecimal.parse("0.2"))

        assertEquals("0.3", result.toPlainString())
    }

    @Test
    fun operatorsDelegateToTheNamedOperations() {
        val one = BigDecimal.ONE
        val two = BigDecimal.TWO

        assertEquals("3", (one + two).toPlainString())
        assertEquals("-1", (one - two).toPlainString())
        assertEquals("2", (one * two).toPlainString())
        assertEquals("0.5", (one / two).toPlainString())
        assertEquals("1", (BigDecimal.valueOf(5) % two).toPlainString())
        assertEquals("-2", (-two).toPlainString())
    }

    @Test
    fun addsValuesWithDifferentScales() {
        val result = BigDecimal.parse("10").add(BigDecimal.parse("0.125"))

        assertEquals("10.125", result.toPlainString())
    }

    @Test
    fun subtractsValuesWithDifferentScales() {
        val result = BigDecimal.parse("10").subtract(BigDecimal.parse("3.5"))

        assertEquals("6.5", result.toPlainString())
    }

    @Test
    fun subtractsToNegativeResult() {
        val result = BigDecimal.parse("3").subtract(BigDecimal.parse("5"))

        assertEquals("-2", result.toPlainString())
    }

    @Test
    fun multipliesExactly() {
        assertEquals(
            "6.0",
            BigDecimal.parse("1.5").multiply(BigDecimal.parse("4")).toPlainString(),
        )
        assertEquals(
            "121932631112635269",
            BigDecimal.parse("123456789")
                .multiply(BigDecimal.parse("987654321"))
                .toPlainString(),
        )
    }

    @Test
    fun multipliesLargeMultiLimbNumbers() {
        val result = BigDecimal.parse("9999999999999999")
            .multiply(BigDecimal.parse("9999999999999999"))

        assertEquals("99999999999999980000000000000001", result.toPlainString())
    }

    @Test
    fun dividendWithZeroLimbsIsHandledCorrectly() {
        val result = BigDecimal.parse("1000000000")
            .divide(BigDecimal.parse("2"), 0, RoundingMode.HALF_UP)

        assertEquals("500000000", result.toPlainString())
    }

    @Test
    fun dividesWithRequestedScale() {
        val result = BigDecimal.parse("1")
            .divide(BigDecimal.parse("3"), 16, RoundingMode.HALF_UP)

        assertEquals("0.3333333333333333", result.toPlainString())
    }

    @Test
    fun dividesExactlyAndCanStripTrailingZeroes() {
        val result = BigDecimal.parse("10")
            .divide(BigDecimal.parse("4"), 16, RoundingMode.HALF_UP)
            .stripTrailingZeros()

        assertEquals("2.5", result.toPlainString())
    }

    @Test
    fun divisionByZeroThrows() {
        assertFailsWith<ArithmeticException> {
            BigDecimal.ONE.divide(BigDecimal.ZERO, 4)
        }
    }

    @Test
    fun roundsHalfEvenAtTheRequestedScale() {
        assertEquals(
            "1.2",
            BigDecimal.parse("1.25").setScale(1, RoundingMode.HALF_EVEN).toPlainString(),
        )
        assertEquals(
            "1.4",
            BigDecimal.parse("1.35").setScale(1, RoundingMode.HALF_EVEN).toPlainString(),
        )
        assertEquals(
            "0.12",
            BigDecimal.parse("1")
                .divide(BigDecimal.parse("8"), 2, RoundingMode.HALF_EVEN)
                .toPlainString(),
        )
    }

    @Test
    fun supportsAllCommonRoundingDirections() {
        assertEquals(
            "0.13",
            BigDecimal.parse("1")
                .divide(BigDecimal.parse("8"), 2, RoundingMode.HALF_UP)
                .toPlainString(),
        )
        assertEquals(
            "-0.13",
            BigDecimal.parse("-1")
                .divide(BigDecimal.parse("8"), 2, RoundingMode.HALF_UP)
                .toPlainString(),
        )
        assertEquals(
            "-0.12",
            BigDecimal.parse("-1")
                .divide(BigDecimal.parse("8"), 2, RoundingMode.CEILING)
                .toPlainString(),
        )
        assertEquals(
            "-0.13",
            BigDecimal.parse("-1")
                .divide(BigDecimal.parse("8"), 2, RoundingMode.FLOOR)
                .toPlainString(),
        )
    }

    @Test
    fun roundsToSignificantDigits() {
        assertEquals(
            "123000",
            BigDecimal.parse("123456").round(MathContext(3)).toPlainString(),
        )
        assertEquals(
            "1000",
            BigDecimal.parse("999").round(MathContext(2)).toPlainString(),
        )
    }

    @Test
    fun dividesWithMathContext() {
        assertEquals(
            "0.33333",
            BigDecimal.parse("1")
                .divide(BigDecimal.parse("3"), MathContext(5))
                .toPlainString(),
        )
        assertEquals(
            "333.3",
            BigDecimal.parse("1000")
                .divide(BigDecimal.parse("3"), MathContext(4))
                .toPlainString(),
        )
        assertEquals(
            "0.003333",
            BigDecimal.parse("1")
                .divide(BigDecimal.parse("300"), MathContext(4))
                .toPlainString(),
        )
    }

    @Test
    fun keepsArbitraryPrecisionForLargeNumbers() {
        val result = BigDecimal.parse("9999999999999999").add(BigDecimal.ONE)

        assertEquals("10000000000000000", result.toPlainString())
    }

    @Test
    fun stripsOnlyInsignificantZeroes() {
        assertEquals(
            "2.5",
            BigDecimal.parse("2.5000").stripTrailingZeros().toPlainString(),
        )
        assertEquals(
            "1000",
            BigDecimal.parse("1000").stripTrailingZeros().toPlainString(),
        )
        assertEquals(
            "0",
            BigDecimal.parse("0.000").stripTrailingZeros().toPlainString(),
        )
    }

    @Test
    fun comparesNumericallyIgnoringScale() {
        val left = BigDecimal.parse("1.0")
        val right = BigDecimal.parse("1.00")

        assertEquals(0, left.compareTo(right))
        assertTrue(left == BigDecimal.parse("1.0"))
        assertTrue(left != right)
    }
}
