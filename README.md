# semver-kt

A spec-correct [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html) parser & comparator for Kotlin/JVM — parsing, precedence ordering, and bump helpers.

![CI](https://github.com/roekdee/semver-kt/actions/workflows/ci.yml/badge.svg)

## Features

- **Parsing** — `SemVer.parse(String)` returns a structured version or throws on invalid input; `SemVer.isValid(String)` for a boolean check.
- **Full spec coverage** — validates numeric core components (no leading zeroes), dot-separated pre-release and build identifiers, and the allowed character set `[0-9A-Za-z-]`.
- **Correct precedence** — implements `Comparable<SemVer>` per §11: numeric vs alphanumeric pre-release identifiers, pre-release < release, and build metadata ignored when ordering.
- **Bump helpers** — `nextMajor()`, `nextMinor()`, `nextPatch()` reset lower components and drop pre-release/build metadata.
- **Lossless round-trip** — `SemVer.parse(v.toString()) == v` always holds.
- **Immutable** — a Kotlin `data class` with no mutable state.

## Usage

```kotlin
import io.roekdee.semver.SemVer

// Parse
val v = SemVer.parse("1.4.2-rc.1+build.27")
println(v.major)        // 1
println(v.prerelease)   // [rc, 1]
println(v.build)        // [build, 27]
println(v.isStable)     // false

// Validate
SemVer.isValid("1.0.0")     // true
SemVer.isValid("1.0")       // false

// Compare (precedence per the spec)
SemVer.parse("1.0.0-alpha") < SemVer.parse("1.0.0-alpha.1")   // true
SemVer.parse("1.0.0-beta.2") < SemVer.parse("1.0.0-beta.11")  // true (numeric)
SemVer.parse("1.0.0-rc.1") < SemVer.parse("1.0.0")            // true (pre-release < release)

val sorted = listOf("1.0.0", "1.0.0-alpha", "1.0.0-beta")
    .map(SemVer::parse)
    .sorted()
// [1.0.0-alpha, 1.0.0-beta, 1.0.0]

// Bump
SemVer.parse("1.2.3").nextMajor()   // 2.0.0
SemVer.parse("1.2.3").nextMinor()   // 1.3.0
SemVer.parse("1.2.3").nextPatch()   // 1.2.4
```

## Build & test

```bash
./gradlew test
```

Requires JDK 17. The Gradle wrapper is committed, so no separate Gradle install is needed.

## Notes on precedence rules

Ordering follows Semantic Versioning 2.0.0 §11:

1. Major, minor, and patch are compared numerically in that order.
2. A version **with** a pre-release has *lower* precedence than the otherwise-equal version **without** one (`1.0.0-rc.1 < 1.0.0`).
3. Pre-release identifiers are compared left to right:
   - Identifiers consisting only of digits are compared numerically.
   - Identifiers with letters or hyphens are compared lexically in ASCII order.
   - Numeric identifiers always have **lower** precedence than alphanumeric ones.
   - If all shared identifiers are equal, the version with **more** identifiers has higher precedence.
4. **Build metadata is ignored** when determining precedence: `1.0.0+build.1` and `1.0.0+build.2` compare equal. (They remain distinct under data-class `equals`, which keeps the round-trip property intact.)

The canonical spec example holds:

```
1.0.0-alpha < 1.0.0-alpha.1 < 1.0.0-alpha.beta < 1.0.0-beta
  < 1.0.0-beta.2 < 1.0.0-beta.11 < 1.0.0-rc.1 < 1.0.0
```

## Tech

- Kotlin 1.9 (JVM), JDK 17 toolchain
- Gradle (Kotlin DSL, `build.gradle.kts`) with committed wrapper
- JUnit 5 (Jupiter + Parameterized) and `kotlin-test`
- GitHub Actions CI on `ubuntu-latest`
