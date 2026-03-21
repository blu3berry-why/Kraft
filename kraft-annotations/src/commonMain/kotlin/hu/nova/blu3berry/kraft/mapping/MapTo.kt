package hu.nova.blu3berry.kraft.mapping

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class MapTo(
    val target: KClass<*>,
    val config: KClass<*> = Unit::class
)
