package hu.nova.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class CircularNestedMappingTest {

    /**
     * NodeSource.next → NodeDto.next creates a self-referential implicit dependency.
     * The DFS marks NodeSource→NodeDto GRAY while building it, then encounters its own
     * NestedMapper dependency back to NodeSource→NodeDto (GRAY) → cycle detected.
     *
     * A Container wrapper is used as the explicit root so NodeSource→NodeDto is
     * resolved implicitly, allowing the GRAY marker to be hit.
     */
    @Test
    fun `self-referential implicit nested mapping is detected as a cycle`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class NodeSource(val value: Int, val next: NodeSource?)
            data class NodeDto(val value: Int, val next: NodeDto?)

            data class Container(val node: NodeSource)

            @hu.nova.blu3berry.kraft.onclass.from.MapFrom(Container::class)
            data class ContainerDto(val node: NodeDto)
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("Circular nested mapping")
    }
}
