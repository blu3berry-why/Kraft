package hu.nova.blu3berry.kraft.enummapping

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FileSpec
import hu.nova.blu3berry.kraft.processor.util.CodeGenUtils

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

    // ------------------- VALIDATION -------------------

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

    private fun validateEntries(
        logger: KSPLogger,
        fromDecl: KSClassDeclaration,
        toDecl: KSClassDeclaration,
        fromEntries: List<String>,
        toEntries: List<String>
    ): Boolean {
        if (fromEntries.size != toEntries.size) {
            logger.error(
                "EnumMap: Enum sizes differ: ${fromDecl.simpleName.asString()} has ${fromEntries.size}, " +
                        "${toDecl.simpleName.asString()} has ${toEntries.size}",
                fromDecl
            )
            return false
        }

        fromEntries.forEach { entry ->
            if (!toEntries.contains(entry)) {
                logger.error(
                    "EnumMap: Enum entry '$entry' missing in ${toDecl.simpleName.asString()}",
                    fromDecl
                )
                return false
            }
        }

        return true
    }

    fun getEnumEntries(decl: KSClassDeclaration): List<String> =
        decl.declarations
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.ENUM_ENTRY }
            .map { it.simpleName.asString() }
            .toList()




