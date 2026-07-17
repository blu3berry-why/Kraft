# Getting Started

Kraft is a Kotlin Symbol Processing (KSP) plugin that generates type-safe mapping functions between data classes at compile time. Instead of writing boilerplate conversion code by hand, you annotate your classes and Kraft generates extension functions for you.

## Installation

Kraft is built and tested with **Kotlin 2.2.21** and **KSP 2.3.3**, and the processor runs on a **JDK 17** build toolchain (Kotlin 2.2+ recommended — see [Compatibility and legacy projects](#compatibility-and-legacy-projects) below). It is published to **Maven Central** under the `com.blu3berry.kraft` group, so make sure `mavenCentral()` is in your `repositories { }` block. Add the following to your `build.gradle.kts` (replace `<version>` with the [latest release](https://central.sonatype.com/namespace/com.blu3berry.kraft)):

```kotlin
plugins {
    id("com.google.devtools.ksp") version "<ksp-version>"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.blu3berry.kraft:kraft-annotations:<version>")
    ksp("com.blu3berry.kraft:kraft-ksp:<version>")
}
```

### Kotlin Multiplatform — the Gradle plugin (recommended)

For KMP modules, the Kraft Gradle plugin replaces all of the wiring below with one line:

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("com.google.devtools.ksp") version "<ksp-version>"
    id("com.blu3berry.kraft") version "<version>"
}
```

It adds the `kraft-ksp` and `kraft-annotations` dependencies pinned to its own version (all modules on one classpath must use the same Kraft version — the plugin makes that automatic), wires the generated sources into `commonMain`, orders KSP before compilation, and sets `kraft.moduleId` to the project path so cross-module diagnostics name real modules. The Kotlin Multiplatform and KSP plugins are required alongside it — Kraft deliberately doesn't force their versions onto your build; if either is missing, the build fails with instructions.

#### Configuring sides and naming with the `kraft { }` DSL

Instead of hand-writing raw `ksp { arg("kraft.side.…", …) }` options, configure Kraft through the typed extension the plugin registers:

```kotlin
kraft {
    // Custom generated function name (optional; default is `to${target}`).
    functionNameFormat = "to\${target}From\${source}"

    // Named sides: a class in a side's package gets a short alias (e.g. `toDomain`).
    side("dto")    { packagePattern = "com.example.dto.**" }      // name defaults to "Dto"
    side("domain") { packagePattern = "com.example.domain.**" }   // name defaults to "Domain"
}
```

Each `side("<slot>")` maps to the `kraft.side.<slot>.*` options: `packagePattern` is required, `name` defaults to the slot capitalized, and `template` / `emitMode` are overridable. `moduleId` is also settable here but defaults to the project path. Everything the DSL sets is translated 1:1 into KSP options — a raw `ksp { arg(...) }` block still works and coexists with it.

### Kotlin Multiplatform — manual wiring

Equivalent manual setup, if you prefer explicit control:

```kotlin
kotlin {
    sourceSets {
        commonMain {
            // Make generated mappers visible to common source code
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
        }
        commonMain.dependencies {
            implementation("com.blu3berry.kraft:kraft-annotations:<version>")
        }
    }
}

// Add KSP for common main metadata:
dependencies {
    add("kspCommonMainMetadata", "com.blu3berry.kraft:kraft-ksp:<version>")
}

// Ensure KSP runs before compilation so generated code is available:
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
```

The `kotlin.srcDir` line adds the KSP output directory to your common source set so the IDE and all platform compilations can see the generated mappers. The `dependsOn` block ensures `kspCommonMainKotlinMetadata` runs before any Kotlin compilation task, so generated code is always up to date.

## Compatibility and legacy projects

Kraft runs entirely at **build time**. The toolchain it needs — Kotlin 2.2.21, KSP 2.3.3, JDK 17 — is only used to *generate* the mappers. It is never required at runtime:

- **Generated mappers are plain Kotlin.** Kraft emits trivial extension functions with no modern-only language features, e.g.:
  ```kotlin
  public fun User.toUserDto(): UserDto = UserDto(name = this.name, age = this.age)
  ```
  This compiles cleanly on older Kotlin versions.
- **The annotations are JVM 1.8 bytecode.** `kraft-annotations` targets `JVM_1_8`, so referencing the annotations does not raise your runtime floor.
- **The processor needs JDK 17 to *run*, not your app.** Gradle [toolchains](https://docs.gradle.org/current/userguide/toolchains.html) can auto-provision JDK 17 for the KSP/compile tasks while your application still targets and runs on an older JVM (8/11). You do not change your app's runtime JVM — you just make a JDK 17 available to the build (the "extra distribution").

Because of this split, Kraft works for legacy applications via one of two integration patterns:

| Pattern | Use when | How |
|---|---|---|
| **Modern build, legacy bytecode** | Your app is already on **Kotlin 2.2+** | Apply KSP as shown above and set `jvmTarget` (and `-Xjdk-release`) to your legacy target, down to JVM 1.8. Build on a JDK 17 toolchain; ship old bytecode. Your runtime JVM is unaffected. |
| **Dedicated mapper module** | Your app is stuck on an **older Kotlin/KSP** version | Put the data classes (or copies) + Kraft annotations in a small Gradle module on the modern toolchain and let KSP generate the mappers there. Your legacy app then consumes either the generated `.kt` source (plain functions — they compile under your older Kotlin) or the published JVM 1.8 mapper artifact. |

> **Why a dedicated module for very old projects?** The KSP processor is compiled against the KSP 2.3.x API, which is not binary-compatible with much older KSP releases. You therefore can't drop KSP 2.3.3 into a project whose own Kotlin version predates it — generate the mappers in an up-to-date module instead and consume the (toolchain-agnostic) output.

## Your First Mapper

Define two data classes and a mapping config object:

```kotlin
import com.blu3berry.kraft.config.MapConfig

data class User(val name: String, val age: Int)
data class UserDto(val name: String, val age: Int)

@MapConfig(source = User::class, target = UserDto::class)
object UserMapper
```

Kraft generates an extension function on the source class:

```kotlin
// Generated by Kraft
fun User.toUserDto(): UserDto = UserDto(
    name = this.name,
    age = this.age,
)
```

You can then call it anywhere in your code:

```kotlin
val user = User(name = "Alice", age = 30)
val dto: UserDto = user.toUserDto()
```

Properties are matched by name. If the source and target have a property with the same name and the same type, it is mapped automatically -- no additional configuration needed.

## Verifying Generated Code

Generated code appears in your build output directory:

- **JVM projects:** `build/generated/ksp/main/kotlin/`
- **Kotlin Multiplatform:** `build/generated/ksp/metadata/commonMain/kotlin/`

After building your project (`./gradlew build` or `./gradlew kspKotlin`), you can inspect the generated files there. Your IDE should also index them automatically, making the generated extension functions available for autocompletion.

## Three Ways to Declare Mappings

Kraft provides three annotation styles depending on where you want to place the mapping declaration:

| Annotation | Placed on | Declares |
|---|---|---|
| `@MapConfig(source = ..., target = ...)` | Standalone object | External config, neither class is annotated |
| `@MapFrom(Source::class)` | Target class | "I am built from Source" |
| `@MapTo(Target::class)` | Source class | "I can be converted to Target" |

All three generate the same extension function: `fun Source.toTarget(): Target`. Choose whichever fits your codebase conventions. The rest of this guide uses `@MapConfig` for most examples, but the concepts apply equally to `@MapFrom` and `@MapTo`.

## Next Steps

- [Basic Mapping](basic-mapping.md) -- property matching, `@MapFrom` vs `@MapTo`, default values
- [Field Renaming](field-renaming.md) -- mapping between differently named properties
- [Nested Mapping](nested-mapping.md) -- mapping objects that contain other mapped objects
