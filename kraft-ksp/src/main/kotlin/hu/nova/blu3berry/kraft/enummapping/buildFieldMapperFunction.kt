package hu.nova.blu3berry.kraft.enummapping

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.buildCodeBlock


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
