package hu.nova.blu3berry.kraft.basic

import com.tschuchort.compiletesting.SourceFile
import com.google.common.truth.Truth.assertThat
import hu.nova.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class AutoMapperProcessorTest {

    @Test
    fun `test simple mapper generation`() {
        val source = SourceFile.kotlin(
            "TestModels.kt",
            """
            data class UserDto(val name: String, val age: Int)
            data class User(val name: String, val age: Int)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                from = UserDto::class,
                to = User::class,
            )
            object UserMapper
        """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)

        assertThat(generated).isNotEmpty()

        val text = generated.first().readText()

        // Extension mapper validation
        assertThat(text).contains("fun UserDto.toUser")
        assertThat(text).contains("name = this.name")
        assertThat(text).contains("age = this.age")
    }

}
