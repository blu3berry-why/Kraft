package hu.nova.blu3berry.kraft.processor.descriptor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import hu.nova.blu3berry.kraft.model.ClassMappingScanResult
import hu.nova.blu3berry.kraft.model.ConfigObjectScanResult
import hu.nova.blu3berry.kraft.model.EnumMappingDescriptor
import hu.nova.blu3berry.kraft.model.MapperDescriptor
import hu.nova.blu3berry.kraft.model.MapperId
import hu.nova.blu3berry.kraft.model.MappingDirection
import hu.nova.blu3berry.kraft.model.PropertyMappingStrategy

class DescriptorBuilder(
    private val logger: KSPLogger
) {

    fun build(
        classMappings: List<ClassMappingScanResult>,
        configMappings: List<ConfigObjectScanResult>,
        enumMappings: List<EnumMappingDescriptor>,
    ): List<MapperDescriptor> {

        val builtDescriptors = mutableMapOf<MapperId, MapperDescriptor>()

        // ---------------------------
        // 1) Handle CLASS mappings
        // ---------------------------
        for (mapping in classMappings) {

            val configsForThis = configMappings.filter {
                it.fromType == mapping.sourceType &&
                        it.toType == mapping.targetType
            }

            val enumsForThis = enumMappings.filter {
                it.sourceType.declaration == mapping.sourceType &&
                        it.targetType.declaration == mapping.targetType
            }

            val descriptor = ClassDescriptorBuilder(
                logger,
                mapping,
                configsForThis,
                enumsForThis
            ).build()

            if (descriptor != null) builtDescriptors[descriptor.id] = descriptor
        }

        // ---------------------------
        // 2) Handle CONFIG-only mappings
        // ---------------------------
        val classPairs = classMappings.map { it.sourceType to it.targetType }.toSet()

        for (config in configMappings) {
            val pair = config.fromType to config.toType

            if (pair !in classPairs) {
                val enumsForThis = enumMappings.filter {
                    it.sourceType.declaration == config.fromType &&
                            it.targetType.declaration == config.toType
                }

                val descriptor = ConfigDescriptorBuilder(
                    logger = logger,
                    config = config,
                    enumMappings = enumsForThis
                ).build()

                if (descriptor != null) builtDescriptors[descriptor.id] = descriptor
            }
        }

        // ---------------------------
        // 3) DFS resolution of implicit nested dependencies
        // ---------------------------
        val inProgress = mutableSetOf<MapperId>()

        for (descriptor in builtDescriptors.values.toList()) {
            descriptor.propertyMappings
                .filterIsInstance<PropertyMappingStrategy.NestedMapper>()
                .filter { it.nestedMappingDescriptor.nestedMapperId !in builtDescriptors }
                .forEach { strategy ->
                    resolveImplicit(
                        source = strategy.nestedMappingDescriptor.sourceType.declaration,
                        target = strategy.nestedMappingDescriptor.targetType.declaration,
                        path = listOf(descriptor.id),
                        builtDescriptors = builtDescriptors,
                        inProgress = inProgress
                    )
                }
        }

        return builtDescriptors.values.toList()
    }

    private fun resolveImplicit(
        source: KSClassDeclaration,
        target: KSClassDeclaration,
        path: List<MapperId>,
        builtDescriptors: MutableMap<MapperId, MapperDescriptor>,
        inProgress: MutableSet<MapperId>
    ) {
        val id = MapperId(
            fromQualifiedName = source.qualifiedName?.asString() ?: source.simpleName.asString(),
            toQualifiedName = target.qualifiedName?.asString() ?: target.simpleName.asString()
        )

        if (id in builtDescriptors) return // BLACK — already done

        if (id in inProgress) { // GRAY — back-edge, cycle detected
            val cycleStart = path.indexOf(id)
            val cyclePath = (if (cycleStart >= 0) path.drop(cycleStart) else path) + id
            logger.error(
                "Circular nested mapping: ${cyclePath.joinToString(" → ")}. " +
                    "Break the cycle with a @MapUsing converter on one side.",
                source
            )
            return
        }

        inProgress += id // mark GRAY

        val descriptor = buildMinimalDescriptor(source, target)

        if (descriptor == null) { // build failed — error already emitted by ClassDescriptorBuilder
            inProgress -= id
            return
        }

        // Recurse into this descriptor's own nested dependencies
        descriptor.propertyMappings
            .filterIsInstance<PropertyMappingStrategy.NestedMapper>()
            .forEach { strategy ->
                resolveImplicit(
                    source = strategy.nestedMappingDescriptor.sourceType.declaration,
                    target = strategy.nestedMappingDescriptor.targetType.declaration,
                    path = path + id,
                    builtDescriptors = builtDescriptors,
                    inProgress = inProgress
                )
            }

        inProgress -= id          // unmark GRAY
        builtDescriptors[id] = descriptor // mark BLACK
    }

    private fun buildMinimalDescriptor(
        source: KSClassDeclaration,
        target: KSClassDeclaration
    ): MapperDescriptor? {
        val syntheticMapping = ClassMappingScanResult(
            direction = MappingDirection.FROM,
            sourceType = source,
            targetType = target,
            annotatedClass = target,
            propertyScanResults = emptyList()
        )
        return ClassDescriptorBuilder(
            logger = logger,
            mapping = syntheticMapping,
            configObjects = emptyList(),
            enumMappings = emptyList()
        ).build()
    }
}
