package com.smartsiddur.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

private val TAG = Regex("""<(/?)(b|small|big)>""")

/**
 * Converts the prayer-text markup (<b>, <small>, <big>) into an AnnotatedString.
 * Sizes are relative (em), so pinch-zooming the base font size scales everything.
 */
fun parseMarkup(text: String, smallColor: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in TAG.findAll(text)) {
        append(text.substring(last, match.range.first))
        last = match.range.last + 1
        val closing = match.groupValues[1] == "/"
        if (closing) {
            // Unbalanced closing tags in the source are ignored
            runCatching { pop() }
        } else {
            pushStyle(
                when (match.groupValues[2]) {
                    "b" -> SpanStyle(fontWeight = FontWeight.Bold)
                    "small" -> SpanStyle(fontSize = 0.78.em, color = smallColor)
                    else -> SpanStyle(fontSize = 1.25.em)
                },
            )
        }
    }
    append(text.substring(last))
}
