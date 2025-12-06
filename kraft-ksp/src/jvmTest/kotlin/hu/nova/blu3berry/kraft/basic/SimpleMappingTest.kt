package hu.nova.blu3berry.kraft.basic

import com.tschuchort.compiletesting.SourceFile
import com.google.common.truth.Truth.assertThat
import hu.nova.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class SimpleMappingTest {

    @Test
    fun `direct property copy`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class UserDto(val name: String, val age: Int)
            data class User(val name: String, val age: Int)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                from = UserDto::class,
                to = User::class
            )
            object UserMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val file = generated.first().readText()

        assertThat(file).contains("fun UserDto.toUser()")
        assertThat(file).contains("name = this.name")
        assertThat(file).contains("age = this.age")
    }
}
