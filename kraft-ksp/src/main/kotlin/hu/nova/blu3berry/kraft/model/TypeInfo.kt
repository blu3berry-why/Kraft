package hu.nova.blu3berry.kraft.model

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Nullability
import com.squareup.kotlinpoet.ClassName

data class TypeInfo(
    val declaration: KSClassDeclaration,
    val ksType: KSType,
    val className: ClassName,
    val isNullable: Boolean
)

fun KSClassDeclaration.toTypeInfo(ksType: KSType): TypeInfo =
    TypeInfo(
        declaration = this,
        ksType = ksType,
        className = ClassName(
            packageName.asString(),
            simpleName.asString()
        ),
        isNullable = ksType.nullability == Nullability.NULLABLE
    )