# Enum Mapping

Kraft generates exhaustive `when` expressions to map between enum classes. Every source entry must be accounted for, either by automatic name matching or by explicit field mappings.

## @MapEnum

Place `@MapEnum` on a config object (class or object). Both `source` and `target` must reference enum classes -- using a non-enum class produces a compile-time error.

```kotlin
@MapEnum(source = Status::class, target = StatusDto::class)
object StatusMapping
```

Parameters:

| Parameter | Description |
|---|---|
| `source` | The enum class to map from |
| `target` | The enum class to map to |
| `fieldMappings` | Array of `FieldMapping` for entries with different names (default: empty) |

## Auto-Mapping

When all entries share the same name on both sides, no `fieldMappings` are needed. Kraft matches entries by name automatically.

```kotlin
enum class Status { ACTIVE, INACTIVE, BANNED }
enum class StatusDto { ACTIVE, INACTIVE, BANNED }

@MapEnum(source = Status::class, target = StatusDto::class)
object StatusMapping
```

Generated code:

```kotlin
fun Status.toStatusDto(): StatusDto = when (this) {
    Status.ACTIVE -> StatusDto.ACTIVE
    Status.INACTIVE -> StatusDto.INACTIVE
    Status.BANNED -> StatusDto.BANNED
}
```

## Custom Entry Mapping

Use `fieldMappings` when source and target entries have different names. Each `FieldMapping` maps one source entry name to one target entry name.

```kotlin
enum class Status { ACTIVE, DISABLED }
enum class StatusDto { ACTIVE, INACTIVE }

@MapEnum(
    source = Status::class,
    target = StatusDto::class,
    fieldMappings = [FieldMapping(source = "DISABLED", target = "INACTIVE")]
)
object StatusMapping
```

Generated code:

```kotlin
fun Status.toStatusDto(): StatusDto = when (this) {
    Status.ACTIVE -> StatusDto.ACTIVE
    Status.DISABLED -> StatusDto.INACTIVE
}
```

## Mixed Mapping

Auto-matched entries and custom field mappings coexist naturally. Entries that share a name are mapped automatically; entries that differ are covered by `fieldMappings`.

```kotlin
enum class PaymentState  { PAID, PENDING, FAILED }
enum class PaymentStatus { PAID, AWAITING, ERROR }

@MapEnum(
    source = PaymentState::class,
    target = PaymentStatus::class,
    fieldMappings = [
        FieldMapping(source = "PENDING", target = "AWAITING"),
        FieldMapping(source = "FAILED",  target = "ERROR")
    ]
)
object PaymentMapping
```

Generated code:

```kotlin
fun PaymentState.toPaymentStatus(): PaymentStatus = when (this) {
    PaymentState.PAID -> PaymentStatus.PAID
    PaymentState.PENDING -> PaymentStatus.AWAITING
    PaymentState.FAILED -> PaymentStatus.ERROR
}
```

`PAID` is auto-mapped by name, while `PENDING` and `FAILED` use their explicit mappings.

## Exhaustiveness

Every source entry must be accounted for -- either by automatic name matching or by an explicit `FieldMapping`. If any source entry is left unmapped, Kraft emits a compile-time error.

### Example: unmapped entry

```kotlin
enum class Flag    { A, B, C }
enum class FlagDto { A, B }   // C has no match and no fieldMappings entry

@MapEnum(source = Flag::class, target = FlagDto::class)
object FlagMapping
```

This produces a compile-time KSP error indicating "unmapped source entries", listing which entries need to be covered.

## Error Cases

| Condition | Result |
|---|---|
| Source or target is not an enum class | Compile-time error |
| A source entry has no matching target entry and no `FieldMapping` | Compile-time error ("unmapped source entries") |
| A `FieldMapping` references a source entry name that does not exist | Compile-time error |
| A `FieldMapping` references a target entry name that does not exist | Compile-time error |
