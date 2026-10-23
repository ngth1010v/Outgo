package app.outgo.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.outgo.util.Money

/**
 * The cursor of an amount input (see [AmountField], [MoneyTextField]): focusing the field puts it at
 * the end, a tap while focused puts it where tapped, and it keeps its place among the digits when
 * [Money.cleanInput] regroups them. Put [modifier] on the text field.
 */
internal class MoneyInputCursor {
    // Valid while the text is still selectionText: a text set from outside (cleared after a save,
    // put back by Undo) puts the cursor back at the end.
    private var selection by mutableStateOf(TextRange.Zero)
    private var selectionText by mutableStateOf<String?>(null)
    private var focused = false
    // Set when the field gains focus: the cursor placed by that same tap is moved to the end.
    private var snapToEnd = false

    fun value(text: String) = TextFieldValue(text, if (selectionText == text) selection else TextRange(text.length))

    /** The text to show after the user's edit [new] over [text], cleaned by [Money.cleanInput]. */
    fun onValueChange(text: String, new: TextFieldValue): String {
        if (new.text == text) {
            // Only the cursor moved: the tap that focused the field leaves it at the end.
            selection = if (snapToEnd) TextRange(text.length) else new.selection
            selectionText = text
            snapToEnd = false
            return text
        }
        snapToEnd = false
        var raw = new.text
        var rawCursor = new.selection.end
        var cleaned = Money.cleanInput(text, raw)
        // Backspace over a thousands separator: regrouping would put it straight back,
        // so delete the digit before it instead.
        val at = rawCursor
        if (cleaned == text && raw.length == text.length - 1 && at in 1 until text.length &&
            !text[at].isDigit() && text[at] != Money.style.decimal
        ) {
            raw = raw.removeRange(at - 1, at)
            rawCursor = at - 1
            cleaned = Money.cleanInput(text, raw)
        }
        // Left as it was (too many decimals, say): the cursor stays put too.
        if (cleaned != text) {
            selection = TextRange(cursorAfterClean(raw, rawCursor, cleaned))
        } else if (selectionText != text) {
            selection = TextRange(text.length)
        }
        selectionText = cleaned
        return cleaned
    }

    fun modifier(text: () -> String): Modifier = Modifier
        .onFocusChanged {
            if (it.isFocused && !focused) {
                val now = text()
                selection = TextRange(now.length)
                selectionText = now
                snapToEnd = true
            }
            focused = it.isFocused
        }
        // A tap on the field while it already has focus moves the cursor where tapped.
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (focused) snapToEnd = false
            }
        }
}

@Composable
internal fun rememberMoneyInputCursor() = remember { MoneyInputCursor() }

/**
 * Where the cursor goes in [cleaned] for one at [cursor] in [raw]: before as many digits and
 * decimal separators as it had after it. Counted from the end, since regrouping and dropped
 * leading zeros change what comes before.
 */
private fun cursorAfterClean(raw: String, cursor: Int, cleaned: String): Int {
    fun counts(c: Char) = c.isDigit() || c == Money.style.decimal
    var after = raw.substring(cursor.coerceIn(0, raw.length)).count(::counts)
    var i = cleaned.length
    while (i > 0 && after > 0) {
        if (counts(cleaned[i - 1])) after--
        i--
    }
    return i
}
