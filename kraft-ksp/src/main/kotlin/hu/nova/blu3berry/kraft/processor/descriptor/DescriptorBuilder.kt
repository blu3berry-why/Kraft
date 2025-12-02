package hu.nova.blu3berry.kraft.processor.descriptor

import com.google.devtools.ksp.processing.KSPLogger
import hu.nova.blu3berry.kraft.model.MapperDescriptor
import hu.nova.blu3berry.kraft.processor.scanner.ClassMappingScanResult
import hu.nova.blu3berry.kraft.processor.scanner.ConfigObjectScanResult
import hu.nova.blu3berry.kraft.model.MappingDirection

class DescriptorBuilder(
    private val logger: KSPLogger
) {

    fun build(
        classMappings: List<ClassMappingScanResult>,
        configMappings: List<ConfigObjectScanResult>
    ): List<MapperDescriptor> {

        val descriptors = mutableListOf<MapperDescriptor>()

        for (mapping in classMappings) {

            // 1) find matching config objects for this mapping
            val configsForThisMapping = configMappings.filter {
                it.fromType == mapping.sourceType &&
                it.toType == mapping.targetType
            }

            // 2) build descriptor
            val builder = ClassDescriptorBuilder(
                logger = logger,
                mapping = mapping,
                configObjects = configsForThisMapping
            )

            val descriptor = builder.build()
            if (descriptor != null) {
                descriptors += descriptor
            }
        }

        return descriptors
    }
}
