package hu.nova.blu3berry.kraft.mapignore

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class MapIgnoreNullablePropertyTest {

    @Test
    fun `@MapIgnore on nullable property omits it from generated constructor call`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class UserSource(val name: String, val email: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(UserSource::class)
            data class UserDto(
                val name: String,
                @hu.nova.blu3berry.kraft.mapping.MapIgnore
                val email: String? = null
            )
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first().readText()

        assertThat(content).contains("fun UserSource.toUserDto()")
        assertThat(content).contains("name = this.name")
        assertThat(content).doesNotContain("email")
    }
}
