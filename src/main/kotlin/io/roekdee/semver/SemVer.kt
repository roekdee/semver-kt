package io.roekdee.semver

/**
 * An immutable representation of a Semantic Version as defined by the
 * [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html) specification.
 *
 * Instances are comparable according to the precedence rules in the spec:
 * - Major, minor and patch are compared numerically.
 * - A version with a pre-release has *lower* precedence than the associated normal version.
 * - Pre-release identifiers are compared field by field; numeric identifiers compare
 *   numerically and always have lower precedence than alphanumeric identifiers.
 * - Build metadata is ignored when determining precedence.
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String> = emptyList(),
    val build: List<String> = emptyList(),
) : Comparable<SemVer> {

    init {
        require(major >= 0) { "major must be non-negative, was $major" }
        require(minor >= 0) { "minor must be non-negative, was $minor" }
        require(patch >= 0) { "patch must be non-negative, was $patch" }
        prerelease.forEach { id ->
            require(id.isNotEmpty()) { "pre-release identifiers must not be empty" }
            require(id.all(::isAllowedIdentifierChar)) {
                "pre-release identifier '$id' contains invalid characters"
            }
            require(!isNumericWithLeadingZero(id)) {
                "numeric pre-release identifier '$id' must not contain leading zeroes"
            }
        }
        build.forEach { id ->
            require(id.isNotEmpty()) { "build identifiers must not be empty" }
            require(id.all(::isAllowedIdentifierChar)) {
                "build identifier '$id' contains invalid characters"
            }
        }
    }

    /** `true` when this version carries no pre-release identifiers (a stable release). */
    val isStable: Boolean get() = prerelease.isEmpty()

    /** Returns the next major release: `(major+1).0.0`, dropping pre-release and build metadata. */
    fun nextMajor(): SemVer = SemVer(major + 1, 0, 0)

    /** Returns the next minor release: `major.(minor+1).0`, dropping pre-release and build metadata. */
    fun nextMinor(): SemVer = SemVer(major, minor + 1, 0)

    /** Returns the next patch release: `major.minor.(patch+1)`, dropping pre-release and build metadata. */
    fun nextPatch(): SemVer = SemVer(major, minor, patch + 1)

    override fun compareTo(other: SemVer): Int {
        (major - other.major).let { if (it != 0) return it.coerceSign() }
        (minor - other.minor).let { if (it != 0) return it.coerceSign() }
        (patch - other.patch).let { if (it != 0) return it.coerceSign() }
        return comparePrerelease(prerelease, other.prerelease)
    }

    /**
     * Renders this version back to its canonical string form. Parsing the result with
     * [parse] yields an equal [SemVer], so `parse(v.toString()) == v` always holds.
     */
    override fun toString(): String = buildString {
        append(major).append('.').append(minor).append('.').append(patch)
        if (prerelease.isNotEmpty()) {
            append('-').append(prerelease.joinToString("."))
        }
        if (build.isNotEmpty()) {
            append('+').append(build.joinToString("."))
        }
    }

    companion object {
        // major.minor.patch with optional -prerelease and optional +build
        private val CORE = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""")

        /**
         * Parses [text] into a [SemVer].
         *
         * @throws IllegalArgumentException if [text] is not a valid Semantic Version 2.0.0 string.
         */
        fun parse(text: String): SemVer {
            require(text.isNotEmpty()) { "version string must not be empty" }

            var rest = text
            var build: List<String> = emptyList()
            var prerelease: List<String> = emptyList()

            val plus = rest.indexOf('+')
            if (plus >= 0) {
                build = splitIdentifiers(rest.substring(plus + 1), "build")
                rest = rest.substring(0, plus)
            }

            val dash = rest.indexOf('-')
            if (dash >= 0) {
                prerelease = splitIdentifiers(rest.substring(dash + 1), "pre-release")
                rest = rest.substring(0, dash)
            }

            val match = CORE.matchEntire(rest)
                ?: throw IllegalArgumentException("invalid version core '$rest' in '$text'")
            val (maj, min, pat) = match.destructured

            return SemVer(
                major = maj.toIntOrThrow("major", text),
                minor = min.toIntOrThrow("minor", text),
                patch = pat.toIntOrThrow("patch", text),
                prerelease = validatePrerelease(prerelease, text),
                build = build,
            )
        }

        /** Returns `true` if [text] is a valid Semantic Version 2.0.0 string, `false` otherwise. */
        fun isValid(text: String): Boolean =
            try {
                parse(text)
                true
            } catch (_: IllegalArgumentException) {
                false
            }

        private fun splitIdentifiers(segment: String, kind: String): List<String> {
            require(segment.isNotEmpty()) { "$kind metadata must not be empty" }
            val parts = segment.split('.')
            parts.forEach { part ->
                require(part.isNotEmpty()) { "empty $kind identifier in '$segment'" }
                require(part.all(::isAllowedIdentifierChar)) {
                    "$kind identifier '$part' contains invalid characters"
                }
            }
            return parts
        }

        private fun validatePrerelease(parts: List<String>, text: String): List<String> {
            parts.forEach { part ->
                require(!isNumericWithLeadingZero(part)) {
                    "numeric pre-release identifier '$part' must not have leading zeroes in '$text'"
                }
            }
            return parts
        }

        private fun String.toIntOrThrow(field: String, source: String): Int =
            toIntOrNull() ?: throw IllegalArgumentException("$field '$this' out of range in '$source'")
    }
}

private fun isAllowedIdentifierChar(c: Char): Boolean =
    c.isDigit() || c in 'a'..'z' || c in 'A'..'Z' || c == '-'

private fun isNumeric(id: String): Boolean = id.isNotEmpty() && id.all { it.isDigit() }

private fun isNumericWithLeadingZero(id: String): Boolean =
    isNumeric(id) && id.length > 1 && id[0] == '0'

/** Normalises an arbitrary difference to one of -1, 0, 1 so callers never see overflow surprises. */
private fun Int.coerceSign(): Int = when {
    this > 0 -> 1
    this < 0 -> -1
    else -> 0
}

private fun comparePrerelease(a: List<String>, b: List<String>): Int {
    // A version without pre-release has higher precedence than one with.
    if (a.isEmpty() && b.isEmpty()) return 0
    if (a.isEmpty()) return 1
    if (b.isEmpty()) return -1

    val limit = minOf(a.size, b.size)
    for (i in 0 until limit) {
        val cmp = compareIdentifier(a[i], b[i])
        if (cmp != 0) return cmp
    }
    // All shared identifiers equal: the longer set has higher precedence.
    return a.size.compareTo(b.size).coerceSign()
}

private fun compareIdentifier(x: String, y: String): Int {
    val xNum = isNumeric(x)
    val yNum = isNumeric(y)
    return when {
        xNum && yNum -> x.toLong().compareTo(y.toLong()).coerceSign()
        xNum -> -1 // numeric identifiers always rank lower than alphanumeric
        yNum -> 1
        else -> x.compareTo(y).coerceSign()
    }
}
