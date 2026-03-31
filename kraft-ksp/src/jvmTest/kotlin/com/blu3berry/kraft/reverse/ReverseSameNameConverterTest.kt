package com.blu3berry.kraft.reverse

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class ReverseSameNameConverterTest {

    @Test
    fun `auto-detect direction when same property name has different types`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Entity(val id: Int, val name: String)
            data class Dto(val id: String, val name: String)

            @com.blu3berry.kraft.config.MapReverse
            @com.blu3berry.kraft.config.MapConfig(
                source = Entity::class,
                target = Dto::class
            )
            object EntityMapper {
                @com.blu3berry.kraft.config.MapUsing(source = "id", target = "id")
                fun intToString(v: Int): String = v.toString()

                @com.blu3berry.kraft.config.MapUsing(source = "id", target = "id")
                fun stringToInt(v: String): Int = v.toInt()
            }
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val allContent = generated.joinToString("\n") { it.readText() }

        // Forward: Entity → Dto — converter wired inside the constructor call
        assertThat(allContent).contains(
            "fun Entity.toDto(): Dto = Dto(\n" +
            "  id = EntityMapper.intToString(this.id),"
        )

        // Reverse: Dto → Entity — reverse converter wired inside the constructor call
        assertThat(allContent).contains(
            "fun Dto.toEntity(): Entity = Entity(\n" +
            "  id = EntityMapper.stringToInt(this.id),"
        )
    }

    @Test
    fun `explicit direction when same property name has different types`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Entity(val id: Int, val name: String)
            data class Dto(val id: String, val name: String)

            @com.blu3berry.kraft.config.MapReverse
            @com.blu3berry.kraft.config.MapConfig(
                source = Entity::class,
                target = Dto::class
            )
            object EntityMapper {
                @com.blu3berry.kraft.config.MapUsing(
                    source = "id", target = "id",
                    direction = com.blu3berry.kraft.config.ConverterDirection.FORWARD
                )
                fun intToString(v: Int): String = v.toString()

                @com.blu3berry.kraft.config.MapUsing(
                    source = "id", target = "id",
                    direction = com.blu3berry.kraft.config.ConverterDirection.REVERSE
                )
                fun stringToInt(v: String): Int = v.toInt()
            }
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val allContent = generated.joinToString("\n") { it.readText() }

        // Forward: Entity → Dto — converter wired inside the constructor call
        assertThat(allContent).contains(
            "fun Entity.toDto(): Dto = Dto(\n" +
            "  id = EntityMapper.intToString(this.id),"
        )

        // Reverse: Dto → Entity — reverse converter wired inside the constructor call
        assertThat(allContent).contains(
            "fun Dto.toEntity(): Entity = Entity(\n" +
            "  id = EntityMapper.stringToInt(this.id),"
        )
    }

    @Test
    fun `wrong explicit direction emits type mismatch error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Entity(val id: Int, val name: String)
            data class Dto(val id: String, val name: String)

            @com.blu3berry.kraft.config.MapReverse
            @com.blu3berry.kraft.config.MapConfig(
                source = Entity::class,
                target = Dto::class
            )
            object EntityMapper {
                @com.blu3berry.kraft.config.MapUsing(
                    source = "id", target = "id",
                    direction = com.blu3berry.kraft.config.ConverterDirection.REVERSE
                )
                fun intToString(v: Int): String = v.toString()

                @com.blu3berry.kraft.config.MapUsing(
                    source = "id", target = "id",
                    direction = com.blu3berry.kraft.config.ConverterDirection.FORWARD
                )
                fun stringToInt(v: String): Int = v.toInt()
            }
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("Type mismatch")
        assertThat(result.messages).contains("intToString")
    }
}
