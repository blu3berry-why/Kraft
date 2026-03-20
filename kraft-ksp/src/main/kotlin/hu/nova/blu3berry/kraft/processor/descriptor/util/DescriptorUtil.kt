package hu.nova.blu3berry.kraft.processor.descriptor.util

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.toTypeInfo
import hu.nova.blu3berry.kraft.processor.util.unsupportedSourcePropertyType

fun KSClassDeclaration.toPropertyInfoMap(logger: KSPLogger): Map<String, PropertyInfo> =
    getDeclaredProperties().mapNotNull { prop ->
        val ks = prop.type.resolve()
        val typeDecl = ks.declaration as? KSClassDeclaration ?: run {
            logger.unsupportedSourcePropertyType(
                typeName = simpleName.asString(),
                propName = prop.simpleName.asString(),
                ksTypeName = ks.toString(),
                declarationKind = ks.declaration::class.simpleName ?: "Unknown",
                symbol = prop
            )
            return@mapNotNull null
        }
        prop.simpleName.asString() to PropertyInfo(
            name = prop.simpleName.asString(),
            type = typeDecl.toTypeInfo(ks),
            declaration = prop,
            hasDefault = false
        )
    }.toMap()