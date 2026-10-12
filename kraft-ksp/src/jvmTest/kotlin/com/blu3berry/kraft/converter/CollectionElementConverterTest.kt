package com.blu3berry.kraft.converter

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Characterises which converters reach *element* position in a `List`/`Set` property.
 *
 * `GlobalConverterRule.findCollectionElementMatch` admits only
 * `ConverterEntry.Synthetic` (auto-derived `@MapEnum` mappers), because rendering the
 * element call assumes a `to<Target>` callable name that a hand-written converter need
 * not have. A real `@KraftConverter` is therefore found and declined, and the property
 * fails as a plain type mismatch.
 *
 * These tests pin that split so it is a documented boundary rather than a surprise —
 * see docs/ROADMAP.md item 2, which proposes lifting the restriction. When it is lifted,
 * the second test here is the one that must change.
 */
@OptIn(ExperimentalCompilerApi::class)
class CollectionElementConverterTest {

    @Test
    fun `a @MapEnum pair is applied element-wise inside a List with no extra annotation`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class StatusDto { OPEN, CLOSED }
            enum class Status { OPEN, CLOSED }

            @com.blu3berry.kraft.config.MapEnum(source = StatusDto::class, target = Status::class)
            object StatusMapping

            data class Src(val states: List<StatusDto>)
            data class Dst(val states: List<Status>)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val text = generated.first { it.name.contains("Src") }.readText()

        assertThat(text).contains("states = this.states.map { it.toStatus() }")
    }

    @Test
    fun `a hand-written @KraftConverter is NOT applied at element position`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            class Amount(val minor: Long)

            // Registered for the pair, and deliberately not named 'toLabel'-style
            // after the target — the naming assumption is exactly why element
            // position declines it.
            @com.blu3berry.kraft.config.KraftConverter
            fun Amount.intoLabel(): String = minor.toString()

            data class Src(val totals: List<Amount>)
            data class Dst(val totals: List<String>)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compile(source)

        // The scalar form of this same pair resolves; the collection form does not.
        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("Type mismatch for property 'totals'")
    }

    @Test
    fun `the same converter pair DOES resolve outside a collection`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            class Amount(val minor: Long)

            @com.blu3berry.kraft.config.KraftConverter
            fun Amount.intoLabel(): String = minor.toString()

            data class Src(val total: Amount)
            data class Dst(val total: String)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val text = generated.first { it.name.contains("Src") }.readText()

        assertThat(text).contains("total = this.total.intoLabel()")
    }
}
