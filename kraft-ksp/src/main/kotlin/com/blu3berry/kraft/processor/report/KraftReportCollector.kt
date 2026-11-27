package com.blu3berry.kraft.processor.report

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.blu3berry.kraft.model.descriptor.EnumMappingDescriptor
import com.blu3berry.kraft.model.scan.ConverterEntry
import com.blu3berry.kraft.model.scan.ConverterOrigin
import com.blu3berry.kraft.model.scan.ConverterTypeKey
import com.blu3berry.kraft.processor.sides.EmittedAlias
import com.blu3berry.kraft.processor.sides.SideConfig

/**
 * Accumulates what the processor discovered across KSP rounds and writes it once, as
 * `kraft/report.json` in the compilation's KSP resources output (design D2/D3).
 *
 * Every `add*` call is idempotent per key, so a later round re-reporting the same converter,
 * enum pair, side or alias changes nothing.
 */
class KraftReportCollector(private val moduleId: String?) {

    private val converters = sortedMapOf<Triple<String, String, String>, ReportConverter>(tripleOrder())
    private val enumPairs = sortedMapOf<Pair<String, String>, ReportEnumPair>(pairOrder())
    private val sides = sortedMapOf<String, ReportSide>()
    private val aliases = sortedMapOf<Pair<String, String>, ReportAlias>(pairOrder())
    private var written = false

    /** Records each converter in [entries]. [moduleIdOf] names a classpath delegate's producing module. */
    fun addConverters(
        entries: Map<ConverterTypeKey, ConverterEntry>,
        moduleIdOf: (ConverterEntry.Real) -> String?
    ) {
        for ((key, entry) in entries) {
            val isDelegate = entry.origin == ConverterOrigin.CLASSPATH_DELEGATE
            val converter = ReportConverter(
                source = key.sourceRendering,
                target = key.targetRendering,
                function = entry.callFqName,
                origin = entry.origin.label,
                module = (entry as? ConverterEntry.Real)
                    ?.takeIf { isDelegate }
                    ?.let(moduleIdOf)
                    ?.takeIf { it.isNotBlank() },
                isClasspathDelegate = isDelegate,
            )
            converters.putIfAbsent(Triple(converter.source, converter.target, converter.function), converter)
        }
    }

    /**
     * Records each enum mapper in [generated]. A pair whose source and target match one of
     * [declared] is `declared`; any other pair was derived automatically.
     */
    fun addEnumPairs(
        declared: List<EnumMappingDescriptor>,
        generated: Map<ConverterTypeKey, ConverterEntry.Synthetic>
    ) {
        val declaredBy = declared.associateBy { it.sourceType.qualifiedName to it.targetType.qualifiedName }
        for ((key, entry) in generated) {
            val types = key.sourceFqName to key.targetFqName
            val declaration = declaredBy[types]
            enumPairs.putIfAbsent(
                key.sourceRendering to key.targetRendering,
                ReportEnumPair(
                    source = key.sourceRendering,
                    target = key.targetRendering,
                    function = entry.callFqName,
                    derivation = if (declaration != null) "declared" else "auto",
                    declaredBy = declaration?.declaration?.qualifiedName?.asString(),
                )
            )
        }
    }

    fun addSides(configured: List<SideConfig>) {
        for (side in configured) {
            sides.putIfAbsent(
                side.slot,
                ReportSide(
                    slot = side.slot,
                    name = side.name,
                    packagePattern = side.packagePattern.raw,
                    template = side.template.raw,
                    emitMode = side.emitMode.name,
                )
            )
        }
    }

    fun addAliases(emitted: List<EmittedAlias>) {
        for (alias in emitted) {
            aliases.putIfAbsent(
                alias.receiver to alias.alias,
                ReportAlias(alias.receiver, alias.alias, alias.target, alias.side, alias.mapper)
            )
        }
    }

    fun build(): KraftReport = KraftReport(
        moduleId = moduleId,
        converters = converters.values.toList(),
        enumPairs = enumPairs.values.toList(),
        sides = sides.values.toList(),
        sideAliases = aliases.values.toList(),
    )

    /**
     * Writes the report through [codeGenerator] unless it was already written. The file depends
     * on every source file, so any change re-runs it.
     */
    fun writeOnce(codeGenerator: CodeGenerator) {
        if (written) return
        written = true
        codeGenerator.createNewFileByPath(Dependencies.ALL_FILES, REPORT_PATH, REPORT_EXTENSION)
            .bufferedWriter(Charsets.UTF_8)
            .use { it.write(build().render()) }
    }

    private companion object {
        const val REPORT_PATH = "kraft/report"
        const val REPORT_EXTENSION = "json"

        fun tripleOrder() = compareBy<Triple<String, String, String>>({ it.first }, { it.second }, { it.third })
        fun pairOrder() = compareBy<Pair<String, String>>({ it.first }, { it.second })
    }
}
