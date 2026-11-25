# Processor Report

The `kraft.report` option makes the processor write a JSON file describing what it discovered in a compilation:

- every converter it can call, and where each one came from;
- the enum pairs it generates mappers for;
- the configured sides and the alias functions it emitted.

Use it when you need to know whether Kraft is seeing a converter, most often one declared in another module, without reading generated code or bisecting the build.

## Turning it on

With the [Gradle plugin](getting-started.md#the-gradle-plugin-recommended):

```kotlin
// build.gradle.kts
kraft {
    report = true
}
```

Or as a raw KSP arg:

```kotlin
ksp {
    arg("kraft.report", "true")
}
```

Any value other than `true` (case-insensitive) leaves the report off. With the report off, no file is written and the generated code is the same as with it on.

!!! tip "Turn it on only when you need it"
    KSP adds its resources output to the compilation's resources, so the report gets packaged into the jar, klib or AAR built with the option on. Drive the option from a Gradle property, so it is on only in a debugging run:

    ```kotlin
    kraft {
        report = providers.gradleProperty("kraft.report").isPresent
    }
    ```

    Then run `./gradlew compileKotlinJvm -Pkraft.report`.

## Where the file is

One report per KSP compilation, at `kraft/report.json` in that compilation's KSP resources output:

| Project | Path |
|---|---|
| Kotlin/JVM | `build/generated/ksp/main/resources/kraft/report.json` |
| KMP, common code | `build/generated/ksp/metadata/commonMain/resources/kraft/report.json` |
| KMP, one target | `build/generated/ksp/<target>/<target>Main/resources/kraft/report.json` (e.g. `jvm/jvmMain`) |
| Android | `build/generated/ksp/<variant>/resources/kraft/report.json` |

The report is written at the end of processing, including when processing fails with a Kraft error. A failing build is when you need it most.

## Example

```json
{
  "schemaVersion": 1,
  "moduleId": ":feature:orders",
  "converters": [
    {
      "source": "app.Money",
      "target": "kotlin.Long",
      "function": "app.toCents",
      "origin": "same-module"
    },
    {
      "source": "app.StatusDto",
      "target": "app.Status",
      "function": "app.generated.toStatus",
      "origin": "synthetic"
    },
    {
      "source": "core.Id",
      "target": "kotlin.String",
      "function": "kraft.generated.registry.kraftDelegate_Id_to_String_1c2d3e4f",
      "origin": "classpath-delegate",
      "module": "_core"
    }
  ],
  "enumPairs": [
    {
      "source": "app.StatusDto",
      "target": "app.Status",
      "function": "app.generated.toStatus",
      "derivation": "declared",
      "declaredBy": "app.StatusMapper"
    }
  ],
  "sides": [
    {
      "slot": "domain",
      "name": "Domain",
      "packagePattern": "app.domain.**",
      "template": "to{side}",
      "emitMode": "BOTH"
    }
  ],
  "sideAliases": [
    {
      "receiver": "app.dto.OrderDto",
      "alias": "toDomain",
      "target": "app.domain.Order",
      "side": "Domain",
      "mapper": "app.OrderMapper"
    }
  ]
}
```

## Reference

| Key | Content |
|---|---|
| `schemaVersion` | Format version, currently `1`. |
| `moduleId` | The `kraft.moduleId` option, or `null` when it is not set. |
| `converters` | Every converter the generated code can call, sorted by `source`, `target`, `function`. |
| `enumPairs` | Every enum mapper Kraft generates, sorted by `source`, `target`. |
| `sides` | The configured [sides](side-aliases.md), sorted by `slot`. |
| `sideAliases` | Every alias function Kraft emitted, sorted by `receiver`, `alias`. |

### converters

| Field | Content |
|---|---|
| `source`, `target` | Qualified type names, with `?` when the converter's receiver or return type is nullable. |
| `function` | The function the generated code calls. |
| `origin` | `same-module`: a [`@KraftConverter`](custom-converters.md#global-converters) declared in this compilation. `classpath-delegate`: a converter published by an upstream Kraft module, called through its generated `@KraftConverterDelegate` in `kraft.generated.registry`. `synthetic`: an enum mapper Kraft generates in this compilation. |
| `module` | `classpath-delegate` only: the producing module's id as recorded on the delegate. That is its `kraft.moduleId` with non-identifier characters replaced by `_` (`:core` becomes `_core`), or a content hash when the upstream module set none. |

Per-mapper `@MapUsing` converters are not listed. They apply to one property, not to the module.

!!! note "Classpath delegates appear once a mapper uses them"
    Kraft finds upstream converters by name, from the type pair a mapper needs, because klib dependencies (KMP) cannot be listed. So a `classpath-delegate` entry appears only when some mapper in this compilation looked up its pair. If you expected an upstream converter and it is missing, either no mapper needs that exact pair, or the upstream module was built with another Kraft version (delegate names are internal wiring), or the upstream module is not on this compilation's classpath.

### enumPairs

| Field | Content |
|---|---|
| `source`, `target` | The two enum types. |
| `function` | The generated mapper. |
| `derivation` | `declared` for a [`@MapEnum`](enum-mapping.md) declaration, `auto` for a pair Kraft derived from a mapper's property types. |
| `declaredBy` | `declared` only: the `@MapEnum` declaration. |

Each enum pair is also a `synthetic` entry in `converters`, which is how other mappers reuse it.

### sides and sideAliases

`sides` repeats the effective `kraft.side.<slot>.*` options, with the defaults filled in. `sideAliases` lists the `fun <receiver>.<alias>(): <target>` functions Kraft generated. `mapper` is the declaration that produced each alias. A side with `emitMode = FULL_NAME_ONLY`, or a mapper with `aliasEmitMode = FULL_NAME_ONLY`, produces no aliases.

## Stability

The file is deterministic: the same build writes the same bytes, so you can commit it or diff two builds. New information is added as new top-level keys, and the next one planned is a per-mapper coverage section. Tools that read the report should ignore keys they don't know. `schemaVersion` changes only when an existing key changes meaning or shape.

## Related

- The [KRAFT-E005](errors.md#kraft-e005) type-mismatch error lists the converters that touch the failing property's types, so you often don't need the full report.
- [KSP Options](ksp-options.md#kraftreport) lists every option.
