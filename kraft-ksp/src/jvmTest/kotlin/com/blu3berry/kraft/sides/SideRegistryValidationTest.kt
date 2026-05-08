package com.blu3berry.kraft.sides

import com.blu3berry.kraft.processor.sides.SideRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SideRegistryValidationTest {

    @Test
    fun `identical packagePattern on two sides fails`() {
        val ex = assertThrows<IllegalStateException> {
            SideRegistry.parseFromOptions(mapOf(
                "kraft.side.a.name" to "A",
                "kraft.side.a.packagePattern" to "**.data.**",
                "kraft.side.b.name" to "B",
                "kraft.side.b.packagePattern" to "**.data.**",
            ))
        }
        assertThat(ex.message).contains("identical")
        assertThat(ex.message).contains("kraft.side.a")
        assertThat(ex.message).contains("kraft.side.b")
        assertThat(ex.message).contains("build.gradle.kts")
    }

    @Test
    fun `strict subset packagePattern fails`() {
        val ex = assertThrows<IllegalStateException> {
            SideRegistry.parseFromOptions(mapOf(
                "kraft.side.outer.name" to "Data",
                "kraft.side.outer.packagePattern" to "**.data.**",
                "kraft.side.inner.name" to "Api",
                "kraft.side.inner.packagePattern" to "**.data.api.**",
            ))
        }
        assertThat(ex.message).contains("**.data.api.**")
        assertThat(ex.message).contains("**.data.**")
        assertThat(ex.message).contains("subset")
    }

    @Test
    fun `disjoint patterns are accepted`() {
        val r = SideRegistry.parseFromOptions(mapOf(
            "kraft.side.dto.name" to "Dto",
            "kraft.side.dto.packagePattern" to "**.data.**",
            "kraft.side.domain.name" to "Domain",
            "kraft.side.domain.packagePattern" to "**.domain.**",
        ))
        assertThat(r.sides).hasSize(2)
    }
}
