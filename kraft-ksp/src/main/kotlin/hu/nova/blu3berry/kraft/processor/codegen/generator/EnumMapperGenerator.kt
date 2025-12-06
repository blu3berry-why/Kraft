package hu.nova.blu3berry.kraft.processor.codegen.generator

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.writeTo
import hu.nova.blu3berry.kraft.model.EnumEntryMapping
import hu.nova.blu3berry.kraft.model.EnumMappingDescriptor
import hu.nova.blu3berry.kraft.processor.util.CodeGenUtils

/**
 * Generates enum -> enum mapper functions based on EnumMappingDescriptor.
 *
 * Example output:
 *
 *   fun Status.toStatusDto(): StatusDto = when (this) {
 *       Status.ACTIVE  -> StatusDto.ACTIVE
 *       Status.BLOCKED -> StatusDto.BANNED
 *       else -> StatusDto.UNKNOWN
 *   }
 */
class EnumMapperGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) {

    fun generate(descriptors: List<EnumMappingDescriptor>) {
        descriptors.forEach(::generateOne)
    }

    private fun generateOne(desc: EnumMappingDescriptor) {
        val fromDecl = desc.sourceType.declaration
        val toDecl = desc.targetType.declaration

        // 1) Validate enums
        if (!validateEnums(fromDecl, toDecl)) return

        // 2) Extract real enum entries
        val fromEntries = getEnumEntries(fromDecl).toSet()
        val toEntries = getEnumEntries(toDecl).toSet()

        // 3) Validate descriptors
        if (!validateDescriptor(desc, fromEntries, toEntries)) return

        // 4) Build mapper function
        val funSpec = buildEnumMapperFunction(desc)

        // 5) Compose file path + incremental dependency
        val pkg = "${desc.sourceType.className.packageName}.generated"
        val fileName = CodeGenUtils.buildFileName(
            desc.sourceType.className.simpleName,
            desc.targetType.className.simpleName,
            "EnumMapper"
        )

        val fileSpec = FileSpec.builder(pkg, "$fileName.kt")
            .addFileComment(CodeGenUtils.generatedBanner())
            .addFunction(funSpec)
            .build()

        val originating: KSFile? = fromDecl.containingFile ?: toDecl.containingFile
        val deps = Dependencies(false, originating ?: return)

        fileSpec.writeTo(codeGenerator, deps)

        logger.info("Generated enum mapper for ${desc.sourceType.className} → ${desc.targetType.className}")
    }


    // -------------------------------------------------------------------------
    // VALIDATION
    // -------------------------------------------------------------------------

    private fun validateEnums(from: KSClassDeclaration, to: KSClassDeclaration): Boolean {
        var ok = true
        if (from.classKind != ClassKind.ENUM_CLASS) {
            logger.error("'from' must be an enum class", from)
            ok = false
        }
        if (to.classKind != ClassKind.ENUM_CLASS) {
            logger.error("'to' must be an enum class", to)
            ok = false
        }
        return ok
    }

    private fun validateDescriptor(
        desc: EnumMappingDescriptor,
        fromEntries: Set<String>,
        toEntries: Set<String>
    ): Boolean {
        var ok = true

        // Unknown source entries
        for (mapping in desc.entries) {
            if (mapping.source !in fromEntries) {
                logger.error("EnumMap error: source entry '${mapping.source}' does not exist", desc.sourceType.declaration)
                ok = false
            }
            if (mapping.target !in toEntries) {
                logger.error("EnumMap error: target entry '${mapping.target}' does not exist", desc.targetType.declaration)
                ok = false
            }
        }

        // Default target must exist
        if (desc.allowDefault && desc.defaultTarget != null && desc.defaultTarget !in toEntries) {
            logger.error(
                "EnumMap error: defaultTarget '${desc.defaultTarget}' is not a valid entry of ${desc.targetType.className}",
                desc.targetType.declaration
            )
            ok = false
        }

        return ok
    }


    // -------------------------------------------------------------------------
    // BUILD FUNCTION
    // -------------------------------------------------------------------------

    private fun buildEnumMapperFunction(desc: EnumMappingDescriptor): FunSpec {
        val fromClass = desc.sourceType.className
        val toClass = desc.targetType.className

        val funName = "to${toClass.simpleName}"

        val builder = FunSpec.builder(funName)
            .receiver(fromClass)
            .returns(toClass)

        builder.addCode("return when (this) {\n")

        // explicit mappings
        desc.entries.forEach { entry ->
            builder.addStatement(
                "    %T.%L -> %T.%L",
                fromClass, entry.source,
                toClass, entry.target
            )
        }

        builder.addCode("}\n")

        return builder.build()
    }


    // -------------------------------------------------------------------------
    // ENUM ENTRY RESOLUTION
    // -------------------------------------------------------------------------

    private fun getEnumEntries(decl: KSClassDeclaration): List<String> =
        decl.declarations
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.ENUM_ENTRY }
            .map { it.simpleName.asString() }
            .toList()
}
