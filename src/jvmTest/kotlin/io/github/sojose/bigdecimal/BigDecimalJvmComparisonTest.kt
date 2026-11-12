/*
 * Copyright (c) 2026 Sören Johansson
 * SPDX-License-Identifier: MIT
 */

package io.github.sojose.bigdecimal

import java.math.BigDecimal as JavaBigDecimal
import java.math.BigInteger as JavaBigInteger
import java.math.MathContext as JavaMathContext
import java.math.RoundingMode as JavaRoundingMode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-only cross-check of the local implementation against the JDK's
 * battle-tested decimal implementation.
 */
class BigDecimalJvmComparisonTest {

    private val random = Random(20251108)

    private fun randomDecimal(): Pair<BigDecimal, JavaBigDecimal> {
        val unscaled = random.nextLong(-9_999_999_999L, 9_999_999_999L)
        val scale = random.nextInt(0, 7)
        return BigDecimal.valueOf(unscaled, scale) to
            JavaBigDecimal(JavaBigInteger.valueOf(unscaled), scale)
    }

    @Test
    fun exactArithmeticMatchesTheJdk() {
        repeat(500) {
            val (left, javaLeft) = randomDecimal()
            val (right, javaRight) = randomDecimal()

            assertEquals(
                javaLeft.add(javaRight).toPlainString(),
                left.add(right).toPlainString(),
            )
            assertEquals(
                javaLeft.subtract(javaRight).toPlainString(),
                left.subtract(right).toPlainString(),
            )
            if (!left.isZero() && !right.isZero()) {
                assertEquals(
                    javaLeft.multiply(javaRight).stripTrailingZeros().toPlainString(),
                    left.multiply(right).stripTrailingZeros().toPlainString(),
                )
            }
        }
    }

    @Test
    fun fixedScaleDivisionMatchesTheJdk() {
        val modes = listOf(
            RoundingMode.UP to JavaRoundingMode.UP,
            RoundingMode.DOWN to JavaRoundingMode.DOWN,
            RoundingMode.CEILING to JavaRoundingMode.CEILING,
            RoundingMode.FLOOR to JavaRoundingMode.FLOOR,
            RoundingMode.HALF_UP to JavaRoundingMode.HALF_UP,
            RoundingMode.HALF_DOWN to JavaRoundingMode.HALF_DOWN,
            RoundingMode.HALF_EVEN to JavaRoundingMode.HALF_EVEN,
        )

        repeat(400) {
            val (numerator, javaNumerator) = randomDecimal()
            var (denominator, javaDenominator) = randomDecimal()
            if (denominator.isZero()) {
                denominator = BigDecimal.ONE
                javaDenominator = JavaBigDecimal.ONE
            }
            val scale = random.nextInt(0, 7)
            val (mode, javaMode) = modes[random.nextInt(modes.size)]

            assertEquals(
                javaNumerator.divide(javaDenominator, scale, javaMode).toPlainString(),
                numerator.divide(denominator, scale, mode).toPlainString(),
            )
        }
    }

    @Test
    fun mathContextDivisionAndRoundingMatchTheJdk() {
        val contexts = listOf(
            MathContext(3, RoundingMode.HALF_EVEN) to
                JavaMathContext(3, JavaRoundingMode.HALF_EVEN),
            MathContext(7, RoundingMode.HALF_UP) to
                JavaMathContext(7, JavaRoundingMode.HALF_UP),
            MathContext(16, RoundingMode.DOWN) to
                JavaMathContext(16, JavaRoundingMode.DOWN),
        )

        repeat(300) {
            val (value, javaValue) = randomDecimal()
            val (context, javaContext) = contexts[random.nextInt(contexts.size)]

            assertEquals(
                javaValue.round(javaContext).stripTrailingZeros().toPlainString(),
                value.round(context).stripTrailingZeros().toPlainString(),
            )

            var (divisor, javaDivisor) = randomDecimal()
            if (divisor.isZero()) {
                divisor = BigDecimal.TWO
                javaDivisor = JavaBigDecimal.valueOf(2)
            }
            assertEquals(
                javaValue.divide(javaDivisor, javaContext).stripTrailingZeros().toPlainString(),
                value.divide(divisor, context).stripTrailingZeros().toPlainString(),
            )
        }
    }

    @Test
    fun stringFormsUlpAndPreferredScaleDivisionMatchTheJdk() {
        val samples = listOf(
            "0", "1", "-1", "12345", "12345.00", "0.0012", "1e3", "1e-6", "1e-7",
            "1.2345e6", "0.0000001", "999999999999999999999.000000001",
        )
        for (text in samples) {
            val local = BigDecimal.parse(text)
            val java = JavaBigDecimal(text)
            assertEquals(java.toString(), local.toString(), "toString($text)")
            assertEquals(
                java.toEngineeringString(),
                local.toEngineeringString(),
                "toEngineeringString($text)",
            )
            assertEquals(java.ulp().toPlainString(), local.ulp().toPlainString(), "ulp($text)")
        }

        val modes = listOf(
            RoundingMode.UNNECESSARY to JavaRoundingMode.UNNECESSARY,
            RoundingMode.HALF_UP to JavaRoundingMode.HALF_UP,
            RoundingMode.HALF_EVEN to JavaRoundingMode.HALF_EVEN,
        )
        repeat(300) {
            val (left, javaLeft) = randomDecimal()
            var (right, javaRight) = randomDecimal()
            if (right.isZero()) {
                right = BigDecimal.TWO
                javaRight = JavaBigDecimal.valueOf(2)
            }
            val (mode, javaMode) = modes[random.nextInt(modes.size)]

            val localResult = runCatching { left.divide(right, mode).toPlainString() }
                .getOrNull()
            val javaResult = runCatching {
                javaLeft.divide(javaRight, javaMode).toPlainString()
            }.getOrNull()

            assertEquals(
                javaResult,
                localResult,
                "divide(${left.toPlainString()}, ${right.toPlainString()}, $mode)",
            )
        }
    }

    @Test
    fun integerConversionAndExactRangeChecksMatchTheJdk() {
        val samples = listOf(
            "0", "1", "-1", "127", "-128", "32767", "-32768",
            "2147483647", "-2147483648", "9223372036854775807",
            "-9223372036854775808", "9223372036854775808",
            "12.9", "-12.9", "12.0",
        )
        for (text in samples) {
            val local = BigDecimal.parse(text)
            val java = JavaBigDecimal(text)

            assertEquals(java.toLong(), local.toLong(), "toLong($text)")
            assertEquals(java.toInt(), local.toInt(), "toInt($text)")
            assertEquals(java.toShort(), local.toShort(), "toShort($text)")
            assertEquals(java.toByte(), local.toByte(), "toByte($text)")

            assertEquals(
                runCatching { java.longValueExact() }.getOrNull(),
                runCatching { local.longValueExact() }.getOrNull(),
            )
            assertEquals(
                runCatching { java.intValueExact() }.getOrNull(),
                runCatching { local.intValueExact() }.getOrNull(),
            )
            assertEquals(
                runCatching { java.shortValueExact() }.getOrNull(),
                runCatching { local.shortValueExact() }.getOrNull(),
            )
            assertEquals(
                runCatching { java.byteValueExact() }.getOrNull(),
                runCatching { local.byteValueExact() }.getOrNull(),
            )
        }
    }

    @Test
    fun setScaleAndComparisonMatchTheJdk() {
        repeat(300) {
            val (value, javaValue) = randomDecimal()
            val scale = random.nextInt(-2, 7)
            val mode = RoundingMode.HALF_EVEN
            val javaMode = JavaRoundingMode.HALF_EVEN

            assertEquals(
                javaValue.setScale(scale, javaMode).toPlainString(),
                value.setScale(scale, mode).toPlainString(),
            )

            val (other, javaOther) = randomDecimal()
            assertEquals(javaValue.compareTo(javaOther).sign(), value.compareTo(other).sign())
            assertTrue(value == BigDecimal.parse(value.toPlainString()))
        }
    }

    private fun Int.sign(): Int = when {
        this < 0 -> -1
        this > 0 -> 1
        else -> 0
    }
}
