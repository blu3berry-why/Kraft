package com.blu3berry.kraft.configignore

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * `@MapIgnoreField(direction = IgnoreSide.SOURCE)` applies only to the reverse mapper. On a
 * `@MapConfig` without `@MapReverse` it has no effect, which is reported as KRAFT-W010 (a
 * warning, not an error, so configs that already carry such a rule keep compiling).
 */
@OptIn(ExperimentalCompilerApi::class)
class MapIgnoreFieldSourceWithoutReverseTest {

    private fun compile(mapper: String) = TestKspRunner.compile(
        SourceFile.kotlin(
            "Models.kt",
            """
            package app.mapping

            import com.blu3berry.kraft.config.IgnoreSide
            import com.blu3berry.kraft.config.MapConfig
            import com.blu3berry.kraft.config.MapIgnoreField
            import com.blu3berry.kraft.config.MapReverse

            data class UserDto(val name: String, val secret: String = "")
            data class User(val name: String)

            $mapper
            """
        )
    )

    @Test
    fun `SOURCE ignore in the array form without @MapReverse warns KRAFT-W010`() {
        val result = compile(
            """
            @MapConfig(
                source = UserDto::class, target = User::class,
                ignoredMappings = [MapIgnoreField("secret", direction = IgnoreSide.SOURCE)]
            )
            object UserMapper
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("[KRAFT-W010]")
        assertThat(result.messages).contains("@MapIgnoreField(\"secret\", direction = SOURCE)")
        assertThat(result.messages).contains("UserMapper")
    }

    @Test
    fun `SOURCE ignore in the flat form without @MapReverse warns KRAFT-W010`() {
        val result = compile(
            """
            @MapConfig(source = UserDto::class, target = User::class)
            @MapIgnoreField("secret", direction = IgnoreSide.SOURCE)
            object UserMapper
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("[KRAFT-W010]")
        assertThat(result.messages).contains("@MapIgnoreField(\"secret\", direction = SOURCE)")
    }

    @Test
    fun `SOURCE ignore with @MapReverse does not warn`() {
        val result = compile(
            """
            @MapConfig(source = UserDto::class, target = User::class)
            @MapReverse
            @MapIgnoreField("secret", direction = IgnoreSide.SOURCE)
            object UserMapper
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("KRAFT-W010")
    }

    @Test
    fun `TARGET and BOTH ignores without @MapReverse do not warn`() {
        val result = compile(
            """
            data class Wide(val name: String, val note: String = "", val extra: String = "")

            @MapConfig(source = UserDto::class, target = Wide::class)
            @MapIgnoreField("note", direction = IgnoreSide.TARGET)
            @MapIgnoreField("extra")
            object WideMapper
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("KRAFT-W010")
    }

    @Test
    fun `@Suppress on the mapper object silences KRAFT-W010`() {
        val result = compile(
            """
            @Suppress("KRAFT-W010")
            @MapConfig(source = UserDto::class, target = User::class)
            @MapIgnoreField("secret", direction = IgnoreSide.SOURCE)
            object UserMapper
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("KRAFT-W010")
    }
}
