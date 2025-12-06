package hu.nova.blu3berry.kraft.processor.descriptor.util

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.KSClassDeclaration
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.toTypeInfo

fun KSClassDeclaration.toPropertyInfoMap(): Map<String, PropertyInfo> =
    getDeclaredProperties().associate { prop ->
        val ks = prop.type.resolve()
        val typeDecl = ks.declaration as KSClassDeclaration
        prop.simpleName.asString() to PropertyInfo(
            name = prop.simpleName.asString(),
            type = typeDecl.toTypeInfo(ks),
            declaration = prop,
            hasDefault = false
        )
    }