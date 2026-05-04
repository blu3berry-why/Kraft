package com.blu3berry.kraft.processor.util

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration

/**
 * Returns the simple names of the enum entries declared on this enum class
 * declaration, in source-declared order. Empty list if [this] is not an enum
 * class — callers must validate `classKind == ENUM_CLASS` themselves when
 * that distinction matters.
 */
fun KSClassDeclaration.enumEntryNames(): List<String> =
    declarations
        .filterIsInstance<KSClassDeclaration>()
        .filter { it.classKind == ClassKind.ENUM_ENTRY }
        .map { it.simpleName.asString() }
        .toList()
