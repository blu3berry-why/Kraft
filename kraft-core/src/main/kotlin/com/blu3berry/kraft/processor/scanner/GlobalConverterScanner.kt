package com.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.validate
import com.blu3berry.kraft.model.scan.ConverterEntry
import com.blu3berry.kraft.model.scan.ConverterTypeKey
import com.blu3berry.kraft.model.scan.GlobalConverterRegistry
import com.blu3berry.kraft.processor.util.KraftKspConstants
import com.blu3berry.kraft.processor.util.buildConverterTypeKey

/**
 * Scans for top-level extension functions annotated with
 * [@KraftConverter][com.blu3berry.kraft.config.KraftConverter] and returns a
 * [GlobalConverterRegistry] indexed by `(sourceType, targetType)`.
 *
 * Validation rules enforced here:
 * - Symbol must be a `KSFunctionDeclaration`.
 * - Function must declare an extension receiver (the source type) and zero value
 *   parameters; this matches the `@MapUsing` extension form and lets codegen call
 *   the converter as `source.fn()`.
 * - Receiver and return type must both resolve to a `KSClassDeclaration`.
 * - Two converters registering the same `(srcFqName, srcNullable, tgtFqName,
 *   tgtNullable)` pair produce a KSP error pointing at both candidates; only the
 *   first is kept in the registry to allow processing to continue.
 */
class GlobalConverterScanner(
    private val resolver: Resolver,
    private val logger: KSPLogger
) {
    fun scan(): GlobalConverterRegistry {
        val symbols = resolver
            .getSymbolsWithAnnotation(KraftKspConstants.FQ_KRAFT_CONVERTER)
            .filter { it.validate() }
            .filterIsInstance<KSFunctionDeclaration>()
            .toList()

        if (symbols.isEmpty()) return GlobalConverterRegistry.EMPTY

        val entries = mutableMapOf<ConverterTypeKey, ConverterEntry>()

        for (fn in symbols) {
            registerIfValid(fn, entries)
        }

        return GlobalConverterRegistry(entries.toMap())
    }

    private fun registerIfValid(
        fn: KSFunctionDeclaration,
        entries: MutableMap<ConverterTypeKey, ConverterEntry>
    ) {
        if (!validateFunction(fn)) return

        val receiverType = fn.extensionReceiver!!.resolve()
        val returnType = fn.returnType?.resolve() ?: return

        if (receiverType.arguments.isNotEmpty() || returnType.arguments.isNotEmpty()) {
            logger.error(
                "@KraftConverter on '${fn.simpleName.asString()}' has a parameterized receiver or " +
                    "return type. Converters are registered by their raw (source → target) class " +
                    "pair, so type arguments cannot be matched — even concrete ones like " +
                    "List<Int> → String. How to fix: List/Set properties whose element " +
                    "types map are already converted element-wise without a @KraftConverter; for " +
                    "any other parameterized conversion use a per-property @MapUsing on the mapper.",
                fn
            )
            return
        }

        val key = buildConverterTypeKey(receiverType, returnType) ?: return

        val existing = entries[key]
        if (existing != null) {
            // Same-module @KraftConverter scan only registers Real entries; an
            // existing Synthetic (from @MapEnum) cannot land here yet, so the
            // cast is safe — but be explicit to keep the ambiguity reporter's
            // signature focused.
            if (existing is ConverterEntry.Real) reportAmbiguity(key, existing.function, fn)
            return
        }
        entries[key] = ConverterEntry.Real(fn)
    }

    private fun reportAmbiguity(
        key: ConverterTypeKey,
        existing: KSFunctionDeclaration,
        duplicate: KSFunctionDeclaration
    ) {
        val source = "${key.sourceFqName}${nullSuffix(key.sourceNullable)}"
        val target = "${key.targetFqName}${nullSuffix(key.targetNullable)}"
        val existingName = existing.qualifiedName?.asString() ?: existing.simpleName.asString()
        val duplicateName = duplicate.qualifiedName?.asString() ?: duplicate.simpleName.asString()
        logger.error(
            "Ambiguous @KraftConverter for ($source → $target): " +
                "already registered by '$existingName', now also '$duplicateName'. " +
                "Remove one or wrap the alternative call site with @MapUsing.",
            duplicate
        )
    }

    private fun validateFunction(fn: KSFunctionDeclaration): Boolean {
        if (fn.functionKind != FunctionKind.TOP_LEVEL) {
            logger.error(
                "@KraftConverter must be applied to a top-level extension function. " +
                    "'${fn.simpleName.asString()}' is declared at " +
                    "${fn.functionKind.name.lowercase()} scope.",
                fn
            )
            return false
        }
        if (fn.extensionReceiver == null) {
            logger.error(
                "@KraftConverter function '${fn.simpleName.asString()}' must be an extension function. " +
                    "The receiver type is the source type of the conversion.",
                fn
            )
            return false
        }
        if (fn.parameters.isNotEmpty()) {
            logger.error(
                "@KraftConverter extension function '${fn.simpleName.asString()}' must not declare " +
                    "any value parameters — only the receiver is used as input.",
                fn
            )
            return false
        }
        if (fn.returnType == null) {
            logger.error(
                "@KraftConverter function '${fn.simpleName.asString()}' must declare a return type.",
                fn
            )
            return false
        }
        return true
    }

    private fun nullSuffix(nullable: Boolean) = if (nullable) "?" else ""
}
