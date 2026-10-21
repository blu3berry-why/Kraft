package com.blu3berry.kraft.flatconfig

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

/**
 * Spec requirement "Flat @MapIgnoreField ignores a property".
 *
 * `@MapIgnoreField("x")` leaves `direction` at its default, so KSP omits the argument from
 * `KSAnnotation.arguments`; these tests exercise that path.
 */
class FlatMapIgnoreFieldTest {

    @Test
    fun `flat ignore with the default direction`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package demo

            import com.blu3berry.kraft.config.MapConfig
            import com.blu3berry.kraft.config.MapIgnoreField

            data class UserDto(val name: String)
            data class User(val name: String, val internalNotes: String = "")

            @MapConfig(source = UserDto::class, target = User::class)
            @MapIgnoreField("internalNotes")
            object UserMapper
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source).first().readText()

        assertThat(content).contains("name = this.name")
        assertThat(content).doesNotContain("internalNotes =")
    }

    @Test
    fun `flat ignore wins over a same-named source property`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package demo

            import com.blu3berry.kraft.config.MapConfig
            import com.blu3berry.kraft.config.MapIgnoreField

            data class UserDto(val name: String, val internalNotes: String)
            data class User(val name: String, val internalNotes: String = "")

            @MapConfig(source = UserDto::class, target = User::class)
            @MapIgnoreField("internalNotes")
            object UserMapper
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source).first().readText()

        assertThat(content).contains("name = this.name")
        assertThat(content).doesNotContain("internalNotes =")
    }

    @Test
    fun `flat ignore with an explicit direction`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package demo

            import com.blu3berry.kraft.config.IgnoreSide
            import com.blu3berry.kraft.config.MapConfig
            import com.blu3berry.kraft.config.MapIgnoreField
            import com.blu3berry.kraft.config.MapReverse

            data class UserDto(val name: String, val auditLog: String = "")
            data class User(val name: String, val auditLog: String = "")

            @MapConfig(source = UserDto::class, target = User::class)
            @MapReverse
            @MapIgnoreField("auditLog", direction = IgnoreSide.TARGET)
            object UserMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source).map { it.readText() }

        val forward = generated.single { "fun UserDto.toUser()" in it }
        val reverse = generated.single { "fun User.toUserDto()" in it }
        assertThat(forward).doesNotContain("auditLog =")
        assertThat(reverse).contains("auditLog = this.auditLog")
    }
}
