package hu.nova.blu3berry.kraft.configignore

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class MapIgnoreFieldBothWithSameNameTest {

    @Test
    fun `MapIgnoreField with BOTH direction suppresses property when name exists in forward target`() {
        // Both source and target have 'notes'. BOTH is the correct annotation when the
        // same ignore should apply in each direction once reverse mapping is added.
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class User(val id: Int, val notes: String)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                source = User::class,
                target   = UserDto::class,
                ignoredMappings = [
                    hu.nova.blu3berry.kraft.config.MapIgnoreField(
                        "notes",
                        direction = hu.nova.blu3berry.kraft.config.IgnoreSide.BOTH
                    )
                ]
            )
            object UserMapper

            data class UserDto(val id: Int, val notes: String? = null)
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first().readText()

        assertThat(content).contains("fun User.toUserDto()")
        assertThat(content).contains("id = this.id")
        assertThat(content).doesNotContain("notes")
    }
}
