package hu.nova.blu3berry.kraft.processor.enummapping.generator

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FileSpec
import hu.nova.blu3berry.kraft.processor.util.CodeGenUtils
import hu.nova.blu3berry.kraft.processor.enummapping.validator.validateEnumMapping
import hu.nova.blu3berry.kraft.processor.enummapping.generator.buildFieldMapperFunction

/**
 * Generates an enum mapper extension function for mapping between two enum types.
 *
 * @param codeGenerator The KSP code generator for writing files
 * @param logger The KSP logger for reporting errors
 * @param fromDecl The source enum class declaration
 * @param toDecl The target enum class declaration
 * @param fieldMapping Optional custom mapping from source to target enum entries
 */
fun generateEnumMapping(
    codeGenerator: CodeGenerator,
    logger: KSPLogger,
    fromDecl: KSClassDeclaration,
    toDecl: KSClassDeclaration,
    fieldMapping: Map<String, String> = emptyMap() // optional custom mapping
) {
    // Validate that both are enums
    validateEnums(logger = logger, fromDecl = fromDecl, toDecl = toDecl)

    // Extract enum entries
    val fromEntries = getEnumEntries(fromDecl)
    val toEntries = getEnumEntries(toDecl)

    // Validate custom mapping
    if (!validateEnumMapping(fromEntries, toEntries, fieldMapping, logger)) return

    // Build mapper function using resolved mapping
    val mapperFun = buildFieldMapperFunction(fromDecl, toDecl, fieldMapping, logger)

    // Build the file
    val fileName = CodeGenUtils.buildFileName(
        fromDecl.simpleName.asString(),
        toDecl.simpleName.asString(),
        "EnumMapper"
    )

    val fileSpec = FileSpec.builder("${fromDecl.packageName.asString()}.generated", fileName)
        .addFileComment(CodeGenUtils.generatedBanner())
        .addFunction(mapperFun)
        .build()

    // Write the file
    CodeGenUtils.writeFile(codeGenerator, fileSpec, fromDecl.containingFile!!)
}

/**
 * Validates that both declarations are enum classes.
 *
 * @param logger The KSP logger for reporting errors
 * @param fromDecl The source class declaration
 * @param toDecl The target class declaration
 * @return True if both declarations are enum classes, false otherwise
 */
private fun validateEnums(
    logger: KSPLogger,
    fromDecl: KSClassDeclaration,
    toDecl: KSClassDeclaration
): Boolean {
    var valid = true
    if (fromDecl.classKind != ClassKind.ENUM_CLASS) {
        logger.error("EnumMap: 'from' must be an enum class", fromDecl)
        valid = false
    }
    if (toDecl.classKind != ClassKind.ENUM_CLASS) {
        logger.error("EnumMap: 'to' must be an enum class", toDecl)
        valid = false
    }
    return valid
}

/**
 * Extracts enum entries from an enum class declaration.
 *
 * @param decl The enum class declaration
 * @return A list of enum entry names
 */
fun getEnumEntries(decl: KSClassDeclaration): List<String> =
    decl.declarations
        .filterIsInstance<KSClassDeclaration>()
        .filter { it.classKind == ClassKind.ENUM_ENTRY }
        .map { it.simpleName.asString() }
        .toList()
