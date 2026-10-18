package com.blu3berry.kraft.flatconfig

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

/**
 * Spec requirement "The array form keeps working" (change flat-mapconfig-annotations).
 *
 * The expected text in [array form is byte-identical] was captured from the scanner as it was
 * before the flat form existed (annotations already carrying `@Target`/`@Repeatable`).
 */
class ArrayFormCompatibilityTest {

    @Test
    fun `array form is byte-identical`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package demo

            import com.blu3berry.kraft.config.FieldMapping
            import com.blu3berry.kraft.config.MapConfig
            import com.blu3berry.kraft.config.MapIgnoreField
            import com.blu3berry.kraft.config.MapReverse

            data class Src(val a: String, val b: Int, val c: String)
            data class Dst(val x: String, val b: Int, val c: String, val notes: String = "")

            @MapConfig(
                source = Src::class,
                target = Dst::class,
                fieldMappings = [FieldMapping(source = "a", target = "x")],
                ignoredMappings = [MapIgnoreField("notes")]
            )
            @MapReverse
            object SrcToDst
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source).associate { it.name to it.readText() }

        assertThat(generated.keys).containsExactly("SrcToDstMapper.kt", "DstToSrcMapper.kt")
        assertThat(generated.getValue("SrcToDstMapper.kt").bodyFromPackage()).isEqualTo(
            "package demo.generated\n" +
                "\n" +
                "import demo.Dst\n" +
                "import demo.Src\n" +
                "\n" +
                "public fun Src.toDst(): Dst = Dst(\n" +
                "  x = this.a,\n" +
                "  b = this.b,\n" +
                "  c = this.c\n" +
                ")\n"
        )
        assertThat(generated.getValue("DstToSrcMapper.kt").bodyFromPackage()).isEqualTo(
            "package demo.generated\n" +
                "\n" +
                "import demo.Dst\n" +
                "import demo.Src\n" +
                "\n" +
                "public fun Dst.toSrc(): Src = Src(\n" +
                "  a = this.x,\n" +
                "  b = this.b,\n" +
                "  c = this.c\n" +
                ")\n"
        )
    }

    @Test
    fun `mixed forms on different targets combine`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package demo

            import com.blu3berry.kraft.config.FieldMapping
            import com.blu3berry.kraft.config.MapConfig

            data class Src(val a: String, val b: String)
            data class Dst(val x: String, val y: String)

            @MapConfig(
                source = Src::class,
                target = Dst::class,
                fieldMappings = [FieldMapping(source = "a", target = "x")]
            )
            @FieldMapping(source = "b", target = "y")
            object SrcToDst
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source).first().readText()

        assertThat(content).contains("x = this.a")
        assertThat(content).contains("y = this.b")
    }

    @Test
    fun `mixed forms on ignores combine`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package demo

            import com.blu3berry.kraft.config.MapConfig
            import com.blu3berry.kraft.config.MapIgnoreField

            data class Src(val id: Int, val notes: String, val audit: String)
            data class Dst(val id: Int, val notes: String = "", val audit: String = "")

            @MapConfig(
                source = Src::class,
                target = Dst::class,
                ignoredMappings = [MapIgnoreField("notes")]
            )
            @MapIgnoreField("audit")
            object SrcToDst
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source).first().readText()

        assertThat(content).contains("id = this.id")
        assertThat(content).doesNotContain("notes =")
        assertThat(content).doesNotContain("audit =")
    }

    /** Drops the generated-file banner so the comparison starts at the `package` line. */
    private fun String.bodyFromPackage(): String = substring(indexOf("package "))
}
