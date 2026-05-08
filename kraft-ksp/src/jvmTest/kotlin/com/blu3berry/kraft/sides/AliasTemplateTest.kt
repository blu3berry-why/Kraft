package com.blu3berry.kraft.sides

import com.blu3berry.kraft.processor.sides.AliasTemplate
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AliasTemplateTest {

    @Test
    fun `to-side substitutes side name verbatim`() {
        val t = AliasTemplate.parse("to{side}")
        assertThat(t.render(side = "Domain", source = "ProductCategoryDto", target = "Category"))
            .isEqualTo("toDomain")
    }

    @Test
    fun `to-side-target substitutes target simple name`() {
        val t = AliasTemplate.parse("to{side}{target}")
        assertThat(t.render(side = "Domain", source = "ProductCategoryDto", target = "Category"))
            .isEqualTo("toDomainCategory")
    }

    @Test
    fun `from-source substitutes source simple name`() {
        val t = AliasTemplate.parse("from{source}")
        assertThat(t.render(side = "Domain", source = "ProductCategoryDto", target = "Category"))
            .isEqualTo("fromProductCategoryDto")
    }

    @Test
    fun `case is preserved verbatim`() {
        val t = AliasTemplate.parse("to{side}")
        assertThat(t.render(side = "DTO", source = "S", target = "T"))
            .isEqualTo("toDTO")
    }

    @Test
    fun `unknown variable fails parse`() {
        assertThrows<IllegalArgumentException> { AliasTemplate.parse("to{traget}") }
    }

    @Test
    fun `empty template fails parse`() {
        assertThrows<IllegalArgumentException> { AliasTemplate.parse("") }
    }

    @Test
    fun `template that yields a non-identifier after substitution fails at parse`() {
        // Leading digit yielded by the template literal — invalid Kotlin identifier.
        assertThrows<IllegalArgumentException> { AliasTemplate.parse("1{side}") }
    }

    @Test
    fun `template that has no variables is allowed`() {
        val t = AliasTemplate.parse("toDomain")
        assertThat(t.render(side = "X", source = "S", target = "T")).isEqualTo("toDomain")
    }

    @Test
    fun `validates rendered output is a valid Kotlin identifier`() {
        val t = AliasTemplate.parse("to{side}")
        assertThrows<IllegalArgumentException> {
            t.render(side = "1Bad", source = "S", target = "T")
        }
    }
}
