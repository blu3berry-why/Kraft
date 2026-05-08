package com.blu3berry.kraft.processor.sides

/**
 * Compiled template for a side's alias function name.
 *
 * Recognised variables (substituted verbatim, no auto-casing):
 *  - `{side}`   the side's `name` value
 *  - `{target}` simple name of the target class
 *  - `{source}` simple name of the source class
 *
 * Unknown `{...}` references and invalid Kotlin identifiers fail at [parse] time.
 * The rendered output is re-validated as a Kotlin identifier per render so
 * malformed substitutions (e.g. side name with a leading digit) fail loudly too.
 */
class AliasTemplate private constructor(
    val raw: String,
    private val parts: List<Part>,
) {

    fun render(side: String, source: String, target: String): String {
        require(isValidKotlinIdentifier(side)) {
            "Side value '$side' is not a valid Kotlin identifier segment."
        }
        require(isValidKotlinIdentifier(source)) {
            "Source value '$source' is not a valid Kotlin identifier segment."
        }
        require(isValidKotlinIdentifier(target)) {
            "Target value '$target' is not a valid Kotlin identifier segment."
        }
        val sb = StringBuilder()
        for (part in parts) {
            sb.append(
                when (part) {
                    is Part.Literal -> part.value
                    Part.Side -> side
                    Part.Source -> source
                    Part.Target -> target
                }
            )
        }
        val result = sb.toString()
        require(isValidKotlinIdentifier(result)) {
            "Alias template '$raw' rendered '$result', which is not a valid Kotlin identifier."
        }
        return result
    }

    private sealed interface Part {
        data class Literal(val value: String) : Part
        object Side : Part
        object Source : Part
        object Target : Part
    }

    companion object {
        private val ALLOWED = setOf("side", "source", "target")

        fun parse(raw: String): AliasTemplate {
            require(raw.isNotEmpty()) { "Alias template must not be empty." }
            val parts = mutableListOf<Part>()
            val literal = StringBuilder()
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c == '{') {
                    val end = raw.indexOf('}', i + 1)
                    require(end > 0) { "Unterminated `{` in alias template '$raw'." }
                    if (literal.isNotEmpty()) {
                        parts += Part.Literal(literal.toString())
                        literal.clear()
                    }
                    val name = raw.substring(i + 1, end)
                    require(name in ALLOWED) {
                        "Unknown variable '{$name}' in alias template '$raw'. Allowed: ${ALLOWED.sorted()}."
                    }
                    parts += when (name) {
                        "side" -> Part.Side
                        "source" -> Part.Source
                        "target" -> Part.Target
                        else -> error("unreachable")
                    }
                    i = end + 1
                } else {
                    literal.append(c)
                    i++
                }
            }
            if (literal.isNotEmpty()) parts += Part.Literal(literal.toString())

            // Eager identifier check on a "best effort" rendering — substitute every
            // variable with a known-good placeholder. Catches things like a leading
            // digit in the literal portion of the template.
            val probe = AliasTemplate(raw, parts).render(side = "X", source = "X", target = "X")
            require(isValidKotlinIdentifier(probe)) {
                "Alias template '$raw' produces '$probe' for sample input, which is not a valid Kotlin identifier."
            }
            return AliasTemplate(raw, parts)
        }

        private fun isValidKotlinIdentifier(s: String): Boolean {
            if (s.isEmpty()) return false
            if (!s[0].isJavaIdentifierStart()) return false
            for (i in 1 until s.length) if (!s[i].isJavaIdentifierPart()) return false
            return true
        }
    }
}
