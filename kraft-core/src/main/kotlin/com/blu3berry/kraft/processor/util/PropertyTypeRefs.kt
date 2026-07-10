package com.blu3berry.kraft.processor.util

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

/**
 * Returns all declared properties of the receiver keyed by simple name with
 * their resolved [KSType], type aliases unwrapped. Mirrors the access pattern
 * used elsewhere in `kraft-core` (see `ClassAnnotationScanner`).
 */
fun KSClassDeclaration.collectPropertyTypeRefs(): Map<String, KSType> {
    val map = LinkedHashMap<String, KSType>()
    for (prop in getDeclaredProperties()) {
        map[prop.simpleName.asString()] = prop.type.resolve().unwrapTypeAliases()
    }
    return map
}
