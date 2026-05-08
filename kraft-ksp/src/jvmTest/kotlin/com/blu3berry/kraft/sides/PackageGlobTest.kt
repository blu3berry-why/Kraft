package com.blu3berry.kraft.sides

import com.blu3berry.kraft.processor.sides.PackageGlob
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PackageGlobTest {

    @Test
    fun `single segment wildcard matches one segment`() {
        val glob = PackageGlob.parse("com.x.*.User")
        assertThat(glob.matches("com.x.feature.User")).isTrue()
        assertThat(glob.matches("com.x.User")).isFalse()
        assertThat(glob.matches("com.x.feature.sub.User")).isFalse()
    }

    @Test
    fun `double-star matches zero segments`() {
        val glob = PackageGlob.parse("com.x.**.User")
        assertThat(glob.matches("com.x.User")).isTrue()
    }

    @Test
    fun `double-star matches multiple segments`() {
        val glob = PackageGlob.parse("com.x.**.User")
        assertThat(glob.matches("com.x.a.b.c.User")).isTrue()
    }

    @Test
    fun `leading double-star matches any prefix`() {
        val glob = PackageGlob.parse("**.domain.model.Category")
        assertThat(glob.matches("hu.x.feature.domain.model.Category")).isTrue()
        assertThat(glob.matches("domain.model.Category")).isTrue()
    }

    @Test
    fun `trailing double-star matches any suffix`() {
        val glob = PackageGlob.parse("com.x.**")
        assertThat(glob.matches("com.x")).isTrue()
        assertThat(glob.matches("com.x.a.b.User")).isTrue()
    }

    @Test
    fun `match is case-sensitive`() {
        val glob = PackageGlob.parse("com.X.User")
        assertThat(glob.matches("com.x.User")).isFalse()
    }

    @Test
    fun `pattern without wildcards matches exactly`() {
        val glob = PackageGlob.parse("com.x.User")
        assertThat(glob.matches("com.x.User")).isTrue()
        assertThat(glob.matches("com.x.UserDto")).isFalse()
    }

    @Test
    fun `invalid syntax throws IllegalArgumentException`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            PackageGlob.parse("com.x.***")
        }
    }

    @Test
    fun `isStrictSubsetOf detects nested patterns`() {
        val outer = PackageGlob.parse("**.data.**")
        val inner = PackageGlob.parse("**.data.api.**")
        assertThat(inner.isStrictSubsetOf(outer)).isTrue()
        assertThat(outer.isStrictSubsetOf(inner)).isFalse()
    }

    @Test
    fun `isStrictSubsetOf returns false for disjoint patterns`() {
        val a = PackageGlob.parse("**.data.**")
        val b = PackageGlob.parse("**.domain.**")
        assertThat(a.isStrictSubsetOf(b)).isFalse()
        assertThat(b.isStrictSubsetOf(a)).isFalse()
    }

    @Test
    fun `equal patterns are not strict subsets of each other`() {
        val a = PackageGlob.parse("**.data.**")
        val b = PackageGlob.parse("**.data.**")
        assertThat(a.isStrictSubsetOf(b)).isFalse()
    }
}
