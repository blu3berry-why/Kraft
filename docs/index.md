[![GitHub Release](https://img.shields.io/badge/dynamic/json?url=https://api.github.com/repos/blu3berry-why/Kraft/releases/latest&query=tag_name&label=release&color=blue)](https://github.com/blu3berry-why/Kraft/releases/latest)

# Kraft

**Compile-time mapper generation for Kotlin Multiplatform.**

Kraft is a KSP-based annotation processor that generates type-safe extension functions to map between data classes. No reflection, no runtime overhead — just clean, generated Kotlin code.

## Features

- **Annotation-driven** — `@MapConfig`, `@MapFrom`, or `@MapTo` to declare mappings
- **Property matching** — same-name properties mapped automatically
- **Field renaming** — `@MapField` or `@FieldMapping` for cross-name mapping
- **Nested objects** — auto-detected when types differ, with implicit child mapper generation; use `@MapField` / `FieldMapping` for renamed pairs (`@MapNested` is deprecated)
- **Collections** — `List<T>` and `Set<T>` with nested element mapping
- **Enum mapping** — `@MapEnum` with auto-matching and custom entry pairs
- **Custom converters** — `@MapUsing` for property-source or whole-source transformations
- **Global converters** — `@KraftConverter` on a top-level extension is auto-discovered across the module and the classpath, with `@OptIn` markers propagated onto the generated mapper
- **Reverse mapping** — `@MapReverse` generates the inverse mapper automatically
- **Layer-aware aliases** — register `Dto` / `Domain` / `Entity` sides via Gradle to emit short `.toDomain()` / `.toEntity()` extensions alongside the verbose mappers
- **Ignore rules** — `@MapIgnore` and `@MapIgnoreField` with directional control
- **Plugin SPI** — implement `MapperGeneratorProvider` to plug in your own code generator
- **Kotlin Multiplatform** — annotations work on JVM, iOS, JS, and WasmJs

## Quick Start

JVM / Android:

```kotlin
// build.gradle.kts
plugins {
    id("com.google.devtools.ksp") version "2.3.3"
}

dependencies {
    implementation("com.blu3berry.kraft:kraft-annotations:<version>")
    ksp("com.blu3berry.kraft:kraft-ksp:<version>")
}
```

Kotlin Multiplatform:

```kotlin
// build.gradle.kts
plugins {
    id("com.google.devtools.ksp") version "2.3.3"
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

See [Getting Started](user-guide/getting-started.md) for full installation details.

```kotlin
data class User(val id: Int, val name: String, val email: String)
data class UserDto(val id: Int, val name: String, val email: String)

@MapConfig(source = User::class, target = UserDto::class)
object UserMapper

// Generated: fun User.toUserDto(): UserDto
```

## Documentation

- [Getting Started](user-guide/getting-started.md) — installation and first mapper
- [User Guide](user-guide/basic-mapping.md) — all features with examples
- [Architecture](developer-guide/architecture.md) — internals and SPI guide
- [Contributing](https://github.com/blu3berry-why/Kraft/blob/main/CONTRIBUTING.md) — how to contribute
- [AI Integration](user-guide/ai-integration.md) — use Kraft with AI coding agents
