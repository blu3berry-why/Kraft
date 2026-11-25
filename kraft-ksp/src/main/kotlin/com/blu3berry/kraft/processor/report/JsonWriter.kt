package com.blu3berry.kraft.processor.report

/**
 * A minimal, deterministic JSON writer for the `kraft.report` file: objects keep their
 * insertion order, every value goes on its own line with two-space indentation, and empty
 * arrays and objects stay on one line. It supports what the report needs (maps, lists,
 * strings, numbers, booleans and null), so the processor needs no JSON library.
 */
internal object JsonWriter {

    private const val INDENT = "  "
    private const val FIRST_PRINTABLE = 0x20

    /** An insertion-ordered JSON object. */
    fun obj(vararg pairs: Pair<String, Any?>): Map<String, Any?> = linkedMapOf(*pairs)

    fun render(value: Any?): String = StringBuilder().also { write(it, value, 0) }.append('\n').toString()

    private fun write(out: StringBuilder, value: Any?, depth: Int) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(out, value)
            is Number, is Boolean -> out.append(value.toString())
            is Map<*, *> -> writeObject(out, value, depth)
            is List<*> -> writeArray(out, value, depth)
            else -> error("Unsupported JSON value: ${value::class}")
        }
    }

    private fun writeObject(out: StringBuilder, map: Map<*, *>, depth: Int) {
        if (map.isEmpty()) {
            out.append("{}")
            return
        }
        out.append("{\n")
        map.entries.forEachIndexed { i, (key, value) ->
            out.append(INDENT.repeat(depth + 1))
            writeString(out, key.toString())
            out.append(": ")
            write(out, value, depth + 1)
            if (i < map.size - 1) out.append(',')
            out.append('\n')
        }
        out.append(INDENT.repeat(depth)).append('}')
    }

    private fun writeArray(out: StringBuilder, list: List<*>, depth: Int) {
        if (list.isEmpty()) {
            out.append("[]")
            return
        }
        out.append("[\n")
        list.forEachIndexed { i, value ->
            out.append(INDENT.repeat(depth + 1))
            write(out, value, depth + 1)
            if (i < list.size - 1) out.append(',')
            out.append('\n')
        }
        out.append(INDENT.repeat(depth)).append(']')
    }

    /** Writes [s] as a JSON string, escaping per RFC 8259. */
    private fun writeString(out: StringBuilder, s: String) {
        out.append('"')
        for (c in s) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c.code < FIRST_PRINTABLE -> out.append("\\u%04x".format(c.code))
                else -> out.append(c)
            }
        }
        out.append('"')
    }
}
