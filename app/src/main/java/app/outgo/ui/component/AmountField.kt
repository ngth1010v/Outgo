package app.outgo.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.outgo.ui.theme.ExpenseRed
import app.outgo.ui.theme.IncomeGreen
import app.outgo.ui.theme.TransferBlue
import app.outgo.util.Money

/**
 * The Trade screen's amount input: large, bold, centered, colored by
 * Expense/Income/Transfer, never negative (the sign always comes from the
 * Expense/Income toggle, not the number itself). Takes up to two decimals.
 * A tap anywhere on its row (the currency symbol, the empty space beside it)
 * focuses it, not just on the digits. Focusing it puts the cursor at the end; a tap while
 * focused puts it where tapped (see [MoneyInputCursor]).
 */
@Composable
fun AmountField(
    amount: Long,
    onAmountChange: (Long) -> Unit,
    isIncome: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    isTransfer: Boolean = false,
    /** Shown faded in place of the "0" while [amount] is 0; 0 for none. */
    predicted: Long = 0,
) {
    val color = if (isTransfer) TransferBlue else if (isIncome) IncomeGreen else ExpenseRed
    // As typed, so "4." keeps its separator while the decimals are on their way. It follows an
    // amount set from outside (cleared after a save, put back by Undo) or a new number format.
    var typed by remember { mutableStateOf(Money.inputText(amount)) }
    val text = if (Money.parseInput(typed) == amount) typed else Money.inputText(amount)
    val cursor = rememberMoneyInputCursor()
    val style = MaterialTheme.typography.headlineLarge.copy(
        color = color,
        fontWeight = FontWeight.Bold,
    )

    val placeholderColor = color.copy(alpha = 0.35f)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                focusRequester.requestFocus()
                keyboard?.show()
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                if (text.isEmpty()) Text(if (predicted > 0) Money.inputText(predicted) else "0", style = style.copy(color = placeholderColor))
                BasicTextField(
                    value = cursor.value(text),
                    onValueChange = { new ->
                        val cleaned = cursor.onValueChange(text, new)
                        if (cleaned != text) {
                            typed = cleaned
                            onAmountChange(Money.parseInput(typed))
                        }
                    },
                    textStyle = style,
                    singleLine = true,
                    cursorBrush = SolidColor(color),
                    // Done (✓) closes the keyboard, which otherwise covers the save button.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .width(IntrinsicSize.Min)
                        .focusRequester(focusRequester)
                        .then(cursor.modifier { text }),
                )
            }
            // No gap: amounts are always written "<money><currency>", see Money.format.
            Text(Money.symbol, style = style.copy(color = if (text.isEmpty()) placeholderColor else color))
        }
    }
}
