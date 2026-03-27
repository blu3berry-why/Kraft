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
