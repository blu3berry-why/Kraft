package com.blu3berry.kraft.sides

import com.blu3berry.kraft.TestKspRunner
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class SideAliasEmitModeTest {

    @Test
    fun `FULL_NAME_ONLY at project level suppresses alias`() {
        val sources = listOf(
            SourceFile.kotlin("Dto.kt", """
                package app.data.generated.models
                data class FooDto(val v: Int)
            """),
            SourceFile.kotlin("Domain.kt", """
                package app.domain.model
                data class Foo(val v: Int)
            """),
            SourceFile.kotlin("Mapper.kt", """
                package app.mapper
                import app.data.generated.models.FooDto
                import app.domain.model.Foo
                @com.blu3berry.kraft.config.MapConfig(source = FooDto::class, target = Foo::class)
                object FooMapper
            """),
        )

        val generated = TestKspRunner.compileAndReturnGenerated(
            *sources.toTypedArray(),
            kspOptions = mapOf(
                "kraft.side.domain.name" to "Domain",
                "kraft.side.domain.packagePattern" to "**.domain.model.**",
                "kraft.side.domain.emitMode" to "FULL_NAME_ONLY",
            )
        )

        val joined = generated.joinToString("\n") { it.readText() }
        assertThat(joined).contains("fun FooDto.toFoo(")
        assertThat(joined).doesNotContain("fun FooDto.toDomain(")
    }

    @Test
    fun `per-mapper BOTH overrides project-level FULL_NAME_ONLY`() {
        val sources = listOf(
            SourceFile.kotlin("Dto.kt", """
                package app.data.generated.models
                data class FooDto(val v: Int)
            """),
            SourceFile.kotlin("Domain.kt", """
                package app.domain.model
                data class Foo(val v: Int)
            """),
            SourceFile.kotlin("Mapper.kt", """
                package app.mapper
                import app.data.generated.models.FooDto
                import app.domain.model.Foo
                import com.blu3berry.kraft.config.AliasEmitMode

                @com.blu3berry.kraft.config.MapConfig(
                    source = FooDto::class,
                    target = Foo::class,
                    aliasEmitMode = AliasEmitMode.BOTH
                )
                object FooMapper
            """),
        )

        val generated = TestKspRunner.compileAndReturnGenerated(
            *sources.toTypedArray(),
            kspOptions = mapOf(
                "kraft.side.domain.name" to "Domain",
                "kraft.side.domain.packagePattern" to "**.domain.model.**",
                "kraft.side.domain.emitMode" to "FULL_NAME_ONLY",
            )
        )

        val joined = generated.joinToString("\n") { it.readText() }
        assertThat(joined).contains("fun FooDto.toFoo(")
        assertThat(joined).contains("fun FooDto.toDomain(")
    }

    @Test
    fun `per-mapper FULL_NAME_ONLY overrides project-level BOTH`() {
        val sources = listOf(
            SourceFile.kotlin("Dto.kt", """
                package app.data.generated.models
                data class FooDto(val v: Int)
            """),
            SourceFile.kotlin("Domain.kt", """
                package app.domain.model
                data class Foo(val v: Int)
            """),
            SourceFile.kotlin("Mapper.kt", """
                package app.mapper
                import app.data.generated.models.FooDto
                import app.domain.model.Foo
                import com.blu3berry.kraft.config.AliasEmitMode

                @com.blu3berry.kraft.config.MapConfig(
                    source = FooDto::class,
                    target = Foo::class,
                    aliasEmitMode = AliasEmitMode.FULL_NAME_ONLY
                )
                object FooMapper
            """),
        )

        val generated = TestKspRunner.compileAndReturnGenerated(
            *sources.toTypedArray(),
            kspOptions = mapOf(
                "kraft.side.domain.name" to "Domain",
                "kraft.side.domain.packagePattern" to "**.domain.model.**",
            )
        )

        val joined = generated.joinToString("\n") { it.readText() }
        assertThat(joined).contains("fun FooDto.toFoo(")
        assertThat(joined).doesNotContain("fun FooDto.toDomain(")
    }
}
