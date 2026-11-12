/*
 * Copyright (c) 2026 Sören Johansson
 * SPDX-License-Identifier: MIT
 */

package io.github.sojose.bigdecimal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Covers the Java-BigDecimal-style API surface and the extra convenience
 * functions (square, cube, squareRoot, cubeRoot, constants, ...).
 */
class BigDecimalApiTest {

    private val mc = MathContext(34)

    @Test
    fun squareAndCubeAreExactAndHaveContextAwareOverloads() {
        assertEquals("6.25", BigDecimal("2.5").square().toPlainString())
        assertEquals("15.625", BigDecimal("2.5").cube().toPlainString())
        assertEquals("1.44", BigDecimal("1.2").square(mc).toPlainString())
        assertEquals("1.728", BigDecimal("1.2").cube(mc).toPlainString())
        assertEquals("0.5", BigDecimal.TWO.reciprocal(mc).toPlainString())
    }

    @Test
    fun rootAliasesDelegateToTheCoreImplementation() {
        assertEquals(
            "1.414213562373095048801688724209698",
            BigDecimal.TWO.squareRoot(mc).toPlainString(),
        )
        assertEquals(
            "1.259921049894873164767210607278228",
            BigDecimal.TWO.cubeRoot(mc).toPlainString(),
        )
        assertEquals(
            "3",
            BigDecimal("81").nthRoot(4, mc).stripTrailingZeros().toPlainString(),
        )
    }

    @Test
    fun piConstantsHaveTheExpectedPrecision() {
        assertEquals("3.141592653589793238462643383279503", BigDecimal.PI.toPlainString())
        assertEquals("6.283185307179586476925286766559006", BigDecimal.TAU.toPlainString())
        assertEquals("1.570796326794896619231321691639751", BigDecimal.HALF_PI.toPlainString())
        assertEquals(
            "0.7853981633974483096156608458198757",
            BigDecimal.QUARTER_PI.toPlainString(),
        )
    }

    @Test
    fun logarithmicConstantsHaveTheExpectedPrecision() {
        assertEquals("2.718281828459045235360287471352662", BigDecimal.E.toPlainString())
        assertEquals("0.6931471805599453094172321214581766", BigDecimal.LN2.toPlainString())
        assertEquals("2.302585092994045684017991454684364", BigDecimal.LN10.toPlainString())
        assertEquals("1.442695040888963407359924681001892", BigDecimal.LOG2E.toPlainString())
        assertEquals("0.4342944819032518276511289189166051", BigDecimal.LOG10E.toPlainString())
    }

    @Test
    fun rootConstantsHaveTheExpectedPrecision() {
        assertEquals("1.414213562373095048801688724209698", BigDecimal.SQRT2.toPlainString())
        assertEquals("1.732050807568877293527446341505872", BigDecimal.SQRT3.toPlainString())
        assertEquals("0.7071067811865475244008443621048490", BigDecimal.SQRT1_2.toPlainString())
        assertEquals(
            "1.618033988749894848204586834365638",
            BigDecimal.GOLDEN_RATIO.toPlainString(),
        )
        assertEquals(
            "1.259921049894873164767210607278228",
            BigDecimal.CUBE_ROOT_2.toPlainString(),
        )
    }

    @Test
    fun angleConstantsHaveTheExpectedPrecision() {
        assertEquals(
            "57.29577951308232087679815481410517",
            BigDecimal.DEGREES_PER_RADIAN.toPlainString(),
        )
        assertEquals(
            "0.01745329251994329576923690768488613",
            BigDecimal.RADIANS_PER_DEGREE.toPlainString(),
        )
    }

    @Test
    fun constantFactoriesRespectTheRequestedContext() {
        val shortPi = BigDecimal.pi(MathContext(5))
        val shortE = BigDecimal.e(MathContext(5))
        val shortLn2 = BigDecimal.ln2(MathContext(5))
        val shortLn10 = BigDecimal.ln10(MathContext(5))

        assertEquals("3.1416", shortPi.toPlainString())
        assertEquals("2.7183", shortE.toPlainString())
        assertEquals("0.69315", shortLn2.toPlainString())
        assertEquals("2.3026", shortLn10.toPlainString())
    }

    @Test
    fun roundingDivisionUsesTheDividendScaleLikeJavaBigDecimal() {
        assertEquals(
            "25",
            BigDecimal("100").divide(BigDecimal("4"), RoundingMode.UNNECESSARY).toPlainString(),
        )
        assertEquals(
            "0.5",
            BigDecimal("1.0").divide(BigDecimal("2"), RoundingMode.UNNECESSARY).toPlainString(),
        )
        assertFailsWith<ArithmeticException> {
            BigDecimal.ONE.divide(BigDecimal.TWO, RoundingMode.UNNECESSARY)
        }
        // The preferred scale is zero, so HALF_UP rounds 0.5 to one.
        assertEquals(
            "1",
            BigDecimal.ONE.divide(BigDecimal.TWO, RoundingMode.HALF_UP).toPlainString(),
        )
    }

    @Test
    fun stringFormsDistinguishPlainScientificAndEngineeringNotation() {
        assertEquals("1000", BigDecimal.parse("1e3").toPlainString())
        assertEquals("1E+3", BigDecimal.parse("1e3").toString())
        assertEquals("1E-7", BigDecimal.parse("1e-7").toString())
        assertEquals("0.000001", BigDecimal.parse("1e-6").toString())
        assertEquals("12345.00", BigDecimal.parse("12345.00").toString())
        assertEquals("0E+3", BigDecimal.parse("0E+3").toString())

        assertEquals("12345", BigDecimal.parse("12345").toEngineeringString())
        assertEquals("0.0012", BigDecimal.parse("0.0012").toEngineeringString())
        assertEquals("1E+3", BigDecimal.parse("1e3").toEngineeringString())
        assertEquals("1.2345E+6", BigDecimal.parse("1.2345e6").toEngineeringString())
        assertEquals("100E-9", BigDecimal.parse("0.0000001").toEngineeringString())
    }

    @Test
    fun setScaleHasAnExactOneArgumentOverload() {
        assertEquals("1.20", BigDecimal("1.2").setScale(2).toPlainString())
        assertFailsWith<ArithmeticException> {
            BigDecimal("1.234").setScale(2)
        }
        assertEquals(
            "1.23",
            BigDecimal("1.234").setScale(2, RoundingMode.HALF_UP).toPlainString(),
        )
    }

    @Test
    fun roundingHelpersExposeFloorCeilingTruncationAndFraction() {
        assertEquals("2", BigDecimal("2.9").floor().toPlainString())
        assertEquals("-3", BigDecimal("-2.9").floor().toPlainString())
        assertEquals("3", BigDecimal("2.1").ceil().toPlainString())
        assertEquals("-2", BigDecimal("-2.1").ceil().toPlainString())
        assertEquals("2", BigDecimal("2.9").truncate().toPlainString())
        assertEquals("-2", BigDecimal("-2.9").truncate().toPlainString())
        assertEquals("0.9", BigDecimal("2.9").fractionalPart().toPlainString())
        assertEquals("-0.9", BigDecimal("-2.9").fractionalPart().toPlainString())
    }

    @Test
    fun ulpAndPlusFollowTheStandardDecimalApi() {
        assertEquals("0.01", BigDecimal("1.20").ulp().toPlainString())
        assertEquals("1", BigDecimal.ZERO.ulp().toPlainString())
        assertEquals("1.25", BigDecimal("1.25").plus().toPlainString())
        assertEquals(
            "1.3",
            BigDecimal("1.25").plus(MathContext(2, RoundingMode.HALF_UP)).toPlainString(),
        )
    }

    @Test
    fun integerConversionsSupportTruncationAndExactRangeChecks() {
        assertEquals(12, BigDecimal("12.9").intValue())
        assertEquals(12L, BigDecimal("12.9").longValue())
        assertEquals(12, BigDecimal("12.9").intValue())
        assertEquals(1.0e100, BigDecimal("1e100").toDouble())
        assertEquals(1.0e-100, BigDecimal("1e-100").toDouble())
        assertEquals(12, BigDecimal("12.0").intValueExact())
        assertEquals(12L, BigDecimal("12.0").longValueExact())
        assertEquals(127.toByte(), BigDecimal("127").byteValueExact())
        assertEquals(32767.toShort(), BigDecimal("32767").shortValueExact())

        assertEquals(
            Long.MIN_VALUE,
            BigDecimal("9223372036854775808").longValue(),
        )
        assertFailsWith<ArithmeticException> {
            BigDecimal("12.9").intValueExact()
        }
        assertFailsWith<ArithmeticException> {
            BigDecimal("128").byteValueExact()
        }
        assertFailsWith<ArithmeticException> {
            BigDecimal("2147483648").intValueExact()
        }
        assertFailsWith<ArithmeticException> {
            BigDecimal("9223372036854775808").longValueExact()
        }
    }

    @Test
    fun quotientAndRemainderOverloadsAcceptAContext() {
        assertEquals(
            "3",
            BigDecimal.valueOf(10).divideToIntegralValue(BigDecimal.THREE, MathContext(1))
                .toPlainString(),
        )
        assertEquals(
            "1",
            BigDecimal.valueOf(10).remainder(BigDecimal.THREE, MathContext(1)).toPlainString(),
        )
        assertEquals(
            "2",
            BigDecimal.valueOf(17)
                .divideAndRemainder(BigDecimal.valueOf(5), MathContext(1))
                .second
                .toPlainString(),
        )
    }

    @Test
    fun legacyRoundingConstantsAndIntegerOverloadsWork() {
        assertEquals(0, BigDecimal.ROUND_UP)
        assertEquals(4, BigDecimal.ROUND_HALF_UP)
        assertEquals(7, BigDecimal.ROUND_UNNECESSARY)
        assertEquals(4, RoundingMode.HALF_UP.toInt())
        assertEquals(RoundingMode.HALF_EVEN, RoundingMode.fromInt(6))

        assertEquals(
            "1.23",
            BigDecimal("1.234").setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString(),
        )
        assertEquals(
            "1",
            BigDecimal.ONE.divide(BigDecimal.TWO, BigDecimal.ROUND_HALF_UP).toPlainString(),
        )
        assertEquals(
            "0.33",
            BigDecimal.ONE.divide(BigDecimal.THREE, 2, BigDecimal.ROUND_HALF_UP)
                .toPlainString(),
        )
    }

    @Test
    fun bigIntegerHelpersSupportDivisionNumberTheoryAndConversions() {
        val left = BigInteger.parse("48")
        val right = BigInteger.parse("18")

        assertEquals("2", left.divide(right).toString())
        assertEquals("12", left.remainder(right).toString())
        assertEquals("6", left.gcd(right).toString())
        assertEquals("144", left.lcm(right).toString())
        assertEquals("18", left.min(right).toString())
        assertEquals("48", left.max(right).toString())
        assertEquals(48L, left.toLong())
        assertEquals(48, left.toInt())
        assertEquals(48.0, left.toDouble())
    }

    @Test
    fun factoriesAndExtensionsCoverCommonConstructionPaths() {
        assertEquals("1.5", BigDecimal(1.5).toPlainString())
        assertEquals("1.5", BigDecimal.valueOf(1.5).toPlainString())
        assertEquals("2.5", "2.5".toBigDecimal().toPlainString())
        assertEquals("7", 7.toBigDecimal().toPlainString())
        assertEquals("8", bigDecimalOf(8L).toPlainString())
        assertEquals("123.45", BigInteger.parse("12345").toBigDecimal(2).toPlainString())

        assertEquals("1.234", BigDecimal("1.2344", MathContext(4)).toPlainString())
        assertTrue(BigDecimal("9.999").round(MathContext(3)).compareTo(BigDecimal.TEN) == 0)
    }
}
