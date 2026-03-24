package hu.nova.blu3berry.kraft.propertyresolver.rules

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class ClassOverrideRuleTest {

    @Test
    fun `match - @MapField renames source property in generated code`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class UserSource(val userId: Int, val fullName: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(UserSource::class)
            data class UserDto(
                @hu.nova.blu3berry.kraft.mapping.MapField(counterPartName = "userId")
                val id: Int,
                val fullName: String
            )
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first().readText()

        assertThat(content).contains("id = this.userId")
        assertThat(content).contains("fullName = this.fullName")
    }

    @Test
    fun `no-match - property without @MapField uses direct matching`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class OrderSource(val ref: String, val total: Double)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(OrderSource::class)
            data class OrderDto(val ref: String, val total: Double)
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first().readText()

        assertThat(content).contains("ref = this.ref")
        assertThat(content).contains("total = this.total")
    }

    @Test
    fun `error - @MapField referencing unknown source property emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class UserSource(val name: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(UserSource::class)
            data class UserDto(
                val name: String,
                @hu.nova.blu3berry.kraft.mapping.MapField(counterPartName = "nonexistent")
                val id: Int
            )
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("nonexistent")
    }

    @Test
    fun `error - @MapField with type mismatch between source and target emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class UserSource(val age: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(UserSource::class)
            data class UserDto(
                @hu.nova.blu3berry.kraft.mapping.MapField(counterPartName = "age")
                val age: Int
            )
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("mismatch")
    }
}
