package hu.nova.blu3berry.kraft.processor.enummapping.generator

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.buildCodeBlock
import hu.nova.blu3berry.kraft.processor.enummapping.validator.validateEnumMapping

/**
 * Builds a function that maps from one enum type to another.
 *
 * The function creates an extension function on the source enum type that returns
 * the corresponding value from the target enum type based on the provided mapping.
 *
 * @param fromDecl The source enum class declaration
 * @param toDecl The target enum class declaration
 * @param fieldMapping Map of custom mappings from source to target enum entries
 * @param logger KSP logger for reporting errors
 * @return A FunSpec representing the generated extension function
 */
fun buildFieldMapperFunction(
    fromDecl: KSClassDeclaration,
    toDecl: KSClassDeclaration,
    fieldMapping: Map<String, String>, // optional overrides
    logger: KSPLogger
): FunSpec {
    val fromName = fromDecl.simpleName.asString()
    val toName = toDecl.simpleName.asString()

    val fromClassName = ClassName(fromDecl.packageName.asString(), fromName)
    val toClassName = ClassName(toDecl.packageName.asString(), toName)

    val fromEntries = getEnumEntries(fromDecl)
    val toEntries = getEnumEntries(toDecl)

    // Validate mapping again for safety
    if (!validateEnumMapping(fromEntries, toEntries, fieldMapping, logger)) {
        return FunSpec.builder("to$toName").build() // return empty function if invalid
    }

    // Build the "when" mapping
    val codeBlock = buildCodeBlock {
        beginControlFlow("return when(this)")
        fromEntries.forEach { fromEntry ->
            val targetEntry = fieldMapping[fromEntry] ?: fromEntry
            addStatement("%T.%L -> %T.%L", fromClassName, fromEntry, toClassName, targetEntry)
        }
        endControlFlow()
    }

    return FunSpec.builder("to$toName")
        .receiver(fromClassName)
        .returns(toClassName)
        .addCode(codeBlock)
        .build()
}