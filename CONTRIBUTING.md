# Contributing to Kraft

## Static Analysis

Kraft uses [detekt](https://detekt.dev/) for static analysis. All code must pass detekt with zero violations — no baseline is used.

### Running detekt locally

```bash
./gradlew detekt
```

### Configuration

The detekt configuration lives at `detekt.yml` in the repository root. Key settings:

- Max line length: 120 characters
- Max method length: 60 lines
- Cyclomatic complexity threshold: 15
- Long parameter list threshold: 8
- Wildcard imports: disabled
- TODO/FIXME comments: forbidden (use issues/tasks instead)

### Suppressing false positives

If a rule does not apply to a specific case, use a targeted `@Suppress`:

```kotlin
@Suppress("LongMethod")
private fun legitimatelyLongMethod() { /*...*/ }
```

Prefer fixing the violation over suppressing it. Suppressions should be rare and justified.

### Pre-commit hook

Install [lefthook](https://github.com/evilmartians/lefthook) to run detekt automatically before each commit:

```bash
brew install lefthook   # or: npm install -g lefthook
lefthook install
```

### CI

Detekt runs automatically on every pull request via GitHub Actions. PRs with violations will fail the check.

## Commit Conventions

Kraft uses [Conventional Commits](https://www.conventionalcommits.org/). The commit message drives automated versioning via the project's `create-release.yml` workflow.

| Prefix | Meaning | Version bump |
|--------|---------|-------------|
| `fix:` | Bug fix | Patch (0.0.X) |
| `feat:` | New feature | Minor (0.X.0) |
| `feat!:` or `BREAKING CHANGE:` | Breaking change | Major (X.0.0) — **minor while pre-1.0, see below** |
| `docs:` | Documentation only | No release |
| `refactor:` | Code restructuring | No release |
| `test:` | Adding/fixing tests | No release |
| `ci:` | CI/CD changes | No release |

> **Pre-1.0 rule:** while the project version is `0.x`, breaking changes (`!` suffix or
> `BREAKING CHANGE:`) bump the **minor** version, not the major — per semver, 0.x makes
> no stability promise, and 1.0.0 must never happen as a side effect of a commit marker.
> Cutting `1.0.0` (or any later major ahead of schedule) is a deliberate act: dispatch
> the **Create Release** workflow with its `version-override` input set to the exact
> version. After 1.0, breaking changes bump the major version as the table says.

Examples:
```text
fix: resolve nullable nested property codegen
feat: add Map<K,V> collection mapping support
feat!: rename MapperGenerator SPI interface
docs: add reverse mapping user guide
```

## Testing

### Test Infrastructure

Tests use [kotlin-compile-testing](https://github.com/ZacSweers/kotlin-compile-testing) with KSP support. The `TestKspRunner` object provides two methods:

- `compile(vararg sources, kspOptions)` — compiles sources with KSP and returns the compilation result
- `compileAndReturnGenerated(vararg sources, kspOptions)` — compiles and returns only the generated `.kt` files

Before writing a new fixture, check [Testing the Processor](docs/developer-guide/ksp-compile-testing-gotchas.md) — notably: select generated files by name (never `generated.first()`, its order is filesystem-dependent), and give converter-declaring fixtures a `package` line.

### Writing a Happy-Path Test

```kotlin
@Test
fun `descriptive test name in backticks`() {
    val source = SourceFile.kotlin("Models.kt", """
        package test

        import com.blu3berry.kraft.mapping.MapFrom

        data class Source(val name: String, val age: Int)

        @MapFrom(Source::class)
        data class Target(val name: String, val age: Int)
    """)

    val generated = TestKspRunner.compileAndReturnGenerated(source)
    val content = generated.joinToString("\n") { it.readText() }

    assertThat(content).contains("fun Source.toTarget()")
    assertThat(content).contains("name = this.name")
}
```

### Writing an Error-Path Test

```kotlin
@Test
fun `error when source property is missing`() {
    val source = SourceFile.kotlin("Models.kt", """
        package test

        import com.blu3berry.kraft.mapping.MapFrom

        data class Source(val name: String)

        @MapFrom(Source::class)
        data class Target(val name: String, val missing: Int)
    """)

    val result = TestKspRunner.compile(source)
    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("missing")
}
```

### Test Directory Structure

Tests are organized by feature in `kraft-ksp/src/jvmTest/kotlin/com/blu3berry/kraft/`:

```text
basic/           — simple mapping, rename, multi-mapper, annotation guards
nested/          — auto-detection, @MapNested, config nested, collections, nullable, circular
mapenum/         — auto, custom, mixed, error cases
converter/       — property-source, whole-source, extension functions, type matching
mapignore/       — class-level @MapIgnore
configignore/    — config-level @MapIgnoreField, IgnoreSide
reverse/         — @MapReverse with renames, nested, converters
optin/           — @OptIn propagation from @KraftConverter to generated functions
sides/           — side-alias happy paths, error cases, emit modes, enum aliases, @MapReverse interaction, registry parsing and collision detection
propertyresolver/ — unit tests for individual MappingRule implementations
```

### Running Tests

```bash
./gradlew :kraft-ksp:jvmTest           # all tests
./gradlew :kraft-ksp:jvmTest --tests "*.SimpleMappingTest"  # single class
```

## How to Add a New MappingRule

The property resolver uses an ordered chain of rules. Each rule tries to resolve a target property to a `PropertyMappingStrategy`. To add a new rule:

1. Create a new class in `kraft-core/src/main/kotlin/.../propertyresolver/rules/` implementing `MappingRule`
2. Implement `tryResolve(target: PropertyInfo, ctx: MappingContext): PropertyMappingStrategy?`
   - Return a strategy if the rule applies, `null` to pass to the next rule
3. Register the rule in `PropertyResolver`'s rule chain (order matters — first match wins)
4. Add unit tests in `kraft-ksp/src/jvmTest/.../propertyresolver/rules/`
5. Add integration tests with full KSP compilation

For the current rule chain order, see [Architecture: Property Resolver Rule Chain](docs/developer-guide/architecture.md#property-resolver-rule-chain).

## How to Add a New Annotation

1. Define the annotation in `kraft-annotations/src/commonMain/kotlin/` (under `mapping/` or `config/`)
2. Add the FQ name constant to `KraftKspConstants` in kraft-core
3. Add scanning logic in the appropriate scanner (`ClassAnnotationScanner`, `ConfigObjectScanner`, or `EnumMapScanner`)
4. If needed, add a scan result model in `kraft-core/src/main/kotlin/.../model/scan/`
5. Wire into descriptor building in the appropriate builder
6. Add tests covering happy-path and error-path

## Release Process

Releases are driven by two GitHub Actions workflows. The version is stored in `gradle.properties` as `kraft.version` (and mirrored in `.release-please-manifest.json`) and shared across all modules.

1. A maintainer manually triggers the **Create Release** workflow (`create-release.yml`) via `workflow_dispatch`.
2. The workflow reads the current version from `.release-please-manifest.json`, then walks commits since the last `MAJOR.MINOR.PATCH` tag — inspecting both commit subjects and bodies — and computes the next version: a `BREAKING CHANGE` or `!`-suffix commit triggers a major bump, a `feat:` commit triggers a minor bump, anything else triggers a patch bump.
3. A `release/<version>` branch is created; `.release-please-manifest.json` and `gradle.properties` are bumped, committed, and pushed; a PR targeting `release-branch` is opened automatically.
4. A maintainer reviews and merges the PR into `release-branch`.
5. The push to `release-branch` triggers the **Publish** workflow (`publish.yml`), which validates GPG and Maven Central secrets, signs the artifacts, and publishes all modules to Maven Central.
6. After publishing, a GitHub Release is created with auto-generated notes (`gh release create --generate-notes`), and the updated version files are synced back to `main`.
