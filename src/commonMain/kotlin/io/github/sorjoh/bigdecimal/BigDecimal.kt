/*
 * Copyright (c) 2026 Sören Johansson
 * SPDX-License-Identifier: MIT
 */

package io.github.sorjoh.bigdecimal

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.sqrt as floatingSqrt

/**
 * An immutable, dependency-free arbitrary-precision decimal value.
 *
 * The represented value is `unscaledValue * 10^(-scale)`. A negative scale is
 * allowed, so `BigDecimal(1, -3)` represents `1000`.
 *
 * Addition, subtraction and multiplication are exact. Division, roots and the
 * transcendental functions use a [MathContext] (or an explicit scale) and round
 * according to its [RoundingMode].
 *
 * The implementation is pure Kotlin and uses only the Kotlin standard library,
 * so it works on Android, iOS, JVM, JavaScript and WebAssembly without any
 * platform-specific code or third-party dependency.
 */
class BigDecimal internal constructor(
    internal val unscaled: BigInteger,
    val scale: Int,
) : Comparable<BigDecimal> {

    /** Number of digits in the unscaled value. Zero has precision 1. */
    val precision: Int
        get() = unscaled.digitCount()

    val signum: Int
        get() = unscaled.signum

    fun isZero(): Boolean = unscaled.isZero()

    /** True when this value has no non-zero fractional part. */
    fun isInteger(): Boolean = stripTrailingZeros().scale <= 0

    fun unscaledValue(): BigInteger = unscaled

    fun abs(): BigDecimal = if (signum >= 0) this else negate()

    fun abs(context: MathContext): BigDecimal = abs().round(context)

    fun negate(): BigDecimal =
        if (isZero()) this else BigDecimal(unscaled.negate(), scale)

    fun negate(context: MathContext): BigDecimal = negate().round(context)

    /** Operator form of [negate]. */
    operator fun unaryMinus(): BigDecimal = negate()

    /** Unary plus, matching the familiar BigDecimal API. */
    fun plus(): BigDecimal = this

    /** Unary plus rounded to [context]. */
    fun plus(context: MathContext): BigDecimal = round(context)

    /** Operator form of unary plus. */
    operator fun unaryPlus(): BigDecimal = this

    fun isNegative(): Boolean = signum < 0

    fun isPositive(): Boolean = signum > 0

    fun isOne(): Boolean = compareTo(ONE) == 0

    fun add(other: BigDecimal): BigDecimal {
        val targetScale = maxOf(scale, other.scale)
        val left = if (scale == targetScale) {
            unscaled
        } else {
            unscaled.multiply(BigInteger.pow10(targetScale - scale))
        }
        val right = if (other.scale == targetScale) {
            other.unscaled
        } else {
            other.unscaled.multiply(BigInteger.pow10(targetScale - other.scale))
        }
        return BigDecimal(left.add(right), targetScale)
    }

    fun add(other: BigDecimal, context: MathContext): BigDecimal =
        add(other).round(context)

    fun subtract(other: BigDecimal): BigDecimal = add(other.negate())

    fun subtract(other: BigDecimal, context: MathContext): BigDecimal =
        subtract(other).round(context)

    /** Operator form of [add]. */
    operator fun plus(other: BigDecimal): BigDecimal = add(other)

    /** Operator form of [subtract]. */
    operator fun minus(other: BigDecimal): BigDecimal = subtract(other)

    fun multiply(other: BigDecimal): BigDecimal {
        val resultScale = scale.toLong() + other.scale.toLong()
        require(resultScale in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "Scale overflow"
        }
        if (isZero() || other.isZero()) {
            return BigDecimal(BigInteger.ZERO, resultScale.toInt())
        }
        return BigDecimal(
            unscaled.multiply(other.unscaled),
            resultScale.toInt(),
        )
    }

    fun multiply(other: BigDecimal, context: MathContext): BigDecimal =
        multiply(other).round(context)

    /** Operator form of [multiply]. */
    operator fun times(other: BigDecimal): BigDecimal = multiply(other)

    /** Exact square, `this * this`. */
    fun square(): BigDecimal = multiply(this)

    /** Square rounded to [context]. */
    fun square(context: MathContext): BigDecimal = square().round(context)

    /** Exact cube, `this * this * this`. */
    fun cube(): BigDecimal = multiply(this).multiply(this)

    /** Cube rounded to [context]. */
    fun cube(context: MathContext): BigDecimal = cube().round(context)

    /** Multiplicative inverse, `1 / this`. */
    fun reciprocal(context: MathContext = MathContext.DECIMAL128): BigDecimal =
        ONE.divide(this, context)

    /**
     * Divides by [other] and returns a value with exactly [scale] digits after
     * the decimal point, using [roundingMode] when the quotient is not exact.
     */
    fun divide(
        other: BigDecimal,
        scale: Int,
        roundingMode: RoundingMode = RoundingMode.HALF_UP,
    ): BigDecimal {
        if (other.isZero()) throw ArithmeticException("Division by zero")
        if (isZero()) return BigDecimal(BigInteger.ZERO, scale)


        val shift = other.scale.toLong() - this.scale.toLong() + scale.toLong()
        var numerator = unscaled.abs()
        var denominator = other.unscaled.abs()

        if (shift >= 0) {
            require(shift <= Int.MAX_VALUE.toLong()) { "Scale overflow" }
            numerator = numerator.multiply(BigInteger.pow10(shift.toInt()))
        } else {
            val denominatorShift = -shift
            require(denominatorShift <= Int.MAX_VALUE.toLong()) { "Scale overflow" }
            denominator = denominator.multiply(BigInteger.pow10(denominatorShift.toInt()))
        }

        val (quotient, remainder) = numerator.divideAndRemainder(denominator)
        val negative = (unscaled.signum < 0) xor (other.unscaled.signum < 0)
        val roundedQuotient = if (
            shouldIncrement(quotient, remainder, denominator, roundingMode, negative)
        ) {
            quotient.add(BigInteger.ONE)
        } else {
            quotient
        }

        return BigDecimal(
            if (negative && !roundedQuotient.isZero()) roundedQuotient.negate() else roundedQuotient,
            scale,
        )
    }

    /**
     * Divides by [other], keeping approximately [MathContext.precision]
     * significant digits.
     */
    fun divide(other: BigDecimal, context: MathContext): BigDecimal {
        if (other.isZero()) throw ArithmeticException("Division by zero")
        if (isZero()) return ZERO

        val leftDigits = unscaled.abs().digitCount()
        val rightDigits = other.unscaled.abs().digitCount()
        val exponent = leftDigits.toLong() - rightDigits.toLong() +
            other.scale.toLong() - scale.toLong()
        val workingScale = context.precision.toLong() + 3L - exponent
        require(workingScale in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "Scale overflow"
        }

        val rounded = divide(other, workingScale.toInt(), context.roundingMode)
            .round(context)
        if (rounded.isZero()) return rounded

        // Exact quotients such as 1 / 2 should not be padded with insignificant
        // zeroes up to the context precision. Keep those zeroes only when they
        // are part of an inexact, rounded result.
        val stripped = rounded.stripTrailingZeros()
        return if (
            stripped.precision <= context.precision &&
            stripped.multiply(other).compareTo(this) == 0
        ) {
            stripped
        } else {
            rounded
        }
    }

    /**
     * Convenience division with [MathContext.DECIMAL128]. Use the scale or
     * [MathContext] overload when you need control over the result precision.
     */
    fun divide(other: BigDecimal): BigDecimal = divide(other, MathContext.DECIMAL128)

    /** Operator form of the [MathContext.DECIMAL128] [divide] overload. */
    operator fun div(other: BigDecimal): BigDecimal = divide(other)

    /**
     * Divides by [other], using this value's scale as the result scale. This
     * mirrors `java.math.BigDecimal.divide(divisor, roundingMode)`.
     *
     * Use the `scale` overload when you want to choose the number of
     * fractional digits explicitly.
     */
    fun divide(other: BigDecimal, roundingMode: RoundingMode): BigDecimal =
        divide(other, scale, roundingMode)

    /** Legacy integer rounding-mode overload. */
    fun divide(other: BigDecimal, roundingMode: Int): BigDecimal =
        divide(other, RoundingMode.fromInt(roundingMode))

    /** Legacy integer rounding-mode overload. */
    fun divide(other: BigDecimal, scale: Int, roundingMode: Int): BigDecimal =
        divide(other, scale, RoundingMode.fromInt(roundingMode))

    /**
     * Integer part of `this / other`, truncated towards zero.
     */
    fun divideToIntegralValue(other: BigDecimal): BigDecimal =
        divide(other, 0, RoundingMode.DOWN)

    /** Integer part of `this / other`, then rounded to [context]. */
    fun divideToIntegralValue(other: BigDecimal, context: MathContext): BigDecimal =
        divideToIntegralValue(other).round(context)

    /**
     * Remainder of `this / other`. The result has the sign of the dividend,
     * matching the usual truncated-division convention.
     */
    fun remainder(other: BigDecimal): BigDecimal =
        subtract(divideToIntegralValue(other).multiply(other))

    /** Remainder computed with the integral quotient rounded to [context]. */
    fun remainder(other: BigDecimal, context: MathContext): BigDecimal {
        val quotient = divideToIntegralValue(other, context)
        return subtract(quotient.multiply(other))
    }

    /** Operator form of [remainder]. */
    operator fun rem(other: BigDecimal): BigDecimal = remainder(other)

    fun divideAndRemainder(other: BigDecimal): Pair<BigDecimal, BigDecimal> {
        val quotient = divideToIntegralValue(other)
        return quotient to subtract(quotient.multiply(other))
    }

    fun divideAndRemainder(
        other: BigDecimal,
        context: MathContext,
    ): Pair<BigDecimal, BigDecimal> {
        val quotient = divideToIntegralValue(other, context)
        return quotient to subtract(quotient.multiply(other))
    }

    /**
     * Returns a value with exactly [newScale] digits after the decimal point.
     * Throws if rounding is necessary, matching `java.math.BigDecimal`.
     */
    fun setScale(newScale: Int): BigDecimal = setScale(newScale, RoundingMode.UNNECESSARY)

    fun setScale(
        newScale: Int,
        roundingMode: RoundingMode,
    ): BigDecimal {
        if (newScale == scale) return this
        if (newScale > scale) {
            return BigDecimal(
                unscaled.multiply(BigInteger.pow10(newScale - scale)),
                newScale,
            )
        }
        return divide(ONE, newScale, roundingMode)
    }

    /** Legacy integer rounding-mode overload. */
    fun setScale(newScale: Int, roundingMode: Int): BigDecimal =
        setScale(newScale, RoundingMode.fromInt(roundingMode))

    fun round(context: MathContext): BigDecimal {
        if (isZero()) return this

        var precision = unscaled.digitCount()
        if (precision <= context.precision) return this

        var result = setScale(scale - (precision - context.precision), context.roundingMode)

        // Rounding 9.99 to two significant digits produces 10.0. That result
        // still has three digits in its unscaled value, so remove any carry
        // digit until the requested precision is respected.
        while (result.precision > context.precision) {
            val extraDigits = result.precision - context.precision
            result = result.setScale(result.scale - extraDigits, context.roundingMode)
            precision = result.precision
            if (precision <= context.precision) break
        }
        return result
    }

    /**
     * Removes insignificant trailing zeroes. For example, `2.5000` becomes
     * `2.5` and `1000` becomes `1E+3` internally (rendered as `1000` by
     * [toPlainString]).
     */
    fun stripTrailingZeros(): BigDecimal {
        if (isZero()) return ZERO

        var value = unscaled.abs()
        var resultScale = scale
        while (true) {
            val (quotient, remainder) = value.divideAndRemainder(BigInteger.TEN)
            if (!remainder.isZero()) break
            value = quotient
            resultScale--
        }

        if (unscaled.signum < 0) value = value.negate()
        return BigDecimal(value, resultScale)
    }

    /** Value of the least significant digit of this number. */
    fun ulp(): BigDecimal = BigDecimal(BigInteger.ONE, scale)

    /** Largest integer less than or equal to this value. */
    fun floor(): BigDecimal = setScale(0, RoundingMode.FLOOR)

    /** Smallest integer greater than or equal to this value. */
    fun ceil(): BigDecimal = setScale(0, RoundingMode.CEILING)

    /** Integer part, truncated towards zero. */
    fun truncate(): BigDecimal = setScale(0, RoundingMode.DOWN)

    /** Fractional part, `this - truncate()`. */
    fun fractionalPart(): BigDecimal = subtract(truncate())

    /** Multiplies by `10^-n`; useful for changing the decimal point position. */
    fun movePointLeft(n: Int): BigDecimal = withScale(scale.toLong() + n.toLong())

    /** Multiplies by `10^n`. */
    fun movePointRight(n: Int): BigDecimal = withScale(scale.toLong() - n.toLong())

    /** Multiplies by `10^n`. */
    fun scaleByPowerOfTen(n: Int): BigDecimal = movePointRight(n)

    /**
     * Renders the value without scientific notation.
     */
    fun toPlainString(): String {
        if (isZero()) {
            return if (scale > 0) "0." + "0".repeat(scale) else "0"
        }

        val digits = unscaled.abs().toString()
        val signPrefix = if (signum < 0) "-" else ""
        return when {
            scale == 0 -> signPrefix + digits
            scale < 0 -> signPrefix + digits + "0".repeat(-scale)
            digits.length > scale -> {
                val point = digits.length - scale
                signPrefix + digits.substring(0, point) + "." + digits.substring(point)
            }
            else -> {
                signPrefix + "0." + "0".repeat(scale - digits.length) + digits
            }
        }
    }

    /**
     * Renders the value using engineering notation, where the exponent is a
     * multiple of three. Values in the plain-decimal window use ordinary
     * notation, matching `java.math.BigDecimal.toEngineeringString()`.
     */
    fun toEngineeringString(): String {
        if (isZero()) return toPlainString()

        val digits = unscaled.abs().toString()
        val adjustedExponent = digits.length - 1L - scale.toLong()
        if (scale >= 0 && adjustedExponent >= -6L) return toPlainString()

        val engineeringExponent = floorDiv(adjustedExponent, 3L) * 3L
        val digitsBeforePoint = (adjustedExponent - engineeringExponent + 1L).toInt()
        val mantissa = buildString {
            if (signum < 0) append('-')
            if (digits.length <= digitsBeforePoint) {
                append(digits)
                repeat(digitsBeforePoint - digits.length) { append('0') }
            } else {
                append(digits, 0, digitsBeforePoint)
                append('.')
                append(digits, digitsBeforePoint, digits.length)
            }
            append('E')
            if (engineeringExponent >= 0) append('+')
            append(engineeringExponent)
        }
        return mantissa
    }

    override fun compareTo(other: BigDecimal): Int {
        if (signum != other.signum) return signum.compareTo(other.signum)
        if (signum == 0) return 0

        val targetScale = maxOf(scale, other.scale)
        val left = if (scale == targetScale) {
            unscaled
        } else {
            unscaled.multiply(BigInteger.pow10(targetScale - scale))
        }
        val right = if (other.scale == targetScale) {
            other.unscaled
        } else {
            other.unscaled.multiply(BigInteger.pow10(targetScale - other.scale))
        }
        return left.compareTo(right)
    }

    fun max(other: BigDecimal): BigDecimal = if (compareTo(other) >= 0) this else other

    fun min(other: BigDecimal): BigDecimal = if (compareTo(other) <= 0) this else other

    fun toBigInteger(): BigInteger {
        if (scale == 0) return unscaled
        if (scale < 0) {
            require(scale != Int.MIN_VALUE) { "Scale overflow" }
            return unscaled.multiply(BigInteger.pow10(-scale))
        }
        return unscaled.divideAndRemainder(BigInteger.pow10(scale)).first
    }

    fun toBigIntegerExact(): BigInteger {
        if (scale <= 0) return toBigInteger()
        val (quotient, remainder) = unscaled.divideAndRemainder(BigInteger.pow10(scale))
        if (!remainder.isZero()) throw ArithmeticException("Rounding necessary")
        return quotient
    }

    /** Low-order 64 bits of the truncated integer value. */
    fun toLong(): Long = toBigInteger().toLong()

    /** Low-order 32 bits of the truncated integer value. */
    fun toInt(): Int = toBigInteger().toInt()

    fun toShort(): Short = toBigInteger().toShort()

    fun toByte(): Byte = toBigInteger().toByte()

    fun longValue(): Long = toLong()

    fun intValue(): Int = toInt()

    fun shortValue(): Short = toShort()

    fun byteValue(): Byte = toByte()

    fun doubleValue(): Double = toDouble()

    fun floatValue(): Float = toFloat()

    fun longValueExact(): Long =
        toBigIntegerExact().toString().toLongOrNull()
            ?: throw ArithmeticException("Value does not fit in a Long")

    fun intValueExact(): Int {
        val value = longValueExact()
        if (value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            throw ArithmeticException("Value does not fit in an Int")
        }
        return value.toInt()
    }

    fun shortValueExact(): Short {
        val value = intValueExact()
        if (value !in Short.MIN_VALUE.toInt()..Short.MAX_VALUE.toInt()) {
            throw ArithmeticException("Value does not fit in a Short")
        }
        return value.toShort()
    }

    fun byteValueExact(): Byte {
        val value = intValueExact()
        if (value !in Byte.MIN_VALUE.toInt()..Byte.MAX_VALUE.toInt()) {
            throw ArithmeticException("Value does not fit in a Byte")
        }
        return value.toByte()
    }

    fun toDouble(): Double = (unscaled.toString() + "E" + (-scale.toLong())).toDouble()

    fun toFloat(): Float = toDouble().toFloat()

    /**
     * Raises this value to a non-negative integer power exactly.
     */
    fun pow(exponent: Int): BigDecimal {
        require(exponent >= 0) { "Exponent must not be negative" }
        if (exponent == 0) return ONE
        if (isZero()) return ZERO

        val resultScale = scale.toLong() * exponent.toLong()
        require(resultScale in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "Scale overflow"
        }
        return BigDecimal(unscaled.pow(exponent), resultScale.toInt())
    }

    /**
     * Raises this value to an integer power, rounding each step to [context].
     * Negative exponents are supported.
     */
    fun pow(exponent: Int, context: MathContext = MathContext.DECIMAL128): BigDecimal {
        if (exponent == 0) return ONE
        if (isZero()) {
            if (exponent < 0) throw ArithmeticException("Zero cannot be raised to a negative power")
            return ZERO
        }
        if (exponent == 1) return round(context)
        if (exponent < 0) {
            require(exponent != Int.MIN_VALUE) { "Exponent is too small" }
            return ONE.divide(pow(-exponent, context), context)
        }

        var result = ONE
        var base = round(context)
        var remaining = exponent
        while (remaining > 0) {
            if (remaining and 1 == 1) {
                result = result.multiply(base).round(context)
            }
            remaining = remaining ushr 1
            if (remaining > 0) {
                base = base.multiply(base).round(context)
            }
        }
        return result
    }

    /**
     * Raises this value to an arbitrary decimal power.
     *
     * A negative base is only supported for integer exponents. A positive base
     * is evaluated as `exp(exponent * ln(this))`.
     */
    fun pow(
        exponent: BigDecimal,
        context: MathContext = MathContext.DECIMAL128,
    ): BigDecimal {
        if (exponent.isZero()) return ONE
        if (isZero()) {
            if (exponent.signum < 0) {
                throw ArithmeticException("Zero cannot be raised to a negative power")
            }
            return ZERO
        }
        if (compareTo(ONE) == 0) return ONE.round(context)

        if (signum < 0) {
            if (!exponent.isInteger()) {
                throw ArithmeticException("A negative base requires an integer exponent")
            }
            val integerExponent = exponent.toBigInteger().toString().toIntOrNull()
                ?: throw ArithmeticException("Exponent is outside the supported Int range")
            return pow(integerExponent, context)
        }

        val working = workingContext(context)
        return exponent.multiply(ln(working)).exp(working).round(context)
    }

    /** Square root, rounded to [context]. */
    fun sqrt(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        require(signum >= 0) { "Square root of a negative value" }
        if (isZero()) return ZERO

        val working = workingContext(context)
        var guess = sqrtInitialGuess(working)
        val maxIterations = maxOf(20, context.precision + 16)

        repeat(maxIterations) {
            val next = guess
                .add(divide(guess, working))
                .divide(TWO, working)
                .round(working)
            if (next.compareTo(guess) == 0) {
                return next.round(context)
            }
            guess = next
        }
        return guess.round(context)
    }

    /** Cube root, rounded to [context]. */
    fun cbrt(context: MathContext = MathContext.DECIMAL128): BigDecimal =
        root(3, context)

    /** Alias for [sqrt]. */
    fun squareRoot(context: MathContext = MathContext.DECIMAL128): BigDecimal =
        sqrt(context)

    /** Alias for [cbrt]. */
    fun cubeRoot(context: MathContext = MathContext.DECIMAL128): BigDecimal =
        cbrt(context)

    /** Alias for [root]. */
    fun nthRoot(n: Int, context: MathContext = MathContext.DECIMAL128): BigDecimal =
        root(n, context)

    /**
     * The [n]-th root of this value, rounded to [context]. Negative values are
     * supported for odd roots.
     */
    fun root(n: Int, context: MathContext = MathContext.DECIMAL128): BigDecimal {
        require(n != 0) { "Root degree must not be zero" }
        if (n == 1) return round(context)
        if (n < 0) {
            require(n != Int.MIN_VALUE) { "Root degree is too small" }
            return ONE.divide(root(-n, context), context)
        }
        if (isZero()) return ZERO

        if (signum < 0) {
            if (n % 2 == 0) {
                throw ArithmeticException("Even root of a negative value")
            }
            return negate().root(n, context).negate()
        }
        if (n == 2) return sqrt(context)

        val working = workingContext(context)
        val decimalExponent = precision.toLong() - 1L - scale.toLong()
        val rootExponent = ceilDivision(decimalExponent, n)
        require(rootExponent in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "Scale overflow"
        }

        var guess = BigDecimal(BigInteger.ONE, (-rootExponent).toInt())
        val nValue = valueOf(n)
        val nMinusOne = valueOf(n - 1)
        val maxIterations = maxOf(40, context.precision * 2 + 32)

        repeat(maxIterations) {
            val denominator = guess.pow(n - 1, working)
            val next = guess
                .multiply(nMinusOne)
                .add(divide(denominator, working))
                .divide(nValue, working)
                .round(working)
            if (next.compareTo(guess) == 0) {
                return next.round(context)
            }
            guess = next
        }
        return guess.round(context)
    }

    /** Natural exponential function, `e^this`. */
    fun exp(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        if (isZero()) return ONE
        if (signum < 0) {
            val working = workingContext(context)
            return ONE.divide(negate().exp(working), context)
        }

        val working = workingContext(context)
        var argument = this
        var squarings = 0

        val approximation = toDouble()
        if (approximation.isFinite() && approximation > EXP_REDUCTION_LIMIT) {
            squarings = ceil(ln(approximation / EXP_REDUCTION_LIMIT) / LN_2).toInt()
                .coerceAtLeast(0)
        } else if (!approximation.isFinite()) {
            val decimalExponent = precision.toLong() - scale.toLong()
            val estimated = decimalExponent * LOG2_OF_10 + 2.0
            require(estimated < Int.MAX_VALUE) { "Exponent is too large for exp" }
            squarings = estimated.toInt().coerceAtLeast(0)
        }

        repeat(squarings) {
            argument = argument.divide(TWO, working)
        }

        val threshold = tinyThreshold(working)
        var sum = ONE
        var term = ONE
        var n = 1L
        val maxIterations = working.precision * 2 + 200

        while (n <= maxIterations) {
            term = term.multiply(argument).round(working)
                .divide(valueOf(n), working)
            if (term.isTiny(threshold)) {
                sum = sum.add(term).round(working)
                break
            }
            sum = sum.add(term).round(working)
            n++
        }

        repeat(squarings) {
            sum = sum.multiply(sum).round(working)
        }
        return sum.round(context)
    }

    /** Natural logarithm. Requires a positive value. */
    fun ln(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        require(signum > 0) { "Logarithm of a non-positive value" }
        if (compareTo(ONE) == 0) return ZERO

        val working = workingContext(context)
        val digits = precision
        val tenExponent = digits.toLong() - 1L - scale.toLong()

        // Scale the mantissa into [1, 10) without changing the represented
        // digits. This also handles subnormal-sized decimal values cleanly.
        var mantissa = BigDecimal(unscaled, digits - 1)
        var halves = 0
        while (mantissa.compareTo(TWO) >= 0) {
            mantissa = mantissa.divide(TWO, working)
            halves++
        }

        val z = mantissa.subtract(ONE).divide(mantissa.add(ONE), working)
        var result = atanhSeries(z, working).multiply(TWO).round(working)

        if (halves != 0) {
            result = result
                .add(valueOf(halves.toLong()).multiply(ln2At(working)))
                .round(working)
        }
        if (tenExponent != 0L) {
            result = result
                .add(valueOf(tenExponent).multiply(ln10At(working)))
                .round(working)
        }
        return result.round(context)
    }

    /** Base-10 logarithm. */
    fun log10(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        require(signum > 0) { "Logarithm of a non-positive value" }
        val working = workingContext(context)
        return ln(working).divide(ln10At(working), context)
    }

    /** Base-2 logarithm. */
    fun log2(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        require(signum > 0) { "Logarithm of a non-positive value" }
        val working = workingContext(context)
        return ln(working).divide(ln2At(working), context)
    }

    /** Logarithm in an arbitrary positive base other than 1. */
    fun log(base: BigDecimal, context: MathContext = MathContext.DECIMAL128): BigDecimal {
        require(signum > 0) { "Logarithm of a non-positive value" }
        require(base.signum > 0) { "Base must be positive" }
        require(base.compareTo(ONE) != 0) { "Base must not be one" }
        val working = workingContext(context)
        return ln(working).divide(base.ln(working), context)
    }

    /** Sine of this angle in radians. */
    fun sin(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        if (isZero()) return ZERO
        val working = workingContext(context)
        val reduced = reduceAngle(working)
        val halfPi = pi(working).divide(TWO, working)
        var x = reduced
        if (x.compareTo(halfPi) > 0) {
            x = pi(working).subtract(x).round(working)
        } else if (x.compareTo(halfPi.negate()) < 0) {
            x = pi(working).negate().subtract(x).round(working)
        }
        return sinSeries(x, working).round(context)
    }

    /** Cosine of this angle in radians. */
    fun cos(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        if (isZero()) return ONE
        val working = workingContext(context)
        val reduced = reduceAngle(working)
        var x = if (reduced.signum < 0) reduced.negate() else reduced
        var negateResult = false
        val piValue = pi(working)
        val halfPi = piValue.divide(TWO, working)
        if (x.compareTo(halfPi) > 0) {
            x = piValue.subtract(x).round(working)
            negateResult = true
        }
        val result = cosSeries(x, working)
        return (if (negateResult) result.negate() else result).round(context)
    }

    /** Tangent of this angle in radians. */
    fun tan(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        val working = workingContext(context)
        val sine = sin(working)
        val cosine = cos(working)
        if (cosine.isZero()) throw ArithmeticException("Tangent is undefined")
        return sine.divide(cosine, context)
    }

    /** Arcsine in radians. */
    fun asin(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        if (compareTo(ONE.negate()) < 0 || compareTo(ONE) > 0) {
            throw ArithmeticException("Arcsine is only defined for values in [-1, 1]")
        }
        if (isZero()) return ZERO
        if (compareTo(ONE) == 0) return pi(workingContext(context)).divide(TWO, context)
        if (compareTo(ONE.negate()) == 0) {
            return pi(workingContext(context)).divide(TWO, context).negate()
        }

        val working = workingContext(context)
        val root = ONE.subtract(multiply(this).round(working)).round(working).sqrt(working)
        if (root.isZero()) {
            return if (signum > 0) {
                pi(working).divide(TWO, context)
            } else {
                pi(working).divide(TWO, context).negate()
            }
        }
        return divide(root, working).atan(working).round(context)
    }

    /** Arccosine in radians. */
    fun acos(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        if (compareTo(ONE.negate()) < 0 || compareTo(ONE) > 0) {
            throw ArithmeticException("Arccosine is only defined for values in [-1, 1]")
        }
        val working = workingContext(context)
        val halfPi = pi(working).divide(TWO, working)
        return halfPi.subtract(asin(working)).round(context)
    }

    /** Arctangent in radians. */
    fun atan(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        if (isZero()) return ZERO
        val working = workingContext(context)
        val negative = signum < 0
        val value = if (negative) negate() else this
        val result = atanPositive(value, working)
        return (if (negative) result.negate() else result).round(context)
    }

    /** Hyperbolic sine. */
    fun sinh(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        val working = workingContext(context)
        return exp(working)
            .subtract(negate().exp(working))
            .divide(TWO, context)
    }

    /** Hyperbolic cosine. */
    fun cosh(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        val working = workingContext(context)
        return exp(working)
            .add(negate().exp(working))
            .divide(TWO, context)
    }

    /** Hyperbolic tangent. */
    fun tanh(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        val working = workingContext(context)
        return sinh(working).divide(cosh(working), context)
    }

    /** Inverse hyperbolic sine. */
    fun asinh(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        val working = workingContext(context)
        return add(multiply(this).add(ONE).sqrt(working)).ln(working).round(context)
    }

    /** Inverse hyperbolic cosine. Requires a value of at least one. */
    fun acosh(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        require(compareTo(ONE) >= 0) { "Arccosh requires a value >= 1" }
        val working = workingContext(context)
        return add(multiply(this).subtract(ONE).sqrt(working)).ln(working).round(context)
    }

    /** Inverse hyperbolic tangent. Requires a value strictly between -1 and 1. */
    fun atanh(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        require(compareTo(ONE.negate()) > 0 && compareTo(ONE) < 0) {
            "Arctanh requires a value in (-1, 1)"
        }
        if (isZero()) return ZERO
        val working = workingContext(context)
        return ONE.add(this)
            .divide(ONE.subtract(this), working)
            .ln(working)
            .divide(TWO, context)
    }

    /** Converts radians to degrees. */
    fun toDegrees(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        val working = workingContext(context)
        return multiply(valueOf(180)).divide(pi(working), context)
    }

    /** Converts degrees to radians. */
    fun toRadians(context: MathContext = MathContext.DECIMAL128): BigDecimal {
        val working = workingContext(context)
        return multiply(pi(working)).divide(valueOf(180), context)
    }

    override fun equals(other: Any?): Boolean =
        other is BigDecimal && scale == other.scale && unscaled == other.unscaled

    override fun hashCode(): Int = 31 * unscaled.hashCode() + scale

    /**
     * Uses plain notation for ordinary magnitudes and scientific notation when
     * the scale is negative or the adjusted exponent is below -6, matching
     * `java.math.BigDecimal.toString()`. Use [toPlainString] when scientific
     * notation is not wanted.
     */
    override fun toString(): String {
        if (isZero()) {
            return if (scale == 0) {
                "0"
            } else if (scale > 0) {
                toPlainString()
            } else {
                "0E${formatExponent(-scale.toLong())}"
            }
        }

        val digits = unscaled.abs().toString()
        val adjustedExponent = digits.length - 1L - scale.toLong()
        if (scale >= 0 && adjustedExponent >= -6L) return toPlainString()

        return buildString {
            if (signum < 0) append('-')
            append(digits[0])
            if (digits.length > 1) {
                append('.')
                append(digits, 1, digits.length)
            }
            append('E')
            append(formatExponent(adjustedExponent))
        }
    }

    private fun withScale(newScale: Long): BigDecimal {
        require(newScale in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "Scale overflow"
        }
        return BigDecimal(unscaled, newScale.toInt())
    }

    private fun sqrtInitialGuess(context: MathContext): BigDecimal {
        val approximation = toDouble()
        if (approximation.isFinite() && approximation > 0.0) {
            val root = floatingSqrt(approximation)
            if (root.isFinite() && root > 0.0) {
                return parse(root.toString())
            }
        }

        val decimalExponent = precision.toLong() - 1L - scale.toLong()
        val rootExponent = if (decimalExponent >= 0) {
            (decimalExponent + 1L) / 2L
        } else {
            decimalExponent / 2L
        }
        require(rootExponent in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "Scale overflow"
        }
        return BigDecimal(BigInteger.ONE, (-rootExponent).toInt()).round(context)
    }

    private fun reduceAngle(context: MathContext): BigDecimal {
        val piValue = pi(context)
        val twoPi = piValue.multiply(TWO).round(context)
        var x = remainder(twoPi)
        if (x.compareTo(piValue) > 0) {
            x = x.subtract(twoPi).round(context)
        } else if (x.compareTo(piValue.negate()) < 0) {
            x = x.add(twoPi).round(context)
        }
        return x
    }

    private fun sinSeries(x: BigDecimal, context: MathContext): BigDecimal {
        val threshold = tinyThreshold(context)
        val xSquared = x.multiply(x).round(context)
        var sum = x
        var term = x
        var n = 1L
        val maxIterations = context.precision * 2 + 100

        while (n <= maxIterations) {
            val denominator = valueOf((2L * n) * (2L * n + 1L))
            term = term.multiply(xSquared).round(context).divide(denominator, context).negate()
            if (term.isTiny(threshold)) break
            sum = sum.add(term).round(context)
            n++
        }
        return sum
    }

    private fun cosSeries(x: BigDecimal, context: MathContext): BigDecimal {
        val threshold = tinyThreshold(context)
        val xSquared = x.multiply(x).round(context)
        var sum = ONE
        var term = ONE
        var n = 1L
        val maxIterations = context.precision * 2 + 100

        while (n <= maxIterations) {
            val denominator = valueOf((2L * n - 1L) * (2L * n))
            term = term.multiply(xSquared).round(context).divide(denominator, context).negate()
            if (term.isTiny(threshold)) break
            sum = sum.add(term).round(context)
            n++
        }
        return sum
    }

    private fun atanPositive(value: BigDecimal, context: MathContext): BigDecimal {
        if (value.isZero()) return ZERO

        if (value.compareTo(ONE) > 0) {
            val halfPi = pi(context).divide(TWO, context)
            return halfPi.subtract(atanUnitInterval(ONE.divide(value, context), context))
                .round(context)
        }
        return atanUnitInterval(value, context)
    }

    private fun atanUnitInterval(value: BigDecimal, context: MathContext): BigDecimal {
        var x = value
        var multiplier = 1

        while (x.compareTo(HALF) > 0) {
            val root = ONE.add(x.multiply(x).round(context)).round(context).sqrt(context)
            x = x.divide(ONE.add(root), context).round(context)
            multiplier *= 2
        }

        val series = atanSeries(x, context)
        return if (multiplier == 1) {
            series
        } else {
            series.multiply(valueOf(multiplier)).round(context)
        }
    }

    private fun atanSeries(x: BigDecimal, context: MathContext): BigDecimal {
        val threshold = tinyThreshold(context)
        val xSquared = x.multiply(x).round(context)
        var sum = x
        var power = x
        var n = 1L
        val maxIterations = context.precision * 4 + 200

        while (n <= maxIterations) {
            power = power.multiply(xSquared).round(context).negate()
            val term = power.divide(valueOf(2L * n + 1L), context)
            if (term.isTiny(threshold)) break
            sum = sum.add(term).round(context)
            n++
        }
        return sum
    }

    private fun ln2At(context: MathContext): BigDecimal =
        atanhSeries(ONE.divide(THREE, context), context)
            .multiply(TWO)
            .round(context)

    private fun ln10At(context: MathContext): BigDecimal =
        atanhSeries(valueOf(9).divide(valueOf(11), context), context)
            .multiply(TWO)
            .round(context)

    private fun atanhSeries(z: BigDecimal, context: MathContext): BigDecimal {
        val threshold = tinyThreshold(context)
        val zSquared = z.multiply(z).round(context)
        var sum = z
        var power = z
        var n = 1L
        val maxIterations = context.precision * 4 + 200

        while (n <= maxIterations) {
            power = power.multiply(zSquared).round(context)
            val term = power.divide(valueOf(2L * n + 1L), context)
            if (term.isTiny(threshold)) break
            sum = sum.add(term).round(context)
            n++
        }
        return sum
    }

    companion object {
        private const val GUARD_DIGITS = 12
        private const val EXP_REDUCTION_LIMIT = 0.125
        private const val LN_2 = 0.6931471805599453
        private const val LOG2_OF_10 = 3.321928094887362

        /**
         * Constants are evaluated with extra working precision before being
         * rounded to the public 34-digit DECIMAL128 values, so derived
         * constants do not accumulate a last-digit error from an already
         * rounded PI, E, ln(2) or ln(10).
         */
        private val CONSTANT_CONTEXT = MathContext(40, RoundingMode.HALF_EVEN)
        private val PI_AT_CONSTANT_PRECISION: BigDecimal by lazy { pi(CONSTANT_CONTEXT) }
        private val LN2_AT_CONSTANT_PRECISION: BigDecimal by lazy { ln2(CONSTANT_CONTEXT) }
        private val LN10_AT_CONSTANT_PRECISION: BigDecimal by lazy { ln10(CONSTANT_CONTEXT) }

        /** Legacy integer rounding-mode constants. */
        const val ROUND_UP: Int = 0
        const val ROUND_DOWN: Int = 1
        const val ROUND_CEILING: Int = 2
        const val ROUND_FLOOR: Int = 3
        const val ROUND_HALF_UP: Int = 4
        const val ROUND_HALF_DOWN: Int = 5
        const val ROUND_HALF_EVEN: Int = 6
        const val ROUND_UNNECESSARY: Int = 7

        val ZERO: BigDecimal = BigDecimal(BigInteger.ZERO, 0)
        val ONE: BigDecimal = BigDecimal(BigInteger.ONE, 0)
        val TWO: BigDecimal = BigDecimal(BigInteger.TWO, 0)
        val THREE: BigDecimal = BigDecimal(BigInteger.THREE, 0)
        val FOUR: BigDecimal = BigDecimal(BigInteger.fromLong(4L), 0)
        val FIVE: BigDecimal = BigDecimal(BigInteger.fromLong(5L), 0)
        val TEN: BigDecimal = BigDecimal(BigInteger.TEN, 0)
        val HALF: BigDecimal = BigDecimal(BigInteger.fromLong(5L), 1)
        val HUNDRED: BigDecimal = valueOf(100)
        val THOUSAND: BigDecimal = valueOf(1000)

        /** Pi rounded to 34 significant digits. */
        val PI: BigDecimal by lazy { pi(MathContext.DECIMAL128) }

        /** Euler's number rounded to 34 significant digits. */
        val E: BigDecimal by lazy { ONE.exp(MathContext.DECIMAL128) }

        /** Full turn, `2 * pi`. */
        val TAU: BigDecimal by lazy {
            PI_AT_CONSTANT_PRECISION.multiply(TWO).round(MathContext.DECIMAL128)
        }

        /** Alias for [TAU]. */
        val TWO_PI: BigDecimal get() = TAU

        /** Half a turn, `pi / 2`. */
        val HALF_PI: BigDecimal by lazy {
            PI_AT_CONSTANT_PRECISION.divide(TWO, CONSTANT_CONTEXT)
                .round(MathContext.DECIMAL128)
        }

        /** Quarter turn, `pi / 4`. */
        val QUARTER_PI: BigDecimal by lazy {
            PI_AT_CONSTANT_PRECISION.divide(FOUR, CONSTANT_CONTEXT)
                .round(MathContext.DECIMAL128)
        }

        /** Natural logarithm of two. */
        val LN2: BigDecimal by lazy {
            LN2_AT_CONSTANT_PRECISION.round(MathContext.DECIMAL128)
        }

        /** Natural logarithm of ten. */
        val LN10: BigDecimal by lazy {
            LN10_AT_CONSTANT_PRECISION.round(MathContext.DECIMAL128)
        }

        /** Base-2 logarithm of Euler's number, `1 / ln(2)`. */
        val LOG2E: BigDecimal by lazy {
            ONE.divide(LN2_AT_CONSTANT_PRECISION, CONSTANT_CONTEXT)
                .round(MathContext.DECIMAL128)
        }

        /** Base-10 logarithm of Euler's number, `1 / ln(10)`. */
        val LOG10E: BigDecimal by lazy {
            ONE.divide(LN10_AT_CONSTANT_PRECISION, CONSTANT_CONTEXT)
                .round(MathContext.DECIMAL128)
        }

        /** Square root of two. */
        val SQRT2: BigDecimal by lazy {
            TWO.sqrt(CONSTANT_CONTEXT).round(MathContext.DECIMAL128)
        }

        /** Square root of three. */
        val SQRT3: BigDecimal by lazy {
            THREE.sqrt(CONSTANT_CONTEXT).round(MathContext.DECIMAL128)
        }

        /** Square root of one half. */
        val SQRT1_2: BigDecimal by lazy {
            HALF.sqrt(CONSTANT_CONTEXT).round(MathContext.DECIMAL128)
        }

        /** Alias for [SQRT1_2]. */
        val INV_SQRT2: BigDecimal get() = SQRT1_2

        /** The golden ratio, `(1 + sqrt(5)) / 2`. */
        val GOLDEN_RATIO: BigDecimal by lazy {
            ONE.add(FIVE.sqrt(CONSTANT_CONTEXT))
                .divide(TWO, CONSTANT_CONTEXT)
                .round(MathContext.DECIMAL128)
        }

        /** Cube root of two. */
        val CUBE_ROOT_2: BigDecimal by lazy {
            TWO.cbrt(CONSTANT_CONTEXT).round(MathContext.DECIMAL128)
        }

        /** Degrees in one radian, `180 / pi`. */
        val DEGREES_PER_RADIAN: BigDecimal by lazy {
            valueOf(180).divide(PI_AT_CONSTANT_PRECISION, CONSTANT_CONTEXT)
                .round(MathContext.DECIMAL128)
        }

        /** Radians in one degree, `pi / 180`. */
        val RADIANS_PER_DEGREE: BigDecimal by lazy {
            PI_AT_CONSTANT_PRECISION.divide(valueOf(180), CONSTANT_CONTEXT)
                .round(MathContext.DECIMAL128)
        }

        /** Pi evaluated with the supplied precision. */
        fun pi(context: MathContext = MathContext.DECIMAL128): BigDecimal {
            val working = workingContext(context)
            val fifth = ONE.divide(valueOf(5), working)
            val twoThirtyNinth = ONE.divide(valueOf(239), working)
            return atanSeriesForConstant(fifth, working)
                .multiply(valueOf(16))
                .subtract(atanSeriesForConstant(twoThirtyNinth, working).multiply(valueOf(4)))
                .round(context)
        }

        /** Euler's number evaluated with the supplied precision. */
        fun e(context: MathContext = MathContext.DECIMAL128): BigDecimal = ONE.exp(context)

        /** Natural logarithm of two, evaluated with the supplied precision. */
        fun ln2(context: MathContext = MathContext.DECIMAL128): BigDecimal {
            val working = workingContext(context)
            return atanhSeriesForConstant(ONE.divide(THREE, working), working)
                .multiply(TWO)
                .round(context)
        }

        /** Natural logarithm of ten, evaluated with the supplied precision. */
        fun ln10(context: MathContext = MathContext.DECIMAL128): BigDecimal {
            val working = workingContext(context)
            return atanhSeriesForConstant(valueOf(9).divide(valueOf(11), working), working)
                .multiply(TWO)
                .round(context)
        }

        /** `1 / ln(2)`, evaluated with the supplied precision. */
        fun log2e(context: MathContext = MathContext.DECIMAL128): BigDecimal {
            val working = workingContext(context)
            return ONE.divide(ln2(working), context)
        }

        /** `1 / ln(10)`, evaluated with the supplied precision. */
        fun log10e(context: MathContext = MathContext.DECIMAL128): BigDecimal {
            val working = workingContext(context)
            return ONE.divide(ln10(working), context)
        }

        /** Allows the concise form `BigDecimal("123.45")`. */
        operator fun invoke(text: String): BigDecimal = parse(text)

        /** Allows the concise form `BigDecimal("123.45", context)`. */
        operator fun invoke(text: String, context: MathContext): BigDecimal =
            parse(text, context)

        /** Allows the concise form `BigDecimal(12345L, 2)` for 123.45. */
        operator fun invoke(unscaledValue: Long, scale: Int): BigDecimal =
            BigDecimal(BigInteger.fromLong(unscaledValue), scale)

        /** Allows the concise form `BigDecimal(12345, 2)` for 123.45. */
        operator fun invoke(unscaledValue: Int, scale: Int): BigDecimal =
            BigDecimal(BigInteger.fromLong(unscaledValue.toLong()), scale)

        /** Creates a value from an arbitrary-precision integer. */
        operator fun invoke(unscaledValue: BigInteger): BigDecimal = valueOf(unscaledValue)

        /** Creates a value from an arbitrary-precision integer and scale. */
        operator fun invoke(unscaledValue: BigInteger, scale: Int): BigDecimal =
            valueOf(unscaledValue, scale)

        /** Creates a decimal from a finite Double. */
        operator fun invoke(value: Double): BigDecimal = valueOf(value)

        /** Creates a decimal from a finite Float. */
        operator fun invoke(value: Float): BigDecimal = valueOf(value)

        /** Four-quadrant arctangent in radians. */
        fun atan2(
            y: BigDecimal,
            x: BigDecimal,
            context: MathContext = MathContext.DECIMAL128,
        ): BigDecimal {
            if (x.isZero() && y.isZero()) return ZERO
            val working = workingContext(context)

            if (x.signum > 0) {
                return y.divide(x, working).atan(working).round(context)
            }
            if (x.signum < 0) {
                val base = y.divide(x, working).atan(working)
                val adjustment = if (y.signum >= 0) pi(working) else pi(working).negate()
                return base.add(adjustment).round(context)
            }

            val halfPi = pi(working).divide(TWO, working)
            return (if (y.signum > 0) halfPi else halfPi.negate()).round(context)
        }

        /** Euclidean length of the vector `(x, y)`. */
        fun hypot(
            x: BigDecimal,
            y: BigDecimal,
            context: MathContext = MathContext.DECIMAL128,
        ): BigDecimal {
            val working = workingContext(context)
            return x.multiply(x).add(y.multiply(y)).sqrt(working).round(context)
        }

        fun valueOf(value: Int): BigDecimal =
            BigDecimal(BigInteger.fromLong(value.toLong()), 0)

        fun valueOf(value: Long): BigDecimal =
            BigDecimal(BigInteger.fromLong(value), 0)

        fun valueOf(unscaledValue: Long, scale: Int): BigDecimal =
            BigDecimal(BigInteger.fromLong(unscaledValue), scale)

        fun valueOf(unscaledValue: Int, scale: Int): BigDecimal =
            BigDecimal(BigInteger.fromLong(unscaledValue.toLong()), scale)

        fun valueOf(unscaledValue: BigInteger): BigDecimal =
            BigDecimal(unscaledValue, 0)

        fun valueOf(unscaledValue: BigInteger, scale: Int): BigDecimal =
            BigDecimal(unscaledValue, scale)

        fun valueOf(value: Double): BigDecimal {
            require(value.isFinite()) { "Cannot convert a non-finite Double" }
            return parse(value.toString())
        }

        fun valueOf(value: Float): BigDecimal = valueOf(value.toDouble())

        /**
         * Parses values such as `42`, `-0.125`, `.5`, `1.` and `1.25e-3`.
         */
        fun parse(text: String): BigDecimal {
            val value = text.trim()
            require(value.isNotEmpty()) { "Cannot parse an empty decimal" }

            var index = 0
            var negative = false
            if (value[index] == '+' || value[index] == '-') {
                negative = value[index] == '-'
                index++
            }
            require(index < value.length) { "Cannot parse '$text' as a decimal" }

            val digits = StringBuilder()
            var fractionDigits = 0
            var seenDecimalPoint = false
            var seenDigit = false

            while (index < value.length) {
                val character = value[index]
                when {
                    character == '.' -> {
                        require(!seenDecimalPoint) { "Cannot parse '$text' as a decimal" }
                        seenDecimalPoint = true
                    }

                    character in '0'..'9' -> {
                        digits.append(character)
                        seenDigit = true
                        if (seenDecimalPoint) fractionDigits++
                    }

                    character == 'e' || character == 'E' -> break
                    else -> throw IllegalArgumentException("Cannot parse '$text' as a decimal")
                }
                index++
            }

            require(seenDigit) { "Cannot parse '$text' as a decimal" }

            var exponent = 0L
            if (index < value.length) {
                index++ // skip 'e' or 'E'
                var exponentNegative = false
                if (index < value.length && (value[index] == '+' || value[index] == '-')) {
                    exponentNegative = value[index] == '-'
                    index++
                }
                require(index < value.length) { "Cannot parse '$text' as a decimal" }

                var exponentDigits = 0
                while (index < value.length) {
                    val character = value[index]
                    require(character in '0'..'9') {
                        "Cannot parse '$text' as a decimal"
                    }
                    exponent = exponent * 10 + (character - '0')
                    require(exponent <= MAX_EXPONENT) { "Exponent is too large" }
                    exponentDigits++
                    index++
                }
                require(exponentDigits > 0) { "Cannot parse '$text' as a decimal" }
                if (exponentNegative) exponent = -exponent
            }

            val resultScale = fractionDigits.toLong() - exponent
            require(resultScale in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                "Scale overflow"
            }

            val unscaledText = buildString {
                if (negative) append('-')
                append(digits)
            }
            return BigDecimal(BigInteger.parse(unscaledText), resultScale.toInt())
        }

        /** Parses [text] and rounds it to [context]. */
        fun parse(text: String, context: MathContext): BigDecimal =
            parse(text).round(context)

        private const val MAX_EXPONENT = 1_000_000_000L

        private fun floorDiv(value: Long, divisor: Long): Long {
            val quotient = value / divisor
            return if (value % divisor != 0L && (value xor divisor) < 0L) {
                quotient - 1L
            } else {
                quotient
            }
        }

        private fun formatExponent(value: Long): String =
            if (value >= 0) "+$value" else value.toString()

        private fun workingContext(context: MathContext): MathContext {
            val digits = context.precision.toLong() + GUARD_DIGITS
            require(digits <= Int.MAX_VALUE) { "Precision is too large" }
            return MathContext(digits.toInt(), RoundingMode.HALF_EVEN)
        }

        private fun tinyThreshold(context: MathContext): BigDecimal =
            BigDecimal(BigInteger.ONE, context.precision + 2)

        private fun ceilDivision(value: Long, divisor: Int): Long {
            val positiveDivisor = divisor.toLong()
            return if (value >= 0) {
                (value + positiveDivisor - 1) / positiveDivisor
            } else {
                -((-value) / positiveDivisor)
            }
        }

        private fun atanSeriesForConstant(x: BigDecimal, context: MathContext): BigDecimal {
            val threshold = tinyThreshold(context)
            val xSquared = x.multiply(x).round(context)
            var sum = x
            var power = x
            var n = 1L
            val maxIterations = context.precision * 4 + 200

            while (n <= maxIterations) {
                power = power.multiply(xSquared).round(context).negate()
                val term = power.divide(valueOf(2L * n + 1L), context)
                if (term.isTiny(threshold)) break
                sum = sum.add(term).round(context)
                n++
            }
            return sum
        }

        private fun atanhSeriesForConstant(z: BigDecimal, context: MathContext): BigDecimal {
            val threshold = tinyThreshold(context)
            val zSquared = z.multiply(z).round(context)
            var sum = z
            var power = z
            var n = 1L
            val maxIterations = context.precision * 4 + 200

            while (n <= maxIterations) {
                power = power.multiply(zSquared).round(context)
                val term = power.divide(valueOf(2L * n + 1L), context)
                if (term.isTiny(threshold)) break
                sum = sum.add(term).round(context)
                n++
            }
            return sum
        }

        private fun BigDecimal.isTiny(threshold: BigDecimal): Boolean =
            abs().compareTo(threshold) < 0

        private fun shouldIncrement(
            quotient: BigInteger,
            remainder: BigInteger,
            divisor: BigInteger,
            roundingMode: RoundingMode,
            negative: Boolean,
        ): Boolean {
            if (remainder.isZero()) return false

            return when (roundingMode) {
                RoundingMode.UNNECESSARY ->
                    throw ArithmeticException("Rounding necessary")

                RoundingMode.DOWN -> false
                RoundingMode.UP -> true
                RoundingMode.CEILING -> !negative
                RoundingMode.FLOOR -> negative
                RoundingMode.HALF_UP ->
                    remainder.add(remainder).compareTo(divisor) >= 0

                RoundingMode.HALF_DOWN ->
                    remainder.add(remainder).compareTo(divisor) > 0

                RoundingMode.HALF_EVEN -> {
                    val comparison = remainder.add(remainder).compareTo(divisor)
                    comparison > 0 || (comparison == 0 && quotient.isOdd())
                }
            }
        }
    }
}
