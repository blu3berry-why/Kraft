package hu.nova.blu3berry.kraft.model

data class MapperId(
    val fromQualifiedName: String,
    val toQualifiedName: String
) {
    override fun toString(): String = "$fromQualifiedName -> $toQualifiedName"
}