package com.blu3berry.kraft.diagnostics

import com.blu3berry.kraft.TestKspRunner
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Covers the `diagnostics/type-mismatch-hints` requirements added by the kraft-report change:
 * KRAFT-E005 lists the converters Kraft knows whose source or target matches the failing
 * property's types, or says that there are none.
 */
@OptIn(ExperimentalCompilerApi::class)
class ConverterCandidatesInTypeMismatchTest {

    private fun compile(body: String) = TestKspRunner.compile(
        SourceFile.kotlin(
            "Models.kt",
            """
            package app

            import com.blu3berry.kraft.config.KraftConverter
            import com.blu3berry.kraft.mapping.MapFrom

            $body
            """
        )
    )

    @Test
    fun `lists a converter whose source matches but whose target does not`() {
        val result = compile(
            """
            data class Money(val cents: Long)

            @KraftConverter fun Money.toCents(): Long = cents

            data class A(val price: Money)

            @MapFrom(A::class)
            data class B(val price: String)
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E005]")
        assertThat(result.messages).contains("Registered converters touching these types:")
        assertThat(result.messages).contains("• app.Money → kotlin.Long (app.toCents, same-module)")
    }

    @Test
    fun `lists a converter for the element type of a list`() {
        val result = compile(
            """
            data class Money(val cents: Long)

            @KraftConverter fun Money.toCents(): Long = cents

            data class A(val prices: List<Money>)

            @MapFrom(A::class)
            data class B(val prices: List<String>)
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E005]")
        assertThat(result.messages).contains("• app.Money → kotlin.Long (app.toCents, same-module)")
    }

    @Test
    fun `does not list unrelated converters`() {
        val result = compile(
            """
            data class Money(val cents: Long)
            data class Tag(val value: String)

            @KraftConverter fun Tag.toLabel(): String = value

            data class A(val price: Money)

            @MapFrom(A::class)
            data class B(val price: Int)
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E005]")
        assertThat(result.messages).doesNotContain("app.toLabel")
    }

    @Test
    fun `names a classpath delegate with its origin`() {
        val result = TestKspRunner.compileWithUpstream(
            upstreamSources = listOf(
                SourceFile.kotlin(
                    "Money.kt",
                    """
                    package core

                    import com.blu3berry.kraft.config.KraftConverter

                    data class Money(val cents: Long)

                    @KraftConverter fun Money.toCents(): Long = cents
                    """
                )
            ),
            consumerSources = listOf(
                SourceFile.kotlin(
                    "Models.kt",
                    """
                    package app

                    import com.blu3berry.kraft.mapping.MapFrom
                    import core.Money

                    data class A(val price: Money, val cost: Money)

                    @MapFrom(A::class)
                    data class B(val price: Long, val cost: String)
                    """
                )
            ),
            upstreamKspOptions = mapOf("kraft.moduleId" to ":core")
        ).consumer

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E005]")
        assertThat(result.messages).containsMatch("• core\\.Money → kotlin\\.Long \\(\\S+, classpath-delegate\\)")
    }

    @Test
    fun `says so when no converter touches the types`() {
        val result = compile(
            """
            data class A(val id: Int)

            @MapFrom(A::class)
            data class B(val id: String)
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E005]")
        assertThat(result.messages).contains(
            "No registered converter has kotlin.Int as its source or kotlin.String as its target."
        )
        assertThat(result.messages).contains(
            "Set the KSP option kraft.report=true to list every converter Kraft registered."
        )
    }
}
