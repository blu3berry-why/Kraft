package com.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.blu3berry.kraft.config.ConverterDirection
import com.blu3berry.kraft.model.TypeInfo
import com.blu3berry.kraft.model.descriptor.ConstantDescriptor
import com.blu3berry.kraft.processor.util.KraftKspConstants
import com.blu3berry.kraft.processor.util.UNRESOLVED_TYPE_RENDERING
import com.blu3berry.kraft.processor.util.findAnnotation
import com.blu3berry.kraft.processor.util.getEnumArgOrDefault
import com.blu3berry.kraft.processor.util.getStringArgOrDefault
import com.blu3berry.kraft.processor.util.mapConstantExtensionFunction
import com.blu3berry.kraft.processor.util.mapConstantFunctionWithParameters
import com.blu3berry.kraft.processor.util.mapConstantPrivate
import com.blu3berry.kraft.processor.util.mapConstantTypeMismatch
import com.blu3berry.kraft.processor.util.mapConstantUnknownTarget
import com.blu3berry.kraft.processor.util.simpleRendering

/**
 * Finds the `@MapConstant` members of one `@MapConfig` object, validates them (KRAFT-E069),
 * resolves each target name (the member name unless `target` is set) and direction, and
 * returns them as [ConstantDescriptor]s. Members that fail validation are reported and left out.
 *
 * Members outside any `@MapConfig` object are reported by [ConfigObjectScanner], which sees
 * every `@MapConstant` symbol.
 */
internal class ConstantMemberScanner(private val logger: KSPLogger) {

    /**
     * @param configObject  The `@MapConfig` object.
     * @param forwardSource The `@MapConfig.source` class, the reverse mapper's target.
     * @param forwardTarget The `@MapConfig.target` class.
     * @param hasReverse    Whether the object also carries `@MapReverse`.
     */
    fun scan(
        configObject: KSClassDeclaration,
        forwardSource: KSClassDeclaration,
        forwardTarget: KSClassDeclaration,
        hasReverse: Boolean
    ): List<ConstantDescriptor> =
        configObject.declarations
            .filter { it is KSPropertyDeclaration || it is KSFunctionDeclaration }
            .mapNotNull { member ->
                val annotation = member.findAnnotation(KraftKspConstants.FQ_MAP_CONSTANT) ?: return@mapNotNull null
                if (!isValidMember(configObject, member)) return@mapNotNull null

                val memberName = member.simpleName.asString()
                val target = annotation.getStringArgOrDefault(
                    name = KraftKspConstants.ARG_TARGET,
                    default = KraftKspConstants.DEFAULT_MAP_CONSTANT_TARGET,
                    logger = logger,
                    symbol = member,
                    annotationFqName = KraftKspConstants.FQ_MAP_CONSTANT
                ).ifBlank { memberName }
                val directionName = annotation.getEnumArgOrDefault(
                    name = KraftKspConstants.ARG_DIRECTION,
                    default = KraftKspConstants.DEFAULT_MAP_CONSTANT_DIRECTION,
                    logger = logger,
                    symbol = member,
                    annotationFqName = KraftKspConstants.FQ_MAP_CONSTANT
                )
                val direction = resolveDirection(
                    ConverterDirection.entries.firstOrNull { it.name == directionName } ?: ConverterDirection.AUTO,
                    target, forwardSource, forwardTarget, hasReverse
                )
                val directionTarget = if (direction == ConverterDirection.REVERSE) forwardSource else forwardTarget

                val valueKsType = member.valueType()
                val valueType = valueKsType?.let { runCatching { TypeInfo.fromKSType(it) }.getOrNull() }
                    ?: return@mapNotNull reportUnusableType(configObject, member, valueKsType, target, directionTarget)

                ConstantDescriptor(
                    enclosingObject = configObject,
                    memberName = memberName,
                    isFunction = member is KSFunctionDeclaration,
                    targetPropertyName = target,
                    valueType = valueType,
                    declaration = member,
                    resolvedDirection = direction
                )
            }
            .toList()

    /** Reports KRAFT-E069 for a private member, an extension function or a function with parameters. */
    private fun isValidMember(configObject: KSClassDeclaration, member: KSDeclaration): Boolean {
        val name = "${configObject.simpleName.asString()}.${member.simpleName.asString()}"
        val isFunction = member is KSFunctionDeclaration
        when {
            member.isPrivate() -> logger.mapConstantPrivate(name, isFunction, member)
            member is KSFunctionDeclaration && member.extensionReceiver != null ->
                logger.mapConstantExtensionFunction(name, member)
            member is KSFunctionDeclaration && member.parameters.isNotEmpty() ->
                logger.mapConstantFunctionWithParameters(name, member)
            else -> return true
        }
        return false
    }

    /**
     * AUTO is forward when the forward target's primary constructor has the parameter, else
     * reverse when the object has `@MapReverse` and the source's primary constructor has it,
     * else forward (so KRAFT-E067 then reports it against the forward target). Never both.
     */
    private fun resolveDirection(
        declared: ConverterDirection,
        target: String,
        forwardSource: KSClassDeclaration,
        forwardTarget: KSClassDeclaration,
        hasReverse: Boolean
    ): ConverterDirection = when {
        declared != ConverterDirection.AUTO -> declared
        forwardTarget.hasConstructorParameter(target) -> ConverterDirection.FORWARD
        hasReverse && forwardSource.hasConstructorParameter(target) -> ConverterDirection.REVERSE
        else -> ConverterDirection.FORWARD
    }

    private fun KSClassDeclaration.hasConstructorParameter(name: String): Boolean =
        primaryConstructor?.parameters?.any { it.name?.asString() == name } == true

    /** The property's type or the function's return type, declared or inferred. */
    private fun KSDeclaration.valueType(): KSType? = when (this) {
        is KSPropertyDeclaration -> type.resolve()
        is KSFunctionDeclaration -> returnType?.resolve()
        else -> null
    }

    /**
     * A member whose type is not class-backed (a type parameter, a parameterized type alias)
     * cannot become a [TypeInfo]. It is reported here, as KRAFT-E068 against its target, or
     * as KRAFT-E067 when that target does not exist, so it never vanishes silently.
     */
    private fun reportUnusableType(
        configObject: KSClassDeclaration,
        member: KSDeclaration,
        valueType: KSType?,
        target: String,
        targetClass: KSClassDeclaration
    ): ConstantDescriptor? {
        val name = "${configObject.simpleName.asString()}.${member.simpleName.asString()}"
        val className = targetClass.simpleName.asString()
        val parameter = targetClass.primaryConstructor?.parameters?.firstOrNull { it.name?.asString() == target }
        if (parameter == null) {
            val available = targetClass.primaryConstructor?.parameters.orEmpty().mapNotNull { it.name?.asString() }
            logger.mapConstantUnknownTarget(name, target, className, available, member)
        } else {
            logger.mapConstantTypeMismatch(
                constantName = name,
                memberType = valueType?.simpleRendering() ?: UNRESOLVED_TYPE_RENDERING,
                target = target,
                targetClass = className,
                targetType = parameter.type.resolve().simpleRendering(),
                symbol = member
            )
        }
        return null
    }
}
