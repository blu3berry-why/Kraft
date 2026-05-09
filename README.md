[![GitHub Release](https://img.shields.io/badge/dynamic/json?url=https://api.github.com/repos/blu3berry-why/Kraft/releases/latest&query=tag_name&label=release&color=blue)](https://github.com/blu3berry-why/Kraft/releases/latest)
[![Test](https://github.com/blu3berry-why/Kraft/actions/workflows/test.yml/badge.svg)](https://github.com/blu3berry-why/Kraft/actions/workflows/test.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF.svg)](https://kotlinlang.org)

# Kraft

Compile-time mapper generation for Kotlin Multiplatform, powered by KSP.

Kraft generates type-safe extension functions to map between data classes. No reflection, no runtime overhead — just clean, generated Kotlin code.

```kotlin
data class User(val id: Int, val name: String, val email: String)
data class UserDto(val id: Int, val name: String, val email: String)

@MapConfig(source = User::class, target = UserDto::class)
object UserMapper

// Generated: fun User.toUserDto(): UserDto
val dto = user.toUserDto()
```

## Features

- **Automatic property matching** — same-name properties mapped without configuration
- **Field renaming** — `@MapField` or `@FieldMapping` for cross-name mapping
- **Nested objects** — auto-detected when types differ, including renamed properties
- **Collections** — `List<T>` and `Set<T>` with nested element mapping
- **Enum mapping** — `@MapEnum` with auto-matching and custom entry pairs
- **Custom converters** — `@MapUsing` for property-source or whole-source transformations
- **Global converters** — `@KraftConverter` on a top-level extension is auto-discovered across the module and the classpath, with `@OptIn` markers propagated onto the generated mapper
- **Reverse mapping** — `@MapReverse` generates the inverse mapper automatically
- **Layer-aware aliases** — register `Dto` / `Domain` / `Entity` sides via Gradle to emit short `.toDomain()` / `.toEntity()` extensions alongside the verbose mappers
- **Ignore rules** — `@MapIgnore` and `@MapIgnoreField` with directional control
- **Configuration objects** — `@MapConfig` for mapping without modifying data classes
- **Plugin SPI** — implement `MapperGeneratorProvider` to plug in your own code generator
- **Kotlin Multiplatform** — annotations work on JVM, iOS, JS, and WasmJs

## Requirements

- Kotlin 2.0+
- KSP (matching your Kotlin version)
- JVM 17+ for the build toolchain (the processor runs at compile time)

## Installation

### Kotlin Multiplatform

```kotlin
// build.gradle.kts
plugins {
    id("com.google.devtools.ksp") version "<ksp-version>"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.blu3berry.kraft:kraft-annotations:<version>")
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", "com.blu3berry.kraft:kraft-ksp:<version>")
}
```

### JVM / Android

```kotlin
// build.gradle.kts
plugins {
    id("com.google.devtools.ksp") version "<ksp-version>"
}

dependencies {
    implementation("com.blu3berry.kraft:kraft-annotations:<version>")
    ksp("com.blu3berry.kraft:kraft-ksp:<version>")
}
```

## Quick Reference

| Annotation | Purpose | Placement |
|-----------|---------|-----------|
| `@MapConfig` | Standalone mapping config | On object |
| `@MapFrom` | Map from source class | On target data class |
| `@MapTo` | Map to target class | On source data class |
| `@MapField` | Rename a property | On property in `@MapFrom`/`@MapTo` class |
| `@MapNested` | ~~Deprecated~~ — auto-detected | On property in `@MapFrom`/`@MapTo` class |
| `@MapIgnore` | Skip a property | On property (must have default value) |
| `@MapUsing` | Custom converter function | On function in `@MapConfig` object |
| `@KraftConverter` | Globally discoverable converter | On top-level extension function |
| `@MapEnum` | Enum-to-enum mapping | On object |
| `@MapReverse` | Generate inverse mapper | On class or `@MapConfig` object |
| `aliasEmitMode` | Per-mapper alias control (`AliasEmitMode` enum) | Parameter on `@MapConfig` and `@MapEnum` |
| `ConverterDirection` | Direction selector for `@MapUsing` (`AUTO` / `FORWARD` / `REVERSE`) | Parameter on `@MapUsing(direction = …)` |
| `IgnoreSide` | Direction selector for `@MapIgnoreField` (`TARGET` / `SOURCE` / `BOTH`) | Parameter on `@MapIgnoreField(direction = …)` |
| `@FieldMapping` | Config-level rename | In `@MapConfig.fieldMappings` |
| `@NestedMapping` | ~~Deprecated~~ — auto-detected | In `@MapConfig.nestedMappings` |
| `@MapIgnoreField` | Config-level ignore | In `@MapConfig.ignoredMappings` |

## Documentation

- [Getting Started](https://blu3berry-why.github.io/Kraft/user-guide/getting-started/)
- [User Guide](https://blu3berry-why.github.io/Kraft/user-guide/basic-mapping/)
- [Architecture & SPI](https://blu3berry-why.github.io/Kraft/developer-guide/architecture/)
- [AI Integration](https://blu3berry-why.github.io/Kraft/user-guide/ai-integration/)
- [Contributing](CONTRIBUTING.md)

## License

```
Copyright Kraft Contributors

Licensed under the Apache License, Version 2.0
```

See [LICENSE](LICENSE) for the full text.
