package com.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class TransitiveNestedMappingTest {

    @Test
    fun `three-level chain generates all three mappers in one pass`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class EmployeeSource(val id: Int)
            data class DepartmentSource(val name: String, val manager: EmployeeSource)
            data class CompanySource(val title: String, val department: DepartmentSource)

            data class EmployeeDto(val id: Int)
            data class DepartmentDto(val name: String, val manager: EmployeeDto)

            @com.blu3berry.kraft.mapping.MapFrom(CompanySource::class)
            data class CompanyDto(val title: String, val department: DepartmentDto)
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("fun CompanySource.toCompanyDto()")
        assertThat(content).contains("fun DepartmentSource.toDepartmentDto()")
        assertThat(content).contains("fun EmployeeSource.toEmployeeDto()")
        assertThat(content).contains("department = this.department.toDepartmentDto()")
        assertThat(content).contains("manager = this.manager.toEmployeeDto()")
    }
}
