package com.blu3berry.kraft.config

import kotlin.reflect.KClass

/**
 * Declares an enum-to-enum mapping on a config object.
 *
 * Kraft generates a mapping function that converts every entry of [source] to the
 * corresponding entry in [target]. Entries with the same name are mapped automatically.
 * Entries with different names must be declared explicitly via [fieldMappings].
 * Every source entry must be covered — an unmapped entry is a compile-time KSP error.
 *
 * Both [source] and [target] must be enum classes.
 *
 * @param source The enum class to map **from**.
 * @param target The enum class to map **to**.
 * @param fieldMappings Explicit entry mappings for source entries whose name differs
 *                      from the target entry name. Each [@FieldMapping][FieldMapping]
 *                      maps a source entry name to a target entry name.
 *
 * Example — all entries share the same name (no fieldMappings needed):
 * ```
 * enum class Status { ACTIVE, INACTIVE }
 * enum class StatusDto { ACTIVE, INACTIVE }
 *
 * @MapEnum(source = Status::class, target = StatusDto::class)
 * object StatusMapping
 * ```
 *
 * Example — some entries have different names:
 * ```
 * enum class Status { ACTIVE, DISABLED }
 * enum class StatusDto { ACTIVE, INACTIVE }
 *
 * @MapEnum(
 *     source = Status::class,
 *     target = StatusDto::class,
 *     fieldMappings = [FieldMapping(source = "DISABLED", target = "INACTIVE")]
 * )
 * object StatusMapping
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class MapEnum(
    val source: KClass<*>,
    val target: KClass<*>,
    val fieldMappings: Array<FieldMapping> = []
)
