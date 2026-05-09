package com.blu3berry.kraft.model.descriptor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.blu3berry.kraft.config.AliasEmitMode
import com.blu3berry.kraft.model.TypeInfo

/**
 * Describes an enum-to-enum mapping declared via `@MapEnum` or a `@FieldOverride`
 * inside `@MapConfig`.
 *
 * @param sourceType    The source enum type.
 * @param targetType    The target enum type.
 * @param entries       Per-entry name mappings (source constant → target constant).
 * @param declaration   The `@MapEnum`-annotated declaration this descriptor was built
 *                      from, when applicable. Used both as a KSP source-location
 *                      anchor for diagnostics (e.g. duplicate `@MapEnum` pairs) and
 *                      to forward `containingFile` into KSP `Dependencies` so
 *                      generated artifacts (the enum mapper itself and its
 *                      synthetic registry trampoline) re-run when the `@MapEnum`
 *                      arguments or `fieldMappings` change, even if neither enum
 *                      source/target type file was edited.
 * @param aliasEmitMode Per-mapper override for alias emission; mirrors
 *                      [com.blu3berry.kraft.config.MapEnum.aliasEmitMode]. Auto-derived
 *                      enum mappers (no `@MapEnum` declaration) default to
 *                      [AliasEmitMode.INHERIT].
 */
data class EnumMappingDescriptor(
    val sourceType: TypeInfo,
    val targetType: TypeInfo,
    val entries: List<EnumEntryMapping>,
    val declaration: KSClassDeclaration? = null,
    val aliasEmitMode: AliasEmitMode = AliasEmitMode.INHERIT,
)
