# Changelog

All notable changes to this project are documented in this file.
The project follows [Semantic Versioning](https://semver.org/).

## [0.2.0] - 2026-11-12

### Changed

- GitHub account and repository references moved from `sorjoh` to `sojo-se`.
- Maven group changed to `io.github.sojo-se`.
- Kotlin package changed to `io.github.sojose.bigdecimal`; package
  identifiers cannot contain a hyphen, so the package drops it.
- **Migration:** replace imports of
  `io.github.sorjoh.bigdecimal.*` with `io.github.sojose.bigdecimal.*` and use
  the new `io.github.sojo-se:kmp-bigdecimal` coordinates.

## [0.1.1] - 2026-11-12

### Changed

- Updated GitHub Actions to current major versions.
- Fixed the Android CI job by migrating from
  `android-actions/setup-android@v3` to `@v4` and explicitly installing the
  Android 35 platform and build tools.
- No library API or behaviour changes.

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
