# KSP Options

Kraft supports KSP processor options to customize code generation behavior. There are two ways to set them in `build.gradle.kts`:

- **The `kraft { }` DSL** (recommended) — the typed extension registered by the [Kraft Gradle plugin](getting-started.md#the-gradle-plugin-recommended). Everything it sets is translated 1:1 into the raw options below.
- **Raw `ksp { arg(...) }`** — works with or without the plugin (required for manual wiring). If both set the same option, the DSL value wins with a build warning; a raw `kraft.moduleId` is kept unless the DSL sets `moduleId` explicitly.

## kraft.functionNameFormat

Customize the generated extension function name pattern.

### Default

`to${target}` -- generates functions like `toUserDto()`, `toOrder()`, `toStatusDto()`.

### Template Variables

| Variable | Description |
|----------|-------------|
| `${target}` | Simple name of the target class |
| `${source}` | Simple name of the source class |

### Configuration

With the Gradle plugin:

```kotlin
// build.gradle.kts
kraft {
    functionNameFormat = "map\${source}To\${target}"
}
```

Or as a raw KSP arg:

```kotlin
// build.gradle.kts — default behavior
ksp {
    arg("kraft.functionNameFormat", "to\${target}")
}
```

```kotlin
// build.gradle.kts — custom: includes source name
ksp {
    arg("kraft.functionNameFormat", "map\${source}To\${target}")
}
```

```kotlin
// build.gradle.kts — custom: different prefix
ksp {
    arg("kraft.functionNameFormat", "convert\${target}")
}
```

> **Note:** Only one format is active per build (the last `arg()` call wins).

### Examples

Given this mapping:

```kotlin
data class UserDto(val name: String)
data class User(val name: String)

@MapConfig(source = UserDto::class, target = User::class)
object UserMapper
```

| Format | Generated function |
|--------|--------------------|
| `to${target}` (default) | `fun UserDto.toUser()` |
| `map${source}To${target}` | `fun UserDto.mapUserDtoToUser()` |
| `convert${target}` | `fun UserDto.convertUser()` |

### Applies to Enum Mappers Too

The format option applies equally to enum mappers declared with `@MapEnum`.

```kotlin
enum class SourceStatus { ACTIVE, INACTIVE }
enum class TargetStatus { ACTIVE, INACTIVE }

@MapEnum(source = SourceStatus::class, target = TargetStatus::class)
object StatusMapper
```

| Format | Generated function |
|--------|--------------------|
| `to${target}` (default) | `fun SourceStatus.toTargetStatus()` |
| `map${source}To${target}` | `fun SourceStatus.mapSourceStatusToTargetStatus()` |

### Multiplatform Projects

For Kotlin Multiplatform projects, place the `ksp` block inside the module that applies the KSP plugin:

```kotlin
// shared/build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
}

ksp {
    arg("kraft.functionNameFormat", "map\${source}To\${target}")
}
```

The option applies globally to all mappers processed by that KSP invocation.

## kraft.moduleId

Disambiguates the generated `@KraftConverterDelegate` registry file when multiple modules contribute `@KraftConverter` functions to the same compile classpath. See [Custom Converters — Global Converters](custom-converters.md#global-converters) for the full feature.

### Default

**With the Gradle plugin:** the project path (e.g. `:feature:auth`) — cross-module diagnostics name real modules with no configuration.

**Without the plugin:** unset. Kraft falls back to a deterministic content hash of the module's converter FQN list — the same converter set always produces the same hash, but collisions remain possible (two modules declaring identical converter signatures hash identically). Setting an explicit module ID is recommended for any manually-wired project with more than one converter-producing module.

### Configuration

Override with the DSL:

```kotlin
kraft {
    moduleId = "upstream"
}
```

Or set the raw option in each producing module's `build.gradle.kts`:

```kotlin
// upstream/build.gradle.kts
ksp {
    arg("kraft.moduleId", "upstream")
}
```

```kotlin
// consumer/build.gradle.kts
ksp {
    arg("kraft.moduleId", "consumer")
}
```

The value is sanitized into a Kotlin file-name suffix, so any non-alphanumeric character is replaced with `_`. The Gradle project path (e.g. `:feature:auth`) is a good source of values.

### When you do **not** need this option

- Single-module projects.
- Projects where exactly one module declares `@KraftConverter` functions.
- Projects that consume converters from a published library but do not declare any of their own.

In those cases the default hash is sufficient.

## kraft.side.*

Register named layers (`Dto`, `Domain`, `Entity`, …) so Kraft emits short alias extensions like `.toDomain()` alongside the verbose mappers. See [Side Aliases](side-aliases.md) for the full feature guide; this section is a reference of the four KSP keys.

### Default

Unset. No sides are registered, no aliases are emitted, and behaviour is identical to a build with no side configuration.

### Keys

Each side is identified by a slot key (your choice of lowercase identifier — e.g. `dto`, `domain`). All four keys share the prefix `kraft.side.<slot>.`:

| Key | Required | Default | Type |
|---|---|---|---|
| `kraft.side.<slot>.name` | yes | — | string — substituted into `{side}` in the template, verbatim |
| `kraft.side.<slot>.packagePattern` | yes | — | Ant-style glob matched against the target class FQN |
| `kraft.side.<slot>.template` | no | `to{side}` | string — alias function name; variables: `{side}`, `{target}`, `{source}` |
| `kraft.side.<slot>.emitMode` | no | `BOTH` | `BOTH` or `FULL_NAME_ONLY` |

### Configuration

With the DSL, each `side("<slot>")` block maps to the four keys — `sideName` → `name` (defaults to the slot capitalized), `packagePattern`, `template`, `emitMode`:

```kotlin
// build.gradle.kts
kraft {
    side("dto")    { packagePattern = "com.example.**.dto.**" }
    side("domain") {
        packagePattern = "com.example.**.domain.**"
        template = "into{side}"          // optional
        emitMode = "FULL_NAME_ONLY"      // optional
    }
}
```

Or as raw KSP args:

```kotlin
// build.gradle.kts
ksp {
    arg("kraft.side.dto.name",           "Dto")
    arg("kraft.side.dto.packagePattern", "com.example.**.dto.**")

    arg("kraft.side.domain.name",           "Domain")
    arg("kraft.side.domain.packagePattern", "com.example.**.domain.**")
}
```

For a `@MapConfig(source = CategoryDto::class, target = Category::class)` mapper where `Category` lives under `com.example.feature.domain`, this generates:

```kotlin
public fun CategoryDto.toCategory(): Category = /* … */

/** Alias generated for side Domain (template = to{side}) */
public fun CategoryDto.toDomain(): Category = toCategory()
```

### Per-mapper override

A `@MapConfig` can opt out of (or into) alias emission via `aliasEmitMode = AliasEmitMode.{INHERIT, BOTH, FULL_NAME_ONLY}`. See [Side Aliases — Per-Mapper Override](side-aliases.md#per-mapper-override).
