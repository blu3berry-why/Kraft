package hu.nova.blu3berry.kraft

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import hu.nova.blu3berry.kraft.config.EnumMap
import hu.nova.blu3berry.kraft.enummapping.generateEnumMapping

class EnumMapProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Find all classes annotated with @EnumMap
        val symbols = resolver.getSymbolsWithAnnotation(EnumMap::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()

        symbols.forEach { classDecl ->
            try {
                generateEnumMapper(classDecl, resolver)
            } catch (e: Exception) {
                logger.error("EnumMap generation failed for ${classDecl.simpleName.getShortName()}: ${e.message}")
            }
        }

        return emptyList()
    }

    private fun generateEnumMapper(classDecl: KSClassDeclaration, resolver: Resolver) {
        val annotation = classDecl.annotations.first {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == EnumMap::class.qualifiedName
        }

        val fromType = annotation.arguments.first { it.name?.asString() == "from" }.value as KSType
        val toType = annotation.arguments.first { it.name?.asString() == "to" }.value as KSType

        val fromDecl = fromType.declaration as KSClassDeclaration
        val toDecl = toType.declaration as KSClassDeclaration

        val fieldMappingList = (annotation.arguments
            .firstOrNull { it.name?.asString() == "fieldMapping" }
            ?.value as List<*>?)
            ?.mapNotNull { sp ->
                val spAnnotation = sp as com.google.devtools.ksp.symbol.KSAnnotation
                val from = spAnnotation.arguments.first { it.name?.asString() == "from" }.value as String
                val to = spAnnotation.arguments.first { it.name?.asString() == "to" }.value as String
                from to to
            }

// Detect duplicates
        val duplicates = fieldMappingList?.groupingBy { it.first }
            ?.eachCount()
            ?.filter { it.value > 1 }
            ?.keys

        if (duplicates?.isNotEmpty() == true) {
            duplicates.forEach { dup ->
                logger.error("Duplicate mapping found for '$dup'. Each source entry must map to exactly one target entry.")
                 throw IllegalArgumentException("Duplicate mapping found for '$dup'. Each source entry must map to exactly one target entry.")
            }
        }

// Safe map conversion
        val fieldMapping = fieldMappingList?.toMap() ?: emptyMap()

        // Call the helper function (we wrote before)
        generateEnumMapping(codeGenerator, logger, fromDecl, toDecl,fieldMapping)
    }
}