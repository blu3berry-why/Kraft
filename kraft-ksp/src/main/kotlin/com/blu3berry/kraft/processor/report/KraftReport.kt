package com.blu3berry.kraft.processor.report

/**
 * The `kraft.report` file's content: what the processor discovered in one compilation.
 * Rendered by [render] as deterministic JSON. See `docs/user-guide/processor-report.md`.
 *
 * New sections are added as new top-level keys (for example the per-mapper coverage report of
 * roadmap #34) without changing the existing ones. [SCHEMA_VERSION] only goes up when an
 * existing key changes meaning or shape.
 */
data class KraftReport(
    val moduleId: String?,
    val converters: List<ReportConverter>,
    val enumPairs: List<ReportEnumPair>,
    val sides: List<ReportSide>,
    val sideAliases: List<ReportAlias>,
) {
    /** Renders the report as two-space-indented JSON with a trailing newline. */
    fun render(): String = JsonWriter.render(
        JsonWriter.obj(
            "schemaVersion" to SCHEMA_VERSION,
            "moduleId" to moduleId,
            "converters" to converters.map { it.toJson() },
            "enumPairs" to enumPairs.map { it.toJson() },
            "sides" to sides.map { it.toJson() },
            "sideAliases" to sideAliases.map { it.toJson() },
        )
    )

    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/** One converter the generated code can call. [module] is set for classpath delegates only. */
data class ReportConverter(
    val source: String,
    val target: String,
    val function: String,
    val origin: String,
    val module: String?,
    val isClasspathDelegate: Boolean,
) {
    internal fun toJson(): Map<String, Any?> = buildMap {
        put("source", source)
        put("target", target)
        put("function", function)
        put("origin", origin)
        if (isClasspathDelegate) put("module", module)
    }
}

/** One enum mapping. [declaredBy] is the `@MapEnum` declaration, null for an auto-derived pair. */
data class ReportEnumPair(
    val source: String,
    val target: String,
    val function: String,
    val derivation: String,
    val declaredBy: String?,
) {
    internal fun toJson(): Map<String, Any?> = buildMap {
        put("source", source)
        put("target", target)
        put("function", function)
        put("derivation", derivation)
        if (declaredBy != null) put("declaredBy", declaredBy)
    }
}

/** One configured side. */
data class ReportSide(
    val slot: String,
    val name: String,
    val packagePattern: String,
    val template: String,
    val emitMode: String,
) {
    internal fun toJson(): Map<String, Any?> = JsonWriter.obj(
        "slot" to slot,
        "name" to name,
        "packagePattern" to packagePattern,
        "template" to template,
        "emitMode" to emitMode,
    )
}

/** One emitted alias function: `fun <receiver>.<alias>(): <target>`. */
data class ReportAlias(
    val receiver: String,
    val alias: String,
    val target: String?,
    val side: String?,
    val mapper: String,
) {
    internal fun toJson(): Map<String, Any?> = JsonWriter.obj(
        "receiver" to receiver,
        "alias" to alias,
        "target" to target,
        "side" to side,
        "mapper" to mapper,
    )
}
