IR representation:

```mermaid
classDiagram
    class MapperDescriptor {
        +String id
        +TypeRef sourceType
        +TypeRef targetType
        +MapperKind kind
        +MapperConfigDescriptor config
        +List~FieldMappingDescriptor~ fieldMappings
        +List~NestedMappingDescriptor~ nestedMappings
        +List~EnumMappingDescriptor~ enumMappings
        +List~ConversionDescriptor~ conversions
    }

    class TypeRef {
        +String packageName
        +String simpleName
        +Boolean isNullable
        +List~TypeRef~ typeArguments
        +String fqName()
    }

    class MapperKind {
        <<enum>>
        SIMPLE
        BI_DIRECTIONAL
        UPDATE
    }

    class MapperConfigDescriptor {
        +StrictMode strictMode
        +NullHandlingStrategy nullHandling
        +NamingStrategy namingStrategy
        +Boolean allowUnmappedTargetProperties
        +Boolean allowUnmappedSourceProperties
    }

    class StrictMode {
        <<enum>>
        STRICT
        LOOSE
        WARN
    }

    class NullHandlingStrategy {
        <<enum>>
        SOURCE_NULL_PASSTHROUGH
        USE_TARGET_DEFAULT
        FAIL
    }

    class NamingStrategy {
        <<enum>>
        IDENTITY
        SNAKE_TO_CAMEL
        CAMEL_TO_SNAKE
        UPPER_SNAKE_TO_CAMEL
    }

    class FieldMappingDescriptor {
        +PropertyPath sourcePath
        +PropertyPath targetPath
        +FieldMappingKind kind
        +ConversionDescriptor~nullable~ conversion
        +EnumMappingDescriptor~nullable~ enumMapping
        +String~nullable~ customExpression
        +Boolean ignore
    }

    class FieldMappingKind {
        <<enum>>
        DIRECT
        CONVERSION
        ENUM
        NESTED
        CUSTOM_EXPRESSION
        IGNORE
    }

    class PropertyPath {
        +List~String~ segments
        +String render()
    }

    class EnumMappingDescriptor {
        +TypeRef sourceEnumType
        +TypeRef targetEnumType
        +List~EnumEntryMapping~ entries
        +Boolean allowDefault
        +String~nullable~ defaultTargetName
    }

    class EnumEntryMapping {
        +String sourceName
        +String targetName
    }

    class ConversionDescriptor {
        +TypeRef sourceType
        +TypeRef targetType
        +ConversionKind kind
        +String converterFqName
    }

    class ConversionKind {
        <<enum>>
        BUILTIN
        CUSTOM
    }

    class NestedMappingDescriptor {
        +TypeRef sourceType
        +TypeRef targetType
        +String mapperFqName
        +Boolean isCollection
    }

    MapperDescriptor "1" --> "1" MapperConfigDescriptor
    MapperDescriptor "1" --> "1" TypeRef : sourceType
    MapperDescriptor "1" --> "1" TypeRef : targetType
    MapperDescriptor "1" --> "many" FieldMappingDescriptor
    MapperDescriptor "1" --> "many" NestedMappingDescriptor
    MapperDescriptor "1" --> "many" EnumMappingDescriptor
    MapperDescriptor "1" --> "many" ConversionDescriptor

    FieldMappingDescriptor "1" --> "1" PropertyPath : sourcePath
    FieldMappingDescriptor "1" --> "1" PropertyPath : targetPath
    FieldMappingDescriptor "0..1" --> "1" ConversionDescriptor
    FieldMappingDescriptor "0..1" --> "1" EnumMappingDescriptor

    EnumMappingDescriptor "1" --> "1" TypeRef : sourceEnumType
    EnumMappingDescriptor "1" --> "1" TypeRef : targetEnumType
    EnumMappingDescriptor "1" --> "many" EnumEntryMapping

    ConversionDescriptor "1" --> "1" TypeRef : sourceType
    ConversionDescriptor "1" --> "1" TypeRef : targetType

```
