package hu.nova.blu3berry.kraft.model

/**
 * Unique identity key for a mapper pair.
 *
 * Used for deduplication (multiple parents may depend on the same child mapper)
 * and cycle detection (three-colour DFS) during nested mapper resolution.
 *
 * @param sourceQualifiedName  Fully-qualified name of the source class.
 * @param targetQualifiedName  Fully-qualified name of the target class.
 */
data class MapperId(
    val sourceQualifiedName: String,
    val targetQualifiedName: String
) {
    override fun toString(): String = "$sourceQualifiedName -> $targetQualifiedName"
}
