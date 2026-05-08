package com.blu3berry.kraft.config

/**
 * Controls whether Kraft emits the short side-alias extension function
 * alongside the verbose `to<Target>From<Source>()` mapper.
 *
 * - [INHERIT] (default): use the project-level `emitMode` set on the matched side
 *   in `build.gradle.kts` (`kraft.side.<slot>.emitMode`).
 * - [BOTH]: emit both the verbose function AND the alias for this mapper.
 * - [FULL_NAME_ONLY]: emit only the verbose function — no alias for this mapper.
 *
 * Used as the value of [MapConfig.aliasEmitMode].
 */
enum class AliasEmitMode { INHERIT, BOTH, FULL_NAME_ONLY }
