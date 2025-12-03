package hu.nova.blu3berry.kraft.processor.util

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.processor.scanner.FieldOverride

/**
 * Base error format with code-like block.
 */
private fun KSPLogger.err(message: String, symbol: KSNode) {
    error(
        """
----------------------------------------
AutoMapper KSP Error
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
 * A @MapField refers to a property that does not exist
 */
fun KSPLogger.noSuchProperty(
    typeName: String,
    propertyName: String,
    available: List<String>,
    symbol: KSNode
) {
    val suggestions = suggestNames(propertyName, available)

    err(
        """
        Property '$propertyName' does not exist on type '$typeName'.

        Available properties:
        ${available.joinToString("\n") { " - $it" }}

        ${if (suggestions.isNotEmpty()) "Did you mean: ${suggestions.joinToString(", ")} ?" else ""}

        Fix:
        - Check the spelling of the property.
        - Update your @MapField or @MapFieldOverride accordingly.
        """.trimIndent(),
        symbol
    )
}

/**
 * Unmapped non-nullable property in target class
 */
fun KSPLogger.unmappedNonNullableProperty(
    targetType: String,
    propertyName: String,
    symbol: KSNode
) = err(
    """
    Property '$propertyName' in target type '$targetType' is non-nullable 
    but no mapping was provided.

    Fix:
    - Add @MapField(other = "sourceName")
    - Or add @MapUsing with a converter
    - Or make '$propertyName' nullable
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
        else configOverrides.joinToString("\n") { "      • ${it.to} ← ${it.from}" }
    }

    How to fix:
      ✓ Add @MapField("sourceName") to the target property '${targetProperty.name}'
      ✓ Or add a config override: FieldOverride(from = "sourceName", to = "${targetProperty.name}")
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
) = err(
    """
    Type mismatch for property '${targetProperty.name}'.

    From source ($sourceType):
      • ${sourceProperty.name}: ${sourceProperty.type.ksType}

    To target ($targetType):
      • ${targetProperty.name}: ${targetProperty.type.ksType}

    Types must match exactly.

    How to fix:
      ✓ Align nullability in both types
      ✓ Use @MapUsing with a converter
      ✓ Ensure both types are compatible
    """.trimIndent(),
    symbol
)

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

