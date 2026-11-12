/*
 * Copyright (c) 2026 Sören Johansson
 * SPDX-License-Identifier: MIT
 */

package io.github.sojose.bigdecimal

/**
 * Small convenience factories and conversion extensions modelled after the
 * familiar `kotlin-multiplatform-bignum` API. They are optional sugar; every
 * operation delegates to the local [BigDecimal] and [BigInteger] types.
 */

fun bigDecimalOf(text: String): BigDecimal = BigDecimal.parse(text)

fun bigDecimalOf(value: Int): BigDecimal = BigDecimal.valueOf(value)

fun bigDecimalOf(value: Long): BigDecimal = BigDecimal.valueOf(value)

fun bigDecimalOf(value: Double): BigDecimal = BigDecimal.valueOf(value)

fun bigDecimalOf(value: BigInteger): BigDecimal = BigDecimal.valueOf(value)

fun bigDecimalOf(value: BigInteger, scale: Int): BigDecimal =
    BigDecimal.valueOf(value, scale)

fun bigIntegerOf(text: String): BigInteger = BigInteger.parse(text)

fun bigIntegerOf(value: Int): BigInteger = BigInteger.fromInt(value)

fun bigIntegerOf(value: Long): BigInteger = BigInteger.fromLong(value)

fun String.toBigDecimal(): BigDecimal = BigDecimal.parse(this)

fun String.toBigInteger(): BigInteger = BigInteger.parse(this)

fun Int.toBigDecimal(): BigDecimal = BigDecimal.valueOf(this)

fun Long.toBigDecimal(): BigDecimal = BigDecimal.valueOf(this)

fun Double.toBigDecimal(): BigDecimal = BigDecimal.valueOf(this)

fun Int.toBigInteger(): BigInteger = BigInteger.fromInt(this)

fun Long.toBigInteger(): BigInteger = BigInteger.fromLong(this)

fun BigInteger.toBigDecimal(scale: Int = 0): BigDecimal =
    BigDecimal.valueOf(this, scale)
