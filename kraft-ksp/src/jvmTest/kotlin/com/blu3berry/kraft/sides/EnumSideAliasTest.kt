package com.blu3berry.kraft.sides

import com.blu3berry.kraft.TestKspRunner
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class EnumSideAliasTest {

    @Test
    fun `enum mapper target in registered side gets short alias delegate`() {
        val sources = listOf(
            SourceFile.kotlin("Dto.kt", """
                package app.feature.data.generated.models
                enum class StatusDto { ACTIVE, INACTIVE }
            """),
            SourceFile.kotlin("Domain.kt", """
                package app.feature.domain.model
                enum class Status { ACTIVE, INACTIVE }
            """),
            SourceFile.kotlin("Mapper.kt", """
                package app.feature.data.mapper

                import app.feature.data.generated.models.StatusDto
                import app.feature.domain.model.Status

                @com.blu3berry.kraft.config.MapEnum(
                    source = StatusDto::class,
                    target = Status::class
                )
                object StatusEnumMapper
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

        // Verbose function still emitted.
        assertThat(joined).contains("fun StatusDto.toStatus(")
        // Short alias emitted.
        assertThat(joined).contains("fun StatusDto.toDomain(")
        // Alias delegates to verbose.
        assertThat(joined).containsMatch("fun StatusDto\\.toDomain\\([^)]*\\)[^=]*=\\s*toStatus\\(")
    }

    @Test
    fun `enum mapper does not emit alias when no side matches target package`() {
        val sources = listOf(
            SourceFile.kotlin("Models.kt", """
                package app
                enum class StatusDto { ACTIVE, INACTIVE }
                enum class Status { ACTIVE, INACTIVE }

                @com.blu3berry.kraft.config.MapEnum(
                    source = StatusDto::class,
                    target = Status::class
                )
                object StatusEnumMapper
            """),
        )

        val generated = TestKspRunner.compileAndReturnGenerated(*sources.toTypedArray())
        val joined = generated.joinToString("\n") { it.readText() }

        assertThat(joined).contains("fun StatusDto.toStatus(")
        assertThat(joined).doesNotContain(".toDomain(")
    }

    @Test
    fun `aliasEmitMode FULL_NAME_ONLY on @MapEnum suppresses alias even when side matches`() {
        val sources = listOf(
            SourceFile.kotlin("Dto.kt", """
                package app.feature.data.generated.models
                enum class StatusDto { ACTIVE, INACTIVE }
            """),
            SourceFile.kotlin("Domain.kt", """
                package app.feature.domain.model
                enum class Status { ACTIVE, INACTIVE }
            """),
            SourceFile.kotlin("Mapper.kt", """
                package app.feature.data.mapper

                import app.feature.data.generated.models.StatusDto
                import app.feature.domain.model.Status
                import com.blu3berry.kraft.config.AliasEmitMode

                @com.blu3berry.kraft.config.MapEnum(
                    source = StatusDto::class,
                    target = Status::class,
                    aliasEmitMode = AliasEmitMode.FULL_NAME_ONLY
                )
                object StatusEnumMapper
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
        assertThat(joined).contains("fun StatusDto.toStatus(")
        assertThat(joined).doesNotContain(".toDomain(")
    }

    @Test
    fun `enum mapper alias collides with @MapConfig alias on same receiver`() {
        val sources = listOf(
            SourceFile.kotlin("Models.kt", """
                package app.shared
                data class Source(val v: Int)
                data class TargetA(val v: Int)
                enum class SourceEnum { ACTIVE }
                enum class TargetEnum { ACTIVE }
            """),
            SourceFile.kotlin("MapperConfig.kt", """
                package app.mapper
                import app.shared.Source
                import app.shared.TargetA

                @com.blu3berry.kraft.config.MapConfig(source = Source::class, target = TargetA::class)
                object SourceToTargetAMapper
            """),
            SourceFile.kotlin("MapperEnum.kt", """
                package app.mapper
                import app.shared.SourceEnum
                import app.shared.TargetEnum

                @com.blu3berry.kraft.config.MapEnum(source = SourceEnum::class, target = TargetEnum::class)
                object SourceEnumMapper
            """),
        )

        val result = TestKspRunner.compile(
            *sources.toTypedArray(),
            kspOptions = mapOf(
                "kraft.side.shared.name" to "Shared",
                "kraft.side.shared.packagePattern" to "app.shared.**",
            )
        )

        // Two distinct receivers (Source vs SourceEnum), so no collision — alias names share
        // the slot key but the receiver type differs. Both aliases emit and compile.
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `two @MapEnum mappers on same receiver collide on alias name`() {
        val sources = listOf(
            SourceFile.kotlin("Models.kt", """
                package app.shared
                enum class SourceEnum { ACTIVE }
                enum class TargetA { ACTIVE }
                enum class TargetB { ACTIVE }
            """),
            SourceFile.kotlin("MapperA.kt", """
                package app.mapper
                import app.shared.SourceEnum
                import app.shared.TargetA

                @com.blu3berry.kraft.config.MapEnum(source = SourceEnum::class, target = TargetA::class)
                object SourceToTargetAEnumMapper
            """),
            SourceFile.kotlin("MapperB.kt", """
                package app.mapper
                import app.shared.SourceEnum
                import app.shared.TargetB

                @com.blu3berry.kraft.config.MapEnum(source = SourceEnum::class, target = TargetB::class)
                object SourceToTargetBEnumMapper
            """),
        )

        val result = TestKspRunner.compile(
            *sources.toTypedArray(),
            kspOptions = mapOf(
                "kraft.side.shared.name" to "Shared",
                "kraft.side.shared.packagePattern" to "app.shared.**",
            )
        )

        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("SourceToTargetAEnumMapper")
        assertThat(result.messages).contains("SourceToTargetBEnumMapper")
        assertThat(result.messages).contains("toShared")
    }
}
