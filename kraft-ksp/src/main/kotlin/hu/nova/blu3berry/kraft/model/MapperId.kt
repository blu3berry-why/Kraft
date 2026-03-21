package hu.nova.blu3berry.kraft.model

data class MapperId(
    val sourceQualifiedName: String,
    val targetQualifiedName: String
) {
    override fun toString(): String = "$sourceQualifiedName -> $targetQualifiedName"
}
