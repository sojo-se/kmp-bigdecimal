# Provenance and independence

This document records the provenance of `kmp-bigdecimal` and the technical
similarity audit performed before the initial public release. It is intended to
make the project's authorship and independent implementation easy to verify.

## Origin

The library was written as the local arbitrary-precision decimal module for a
Kotlin Multiplatform calculator. The author wanted a small, dependency-free
BigDecimal implementation that worked on Android, iOS, JVM, JavaScript and
WebAssembly.

The existence and general API goals of
[ArtificialPB/bignum-kt](https://github.com/ArtificialPB/bignum-kt) were an
inspiration for the feature set: a familiar BigDecimal-style API on every Kotlin
Multiplatform target. That is an idea and an API shape, not copied
implementation code.

## Upstream license check

The audit snapshot was taken on **2026-11-12**:

- Repository: `ArtificialPB/bignum-kt`, default branch `master`
- The cloned repository did not contain a `LICENSE`, `LICENSE.txt`, `COPYING`
  or `NOTICE` file.
- The upstream README did not contain a license section.
- The GitHub REST API reported `"license": null` for the repository.

A repository with no license grants no rights by default; all rights are
reserved by the copyright holder. For that reason this project does **not**
copy upstream source, tests, documentation or examples.

This observation is factual and is not legal advice.

## Implementation differences

The two projects solve the problem with different architectures:

| Area | `kmp-bigdecimal` | `ArtificialPB/bignum-kt` (audited snapshot) |
| --- | --- | --- |
| JVM/Android | Pure Kotlin implementation on every target | Type aliases to `java.math` |
| Integer representation | Base-1,000,000,000 `IntArray` limbs | JS `BigInt`, LibTomMath and hybrid native backends |
| Decimal representation | Pure Kotlin `unscaled × 10^-scale` | Platform/shared decimal backends |
| Native dependencies | None | LibTomMath and cinterop for native targets |
| Build structure | Single standalone module | Multi-module project with benchmark and fixture infrastructure |
| Transcendental functions | Machin/atan series, Newton roots, Taylor series | Different backend-specific implementation |

## Code similarity audit

The extracted source was compared with every non-test Kotlin file in the
upstream snapshot using normalized token sequences, `difflib.SequenceMatcher`
and exact-line overlap checks.

Results for the initial extraction:

- Highest token-sequence ratio: **0.35**, for the small convenience-factory
  file whose identifiers are mostly public API names.
- Highest ratio for the main `BigDecimal.kt`: **below 0.09**.
- Longest common token run: **45 tokens**, in the standard decimal
  `toPlainString` layout algorithm.
- Exact non-comment line overlap: **64 short lines**, consisting of generic
  Kotlin syntax, method signatures, rounding-mode enum names and common
  one-line conditions such as `if (sign == 0) return ZERO`.

No upstream source file, test file, comment block, README paragraph or build
file was copied. The comments and documentation in this repository are
original.

These measurements are technical evidence, not a legal opinion. Similarity
metrics cannot prove provenance by themselves; they are provided so that the
audit can be repeated or inspected.

## Public API naming

Names such as `BigDecimal`, `BigInteger`, `MathContext`, `RoundingMode`,
`add`, `multiply`, `divide`, `sqrt` and `toPlainString` come from the
long-established Java/Kotlin decimal vocabulary. Reusing an API name does not
mean reusing the implementation behind it.

## Evidence of authorship

- The standalone repository was initialized with an initial commit authored by
  Sören Johansson.
- Source files carry SPDX copyright headers.
- The commit history and release tags provide a dated record.
- The test suite is original and includes differential checks against the JDK.

If you need a formal legal opinion about copyright or licensing, consult an
intellectual-property lawyer in your jurisdiction.
