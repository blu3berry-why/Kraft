# argument-defaults Specification

## Purpose

Defines how Kraft treats annotation arguments that the user omitted, so that defaulted arguments behave the same in every KSP compilation (JVM, Android, KMP metadata) and a missing-argument error suggests a value of the right kind.

## Requirements

### Requirement: Omitted defaulted arguments use their declared default
When KSP leaves a Kraft annotation argument out of its argument list and that argument has a default in the annotation class, Kraft SHALL use the declared default and SHALL NOT report a missing argument. This holds even when KSP doesn't supply the default itself, as in KMP metadata compilations.

#### Scenario: Whole-source @MapUsing without source
- **WHEN** a `@MapConfig(source = Draft::class, target = Request::class)` object contains `@MapUsing(target = "inputs") fun Draft.toInputs(): List<Input> = …`, compiled in a KMP `commonMain` metadata compilation
- **THEN** compilation succeeds and the generated mapper assigns `inputs = this.toInputs()`

#### Scenario: Omitted direction defaults to AUTO
- **WHEN** `@MapUsing(source = "id", target = "id")` is compiled and KSP omits `direction`
- **THEN** the converter is treated as `ConverterDirection.AUTO` and no error is reported

### Requirement: Missing required arguments are reported with a kind-appropriate example
When an argument with no default is missing, the error SHALL name the annotation and argument, and SHALL show an example value that matches the argument's declared kind. A class argument gets `MyType::class`, a string argument gets `"propertyName"`, and an enum argument gets its first entry.

#### Scenario: Missing String argument
- **WHEN** an annotation is compiled where KSP reports no value for the required `String` argument `target` of `@MapUsing`
- **THEN** compilation fails with an error containing `Missing required argument 'target' in @MapUsing.` and `@MapUsing(target = "propertyName")`, and the error does not contain `::class`

#### Scenario: Missing KClass argument
- **WHEN** `@MapConfig` is compiled without its required `source` argument
- **THEN** compilation fails with an error containing `Missing required argument 'source' in @MapConfig.` and `@MapConfig(source = MyType::class)`
