package com.blu3berry.kraft.sides

import com.blu3berry.kraft.config.AliasEmitMode
import com.blu3berry.kraft.processor.sides.SideRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SideRegistryParseTest {

    @Test
    fun `empty options yield empty registry`() {
        val r = SideRegistry.parseFromOptions(emptyMap())
        assertThat(r.sides).isEmpty()
    }

    @Test
    fun `single side parses with all fields`() {
        val r = SideRegistry.parseFromOptions(mapOf(
            "kraft.side.dto.name" to "Dto",
            "kraft.side.dto.packagePattern" to "**.data.**",
            "kraft.side.dto.template" to "to{side}",
            "kraft.side.dto.emitMode" to "BOTH",
        ))
        assertThat(r.sides).hasSize(1)
        val side = r.sides.single()
        assertThat(side.slot).isEqualTo("dto")
        assertThat(side.name).isEqualTo("Dto")
        assertThat(side.packagePattern.raw).isEqualTo("**.data.**")
        assertThat(side.template.raw).isEqualTo("to{side}")
        assertThat(side.emitMode).isEqualTo(AliasEmitMode.BOTH)
    }

    @Test
    fun `template defaults to to-side`() {
        val r = SideRegistry.parseFromOptions(mapOf(
            "kraft.side.dto.name" to "Dto",
            "kraft.side.dto.packagePattern" to "**.data.**",
        ))
        assertThat(r.sides.single().template.raw).isEqualTo("to{side}")
    }

    @Test
    fun `emitMode defaults to BOTH`() {
        val r = SideRegistry.parseFromOptions(mapOf(
            "kraft.side.dto.name" to "Dto",
            "kraft.side.dto.packagePattern" to "**.data.**",
        ))
        assertThat(r.sides.single().emitMode).isEqualTo(AliasEmitMode.BOTH)
    }

    @Test
    fun `multiple sides parse independently`() {
        val r = SideRegistry.parseFromOptions(mapOf(
            "kraft.side.dto.name" to "Dto",
            "kraft.side.dto.packagePattern" to "**.data.**",
            "kraft.side.domain.name" to "Domain",
            "kraft.side.domain.packagePattern" to "**.domain.**",
        ))
        assertThat(r.sides.map { it.slot }).containsExactly("dto", "domain")
    }

    @Test
    fun `unrelated KSP options are ignored`() {
        val r = SideRegistry.parseFromOptions(mapOf(
            "kraft.functionNameFormat" to "to\${target}",
            "kraft.side.dto.name" to "Dto",
            "kraft.side.dto.packagePattern" to "**.data.**",
        ))
        assertThat(r.sides).hasSize(1)
    }

    @Test
    fun `unknown emitMode value fails`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            SideRegistry.parseFromOptions(mapOf(
                "kraft.side.dto.name" to "Dto",
                "kraft.side.dto.packagePattern" to "**.data.**",
                "kraft.side.dto.emitMode" to "ALIAS_ONLY",
            ))
        }
    }
}
