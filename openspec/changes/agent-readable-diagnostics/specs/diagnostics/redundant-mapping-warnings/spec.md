# Spec Delta

## Purpose

Warns when user-written converter code duplicates a mapping Kraft already generates natively, so that authors (and coding agents) delete hand-written code instead of maintaining it, while keeping an explicit way to opt out when the duplication is intentional.

## ADDED Requirements

### Requirement: Redundant property-source @MapUsing is reported
When a property-source `@MapUsing` (non-blank `source`) claims a target property that Kraft would map without it, Kraft SHALL emit warning `KRAFT-W001`, still use the user's converter, and still generate the mapper.

#### Scenario: Hand-written list helper duplicates element-wise mapping
- **WHEN** `RowDto → Row` is mappable (a `@MapConfig` or `@KraftConverter` for the pair exists), the source declares `val rows: List<RowDto>?`, the target declares `val rows: List<Row>`, and the config object contains `@MapUsing(source = "rows", target = "rows") fun List<RowDto>?.toRows() = this?.map { it.toRow() } ?: emptyList()`
- **THEN** compilation succeeds, the generated mapper calls `toRows()`, and a warning is emitted whose first line is `[KRAFT-W001] @MapUsing 'toRows' on target property 'rows' duplicates a mapping Kraft already generates.`
- **AND** the warning body contains the expression Kraft would have generated, `rows = this.rows?.map { it.toRow() } ?: emptyList()`, and the fix line `Remove 'toRows', or add @Suppress("KRAFT-W001") to it if its body intentionally differs (filtering, sorting, defaults).`

#### Scenario: Identity converter on a direct match
- **WHEN** source and target both declare `val name: String` and the config object contains `@MapUsing(source = "name", target = "name") fun keep(name: String): String = name`
- **THEN** compilation succeeds and `KRAFT-W001` is emitted for `keep` on `name`, showing `name = this.name`

#### Scenario: Converter that bridges a real type gap is not reported
- **WHEN** the source declares `val birthDate: LocalDate`, the target declares `val age: Int`, and `@MapUsing(source = "birthDate", target = "age")` is present
- **THEN** compilation succeeds and no `KRAFT-W001` is emitted

#### Scenario: Per-mapper override of a global converter is not reported
- **WHEN** a module declares `@KraftConverter fun Int.toGlobal(): Long`, source and target declare `val count: Int` / `val count: Long`, and the config object contains `@MapUsing(source = "count", target = "count") fun special(c: Int): Long = c * 100L`
- **THEN** compilation succeeds, the generated mapper calls `special`, and no `KRAFT-W001` is emitted, because overriding a global converter for one mapper is a supported use

#### Scenario: Global converter applied element-wise is still reported
- **WHEN** `RowDto → Row` is bridged only by `@KraftConverter fun RowDto.toRow(): Row`, and the list-helper `@MapUsing` from the first scenario is present
- **THEN** `KRAFT-W001` is emitted for `toRows`

#### Scenario: Whole-source converter is never reported
- **WHEN** `@MapUsing(target = "fullName") fun User.toFullName(): String = "$firstName $lastName"` is present, and the source also happens to declare `val fullName: String`
- **THEN** no `KRAFT-W001` is emitted

### Requirement: Nullable-twin @KraftConverter is reported
When a module registers a `@KraftConverter` from `S?` to `T?` and also registers one from `S` to `T` (same module, or a classpath upstream), Kraft SHALL emit warning `KRAFT-W002` on the nullable converter. Both converters stay registered.

#### Scenario: Nullable twin next to a non-null bridge
- **WHEN** a module declares `@KraftConverter fun ReasonDto.toReason(): Reason` and `@KraftConverter fun ReasonDto?.toReasonNullable(): Reason? = this?.toReason()`
- **THEN** compilation succeeds and a warning is emitted on `toReasonNullable` whose first line is `[KRAFT-W002] @KraftConverter 'toReasonNullable' (ReasonDto? → Reason?) only lifts 'toReason' (ReasonDto → Reason) through null.`
- **AND** the warning body contains `Kraft already generates 'this.<property>?.toReason()' for nullable ReasonDto? → Reason? properties. Remove 'toReasonNullable', or add @Suppress("KRAFT-W002") if it does more than a safe call.`

#### Scenario: Nullable converter with no non-null counterpart
- **WHEN** a module declares only `@KraftConverter fun ReasonDto?.toReason(): Reason = this?.let { … } ?: Reason.Unknown`
- **THEN** no `KRAFT-W002` is emitted

### Requirement: Redundancy warnings are suppressible per declaration
Kraft SHALL NOT emit `KRAFT-W001` or `KRAFT-W002` for a function annotated `@Suppress("<code>")` with the matching code. A suppression for one code SHALL NOT silence the other.

#### Scenario: Suppressed redundant @MapUsing
- **WHEN** the list-helper scenario's `toRows` is additionally annotated `@Suppress("KRAFT-W001")`
- **THEN** compilation succeeds with no `KRAFT-W001` warning

#### Scenario: Wrong code does not suppress
- **WHEN** the same `toRows` is annotated `@Suppress("KRAFT-W002")` instead
- **THEN** `KRAFT-W001` is still emitted

### Requirement: Redundancy warnings never change generated code
Emitting or suppressing `KRAFT-W001` / `KRAFT-W002` SHALL NOT change the generated mapper source. The user's converter keeps priority over native resolution.

#### Scenario: Generated source identical with and without suppression
- **WHEN** the list-helper scenario is compiled once with and once without `@Suppress("KRAFT-W001")`
- **THEN** the generated mapper files are byte-identical
