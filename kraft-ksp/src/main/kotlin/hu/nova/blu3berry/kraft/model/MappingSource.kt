package hu.nova.blu3berry.kraft.model

import com.google.devtools.ksp.symbol.KSClassDeclaration

sealed interface MappingSource {

    data class ClassAnnotation(
        val annotatedClass: KSClassDeclaration,
        val direction: MappingDirection
    ) : MappingSource

    data class ConfigObject(
        val configObject: KSClassDeclaration
    ) : MappingSource
}