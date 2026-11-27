# Spec Delta

## ADDED Requirements

### Requirement: Type mismatch lists converters touching the failing types
The type-mismatch error SHALL list every converter the processor knows whose source type matches the source property's type, or whose target type matches the target property's type, ignoring nullability. For collections and maps, the element, key and value types count too. Each line SHALL give the pair, the function and the origin. At most 10 lines are listed, followed by a count of the rest.

#### Scenario: Converter with the right source but the wrong target
- **WHEN** a module in package `app` declares `@KraftConverter fun Money.toCents(): Long`, `data class A(val price: Money)` and `@MapFrom(A::class) data class B(val price: String)`
- **THEN** compilation fails with `[KRAFT-E005]` for `price`, and the error contains `Registered converters touching these types:` followed by the line `• app.Money → kotlin.Long (app.toCents, same-module)`

#### Scenario: Converter for the element type of a list
- **WHEN** the module declares `@KraftConverter fun Money.toCents(): Long`, `data class A(val prices: List<Money>)` and `@MapFrom(A::class) data class B(val prices: List<String>)`
- **THEN** the type-mismatch error for `prices` contains `• app.Money → kotlin.Long (app.toCents, same-module)`

#### Scenario: Unrelated converters are not listed
- **WHEN** the module declares `@KraftConverter fun Tag.toLabel(): String` and the mismatch is `price: Money` to `price: Int`
- **THEN** the error does not contain `app.toLabel`

#### Scenario: Classpath delegate is named with its origin
- **WHEN** an upstream module in package `core` declares `@KraftConverter fun Money.toCents(): Long`, and the consumer maps `data class A(val price: Money, val cost: Money)` to `@MapFrom(A::class) data class B(val price: Long, val cost: String)`, so `price` resolves the delegate before `cost` fails
- **THEN** the error lists the `core.Money → kotlin.Long` converter with origin `classpath-delegate`

### Requirement: Type mismatch says when no converter touches the types
When no known converter matches, the type-mismatch error SHALL state that and point at the report option.

#### Scenario: No converters at all
- **WHEN** a module declares `data class A(val id: Int)` and `@MapFrom(A::class) data class B(val id: String)`, with no converters
- **THEN** the error contains `No registered converter has kotlin.Int as its source or kotlin.String as its target.` and `Set the KSP option kraft.report=true to list every converter Kraft registered.`
