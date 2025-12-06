package hu.nova.blu3berry.kraft.config

import kotlin.reflect.KClass

annotation class NestedMapping(
    val from: KClass<*>,
    val to: KClass<*>
)