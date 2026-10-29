package app.outgo.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** The height of [CompactOutlinedTextField]: that of an [app.outgo.ui.category.OffsetPicker] row. */
val CompactFieldHeight = 48.dp

/**
 * A single-line outlined text field 48dp tall instead of 56dp, so it lines up with the picker
 * rows around it. Its label sits inside while empty and floats onto the border, as usual.
 */
@Composable
fun CompactOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(CompactFieldHeight),
        readOnly = readOnly,
        singleLine = true,
        textStyle = compactTextStyle(),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        interactionSource = interactionSource,
        decorationBox = { inner -> CompactDecoration(value, inner, label, trailingIcon, interactionSource) },
    )
}

/** [CompactOutlinedTextField] over a [TextFieldValue], for fields that place the cursor themselves. */
@Composable
fun CompactOutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(CompactFieldHeight),
        singleLine = true,
        textStyle = compactTextStyle(),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        interactionSource = interactionSource,
        decorationBox = { inner -> CompactDecoration(value.text, inner, label, null, interactionSource) },
    )
}

@Composable
private fun compactTextStyle() = LocalTextStyle.current.merge(color = MaterialTheme.colorScheme.onSurface)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactDecoration(
    text: String,
    innerTextField: @Composable () -> Unit,
    label: String,
    trailingIcon: (@Composable () -> Unit)?,
    interactionSource: MutableInteractionSource,
) = OutlinedTextFieldDefaults.DecorationBox(
    value = text,
    innerTextField = innerTextField,
    enabled = true,
    singleLine = true,
    visualTransformation = VisualTransformation.None,
    interactionSource = interactionSource,
    label = { Text(label, maxLines = 1) },
    trailingIcon = trailingIcon,
    // 12dp above and below a 24dp line: 48dp in all.
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    container = {
        OutlinedTextFieldDefaults.ContainerBox(enabled = true, isError = false, interactionSource = interactionSource, colors = OutlinedTextFieldDefaults.colors())
    },
)
