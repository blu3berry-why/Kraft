package com.blu3berry.kraft.processor.codegen.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.blu3berry.kraft.model.descriptor.ContainerKind
import com.blu3berry.kraft.model.descriptor.ContainerShape
import com.blu3berry.kraft.model.descriptor.ConverterDescriptor
import com.blu3berry.kraft.model.descriptor.ElementOp
import com.blu3berry.kraft.model.descriptor.NestedMappingDescriptor
import com.blu3berry.kraft.processor.codegen.className

/**
 * Renders the expression for a [com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy.ContainerMapping]
 * from its [ContainerShape]: `this.items.map { it.toItemDto() }.toSet()`.
 *
 * Holds the per-kind tables the generated code depends on: the conversion suffix after
 * `map { }` ([mappedLinearSuffix]) and the empty fallback for a nullable source ([emptyValue]).
 *
 * @param nestedMapperMember Resolves the generated nested (or enum) mapper a leaf calls.
 * @param converterMember    Resolves the top-level converter function a leaf calls.
 */
internal class ContainerShapeRenderer(
    private val nestedMapperMember: (NestedMappingDescriptor) -> MemberName,
    private val converterMember: (ConverterDescriptor) -> MemberName
) {

    /** Renders [shape] applied to [receiver] (e.g. `this.items`). */
    fun render(receiver: CodeBlock, shape: ContainerShape): CodeBlock = renderShape(receiver, shape, depth = 0)

    /**
     * Renders [shape] applied to [receiver], one container level per recursion step.
     *
     * A [ContainerShape.Linear] level becomes `<receiver>.map { … }` (`mapNotNull` when null
     * elements are dropped), then the conversion suffix for the target kind, safe-called with
     * `?.` and given an `?: <empty>` fallback when the source level is nullable. The innermost
     * lambda uses `it`; an enclosing lambda names its parameter `e<depth>` so no parameter is
     * shadowed.
     */
    private fun renderShape(receiver: CodeBlock, shape: ContainerShape, depth: Int): CodeBlock = when (shape) {
        is ContainerShape.Leaf -> renderLeaf(receiver, shape)
        is ContainerShape.Linear -> renderLinear(receiver, shape, depth)
        is ContainerShape.Keyed -> renderKeyed(receiver, shape, depth)
    }

    /**
     * A map level: `<receiver>.mapValues { … }` when only the values map, `mapKeys { … }` when only
     * the keys map, and `entries.associate { <key> to <value> }` when both do. Then `.toMutableMap()`
     * for a `MutableMap` target, safe-called with `?.` and given an `?: <empty>` fallback when the
     * source is nullable. Identical keys and values need only the copy (`toMutableMap()`). An entry
     * is read as `it` in the innermost lambda and `e<depth>` in an enclosing one.
     */
    private fun renderKeyed(receiver: CodeBlock, shape: ContainerShape.Keyed, depth: Int): CodeBlock {
        val value = shape.value
        val dot = if (shape.sourceNullable) "?." else "."
        val fallback = if (shape.sourceNullable && !shape.targetNullable) " ?: ${emptyValue(shape.targetKind)}" else ""
        val toMutable = shape.targetKind == ContainerKind.MUTABLE_MAP
        val keyIdentity = shape.key.op is ElementOp.Identity
        val valueIdentity = value is ContainerShape.Leaf && value.op is ElementOp.Identity
        if (keyIdentity && valueIdentity) {
            val copy = if (toMutable) "toMutableMap()" else "toMap()"
            return CodeBlock.of("%L%L%L%L", receiver, dot, copy, fallback)
        }
        val suffix = if (toMutable) (if (shape.sourceNullable) "?.toMutableMap()" else ".toMutableMap()") else ""
        val call = when {
            keyIdentity -> CodeBlock.of(
                "%L%LmapValues %L", receiver, dot, entryLambda(value, depth) { renderValue(it, value, depth) }
            )
            valueIdentity -> CodeBlock.of(
                "%L%LmapKeys { %L }", receiver, dot, renderLeaf(CodeBlock.of("it.key"), shape.key)
            )
            else -> CodeBlock.of(
                "%L%Lentries%Lassociate %L", receiver, dot, dot,
                entryLambda(value, depth) { entry ->
                    CodeBlock.of(
                        "%L to %L",
                        renderLeaf(CodeBlock.of("%L.key", entry), shape.key),
                        renderValue(entry, value, depth)
                    )
                }
            )
        }
        return CodeBlock.of("%L%L%L", call, suffix, fallback)
    }

    /**
     * The `{ … }` lambda over one map entry: `it` when [value] is a leaf (nothing nests inside),
     * else an `e<depth>` parameter so the inner lambdas can use `it`.
     */
    private fun entryLambda(value: ContainerShape, depth: Int, body: (CodeBlock) -> CodeBlock): CodeBlock =
        if (value is ContainerShape.Leaf) {
            CodeBlock.of("{ %L }", body(CodeBlock.of("it")))
        } else {
            val param = CodeBlock.of("e%L", depth)
            CodeBlock.of("{ %L -> %L }", param, body(param))
        }

    /** The mapped value of [entry]: the leaf call on `<entry>.value`, or the nested container level. */
    private fun renderValue(entry: CodeBlock, value: ContainerShape, depth: Int): CodeBlock =
        if (value is ContainerShape.Leaf) {
            renderLeaf(CodeBlock.of("%L.value", entry), value)
        } else {
            renderShape(CodeBlock.of("%L.value", entry), value, depth + 1)
        }

    private fun renderLinear(receiver: CodeBlock, shape: ContainerShape.Linear, depth: Int): CodeBlock {
        val element = shape.element
        val dot = if (shape.sourceNullable) "?." else "."
        val fallback = if (shape.sourceNullable && !shape.targetNullable) " ?: ${emptyValue(shape.targetKind)}" else ""
        if (element is ContainerShape.Leaf && element.op is ElementOp.Identity) {
            // Same elements, different collection type: only the conversion call, no `map { }`.
            val conversion = identityConversion(shape.sourceKind, shape.targetKind)
            return CodeBlock.of("%L%L%L%L", receiver, dot, conversion, fallback)
        }
        val lambda = if (element is ContainerShape.Leaf) {
            CodeBlock.of("{ %L }", renderLeaf(CodeBlock.of("it"), element))
        } else {
            val param = "e$depth"
            CodeBlock.of("{ %L -> %L }", param, renderShape(CodeBlock.of(param), element, depth + 1))
        }
        val dropsNulls = element is ContainerShape.Leaf && element.sourceNullable && !element.targetNullable
        val mapFn = if (dropsNulls) "mapNotNull" else "map"
        val suffix = mappedLinearSuffix(shape.targetKind)
            .let { if (it.isNotEmpty() && shape.sourceNullable) "?$it" else it }
        return CodeBlock.of("%L%L%L %L%L%L", receiver, dot, mapFn, lambda, suffix, fallback)
    }

    private fun renderLeaf(receiver: CodeBlock, leaf: ContainerShape.Leaf): CodeBlock {
        val dot = if (leaf.sourceNullable) "?." else "."
        return when (val op = leaf.op) {
            is ElementOp.Identity -> receiver
            is ElementOp.Nested -> CodeBlock.of("%L%L%M()", receiver, dot, nestedMapperMember(op.descriptor))
            // %L, not %N: KotlinPoet backtick-escapes the soft keyword `value`.
            is ElementOp.Unwrap -> CodeBlock.of("%L%L%L", receiver, dot, op.propertyName)
            is ElementOp.Wrap -> if (leaf.sourceNullable) {
                CodeBlock.of("%L?.let { %T(it) }", receiver, op.valueClass.className)
            } else {
                CodeBlock.of("%T(%L)", op.valueClass.className, receiver)
            }
            is ElementOp.Converter -> if (op.converter.isExtension) {
                CodeBlock.of("%L%L%M()", receiver, dot, converterMember(op.converter))
            } else {
                CodeBlock.of("%M(%L)", converterMember(op.converter), receiver)
            }
        }
    }

    /** The call that turns the `List` produced by `map { }` into the target kind. */
    private fun mappedLinearSuffix(targetKind: ContainerKind): String = when (targetKind) {
        ContainerKind.LIST, ContainerKind.COLLECTION, ContainerKind.ITERABLE -> ""
        ContainerKind.SET -> ".toSet()"
        ContainerKind.ARRAY -> ".toTypedArray()"
        ContainerKind.MUTABLE_LIST, ContainerKind.MUTABLE_COLLECTION -> ".toMutableList()"
        ContainerKind.MUTABLE_SET -> ".toMutableSet()"
        ContainerKind.MAP, ContainerKind.MUTABLE_MAP -> error("$targetKind is not a single-element container")
    }

    /**
     * The call that converts a source container whose elements are kept as is into the target
     * kind. A target that is a supertype of the source never gets here (it is a plain assignment),
     * and a `Set` target only ever comes from a `Set`, so these are the real copies. Only a
     * `Collection` can become an `Array` directly; an `Iterable` goes through a `List` first.
     */
    private fun identityConversion(sourceKind: ContainerKind, targetKind: ContainerKind): String = when (targetKind) {
        ContainerKind.LIST, ContainerKind.COLLECTION, ContainerKind.ITERABLE -> "toList()"
        ContainerKind.SET -> "toSet()"
        ContainerKind.ARRAY ->
            if (sourceKind == ContainerKind.ITERABLE) "toList().toTypedArray()" else "toTypedArray()"
        ContainerKind.MUTABLE_LIST, ContainerKind.MUTABLE_COLLECTION -> "toMutableList()"
        ContainerKind.MUTABLE_SET -> "toMutableSet()"
        ContainerKind.MAP, ContainerKind.MUTABLE_MAP -> error("$targetKind is not a single-element container")
    }

    /** The empty value a nullable source falls back to for a non-null target of [kind]. */
    private fun emptyValue(kind: ContainerKind): String = when (kind) {
        ContainerKind.LIST, ContainerKind.COLLECTION, ContainerKind.ITERABLE -> "emptyList()"
        ContainerKind.MUTABLE_LIST, ContainerKind.MUTABLE_COLLECTION -> "mutableListOf()"
        ContainerKind.SET -> "emptySet()"
        ContainerKind.MUTABLE_SET -> "mutableSetOf()"
        ContainerKind.ARRAY -> "emptyArray()"
        ContainerKind.MAP -> "emptyMap()"
        ContainerKind.MUTABLE_MAP -> "mutableMapOf()"
    }
}
