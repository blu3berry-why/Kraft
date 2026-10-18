package com.blu3berry.kraft.processor.descriptor

import com.google.devtools.ksp.processing.KSPLogger
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.ConstantDescriptor
import com.blu3berry.kraft.processor.util.mapConstantUnknownTarget

/**
 * Keeps the [constants] whose target is a constructor parameter of the target class and
 * reports KRAFT-E067 for each one that is not. The `@MapConstant` counterpart of the
 * `@MapIgnoreField` unknown-name check in [IgnoredPropertyAggregator.resolveConfigIgnored].
 *
 * @param targetClassName Simple name of the class this direction's mapper constructs.
 */
internal fun validateConstantTargets(
    logger: KSPLogger,
    constants: List<ConstantDescriptor>,
    targetProps: List<PropertyInfo>,
    targetClassName: String
): List<ConstantDescriptor> {
    val names = targetProps.map { it.name }.toSet()
    return constants.filter { constant ->
        val found = constant.targetPropertyName in names
        if (!found) {
            logger.mapConstantUnknownTarget(
                constantName = constant.displayName,
                target = constant.targetPropertyName,
                targetClass = targetClassName,
                available = names,
                symbol = constant.declaration
            )
        }
        found
    }
}
