package hu.nova.blu3berry.kraft.config

import kotlin.reflect.KClass

/**
 * Declares a mapping configuration on a companion or standalone object.
 *
 * Use this when you cannot (or prefer not to) annotate the source/target classes
 * directly with [@MapFrom][hu.nova.blu3berry.kraft.mapping.MapFrom] /
 * [@MapTo][hu.nova.blu3berry.kraft.mapping.MapTo].
 *
 * @param source The class to map **from**.
 * @param target The class to map **to**.
 * @param fieldMappings Explicit property renames: each [@FieldMapping][FieldMapping]
 *                      entry maps a source property name to a target property name.
 * @param nestedMappings Explicit nested-object mapper declarations: each
 *                       [@NestedMapping][NestedMapping] entry registers a mapper for
 *                       a source/target type pair used as a nested property.
 * @param ignoredMappings Target constructor parameters to skip: each
 *                        [@MapIgnoreField][MapIgnoreField] entry names a parameter
 *                        that will be omitted from the generated constructor call
 *                        (the parameter must declare a default value).
 *
 * Example:
 * ```
 * @MapConfig(
 *     source = User::class,
 *     target = UserDto::class,
 *     fieldMappings   = [FieldMapping(source = "userId", target = "id")],
 *     ignoredMappings = [MapIgnoreField("internalNotes")],
 * )
 * object UserMappingConfig
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class MapConfig(
    val source: KClass<*>,
    val target: KClass<*>,
    val fieldMappings: Array<FieldMapping> = [],
    val nestedMappings: Array<NestedMapping> = [],
    val ignoredMappings: Array<MapIgnoreField> = [],
)
