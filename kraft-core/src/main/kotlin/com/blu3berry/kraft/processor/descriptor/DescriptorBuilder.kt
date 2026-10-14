package com.blu3berry.kraft.processor.descriptor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.blu3berry.kraft.model.scan.ClassMappingScanResult
import com.blu3berry.kraft.model.scan.ConfigObjectScanResult
import com.blu3berry.kraft.model.scan.GlobalConverterRegistry
import com.blu3berry.kraft.model.descriptor.EnumMappingDescriptor
import com.blu3berry.kraft.model.descriptor.MapperDescriptor
import com.blu3berry.kraft.model.MapperId
import com.blu3berry.kraft.model.descriptor.MappingDirection
import com.blu3berry.kraft.model.descriptor.NestedMappingDescriptor
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.processor.diagnostics.MutedKspLogger
import com.blu3berry.kraft.processor.diagnostics.KraftDiagnostic
import com.blu3berry.kraft.processor.diagnostics.report

/** Top-level orchestrator that converts scan results into a list of [MapperDescriptor], resolving implicit nested dependencies via DFS. */
class DescriptorBuilder(
    private val logger: KSPLogger
) {

    fun build(
        classMappings: List<ClassMappingScanResult>,
        configMappings: List<ConfigObjectScanResult>,
        enumMappings: List<EnumMappingDescriptor>,
        globalConverters: GlobalConverterRegistry = GlobalConverterRegistry.EMPTY,
    ): List<MapperDescriptor> {
        val builtDescriptors = mutableMapOf<MapperId, MapperDescriptor>()

        buildClassDescriptors(classMappings, configMappings, enumMappings, globalConverters, builtDescriptors)
        buildConfigDescriptors(configMappings, classMappings, enumMappings, globalConverters, builtDescriptors)
        buildReverseDescriptors(classMappings, configMappings, globalConverters, builtDescriptors)
        // Enum-backed nested IDs (e.g. Status → StatusDto from List<Status>/List<StatusDto>
        // collection-element pairs) are covered by EnumMappingDescriptor, not MapperDescriptor.
        // Exclude them from implicit dependency resolution (so no class mapper is built for
        // enum types, which would produce a duplicate conflicting overload) and from the
        // missing-nested-dependency validation.
        val enumBackedIds = enumMappings.mapNotNullTo(HashSet()) { desc ->
            val src = desc.sourceType.declaration.qualifiedName?.asString() ?: return@mapNotNullTo null
            val tgt = desc.targetType.declaration.qualifiedName?.asString() ?: return@mapNotNullTo null
            MapperId(src, tgt)
        }
        resolveImplicitDependencies(builtDescriptors, configMappings, globalConverters, enumBackedIds)
        validateNestedDependencies(builtDescriptors, enumBackedIds)

        val shadowNestedCheck = ShadowNestedCheck(
            available = builtDescriptors.keys + enumBackedIds,
            configMappings = configMappings,
            globalConverters = globalConverters.silent(),
        )
        return builtDescriptors.values.map { dropUnavailableShadows(it, shadowNestedCheck) }
    }

    /**
     * Clears [PropertyMappingStrategy.ConverterFunction.shadowedNative] when it is a nested
     * mapper call that Kraft could not actually generate. The `KRAFT-W001` probe is not
     * allowed to build nested mappers, so this answers "would the mapper exist?" here: it
     * exists if it was built, is enum-backed, or could be built implicitly (checked with a
     * muted logger and nothing registered).
     */
    private fun dropUnavailableShadows(
        descriptor: MapperDescriptor,
        check: ShadowNestedCheck
    ): MapperDescriptor {
        var changed = false
        val mappings = descriptor.propertyMappings.map { strategy ->
            val shadow = (strategy as? PropertyMappingStrategy.ConverterFunction)?.shadowedNative
            if (shadow is PropertyMappingStrategy.NestedMapper &&
                !check.isAvailable(shadow.nestedMappingDescriptor)
            ) {
                changed = true
                strategy.copy(shadowedNative = null)
            } else {
                strategy
            }
        }
        return if (changed) descriptor.copy(propertyMappings = mappings) else descriptor
    }

    /**
     * Side-effect-free "could this nested mapper exist?" check for `KRAFT-W001` shadows.
     * Mirrors [resolveImplicit] (including its dependencies, recursively) with a
     * [MutedKspLogger], a silent converter registry, and no registration. A cycle counts as
     * unavailable, because the real build reports it as an error.
     */
    private class ShadowNestedCheck(
        private val available: Set<MapperId>,
        private val configMappings: List<ConfigObjectScanResult>,
        private val globalConverters: GlobalConverterRegistry,
    ) {
        private val mutedBuilder = DescriptorBuilder(MutedKspLogger)
        private val verdicts = mutableMapOf<MapperId, Boolean>()

        fun isAvailable(nested: NestedMappingDescriptor): Boolean =
            isAvailable(
                nested.nestedMapperId,
                nested.sourceType.declaration,
                nested.targetType.declaration,
                mutableSetOf()
            )

        private fun isAvailable(
            id: MapperId,
            source: KSClassDeclaration,
            target: KSClassDeclaration,
            visiting: MutableSet<MapperId>
        ): Boolean {
            if (id in available) return true
            verdicts[id]?.let { return it }
            if (!visiting.add(id)) return false
            val descriptor = mutedBuilder.buildMinimalDescriptor(source, target, configMappings, globalConverters)
            val verdict = descriptor != null && descriptor.propertyMappings
                .filterIsInstance<PropertyMappingStrategy.NestedMapper>()
                .all {
                    val dep = it.nestedMappingDescriptor
                    isAvailable(dep.nestedMapperId, dep.sourceType.declaration, dep.targetType.declaration, visiting)
                }
            visiting -= id
            verdicts[id] = verdict
            return verdict
        }
    }

    // ---------------------------
    // 1) CLASS mappings
    // ---------------------------

    private fun buildClassDescriptors(
        classMappings: List<ClassMappingScanResult>,
        configMappings: List<ConfigObjectScanResult>,
        enumMappings: List<EnumMappingDescriptor>,
        globalConverters: GlobalConverterRegistry,
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
            ClassDescriptorBuilder(logger, mapping, configsForThis, enumsForThis, globalConverters)
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
        globalConverters: GlobalConverterRegistry,
        builtDescriptors: MutableMap<MapperId, MapperDescriptor>
    ) {
        val classPairs = classMappings.map { it.sourceType to it.targetType }.toSet()
        for (config in configMappings) {
            if ((config.sourceType to config.targetType) in classPairs) continue
            val enumsForThis = enumMappings.filter {
                it.sourceType.declaration == config.sourceType &&
                    it.targetType.declaration == config.targetType
            }
            ConfigDescriptorBuilder(
                logger = logger,
                config = config,
                enumMappings = enumsForThis,
                globalConverters = globalConverters
            ).build()
                ?.let {
                    val existing = builtDescriptors[it.id]
                    if (existing != null) {
                        logger.report(
                            KraftDiagnostic.DUPLICATE_MAPPING,
                            "Duplicate mapping for ${it.id}: multiple @MapConfig objects declare " +
                                "the same source → target pair. Only one mapping per type pair is allowed.",
                            config.configObject
                        )
                    } else {
                        builtDescriptors[it.id] = it
                    }
                }
        }
    }

    // ---------------------------
    // 3) REVERSE descriptors for @MapReverse
    // ---------------------------

    private fun buildReverseDescriptors(
        classMappings: List<ClassMappingScanResult>,
        configMappings: List<ConfigObjectScanResult>,
        globalConverters: GlobalConverterRegistry,
        builtDescriptors: MutableMap<MapperId, MapperDescriptor>
    ) {
        buildClassReverseDescriptors(classMappings, configMappings, globalConverters, builtDescriptors)
        buildConfigReverseDescriptors(classMappings, configMappings, globalConverters, builtDescriptors)
    }

    private fun buildClassReverseDescriptors(
        classMappings: List<ClassMappingScanResult>,
        configMappings: List<ConfigObjectScanResult>,
        globalConverters: GlobalConverterRegistry,
        builtDescriptors: MutableMap<MapperId, MapperDescriptor>
    ) {
        for (mapping in classMappings) {
            val configsForThis = configMappings.filter {
                it.sourceType == mapping.sourceType &&
                    it.targetType == mapping.targetType
            }
            if (!mapping.hasReverse && configsForThis.none { it.hasReverse }) {
                continue
            }
            tryBuildReverse(
                mapping.sourceType, mapping.targetType,
                configsForThis, mapping.annotatedClass, globalConverters, builtDescriptors
            )
        }
    }

    private fun buildConfigReverseDescriptors(
        classMappings: List<ClassMappingScanResult>,
        configMappings: List<ConfigObjectScanResult>,
        globalConverters: GlobalConverterRegistry,
        builtDescriptors: MutableMap<MapperId, MapperDescriptor>
    ) {
        val classPairs = classMappings
            .map { it.sourceType to it.targetType }.toSet()
        configMappings
            .filter { it.hasReverse }
            .filter { (it.sourceType to it.targetType) !in classPairs }
            .forEach { config ->
                tryBuildReverse(
                    config.sourceType, config.targetType,
                    listOf(config), config.configObject, globalConverters, builtDescriptors
                )
            }
    }

    @Suppress("LongParameterList")
    private fun tryBuildReverse(
        sourceType: KSClassDeclaration,
        targetType: KSClassDeclaration,
        configs: List<ConfigObjectScanResult>,
        errorNode: KSClassDeclaration,
        globalConverters: GlobalConverterRegistry,
        builtDescriptors: MutableMap<MapperId, MapperDescriptor>
    ) {
        val forwardId = MapperId(
            sourceQualifiedName = sourceType.qualifiedName?.asString()
                ?: sourceType.simpleName.asString(),
            targetQualifiedName = targetType.qualifiedName?.asString()
                ?: targetType.simpleName.asString()
        )
        val forwardDescriptor = builtDescriptors[forwardId] ?: return
        val reverseId = MapperId(
            forwardId.targetQualifiedName,
            forwardId.sourceQualifiedName
        )
        if (reverseId in builtDescriptors) return

        ReverseDescriptorBuilder(logger, forwardDescriptor, configs, errorNode, globalConverters)
            .build()
            ?.let { builtDescriptors[it.id] = it }
    }

    // ---------------------------
    // 4) DFS resolution of implicit nested dependencies
    // ---------------------------

    private fun resolveImplicitDependencies(
        builtDescriptors: MutableMap<MapperId, MapperDescriptor>,
        configMappings: List<ConfigObjectScanResult>,
        globalConverters: GlobalConverterRegistry,
        enumBackedIds: Set<MapperId> = emptySet(),
    ) {
        val inProgress = mutableSetOf<MapperId>()
        for (descriptor in builtDescriptors.values.toList()) {
            descriptor.propertyMappings
                .filterIsInstance<PropertyMappingStrategy.NestedMapper>()
                .filter {
                    val id = it.nestedMappingDescriptor.nestedMapperId
                    id !in builtDescriptors && id !in enumBackedIds
                }
                .forEach { strategy ->
                    resolveImplicit(
                        source = strategy.nestedMappingDescriptor.sourceType.declaration,
                        target = strategy.nestedMappingDescriptor.targetType.declaration,
                        path = listOf(descriptor.id),
                        builtDescriptors = builtDescriptors,
                        inProgress = inProgress,
                        configMappings = configMappings,
                        globalConverters = globalConverters,
                        enumBackedIds = enumBackedIds,
                    )
                }
        }
    }

    @Suppress("LongParameterList")
    private fun resolveImplicit(
        source: KSClassDeclaration,
        target: KSClassDeclaration,
        path: List<MapperId>,
        builtDescriptors: MutableMap<MapperId, MapperDescriptor>,
        inProgress: MutableSet<MapperId>,
        configMappings: List<ConfigObjectScanResult>,
        globalConverters: GlobalConverterRegistry,
        enumBackedIds: Set<MapperId> = emptySet(),
    ) {
        val id = MapperId(
            sourceQualifiedName = source.qualifiedName?.asString() ?: source.simpleName.asString(),
            targetQualifiedName = target.qualifiedName?.asString() ?: target.simpleName.asString()
        )

        if (id in builtDescriptors) return                    // BLACK — already done
        if (id in inProgress) return reportCycleError(path, id, source) // GRAY — cycle detected

        inProgress += id // mark GRAY

        val descriptor = buildMinimalDescriptor(source, target, configMappings, globalConverters)
        if (descriptor == null) {                             // build failed — error already emitted
            inProgress -= id
            return
        }

        recurseIntoDependencies(
            descriptor, id, path, builtDescriptors, inProgress,
            configMappings, globalConverters, enumBackedIds,
        )

        inProgress -= id              // unmark GRAY
        builtDescriptors[id] = descriptor // mark BLACK
    }

    @Suppress("LongParameterList")
    private fun recurseIntoDependencies(
        descriptor: MapperDescriptor,
        currentId: MapperId,
        path: List<MapperId>,
        builtDescriptors: MutableMap<MapperId, MapperDescriptor>,
        inProgress: MutableSet<MapperId>,
        configMappings: List<ConfigObjectScanResult>,
        globalConverters: GlobalConverterRegistry,
        enumBackedIds: Set<MapperId> = emptySet(),
    ) {
        descriptor.propertyMappings
            .filterIsInstance<PropertyMappingStrategy.NestedMapper>()
            .filter { it.nestedMappingDescriptor.nestedMapperId !in enumBackedIds }
            .forEach { strategy ->
                resolveImplicit(
                    source = strategy.nestedMappingDescriptor.sourceType.declaration,
                    target = strategy.nestedMappingDescriptor.targetType.declaration,
                    path = path + currentId,
                    builtDescriptors = builtDescriptors,
                    inProgress = inProgress,
                    configMappings = configMappings,
                    globalConverters = globalConverters,
                    enumBackedIds = enumBackedIds,
                )
            }
    }

    private fun reportCycleError(path: List<MapperId>, id: MapperId, source: KSClassDeclaration) {
        val cycleStart = path.indexOf(id)
        val cyclePath = (if (cycleStart >= 0) path.drop(cycleStart) else path) + id
        logger.report(
            KraftDiagnostic.CIRCULAR_NESTED_MAPPING,
            "Circular nested mapping: ${cyclePath.joinToString(" → ")}. " +
                "Break the cycle with a @MapUsing converter on one side.",
            source
        )
    }

    private fun buildMinimalDescriptor(
        source: KSClassDeclaration,
        target: KSClassDeclaration,
        configMappings: List<ConfigObjectScanResult> = emptyList(),
        globalConverters: GlobalConverterRegistry = GlobalConverterRegistry.EMPTY
    ): MapperDescriptor? {
        val configsForThis = configMappings.filter {
            it.sourceType == source && it.targetType == target
        }
        val syntheticMapping = ClassMappingScanResult(
            direction = MappingDirection.MAP_FROM,
            sourceType = source,
            targetType = target,
            annotatedClass = target,
            propertyScanResults = emptyList()
        )
        return ClassDescriptorBuilder(
            logger = logger,
            mapping = syntheticMapping,
            configObjects = configsForThis,
            enumMappings = emptyList(),
            globalConverters = globalConverters
        ).build()
    }

    // ---------------------------
    // 4) Final validation pass
    // ---------------------------

    private fun validateNestedDependencies(
        builtDescriptors: Map<MapperId, MapperDescriptor>,
        enumBackedIds: Set<MapperId> = emptySet(),
    ) {
        for (descriptor in builtDescriptors.values) {
            descriptor.nestedDependencies
                .filter { it !in builtDescriptors && it !in enumBackedIds }
                .forEach { depId ->
                    logger.report(
                        KraftDiagnostic.NESTED_MAPPER_NOT_BUILT,
                        "Nested mapper for $depId could not be built; " +
                            "mapper for ${descriptor.id} will generate code that does not compile.",
                        descriptor.sourceType.declaration
                    )
                }
        }
    }
}
