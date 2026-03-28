package com.blu3berry.kraft.model.scan

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.blu3berry.kraft.model.descriptor.MappingDirection

/**
 * Raw result of scanning a class annotated with `@MapFrom` or `@MapTo`.
 *
 * Produced by the class annotation scanner and later converted into a [MapperDescriptor].
 *
 * @param direction           Whether the annotated class is source or target.
 * @param sourceType          The source class declaration.
 * @param targetType          The target class declaration.
 * @param annotatedClass      The class that carries the annotation.
 * @param propertyScanResults Per-property scan results (one per declared property).
 * @param hasReverse          Whether `@MapReverse` is also present on the annotated class.
 */
data class ClassMappingScanResult(
    val direction: MappingDirection,
    val sourceType: KSClassDeclaration,
    val targetType: KSClassDeclaration,
    val annotatedClass: KSClassDeclaration,
    val propertyScanResults: List<PropertyScanResult> = emptyList(),
    val hasReverse: Boolean = false,
)
