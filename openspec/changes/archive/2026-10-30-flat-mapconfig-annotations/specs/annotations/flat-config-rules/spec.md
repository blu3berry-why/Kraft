# Spec Delta

## Purpose

Lets a `@MapConfig` object state each rename and ignore rule as its own repeatable annotation on the object, one line per rule, as an alternative to the nested array arguments of `@MapConfig`.

## ADDED Requirements

### Requirement: Flat @FieldMapping renames a property
A `@FieldMapping(source, target)` placed directly on a `@MapConfig` object, repeated as often as needed, SHALL have the same effect as the same entry in `@MapConfig.fieldMappings`.

#### Scenario: Two flat renames
- **WHEN** `object UserMapper` carries `@MapConfig(source = UserDto::class, target = User::class)`, `@FieldMapping(source = "name", target = "fullName")` and `@FieldMapping(source = "mail", target = "email")`, `UserDto(val name: String, val mail: String)` and `User(val fullName: String, val email: String)`
- **THEN** compilation succeeds and the generated `fun UserDto.toUser()` contains `fullName = this.name` and `email = this.mail`

#### Scenario: Flat rename on a reverse mapper
- **WHEN** the object from "Two flat renames" also carries `@MapReverse`
- **THEN** the generated `fun User.toUserDto()` contains `name = this.fullName` and `mail = this.email`

### Requirement: Flat @MapIgnoreField ignores a property
A `@MapIgnoreField(name, direction)` placed directly on a `@MapConfig` object, repeated as often as needed, SHALL have the same effect, `direction` default included, as the same entry in `@MapConfig.ignoredMappings`.

#### Scenario: Flat ignore with the default direction
- **WHEN** `object UserMapper` carries `@MapConfig(source = UserDto::class, target = User::class)` and `@MapIgnoreField("internalNotes")`, and `User` declares `val internalNotes: String = ""` with no matching property on `UserDto`
- **THEN** compilation succeeds and the generated constructor call has no `internalNotes =` argument

#### Scenario: Flat ignore with an explicit direction
- **WHEN** the object carries `@MapReverse`, `@MapIgnoreField("auditLog", direction = IgnoreSide.TARGET)`, and both `User` and `UserDto` declare `val auditLog: String = ""`
- **THEN** the forward mapper has no `auditLog =` argument and the reverse mapper contains `auditLog = this.auditLog`

### Requirement: The array form keeps working
`@MapConfig.fieldMappings` and `@MapConfig.ignoredMappings` SHALL keep their existing behaviour and generated output. Flat and array rules on one object SHALL combine when they name different targets.

#### Scenario: Array form unchanged
- **WHEN** a `@MapConfig` object uses `fieldMappings = [FieldMapping(source = "a", target = "x")]` and `ignoredMappings = [MapIgnoreField("notes")]` with no flat annotations
- **THEN** the generated mapper is byte-identical to the one generated before this change

#### Scenario: Mixed forms on different targets
- **WHEN** a `@MapConfig` object has `fieldMappings = [FieldMapping(source = "a", target = "x")]` and a flat `@FieldMapping(source = "b", target = "y")`
- **THEN** the generated mapper contains both `x = this.a` and `y = this.b`

### Requirement: A target given in both forms is a compile error
When one object has a `@FieldMapping` for the same `target`, or a `@MapIgnoreField` for the same `name`, both in the array and as a flat annotation, compilation SHALL fail with a new `KRAFT-E` code. This holds even when the two rules are identical.

#### Scenario: Same rename in both forms
- **WHEN** `object UserMapper` has `fieldMappings = [FieldMapping(source = "name", target = "fullName")]` and a flat `@FieldMapping(source = "name", target = "fullName")`
- **THEN** compilation fails with an error whose first line is `[KRAFT-E066] @FieldMapping for target 'fullName' is declared both in @MapConfig.fieldMappings and as an annotation on 'UserMapper'. Keep one.`

#### Scenario: Same ignore in both forms
- **WHEN** `object UserMapper` has `ignoredMappings = [MapIgnoreField("notes")]` and a flat `@MapIgnoreField("notes", direction = IgnoreSide.TARGET)`
- **THEN** compilation fails with an error whose first line is `[KRAFT-E066] @MapIgnoreField for 'notes' is declared both in @MapConfig.ignoredMappings and as an annotation on 'UserMapper'. Keep one.`

### Requirement: A flat rule requires @MapConfig on the same declaration
A flat `@FieldMapping` or `@MapIgnoreField` on a declaration without `@MapConfig` SHALL fail compilation with a new `KRAFT-E` code. It SHALL NOT be ignored silently.

#### Scenario: Flat rename on a @MapFrom class
- **WHEN** `@MapFrom(UserDto::class) @FieldMapping(source = "name", target = "fullName") data class User(val fullName: String)` is compiled
- **THEN** compilation fails with an error whose first line is `[KRAFT-E071] @FieldMapping on 'User' requires @MapConfig on the same object. Use @MapField on the property for @MapFrom/@MapTo classes.`

### Requirement: Flat rules work in the KMP metadata compilation
Flat rules on a `@MapConfig` object in `commonMain` SHALL be honoured when Kraft runs as `kspCommonMainMetadata`.

#### Scenario: Flat rules in commonMain
- **WHEN** `integration-tests/kmp-consumer` declares a `commonMain` `@MapConfig` object with a flat `@FieldMapping` and a flat `@MapIgnoreField`, and runs `kspCommonMainKotlinMetadata`
- **THEN** the generated `commonMain` mapper applies both rules, and a `commonTest` calling it passes on JVM and compiles for JS
