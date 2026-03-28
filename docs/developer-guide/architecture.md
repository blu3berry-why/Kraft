# Architecture

## Pipeline Overview

```text
@MapFrom / @MapTo / @MapConfig / @MapEnum (annotations)
        |
        v
  +-----------+     +-----------------+     +---------------+
  | Scanners  | --> | DescriptorBuilder| --> | MapperGenerator|
  +-----------+     +-----------------+     +---------------+
        |                   |                       |
  ClassAnnotation     MapperDescriptor       Generated .kt files
  Scanner, Config     (central IR)           (extension functions)
  ObjectScanner,
  EnumMapScanner
```

The processing pipeline has three phases:

### 1. Scanning

Scanners read KSP symbols and produce raw scan results. Each scanner is responsible for a specific annotation family:

- **ClassAnnotationScanner** -- processes `@MapFrom` and `@MapTo` annotations on data classes, producing `ClassMappingScanResult` instances that capture source/target types, field-level overrides, converters, and ignored properties.
- **ConfigObjectScanner** -- processes `@MapConfig` companion objects, producing `ConfigObjectScanResult` instances that capture bulk rename rules, shared converters, and field mappings declared outside the class itself.
- **EnumMapScanner** -- processes `@MapEnum` annotations, producing `EnumMappingDescriptor` instances that capture enum constant correspondence.

### 2. Descriptor Building

`DescriptorBuilder` converts raw scan results into `MapperDescriptor`, the central intermediate representation. This phase:

- Resolves property mappings via the rule chain (see "Property Resolver Rule Chain" below).
- Validates type compatibility between source and target properties.
- Resolves implicit nested dependencies via DFS (see "Implicit Nested Dependency Resolution" below).

Concrete builders include `ClassDescriptorBuilder`, `ConfigDescriptorBuilder`, and `ReverseDescriptorBuilder` (for bidirectional mappings).

### 3. Code Generation

`MapperGenerator` implementations consume `MapperDescriptor` and emit Kotlin source files. The built-in generators produce extension functions (e.g., `fun SourceDto.toTarget(): Target`), but this phase is pluggable via the SPI (see "SPI: Custom Code Generators" below).

---

## Module Structure

```text
kraft-annotations (KMP: JVM, iOS, JS, WasmJs)
  User-facing annotations (@MapFrom, @MapTo, @MapConfig, etc.)
  No external dependencies.

kraft-core (JVM only)
  depends on: kraft-annotations, KSP API
  model/         -- MapperDescriptor, PropertyMappingStrategy, TypeInfo, PropertyInfo, MapperId
  model/scan/    -- Raw scan results
  scanner/       -- ClassAnnotationScanner, ConfigObjectScanner, EnumMapScanner
  descriptor/    -- DescriptorBuilder, ClassDescriptorBuilder, ConfigDescriptorBuilder, ReverseDescriptorBuilder
  descriptor/propertyresolver/ -- PropertyResolver + MappingRule chain
  codegen/       -- MapperGenerator SPI, GenerationConfig, provider interfaces
  util/          -- KraftKspConstants, AnnotationExtensions, LoggerExtensions

kraft-ksp (JVM only)
  depends on: kraft-core, KotlinPoet
  AutoMapperProcessor       -- KSP SymbolProcessor entry point
  AutoMapperProcessorProvider -- ServiceLoader registration
  ExtensionMapperGenerator  -- Built-in generator (extension functions via KotlinPoet)
  EnumMapperGenerator       -- Built-in enum mapper generator
  CtorCallBuilder           -- Builds constructor invocation CodeBlocks
  TypeInfoExt               -- Bridge: TypeInfo -> KotlinPoet ClassName
  CodeGenUtils              -- File naming, banner utilities
```

---

## Key Types

### MapperDescriptor

The central intermediate representation. Contains:

- `id: MapperId` -- unique source/target qualified name pair.
- `sourceType / targetType: TypeInfo` -- source and target class info.
- `propertyMappings: List<PropertyMappingStrategy>` -- resolved strategy for each target property.
- `nestedMappings: List<NestedMappingDescriptor>` -- child mapper dependencies.
- `converters: List<ConverterDescriptor>` -- `@MapUsing` converter functions.

### PropertyMappingStrategy (sealed interface)

Six variants:

| Variant | Description | Example |
|---|---|---|
| `Direct` | Same name, same type | `name = this.name` |
| `Renamed` | Different name, same type | `id = this.userId` |
| `ConverterFunction` | Custom converter | `label = Mapper.convert(this.count)` |
| `NestedMapper` | Nested object | `address = this.address.toAddressDto()` |
| `Constant` | Literal value (reserved for future use) | -- |
| `Ignored` | Property skipped (must have default) | -- |

### TypeInfo

Wraps a KSP type with metadata:

- `declaration: KSClassDeclaration` -- KSP class declaration.
- `ksType: KSType` -- resolved type for equality checks.
- `packageName / simpleName` -- plain strings (no KotlinPoet dependency in kraft-core).
- `qualifiedName` -- computed: `"$packageName.$simpleName"`.
- `isNullable: Boolean`.

### MappingContext

Aggregated context passed to each `MappingRule`:

- Source properties, class-level renames, config-level renames.
- Converters, nested mappings, ignored properties.
- Logger for error reporting.

---

## Property Resolver Rule Chain

`PropertyResolver` applies rules in order. The first match wins:

1. **IgnoreRule** -- returns `Ignored` if the property is in the ignored set.
2. **ConverterRule** -- returns `ConverterFunction` if a `@MapUsing` targets this property.
3. **ClassOverrideRule** -- returns `Renamed` if `@MapField` provides a counterpart name.
4. **ConfigOverrideRule** -- returns `Renamed` if `@FieldMapping` provides a source name.
5. **NestedRule** -- returns `NestedMapper` if the property is a nested object/collection with a different mappable type.
6. **DirectMatchRule** -- returns `Direct` if a same-named, same-typed source property exists.
7. **RequiredFieldErrorRule** -- emits a compile-time error (the property has no default and no rule resolved it).

---

## SPI: Custom Code Generators

### Writing a Custom Generator

1. Add `kraft-core` as a dependency (no KotlinPoet pulled in):

```kotlin
dependencies {
    implementation("com.blu3berry.kraft:kraft-core:<version>")
}
```

2. Implement `MapperGeneratorProvider`:

```kotlin
class MyGeneratorProvider : MapperGeneratorProvider {
    override fun create(environment: GeneratorEnvironment): MapperGenerator {
        return MyGenerator(environment.logger, environment.config)
    }
}
```

3. Implement `MapperGenerator`:

```kotlin
class MyGenerator(
    private val logger: KSPLogger,
    private val config: GenerationConfig
) : MapperGenerator {
    override fun generate(descriptor: MapperDescriptor, codeGenerator: CodeGenerator) {
        val functionName = config.functionNameFor(descriptor)
        // Use descriptor.propertyMappings to emit code
        // Use codeGenerator to write files
    }
}
```

4. Register via ServiceLoader. Create the file `META-INF/services/com.blu3berry.kraft.processor.codegen.MapperGeneratorProvider`:

```text
com.example.MyGeneratorProvider
```

5. Add your module as a KSP dependency alongside kraft-ksp:

```kotlin
ksp("com.blu3berry.kraft:kraft-ksp:<version>")
ksp("com.example:my-kraft-generator:<version>")
```

### How Discovery Works

`AutoMapperProcessor` uses `java.util.ServiceLoader` to find `MapperGeneratorProvider` implementations on the KSP classpath. If none are found, it falls back to the built-in `ExtensionMapperGenerator`. The same pattern applies to `EnumMapperGeneratorProvider`.

---

## Implicit Nested Dependency Resolution

When a `MapperDescriptor` references a nested type pair that does not have an explicit mapper, `DescriptorBuilder` resolves it via DFS:

1. For each `NestedMapper` strategy, check if a descriptor already exists for the nested pair.
2. If not, synthesize a minimal descriptor using `ClassDescriptorBuilder`.
3. Recurse into the synthesized descriptor's own nested dependencies.
4. Circular dependencies are detected (gray/black DFS coloring) and reported as compile-time errors.
