package com.blu3berry.kraft.processor.sides

import com.blu3berry.kraft.config.AliasEmitMode

/**
 * One registered side. The `slot` is the internal grouping key from the
 * KSP option `kraft.side.<slot>.<field>`; `name` is the user-visible label
 * substituted into the template's `{side}` variable.
 *
 * `emitMode` here is the project-level default for this side. The per-mapper
 * `aliasEmitMode = INHERIT` resolves to this value at codegen time.
 */
data class SideConfig(
    val slot: String,
    val name: String,
    val packagePattern: PackageGlob,
    val template: AliasTemplate,
    val emitMode: AliasEmitMode,
)

/**
 * One alias function a generator emitted: `fun <receiver>.<alias>(): <target>`, produced for
 * [side] by [mapper]. Reported in the `kraft.report` file.
 */
data class EmittedAlias(
    val receiver: String,
    val alias: String,
    val target: String?,
    val side: String?,
    val mapper: String,
)
