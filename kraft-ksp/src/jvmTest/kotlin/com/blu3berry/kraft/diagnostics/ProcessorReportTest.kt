package com.blu3berry.kraft.diagnostics

import com.blu3berry.kraft.TestKspRunner
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Covers `diagnostics/processor-report`: the opt-in `kraft.report` option writes
 * `kraft/report.json` with the converter table, enum pairs, sides and emitted aliases.
 *
 * JSON assertions run on [compact]ed text, so they pin keys, values and key order but not
 * indentation.
 */
@OptIn(ExperimentalCompilerApi::class)
class ProcessorReportTest {

    private companion object {
        val REPORT_ON = mapOf("kraft.report" to "true")

        const val SIMPLE_MAPPER = """
            package app

            import com.blu3berry.kraft.mapping.MapFrom

            data class A(val x: Int)

            @MapFrom(A::class)
            data class B(val x: Int)
        """
    }

    private fun source(text: String) = SourceFile.kotlin("Models.kt", text)

    private fun reportFileOf(result: JvmCompilationResult): File? =
        result.sourcesGeneratedBySymbolProcessor
            .firstOrNull { it.name == "report.json" && it.parentFile.name == "kraft" }

    private fun reportOf(result: JvmCompilationResult): String {
        val file = reportFileOf(result)
        assertThat(file).isNotNull()
        return file!!.readText()
    }

    /** Removes whitespace outside JSON strings: `{ "a": 1 }` → `{"a":1}`. */
    private fun compact(json: String): String {
        val out = StringBuilder()
        var inString = false
        var escaped = false
        for (c in json) {
            when {
                inString -> {
                    out.append(c)
                    if (escaped) escaped = false
                    else if (c == '\\') escaped = true
                    else if (c == '"') inString = false
                }
                c == '"' -> { inString = true; out.append(c) }
                !c.isWhitespace() -> out.append(c)
            }
        }
        return out.toString()
    }

    private fun compiledReport(text: String, options: Map<String, String> = REPORT_ON): String {
        val result = TestKspRunner.compile(source(text), kspOptions = options)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        return compact(reportOf(result))
    }

    @Test
    fun `option enabled writes the report`() {
        val result = TestKspRunner.compile(source(SIMPLE_MAPPER), kspOptions = REPORT_ON)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val file = reportFileOf(result)
        assertThat(file).isNotNull()
        assertThat(file!!.invariantSeparatorsPath).contains("/resources/kraft/report.json")
    }

    @Test
    fun `option absent writes no report and the same Kotlin output`() {
        val off = TestKspRunner.compile(source(SIMPLE_MAPPER))
        val on = TestKspRunner.compile(source(SIMPLE_MAPPER), kspOptions = REPORT_ON)

        assertThat(off.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(reportFileOf(off)).isNull()
        fun kotlinOutput(r: JvmCompilationResult) = r.sourcesGeneratedBySymbolProcessor
            .filter { it.extension == "kt" }
            .associate { it.name to it.readText() }
        assertThat(kotlinOutput(on)).isEqualTo(kotlinOutput(off))
    }

    @Test
    fun `option with another value writes no report`() {
        val result = TestKspRunner.compile(source(SIMPLE_MAPPER), kspOptions = mapOf("kraft.report" to "yes"))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(reportFileOf(result)).isNull()
    }

    @Test
    fun `empty module reports every section`() {
        val report = compiledReport(SIMPLE_MAPPER, REPORT_ON + ("kraft.moduleId" to ":app"))

        assertThat(report).isEqualTo(
            """{"schemaVersion":1,"moduleId":":app","converters":[],"enumPairs":[],"sides":[],"sideAliases":[]}"""
        )
    }

    @Test
    fun `report is byte-identical across runs and converters are sorted`() {
        val text = """
            package app

            import com.blu3berry.kraft.config.KraftConverter
            import com.blu3berry.kraft.mapping.MapFrom

            data class Tag(val value: String)
            data class Money(val cents: Long)

            @KraftConverter fun Tag.toLabel(): String = value
            @KraftConverter fun Money.toCents(): Long = cents

            data class A(val tag: Tag, val price: Money)

            @MapFrom(A::class)
            data class B(val tag: String, val price: Long)
        """
        val first = TestKspRunner.compile(source(text), kspOptions = REPORT_ON)
        val second = TestKspRunner.compile(source(text), kspOptions = REPORT_ON)

        val firstText = reportOf(first)
        assertThat(reportOf(second)).isEqualTo(firstText)
        val report = compact(firstText)
        assertThat(report.indexOf("app.toCents")).isLessThan(report.indexOf("app.toLabel"))
    }

    @Test
    fun `same-module converter is reported with its origin`() {
        val report = compiledReport(
            """
            package app

            import com.blu3berry.kraft.config.KraftConverter

            data class Money(val cents: Long)

            @KraftConverter fun Money.toCents(): Long = cents
            """
        )

        assertThat(report).contains(
            """{"source":"app.Money","target":"kotlin.Long","function":"app.toCents","origin":"same-module"}"""
        )
    }

    @Test
    fun `nullable converter keeps its nullability`() {
        val report = compiledReport(
            """
            package app

            import com.blu3berry.kraft.config.KraftConverter

            data class Tag(val value: String)

            @KraftConverter fun String?.toTag(): Tag? = this?.let(::Tag)
            """
        )

        assertThat(report).contains(
            """{"source":"kotlin.String?","target":"app.Tag?","function":"app.toTag","origin":"same-module"}"""
        )
    }

    private val upstreamMoney = SourceFile.kotlin(
        "Money.kt",
        """
        package core

        import com.blu3berry.kraft.config.KraftConverter

        data class Money(val cents: Long)

        @KraftConverter fun Money.toCents(): Long = cents
        """
    )

    @Test
    fun `classpath delegate used by a mapper is reported with its module`() {
        val result = TestKspRunner.compileWithUpstream(
            upstreamSources = listOf(upstreamMoney),
            consumerSources = listOf(
                source(
                    """
                    package app

                    import com.blu3berry.kraft.mapping.MapFrom
                    import core.Money

                    data class A(val price: Money)

                    @MapFrom(A::class)
                    data class B(val price: Long)
                    """
                )
            ),
            upstreamKspOptions = mapOf("kraft.moduleId" to ":core"),
            consumerKspOptions = REPORT_ON
        ).consumer

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val report = compact(reportOf(result))
        assertThat(report).containsMatch(
            """\{"source":"core\.Money","target":"kotlin\.Long","function":"[^"]+",""" +
                """"origin":"classpath-delegate","module":"_core"\}"""
        )
    }

    @Test
    fun `unused classpath delegate is not reported`() {
        val result = TestKspRunner.compileWithUpstream(
            upstreamSources = listOf(upstreamMoney),
            consumerSources = listOf(source(SIMPLE_MAPPER)),
            upstreamKspOptions = mapOf("kraft.moduleId" to ":core"),
            consumerKspOptions = REPORT_ON
        ).consumer

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(compact(reportOf(result))).doesNotContain("classpath-delegate")
    }

    @Test
    fun `declared enum pair is a synthetic converter and a declared pair`() {
        val report = compiledReport(
            """
            package app

            import com.blu3berry.kraft.config.MapEnum

            enum class StatusDto { ACTIVE, INACTIVE }
            enum class Status { ACTIVE, INACTIVE }

            @MapEnum(source = StatusDto::class, target = Status::class)
            object StatusMapper
            """
        )

        assertThat(report).contains(
            """{"source":"app.StatusDto","target":"app.Status","function":"app.generated.toStatus","origin":"synthetic"}"""
        )
        assertThat(report).contains(
            """{"source":"app.StatusDto","target":"app.Status","function":"app.generated.toStatus",""" +
                """"derivation":"declared","declaredBy":"app.StatusMapper"}"""
        )
    }

    @Test
    fun `auto-derived enum pair is reported as auto`() {
        val report = compiledReport(
            """
            package app

            import com.blu3berry.kraft.mapping.MapFrom

            enum class RoleDto { ADMIN }
            enum class Role { ADMIN }

            data class UserDto(val role: RoleDto)

            @MapFrom(UserDto::class)
            data class User(val role: Role)
            """
        )

        assertThat(report).containsMatch(
            """\{"source":"app\.RoleDto","target":"app\.Role","function":"[^"]+","derivation":"auto"\}"""
        )
    }

    private val sideSources = """
        package app

        import com.blu3berry.kraft.config.MapConfig

        @MapConfig(source = app.domain.User::class, target = app.dto.UserDto::class)
        object UserMapper
    """

    private val sideModels = listOf(
        SourceFile.kotlin("User.kt", "package app.domain\n\ndata class User(val name: String)\n"),
        SourceFile.kotlin("UserDto.kt", "package app.dto\n\ndata class UserDto(val name: String)\n"),
    )

    private fun sideReport(extra: Map<String, String>): String {
        val result = TestKspRunner.compile(
            source(sideSources),
            *sideModels.toTypedArray(),
            kspOptions = REPORT_ON + mapOf(
                "kraft.side.dto.name" to "Dto",
                "kraft.side.dto.packagePattern" to "app.dto.**",
            ) + extra
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        return compact(reportOf(result))
    }

    @Test
    fun `sides and emitted aliases are reported`() {
        val report = sideReport(emptyMap())

        assertThat(report).contains(
            """"sides":[{"slot":"dto","name":"Dto","packagePattern":"app.dto.**",""" +
                """"template":"to{side}","emitMode":"BOTH"}]"""
        )
        assertThat(report).contains(
            """"sideAliases":[{"receiver":"app.domain.User","alias":"toDto","target":"app.dto.UserDto",""" +
                """"side":"Dto","mapper":"app.UserMapper"}]"""
        )
    }

    @Test
    fun `FULL_NAME_ONLY side emits no alias`() {
        val report = sideReport(mapOf("kraft.side.dto.emitMode" to "FULL_NAME_ONLY"))

        assertThat(report).contains(""""emitMode":"FULL_NAME_ONLY"""")
        assertThat(report).contains(""""sideAliases":[]""")
    }

    @Test
    fun `report is written when compilation fails`() {
        val result = TestKspRunner.compile(
            source(
                """
                package app

                import com.blu3berry.kraft.config.KraftConverter
                import com.blu3berry.kraft.mapping.MapFrom

                data class Money(val cents: Long)

                @KraftConverter fun Money.toCents(): Long = cents

                data class A(val price: Money)

                @MapFrom(A::class)
                data class B(val price: String)
                """
            ),
            kspOptions = REPORT_ON
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E005]")
        assertThat(compact(reportOf(result))).contains(
            """{"source":"app.Money","target":"kotlin.Long","function":"app.toCents","origin":"same-module"}"""
        )
    }
}
