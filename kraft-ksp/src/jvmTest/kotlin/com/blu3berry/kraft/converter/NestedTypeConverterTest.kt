package com.blu3berry.kraft.converter

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Regression: the converter lookup key was composed from
 * (packageName, leaf-simpleName), which silently aliased a nested type
 * with a same-leaf-named top-level type in the same package. A
 * `@KraftConverter` for one would resolve property mappings of the
 * other, or a property of a nested type would fail to find a converter
 * registered for that exact nested type because the key collided with a
 * distinct top-level one.
 */
@OptIn(ExperimentalCompilerApi::class)
class NestedTypeConverterTest {

    @Test
    fun `@KraftConverter registered for a nested receiver type resolves nested-typed property`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            class Outer {
                class Inner {
                    enum class Role { STAFF, MANAGER }
                }
            }

            @com.blu3berry.kraft.config.KraftConverter
            fun Outer.Inner.Role.toLabel(): String = name

            data class Src(val role: Outer.Inner.Role)
            data class Dst(val role: String)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }

        val mapper = TestKspRunner.compileAndReturnGenerated(source)
            .first { "ToDstMapper" in it.name }
            .readText()

        assertThat(mapper).contains("role = this.role.toLabel()")
    }

    @Test
    fun `top-level @KraftConverter does not silently match a same-leaf nested-type property`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            class Outer {
                class Inner {
                    enum class Role { STAFF, MANAGER }
                }
            }

            // Unrelated top-level enum sharing the simple name 'Role'.
            enum class Role { A, B }

            // Converter for the TOP-LEVEL Role only.
            @com.blu3berry.kraft.config.KraftConverter
            fun Role.toLabel(): String = name

            data class Src(val role: Outer.Inner.Role)
            data class Dst(val role: String)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        // The converter is registered for top-level Role, but Src.role is
        // typed as Outer.Inner.Role. With the bug, the qualified-name key
        // collision would let the wrong converter resolve the property. The
        // fix routes the lookup through the actual nested FQN, so no
        // converter matches and the resolver chain falls through to the
        // type-mismatch error (the property types String vs Outer.Inner.Role
        // don't directly match either).
        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("Required property 'role'")
    }
}
