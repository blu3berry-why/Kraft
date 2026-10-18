package com.blu3berry.kraft.flatconfig

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Spec requirements "A target given in both forms is a compile error" (KRAFT-E066) and
 * "A flat rule requires @MapConfig on the same declaration" (KRAFT-E071).
 */
@OptIn(ExperimentalCompilerApi::class)
class FlatRuleErrorsTest {

    private fun compile(body: String) = TestKspRunner.compile(
        SourceFile.kotlin(
            "Models.kt",
            """
            package demo

            import com.blu3berry.kraft.config.FieldMapping
            import com.blu3berry.kraft.config.IgnoreSide
            import com.blu3berry.kraft.config.MapConfig
            import com.blu3berry.kraft.config.MapIgnoreField
            import com.blu3berry.kraft.mapping.MapFrom
            $body
            """
        )
    )

    private fun String.occurrences(needle: String) = windowed(needle.length).count { it == needle }

    @Test
    fun `same rename in both forms`() {
        val result = compile(
            """
            data class UserDto(val name: String)
            data class User(val fullName: String)

            @MapConfig(
                source = UserDto::class,
                target = User::class,
                fieldMappings = [FieldMapping(source = "name", target = "fullName")]
            )
            @FieldMapping(source = "name", target = "fullName")
            object UserMapper
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "[KRAFT-E066] @FieldMapping for target 'fullName' is declared both in " +
                "@MapConfig.fieldMappings and as an annotation on 'UserMapper'. Keep one."
        )
        assertThat(result.messages.occurrences("[KRAFT-E066]")).isEqualTo(1)
    }

    @Test
    fun `same ignore in both forms`() {
        val result = compile(
            """
            data class UserDto(val name: String)
            data class User(val name: String, val notes: String = "")

            @MapConfig(
                source = UserDto::class,
                target = User::class,
                ignoredMappings = [MapIgnoreField("notes")]
            )
            @MapIgnoreField("notes", direction = IgnoreSide.TARGET)
            object UserMapper
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "[KRAFT-E066] @MapIgnoreField for 'notes' is declared both in " +
                "@MapConfig.ignoredMappings and as an annotation on 'UserMapper'. Keep one."
        )
        assertThat(result.messages.occurrences("[KRAFT-E066]")).isEqualTo(1)
    }

    @Test
    fun `different targets in the two forms are not a conflict`() {
        val result = compile(
            """
            data class UserDto(val a: String, val b: String)
            data class User(val x: String, val y: String)

            @MapConfig(
                source = UserDto::class,
                target = User::class,
                fieldMappings = [FieldMapping(source = "a", target = "x")]
            )
            @FieldMapping(source = "b", target = "y")
            object UserMapper
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("KRAFT-E066")
    }

    @Test
    fun `flat rename on a MapFrom class`() {
        val result = compile(
            """
            data class UserDto(val name: String)

            @MapFrom(UserDto::class)
            @FieldMapping(source = "name", target = "fullName")
            data class User(val fullName: String)
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "[KRAFT-E071] @FieldMapping on 'User' requires @MapConfig on the same object. " +
                "Use @MapField on the property for @MapFrom/@MapTo classes."
        )
        assertThat(result.messages.occurrences("[KRAFT-E071]")).isEqualTo(1)
    }

    @Test
    fun `flat ignore on a MapFrom class`() {
        val result = compile(
            """
            data class UserDto(val name: String)

            @MapFrom(UserDto::class)
            @MapIgnoreField("notes")
            data class User(val name: String, val notes: String = "")
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "[KRAFT-E071] @MapIgnoreField on 'User' requires @MapConfig on the same object. " +
                "Use @MapIgnore on the property for @MapFrom/@MapTo classes."
        )
        assertThat(result.messages.occurrences("[KRAFT-E071]")).isEqualTo(1)
    }

    @Test
    fun `array-only rules on a MapConfig object do not trigger E071`() {
        val result = compile(
            """
            data class UserDto(val name: String)
            data class User(val fullName: String, val notes: String = "")

            @MapConfig(
                source = UserDto::class,
                target = User::class,
                fieldMappings = [FieldMapping(source = "name", target = "fullName")],
                ignoredMappings = [MapIgnoreField("notes")]
            )
            object UserMapper
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("KRAFT-E071")
    }
}
