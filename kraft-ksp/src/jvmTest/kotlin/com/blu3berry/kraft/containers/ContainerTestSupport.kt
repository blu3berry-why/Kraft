package com.blu3berry.kraft.containers

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Shared fixtures for the container compile-tests: the spec's `ItemSource` / `ItemDto` pair,
 * a `Src` / `Tgt` pair with one property, and `@MapConfig(source = Src::class, target = Tgt::class) object M`.
 */
@OptIn(ExperimentalCompilerApi::class)
internal object ContainerTestSupport {

    private const val ITEMS = """
        data class ItemSource(val id: Int)
        data class ItemDto(val id: Int)
    """

    /** Models for one property [name] of type [sourceType] -> [targetType], plus [extraModels] and [configAnnotations]. */
    fun models(
        sourceType: String,
        targetType: String,
        name: String = "items",
        extraModels: String = ITEMS,
        configAnnotations: String = ""
    ): String = """
        package models

        $extraModels

        data class Src(val $name: $sourceType)
        data class Tgt(val $name: $targetType)

        @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Tgt::class)
        $configAnnotations
        object M
    """.trimIndent()

    fun compile(models: String): JvmCompilationResult =
        TestKspRunner.compile(SourceFile.kotlin("Models.kt", models))

    /** Compiles [models], which must succeed, and returns all generated source text. */
    fun generatedText(models: String): String =
        TestKspRunner.compileAndReturnGenerated(SourceFile.kotlin("Models.kt", models))
            .joinToString("\n") { it.readText() }
}
