package hu.nova.blu3berry.kraft.propertyresolver.rules

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class ConfigOverrideRuleTest {

    @Test
    fun `match - FieldMapping in @MapConfig renames source property in generated code`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class PersonSource(val fullName: String, val email: String)
            data class PersonDto(val name: String, val email: String)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                source = PersonSource::class,
                target = PersonDto::class,
                fieldMappings = [
                    hu.nova.blu3berry.kraft.config.FieldMapping(source = "fullName", target = "name")
                ]
            )
            object PersonMapper
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first().readText()

        assertThat(content).contains("name = this.fullName")
        assertThat(content).contains("email = this.email")
    }

    @Test
    fun `error - FieldMapping referencing unknown source property emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class PersonSource(val name: String)
            data class PersonDto(val name: String, val alias: String)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                source = PersonSource::class,
                target = PersonDto::class,
                fieldMappings = [
                    hu.nova.blu3berry.kraft.config.FieldMapping(source = "ghost", target = "alias")
                ]
            )
            object PersonMapper
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("ghost")
    }

    @Test
    fun `error - FieldMapping with type mismatch between source and target emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class OrderSource(val total: String)
            data class OrderDto(val total: Int)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                source = OrderSource::class,
                target = OrderDto::class,
                fieldMappings = [
                    hu.nova.blu3berry.kraft.config.FieldMapping(source = "total", target = "total")
                ]
            )
            object OrderMapper
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("mismatch")
    }
}
