package hu.nova.blu3berry.kraft.model

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

data class ConverterDescriptor(
    val enclosingObject: KSClassDeclaration?,   // null for top-level functions (if you allow)
    val function: KSFunctionDeclaration,
    val fromType: TypeInfo,
    val toType: TypeInfo
) {
    val functionName: String
        get() = function.simpleName.asString()

    val isExtension: Boolean
        get() = function.extensionReceiver != null
}