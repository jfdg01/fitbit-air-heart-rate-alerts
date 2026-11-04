package com.nikashitsa.polar_alert_android.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nikashitsa.polar_alert_android.R
import com.nikashitsa.polar_alert_android.lib.RoomState
import com.nikashitsa.polar_alert_android.lib.RoomViewModel
import com.nikashitsa.polar_alert_android.ui.screens.SettingRow
import com.nikashitsa.polar_alert_android.ui.screens.SettingSection
import com.nikashitsa.polar_alert_android.ui.theme.Colors
import com.nikashitsa.polar_alert_android.ui.theme.Fonts

private val HOST_PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
private val JOIN_PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

/** The section "Run as a pair" of the settings: create a room, join a room, or leave it. */
@Composable
fun RoomSection(viewModel: RoomViewModel = hiltViewModel()) {
    val room = viewModel.room
    val state by room.state.collectAsState()
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) pending?.invoke()
        pending = null
    }
    val withPermissions = { permissions: Array<String>, action: () -> Unit ->
        if (permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            action()
        } else {
            pending = action
            launcher.launch(permissions)
        }
    }
    var typing by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    val gap = @Composable { Spacer(Modifier.height(12.dp)) }

    SettingSection(title = stringResource(R.string.room_section)) {
        when (val s = state) {
            is RoomState.Idle -> {
                s.message?.let {
                    Text(stringResource(it.text, it.code ?: 0))
                    gap()
                }
                if (typing) {
                    SettingRow(label = stringResource(R.string.room_code)) {
                        CodeField(code) { new ->
                            code = new
                            if (new.length == 4) withPermissions(JOIN_PERMISSIONS) {
                                typing = false
                                code = ""
                                room.join(new)
                            }
                        }
                    }
                    gap()
                    AppButton(stringResource(R.string.room_cancel)) { typing = false; code = "" }
                } else {
                    AppButton(stringResource(R.string.room_create)) { withPermissions(HOST_PERMISSIONS, room::create) }
                    gap()
                    AppButton(stringResource(R.string.room_join)) { typing = true }
                }
            }
            is RoomState.Hosting -> {
                SettingRow(label = stringResource(R.string.room_code)) { Text(s.code, style = Fonts.textLgBold) }
                SettingRow(label = stringResource(R.string.room_waiting)) {}
                gap()
                AppButton(stringResource(R.string.room_cancel), onClick = room::leave)
            }
            is RoomState.Joining -> {
                SettingRow(label = stringResource(R.string.room_searching)) { Text(s.code) }
                gap()
                AppButton(stringResource(R.string.room_cancel), onClick = room::leave)
            }
            is RoomState.InRoom -> {
                SettingRow(label = s.partner.name) {
                    Text(stringResource(if (s.linked) R.string.room_linked else R.string.reconnecting))
                }
                gap()
                AppButton(stringResource(R.string.room_leave), onClick = room::leave)
            }
        }
    }
}

@Composable
private fun CodeField(value: String, setValue: (String) -> Unit) {
    val style = LocalTextStyle.current.copy(color = Colors.White, textAlign = TextAlign.End)
    Column(Modifier.width(80.dp)) {
        BasicTextField(
            value = value,
            onValueChange = { setValue(it.filter(Char::isDigit).take(4)) },
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(Colors.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        HorizontalDivider(color = Colors.White)
    }
}
