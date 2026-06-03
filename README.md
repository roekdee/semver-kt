# semver-kt

A small Kotlin/JVM library for parsing and comparing [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html) strings. It turns a version into its parts, orders versions by the spec's precedence rules, and has a few helpers for bumping major/minor/patch.

![CI](https://github.com/roekdee/semver-kt/actions/workflows/ci.yml/badge.svg)

## Usage

```kotlin
import io.roekdee.semver.SemVer

val v = SemVer.parse("1.4.2-rc.1+build.27")
v.major        // 1
v.prerelease   // [rc, 1]
v.build        // [build, 27]
v.isStable     // false

SemVer.isValid("1.0.0")   // true
SemVer.isValid("1.0")     // false

// Comparison follows §11 of the spec
SemVer.parse("1.0.0-beta.2") < SemVer.parse("1.0.0-beta.11")  // true (compared numerically)
SemVer.parse("1.0.0-rc.1")   < SemVer.parse("1.0.0")          // true (pre-release sorts first)

SemVer.parse("1.2.3").nextMajor()   // 2.0.0
SemVer.parse("1.2.3").nextMinor()   // 1.3.0
SemVer.parse("1.2.3").nextPatch()   // 1.2.4
```

`SemVer` is a data class, so `parse(v.toString()) == v` round-trips. Build metadata is ignored when ordering but kept on the value — two versions that differ only in build metadata compare equal under `compareTo`, but are still distinct under `equals`.

## Build & test

```bash
./gradlew test
```

Needs JDK 17. The Gradle wrapper is committed, so you don't need Gradle installed separately.

## Notes

The reason I wrote this instead of reaching for an existing parser was the pre-release precedence in §11 — the numeric-vs-alphanumeric ordering is the part that's easy to get subtly wrong, so the canonical spec chain is in the tests as a guard:

```
1.0.0-alpha < 1.0.0-alpha.1 < 1.0.0-alpha.beta < 1.0.0-beta
  < 1.0.0-beta.2 < 1.0.0-beta.11 < 1.0.0-rc.1 < 1.0.0
```

It's JVM-only at the moment — I didn't set it up as a multiplatform module. The next thing I'd add is range/constraint matching (`^1.2.0`, `>=1.0.0 <2.0.0`), since that's usually what you want right after parsing.

Built with Kotlin 1.9 and JUnit 5. CI runs on GitHub Actions.

## License

[MIT](LICENSE)
