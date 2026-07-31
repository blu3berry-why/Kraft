package com.blu3berry.kraft.converter

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Issue #105: a nullable scalar property pair `X? → Y?` must thread a registered
 * non-null bridge (`@MapEnum` / `@KraftConverter`) through a safe call — the
 * scalar analogue of what collections already get with `?.map { it.toY() }`.
 */
@OptIn(ExperimentalCompilerApi::class)
class NullableScalarBridgeTest {

    @Test
    fun `nullable enum field threads a non-null @MapEnum bridge through a safe call`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class ReasonDto { LATE, MISSING }
            enum class Reason { LATE, MISSING }

            @com.blu3berry.kraft.config.MapEnum(source = ReasonDto::class, target = Reason::class)
            object ReasonMapping

            data class Src(val reason: ReasonDto?)
            data class Dst(val reason: Reason?)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val text = generated.first { it.name.contains("Src") }.readText()

        assertThat(text).contains("reason = this.reason?.toReason()")
    }

    @Test
    fun `nullable object field threads a non-null @KraftConverter bridge through a safe call`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            class StampDto(val raw: String)

            @com.blu3berry.kraft.config.KraftConverter
            fun StampDto.toIso(): String = raw

            data class Src(val stamp: StampDto?)
            data class Dst(val stamp: String?)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val text = generated.first { it.name.contains("Src") }.readText()

        assertThat(text).contains("stamp = this.stamp?.toIso()")
    }

    @Test
    fun `nullable source to non-null target is not lifted and still errors`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class ReasonDto { LATE, MISSING }
            enum class Reason { LATE, MISSING }

            @com.blu3berry.kraft.config.MapEnum(source = ReasonDto::class, target = Reason::class)
            object ReasonMapping

            data class Src(val reason: ReasonDto?)
            data class Dst(val reason: Reason)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("Type mismatch for property 'reason'")
    }

    @Test
    fun `type mismatch between same-simple-name types prints qualified names`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            data class Src(val status: models.dto.Status)
            data class Dst(val status: models.domain.Status)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )
        val dtoStatus = SourceFile.kotlin(
            "DtoStatus.kt",
            """
            package models.dto

            enum class Status { OK }
            """
        )
        val domainStatus = SourceFile.kotlin(
            "DomainStatus.kt",
            """
            package models.domain

            class Status(val v: Int)
            """
        )

        val result = TestKspRunner.compile(source, dtoStatus, domainStatus)

        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("models.dto.Status")
        assertThat(result.messages).contains("models.domain.Status")
    }

    @Test
    fun `nullability-only mismatch of the same class prints qualified names`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            class Status(val v: Int)

            data class Src(val status: Status?)
            data class Dst(val status: Status)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("models.Status?")
        assertThat(result.messages).contains("models.Status")
    }
}
