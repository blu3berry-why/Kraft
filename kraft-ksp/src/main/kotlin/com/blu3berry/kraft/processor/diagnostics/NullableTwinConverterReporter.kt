package com.blu3berry.kraft.processor.diagnostics

import com.google.devtools.ksp.processing.KSPLogger
import com.blu3berry.kraft.model.scan.ConverterEntry
import com.blu3berry.kraft.model.scan.ConverterTypeKey
import com.blu3berry.kraft.model.scan.GlobalConverterRegistry

/**
 * Emits `KRAFT-W002` for each same-module `(S?, T?)` `@KraftConverter` whose non-null
 * `(S, T)` twin is also registered, here or on the classpath. Kraft derives the safe call
 * `this.<property>?.<fn>()` from the twin, so the nullable converter adds nothing unless it
 * does more than a safe call. Reporting never changes registration or generated code.
 *
 * Classpath twins are looked up by name per type pair, silently; the classpath is never
 * enumerated. Only same-module nullable converters are reported, since an upstream one
 * cannot carry a suppression the user controls. [reported] guards against repeats when KSP
 * runs more than one round.
 */
class NullableTwinConverterReporter(private val logger: KSPLogger) {
    private val reported = mutableSetOf<ConverterTypeKey>()

    fun report(sameModule: GlobalConverterRegistry, merged: GlobalConverterRegistry) {
        val silent = merged.silent()
        sameModule.entries
            .filterKeys { it.sourceNullable && it.targetNullable }
            .forEach { (key, entry) -> reportIfTwinned(key, entry, sameModule, silent) }
    }

    private fun reportIfTwinned(
        key: ConverterTypeKey,
        entry: ConverterEntry,
        sameModule: GlobalConverterRegistry,
        silent: GlobalConverterRegistry
    ) {
        val nullableFn = (entry as? ConverterEntry.Real)?.function ?: return
        val twinKey = key.copy(sourceNullable = false, targetNullable = false)
        val twin = sameModule.entries[twinKey] ?: silent.lookup(twinKey) ?: return
        if (nullableFn.isKraftSuppressed(KraftDiagnostic.NULLABLE_TWIN_CONVERTER)) return
        if (!reported.add(key)) return

        logger.report(
            KraftDiagnostic.NULLABLE_TWIN_CONVERTER,
            nullableTwinMessage(
                nullableFn = entry.simpleName,
                nonNullFn = twin.simpleName,
                source = key.sourceFqName.substringAfterLast('.'),
                target = key.targetFqName.substringAfterLast('.'),
            ),
            nullableFn
        )
    }
}

/** The `KRAFT-W002` message body; [report] adds the code prefix, banner and docs link. */
internal fun nullableTwinMessage(nullableFn: String, nonNullFn: String, source: String, target: String): String =
    "@KraftConverter '$nullableFn' ($source? → $target?) only lifts '$nonNullFn' ($source → $target) through null.\n" +
        "\n" +
        "Kraft already generates 'this.<property>?.$nonNullFn()' for nullable $source? → $target? properties. " +
        "Remove '$nullableFn', or add @Suppress(\"KRAFT-W002\") if it does more than a safe call."
