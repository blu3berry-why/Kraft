package com.blu3berry.kraft.diagnostics

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * A source property whose type is a type parameter (`val value: T`) must be reported as
 * KRAFT-E011, not crash the processor with an internal exception.
 */
@OptIn(ExperimentalCompilerApi::class)
class UnsupportedSourcePropertyTypeTest {

    @Test
    fun `type-parameter source property on @MapTo reports KRAFT-E011`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            @com.blu3berry.kraft.mapping.MapTo(Target::class)
            data class Box<T>(val id: Int, val value: T)

            data class Target(val id: Int, val value: String)
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E011]")
        assertThat(result.messages).contains("Property 'value' on 'Box'")
        assertThat(result.messages).doesNotContain("Kraft cannot map 'T'")
    }

    @Test
    fun `type-parameter source property on @MapConfig reports KRAFT-E011`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Box<T>(val id: Int, val value: T)

            data class Target(val id: Int, val value: String)

            @com.blu3berry.kraft.config.MapConfig(source = Box::class, target = Target::class)
            object BoxMapper
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E011]")
        assertThat(result.messages).contains("Property 'value' on 'Box'")
        assertThat(result.messages).doesNotContain("Kraft cannot map 'T'")
    }

    @Test
    fun `parameterized type alias source property reports KRAFT-E011`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            typealias Bag<T> = List<T>

            @com.blu3berry.kraft.mapping.MapTo(Target::class)
            data class Source(val id: Int, val items: Bag<String>)

            data class Target(val id: Int, val items: List<String>)
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E011]")
        assertThat(result.messages).contains("Property 'items' on 'Source'")
        assertThat(result.messages).doesNotContain("parameterized type aliases")
    }
}
