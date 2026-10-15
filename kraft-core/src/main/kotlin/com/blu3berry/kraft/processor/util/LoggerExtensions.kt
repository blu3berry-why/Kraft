@file:Suppress("TooManyFunctions")

package com.blu3berry.kraft.processor.util

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.blu3berry.kraft.processor.diagnostics.KraftDiagnostic
import com.blu3berry.kraft.processor.diagnostics.report
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.TypeInfo
import com.blu3berry.kraft.model.scan.FieldOverride

fun KSPLogger.annotationTargetError(
    annotationName: String,
    expectedTarget: String,
    actualNode: KSNode
) = report(
    KraftDiagnostic.ANNOTATION_TARGET_INVALID,
    """
    Incorrect use of @$annotationName.

    ✔ Expected annotation target: $expectedTarget
    ✘ Found: ${actualNode::class.simpleName}

    Why this is an error:
    @$annotationName is only valid on $expectedTarget declarations.

    How to fix:
    - Move @$annotationName onto a $expectedTarget.
    - Example:
          @$annotationName(SomeType::class)
          data class MyTarget(...)

    """.trimIndent(),
    actualNode
)

/**
 * Missing required annotation argument
 */
fun KSPLogger.missingAnnotationArgument(
    annotationName: String,
    argName: String,
    kind: ArgumentKind,
    symbol: KSNode,
    enumExample: String? = null
) = report(
    KraftDiagnostic.MISSING_ANNOTATION_ARGUMENT,
    """
    Missing required argument '$argName' in @$annotationName.

    ✔ Correct usage:
        @$annotationName($argName = ${when (kind) {
            ArgumentKind.CLASS -> "MyType::class"
            ArgumentKind.STRING -> "\"propertyName\""
            ArgumentKind.ENUM -> enumExample ?: "<enum entry>"
        }})

    ✘ Found: argument '$argName' was not provided.

    Fix:
    - Provide the missing '$argName' argument.
    """.trimIndent(),
    symbol
)

/**
 * Annotation argument is not a KClass
 */
fun KSPLogger.invalidKClassArgument(
    annotationName: String,
    argName: String,
    actualValue: Any?,
    symbol: KSNode
) = report(
    KraftDiagnostic.INVALID_KCLASS_ARGUMENT,
    """
    Invalid @$annotationName argument type for '$argName'.

    ✔ Expected: KClass<*>, e.g. MyType::class
    ✘ Found:    ${actualValue?.let { it::class.simpleName } ?: "null"}

    Fix:
    - Use a class literal like MyType::class
    """.trimIndent(),
    symbol
)

/**
 * More informative error: required target property cannot be mapped.
 */
fun KSPLogger.detailedMissingMapping(
    sourceType: String,
    targetType: String,
    targetProperty: PropertyInfo,
    sourceProperties: Map<String, PropertyInfo>,
    classLevelOverrides: Map<String, String>,
    configOverrides: List<FieldOverride>,
    symbol: KSNode
) = report(
    KraftDiagnostic.REQUIRED_PROPERTY_UNMAPPED,
    """
    Required property '${targetProperty.name}' in target type '$targetType'
    has no mapping source and is non-nullable.

    Source type: $sourceType

    Target property:
      • ${targetProperty.name}: ${targetProperty.type.ksType}

    Available source properties:
${sourceProperties.keys.joinToString("\n") { "      • $it" }}

    Class-level overrides (@MapField):
${
        if (classLevelOverrides.isEmpty()) "      (none)"
        else classLevelOverrides.entries.joinToString("\n") { "      • ${it.key} ← ${it.value}" }
    }

    Config-level overrides:
${
        if (configOverrides.isEmpty()) "      (none)"
        else configOverrides.joinToString("\n") { "      • ${it.target} ← ${it.source}" }
    }

    How to fix:
      ✓ Add @MapField("sourceName") to the target property '${targetProperty.name}'
      ✓ Or add a config override: FieldMapping(source = "sourceName", target = "${targetProperty.name}")
      ✓ Or make the property nullable
      ✓ Or provide a default value in the target constructor
    """.trimIndent(),
    symbol
)

/**
 * More informative type mismatch message.
 */
fun KSPLogger.detailedTypeMismatch(
    sourceType: String,
    targetType: String,
    sourceProperty: PropertyInfo,
    targetProperty: PropertyInfo,
    symbol: KSNode
) {
    var sourceShown = sourceProperty.type.ksType.toString()
    var targetShown = targetProperty.type.ksType.toString()
    // Same-simple-name pairs (DTO vs domain) render identically — or differ only
    // in nullability — and make the mismatch unreadable. Fall back to qualified
    // names when those actually disambiguate; for parameterized types (List<A>
    // vs List<B>) the type arguments carry the difference, so keep the short form.
    if (sourceProperty.type.simpleName == targetProperty.type.simpleName) {
        val sourceQualified = sourceProperty.type.qualifiedRendering()
        val targetQualified = targetProperty.type.qualifiedRendering()
        if (sourceQualified != targetQualified) {
            sourceShown = sourceQualified
            targetShown = targetQualified
        }
    }
    report(
        KraftDiagnostic.PROPERTY_TYPE_MISMATCH,
        """
    Type mismatch for property '${targetProperty.name}'.

    From source ($sourceType):
      • ${sourceProperty.name}: $sourceShown

    To target ($targetType):
      • ${targetProperty.name}: $targetShown

    Types must match exactly, or be bridged by a registered converter.

${typeMismatchGuidance(sourceProperty, targetProperty)}
        """.trimIndent(),
        symbol
    )
}

/**
 * Builds the "how to fix" half of [detailedTypeMismatch].
 *
 * A nullable source paired with a non-null target gets its own text: Kraft *does*
 * reuse a non-null `X → Y` converter for an `X? → Y?` pair (threaded through a safe
 * call), so a user who has that converter registered and still sees a mismatch is
 * almost always looking at the one pair that is deliberately never lifted. Saying
 * "align nullability" there hides the actual rule.
 */
private fun typeMismatchGuidance(
    sourceProperty: PropertyInfo,
    targetProperty: PropertyInfo
): String {
    val hint = duplicatedTypesHint(sourceProperty, targetProperty)
    val fix = fixGuidance(sourceProperty, targetProperty)
    val mapHint = mapValueNullabilityHint(sourceProperty, targetProperty)
    val hints = listOfNotNull(hint, mapHint).joinToString("\n\n")
    return if (hints.isEmpty()) fix else "$hints\n\n$fix"
}

/**
 * The hint for two maps whose source value is nullable and whose target value is not
 * (`Map<K, V?>` into `Map<K, V>`). A list drops null elements, but dropping a null map value
 * would drop its key as well.
 */
private fun mapValueNullabilityHint(sourceProperty: PropertyInfo, targetProperty: PropertyInfo): String? {
    val sourceValue = containerKindOf(sourceProperty.type)?.takeIf { it.kind.isKeyed }?.element
    val targetValue = containerKindOf(targetProperty.type)?.takeIf { it.kind.isKeyed }?.element
    if (sourceValue == null || targetValue == null) return null
    if (!sourceValue.isNullable || targetValue.isNullable) return null
    return "    Map values cannot be dropped like list elements: removing a null value would also remove its key."
}

private fun fixGuidance(
    sourceProperty: PropertyInfo,
    targetProperty: PropertyInfo
): String {
    val name = targetProperty.name
    // The fallback expression reads the SOURCE property; under a @MapField or
    // FieldMapping rename the two names differ, and using the target's name would
    // emit a snippet referencing a property the source class does not have.
    val readName = sourceProperty.name
    val src = sourceProperty.type.simpleName
    val tgt = targetProperty.type.simpleName

    val nullableSourceOnly = sourceProperty.type.isNullable && !targetProperty.type.isNullable

    // Same type on both sides, differing only by '?'. No converter is involved or
    // wanted here, so suggesting one ('$src → $src') would be noise.
    //
    // Compare the resolved types with nullability normalised rather than their
    // qualified names: `List<A>?` and `List<B>` share the qualified name
    // `kotlin.collections.List`, so a name comparison would claim "only nullability
    // differs" for a pair whose element types differ too.
    if (nullableSourceOnly && sourceProperty.type.ksType.makeNotNullable() == targetProperty.type.ksType) {
        return """
    Both sides are '$src'; only nullability differs. Kraft never drops a null into a
    non-null target, so this pair needs an explicit decision rather than a converter.

    How to fix:
      ✓ Make the target property '$name' nullable
      ✓ Or give '$name' a default in the target constructor and add @MapIgnore
      ✓ Or choose the fallback explicitly with a whole-source @MapUsing:
            @MapUsing(target = "$name") fun Source.${name}OrDefault(): $tgt = $readName ?: <default>
        """.trimIndent().prependIndent("    ")
    }

    if (nullableSourceOnly) {
        return """
    The source is nullable and the target is not. Kraft threads a registered
    '$src → $tgt' converter through a safe call only when BOTH sides are nullable
    ($src? → $tgt?). A nullable source with a non-null target is never lifted:
    the safe call would produce a null the target cannot hold, and unlike a
    List (which falls back to emptyList()) a scalar has no natural empty value.

    How to fix:
      ✓ Make the target property '$name' nullable — an existing '$src → $tgt'
        @KraftConverter or @MapEnum is then applied automatically
      ✓ Or supply the fallback yourself with a whole-source @MapUsing:
            @MapUsing(target = "$name") fun Source.${name}OrDefault(): $tgt = $readName ?: <default>
      ✓ Or declare a converter that accepts the nullable source:
            @KraftConverter fun $src?.to$tgt(): $tgt = ...
        """.trimIndent().prependIndent("    ")
    }

    return """
    How to fix:
      ✓ Register a @KraftConverter for this pair — one top-level extension covers
        every mapper in the module:
            @KraftConverter fun $src.to$tgt(): $tgt = ...
      ✓ Or, if both types are enums, add @MapEnum(source = $src::class, target = $tgt::class)
      ✓ Or override just this property with @MapUsing on the @MapConfig object
      ✓ Or align the declared types (including nullability)
    """.trimIndent().prependIndent("    ")
}

/**
 * Detects the "same type declared twice" shape: equal simple names, different
 * qualified names, at least one nested, and identical enum entries (enums) or
 * primary-constructor parameter names (classes). Typical of a code generator
 * inlining the same `$ref` into two places.
 */
private fun duplicatedTypesHint(
    sourceProperty: PropertyInfo,
    targetProperty: PropertyInfo
): String? {
    val (source, target) = duplicatedPair(sourceProperty, targetProperty) ?: return null
    val s = source.displayName()
    val t = target.displayName()

    val ref = "${'$'}ref"
    val sourceIsEnum = source.classKind == ClassKind.ENUM_CLASS
    val targetIsEnum = target.classKind == ClassKind.ENUM_CLASS
    val text = when {
        sourceIsEnum && targetIsEnum && enumEntryNames(source) == enumEntryNames(target) -> """
            Types differ only by enclosing class: $s vs $t, with identical entries.
            This is usually a code generator inlining the same $ref twice (e.g. kmpgen). Fix it in the generator, or bridge the pair with @MapEnum(source = $s::class, target = $t::class).
        """.trimIndent()
        !sourceIsEnum && !targetIsEnum && sameConstructorParameters(source, target) -> """
            Types differ only by enclosing class: $s vs $t, with identical constructor parameters.
            This is usually a code generator inlining the same $ref twice (e.g. kmpgen). Fix it in the generator, or bridge the pair with @MapConfig(source = $s::class, target = $t::class) or a @KraftConverter.
        """.trimIndent()
        else -> null
    }
    return text?.prependIndent("    ")
}

/** Both declarations when they have equal simple names, different qualified names and one is nested. */
private fun duplicatedPair(
    sourceProperty: PropertyInfo,
    targetProperty: PropertyInfo
): Pair<KSClassDeclaration, KSClassDeclaration>? {
    val source = sourceProperty.type.ksType.makeNotNullable().declaration as? KSClassDeclaration
    val target = targetProperty.type.ksType.makeNotNullable().declaration as? KSClassDeclaration
    if (source == null || target == null) return null
    val sameSimpleName = source.simpleName.asString() == target.simpleName.asString()
    val differentQualified = source.qualifiedName?.asString() != target.qualifiedName?.asString()
    val nested = source.parentDeclaration is KSClassDeclaration || target.parentDeclaration is KSClassDeclaration
    return if (sameSimpleName && differentQualified && nested) source to target else null
}

/** Qualified name without its package prefix, e.g. `AuthResponse.User.Role`. */
private fun KSClassDeclaration.displayName(): String =
    (qualifiedName?.asString() ?: simpleName.asString()).removePrefix(packageName.asString() + ".")

private fun enumEntryNames(declaration: KSClassDeclaration): Set<String> =
    declaration.declarations
        .filterIsInstance<KSClassDeclaration>()
        .filter { it.classKind == ClassKind.ENUM_ENTRY }
        .map { it.simpleName.asString() }
        .toSet()

private fun sameConstructorParameters(source: KSClassDeclaration, target: KSClassDeclaration): Boolean {
    val sourceParams = source.primaryConstructor?.parameters?.map { it.name?.asString() } ?: return false
    val targetParams = target.primaryConstructor?.parameters?.map { it.name?.asString() } ?: return false
    return sourceParams == targetParams
}

private fun TypeInfo.qualifiedRendering(): String =
    qualifiedName + if (isNullable) "?" else ""

/**
 * More informative @MapField override failure message.
 */
fun KSPLogger.invalidMapFieldOverride(
    sourceType: String,
    targetPropertyName: String,
    referencedSourceName: String,
    sourceProperties: Map<String, PropertyInfo>,
    symbol: KSNode
) {
    val available = sourceProperties.keys.toList()
    val suggestions = suggestNames(referencedSourceName, available)

    report(
        KraftDiagnostic.INVALID_MAP_FIELD_OVERRIDE,
        """
        Invalid @MapField override for '$targetPropertyName'.

        Referenced source property '$referencedSourceName' does not exist
        in source type '$sourceType'.

        Available source properties:
        ${available.joinToString("\n") { "      • $it" }}

        ${if (suggestions.isNotEmpty()) "Did you mean: ${suggestions.joinToString(", ")} ?" else ""}

        How to fix:
          ✓ Correct the @MapField name
          ✓ Or ensure the source class declares '$referencedSourceName'
        """.trimIndent(),
        symbol
    )
}

/**
 * Missing primary constructor error.
 */
fun KSPLogger.missingPrimaryConstructor(typeName: String, symbol: KSNode) = report(
    KraftDiagnostic.MISSING_PRIMARY_CONSTRUCTOR,
    """
    Type '$typeName' must declare a primary constructor.

    Why:
    AutoMapper needs to construct an instance of the target type.

    Fix:
    - Add a primary constructor:
        data class $typeName(...)
    """.trimIndent(),
    symbol
)

/**
 * Constructor parameter does not match any property.
 */
fun KSPLogger.missingConstructorProperty(
    typeName: String,
    parameterName: String,
    available: List<String>,
    symbol: KSNode
) {
    val suggestions = suggestNames(parameterName, available)

    report(
        KraftDiagnostic.CONSTRUCTOR_PARAMETER_WITHOUT_PROPERTY,
        """
        Constructor parameter '$parameterName' in '$typeName'
        has no corresponding property.

        Available properties:
        ${available.joinToString("\n") { "      • $it" }}

        ${if (suggestions.isNotEmpty()) "Did you mean: ${suggestions.joinToString(", ")} ?" else ""}

        Fix:
        - Add 'val $parameterName' or 'var $parameterName' to the class body.
        """.trimIndent(),
        symbol
    )
}

/**
 * Unsupported type in constructor.
 */
fun KSPLogger.unsupportedTypeInConstructor(
    typeName: String,
    parameterName: String,
    actualType: String,
    symbol: KSNode
) = report(
    KraftDiagnostic.UNSUPPORTED_CONSTRUCTOR_PARAMETER_TYPE,
    """
    Unsupported type for constructor parameter '$parameterName' in '$typeName'.

    Actual type:
      • $actualType

    Only class declarations are supported as mapping targets.

    Fix:
    - Ensure the parameter is a data-class-like type.
    """.trimIndent(),
    symbol
)

/**
 * Constructor mismatch: parameters vs properties count mismatch.
 */
fun KSPLogger.constructorPropertyMismatch(
    typeName: String,
    symbol: KSNode
) = report(
    KraftDiagnostic.CONSTRUCTOR_PROPERTY_MISMATCH,
    """
    Constructor parameters do not match declared properties in '$typeName'.

    This may happen if:
      - A constructor parameter is missing its 'val'/'var'
      - A delegated property is used
      - The class contains synthetic/unmappable properties

    Fix:
    - Ensure each constructor argument has a matching property.
    """.trimIndent(),
    symbol
)

/**
 * A source property's type cannot be resolved to a concrete class declaration.
 * Triggered when the type is a generic type parameter, a type alias, or any other
 * non-class KSDeclaration.
 */
fun KSPLogger.unsupportedSourcePropertyType(
    typeName: String,
    propName: String,
    ksTypeName: String,
    declarationKind: String,
    symbol: KSNode
) = report(
    KraftDiagnostic.UNSUPPORTED_SOURCE_PROPERTY_TYPE,
    """
    Property '$propName' on '$typeName' has a type AutoMapper cannot map.

      Property:         $propName
      Resolved type:    $ksTypeName
      Declaration kind: $declarationKind

      Why this is an error:
      AutoMapper can only map properties whose type resolves to a concrete
      class declaration. Generic type parameters (e.g. T), type aliases, and
      other non-class declarations are not supported as direct property types.

      How to fix:
      ✓ Use a concrete type for '$propName' in '$typeName'.
      ✓ Or handle this property with a @MapUsing converter function.
    """.trimIndent(),
    symbol
)

/**
 * One or more source enum entries have no mapping to the target enum.
 */
@Suppress("LongParameterList")
fun KSPLogger.unmappedEnumEntries(
    declaringClass: String,
    sourceQualifiedName: String,
    targetQualifiedName: String,
    fromSimpleName: String,
    toSimpleName: String,
    unmappedEntries: List<String>,
    customEntries: List<Pair<String, String>>,
    autoEntries: List<String>,
    availableTargetEntries: List<String>,
    symbol: KSNode
) {
    val maxLen = (unmappedEntries + customEntries.map { it.first } + autoEntries)
        .maxOfOrNull { it.length } ?: 0

    val unmappedLines = unmappedEntries
        .joinToString("\n") { "    ✘ $it" }

    val alreadyMappedLines = buildList {
        customEntries.forEach { (from, to) -> add("    ✔ ${from.padEnd(maxLen)}  →  $to  (custom)") }
        autoEntries.forEach { name -> add("    ✔ ${name.padEnd(maxLen)}  →  $name  (auto)") }
    }.joinToString("\n").ifEmpty { "    (none)" }

    val targetLines = availableTargetEntries
        .joinToString("\n") { "    • $it" }

    val snippetLines = unmappedEntries
        .joinToString("\n") { "            FieldMapping(source = \"$it\", target = \"???\")," }

    report(
        KraftDiagnostic.UNMAPPED_ENUM_ENTRIES,
        """
    @MapEnum on '$declaringClass' has unmapped source entries.

      Source: $sourceQualifiedName
      Target: $targetQualifiedName

      Unmapped entries (must be resolved):
$unmappedLines

      Already mapped:
$alreadyMappedLines

      Available target entries:
$targetLines

      Why this is an error:
      Every source entry must have a target. Without a full mapping a
      non-exhaustive 'when' expression would be generated and the
      Kotlin compiler would reject it.

      How to fix — add a FieldMapping for each unmapped entry:

        @MapEnum(
            source        = $fromSimpleName::class,
            target        = $toSimpleName::class,
            fieldMappings = [
$snippetLines
            ]
        )

      ✓ Or rename the target entry to match the source name for automatic 1:1 mapping.
        """.trimIndent(),
        symbol
    )
}

/**
 * @MapIgnore or @MapIgnoreField targets a property that has no default value.
 * Omitting it from the constructor call produces invalid code.
 */
fun KSPLogger.ignoredRequiredProperty(
    targetType: String,
    propertyName: String,
    symbol: KSNode
) = report(
    KraftDiagnostic.IGNORED_REQUIRED_PROPERTY,
    """
    @MapIgnore / @MapIgnoreField targets '$propertyName' in '$targetType',
    but the property has no default value.

    The generated constructor call will omit '$propertyName', producing
    code that does not compile.

    How to fix:
      ✓ Add a default value to '$propertyName' in the target constructor.
      ✓ Or remove the ignore declaration.
    """.trimIndent(),
    symbol
)

/**
 * Source property is nullable but the target property is non-null,
 * making the nested mapper call unsafe.
 */
fun KSPLogger.nullableNestedSource(
    propertyName: String,
    sourceTypeName: String,
    targetTypeName: String,
    symbol: KSNode
) = report(
    KraftDiagnostic.NULLABLE_NESTED_SOURCE,
    """
    Nullable source for nested property '$propertyName'.

    In source ($sourceTypeName):
      • $propertyName is nullable

    In target ($targetTypeName):
      • $propertyName is non-null

    A nullable source cannot be mapped to a non-null target because
    the generated call `this.$propertyName.toTarget()` would fail at
    runtime when the source value is null.

    How to fix:
      ✓ Mark the target property '$propertyName' as nullable.
      ✓ Or provide a @MapUsing converter that handles the null case.
    """.trimIndent(),
    symbol
)

/**
 * A nested mapping involves a type that is not a concrete mappable class
 * (e.g. an interface or a generic type parameter). For a collection property,
 * [typeName] names the element type that is not mappable.
 */
fun KSPLogger.nestedTypeNotMappable(
    propertyName: String,
    typeName: String,
    symbol: KSNode
) = report(
    KraftDiagnostic.NESTED_TYPE_NOT_MAPPABLE,
    """
    Cannot generate nested mapper for property '$propertyName': type '$typeName' is not a concrete class.

    Why:
    Nested mapping requires both the source and target types to be concrete
    classes with a primary constructor. Interfaces, abstract and sealed classes,
    and generic type parameters are not supported directly.
    For a collection property, the element type must itself be a mappable class.

    How to fix:
      ✓ Use a @MapUsing converter for this property.
      ✓ Or ensure the element type '$typeName' is a concrete mappable class.
      ✓ Or declare an explicit @MapConfig with a @NestedMapping for this pair.
    """.trimIndent(),
    symbol
)

/**
 * A collection property would be converted into a `Set` from a non-`Set` type,
 * which silently drops duplicate elements.
 *
 * @param sourceType The source container as shown to the user, e.g. `List<ItemSource>`.
 * @param targetType The target container as shown to the user, e.g. `Set<ItemDto>`.
 * @param listType   The `List` form of the target the user can switch to, e.g. `List<ItemDto>`.
 */
fun KSPLogger.lossyCollectionConversion(
    propertyName: String,
    sourceType: String,
    targetType: String,
    listType: String,
    symbol: KSNode
) = report(
    KraftDiagnostic.LOSSY_COLLECTION_CONVERSION,
    """
    Cannot map property '$propertyName': converting $sourceType to $targetType would drop duplicate elements.

    Why:
    A Set holds each element once, so building one from a List, Array or other
    collection that may contain duplicates would lose elements without a trace.
    Kraft only generates conversions that keep every element.

    How to fix:
      ✓ Change the target to $listType, or map it with @MapUsing and call .toSet() explicitly.
    """.trimIndent(),
    symbol
)

/**
 * Multiple @NestedMapping declarations in @MapConfig share the same target type,
 * making it impossible to unambiguously select one for the current target property.
 */
fun KSPLogger.ambiguousNestedDescriptors(
    targetTypeName: String,
    matchCount: Int,
    symbol: KSNode
) = report(
    KraftDiagnostic.AMBIGUOUS_NESTED_MAPPING,
    """
    Ambiguous @NestedMapping declarations: $matchCount entries target '$targetTypeName'.

    Why:
    Kraft cannot determine which @NestedMapping to use when multiple declarations
    share the same target type. The first match would be picked arbitrarily.

    How to fix:
      ✓ Remove duplicate @NestedMapping entries so only one targets '$targetTypeName'.
      ✓ Or annotate the target property with @MapNested to resolve ambiguity explicitly.
    """.trimIndent(),
    symbol
)

/**
 * Multiple source properties share the type required by an explicit @NestedMapping,
 * making it impossible to unambiguously pick the source property.
 */
fun KSPLogger.ambiguousNestedSourceProperty(
    sourceTypeName: String,
    nestedSourceType: String,
    matchingProps: List<String>,
    symbol: KSNode
) = report(
    KraftDiagnostic.AMBIGUOUS_NESTED_SOURCE_PROPERTY,
    """
    Ambiguous source property for @NestedMapping(source = $nestedSourceType, ...):
    ${matchingProps.size} properties of type '$nestedSourceType' exist in '$sourceTypeName'.

    Matching properties:
    ${matchingProps.joinToString("\n") { "  • $it" }}

    Why:
    Kraft cannot determine which property to use as the nested mapping source
    when multiple candidates share the same type.

    How to fix:
      ✓ Annotate the target property with @MapNested(sourceName = "propertyName")
        to resolve ambiguity explicitly.
    """.trimIndent(),
    symbol
)

/**
 * An explicit @NestedMapping declares a source type that has no corresponding
 * property in the source class.
 */
fun KSPLogger.nestedMappingSourceNotFound(
    sourceTypeName: String,
    nestedSourceType: String,
    nestedTargetType: String,
    symbol: KSNode
) = report(
    KraftDiagnostic.NESTED_MAPPING_SOURCE_NOT_FOUND,
    """
    @NestedMapping(source = $nestedSourceType, target = $nestedTargetType) declared in config
    but no property of type '$nestedSourceType' exists in source class '$sourceTypeName'.

    Why:
    The explicit @NestedMapping specifies a source type that has no matching
    property in '$sourceTypeName'.

    How to fix:
      ✓ Add a property of type '$nestedSourceType' to '$sourceTypeName'.
      ✓ Or remove the @NestedMapping declaration if it is no longer needed.
    """.trimIndent(),
    symbol
)

private fun suggestNames(target: String, candidates: Collection<String>): List<String> =
    candidates
        .map { it to levenshtein(target, it) }
        .filter { (_, dist) -> dist <= 2 }
        .sortedBy { it.second }
        .map { it.first }

private fun levenshtein(a: String, b: String): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }

    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j

    for (i in 1..a.length) {
        for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) {
                dp[i - 1][j - 1]
            } else {
                1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
            }
        }
    }

    return dp[a.length][b.length]
}

