package com.blu3berry.kraft.valueclass

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Value classes are auto-wrapped / unwrapped against their underlying type:
 * `UserId(val value: String)` ↔ `String`, including nullable properties,
 * `List` / `Set` elements, reverse mappers and classpath-compiled value classes.
 */
@OptIn(ExperimentalCompilerApi::class)
class ValueClassMappingTest {

    private fun models(body: String) = SourceFile.kotlin(
        "Models.kt",
        """
        package models

        @JvmInline
        value class UserId(val value: String)

        $body
        """
    )

    private fun generatedText(vararg sources: SourceFile, fileHint: String = "Src"): String =
        TestKspRunner.compileAndReturnGenerated(*sources)
            .first { it.name.contains(fileHint) }
            .readText()

    @Test
    fun `value class is unwrapped to its underlying type`() {
        val text = generatedText(
            models(
                """
                data class Src(val id: UserId)
                data class Dst(val id: String)

                @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
                object SrcMapper
                """
            )
        )

        assertThat(text).contains("id = this.id.value")
    }

    @Test
    fun `underlying type is wrapped into the value class`() {
        val text = generatedText(
            models(
                """
                data class Src(val id: String)
                data class Dst(val id: UserId)

                @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
                object SrcMapper
                """
            )
        )

        assertThat(text).contains("id = UserId(this.id)")
    }

    @Test
    fun `nullable value class is unwrapped with a safe call`() {
        val text = generatedText(
            models(
                """
                data class Src(val id: UserId?)
                data class Dst(val id: String?)

                @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
                object SrcMapper
                """
            )
        )

        assertThat(text).contains("id = this.id?.value")
    }

    @Test
    fun `nullable underlying value is wrapped with a safe call`() {
        val text = generatedText(
            models(
                """
                data class Src(val id: String?)
                data class Dst(val id: UserId?)

                @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
                object SrcMapper
                """
            )
        )

        assertThat(text).contains("id = this.id?.let { UserId(it) }")
    }

    @Test
    fun `nullable source into non-null target still errors`() {
        val result = TestKspRunner.compile(
            models(
                """
                data class Src(val id: UserId?)
                data class Dst(val id: String)

                @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
                object SrcMapper
                """
            )
        )

        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("[KRAFT-E005]")
        assertThat(result.messages).contains("Type mismatch for property 'id'")
    }

    @Test
    fun `renamed property is unwrapped`() {
        val text = generatedText(
            models(
                """
                data class Src(val userId: UserId)
                data class Dst(val id: String)

                @com.blu3berry.kraft.config.MapConfig(
                    source = Src::class,
                    target = Dst::class,
                    fieldMappings = [com.blu3berry.kraft.config.FieldMapping(source = "userId", target = "id")]
                )
                object SrcMapper
                """
            )
        )

        assertThat(text).contains("id = this.userId.value")
    }

    @Test
    fun `list and set elements are unwrapped and wrapped`() {
        val text = generatedText(
            models(
                """
                data class Src(val ids: List<UserId>, val tags: Set<String>?)
                data class Dst(val ids: List<String>, val tags: Set<UserId>?)

                @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
                object SrcMapper
                """
            )
        )

        assertThat(text).contains("ids = this.ids.map { it.value }")
        assertThat(text).contains("tags = this.tags?.map { UserId(it) }?.toSet()")
    }

    @Test
    fun `reverse mapper wraps what the forward mapper unwraps`() {
        val generated = TestKspRunner.compileAndReturnGenerated(
            models(
                """
                data class Src(val id: UserId)
                data class Dst(val id: String)

                @com.blu3berry.kraft.config.MapReverse
                @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
                object SrcMapper
                """
            )
        )
        val text = generated.joinToString("\n") { it.readText() }

        assertThat(text).contains("id = this.id.value")
        assertThat(text).contains("id = UserId(this.id)")
    }

    @Test
    fun `explicit KraftConverter wins over automatic unwrapping`() {
        val text = generatedText(
            models(
                """
                @com.blu3berry.kraft.config.KraftConverter
                fun UserId.toDisplay(): String = "user-" + value

                data class Src(val id: UserId)
                data class Dst(val id: String)

                @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
                object SrcMapper
                """
            )
        )

        assertThat(text).contains("id = this.id.toDisplay()")
    }

    @Test
    fun `value class with a private property is not unwrapped`() {
        val result = TestKspRunner.compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package models

                @JvmInline
                value class Secret(private val raw: String)

                data class Src(val secret: Secret)
                data class Dst(val secret: String)

                @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
                object SrcMapper
                """
            )
        )

        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("[KRAFT-E005]")
        assertThat(result.messages).contains("Type mismatch for property 'secret'")
    }

    @Test
    fun `value class compiled in an upstream module is unwrapped`() {
        val upstream = SourceFile.kotlin(
            "Ids.kt",
            """
            package shared

            @JvmInline
            value class OrderId(val raw: Long)
            """
        )
        val consumer = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            data class Src(val id: shared.OrderId)
            data class Dst(val id: Long)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compileWithUpstream(listOf(upstream), listOf(consumer))

        assertThat(result.consumer.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val text = result.consumerGeneratedFiles.first { it.name.contains("Src") }.readText()
        assertThat(text).contains("id = this.id.raw")
    }
}
