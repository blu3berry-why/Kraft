package com.blu3berry.kraft.model.descriptor

/**
 * Two or more source enum entries that an enum mapping sends to the same target entry, which
 * makes the mapping unusable for map keys: the entries' keys would merge and drop entries.
 *
 * @property sources The colliding source entries, in source declaration order.
 * @property target  The target entry they all map to.
 */
data class EnumKeyCollision(val sources: List<String>, val target: String) {

    companion object {
        /**
         * The first collision in [entries], or null when the table is one-to-one (no two source
         * entries map to the same target entry). Source entries are ordered by [sourceOrder], the
         * source enum's declaration order; entries missing from it keep their table order, after
         * the declared ones. "First" is the collision whose earliest source entry comes first.
         */
        fun of(entries: List<EnumEntryMapping>, sourceOrder: List<String>): EnumKeyCollision? {
            val rank = sourceOrder.withIndex().associate { (index, name) -> name to index }
            val ordered = entries.withIndex()
                .sortedWith(compareBy({ rank[it.value.source] ?: (sourceOrder.size + it.index) }))
                .map { it.value }
            return ordered.groupBy { it.target }
                .values
                .firstOrNull { group -> group.size > 1 }
                ?.let { group -> EnumKeyCollision(group.map { it.source }, group.first().target) }
        }
    }
}
