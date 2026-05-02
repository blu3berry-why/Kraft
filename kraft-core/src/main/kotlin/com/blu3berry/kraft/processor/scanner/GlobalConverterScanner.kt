package com.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.validate
import com.blu3berry.kraft.model.scan.ConverterTypeKey
import com.blu3berry.kraft.model.scan.GlobalConverterRegistry
import com.blu3berry.kraft.processor.util.KraftKspConstants

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

        val entries = mutableMapOf<ConverterTypeKey, KSFunctionDeclaration>()

        for (fn in symbols) {
            registerIfValid(fn, entries)
        }

        return GlobalConverterRegistry(entries.toMap())
    }

    private fun registerIfValid(
        fn: KSFunctionDeclaration,
        entries: MutableMap<ConverterTypeKey, KSFunctionDeclaration>
    ) {
        if (!validateFunction(fn)) return

        val receiverType = fn.extensionReceiver!!.resolve()
        val returnType = fn.returnType?.resolve() ?: return
        val key = buildKey(receiverType, returnType) ?: return

        val existing = entries[key]
        if (existing != null) {
            reportAmbiguity(key, existing, fn)
            return
        }
        entries[key] = fn
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

    private fun buildKey(sourceType: KSType, targetType: KSType): ConverterTypeKey? {
        val sourceDecl = sourceType.declaration as? KSClassDeclaration ?: return null
        val targetDecl = targetType.declaration as? KSClassDeclaration ?: return null
        val sourceFq = sourceDecl.qualifiedName?.asString() ?: return null
        val targetFq = targetDecl.qualifiedName?.asString() ?: return null
        return ConverterTypeKey(
            sourceFqName = sourceFq,
            sourceNullable = sourceType.nullability == Nullability.NULLABLE,
            targetFqName = targetFq,
            targetNullable = targetType.nullability == Nullability.NULLABLE
        )
    }

    private fun nullSuffix(nullable: Boolean) = if (nullable) "?" else ""
}
