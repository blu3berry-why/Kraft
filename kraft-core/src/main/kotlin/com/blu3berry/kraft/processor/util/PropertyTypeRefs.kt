package com.blu3berry.kraft.processor.util

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

/**
 * Lightweight `(propertyName -> resolvedType)` view of a class's declared
 * properties. Used by the auto-enum deriver to find property pairs by name
 * without taking on `DescriptorBuilder`'s rename/ignore machinery — this
 * deriver is type-driven and only matches by literal property name.
 */
data class PropertyTypeRef(val name: String, val type: KSType)

/**
 * Returns all declared properties of [decl] keyed by simple name with their
 * resolved [KSType]. Mirrors the access pattern used elsewhere in
 * `kraft-core` (see `ClassAnnotationScanner`).
 */
fun collectPropertyTypeRefs(decl: KSClassDeclaration): Map<String, PropertyTypeRef> {
    val map = LinkedHashMap<String, PropertyTypeRef>()
    for (prop in decl.getDeclaredProperties()) {
        val name = prop.simpleName.asString()
        val type = prop.type.resolve()
        map[name] = PropertyTypeRef(name, type)
    }
    return map
}
