package com.blu3berry.kraft.processor.util

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.blu3berry.kraft.model.TypeInfo

/**
 * The single underlying property of a value class Kraft can wrap and unwrap.
 *
 * @property propertyName  Name of the underlying property (`value` in `UserId(val value: String)`).
 * @property type          Resolved underlying type, type aliases unwrapped, use-site nullability kept.
 */
data class ValueClassInfo(
    val propertyName: String,
    val type: KSType
)

/**
 * Returns the underlying property of [type] when it is a non-generic value class
 * whose constructor and underlying property are both public — the only shape a
 * generated mapper can both construct (`UserId(x)`) and read (`id.value`).
 * Returns `null` for every other type, so callers fall through to the rest of the
 * resolver chain.
 */
fun valueClassInfo(type: TypeInfo): ValueClassInfo? {
    val decl = type.declaration
    val isValueClass = Modifier.VALUE in decl.modifiers || Modifier.INLINE in decl.modifiers
    if (!isValueClass || decl.typeParameters.isNotEmpty()) return null
    val paramName = decl.primaryConstructor
        ?.takeIf { it.isPublic() }
        ?.parameters?.singleOrNull()
        ?.name?.asString()
        ?: return null
    return decl.getDeclaredProperties()
        .firstOrNull { it.simpleName.asString() == paramName && it.isPublic() }
        ?.let { property ->
            ValueClassInfo(
                propertyName = paramName,
                type = property.type.resolve().unwrapTypeAliases()
            )
        }
}

/** `true` when [a] and [b] denote the same type once nullability is ignored. */
fun sameTypeIgnoringNullability(a: KSType, b: KSType): Boolean =
    a.makeNotNullable() == b.makeNotNullable()
