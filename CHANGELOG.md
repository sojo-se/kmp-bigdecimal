# Changelog

All notable changes to this project are documented in this file.
The project follows [Semantic Versioning](https://semver.org/).

## [0.1.0] - 2026-11-12

### Added

- First public extraction of the local `:bigdecimal` module.
- Pure Kotlin `BigInteger` using base-1,000,000,000 limbs.
- Pure Kotlin `BigDecimal` with arbitrary precision and `Int` scale.
- Android, iOS, JVM, JavaScript and WebAssembly targets.
- Exact basic arithmetic, powers, square/cube roots and nth roots.
- Exponential, logarithmic, trigonometric and hyperbolic functions.
- `MathContext`, all `RoundingMode` values and legacy `ROUND_*` constants.
- Mathematical constants including `PI`, `E`, `TAU`, `LN2`, `LN10`, `SQRT2`,
  `GOLDEN_RATIO` and degree/radian conversion constants.
- JVM differential tests against `java.math.BigDecimal`.
