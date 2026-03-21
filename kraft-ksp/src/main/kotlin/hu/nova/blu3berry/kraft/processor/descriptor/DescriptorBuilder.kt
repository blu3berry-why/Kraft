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

        buildClassDescriptors(classMappings, configMappings, enumMappings, builtDescriptors)
        buildConfigDescriptors(configMappings, classMappings, enumMappings, builtDescriptors)
        resolveImplicitDependencies(builtDescriptors)
        validateNestedDependencies(builtDescriptors)

        return builtDescriptors.values.toList()
    }

    // ---------------------------
    // 1) CLASS mappings
    // ---------------------------

    private fun buildClassDescriptors(
        classMappings: List<ClassMappingScanResult>,
        configMappings: List<ConfigObjectScanResult>,
        enumMappings: List<EnumMappingDescriptor>,
        builtDescriptors: MutableMap<MapperId, MapperDescriptor>
    ) {
        for (mapping in classMappings) {
            val configsForThis = configMappings.filter {
                it.sourceType == mapping.sourceType && it.targetType == mapping.targetType
            }
            val enumsForThis = enumMappings.filter {
                it.sourceType.declaration == mapping.sourceType &&
                    it.targetType.declaration == mapping.targetType
            }
            ClassDescriptorBuilder(logger, mapping, configsForThis, enumsForThis)
                .build()
                ?.let { builtDescriptors[it.id] = it }
        }
    }

    // ---------------------------
    // 2) CONFIG-only mappings
    // ---------------------------

    private fun buildConfigDescriptors(
        configMappings: List<ConfigObjectScanResult>,
        classMappings: List<ClassMappingScanResult>,
        enumMappings: List<EnumMappingDescriptor>,
        builtDescriptors: MutableMap<MapperId, MapperDescriptor>
    ) {
        val classPairs = classMappings.map { it.sourceType to it.targetType }.toSet()
        for (config in configMappings) {
            if ((config.sourceType to config.targetType) in classPairs) continue
            val enumsForThis = enumMappings.filter {
                it.sourceType.declaration == config.sourceType &&
                    it.targetType.declaration == config.targetType
            }
            ConfigDescriptorBuilder(logger = logger, config = config, enumMappings = enumsForThis)
                .build()
                ?.let { builtDescriptors[it.id] = it }
        }
    }

    // ---------------------------
    // 3) DFS resolution of implicit nested dependencies
    // ---------------------------

    private fun resolveImplicitDependencies(builtDescriptors: MutableMap<MapperId, MapperDescriptor>) {
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
    }

    private fun resolveImplicit(
        source: KSClassDeclaration,
        target: KSClassDeclaration,
        path: List<MapperId>,
        builtDescriptors: MutableMap<MapperId, MapperDescriptor>,
        inProgress: MutableSet<MapperId>
    ) {
        val id = MapperId(
            sourceQualifiedName = source.qualifiedName?.asString() ?: source.simpleName.asString(),
            targetQualifiedName = target.qualifiedName?.asString() ?: target.simpleName.asString()
        )

        if (id in builtDescriptors) return                    // BLACK — already done
        if (id in inProgress) return reportCycleError(path, id, source) // GRAY — cycle detected

        inProgress += id // mark GRAY

        val descriptor = buildMinimalDescriptor(source, target)
        if (descriptor == null) {                             // build failed — error already emitted
            inProgress -= id
            return
        }

        recurseIntoDependencies(descriptor, id, path, builtDescriptors, inProgress)

        inProgress -= id              // unmark GRAY
        builtDescriptors[id] = descriptor // mark BLACK
    }

    private fun recurseIntoDependencies(
        descriptor: MapperDescriptor,
        currentId: MapperId,
        path: List<MapperId>,
        builtDescriptors: MutableMap<MapperId, MapperDescriptor>,
        inProgress: MutableSet<MapperId>
    ) {
        descriptor.propertyMappings
            .filterIsInstance<PropertyMappingStrategy.NestedMapper>()
            .forEach { strategy ->
                resolveImplicit(
                    source = strategy.nestedMappingDescriptor.sourceType.declaration,
                    target = strategy.nestedMappingDescriptor.targetType.declaration,
                    path = path + currentId,
                    builtDescriptors = builtDescriptors,
                    inProgress = inProgress
                )
            }
    }

    private fun reportCycleError(path: List<MapperId>, id: MapperId, source: KSClassDeclaration) {
        val cycleStart = path.indexOf(id)
        val cyclePath = (if (cycleStart >= 0) path.drop(cycleStart) else path) + id
        logger.error(
            "Circular nested mapping: ${cyclePath.joinToString(" → ")}. " +
                "Break the cycle with a @MapUsing converter on one side.",
            source
        )
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

    // ---------------------------
    // 4) Final validation pass
    // ---------------------------

    private fun validateNestedDependencies(builtDescriptors: Map<MapperId, MapperDescriptor>) {
        for (descriptor in builtDescriptors.values) {
            descriptor.nestedDependencies
                .filter { it !in builtDescriptors }
                .forEach { depId ->
                    logger.error(
                        "Nested mapper for $depId could not be built; " +
                            "mapper for ${descriptor.id} will generate code that does not compile.",
                        descriptor.sourceType.declaration
                    )
                }
        }
    }
}
