# Auto-Generate Same-Module Enum Mappers By Name — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a parent `@MapConfig` / `@MapTo` mapper has a property whose source and target types are two different enum classes both declared in the current module, and every source entry has a same-named target entry, automatically synthesize an enum mapper (and the synthetic-converter trampoline) so the user does not have to declare a `@MapEnum` for the trivial case.

**Architecture:** Add a pre-pass after the scanners run in `AutoMapperProcessor.process()` that walks every parent mapping's `(source, target)` class pair (forward and reverse), finds property pairs where both types are enum classes in the same module, attempts a strict by-name pairing of source entries onto target entries, and — on success — appends a synthetic `EnumMappingDescriptor` to the existing `enumMappings` list. From there, the existing pipeline (`enumMappingsToConverterEntries` → `mergeWithEnumAmbiguityCheck` → `EnumMapperGenerator`) already handles registry, codegen, and `@KraftConverterDelegate` emission. No resolver-rule changes are required: the existing `GlobalConverterRule` will pick up the new synthetic registry entries during property resolution.

**Tech Stack:** Kotlin / KSP (Symbol Processing API) / KotlinPoet / kotlin-compile-testing (tests).

---

## Pre-cleared deviations

(None yet — first execution.)

---

## File Structure

**New files:**

- `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/scanner/AutoEnumMappingDeriver.kt`
  Pure-logic deriver: takes the scanner outputs (`classMappings`, `configMappings`), the existing user-declared `enumMappings`, and the same-module `@KraftConverter` registry, and returns a list of newly derived `EnumMappingDescriptor`s for property-level enum→enum pairs that are eligible for by-name auto-pairing.

- `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt`
  Compile-testing-based regression suite covering the eight scenarios listed under "Test matrix."

**Modified files:**

- `kraft-ksp/src/main/kotlin/com/blu3berry/kraft/AutoMapperProcessor.kt`
  Insert the deriver call between scanning and the existing `enumMappingsToConverterEntries(...)` call. Concatenate derived descriptors onto `enumMappings` so the rest of the pipeline picks them up unchanged.

- `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumAutoResolveTest.kt`
  No code changes; only used to confirm existing behavior is preserved (the previously hand-declared `@MapEnum` test still passes; the new auto-by-name path doesn't accidentally register a duplicate when `@MapEnum` is also declared).

---

## Test matrix (acceptance criteria)

The test suite must cover all eight cases. Each is a separate `@Test` method. Each test uses `TestKspRunner.compileAndReturnGenerated(...)` (or `compileWithUpstream` for the cross-module case) and asserts on the generated mapper file contents.

| # | Scenario | Expected behavior |
|---|----------|-------------------|
| 1 | Same-module enums, identical entry sets | Generated `Status.toStatusDto()` exists; parent mapper invokes it |
| 2 | Same-module enums, source has entry NOT in target | No auto-gen; existing type-mismatch error fires |
| 3 | Same-module enums, target has entries source doesn't | Auto-gen succeeds (extra target entries unused); parent mapper compiles |
| 4 | User-declared `@MapEnum` for the same pair | User declaration wins; auto-derive skipped (no duplicate-mapper error) |
| 5 | Cross-module enum pair (source enum on classpath only) | No auto-gen; user must declare upstream `@MapEnum` (existing path) |
| 6 | Parent `@MapReverse` with bidirectionally-alignable enums | Both `Status.toStatusDto()` and `StatusDto.toStatus()` auto-gen |
| 7 | Two different parent mappers reference the same enum pair | Exactly one mapper file is generated (deduped by ConverterTypeKey) |
| 8 | Nested property: `Store.user.status` enum mismatch | Auto-derive walks nested-property type pairs and resolves |

---

### Task 1: Skeleton + Test 1 (happy path, same-module identical entries)

**Files:**
- Create: `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/scanner/AutoEnumMappingDeriver.kt`
- Modify: `kraft-ksp/src/main/kotlin/com/blu3berry/kraft/AutoMapperProcessor.kt`
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt`

- [ ] **Step 1: Write the failing test for the happy path**

Create `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt`:

```kotlin
package com.blu3berry.kraft.mapenum

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * `@MapEnum` is NOT required when both enums live in the same module and every
 * source entry has a target entry of the same name. The deriver synthesizes an
 * EnumMappingDescriptor up front; the rest of the pipeline (synthetic registry
 * + EnumMapperGenerator) is unchanged.
 */
@OptIn(ExperimentalCompilerApi::class)
class EnumByNameAutoTest {

    @Test
    fun `same-module enums with identical entries auto-generate without @MapEnum`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE }

            data class Src(val status: Status, val name: String)
            data class Dst(val status: StatusDto, val name: String)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }

        val files = TestKspRunner.compileAndReturnGenerated(source)
        val parent = files.first { "ToDstMapper" in it.name }.readText()
        val enumMapper = files.first { "Status_To_StatusDto_EnumMapper" in it.name }.readText()

        assertThat(parent).contains("status = this.status.toStatusDto()")
        assertThat(enumMapper).contains("Status.ACTIVE -> StatusDto.ACTIVE")
        assertThat(enumMapper).contains("Status.INACTIVE -> StatusDto.INACTIVE")
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

Run: `./gradlew :kraft-ksp:jvmTest --tests "com.blu3berry.kraft.mapenum.EnumByNameAutoTest.same-module enums with identical entries auto-generate without @MapEnum"`
Expected: FAIL — compilation aborts with the existing "Type mismatch for property 'status'" diagnostic, because nothing currently auto-derives the `Status → StatusDto` mapper.

- [ ] **Step 3: Create the deriver skeleton**

Create `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/scanner/AutoEnumMappingDeriver.kt`:

```kotlin
package com.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Nullability
import com.blu3berry.kraft.model.TypeInfo
import com.blu3berry.kraft.model.descriptor.EnumEntryMapping
import com.blu3berry.kraft.model.descriptor.EnumMappingDescriptor
import com.blu3berry.kraft.model.scan.ClassMappingScanResult
import com.blu3berry.kraft.model.scan.ConfigObjectScanResult
import com.blu3berry.kraft.model.scan.ConverterTypeKey
import com.blu3berry.kraft.model.scan.GlobalConverterRegistry

/**
 * Walks every parent `@MapConfig` / `@MapTo` mapping pair and synthesizes an
 * [EnumMappingDescriptor] for every property pair whose source and target are
 * two different enum classes, both declared in the current module, when every
 * source entry has a same-named target entry.
 *
 * Skips pairs that are already covered by a user-declared `@MapEnum`
 * descriptor or a hand-written `@KraftConverter`. Skips cross-module pairs
 * (one or both enums on the classpath only). Pairs that don't auto-pair are
 * left alone — the existing `RequiredFieldErrorRule` path will still emit the
 * "type mismatch" diagnostic, which is the correct outcome.
 *
 * Output is keyed by `(sourceFqName, targetFqName)` so two parent mappers
 * referencing the same enum pair don't produce duplicate descriptors.
 */
class AutoEnumMappingDeriver(
    private val logger: KSPLogger,
) {
    fun derive(
        classMappings: List<ClassMappingScanResult>,
        configMappings: List<ConfigObjectScanResult>,
        existingEnumMappings: List<EnumMappingDescriptor>,
        sameModuleConverters: GlobalConverterRegistry,
    ): List<EnumMappingDescriptor> {
        // Filled in over Tasks 2-7. Task 1 returns emptyList() so the
        // skeleton compiles and existing tests stay green.
        return emptyList()
    }
}
```

- [ ] **Step 4: Wire the deriver into AutoMapperProcessor**

In `kraft-ksp/src/main/kotlin/com/blu3berry/kraft/AutoMapperProcessor.kt`, add an import:

```kotlin
import com.blu3berry.kraft.processor.scanner.AutoEnumMappingDeriver
```

Then change the block that currently reads:

```kotlin
        val classMappings = ClassAnnotationScanner(resolver, logger).scan()
        val configMappings = ConfigObjectScanner(resolver, logger).scan()
        val enumMappings = EnumMapScanner(resolver, logger).scan()
        val handWrittenConverters = GlobalConverterScanner(resolver, logger).scan()
```

to:

```kotlin
        val classMappings = ClassAnnotationScanner(resolver, logger).scan()
        val configMappings = ConfigObjectScanner(resolver, logger).scan()
        val declaredEnumMappings = EnumMapScanner(resolver, logger).scan()
        val handWrittenConverters = GlobalConverterScanner(resolver, logger).scan()

        // Derive @MapEnum-equivalent descriptors for property-level enum→enum
        // pairs that auto-pair by name. Appended to the user-declared list so
        // the rest of the pipeline treats them identically.
        val derivedEnumMappings = AutoEnumMappingDeriver(logger).derive(
            classMappings = classMappings,
            configMappings = configMappings,
            existingEnumMappings = declaredEnumMappings,
            sameModuleConverters = handWrittenConverters,
        )
        val enumMappings = declaredEnumMappings + derivedEnumMappings
```

The downstream code (`enumMappingsToConverterEntries`, `mergeWithEnumAmbiguityCheck`, `EnumMapperGenerator.generate`, `DescriptorBuilder.build`) does not change; `enumMappings` is the only seam.

- [ ] **Step 5: Implement the deriver body for the happy path**

Replace the `derive` body in `AutoEnumMappingDeriver.kt` with:

```kotlin
    fun derive(
        classMappings: List<ClassMappingScanResult>,
        configMappings: List<ConfigObjectScanResult>,
        existingEnumMappings: List<EnumMappingDescriptor>,
        sameModuleConverters: GlobalConverterRegistry,
    ): List<EnumMappingDescriptor> {
        val pairs = collectParentClassPairs(classMappings, configMappings)
        if (pairs.isEmpty()) return emptyList()

        val covered = coveredPairs(existingEnumMappings, sameModuleConverters)
        val out = LinkedHashMap<Pair<String, String>, EnumMappingDescriptor>()

        for ((source, target) in pairs) {
            val targetProps = target.declarationsByProperty()
            for ((propName, targetProp) in targetProps) {
                val sourceProp = source.declarationsByProperty()[propName] ?: continue
                val descriptor = tryDeriveEnumDescriptor(
                    sourceProp.type, targetProp.type, source, target, covered, out
                ) ?: continue
                val key = descriptor.sourceType.qualifiedName to descriptor.targetType.qualifiedName
                out.putIfAbsent(key, descriptor)
            }
        }
        return out.values.toList()
    }

    private data class ClassPair(val source: KSClassDeclaration, val target: KSClassDeclaration)

    private fun collectParentClassPairs(
        classMappings: List<ClassMappingScanResult>,
        configMappings: List<ConfigObjectScanResult>,
    ): List<ClassPair> {
        // Filled out in Task 6 to also include reverse directions when
        // hasReverse is true. Task 1 forward-only is enough to pass test 1.
        val pairs = mutableListOf<ClassPair>()
        for (m in classMappings) pairs += ClassPair(m.sourceType, m.targetType)
        for (m in configMappings) pairs += ClassPair(m.sourceType, m.targetType)
        return pairs
    }

    private fun coveredPairs(
        existingEnumMappings: List<EnumMappingDescriptor>,
        sameModuleConverters: GlobalConverterRegistry,
    ): Set<Pair<String, String>> {
        val covered = HashSet<Pair<String, String>>()
        for (m in existingEnumMappings) {
            covered += m.sourceType.qualifiedName to m.targetType.qualifiedName
        }
        for ((key, _) in sameModuleConverters.entries) {
            covered += key.sourceFqName to key.targetFqName
        }
        return covered
    }

    private fun tryDeriveEnumDescriptor(
        sourceType: KSType,
        targetType: KSType,
        sourceParent: KSClassDeclaration,
        targetParent: KSClassDeclaration,
        covered: Set<Pair<String, String>>,
        already: Map<Pair<String, String>, EnumMappingDescriptor>,
    ): EnumMappingDescriptor? {
        // Auto-derivation only handles non-nullable enum property pairs.
        // Nullable property types require the user to declare @MapEnum
        // explicitly so the nullable-key shape is intentional. Platform
        // types are likewise out of scope (the same rule the rest of the
        // pipeline applies).
        if (sourceType.nullability != Nullability.NOT_NULL) return null
        if (targetType.nullability != Nullability.NOT_NULL) return null

        val sourceDecl = sourceType.declaration as? KSClassDeclaration ?: return null
        val targetDecl = targetType.declaration as? KSClassDeclaration ?: return null
        if (sourceDecl.classKind != ClassKind.ENUM_CLASS) return null
        if (targetDecl.classKind != ClassKind.ENUM_CLASS) return null
        if (sourceDecl.qualifiedName?.asString() == targetDecl.qualifiedName?.asString()) return null

        // Scope to the same module: KSP returns null containingFile for
        // declarations that live on the compile classpath. We only auto-derive
        // when both enums are in the current source set.
        if (sourceDecl.containingFile == null || targetDecl.containingFile == null) return null

        val sourceFq = sourceDecl.qualifiedName?.asString() ?: return null
        val targetFq = targetDecl.qualifiedName?.asString() ?: return null
        val pairKey = sourceFq to targetFq
        if (pairKey in covered) return null
        if (pairKey in already.keys) return null

        val sourceEntries = sourceDecl.enumEntryNames()
        val targetEntries = targetDecl.enumEntryNames().toSet()
        val unmappable = sourceEntries.filterNot { it in targetEntries }
        if (unmappable.isNotEmpty()) return null

        val sourceTypeInfo = TypeInfo.fromKSType(sourceType)
        val targetTypeInfo = TypeInfo.fromKSType(targetType)
        val entries = sourceEntries.map { EnumEntryMapping(source = it, target = it) }
        return EnumMappingDescriptor(
            sourceType = sourceTypeInfo,
            targetType = targetTypeInfo,
            entries = entries,
            // No KSClassDeclaration to anchor diagnostics at — derived
            // descriptors are synthetic. The enum source/target files
            // themselves still appear in originatingFiles via TypeInfo.
            declaration = null,
        )
    }

    private fun KSClassDeclaration.enumEntryNames(): List<String> =
        declarations
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.ENUM_ENTRY }
            .map { it.simpleName.asString() }
            .toList()

    private fun KSClassDeclaration.declarationsByProperty(): Map<String, com.blu3berry.kraft.processor.util.PropertyTypeRef> =
        com.blu3berry.kraft.processor.util.collectPropertyTypeRefs(this)
```

The `KSType.makeNotNull()` extension is provided by KSP and strips nullability. The `PropertyTypeRef` helper is created in step 6. Until then, the class won't compile. That's deliberate — the next step adds it.

- [ ] **Step 6: Add the property-collection utility**

Create `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/util/PropertyTypeRefs.kt`:

```kotlin
package com.blu3berry.kraft.processor.util

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

/**
 * Lightweight `(propertyName -> resolvedType)` view of a class's declared
 * properties. Used by the auto-enum deriver to find property pairs by name
 * without taking on `DescriptorBuilder`'s rename/ignore machinery — this
 * deriver is type-driven and only matches by literal property name.
 */
data class PropertyTypeRef(val name: String, val type: KSType)

/**
 * Returns all declared properties of [decl] keyed by simple name with their
 * resolved [KSType]. Mirrors the access pattern used elsewhere in
 * `kraft-core` (see `ClassAnnotationScanner`).
 */
fun collectPropertyTypeRefs(decl: KSClassDeclaration): Map<String, PropertyTypeRef> {
    val map = LinkedHashMap<String, PropertyTypeRef>()
    for (prop in decl.getDeclaredProperties()) {
        val name = prop.simpleName.asString()
        val type = prop.type.resolve()
        map[name] = PropertyTypeRef(name, type)
    }
    return map
}
```

- [ ] **Step 7: Run the failing test and confirm it now passes**

Run: `./gradlew :kraft-ksp:jvmTest --tests "com.blu3berry.kraft.mapenum.EnumByNameAutoTest.same-module enums with identical entries auto-generate without @MapEnum"`
Expected: PASS.

- [ ] **Step 8: Run the full kraft-ksp test suite to confirm no regressions**

Run: `./gradlew :kraft-ksp:jvmTest`
Expected: PASS — including all `EnumAutoResolveTest` cases.

- [ ] **Step 9: Commit**

```bash
git add kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/scanner/AutoEnumMappingDeriver.kt \
        kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/util/PropertyTypeRefs.kt \
        kraft-ksp/src/main/kotlin/com/blu3berry/kraft/AutoMapperProcessor.kt \
        kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt
git commit -m "feat(kraft-ksp): auto-derive same-module enum mappers when entries pair by name"
```

---

### Task 2: Test 2 — unmappable source entry leaves the existing error path intact

**Files:**
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt`

- [ ] **Step 1: Add the failing test**

Append to `EnumByNameAutoTest.kt`:

```kotlin
    @Test
    fun `same-module enums with a source entry missing in target produce the existing type-mismatch error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, BLOCKED }
            enum class StatusDto { ACTIVE, BANNED }

            data class Src(val status: Status)
            data class Dst(val status: StatusDto)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compile(source)
        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("Type mismatch for property 'status'")
    }
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :kraft-ksp:jvmTest --tests "com.blu3berry.kraft.mapenum.EnumByNameAutoTest.same-module enums with a source entry missing in target produce the existing type-mismatch error"`
Expected: PASS — the deriver's `unmappable.isNotEmpty()` guard already returns `null`, so no synthetic descriptor is created and the existing `RequiredFieldErrorRule` emits the type-mismatch diagnostic.

- [ ] **Step 3: Commit**

```bash
git add kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt
git commit -m "test(kraft-ksp): unmappable source entry preserves type-mismatch error"
```

---

### Task 3: Test 3 — extra target entries are tolerated

**Files:**
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt`

- [ ] **Step 1: Add the failing test**

Append to `EnumByNameAutoTest.kt`:

```kotlin
    @Test
    fun `extra target entries do not block auto-derivation`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE, PENDING }

            data class Src(val status: Status)
            data class Dst(val status: StatusDto)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }
        val mapper = TestKspRunner.compileAndReturnGenerated(source)
            .first { "Status_To_StatusDto_EnumMapper" in it.name }
            .readText()
        assertThat(mapper).contains("Status.ACTIVE -> StatusDto.ACTIVE")
        assertThat(mapper).contains("Status.INACTIVE -> StatusDto.INACTIVE")
    }
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :kraft-ksp:jvmTest --tests "com.blu3berry.kraft.mapenum.EnumByNameAutoTest.extra target entries do not block auto-derivation"`
Expected: PASS — the deriver only checks that source entries are a subset of target entries, so extra target entries are tolerated.

- [ ] **Step 3: Commit**

```bash
git add kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt
git commit -m "test(kraft-ksp): auto-derivation tolerates extra target enum entries"
```

---

### Task 4: Test 4 — user-declared `@MapEnum` for the same pair wins

**Files:**
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt`

- [ ] **Step 1: Add the failing test**

Append to `EnumByNameAutoTest.kt`:

```kotlin
    @Test
    fun `user-declared @MapEnum for the pair suppresses auto-derivation`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE }

            // User explicitly declares the same pair the deriver would otherwise
            // pick up. This must NOT result in a duplicate-pair compile error.
            @com.blu3berry.kraft.config.MapEnum(source = Status::class, target = StatusDto::class)
            object StatusMapping

            data class Src(val status: Status)
            data class Dst(val status: StatusDto)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }
        // Exactly one enum mapper file — no duplicate from the deriver.
        val files = TestKspRunner.compileAndReturnGenerated(source)
            .filter { "Status_To_StatusDto_EnumMapper" in it.name }
        assertThat(files).hasSize(1)
    }
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :kraft-ksp:jvmTest --tests "com.blu3berry.kraft.mapenum.EnumByNameAutoTest.user-declared @MapEnum for the pair suppresses auto-derivation"`
Expected: PASS — the deriver's `coveredPairs(...)` set contains the user-declared pair, so the synthetic descriptor is not added.

- [ ] **Step 3: Commit**

```bash
git add kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt
git commit -m "test(kraft-ksp): user @MapEnum suppresses auto-derivation for the same pair"
```

---

### Task 5: Test 5 — cross-module pair does not auto-derive

**Files:**
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt`

- [ ] **Step 1: Add the failing test**

Append to `EnumByNameAutoTest.kt`:

```kotlin
    @Test
    fun `cross-module enum pair does not auto-derive`() {
        val upstream = SourceFile.kotlin(
            "Upstream.kt",
            """
            package upstream

            enum class Status { ACTIVE, INACTIVE }
            """
        )
        val consumer = SourceFile.kotlin(
            "Models.kt",
            """
            package consumer

            enum class StatusDto { ACTIVE, INACTIVE }

            data class Src(val status: upstream.Status)
            data class Dst(val status: StatusDto)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compileWithUpstream(
            upstreamSources = listOf(upstream),
            consumerSources = listOf(consumer),
            upstreamKspOptions = mapOf("kraft.moduleId" to "upstream"),
            consumerKspOptions = mapOf("kraft.moduleId" to "consumer"),
        )
        // Cross-module: deriver requires both enums to be in the current
        // module. The user must publish an upstream @MapEnum if they want
        // the cross-module path to work; absent that, this is a hard error.
        assertThat(result.consumer.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.consumer.messages).contains("Type mismatch for property 'status'")
    }
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :kraft-ksp:jvmTest --tests "com.blu3berry.kraft.mapenum.EnumByNameAutoTest.cross-module enum pair does not auto-derive"`
Expected: PASS — `upstream.Status.containingFile` is null in the consumer compilation, so the deriver returns `null` for the pair.

- [ ] **Step 3: Commit**

```bash
git add kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt
git commit -m "test(kraft-ksp): cross-module enum pair is not auto-derived"
```

---

### Task 6: Test 6 — `@MapReverse` derives both directions

**Files:**
- Modify: `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/scanner/AutoEnumMappingDeriver.kt`
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt`

- [ ] **Step 1: Add the failing test**

Append to `EnumByNameAutoTest.kt`:

```kotlin
    @Test
    fun `parent @MapReverse derives both directions when entries align both ways`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE }

            data class Src(val status: Status)
            data class Dst(val status: StatusDto)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            @com.blu3berry.kraft.config.MapReverse
            object SrcMapper
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }
        val files = TestKspRunner.compileAndReturnGenerated(source)
            .map { it.name }
        assertThat(files).contains("Status_To_StatusDto_EnumMapper.kt")
        assertThat(files).contains("StatusDto_To_Status_EnumMapper.kt")
    }
```

- [ ] **Step 2: Run the test to confirm it fails**

Run: `./gradlew :kraft-ksp:jvmTest --tests "com.blu3berry.kraft.mapenum.EnumByNameAutoTest.parent @MapReverse derives both directions when entries align both ways"`
Expected: FAIL — the Task 1 deriver only walks forward `(source, target)` pairs from each scanner result; the reverse pair is never considered.

- [ ] **Step 3: Update `collectParentClassPairs` to honour `hasReverse`**

In `AutoEnumMappingDeriver.kt` replace the existing `collectParentClassPairs(...)` with:

```kotlin
    private fun collectParentClassPairs(
        classMappings: List<ClassMappingScanResult>,
        configMappings: List<ConfigObjectScanResult>,
    ): List<ClassPair> {
        val pairs = mutableListOf<ClassPair>()
        for (m in classMappings) {
            pairs += ClassPair(m.sourceType, m.targetType)
            if (m.hasReverse) pairs += ClassPair(m.targetType, m.sourceType)
        }
        for (m in configMappings) {
            pairs += ClassPair(m.sourceType, m.targetType)
            if (m.hasReverse) pairs += ClassPair(m.targetType, m.sourceType)
        }
        return pairs
    }
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew :kraft-ksp:jvmTest --tests "com.blu3berry.kraft.mapenum.EnumByNameAutoTest.parent @MapReverse derives both directions when entries align both ways"`
Expected: PASS — both `Status → StatusDto` and `StatusDto → Status` are now considered, both pair-by-name, both descriptors are emitted.

- [ ] **Step 5: Run the full kraft-ksp suite**

Run: `./gradlew :kraft-ksp:jvmTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/scanner/AutoEnumMappingDeriver.kt \
        kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt
git commit -m "feat(kraft-ksp): auto-derive reverse direction when parent has @MapReverse"
```

---

### Task 7: Test 7 — same enum pair across multiple parents derives only once

**Files:**
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt`

- [ ] **Step 1: Add the failing test**

Append to `EnumByNameAutoTest.kt`:

```kotlin
    @Test
    fun `same enum pair referenced by two parent mappers is derived exactly once`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE }

            data class SrcA(val status: Status)
            data class DstA(val status: StatusDto)
            data class SrcB(val status: Status, val name: String)
            data class DstB(val status: StatusDto, val name: String)

            @com.blu3berry.kraft.config.MapConfig(source = SrcA::class, target = DstA::class)
            object MapperA

            @com.blu3berry.kraft.config.MapConfig(source = SrcB::class, target = DstB::class)
            object MapperB
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }
        val enumMappers = TestKspRunner.compileAndReturnGenerated(source)
            .filter { "Status_To_StatusDto_EnumMapper" in it.name }
        assertThat(enumMappers).hasSize(1)
    }
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :kraft-ksp:jvmTest --tests "com.blu3berry.kraft.mapenum.EnumByNameAutoTest.same enum pair referenced by two parent mappers is derived exactly once"`
Expected: PASS — the deriver's `out` map is keyed by `(sourceFqName, targetFqName)` and uses `putIfAbsent`, so the second parent's hit is a no-op.

- [ ] **Step 3: Commit**

```bash
git add kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt
git commit -m "test(kraft-ksp): same enum pair across parents derives a single mapper"
```

---

### Task 8: Test 8 — nested enum mismatch via parent's nested property

**Files:**
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt`

- [ ] **Step 1: Add the failing test**

Append to `EnumByNameAutoTest.kt`:

```kotlin
    @Test
    fun `nested property enum mismatch auto-derives when an inner @MapConfig also exists`() {
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

            @com.blu3berry.kraft.config.MapConfig(source = User::class, target = UserDto::class)
            object UserMapper

            @com.blu3berry.kraft.config.MapConfig(source = Store::class, target = StoreDto::class)
            object StoreMapper
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }
        val mapper = TestKspRunner.compileAndReturnGenerated(source)
            .first { "ToUserDtoMapper" in it.name }
            .readText()
        assertThat(mapper).contains("status = this.status.toStatusDto()")
    }
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :kraft-ksp:jvmTest --tests "com.blu3berry.kraft.mapenum.EnumByNameAutoTest.nested property enum mismatch auto-derives when an inner @MapConfig also exists"`
Expected: PASS — `User → UserDto` is itself a parent pair (via `UserMapper`), the deriver inspects its properties, finds `Status → StatusDto`, auto-derives. `Store → StoreDto` resolves nested-mapping to `UserMapper.toUserDto(this)` via the existing `NestedRule`, which compiles cleanly because `UserMapper` is now satisfied by the auto-derived enum mapper.

This test does NOT require the deriver to recurse into nested types itself; it only needs the inner `@MapConfig` (`UserMapper`) to be in the parent-pair list. The `Store → StoreDto` parent does not introduce any new enum pair to consider.

- [ ] **Step 3: Commit**

```bash
git add kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt
git commit -m "test(kraft-ksp): nested mappers benefit from auto-derived enum mappers"
```

---

### Task 9: Documentation update

**Files:**
- Modify: `docs/` — locate the existing enum-mapping doc (see step 1).

- [ ] **Step 1: Locate the relevant doc**

Run: `grep -rln "@MapEnum" docs/ 2>/dev/null`
Expected: a file (likely `docs/enum-mapping.md` or a chapter under `docs/usage/`). If multiple, pick the one that introduces `@MapEnum` for the first time.

If no doc file exists for `@MapEnum`, skip this task entirely and add a one-line note to the next release-notes / CHANGELOG entry instead.

- [ ] **Step 2: Add the auto-derivation note**

Edit the located doc and add this section near the introduction of `@MapEnum`:

```markdown
## When you do NOT need `@MapEnum`

Kraft auto-generates an enum mapper at compile time when **all** of these
hold:

- The source and target enum types are both declared in the **current
  module** (i.e. KSP is processing both files this round).
- The pair is referenced by at least one `@MapConfig` or `@MapTo` parent
  mapper as a property type.
- Every source-enum entry has a same-named target-enum entry. Extra entries
  on the target are fine.

In that case the parent mapper compiles without any `@MapEnum` declaration.
If any of the conditions above does not hold (cross-module pair, mismatched
entry names, custom `fieldMappings`), declare `@MapEnum` explicitly — Kraft
will not silently guess.
```

- [ ] **Step 3: Commit**

```bash
git add docs/
git commit -m "docs: explain the same-module by-name auto @MapEnum derivation"
```

---

### Task 10: Final verification

- [ ] **Step 1: Run the full kraft-ksp + kraft-core test suites**

Run: `./gradlew :kraft-ksp:jvmTest :kraft-core:jvmTest`
Expected: PASS.

- [ ] **Step 2: Run a clean rebuild to verify codegen reproducibility**

Run: `./gradlew clean :kraft-ksp:jvmTest`
Expected: PASS — exercises the deriver from a cold cache and re-confirms generated FQNs.

- [ ] **Step 3: Final commit if any cleanup was needed**

If steps 1 and 2 both pass with no changes, no commit is needed. Otherwise commit any cleanup separately and re-run step 1.

---

## Self-review notes

- **Spec coverage:** Tests 1–8 each map to a row of the test matrix at the top.
- **Type / API consistency:** `EnumMappingDescriptor`, `ConverterTypeKey`, `ClassMappingScanResult`, `ConfigObjectScanResult`, `GlobalConverterRegistry`, `EnumEntryMapping`, `TypeInfo.fromKSType`, `TypeInfo.qualifiedName`, `KSClassDeclaration.containingFile`, and `KSP.getDeclaredProperties()` are all verified to exist in the current branch (`feat-enum-auto-resolve` head). Generated file naming `<Source>_<Target>_EnumMapper.kt` is verified at `kraft-ksp/.../EnumMapperGenerator.kt` line ~70 (uses `CodeGenUtils.buildFileName`).
- **TestKspRunner API:** `compile`, `compileAndReturnGenerated`, `compileWithUpstream` all confirmed at `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/TestKspRunner.kt`.
- **`@MapReverse` annotation FQN:** the test in Task 6 references `com.blu3berry.kraft.config.MapReverse`. Verify before execution by running `grep -rn 'package com.blu3berry.kraft.config' kraft-annotations/` — adjust if the package differs.
- **Nullability handling:** Nullable enum property types are explicitly out of scope (the deriver returns `null`). The user must declare `@MapEnum` for nullable enum properties so the nullable-keyed registry entry is created intentionally.

## Out of scope

- Recursive auto-derivation through nested types that are NOT themselves a parent `@MapConfig` / `@MapTo`. This plan only inspects parent pairs that are already declared mappers. Adding "discover enum pairs through arbitrary class graphs" would require walking property types transitively — explicitly deferred.
- Auto-deriving when one side is an enum and the other is `String` / `Int` / a sealed class. That's a separate "enum to scalar" feature and would need its own design.
- Cross-module auto-derivation. Users who want cross-module discoverability must publish an explicit upstream `@MapEnum`; this plan only changes same-module ergonomics.
