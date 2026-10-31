package com.nikashitsa.polar_alert_android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nikashitsa.polar_alert_android.ui.theme.Colors

/**
 * A whole number that the user types. Each valid value goes to [setValue] at once, because a
 * tap on a button does not move the focus. Text outside [range] shows in red, and the last
 * valid value comes back when the field loses the focus.
 */
@Composable
fun NumberField(
    value: Int,
    range: IntRange,
    setValue: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("$value") }
    LaunchedEffect(value) { text = "$value" }
    val valid = text.toIntOrNull() in range
    val focusManager = LocalFocusManager.current
    val color = if (valid) Colors.White else Colors.Red

    Column(modifier = modifier.width(64.dp)) {
        BasicTextField(
            value = text,
            onValueChange = { new ->
                text = new.filter(Char::isDigit).take(3)
                text.toIntOrNull()?.takeIf { it in range }?.let(setValue)
            },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = color, textAlign = TextAlign.End),
            cursorBrush = SolidColor(Colors.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.onFocusChanged { if (!it.isFocused) text = "$value" },
        )
        HorizontalDivider(color = color)
    }
}
