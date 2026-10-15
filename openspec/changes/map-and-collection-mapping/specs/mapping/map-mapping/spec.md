# Spec Delta

## Purpose

Defines how Kraft maps `Map` and `MutableMap` properties: how values are mapped, which key mappings are allowed because they keep every entry, how map values nest with collections, and how nullability behaves for map containers and values.

Unless a scenario says otherwise, every scenario uses `data class ItemSource(val id: Int)`, `data class ItemDto(val id: Int)`, `@JvmInline value class UserId(val value: String)`, a `Src`/`Tgt` pair with the stated property, and `@MapConfig(source = Src::class, target = Tgt::class) object M`. `toItemDto()` is the nested mapper Kraft generates for `ItemSource → ItemDto`.

## ADDED Requirements

### Requirement: Map values are mapped element-wise
When source and target are `Map` or `MutableMap` and the value types differ, Kraft SHALL map each value with the operation it would use for a single property of the value types (nested mapper, enum mapper, `@KraftConverter`, value-class wrap/unwrap), keeping keys of the same type unchanged.

#### Scenario: Nested values
- **WHEN** `Src.byId: Map<String, ItemSource>` and `Tgt.byId: Map<String, ItemDto>`
- **THEN** the mapper assigns `byId = this.byId.mapValues { it.value.toItemDto() }`

#### Scenario: Named @KraftConverter on values
- **WHEN** `@KraftConverter fun Foo.intoBar(): Bar` is registered (`Foo` and `Bar` are interfaces, so no nested mapper is generated for them), `Src.m: Map<String, Foo>` and `Tgt.m: Map<String, Bar>`
- **THEN** the mapper assigns `m = this.m.mapValues { it.value.intoBar() }`

#### Scenario: MutableMap target
- **WHEN** `Src.byId: Map<String, ItemSource>` and `Tgt.byId: MutableMap<String, ItemDto>`
- **THEN** the mapper assigns `byId = this.byId.mapValues { it.value.toItemDto() }.toMutableMap()`

#### Scenario: Same types, MutableMap target
- **WHEN** `Src.m: Map<String, Int>` and `Tgt.m: MutableMap<String, Int>`
- **THEN** the mapper assigns `m = this.m.toMutableMap()`

#### Scenario: Map and non-map
- **WHEN** `Src.items: Map<String, ItemSource>` and `Tgt.items: List<ItemDto>`
- **THEN** compilation fails with the KRAFT-E005 type-mismatch error for `items`

### Requirement: One-to-one key mappings
Kraft SHALL map keys whose types differ when the key operation is one-to-one: a value-class wrap or unwrap, or an enum mapping in which no two source entries map to the same target entry. Auto-derived enum mappings are one-to-one by construction.

#### Scenario: Value class keys and nested values
- **WHEN** `Src.byUser: Map<String, ItemSource>` and `Tgt.byUser: Map<UserId, ItemDto>`
- **THEN** the mapper assigns `byUser = this.byUser.entries.associate { UserId(it.key) to it.value.toItemDto() }`

#### Scenario: Keys only
- **WHEN** `Src.counts: Map<UserId, Int>` and `Tgt.counts: Map<String, Int>`
- **THEN** the mapper assigns `counts = this.counts.mapKeys { it.key.value }`

#### Scenario: Auto-derived enum keys
- **WHEN** `enum class StatusDto { OPEN, CLOSED }`, `enum class Status { OPEN, CLOSED }`, `Src.counts: Map<StatusDto, Int>` and `Tgt.counts: Map<Status, Int>`
- **THEN** the mapper assigns `counts = this.counts.mapKeys { it.key.toStatus() }`

#### Scenario: One-to-one @MapEnum keys
- **WHEN** `@MapEnum` maps `StatusDto.OPEN → Status.ACTIVE` and `StatusDto.CLOSED → Status.DONE`, and the properties are as in "Auto-derived enum keys"
- **THEN** the mapper assigns `counts = this.counts.mapKeys { it.key.toStatus() }`

### Requirement: Key mappings that can merge entries are refused
Kraft SHALL fail compilation with KRAFT-E064 when keys would be mapped by an operation it cannot prove one-to-one: a nested mapper, a `@KraftConverter`, or a `@MapEnum` that maps two or more source entries to the same target entry.

#### Scenario: Nested-mapper keys
- **WHEN** `data class KeySource(val id: Int)`, `data class KeyDto(val id: Int)`, `Src.m: Map<KeySource, Int>` and `Tgt.m: Map<KeyDto, Int>`
- **THEN** compilation fails with `[KRAFT-E064] Cannot map property 'm': keys KeySource → KeyDto would go through a nested mapper, which can map two different keys to the same key and silently drop entries.`
- **AND** the error contains `Keep the key type the same on both sides, use a value-class or one-to-one enum key, or map the property with @MapUsing.`

#### Scenario: @KraftConverter keys
- **WHEN** `@KraftConverter fun Long.toKey(): String` is registered, `Src.m: Map<Long, Int>` and `Tgt.m: Map<String, Int>`
- **THEN** compilation fails with `[KRAFT-E064] Cannot map property 'm': keys Long → String would go through @KraftConverter Long.toKey(), which can map two different keys to the same key and silently drop entries.`

#### Scenario: Many-to-one @MapEnum keys
- **WHEN** `@MapEnum` maps `StatusDto.OPEN → Status.ACTIVE` and `StatusDto.REOPENED → Status.ACTIVE`, `Src.counts: Map<StatusDto, Int>` and `Tgt.counts: Map<Status, Int>`
- **THEN** compilation fails with `[KRAFT-E064] Cannot map property 'counts': keys StatusDto → Status would go through @MapEnum StatusDto → Status, which maps OPEN, REOPENED to ACTIVE and would silently drop entries.`

#### Scenario: Many-to-one enum as a value is still allowed
- **WHEN** the many-to-one `@MapEnum` above is used for values: `Src.m: Map<String, StatusDto>` and `Tgt.m: Map<String, Status>`
- **THEN** the mapper assigns `m = this.m.mapValues { it.value.toStatus() }`

### Requirement: Map nullability
A nullable source map SHALL be safe-called and, into a non-null target, fall back to `emptyMap()` (`mutableMapOf()` for `MutableMap`). Nullable values SHALL map to nullable values through a safe call. Nullable values into non-null values SHALL fail with the KRAFT-E005 type-mismatch error, because dropping an entry would lose its key.

#### Scenario: Nullable map into non-null map
- **WHEN** `Src.byId: Map<String, ItemSource>?` and `Tgt.byId: Map<String, ItemDto>`
- **THEN** the mapper assigns `byId = this.byId?.mapValues { it.value.toItemDto() } ?: emptyMap()`

#### Scenario: Nullable values kept
- **WHEN** `Src.byId: Map<String, ItemSource?>` and `Tgt.byId: Map<String, ItemDto?>`
- **THEN** the mapper assigns `byId = this.byId.mapValues { it.value?.toItemDto() }`

#### Scenario: Nullable values into non-null values
- **WHEN** `Src.byId: Map<String, ItemSource?>` and `Tgt.byId: Map<String, ItemDto>`
- **THEN** compilation fails with the KRAFT-E005 type-mismatch error for `byId`, and the error contains `Map values cannot be dropped like list elements: removing a null value would also remove its key.`

### Requirement: Maps nest with collections
Map values and collection elements SHALL nest in each other to any depth, following the collection-mapping rules at collection levels and these rules at map levels, without shadowing lambda parameters: the innermost lambda uses `it`, and each enclosing lambda names its parameter `e<depth>`, counting from 0 at the outermost.

#### Scenario: Map of lists
- **WHEN** `Src.groups: Map<String, List<ItemSource>>` and `Tgt.groups: Map<String, List<ItemDto>>`
- **THEN** the mapper assigns `groups = this.groups.mapValues { e0 -> e0.value.map { it.toItemDto() } }`

#### Scenario: List of maps
- **WHEN** `Src.pages: List<Map<String, ItemSource>>` and `Tgt.pages: List<Map<String, ItemDto>>`
- **THEN** the mapper assigns `pages = this.pages.map { e0 -> e0.mapValues { it.value.toItemDto() } }`

#### Scenario: Lossy conversion inside a map value
- **WHEN** `Src.groups: Map<String, List<ItemSource>>` and `Tgt.groups: Map<String, Set<ItemDto>>`
- **THEN** compilation fails with `[KRAFT-E063] Cannot map property 'groups': converting List<ItemSource> to Set<ItemDto> would drop duplicate elements.`
