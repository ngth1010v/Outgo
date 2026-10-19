package app.outgo.ui.component

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import app.outgo.util.Money

/**
 * An outlined amount input: [text] is as [Money.cleanInput] writes it (grouped, up to two
 * decimals), and [Money.parseInput] turns it into an amount. The cursor stays at the end, like the
 * Trade screen's amount: regrouping changes the text's length, and a cursor left where the
 * keyboard had it would put the next digit or decimal separator in the middle of the number.
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
) {
    OutlinedTextField(
        value = TextFieldValue(text, selection = TextRange(text.length)),
        onValueChange = { onTextChange(Money.cleanInput(text, it.text)) },
        label = { Text(label, maxLines = 1) },
        enabled = enabled,
        isError = isError,
        supportingText = supportingText,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = modifier,
    )
}
