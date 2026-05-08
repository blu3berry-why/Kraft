package com.blu3berry.kraft.sides

import com.blu3berry.kraft.processor.sides.SideRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SideRegistryCollisionTest {

    private fun registry() = SideRegistry.parseFromOptions(mapOf(
        "kraft.side.dto.name" to "Dto",
        "kraft.side.dto.packagePattern" to "**.data.**",
    ))

    @Test
    fun `recordAlias accepts unique pairs`() {
        val r = registry()
        r.recordAlias("com.x.A", "toDto", "MapperA")
        r.recordAlias("com.x.B", "toDto", "MapperB")
        // No throw.
    }

    @Test
    fun `recordAlias accepts same receiver with different alias names`() {
        val r = registry()
        r.recordAlias("com.x.A", "toDto", "MapperA")
        r.recordAlias("com.x.A", "toDtoOther", "MapperA2")
        // No throw.
    }

    @Test
    fun `recordAlias is idempotent for same triple`() {
        val r = registry()
        r.recordAlias("com.x.A", "toDto", "MapperA")
        r.recordAlias("com.x.A", "toDto", "MapperA") // same origin must not throw
    }

    @Test
    fun `duplicate (receiver, alias) raises with both mapper origins`() {
        val r = registry()
        r.recordAlias("com.x.A", "toDto", "MapperA")
        val ex = assertThrows<IllegalStateException> {
            r.recordAlias("com.x.A", "toDto", "MapperB")
        }
        assertThat(ex.message).contains("MapperA")
        assertThat(ex.message).contains("MapperB")
        assertThat(ex.message).contains("toDto")
        assertThat(ex.message).contains("com.x.A")
    }
}
