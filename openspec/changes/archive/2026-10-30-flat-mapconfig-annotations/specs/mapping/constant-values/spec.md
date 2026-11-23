# Spec Delta

## Purpose

Lets a mapper give a target property a fixed or computed value written as ordinary Kotlin: a property or zero-parameter function of the `@MapConfig` object marked `@MapConstant`, with no value written as a string.

## ADDED Requirements

### Requirement: A @MapConstant property supplies a target value
A property of a `@MapConfig` object annotated `@MapConstant` SHALL be assigned to the target constructor parameter of the same name. The generated mapper SHALL read the property through the object, so the value is evaluated as Kotlin evaluates that property.

#### Scenario: Literal property, target from the member name
- **WHEN** `object UserMapper` in package `app.mapping` carries `@MapConfig(source = UserDto::class, target = User::class)` and declares `@MapConstant val currency = "HUF"`, and `User` declares `val currency: String` with no matching property on `UserDto`
- **THEN** compilation succeeds, the generated `fun UserDto.toUser()` (in Kraft's generated package) contains `currency = UserMapper.currency`, and the generated file compiles with no manual import

#### Scenario: const val
- **WHEN** the object declares `@MapConstant const val isActive = true` and `User` declares `val isActive: Boolean`
- **THEN** the generated mapper contains `isActive = UserMapper.isActive`

#### Scenario: Constant over a same-named source property
- **WHEN** both `UserDto` and `User` declare `val currency: String` and the object declares `@MapConstant val currency = "HUF"`
- **THEN** the generated mapper contains `currency = UserMapper.currency`, not `currency = this.currency`, and no warning is emitted

### Requirement: A @MapConstant function is called on every mapping
A zero-parameter, non-extension function of a `@MapConfig` object annotated `@MapConstant` SHALL be called once per generated-mapper invocation.

#### Scenario: Function with an explicit target
- **WHEN** the object declares `@MapConstant(target = "createdAt") fun now(): Long = counter++` with `var counter = 0L`, and `User` declares `val createdAt: Long`
- **THEN** the generated mapper contains `createdAt = UserMapper.now()`, and calling `toUser()` twice yields `createdAt` values `0` and `1`

### Requirement: The target name defaults to the member name
`@MapConstant.target` SHALL default to the annotated member's simple name. A non-blank `target` SHALL override it.

#### Scenario: Overridden target on a property
- **WHEN** the object declares `@MapConstant(target = "currency") val defaultCurrency = "HUF"`
- **THEN** the generated mapper contains `currency = UserMapper.defaultCurrency`

### Requirement: The target must exist
A `@MapConstant` whose target name is not a constructor parameter of the target class in the applicable direction SHALL fail compilation.

#### Scenario: Misspelled target
- **WHEN** the object declares `@MapConstant val curency = "HUF"` and `User` has no parameter `curency`
- **THEN** compilation fails with an error whose first line is `[KRAFT-E067] @MapConstant 'UserMapper.curency' targets 'curency', which is not a constructor parameter of 'User'.`

### Requirement: The member type must fit the target type
The member's type (a property's type, or a function's return type, declared or inferred) SHALL be assignable to the target parameter's type, or compilation SHALL fail. A non-null member MAY fill a nullable target.

#### Scenario: Wrong type
- **WHEN** the object declares `@MapConstant val isActive = "yes"` and `User` declares `val isActive: Boolean`
- **THEN** compilation fails with an error whose first line is `[KRAFT-E068] @MapConstant 'UserMapper.isActive' has type String, but target 'isActive' in 'User' has type Boolean.`

#### Scenario: Nullable member into a non-null target
- **WHEN** the object declares `@MapConstant val nickname: String? = null` and `User` declares `val nickname: String`
- **THEN** compilation fails with an error whose first line is `[KRAFT-E068] @MapConstant 'UserMapper.nickname' has type String?, but target 'nickname' in 'User' has type String.`

#### Scenario: Non-null member into a nullable target
- **WHEN** the object declares `@MapConstant val nickname = "anon"` and `User` declares `val nickname: String?` with no default
- **THEN** compilation succeeds, the mapper contains `nickname = UserMapper.nickname`, and no `KRAFT-W009` is emitted

### Requirement: Only readable, argument-free members qualify
`@MapConstant` SHALL be accepted only on a non-private property or a non-private, zero-parameter, non-extension function declared directly in a `@MapConfig` object. Anything else SHALL fail compilation with one `KRAFT-E069` error per member.

#### Scenario: Private member
- **WHEN** the object declares `@MapConstant private val currency = "HUF"`
- **THEN** compilation fails with an error whose first line is `[KRAFT-E069] @MapConstant 'UserMapper.currency' must not be private: the generated mapper cannot read it.`

#### Scenario: Function with parameters
- **WHEN** the object declares `@MapConstant fun currency(country: String) = "HUF"`
- **THEN** compilation fails with an error whose first line is `[KRAFT-E069] @MapConstant function 'UserMapper.currency' must take no parameters. To compute a value from the source, use @MapUsing.`

#### Scenario: Extension function
- **WHEN** the object declares `@MapConstant fun UserDto.currency() = "HUF"`
- **THEN** compilation fails with an error whose first line is `[KRAFT-E069] @MapConstant function 'UserMapper.currency' must not be an extension function. To compute a value from the source, use @MapUsing.`

#### Scenario: Not inside a @MapConfig object
- **WHEN** `object Defaults { @MapConstant val currency = "HUF" }` has no `@MapConfig`
- **THEN** compilation fails with an error whose first line is `[KRAFT-E069] @MapConstant 'Defaults.currency' must be declared in a @MapConfig object.`

### Requirement: One rule per target
A target property claimed by a `@MapConstant` SHALL NOT also be claimed, in the same direction, by another `@MapConstant`, a `@FieldMapping` (either form), a `@MapUsing` or a `@MapIgnoreField`. Otherwise compilation SHALL fail.

#### Scenario: Constant and rename on one target
- **WHEN** the object carries `@FieldMapping(source = "cur", target = "currency")` and declares `@MapConstant val currency = "HUF"`
- **THEN** compilation fails with an error whose first line is `[KRAFT-E070] Target 'currency' in 'User' has more than one rule on 'UserMapper': @MapConstant 'currency' and @FieldMapping(source = "cur", target = "currency"). Keep one.`

#### Scenario: Constant and converter on one target
- **WHEN** the object declares `@MapConstant val currency = "HUF"` and `@MapUsing(target = "currency") fun UserDto.cur() = "EUR"`
- **THEN** compilation fails with an error whose first line starts with `[KRAFT-E070] Target 'currency' in 'User' has more than one rule on 'UserMapper'`

#### Scenario: Two constants on one target
- **WHEN** the object declares `@MapConstant val currency = "HUF"` and `@MapConstant(target = "currency") fun cur() = "EUR"`
- **THEN** compilation fails with an error whose first line starts with `[KRAFT-E070] Target 'currency' in 'User' has more than one rule on 'UserMapper'`

#### Scenario: Constant and ignore on one target
- **WHEN** the object carries `@MapIgnoreField("currency")` and declares `@MapConstant val currency = "HUF"`
- **THEN** compilation fails with an error whose first line starts with `[KRAFT-E070] Target 'currency' in 'User' has more than one rule on 'UserMapper'`

### Requirement: Direction on a reverse mapper
`@MapConstant.direction` SHALL default to `ConverterDirection.AUTO`. With AUTO, the constant applies to the forward mapper when its target class has the parameter, otherwise to the reverse mapper. It SHALL never apply to both. FORWARD and REVERSE select a direction explicitly.

#### Scenario: AUTO picks forward
- **WHEN** a `@MapReverse` `@MapConfig(source = UserDto::class, target = User::class)` object declares `@MapConstant val currency = "HUF"`, and both `User` and `UserDto` declare `val currency: String`
- **THEN** `fun UserDto.toUser()` contains `currency = UserMapper.currency` and `fun User.toUserDto()` contains `currency = this.currency`

#### Scenario: AUTO falls back to reverse
- **WHEN** the same object declares `@MapConstant val origin = "api"`, only `UserDto` declares `val origin: String`, and `User` has no `origin`
- **THEN** `fun User.toUserDto()` contains `origin = UserMapper.origin` and the forward mapper does not mention `origin`

#### Scenario: Explicit REVERSE
- **WHEN** both classes declare `val currency: String` and the object declares `@MapConstant(direction = ConverterDirection.REVERSE) val currency = "HUF"`
- **THEN** `fun User.toUserDto()` contains `currency = UserMapper.currency` and `fun UserDto.toUser()` contains `currency = this.currency`

### Requirement: Constants work in the KMP metadata compilation
A `@MapConstant` member of a `commonMain` `@MapConfig` object SHALL be honoured when Kraft runs as `kspCommonMainMetadata`, including members whose type is inferred.

#### Scenario: Inferred-type constant in commonMain
- **WHEN** `integration-tests/kmp-consumer` declares a `commonMain` `@MapConfig` object with `@MapConstant val currency = "HUF"` (no explicit type)
- **THEN** the generated mapper assigns `currency = <Object>.currency`, and a `commonTest` asserting `"HUF"` passes on JVM and compiles for JS
