# kmp-bigdecimal

[![CI](https://github.com/sojo-se/kmp-bigdecimal/actions/workflows/ci.yml/badge.svg)](https://github.com/sojo-se/kmp-bigdecimal/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A dependency-free arbitrary-precision decimal library for Kotlin Multiplatform.

`kmp-bigdecimal` gives you a familiar `BigDecimal` API on Android, iOS, JVM,
JavaScript and WebAssembly without relying on `java.math`, JavaScript `BigInt`,
LibTomMath or another big-number library. All arithmetic is implemented in
common Kotlin, so results such as `0.1 + 0.2 = 0.3` are exact on every target.

## Supported targets

| Target | Status |
| --- | --- |
| Android (`minSdk 24`) | ✅ |
| iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`) | ✅ |
| JVM | ✅ |
| JavaScript / Node.js and browser | ✅ |
| WebAssembly / WasmJS, Node.js and browser | ✅ |

## Installation

The Maven group is `io.github.sojo-se`. Kotlin package identifiers cannot
contain a hyphen, so the package is `io.github.sojose.bigdecimal`.

Upgrading from 0.1.x? Replace imports of `io.github.sorjoh.bigdecimal.*` with
`io.github.sojose.bigdecimal.*`.

### Maven Central

The first release is being prepared with these coordinates:

```kotlin
commonMain.dependencies {
    implementation("io.github.sojo-se:kmp-bigdecimal:0.2.0")
}
```

### JitPack

Once the repository is tagged, JitPack can build it directly from GitHub:

```kotlin
repositories {
    maven("https://jitpack.io")
}

commonMain.dependencies {
    implementation("com.github.sojo-se:kmp-bigdecimal:0.2.0")
}
```

### Local build

```bash
./gradlew publishToMavenLocal
```

Then add `mavenLocal()` to your repositories and use:

```kotlin
commonMain.dependencies {
    implementation("io.github.sojo-se:kmp-bigdecimal:0.2.0")
}
```

## Quick start

```kotlin
import io.github.sojose.bigdecimal.BigDecimal
import io.github.sojose.bigdecimal.MathContext
import io.github.sojose.bigdecimal.RoundingMode

val exact = BigDecimal("0.1") + BigDecimal("0.2")
check(exact.toPlainString() == "0.3")

val third = BigDecimal.ONE.divide(
    BigDecimal("3"),
    scale = 16,
    roundingMode = RoundingMode.HALF_UP,
)
println(third) // 0.3333333333333333

val root = BigDecimal("2").sqrt(MathContext.DECIMAL128)
println(root) // 1.414213562373095048801688724209698

println(BigDecimal.PI) // 3.141592653589793238462643383279503
```

The primary factories are `BigDecimal.parse`, `BigDecimal.valueOf` and the
companion `invoke` helpers:

```kotlin
BigDecimal("123.45")
BigDecimal(12345L, 2)
BigDecimal(12.5)
BigDecimal.valueOf(12345, 2)
```

## API overview

### Exact arithmetic

- `add`, `subtract`, `multiply`
- `square`, `cube`
- `pow(Int)` for non-negative exponents
- Operators `+`, `-`, `*`, unary `+`, unary `-`
- `abs`, `negate`, `plus`, `min`, `max`
- `stripTrailingZeros`
- Exact integer conversion: `toBigInteger`, `toBigIntegerExact`

### Division and rounding

- `divide(other, scale, roundingMode)`
- `divide(other, roundingMode)`
- `divide(other, MathContext)`
- `divide(other)` using `MathContext.DECIMAL128`
- `divideToIntegralValue`, `remainder`, `divideAndRemainder`
- `setScale`, `round`, `floor`, `ceil`, `truncate`, `fractionalPart`
- `RoundingMode`: `UP`, `DOWN`, `CEILING`, `FLOOR`, `HALF_UP`, `HALF_DOWN`,
  `HALF_EVEN`, `UNNECESSARY`
- Legacy `ROUND_*` constants and integer rounding-mode overloads
- `MathContext`: `DECIMAL32`, `DECIMAL64`, `DECIMAL128`

### Powers and roots

- `pow(Int, MathContext)` including negative exponents
- `pow(BigDecimal, MathContext)` for decimal exponents
- `sqrt` / `squareRoot`
- `cbrt` / `cubeRoot`
- `root(n)` / `nthRoot(n)`
- `hypot`, `reciprocal`

### Exponential and logarithmic functions

- `exp`, `ln`
- `log10`, `log2`, `log(base)`
- `ln2`, `ln10`, `log2e`, `log10e`

### Trigonometry and hyperbolic functions

- `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2`
- `sinh`, `cosh`, `tanh`, `asinh`, `acosh`, `atanh`
- `toRadians`, `toDegrees`

### Constants

- `ZERO`, `ONE`, `TWO`, `THREE`, `FOUR`, `FIVE`, `TEN`, `HALF`, `HUNDRED`,
  `THOUSAND`
- `PI`, `E`, `TAU`, `TWO_PI`, `HALF_PI`, `QUARTER_PI`
- `LN2`, `LN10`, `LOG2E`, `LOG10E`
- `SQRT2`, `SQRT3`, `SQRT1_2`, `INV_SQRT2`
- `GOLDEN_RATIO`, `CUBE_ROOT_2`
- `DEGREES_PER_RADIAN`, `RADIANS_PER_DEGREE`

Constants and transcendental functions default to 34 significant digits
(`MathContext.DECIMAL128`). You can request a different precision:

```kotlin
val pi100 = BigDecimal.pi(MathContext(100))
val e50 = BigDecimal.e(MathContext(50))
```

### Conversions and formatting

- `toPlainString`, `toString`, `toEngineeringString`
- `toInt`, `toLong`, `toShort`, `toByte`, `toDouble`, `toFloat`
- `intValue`, `longValue`, `shortValue`, `byteValue`
- `intValueExact`, `longValueExact`, `shortValueExact`, `byteValueExact`
- `unscaledValue`, `precision`, `scale`, `signum`, `ulp`
- `isZero`, `isInteger`, `isNegative`, `isPositive`, `isOne`
- `movePointLeft`, `movePointRight`, `scaleByPowerOfTen`

### BigInteger

The library also exposes its pure Kotlin arbitrary-precision integer:

- `add`, `subtract`, `multiply`, `divide`, `remainder`, `mod`
- `divideAndRemainder`, `pow`, `gcd`, `lcm`
- `abs`, `negate`, `min`, `max`, `compareTo`
- `parse`, `fromInt`, `fromLong`, `valueOf`, `pow10`
- `toLong`, `toInt`, `toShort`, `toByte`, `toDouble`

### Convenience extensions

```kotlin
import io.github.sojose.bigdecimal.toBigDecimal
import io.github.sojose.bigdecimal.toBigInteger

"1.25".toBigDecimal()
42.toBigDecimal()
7L.toBigInteger()
```

## Accuracy

Addition, subtraction, multiplication and non-negative integer powers are
exact. Division, roots and transcendental functions compute with extra guard
digits internally and round only the final result to the requested
`MathContext` or scale.

The default context is 34 significant digits with `HALF_EVEN` rounding. The
test suite checks constants, roots, logarithms and trigonometric values against
known high-precision values.

## Design

- `BigInteger` stores a signed magnitude as little-endian base-1,000,000,000
  limbs.
- `BigDecimal` stores an arbitrary-precision unscaled value and a 32-bit scale:
  `unscaledValue × 10^-scale`.
- Arithmetic, rounding, roots and transcendental functions are pure Kotlin.
- There are no `expect`/`actual` declarations and no third-party runtime
  dependencies.
- `kotlin.math` is used only for small floating-point seed estimates; every
  stored digit and every final result is calculated by this library.

## Provenance and independence

This is an independent implementation. It was inspired by the goal of having a
BigDecimal-style API on all Kotlin Multiplatform targets, but it does not copy
code, tests, documentation or build files from other big-number projects.

The upstream `ArtificialPB/bignum-kt` snapshot reviewed on 2026-11-12 had no
LICENSE file and no license statement; the GitHub API reported
`"license": null`. Its architecture is also different (JVM type aliases,
JavaScript `BigInt`, LibTomMath native backends). See
[PROVENANCE.md](PROVENANCE.md) for the audit details and similarity results.

## Building and testing

Requirements: JDK 17 or newer. Android builds need the Android SDK, either via
`ANDROID_HOME` or a local `local.properties` file. iOS builds need macOS and
Xcode; the Kotlin iOS klibs also compile from other hosts.

```bash
# JVM tests, including randomized comparison with java.math.BigDecimal
./gradlew :jvmTest

# JavaScript and WebAssembly tests
./gradlew :jsNodeTest
./gradlew :wasmJsNodeTest

# Android unit tests
./gradlew :testDebugUnitTest

# Build an Android AAR / all publishable artifacts
./gradlew :assembleRelease
./gradlew publishToMavenLocal
```

The JS Node test task uses a 60-second Mocha timeout because high-precision
transcendental tests can exceed Mocha's two-second default on a cold runtime.

## Publishing

The project applies `maven-publish` and configures POM metadata for Maven
Central. To publish:

1. Set the version in `gradle.properties`.
2. Configure Maven Central credentials and GPG signing.
3. Run `./gradlew publishToMavenLocal` to inspect the artifacts.
4. Run `./gradlew publish` with the Sonatype credentials.

JitPack requires no publishing step: tag a release and use the
`com.github.sojo-se:kmp-bigdecimal:<tag>` coordinate.

## License

MIT License. See [LICENSE](LICENSE).

Copyright (c) 2026 Sören Johansson.

## Acknowledgements

The public API vocabulary follows the long-established Java/Kotlin BigDecimal
conventions. The existence and feature goals of
[ArtificialPB/bignum-kt](https://github.com/ArtificialPB/bignum-kt) were an
inspiration for offering this functionality across Kotlin Multiplatform
targets.
