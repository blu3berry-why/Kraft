# Enum Mapper

The Enum Mapper is a feature of the Kraft KSP module that generates extension functions to map between enum types. This allows for seamless conversion between different enum representations of the same concept, such as between API and UI representations.

## Architecture

The Enum Mapper is organized into the following components:

1. **Scanner** - Scans the codebase for classes annotated with `@EnumMap` and builds `EnumMappingDescriptor` objects.
2. **Validator** - Validates the enum mapping configuration to ensure all source enum entries have a valid mapping to the target enum.
3. **Generator** - Generates the extension functions for mapping between enum types.
4. **Processor** - Coordinates the scanning, validation, and generation process.

### Package Structure

- `hu.nova.blu3berry.kraft.processor.enummapping` - Root package for enum mapping functionality
  - `scanner` - Scanner component
  - `validator` - Validator component
  - `generator` - Generator component
  - `model` - Model classes specific to enum mapping (uses existing model classes from `hu.nova.blu3berry.kraft.model`)

## Usage Examples

### Basic Enum Mapping

```kotlin
// Define two enum classes
enum class ApiStatus {
    ACTIVE,
    INACTIVE,
    DELETED
}

enum class UiStatus {
    ACTIVE,
    INACTIVE,
    REMOVED
}

// Create a mapping between them
@EnumMap(
    from = ApiStatus::class,
    to = UiStatus::class,
    fieldMapping = [
        StringPair("DELETED", "REMOVED")
    ]
)
object StatusMapping

// Use the generated extension function
val apiStatus = ApiStatus.DELETED
val uiStatus = apiStatus.toUiStatus() // Returns UiStatus.REMOVED
```

### Custom Field Mapping

```kotlin
// Define two enum classes with different naming conventions
enum class DatabaseStatus {
    ACTIVE,
    INACTIVE,
    DELETED
}

enum class DisplayStatus {
    Active,
    Inactive,
    Removed
}

// Create a mapping between them with custom field mappings
@EnumMap(
    from = DatabaseStatus::class,
    to = DisplayStatus::class,
    fieldMapping = [
        StringPair("ACTIVE", "Active"),
        StringPair("INACTIVE", "Inactive"),
        StringPair("DELETED", "Removed")
    ]
)
object StatusMapping

// Use the generated extension function
val dbStatus = DatabaseStatus.DELETED
val displayStatus = dbStatus.toDisplayStatus() // Returns DisplayStatus.Removed
```

### Integration with Object Mapping

```kotlin
// Define data classes with enum properties
data class ApiUser(
    val id: String,
    val status: ApiStatus
)

data class UiUser(
    val id: String,
    val status: UiStatus
)

// Create a mapping between the data classes
@MapFrom(ApiUser::class)
@MapTo(UiUser::class)
object UserMapping

// The generated mapper will automatically use the enum mapper
val apiUser = ApiUser("123", ApiStatus.DELETED)
val uiUser = apiUser.toUiUser() // UiUser("123", UiStatus.REMOVED)
```

## Design Decisions

1. **Automatic Mapping for Same-Named Entries** - If source and target enums contain entries with identical names, they are mapped automatically without requiring explicit `StringPair` mappings.

2. **Custom Field Mapping** - Custom mappings can be defined using `StringPair` annotations to override the default auto-mapping for specific entries.

3. **Validation** - The system validates that all source enum entries have a mapping to the target enum, either via auto-mapping or custom mapping.

4. **File Generation** - Each enum mapping is generated in its own file with a predictable naming convention.

5. **Integration with Object Mapping** - The enum mapper integrates with the larger object mapping system, allowing enum properties to be automatically mapped when mapping between objects.