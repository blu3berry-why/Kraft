package hu.nova.blu3berry.kraft.onclass.from

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class MapFrom(
    val value: KClass<*>,      // source type
    val config: KClass<*> = Unit::class // optional config object
)