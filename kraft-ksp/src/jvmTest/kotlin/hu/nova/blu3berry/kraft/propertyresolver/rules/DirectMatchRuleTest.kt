package hu.nova.blu3berry.kraft.propertyresolver.rules

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class DirectMatchRuleTest {

    @Test
    fun `match - same name and type produces direct property copy`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class UserSource(val name: String, val age: Int)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(UserSource::class)
            data class UserDto(val name: String, val age: Int)
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first().readText()

        assertThat(content).contains("name = this.name")
        assertThat(content).contains("age = this.age")
    }

    @Test
    fun `no-match - differently named property is not mapped by DirectMatchRule`() {
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

        // DirectMatchRule cannot match 'id' (no source property named 'id');
        // ClassOverrideRule resolves it via @MapField instead.
        assertThat(content).contains("id = this.userId")
        assertThat(content).doesNotContain("id = this.id")
        assertThat(content).contains("fullName = this.fullName")
    }

    @Test
    fun `error - same property name but incompatible types emits type mismatch error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class UserSource(val age: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(UserSource::class)
            data class UserDto(val age: Int)
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("mismatch")
    }
}
