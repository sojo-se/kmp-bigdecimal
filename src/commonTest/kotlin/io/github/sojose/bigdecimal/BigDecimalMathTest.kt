/*
 * Copyright (c) 2026 Sören Johansson
 * SPDX-License-Identifier: MIT
 */

package io.github.sojose.bigdecimal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BigDecimalMathTest {

    private val mc = MathContext(30)

    @Test
    fun constantsAndRootsAreAccurate() {
        assertEquals("3.14159265358979323846264338328", BigDecimal.pi(mc).toPlainString())
        assertEquals("2.71828182845904523536028747135", BigDecimal.e(mc).toPlainString())
        assertEquals(
            "1.41421356237309504880168872421",
            BigDecimal.parse("2").sqrt(mc).toPlainString(),
        )
        assertEquals(
            "3",
            BigDecimal.parse("27").cbrt(mc).stripTrailingZeros().toPlainString(),
        )
        assertEquals(
            "2",
            BigDecimal.parse("1024").root(10, mc).stripTrailingZeros().toPlainString(),
        )
        assertEquals(
            "-2",
            BigDecimal.parse("-8").cbrt(mc).stripTrailingZeros().toPlainString(),
        )
    }

    @Test
    fun powersSupportExactIntegerAndDecimalExponents() {
        assertEquals("1024", BigDecimal.TWO.pow(10).toPlainString())
        assertEquals(
            "0.125",
            BigDecimal.TWO.pow(-3, mc).stripTrailingZeros().toPlainString(),
        )
        assertEquals(
            "1.41421356237309504880168872421",
            BigDecimal.TWO.pow(BigDecimal.HALF, mc).toPlainString(),
        )
        assertEquals(
            "-8",
            BigDecimal.valueOf(-2).pow(BigDecimal.valueOf(3), mc).toPlainString(),
        )
        assertFailsWith<ArithmeticException> {
            BigDecimal.ZERO.pow(Int.MIN_VALUE, mc)
        }
    }

    @Test
    fun exponentialAndLogarithmicFunctionsAreAccurate() {
        assertEquals("2.71828182845904523536028747135", BigDecimal.ONE.exp(mc).toPlainString())
        assertEquals(
            "0.693147180559945309417232121458",
            BigDecimal.TWO.ln(mc).toPlainString(),
        )
        assertEquals(
            "2.30258509299404568401799145468",
            BigDecimal.TEN.ln(mc).toPlainString(),
        )
        assertEquals(
            "3",
            BigDecimal.parse("1000").log10(mc).stripTrailingZeros().toPlainString(),
        )
        assertEquals(
            "10",
            BigDecimal.parse("1024").log2(mc).stripTrailingZeros().toPlainString(),
        )
        assertEquals(
            "3",
            BigDecimal.parse("8").log(BigDecimal.TWO, mc).stripTrailingZeros().toPlainString(),
        )
    }

    @Test
    fun trigonometricFunctionsAreAccurate() {
        assertEquals("0.841470984807896506652502321630", BigDecimal.ONE.sin(mc).toPlainString())
        assertEquals("0.540302305868139717400936607443", BigDecimal.ONE.cos(mc).toPlainString())
        assertEquals("1.55740772465490223050697480746", BigDecimal.ONE.tan(mc).toPlainString())
        assertEquals(
            "0.523598775598298873077107230547",
            BigDecimal.HALF.asin(mc).toPlainString(),
        )
        assertEquals(
            "1.04719755119659774615421446109",
            BigDecimal.HALF.acos(mc).toPlainString(),
        )
        assertEquals(
            "0.785398163397448309615660845820",
            BigDecimal.ONE.atan(mc).toPlainString(),
        )
        assertEquals(
            "0.785398163397448309615660845820",
            BigDecimal.atan2(BigDecimal.ONE, BigDecimal.ONE, mc).toPlainString(),
        )
        assertEquals(
            "5",
            BigDecimal.hypot(BigDecimal.valueOf(3), BigDecimal.valueOf(4), mc)
                .stripTrailingZeros()
                .toPlainString(),
        )
    }

    @Test
    fun hyperbolicFunctionsAreAccurate() {
        assertEquals("1.17520119364380145688238185060", BigDecimal.ONE.sinh(mc).toPlainString())
        assertEquals("1.54308063481524377847790562076", BigDecimal.ONE.cosh(mc).toPlainString())
        assertEquals("0.761594155955764888119458282605", BigDecimal.ONE.tanh(mc).toPlainString())
        assertEquals("0.881373587019543025232609324980", BigDecimal.ONE.asinh(mc).toPlainString())
        assertEquals("1.31695789692481670862504634731", BigDecimal.TWO.acosh(mc).toPlainString())
        assertEquals(
            "0.549306144334054845697622618461",
            BigDecimal.HALF.atanh(mc).toPlainString(),
        )
    }

    @Test
    fun angleConversionsWork() {
        assertEquals(
            "3.14159265358979323846264338328",
            BigDecimal.valueOf(180).toRadians(mc).toPlainString(),
        )
        assertEquals(
            "180",
            BigDecimal.pi(mc).toDegrees(mc).stripTrailingZeros().toPlainString(),
        )
    }

    @Test
    fun quotientRemainderAndIntegralDivisionFollowTruncatedSemantics() {
        assertEquals(
            "3",
            BigDecimal.valueOf(7).divideToIntegralValue(BigDecimal.TWO).toPlainString(),
        )
        assertEquals(
            "-3",
            BigDecimal.valueOf(-7).divideToIntegralValue(BigDecimal.TWO).toPlainString(),
        )
        assertEquals(
            "1",
            BigDecimal.valueOf(10).remainder(BigDecimal.valueOf(3)).toPlainString(),
        )
        assertEquals(
            "-1",
            BigDecimal.valueOf(-10).remainder(BigDecimal.valueOf(3)).toPlainString(),
        )

        val (quotient, remainder) = BigDecimal.valueOf(17).divideAndRemainder(BigDecimal.valueOf(5))
        assertEquals("3", quotient.toPlainString())
        assertEquals("2", remainder.toPlainString())
    }

    @Test
    fun movingTheDecimalPointAndScaleHelpersWork() {
        assertEquals(
            "1.2345",
            BigDecimal.parse("123.45").movePointLeft(2).toPlainString(),
        )
        assertEquals(
            "12345",
            BigDecimal.parse("123.45").movePointRight(2).toPlainString(),
        )
        assertEquals(
            "12345",
            BigDecimal.parse("123.45").scaleByPowerOfTen(2).toPlainString(),
        )
    }

    @Test
    fun integerConversionsTruncateTowardsZero() {
        assertEquals("12", BigDecimal.parse("12.9").toBigInteger().toString())
        assertEquals("-12", BigDecimal.parse("-12.9").toBigInteger().toString())
        assertEquals("12", BigDecimal.parse("12.9").toInt().toString())
        assertEquals("12", BigDecimal.parse("12.9").toLong().toString())
        assertEquals(12.9, BigDecimal.parse("12.9").toDouble())
        assertFailsWith<ArithmeticException> {
            BigDecimal.parse("12.9").toBigIntegerExact()
        }
    }

    @Test
    fun roundingHandlesADigitCarry() {
        assertEquals("10", BigDecimal.parse("9.99").round(MathContext(2)).toPlainString())
        assertEquals("1000", BigDecimal.parse("999.9").round(MathContext(2)).toPlainString())
    }

    @Test
    fun conciseCompanionConstructorsWork() {
        assertEquals("123.45", BigDecimal("123.45").toPlainString())
        assertEquals("123.45", BigDecimal(12345L, 2).toPlainString())
        assertEquals("123.45", BigDecimal(12345, 2).toPlainString())
    }

    @Test
    fun invalidDomainsThrow() {
        assertFailsWith<IllegalArgumentException> { BigDecimal.parse("-1").sqrt(mc) }
        assertFailsWith<ArithmeticException> { BigDecimal.parse("-4").root(2, mc) }
        assertFailsWith<IllegalArgumentException> { BigDecimal.ZERO.ln(mc) }
        assertFailsWith<IllegalArgumentException> { BigDecimal.TWO.log(BigDecimal.ONE, mc) }
        assertFailsWith<ArithmeticException> { BigDecimal.TWO.asin(mc) }
        assertFailsWith<ArithmeticException> { BigDecimal.TWO.acos(mc) }
        assertFailsWith<IllegalArgumentException> { BigDecimal.ONE.atanh(mc) }
    }

    @Test
    fun largeAndSmallMagnitudesRemainArbitraryPrecision() {
        val huge = BigDecimal.parse("1e100").add(BigDecimal.ONE)
        assertTrue(huge.toPlainString().startsWith("100000000000000000000"))
        assertTrue(huge.toPlainString().endsWith("0000000001"))

        assertEquals(
            "-10",
            BigDecimal.parse("0.0000000001").log10(mc).stripTrailingZeros().toPlainString(),
        )
    }
}
