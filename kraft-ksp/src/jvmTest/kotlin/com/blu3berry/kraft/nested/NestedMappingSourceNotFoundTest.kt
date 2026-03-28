package com.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class NestedMappingSourceNotFoundTest {

    @Test
    fun `@NestedMapping whose source type has no matching property in source class emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class AddressSource(val street: String, val city: String)
            data class PersonSource(val name: String)  // no address property

            data class AddressDto(val street: String, val city: String)
            data class PersonDto(val name: String, val address: AddressDto)

            @com.blu3berry.kraft.config.MapConfig(
                source = PersonSource::class,
                target   = PersonDto::class,
                nestedMappings = [
                    com.blu3berry.kraft.config.NestedMapping(
                        source = AddressSource::class,
                        target   = AddressDto::class
                    )
                ]
            )
            object PersonMapper
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("exists in source class")
    }
}
