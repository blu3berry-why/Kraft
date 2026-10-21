package com.blu3berry.kraft.flatconfig

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

/** Spec requirement "Flat @FieldMapping renames a property". */
class FlatFieldMappingTest {

    private fun models(configAnnotations: String = "") = SourceFile.kotlin(
        "Models.kt",
        """
        package demo

        import com.blu3berry.kraft.config.FieldMapping
        import com.blu3berry.kraft.config.MapConfig
        import com.blu3berry.kraft.config.MapReverse

        data class UserDto(val name: String, val mail: String)
        data class User(val fullName: String, val email: String)

        @MapConfig(source = UserDto::class, target = User::class)
        @FieldMapping(source = "name", target = "fullName")
        @FieldMapping(source = "mail", target = "email")
        $configAnnotations
        object UserMapper
        """
    )

    @Test
    fun `two flat renames`() {
        val content = TestKspRunner.compileAndReturnGenerated(models()).first().readText()

        assertThat(content).contains("fun UserDto.toUser()")
        assertThat(content).contains("fullName = this.name")
        assertThat(content).contains("email = this.mail")
    }

    @Test
    fun `flat rename on a reverse mapper`() {
        val generated = TestKspRunner.compileAndReturnGenerated(models("@MapReverse")).map { it.readText() }

        val forward = generated.single { "fun UserDto.toUser()" in it }
        val reverse = generated.single { "fun User.toUserDto()" in it }
        assertThat(forward).contains("fullName = this.name")
        assertThat(forward).contains("email = this.mail")
        assertThat(reverse).contains("name = this.fullName")
        assertThat(reverse).contains("mail = this.email")
    }
}
