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
    fun `markers survive typealiased receiver and return types on delegate registry`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            @RequiresOptIn(level = RequiresOptIn.Level.WARNING)
            annotation class ExperimentalId

            @RequiresOptIn(level = RequiresOptIn.Level.WARNING)
            @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS)
            annotation class ExperimentalAlias

            @ExperimentalId
            class StampId(val raw: String)

            @ExperimentalAlias
            typealias AliasedId = StampId

            @com.blu3berry.kraft.config.KraftConverter
            fun String.toAliasedId(): AliasedId = StampId(this)

            @com.blu3berry.kraft.config.KraftConverter
            fun AliasedId.toRaw(): String = raw
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val text = generated.first { it.name.startsWith("Converters_") }.readText()

        // The underlying class's marker must be found through the alias, and the
        // alias's own marker must be collected too — one delegate per direction.
        assertThat("ExperimentalId::class".toRegex().findAll(text).count()).isEqualTo(2)
        assertThat("ExperimentalAlias::class".toRegex().findAll(text).count()).isEqualTo(2)
    }

    @Test
    fun `file-level @file OptIn on the source class file propagates to the mapper`() {
        val markers = SourceFile.kotlin(
            "Markers.kt",
            """
            package models

            @RequiresOptIn(level = RequiresOptIn.Level.WARNING)
            annotation class ExperimentalModel
            """
        )
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            @file:OptIn(models.ExperimentalModel::class)
            package models

            data class Src(val count: Int)
            data class Dst(val count: Int)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(markers, source)
        val text = generated.first { it.name.contains("Src") }.readText()

        assertThat(text).contains("@OptIn(ExperimentalModel::class)")
    }

    @Test
    fun `file-level @file OptIn on the converter file propagates to the delegate registry`() {
        val markers = SourceFile.kotlin(
            "Markers.kt",
            """
            package models

            @RequiresOptIn(level = RequiresOptIn.Level.WARNING)
            annotation class ExperimentalConv
            """
        )
        val source = SourceFile.kotlin(
            "Converters.kt",
            """
            @file:OptIn(models.ExperimentalConv::class)
            package models

            @com.blu3berry.kraft.config.KraftConverter
            fun Int.toLabel(): String = "n=" + this
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(markers, source)
        val text = generated.first { it.name.startsWith("Converters_") }.readText()

        assertThat(text).contains("ExperimentalConv::class")
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
