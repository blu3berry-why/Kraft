package hu.nova.blu3berry.kraft.config

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class EnumMap(
    val from: KClass<*>,
    val to: KClass<*>,
    val fieldMapping: Array<FieldOverride> = []
)