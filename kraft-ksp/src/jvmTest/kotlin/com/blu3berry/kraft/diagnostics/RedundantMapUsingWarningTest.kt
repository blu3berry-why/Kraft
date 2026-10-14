package com.blu3berry.kraft.diagnostics

import com.blu3berry.kraft.TestKspRunner
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Covers `diagnostics/redundant-mapping-warnings` for `KRAFT-W001`: a property-source
 * `@MapUsing` that claims a property Kraft would map natively is reported with the
 * assignment Kraft would have generated, is suppressible per function, and never changes
 * the generated code.
 */
@OptIn(ExperimentalCompilerApi::class)
class RedundantMapUsingWarningTest {

    private companion object {
        const val W001 = "[KRAFT-W001]"
    }

    /** The list-helper scenario; [suppression] is an annotation line placed on `toRows`. */
    private fun listHelperSource(suppression: String = ""): SourceFile = SourceFile.kotlin(
        "Models.kt",
        """
        package models

        import com.blu3berry.kraft.config.MapConfig
        import com.blu3berry.kraft.config.MapUsing
        import models.generated.toRow

        data class RowDto(val id: Int)
        data class Row(val id: Int)

        data class ReportDto(val rows: List<RowDto>?)
        data class Report(val rows: List<Row>)

        @MapConfig(source = RowDto::class, target = Row::class)
        object RowMapper

        @MapConfig(source = ReportDto::class, target = Report::class)
        object ReportMapper {
            $suppression
            @MapUsing(source = "rows", target = "rows")
            fun List<RowDto>?.toRows(): List<Row> = this?.map { it.toRow() } ?: emptyList()
        }
        """
    )

    private fun compileOk(source: SourceFile): JvmCompilationResult {
        val result = TestKspRunner.compile(source)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        return result
    }

    private fun JvmCompilationResult.generatedTexts(): Map<String, String> =
        sourcesGeneratedBySymbolProcessor
            .filter { it.extension == "kt" }
            .associate { it.name to it.readText() }

    private fun JvmCompilationResult.w001Count(): Int = messages.windowed(W001.length).count { it == W001 }

    @Test
    fun `hand-written list helper duplicates element-wise mapping`() {
        val result = compileOk(listHelperSource())

        assertThat(result.generatedTexts().values.joinToString("\n"))
            .contains("rows = with(ReportMapper) { this@toReport.rows.toRows() }")
        assertThat(result.messages).contains(
            "[KRAFT-W001] @MapUsing 'toRows' on target property 'rows' duplicates a mapping Kraft already generates."
        )
        assertThat(result.messages).contains(
            "Kraft would generate:\n    rows = this.rows?.map { it.toRow() } ?: emptyList()\n"
        )
        assertThat(result.messages).contains(
            "Remove 'toRows', or add @Suppress(\"KRAFT-W001\") to it if its body intentionally differs " +
                "(filtering, sorting, defaults)."
        )
        assertThat(result.w001Count()).isEqualTo(1)
    }

    @Test
    fun `identity converter on a direct match`() {
        val result = compileOk(
            SourceFile.kotlin(
                "Models.kt",
                """
                data class User(val name: String)
                data class UserDto(val name: String)

                @com.blu3berry.kraft.config.MapConfig(source = User::class, target = UserDto::class)
                object UserMapper {
                    @com.blu3berry.kraft.config.MapUsing(source = "name", target = "name")
                    fun keep(name: String): String = name
                }
                """
            )
        )

        assertThat(result.messages).contains(
            "[KRAFT-W001] @MapUsing 'keep' on target property 'name' duplicates a mapping Kraft already generates."
        )
        assertThat(result.messages).contains("Kraft would generate:\n    name = this.name\n")
    }

    @Test
    fun `converter that bridges a real type gap is not reported`() {
        val result = compileOk(
            SourceFile.kotlin(
                "Models.kt",
                """
                data class Stamp(val epochDay: Long)
                data class Person(val birthDate: Stamp)
                data class PersonDto(val age: Int)

                @com.blu3berry.kraft.config.MapConfig(source = Person::class, target = PersonDto::class)
                object PersonMapper {
                    @com.blu3berry.kraft.config.MapUsing(source = "birthDate", target = "age")
                    fun ageOf(birthDate: Stamp): Int = (birthDate.epochDay / 365).toInt()
                }
                """
            )
        )

        assertThat(result.messages).doesNotContain(W001)
    }

    @Test
    fun `whole-source converter is never reported`() {
        val result = compileOk(
            SourceFile.kotlin(
                "Models.kt",
                """
                data class User(val firstName: String, val lastName: String, val fullName: String)
                data class UserDto(val fullName: String)

                @com.blu3berry.kraft.config.MapConfig(source = User::class, target = UserDto::class)
                object UserMapper {
                    @com.blu3berry.kraft.config.MapUsing(target = "fullName")
                    fun User.toFullName(): String = "${'$'}firstName ${'$'}lastName"
                }
                """
            )
        )

        assertThat(result.messages).doesNotContain(W001)
    }

    @Test
    fun `per-mapper override of a global converter is not reported`() {
        val result = compileOk(
            SourceFile.kotlin(
                "Models.kt",
                """
                package models

                @com.blu3berry.kraft.config.KraftConverter
                fun Int.toGlobal(): Long = this.toLong()

                data class Src(val count: Int)
                data class Dst(val count: Long)

                @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
                object SrcMapper {
                    @com.blu3berry.kraft.config.MapUsing(source = "count", target = "count")
                    fun special(c: Int): Long = c * 100L
                }
                """
            )
        )

        assertThat(result.generatedTexts().values.joinToString(" "))
            .contains("count = SrcMapper.special(this.count)")
        assertThat(result.messages).doesNotContain(W001)
    }

    @Test
    fun `global converter applied element-wise is still reported`() {
        val result = compileOk(
            SourceFile.kotlin(
                "Models.kt",
                """
                package models

                import com.blu3berry.kraft.config.KraftConverter
                import com.blu3berry.kraft.config.MapConfig
                import com.blu3berry.kraft.config.MapUsing

                data class RowDto(val id: Int)
                data class Row(val id: Int)

                @KraftConverter
                fun RowDto.toRow(): Row = Row(id)

                data class ReportDto(val rows: List<RowDto>?)
                data class Report(val rows: List<Row>)

                @MapConfig(source = ReportDto::class, target = Report::class)
                object ReportMapper {
                    @MapUsing(source = "rows", target = "rows")
                    fun List<RowDto>?.toRows(): List<Row> = this?.map { it.toRow() } ?: emptyList()
                }
                """
            )
        )

        assertThat(result.messages).contains(
            "[KRAFT-W001] @MapUsing 'toRows' on target property 'rows' duplicates a mapping Kraft already generates."
        )
        assertThat(result.messages).contains(
            "Kraft would generate:\n    rows = this.rows?.map { it.toRow() } ?: emptyList()\n"
        )
    }

    @Test
    fun `Suppress KRAFT-W001 silences the warning`() {
        val result = compileOk(listHelperSource("@Suppress(\"KRAFT-W001\")"))

        assertThat(result.messages).doesNotContain(W001)
    }

    @Test
    fun `Suppress with another code does not silence it`() {
        val result = compileOk(listHelperSource("@Suppress(\"KRAFT-W002\")"))

        assertThat(result.messages).contains(
            "[KRAFT-W001] @MapUsing 'toRows' on target property 'rows' duplicates a mapping Kraft already generates."
        )
    }

    @Test
    fun `generated files are identical with and without suppression`() {
        val warned = compileOk(listHelperSource()).generatedTexts()
        val suppressed = compileOk(listHelperSource("@Suppress(\"KRAFT-W001\")")).generatedTexts()

        assertThat(warned).isNotEmpty()
        assertThat(suppressed).isEqualTo(warned)
    }
}
