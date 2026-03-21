package hu.nova.blu3berry.kraft.configignore

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class IgnoreFieldWithFieldOverrideTest {

    @Test
    fun `IgnoreField and FieldOverride coexist correctly in the same MapConfig`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class User(val id: Int, val email: String, val notes: String)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                from = User::class,
                to   = UserDto::class,
                fieldMappings = [
                    hu.nova.blu3berry.kraft.config.FieldOverride(from = "email", to = "contactEmail")
                ],
                ignoredMappings = [
                    hu.nova.blu3berry.kraft.config.IgnoreField("notes")
                ]
            )
            object UserMapper

            data class UserDto(val id: Int, val contactEmail: String, val notes: String? = null)
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first().readText()

        assertThat(content).contains("fun User.toUserDto()")
        assertThat(content).contains("id = this.id")
        assertThat(content).contains("contactEmail = this.email")
        assertThat(content).doesNotContain("notes")
    }
}
