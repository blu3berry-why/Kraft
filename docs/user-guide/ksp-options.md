# KSP Options

Kraft supports KSP processor options to customize code generation behavior. These options are set in your `build.gradle.kts` file inside the `ksp` block.

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

Set the option in your module's `build.gradle.kts`:

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

Unset. Kraft falls back to a deterministic content hash of the module's converter FQN list. The hash is unique per converter set, but two unrelated modules that happen to declare the same converter signatures would still collide. Setting an explicit module ID is recommended for any project with more than one converter-producing module.

### Configuration

Set the option in each producing module's `build.gradle.kts`:

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
