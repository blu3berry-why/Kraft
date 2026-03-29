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

Kraft uses [Conventional Commits](https://www.conventionalcommits.org/). The commit message drives automated versioning via release-please.

| Prefix | Meaning | Version bump |
|--------|---------|-------------|
| `fix:` | Bug fix | Patch (0.0.X) |
| `feat:` | New feature | Minor (0.X.0) |
| `feat!:` or `BREAKING CHANGE:` | Breaking change | Major (X.0.0) |
| `docs:` | Documentation only | No release |
| `refactor:` | Code restructuring | No release |
| `test:` | Adding/fixing tests | No release |
| `ci:` | CI/CD changes | No release |

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

Kraft uses [release-please](https://github.com/googleapis/release-please) for automated releases:

1. Merge PRs with conventional commit messages to `main`
2. Run the "Create Release" workflow (manual dispatch) — computes next version from commit history
3. Review and merge the generated release PR to `release-branch`
4. The publish workflow auto-triggers, publishing to GitHub Packages
5. A GitHub Release is created with auto-generated changelog

Version is stored in `gradle.properties` as `kraft.version` and shared across all modules.
