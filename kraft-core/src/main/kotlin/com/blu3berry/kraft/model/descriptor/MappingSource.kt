package com.blu3berry.kraft.model.descriptor

import com.google.devtools.ksp.symbol.KSClassDeclaration

/**
 * Describes how a [MapperDescriptor] was declared — either via a class-level annotation
 * or via a standalone `@MapConfig` object.
 *
 * Used to trace the origin of a mapper for error reporting and code generation.
 */
sealed interface MappingSource {

    /**
     * Mapper was declared by annotating a class with `@MapFrom` or `@MapTo`.
     *
     * @param annotatedClass  The class that carries the annotation.
     * @param direction       Whether the annotated class is the source or the target.
     */
    data class ClassAnnotation(
        val annotatedClass: KSClassDeclaration,
        val direction: MappingDirection
    ) : MappingSource

    /**
     * Mapper was declared via a `@MapConfig`-annotated companion/singleton object.
     *
     * @param configObject  The object declaration that carries `@MapConfig`.
     */
    data class ConfigObject(
        val configObject: KSClassDeclaration
    ) : MappingSource
}
