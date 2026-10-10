package com.smartsiddur.logic

import java.util.concurrent.ConcurrentHashMap

/**
 * A boolean expression over day flags, used by the "when" field of prayer sections and items.
 *
 * Grammar: `|` (or) binds loosest, then `&` (and), then `!` (not); parentheses group.
 * Flags are lower_snake_case names, e.g. `rosh_chodesh | (tachanun & day_2)`.
 */
sealed interface Condition {
    fun evaluate(flags: Set<String>): Boolean

    /** Every flag name the expression refers to. */
    fun flagNames(): Set<String>

    data class Flag(val name: String) : Condition {
        override fun evaluate(flags: Set<String>) = name in flags
        override fun flagNames() = setOf(name)
    }

    data class Not(val inner: Condition) : Condition {
        override fun evaluate(flags: Set<String>) = !inner.evaluate(flags)
        override fun flagNames() = inner.flagNames()
    }

    data class And(val left: Condition, val right: Condition) : Condition {
        override fun evaluate(flags: Set<String>) = left.evaluate(flags) && right.evaluate(flags)
        override fun flagNames() = left.flagNames() + right.flagNames()
    }

    data class Or(val left: Condition, val right: Condition) : Condition {
        override fun evaluate(flags: Set<String>) = left.evaluate(flags) || right.evaluate(flags)
        override fun flagNames() = left.flagNames() + right.flagNames()
    }

    companion object {
        private val cache = ConcurrentHashMap<String, Condition>()

        /** Parses an expression; throws [IllegalArgumentException] if it is malformed. */
        fun parse(expression: String): Condition =
            cache.getOrPut(expression) { Parser(expression).parse() }

        /** True when [expression] is null/blank or evaluates to true. */
        fun isSatisfied(expression: String?, flags: Set<String>): Boolean =
            expression.isNullOrBlank() || parse(expression).evaluate(flags)
    }
}

private class Parser(private val src: String) {
    private var pos = 0

    fun parse(): Condition {
        val result = parseOr()
        skipSpaces()
        require(pos == src.length) { "Unexpected '${src[pos]}' at $pos in \"$src\"" }
        return result
    }

    private fun parseOr(): Condition {
        var left = parseAnd()
        while (consume('|')) left = Condition.Or(left, parseAnd())
        return left
    }

    private fun parseAnd(): Condition {
        var left = parseUnary()
        while (consume('&')) left = Condition.And(left, parseUnary())
        return left
    }

    private fun parseUnary(): Condition = when {
        consume('!') -> Condition.Not(parseUnary())
        consume('(') -> parseOr().also {
            require(consume(')')) { "Missing ')' at $pos in \"$src\"" }
        }
        else -> parseFlag()
    }

    private fun parseFlag(): Condition {
        skipSpaces()
        val start = pos
        while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '_')) pos++
        require(pos > start) { "Expected a flag name at $start in \"$src\"" }
        return Condition.Flag(src.substring(start, pos))
    }

    private fun consume(c: Char): Boolean {
        skipSpaces()
        if (pos < src.length && src[pos] == c) {
            pos++
            return true
        }
        return false
    }

    private fun skipSpaces() {
        while (pos < src.length && src[pos].isWhitespace()) pos++
    }
}
