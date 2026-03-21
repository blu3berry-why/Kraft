package hu.nova.blu3berry.kraft.configignore

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class IgnoreFieldForwardDirectionTest {

    @Test
    fun `IgnoreField with FORWARD direction omits property from generated constructor call`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class User(val id: Int, val name: String, val secret: String)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                from = User::class,
                to   = UserDto::class,
                ignoredMappings = [
                    hu.nova.blu3berry.kraft.config.IgnoreField(
                        "secret",
                        direction = hu.nova.blu3berry.kraft.config.IgnoreDirection.FORWARD
                    )
                ]
            )
            object UserMapper

            data class UserDto(val id: Int, val name: String, val secret: String? = null)
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first().readText()

        assertThat(content).contains("fun User.toUserDto()")
        assertThat(content).contains("id = this.id")
        assertThat(content).contains("name = this.name")
        assertThat(content).doesNotContain("secret")
    }
}
