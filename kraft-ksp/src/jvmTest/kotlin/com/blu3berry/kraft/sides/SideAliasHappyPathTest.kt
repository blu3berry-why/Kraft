package com.blu3berry.kraft.sides

import com.blu3berry.kraft.TestKspRunner
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class SideAliasHappyPathTest {

    @Test
    fun `target in registered side gets short alias delegate`() {
        val sources = listOf(
            SourceFile.kotlin("Dto.kt", """
                package app.feature.data.generated.models
                data class CategoryDto(val id: Int, val label: String)
            """),
            SourceFile.kotlin("Domain.kt", """
                package app.feature.domain.model
                data class Category(val id: Int, val label: String)
            """),
            SourceFile.kotlin("Mapper.kt", """
                package app.feature.data.mapper

                import app.feature.data.generated.models.CategoryDto
                import app.feature.domain.model.Category

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
                "kraft.side.domain.name" to "Domain",
                "kraft.side.domain.packagePattern" to "**.domain.model.**",
            )
        )

        val joined = generated.joinToString("\n") { it.readText() }

        // Verbose function still emitted.
        assertThat(joined).contains("fun CategoryDto.toCategory(")
        // Short alias emitted.
        assertThat(joined).contains("fun CategoryDto.toDomain(")
        // Alias body delegates to verbose — KotlinPoet emits single-expression form
        // "fun CategoryDto.toDomain(): Category = toCategory()"
        assertThat(joined).containsMatch("fun CategoryDto\\.toDomain\\([^)]*\\)[^=]*=\\s*toCategory\\(")
    }
}
