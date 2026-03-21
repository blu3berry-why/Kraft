# Ignore — Developer Documentation

> Implemented in T-10 on branch `ignore-rule`.
> Test suites:
> - `kraft-ksp/src/jvmTest/kotlin/hu/nova/blu3berry/kraft/mapignore/`
> - `kraft-ksp/src/jvmTest/kotlin/hu/nova/blu3berry/kraft/configignore/`

---

## 1. Overview

The ignore feature allows a target constructor parameter to be skipped during mapping. The
generator omits the parameter from the constructor call entirely, so the class must supply
a default value for that parameter — otherwise the generated code will not compile (a KSP
error for this case is a known future improvement; see §7).

There are two independent ways to declare an ignore, which are unified into a single set
before the rule chain runs:

| Path | Annotation | Where it lives |
|---|---|---|
| **Class-level** | `@MapIgnore` | On a property in the `@MapFrom`/`@MapTo`-annotated class |
| **Config-level** | `@IgnoreField` inside `@MapConfig.ignoredMappings` | On the mapping config object |

Both paths feed into `MappingContext.classIgnoredProperties: Set<String>`, which
`IgnoreRule` consumes. There is no separate code path per source.

---

## 2. Annotation Design

### `@MapIgnore`

```kotlin
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class MapIgnore
```

Placed directly on a property in the class that carries `@MapFrom` or `@MapTo`. No
parameters — the annotation is a pure marker.

**`@MapFrom` semantics** — the annotated class is the **target**. The annotated property
name is a target constructor parameter name. `IgnoreRule` matches it directly.

**`@MapTo` semantics** — the annotated class is the **source**. The annotated property name
is a *source* property name. `IgnoreRule` matches it against *target* constructor parameter
names. This works only when the source and target share the same property name. See §5 for
the full asymmetry explanation.

---

### `@IgnoreField`

```kotlin
annotation class IgnoreField(
    val name: String,
    val direction: IgnoreDirection = IgnoreDirection.BOTH
)
```

Used inside `@MapConfig.ignoredMappings`. `name` is always the **target-side** constructor
parameter name for the direction being generated:
- `FORWARD` — the `to`-class parameter name.
- `REVERSE` — the `from`-class parameter name (reserved; not applied yet).
- `BOTH` — applied wherever the name exists in each direction's target constructor.

---

### `IgnoreDirection`

```kotlin
enum class IgnoreDirection { FORWARD, REVERSE, BOTH }
```

| Value | Behaviour today |
|---|---|
| `FORWARD` | Applied. Unknown name emits a KSP error. |
| `REVERSE` | Parsed and stored; **not applied** (reverse generation not yet implemented). |
| `BOTH` | Applied if the name exists in the forward target constructor; silently skipped if it does not (may be valid for the future reverse direction). |

---

### `@MapConfig.ignoredMappings`

```kotlin
annotation class MapConfig(
    val from: KClass<*>,
    val to: KClass<*>,
    val fieldMappings: Array<FieldOverride> = [],
    val nestedMappings: Array<NestedMapping> = [],
    val ignoredMappings: Array<IgnoreField> = [],
)
```

`ignoredMappings` is an array of `@IgnoreField` entries. Multiple entries are supported;
each is processed independently.

---

## 3. Data Flow

```
ClassAnnotationScanner                    ConfigObjectScanner
  ↓ scans @MapIgnore on properties          ↓ scans @IgnoreField in @MapConfig
  PropertyScanResult.isIgnored = true       IgnoredMappingConfig(name, direction)
                                            stored in ConfigObjectScanResult.ignoredMappings

ClassDescriptorBuilder.build()
  ├─ extractClassIgnoredProperties()
  │    filters propertyScanResults where isIgnored == true
  │    → Set<String> (property names from the annotated class)
  │
  ├─ buildConfigIgnoredProperties(targetProps, targetTypeName)
  │    iterates configObjects[*].ignoredMappings
  │    applies direction filter (FORWARD / BOTH / skip REVERSE)
  │    FORWARD: validates name is in targetProps → KSP error if not
  │    BOTH:    silently skips if name absent from this target
  │    → Set<String>
  │
  └─ classIgnoredProperties = extractClassIgnoredProperties() +
                               buildConfigIgnoredProperties(...)
       stored in MappingContext

PropertyResolver rule chain (per target property)
  └─ IgnoreRule.tryResolve(target, ctx)
       return Ignored(target) if target.name in ctx.classIgnoredProperties
       else null → continue to next rule
```

---

## 4. `IgnoreRule`

```kotlin
/**
 * Returns [PropertyMappingStrategy.Ignored] if the target property should be skipped.
 *
 * Two sources are merged into [MappingContext.classIgnoredProperties] before the rule
 * is invoked:
 *  - `@MapIgnore` on the `@MapFrom`/`@MapTo` annotated class.
 *  - `@IgnoreField` entries in `@MapConfig.ignoredMappings`, filtered to the current
 *    mapping direction by [ClassDescriptorBuilder].
 */
class IgnoreRule : MappingRule {
    override fun tryResolve(target: PropertyInfo, ctx: MappingContext): PropertyMappingStrategy? {
        val isIgnored = target.name in ctx.classIgnoredProperties
            || ctx.configOverrides[target.name] == KraftKspConstants.IGNORE_VALUE
        return if (isIgnored) PropertyMappingStrategy.Ignored(target) else null
    }
}
```

**Position in the rule chain** — `IgnoreRule` runs second (after `ConverterRule`). This
ensures:
- An explicit `@MapUsing` converter is never silently shadowed by an ignore.
- An ignored property is never mistakenly matched by `ConfigOverrideRule` or
  `DirectMatchRule` further down the chain.

---

## 5. `@MapTo` Direction Asymmetry

`@MapIgnore` behaves differently depending on which annotation is on the class:

**`@MapFrom` (annotated class = target)**

```kotlin
@MapFrom(ItemSource::class)
data class ItemDto(
    val name: String,
    @MapIgnore
    val quantity: Int = 0   // target property — skipped from constructor call
)
```

`extractClassIgnoredProperties()` collects `"quantity"` from `ItemDto`'s
`propertyScanResults`. `IgnoreRule` checks the *target* constructor parameter `"quantity"` →
match. Intuitive and direct.

**`@MapTo` (annotated class = source)**

```kotlin
@MapTo(ReportDto::class)
data class Report(
    val title: String,
    @MapIgnore
    val summary: String     // source property — name checked against ReportDto's constructor
)
```

`extractClassIgnoredProperties()` collects `"summary"` from `Report`'s
`propertyScanResults`. `IgnoreRule` checks the *target* constructor parameter name. This
works only because `ReportDto` also has a `summary` parameter — the ignore is resolved via
**name equality** between the annotated source property and the target parameter.

**Consequence:** `@MapIgnore` on a `@MapTo` source property has no effect if the target
class uses a different name for the corresponding parameter. In that case, use
`@IgnoreField` in a `@MapConfig` object, which always operates on the target name directly.

---

## 6. Validation

| Scenario | Level | Behaviour |
|---|---|---|
| `@IgnoreField(name, FORWARD)` — `name` not in target constructor | **error** | KSP error with available property names listed |
| `@IgnoreField(name, BOTH)` — `name` not in this direction's target | silent | Skipped; may be valid for the reverse direction |
| `@IgnoreField` with unrecognised direction string | **error** | KSP error: `"@IgnoreField unknown direction '...' on property '...'"` |
| `@MapIgnore` / `@IgnoreField` on non-null, no-default property | **error** | KSP error: `"non-nullable and has no default value"` |

---

## 7. Known Limitations

- **`@MapIgnore` on `@MapTo` requires same-name target property.** Annotating a source
  property whose target uses a different name has no effect. Use `@IgnoreField` in
  `@MapConfig` for cross-name ignores.

- **REVERSE direction not applied.** `@IgnoreField(direction = REVERSE)` entries are
  scanned and stored in `ConfigObjectScanResult.ignoredMappings` but are never added to
  `classIgnoredProperties`. They will activate automatically once reverse-mapping
  generation is implemented.

---

## 8. Testing Conventions

Tests follow the same conventions as the `nested/` suite. Two runners are available:

**Happy-path** — use `TestKspRunner.compileAndReturnGenerated(source)`:

```kotlin
val content = TestKspRunner.compileAndReturnGenerated(source)
    .first().readText()

assertThat(content).contains("title = this.title")
assertThat(content).doesNotContain("summary")
```

**Error / compile-failure path** — use `TestKspRunner.compile(source)` and inspect
`result.messages` or `result.exitCode`. Requires `@OptIn(ExperimentalCompilerApi::class)`:

```kotlin
@OptIn(ExperimentalCompilerApi::class)
class IgnoreFieldUnknownPropertyTest {
    @Test
    fun `FORWARD with unknown name emits KSP error`() {
        val result = TestKspRunner.compile(source)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("property not found in target")
    }
}
```

Do not use `compileAndReturnGenerated()` for error tests — it returns `List<File>` with no
access to compilation messages.

---

## 9. Examples

### Class-level `@MapIgnore` with `@MapFrom`

```kotlin
data class ItemSource(val name: String, val quantity: Int)

@MapFrom(ItemSource::class)
data class ItemDto(
    val name: String,
    @MapIgnore
    val quantity: Int = 0
)
```

**Generated:**

```kotlin
fun ItemSource.toItemDto() = ItemDto(
    name = this.name
    // quantity omitted; uses default value 0
)
```

---

### Class-level `@MapIgnore` with `@MapTo`

```kotlin
data class ReportDto(val title: String, val summary: String? = null)

@MapTo(ReportDto::class)
data class Report(
    val title: String,
    @MapIgnore        // source property; same name "summary" matched against ReportDto.summary
    val summary: String
)
```

**Generated:**

```kotlin
fun Report.toReportDto() = ReportDto(
    title = this.title
    // summary omitted; uses default null
)
```

---

### Config-level `@IgnoreField` — forward only

```kotlin
data class User(val name: String, val internalNotes: String, val auditLog: String)
data class UserDto(val name: String, val internalNotes: String = "", val auditLog: String = "")

@MapConfig(
    from = User::class,
    to = UserDto::class,
    ignoredMappings = [
        IgnoreField("internalNotes", direction = IgnoreDirection.FORWARD),
        IgnoreField("auditLog",      direction = IgnoreDirection.FORWARD),
    ]
)
object UserMapping
```

**Generated:**

```kotlin
fun User.toUserDto() = UserDto(
    name = this.name
    // internalNotes and auditLog omitted; both use their defaults
)
```

---

### Config-level `@IgnoreField` — BOTH direction

```kotlin
@MapConfig(
    from = Order::class,
    to = OrderDto::class,
    ignoredMappings = [
        IgnoreField("metadata")   // default direction = BOTH
    ]
)
object OrderMapping
```

`"metadata"` is looked up in `OrderDto`'s constructor. If found, it is added to the ignore
set for the forward pass. The same name will be looked up in `Order`'s constructor when
reverse generation is implemented.

---

### Interaction with `@MapField` (config override)

`@IgnoreField` takes priority over `@MapField` / `fieldMappings` for the same target
property name. `IgnoreRule` runs before `ConfigOverrideRule` in the rule chain, so the
property is claimed as ignored before any rename override can fire.

```kotlin
@MapConfig(
    from = Product::class,
    to = ProductDto::class,
    fieldMappings     = [FieldOverride(from = "internalId", to = "id")],
    ignoredMappings   = [IgnoreField("id")]
)
object ProductMapping
```

`id` is ignored; the `fieldMappings` entry for `id` is never reached.
