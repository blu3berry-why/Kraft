package com.blu3berry.kraft.processor.diagnostics

/**
 * The catalogue of every diagnostic Kraft's processor can emit.
 *
 * Each entry has a stable [code] (`KRAFT-E###` for errors, `KRAFT-W###` for warnings)
 * that users grep for, suppress with `@Suppress("<code>")`, and look up on the errors
 * reference page (`docs/user-guide/errors.md`, anchor [anchor]). A jvmTest fails when an
 * entry has no heading on that page, or the page has a heading with no entry here.
 *
 * Append only; never renumber; retired codes stay as @Deprecated entries.
 * A released code keeps meaning the same failure condition forever.
 */
enum class KraftDiagnostic(
    val code: String,
    val severity: Severity,
    val title: String,
) {
    /** A property-source `@MapUsing` converter duplicates a mapping Kraft would generate natively. */
    REDUNDANT_MAP_USING("KRAFT-W001", Severity.WARNING, "Redundant @MapUsing"),

    /** A same-module `S? -> T?` `@KraftConverter` duplicates the safe call Kraft derives from `S -> T`. */
    NULLABLE_TWIN_CONVERTER("KRAFT-W002", Severity.WARNING, "Nullable-twin @KraftConverter"),

    /** A Kraft annotation is applied to a declaration kind it does not support. */
    ANNOTATION_TARGET_INVALID("KRAFT-E001", Severity.ERROR, "Annotation on the wrong target"),

    /** A required annotation argument was not provided. */
    MISSING_ANNOTATION_ARGUMENT("KRAFT-E002", Severity.ERROR, "Missing annotation argument"),

    /** An annotation argument that must be a `KClass` has another type. */
    INVALID_KCLASS_ARGUMENT("KRAFT-E003", Severity.ERROR, "Argument is not a class literal"),

    /** A non-nullable target property without a default has no source to map from. */
    REQUIRED_PROPERTY_UNMAPPED("KRAFT-E004", Severity.ERROR, "Required property has no mapping source"),

    /** Source and target property types differ and no converter bridges them. */
    PROPERTY_TYPE_MISMATCH("KRAFT-E005", Severity.ERROR, "Property type mismatch"),

    /** `@MapField` names a source property that does not exist. */
    INVALID_MAP_FIELD_OVERRIDE("KRAFT-E006", Severity.ERROR, "Invalid @MapField override"),

    /** A mapping target declares no primary constructor. */
    MISSING_PRIMARY_CONSTRUCTOR("KRAFT-E007", Severity.ERROR, "Missing primary constructor"),

    /** A constructor parameter has no matching property. */
    CONSTRUCTOR_PARAMETER_WITHOUT_PROPERTY("KRAFT-E008", Severity.ERROR, "Constructor parameter without property"),

    /** A constructor parameter type is not a class declaration. */
    UNSUPPORTED_CONSTRUCTOR_PARAMETER_TYPE("KRAFT-E009", Severity.ERROR, "Unsupported constructor parameter type"),

    /** Constructor parameters and declared properties do not line up. */
    CONSTRUCTOR_PROPERTY_MISMATCH("KRAFT-E010", Severity.ERROR, "Constructor and property mismatch"),

    /** A source property type does not resolve to a concrete class. */
    UNSUPPORTED_SOURCE_PROPERTY_TYPE("KRAFT-E011", Severity.ERROR, "Unsupported source property type"),

    /** A `@MapEnum` source enum has entries with no target entry. */
    UNMAPPED_ENUM_ENTRIES("KRAFT-E012", Severity.ERROR, "Unmapped enum entries"),

    /** `@MapIgnore` or `@MapIgnoreField` targets a property with no default value. */
    IGNORED_REQUIRED_PROPERTY("KRAFT-E013", Severity.ERROR, "Ignored property has no default"),

    /** A nullable nested source maps to a non-null nested target. */
    NULLABLE_NESTED_SOURCE("KRAFT-E014", Severity.ERROR, "Nullable nested source"),

    /** A nested mapping involves a type that is not a concrete mappable class. */
    NESTED_TYPE_NOT_MAPPABLE("KRAFT-E015", Severity.ERROR, "Nested type not mappable"),

    /** Several `@NestedMapping` declarations share one target type. */
    AMBIGUOUS_NESTED_MAPPING("KRAFT-E016", Severity.ERROR, "Ambiguous nested mapping"),

    /** Several source properties match a `@NestedMapping` source type. */
    AMBIGUOUS_NESTED_SOURCE_PROPERTY("KRAFT-E017", Severity.ERROR, "Ambiguous nested source property"),

    /** A `@NestedMapping` source type has no matching source property. */
    NESTED_MAPPING_SOURCE_NOT_FOUND("KRAFT-E018", Severity.ERROR, "Nested mapping source not found"),

    /** Several `@MapConfig` objects declare the same source and target pair. */
    DUPLICATE_MAPPING("KRAFT-E019", Severity.ERROR, "Duplicate mapping"),

    /** Nested mappings form a cycle. */
    CIRCULAR_NESTED_MAPPING("KRAFT-E020", Severity.ERROR, "Circular nested mapping"),

    /** A nested mapper a descriptor depends on failed to build. */
    NESTED_MAPPER_NOT_BUILT("KRAFT-E021", Severity.ERROR, "Nested mapper could not be built"),

    /** `@MapIgnoreField` names a property missing from the target constructor. */
    IGNORE_FIELD_NOT_FOUND("KRAFT-E022", Severity.ERROR, "@MapIgnoreField property not found"),

    /** A reversed mapping uses a forward converter with no reverse counterpart. */
    MISSING_REVERSE_CONVERTER("KRAFT-E023", Severity.ERROR, "Missing reverse converter"),

    /** A config-level field override names an unknown source property. */
    CONFIG_OVERRIDE_UNKNOWN_PROPERTY("KRAFT-E024", Severity.ERROR, "Config override of unknown property"),

    /** More than one `@MapUsing` converter targets the same property. */
    DUPLICATE_MAP_USING_TARGET("KRAFT-E025", Severity.ERROR, "Multiple @MapUsing converters for one property"),

    /** `@MapUsing` names a source or target property that does not exist. */
    MAP_USING_UNKNOWN_PROPERTY("KRAFT-E026", Severity.ERROR, "Unknown property in @MapUsing"),

    /** `@MapNested` resolves to a source property that does not exist. */
    MAP_NESTED_SOURCE_NOT_FOUND("KRAFT-E027", Severity.ERROR, "@MapNested source property not found"),

    /** A class carries both `@MapFrom` and `@MapTo`. */
    MAP_FROM_AND_MAP_TO_CONFLICT("KRAFT-E028", Severity.ERROR, "@MapFrom and @MapTo on one class"),

    /** `@MapReverse` is not paired with a mapping annotation. */
    MAP_REVERSE_WITHOUT_MAPPING("KRAFT-E029", Severity.ERROR, "@MapReverse without a mapping annotation"),

    /** Two dependencies register a `@KraftConverter` for the same type pair. */
    AMBIGUOUS_CLASSPATH_CONVERTER("KRAFT-E030", Severity.ERROR, "Ambiguous classpath converter"),

    /** A reverse-direction `@MapUsing` sits in a config that is not reversed. */
    MAP_USING_REVERSE_WITHOUT_MAP_REVERSE("KRAFT-E031", Severity.ERROR, "@MapUsing REVERSE without @MapReverse"),

    /** A validated `@MapUsing` property was missing from Kraft's own lookup. */
    MAP_USING_INTERNAL_LOOKUP_FAILED("KRAFT-E032", Severity.ERROR, "@MapUsing internal lookup failure"),

    /** `@MapUsing` has an empty `target` value. */
    MAP_USING_BLANK_TARGET("KRAFT-E033", Severity.ERROR, "@MapUsing without a target"),

    /** `@MapUsing` has a direction value Kraft does not recognise. */
    MAP_USING_UNKNOWN_DIRECTION("KRAFT-E034", Severity.ERROR, "@MapUsing unknown direction"),

    /** A whole-source `@MapUsing` takes a type other than the source class. */
    WHOLE_SOURCE_PARAMETER_MISMATCH("KRAFT-E035", Severity.ERROR, "Whole-source converter parameter mismatch"),

    /** A `@MapUsing` function returns a type other than the target property type. */
    MAP_USING_RETURN_TYPE_MISMATCH("KRAFT-E036", Severity.ERROR, "@MapUsing return type mismatch"),

    /** A `@MapUsing` extension function declares value parameters. */
    MAP_USING_EXTENSION_HAS_PARAMETERS("KRAFT-E037", Severity.ERROR, "@MapUsing extension with parameters"),

    /** A `@MapUsing` function is neither an extension nor single-parameter. */
    MAP_USING_INVALID_SIGNATURE("KRAFT-E038", Severity.ERROR, "Invalid @MapUsing signature"),

    /** A `@MapUsing` function declares no return type. */
    MAP_USING_MISSING_RETURN_TYPE("KRAFT-E039", Severity.ERROR, "@MapUsing without a return type"),

    /** A `@MapUsing` parameter or return type is not a class. */
    MAP_USING_TYPE_NOT_CLASS("KRAFT-E040", Severity.ERROR, "@MapUsing type is not a class"),

    /** A `@MapUsing` parameter type differs from the source property type. */
    MAP_USING_PARAMETER_MISMATCH("KRAFT-E041", Severity.ERROR, "@MapUsing parameter type mismatch"),

    /** `@MapIgnoreField` has a blank `name`. */
    MAP_IGNORE_FIELD_BLANK_NAME("KRAFT-E042", Severity.ERROR, "@MapIgnoreField with a blank name"),

    /** `@MapIgnoreField` has a direction value Kraft does not recognise. */
    MAP_IGNORE_FIELD_UNKNOWN_DIRECTION("KRAFT-E043", Severity.ERROR, "@MapIgnoreField unknown direction"),

    /** A `@MapEnum` source or target is not an enum class. */
    MAP_ENUM_NOT_ENUM_CLASS("KRAFT-E044", Severity.ERROR, "@MapEnum type is not an enum"),

    /** A source enum entry appears twice in `fieldMappings`. */
    MAP_ENUM_DUPLICATE_SOURCE("KRAFT-E045", Severity.ERROR, "@MapEnum duplicate source entry"),

    /** A `@FieldMapping` in `@MapEnum` lacks a String `source` or `target`. */
    MAP_ENUM_MALFORMED_FIELD_MAPPING("KRAFT-E046", Severity.ERROR, "@MapEnum malformed field mapping"),

    /** A `@FieldMapping` source is not an entry of the source enum. */
    MAP_ENUM_UNKNOWN_SOURCE_ENTRY("KRAFT-E047", Severity.ERROR, "@MapEnum unknown source entry"),

    /** A `@FieldMapping` target is not an entry of the target enum. */
    MAP_ENUM_UNKNOWN_TARGET_ENTRY("KRAFT-E048", Severity.ERROR, "@MapEnum unknown target entry"),

    /** A `@KraftConverter` receiver or return type has type arguments. */
    CONVERTER_PARAMETERIZED_TYPE("KRAFT-E049", Severity.ERROR, "@KraftConverter with generic types"),

    /** Two same-module `@KraftConverter` functions register the same type pair. */
    AMBIGUOUS_KRAFT_CONVERTER("KRAFT-E050", Severity.ERROR, "Ambiguous @KraftConverter"),

    /** A `@KraftConverter` function is not a top-level declaration. */
    CONVERTER_NOT_TOP_LEVEL("KRAFT-E051", Severity.ERROR, "@KraftConverter not top-level"),

    /** A `@KraftConverter` function has no receiver. */
    CONVERTER_NOT_EXTENSION("KRAFT-E052", Severity.ERROR, "@KraftConverter not an extension"),

    /** A `@KraftConverter` function declares value parameters. */
    CONVERTER_HAS_PARAMETERS("KRAFT-E053", Severity.ERROR, "@KraftConverter with parameters"),

    /** A `@KraftConverter` function declares no return type. */
    CONVERTER_MISSING_RETURN_TYPE("KRAFT-E054", Severity.ERROR, "@KraftConverter without a return type"),

    /** An annotation argument that must be a String has another type. */
    ANNOTATION_ARGUMENT_NOT_STRING("KRAFT-E055", Severity.ERROR, "Annotation argument is not a String"),

    /** An annotation argument that must be an enum value has another type. */
    ANNOTATION_ARGUMENT_NOT_ENUM("KRAFT-E056", Severity.ERROR, "Annotation argument is not an enum value"),

    /** An annotation argument that must be an array has another type. */
    ANNOTATION_ARGUMENT_NOT_ARRAY("KRAFT-E057", Severity.ERROR, "Annotation argument is not an array"),

    /** The `kraft { }` side options are invalid. */
    SIDE_CONFIGURATION_ERROR("KRAFT-E058", Severity.ERROR, "Invalid side configuration"),

    /** Two converter type pairs derive the same generated delegate name. */
    DELEGATE_NAME_COLLISION("KRAFT-E059", Severity.ERROR, "Delegate name collision"),

    /** Several `@MapEnum` declarations register the same type pair. */
    AMBIGUOUS_MAP_ENUM("KRAFT-E060", Severity.ERROR, "Ambiguous @MapEnum"),

    /** A `@KraftConverter` and a `@MapEnum` register the same type pair. */
    ENUM_CONVERTER_CONFLICT("KRAFT-E061", Severity.ERROR, "Enum converter conflict"),

    /** Two mappers generate the same side alias name. */
    ALIAS_COLLISION("KRAFT-E062", Severity.ERROR, "Alias collision"),

    /** A collection would be converted into a `Set` from a non-`Set`, silently dropping duplicate elements. */
    LOSSY_COLLECTION_CONVERSION("KRAFT-E063", Severity.ERROR, "Lossy collection conversion"),

    /** `Map` keys would be mapped by an operation Kraft cannot prove one-to-one, which could merge entries. */
    NON_INJECTIVE_MAP_KEY("KRAFT-E064", Severity.ERROR, "Map key mapping can merge entries"),

    /** A `BOTH`-direction `@MapIgnoreField` names a property missing from one direction's target. */
    IGNORE_FIELD_NOT_FOUND_BOTH("KRAFT-W003", Severity.WARNING, "@MapIgnoreField(BOTH) property not found"),

    /** `@MapNested` and `@MapField` are both on one property. */
    MAP_NESTED_OVERRIDES_MAP_FIELD("KRAFT-W004", Severity.WARNING, "@MapNested overrides @MapField"),

    /** `@MapNested(sourceName)` equals the property name. */
    REDUNDANT_MAP_NESTED_SOURCE_NAME("KRAFT-W005", Severity.WARNING, "Redundant @MapNested sourceName"),

    /** Several `MapperGeneratorProvider` services are on the processor classpath. */
    MULTIPLE_MAPPER_GENERATOR_PROVIDERS("KRAFT-W006", Severity.WARNING, "Multiple mapper generator providers"),

    /** Several `EnumMapperGeneratorProvider` services are on the processor classpath. */
    MULTIPLE_ENUM_GENERATOR_PROVIDERS("KRAFT-W007", Severity.WARNING, "Multiple enum generator providers"),

    /** A mapper was skipped because no originating file was found. */
    MAPPER_GENERATION_SKIPPED("KRAFT-W008", Severity.WARNING, "Mapper generation skipped"),

    /** An unmapped nullable target property with no default receives `null`. */
    NULLABLE_TARGET_DEFAULTED_TO_NULL("KRAFT-W009", Severity.WARNING, "Nullable property set to null"),
    ;

    /** The heading anchor on the errors reference page, e.g. `kraft-w001`. */
    val anchor: String get() = code.lowercase()

    /** The full link to this diagnostic's entry on the errors reference page. */
    val docsUrl: String get() = "$ERRORS_PAGE_URL#$anchor"

    companion object {
        /** Published location of `docs/user-guide/errors.md`. */
        const val ERRORS_PAGE_URL = "https://blu3berry-why.github.io/Kraft/user-guide/errors/"
    }
}
