package com.blu3berry.kraft.diagnostics

import com.blu3berry.kraft.TestKspRunner
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Covers `diagnostics/redundant-mapping-warnings` for `KRAFT-W002`: a same-module
 * `S? -> T?` `@KraftConverter` whose `S -> T` twin is registered (here or upstream) is
 * reported, is suppressible, and never changes registration or generated code.
 */
@OptIn(ExperimentalCompilerApi::class)
class NullableTwinConverterWarningTest {

    private companion object {
        const val W002 = "[KRAFT-W002]"
    }

    private val models = """
        package models

        data class ReasonDto(val code: String)
        data class Reason(val code: String)
    """

    private fun converters(nonNull: Boolean, nullable: Boolean, suppression: String = ""): String = buildString {
        appendLine("package conv")
        appendLine("import models.*")
        if (nonNull) {
            appendLine("@com.blu3berry.kraft.config.KraftConverter")
            appendLine("fun ReasonDto.toReason(): Reason = Reason(code)")
        }
        if (nullable) {
            appendLine(suppression)
            appendLine("@com.blu3berry.kraft.config.KraftConverter")
            appendLine("fun ReasonDto?.toReasonNullable(): Reason? = this?.toReason()")
        }
    }

    private fun String.count(token: String): Int = windowed(token.length).count { it == token }

    @Test
    fun `nullable twin of a same-module converter is warned`() {
        val result = TestKspRunner.compile(
            SourceFile.kotlin("Models.kt", models),
            SourceFile.kotlin("Conv.kt", converters(nonNull = true, nullable = true))
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(
            "[KRAFT-W002] @KraftConverter 'toReasonNullable' (ReasonDto? → Reason?) only lifts " +
                "'toReason' (ReasonDto → Reason) through null."
        )
        assertThat(result.messages).contains(
            "Kraft already generates 'this.<property>?.toReason()' for nullable ReasonDto? → Reason? properties. " +
                "Remove 'toReasonNullable', or add @Suppress(\"KRAFT-W002\") if it does more than a safe call."
        )
        assertThat(result.messages.count(W002)).isEqualTo(1)
    }

    @Test
    fun `nullable-only converter is not warned`() {
        val result = TestKspRunner.compile(
            SourceFile.kotlin("Models.kt", models),
            SourceFile.kotlin(
                "Conv.kt",
                """
                package conv
                import models.*

                @com.blu3berry.kraft.config.KraftConverter
                fun ReasonDto?.toReason(): Reason = Reason(this?.code ?: "none")
                """
            )
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain(W002)
    }

    @Test
    fun `suppression on the nullable converter silences the warning`() {
        val result = TestKspRunner.compile(
            SourceFile.kotlin("Models.kt", models),
            SourceFile.kotlin(
                "Conv.kt",
                converters(nonNull = true, nullable = true, suppression = "@Suppress(\"KRAFT-W002\")")
            )
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain(W002)
    }

    @Test
    fun `non-null converter upstream with the nullable twin local is warned`() {
        val result = TestKspRunner.compileWithUpstream(
            upstreamSources = listOf(
                SourceFile.kotlin("Models.kt", models),
                SourceFile.kotlin("Conv.kt", converters(nonNull = true, nullable = false))
            ),
            consumerSources = listOf(
                SourceFile.kotlin(
                    "Local.kt",
                    """
                    package local
                    import models.*
                    import conv.toReason

                    @com.blu3berry.kraft.config.KraftConverter
                    fun ReasonDto?.toReasonNullable(): Reason? = this?.toReason()
                    """
                )
            )
        )

        assertThat(result.consumer.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.consumer.messages).contains(
            "(ReasonDto? → Reason?) only lifts"
        )
        assertThat(result.consumer.messages.count(W002)).isEqualTo(1)
    }

    @Test
    fun `nullable twin upstream with the non-null converter local is not warned`() {
        val result = TestKspRunner.compileWithUpstream(
            upstreamSources = listOf(
                SourceFile.kotlin("Models.kt", models),
                SourceFile.kotlin(
                    "Conv.kt",
                    """
                    package conv
                    import models.*

                    @com.blu3berry.kraft.config.KraftConverter
                    fun ReasonDto?.toReasonNullable(): Reason? = this?.let { Reason(it.code) }
                    """
                )
            ),
            consumerSources = listOf(
                SourceFile.kotlin(
                    "Local.kt",
                    """
                    package local
                    import models.*

                    @com.blu3berry.kraft.config.KraftConverter
                    fun ReasonDto.toReason(): Reason = Reason(code)
                    """
                )
            )
        )

        assertThat(result.consumer.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.consumer.messages).doesNotContain(W002)
    }

    @Test
    fun `suppression silences a nullable twin whose non-null converter is upstream`() {
        val result = TestKspRunner.compileWithUpstream(
            upstreamSources = listOf(
                SourceFile.kotlin("Models.kt", models),
                SourceFile.kotlin("Conv.kt", converters(nonNull = true, nullable = false))
            ),
            consumerSources = listOf(
                SourceFile.kotlin(
                    "Local.kt",
                    """
                    package local
                    import models.*
                    import conv.toReason

                    @Suppress("KRAFT-W002")
                    @com.blu3berry.kraft.config.KraftConverter
                    fun ReasonDto?.toReasonNullable(): Reason? = this?.toReason()
                    """
                )
            )
        )

        assertThat(result.consumer.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.consumer.messages).doesNotContain(W002)
    }
}
