package app.outgo.ui.component

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.outgo.util.Money

/**
 * An outlined amount input: [text] is as [Money.cleanInput] writes it (grouped, up to
 * [Money.decimals] decimals), and [Money.parseInput] turns it into an amount. The cursor works
 * like the Trade screen's amount (see [MoneyInputCursor]): focusing puts it at the end, a tap
 * while focused puts it where tapped, and it keeps its place among the digits when they regroup.
 */
@Composable
fun MoneyTextField(
    text: String,
    onTextChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    /** 48dp tall, as a [CompactOutlinedTextField]; ignores [enabled], [isError] and [supportingText]. */
    compact: Boolean = false,
) {
    val cursor = rememberMoneyInputCursor()
    val keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)
    if (compact) {
        CompactOutlinedTextField(
            value = cursor.value(text),
            onValueChange = { new ->
                val cleaned = cursor.onValueChange(text, new)
                if (cleaned != text) onTextChange(cleaned)
            },
            label = label,
            keyboardOptions = keyboard,
            modifier = modifier.then(cursor.modifier { text }),
        )
        return
    }
    OutlinedTextField(
        value = cursor.value(text),
        onValueChange = { new ->
            val cleaned = cursor.onValueChange(text, new)
            if (cleaned != text) onTextChange(cleaned)
        },
        label = { Text(label, maxLines = 1) },
        enabled = enabled,
        isError = isError,
        supportingText = supportingText,
        singleLine = true,
        keyboardOptions = keyboard,
        modifier = modifier.then(cursor.modifier { text }),
    )
}
