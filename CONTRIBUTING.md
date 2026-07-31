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

Kraft uses [Conventional Commits](https://www.conventionalcommits.org/). The commit message drives automated versioning via the project's `release-please.yml` workflow.

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
> Cutting `1.0.0` (or any later major ahead of schedule) is a deliberate act: land a
> commit whose body contains `Release-As: 1.0.0` and release-please retargets the
> release PR to it. After 1.0, breaking changes bump the major version as the table says.

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

Releases are driven by [release-please](https://github.com/googleapis/release-please). The version is stored in `gradle.properties` as `kraft.version` (kept in sync by release-please via the `x-release-please-*` markers around it — do not remove them) and mirrored in `.release-please-manifest.json`.

The workflow authenticates with the `RELEASE_PLEASE_TOKEN` repository secret — a fine-grained PAT with **contents: write** and **pull-requests: write** on this repo. The default `GITHUB_TOKEN` cannot be used: releases it creates never trigger other workflows, so `publish.yml` would not fire.

1. Every push to `main` runs the **Release Please** workflow (`release-please.yml`), which maintains a rolling release PR containing the version bump and CHANGELOG, computed from conventional commits since the last release. Pre-1.0, breaking changes bump **minor** (`bump-minor-pre-major`); `feat:` bumps minor; `fix:` bumps patch; docs/refactor/test/ci accumulate without forcing a release.
2. When you want to release, review and merge the release PR. Nothing publishes before that merge — the release PR is the dry run.
3. Merging it creates the git tag and the GitHub Release, which triggers the **Publish** workflow (`publish.yml`) to sign and publish all modules to Maven Central.
4. To force an exact version (e.g. the deliberate `1.0.0`), land a commit on `main` whose body contains `Release-As: 1.0.0` — release-please retargets the release PR to that version.

### Pre-releases (RCs)

Pre-release versions (e.g. `0.13.0-RC01`) exist to validate Kraft against a new
Kotlin/KSP pair in a real consumer project before promoting to stable.

The chosen path is the one-off `Release-As` footer — no release-please config
change (a sustained `prerelease` config cannot be used while Kraft is pre-1.0:
release-please's `prerelease` option also marks every pre-major `0.x` release
as a GitHub pre-release):

1. **Cut the RC:** land a commit on `main` whose body contains
   `Release-As: 0.13.0-RC01`. Release-please retargets the release PR to that
   version; merging it tags `0.13.0-RC01`, creates the GitHub Release, and
   `publish.yml` publishes the artifacts to Maven Central as normal.
2. **Docs stay stable:** `docs-deploy.yml` skips any release whose tag contains
   `-`, so an RC release never overwrites the stable GitHub Pages site. This
   guard applies to automatic release-triggered deploys only — a manual
   `workflow_dispatch` deploys whatever ref it is run on, so don't dispatch it
   on an RC tag.
3. **Promote to stable:** after validation, land a commit with
   `Release-As: 0.13.0` and merge the retargeted release PR. Use the explicit
   footer for the promotion — do not rely on the automatic bump from an RC
   version, which is not deterministic across release-please versions.

Consumers on `latest.release` or version ranges may still resolve an RC from
Maven Central (Gradle does not treat `-RC` as special in `latest.release`), so
announce RCs as opt-in and keep their validation window short.

> **Transition note:** the legacy `create-release.yml` + `release-branch` flow is still present but dormant; it will be deleted after the first verified release-please release. Its `version-override` dispatch input is superseded by the `Release-As:` footer. Release PRs are opened by the `RELEASE_PLEASE_TOKEN` PAT's user, so PR CI runs on them like any other PR — they only touch version files and CHANGELOG.
