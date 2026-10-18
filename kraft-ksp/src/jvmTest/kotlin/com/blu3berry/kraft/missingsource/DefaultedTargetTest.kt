package com.blu3berry.kraft.missingsource

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Spec `mapping/missing-source-properties`, requirement "Unmapped defaulted targets use their default".
 * An unmapped target property with a default is left out of the constructor call, with no diagnostic.
 */
@OptIn(ExperimentalCompilerApi::class)
class DefaultedTargetTest {

    private fun models(dto: String) = SourceFile.kotlin(
        "Models.kt",
        """
        data class PersonSource(val name: String)

        $dto

        @com.blu3berry.kraft.config.MapConfig(source = PersonSource::class, target = PersonDto::class)
        object PersonMapper
        """
    )

    @Test
    fun `Non-null property with a default`() {
        val result = TestKspRunner.compile(models("data class PersonDto(val name: String, val score: Int = 0)"))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("KRAFT-")
        val mapper = result.generatedFile("PersonSourceToPersonDtoMapper.kt").readText()
        assertThat(mapper).contains(
            """
            public fun PersonSource.toPersonDto(): PersonDto = PersonDto(
              name = this.name
            )
            """.trimIndent()
        )
        assertThat(mapper).doesNotContain("score")
    }

    @Test
    fun `Nullable property with a null default`() {
        val result = TestKspRunner.compile(
            models("data class PersonDto(val name: String, val nickname: String? = null)")
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("KRAFT-")
        val mapper = result.generatedFile("PersonSourceToPersonDtoMapper.kt").readText()
        assertThat(mapper).contains("public fun PersonSource.toPersonDto(): PersonDto = PersonDto(")
        assertThat(mapper).doesNotContain("nickname")
    }

    @Test
    fun `Calling the generated mapper`() {
        val result = TestKspRunner.compile(models("data class PersonDto(val name: String, val score: Int = 0)"))
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

        val loader = result.classLoader
        val source = loader.loadClass("PersonSource").getConstructor(String::class.java).newInstance("Ada")
        val expected = loader.loadClass("PersonDto")
            .getConstructor(String::class.java, Int::class.javaPrimitiveType)
            .newInstance("Ada", 0)

        // The generated file's facade class: <package>.<FileName>Kt.
        val mapperFile = result.generatedFile("PersonSourceToPersonDtoMapper.kt")
        val pkg = Regex("""^package\s+(\S+)""", RegexOption.MULTILINE)
            .find(mapperFile.readText())?.groupValues?.get(1)
        val facadeName = listOfNotNull(pkg, mapperFile.nameWithoutExtension + "Kt").joinToString(".")
        val toPersonDto = loader.loadClass(facadeName).getMethod("toPersonDto", source.javaClass)

        assertThat(toPersonDto.invoke(null, source)).isEqualTo(expected)
    }

    private fun JvmCompilationResult.generatedFile(name: String) =
        sourcesGeneratedBySymbolProcessor.single { it.name == name }
}
