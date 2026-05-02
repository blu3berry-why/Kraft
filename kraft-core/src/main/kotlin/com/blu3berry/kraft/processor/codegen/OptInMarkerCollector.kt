package com.blu3berry.kraft.processor.codegen

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.blu3berry.kraft.model.descriptor.MapperDescriptor
import com.blu3berry.kraft.model.descriptor.MappingSource
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy

/**
 * Pair of (package, simple name) for an `@RequiresOptIn` marker class. Used by the
 * generator to construct a KotlinPoet `ClassName` without leaking the
 * `KSClassDeclaration` reference into the codegen layer.
 */
data class OptInMarker(val packageName: String, val simpleName: String) {
    val qualifiedName: String get() = if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"
}

/**
 * Collects the experimental markers that must be opted into by the generated mapper
 * file in order for it to compile.
 *
 * Sources walked, in order:
 * 1. The `@MapConfig` object (when the mapper was declared via `MappingSource.ConfigObject`).
 * 2. The source class declaration.
 * 3. The target class declaration.
 * 4. Every `@MapUsing` converter function on the config object.
 * 5. Every `@KraftConverter` (or `@MapUsing`) function actually invoked by a
 *    [PropertyMappingStrategy.ConverterFunction] entry of the descriptor.
 *
 * Two annotation patterns contribute markers:
 * - `@OptIn(M::class, ...)` — the listed marker classes are added directly.
 * - Any annotation whose own annotation type is meta-annotated with
 *   `kotlin.RequiresOptIn` — the annotation type itself is added as a marker.
 *
 * Markers are deduplicated by qualified name. The returned list is order-stable so
 * the generated annotation argument list is deterministic across builds.
 */
object OptInMarkerCollector {

    private const val OPT_IN_FQ = "kotlin.OptIn"
    private const val REQUIRES_OPT_IN_FQ = "kotlin.RequiresOptIn"
    private const val OPT_IN_MARKER_ARG = "markerClass"

    fun collect(descriptor: MapperDescriptor): List<OptInMarker> {
        val markers = linkedMapOf<String, OptInMarker>()

        collectFrom(descriptor.sourceType.declaration, markers)
        collectFrom(descriptor.targetType.declaration, markers)
        when (val src = descriptor.source) {
            is MappingSource.ConfigObject -> collectFrom(src.configObject, markers)
            is MappingSource.ClassAnnotation -> collectFrom(src.annotatedClass, markers)
        }
        descriptor.converters.forEach { collectFrom(it.function, markers) }
        descriptor.propertyMappings
            .filterIsInstance<PropertyMappingStrategy.ConverterFunction>()
            .forEach { collectFrom(it.converter.function, markers) }

        return markers.values.toList()
    }

    /**
     * Collects opt-in markers from each [KSAnnotated] in [symbols], in iteration order.
     * Used by callers — like [com.blu3berry.kraft.processor.codegen.generator] — that
     * need to assemble markers for a single declaration (e.g. a generated delegate
     * function) without going through a full [MapperDescriptor].
     */
    fun collectFromAnnotated(symbols: Iterable<KSAnnotated>): List<OptInMarker> {
        val markers = linkedMapOf<String, OptInMarker>()
        symbols.forEach { collectFrom(it, markers) }
        return markers.values.toList()
    }

    private fun collectFrom(symbol: KSAnnotated, sink: MutableMap<String, OptInMarker>) {
        for (annotation in symbol.annotations) {
            val annotationType = annotation.annotationType.resolve().declaration as? KSClassDeclaration
                ?: continue
            val annotationFq = annotationType.qualifiedName?.asString() ?: continue

            when {
                annotationFq == OPT_IN_FQ -> addMarkersFromOptIn(annotation, sink)
                isRequiresOptInMarker(annotationType) -> addMarker(annotationType, sink)
            }
        }
    }

    private fun isRequiresOptInMarker(annotationType: KSClassDeclaration): Boolean =
        annotationType.annotations.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == REQUIRES_OPT_IN_FQ
        }

    private fun addMarkersFromOptIn(annotation: KSAnnotation, sink: MutableMap<String, OptInMarker>) {
        val markerArg = annotation.arguments.firstOrNull { it.name?.asString() == OPT_IN_MARKER_ARG }
            ?: return
        val markerValues = markerArg.value as? List<*> ?: return
        for (value in markerValues) {
            val type = value as? KSType ?: continue
            val decl = type.declaration as? KSClassDeclaration ?: continue
            addMarker(decl, sink)
        }
    }

    private fun addMarker(decl: KSClassDeclaration, sink: MutableMap<String, OptInMarker>) {
        val fq = decl.qualifiedName?.asString() ?: return
        if (fq in sink) return
        sink[fq] = OptInMarker(
            packageName = decl.packageName.asString(),
            simpleName = decl.simpleName.asString()
        )
    }
}
