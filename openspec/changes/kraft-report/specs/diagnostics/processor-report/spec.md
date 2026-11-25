# Spec Delta

## Purpose

An opt-in, machine-readable report of what the Kraft processor discovered in a compilation (converters and where they came from, enum pairs, sides and emitted side aliases), so a user can answer "is my converter being seen?" without reading generated code.

## ADDED Requirements

### Requirement: Report is opt-in through kraft.report
The processor SHALL write the report only when the KSP option `kraft.report` is `true` (case-insensitive). With the option unset or any other value, the processor SHALL write no report file and its generated Kotlin output SHALL be unchanged.

#### Scenario: Option enabled
- **WHEN** a module declares `data class A(val x: Int)` and `@MapFrom(A::class) data class B(val x: Int)` and compiles with `kraft.report=true`
- **THEN** compilation succeeds and the KSP output contains a resource file `kraft/report.json`

#### Scenario: Option absent
- **WHEN** the same sources compile with no `kraft.report` option
- **THEN** the KSP output contains no `kraft/report.json`, and the generated `.kt` files are identical to the run with `kraft.report=true`

### Requirement: Report is versioned, deterministic JSON
The report SHALL be a JSON object with an integer `schemaVersion` (1 for this change), the compilation's `moduleId` (or null when unset), and the arrays `converters`, `enumPairs`, `sides` and `sideAliases`, each always present and sorted by a stable key. Running the same compilation twice SHALL produce byte-identical files. Later sections SHALL be added as new top-level keys without changing existing ones.

#### Scenario: Empty module
- **WHEN** a module with one `@MapFrom` mapper and no converters, enums or sides compiles with `kraft.report=true` and `kraft.moduleId=:app`
- **THEN** `kraft/report.json` contains `"schemaVersion": 1`, `"moduleId": ":app"`, and `"converters": []`, `"enumPairs": []`, `"sides": []`, `"sideAliases": []`

#### Scenario: Stable across runs
- **WHEN** a module with two `@KraftConverter` functions declared in reverse alphabetical order compiles twice with `kraft.report=true`
- **THEN** both report files are byte-identical, and the converter entries are ordered by source type, then target type, then function

### Requirement: Converter table names each entry's origin
Each entry in `converters` SHALL give `source` and `target` as qualified type names with a trailing `?` when nullable, `function` as the qualified name of the function the generated code calls, and `origin` as one of `same-module`, `classpath-delegate` or `synthetic`. A `classpath-delegate` entry SHALL also give `module`, the producing module id recorded on the delegate (the `kraft.moduleId` sanitised to an identifier, or the module's content hash when unset).

#### Scenario: Same-module converter
- **WHEN** a module declares `@KraftConverter fun Money.toCents(): Long` in package `app` and compiles with `kraft.report=true`
- **THEN** `converters` contains `{"source": "app.Money", "target": "kotlin.Long", "function": "app.toCents", "origin": "same-module"}`

#### Scenario: Nullable converter
- **WHEN** the module declares `@KraftConverter fun String?.toTag(): Tag?` in package `app`
- **THEN** the entry has `"source": "kotlin.String?"` and `"target": "app.Tag?"`

#### Scenario: Classpath delegate used by a mapper
- **WHEN** an upstream module with `kraft.moduleId=:core` declares `@KraftConverter fun Money.toCents(): Long`, and the consumer maps `data class A(val price: Money)` to `@MapFrom(A::class) data class B(val price: Long)` with `kraft.report=true`
- **THEN** the consumer's `converters` contains an entry with `"source": "core.Money"`, `"target": "kotlin.Long"`, `"origin": "classpath-delegate"` and `"module": "_core"`

#### Scenario: Enum mapper as a synthetic converter
- **WHEN** a module declares `@MapEnum(source = StatusDto::class, target = Status::class) object StatusMapper` in package `app`
- **THEN** `converters` contains an entry with `"source": "app.StatusDto"`, `"target": "app.Status"`, `"function": "app.generated.toStatus"` and `"origin": "synthetic"`

### Requirement: Classpath delegates are reported only once resolved
A classpath delegate SHALL appear in `converters` only when a mapper in the compilation looked up its type pair. Delegates for pairs no mapper needed SHALL NOT appear, because they cannot be enumerated on klib classpaths.

#### Scenario: Unused upstream converter
- **WHEN** the upstream module declares `@KraftConverter fun Money.toCents(): Long` and no consumer mapper has a `Money` to `Long` property pair
- **THEN** the consumer's `converters` contains no entry with `"origin": "classpath-delegate"`

### Requirement: Enum pairs record how they were derived
Each entry in `enumPairs` SHALL give `source`, `target`, the generated `function`, and `derivation`: `declared` for a `@MapEnum` declaration, with `declaredBy` naming that declaration, or `auto` for a pair Kraft derived from a mapper's property types, with no `declaredBy` key.

#### Scenario: Declared enum pair
- **WHEN** a module declares `@MapEnum(source = StatusDto::class, target = Status::class) object StatusMapper` in package `app`
- **THEN** `enumPairs` contains `{"source": "app.StatusDto", "target": "app.Status", "function": "app.generated.toStatus", "derivation": "declared", "declaredBy": "app.StatusMapper"}`

#### Scenario: Auto-derived enum pair
- **WHEN** a module declares enums `RoleDto { ADMIN }` and `Role { ADMIN }` in package `app`, `data class UserDto(val role: RoleDto)` and `@MapFrom(UserDto::class) data class User(val role: Role)`, with no `@MapEnum`
- **THEN** `enumPairs` contains an entry with `"source": "app.RoleDto"`, `"target": "app.Role"` and `"derivation": "auto"`

### Requirement: Sides and emitted aliases are reported
`sides` SHALL list each configured side with `slot`, `name`, `packagePattern`, `template` and `emitMode`. `sideAliases` SHALL list each alias function the processor emitted, with `receiver`, `alias`, `target`, `side` and `mapper` (the declaration that produced it).

#### Scenario: Alias emitted for a side
- **WHEN** `kraft.side.dto.name=Dto` and `kraft.side.dto.packagePattern=app.dto.**` are set, and `@MapConfig(source = app.domain.User::class, target = app.dto.UserDto::class) object UserMapper` is declared in package `app`, with `kraft.report=true`
- **THEN** `sides` contains `{"slot": "dto", "name": "Dto", "packagePattern": "app.dto.**", "template": "to{side}", "emitMode": "BOTH"}`
- **AND** `sideAliases` contains `{"receiver": "app.domain.User", "alias": "toDto", "target": "app.dto.UserDto", "side": "Dto", "mapper": "app.UserMapper"}`

#### Scenario: FULL_NAME_ONLY emits no alias
- **WHEN** the same side is configured with `kraft.side.dto.emitMode=FULL_NAME_ONLY`
- **THEN** `sides` lists the side with `"emitMode": "FULL_NAME_ONLY"` and `sideAliases` is empty

### Requirement: Report is written when compilation fails
When `kraft.report=true` and processing reports errors, the processor SHALL still write the report with everything discovered before the failure.

#### Scenario: Type mismatch with report enabled
- **WHEN** a module declares `@KraftConverter fun Money.toCents(): Long` and a mapper whose `price: Money` maps to `price: String`, compiled with `kraft.report=true`
- **THEN** compilation fails with `[KRAFT-E005]`, and `kraft/report.json` exists and lists the `app.Money` → `kotlin.Long` converter
