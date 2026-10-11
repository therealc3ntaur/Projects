package com.smartsiddur.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

private val TAG = Regex("""<(/?)(b|small|big|if)(?:\s+when="([^"]*)")?>""")

/**
 * Converts the prayer-text markup into an AnnotatedString.
 *
 * - `<b>`, `<small>`, `<big>`: styling. Sizes are relative (em), so pinch-zooming the base
 *   font size scales everything.
 * - `<if when="expr">…</if>`: an option embedded in the line (e.g. "לר״ח: ראש החודש הזה").
 *   [option] returns the style to show it with, or null to leave it out.
 */
fun parseMarkup(
    text: String,
    smallColor: Color,
    option: (condition: String) -> SpanStyle? = { SpanStyle() },
): AnnotatedString = buildAnnotatedString {
    var last = 0
    var hidden = false
    var lastChar = ' '

    fun appendText(s: String) {
        // Avoid a double space where a hidden option was cut out
        val chunk = if (lastChar == ' ' && s.startsWith(' ')) s.trimStart(' ') else s
        if (chunk.isEmpty()) return
        append(chunk)
        lastChar = chunk.last()
    }

    for (match in TAG.findAll(text)) {
        if (!hidden) appendText(text.substring(last, match.range.first))
        last = match.range.last + 1
        val closing = match.groupValues[1] == "/"
        val tag = match.groupValues[2]
        when {
            tag == "if" && !closing -> {
                val style = option(match.groupValues[3])
                if (style != null) pushStyle(style) else hidden = true
            }
            tag == "if" -> if (hidden) hidden = false else runCatching { pop() }
            hidden -> Unit
            // Unbalanced closing tags in the source are ignored
            closing -> runCatching { pop() }
            else -> pushStyle(
                when (tag) {
                    "b" -> SpanStyle(fontWeight = FontWeight.Bold)
                    "small" -> SpanStyle(fontSize = 0.78.em, color = smallColor)
                    else -> SpanStyle(fontSize = 1.25.em)
                },
            )
        }
    }
    if (!hidden) appendText(text.substring(last))
}

/** The conditions of all `<if when="…">` options in [text]. */
fun inlineConditions(text: String): List<String> =
    TAG.findAll(text).filter { it.groupValues[2] == "if" && it.groupValues[1].isEmpty() }
        .map { it.groupValues[3] }.toList()
