package hu.nova.blu3berry.kraft.model

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Nullability
import com.squareup.kotlinpoet.ClassName

/**
 * Wraps a KSP type reference with metadata needed for both type comparison
 * and code generation.
 *
 * @param declaration  KSP class declaration backing this type.
 * @param ksType       Resolved KSP type (used for equality and nullability checks).
 * @param className    KotlinPoet [ClassName] used when emitting generated code.
 * @param isNullable   Whether the type is declared nullable (`?`).
 */
data class TypeInfo(
    val declaration: KSClassDeclaration,
    val ksType: KSType,
    val className: ClassName,
    val isNullable: Boolean
) {
    companion object {

        /** Creates a [TypeInfo] from a resolved [KSType]. */
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

/** Convenience extension to build a [TypeInfo] from a [KSClassDeclaration] and its resolved [KSType]. */
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


