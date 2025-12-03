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
){
    companion object {

        fun fromKSType(type: KSType): TypeInfo {
            val decl = type.declaration as? KSClassDeclaration
                ?: error("TypeInfo.fromKSType: expected KSClassDeclaration for $type")

            return TypeInfo(
                declaration = decl,
                ksType = type,
                className = ClassName(
                    decl.packageName.asString(),
                    decl.simpleName.asString()
                ),
                isNullable = type.nullability == Nullability.NULLABLE
            )
        }
    }
}

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


