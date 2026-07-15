# Kraft Side Aliases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let projects register layer-named "sides" (e.g. `Dto`, `Domain`, `Entity`) by package pattern in Gradle so Kraft emits short alias extensions (`toDomain()`, `toEntity()`, …) alongside the existing verbose mapper functions, eliminating hand-written wrapper files.

**Architecture:** New `SideRegistry` lives in `kraft-core`, parses KSP options at processor init (`kraft.side.<slot>.{name,packagePattern,template,emitMode}`), validates patterns/templates eagerly, and provides `resolveSide(targetFqn)` + alias-collision tracking to `ExtensionMapperGenerator`. The generator emits a one-line delegate (`fun X.toDomain(): Y = toYFromX()`) in the same generated file as the verbose function. A new `aliasEmitMode` parameter on `@MapConfig` (with default `INHERIT`) lets per-mapper code override the project-wide policy.

**Tech Stack:** Kotlin 2.x · KSP 2 · KotlinPoet · JUnit 5 · `com.tschuchort.compiletesting` (KSP test harness) · Google Truth.

**Source layout reference:**
- Annotations (KMP): `kraft-annotations/src/commonMain/kotlin/com/blu3berry/kraft/config/`
- Processor logic (JVM): `kraft-core/src/main/kotlin/com/blu3berry/kraft/`
- KSP processor entry (JVM): `kraft-ksp/src/main/kotlin/com/blu3berry/kraft/`
- Tests (JVM): `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/`

**Spec:** `docs/superpowers/specs/2026-05-08-kraft-side-aliases-design.md`

## Pre-cleared deviations from spec

Captured during execution; reviewers should treat these as out of scope and not re-flag.

1. **Alias comment is KDoc, not `//` line comment.** Spec Section 5 called for a terse single-line `//` comment for machine-greppability. Implementation uses KotlinPoet's `addKdoc(...)` which renders as a `/** Alias generated for side X (template = Y) */` block. Decision to keep: KDoc is more useful for IDE users (hover tooltips), and `grep "Alias generated for side"` still finds it inside the KDoc body — the machine-greppability rationale is preserved.

2. **`aliasEmitMode` applies symmetrically to both `@MapReverse` directions.** Spec said each direction is "resolved independently" but is silent on whether `aliasEmitMode = FULL_NAME_ONLY` suppresses one or both directions. `ReverseDescriptorBuilder` propagates the value verbatim, so a `FULL_NAME_ONLY` annotation suppresses aliases in both directions. This matches user intent (annotating once means "this whole mapper, both ways") and is the simplest semantics. Per-direction override is not in scope.

3. **Template syntax is `{var}` (Mustache-style), not `${var}` (Kotlin-template-style).** The existing `GenerationConfig.functionNameTemplate` in `kraft-core` uses `${target}` syntax, so the spec uses a different placeholder syntax than the existing engine. Decision recorded during brainstorming: stick with the spec's `{var}` syntax; consider unifying both engines in a follow-up.

---

## Task 1: Add `AliasEmitMode` enum and `aliasEmitMode` parameter on `@MapConfig`

**Files:**
- Create: `kraft-annotations/src/commonMain/kotlin/com/blu3berry/kraft/config/AliasEmitMode.kt`
- Modify: `kraft-annotations/src/commonMain/kotlin/com/blu3berry/kraft/config/MapConfig.kt`

- [ ] **Step 1: Create `AliasEmitMode.kt`**

```kotlin
package com.blu3berry.kraft.config

/**
 * Controls whether Kraft emits the short side-alias extension function
 * alongside the verbose `to<Target>From<Source>()` mapper.
 *
 * - [INHERIT] (default): use the project-level `emitMode` set on the matched side
 *   in `build.gradle.kts` (`kraft.side.<slot>.emitMode`).
 * - [BOTH]: emit both the verbose function AND the alias for this mapper.
 * - [FULL_NAME_ONLY]: emit only the verbose function — no alias for this mapper.
 *
 * Used as the value of [MapConfig.aliasEmitMode].
 */
enum class AliasEmitMode { INHERIT, BOTH, FULL_NAME_ONLY }
```

- [ ] **Step 2: Add the new parameter to `MapConfig`**

Replace the existing annotation declaration in
`kraft-annotations/src/commonMain/kotlin/com/blu3berry/kraft/config/MapConfig.kt`
(keep the existing KDoc block; only the constructor changes). The full updated file should read:

```kotlin
package com.blu3berry.kraft.config

import kotlin.reflect.KClass

/**
 * (existing KDoc — preserve unchanged.)
 *
 * @param aliasEmitMode Per-mapper override of the alias emission policy. Defaults
 *                      to [AliasEmitMode.INHERIT], which uses the project-level
 *                      `emitMode` set on the matched side in build.gradle.kts.
 *                      See [AliasEmitMode] for full semantics.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class MapConfig(
    val source: KClass<*>,
    val target: KClass<*>,
    val fieldMappings: Array<FieldMapping> = [],
    val nestedMappings: Array<NestedMapping> = [],
    val ignoredMappings: Array<MapIgnoreField> = [],
    val useGlobalConverters: Boolean = true,
    val aliasEmitMode: AliasEmitMode = AliasEmitMode.INHERIT,
)
```

- [ ] **Step 3: Verify the module still builds**

Run: `./gradlew :kraft-annotations:build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add kraft-annotations/src/commonMain/kotlin/com/blu3berry/kraft/config/AliasEmitMode.kt \
        kraft-annotations/src/commonMain/kotlin/com/blu3berry/kraft/config/MapConfig.kt
git commit -m "feat(kraft-annotations): add AliasEmitMode and aliasEmitMode param on @MapConfig"
```

---

## Task 2: Add KSP option-key constants for `kraft.side.*`

**Files:**
- Modify: `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/util/KraftKspConstants.kt`

- [ ] **Step 1: Add constants**

Append to the `KraftKspConstants` object (after the existing `OPTION_MODULE_ID`):

```kotlin
    // Side-alias config (per-side keys: kraft.side.<slot>.<field>)
    const val OPTION_SIDE_PREFIX           = "kraft.side."
    const val OPTION_SIDE_FIELD_NAME       = "name"
    const val OPTION_SIDE_FIELD_PATTERN    = "packagePattern"
    const val OPTION_SIDE_FIELD_TEMPLATE   = "template"
    const val OPTION_SIDE_FIELD_EMIT_MODE  = "emitMode"

    // @MapConfig argument names for the new parameter
    const val ARG_ALIAS_EMIT_MODE = "aliasEmitMode"
```

- [ ] **Step 2: Verify the module still builds**

Run: `./gradlew :kraft-core:build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/util/KraftKspConstants.kt
git commit -m "feat(kraft-core): add side-alias KSP option key constants"
```

---

## Task 3: Implement Ant-style glob matcher (pure unit, TDD)

**Files:**
- Create: `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/sides/PackageGlob.kt`
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/PackageGlobTest.kt`

The glob matches FQNs against patterns where:
- `*` matches a single package segment (no dots)
- `**` matches zero or more package segments

- [ ] **Step 1: Write failing tests**

Create `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/PackageGlobTest.kt`:

```kotlin
package com.blu3berry.kraft.sides

import com.blu3berry.kraft.processor.sides.PackageGlob
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PackageGlobTest {

    @Test
    fun `single segment wildcard matches one segment`() {
        val glob = PackageGlob.parse("com.x.*.User")
        assertThat(glob.matches("com.x.feature.User")).isTrue()
        assertThat(glob.matches("com.x.User")).isFalse()
        assertThat(glob.matches("com.x.feature.sub.User")).isFalse()
    }

    @Test
    fun `double-star matches zero segments`() {
        val glob = PackageGlob.parse("com.x.**.User")
        assertThat(glob.matches("com.x.User")).isTrue()
    }

    @Test
    fun `double-star matches multiple segments`() {
        val glob = PackageGlob.parse("com.x.**.User")
        assertThat(glob.matches("com.x.a.b.c.User")).isTrue()
    }

    @Test
    fun `leading double-star matches any prefix`() {
        val glob = PackageGlob.parse("**.domain.model.Category")
        assertThat(glob.matches("hu.x.feature.domain.model.Category")).isTrue()
        assertThat(glob.matches("domain.model.Category")).isTrue()
    }

    @Test
    fun `trailing double-star matches any suffix`() {
        val glob = PackageGlob.parse("com.x.**")
        assertThat(glob.matches("com.x")).isTrue()
        assertThat(glob.matches("com.x.a.b.User")).isTrue()
    }

    @Test
    fun `match is case-sensitive`() {
        val glob = PackageGlob.parse("com.X.User")
        assertThat(glob.matches("com.x.User")).isFalse()
    }

    @Test
    fun `pattern without wildcards matches exactly`() {
        val glob = PackageGlob.parse("com.x.User")
        assertThat(glob.matches("com.x.User")).isTrue()
        assertThat(glob.matches("com.x.UserDto")).isFalse()
    }

    @Test
    fun `invalid syntax throws IllegalArgumentException`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            PackageGlob.parse("com.x.***")
        }
    }

    @Test
    fun `isStrictSubsetOf detects nested patterns`() {
        val outer = PackageGlob.parse("**.data.**")
        val inner = PackageGlob.parse("**.data.api.**")
        assertThat(inner.isStrictSubsetOf(outer)).isTrue()
        assertThat(outer.isStrictSubsetOf(inner)).isFalse()
    }

    @Test
    fun `isStrictSubsetOf returns false for disjoint patterns`() {
        val a = PackageGlob.parse("**.data.**")
        val b = PackageGlob.parse("**.domain.**")
        assertThat(a.isStrictSubsetOf(b)).isFalse()
        assertThat(b.isStrictSubsetOf(a)).isFalse()
    }

    @Test
    fun `equal patterns are not strict subsets of each other`() {
        val a = PackageGlob.parse("**.data.**")
        val b = PackageGlob.parse("**.data.**")
        assertThat(a.isStrictSubsetOf(b)).isFalse()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :kraft-ksp:test --tests 'com.blu3berry.kraft.sides.PackageGlobTest'`
Expected: FAIL — `PackageGlob` class not found.

- [ ] **Step 3: Implement `PackageGlob.kt`**

Create `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/sides/PackageGlob.kt`:

```kotlin
package com.blu3berry.kraft.processor.sides

/**
 * Ant-style package-name glob.
 *
 * - `*`  matches exactly one segment (no dots).
 * - `**` matches zero or more segments.
 *
 * Match is case-sensitive. Compiled to a [Regex] for runtime evaluation.
 */
class PackageGlob private constructor(
    val raw: String,
    private val regex: Regex,
    private val tokens: List<Token>,
) {

    fun matches(fqn: String): Boolean = regex.matches(fqn)

    /**
     * True if every FQN matched by `this` is also matched by [other], and
     * `other` matches at least one FQN that `this` doesn't (i.e. `other`
     * is strictly broader). Conservative: returns false for any pair that
     * isn't structurally provable.
     */
    fun isStrictSubsetOf(other: PackageGlob): Boolean {
        if (this.raw == other.raw) return false
        return tokensAreSubset(this.tokens, other.tokens) &&
            !tokensAreSubset(other.tokens, this.tokens)
    }

    internal sealed interface Token {
        data class Literal(val value: String) : Token
        object SingleStar : Token
        object DoubleStar : Token
    }

    companion object {
        fun parse(pattern: String): PackageGlob {
            require(pattern.isNotEmpty()) { "Empty package pattern" }
            // Disallow more than two consecutive stars.
            require(!pattern.contains("***")) {
                "Invalid glob pattern '$pattern': only `*` and `**` wildcards are allowed."
            }
            val tokens = tokenize(pattern)
            val regex = compileRegex(tokens)
            return PackageGlob(pattern, regex, tokens)
        }

        private fun tokenize(pattern: String): List<Token> = pattern
            .split('.')
            .map { segment ->
                when (segment) {
                    "**" -> Token.DoubleStar
                    "*" -> Token.SingleStar
                    else -> {
                        require(!segment.contains('*')) {
                            "Wildcards must be whole segments: '$pattern'"
                        }
                        Token.Literal(segment)
                    }
                }
            }

        private fun compileRegex(tokens: List<Token>): Regex {
            val sb = StringBuilder("^")
            tokens.forEachIndexed { i, token ->
                val isLast = i == tokens.lastIndex
                when (token) {
                    is Token.Literal -> {
                        if (i > 0 && tokens[i - 1] !is Token.DoubleStar) sb.append("\\.")
                        sb.append(Regex.escape(token.value))
                    }
                    Token.SingleStar -> {
                        if (i > 0 && tokens[i - 1] !is Token.DoubleStar) sb.append("\\.")
                        sb.append("[^.]+")
                    }
                    Token.DoubleStar -> {
                        // Match zero-or-more segments. We let the next literal
                        // / single-star append its own dot.
                        if (i == 0) {
                            sb.append("(?:[^.]+(?:\\.[^.]+)*)?")
                            // If next token exists, consume optional dot:
                            if (!isLast) sb.append("\\.?")
                        } else {
                            // Preceding dot already required by previous token
                            sb.append("(?:\\.[^.]+)*")
                            if (!isLast) sb.append("\\.?")
                        }
                    }
                }
            }
            sb.append("$")
            return Regex(sb.toString())
        }

        // ---- subset reasoning over token sequences ----

        /**
         * Returns true if every FQN matched by `inner` is matched by `outer`.
         * Implemented by walking both token sequences with backtracking on
         * `**`. Conservative — returns false for cases it can't prove.
         */
        private fun tokensAreSubset(inner: List<Token>, outer: List<Token>): Boolean =
            tokensAreSubset(inner, 0, outer, 0)

        @Suppress("ReturnCount")
        private fun tokensAreSubset(
            inner: List<Token>, i: Int,
            outer: List<Token>, o: Int,
        ): Boolean {
            if (o == outer.size) return i == inner.size
            val ot = outer[o]
            if (ot is Token.DoubleStar) {
                // ** in outer can match zero-or-more inner tokens.
                for (consumed in i..inner.size) {
                    if (tokensAreSubset(inner, consumed, outer, o + 1)) return true
                }
                return false
            }
            if (i == inner.size) return false
            val it = inner[i]
            return when {
                it is Token.DoubleStar -> false // ** in inner can't be proven subset of literal/* outer
                ot is Token.SingleStar -> {
                    if (it is Token.SingleStar || it is Token.Literal) {
                        tokensAreSubset(inner, i + 1, outer, o + 1)
                    } else false
                }
                ot is Token.Literal -> {
                    if (it is Token.Literal && it.value == ot.value) {
                        tokensAreSubset(inner, i + 1, outer, o + 1)
                    } else false
                }
                else -> false
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :kraft-ksp:test --tests 'com.blu3berry.kraft.sides.PackageGlobTest'`
Expected: All 11 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/sides/PackageGlob.kt \
        kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/PackageGlobTest.kt
git commit -m "feat(kraft-core): add Ant-style PackageGlob matcher with subset detection"
```

---

## Task 4: Implement template engine ({side} / {target} / {source})

**Files:**
- Create: `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/sides/AliasTemplate.kt`
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/AliasTemplateTest.kt`

- [ ] **Step 1: Write failing tests**

Create `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/AliasTemplateTest.kt`:

```kotlin
package com.blu3berry.kraft.sides

import com.blu3berry.kraft.processor.sides.AliasTemplate
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AliasTemplateTest {

    @Test
    fun `to-side substitutes side name verbatim`() {
        val t = AliasTemplate.parse("to{side}")
        assertThat(t.render(side = "Domain", source = "ProductCategoryDto", target = "Category"))
            .isEqualTo("toDomain")
    }

    @Test
    fun `to-side-target substitutes target simple name`() {
        val t = AliasTemplate.parse("to{side}{target}")
        assertThat(t.render(side = "Domain", source = "ProductCategoryDto", target = "Category"))
            .isEqualTo("toDomainCategory")
    }

    @Test
    fun `from-source substitutes source simple name`() {
        val t = AliasTemplate.parse("from{source}")
        assertThat(t.render(side = "Domain", source = "ProductCategoryDto", target = "Category"))
            .isEqualTo("fromProductCategoryDto")
    }

    @Test
    fun `case is preserved verbatim`() {
        val t = AliasTemplate.parse("to{side}")
        assertThat(t.render(side = "DTO", source = "S", target = "T"))
            .isEqualTo("toDTO")
    }

    @Test
    fun `unknown variable fails parse`() {
        assertThrows<IllegalArgumentException> { AliasTemplate.parse("to{traget}") }
    }

    @Test
    fun `empty template fails parse`() {
        assertThrows<IllegalArgumentException> { AliasTemplate.parse("") }
    }

    @Test
    fun `template that yields a non-identifier after substitution fails at parse`() {
        // Leading digit yielded by the template literal — invalid Kotlin identifier.
        assertThrows<IllegalArgumentException> { AliasTemplate.parse("1{side}") }
    }

    @Test
    fun `template that has no variables is allowed`() {
        val t = AliasTemplate.parse("toDomain")
        assertThat(t.render(side = "X", source = "S", target = "T")).isEqualTo("toDomain")
    }

    @Test
    fun `validates rendered output is a valid Kotlin identifier`() {
        val t = AliasTemplate.parse("to{side}")
        assertThrows<IllegalArgumentException> {
            t.render(side = "1Bad", source = "S", target = "T")
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :kraft-ksp:test --tests 'com.blu3berry.kraft.sides.AliasTemplateTest'`
Expected: FAIL — `AliasTemplate` class not found.

- [ ] **Step 3: Implement `AliasTemplate.kt`**

Create `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/sides/AliasTemplate.kt`:

```kotlin
package com.blu3berry.kraft.processor.sides

/**
 * Compiled template for a side's alias function name.
 *
 * Recognised variables (substituted verbatim, no auto-casing):
 *  - `{side}`   the side's `name` value
 *  - `{target}` simple name of the target class
 *  - `{source}` simple name of the source class
 *
 * Unknown `{...}` references and invalid Kotlin identifiers fail at [parse] time.
 * The rendered output is re-validated as a Kotlin identifier per render so
 * malformed substitutions (e.g. side name with a leading digit) fail loudly too.
 */
class AliasTemplate private constructor(
    val raw: String,
    private val parts: List<Part>,
) {

    fun render(side: String, source: String, target: String): String {
        val sb = StringBuilder()
        for (part in parts) {
            sb.append(
                when (part) {
                    is Part.Literal -> part.value
                    Part.Side -> side
                    Part.Source -> source
                    Part.Target -> target
                }
            )
        }
        val result = sb.toString()
        require(isValidKotlinIdentifier(result)) {
            "Alias template '$raw' rendered '$result', which is not a valid Kotlin identifier."
        }
        return result
    }

    private sealed interface Part {
        data class Literal(val value: String) : Part
        object Side : Part
        object Source : Part
        object Target : Part
    }

    companion object {
        private val ALLOWED = setOf("side", "source", "target")

        fun parse(raw: String): AliasTemplate {
            require(raw.isNotEmpty()) { "Alias template must not be empty." }
            val parts = mutableListOf<Part>()
            val literal = StringBuilder()
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c == '{') {
                    val end = raw.indexOf('}', i + 1)
                    require(end > 0) { "Unterminated `{` in alias template '$raw'." }
                    if (literal.isNotEmpty()) {
                        parts += Part.Literal(literal.toString())
                        literal.clear()
                    }
                    val name = raw.substring(i + 1, end)
                    require(name in ALLOWED) {
                        "Unknown variable '{$name}' in alias template '$raw'. Allowed: ${ALLOWED.sorted()}."
                    }
                    parts += when (name) {
                        "side" -> Part.Side
                        "source" -> Part.Source
                        "target" -> Part.Target
                        else -> error("unreachable")
                    }
                    i = end + 1
                } else {
                    literal.append(c)
                    i++
                }
            }
            if (literal.isNotEmpty()) parts += Part.Literal(literal.toString())

            // Eager identifier check on a "best effort" rendering — substitute every
            // variable with a known-good placeholder. Catches things like a leading
            // digit in the literal portion of the template.
            val probe = AliasTemplate(raw, parts).render(side = "X", source = "X", target = "X")
            require(isValidKotlinIdentifier(probe)) {
                "Alias template '$raw' produces '$probe' for sample input, which is not a valid Kotlin identifier."
            }
            return AliasTemplate(raw, parts)
        }

        private fun isValidKotlinIdentifier(s: String): Boolean {
            if (s.isEmpty()) return false
            if (!s[0].isJavaIdentifierStart()) return false
            for (i in 1 until s.length) if (!s[i].isJavaIdentifierPart()) return false
            return true
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :kraft-ksp:test --tests 'com.blu3berry.kraft.sides.AliasTemplateTest'`
Expected: All 9 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/sides/AliasTemplate.kt \
        kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/AliasTemplateTest.kt
git commit -m "feat(kraft-core): add alias-template engine with identifier validation"
```

---

## Task 5: Implement `SideConfig` + `SideRegistry` parsing from KSP options

**Files:**
- Create: `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/sides/SideConfig.kt`
- Create: `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/sides/SideRegistry.kt`
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideRegistryParseTest.kt`

`SideRegistry.parseFromOptions` reads the KSP `options: Map<String, String>` and yields a registry — leaving validation (Task 6) and resolution (Task 7) for follow-up tasks.

- [ ] **Step 1: Write failing tests**

Create `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideRegistryParseTest.kt`:

```kotlin
package com.blu3berry.kraft.sides

import com.blu3berry.kraft.config.AliasEmitMode
import com.blu3berry.kraft.processor.sides.SideRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SideRegistryParseTest {

    @Test
    fun `empty options yield empty registry`() {
        val r = SideRegistry.parseFromOptions(emptyMap())
        assertThat(r.sides).isEmpty()
    }

    @Test
    fun `single side parses with all fields`() {
        val r = SideRegistry.parseFromOptions(mapOf(
            "kraft.side.dto.name" to "Dto",
            "kraft.side.dto.packagePattern" to "**.data.**",
            "kraft.side.dto.template" to "to{side}",
            "kraft.side.dto.emitMode" to "BOTH",
        ))
        assertThat(r.sides).hasSize(1)
        val side = r.sides.single()
        assertThat(side.slot).isEqualTo("dto")
        assertThat(side.name).isEqualTo("Dto")
        assertThat(side.packagePattern.raw).isEqualTo("**.data.**")
        assertThat(side.template.raw).isEqualTo("to{side}")
        assertThat(side.emitMode).isEqualTo(AliasEmitMode.BOTH)
    }

    @Test
    fun `template defaults to to-side`() {
        val r = SideRegistry.parseFromOptions(mapOf(
            "kraft.side.dto.name" to "Dto",
            "kraft.side.dto.packagePattern" to "**.data.**",
        ))
        assertThat(r.sides.single().template.raw).isEqualTo("to{side}")
    }

    @Test
    fun `emitMode defaults to BOTH`() {
        val r = SideRegistry.parseFromOptions(mapOf(
            "kraft.side.dto.name" to "Dto",
            "kraft.side.dto.packagePattern" to "**.data.**",
        ))
        assertThat(r.sides.single().emitMode).isEqualTo(AliasEmitMode.BOTH)
    }

    @Test
    fun `multiple sides parse independently`() {
        val r = SideRegistry.parseFromOptions(mapOf(
            "kraft.side.dto.name" to "Dto",
            "kraft.side.dto.packagePattern" to "**.data.**",
            "kraft.side.domain.name" to "Domain",
            "kraft.side.domain.packagePattern" to "**.domain.**",
        ))
        assertThat(r.sides.map { it.slot }).containsExactly("dto", "domain")
    }

    @Test
    fun `unrelated KSP options are ignored`() {
        val r = SideRegistry.parseFromOptions(mapOf(
            "kraft.functionNameFormat" to "to\${target}",
            "kraft.side.dto.name" to "Dto",
            "kraft.side.dto.packagePattern" to "**.data.**",
        ))
        assertThat(r.sides).hasSize(1)
    }

    @Test
    fun `unknown emitMode value fails`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            SideRegistry.parseFromOptions(mapOf(
                "kraft.side.dto.name" to "Dto",
                "kraft.side.dto.packagePattern" to "**.data.**",
                "kraft.side.dto.emitMode" to "ALIAS_ONLY",
            ))
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :kraft-ksp:test --tests 'com.blu3berry.kraft.sides.SideRegistryParseTest'`
Expected: FAIL — classes not found.

- [ ] **Step 3: Implement `SideConfig.kt`**

Create `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/sides/SideConfig.kt`:

```kotlin
package com.blu3berry.kraft.processor.sides

import com.blu3berry.kraft.config.AliasEmitMode

/**
 * One registered side. The `slot` is the internal grouping key from the
 * KSP option `kraft.side.<slot>.<field>`; `name` is the user-visible label
 * substituted into the template's `{side}` variable.
 */
data class SideConfig(
    val slot: String,
    val name: String,
    val packagePattern: PackageGlob,
    val template: AliasTemplate,
    val emitMode: AliasEmitMode,
)
```

Note: `emitMode` here is the project-level default for this side. The per-mapper `aliasEmitMode = INHERIT` resolves to this value at codegen time.

- [ ] **Step 4: Implement `SideRegistry.kt` (parse only, validation in Task 6)**

Create `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/sides/SideRegistry.kt`:

```kotlin
package com.blu3berry.kraft.processor.sides

import com.blu3berry.kraft.config.AliasEmitMode
import com.blu3berry.kraft.processor.util.KraftKspConstants

/**
 * Project-level alias-side configuration parsed from KSP options.
 *
 * Built once at processor init via [parseFromOptions], then queried via
 * [resolveSide] (added in a later task) and the alias-collision recorder
 * (added in a later task).
 */
class SideRegistry private constructor(
    val sides: List<SideConfig>,
) {

    companion object {

        fun parseFromOptions(options: Map<String, String>): SideRegistry {
            val prefix = KraftKspConstants.OPTION_SIDE_PREFIX
            val grouped: Map<String, MutableMap<String, String>> = mutableMapOf<String, MutableMap<String, String>>().apply {
                for ((key, value) in options) {
                    if (!key.startsWith(prefix)) continue
                    val rest = key.removePrefix(prefix)
                    val dot = rest.indexOf('.')
                    if (dot < 0) continue           // not a side field; ignore
                    val slot = rest.substring(0, dot)
                    val field = rest.substring(dot + 1)
                    getOrPut(slot) { mutableMapOf() }[field] = value
                }
            }

            val sides = grouped.entries
                .sortedBy { it.key }                // deterministic ordering
                .map { (slot, fields) -> buildSide(slot, fields) }

            return SideRegistry(sides)
        }

        private fun buildSide(slot: String, fields: Map<String, String>): SideConfig {
            val name = requireNotNull(fields[KraftKspConstants.OPTION_SIDE_FIELD_NAME]) {
                "Kraft side configuration error in build.gradle.kts: " +
                    "missing required `kraft.side.$slot.${KraftKspConstants.OPTION_SIDE_FIELD_NAME}`."
            }
            val patternRaw = requireNotNull(fields[KraftKspConstants.OPTION_SIDE_FIELD_PATTERN]) {
                "Kraft side configuration error in build.gradle.kts: " +
                    "missing required `kraft.side.$slot.${KraftKspConstants.OPTION_SIDE_FIELD_PATTERN}`."
            }
            val templateRaw = fields[KraftKspConstants.OPTION_SIDE_FIELD_TEMPLATE] ?: "to{side}"
            val emitModeRaw = fields[KraftKspConstants.OPTION_SIDE_FIELD_EMIT_MODE] ?: "BOTH"

            val emitMode = try {
                AliasEmitMode.valueOf(emitModeRaw)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Kraft side configuration error in build.gradle.kts: " +
                        "`kraft.side.$slot.${KraftKspConstants.OPTION_SIDE_FIELD_EMIT_MODE}=$emitModeRaw` " +
                        "is not a valid AliasEmitMode. Allowed: BOTH, FULL_NAME_ONLY (INHERIT is per-mapper only).",
                    e
                )
            }
            require(emitMode != AliasEmitMode.INHERIT) {
                "Kraft side configuration error in build.gradle.kts: " +
                    "`kraft.side.$slot.${KraftKspConstants.OPTION_SIDE_FIELD_EMIT_MODE}=INHERIT` " +
                    "is not valid as a project default. Use BOTH or FULL_NAME_ONLY."
            }

            return SideConfig(
                slot = slot,
                name = name,
                packagePattern = PackageGlob.parse(patternRaw),
                template = AliasTemplate.parse(templateRaw),
                emitMode = emitMode,
            )
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :kraft-ksp:test --tests 'com.blu3berry.kraft.sides.SideRegistryParseTest'`
Expected: All 6 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/sides/SideConfig.kt \
        kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/sides/SideRegistry.kt \
        kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideRegistryParseTest.kt
git commit -m "feat(kraft-core): add SideConfig + SideRegistry KSP-options parser"
```

---

## Task 6: Phase 1 config-load validation (identical patterns, strict subsets)

**Files:**
- Modify: `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/sides/SideRegistry.kt`
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideRegistryValidationTest.kt`

- [ ] **Step 1: Write failing tests**

Create `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideRegistryValidationTest.kt`:

```kotlin
package com.blu3berry.kraft.sides

import com.blu3berry.kraft.processor.sides.SideRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SideRegistryValidationTest {

    @Test
    fun `identical packagePattern on two sides fails`() {
        val ex = assertThrows<IllegalStateException> {
            SideRegistry.parseFromOptions(mapOf(
                "kraft.side.a.name" to "A",
                "kraft.side.a.packagePattern" to "**.data.**",
                "kraft.side.b.name" to "B",
                "kraft.side.b.packagePattern" to "**.data.**",
            ))
        }
        assertThat(ex.message).contains("identical")
        assertThat(ex.message).contains("kraft.side.a")
        assertThat(ex.message).contains("kraft.side.b")
        assertThat(ex.message).contains("build.gradle.kts")
    }

    @Test
    fun `strict subset packagePattern fails`() {
        val ex = assertThrows<IllegalStateException> {
            SideRegistry.parseFromOptions(mapOf(
                "kraft.side.outer.name" to "Data",
                "kraft.side.outer.packagePattern" to "**.data.**",
                "kraft.side.inner.name" to "Api",
                "kraft.side.inner.packagePattern" to "**.data.api.**",
            ))
        }
        assertThat(ex.message).contains("**.data.api.**")
        assertThat(ex.message).contains("**.data.**")
        assertThat(ex.message).contains("subset")
    }

    @Test
    fun `disjoint patterns are accepted`() {
        val r = SideRegistry.parseFromOptions(mapOf(
            "kraft.side.dto.name" to "Dto",
            "kraft.side.dto.packagePattern" to "**.data.**",
            "kraft.side.domain.name" to "Domain",
            "kraft.side.domain.packagePattern" to "**.domain.**",
        ))
        assertThat(r.sides).hasSize(2)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :kraft-ksp:test --tests 'com.blu3berry.kraft.sides.SideRegistryValidationTest'`
Expected: FAIL — `IllegalStateException` not thrown (current parser accepts everything that parses individually).

- [ ] **Step 3: Add validation pass to `SideRegistry.parseFromOptions`**

Modify `SideRegistry.kt`. After the `sides` list is built but before the `SideRegistry(sides)` return, run pairwise validation. Replace the body of `parseFromOptions` to insert the call after `sides` is computed:

```kotlin
        fun parseFromOptions(options: Map<String, String>): SideRegistry {
            val prefix = KraftKspConstants.OPTION_SIDE_PREFIX
            val grouped: Map<String, MutableMap<String, String>> = mutableMapOf<String, MutableMap<String, String>>().apply {
                for ((key, value) in options) {
                    if (!key.startsWith(prefix)) continue
                    val rest = key.removePrefix(prefix)
                    val dot = rest.indexOf('.')
                    if (dot < 0) continue
                    val slot = rest.substring(0, dot)
                    val field = rest.substring(dot + 1)
                    getOrPut(slot) { mutableMapOf() }[field] = value
                }
            }

            val sides = grouped.entries
                .sortedBy { it.key }
                .map { (slot, fields) -> buildSide(slot, fields) }

            validateNoOverlap(sides)
            return SideRegistry(sides)
        }

        private fun validateNoOverlap(sides: List<SideConfig>) {
            for (i in sides.indices) for (j in i + 1 until sides.size) {
                val a = sides[i]
                val b = sides[j]
                if (a.packagePattern.raw == b.packagePattern.raw) {
                    error(
                        "Kraft side configuration error in build.gradle.kts: " +
                            "sides `kraft.side.${a.slot}` and `kraft.side.${b.slot}` " +
                            "have identical packagePattern '${a.packagePattern.raw}'. " +
                            "Patterns must be disjoint."
                    )
                }
                if (a.packagePattern.isStrictSubsetOf(b.packagePattern)) {
                    error(
                        "Kraft side configuration error in build.gradle.kts: " +
                            "`kraft.side.${a.slot}.packagePattern` ('${a.packagePattern.raw}') is a strict subset " +
                            "of `kraft.side.${b.slot}.packagePattern` ('${b.packagePattern.raw}'). " +
                            "Every class matched by the first would also match the second. " +
                            "Patterns must be disjoint — tighten one of the two."
                    )
                }
                if (b.packagePattern.isStrictSubsetOf(a.packagePattern)) {
                    error(
                        "Kraft side configuration error in build.gradle.kts: " +
                            "`kraft.side.${b.slot}.packagePattern` ('${b.packagePattern.raw}') is a strict subset " +
                            "of `kraft.side.${a.slot}.packagePattern` ('${a.packagePattern.raw}'). " +
                            "Every class matched by the first would also match the second. " +
                            "Patterns must be disjoint — tighten one of the two."
                    )
                }
            }
        }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :kraft-ksp:test --tests 'com.blu3berry.kraft.sides.SideRegistryValidationTest'`
Expected: All 3 tests PASS.

- [ ] **Step 5: Re-run earlier registry tests to confirm no regression**

Run: `./gradlew :kraft-ksp:test --tests 'com.blu3berry.kraft.sides.*'`
Expected: All glob, template, parse, and validation tests PASS.

- [ ] **Step 6: Commit**

```bash
git add kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/sides/SideRegistry.kt \
        kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideRegistryValidationTest.kt
git commit -m "feat(kraft-core): validate side patterns disjoint at config-load (Phase 1)"
```

---

## Task 7: Side resolution + Phase 2 multi-match detection

**Files:**
- Modify: `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/sides/SideRegistry.kt`
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideRegistryResolveTest.kt`

- [ ] **Step 1: Write failing tests**

Create `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideRegistryResolveTest.kt`:

```kotlin
package com.blu3berry.kraft.sides

import com.blu3berry.kraft.processor.sides.SideRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SideRegistryResolveTest {

    private fun registry() = SideRegistry.parseFromOptions(mapOf(
        "kraft.side.dto.name" to "Dto",
        "kraft.side.dto.packagePattern" to "**.data.generated.models.**",
        "kraft.side.domain.name" to "Domain",
        "kraft.side.domain.packagePattern" to "**.domain.model.**",
    ))

    @Test
    fun `unique match returns the side`() {
        val side = registry().resolveSide("hu.x.feature.domain.model.Category")
        assertThat(side).isNotNull()
        assertThat(side!!.name).isEqualTo("Domain")
    }

    @Test
    fun `no match returns null`() {
        assertThat(registry().resolveSide("hu.x.feature.something.else.X")).isNull()
    }

    @Test
    fun `runtime multi-match raises gradle-config error`() {
        // These patterns are not provable subsets at config load (they
        // diverge at the third segment), but a class matches both at runtime.
        val r = SideRegistry.parseFromOptions(mapOf(
            "kraft.side.a.name" to "A",
            "kraft.side.a.packagePattern" to "**.data.**.UserDto",
            "kraft.side.b.name" to "B",
            "kraft.side.b.packagePattern" to "**.api.**.UserDto",
        ))
        val ex = assertThrows<IllegalStateException> {
            r.resolveSide("com.x.data.api.UserDto")
        }
        assertThat(ex.message).contains("com.x.data.api.UserDto")
        assertThat(ex.message).contains("kraft.side.a")
        assertThat(ex.message).contains("kraft.side.b")
        assertThat(ex.message).contains("build.gradle.kts")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :kraft-ksp:test --tests 'com.blu3berry.kraft.sides.SideRegistryResolveTest'`
Expected: FAIL — `resolveSide` does not exist.

- [ ] **Step 3: Add `resolveSide` to `SideRegistry`**

Add a method to the `SideRegistry` class body in `SideRegistry.kt`:

```kotlin
    /**
     * Returns the side that names this target FQN, or null if no registered
     * side matches. Throws [IllegalStateException] (framed as a gradle config
     * error) if multiple sides match — Phase 2 of overlap detection from the
     * spec, used for cases the eager subset analysis can't prove.
     */
    fun resolveSide(targetFqn: String): SideConfig? {
        val matches = sides.filter { it.packagePattern.matches(targetFqn) }
        return when (matches.size) {
            0 -> null
            1 -> matches.single()
            else -> {
                val lines = matches.joinToString("\n") {
                    "  - kraft.side.${it.slot}.packagePattern  = \"${it.packagePattern.raw}\""
                }
                error(
                    """
                    Kraft side configuration error: package patterns overlap.

                    Class $targetFqn matches ${matches.size} sides:
                    $lines

                    Patterns must be disjoint. Tighten one of the patterns in
                    build.gradle.kts so the classes you intend each side to
                    match no longer overlap.
                    """.trimIndent()
                )
            }
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :kraft-ksp:test --tests 'com.blu3berry.kraft.sides.SideRegistryResolveTest'`
Expected: All 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/sides/SideRegistry.kt \
        kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideRegistryResolveTest.kt
git commit -m "feat(kraft-core): add SideRegistry.resolveSide with Phase-2 overlap detection"
```

---

## Task 8: Alias-name collision tracking

**Files:**
- Modify: `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/sides/SideRegistry.kt`
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideRegistryCollisionTest.kt`

- [ ] **Step 1: Write failing tests**

Create `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideRegistryCollisionTest.kt`:

```kotlin
package com.blu3berry.kraft.sides

import com.blu3berry.kraft.processor.sides.SideRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SideRegistryCollisionTest {

    private fun registry() = SideRegistry.parseFromOptions(mapOf(
        "kraft.side.dto.name" to "Dto",
        "kraft.side.dto.packagePattern" to "**.data.**",
    ))

    @Test
    fun `recordAlias accepts unique pairs`() {
        val r = registry()
        r.recordAlias("com.x.A", "toDto", "MapperA")
        r.recordAlias("com.x.B", "toDto", "MapperB")
        // No throw.
    }

    @Test
    fun `recordAlias accepts same receiver with different alias names`() {
        val r = registry()
        r.recordAlias("com.x.A", "toDto", "MapperA")
        r.recordAlias("com.x.A", "toDtoOther", "MapperA2")
        // No throw.
    }

    @Test
    fun `duplicate (receiver, alias) raises with both mapper origins`() {
        val r = registry()
        r.recordAlias("com.x.A", "toDto", "MapperA")
        val ex = assertThrows<IllegalStateException> {
            r.recordAlias("com.x.A", "toDto", "MapperB")
        }
        assertThat(ex.message).contains("MapperA")
        assertThat(ex.message).contains("MapperB")
        assertThat(ex.message).contains("toDto")
        assertThat(ex.message).contains("com.x.A")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :kraft-ksp:test --tests 'com.blu3berry.kraft.sides.SideRegistryCollisionTest'`
Expected: FAIL — `recordAlias` does not exist.

- [ ] **Step 3: Add collision tracking to `SideRegistry`**

Add inside the `SideRegistry` class body in `SideRegistry.kt`:

```kotlin
    private val recordedAliases: MutableMap<Pair<String, String>, String> = mutableMapOf()

    /**
     * Track an emitted alias `(receiverFqn, aliasName)` and the originating
     * mapper's identifier (e.g. the `@MapConfig`-bearing object's qualified
     * name). Throws [IllegalStateException] on collision — same receiver +
     * same alias name from two different mappers.
     */
    fun recordAlias(receiverFqn: String, aliasName: String, mapperOrigin: String) {
        val key = receiverFqn to aliasName
        val previous = recordedAliases.put(key, mapperOrigin)
        if (previous != null && previous != mapperOrigin) {
            error(
                "Alias name collision: two @MapConfig declarations would emit " +
                    "`$receiverFqn.$aliasName(): ?`.\n" +
                    "  - $previous\n" +
                    "  - $mapperOrigin\n" +
                    "Fix by either:\n" +
                    "  1. Disambiguate via template (e.g. `to{side}{target}` instead of `to{side}`).\n" +
                    "  2. Set `aliasEmitMode = AliasEmitMode.FULL_NAME_ONLY` on one of the colliding @MapConfigs."
            )
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :kraft-ksp:test --tests 'com.blu3berry.kraft.sides.SideRegistryCollisionTest'`
Expected: All 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/sides/SideRegistry.kt \
        kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideRegistryCollisionTest.kt
git commit -m "feat(kraft-core): add SideRegistry alias-collision tracker"
```

---

## Task 9: Wire `SideRegistry` into `AutoMapperProcessor` and `GeneratorEnvironment`

**Files:**
- Modify: `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/codegen/MapperGeneratorProvider.kt` (this is where `GeneratorEnvironment` lives — alongside `MapperGeneratorProvider`)
- Modify: `kraft-ksp/src/main/kotlin/com/blu3berry/kraft/AutoMapperProcessor.kt`

- [ ] **Step 1: Add `sideRegistry` to `GeneratorEnvironment`**

The current contents of `MapperGeneratorProvider.kt`:

```kotlin
package com.blu3berry.kraft.processor.codegen

import com.google.devtools.ksp.processing.KSPLogger

/** Provides the KSP logger, processor options, and [GenerationConfig] to generator implementations. */
data class GeneratorEnvironment(
    val logger: KSPLogger,
    val options: Map<String, String>,
    val config: GenerationConfig
)

/** ServiceLoader entry point for custom [MapperGenerator] implementations. */
fun interface MapperGeneratorProvider {
    fun create(environment: GeneratorEnvironment): MapperGenerator
}
```

Change `GeneratorEnvironment` to add `sideRegistry`:

```kotlin
package com.blu3berry.kraft.processor.codegen

import com.google.devtools.ksp.processing.KSPLogger
import com.blu3berry.kraft.processor.sides.SideRegistry

/** Provides the KSP logger, processor options, [GenerationConfig], and [SideRegistry] to generator implementations. */
data class GeneratorEnvironment(
    val logger: KSPLogger,
    val options: Map<String, String>,
    val config: GenerationConfig,
    val sideRegistry: SideRegistry,
)

/** ServiceLoader entry point for custom [MapperGenerator] implementations. */
fun interface MapperGeneratorProvider {
    fun create(environment: GeneratorEnvironment): MapperGenerator
}
```

- [ ] **Step 2: Build the `SideRegistry` in `AutoMapperProcessor.process`**

In `kraft-ksp/src/main/kotlin/com/blu3berry/kraft/AutoMapperProcessor.kt`, replace the existing `generatorEnv` construction. Before the line `val generatorEnv = GeneratorEnvironment(...)`, add:

```kotlin
        val sideRegistry = try {
            com.blu3berry.kraft.processor.sides.SideRegistry.parseFromOptions(env.options)
        } catch (e: IllegalArgumentException) {
            logger.error(e.message ?: "Kraft side configuration error.")
            return deferred
        } catch (e: IllegalStateException) {
            logger.error(e.message ?: "Kraft side configuration error.")
            return deferred
        }
```

Then modify the `GeneratorEnvironment` construction to include `sideRegistry = sideRegistry,`.

- [ ] **Step 3: Verify the project builds**

Run: `./gradlew :kraft-ksp:assemble`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run all existing kraft-ksp tests to ensure no regression**

Run: `./gradlew :kraft-ksp:test`
Expected: All existing tests still PASS (no behavioural change yet — registry is built but unused by generators).

- [ ] **Step 5: Commit**

```bash
git add kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/codegen/MapperGeneratorProvider.kt \
        kraft-ksp/src/main/kotlin/com/blu3berry/kraft/AutoMapperProcessor.kt
git commit -m "feat(kraft-ksp): wire SideRegistry into processor init and GeneratorEnvironment"
```

---

## Task 10: Read `aliasEmitMode` from `@MapConfig` annotation

**Files:**
- Modify: scanner that reads `@MapConfig` (likely `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/scanner/ConfigObjectScanner.kt` — read first to confirm)
- Modify: scan-result data class that carries `MapConfig` fields (likely `kraft-core/src/main/kotlin/com/blu3berry/kraft/model/scan/ConfigObjectScanResult.kt` — read first)
- Modify: descriptor builder that maps scan → descriptor (likely `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/descriptor/ConfigDescriptorBuilder.kt`)
- Modify: `MapperDescriptor.kt`

- [ ] **Step 1: Read the relevant files**

Read:
- `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/scanner/ConfigObjectScanner.kt`
- `kraft-core/src/main/kotlin/com/blu3berry/kraft/model/scan/ConfigObjectScanResult.kt`
- `kraft-core/src/main/kotlin/com/blu3berry/kraft/model/descriptor/MapperDescriptor.kt`
- `kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/descriptor/ConfigDescriptorBuilder.kt`

Confirm where `useGlobalConverters` is read — `aliasEmitMode` follows the same pattern.

- [ ] **Step 2: Add `aliasEmitMode: AliasEmitMode` field to `ConfigObjectScanResult`**

Default in the data class to `AliasEmitMode.INHERIT`. Add the import for `com.blu3berry.kraft.config.AliasEmitMode`.

- [ ] **Step 3: Read the new arg in `ConfigObjectScanner`**

Locate the spot where `useGlobalConverters` is parsed from the annotation arguments. Right next to it, add:

```kotlin
        val aliasEmitMode = run {
            val argValue = annotation.arguments
                .firstOrNull { it.name?.asString() == KraftKspConstants.ARG_ALIAS_EMIT_MODE }
                ?.value
            // KSP returns enum values as KSType — convert to enum name string.
            val name = when (argValue) {
                is com.google.devtools.ksp.symbol.KSType -> argValue.declaration.simpleName.asString()
                is String -> argValue
                else -> null
            }
            name?.let { runCatching { AliasEmitMode.valueOf(it) }.getOrNull() }
                ?: AliasEmitMode.INHERIT
        }
```

Pass `aliasEmitMode` into the `ConfigObjectScanResult` constructor.

- [ ] **Step 4: Add `aliasEmitMode` to `MapperDescriptor`**

Add `val aliasEmitMode: AliasEmitMode = AliasEmitMode.INHERIT` to the descriptor data class. Threading: `ConfigDescriptorBuilder` already maps scan → descriptor — copy the value across.

- [ ] **Step 5: Verify the project builds and existing tests still pass**

Run: `./gradlew :kraft-ksp:test`
Expected: All existing tests PASS (the new field has a default and is unused so far).

- [ ] **Step 6: Commit**

```bash
git add kraft-core/src/main/kotlin/com/blu3berry/kraft/model/scan/ConfigObjectScanResult.kt \
        kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/scanner/ConfigObjectScanner.kt \
        kraft-core/src/main/kotlin/com/blu3berry/kraft/model/descriptor/MapperDescriptor.kt \
        kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/descriptor/ConfigDescriptorBuilder.kt
git commit -m "feat(kraft-core): thread aliasEmitMode from @MapConfig into MapperDescriptor"
```

---

## Task 11: Emit alias delegate in `ExtensionMapperGenerator`

**Files:**
- Modify: `kraft-ksp/src/main/kotlin/com/blu3berry/kraft/processor/codegen/generator/ExtensionMapperGenerator.kt`

- [ ] **Step 1: Add alias emission to the generator**

Modify `ExtensionMapperGenerator.generate(...)` so it consults `config` (which now carries `sideRegistry` via `GeneratorEnvironment`) — but the generator is constructed with `config: GenerationConfig` only, so the easiest threading is to also pass `sideRegistry` into its constructor. Update the constructor and the two call sites (`AutoMapperProcessor.loadMapperGenerator` default branch, and any provider that creates this generator).

The new generator body:

```kotlin
package com.blu3berry.kraft.processor.codegen.generator

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.writeTo
import com.blu3berry.kraft.config.AliasEmitMode
import com.blu3berry.kraft.model.descriptor.MapperDescriptor
import com.blu3berry.kraft.model.descriptor.MappingSource
import com.blu3berry.kraft.processor.codegen.GenerationConfig
import com.blu3berry.kraft.processor.codegen.MapperGenerator
import com.blu3berry.kraft.processor.codegen.OptInMarker
import com.blu3berry.kraft.processor.codegen.OptInMarkerCollector
import com.blu3berry.kraft.processor.codegen.className
import com.blu3berry.kraft.processor.codegen.generatedMapperPackage
import com.blu3berry.kraft.processor.sides.SideRegistry
import com.blu3berry.kraft.processor.util.CodeGenUtils

class ExtensionMapperGenerator(
    private val logger: KSPLogger,
    private val config: GenerationConfig,
    private val sideRegistry: SideRegistry = SideRegistry.parseFromOptions(emptyMap()),
) : MapperGenerator {

    private val ctorCallBuilder = CtorCallBuilder(config)

    override fun generate(descriptor: MapperDescriptor, codeGenerator: CodeGenerator) {
        val fromClass = descriptor.sourceType.className
        val toClass = descriptor.targetType.className

        val packageName = generatedMapperPackage(fromClass.packageName)
        val functionName = config.functionNameFor(descriptor)
        val fileName = "${fromClass.simpleName}To${toClass.simpleName}Mapper"

        val originatingFiles = listOfNotNull(
            when (val src = descriptor.source) {
                is MappingSource.ClassAnnotation -> src.annotatedClass.containingFile
                is MappingSource.ConfigObject -> src.configObject.containingFile
            },
            descriptor.sourceType.declaration.containingFile,
            descriptor.targetType.declaration.containingFile
        ).distinct()

        if (originatingFiles.isEmpty()) {
            logger.warn("Skipping mapper generation for $fromClass → $toClass: no originating file found.")
            return
        }

        val verboseFn = FunSpec.builder(functionName)
            .receiver(fromClass)
            .returns(toClass)
            .addCode("return %L\n", ctorCallBuilder.build(descriptor))

        optInAnnotation(OptInMarkerCollector.collect(descriptor))?.let(verboseFn::addAnnotation)

        val fileBuilder = FileSpec.builder(packageName, "$fileName.kt")
            .addFileComment(CodeGenUtils.generatedBanner())
            .addFunction(verboseFn.build())

        // ----- Side alias (if any) -----
        val aliasFn = buildAliasFunSpec(
            descriptor = descriptor,
            verboseFunctionName = functionName,
            fromClass = fromClass,
            toClass = toClass,
        )
        if (aliasFn != null) fileBuilder.addFunction(aliasFn)

        @Suppress("SpreadOperator")
        val deps = Dependencies(
            aggregating = false,
            *originatingFiles.toTypedArray()
        )
        fileBuilder.build().writeTo(codeGenerator = codeGenerator, dependencies = deps)
        logger.info("Generated extension mapper function: $packageName.$functionName")
    }

    private fun buildAliasFunSpec(
        descriptor: MapperDescriptor,
        verboseFunctionName: String,
        fromClass: ClassName,
        toClass: ClassName,
    ): FunSpec? {
        val targetFqn = toClass.canonicalName
        val side = try {
            sideRegistry.resolveSide(targetFqn) ?: return null
        } catch (e: IllegalStateException) {
            logger.error(e.message ?: "Kraft side configuration error.")
            return null
        }

        val effectiveMode = when (descriptor.aliasEmitMode) {
            AliasEmitMode.INHERIT -> side.emitMode
            else -> descriptor.aliasEmitMode
        }
        if (effectiveMode == AliasEmitMode.FULL_NAME_ONLY) return null

        val aliasName = side.template.render(
            side = side.name,
            source = fromClass.simpleName,
            target = toClass.simpleName,
        )

        // Track for cross-mapper collision.
        val mapperOrigin = when (val src = descriptor.source) {
            is MappingSource.ClassAnnotation -> src.annotatedClass.qualifiedName?.asString() ?: "<unknown>"
            is MappingSource.ConfigObject -> src.configObject.qualifiedName?.asString() ?: "<unknown>"
        }
        try {
            sideRegistry.recordAlias(fromClass.canonicalName, aliasName, mapperOrigin)
        } catch (e: IllegalStateException) {
            logger.error(e.message ?: "Alias collision.")
            return null
        }

        return FunSpec.builder(aliasName)
            .receiver(fromClass)
            .returns(toClass)
            .addKdoc("Alias generated for side ${side.name} (template = ${side.template.raw})")
            .addCode("return %N()\n", verboseFunctionName)
            .build()
    }

    @Suppress("SpreadOperator")
    private fun optInAnnotation(markers: List<OptInMarker>): AnnotationSpec? {
        if (markers.isEmpty()) return null
        val format = markers.joinToString(", ") { "%T::class" }
        val markerTypes = markers.map { ClassName(it.packageName, it.simpleName) }.toTypedArray()
        return AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
            .addMember(format, *markerTypes)
            .build()
    }
}
```

- [ ] **Step 2: Pass `sideRegistry` into the generator at construction**

In `AutoMapperProcessor.loadMapperGenerator(...)`, in the `providers.isEmpty()` branch:

```kotlin
            providers.isEmpty() -> ExtensionMapperGenerator(
                logger = env.logger,
                config = env.config,
                sideRegistry = env.sideRegistry,
            )
```

- [ ] **Step 3: Verify the project builds**

Run: `./gradlew :kraft-ksp:assemble`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run all existing tests**

Run: `./gradlew :kraft-ksp:test`
Expected: All existing tests PASS — no kspOptions sets `kraft.side.*` so the registry is empty and the generator's behaviour is unchanged.

- [ ] **Step 5: Commit**

```bash
git add kraft-ksp/src/main/kotlin/com/blu3berry/kraft/processor/codegen/generator/ExtensionMapperGenerator.kt \
        kraft-ksp/src/main/kotlin/com/blu3berry/kraft/AutoMapperProcessor.kt
git commit -m "feat(kraft-ksp): emit side-alias delegate in ExtensionMapperGenerator"
```

---

## Task 12: Integration test — happy path (single side, target matches)

**Files:**
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideAliasHappyPathTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.blu3berry.kraft.sides

import com.blu3berry.kraft.TestKspRunner
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class SideAliasHappyPathTest {

    @Test
    fun `target in registered side gets short alias delegate`() {
        val sources = listOf(
            SourceFile.kotlin("Dto.kt", """
                package app.feature.data.generated.models
                data class CategoryDto(val id: Int, val label: String)
            """),
            SourceFile.kotlin("Domain.kt", """
                package app.feature.domain.model
                data class Category(val id: Int, val label: String)
            """),
            SourceFile.kotlin("Mapper.kt", """
                package app.feature.data.mapper

                import app.feature.data.generated.models.CategoryDto
                import app.feature.domain.model.Category

                @com.blu3berry.kraft.config.MapConfig(
                    source = CategoryDto::class,
                    target = Category::class
                )
                object CategoryMapper
            """),
        )

        val generated = TestKspRunner.compileAndReturnGenerated(
            *sources.toTypedArray(),
            kspOptions = mapOf(
                "kraft.side.domain.name" to "Domain",
                "kraft.side.domain.packagePattern" to "**.domain.model.**",
            )
        )

        val joined = generated.joinToString("\n") { it.readText() }

        // Verbose function still emitted.
        assertThat(joined).contains("fun CategoryDto.toCategory(")
        // Short alias emitted.
        assertThat(joined).contains("fun CategoryDto.toDomain(")
        // Alias body delegates to verbose.
        assertThat(joined).containsMatch("fun CategoryDto\\.toDomain\\([^)]*\\)[^{]*\\{[^}]*toCategory\\(")
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :kraft-ksp:test --tests 'com.blu3berry.kraft.sides.SideAliasHappyPathTest'`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideAliasHappyPathTest.kt
git commit -m "test(kraft-ksp): integration test for happy-path side alias emission"
```

---

## Task 13: Integration test — `FULL_NAME_ONLY` modes (project default and per-mapper)

**Files:**
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideAliasEmitModeTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.blu3berry.kraft.sides

import com.blu3berry.kraft.TestKspRunner
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class SideAliasEmitModeTest {

    @Test
    fun `FULL_NAME_ONLY at project level suppresses alias`() {
        val sources = listOf(
            SourceFile.kotlin("Dto.kt", """
                package app.data.generated.models
                data class FooDto(val v: Int)
            """),
            SourceFile.kotlin("Domain.kt", """
                package app.domain.model
                data class Foo(val v: Int)
            """),
            SourceFile.kotlin("Mapper.kt", """
                package app.mapper
                import app.data.generated.models.FooDto
                import app.domain.model.Foo
                @com.blu3berry.kraft.config.MapConfig(source = FooDto::class, target = Foo::class)
                object FooMapper
            """),
        )

        val generated = TestKspRunner.compileAndReturnGenerated(
            *sources.toTypedArray(),
            kspOptions = mapOf(
                "kraft.side.domain.name" to "Domain",
                "kraft.side.domain.packagePattern" to "**.domain.model.**",
                "kraft.side.domain.emitMode" to "FULL_NAME_ONLY",
            )
        )

        val joined = generated.joinToString("\n") { it.readText() }
        assertThat(joined).contains("fun FooDto.toFoo(")
        assertThat(joined).doesNotContain("fun FooDto.toDomain(")
    }

    @Test
    fun `per-mapper BOTH overrides project-level FULL_NAME_ONLY`() {
        val sources = listOf(
            SourceFile.kotlin("Dto.kt", """
                package app.data.generated.models
                data class FooDto(val v: Int)
            """),
            SourceFile.kotlin("Domain.kt", """
                package app.domain.model
                data class Foo(val v: Int)
            """),
            SourceFile.kotlin("Mapper.kt", """
                package app.mapper
                import app.data.generated.models.FooDto
                import app.domain.model.Foo
                import com.blu3berry.kraft.config.AliasEmitMode

                @com.blu3berry.kraft.config.MapConfig(
                    source = FooDto::class,
                    target = Foo::class,
                    aliasEmitMode = AliasEmitMode.BOTH
                )
                object FooMapper
            """),
        )

        val generated = TestKspRunner.compileAndReturnGenerated(
            *sources.toTypedArray(),
            kspOptions = mapOf(
                "kraft.side.domain.name" to "Domain",
                "kraft.side.domain.packagePattern" to "**.domain.model.**",
                "kraft.side.domain.emitMode" to "FULL_NAME_ONLY",
            )
        )

        val joined = generated.joinToString("\n") { it.readText() }
        assertThat(joined).contains("fun FooDto.toFoo(")
        assertThat(joined).contains("fun FooDto.toDomain(")
    }

    @Test
    fun `per-mapper FULL_NAME_ONLY overrides project-level BOTH`() {
        val sources = listOf(
            SourceFile.kotlin("Dto.kt", """
                package app.data.generated.models
                data class FooDto(val v: Int)
            """),
            SourceFile.kotlin("Domain.kt", """
                package app.domain.model
                data class Foo(val v: Int)
            """),
            SourceFile.kotlin("Mapper.kt", """
                package app.mapper
                import app.data.generated.models.FooDto
                import app.domain.model.Foo
                import com.blu3berry.kraft.config.AliasEmitMode

                @com.blu3berry.kraft.config.MapConfig(
                    source = FooDto::class,
                    target = Foo::class,
                    aliasEmitMode = AliasEmitMode.FULL_NAME_ONLY
                )
                object FooMapper
            """),
        )

        val generated = TestKspRunner.compileAndReturnGenerated(
            *sources.toTypedArray(),
            kspOptions = mapOf(
                "kraft.side.domain.name" to "Domain",
                "kraft.side.domain.packagePattern" to "**.domain.model.**",
            )
        )

        val joined = generated.joinToString("\n") { it.readText() }
        assertThat(joined).contains("fun FooDto.toFoo(")
        assertThat(joined).doesNotContain("fun FooDto.toDomain(")
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :kraft-ksp:test --tests 'com.blu3berry.kraft.sides.SideAliasEmitModeTest'`
Expected: All 3 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideAliasEmitModeTest.kt
git commit -m "test(kraft-ksp): cover FULL_NAME_ONLY emit-mode interactions"
```

---

## Task 14: Integration test — `@MapReverse` with both directions in different sides

**Files:**
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideAliasReverseTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.blu3berry.kraft.sides

import com.blu3berry.kraft.TestKspRunner
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class SideAliasReverseTest {

    @Test
    fun `forward and reverse aliases each match their own side`() {
        val sources = listOf(
            SourceFile.kotlin("Dto.kt", """
                package app.data.generated.models
                data class CategoryDto(val id: Int)
            """),
            SourceFile.kotlin("Domain.kt", """
                package app.domain.model
                data class Category(val id: Int)
            """),
            SourceFile.kotlin("Mapper.kt", """
                package app.mapper
                import app.data.generated.models.CategoryDto
                import app.domain.model.Category

                @com.blu3berry.kraft.config.MapReverse
                @com.blu3berry.kraft.config.MapConfig(
                    source = CategoryDto::class,
                    target = Category::class
                )
                object CategoryMapper
            """),
        )

        val generated = TestKspRunner.compileAndReturnGenerated(
            *sources.toTypedArray(),
            kspOptions = mapOf(
                "kraft.side.dto.name" to "Dto",
                "kraft.side.dto.packagePattern" to "**.data.generated.models.**",
                "kraft.side.domain.name" to "Domain",
                "kraft.side.domain.packagePattern" to "**.domain.model.**",
            )
        )

        val joined = generated.joinToString("\n") { it.readText() }
        assertThat(joined).contains("fun CategoryDto.toDomain(")  // forward
        assertThat(joined).contains("fun Category.toDto(")        // reverse
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :kraft-ksp:test --tests 'com.blu3berry.kraft.sides.SideAliasReverseTest'`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideAliasReverseTest.kt
git commit -m "test(kraft-ksp): cover @MapReverse interaction with side aliases"
```

---

## Task 15: Integration test — alias collision and config errors surface as compile errors

**Files:**
- Test: `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideAliasErrorTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.blu3berry.kraft.sides

import com.blu3berry.kraft.TestKspRunner
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class SideAliasErrorTest {

    @Test
    fun `alias name collision fails compilation with both mapper origins`() {
        val sources = listOf(
            SourceFile.kotlin("Models.kt", """
                package app.shared
                data class Source(val v: Int)
                data class TargetA(val v: Int)
                data class TargetB(val v: Int)
            """),
            SourceFile.kotlin("MapperA.kt", """
                package app.mapper
                import app.shared.Source
                import app.shared.TargetA

                @com.blu3berry.kraft.config.MapConfig(source = Source::class, target = TargetA::class)
                object SourceToTargetAMapper
            """),
            SourceFile.kotlin("MapperB.kt", """
                package app.mapper
                import app.shared.Source
                import app.shared.TargetB

                @com.blu3berry.kraft.config.MapConfig(source = Source::class, target = TargetB::class)
                object SourceToTargetBMapper
            """),
        )

        val result = TestKspRunner.compile(
            *sources.toTypedArray(),
            kspOptions = mapOf(
                "kraft.side.shared.name" to "Shared",
                "kraft.side.shared.packagePattern" to "app.shared.**",
            )
        )

        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("SourceToTargetAMapper")
        assertThat(result.messages).contains("SourceToTargetBMapper")
        assertThat(result.messages).contains("toShared")
    }

    @Test
    fun `pattern-overlap at config load surfaces as gradle config error`() {
        val sources = listOf(
            SourceFile.kotlin("Models.kt", """
                package app.x
                data class A(val v: Int)
                data class B(val v: Int)

                @com.blu3berry.kraft.config.MapConfig(source = A::class, target = B::class)
                object ABMapper
            """),
        )

        val result = TestKspRunner.compile(
            *sources.toTypedArray(),
            kspOptions = mapOf(
                "kraft.side.outer.name" to "Outer",
                "kraft.side.outer.packagePattern" to "**.data.**",
                "kraft.side.inner.name" to "Inner",
                "kraft.side.inner.packagePattern" to "**.data.api.**",
            )
        )

        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("build.gradle.kts")
        assertThat(result.messages).contains("subset")
    }

    @Test
    fun `no-side-registered project compiles unchanged`() {
        val sources = listOf(
            SourceFile.kotlin("Models.kt", """
                package app
                data class S(val v: Int)
                data class T(val v: Int)

                @com.blu3berry.kraft.config.MapConfig(source = S::class, target = T::class)
                object Mapper
            """),
        )

        val generated = TestKspRunner.compileAndReturnGenerated(*sources.toTypedArray())
        val joined = generated.joinToString("\n") { it.readText() }
        assertThat(joined).contains("fun S.toT(")
        assertThat(joined).doesNotContain(".toDomain(")
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :kraft-ksp:test --tests 'com.blu3berry.kraft.sides.SideAliasErrorTest'`
Expected: All 3 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/sides/SideAliasErrorTest.kt
git commit -m "test(kraft-ksp): cover alias-collision and pattern-overlap error paths"
```

---

## Task 16: Sample-app coverage in `composeApp`

**Files:**
- Modify: `composeApp/build.gradle.kts` (add KSP side-alias options — read first to confirm structure)
- Create: a small mapper file under `composeApp/src/commonMain/kotlin/...` exercising aliases
- Modify any `composeApp` source touching mappers if needed

- [ ] **Step 1: Inspect the sample app**

Read:
- `composeApp/build.gradle.kts`
- `find composeApp/src -type f -name "*Mapper*.kt" -o -name "*MapConfig*"`

Identify whether `composeApp` already uses `@MapConfig`. If yes, target one existing pair. If not, create a minimal pair (Dto + Domain + Mapper) and a usage call site that demonstrates both `.toDomain()` and the verbose form coexist.

- [ ] **Step 2: Add `kraft.side.*` KSP options to `composeApp/build.gradle.kts`**

Inside the existing `ksp { }` block (or add one if absent):

```kotlin
ksp {
    arg("kraft.side.dto.name", "Dto")
    arg("kraft.side.dto.packagePattern", "**.data.generated.models.**")
    arg("kraft.side.domain.name", "Domain")
    arg("kraft.side.domain.packagePattern", "**.domain.model.**")
}
```

- [ ] **Step 3: Add or update a mapper that demonstrates the alias**

Concrete example file at `composeApp/src/commonMain/kotlin/<root>/sample/CategoryMapper.kt`:

```kotlin
package <root>.sample

import com.blu3berry.kraft.config.MapConfig
import com.blu3berry.kraft.config.MapReverse

// Note: actual paths must match the side packagePatterns above. Adjust source/target
// FQNs so they fall under `**.data.generated.models.**` and `**.domain.model.**`.

@MapReverse
@MapConfig(source = SampleCategoryDto::class, target = SampleCategory::class)
object SampleCategoryMapper
```

(Engineer: replace `<root>` and add the two data classes in the matching packages. Keep the example tiny — one or two fields.)

- [ ] **Step 4: Add a `main`-side or test-side call demonstrating the alias**

Example (in any `commonMain` file):

```kotlin
fun demoSideAliases() {
    val dto = SampleCategoryDto(id = 1, name = "Demo")
    val domain = dto.toDomain()       // alias from side `Domain`
    val backDto = domain.toDto()      // alias from side `Dto` via @MapReverse
    println("${dto.name} → ${domain.name} → ${backDto.name}")
}
```

- [ ] **Step 5: Build the sample**

Run: `./gradlew :composeApp:assemble`
Expected: BUILD SUCCESSFUL — both `.toDomain()` and `.toDto()` resolve.

- [ ] **Step 6: Commit**

```bash
git add composeApp/build.gradle.kts \
        composeApp/src/commonMain/kotlin/<root>/sample/
git commit -m "docs(composeApp): demonstrate side-alias usage in the sample app"
```

---

## Final verification

- [ ] **Run the full test suite**

Run: `./gradlew test`
Expected: All tests PASS, including the existing test suite (regression guard) and the new side-alias suite.

- [ ] **Run KSP build end-to-end**

Run: `./gradlew :composeApp:kspCommonMainKotlinMetadata` (or the closest equivalent for the sample app's KSP target)
Expected: BUILD SUCCESSFUL — confirms the new feature works through the real KSP pipeline, not just the test harness.

- [ ] **Verify no orphan files in generated output**

Spot-check one generated file under `composeApp/build/generated/ksp/...` to confirm the alias delegate is present alongside the verbose function.

---

## Open Questions (for plan reviewer)

1. **Template syntax — `{var}` vs `${var}`.** The spec uses `{side}` / `{target}` / `{source}` (Mustache-style). The existing `GenerationConfig.functionNameTemplate` in `kraft-core` uses `${var}` (Kotlin-template-style). The plan above implements the spec verbatim. If consistency with the existing template syntax is preferred, swap the `AliasTemplate` parser to recognize `${...}` instead — straightforward find/replace plus updated tests. **Recommendation: stick with `{var}` (spec is canonical) and consider unifying both engines in a follow-up.**

2. **Sample-app integration scope (Task 16).** Task 16 sketches a minimal example. If `composeApp` already has a representative mapper pair, the engineer should adapt that one rather than create new types. Confirm before execution.

If either is decided differently, list it under the `Pre-cleared deviations` section at the top of this plan before execution begins.
