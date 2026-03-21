package hu.nova.blu3berry.kraft.config

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class MapEnum(
    val from: KClass<*>,
    val to: KClass<*>,
    val fieldMappings: Array<FieldOverride> = []
)
