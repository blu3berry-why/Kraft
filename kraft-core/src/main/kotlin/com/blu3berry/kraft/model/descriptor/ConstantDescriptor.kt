package com.blu3berry.kraft.model.descriptor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.blu3berry.kraft.config.ConverterDirection
import com.blu3berry.kraft.model.TypeInfo

/**
 * A `@MapConstant` member of a `@MapConfig` object: a property or a zero-parameter function
 * whose value fills one target constructor parameter.
 *
 * @param enclosingObject    The `@MapConfig` object that declares the member.
 * @param memberName         Simple name of the property or function.
 * @param isFunction         True for a function (called on every mapping, `Obj.fn()`),
 *                           false for a property (read as `Obj.prop`).
 * @param targetPropertyName The target constructor parameter the value fills:
 *                           `@MapConstant.target`, or [memberName] when that is blank.
 * @param valueType          The property's type or the function's return type, declared or inferred.
 * @param declaration        The member declaration, used as the location of diagnostics.
 * @param resolvedDirection  [ConverterDirection.FORWARD] or [ConverterDirection.REVERSE]:
 *                           the one mapping direction this constant applies to. AUTO is
 *                           resolved by the scanner and never stored here.
 */
data class ConstantDescriptor(
    val enclosingObject: KSClassDeclaration,
    val memberName: String,
    val isFunction: Boolean,
    val targetPropertyName: String,
    val valueType: TypeInfo,
    val declaration: KSDeclaration,
    val resolvedDirection: ConverterDirection,
) {
    /** `Object.member`, as diagnostics name the constant. */
    val displayName: String
        get() = "${enclosingObject.simpleName.asString()}.$memberName"
}
