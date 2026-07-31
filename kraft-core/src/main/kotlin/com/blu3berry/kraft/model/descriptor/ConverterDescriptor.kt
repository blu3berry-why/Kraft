package com.blu3berry.kraft.model.descriptor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.blu3berry.kraft.config.ConverterDirection
import com.blu3berry.kraft.model.TypeInfo

/**
 * Describes a converter function — either a `@MapUsing`-annotated function declared
 * inside a `@MapConfig` object, a hand-written `@KraftConverter` extension, or an
 * `@MapEnum`-derived enum mapper that this same KSP run is generating.
 *
 * A converter operates in one of two modes:
 * - **Property-source** ([sourcePropertyName] non-null): receives the value of a single source property.
 * - **Whole-source** ([sourcePropertyName] null): receives the entire source object.
 *
 * @param enclosingObject     The config object containing this function; null for top-level functions.
 * @param function            The KSP function declaration. Null for **synthetic** converters
 *                            generated in this same KSP round (e.g. `@MapEnum` mappers) — those
 *                            don't exist yet as declarations and identify themselves via
 *                            [callPackageName] / [callFunctionName] instead.
 * @param callPackageName     Package containing the function to call. For real converters this
 *                            mirrors `function.packageName`; for synthetic converters it points at
 *                            the package the generator will write to.
 * @param callFunctionName    Simple name of the function to call.
 * @param isExtension         Whether the call site invokes the converter as an extension function
 *                            (`source.fn()`) versus a free call (`fn(source)`). For real converters
 *                            this is `function.extensionReceiver != null`; for synthetic enum
 *                            mappers it is always `true` (they're emitted as `fun Src.toDst()`).
 * @param sourcePropertyName  Source property whose value is passed in; null in whole-source mode.
 * @param targetPropertyName  Target property that receives the converter's return value.
 * @param sourceType          Type of the converter's input.
 * @param targetType          Type of the converter's output.
 * @param resolvedDirection   The mapping direction this converter applies to; [ConverterDirection.AUTO]
 *                            when the config has no `@MapReverse` (all converters serve the single direction).
 * @param useSafeCall         Emit the call as `source?.fn()` instead of `source.fn()`. Set when a
 *                            nullable `X? → Y?` property pair is served by a registered non-null
 *                            `X → Y` bridge (the scalar analogue of `?.map { it.fn() }`).
 */
data class ConverterDescriptor(
    val enclosingObject: KSClassDeclaration?,
    val function: KSFunctionDeclaration?,
    val callPackageName: String,
    val callFunctionName: String,
    val isExtension: Boolean,
    val sourcePropertyName: String?,
    val targetPropertyName: String,
    val sourceType: TypeInfo,
    val targetType: TypeInfo,
    val resolvedDirection: ConverterDirection = ConverterDirection.AUTO,
    val useSafeCall: Boolean = false
) {
    /** Simple name of the converter function. */
    val functionName: String
        get() = callFunctionName
}
