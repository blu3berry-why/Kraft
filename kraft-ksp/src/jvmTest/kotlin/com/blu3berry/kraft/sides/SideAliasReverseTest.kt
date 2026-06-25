package com.blu3berry.kraft.sides

import com.blu3berry.kraft.TestKspRunner
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class SideAliasReverseTest {

    @Test
    fun `forward and reverse aliases each match their own side`() {
        val sources = listOf(
            SourceFile.kotlin("Dto.kt", """
                package app.data.generated.models
                data class CategoryDto(val id: Int)
            """),
            SourceFile.kotlin("Domain.kt", """
                package app.domain.model
                data class Category(val id: Int)
            """),
            SourceFile.kotlin("Mapper.kt", """
                package app.mapper
                import app.data.generated.models.CategoryDto
                import app.domain.model.Category

                @com.blu3berry.kraft.config.MapReverse
                @com.blu3berry.kraft.config.MapConfig(
                    source = CategoryDto::class,
                    target = Category::class
                )
                object CategoryMapper
            """),
        )

        val generated = TestKspRunner.compileAndReturnGenerated(
            *sources.toTypedArray(),
            kspOptions = mapOf(
                "kraft.side.dto.name" to "Dto",
                "kraft.side.dto.packagePattern" to "**.data.generated.models.**",
                "kraft.side.domain.name" to "Domain",
                "kraft.side.domain.packagePattern" to "**.domain.model.**",
            )
        )

        val joined = generated.joinToString("\n") { it.readText() }
        assertThat(joined).contains("fun CategoryDto.toDomain(")  // forward
        assertThat(joined).contains("fun Category.toDto(")        // reverse
    }
}
