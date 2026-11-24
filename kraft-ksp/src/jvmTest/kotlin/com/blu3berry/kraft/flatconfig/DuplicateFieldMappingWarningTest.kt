package com.blu3berry.kraft.flatconfig

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Roadmap #43: two `@FieldMapping` rules that claim one target in one direction resolve
 * last-wins. That stays the behaviour, but it is now reported as KRAFT-W011 (a warning, so
 * existing code keeps compiling). With `@MapReverse`, two renames from one source claim that
 * source as the reverse mapper's target, which is the same conflict seen from the other side.
 * The same rule given in both forms stays KRAFT-E066.
 */
@OptIn(ExperimentalCompilerApi::class)
class DuplicateFieldMappingWarningTest {

    private fun compile(body: String) = TestKspRunner.compile(
        SourceFile.kotlin(
            "Models.kt",
            """
            package demo

            import com.blu3berry.kraft.config.FieldMapping
            import com.blu3berry.kraft.config.IgnoreSide
            import com.blu3berry.kraft.config.MapConfig
            import com.blu3berry.kraft.config.MapIgnoreField
            import com.blu3berry.kraft.config.MapReverse

            data class UserDto(val first: String, val second: String, val extra: String = "")
            data class User(val name: String, val extra: String = "")
            $body
            """
        )
    )

    private fun com.tschuchort.compiletesting.JvmCompilationResult.forwardMapper(): String =
        sourcesGeneratedBySymbolProcessor.single { it.name.startsWith("UserDtoToUserMapper") }.readText()

    private fun String.occurrences(needle: String) = windowed(needle.length).count { it == needle }

    @Test
    fun `two renames to one target in the array form warn KRAFT-W011`() {
        val result = compile(
            """
            @MapConfig(
                source = UserDto::class, target = User::class,
                fieldMappings = [
                    FieldMapping(source = "first", target = "name"),
                    FieldMapping(source = "second", target = "name"),
                ]
            )
            object UserMapper
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages.occurrences("[KRAFT-W011]")).isEqualTo(1)
        assertThat(result.messages).contains(
            "[KRAFT-W011] Target 'name' in 'User' has 2 @FieldMapping rules on 'UserMapper'"
        )
        assertThat(result.messages).contains("Only the last one is used: source = \"second\".")
        // Behaviour is unchanged: the last rule still wins.
        assertThat(result.forwardMapper()).contains("name = this.second")
    }

    @Test
    fun `two renames to one target in the flat form warn KRAFT-W011`() {
        val result = compile(
            """
            @MapConfig(source = UserDto::class, target = User::class)
            @FieldMapping(source = "first", target = "name")
            @FieldMapping(source = "second", target = "name")
            object UserMapper
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(
            "[KRAFT-W011] Target 'name' in 'User' has 2 @FieldMapping rules on 'UserMapper'"
        )
        assertThat(result.forwardMapper()).contains("name = this.second")
    }

    @Test
    fun `the same rename written twice warns KRAFT-W011 as a duplicate`() {
        val result = compile(
            """
            @MapConfig(source = UserDto::class, target = User::class)
            @FieldMapping(source = "first", target = "name")
            @FieldMapping(source = "first", target = "name")
            object UserMapper
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages.occurrences("[KRAFT-W011]")).isEqualTo(1)
        assertThat(result.messages).contains(
            "[KRAFT-W011] @FieldMapping(source = \"first\", target = \"name\") is declared 2 times on 'UserMapper'"
        )
        assertThat(result.messages).contains("Keep the last copy and delete the earlier ones")
    }

    @Test
    fun `the same rename written twice does not warn when a forward @MapUsing claims its target`() {
        val result = TestKspRunner.compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package demo

                import com.blu3berry.kraft.config.FieldMapping
                import com.blu3berry.kraft.config.MapConfig
                import com.blu3berry.kraft.config.MapUsing

                data class UserDto(val first: String)
                data class User(val name: String)

                @MapConfig(source = UserDto::class, target = User::class)
                @FieldMapping(source = "first", target = "name")
                @FieldMapping(source = "first", target = "name")
                object UserMapper {
                    @MapUsing(target = "name")
                    fun UserDto.upper(): String = first.uppercase()
                }
                """
            )
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("KRAFT-W011")
    }

    @Test
    fun `with @MapReverse, the same rename written twice still warns when only the forward side is claimed`() {
        val result = TestKspRunner.compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package demo

                import com.blu3berry.kraft.config.FieldMapping
                import com.blu3berry.kraft.config.MapConfig
                import com.blu3berry.kraft.config.MapReverse
                import com.blu3berry.kraft.config.MapUsing

                data class UserDto(val first: String)
                data class User(val name: String)

                @MapConfig(source = UserDto::class, target = User::class)
                @MapReverse
                @FieldMapping(source = "first", target = "name")
                @FieldMapping(source = "first", target = "name")
                object UserMapper {
                    @MapUsing(target = "name")
                    fun UserDto.upper(): String = first.uppercase()
                }
                """
            )
        )

        // A forward-only converter under @MapReverse is also KRAFT-E023, so the build fails;
        // this only checks that the duplicate is still reported for the unclaimed reverse side.
        assertThat(result.messages).contains("is declared 2 times on 'UserMapper'")
    }

    @Test
    fun `with @MapReverse, two renames from one source warn KRAFT-W011 for the reverse mapper`() {
        val result = TestKspRunner.compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package demo

                import com.blu3berry.kraft.config.FieldMapping
                import com.blu3berry.kraft.config.MapConfig
                import com.blu3berry.kraft.config.MapReverse

                data class UserDto(val first: String)
                data class User(val name: String, val alias: String)

                @MapConfig(source = UserDto::class, target = User::class)
                @MapReverse
                @FieldMapping(source = "first", target = "name")
                @FieldMapping(source = "first", target = "alias")
                object UserMapper
                """
            )
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages.occurrences("[KRAFT-W011]")).isEqualTo(1)
        assertThat(result.messages).contains(
            "[KRAFT-W011] In the reverse mapper, target 'first' in 'UserDto' has 2 @FieldMapping rules on 'UserMapper'"
        )
        assertThat(result.messages).contains("Only the last one is used: target = \"alias\".")
    }

    @Test
    fun `a reverse @MapUsing on the shared source resolves the conflict and does not warn`() {
        val result = TestKspRunner.compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package demo

                import com.blu3berry.kraft.config.ConverterDirection
                import com.blu3berry.kraft.config.FieldMapping
                import com.blu3berry.kraft.config.MapConfig
                import com.blu3berry.kraft.config.MapReverse
                import com.blu3berry.kraft.config.MapUsing

                data class UserDto(val first: String)
                data class User(val name: String, val alias: String)

                @MapConfig(source = UserDto::class, target = User::class)
                @MapReverse
                @FieldMapping(source = "first", target = "name")
                @FieldMapping(source = "first", target = "alias")
                object UserMapper {
                    @MapUsing(target = "first", direction = ConverterDirection.REVERSE)
                    fun User.pickFirst(): String = name
                }
                """
            )
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("KRAFT-W011")
        // The fix the reverse-mapper warning recommends really is what the reverse mapper uses.
        val reverseMapper = result.sourcesGeneratedBySymbolProcessor
            .single { it.name.startsWith("UserToUserDtoMapper") }.readText()
        assertThat(reverseMapper).contains("pickFirst()")
    }

    @Test
    fun `a forward @MapUsing on the target means no rename is used and does not warn`() {
        val result = TestKspRunner.compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package demo

                import com.blu3berry.kraft.config.FieldMapping
                import com.blu3berry.kraft.config.MapConfig
                import com.blu3berry.kraft.config.MapUsing

                data class UserDto(val first: String, val second: String)
                data class User(val name: String)

                @MapConfig(source = UserDto::class, target = User::class)
                @FieldMapping(source = "first", target = "name")
                @FieldMapping(source = "second", target = "name")
                object UserMapper {
                    @MapUsing(target = "name")
                    fun UserDto.both(): String = first + second
                }
                """
            )
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("KRAFT-W011")
    }

    @Test
    fun `two renames from one source without @MapReverse do not warn`() {
        val result = TestKspRunner.compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package demo

                import com.blu3berry.kraft.config.FieldMapping
                import com.blu3berry.kraft.config.MapConfig

                data class UserDto(val first: String)
                data class User(val name: String, val alias: String)

                @MapConfig(source = UserDto::class, target = User::class)
                @FieldMapping(source = "first", target = "name")
                @FieldMapping(source = "first", target = "alias")
                object UserMapper
                """
            )
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("KRAFT-W011")
    }

    @Test
    fun `a target in both forms stays KRAFT-E066 without an extra KRAFT-W011`() {
        val result = compile(
            """
            @MapConfig(
                source = UserDto::class, target = User::class,
                fieldMappings = [FieldMapping(source = "first", target = "name")]
            )
            @FieldMapping(source = "second", target = "name")
            object UserMapper
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E066]")
        assertThat(result.messages).doesNotContain("KRAFT-W011")
    }

    @Test
    fun `duplicate @MapIgnoreField rules do not warn, since ignores combine`() {
        val result = compile(
            """
            @MapConfig(source = UserDto::class, target = User::class)
            @FieldMapping(source = "first", target = "name")
            @MapIgnoreField("extra", direction = IgnoreSide.TARGET)
            @MapIgnoreField("extra")
            object UserMapper
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("KRAFT-W011")
    }

    @Test
    fun `@Suppress on the mapper object silences KRAFT-W011`() {
        val result = compile(
            """
            @Suppress("KRAFT-W011")
            @MapConfig(source = UserDto::class, target = User::class)
            @FieldMapping(source = "first", target = "name")
            @FieldMapping(source = "second", target = "name")
            object UserMapper
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("KRAFT-W011")
    }
}
