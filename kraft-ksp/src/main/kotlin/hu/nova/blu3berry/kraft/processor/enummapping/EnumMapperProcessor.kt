package hu.nova.blu3berry.kraft.processor.enummapping

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import hu.nova.blu3berry.kraft.config.EnumMap
import hu.nova.blu3berry.kraft.processor.enummapping.generator.generateEnumMapping
import hu.nova.blu3berry.kraft.processor.enummapping.scanner.EnumMapperScanner

/**
 * Symbol processor for handling @EnumMap annotations.
 *
 * This processor finds all classes annotated with @EnumMap and generates
 * extension functions for mapping between the specified enum types.
 */
class EnumMapperProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Use the scanner to find all enum mappings
        val scanner = EnumMapperScanner(resolver, logger)
        val mappingDescriptors = scanner.scan()
        
        // Process each mapping descriptor
        mappingDescriptors.forEach { descriptor ->
            try {
                generateEnumMapping(
                    codeGenerator = codeGenerator,
                    logger = logger,
                    fromDecl = descriptor.sourceType.declaration,
                    toDecl = descriptor.targetType.declaration,
                    fieldMapping = descriptor.entries.associate { it.source to it.target }
                )
            } catch (e: Exception) {
                logger.error(
                    "EnumMap generation failed for ${descriptor.sourceType.className.simpleName} to ${descriptor.targetType.className.simpleName}: ${e.message}"
                )
            }
        }

        return emptyList()
    }
}