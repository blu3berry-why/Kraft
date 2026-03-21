package hu.nova.blu3berry.kraft.mapping

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class MapFrom(
    val source: KClass<*>,
    val config: KClass<*> = Unit::class
)
