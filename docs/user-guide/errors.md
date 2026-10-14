# Errors Reference

Every error and warning Kraft prints during compilation has a stable code. This page lists each code with its cause and fix.

## Reading a Kraft diagnostic

A Kraft diagnostic looks like this:

```text
----------------------------------------
Kraft KSP Warning
----------------------------------------

[KRAFT-W001] <what went wrong>
<details: the types and properties involved, and how to fix it>

----------------------------------------
See: https://blu3berry-why.github.io/Kraft/user-guide/errors/#kraft-w001
```

- The code in square brackets on the first line identifies the diagnostic. `KRAFT-E###` codes are errors, which fail compilation. `KRAFT-W###` codes are warnings, which do not.
- The `See:` line on the last line links to that code's entry below.
- A code keeps its meaning across releases. The message wording can change, but the code stays the same, so it is safe to search for a code in logs, issues or CI output.

## Suppressing a warning

A warning can be silenced on the declaration it points at, using Kotlin's standard `@Suppress` with the code:

```kotlin
@Suppress("KRAFT-W001")
fun List<RowDto>?.toRows(): List<Row> = this.orEmpty().filter { it.visible }.map { it.toRow() }
```

- The code must match exactly, including case: `@Suppress("kraft-w001")` does not suppress anything.
- A suppression only covers the code it names. `@Suppress("KRAFT-W002")` does not silence `KRAFT-W001`.
- The annotation goes on the declaration itself. A `@Suppress` on the enclosing class or file is not read.
- Errors cannot be suppressed: they mean Kraft cannot generate valid code.

## Warnings

### KRAFT-W001 — Redundant @MapUsing {#kraft-w001}

A property-source `@MapUsing` converter produces the same mapping Kraft would generate without it.

Full entry lands with the warning implementation.

### KRAFT-W002 — Nullable-twin @KraftConverter {#kraft-w002}

A `@KraftConverter` for `S? → T?` duplicates the safe call Kraft already derives from an existing `S → T` converter.

Full entry lands with the warning implementation.
