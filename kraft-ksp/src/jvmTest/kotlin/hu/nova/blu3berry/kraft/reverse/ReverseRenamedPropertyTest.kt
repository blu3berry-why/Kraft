package hu.nova.blu3berry.kraft.reverse

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class ReverseRenamedPropertyTest {

    @Test
    fun `@MapField renames are correctly inverted in reverse`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class User(val userId: Int, val fullName: String)

            @hu.nova.blu3berry.kraft.config.MapReverse
            @hu.nova.blu3berry.kraft.mapping.MapFrom(User::class)
            data class UserDto(
                @hu.nova.blu3berry.kraft.mapping.MapField(counterPartName = "userId")
                val id: Int,
                @hu.nova.blu3berry.kraft.mapping.MapField(counterPartName = "fullName")
                val name: String
            )
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val files = generated.map { it.readText() }
        val allContent = files.joinToString("\n")

        // Forward: User.toUserDto() with id = this.userId, name = this.fullName
        assertThat(allContent).contains("fun User.toUserDto()")
        assertThat(allContent).contains("id = this.userId")
        assertThat(allContent).contains("name = this.fullName")

        // Reverse: UserDto.toUser() with userId = this.id, fullName = this.name
        assertThat(allContent).contains("fun UserDto.toUser()")
        assertThat(allContent).contains("userId = this.id")
        assertThat(allContent).contains("fullName = this.name")
    }

    @Test
    fun `config-level @FieldMapping renames are correctly inverted in reverse`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class User(val userId: Int, val fullName: String)
            data class UserDto(val id: Int, val name: String)

            @hu.nova.blu3berry.kraft.config.MapReverse
            @hu.nova.blu3berry.kraft.config.MapConfig(
                source = User::class,
                target = UserDto::class,
                fieldMappings = [
                    hu.nova.blu3berry.kraft.config.FieldMapping(source = "userId", target = "id"),
                    hu.nova.blu3berry.kraft.config.FieldMapping(source = "fullName", target = "name")
                ]
            )
            object UserMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val files = generated.map { it.readText() }
        val allContent = files.joinToString("\n")

        // Forward
        assertThat(allContent).contains("fun User.toUserDto()")
        assertThat(allContent).contains("id = this.userId")

        // Reverse
        assertThat(allContent).contains("fun UserDto.toUser()")
        assertThat(allContent).contains("userId = this.id")
    }
}
