package com.blu3berry.kraft.model.descriptor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.blu3berry.kraft.config.ConverterDirection
import com.blu3berry.kraft.model.TypeInfo

/**
 * Describes a `@MapUsing`-annotated converter function declared inside a `@MapConfig` object.
 *
 * A converter operates in one of two modes:
 * - **Property-source** ([sourcePropertyName] non-null): receives the value of a single source property.
 * - **Whole-source** ([sourcePropertyName] null): receives the entire source object.
 *
 * @param enclosingObject     The config object containing this function; null for top-level functions.
 * @param function            The KSP function declaration.
 * @param sourcePropertyName  Source property whose value is passed in; null in whole-source mode.
 * @param targetPropertyName  Target property that receives the converter's return value.
 * @param sourceType          Type of the converter's input.
 * @param targetType          Type of the converter's output.
 * @param resolvedDirection   The mapping direction this converter applies to; [ConverterDirection.AUTO]
 *                            when the config has no `@MapReverse` (all converters serve the single direction).
 */
data class ConverterDescriptor(
    val enclosingObject: KSClassDeclaration?,
    val function: KSFunctionDeclaration,
    val sourcePropertyName: String?,
    val targetPropertyName: String,
    val sourceType: TypeInfo,
    val targetType: TypeInfo,
    val resolvedDirection: ConverterDirection = ConverterDirection.AUTO
) {
    /** Simple name of the converter function. */
    val functionName: String
        get() = function.simpleName.asString()

    /** Whether the converter function is an extension function on the source type. */
    val isExtension: Boolean
        get() = function.extensionReceiver != null
}
