package com.blu3berry.kraft.config

import kotlin.reflect.KClass

/**
 * Registers an explicit nested-object mapper inside [@MapConfig.nestedMappings][MapConfig.nestedMappings].
 *
 * Use this when the source and target classes each contain a nested property whose
 * types differ and no [@MapFrom][com.blu3berry.kraft.mapping.MapFrom] /
 * [@MapTo][com.blu3berry.kraft.mapping.MapTo] annotation is present on those
 * nested types. Kraft will generate (or reuse) a mapper for the [source] → [target]
 * type pair and call it for every matching nested property.
 *
 * @param source The nested object type on the **source** side.
 * @param target The nested object type on the **target** side.
 *
 * Example:
 * ```
 * @MapConfig(
 *     source = Order::class,
 *     target = OrderDto::class,
 *     nestedMappings = [NestedMapping(source = Address::class, target = AddressDto::class)]
 * )
 * object OrderMappingConfig
 * ```
 */
annotation class NestedMapping(
    val source: KClass<*>,
    val target: KClass<*>
)
