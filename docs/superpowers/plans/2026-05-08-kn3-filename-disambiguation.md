# K-N3: Generated Mapper Filename Disambiguation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop Kraft's KSP processor from throwing `FileAlreadyExistsException` when two `@MapEnum` (or `@MapConfig`) declarations target classes whose simple names collide but whose FQNs differ — typically nested types like `AuthResponse.User.Role` vs `AuthMe200Response.Role`.

**Architecture:** Introduce a `qualifiedSegments(KSClassDeclaration): String` helper that joins the parent-class chain plus the leaf simple name with `_`. Feed those strings into the existing filename builders in `EnumMapperGenerator` and `ExtensionMapperGenerator`. Top-level types produce the same filenames as today (backward compatible); nested types disambiguate by including their parent simple-name segments. No hash, no two-pass codegen, no signature change to `CodeGenUtils.buildFileName`.

**Tech Stack:** Kotlin / KSP (Symbol Processing API) / KotlinPoet / kotlin-compile-testing (tests) / JUnit5 / Truth.

---

## Pre-cleared deviations

(None — first execution.)

---

## File Structure

**New files:**

- `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/MapEnumNestedSimpleNameCollisionTest.kt`
  Compile-testing regression that declares two `@MapEnum`s whose source enums share the simple name `Role` but live under different parent classes. Pre-fix: KSP throws `FileAlreadyExistsException`. Post-fix: two distinct files emitted with parent-chain-prefixed names.

- `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/basic/MapConfigNestedSimpleNameCollisionTest.kt`
  Same shape but with `@MapConfig` data-class pairs (exercises `ExtensionMapperGenerator`).

**Modified files:**

- `kraft-ksp/src/main/kotlin/com/blu3berry/kraft/processor/util/CodeGenUtils.kt`
  Add `qualifiedSegments(decl: KSClassDeclaration): String` helper. Walks `parentDeclaration` upward, joins simple names with `_`. `buildFileName` signature unchanged.

- `kraft-ksp/src/main/kotlin/com/blu3berry/kraft/processor/codegen/generator/EnumMapperGenerator.kt:73`
  Replace `desc.sourceType.className.simpleName` / `desc.targetType.className.simpleName` arguments with `CodeGenUtils.qualifiedSegments(desc.sourceType.declaration)` / `qualifiedSegments(desc.targetType.declaration)`.

- `kraft-ksp/src/main/kotlin/com/blu3berry/kraft/processor/codegen/generator/ExtensionMapperGenerator.kt:38`
  Replace inline `"${fromClass.simpleName}To${toClass.simpleName}Mapper"` with `"${qualifiedSegments(descriptor.sourceType.declaration)}To${qualifiedSegments(descriptor.targetType.declaration)}Mapper"`.

- `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt`
  Add one assertion confirming top-level types still produce `Status_To_StatusDto_EnumMapper.kt` exactly — explicit regression guard against silent filename refactors of the common case.

---

## Verification commands

- Test suite: `./gradlew :kraft-ksp:jvmTest --tests 'com.blu3berry.kraft.*' --info`
- Detekt: `./gradlew detekt`
- Single-test runs (per task): `./gradlew :kraft-ksp:jvmTest --tests '<fully-qualified-class-name>'`

---

### Task 1: TDD cycle — `@MapEnum` collision

**Files:**
- Create: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/MapEnumNestedSimpleNameCollisionTest.kt`
- Modify: `kraft-ksp/src/main/kotlin/com/blu3berry/kraft/processor/util/CodeGenUtils.kt`
- Modify: `kraft-ksp/src/main/kotlin/com/blu3berry/kraft/processor/codegen/generator/EnumMapperGenerator.kt`

- [ ] **Step 1: Write the failing test**

Create `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/MapEnumNestedSimpleNameCollisionTest.kt`:

```kotlin
package com.blu3berry.kraft.mapenum

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Regression for K-N3: two `@MapEnum` declarations whose source enums share
 * a simple name (`Role`) but live under different parents must each produce
 * a distinct generated file. Pre-fix this threw FileAlreadyExistsException
 * because the filename was derived from leaf simple names only.
 */
@OptIn(ExperimentalCompilerApi::class)
class MapEnumNestedSimpleNameCollisionTest {

    @Test
    fun `two @MapEnum with nested sources sharing a simple name emit distinct files`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            class AuthMe200Response {
                enum class Role { STAFF, MANAGER, OWNER }
            }

            class AuthResponse {
                class User {
                    enum class Role { STAFF, MANAGER, OWNER }
                }
            }

            enum class UserRole { STAFF, MANAGER, OWNER }

            @com.blu3berry.kraft.config.MapEnum(
                source = AuthMe200Response.Role::class,
                target = UserRole::class
            )
            object MeRoleMapping

            @com.blu3berry.kraft.config.MapEnum(
                source = AuthResponse.User.Role::class,
                target = UserRole::class
            )
            object UserRoleMapping
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }

        val files = TestKspRunner.compileAndReturnGenerated(source)
        val enumMappers = files.filter { it.name.contains("_EnumMapper") }

        // Two source enums → two distinct mapper files.
        assertThat(enumMappers).hasSize(2)

        val fileNames = enumMappers.map { it.name }
        assertThat(fileNames.any { it.contains("AuthMe200Response_Role_To_UserRole_EnumMapper") }).isTrue()
        assertThat(fileNames.any { it.contains("AuthResponse_User_Role_To_UserRole_EnumMapper") }).isTrue()
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :kraft-ksp:jvmTest --tests 'com.blu3berry.kraft.mapenum.MapEnumNestedSimpleNameCollisionTest' --info`

Expected: FAIL. The error inside the captured KSP messages will contain `FileAlreadyExistsException` and reference `Role_To_UserRole_EnumMapper.kt.kt` (the duplicate path).

If the test instead fails for a different reason (e.g. compilation error in the fixture), fix the fixture before proceeding — the test must reach the codegen stage to expose the real bug.

- [ ] **Step 3: Add the `qualifiedSegments` helper**

Edit `kraft-ksp/src/main/kotlin/com/blu3berry/kraft/processor/util/CodeGenUtils.kt`. Add the import for `KSClassDeclaration` and a new function alongside `buildFileName`:

```kotlin
package com.blu3berry.kraft.processor.util

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo

object CodeGenUtils {

    /**
     * Writes a FileSpec to disk using the KSP CodeGenerator
     */
    fun writeFile(
        codeGenerator: CodeGenerator,
        fileSpec: FileSpec,
        originatingFile: KSFile,
        aggregating: Boolean = false
    ) {
        fileSpec.writeTo(
            codeGenerator = codeGenerator,
            dependencies = Dependencies(aggregating, originatingFile)
        )
    }

    /**
     * Returns a standard disclaimer banner to include at the top of every generated file
     */
    fun generatedBanner(): String =
        """                                                
         * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * 
         *                                                                                     *
         *   This file is generated by Kraft via the KSP processor. Do not edit manually.      *
         *                                                                                     *
         * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
        """.trimIndent()

    /**
     * Builds a file name in the standard convention: From_To_TypeMapper
     */
    fun buildFileName(fromClassName: String, toClassName: String, suffix: String = "Mapper"): String =
        "${fromClassName}_To_${toClassName}_$suffix"

    /**
     * Walks the parent-class chain for [decl] and returns the simple names from
     * outermost enclosing class down to [decl] itself, joined by `_`.
     *
     * Top-level: `Status` → `"Status"`.
     * Nested:    `AuthResponse.User.Role` → `"AuthResponse_User_Role"`.
     *
     * Used to disambiguate generated mapper filenames when two declarations
     * share a leaf simple name (K-N3). Within a single KSP run the result is
     * unique per FQN: parent chain + simple name + package = FQN, and Kotlin
     * guarantees FQN uniqueness.
     */
    fun qualifiedSegments(decl: KSClassDeclaration): String =
        generateSequence(decl) { it.parentDeclaration as? KSClassDeclaration }
            .toList()
            .asReversed()
            .joinToString("_") { it.simpleName.asString() }
}
```

- [ ] **Step 4: Switch `EnumMapperGenerator` to use `qualifiedSegments`**

Edit `kraft-ksp/src/main/kotlin/com/blu3berry/kraft/processor/codegen/generator/EnumMapperGenerator.kt`. Replace lines 73-77 (the `buildFileName` call) with the new arguments:

```kotlin
        // 5) Compose file path + incremental dependency
        val pkg = generatedPackage(desc)
        val fileName = CodeGenUtils.buildFileName(
            CodeGenUtils.qualifiedSegments(desc.sourceType.declaration),
            CodeGenUtils.qualifiedSegments(desc.targetType.declaration),
            "EnumMapper"
        )
```

No other changes in this file.

- [ ] **Step 5: Re-run the test and confirm it passes**

Run: `./gradlew :kraft-ksp:jvmTest --tests 'com.blu3berry.kraft.mapenum.MapEnumNestedSimpleNameCollisionTest' --info`

Expected: PASS. Two files emitted, both filenames present.

- [ ] **Step 6: Commit**

```bash
git add kraft-ksp/src/main/kotlin/com/blu3berry/kraft/processor/util/CodeGenUtils.kt \
        kraft-ksp/src/main/kotlin/com/blu3berry/kraft/processor/codegen/generator/EnumMapperGenerator.kt \
        kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/MapEnumNestedSimpleNameCollisionTest.kt
git commit -m "fix(kraft-ksp): disambiguate enum mapper filenames when nested simple names collide"
```

---

### Task 2: TDD cycle — `@MapConfig` (extension) collision

**Files:**
- Create: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/basic/MapConfigNestedSimpleNameCollisionTest.kt`
- Modify: `kraft-ksp/src/main/kotlin/com/blu3berry/kraft/processor/codegen/generator/ExtensionMapperGenerator.kt`

- [ ] **Step 1: Write the failing test**

Create `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/basic/MapConfigNestedSimpleNameCollisionTest.kt`:

```kotlin
package com.blu3berry.kraft.basic

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Regression for K-N3 (extension-mapper variant): two `@MapConfig` declarations
 * whose source data classes share a leaf simple name (`User`) but live under
 * different parents must each produce a distinct generated file. Pre-fix this
 * threw FileAlreadyExistsException for `UserToUserDtoMapper.kt`.
 */
@OptIn(ExperimentalCompilerApi::class)
class MapConfigNestedSimpleNameCollisionTest {

    @Test
    fun `two @MapConfig with nested sources sharing a simple name emit distinct files`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            data class UserDto(val name: String)

            class AuthMe200Response {
                data class User(val name: String)
            }

            class AuthResponse {
                class Wrapper {
                    data class User(val name: String)
                }
            }

            @com.blu3berry.kraft.config.MapConfig(
                source = AuthMe200Response.User::class,
                target = UserDto::class
            )
            object MeUserMapping

            @com.blu3berry.kraft.config.MapConfig(
                source = AuthResponse.Wrapper.User::class,
                target = UserDto::class
            )
            object WrapperUserMapping
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }

        val files = TestKspRunner.compileAndReturnGenerated(source)
        val mappers = files.filter { it.name.contains("Mapper") && !it.name.contains("_EnumMapper") }

        assertThat(mappers).hasSize(2)

        val fileNames = mappers.map { it.name }
        assertThat(fileNames.any { it.contains("AuthMe200Response_UserToUserDtoMapper") }).isTrue()
        assertThat(fileNames.any { it.contains("AuthResponse_Wrapper_UserToUserDtoMapper") }).isTrue()
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :kraft-ksp:jvmTest --tests 'com.blu3berry.kraft.basic.MapConfigNestedSimpleNameCollisionTest' --info`

Expected: FAIL with `FileAlreadyExistsException` referencing `UserToUserDtoMapper.kt.kt`.

- [ ] **Step 3: Switch `ExtensionMapperGenerator` to use `qualifiedSegments`**

Edit `kraft-ksp/src/main/kotlin/com/blu3berry/kraft/processor/codegen/generator/ExtensionMapperGenerator.kt`. Add the import and replace line 38:

Add to imports (alphabetical order alongside the existing `com.blu3berry.kraft.processor.util.CodeGenUtils`):

```kotlin
import com.blu3berry.kraft.processor.util.CodeGenUtils
```

(This import already exists in the file — verify before re-adding.)

Replace line 38:

```kotlin
        val fileName = "${fromClass.simpleName}To${toClass.simpleName}Mapper"
```

with:

```kotlin
        val fileName = "${CodeGenUtils.qualifiedSegments(descriptor.sourceType.declaration)}" +
            "To${CodeGenUtils.qualifiedSegments(descriptor.targetType.declaration)}Mapper"
```

The local `fromClass` / `toClass` `ClassName` values are still used elsewhere in this function (for the receiver type and return type in `FunSpec.builder`), so leave them in place. Only the filename construction changes.

- [ ] **Step 4: Re-run the test and confirm it passes**

Run: `./gradlew :kraft-ksp:jvmTest --tests 'com.blu3berry.kraft.basic.MapConfigNestedSimpleNameCollisionTest' --info`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add kraft-ksp/src/main/kotlin/com/blu3berry/kraft/processor/codegen/generator/ExtensionMapperGenerator.kt \
        kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/basic/MapConfigNestedSimpleNameCollisionTest.kt
git commit -m "fix(kraft-ksp): disambiguate extension mapper filenames when nested simple names collide"
```

---

### Task 3: Top-level regression guard

**Files:**
- Modify: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt`

- [ ] **Step 1: Add the regression assertion**

Edit `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt`. Locate the first test (`same-module enums with identical entries auto-generate without @MapEnum`, line 19-44) and add an explicit filename equality assertion just before the existing `assertThat(parent).contains(...)` line.

Replace this section (currently lines 36-43):

```kotlin
        val files = TestKspRunner.compileAndReturnGenerated(source)
        val parent = files.first { "ToDstMapper" in it.name }.readText()
        val enumMapper = files.first { "Status_To_StatusDto_EnumMapper" in it.name }.readText()

        assertThat(parent).contains("status = this.status.toStatusDto()")
        assertThat(enumMapper).contains("Status.ACTIVE -> StatusDto.ACTIVE")
        assertThat(enumMapper).contains("Status.INACTIVE -> StatusDto.INACTIVE")
    }
```

with:

```kotlin
        val files = TestKspRunner.compileAndReturnGenerated(source)
        val parent = files.first { "ToDstMapper" in it.name }.readText()
        val enumMapperFile = files.first { "Status_To_StatusDto_EnumMapper" in it.name }
        val enumMapper = enumMapperFile.readText()

        // K-N3 backward-compat guard: top-level enum filenames must remain unchanged.
        // Use startsWith so the assertion is robust to KotlinPoet's `.kt` extension
        // handling but still catches any spurious parent-chain prefix being added.
        assertThat(enumMapperFile.name).startsWith("Status_To_StatusDto_EnumMapper")

        assertThat(parent).contains("status = this.status.toStatusDto()")
        assertThat(enumMapper).contains("Status.ACTIVE -> StatusDto.ACTIVE")
        assertThat(enumMapper).contains("Status.INACTIVE -> StatusDto.INACTIVE")
    }
```

- [ ] **Step 2: Run the test and confirm it passes**

Run: `./gradlew :kraft-ksp:jvmTest --tests 'com.blu3berry.kraft.mapenum.EnumByNameAutoTest' --info`

Expected: PASS — top-level filenames are unchanged after the K-N3 fix.

- [ ] **Step 3: Commit**

```bash
git add kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/mapenum/EnumByNameAutoTest.kt
git commit -m "test(kraft-ksp): assert top-level mapper filenames unchanged after K-N3"
```

---

### Task 4: Full test suite + detekt

**Files:**
- (Modifications only if regressions are found.)

- [ ] **Step 1: Run the full kraft-ksp test suite**

Run: `./gradlew :kraft-ksp:jvmTest --info`

Expected: All tests pass.

If any test fails because it asserts on a *nested-type* generated filename, update the assertion to the new naming. Top-level naming is unchanged, so any test failure on a top-level fixture indicates a real regression — investigate before changing.

Tests to keep an eye on:
- `MapEnumNestedSourceTest` (`mapenum/MapEnumNestedSourceTest.kt:53`) — uses loose `it.name.contains("Role") && it.name.contains("UserRole")`, so it should keep matching `AuthResponse_User_Role_To_UserRole_EnumMapper.kt`. Verify.
- All `mapenum/*Test.kt` files — most use top-level `Status_To_StatusDto_EnumMapper` and remain unchanged.

- [ ] **Step 2: Run detekt**

Run: `./gradlew detekt`

Expected: PASS. The new helper is small (≤6 lines) and follows the existing `CodeGenUtils` style.

- [ ] **Step 3: If any test failed in Step 1, update it and commit separately**

Only after fixing each failing test individually. Commit message template:

```bash
git commit -m "test(kraft-ksp): update <test-name> filename assertion for K-N3"
```

If no failures, no commit needed for this step.

- [ ] **Step 4: Final verification — run the suite once more**

Run: `./gradlew :kraft-ksp:jvmTest detekt`

Expected: Green.

---

## Out of scope

- Function names, generated `import` statements — unchanged by design.
- Cross-module naming concerns (K-N5).
- `@MapUsing` extensions (K-N1/N2/N4).
- Refactoring `CodeGenUtils.buildFileName` signature beyond the new `qualifiedSegments` helper.

## Risk and rollback

Single-revert recovery: each task is an independent commit. Worst case, revert Task 1 + Task 2 to restore prior behavior.
