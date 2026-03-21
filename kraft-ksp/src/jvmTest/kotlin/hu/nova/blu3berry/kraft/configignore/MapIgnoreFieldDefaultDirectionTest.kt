package hu.nova.blu3berry.kraft.configignore

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class MapIgnoreFieldDefaultDirectionTest {

    @Test
    fun `MapIgnoreField with default direction omits property from generated constructor call`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class UserSource(val id: Int, val name: String)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                source = UserSource::class,
                target   = UserDto::class,
                ignoredMappings = [
                    hu.nova.blu3berry.kraft.config.MapIgnoreField("internalNotes")
                ]
            )
            object UserMapper

            data class UserDto(val id: Int, val name: String, val internalNotes: String? = null)
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first().readText()

        assertThat(content).contains("fun UserSource.toUserDto()")
        assertThat(content).contains("id = this.id")
        assertThat(content).contains("name = this.name")
        assertThat(content).doesNotContain("internalNotes")
    }
}
