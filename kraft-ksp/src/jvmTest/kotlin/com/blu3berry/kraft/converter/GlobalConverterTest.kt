package com.blu3berry.kraft.converter

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class GlobalConverterTest {

    @Test
    fun `global converter resolves mismatched property type`() {
        val converters = SourceFile.kotlin(
            "Converters.kt",
            """
            package converters

            @com.blu3berry.kraft.config.KraftConverter
            fun Int.toLabel(): String = "n=" + this
            """
        )
        val models = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            data class Src(val count: Int)
            data class Dst(val count: String)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(converters, models)
        val text = generated.first { it.name.contains("Src") }.readText()

        assertThat(text).contains("count = this.count.toLabel()")
        assertThat(text).contains("import converters.toLabel")
    }

    @Test
    fun `global converter applies through @MapField rename`() {
        val converters = SourceFile.kotlin(
            "Converters.kt",
            """
            package converters

            @com.blu3berry.kraft.config.KraftConverter
            fun Int.toText(): String = toString()
            """
        )
        val models = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            data class Src(val count: Int)

            @com.blu3berry.kraft.mapping.MapFrom(Src::class)
            data class Dst(
                @com.blu3berry.kraft.mapping.MapField(counterPartName = "count")
                val label: String
            )
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(converters, models)
        val text = generated.first { it.name.contains("Src") }.readText()

        assertThat(text).contains("label = this.count.toText()")
    }

    @Test
    fun `MapUsing wins over global converter for the same target`() {
        val converters = SourceFile.kotlin(
            "Converters.kt",
            """
            package converters

            @com.blu3berry.kraft.config.KraftConverter
            fun Int.toGlobal(): String = "global"
            """
        )
        val models = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            data class Src(val count: Int)
            data class Dst(val count: String)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper {
                @com.blu3berry.kraft.config.MapUsing(source = "count", target = "count")
                fun localConvert(v: Int): String = "local"
            }
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(converters, models)
        val text = generated.first { it.name.contains("Src") }.readText()

        assertThat(text).contains("count = SrcMapper.localConvert(this.count)")
        assertThat(text).doesNotContain("toGlobal")
    }

    @Test
    fun `ambiguous global converter pair reports KSP error`() {
        val converters = SourceFile.kotlin(
            "Converters.kt",
            """
            package converters

            @com.blu3berry.kraft.config.KraftConverter
            fun Int.toLabelA(): String = "a"

            @com.blu3berry.kraft.config.KraftConverter
            fun Int.toLabelB(): String = "b"
            """
        )

        val result = TestKspRunner.compile(converters)

        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("Ambiguous @KraftConverter")
    }

    @Test
    fun `missing converter still reports type-mismatch error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Src(val count: Int)
            data class Dst(val count: String)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `non-extension function with @KraftConverter reports error`() {
        val source = SourceFile.kotlin(
            "Converters.kt",
            """
            package converters

            @com.blu3berry.kraft.config.KraftConverter
            fun toLabel(value: Int): String = value.toString()
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("must be an extension function")
    }
}
