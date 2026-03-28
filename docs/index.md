# Kraft

**Compile-time mapper generation for Kotlin Multiplatform.**

Kraft is a KSP-based annotation processor that generates type-safe extension functions to map between data classes. No reflection, no runtime overhead — just clean, generated Kotlin code.

## Features

- **Annotation-driven** — `@MapFrom`, `@MapTo`, or `@MapConfig` to declare mappings
- **Property matching** — same-name properties mapped automatically
- **Field renaming** — `@MapField` or `@FieldMapping` for cross-name mapping
- **Nested objects** — auto-detected or explicit `@MapNested`, with implicit child mapper generation
- **Collections** — `List<T>` and `Set<T>` with nested element mapping
- **Enum mapping** — `@MapEnum` with auto-matching and custom entry pairs
- **Custom converters** — `@MapUsing` for property-source or whole-source transformations
- **Reverse mapping** — `@MapReverse` generates the inverse mapper automatically
- **Ignore rules** — `@MapIgnore` and `@MapIgnoreField` with directional control
- **Plugin SPI** — implement `MapperGeneratorProvider` to plug in your own code generator
- **Kotlin Multiplatform** — annotations work on JVM, iOS, JS, and WasmJs

## Quick Start

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

```kotlin
data class User(val id: Int, val name: String, val email: String)

@MapFrom(User::class)
data class UserDto(val id: Int, val name: String, val email: String)

// Generated: fun User.toUserDto(): UserDto
```

## Documentation

- [Getting Started](user-guide/getting-started.md) — installation and first mapper
- [User Guide](user-guide/basic-mapping.md) — all features with examples
- [Architecture](developer-guide/architecture.md) — internals and SPI guide
- [Contributing](https://github.com/blu3berry-why/Kraft/blob/main/CONTRIBUTING.md) — how to contribute
- [AI Integration](user-guide/ai-integration.md) — use Kraft with AI coding agents
