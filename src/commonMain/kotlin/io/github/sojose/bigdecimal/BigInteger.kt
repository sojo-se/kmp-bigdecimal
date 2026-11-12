/*
 * Copyright (c) 2026 Sören Johansson
 * SPDX-License-Identifier: MIT
 */

package io.github.sojose.bigdecimal

/**
 * A small, dependency-free arbitrary-precision signed integer.
 *
 * The magnitude is stored as little-endian limbs in base 1_000_000_000. That
 * keeps every intermediate product inside a [Long] and makes decimal
 * conversion cheap. The implementation is intentionally limited to the
 * operations the decimal layer needs: comparison, addition, subtraction,
 * multiplication, division with remainder, powers of ten and decimal output.
 *
 * This is the platform-independent foundation used by [BigDecimal] on Android,
 * iOS, JVM, JavaScript and WebAssembly.
 */
class BigInteger private constructor(
    private val sign: Int,
    private val magnitude: IntArray,
) : Comparable<BigInteger> {

    val signum: Int
        get() = sign

    fun isZero(): Boolean = sign == 0

    fun isOdd(): Boolean = sign != 0 && (magnitude[0] and 1) == 1

    fun abs(): BigInteger = if (sign >= 0) this else BigInteger(1, magnitude)

    fun negate(): BigInteger = if (sign == 0) this else BigInteger(-sign, magnitude)

    fun add(other: BigInteger): BigInteger {
        if (sign == 0) return other
        if (other.sign == 0) return this

        if (sign == other.sign) {
            return create(sign, addMagnitudes(magnitude, other.magnitude))
        }

        val comparison = compareMagnitudes(magnitude, other.magnitude)
        return when {
            comparison > 0 -> create(sign, subtractMagnitudes(magnitude, other.magnitude))
            comparison < 0 -> create(other.sign, subtractMagnitudes(other.magnitude, magnitude))
            else -> ZERO
        }
    }

    fun subtract(other: BigInteger): BigInteger = add(other.negate())

    fun divide(divisor: BigInteger): BigInteger = divideAndRemainder(divisor).first

    fun remainder(divisor: BigInteger): BigInteger = divideAndRemainder(divisor).second

    /** Non-negative remainder, matching the usual `mod` convention. */
    fun mod(divisor: BigInteger): BigInteger {
        val remainder = remainder(divisor)
        return if (remainder.sign < 0) remainder.add(divisor.abs()) else remainder
    }

    fun gcd(other: BigInteger): BigInteger {
        var left = abs()
        var right = other.abs()
        while (!right.isZero()) {
            val remainder = left.remainder(right)
            left = right
            right = remainder
        }
        return left
    }

    fun lcm(other: BigInteger): BigInteger {
        if (isZero() || other.isZero()) return ZERO
        return abs().divide(gcd(other)).multiply(other.abs())
    }

    fun min(other: BigInteger): BigInteger = if (compareTo(other) <= 0) this else other

    fun max(other: BigInteger): BigInteger = if (compareTo(other) >= 0) this else other

    fun multiply(other: BigInteger): BigInteger {
        if (sign == 0 || other.sign == 0) return ZERO
        return create(sign * other.sign, multiplyMagnitudes(magnitude, other.magnitude))
    }

    /**
     * Returns this integer raised to a non-negative power.
     */
    fun pow(exponent: Int): BigInteger {
        require(exponent >= 0) { "Exponent must not be negative" }
        if (exponent == 0) return ONE
        if (sign == 0) return ZERO

        var result = ONE
        var base = this
        var remaining = exponent
        while (remaining > 0) {
            if (remaining and 1 == 1) {
                result = result.multiply(base)
            }
            remaining = remaining ushr 1
            if (remaining > 0) {
                base = base.multiply(base)
            }
        }
        return result
    }

    fun divideAndRemainder(divisor: BigInteger): Pair<BigInteger, BigInteger> {
        require(!divisor.isZero()) { "Division by zero" }
        if (sign == 0) return ZERO to ZERO

        if (compareMagnitudes(magnitude, divisor.magnitude) < 0) {
            return ZERO to this
        }

        val (quotientMagnitude, remainderMagnitude) =
            divideMagnitudes(magnitude, divisor.magnitude)

        val quotient = if (quotientMagnitude.isEmpty()) {
            ZERO
        } else {
            create(sign * divisor.sign, quotientMagnitude)
        }
        val remainder = if (remainderMagnitude.isEmpty()) {
            ZERO
        } else {
            create(sign, remainderMagnitude)
        }
        return quotient to remainder
    }

    fun digitCount(): Int {
        if (sign == 0) return 1

        var digits = (magnitude.size - 1) * LIMB_DIGITS
        var top = magnitude[magnitude.size - 1]
        while (top > 0) {
            digits++
            top /= 10
        }
        return digits
    }

    /** Low-order 64 bits, following the usual two's-complement conversion. */
    fun toLong(): Long {
        var result = 0uL
        for (index in magnitude.indices.reversed()) {
            result = result * BASE_U + magnitude[index].toULong()
        }
        val unsigned = result.toLong()
        return if (sign < 0) -unsigned else unsigned
    }

    fun toInt(): Int = toLong().toInt()

    fun toShort(): Short = toLong().toShort()

    fun toByte(): Byte = toLong().toByte()

    fun toDouble(): Double = toString().toDouble()

    override fun compareTo(other: BigInteger): Int {
        if (sign != other.sign) return sign.compareTo(other.sign)
        if (sign == 0) return 0

        val comparison = compareMagnitudes(magnitude, other.magnitude)
        return if (sign > 0) comparison else -comparison
    }

    override fun equals(other: Any?): Boolean =
        other is BigInteger && sign == other.sign && magnitude.contentEquals(other.magnitude)

    override fun hashCode(): Int = 31 * sign + magnitude.contentHashCode()

    override fun toString(): String {
        if (sign == 0) return "0"

        return buildString {
            if (sign < 0) append('-')
            append(magnitude[magnitude.size - 1])
            for (index in magnitude.lastIndex - 1 downTo 0) {
                val limb = magnitude[index].toString()
                repeat(LIMB_DIGITS - limb.length) { append('0') }
                append(limb)
            }
        }
    }

    companion object {
        private const val BASE = 1_000_000_000
        private const val LIMB_DIGITS = 9
        private const val BASE_U = 1_000_000_000uL

        val ZERO = BigInteger(0, IntArray(0))
        val ONE = BigInteger(1, intArrayOf(1))
        val TWO = BigInteger(1, intArrayOf(2))
        val THREE = BigInteger(1, intArrayOf(3))
        val TEN = BigInteger(1, intArrayOf(10))

        fun parse(text: String): BigInteger {
            val value = text.trim()
            require(value.isNotEmpty()) { "Cannot parse an empty integer" }

            var start = 0
            var sign = 1
            if (value[0] == '+' || value[0] == '-') {
                if (value[0] == '-') sign = -1
                start = 1
            }
            require(start < value.length) { "Cannot parse '$text' as an integer" }

            for (index in start until value.length) {
                require(value[index] in '0'..'9') {
                    "Cannot parse '$text' as an integer"
                }
            }

            var firstDigit = start
            while (firstDigit < value.length && value[firstDigit] == '0') {
                firstDigit++
            }
            if (firstDigit == value.length) return ZERO

            val digitCount = value.length - firstDigit
            val limbCount = (digitCount + LIMB_DIGITS - 1) / LIMB_DIGITS
            val limbs = IntArray(limbCount)

            var end = value.length
            var limbIndex = 0
            while (end > firstDigit) {
                val limbStart = maxOf(firstDigit, end - LIMB_DIGITS)
                var limb = 0
                for (index in limbStart until end) {
                    limb = limb * 10 + (value[index] - '0')
                }
                limbs[limbIndex++] = limb
                end = limbStart
            }

            return create(sign, limbs)
        }

        fun fromLong(value: Long): BigInteger = parse(value.toString())

        fun fromInt(value: Int): BigInteger = fromLong(value.toLong())

        fun valueOf(value: Long): BigInteger = fromLong(value)

        fun valueOf(value: Int): BigInteger = fromLong(value.toLong())

        fun pow10(exponent: Int): BigInteger {
            require(exponent >= 0) { "Exponent must not be negative" }
            if (exponent == 0) return ONE

            val fullLimbs = exponent / LIMB_DIGITS
            val remainingDigits = exponent % LIMB_DIGITS
            var factor = 1
            repeat(remainingDigits) { factor *= 10 }

            val limbs = IntArray(fullLimbs + 1)
            limbs[fullLimbs] = factor
            return create(1, limbs)
        }

        private fun create(sign: Int, magnitude: IntArray): BigInteger {
            if (sign == 0) return ZERO

            var length = magnitude.size
            while (length > 0 && magnitude[length - 1] == 0) {
                length--
            }
            if (length == 0) return ZERO

            val normalized = if (length == magnitude.size) {
                magnitude
            } else {
                magnitude.copyOf(length)
            }
            return BigInteger(sign, normalized)
        }

        private fun compareMagnitudes(left: IntArray, right: IntArray): Int {
            if (left.size != right.size) return left.size.compareTo(right.size)

            for (index in left.lastIndex downTo 0) {
                if (left[index] != right[index]) {
                    return left[index].compareTo(right[index])
                }
            }
            return 0
        }

        private fun addMagnitudes(left: IntArray, right: IntArray): IntArray {
            val result = IntArray(maxOf(left.size, right.size) + 1)
            var carry = 0L

            for (index in result.indices) {
                val sum = left.getOrElse(index) { 0 }.toLong() +
                    right.getOrElse(index) { 0 }.toLong() +
                    carry
                result[index] = (sum % BASE).toInt()
                carry = sum / BASE
            }
            return trim(result)
        }

        /** Subtracts [right] from [left]. The caller guarantees that left >= right. */
        private fun subtractMagnitudes(left: IntArray, right: IntArray): IntArray {
            val result = IntArray(left.size)
            var borrow = 0L

            for (index in left.indices) {
                var difference = left[index].toLong() -
                    right.getOrElse(index) { 0 }.toLong() -
                    borrow
                if (difference < 0) {
                    difference += BASE
                    borrow = 1
                } else {
                    borrow = 0
                }
                result[index] = difference.toInt()
            }
            return trim(result)
        }

        private fun multiplyMagnitudes(left: IntArray, right: IntArray): IntArray {
            val result = IntArray(left.size + right.size)

            for (leftIndex in left.indices) {
                var carry = 0L
                val leftLimb = left[leftIndex].toLong()

                for (rightIndex in right.indices) {
                    val resultIndex = leftIndex + rightIndex
                    val product = result[resultIndex].toLong() +
                        leftLimb * right[rightIndex].toLong() +
                        carry
                    result[resultIndex] = (product % BASE).toInt()
                    carry = product / BASE
                }
                result[leftIndex + right.size] = carry.toInt()
            }
            return trim(result)
        }

        private fun multiplyMagnitudeByInt(value: IntArray, factor: Int): IntArray {
            if (value.isEmpty() || factor == 0) return IntArray(0)

            val result = IntArray(value.size + 1)
            var carry = 0L
            for (index in value.indices) {
                val product = value[index].toLong() * factor + carry
                result[index] = (product % BASE).toInt()
                carry = product / BASE
            }
            result[value.size] = carry.toInt()
            return trim(result)
        }

        /**
         * Schoolbook long division in base 1e9. Each quotient limb is found
         * with a binary search, which is more than fast enough for calculator
         * sized values and keeps the code compact.
         */
        private fun divideMagnitudes(
            dividend: IntArray,
            divisor: IntArray,
        ): Pair<IntArray, IntArray> {
            val quotient = IntArray(dividend.size)
            var remainder = IntArray(0)

            for (index in dividend.indices.reversed()) {
                remainder = appendDigit(remainder, dividend[index])

                var low = 0
                var high = BASE - 1
                while (low <= high) {
                    val middle = low + (high - low) / 2
                    val product = multiplyMagnitudeByInt(divisor, middle)
                    if (compareMagnitudes(product, remainder) <= 0) {
                        low = middle + 1
                    } else {
                        high = middle - 1
                    }
                }

                val quotientLimb = high
                quotient[index] = quotientLimb
                if (quotientLimb != 0) {
                    remainder = subtractMagnitudes(
                        remainder,
                        multiplyMagnitudeByInt(divisor, quotientLimb),
                    )
                }
            }

            return trim(quotient) to remainder
        }

        /**
         * Shifts [value] one base to the left and stores [digit] in the new
         * least-significant limb, i.e. returns `value * BASE + digit`.
         */
        private fun appendDigit(value: IntArray, digit: Int): IntArray {
            if (value.isEmpty() && digit == 0) return value

            val result = IntArray(value.size + 1)
            value.copyInto(result, destinationOffset = 1)
            result[0] = digit
            return trim(result)
        }

        private fun trim(value: IntArray): IntArray {
            var length = value.size
            while (length > 0 && value[length - 1] == 0) {
                length--
            }
            return if (length == value.size) value else value.copyOf(length)
        }
    }
}
