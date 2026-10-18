# Upgrading

This page lists the changes in each release that can affect a build that already works. New features that only make previously failing code compile are listed in the [changelog](https://github.com/blu3berry-why/Kraft/blob/main/CHANGELOG.md).

## 0.14.0

**No generated function signature changes.** A mapper that compiled on 0.13 generates the same code on 0.14.

### New warnings can fail a warnings-as-errors build

0.14 maps many shapes natively that used to need a hand-written helper: `Map`, `Array`, `Collection`, `Iterable` and the `Mutable*` types, nesting to any depth, value-class wrap and unwrap, and `@KraftConverter`s inside collections. When existing code duplicates one of those mappings, Kraft keeps using your code but reports a warning:

- [`KRAFT-W001`](errors.md#kraft-w001): a property-source `@MapUsing` that does what Kraft would generate anyway.
- [`KRAFT-W002`](errors.md#kraft-w002): a `@KraftConverter` for `S? → T?` in a module that also registers `S → T`. Kraft already lifts the non-null converter through a safe call.

If your build treats KSP warnings as errors, it fails on these. Delete the redundant helper, or keep it with `@Suppress("KRAFT-W001")` / `@Suppress("KRAFT-W002")` on its declaration when its body does something the native mapping does not (filtering, sorting, logging).

### Every diagnostic starts with a code

Each Kraft error and warning now begins with a stable code (`KRAFT-E005`, `KRAFT-W001`, …) and links to the [errors reference](errors.md). If a script or test matches Kraft's message text, match the code instead. Codes are never reused or renumbered.

### Some failures report a more specific error

These shapes failed on 0.13 too. They now fail with a dedicated code instead of a general type mismatch:

- [`KRAFT-E063`](errors.md#kraft-e063): converting into a `Set` from a collection that can hold duplicates (`List<A>` → `Set<B>`). Use a `List` target or an explicit `@MapUsing`.
- [`KRAFT-E064`](errors.md#kraft-e064): a `Map` key mapping that could merge two keys into one and silently drop an entry.

### The deprecated nested-mapping annotations stay at 0.13 behaviour

`@MapNested` and `@MapConfig(nestedMappings = …)` do not learn the new collection shapes, because they are removed at 1.0. Existing uses keep compiling unchanged, including the one `List` → `Set` path that already compiled on 0.13 through them. Use auto-detection, with `@MapField` / `FieldMapping` for renames. See [Deprecated nested-mapping annotations](collection-mapping.md#deprecated-nested-mapping-annotations).

## 0.13.0

### Nullable scalar fields reuse non-null converters

A `X? → Y?` property pair now uses a registered non-null `X → Y` converter through a safe call (`this.field?.toY()`). On 0.12 the same pair failed to compile. Nothing that compiled before changes, but a pair you expected to fail now compiles and calls your converter. A nullable source with a non-null target (`X? → Y`) is still an error. See [Restrictions and caveats](custom-converters.md#restrictions-and-caveats).

### File-level `@file:OptIn` markers reach generated code

Before 0.13, Kraft copied an opt-in onto the generated mapper only when the marker sat on the declaration itself. A file-level `@file:OptIn(...)`, which some DTO generators emit, was silently dropped. The same models could produce a compiling mapper with one generator version and an opt-in error with another. On 0.13 both forms are copied. A build that needed a module-wide `-opt-in=...` flag to compile generated mappers still compiles, and you can remove that flag once nothing else needs it. See [Opt-in propagation](custom-converters.md#opt-in-propagation).
