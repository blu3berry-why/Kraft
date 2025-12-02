package hu.nova.blu3berry.kraft.model

import com.google.devtools.ksp.symbol.KSPropertyDeclaration

data class PropertyInfo(
    val name: String,
    val type: TypeInfo,
    val declaration: KSPropertyDeclaration,
    val hasDefault: Boolean
)