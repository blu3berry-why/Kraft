package hu.nova.blu3berry.kraft.mapping

/**
 * Triggers nested object mapping for this property.
 *
 * Kraft will look up (or auto-generate) a mapper for the property's type pair and
 * call it inline. Both the source and target property types must be concrete classes
 * with a primary constructor.
 *
 * @param sourceName The source property name to read the nested object from.
 *                   Leave empty (default) to use the same name as the annotated property.
 *
 * Example — same name on both sides:
 * ```
 * data class AddressSource(val street: String, val city: String)
 * data class Person(val name: String, val address: AddressSource)
 *
 * data class AddressDto(val street: String, val city: String)
 *
 * @MapFrom(Person::class)
 * data class PersonDto(
 *     val name: String,
 *     @MapNested   // reads Person.address, maps AddressSource → AddressDto
 *     val address: AddressDto
 * )
 * ```
 *
 * Example — source property has a different name:
 * ```
 * @MapFrom(Person::class)
 * data class PersonDto(
 *     val name: String,
 *     @MapNested(sourceName = "address")
 *     val location: AddressDto
 * )
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class MapNested(
    val sourceName: String = ""   // "" means: same property name as the target
)
