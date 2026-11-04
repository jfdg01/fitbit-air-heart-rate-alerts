package com.nikashitsa.polar_alert_android.lib

import com.nikashitsa.polar_alert_android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceTest {
    @Test
    fun theZoneAlertsHaveAVoiceLineInSpanishAndEnglish() {
        assertEquals(R.string.voice_too_high, voiceLine(SoundType.TOO_HIGH, AppLanguage.ES))
        assertEquals(R.string.voice_too_low, voiceLine(SoundType.TOO_LOW, AppLanguage.EN))
        assertEquals(R.string.voice_good, voiceLine(SoundType.GOOD, AppLanguage.ES))
    }

    @Test
    fun theBeepsAndTheConnectionAlertsKeepTheirWav() {
        assertNull(voiceLine(SoundType.HIGH_BEEP, AppLanguage.ES))
        assertNull(voiceLine(SoundType.LOW_BEEP, AppLanguage.EN))
        assertNull(voiceLine(SoundType.CONNECTED, AppLanguage.ES))
        assertNull(voiceLine(SoundType.DISCONNECTED, AppLanguage.ES))
        assertNull(voiceLine(SoundType.RECONNECTING, AppLanguage.EN))
    }

    @Test
    fun aLanguageWithoutTheLinesKeepsTheWav() {
        assertNull(voiceLine(SoundType.TOO_HIGH, AppLanguage.DE))
        assertNull(voiceLine(SoundType.GOOD, AppLanguage.PT_BR))
    }

    @Test
    fun aRunnerWithoutANameIsA() {
        assertEquals("A", spokenName(""))
        assertEquals("Javi", spokenName("Javi"))
    }
}
