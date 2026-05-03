package com.blu3berry.kraft.optin

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class OptInPropagationTest {

    @Test
    fun `@OptIn on @KraftConverter propagates to generated function`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            @RequiresOptIn(level = RequiresOptIn.Level.WARNING)
            annotation class ExperimentalThing

            @com.blu3berry.kraft.config.KraftConverter
            @OptIn(ExperimentalThing::class)
            fun Int.toLabel(): String = "n=" + this

            data class Src(val count: Int)
            data class Dst(val count: String)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val text = generated.first { it.name.contains("Src") }.readText()

        assertThat(text).contains("@OptIn(ExperimentalThing::class)")
    }

    @Test
    fun `@RequiresOptIn marker on source class propagates as @OptIn on mapper`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            @RequiresOptIn(level = RequiresOptIn.Level.WARNING)
            annotation class ExperimentalSource

            @ExperimentalSource
            data class Src(val count: Int)

            data class Dst(val count: Int)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val text = generated.first { it.name.contains("Src") }.readText()

        assertThat(text).contains("@OptIn(ExperimentalSource::class)")
    }

    @Test
    fun `multiple markers across source target and converter dedupe`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            @RequiresOptIn(level = RequiresOptIn.Level.WARNING) annotation class MarkerA
            @RequiresOptIn(level = RequiresOptIn.Level.WARNING) annotation class MarkerB
            @RequiresOptIn(level = RequiresOptIn.Level.WARNING) annotation class MarkerC

            @com.blu3berry.kraft.config.KraftConverter
            @OptIn(MarkerA::class, MarkerC::class)
            fun Int.toLabel(): String = toString()

            @MarkerB
            data class Src(val count: Int)

            @OptIn(MarkerA::class)
            data class Dst(val count: String)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val text = generated.first { it.name.contains("Src") }.readText()

        assertThat(text).contains("MarkerA::class")
        assertThat(text).contains("MarkerB::class")
        assertThat(text).contains("MarkerC::class")
        // Each marker appears exactly once.
        assertThat("MarkerA::class".toRegex().findAll(text).count()).isEqualTo(1)
        assertThat("MarkerB::class".toRegex().findAll(text).count()).isEqualTo(1)
        assertThat("MarkerC::class".toRegex().findAll(text).count()).isEqualTo(1)
    }

    @Test
    fun `mapper without experimental dependencies has no @OptIn annotation`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            data class Src(val count: Int)
            data class Dst(val count: Int)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val text = generated.first { it.name.contains("Src") }.readText()

        assertThat(text).doesNotContain("@OptIn")
    }

    @Test
    fun `@OptIn on @MapUsing converter propagates to generated function`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            @RequiresOptIn(level = RequiresOptIn.Level.WARNING) annotation class ExperimentalConverter

            data class Src(val count: Int)
            data class Dst(val count: String)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper {
                @com.blu3berry.kraft.config.MapUsing(source = "count", target = "count")
                @OptIn(ExperimentalConverter::class)
                fun convert(v: Int): String = v.toString()
            }
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val text = generated.first { it.name.contains("Src") }.readText()

        assertThat(text).contains("@OptIn(ExperimentalConverter::class)")
    }
}
