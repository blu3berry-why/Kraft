package hu.nova.blu3berry.kraft.onclass.to

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class MapTo(
    val value: KClass<*>,
    val config: KClass<*> = Unit::class
)