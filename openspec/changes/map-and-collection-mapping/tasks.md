# Tasks

> Execution: dispatch. 8 groups, ~25 hand-written files. Group 1 is a refactor that needs judgment; groups 2–7 follow its pattern.

> Branch: `feat/map-and-collection-mapping` off `main`. All groups run sequentially: each builds on the previous one, and they share the `kraft-core`/`kraft-ksp` build root. `kraft-core` has no test source set, so every test lives in `kraft-ksp/src/jvmTest`, in a new package `com.blu3berry.kraft.containers`. Test command for each group: `./gradlew :kraft-ksp:jvmTest --tests '<pattern>'`. Each compile-test asserts the generated assignment line from the spec scenario verbatim, or the quoted error text.

## 1. Recursive container model, with no behaviour change

> Agent: opus · effort high · scope: kraft-core/src/main/**, kraft-ksp/src/main/**, kraft-ksp/src/jvmTest/**, docs/developer-guide/custom-code-generator.md · verify: ./gradlew :kraft-ksp:jvmTest · after: none

- [x] 1.1 Pin the current output first. Add jvmTest `ContainerRegressionPinTest`, asserting today's generated lines for `List`/`Set` × nested, enum (synthetic converter), value-class wrap and unwrap, and nullable source → non-null target (`?: emptyList()` / `emptySet()`) and nullable elements (`mapNotNull`). This is spec scenario "Existing List and Set output is unchanged". Verify that it passes on `main` before any other edit.
- [x] 1.2 Add `ContainerShape` (`Leaf`, `Linear`, `Keyed`), `ElementOp` and `PropertyMappingStrategy.ContainerMapping` in `kraft-core/model/descriptor` (design D1), and a `ContainerShape.nestedDescriptors()` helper. Add container detection to `MappableTypes.kt` (`containerKindOf`, returning the linear/keyed kind and its type arguments). Verify that it compiles (`./gradlew :kraft-core:compileKotlin`).
- [x] 1.3 Extract `LeafOperationResolver` from the scalar paths of `NestedRule`, `GlobalConverterRule` and `ValueClassRule` (design D3), and make those rules call it for scalars. Verify that the full `kraft-ksp:jvmTest` suite passes unchanged.
- [x] 1.4 Add `ContainerRule` after `IgnoreRule` in `PropertyResolver.default()` (design D2). At this point it handles only same-kind `List`/`Set`, with the leaf ops that work today, so that it takes over exactly the old collection branches. Delete those branches from the three rules, delete `CollectionKind` and the `collectionKind` fields, and update the rule-order KDoc. Verify that `ContainerRegressionPinTest` and the full suite are green.
- [x] 1.5 Replace the three `.map { }` renderers in `CtorCallBuilder` with a recursive `renderShape` (design D5). Make `DescriptorBuilder` (dependency registration and the `isAvailable` walk), `AutoEnumMappingDeriver.resolveRecursableTypes` and the W001 probe's `nativeSourceName` traverse `ContainerMapping` (design D6). Verify that the full suite is green, including `RedundantMapUsingWarningTest` and `ShadowedNativeProbeTest`.
- [x] 1.6 Document the SPI change (the new `ContainerMapping` variant, and the removed `CollectionKind`/`collectionKind`) in `docs/developer-guide/custom-code-generator.md`. Verify with grep that the doc no longer mentions `CollectionKind`.

## 2. More collection types, lossless conversions and E063

> Agent: sonnet · effort high · scope: kraft-core/src/main/**, kraft-ksp/src/main/**, kraft-ksp/src/jvmTest/**, docs/user-guide/errors.md, docs/user-guide/collection-mapping.md, .claude/skills/kraft-mappers/SKILL.md · verify: ./gradlew :kraft-ksp:jvmTest · after: 1

- [ ] 2.1 Extend `ContainerRule` and the suffix table to `Collection`, `Iterable`, `Array`, `MutableList`, `MutableSet` and `MutableCollection`, with the lossless conversion matrix and the identity-element/supertype cases (`Direct`). Verify with jvmTest `LinearContainerTest`, covering the scenarios "Collection to Collection", "Array to Array", "MutableList to MutableList", "Array to List", "Set to List", "Iterable to MutableCollection", "Same elements, different collection type" and "Target already a supertype".
- [ ] 2.2 Add `KraftDiagnostic.LOSSY_COLLECTION_CONVERSION` (KRAFT-E063) and its `errors.md` entry, and report it from `ContainerRule` for non-Set → Set at any level, including reverse mappers. Verify with jvmTest `LossySetConversionTest`, covering "List to Set" (both quoted strings) and "Reverse of Set to List", plus a green `ErrorsReferenceCompletenessTest`.
- [ ] 2.3 Add container-level and element-level nullability for the new kinds (empty values from the spec: `mutableListOf()`, `mutableSetOf()`, `emptyArray()`). Verify with jvmTest `ContainerNullabilityTest`, covering "Nullable Array into non-null List", "Nullable into nullable", "Nullable elements kept" and "Nullable elements dropped".
- [ ] 2.4 Reword KRAFT-E015 (`nestedTypeNotMappable`) and its `errors.md` entry. Verify with jvmTest scenario "Interface element type" in `LinearContainerTest`.
- [ ] 2.5 Update `docs/user-guide/collection-mapping.md` (supported types, conversion matrix, the Set rule) and the `kraft-mappers` SKILL.md "what Kraft does automatically" list. Verify that the doc's examples match the test expectations and that the SKILL.md no longer says only `List`/`Set`.

## 3. Element operations at parity with scalars

> Agent: sonnet · effort medium · scope: kraft-core/src/main/**, kraft-ksp/src/main/**, kraft-ksp/src/jvmTest/**, docs/user-guide/collection-mapping.md, docs/user-guide/custom-converters.md · verify: ./gradlew :kraft-ksp:jvmTest · after: 2

- [ ] 3.1 Allow `ConverterEntry.Real` leaves, rendered under their own callable name. Verify with jvmTest `ContainerElementOpTest` scenario "Named @KraftConverter on elements", and check that `CollectionElementConverterTest` is still green.
- [ ] 3.2 Value-class and `@MapNested` leaves inside the new kinds. Verify with `ContainerElementOpTest` scenarios "Value class elements in an Array", "@MapNested on a Collection" and "No element operation" (KRAFT-E005 is reported, not a new code).
- [ ] 3.3 Note in `custom-converters.md` and `collection-mapping.md` that named `@KraftConverter`s now apply element-wise. Verify that the docs mention `intoBar`-style names.

## 4. Nesting depth and lambda naming

> Agent: sonnet · effort medium · scope: kraft-ksp/src/main/**, kraft-core/src/main/**, kraft-ksp/src/jvmTest/** · verify: ./gradlew :kraft-ksp:jvmTest · after: 3

- [ ] 4.1 Make `ContainerRule` build shapes recursively, and make `renderShape` name enclosing lambdas `e<depth>` while the innermost uses `it`. Verify with jvmTest `NestedContainerTest`, covering "List of lists", "Inner nullable list into non-null list" and "Lossy conversion at an inner level".
- [ ] 4.2 Compile the "List of lists" output with `allWarningsAsErrors = true` in the test runner. Verify with the scenario "No shadowing warnings" in `NestedContainerTest`.

## 5. Map values

> Agent: sonnet · effort high · scope: kraft-core/src/main/**, kraft-ksp/src/main/**, kraft-ksp/src/jvmTest/**, docs/user-guide/collection-mapping.md, docs/user-guide/errors.md, .claude/skills/kraft-mappers/SKILL.md · verify: ./gradlew :kraft-ksp:jvmTest · after: 4

- [ ] 5.1 Add `Keyed` shapes for `Map`/`MutableMap` with an identity key (`mapValues`, `.toMutableMap()`, and the identity-value `toMutableMap()`). Verify with jvmTest `MapValueTest`, covering "Nested values", "Named @KraftConverter on values", "MutableMap target", "Same types, MutableMap target" and "Map and non-map".
- [ ] 5.2 Add map nullability: `?: emptyMap()` / `mutableMapOf()`, nullable values kept, and nullable → non-null values returning `null` from `ContainerRule`, plus the new `detailedTypeMismatch` map-value hint. Verify with jvmTest `MapNullabilityTest`, covering "Nullable map into non-null map", "Nullable values kept" and "Nullable values into non-null values" (quoted hint), with the hint documented under E005 in `errors.md`.
- [ ] 5.3 Add a Map section to `collection-mapping.md` and the SKILL.md list. Verify that the examples match the `MapValueTest` expectations.

## 6. Map keys and E064

> Agent: opus · effort high · scope: kraft-core/src/main/**, kraft-ksp/src/main/**, kraft-ksp/src/jvmTest/**, docs/user-guide/errors.md, docs/user-guide/collection-mapping.md · verify: ./gradlew :kraft-ksp:jvmTest · after: 5

- [ ] 6.1 Expose enum-mapping injectivity on `ConverterEntry.Synthetic` (design D4), computed from the `EnumMappingDescriptor` entry table. Verify with a jvmTest unit test: an auto-derived mapping is injective, and a `@MapEnum` mapping two entries to one is not.
- [ ] 6.2 Add key leaves (`mapKeys`, `entries.associate`) for `Identity`, `Wrap`, `Unwrap` and injective enum ops. Verify with jvmTest `MapKeyTest`, covering "Value class keys and nested values", "Keys only", "Auto-derived enum keys" and "One-to-one @MapEnum keys".
- [ ] 6.3 Add `KraftDiagnostic.NON_INJECTIVE_MAP_KEY` (KRAFT-E064) and its `errors.md` entry, with the operation-specific message text. Verify with `MapKeyTest` scenarios "Nested-mapper keys" (both quoted strings), "@KraftConverter keys", "Many-to-one @MapEnum keys" and "Many-to-one enum as a value is still allowed", plus a green `ErrorsReferenceCompletenessTest`.
- [ ] 6.4 Document the key rules in `collection-mapping.md`. Verify that the doc lists exactly the allowed key operations from the spec.

## 7. Maps nested with collections

> Agent: sonnet · effort medium · scope: kraft-core/src/main/**, kraft-ksp/src/main/**, kraft-ksp/src/jvmTest/** · verify: ./gradlew :kraft-ksp:jvmTest · after: 6

- [ ] 7.1 Recursive `Keyed` values inside `Linear` and the reverse. Verify with jvmTest `MapNestingTest`, covering "Map of lists", "List of maps" and "Lossy conversion inside a map value".
- [ ] 7.2 Make `AutoEnumMappingDeriver` and the nested-class worklist discover pairs through map keys and values. Verify with a `MapNestingTest` case: `Map<StatusDto, List<ItemSource>>` → `Map<Status, List<ItemDto>>` with no `@MapConfig` for the elements and no `@MapEnum` generates `toStatus()` and `toItemDto()`.

## 8. Full gate

> Agent: sonnet · effort medium · scope: integration-tests/** (only if a failure needs a fix) · verify: the commands below · after: 7

- [ ] 8.1 Add a KMP case to `integration-tests/kmp-consumer`: a `commonMain` mapper with `Map<String, List<ItemDto>>` and `Array<UserId>` fields. Verify that it compiles and maps correctly in the consumer's JVM test.
- [ ] 8.2 Run the full gate: `./gradlew detekt :kraft-ksp:jvmTest :kraft-gradle-plugin:test` plus the KMP integration-test tasks, all green. Verify by recording the test counts in the task.

## Workflow follow-up

- Update `ROADMAP-draft.md` / `docs/ROADMAP.md`: mark Tier 1 #1 done.
- Archive the change with `/opsx:archive` after the PR is merged.
