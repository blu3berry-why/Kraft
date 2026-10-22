# Experimental APIs and Opt-in

When a mapping touches an experimental API, such as `kotlin.uuid.Uuid`, Kraft copies the opt-in onto the generated code, so the code that calls your mappers does not need one. You write the opt-in once, where the experimental type or converter is declared.

## What propagates

Kraft copies a deduplicated `@OptIn(...)` onto the generated mapper function. Two kinds of annotation count:

- `@OptIn(M::class, ...)`: each listed marker class is copied.
- Any annotation whose type is itself annotated with `@RequiresOptIn`: that annotation type is copied as a marker.

```kotlin
@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
annotation class ExperimentalSource

@ExperimentalSource
data class Src(val count: Int)

data class Dst(val count: Int)

@MapConfig(source = Src::class, target = Dst::class)
object SrcMapper
```

Generated code:

```kotlin
@OptIn(ExperimentalSource::class)
fun Src.toDst(): Dst = Dst(
    count = this.count
)
```

A mapper with no experimental dependencies gets no `@OptIn` at all.

## Where Kraft looks

Kraft reads markers from these sites:

| Site | Example |
|------|---------|
| The source class | `@OptIn(ExperimentalFoo::class) data class Src(...)` |
| The target class | `@ExperimentalFoo data class Dst(...)` |
| The `@MapConfig` object (or the annotated class, for class-level `@MapFrom` / `@MapTo`) | `@OptIn(ExperimentalFoo::class) object SrcMapper` |
| Every `@MapUsing` function on the config object | `@MapUsing(...) @OptIn(ExperimentalFoo::class) fun convert(...)` |
| Every `@KraftConverter` the mapper invokes, including converters applied to collection elements | `@KraftConverter @OptIn(ExperimentalFoo::class) fun Int.toLabel(): String` |

Each site is read in two places: on the declaration itself, and on the **file** that declares it. A file-level `@file:OptIn(...)` counts the same as a declaration-level one:

```kotlin
@file:OptIn(models.ExperimentalModel::class)
package models

data class Src(val count: Int)
```

The file-level form matters for generated DTOs. Code generators differ on which form they emit, so the same models can arrive with a declaration-level marker from one generator version and a file-level one from another. Kraft treats both alike.

The `@KraftConverter` delegate file that Kraft generates for [cross-module discovery](custom-converters.md#cross-module-discovery) carries the same opt-ins as the original converter. They come from the converter function and its file, from the receiver and return types, and from any type alias in those positions, whether the marker sits on the alias or on the class behind it.

## The contract

On Kraft 0.13.0 and later, generated code owns its opt-ins. You do not need a module-wide `-opt-in` compiler argument to compile Kraft's output, and you do not need an `@OptIn` at the call site of a generated mapper for markers Kraft collected.

Before 0.13.0, only declaration-level markers were copied and a file-level `@file:OptIn` was silently dropped. See [Upgrading](upgrading.md) if you are coming from an earlier version.

## If a marker still leaks

On 0.13.0+, an opt-in error (`This declaration needs opt-in`) inside generated code is a bug in Kraft. Please report it, and include:

- The Kraft version, plus the Kotlin and KSP versions.
- The full compiler error, with the marker it names.
- The declaration that carries the marker, and whether the marker is on the declaration or in a `@file:OptIn`.
- Whether that declaration is in the same module as the mapper or in an upstream module.
- The generated mapper (or `Converters_<moduleId>.kt` delegate file) from `build/generated/ksp`.

If you are on a release before 0.13.0 and the marker is a file-level `@file:OptIn`, upgrading is the fix.

## Compiler-level `-opt-in` is a workaround

You can silence an opt-in error by adding the marker module-wide:

```kotlin
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}
```

That compiles, but it is not the intended setup. It opts in every file in the module, including your own code, so you stop getting the warning where you really do use the experimental API. It also hides the bug: the build passes while the generated code is missing an opt-in it should have carried. If you need it on 0.13.0+ only to compile Kraft's output, remove it and report the case instead. If something else in the module needs the flag, it can stay.

## Coverage notes

`OptInPropagationTest` covers each site above: declaration-level markers on the source class, the `@KraftConverter`, and the `@MapUsing` function; `@file:OptIn` on the source class file and on the converter file; deduplication across sites; type aliases on the delegate registry; and the case with no markers. There is no dedicated test where an upstream module's converter is consumed from a separate compilation and its opt-in is checked in the consumer's generated mapper. The delegate file carrying the opt-in is tested (`CrossModuleConverterTest`), and the mapper side reads the invoked converter's declaration, but that end-to-end klib-classpath case is not pinned by its own test.
