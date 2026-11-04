package com.nikashitsa.polar_alert_android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nikashitsa.polar_alert_android.lib.SettingsOptions
import com.nikashitsa.polar_alert_android.lib.spokenName
import com.nikashitsa.polar_alert_android.ui.theme.Colors

/**
 * The runner's name, in the style of [NumberField]. Each change goes to [setValue] at once, and
 * the repository removes the spaces at the ends. An empty field shows "A", the name that the
 * voice says for a runner without a name.
 */
@Composable
fun NameField(value: String, setValue: (String) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(value) }
    // Each letter is a write to the DataStore, and the stored value comes back late. A late value
    // must not replace the text while the user types, so the field follows the value only without focus.
    LaunchedEffect(value) { if (!focused) text = value }
    val focusManager = LocalFocusManager.current
    val style = LocalTextStyle.current.copy(color = Colors.White, textAlign = TextAlign.End)

    Column(modifier = modifier.width(120.dp)) {
        BasicTextField(
            value = text,
            onValueChange = { new ->
                text = new.take(SettingsOptions.NAME_MAX)
                setValue(text)
            },
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(Colors.White),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    if (text.isEmpty()) Text(spokenName(""), style = style.copy(color = Colors.White.copy(alpha = 0.4f)))
                    innerTextField()
                }
            },
            modifier = Modifier.onFocusChanged {
                // Only a real exit counts: the field reports "no focus" when it first shows too.
                if (focused && !it.isFocused) text = value
                focused = it.isFocused
            },
        )
        HorizontalDivider(color = Colors.White)
    }
}
