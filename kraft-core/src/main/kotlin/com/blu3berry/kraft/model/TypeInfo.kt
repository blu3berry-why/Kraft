package com.blu3berry.kraft.model

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Nullability
import com.blu3berry.kraft.processor.util.unwrapTypeAliases

/**
 * Wraps a KSP type reference with metadata needed for type comparison
 * and code generation.
 *
 * @param declaration  KSP class declaration backing this type.
 * @param ksType       Resolved KSP type (used for equality and nullability checks).
 * @param packageName  Fully qualified package name (e.g. `com.example.model`).
 * @param simpleName   Simple class name (e.g. `UserDto`).
 * @param isNullable   Whether the type is declared nullable (`?`).
 */
data class TypeInfo(
    val declaration: KSClassDeclaration,
    val ksType: KSType,
    val packageName: String,
    val simpleName: String,
    val isNullable: Boolean
) {
    /**
     * Fully qualified name of the type, including any enclosing classes for
     * nested types (e.g. `com.example.Outer.Inner.Role` rather than
     * `com.example.Role`).
     *
     * Used as a string identity key by [GlobalConverterRule] for converter
     * lookup and by [NestedRule] for nested-type comparisons. Composing this
     * from [packageName] + leaf [simpleName] would silently collide a nested
     * type with a same-leaf-named top-level type in the same package, so we
     * defer to KSP's own qualified name resolution which walks the parent
     * declaration chain. Falls back to the package + leaf form only when KSP
     * returns null (anonymous / local declarations, which are rejected
     * elsewhere anyway).
     */
    val qualifiedName: String get() = declaration.qualifiedName?.asString()
        ?: if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"

    companion object {

        /**
         * Creates a [TypeInfo] from a resolved [KSType], unwrapping type
         * aliases to their underlying class first (aliases are transparent
         * at Kotlin call sites, so mapping must see the underlying type).
         */
        fun fromKSType(type: KSType): TypeInfo {
            val unwrapped = type.unwrapTypeAliases()
            val decl = unwrapped.declaration as? KSClassDeclaration
                ?: error("TypeInfo.fromKSType: expected KSClassDeclaration for $type")

            return TypeInfo(
                declaration = decl,
                ksType = unwrapped,
                packageName = decl.packageName.asString(),
                simpleName = decl.simpleName.asString(),
                isNullable = unwrapped.nullability == Nullability.NULLABLE
            )
        }
    }
}

/** Convenience extension to build a [TypeInfo] from a [KSClassDeclaration] and its resolved [KSType]. */
fun KSClassDeclaration.toTypeInfo(ksType: KSType): TypeInfo =
    TypeInfo(
        declaration = this,
        ksType = ksType,
        packageName = packageName.asString(),
        simpleName = simpleName.asString(),
        isNullable = ksType.nullability == Nullability.NULLABLE
    )
