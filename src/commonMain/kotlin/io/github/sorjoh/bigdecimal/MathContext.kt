/*
 * Copyright (c) 2026 Sören Johansson
 * SPDX-License-Identifier: MIT
 */

package io.github.sorjoh.bigdecimal

/**
 * Rounding modes compatible with the familiar Java/Kotlin decimal APIs.
 */
enum class RoundingMode {
    UP,
    DOWN,
    CEILING,
    FLOOR,
    HALF_UP,
    HALF_DOWN,
    HALF_EVEN,
    UNNECESSARY;

    /** Legacy integer value, matching `java.math.BigDecimal.ROUND_*`. */
    fun toInt(): Int = when (this) {
        UP -> 0
        DOWN -> 1
        CEILING -> 2
        FLOOR -> 3
        HALF_UP -> 4
        HALF_DOWN -> 5
        HALF_EVEN -> 6
        UNNECESSARY -> 7
    }

    companion object {
        /** Converts a legacy `ROUND_*` integer to a [RoundingMode]. */
        fun fromInt(value: Int): RoundingMode = when (value) {
            0 -> UP
            1 -> DOWN
            2 -> CEILING
            3 -> FLOOR
            4 -> HALF_UP
            5 -> HALF_DOWN
            6 -> HALF_EVEN
            7 -> UNNECESSARY
            else -> throw IllegalArgumentException("Unknown rounding mode: $value")
        }
    }
}

/**
 * A precision/rounding pair used by [BigDecimal.divide] and
 * [BigDecimal.round].
 */
data class MathContext(
    val precision: Int,
    val roundingMode: RoundingMode = RoundingMode.HALF_UP,
) {
    init {
        require(precision > 0) { "Precision must be greater than zero" }
    }

    companion object {
        val DECIMAL32 = MathContext(7, RoundingMode.HALF_EVEN)
        val DECIMAL64 = MathContext(16, RoundingMode.HALF_EVEN)
        val DECIMAL128 = MathContext(34, RoundingMode.HALF_EVEN)
    }
}
