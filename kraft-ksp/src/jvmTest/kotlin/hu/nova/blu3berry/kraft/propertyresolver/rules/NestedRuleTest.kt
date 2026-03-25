package hu.nova.blu3berry.kraft.propertyresolver.rules

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class NestedRuleTest {

    @Test
    fun `match - auto-detection triggers when same-named property has different mappable types`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class AddressSource(val street: String, val city: String)
            data class PersonSource(val name: String, val address: AddressSource)

            data class AddressDto(val street: String, val city: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(val name: String, val address: AddressDto)
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .joinToString("\n") { it.readText() }

        assertThat(content).contains("fun PersonSource.toPersonDto()")
        assertThat(content).contains("fun AddressSource.toAddressDto()")
        assertThat(content).contains("address = this.address.toAddressDto()")
    }

    @Test
    fun `match - @MapNested explicit annotation generates nested mapper`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class CompanySource(val legalName: String)
            data class EmployeeSource(val fullName: String, val employer: CompanySource)

            data class CompanyDto(val legalName: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(EmployeeSource::class)
            data class EmployeeDto(
                val fullName: String,
                @hu.nova.blu3berry.kraft.mapping.MapNested
                val employer: CompanyDto
            )
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .joinToString("\n") { it.readText() }

        assertThat(content).contains("fun EmployeeSource.toEmployeeDto()")
        assertThat(content).contains("fun CompanySource.toCompanyDto()")
        assertThat(content).contains("employer = this.employer.toCompanyDto()")
    }

    @Test
    fun `no-match - auto-detection does not trigger when source and target types are the same`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class TagSource(val value: String)
            data class ArticleSource(val title: String, val tag: TagSource)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(ArticleSource::class)
            data class ArticleDto(val title: String, val tag: TagSource)
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .joinToString("\n") { it.readText() }

        assertThat(content).contains("tag = this.tag")
        assertThat(content).doesNotContain("fun TagSource.toTagSource()")
    }

    @Test
    fun `error - @MapNested on property with non-mappable type emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class ItemSource(val name: String, val price: Double)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(ItemSource::class)
            data class ItemDto(
                val name: String,
                @hu.nova.blu3berry.kraft.mapping.MapNested
                val price: String
            )
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("is not a concrete class")
    }
}
