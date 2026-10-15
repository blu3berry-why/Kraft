package com.blu3berry.kraft.mapenum

import com.google.common.truth.Truth.assertThat
import com.blu3berry.kraft.model.descriptor.EnumEntryMapping
import com.blu3berry.kraft.model.descriptor.EnumKeyCollision
import org.junit.jupiter.api.Test

/**
 * Enum-mapping injectivity (task 6.1, design D4): the entry table decides whether an enum
 * mapper may map `Map` keys. `ConverterEntry.Synthetic.keyCollision` is this value, computed
 * at registration from the descriptor's entries and the source enum's declaration order.
 */
class EnumKeyCollisionTest {

    @Test
    fun `an auto-derived mapping is injective`() {
        // AutoEnumMappingDeriver maps every source entry to the same-named target entry.
        val names = listOf("OPEN", "CLOSED", "ARCHIVED")
        val entries = names.map { EnumEntryMapping(source = it, target = it) }
        assertThat(EnumKeyCollision.of(entries, names)).isNull()
    }

    @Test
    fun `a one-to-one MapEnum is injective`() {
        val entries = listOf(EnumEntryMapping("OPEN", "ACTIVE"), EnumEntryMapping("CLOSED", "DONE"))
        assertThat(EnumKeyCollision.of(entries, listOf("OPEN", "CLOSED"))).isNull()
    }

    @Test
    fun `a MapEnum mapping two entries to one is not injective`() {
        // fieldMappings order (REOPENED first) differs from declaration order (OPEN first).
        val entries = listOf(
            EnumEntryMapping("REOPENED", "ACTIVE"),
            EnumEntryMapping("OPEN", "ACTIVE"),
            EnumEntryMapping("CLOSED", "CLOSED")
        )
        assertThat(EnumKeyCollision.of(entries, listOf("OPEN", "REOPENED", "CLOSED")))
            .isEqualTo(EnumKeyCollision(sources = listOf("OPEN", "REOPENED"), target = "ACTIVE"))
    }
}
