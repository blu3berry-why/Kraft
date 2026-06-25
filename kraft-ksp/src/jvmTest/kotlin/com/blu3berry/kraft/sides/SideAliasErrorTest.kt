package com.blu3berry.kraft.sides

import com.blu3berry.kraft.TestKspRunner
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class SideAliasErrorTest {

    @Test
    fun `alias name collision fails compilation with both mapper origins`() {
        val sources = listOf(
            SourceFile.kotlin("Models.kt", """
                package app.shared
                data class Source(val v: Int)
                data class TargetA(val v: Int)
                data class TargetB(val v: Int)
            """),
            SourceFile.kotlin("MapperA.kt", """
                package app.mapper
                import app.shared.Source
                import app.shared.TargetA

                @com.blu3berry.kraft.config.MapConfig(source = Source::class, target = TargetA::class)
                object SourceToTargetAMapper
            """),
            SourceFile.kotlin("MapperB.kt", """
                package app.mapper
                import app.shared.Source
                import app.shared.TargetB

                @com.blu3berry.kraft.config.MapConfig(source = Source::class, target = TargetB::class)
                object SourceToTargetBMapper
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
        assertThat(result.messages).contains("SourceToTargetAMapper")
        assertThat(result.messages).contains("SourceToTargetBMapper")
        assertThat(result.messages).contains("toShared")
    }

    @Test
    fun `pattern-overlap at config load surfaces as gradle config error`() {
        val sources = listOf(
            SourceFile.kotlin("Models.kt", """
                package app.x
                data class A(val v: Int)
                data class B(val v: Int)

                @com.blu3berry.kraft.config.MapConfig(source = A::class, target = B::class)
                object ABMapper
            """),
        )

        val result = TestKspRunner.compile(
            *sources.toTypedArray(),
            kspOptions = mapOf(
                "kraft.side.outer.name" to "Outer",
                "kraft.side.outer.packagePattern" to "**.data.**",
                "kraft.side.inner.name" to "Inner",
                "kraft.side.inner.packagePattern" to "**.data.api.**",
            )
        )

        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("build.gradle.kts")
        assertThat(result.messages).contains("subset")
    }

    @Test
    fun `no-side-registered project compiles unchanged`() {
        val sources = listOf(
            SourceFile.kotlin("Models.kt", """
                package app
                data class S(val v: Int)
                data class T(val v: Int)

                @com.blu3berry.kraft.config.MapConfig(source = S::class, target = T::class)
                object Mapper
            """),
        )

        val generated = TestKspRunner.compileAndReturnGenerated(*sources.toTypedArray())
        val joined = generated.joinToString("\n") { it.readText() }
        assertThat(joined).contains("fun S.toT(")
        assertThat(joined).doesNotContain(".toDomain(")
    }
}
