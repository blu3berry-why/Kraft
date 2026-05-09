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
- **GlobalConverterScanner** -- scans current-module top-level functions annotated with `@KraftConverter` and builds a `GlobalConverterRegistry` indexed by `(sourceType, targetType)` pair.
- **ClasspathConverterScanner** -- discovers upstream `@KraftConverterDelegate` extension functions from the classpath (published by upstream Kraft modules) and merges them into a classpath-scoped `GlobalConverterRegistry`.
- **AutoEnumMappingDeriver** -- walks every parent mapping pair and synthesizes `EnumMappingDescriptor` instances for property pairs whose source and target are two same-module enum classes with matching entry names, skipping pairs already covered by `@MapEnum` or `@KraftConverter`.

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
  scanner/       -- ClassAnnotationScanner, ConfigObjectScanner, EnumMapScanner, GlobalConverterScanner, ClasspathConverterScanner, AutoEnumMappingDeriver
  sides/         -- SideRegistry, SideConfig, AliasTemplate, PackageGlob
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
- `converters: List<ConverterDescriptor>` -- `@MapUsing` converter functions. Each carries a `resolvedDirection` (`AUTO`, `FORWARD`, or `REVERSE`) for directional filtering when `@MapReverse` is active.
- `aliasEmitMode: AliasEmitMode` -- controls whether and how the side-alias delegate is emitted; defaults to `INHERIT`.

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
- `qualifiedName` -- delegates to KSP's `declaration.qualifiedName?.asString()`, which correctly handles nested types; falls back to `"$packageName.$simpleName"` only for anonymous or local declarations.
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

The code generation phase is pluggable via a ServiceLoader-based SPI. You can replace or supplement the built-in `ExtensionMapperGenerator` with your own `MapperGenerator` that consumes `MapperDescriptor` and emits any output format — interface adapters, JSON schemas, documentation, test stubs, etc.

`AutoMapperProcessor` uses `java.util.ServiceLoader` to discover `MapperGeneratorProvider` implementations on the KSP classpath. If none are found, it falls back to the built-in generator. The same pattern applies to `EnumMapperGeneratorProvider`.

For a full step-by-step guide, data structure diagram, and working example, see [Adding a Custom Code Generator](custom-code-generator.md).

---

## Implicit Nested Dependency Resolution

When a `MapperDescriptor` references a nested type pair that does not have an explicit mapper, `DescriptorBuilder` resolves it via DFS:

1. For each `NestedMapper` strategy, check if a descriptor already exists for the nested pair.
2. If not, synthesize a minimal descriptor using `ClassDescriptorBuilder`.
3. Recurse into the synthesized descriptor's own nested dependencies.
4. Circular dependencies are detected (gray/black DFS coloring) and reported as compile-time errors.
