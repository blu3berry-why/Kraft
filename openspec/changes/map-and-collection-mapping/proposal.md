# Proposal

## Why

Kraft maps element types inside `List` and `Set` only, and only when both sides use the same kind of collection. A single `Map<String, ItemDto>`, an `Array<TagDto>`, a `Collection<T>` or a `MutableList<T>` field forces the whole class back to a hand-written mapper. The same happens for one level of nesting (`Map<String, List<ItemDto>>`). These fields are routine in API DTOs. A user drops a mapper library the first time it cannot map a field, and this is Kraft's most common one (ROADMAP-draft Tier 1 #1, the remaining Konvert parity gap).

## What Changes

- **More collection types.** Element-wise mapping now also covers `Collection`, `Iterable`, `Array` and the `Mutable*` variants (`MutableList`, `MutableSet`, `MutableCollection`), in addition to `List` and `Set`.
- **Lossless conversions between collection types.** The source and target no longer need the same collection type. Any conversion that keeps every element is generated (`Array<A>` → `List<B>`, `Set<A>` → `List<B>`, `List<A>` → `MutableList<B>`, `Iterable<A>` → `Collection<B>`). Converting into a `Set` from anything other than a `Set` would silently drop duplicates, so it is a compile error (new **KRAFT-E063**) that suggests a `List` target or an explicit `@MapUsing`.
- **`Map<K, V>` and `MutableMap<K, V>`.** Values go through the normal element mapping (nested mapper, enum mapper, `@KraftConverter`, value-class wrap/unwrap). Keys may be the same type on both sides, or mapped through an operation Kraft can prove maps each key to a different key: value-class wrap/unwrap, or an enum mapping with no two entries mapped to the same target entry. Any other key mapping (a nested mapper, a `@KraftConverter`, or a many-to-one `@MapEnum`) is a compile error (new **KRAFT-E064**), because colliding keys would silently drop entries.
- **Nesting to any depth.** `Map<String, List<ItemDto>>`, `List<List<ItemDto>>` and `List<Map<String, ItemDto>>` map recursively, with each level following the rules above.
- **Real `@KraftConverter`s inside collections.** Today an element-level converter is used only when it is an auto-derived enum mapper. A user's `@KraftConverter` with any name (`fun Foo.intoBar()`) now also applies element-wise (`items.map { it.intoBar() }`).
- **Nullability rules extended to every level.** A nullable container mapped to a non-null one falls back to an empty container (`emptyList()`, `emptyMap()`, `emptyArray()`…), as `List` and `Set` already do. Nullable list or set elements mapped to non-null ones are dropped with `mapNotNull`, as they are today. A nullable map value mapped to a non-null one is a type-mismatch error, because dropping the entry would lose its key.
- **`@MapNested` and `@MapConfig` nested mappings** apply to the element types inside these new shapes the same way they apply to `List`/`Set` elements today.
- **KRAFT-E015's message** no longer says "non-List collections are not supported".
- **Not in this change:** primitive arrays (`IntArray`, …), `Sequence`, `kotlinx.collections.immutable` types, conversions between a `Map` and a non-map collection, and lossy conversions into a `Set` (these need a `@MapUsing`).

**No generated function signature changes.** Mappers that compile today generate the same code. Fields that failed before now compile. **Possibly BREAKING for builds that treat warnings as errors:** an existing hand-written `@MapUsing` for a shape Kraft now maps natively triggers KRAFT-W001 (redundant `@MapUsing`).

## Capabilities

### New Capabilities
- `mapping/collection-mapping`: element-wise mapping inside single-element collection types (List, Set, Collection, Iterable, Array, Mutable*), the allowed conversions between them, nullability at each level and nesting.
- `mapping/map-mapping`: mapping `Map`/`MutableMap` keys and values, which key operations are allowed, and how map values nest with collections.

### Modified Capabilities
<!-- none: openspec/specs/ is empty; 0.13 behaviour is not back-filled -->

## Impact

- **Code (`kraft-core`):**
  - `model/descriptor`: `CollectionKind` is replaced by a recursive container shape, and a new `PropertyMappingStrategy` variant carries it.
  - `processor/util/MappableTypes.kt`: container type detection.
  - `NestedRule`, `GlobalConverterRule`, `ValueClassRule`: their `List`/`Set` branches move into one new container rule.
  - `PropertyResolver`: rule order, and the W001 shadow probe.
  - `DescriptorBuilder`: nested-mapper dependency walk and availability check.
  - `AutoEnumMappingDeriver`: recursion through container shapes.
  - `LoggerExtensions.kt` and `KraftDiagnostic`: E063, E064, and the reworded E015.
- **Code (`kraft-ksp`):** `CtorCallBuilder` renders the recursive shape.
- **Tests:** new compile-tests in `kraft-ksp/src/jvmTest`. Existing `CollectionElementConverterTest` and the value-class/nested collection tests must still pass unchanged.
- **Docs:** `docs/user-guide/collection-mapping.md`, `docs/user-guide/errors.md` (E063, E064), and the `kraft-mappers` SKILL.md "what Kraft does automatically" list.
