<p align="center">
  <img src="docs/assets/kraft-banner.png" alt="Kraft — mappers you write, generated. Compile-time KSP, no reflection, no runtime cost, Kotlin Multiplatform." width="100%">
</p>

<p align="center">
  <a href="https://central.sonatype.com/namespace/com.blu3berry.kraft"><img src="https://img.shields.io/maven-central/v/com.blu3berry.kraft/kraft-annotations.svg?label=Maven%20Central" alt="Maven Central"></a>
  <a href="https://github.com/blu3berry-why/Kraft/releases/latest"><img src="https://img.shields.io/github/v/release/blu3berry-why/Kraft?label=release&color=blue" alt="GitHub Release"></a>
  <a href="https://github.com/blu3berry-why/Kraft/actions/workflows/test.yml"><img src="https://github.com/blu3berry-why/Kraft/actions/workflows/test.yml/badge.svg" alt="Test"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.2%2B-7F52FF.svg" alt="Kotlin"></a>
</p>

Every Kotlin codebase has that file. Forty lines of `id = id, name = name, email = email` that nobody wanted to write and nobody wants to review. Kraft deletes it:

```diff
- fun User.toUserDto(): UserDto = UserDto(
-     id = id,
-     name = name,
-     email = email,
- )
+ @MapConfig(source = User::class, target = UserDto::class)
+ object UserMapper
```

```kotlin
val dto = user.toUserDto() // generated at compile time
```

That's the whole mapper. Kraft is a KSP processor that generates type-safe extension functions between your data classes — plain, readable Kotlin you can inspect in `build/generated`. No reflection, no runtime library, nothing to ship.

## Why Kraft

- **Breaks at build time, not in production** — rename a property and the generated mapper stops compiling right there, instead of passing `null` around at runtime.
- **Zero runtime cost** — the generated code is exactly what you'd have written by hand, minus the writing.
- **Multiplatform from the start** — annotations live in `commonMain`; JVM, Android, iOS, JS, and WasmJs all work.
- **Escape hatches everywhere** — renames, ignores, custom converters, enum tables: when your models disagree, you override one property, not the whole mapper.

## Features

### Mapping

- **Automatic property matching** — same-name properties mapped without configuration
- **Field renaming** — `@MapField` or `@FieldMapping` for cross-name mapping
- **Nested objects** — auto-detected when types differ, including renamed properties
- **Collections** — `List<T>` and `Set<T>` with nested element mapping
- **Enum mapping** — `@MapEnum` with auto-matching and custom entry pairs
- **Reverse mapping** — `@MapReverse` generates the inverse mapper automatically

### Control & customization

- **Custom converters** — `@MapUsing` for property-source or whole-source transformations
- **Global converters** — `@KraftConverter` on a top-level extension is auto-discovered across the module and the classpath, with `@OptIn` markers propagated onto the generated mapper
- **Ignore rules** — `@MapIgnore` and `@MapIgnoreField` with directional control
- **Configuration objects** — `@MapConfig` for mapping without modifying data classes
- **Layer-aware aliases** — register `Dto` / `Domain` / `Entity` sides via Gradle to emit short `.toDomain()` / `.toEntity()` extensions alongside the verbose mappers

### Platform & tooling

- **Kotlin Multiplatform** — annotations work on JVM, iOS, JS, and WasmJs
- **Plugin SPI** — implement `MapperGeneratorProvider` to plug in your own code generator

## Requirements

- **Kotlin 2.2+** (built and tested with Kotlin 2.2.21)
- **KSP 2.3.3** (use a KSP version matching your Kotlin version)
- **JDK 17+ build toolchain** — the processor runs at compile time only

Kraft runs entirely at build time; the generated mappers are plain Kotlin and the annotations target JVM 1.8, so **legacy apps on older runtimes are supported** — see [Compatibility and legacy projects](https://blu3berry-why.github.io/Kraft/user-guide/getting-started/#compatibility-and-legacy-projects).

## Installation

Kraft is published to **Maven Central** under the `com.blu3berry.kraft` group (latest version shown in the badge above). Make sure `mavenCentral()` is in your `repositories { }` block, then add the dependencies below — replace `<version>` with the current release.

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
