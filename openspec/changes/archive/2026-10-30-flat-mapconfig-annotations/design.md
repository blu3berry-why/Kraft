# Design

## Context

See proposal.md, Why. The relevant current state:

- `FieldMapping` and `MapIgnoreField` (`kraft-annotations/.../config/`) have no `@Target`
  and default (`RUNTIME`) retention. They are only valid as `@MapConfig` array elements.
- `ConfigObjectScanner.processConfigObject` reads them with `extractFieldOverrides` and
  `extractIgnoredMappings`, through `getArrayArgOrNull<KSAnnotation>` on the `@MapConfig`
  annotation, and builds `FieldOverride` / `IgnoredMappingConfig` into
  `ConfigObjectScanResult`. Everything downstream (`ConfigDescriptorBuilder`,
  `ReverseDescriptorBuilder`, `IgnoredPropertyAggregator`, the rule chain) consumes only the
  scan result.
- `PropertyMappingStrategy.Constant(target, expression: String)` exists and is emitted as
  `%N = %L`. Today only the `KRAFT-W009` null fill produces it.
- Generated mappers go to a different package (`<pkg>.generated`) from the `@MapConfig`
  object. A converter call reaches the object through `%T` (`enclosingClassName`) in
  `CtorCallBuilder`, which imports it.
- Spike, 2026-10-18 (throwaway branch, deleted). With `@Target(CLASS)`,
  `@Retention(SOURCE)` and `@Repeatable` added, the array form still compiles on metadata,
  JVM and JS, and the generated output is unchanged. KSP2 2.3.3 lists each repeated
  annotation separately in `KSClassDeclaration.annotations` during
  `kspCommonMainKotlinMetadata`. Arguments left at their default are **absent** from
  `arguments` (for example, `MapIgnoreField(name=notes)` has no `direction`), which
  `getEnumArgOrDefault` already handles.

## Goals / Non-Goals

**Goals:**
- Both forms feed the same `fieldOverrides` / `ignoredMappings` lists, so no consumer of
  `ConfigObjectScanResult` changes for #40.
- `@MapConstant` values are plain Kotlin that kotlinc type-checks, and Kraft checks the
  member type against the target before generating code.

**Non-Goals:**
- Flat forms of `nestedMappings` (deprecated), `useGlobalConverters` or `aliasEmitMode`.
  They are scalar settings, not rule lists.
- `@MapConstant` on `@MapFrom` / `@MapTo` classes. Constants live on a `@MapConfig` object.
- Changing duplicate handling inside one form. Two `FieldMapping`s for one target in the
  array still resolve last-wins (`associate`), as they do today. The flat form inherits
  that. See Open Questions.
- A redundancy warning for a constant that equals what native mapping would produce.

## Decisions

### D1. Reuse the annotation classes, add three meta-annotations
`FieldMapping` and `MapIgnoreField` gain `@Target(AnnotationTarget.CLASS)`,
`@Retention(AnnotationRetention.SOURCE)` and `@Repeatable`. A `@Target` does not restrict
use as an annotation argument, which the spike confirmed. `SOURCE` matches `MapConfig` and
`MapUsing`, and avoids the JVM container class that `RUNTIME` repeatables generate.
*Alternatives:* new names such as `@Rename` (rejected by the owner: no new names besides
`@MapConstant`), or an explicit container annotation (Kotlin's `@Repeatable` makes it
unnecessary).

### D2. One parse path, two origins
`extractFieldOverrides` and `extractIgnoredMappings` are split into a *collector* (array
entries and class annotations, each tagged `Origin.ARRAY` / `Origin.FLAT`) and the existing
per-annotation parser, unchanged. Flat annotations are found by filtering
`classDeclaration.annotations` by FQ name, using the existing `isAnnotation` helper. The
tagged lists feed the E066 check and are then stripped to the existing `FieldOverride` /
`IgnoredMappingConfig`. *Alternative:* a separate flat-only parser was rejected because it
would duplicate the blank-name and unknown-direction checks.

### D3. Error codes
| Code | Raised in | Condition |
|---|---|---|
| E066 | `ConfigObjectScanner` | same `FieldMapping.target` / `MapIgnoreField.name` in both origins on one object |
| E067 | `ConfigDescriptorBuilder` / `ReverseDescriptorBuilder`, beside the `@MapIgnoreField` unknown-name check | `@MapConstant` target is not a constructor parameter in its resolved direction |
| E068 | `ConstantRule` | member type not assignable to target type |
| E069 | `ConstantMemberScanner` | private, has parameters, extension, or not in a `@MapConfig` object |
| E070 | `ConfigObjectScanner`, after directions are resolved | a constant's target is also claimed in that direction by another constant, a `FieldMapping`, a `@MapUsing` or a `@MapIgnoreField` |
| E071 | `ConfigObjectScanner.scan` | flat `FieldMapping` / `MapIgnoreField` on a declaration without `@MapConfig` |

E071 finds orphans through `resolver.getSymbolsWithAnnotation(FQ_FIELD_MAPPING / FQ_MAP_IGNORE_FIELD)`
minus the config objects. Annotations used as array arguments are not annotations *on* a
symbol, so they are never returned (task 2.4 tests this).

### D4. `@MapConstant` sits on the member that holds the value
```kotlin
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class MapConstant(
    val target: String = "",
    val direction: ConverterDirection = ConverterDirection.AUTO,
)
```
Decided with the owner on 2026-10-18. Annotation arguments cannot hold arbitrary Kotlin
values. This placement keeps the value as real Kotlin with no strings, and the target name
is only written when it differs from the member name. *Alternatives considered:* (A) a
Kotlin-expression string, which kotlinc only checks in generated code and which needs fully
qualified names for anything non-literal; (B) typed parameters (`stringValue`, `intValue`,
…), which can't express arbitrary values and can't tell "unset" from `0`; (C) a string
reference to a member, which is the same as this design but with an extra string;
(D) checked literal strings, which can't hold computed values. **This deviates from
ROADMAP #29's text** ("class-level annotation", string value).
`direction` reuses `ConverterDirection`, as `@MapUsing` does. AUTO means forward if the
forward target has the parameter, else reverse, and never both.

### D5. `ConstantMemberScanner` and `ConstantDescriptor`
A new class `processor/scanner/ConstantMemberScanner` keeps `ConfigObjectScanner` (already
`LargeClass`-suppressed) from growing. For a config object it walks `declarations`, picks
the members with `@MapConstant`, validates them (E069), resolves the target name and
direction against the forward and reverse target classes, and returns
`ConstantDescriptor(enclosingObject, memberName, isFunction, targetPropertyName, valueType:
TypeInfo, declaration, resolvedDirection)`. `ConfigObjectScanResult` gains
`constants: List<ConstantDescriptor> = emptyList()`. This is the only shape change, and it
belongs to #29. Orphan members (E069, "not in a `@MapConfig` object") are found through
`getSymbolsWithAnnotation(FQ_MAP_CONSTANT)`. For the value type, a property uses
`type.resolve()` and a function uses `returnType?.resolve()`. KSP resolves inferred types
for both.

### D6. New strategy `PropertyMappingStrategy.ConstantMember`
`ConstantMember(targetProperty, constant: ConstantDescriptor)` is emitted by
`CtorCallBuilder` as `%N = %T.%N` (property) or `%N = %T.%N()` (function). `%T` reuses
`enclosingClassName`, so the object is imported and companion objects work. *Why not reuse
`Constant(expression)`:* its `%L` string would need a fully qualified name built by hand,
and `Constant` stays what it is today, the W009 null fill. `nestedDescriptors()` and the
W001 shadow probe treat `ConstantMember` like `Constant` (no nested mappers, never a
shadow).

### D7. `ConstantRule` goes first in the rule chain
`MappingContext` gains `constants: List<ConstantDescriptor>`, filtered to the current
direction by the descriptor builders, the same way converters are. `ConstantRule` is placed
before `ConverterRule`. E070 has already rejected every conflicting explicit rule, so the
order only matters against native rules: a constant beats a direct match (spec "Constant
over a same-named source property"). The rule checks
`target.type.ksType.isAssignableFrom(valueType)`. On failure it reports E068 and returns
`RuleOutcome.Reported`.

### D8. Generated signatures
**Unchanged.** Both features only change constructor-call arguments inside existing
`fun S.toT()` mappers. Not a breaking change for consumers.

## Risks / Trade-offs

- [The retention change from RUNTIME to SOURCE breaks someone reading `FieldMapping` by
  reflection] → Kraft never did. Both classes are documented as `@MapConfig` arguments
  only. Mention it in the CHANGELOG line.
- [A `@MapConstant val createdAt = Clock.System.now()` freezes one timestamp forever] →
  documented with a `fun` counter-example in the constants docs page. No warning, by owner
  decision ("don't overcomplicate").
- [The spike did not cover `getSymbolsWithAnnotation` for repeated annotations, which E071
  and the E069 orphan check rely on] → tasks 2.4 and 3.4 test it. If KSP misses repeated
  instances, fall back to walking `resolver.getAllFiles()` class declarations for E071
  only.
- [Inferred-type members in the metadata compilation could resolve to an error type] → the
  KMP integration scenario uses an untyped `val`. On an error type, report E068 with the
  type shown as `<unresolved>`, not a crash.
- [Mixing forms on one object reads worse than either alone] → allowed (no code reason to
  forbid it). The docs recommend one form per object.

## Migration Plan

No migration. Existing array-form mappers produce byte-identical output (spec "Array form
unchanged"). Rollback is a plain revert. No persisted state is involved.

## Open Questions

- Should a target named twice within one form (array or flat) become an error? This is
  out of scope here (it would break existing last-wins code). If wanted, raise it as a
  separate roadmap item.
