package com.blu3berry.kraft.reverse

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Pins the validation behavior introduced when [ReverseDescriptorBuilder]'s
 * reverse-converter check began skipping converters resolved via the global
 * registry (those have `enclosingObject == null`).
 *
 * A user who writes a forward `@KraftConverter` extension and a parent
 * `@MapConfig + @MapReverse` but NO reverse extension still sees a
 * compile-time error — but the diagnostic now originates from the
 * resolver chain (RequiredFieldErrorRule / DirectMatchRule) rather than
 * `ReverseDescriptorBuilder`. The exact message wording is intentionally
 * not pinned because it lives downstream of the changed validation.
 *
 * Fixture uses `Uuid → String` so `NestedRule` does not auto-map the
 * property — this forces `GlobalConverterRule` to claim the forward
 * direction (which is the only path that exercises the changed code).
 */
@OptIn(ExperimentalCompilerApi::class)
class ReverseGlobalConverterMissingReverseTest {

    @Test
    fun `forward @KraftConverter without a reverse extension still fails @MapReverse compilation`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            import kotlin.uuid.ExperimentalUuidApi
            import kotlin.uuid.Uuid

            @com.blu3berry.kraft.config.KraftConverter
            @OptIn(ExperimentalUuidApi::class)
            fun Uuid.toStr(): String = this.toString()

            data class Src @OptIn(ExperimentalUuidApi::class) constructor(val id: Uuid)
            data class Dst(val id: String)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            @com.blu3berry.kraft.config.MapReverse
            object SrcMapper
            """
        )

        val result = TestKspRunner.compile(source)

        // Reverse direction (Dst -> Src) needs to map `String -> Uuid` via
        // some registered converter; the user only wrote the forward one.
        // Compilation must fail. The exact wording is not pinned because
        // the diagnostic moved downstream of ReverseDescriptorBuilder
        // after the global-registry guard was added.
        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("id")
    }
}
