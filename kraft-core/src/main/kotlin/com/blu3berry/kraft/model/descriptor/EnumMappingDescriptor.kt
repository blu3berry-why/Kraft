package com.blu3berry.kraft.model.descriptor

import com.google.devtools.ksp.symbol.KSFile
import com.blu3berry.kraft.model.TypeInfo

/**
 * Describes an enum-to-enum mapping declared via `@MapEnum` or a `@FieldOverride`
 * inside `@MapConfig`.
 *
 * @param sourceType       The source enum type.
 * @param targetType       The target enum type.
 * @param entries          Per-entry name mappings (source constant → target constant).
 * @param declarationFile  Containing file of the `@MapEnum`-annotated declaration,
 *                         when this descriptor was built from one. Forwarded into
 *                         KSP `Dependencies` so generated artifacts (the enum mapper
 *                         itself and its synthetic registry trampoline) re-run when
 *                         the `@MapEnum` arguments or `fieldMappings` change, even
 *                         if neither enum source/target type file was edited.
 */
data class EnumMappingDescriptor(
    val sourceType: TypeInfo,
    val targetType: TypeInfo,
    val entries: List<EnumEntryMapping>,
    val declarationFile: KSFile? = null,
)
