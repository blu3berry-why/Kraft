package com.blu3berry.kraft.processor.codegen

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.blu3berry.kraft.model.TypeInfo

/**
 * KotlinPoet [ClassName] bridge for code generation.
 *
 * Walks the parent-declaration chain so nested types
 * (e.g. `Outer.Inner.Role`) produce a [ClassName] whose `simpleNames` is
 * `[Outer, Inner, Role]` rather than the leaf-only `[Role]`. KotlinPoet
 * then imports the outermost class and qualifies the nested reference
 * inline, instead of emitting a wrong top-level import for `Role`.
 */
val TypeInfo.className: ClassName get() {
    val chain = mutableListOf<String>()
    var current: KSClassDeclaration? = declaration
    while (current != null) {
        chain.add(0, current.simpleName.asString())
        current = current.parentDeclaration as? KSClassDeclaration
    }
    val head = chain.first()
    val rest = chain.drop(1).toTypedArray()
    @Suppress("SpreadOperator")
    return ClassName(packageName, head, *rest)
}
