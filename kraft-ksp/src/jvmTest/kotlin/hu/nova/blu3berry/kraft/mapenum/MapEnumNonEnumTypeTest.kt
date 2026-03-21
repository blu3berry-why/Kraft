package hu.nova.blu3berry.kraft.mapenum

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class MapEnumNonEnumTypeTest {

    @Test
    fun `@MapEnum emits a KSP error when source references a non-enum type`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class NotAnEnum(val value: Int)
            enum class TargetEnum { A, B }

            @hu.nova.blu3berry.kraft.config.MapEnum(
                source = NotAnEnum::class,
                target   = TargetEnum::class
            )
            object BadMapping
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("only mapping between enum classes")
    }

    @Test
    fun `@MapEnum emits a KSP error when target references a non-enum type`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            enum class SourceEnum { A, B }
            data class NotAnEnum(val value: Int)

            @hu.nova.blu3berry.kraft.config.MapEnum(
                source = SourceEnum::class,
                target   = NotAnEnum::class
            )
            object BadMapping
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("only mapping between enum classes")
    }
}
