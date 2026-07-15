package com.blu3berry.kraft.mapenum

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import com.blu3berry.kraft.model.scan.ConverterTypeKey
import com.blu3berry.kraft.processor.util.DelegateNaming
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * `@MapEnum`-generated enum mappers auto-resolve as global converters when a
 * parent class has an enum-typed property whose source/target enum types
 * differ — no per-property `@MapNested` / `@MapUsing` boilerplate needed.
 */
@OptIn(ExperimentalCompilerApi::class)
class EnumAutoResolveTest {

    @Test
    fun `parent mapper auto-uses same-module @MapEnum mapper for differing enum property types`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE }

            @com.blu3berry.kraft.config.MapEnum(source = Status::class, target = StatusDto::class)
            object StatusMapping

            data class Src(val status: Status, val name: String)
            data class Dst(val status: StatusDto, val name: String)

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

        // The enum mapper extension is auto-invoked. Function name is
        // `toStatusDto` per the default to${target} template.
        assertThat(mapper).contains("status = this.status.toStatusDto()")
    }

    @Test
    fun `same pair declared as both @KraftConverter and @MapEnum is a compile-time ambiguity`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE }

            // Hand-written @KraftConverter for the same pair as the @MapEnum below.
            @com.blu3berry.kraft.config.KraftConverter
            fun Status.toStatusDto(): StatusDto = StatusDto.ACTIVE

            @com.blu3berry.kraft.config.MapEnum(source = Status::class, target = StatusDto::class)
            object StatusMapping
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("Ambiguous converter")
        assertThat(result.messages).contains("@MapEnum")
    }

    @Test
    fun `two @MapEnum declarations registering the same source-target pair is a compile-time error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE }

            @com.blu3berry.kraft.config.MapEnum(source = Status::class, target = StatusDto::class)
            object FirstMapping

            @com.blu3berry.kraft.config.MapEnum(source = Status::class, target = StatusDto::class)
            object SecondMapping
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("Ambiguous @MapEnum")
        assertThat(result.messages).contains("models.Status")
        assertThat(result.messages).contains("models.StatusDto")
    }

    @Test
    fun `consumer module picks up upstream @MapEnum via classpath delegate`() {
        val upstream = SourceFile.kotlin(
            "EnumMapping.kt",
            """
            package upstream

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE }

            @com.blu3berry.kraft.config.MapEnum(source = Status::class, target = StatusDto::class)
            object StatusMapping
            """
        )
        val consumer = SourceFile.kotlin(
            "Models.kt",
            """
            package consumer

            data class Src(val status: upstream.Status)
            data class Dst(val status: upstream.StatusDto)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compileWithUpstream(
            upstreamSources = listOf(upstream),
            consumerSources = listOf(consumer),
            upstreamKspOptions = mapOf("kraft.moduleId" to "upstream"),
            consumerKspOptions = mapOf("kraft.moduleId" to "consumer")
        )

        require(result.consumer.exitCode == KotlinCompilation.ExitCode.OK) {
            "Consumer failed:\n${result.consumer.messages}"
        }

        val mapper = result.consumerGeneratedFiles
            .single { "ToDstMapper" in it.name }
            .readText()
        // The consumer's parent mapper resolves Status → StatusDto via the
        // upstream-published @KraftConverterDelegate trampoline. The exact
        // delegate FQN proves we're going through the registry, not a stray
        // same-name fallback.
        val delegateName = DelegateNaming.delegateNameFor(
            ConverterTypeKey(
                sourceFqName = "upstream.Status",
                sourceNullable = false,
                targetFqName = "upstream.StatusDto",
                targetNullable = false
            )
        )
        assertThat(mapper).contains("status = this.status.$delegateName()")
        assertThat(mapper).contains("import kraft.generated.registry.$delegateName")
    }
}
