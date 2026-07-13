# Adding a Custom Code Generator

Kraft's code generation phase is pluggable via a **ServiceLoader-based SPI**. Instead of (or alongside) the built-in extension function generator, you can provide your own `MapperGenerator` that consumes the same intermediate representation (`MapperDescriptor`) and emits any output you need — interface adapters, JSON schemas, documentation, test stubs, or anything else.

---

## The SPI is experimental — opt in first

Every SPI type on this page (`MapperGenerator`, `MapperGeneratorProvider`, `GeneratorEnvironment`, `GenerationConfig`, `EnumMapperGeneratorSpi`, `EnumMapperGeneratorProvider`) is marked `@ExperimentalKraftApi`. The contract: **these types are excluded from Kraft's semantic-versioning promise and may change in any release without a major version bump.** Annotation users and generated code are never affected — only code that references SPI types in source.

Referencing them without opting in is a compile error. Opt in either per file:

```kotlin
@OptIn(ExperimentalKraftApi::class)
class JsonSchemaGeneratorProvider : MapperGeneratorProvider { /* ... */ }
```

or module-wide in your generator module's build script:

```kotlin
kotlin {
    compilerOptions {
        optIn.add("com.blu3berry.kraft.ExperimentalKraftApi")
    }
}
```

The samples below assume the module-wide flag.

---

## Data Structure Diagrams

### SPI Wiring

How your generator is discovered and invoked by the processor:

```mermaid
classDiagram
    direction LR

    class MapperGeneratorProvider {
        <<fun interface>>
        +create(env) MapperGenerator
    }
    class GeneratorEnvironment {
        +logger: KSPLogger
        +options: Map~String, String~
        +config: GenerationConfig
        +sideRegistry: SideRegistry
    }
    class GenerationConfig {
        +functionNameTemplate: String
        +functionNameFor(descriptor) String
    }
    class MapperGenerator {
        <<fun interface>>
        +generate(descriptor, codeGenerator)
    }

    class EnumMapperGeneratorProvider {
        <<fun interface>>
        +create(env) EnumMapperGeneratorSpi
    }
    class EnumMapperGeneratorSpi {
        <<interface>>
        +generate(descriptors, codeGenerator)
        +generatedPackage(descriptor) String
        +generatedFunctionName(descriptor) String
    }

    MapperGeneratorProvider --> GeneratorEnvironment : receives
    MapperGeneratorProvider --> MapperGenerator : creates
    GeneratorEnvironment --> GenerationConfig
    MapperGenerator --> MapperDescriptor : consumes

    EnumMapperGeneratorProvider --> GeneratorEnvironment : receives
    EnumMapperGeneratorProvider --> EnumMapperGeneratorSpi : creates
    EnumMapperGeneratorSpi --> EnumMappingDescriptor : consumes
```

### MapperDescriptor — The Central IR

The `MapperDescriptor` is what your `generate()` method receives. It contains everything needed to emit mapper code for one source-target pair:

```mermaid
classDiagram
    direction TB

    class MapperDescriptor {
        +id: MapperId
        +sourceType: TypeInfo
        +targetType: TypeInfo
        +source: MappingSource
        +propertyMappings: List
        +nestedMappings: List
        +enumMappings: List
        +converters: List
        +aliasEmitMode: AliasEmitMode
    }

    class MapperId {
        +sourceQualifiedName: String
        +targetQualifiedName: String
    }
    class TypeInfo {
        +packageName: String
        +simpleName: String
        +isNullable: Boolean
        +qualifiedName: String
    }
    class PropertyInfo {
        +name: String
        +type: TypeInfo
        +hasDefault: Boolean
    }

    class MappingSource {
        <<sealed>>
    }
    class ClassAnnotation {
        +annotatedClass: KSClassDeclaration
        +direction: MappingDirection
    }
    class ConfigObject {
        +configObject: KSClassDeclaration
    }

    class NestedMappingDescriptor {
        +nestedMapperId: MapperId
        +sourceType: TypeInfo
        +targetType: TypeInfo
        +collectionKind: CollectionKind?
    }
    class EnumMappingDescriptor {
        +sourceType: TypeInfo
        +targetType: TypeInfo
        +entries: List~EnumEntryMapping~
    }
    class ConverterDescriptor {
        +targetPropertyName: String
        +sourcePropertyName: String?
        +functionName: String
        +isExtension: Boolean
        +resolvedDirection: ConverterDirection
    }

    MapperDescriptor --> MapperId : id
    MapperDescriptor --> TypeInfo : sourceType / targetType
    MapperDescriptor --> MappingSource : source
    MapperDescriptor --> NestedMappingDescriptor : nestedMappings *
    MapperDescriptor --> EnumMappingDescriptor : enumMappings *
    MapperDescriptor --> ConverterDescriptor : converters *

    MappingSource <|-- ClassAnnotation
    MappingSource <|-- ConfigObject

    NestedMappingDescriptor --> MapperId
    NestedMappingDescriptor --> TypeInfo
    EnumMappingDescriptor --> TypeInfo
```

### PropertyMappingStrategy Variants

Each target property is resolved to exactly one strategy. Your generator iterates `descriptor.propertyMappings` and handles each variant:

```mermaid
classDiagram
    direction TB

    class PropertyMappingStrategy {
        <<sealed>>
        +targetProperty: PropertyInfo
    }

    class Direct {
        +sourceProperty: PropertyInfo
    }
    class Renamed {
        +sourceProperty: PropertyInfo
    }
    class ConverterFunction {
        +source: ConverterSource
        +converter: ConverterDescriptor
    }
    class NestedMapper {
        +sourceProperty: PropertyInfo
        +nestedMappingDescriptor: NestedMappingDescriptor
    }
    class Constant {
        +expression: String
    }
    class Ignored

    class ConverterSource {
        <<sealed>>
    }
    class Property {
        +info: PropertyInfo
    }
    class WholeObject {
        +sourceType: TypeInfo
    }

    PropertyMappingStrategy <|-- Direct
    PropertyMappingStrategy <|-- Renamed
    PropertyMappingStrategy <|-- ConverterFunction
    PropertyMappingStrategy <|-- NestedMapper
    PropertyMappingStrategy <|-- Constant
    PropertyMappingStrategy <|-- Ignored

    ConverterFunction --> ConverterSource
    ConverterSource <|-- Property
    ConverterSource <|-- WholeObject
```

`Ignored` really has no fields of its own — the empty box is accurate: it carries only the inherited `targetProperty`, and the generator simply emits no constructor argument for it.

---

## Step-by-Step Guide

### 1. Create a Gradle Module

Create a new JVM module (or standalone project) for your generator:

```kotlin
// my-kraft-generator/build.gradle.kts
plugins {
    kotlin("jvm")
}

dependencies {
    implementation("com.blu3berry.kraft:kraft-core:<version>")
    // kraft-core brings in the KSP API transitively.
    // Add KotlinPoet only if your generator needs it — it is NOT required.
}
```

!!! note
    `kraft-core` does **not** depend on KotlinPoet. You are free to use any code-emission strategy — raw string templates, KotlinPoet, or your own writer.

### 2. Implement `MapperGeneratorProvider`

This is the ServiceLoader entry point. It receives a `GeneratorEnvironment` and returns your `MapperGenerator`:

```kotlin
class JsonSchemaGeneratorProvider : MapperGeneratorProvider {
    override fun create(environment: GeneratorEnvironment): MapperGenerator {
        return JsonSchemaGenerator(
            logger = environment.logger,
            config = environment.config,
            options = environment.options
        )
    }
}
```

### 3. Implement `MapperGenerator`

Your generator receives one `MapperDescriptor` per source-target pair. Use its fields to emit whatever output you need:

```kotlin
class JsonSchemaGenerator(
    private val logger: KSPLogger,
    private val config: GenerationConfig,
    private val options: Map<String, String>
) : MapperGenerator {

    override fun generate(descriptor: MapperDescriptor, codeGenerator: CodeGenerator) {
        val targetName = descriptor.targetType.simpleName
        val fileName = "${targetName}_schema"

        // Open an output file via the KSP CodeGenerator
        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = false),
            packageName = "",
            fileName = fileName,
            extensionName = "json"
        )

        val schema = buildString {
            appendLine("{")
            appendLine("""  "type": "object",""")
            appendLine("""  "title": "$targetName",""")
            appendLine("""  "properties": {""")

            descriptor.propertyMappings.forEachIndexed { index, strategy ->
                val propName = strategy.targetProperty.name
                val propType = strategy.targetProperty.type.simpleName
                val comma = if (index < descriptor.propertyMappings.lastIndex) "," else ""

                // Map Kotlin types to JSON Schema types
                val jsonType = when (propType) {
                    "String" -> "string"
                    "Int", "Long" -> "integer"
                    "Double", "Float" -> "number"
                    "Boolean" -> "boolean"
                    else -> "object"
                }

                appendLine("""    "$propName": { "type": "$jsonType" }$comma""")
            }

            appendLine("  }")
            appendLine("}")
        }

        file.write(schema.toByteArray())
        file.close()

        logger.info("Generated JSON schema: $fileName.json")
    }
}
```

### 4. Handle PropertyMappingStrategy Variants

When iterating `descriptor.propertyMappings`, handle each variant according to your output format:

```kotlin
fun processStrategies(descriptor: MapperDescriptor) {
    for (strategy in descriptor.propertyMappings) {
        when (strategy) {
            is PropertyMappingStrategy.Direct -> {
                // Same name, same type: source.x -> target.x
                val name = strategy.targetProperty.name
                val type = strategy.targetProperty.type
            }
            is PropertyMappingStrategy.Renamed -> {
                // Different name: source.oldName -> target.newName
                val sourceName = strategy.sourceProperty.name
                val targetName = strategy.targetProperty.name
            }
            is PropertyMappingStrategy.ConverterFunction -> {
                // Custom converter applied
                val converter = strategy.converter
                val targetName = strategy.targetProperty.name
            }
            is PropertyMappingStrategy.NestedMapper -> {
                // Delegates to a child mapper
                val nested = strategy.nestedMappingDescriptor
                val isCollection = nested.isCollection
            }
            is PropertyMappingStrategy.Constant -> {
                // Literal expression. No built-in MappingRule produces this yet,
                // but handle it for exhaustiveness — the built-in generator does.
                val expr = strategy.expression
            }
            is PropertyMappingStrategy.Ignored -> {
                // Property skipped — has a default value
            }
        }
    }
}
```

### 5. (Optional) Implement Enum Generator

If you also want to customize enum mapping output, implement the enum SPI:

```kotlin
class MyEnumGeneratorProvider : EnumMapperGeneratorProvider {
    override fun create(environment: GeneratorEnvironment): EnumMapperGeneratorSpi {
        return MyEnumGenerator(environment.logger, environment.config)
    }
}

class MyEnumGenerator(
    private val logger: KSPLogger,
    private val config: GenerationConfig
) : EnumMapperGeneratorSpi {

    override fun generate(
        descriptors: List<EnumMappingDescriptor>,
        codeGenerator: CodeGenerator
    ) {
        for (descriptor in descriptors) {
            // descriptor.sourceType / descriptor.targetType — the enum types
            // descriptor.entries — List<EnumEntryMapping> with source/target constant names
        }
    }

    // Must match what generate() actually emits: the converter registry and the
    // @KraftConverterDelegate trampolines use these to compute call coordinates.
    override fun generatedPackage(descriptor: EnumMappingDescriptor): String =
        "${descriptor.sourceType.packageName}.generated"

    override fun generatedFunctionName(descriptor: EnumMappingDescriptor): String =
        config.functionNameFor(descriptor.sourceType.simpleName, descriptor.targetType.simpleName)
}
```

### 6. Register via ServiceLoader

Create the service file so `AutoMapperProcessor` can discover your provider at compile time.

For class mappers, create:

```text title="src/main/resources/META-INF/services/com.blu3berry.kraft.processor.codegen.MapperGeneratorProvider"
com.example.JsonSchemaGeneratorProvider
```

For enum mappers (optional), create:

```text title="src/main/resources/META-INF/services/com.blu3berry.kraft.processor.codegen.EnumMapperGeneratorProvider"
com.example.MyEnumGeneratorProvider
```

### 7. Wire into the Consumer Build

Add your generator module as a KSP dependency alongside `kraft-ksp`:

```kotlin
// app/build.gradle.kts
dependencies {
    ksp("com.blu3berry.kraft:kraft-ksp:<version>")
    ksp("com.example:my-kraft-generator:<version>")
}
```

Both your generator and the built-in one are on the KSP classpath, but only **one** `MapperGeneratorProvider` is used per compilation (see discovery rules below).

---

## GeneratorEnvironment Reference

Your provider's `create()` method receives a `GeneratorEnvironment` with:

| Field | Type | Description |
|-------|------|-------------|
| `logger` | `KSPLogger` | KSP logger — use `info()`, `warn()`, `error()` for compile-time messages. |
| `options` | `Map<String, String>` | All KSP processor options from `build.gradle.kts`. Read custom options here. |
| `config` | `GenerationConfig` | Holds the `functionNameTemplate` (default `"to${target}"`). Use `config.functionNameFor(descriptor)` to get the resolved function name. |
| `sideRegistry` | `SideRegistry` | Registered side-alias configs from `kraft.side.*` KSP args. Call `sideRegistry.resolveSide(targetFqn)` to find the matching side, or check `sideRegistry.sides.isEmpty()` to short-circuit when no sides are configured. See [Side Aliases](../user-guide/side-aliases.md). |

---

## Discovery and Fallback Behavior

`AutoMapperProcessor` uses `java.util.ServiceLoader` to find providers on the KSP classpath:

| Providers found | Behavior |
|-----------------|----------|
| **0** | Falls back to the built-in `ExtensionMapperGenerator`. |
| **1** | Uses that single provider. |
| **2+** | Uses the **first** provider found and logs a warning. |

The same logic applies independently to `EnumMapperGeneratorProvider`.

!!! warning
    If you provide a custom `MapperGeneratorProvider`, it **replaces** the built-in extension function generator entirely. If you still want extension functions alongside your custom output, call through to `ExtensionMapperGenerator` from within your generator, or structure your project so they are separate KSP executions.

---

## Tips

- **Function naming**: Use `config.functionNameFor(descriptor)` to respect the user's `kraft.functionNameFormat` KSP option. This keeps your generator consistent with the naming convention users configure.
- **Incremental processing**: Pass originating `KSFile` references to `Dependencies(...)` when creating output files. This allows KSP to skip regeneration when source files haven't changed.
- **Custom KSP options**: Read your own options from `environment.options`. For example, configure them in `build.gradle.kts`:

    ```kotlin
    ksp {
        arg("myGenerator.outputFormat", "json")
    }
    ```

    Then read in your generator:

    ```kotlin
    val format = options["myGenerator.outputFormat"] ?: "json"
    ```

- **Accessing nested info**: `descriptor.nestedDependencies` gives you the set of `MapperId`s this mapper depends on — useful if your output format needs to declare imports or references to child mappers.
- **Collection handling**: Check `NestedMappingDescriptor.collectionKind` to distinguish `List` vs `Set` wrappers on nested properties.
