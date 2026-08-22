@file:Suppress("TooManyFunctions")

package com.blu3berry.kraft.processor.util

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.TypeInfo
import com.blu3berry.kraft.model.scan.FieldOverride

/**
 * Base error format with code-like block.
 */
private fun KSPLogger.err(message: String, symbol: KSNode) {
    error(
        """
----------------------------------------
Kraft KSP Error
----------------------------------------

$message

----------------------------------------
""".trimIndent(),
        symbol
    )
}

fun KSPLogger.annotationTargetError(
    annotationName: String,
    expectedTarget: String,
    actualNode: KSNode
) = err(
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
    symbol: KSNode
) = err(
    """
    Missing required argument '$argName' in @$annotationName.

    ✔ Correct usage:
        @$annotationName($argName = MyClass::class)

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
) = err(
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
) = err(
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
    err(
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

    err(
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
fun KSPLogger.missingPrimaryConstructor(typeName: String, symbol: KSNode) = err(
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

    err(
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
) = err(
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
) = err(
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
) = err(
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

    err(
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
) = err(
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
) = err(
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
 * @MapNested used on a property whose type is not a concrete mappable class
 * (e.g. interface, generic type parameter, collection).
 */
fun KSPLogger.nestedTypeNotMappable(
    propertyName: String,
    typeName: String,
    symbol: KSNode
) = err(
    """
    Cannot generate nested mapper for property '$propertyName': type '$typeName' is not a concrete class.

    Why:
    Nested mapping requires both the source and target types to be concrete
    classes with a primary constructor. Interfaces, generic type parameters,
    and non-List collections are not supported directly.
    For List<T> properties, T must itself be a mappable class.

    How to fix:
      ✓ Use a @MapUsing converter for this property.
      ✓ Or ensure the List element type is a concrete mappable class.
      ✓ Or declare an explicit @MapConfig with a @NestedMapping for this pair.
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
) = err(
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
) = err(
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
) = err(
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

