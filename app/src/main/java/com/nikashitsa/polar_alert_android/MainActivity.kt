package com.nikashitsa.polar_alert_android

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.nikashitsa.polar_alert_android.lib.SettingsRepository
import com.nikashitsa.polar_alert_android.lib.readSavedLanguageBlocking
import com.nikashitsa.polar_alert_android.lib.withLanguage
import com.nikashitsa.polar_alert_android.ui.Navigation
import com.nikashitsa.polar_alert_android.ui.theme.HeartAlertTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity: ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository

    // The UI language is applied to the Activity's own context, before anything is inflated,
    // so every string resource (dialogs and sheets included) comes out in it.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withLanguage(readSavedLanguageBlocking(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recreate on a language change to pick it up in attachBaseContext. The first value is
        // the one just applied; ViewModels and saved state (the current screen, the BLE
        // connection) survive the recreation.
        lifecycleScope.launch {
            settings.languageFlow.distinctUntilChanged().drop(1).collect { recreate() }
        }
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        setContent {
            HeartAlertTheme {
                Navigation()
            }
        }
    }
}
