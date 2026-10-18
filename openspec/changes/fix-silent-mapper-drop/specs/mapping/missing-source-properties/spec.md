# Spec Delta

## Purpose

Defines what Kraft generates or reports for a target property that has no source property, rename, ignore entry or converter, and guarantees that a mapper is never skipped without a diagnostic.

Unless a scenario says otherwise, every scenario uses `data class PersonSource(val name: String)`, a `PersonDto` target with the stated extra property, and `@MapConfig(source = PersonSource::class, target = PersonDto::class) object PersonMapper`. "Unmapped" means no source property of that name, no `@MapField` / `FieldMapping` override, no `@MapIgnore` / `@MapIgnoreField` entry and no `@MapUsing` for it.

## ADDED Requirements

### Requirement: Unmapped defaulted targets use their default
When a target property is unmapped and its constructor parameter has a default value, Kraft SHALL generate the mapper and SHALL leave that property out of the constructor call, so Kotlin applies the default. Kraft SHALL NOT emit a diagnostic for it. This holds whether the property is nullable or not.

#### Scenario: Non-null property with a default
- **WHEN** `data class PersonDto(val name: String, val score: Int = 0)`
- **THEN** compilation succeeds, the generated `PersonSource.toPersonDto()` is `PersonDto(name = this.name)` with no `score` argument, and no `KRAFT-` diagnostic is emitted

#### Scenario: Nullable property with a null default
- **WHEN** `data class PersonDto(val name: String, val nickname: String? = null)`
- **THEN** compilation succeeds, the generated mapper has no `nickname` argument, and no `KRAFT-` diagnostic is emitted

#### Scenario: Calling the generated mapper
- **WHEN** the non-null-default scenario is compiled and `PersonSource("Ada").toPersonDto()` is evaluated
- **THEN** the result equals `PersonDto(name = "Ada", score = 0)`

### Requirement: Unmapped nullable targets without a default receive null and a warning
When a target property is unmapped, nullable, and has no default, Kraft SHALL generate the mapper passing `<name> = null`, and SHALL emit warning `KRAFT-W009`, unless it is suppressed.

#### Scenario: Nullable property without a default
- **WHEN** `data class PersonDto(val name: String, val nickname: String?)`
- **THEN** compilation succeeds and the generated mapper is `PersonDto(name = this.name, nickname = null)`
- **AND** a warning is emitted whose first line is `[KRAFT-W009] Nullable property 'nickname' in target type 'PersonDto' has no mapping source, so Kraft passes null.`
- **AND** the warning body contains each of these fix lines: `Give 'nickname' a default (= null) to make null the intended value.`, `Or map it explicitly with @MapUsing(target = "nickname").`, `Or add @Suppress("KRAFT-W009") to the property or to the mapping declaration.`

#### Scenario: Warning fails a warnings-as-errors build
- **WHEN** the previous scenario is compiled with all warnings treated as errors
- **THEN** compilation fails on `KRAFT-W009`

### Requirement: KRAFT-W009 is suppressible
Kraft SHALL NOT emit `KRAFT-W009` for a property when `@Suppress("KRAFT-W009")` is present on that target property or constructor parameter, or on the mapping declaration: the `@MapConfig` object, or the class annotated with `@MapFrom` / `@MapTo`. Suppression SHALL NOT change the generated code. Another code in `@Suppress` SHALL NOT silence it.

#### Scenario: Suppressed on the mapping declaration
- **WHEN** the nullable-without-default scenario's `PersonMapper` object is annotated `@Suppress("KRAFT-W009")`
- **THEN** compilation succeeds with no `KRAFT-W009` and the generated mapper still passes `nickname = null`

#### Scenario: Suppressed on the target property
- **WHEN** `@com.blu3berry.kraft.mapping.MapFrom(PersonSource::class) data class PersonDto(val name: String, @Suppress("KRAFT-W009") val nickname: String?)`
- **THEN** compilation succeeds with no `KRAFT-W009` and the generated mapper passes `nickname = null`

#### Scenario: Wrong code does not suppress
- **WHEN** `PersonMapper` is annotated `@Suppress("KRAFT-W001")` instead
- **THEN** `KRAFT-W009` is still emitted

### Requirement: Unmapped required targets still fail
When a target property is unmapped, non-nullable, and has no default, Kraft SHALL fail compilation with `KRAFT-E004`, as before this change.

#### Scenario: Required property without a source
- **WHEN** `data class PersonDto(val name: String, val age: Int)`
- **THEN** compilation fails with an error whose first line starts with `[KRAFT-E004] Required property 'age' in target type`
- **AND** its fix list contains `Or make the property nullable (Kraft then passes null and warns KRAFT-W009)`

### Requirement: The fallback never masks a reported error
Kraft SHALL NOT apply the default or null fallback to a property for which a diagnostic error was already reported during its resolution, such as a type mismatch or an invalid override. That error SHALL remain the only outcome for the property.

#### Scenario: Same-named source with the wrong type
- **WHEN** `data class PersonSource(val name: String, val nickname: Int)` and `data class PersonDto(val name: String, val nickname: String?)`
- **THEN** compilation fails with the type-mismatch error for `nickname`, and no `KRAFT-W009` is emitted

#### Scenario: Invalid @MapField override on a defaulted property
- **WHEN** `@com.blu3berry.kraft.mapping.MapFrom(PersonSource::class) data class PersonDto(val name: String, @com.blu3berry.kraft.mapping.MapField("missing") val score: Int = 0)`
- **THEN** compilation fails with the invalid-override error for `score`

### Requirement: The fallback applies to every mapper kind
The rules above SHALL apply equally to mappers from `@MapConfig`, `@MapFrom`, `@MapTo`, and to reverse mappers from `@MapReverse`.

#### Scenario: @MapFrom target with a default
- **WHEN** `@com.blu3berry.kraft.mapping.MapFrom(PersonSource::class) data class PersonDto(val name: String, val score: Int = 0)`
- **THEN** compilation succeeds and `PersonSource.toPersonDto()` is generated without a `score` argument

#### Scenario: Reverse mapper with a defaulted source-side property
- **WHEN** `data class PersonSource(val name: String, val createdAt: Long = 0L)`, `data class PersonDto(val name: String)`, and `@MapConfig(source = PersonSource::class, target = PersonDto::class) @com.blu3berry.kraft.config.MapReverse object PersonMapper`
- **THEN** compilation succeeds, `PersonSource.toPersonDto()` and `PersonDto.toPersonSource()` are both generated, and the reverse call has no `createdAt` argument

### Requirement: A mapper is never skipped without a diagnostic
When Kraft does not generate a mapper because one of its target properties could not be resolved, the compilation SHALL contain at least one `KRAFT-E` error naming that property. If no other error was reported for it, Kraft SHALL report `KRAFT-E065`.

#### Scenario: Resolution yields nothing and nothing was reported
- **WHEN** a property resolver is configured with a rule chain in which no rule resolves or reports target property `score` of `PersonDto`
- **THEN** compilation fails with an error whose first line is `[KRAFT-E065] Kraft could not map property 'score' in target type 'PersonDto' and reported no other error, so the mapper was not generated.`
- **AND** the error ends with the line `See: https://blu3berry-why.github.io/Kraft/user-guide/errors/#kraft-e065`
