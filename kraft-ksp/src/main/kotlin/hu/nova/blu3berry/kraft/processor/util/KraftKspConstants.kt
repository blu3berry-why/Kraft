package hu.nova.blu3berry.kraft.processor.util

import hu.nova.blu3berry.kraft.config.FieldMapping
import hu.nova.blu3berry.kraft.config.MapConfig
import hu.nova.blu3berry.kraft.config.MapEnum
import hu.nova.blu3berry.kraft.config.MapIgnoreField
import hu.nova.blu3berry.kraft.config.MapReverse
import hu.nova.blu3berry.kraft.config.MapUsing
import hu.nova.blu3berry.kraft.config.NestedMapping
import hu.nova.blu3berry.kraft.mapping.MapField
import hu.nova.blu3berry.kraft.mapping.MapFrom
import hu.nova.blu3berry.kraft.mapping.MapIgnore
import hu.nova.blu3berry.kraft.mapping.MapNested
import hu.nova.blu3berry.kraft.mapping.MapTo

internal object KraftKspConstants {
    // Annotation argument names
    const val ARG_CLASS            = "class"
    const val ARG_NAME             = "name"
    const val ARG_OTHER_NAME       = "counterPartName"
    const val ARG_SOURCE_NAME      = "sourceName"
    const val ARG_SOURCE           = "source"
    const val ARG_TARGET           = "target"
    const val ARG_DIRECTION        = "direction"
    const val ARG_FIELD_MAPPINGS   = "fieldMappings"
    const val ARG_NESTED_MAPPINGS  = "nestedMappings"
    const val ARG_IGNORED_MAPPINGS = "ignoredMappings"
    const val ARG_OBJECT           = "object"

    // Annotation fully-qualified names
    val FQ_MAP_FROM         = MapFrom::class.qualifiedName!!
    val FQ_MAP_TO           = MapTo::class.qualifiedName!!
    val FQ_MAP_FIELD        = MapField::class.qualifiedName!!
    val FQ_MAP_NESTED       = MapNested::class.qualifiedName!!
    val FQ_MAP_IGNORE       = MapIgnore::class.qualifiedName!!
    val FQ_MAP_CONFIG       = MapConfig::class.qualifiedName!!
    val FQ_MAP_USING        = MapUsing::class.qualifiedName!!
    val FQ_MAP_IGNORE_FIELD = MapIgnoreField::class.qualifiedName!!
    val FQ_FIELD_MAPPING    = FieldMapping::class.qualifiedName!!
    val FQ_NESTED_MAPPING   = NestedMapping::class.qualifiedName!!
    val FQ_MAP_ENUM         = MapEnum::class.qualifiedName!!
    val FQ_MAP_REVERSE      = MapReverse::class.qualifiedName!!

    // Processor option keys
    const val OPTION_FUNCTION_NAME_FORMAT = "kraft.functionNameFormat"
}
