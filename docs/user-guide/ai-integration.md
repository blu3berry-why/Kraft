# Using Kraft with AI Coding Agents

AI coding assistants (Claude, Copilot, Cursor, etc.) tend to write manual mapping code by default. Giving them context about Kraft lets them generate proper annotation-based mappers instead.

## Quick Setup

Copy the skill block below into your project's AI configuration file:

- **Claude Code**: `CLAUDE.md` in your project root
- **Cursor**: `.cursorrules`
- **Other agents**: `AGENTS.md` or equivalent

## Kraft Agent Skill

Below is a ready-to-copy block. It gives the AI agent everything it needs to use Kraft correctly.

````markdown
## Kraft Mapper Library

This project uses Kraft, a KSP-based compile-time mapper generator for Kotlin. When creating mappers between data classes, use Kraft annotations instead of writing manual mapping code.

### Quick Reference

| Task | Annotation | Placement |
|------|-----------|-----------|
| Map source to target | `@MapFrom(Source::class)` | On target class |
| Map target from source | `@MapTo(Target::class)` | On source class |
| Rename property | `@MapField(counterPartName = "srcProp")` | On target property |
| Nested object mapping | `@MapNested` or `@MapNested(sourceName = "other")` | On target property |
| Ignore property | `@MapIgnore` | On target property (must have default) |
| Reverse mapping | `@MapReverse` | On class with @MapFrom/@MapTo or on @MapConfig object |
| Enum mapping | `@MapEnum(source = A::class, target = B::class)` | On config object |
| Custom converter | `@MapUsing(source = "prop", target = "prop")` | On function in @MapConfig object |
| Whole-source converter | `@MapUsing(target = "prop")` | On function in @MapConfig object (omit source) |
| Config-based mapping | `@MapConfig(source = A::class, target = B::class)` | On object |

### Decision Rules

- **Simple same-name properties**: No annotation needed -- Kraft maps them automatically.
- **Different property names**: Use `@MapField` on the class or `FieldMapping` in `@MapConfig`.
- **Nested objects**: Use `@MapNested` or let auto-detection handle same-named properties with different types. Use `NestedMapping` in `@MapConfig` when classes cannot be annotated.
- **Complex transformations**: Use `@MapUsing` inside a `@MapConfig` object. Omit the `source` parameter to receive the whole source object.
- **Cannot modify the classes**: Use `@MapConfig` on a standalone object.
- **Need both directions**: Add `@MapReverse` to generate the inverse mapper.
- **Enum-to-enum**: Use `@MapEnum` with auto-matching or explicit `fieldMappings`.

### Anti-Patterns

- Do NOT write manual mapping extension functions -- use Kraft annotations.
- Do NOT use `@MapFrom` and `@MapTo` on the same class (compile-time error).
- Do NOT forget default values on `@MapIgnore` properties (compile-time error).
- Do NOT mix `@MapNested` with `@MapField` on the same property (`@MapNested` wins with a warning).

### Generated Code Location

Generated extension functions appear in:
- KMP: `build/generated/ksp/metadata/commonMain/kotlin/`
- JVM: `build/generated/ksp/main/kotlin/`

### Imports

Annotations are in two packages:
- `hu.nova.blu3berry.kraft.mapping.*` -- @MapFrom, @MapTo, @MapField, @MapIgnore, @MapNested
- `hu.nova.blu3berry.kraft.config.*` -- @MapConfig, @MapEnum, @MapUsing, @MapReverse, FieldMapping, NestedMapping, MapIgnoreField, IgnoreSide
````

## How It Works

When you add this to your project's AI config:

1. The agent knows Kraft exists and what it does.
2. When asked to "create a mapper" or "map X to Y", it uses annotations instead of manual code.
3. It knows the decision rules for choosing the right annotation.
4. It avoids common mistakes (missing defaults, duplicate annotations, manual extension functions).

## Example Interaction

Without the skill, asking an AI to "create a mapper from User to UserDto" typically produces:

```kotlin
// Manual code the AI would write without Kraft context
fun User.toUserDto(): UserDto = UserDto(
    id = this.userId,
    name = this.fullName
)
```

With the skill, the same request produces:

```kotlin
// Annotation-based mapper the AI writes with Kraft context
@MapConfig(
    source = User::class,
    target = UserDto::class,
    fieldMappings = [
        FieldMapping(source = "userId", target = "id"),
        FieldMapping(source = "fullName", target = "name")
    ]
)
object UserMapper
```

The generated extension function is produced at compile time by KSP, is type-safe, and stays in sync with your data classes.

## Customizing the Skill

You can extend the skill block with project-specific conventions:

- **Preferred mapping style**: State whether your project prefers `@MapFrom`/`@MapTo` on classes or `@MapConfig` on standalone objects.
- **Naming conventions**: Specify naming patterns for config objects (e.g. `XToYMapper`, `XMappingConfig`).
- **KSP options**: Document which `kraft.functionNameFormat` your project uses so the AI knows the generated function names.
- **Reverse mapping policy**: State whether `@MapReverse` should be used by default or only when explicitly requested.

Example addition:

````markdown
### Project Conventions

- Prefer `@MapConfig` on standalone objects for all mappings.
- Name config objects as `{Source}To{Target}Mapper` (e.g. `UserToUserDtoMapper`).
- This project uses `kraft.functionNameFormat = "map${source}To${target}"`.
- Always add `@MapReverse` when both directions are needed.
````
