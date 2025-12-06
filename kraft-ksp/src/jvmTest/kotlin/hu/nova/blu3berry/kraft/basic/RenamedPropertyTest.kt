package hu.nova.blu3berry.kraft.basic

import com.tschuchort.compiletesting.SourceFile
import com.google.common.truth.Truth.assertThat
import hu.nova.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class RenamedPropertyTest {

    @Test
    fun `renamed property mapping works`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class PersonDto(val fullName: String)
            data class Person(val name: String)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                from = PersonDto::class,
                to = Person::class,
                fieldMappings = [
                    hu.nova.blu3berry.kraft.config.FieldOverride("fullName", "name")
                ]
            )
            object PersonMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val text = generated.first().readText()

        assertThat(text).contains("name = this.fullName")
    }
}
