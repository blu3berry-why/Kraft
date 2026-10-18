# Spec Delta

## Purpose

Gives every Kraft processor diagnostic a stable, greppable code and a link to a reference entry, so a human or a coding agent can go from a compiler message to its cause and fix without other context.

## ADDED Requirements

### Requirement: Every diagnostic carries a stable code
Every error and warning Kraft's processor emits SHALL start its first line with a code in square brackets: `[KRAFT-E###]` for errors, `[KRAFT-W###]` for warnings, three digits, zero-padded.

#### Scenario: Required-property error carries its code
- **WHEN** a `@MapConfig(source = A::class, target = B::class)` object is compiled and `B` declares `val total: Int` with no default, no matching property on `A`, and no override
- **THEN** compilation fails and the error's first line starts with `[KRAFT-E` followed by three digits and `]`, then `Required property 'total' in target type`

#### Scenario: Warning carries a W code
- **WHEN** a compilation triggers any Kraft warning (for example, a redundant `@MapUsing`, see `diagnostics/redundant-mapping-warnings`)
- **THEN** the warning's first line starts with `[KRAFT-W` followed by three digits and `]`

### Requirement: Every diagnostic links to its reference entry
Every diagnostic SHALL end with the line `See: https://blu3berry-why.github.io/Kraft/user-guide/errors/#kraft-<e|w>###`, using the lower-cased code as the anchor.

#### Scenario: Error ends with its docs link
- **WHEN** a compilation fails with a Kraft error whose code is `KRAFT-E007`
- **THEN** the error message's last non-blank line is `See: https://blu3berry-why.github.io/Kraft/user-guide/errors/#kraft-e007`

### Requirement: Codes are stable across releases
Once a code is released, it SHALL keep meaning the same failure condition. A code SHALL never be renumbered or reassigned. When a diagnostic is removed, its code is retired and documented as retired.

#### Scenario: Message wording changes, code stays
- **WHEN** the wording of the required-property error is changed in a later release
- **THEN** the same input still produces the same `KRAFT-E###` code

### Requirement: The errors reference documents every code
The user guide SHALL contain an errors reference page with one entry per code. Each entry gives the code, its severity, a one-line cause, a minimal annotated input that triggers it, and the fix. A build-time test SHALL fail when a code exists in the processor but has no entry on the page.

#### Scenario: Undocumented code fails the build
- **WHEN** a new diagnostic code is added to the processor without a matching `KRAFT-…` heading on the errors reference page
- **THEN** the `kraft-ksp:jvmTest` (or `kraft-core` test) gate fails and names the undocumented code

### Requirement: Existing message content is preserved
Adding the code and link SHALL NOT remove any explanation, available-property listing, or "How to fix" text that the diagnostic printed before.

#### Scenario: Type-mismatch guidance survives
- **WHEN** a nullable `String?` source property is mapped to a non-null `String` target property of the same name
- **THEN** the error still contains `only nullability differs` and the `@MapUsing(target = "<name>")` fallback snippet, now preceded by its code
