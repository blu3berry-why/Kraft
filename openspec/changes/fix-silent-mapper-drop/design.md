# Design

## Context

See proposal.md (Why) for the bug. The parts of the current code that constrain the fix:

- `PropertyResolver.resolve` runs an ordered rule chain. A rule returns `Resolved`, `Pass`, or `Reported`. Many rules (`DirectMatchRule`, `ClassOverrideRule`, `ConfigOverrideRule`, …) log an error and then return `null`, which means `Pass`, so the chain **continues** after an error. `RequiredFieldErrorRule` is terminal. It logs E004 for required targets and returns `null` silently for everything else.
- All three descriptor builders have a copy of `resolveAllProperties` with `resolver.resolve(prop, ctx) ?: return null`. Any `null` drops the mapper, and the builders assume the cause was already reported.
- Codegen needs no change. `CtorCallBuilder` emits nothing for `PropertyMappingStrategy.Ignored` and `name = <expr>` for `PropertyMappingStrategy.Constant`.
- The `probeShadowedNative` path (KRAFT-W001) re-runs the rules on a context copy with `MutedKspLogger`, so whatever we add must stay inert there.
- `@Suppress` matching already exists in `isKraftSuppressed(diagnostic)`. It checks exactly one declaration.

**Generated function signatures do not change.** The change alters which mappers get generated and what their bodies contain. It lands in the **existing** strategies: `PropertyMappingStrategy.Ignored` for defaulted targets and `PropertyMappingStrategy.Constant(expression = "null")` for nullable targets with no default. This is the first producer of `Constant`.

## Goals / Non-Goals

**Goals:**
- Fix the bug once, in `PropertyResolver`, so the three builders keep their identical `?: return null` loops untouched.
- Make "null with no diagnostic" impossible for any rule chain, not only the default one.

**Non-Goals:**
- Deduplicating the three `resolveAllProperties` copies.
- Fixing the existing double-error case (a type mismatch followed by E004 on a required property). That is noted as a risk below, not changed here.
- Any user-facing way to choose a constant value. That's roadmap #3.

## Decisions

### D1. Per-property error tracking inside `PropertyResolver`
`resolve` wraps `ctx.logger` in a small delegating `ErrorTrackingLogger`, a `KSPLogger` that counts `error()` calls and forwards everything. It runs the chain on `ctx.copy(logger = tracker)`. Afterwards the resolver knows whether *this property's* resolution reported an error.

*Alternative considered:* the fallback rule infers "an error was reported" from the context, for example "a same-named source exists, so a mismatch must have been logged". Rejected because it duplicates every rule's error conditions, and it breaks silently when a new rule logs on a condition the fallback doesn't know about.

### D2. The fallback is a post-chain step in the resolver, not a new `MappingRule`
When the chain yields no strategy **and** the tracker counted zero errors:
1. target `hasDefault` → `Ignored(target)`, no diagnostic;
2. target is nullable without a default → `Constant(target, "null")`, plus `KRAFT-W009` unless suppressed (D4);
3. otherwise → report `KRAFT-E065` (D3) and return `null`.

When the chain yields no strategy and an error *was* counted, return `null` as today. The error is already in the log.

*Alternative considered:* a `MissingSourceRule` placed before `RequiredFieldErrorRule`. A rule can't see the error count without casting `ctx.logger` to the tracker type, and it would run inside `probeShadowedNative` too. As a post-chain step it only runs in `resolve`, so the W001 probe stays as it is. Its result would be `Ignored` or `Constant` anyway, both of which the probe already ignores.

`RequiredFieldErrorRule` stays unchanged and last in the chain, so E004 still fires before the fallback for required targets. Branch 3 is unreachable with the default chain. It exists for custom chains (`PropertyResolver(rules = …)` is public) and for future rules.

### D3. `KRAFT-E065` (Mapper dropped without a diagnostic)
This is a new error code, the next free one after E064. The message is the exact wording in the spec, and the reference entry goes on `errors.md`, which the build-time code-coverage test requires. It's reported on `target.declaration`. The scenario uses `PropertyResolver(rules = listOf(MappingRule { _, _ -> null }))` against a hand-built context, in `kraft-core` or `kraft-ksp` tests.

### D4. `KRAFT-W009` and where it can be suppressed
This is a new warning, the next free one after W008. Suppression is checked on, in order:
1. the target property's declaration (`PropertyInfo.declaration`);
2. its primary-constructor parameter. Kotlin puts `@Suppress` on a `val` parameter's *parameter*, not on the property, unless the user writes a use-site target, so both are checked. That needs the parameter, which `TargetPropertyExtractor` already has: add a nullable `parameter: KSValueParameter?` to `PropertyInfo`, defaulting to `null`;
3. the mapping declaration: add `mappingDeclaration: KSAnnotated? = null` to `MappingContext`. `ConfigDescriptorBuilder` and `ReverseDescriptorBuilder` set it to the `@MapConfig` object, and `ClassDescriptorBuilder` to the `@MapFrom` / `@MapTo` class.

These are three explicit `isKraftSuppressed(W009)` calls. Enclosing-scope matching is not generalised, which keeps the documented "only this declaration" contract of `isKraftSuppressed` true per call.

### D5. E004 message hint
Change "Or make the property nullable" to "Or make the property nullable (Kraft then passes null and warns KRAFT-W009)". Code and first line stay the same, as `diagnostics/diagnostic-codes` requires.

## Risks / Trade-offs

- [Hand-written workaround mappers now conflict with generated ones] → Upgrading-page entry with the exact symptom (conflicting overloads / redeclaration of `toXxx`) and the fix: delete the hand-written function.
- [A `@Suppress` on a constructor `val` lands somewhere KSP doesn't expose] → D4 checks both property and parameter, and the spec scenario "Suppressed on the target property" is the test. If KSP2 exposes neither, fall back to documenting `@property:Suppress` and adjust that scenario.
- [W009 is noisy for DTO codegen output (for example OpenAPI clients) with many nullable, non-defaulted fields] → Suppress once on the `@MapConfig` object. Roadmap #9 (unmapped-target policy) is the long-term knob.
- [Existing double error: type mismatch, then E004, on a required property] → unchanged. D1's tracker makes that cheap to fix later (skip E004 when the count is above 0). Left out to keep this `fix:` focused.
- [The tracker wraps the logger once per property] → it's one allocation per property per mapper at build time, which is negligible next to KSP resolution.

## Migration Plan

Ship as a `fix:` in the next 0.14 RC. Consumers need to act only if they hand-wrote a mapper the processor had been silently skipping (Upgrading page). Rollback is reverting the commit, since there's no persisted state or API surface to unwind.
