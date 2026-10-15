# Design

## Context

Collection support today is a flat `CollectionKind { LIST, SET }` enum, matched in five places that each re-implement "peel one `List`/`Set` and compare kinds":

- `collectionKindOf()` / `elementTypeInfo()` in `processor/util/MappableTypes.kt`
- `NestedRule`: the `@MapNested`, explicit `@NestedMapping` and auto-detected collection branches
- `GlobalConverterRule.findCollectionElementMatch`, which accepts only `ConverterEntry.Synthetic` (enum mappers), because `NestedMapper` rendering assumes a `to<Target>()` name
- `ValueClassRule.resolveCollection`
- `AutoEnumMappingDeriver.resolveRecursableTypes`

The fields `NestedMappingDescriptor.collectionKind`, `ValueClassWrap.collectionKind` and `ValueClassUnwrap.collectionKind` carry the kind to `CtorCallBuilder`, which has three near-identical `.map { … }` renderers. One element operation applies per property, at one level. A `Map` needs two operations (key and value), and nesting needs a tree, so neither fits this model. See proposal.md (Why) for the motivation and the specs for the required behaviour.

`PropertyMappingStrategy` is also consumed by the `MapperGeneratorProvider` SPI, which is marked `@ExperimentalKraftApi`.

## Goals / Non-Goals

**Goals:**
- One recursive model and one rule for all container shapes, replacing the five `List`/`Set` special cases.
- Element and key/value operations are resolved by the same code that resolves a scalar property, so the two cannot drift apart.
- Existing `List`/`Set` output stays byte-identical. The existing collection tests are the regression guard.

**Non-Goals:**
- Generated helper functions per shape (e.g. `List<ItemSource>.toItemDtoList()`). Output stays inline expressions.
- A DSL switch that allows lossy conversions into a `Set`.
- Any annotation or `kraft { }` DSL change.

## Decisions

### D1. New strategy `PropertyMappingStrategy.ContainerMapping`
The change lands in a new variant: `ContainerMapping(targetProperty, sourceProperty, shape: ContainerShape)`. `ContainerShape` is a sealed tree:

- `Leaf(op: ElementOp, sourceNullable, targetNullable)`
- `Linear(sourceKind, targetKind, element: ContainerShape, sourceNullable, targetNullable)` for List / Set / Collection / Iterable / Array / Mutable*
- `Keyed(sourceKind, targetKind, key: Leaf, value: ContainerShape, sourceNullable, targetNullable)` for Map / MutableMap. `key` is a `Leaf` because keys are never containers in this change.

`ElementOp` is one of `Identity`, `Nested(NestedMappingDescriptor)`, `Converter(ConverterDescriptor)`, `Wrap(valueClass)` or `Unwrap(propertyName)`.

`CollectionKind` and the three `collectionKind` fields are removed. `NestedMapper`, `ValueClassWrap` and `ValueClassUnwrap` become scalar-only.

*Alternatives:* (a) Add kinds to `CollectionKind` and keep one op per strategy. This cannot express a map whose key and value operations differ, or any depth. (b) Generate one helper function per shape. That adds public generated functions (a signature change) and clutters `build/generated`.

### D2. One `ContainerRule`, placed after `IgnoreRule` and before `NestedRule`
The rule claims a property when the source or target type is a recognised container and the two types are not identical. It builds the shape top-down. It returns:
- `Direct`, when the target type is a supertype of the source and the element types are identical (`List<String>` → `Collection<String>`)
- `ContainerMapping` on success
- `null` when some leaf has no operation, so `DirectMatchRule` reports KRAFT-E005 as today

It reports E063 or E064 itself when the shape is otherwise resolvable. It runs before `NestedRule` so that all container handling sits in one place. The deprecated `@MapNested` and explicit `@NestedMapping` are frozen: `ContainerRule` honours them only for same-kind `List`/`Set`, with exactly the 0.13 output (including the legacy `List` → `Set` path through a `FieldMapping` rename). New kinds, conversions and nesting go through auto-detection and `@MapField` / `FieldMapping` renames only. The collection branches of `NestedRule`, `GlobalConverterRule` and `ValueClassRule` are deleted.

*Alternative:* keep three per-rule collection branches and extend each one. That was rejected because it is the source of today's drift: `GlobalConverterRule` accepts only synthetic converters, and `ValueClassRule` rejects nullable elements.

### D3. Leaves are resolved by an extracted `LeafOperationResolver`
The scalar logic is extracted from `NestedRule` (the mappable-class check and descriptor synthesis), `GlobalConverterRule` (the converter lookup, now accepting `Real` entries under their own callable name) and `ValueClassRule` (the wrap/unwrap checks) into one resolver. The scalar rules and `ContainerRule` both call it. It tries the operations in the order of the scalar rule chain: nested (auto-detected; the deprecated explicit forms only on the frozen List/Set path), then converter, then value class, then identity. The same pair of types therefore resolves the same way at the top level and inside a container.

### D4. Proving key injectivity
A key `Leaf` is accepted only when its op is `Identity`, `Wrap`, `Unwrap`, or `Converter` backed by an enum mapping whose entry table maps no two source entries to one target entry. That needs the entry table, so `ConverterEntry.Synthetic` gains a reference to its `EnumMappingDescriptor`, or a precomputed `isInjective` flag. A `Real` `@KraftConverter`, a nested mapper, or an enum mapper whose table is not visible (for example, one compiled in another module) is not provably one-to-one, and gets E064. Its message names the operation so the user can see which fix applies.

### D5. Rendering in `CtorCallBuilder`
A single recursive `renderShape(receiver, shape, depth)` replaces the three `.map { }` renderers:
- `Linear`: `<recv>.map { … }` (or `mapNotNull` for nullable → non-null elements), followed by a conversion suffix taken from a `(sourceKind, targetKind)` table: none, `.toSet()`, `.toTypedArray()`, `.toMutableList()` or `.toMutableSet()`. With an `Identity` element the suffix alone is emitted (`.toList()`, `.toMutableList()`).
- `Keyed`: `mapValues` when the key is `Identity`, `mapKeys` when the value is `Identity`, and `entries.associate { k to v }` otherwise, plus `.toMutableMap()` for a `MutableMap` target.
- Nullable containers: `?.` and `?: <empty>`, using the empty value for the target kind.
- Lambda parameters: the innermost lambda uses `it`, and enclosing lambdas use `e<depth>`, so nested output never shadows (see the specs).

### D6. Downstream walkers traverse the shape
- `DescriptorBuilder` collects nested-mapper dependencies and runs the availability check over `ContainerMapping` leaves, using a `ContainerShape.nestedDescriptors()` helper. It also keeps excluding enum-backed IDs, as it does today.
- `AutoEnumMappingDeriver` and the nested-class worklist peel shapes recursively, keys included, so enum and class pairs inside `Map<StatusDto, List<ItemSource>>` are discovered.
- The W001 shadow probe (`nativeSourceName`) gets a `ContainerMapping` branch. W001 therefore also fires for hand-written `@MapUsing`s that duplicate the new shapes.

### D7. Diagnostics
Two new codes are added to `KraftDiagnostic`: `LOSSY_COLLECTION_CONVERSION` (KRAFT-E063) and `NON_INJECTIVE_MAP_KEY` (KRAFT-E064), each with a `docs/user-guide/errors.md` entry, which `ErrorsReferenceCompletenessTest` enforces. KRAFT-E015's text is reworded. `detailedTypeMismatch` gains the map-value nullability hint. Types render as simple names with their type arguments (`List<ItemSource>`), as in the spec wording.

### Signature impact
**Generated function signatures do not change.** Mappers keep their names, receivers, parameters and return types. Only the argument expressions inside the constructor call are new, and they are new only for fields that failed to compile before. **The experimental SPI does change:** a new `PropertyMappingStrategy` variant, and the `collectionKind` fields and `CollectionKind` are removed. A custom `MapperGenerator` with an exhaustive `when` over strategies has to add a branch. This is allowed under `@ExperimentalKraftApi`, and the release notes have to call it out.

## Risks / Trade-offs

- [Moving `List`/`Set` into `ContainerRule` changes which rule claims them, and existing output could shift] → The spec scenario "Existing List and Set output is unchanged", plus the unchanged `CollectionElementConverterTest`, value-class and nested collection tests, guard it. Rule-order comments in `PropertyResolver` are updated.
- [`Real` converters inside collections are new behaviour: a project with a `@MapUsing` workaround now gets W001] → It's intended and suppressible with `@Suppress("KRAFT-W001")`. The proposal flags it for warnings-as-errors builds.
- [Cross-module enum keys are refused even when one-to-one] → E064 names the cause, and a `@MapUsing` works around it. This can be relaxed later by publishing the entry table, without a spec change to the one-to-one rule.
- [`.map { }.toTypedArray()` allocates twice] → Acceptable for mapper code. An `Array(size) { }` fast path can be added later without changing behaviour.
- [`LeafOperationResolver` extraction is a broad refactor of three rules] → It's done as its own task group before any new shape is added, with the full existing suite green at that point.

## Migration Plan

No consumer migration is needed. Rollback is a revert of the change. The SPI note goes in the release-please changelog entry.
