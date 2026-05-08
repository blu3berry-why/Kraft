package com.blu3berry.kraft.processor.sides

/**
 * Ant-style package-name glob.
 *
 * - `*`  matches exactly one segment (no dots).
 * - `**` matches zero or more segments.
 *
 * Match is case-sensitive. Compiled to a [Regex] for runtime evaluation.
 */
class PackageGlob private constructor(
    val raw: String,
    private val regex: Regex,
    private val tokens: List<Token>,
) {

    fun matches(fqn: String): Boolean = regex.matches(fqn)

    /**
     * True if every FQN matched by `this` is also matched by [other], and
     * `other` matches at least one FQN that `this` doesn't (i.e. `other`
     * is strictly broader). Conservative: returns false for any pair that
     * isn't structurally provable.
     */
    fun isStrictSubsetOf(other: PackageGlob): Boolean {
        if (this.raw == other.raw) return false
        return tokensAreSubset(this.tokens, other.tokens) &&
            !tokensAreSubset(other.tokens, this.tokens)
    }

    internal sealed interface Token {
        data class Literal(val value: String) : Token
        object SingleStar : Token
        object DoubleStar : Token
    }

    companion object {
        fun parse(pattern: String): PackageGlob {
            require(pattern.isNotEmpty()) { "Empty package pattern" }
            require(!pattern.contains("***")) {
                "Invalid glob pattern '$pattern': only `*` and `**` wildcards are allowed."
            }
            val tokens = tokenize(pattern)
            val regex = compileRegex(tokens)
            return PackageGlob(pattern, regex, tokens)
        }

        private fun tokenize(pattern: String): List<Token> = pattern
            .split('.')
            .map { segment ->
                when (segment) {
                    "**" -> Token.DoubleStar
                    "*" -> Token.SingleStar
                    else -> {
                        require(!segment.contains('*')) {
                            "Wildcards must be whole segments: '$pattern'"
                        }
                        Token.Literal(segment)
                    }
                }
            }

        private fun compileRegex(tokens: List<Token>): Regex {
            val sb = StringBuilder("^")
            tokens.forEachIndexed { i, token ->
                val isLast = i == tokens.lastIndex
                when (token) {
                    is Token.Literal -> {
                        if (i > 0 && tokens[i - 1] !is Token.DoubleStar) sb.append("\\.")
                        sb.append(Regex.escape(token.value))
                    }
                    Token.SingleStar -> {
                        if (i > 0 && tokens[i - 1] !is Token.DoubleStar) sb.append("\\.")
                        sb.append("[^.]+")
                    }
                    Token.DoubleStar -> {
                        if (i == 0) {
                            sb.append("(?:[^.]+(?:\\.[^.]+)*)?")
                            if (!isLast) sb.append("\\.?")
                        } else {
                            sb.append("(?:\\.[^.]+)*")
                            if (!isLast) sb.append("\\.?")
                        }
                    }
                }
            }
            sb.append("$")
            return Regex(sb.toString())
        }

        private fun tokensAreSubset(inner: List<Token>, outer: List<Token>): Boolean =
            tokensAreSubset(inner, 0, outer, 0)

        @Suppress("ReturnCount")
        private fun tokensAreSubset(
            inner: List<Token>, i: Int,
            outer: List<Token>, o: Int,
        ): Boolean {
            if (o == outer.size) return i == inner.size
            val ot = outer[o]
            if (ot is Token.DoubleStar) {
                for (consumed in i..inner.size) {
                    if (tokensAreSubset(inner, consumed, outer, o + 1)) return true
                }
                return false
            }
            if (i == inner.size) return false
            val it = inner[i]
            return when {
                it is Token.DoubleStar -> false
                ot is Token.SingleStar -> {
                    if (it is Token.SingleStar || it is Token.Literal) {
                        tokensAreSubset(inner, i + 1, outer, o + 1)
                    } else false
                }
                ot is Token.Literal -> {
                    if (it is Token.Literal && it.value == ot.value) {
                        tokensAreSubset(inner, i + 1, outer, o + 1)
                    } else false
                }
                else -> false
            }
        }
    }
}
