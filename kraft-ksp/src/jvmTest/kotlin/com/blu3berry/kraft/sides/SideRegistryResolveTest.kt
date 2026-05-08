package com.blu3berry.kraft.sides

import com.blu3berry.kraft.processor.sides.SideRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SideRegistryResolveTest {

    private fun registry() = SideRegistry.parseFromOptions(mapOf(
        "kraft.side.dto.name" to "Dto",
        "kraft.side.dto.packagePattern" to "**.data.generated.models.**",
        "kraft.side.domain.name" to "Domain",
        "kraft.side.domain.packagePattern" to "**.domain.model.**",
    ))

    @Test
    fun `unique match returns the side`() {
        val side = registry().resolveSide("hu.x.feature.domain.model.Category")
        assertThat(side).isNotNull()
        assertThat(side!!.name).isEqualTo("Domain")
    }

    @Test
    fun `no match returns null`() {
        assertThat(registry().resolveSide("hu.x.feature.something.else.X")).isNull()
    }

    @Test
    fun `runtime multi-match raises gradle-config error`() {
        // These patterns are not provable subsets at config load (they
        // diverge mid-pattern), but a class matches both at runtime.
        val r = SideRegistry.parseFromOptions(mapOf(
            "kraft.side.a.name" to "A",
            "kraft.side.a.packagePattern" to "**.data.**.UserDto",
            "kraft.side.b.name" to "B",
            "kraft.side.b.packagePattern" to "**.api.**.UserDto",
        ))
        val ex = assertThrows<IllegalStateException> {
            r.resolveSide("com.x.data.api.UserDto")
        }
        assertThat(ex.message).contains("com.x.data.api.UserDto")
        assertThat(ex.message).contains("kraft.side.a")
        assertThat(ex.message).contains("kraft.side.b")
        assertThat(ex.message).contains("build.gradle.kts")
    }
}
