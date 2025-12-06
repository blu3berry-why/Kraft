import com.google.devtools.ksp.processing.KSPLogger
import hu.nova.blu3berry.kraft.model.EnumMappingDescriptor
import hu.nova.blu3berry.kraft.model.MapperDescriptor
import hu.nova.blu3berry.kraft.processor.descriptor.ConfigDescriptorBuilder
import hu.nova.blu3berry.kraft.model.ClassMappingScanResult
import hu.nova.blu3berry.kraft.model.ConfigObjectScanResult

class DescriptorBuilder(
    private val logger: KSPLogger
) {

    fun build(
        classMappings: List<ClassMappingScanResult>,
        configMappings: List<ConfigObjectScanResult>,
        enumMappings: List<EnumMappingDescriptor>,
    ): List<MapperDescriptor> {

        val descriptors = mutableListOf<MapperDescriptor>()

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

            val builder = ClassDescriptorBuilder(
                logger,
                mapping,
                configsForThis,
                enumsForThis
            )

            builder.build()?.let { descriptors += it }
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

                val builder = ConfigDescriptorBuilder(
                    logger = logger,
                    config = config,
                    enumMappings = enumsForThis
                )

                builder.build()?.let { descriptors += it }
            }
        }

        return descriptors
    }
}
