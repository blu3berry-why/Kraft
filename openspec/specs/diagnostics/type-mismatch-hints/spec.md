# type-mismatch-hints Specification

## Purpose

Attaches cause-specific guidance to Kraft's type-mismatch error, so that failures caused outside Kraft (such as duplicated generated DTO types) name their real cause instead of reading as a Kraft limitation.

## Requirements

### Requirement: Duplicated nested types get a generator hint
When a type-mismatch error pairs two types with the same simple name, different enclosing declarations, and the same shape, the error SHALL include a duplicated-generated-types hint naming both qualified types. Same shape means identical entry names for enums, or identical primary-constructor parameter names for classes.

#### Scenario: kmpgen-style duplicated enum
- **WHEN** the source declares `val role: AuthResponse.User.Role`, the target declares `val role: AuthMe200Response.Role`, both enums declare entries `ADMIN, MEMBER`, and no converter or `@MapEnum` bridges them
- **THEN** compilation fails with the type-mismatch error for `role`, and the error contains `Types differ only by enclosing class: AuthResponse.User.Role vs AuthMe200Response.Role, with identical entries.`
- **AND** it contains `This is usually a code generator inlining the same $ref twice (e.g. kmpgen). Fix it in the generator, or bridge the pair with @MapEnum(source = AuthResponse.User.Role::class, target = AuthMe200Response.Role::class).`

#### Scenario: Same simple name, different shape
- **WHEN** the source's `Role` enum declares `ADMIN, MEMBER` and the target's `Role` enum declares `OWNER, GUEST`
- **THEN** the type-mismatch error does not contain `Types differ only by enclosing class`

#### Scenario: Duplicated data class
- **WHEN** the source declares `val user: A.User` and the target declares `val user: B.User`, both classes having primary-constructor parameters `id, name`
- **THEN** the type-mismatch error contains `Types differ only by enclosing class: A.User vs B.User, with identical constructor parameters.` and suggests `@KraftConverter` or a `@MapConfig` for the pair instead of `@MapEnum`

### Requirement: Hint does not displace existing guidance
The duplicated-types hint SHALL be added in front of the existing "How to fix" guidance, and SHALL NOT replace it.

#### Scenario: Nullable duplicated enum keeps nullability guidance
- **WHEN** the kmpgen-style scenario's source property is `AuthResponse.User.Role?` and the target property is non-null `AuthMe200Response.Role`
- **THEN** the error contains both the duplicated-types hint and `The source is nullable and the target is not.`
