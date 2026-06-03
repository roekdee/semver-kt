package io.roekdee.semver

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SemVerTest {

    @Test
    fun `parses a plain version`() {
        val v = SemVer.parse("1.2.3")
        assertEquals(1, v.major)
        assertEquals(2, v.minor)
        assertEquals(3, v.patch)
        assertTrue(v.prerelease.isEmpty())
        assertTrue(v.build.isEmpty())
        assertTrue(v.isStable)
    }

    @Test
    fun `parses pre-release and build metadata`() {
        val v = SemVer.parse("1.0.0-alpha.1+build.7")
        assertEquals(listOf("alpha", "1"), v.prerelease)
        assertEquals(listOf("build", "7"), v.build)
        assertFalse(v.isStable)
    }

    @Test
    fun `parses build metadata without pre-release`() {
        val v = SemVer.parse("1.0.0+20130313144700")
        assertTrue(v.prerelease.isEmpty())
        assertEquals(listOf("20130313144700"), v.build)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "0.0.0",
            "1.2.3",
            "10.20.30",
            "1.0.0-alpha",
            "1.0.0-alpha.1",
            "1.0.0-0.3.7",
            "1.0.0-x.7.z.92",
            "1.0.0-alpha+001",
            "1.0.0+20130313144700",
            "1.0.0-beta+exp.sha.5114f85",
            "1.0.0-rc.1+build.1",
            "1.0.0-x-y-z.--",
        ]
    )
    fun `accepts valid versions`(text: String) {
        assertTrue(SemVer.isValid(text), "expected '$text' to be valid")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "1",
            "1.2",
            "1.2.3.4",
            "01.2.3",
            "1.02.3",
            "1.2.03",
            "1.2.3-",
            "1.2.3+",
            "1.2.3-alpha..1",
            "1.2.3-01",
            "1.2.3-béta",
            "v1.2.3",
            "1.2.x",
            "-1.2.3",
            "1.2.3 ",
        ]
    )
    fun `rejects invalid versions`(text: String) {
        assertFalse(SemVer.isValid(text), "expected '$text' to be invalid")
        assertThrows<IllegalArgumentException> { SemVer.parse(text) }
    }

    @Test
    fun `core precedence is numeric`() {
        assertTrue(SemVer.parse("1.0.0") < SemVer.parse("2.0.0"))
        assertTrue(SemVer.parse("2.0.0") < SemVer.parse("2.1.0"))
        assertTrue(SemVer.parse("2.1.0") < SemVer.parse("2.1.1"))
    }

    @Test
    fun `pre-release has lower precedence than release`() {
        assertTrue(SemVer.parse("1.0.0-alpha") < SemVer.parse("1.0.0"))
        assertTrue(SemVer.parse("1.0.0") > SemVer.parse("1.0.0-rc.1"))
    }

    @Test
    fun `spec pre-release ordering example`() {
        // 1.0.0-alpha < 1.0.0-alpha.1 < 1.0.0-alpha.beta < 1.0.0-beta
        //   < 1.0.0-beta.2 < 1.0.0-beta.11 < 1.0.0-rc.1 < 1.0.0
        val ordered = listOf(
            "1.0.0-alpha",
            "1.0.0-alpha.1",
            "1.0.0-alpha.beta",
            "1.0.0-beta",
            "1.0.0-beta.2",
            "1.0.0-beta.11",
            "1.0.0-rc.1",
            "1.0.0",
        ).map(SemVer::parse)

        for (i in 0 until ordered.size - 1) {
            assertTrue(
                ordered[i] < ordered[i + 1],
                "expected ${ordered[i]} < ${ordered[i + 1]}",
            )
        }

        val shuffled = ordered.shuffled().sorted()
        assertEquals(ordered, shuffled)
    }

    @Test
    fun `numeric pre-release identifiers rank below alphanumeric`() {
        assertTrue(SemVer.parse("1.0.0-1") < SemVer.parse("1.0.0-alpha"))
        assertTrue(SemVer.parse("1.0.0-beta.2") < SemVer.parse("1.0.0-beta.11"))
    }

    @Test
    fun `build metadata is ignored for precedence`() {
        val a = SemVer.parse("1.0.0+build.1")
        val b = SemVer.parse("1.0.0+build.2")
        assertEquals(0, a.compareTo(b))
        // ...but the data classes still differ structurally.
        assertFalse(a == b)
    }

    @Test
    fun `equal versions compare to zero`() {
        assertEquals(0, SemVer.parse("1.2.3-rc.1").compareTo(SemVer.parse("1.2.3-rc.1")))
    }

    @Test
    fun `bump helpers reset lower components and drop metadata`() {
        val base = SemVer.parse("1.2.3-rc.1+build.5")
        assertEquals(SemVer(2, 0, 0), base.nextMajor())
        assertEquals(SemVer(1, 3, 0), base.nextMinor())
        assertEquals(SemVer(1, 2, 4), base.nextPatch())
        assertTrue(base.nextMajor().isStable)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "1.2.3",
            "0.0.1",
            "1.0.0-alpha.1",
            "1.0.0-alpha+001",
            "1.0.0-beta+exp.sha.5114f85",
            "10.20.30+build.123",
        ]
    )
    fun `toString round-trips through parse`(text: String) {
        val parsed = SemVer.parse(text)
        assertEquals(text, parsed.toString())
        assertEquals(parsed, SemVer.parse(parsed.toString()))
    }

    @Test
    fun `constructor rejects negative components`() {
        assertThrows<IllegalArgumentException> { SemVer(-1, 0, 0) }
        assertThrows<IllegalArgumentException> { SemVer(0, -1, 0) }
        assertThrows<IllegalArgumentException> { SemVer(0, 0, -1) }
    }

    @Test
    fun `constructor rejects malformed identifiers`() {
        assertThrows<IllegalArgumentException> { SemVer(1, 0, 0, prerelease = listOf("")) }
        assertThrows<IllegalArgumentException> { SemVer(1, 0, 0, prerelease = listOf("01")) }
        assertThrows<IllegalArgumentException> { SemVer(1, 0, 0, prerelease = listOf("bé")) }
        assertThrows<IllegalArgumentException> { SemVer(1, 0, 0, build = listOf("a b")) }
    }
}
