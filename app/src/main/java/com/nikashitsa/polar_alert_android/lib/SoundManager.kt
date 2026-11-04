package com.nikashitsa.polar_alert_android.lib

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.annotation.StringRes
import com.nikashitsa.polar_alert_android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Spoken sounds have per-language versions in `res/raw-<tag>/`; the beeps are shared. */
enum class SoundType(val resId: Int) {
    HIGH_BEEP(R.raw.high_beep),
    LOW_BEEP(R.raw.low_beep),
    CONNECTED(R.raw.connected),
    DISCONNECTED(R.raw.disconnected),
    RECONNECTING(R.raw.reconnecting),
    GOOD(R.raw.good),
    TOO_HIGH(R.raw.too_high),
    TOO_LOW(R.raw.too_low),
}

/**
 * The line that the voice says for [type], with the runner's name, or null when the `.wav`
 * plays. Only the zone alerts say the name. Only English and Spanish have the lines: Android
 * falls back to the English line for any other language, so the check comes before the voice.
 */
@StringRes
fun voiceLine(type: SoundType, language: AppLanguage): Int? {
    if (language != AppLanguage.ES && language != AppLanguage.EN) return null
    return when (type) {
        SoundType.TOO_HIGH -> R.string.voice_too_high
        SoundType.TOO_LOW -> R.string.voice_too_low
        SoundType.GOOD -> R.string.voice_good
        else -> null
    }
}

/** A runner without a name in Settings is "A". */
fun spokenName(name: String): String = name.ifEmpty { "A" }

@Singleton
class SoundManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    settings: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    private val soundPool: SoundPool
    private val voice: TextToSpeech

    // Swapped whole rather than mutated, since it is loaded off the main thread.
    @Volatile
    private var soundMap: Map<SoundType, Int> = emptyMap()
    @Volatile
    private var localizedContext: Context = context
    @Volatile
    private var language: AppLanguage = AppLanguage.resolve(null)
    @Volatile
    private var name = ""
    @Volatile
    private var voiceReady = false
    private var voiceLocale: Locale? = null

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        // The engine starts in the background. Until it is ready, the zone alerts play the .wav.
        voice = TextToSpeech(context) { status ->
            voiceReady = status == TextToSpeech.SUCCESS
            if (!voiceReady) Log.w(TAG, "voice: the TextToSpeech engine did not start ($status)")
        }
        // The same attributes as the beeps, so the voice and the beep sound at the same level.
        voice.setAudioAttributes(audioAttributes)

        // The application context ignores the in-app language, so load through a context
        // localized to it, and reload whenever the user picks another language.
        scope.launch {
            settings.languageFlow.distinctUntilChanged().collect { tag ->
                language = AppLanguage.resolve(tag)
                localizedContext = context.withLanguage(tag)
                load(localizedContext)
            }
        }
        scope.launch {
            settings.nameFlow.collect { name = it }
        }
    }

    private fun load(localizedContext: Context) {
        val old = soundMap
        soundMap = SoundType.entries.associateWith { soundPool.load(localizedContext, it.resId, 1) }
        old.values.forEach(soundPool::unload)
    }

    fun play(type: SoundType, volume: Int) {
        if (speak(type, volume)) return
        soundMap[type]?.let { soundId ->
            val volumeFloat = volume / 100f
            soundPool.play(soundId, volumeFloat, volumeFloat, 1, 0, 1f)
        }
    }

    /**
     * Says the line of [type] with the runner's name, and returns false when the .wav must play
     * instead. An alert without the name is better than an alert that does not sound.
     */
    private fun speak(type: SoundType, volume: Int): Boolean {
        val line = voiceLine(type, language) ?: return false
        if (!voiceReady) {
            Log.w(TAG, "voice: the engine is not ready, $type plays the wav")
            return false
        }
        // setLanguage takes about 200 ms on the main thread, so it runs only for a new language.
        val locale = Locale.forLanguageTag(language.tag)
        if (locale != voiceLocale) {
            val available = voice.setLanguage(locale)
            if (available < TextToSpeech.LANG_AVAILABLE) {
                Log.w(TAG, "voice: no voice for ${language.tag} ($available), $type plays the wav")
                return false
            }
            voiceLocale = locale
        }
        val text = localizedContext.getString(line, spokenName(name))
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume / 100f) }
        if (voice.speak(text, TextToSpeech.QUEUE_FLUSH, params, type.name) != TextToSpeech.SUCCESS) {
            Log.w(TAG, "voice: speak failed, $type plays the wav")
            return false
        }
        Log.d(TAG, "voice \"$text\"")
        return true
    }

    fun release() {
        soundPool.release()
        voice.shutdown()
    }

    private companion object {
        // The tag of the alert lines, so `logcat -s Alerts:D` shows the voice too.
        const val TAG = "Alerts"
    }
}
