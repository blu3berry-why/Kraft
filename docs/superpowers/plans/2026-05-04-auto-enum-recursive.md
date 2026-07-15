# Recursive Auto-Enum Derivation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lift the "explicitly-declared parent only" limit on `AutoEnumMappingDeriver` so a `@MapConfig(Store, StoreDto)` parent auto-derives `Status → StatusDto` even when the enum lives several data-class layers deep (e.g. `Store.user.address.region.status`) without requiring a `@MapConfig` for each intermediate class. Mirrors `NestedRule`'s recursion semantics so the deriver only synthesizes enum mappers for pairs `NestedRule` would also claim at runtime — no dead generated code.

**Architecture:** Replace the deriver's flat parent-pair iteration with a worklist-and-visited walker. Seed the worklist with declared parent pairs (`classMappings + configMappings`, forward + reverse). For each pair, inspect target properties; for each property whose source/target types are *also* a mappable nested-class pair (per `NestedRule`'s predicate, including `List<X>` / `Set<X>` element extraction), push that nested pair onto the worklist. Cycle detection via a visited set keyed by `(sourceFq, targetFq)`. Element-level enum pairs inside `List<Enum>` / `Set<Enum>` likewise auto-derive. Mappability/collection helpers extracted from `NestedRule` into shared `kraft-core` utilities so both walkers stay aligned.

**Tech Stack:** Kotlin / KSP / KotlinPoet / kotlin-compile-testing.

---

## Pre-cleared deviations

(None.)

---

## File Structure

**New files:**

- `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/util/MappableTypes.kt` — public helpers extracted from `NestedRule`: `isMappableClass(TypeInfo)`, `collectionKindOf(TypeInfo)`, `elementTypeInfo(TypeInfo)`. Single shared source of truth used by both `NestedRule` and `AutoEnumMappingDeriver`.

**Modified files:**

- `kraft-core/.../propertyresolver/rules/NestedRule.kt` — its private `isMappableClass`, `collectionKindOf`, `elementTypeInfo` are removed; the rule now imports the shared utility. Pure refactor; behavior unchanged.

- `kraft-core/.../scanner/AutoEnumMappingDeriver.kt` — replaces the flat-iteration body of `derive()` with a worklist-and-visited walker. Adds element-unwrapping for `List<X>` / `Set<X>` and recursion through nested data-class properties.

- `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt` — appends new tests covering the recursive cases.

- `docs/user-guide/enum-mapping.md` — replaces the "explicit parent" wording with the new (broader) contract.

---

## Test matrix (acceptance criteria)

Five new tests plus one regression-update on the docs section. All assertions verify generated content, not file presence alone, where practical.

| # | Scenario | Expected behavior |
|---|----------|-------------------|
| 1 | Depth-2 nested without inner `@MapConfig`: `Store.user: User`, `User.status: Status`, only `@MapConfig(Store, StoreDto)` declared | Auto-derives `Status → StatusDto`; parent compiles |
| 2 | Depth-3 nested without intermediate `@MapConfig`s: `Store.user.address.country: Country` (enum) | Auto-derives the deep-leaf enum |
| 3 | Cycle: `Node(val parent: Node?, val status: Status)` ↔ `NodeDto(val parent: NodeDto?, val status: StatusDto)` with `@MapConfig(Node, NodeDto)` | Walker terminates; auto-derives `Status → StatusDto` once |
| 4 | `List<Enum>`: `Store.statuses: List<Status>` ↔ `StoreDto.statuses: List<StatusDto>` | Auto-derives the enum-element pair |
| 5 | `Set<NestedDataClass>` wrapper around an enum: `Store.users: Set<User>`, `User.status: Status` | Walker unwraps `Set`, recurses into `User`, auto-derives `Status → StatusDto` |

The existing eight tests in `EnumByNameAutoTest` must continue to pass — the new walker is strictly more permissive than the old flat iteration, so prior behavior is preserved by construction.

---

### Task 1: Extract `MappableTypes` helpers from `NestedRule`

Pure refactor that creates a single source of truth for mappability and collection unwrapping. No behavior change; no new tests.

**Files:**
- Create: `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/util/MappableTypes.kt`
- Modify: `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/descriptor/propertyresolver/rules/NestedRule.kt`

- [ ] **Step 1: Create the shared utility file**

```kotlin
package com.blu3berry.kraft.processor.util

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.blu3berry.kraft.model.TypeInfo
import com.blu3berry.kraft.model.descriptor.CollectionKind

/**
 * Returns `true` when [type] denotes a class Kraft is willing to synthesise
 * a nested mapper for: a concrete user-defined class with a primary
 * constructor. Mirrors the predicate `NestedRule` uses to claim a property,
 * so callers that decide to recurse into a type can be confident the
 * resolver chain will also claim it at runtime.
 */
fun isMappableClass(type: TypeInfo): Boolean {
    val decl = type.declaration
    val fqn = decl.qualifiedName?.asString() ?: return false
    return decl.classKind == ClassKind.CLASS &&
        decl.primaryConstructor != null &&
        Modifier.ABSTRACT !in decl.modifiers &&
        Modifier.SEALED !in decl.modifiers &&
        !fqn.startsWith("kotlin.") &&
        !fqn.startsWith("java.")
}

/**
 * Returns the [CollectionKind] (`List` or `Set`) of [type], or `null` when
 * [type] is not one of the supported wrapper types. Matches the wrapper set
 * `NestedRule` already recognises — `Map`, `Iterable`, arrays, etc. are
 * intentionally excluded.
 */
fun collectionKindOf(type: TypeInfo): CollectionKind? =
    when (type.declaration.qualifiedName?.asString()) {
        "kotlin.collections.List" -> CollectionKind.LIST
        "kotlin.collections.Set" -> CollectionKind.SET
        else -> null
    }

/**
 * Returns the [TypeInfo] of the first type argument of [type] when it is a
 * recognised single-element collection wrapper (see [collectionKindOf]).
 * Returns `null` for non-collections or for collections whose argument
 * cannot be resolved as a [KSClassDeclaration]-backed type (typically
 * unresolved or projected types — those should not be auto-mapped).
 */
fun elementTypeInfo(type: TypeInfo): TypeInfo? {
    val arg = type.ksType.arguments.firstOrNull() ?: return null
    val argType = arg.type?.resolve() ?: return null
    if (argType.declaration !is KSClassDeclaration) return null
    return TypeInfo.fromKSType(argType)
}
```

- [ ] **Step 2: Replace `NestedRule`'s private helpers with imports of the shared functions**

In `NestedRule.kt`:
- Add `import com.blu3berry.kraft.processor.util.collectionKindOf` and `import com.blu3berry.kraft.processor.util.elementTypeInfo` and `import com.blu3berry.kraft.processor.util.isMappableClass` near the top (in alphabetical order with the other `processor.util` imports).
- Delete the three `private fun isMappableClass`, `private fun collectionKindOf`, and `private fun elementTypeInfo` definitions at the bottom of the class. The `Modifier`, `ClassKind` imports become unused — remove those too.
- The call sites already use the same names; no call-site edits needed.

- [ ] **Step 3: Run the full kraft-ksp test suite**

Run: `./gradlew :kraft-ksp:jvmTest`
Expected: BUILD SUCCESSFUL — every existing nested-mapping test passes since the helper bodies are byte-identical.

- [ ] **Step 4: Run detekt to confirm no new warnings**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL — no fresh issues. The two `@Suppress("ReturnCount", "CyclomaticComplexMethod")` annotations on `NestedRule` private functions stay in place; they're attached to the methods that still exist (`resolveAutoDetected`, `resolveMapNested`).

- [ ] **Step 5: Commit**

```bash
git add kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/util/MappableTypes.kt \
        kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/descriptor/propertyresolver/rules/NestedRule.kt
git commit -m "refactor(kraft-core): extract isMappableClass / collectionKindOf / elementTypeInfo helpers"
```

---

### Task 2: Convert the deriver to a worklist walker that recurses through nested data classes

Adds depth-N support for nested data-class properties. Cycle-safe.

**Files:**
- Modify: `kraft-core/.../scanner/AutoEnumMappingDeriver.kt`
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt`

- [ ] **Step 1: Add the failing test for depth-2 nesting (no inner `@MapConfig`)**

Append to `EnumByNameAutoTest.kt`:

```kotlin
    @Test
    fun `depth-2 nested data class auto-derives without explicit inner @MapConfig`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE }

            data class User(val status: Status, val name: String)
            data class UserDto(val status: StatusDto, val name: String)
            data class Store(val user: User)
            data class StoreDto(val user: UserDto)

            @com.blu3berry.kraft.config.MapConfig(source = Store::class, target = StoreDto::class)
            object StoreMapper
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }
        val files = TestKspRunner.compileAndReturnGenerated(source)
        val joined = files.joinToString("\n") { it.readText() }
        assertThat(joined).contains("fun Status.toStatusDto()")
        assertThat(joined).contains("status = this.status.toStatusDto()")
    }
```

- [ ] **Step 2: Run the test to confirm it FAILS with the current flat deriver**

Run: `./gradlew :kraft-ksp:jvmTest --tests "com.blu3berry.kraft.mapenum.EnumByNameAutoTest.depth-2 nested data class auto-derives without explicit inner @MapConfig"`
Expected: FAIL — `Status → StatusDto` is never derived because `User → UserDto` is not a declared parent pair.

- [ ] **Step 3: Convert `AutoEnumMappingDeriver.derive()` to a worklist walker**

Replace the existing `derive(...)` body in `AutoEnumMappingDeriver.kt` with the worklist version. The full updated function and its supporting helpers:

```kotlin
fun derive(
    classMappings: List<ClassMappingScanResult>,
    configMappings: List<ConfigObjectScanResult>,
    existingEnumMappings: List<EnumMappingDescriptor>,
    sameModuleConverters: GlobalConverterRegistry,
): List<EnumMappingDescriptor> {
    val seeds = collectParentClassPairs(classMappings, configMappings)
    if (seeds.isEmpty()) return emptyList()

    val covered = coveredPairs(existingEnumMappings, sameModuleConverters)
    val out = LinkedHashMap<Pair<String, String>, EnumMappingDescriptor>()
    val visited = HashSet<Pair<String, String>>()
    val worklist = ArrayDeque<ClassPair>().apply { addAll(seeds) }

    while (worklist.isNotEmpty()) {
        val pair = worklist.removeFirst()
        val key = pair.fqKey() ?: continue
        if (!visited.add(key)) continue
        walkProperties(pair, covered, worklist, out)
    }
    return out.values.toList()
}

private fun walkProperties(
    pair: ClassPair,
    covered: Set<Pair<String, String>>,
    worklist: ArrayDeque<ClassPair>,
    out: MutableMap<Pair<String, String>, EnumMappingDescriptor>,
) {
    val sourceProps = pair.source.collectPropertyTypeRefs()
    val targetProps = pair.target.collectPropertyTypeRefs()
    for ((propName, targetProp) in targetProps) {
        val sourceProp = sourceProps[propName] ?: continue
        // Leaf: enum-pair property — synthesize when eligible.
        tryDeriveEnumDescriptor(sourceProp, targetProp, covered, out)
            ?.also { out.putIfAbsent(it.fqKey(), it) }
        // Recurse into nested data-class properties so the deriver inspects
        // their enum properties even when the user did not declare a
        // @MapConfig for the intermediate type.
        enqueueIfNestedClassPair(sourceProp, targetProp, worklist)
    }
}

private fun enqueueIfNestedClassPair(
    sourceProp: KSType,
    targetProp: KSType,
    worklist: ArrayDeque<ClassPair>,
) {
    val srcDecl = sourceProp.declaration as? KSClassDeclaration ?: return
    val tgtDecl = targetProp.declaration as? KSClassDeclaration ?: return
    val srcFq = srcDecl.qualifiedName?.asString() ?: return
    val tgtFq = tgtDecl.qualifiedName?.asString() ?: return
    if (srcFq == tgtFq) return
    val srcInfo = TypeInfo.fromKSType(sourceProp)
    val tgtInfo = TypeInfo.fromKSType(targetProp)
    if (!isMappableClass(srcInfo) || !isMappableClass(tgtInfo)) return
    if (srcDecl.containingFile == null || tgtDecl.containingFile == null) return
    worklist.addLast(ClassPair(srcDecl, tgtDecl))
}

private fun ClassPair.fqKey(): Pair<String, String>? {
    val s = source.qualifiedName?.asString() ?: return null
    val t = target.qualifiedName?.asString() ?: return null
    return s to t
}

private fun EnumMappingDescriptor.fqKey(): Pair<String, String> =
    sourceType.qualifiedName to targetType.qualifiedName
```

Add the imports at the top of the file:
```kotlin
import com.blu3berry.kraft.processor.util.isMappableClass
```

The existing private helpers `collectParentClassPairs`, `coveredPairs`, `tryDeriveEnumDescriptor`, `validateEnumPair`, and `KSClassDeclaration.isLocalEnum()` stay unchanged. The `data class ClassPair` stays unchanged.

- [ ] **Step 4: Run the depth-2 test, confirm PASS**

Run: `./gradlew :kraft-ksp:jvmTest --tests "com.blu3berry.kraft.mapenum.EnumByNameAutoTest.depth-2 nested data class auto-derives without explicit inner @MapConfig"`
Expected: PASS.

- [ ] **Step 5: Add the depth-3 test**

Append to `EnumByNameAutoTest.kt`:

```kotlin
    @Test
    fun `depth-3 nested auto-derives without intermediate @MapConfigs`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Country { US, UK }
            enum class CountryDto { US, UK }

            data class Address(val country: Country)
            data class AddressDto(val country: CountryDto)
            data class User(val address: Address)
            data class UserDto(val address: AddressDto)
            data class Store(val user: User)
            data class StoreDto(val user: UserDto)

            @com.blu3berry.kraft.config.MapConfig(source = Store::class, target = StoreDto::class)
            object StoreMapper
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }
        val joined = TestKspRunner.compileAndReturnGenerated(source)
            .joinToString("\n") { it.readText() }
        assertThat(joined).contains("fun Country.toCountryDto()")
        assertThat(joined).contains("country = this.country.toCountryDto()")
    }
```

- [ ] **Step 6: Run the depth-3 test, confirm PASS**

Expected: PASS — the worklist walks `Store → StoreDto` → `User → UserDto` → `Address → AddressDto` and synthesizes the leaf `Country → CountryDto`.

- [ ] **Step 7: Add the cycle test**

```kotlin
    @Test
    fun `cycle through self-referencing nested type terminates and auto-derives`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE }

            data class Node(val parent: Node?, val status: Status)
            data class NodeDto(val parent: NodeDto?, val status: StatusDto)

            @com.blu3berry.kraft.config.MapConfig(source = Node::class, target = NodeDto::class)
            object NodeMapper
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }
        val joined = TestKspRunner.compileAndReturnGenerated(source)
            .joinToString("\n") { it.readText() }
        assertThat(joined).contains("fun Status.toStatusDto()")
    }
```

- [ ] **Step 8: Run the cycle test, confirm PASS**

Expected: PASS — the visited set keyed by `(Node, NodeDto)` terminates the walk after one revisit. The presence of the auto-derived `Status → StatusDto` mapper proves the walker did inspect `Node`'s properties at least once.

- [ ] **Step 9: Run the full `:kraft-ksp:jvmTest` suite to confirm no regressions**

Run: `./gradlew :kraft-ksp:jvmTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/scanner/AutoEnumMappingDeriver.kt \
        kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt
git commit -m "feat(kraft-ksp): recurse the auto-enum deriver into nested data-class properties"
```

---

### Task 3: Unwrap `List<X>` and `Set<X>` element types in the recursion

`List<User>` / `Set<User>` should be peeled to recurse into `User`. Mirrors `NestedRule`'s collection support.

**Files:**
- Modify: `kraft-core/.../scanner/AutoEnumMappingDeriver.kt`
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt`

- [ ] **Step 1: Add the failing test for `Set<User>` wrapping**

```kotlin
    @Test
    fun `nested data class inside Set wrapper auto-derives the inner enum`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE }

            data class User(val status: Status, val name: String)
            data class UserDto(val status: StatusDto, val name: String)
            data class Store(val users: Set<User>)
            data class StoreDto(val users: Set<UserDto>)

            @com.blu3berry.kraft.config.MapConfig(source = Store::class, target = StoreDto::class)
            object StoreMapper
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }
        val joined = TestKspRunner.compileAndReturnGenerated(source)
            .joinToString("\n") { it.readText() }
        assertThat(joined).contains("fun Status.toStatusDto()")
    }
```

- [ ] **Step 2: Run, confirm FAIL**

Expected: the walker treats `Set<User>` as opaque; `User → UserDto` is never enqueued and `Status → StatusDto` is never derived.

- [ ] **Step 3: Update `enqueueIfNestedClassPair` to unwrap `List<X>` / `Set<X>`**

Add a `collectionKindOf` import:

```kotlin
import com.blu3berry.kraft.processor.util.collectionKindOf
import com.blu3berry.kraft.processor.util.elementTypeInfo
```

Replace `enqueueIfNestedClassPair` with a version that first attempts collection unwrapping:

```kotlin
private fun enqueueIfNestedClassPair(
    sourceType: KSType,
    targetType: KSType,
    worklist: ArrayDeque<ClassPair>,
) {
    val unwrappedSource = unwrapMatchingCollection(sourceType, targetType) ?: sourceType
    val unwrappedTarget = if (unwrappedSource === sourceType) targetType else unwrapMatchingCollection(targetType, sourceType) ?: return
    enqueueIfMappablePair(unwrappedSource, unwrappedTarget, worklist)
}

/**
 * Returns the element [KSType] of [type] when [type] and [other] are the
 * same kind of single-element collection wrapper (both `List`, both `Set`).
 * Returns `null` when either is not a collection or the kinds differ —
 * [NestedRule] doesn't auto-map mismatched wrappers and neither does the
 * deriver.
 */
private fun unwrapMatchingCollection(type: KSType, other: KSType): KSType? {
    val typeInfo = TypeInfo.fromKSType(type)
    val otherInfo = TypeInfo.fromKSType(other)
    val typeKind = collectionKindOf(typeInfo) ?: return null
    val otherKind = collectionKindOf(otherInfo) ?: return null
    if (typeKind != otherKind) return null
    return elementTypeInfo(typeInfo)?.ksType
}

private fun enqueueIfMappablePair(
    sourceType: KSType,
    targetType: KSType,
    worklist: ArrayDeque<ClassPair>,
) {
    val srcDecl = sourceType.declaration as? KSClassDeclaration ?: return
    val tgtDecl = targetType.declaration as? KSClassDeclaration ?: return
    val srcFq = srcDecl.qualifiedName?.asString() ?: return
    val tgtFq = tgtDecl.qualifiedName?.asString() ?: return
    if (srcFq == tgtFq) return
    val srcInfo = TypeInfo.fromKSType(sourceType)
    val tgtInfo = TypeInfo.fromKSType(targetType)
    if (!isMappableClass(srcInfo) || !isMappableClass(tgtInfo)) return
    if (srcDecl.containingFile == null || tgtDecl.containingFile == null) return
    worklist.addLast(ClassPair(srcDecl, tgtDecl))
}
```

The first overload now does either "direct mappable pair" or "unwrap matching collection then test". The split into `enqueueIfMappablePair` keeps the shared mappability gate in one place.

- [ ] **Step 4: Run the `Set<User>` test, confirm PASS**

Expected: PASS — the walker peels `Set<User>` and `Set<UserDto>` to `User`/`UserDto`, enqueues that pair, recurses, and derives `Status → StatusDto`.

- [ ] **Step 5: Add the `List<User>` test**

```kotlin
    @Test
    fun `nested data class inside List wrapper auto-derives the inner enum`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE }

            data class User(val status: Status, val name: String)
            data class UserDto(val status: StatusDto, val name: String)
            data class Store(val users: List<User>)
            data class StoreDto(val users: List<UserDto>)

            @com.blu3berry.kraft.config.MapConfig(source = Store::class, target = StoreDto::class)
            object StoreMapper
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }
        val joined = TestKspRunner.compileAndReturnGenerated(source)
            .joinToString("\n") { it.readText() }
        assertThat(joined).contains("fun Status.toStatusDto()")
    }
```

- [ ] **Step 6: Run, confirm PASS**

- [ ] **Step 7: Run the full kraft-ksp suite, confirm no regressions**

- [ ] **Step 8: Commit**

```bash
git add kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/scanner/AutoEnumMappingDeriver.kt \
        kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt
git commit -m "feat(kraft-ksp): unwrap List/Set element types when walking nested types in the deriver"
```

---

### Task 4: Auto-derive enum pairs inside `List<Enum>` / `Set<Enum>` element positions

When the property pair is `(List<SourceEnum>, List<TargetEnum>)`, the element types themselves are enums. The deriver should derive the leaf enum mapping directly. (`NestedRule` doesn't claim it because enum classes fail `isMappableClass`.)

**Files:**
- Modify: `kraft-core/.../scanner/AutoEnumMappingDeriver.kt`
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt`

- [ ] **Step 1: Add the failing test**

```kotlin
    @Test
    fun `List of enum auto-derives the element enum mapping`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE }

            data class Src(val statuses: List<Status>)
            data class Dst(val statuses: List<StatusDto>)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }
        val joined = TestKspRunner.compileAndReturnGenerated(source)
            .joinToString("\n") { it.readText() }
        assertThat(joined).contains("fun Status.toStatusDto()")
    }
```

- [ ] **Step 2: Run, confirm FAIL**

Expected: `tryDeriveEnumDescriptor` is never called for the `List<Status>`/`List<StatusDto>` pair because the walker only invokes it on the property's KSType directly, which is `List<Status>` (not an enum).

- [ ] **Step 3: Inside `walkProperties`, also try the unwrapped-element pair against `tryDeriveEnumDescriptor`**

Replace the body of `walkProperties` with:

```kotlin
private fun walkProperties(
    pair: ClassPair,
    covered: Set<Pair<String, String>>,
    worklist: ArrayDeque<ClassPair>,
    out: MutableMap<Pair<String, String>, EnumMappingDescriptor>,
) {
    val sourceProps = pair.source.collectPropertyTypeRefs()
    val targetProps = pair.target.collectPropertyTypeRefs()
    for ((propName, targetProp) in targetProps) {
        val sourceProp = sourceProps[propName] ?: continue
        deriveLeafAndElement(sourceProp, targetProp, covered, out)
        enqueueIfNestedClassPair(sourceProp, targetProp, worklist)
    }
}

private fun deriveLeafAndElement(
    sourceType: KSType,
    targetType: KSType,
    covered: Set<Pair<String, String>>,
    out: MutableMap<Pair<String, String>, EnumMappingDescriptor>,
) {
    tryDeriveEnumDescriptor(sourceType, targetType, covered, out)
        ?.also { out.putIfAbsent(it.fqKey(), it) }
    val sourceElement = unwrapMatchingCollection(sourceType, targetType) ?: return
    val targetElement = unwrapMatchingCollection(targetType, sourceType) ?: return
    tryDeriveEnumDescriptor(sourceElement, targetElement, covered, out)
        ?.also { out.putIfAbsent(it.fqKey(), it) }
}
```

The element-level call attempts the same enum-pair derivation on the unwrapped element types. `tryDeriveEnumDescriptor` already gates on `classKind == ENUM_CLASS`, so non-enum element pairs return null cleanly.

- [ ] **Step 4: Run the test, confirm PASS**

- [ ] **Step 5: Run the full `:kraft-ksp:jvmTest` suite, confirm no regressions**

- [ ] **Step 6: Commit**

```bash
git add kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/scanner/AutoEnumMappingDeriver.kt \
        kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt
git commit -m "feat(kraft-ksp): derive enum mappings for List/Set element-position enum pairs"
```

---

### Task 5: Update the docs

**Files:**
- Modify: `docs/user-guide/enum-mapping.md`

- [ ] **Step 1: Replace the "explicit parent" requirement bullet**

Open `docs/user-guide/enum-mapping.md`. Find the "When you do NOT need `@MapEnum`" section. Replace the body with:

```markdown
## When you do NOT need `@MapEnum`

Kraft auto-generates an enum mapper at compile time when **all** of these
hold:

- The source and target enum types are both declared in the **current
  module** (i.e. KSP is processing both files this round).
- The pair is **reachable** from at least one declared `@MapConfig` /
  `@MapTo` parent — directly as a property type, transitively through any
  nested data-class properties, or through `List<…>` / `Set<…>` element
  positions. Intermediate data classes do NOT need their own `@MapConfig`.
- Both property occurrences are **non-nullable** (`Status`, not `Status?`).
- Every source-enum entry has a same-named target-enum entry. Extra
  entries on the target are fine.

When all four conditions hold the parent mapper compiles without any
`@MapEnum` declaration, and the auto-derived mapper is also published as a
`@KraftConverterDelegate` so downstream modules can import it. If the
parent has `@MapReverse`, both directions auto-derive when the by-name
pairing also succeeds in reverse.

If any of the conditions does not hold (cross-module pair, mismatched
entry names, nullable properties, custom `fieldMappings`), declare
`@MapEnum` explicitly — Kraft will not silently guess.
```

- [ ] **Step 2: Commit**

```bash
git add docs/user-guide/enum-mapping.md
git commit -m "docs: update auto-enum-by-name contract to cover transitive nested + collection element discovery"
```

---

### Task 6: Final verification

- [ ] **Step 1: Run the full `:kraft-ksp:jvmTest` and `:kraft-core:jvmTest` suites with `--rerun-tasks`**

Run: `./gradlew :kraft-ksp:jvmTest :kraft-core:jvmTest --rerun-tasks`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: If any cleanup was needed, commit it separately and re-run step 1**

---

## Self-review notes

- **Spec coverage:** the test matrix maps to one test per task (depth-2 in Task 2, depth-3 in Task 2, cycle in Task 2, `Set<User>` in Task 3, `List<User>` in Task 3, `List<Enum>` in Task 4) — six new tests covering the five matrix rows plus depth-3 as a stronger version of depth-2.
- **API surface verification:** `TypeInfo`, `KSType.declaration`, `KSType.arguments`, `arg.type?.resolve()`, `KSClassDeclaration.containingFile`, `Modifier.ABSTRACT`, `Modifier.SEALED`, `ClassKind.CLASS`, `ClassKind.ENUM_CLASS`, `CollectionKind.LIST`, `CollectionKind.SET` all confirmed to exist in the current branch (`feat-auto-enum-by-name` HEAD `031595f`). `kotlin.collections.List` / `kotlin.collections.Set` qualified-name strings come straight from `NestedRule`.
- **`NestedRule` alignment risk:** the deriver's `enqueueIfNestedClassPair` calls the same `isMappableClass` predicate `NestedRule` uses, plus the `containingFile != null` check (which `NestedRule` doesn't enforce — but the deriver legitimately needs it because cross-module classes have no source we can observe properties on). The shared utility extraction in Task 1 prevents drift.
- **Detekt risk:** the new `walkProperties` / `enqueueIfNestedClassPair` / `unwrapMatchingCollection` functions are short and won't trip the complexity / return-count rules. The `derive()` body shrinks because the inner loop moves into `walkProperties`.

## Out of scope

- `Map<K, V>` recursion (only `List` / `Set` per `NestedRule`'s existing wrapper set).
- Recursing through types that aren't claimed by `NestedRule` (sealed-class hierarchies, abstract base classes, etc.).
- Cross-module nested types (still scoped via `containingFile != null`).
- Resolver-rule redesign (deferred; the worklist deriver covers the same observable behavior with no resolver-chain changes).
