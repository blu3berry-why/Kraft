package hu.nova.blu3berry.kraft.model.descriptor

/**
 * A single entry-level mapping between two enum constants.
 *
 * @param source  Name of the source enum constant.
 * @param target  Name of the target enum constant.
 */
data class EnumEntryMapping(val source: String, val target: String)
