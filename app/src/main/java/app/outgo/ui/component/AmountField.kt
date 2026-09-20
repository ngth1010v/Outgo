package app.outgo.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.outgo.ui.theme.ExpenseRed
import app.outgo.ui.theme.IncomeGreen
import app.outgo.ui.theme.TransferBlue
import app.outgo.util.Money

/**
 * The Trade screen's amount input: large, bold, centered, colored by
 * Expense/Income/Transfer, digits only (never negative — the sign always
 * comes from the Expense/Income toggle, not the number itself).
 */
@Composable
fun AmountField(
    amount: Long,
    onAmountChange: (Long) -> Unit,
    isIncome: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    isTransfer: Boolean = false,
) {
    val color = if (isTransfer) TransferBlue else if (isIncome) IncomeGreen else ExpenseRed
    val text = if (amount == 0L) "" else Money.groupThousands(amount)
    val style = MaterialTheme.typography.headlineLarge.copy(
        color = color,
        fontWeight = FontWeight.Bold,
    )

    val placeholderColor = color.copy(alpha = 0.35f)
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                if (text.isEmpty()) Text("0", style = style.copy(color = placeholderColor))
                BasicTextField(
                    value = TextFieldValue(text = text, selection = TextRange(text.length)),
                    onValueChange = { onAmountChange(Money.parseDigits(it.text)) },
                    textStyle = style,
                    singleLine = true,
                    cursorBrush = SolidColor(color),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .width(IntrinsicSize.Min)
                        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(Money.symbol, style = style.copy(color = if (text.isEmpty()) placeholderColor else color))
        }
    }
}
