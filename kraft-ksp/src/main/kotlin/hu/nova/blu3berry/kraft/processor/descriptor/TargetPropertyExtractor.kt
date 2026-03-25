package hu.nova.blu3berry.kraft.processor.descriptor

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.toTypeInfo
import hu.nova.blu3berry.kraft.processor.util.constructorPropertyMismatch
import hu.nova.blu3berry.kraft.processor.util.missingConstructorProperty
import hu.nova.blu3berry.kraft.processor.util.unsupportedTypeInConstructor

/**
 * Extracts and validates [PropertyInfo] entries from a target class's primary constructor.
 *
 * Each constructor parameter is matched to its declared property and resolved to a
 * [KSClassDeclaration]-backed [PropertyInfo]. Returns `null` (and logs an error) if any
 * parameter cannot be resolved.
 */
internal class TargetPropertyExtractor(private val logger: KSPLogger) {

    fun extract(
        targetDecl: KSClassDeclaration,
        targetCtor: KSFunctionDeclaration,
        targetTypeName: String
    ): List<PropertyInfo>? {

        val props = targetCtor.parameters.mapNotNull { param ->
            val name = param.name?.asString() ?: return@mapNotNull null

            val declProp = targetDecl.getDeclaredProperties()
                .firstOrNull { it.simpleName.asString() == name }
                ?: run {
                    logger.missingConstructorProperty(
                        typeName = targetTypeName,
                        parameterName = name,
                        available = targetDecl.getDeclaredProperties()
                            .map { it.simpleName.asString() }.toList(),
                        symbol = param
                    )
                    return null
                }

            val ksType = param.type.resolve()
            val decl = ksType.declaration as? KSClassDeclaration ?: run {
                logger.unsupportedTypeInConstructor(
                    typeName = targetTypeName,
                    parameterName = name,
                    actualType = ksType.toString(),
                    symbol = param
                )
                return@mapNotNull null
            }

            PropertyInfo(
                name = name,
                type = decl.toTypeInfo(ksType),
                declaration = declProp,
                hasDefault = param.hasDefault
            )
        }

        if (props.size != targetCtor.parameters.size) {
            logger.constructorPropertyMismatch(targetTypeName, targetDecl)
            return null
        }

        return props
    }
}
