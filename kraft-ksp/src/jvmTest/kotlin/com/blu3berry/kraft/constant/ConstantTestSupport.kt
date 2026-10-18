package com.blu3berry.kraft.constant

import com.blu3berry.kraft.TestKspRunner
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File

/** Compiles [body] in package `app.mapping`, with the Kraft config annotations imported. */
@OptIn(ExperimentalCompilerApi::class)
internal fun compileInAppMapping(body: String): JvmCompilationResult = TestKspRunner.compile(
    SourceFile.kotlin(
        "Models.kt",
        """
        package app.mapping

        import com.blu3berry.kraft.config.ConverterDirection
        import com.blu3berry.kraft.config.FieldMapping
        import com.blu3berry.kraft.config.MapConfig
        import com.blu3berry.kraft.config.MapConstant
        import com.blu3berry.kraft.config.MapIgnoreField
        import com.blu3berry.kraft.config.MapReverse
        import com.blu3berry.kraft.config.MapUsing
        $body
        """
    )
)

/** The generated file that declares [signature], for example `fun UserDto.toUser()`. */
@OptIn(ExperimentalCompilerApi::class)
internal fun JvmCompilationResult.generatedFileDeclaring(signature: String): File =
    sourcesGeneratedBySymbolProcessor.single { it.extension == "kt" && it.readText().contains(signature) }

/** The generated text that declares [signature]. */
@OptIn(ExperimentalCompilerApi::class)
internal fun JvmCompilationResult.mapperDeclaring(signature: String): String =
    generatedFileDeclaring(signature).readText()

/** The one message line that carries [code], for example `[KRAFT-E067]`. */
@OptIn(ExperimentalCompilerApi::class)
internal fun JvmCompilationResult.singleLineWith(code: String): String =
    messages.lines().single { it.contains(code) }

/** How many diagnostics with [codePrefix] (for example `[KRAFT-E`) the compilation reported. */
@OptIn(ExperimentalCompilerApi::class)
internal fun JvmCompilationResult.countOf(codePrefix: String): Int =
    messages.windowed(codePrefix.length).count { it == codePrefix }
