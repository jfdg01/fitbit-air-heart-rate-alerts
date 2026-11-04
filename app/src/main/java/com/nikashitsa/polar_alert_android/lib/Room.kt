package com.nikashitsa.polar_alert_android.lib

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.nikashitsa.polar_alert_android.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** The phone that creates the room, or the phone that joins it. A runner without a name is the letter. */
enum class RoomRole(val letter: String) { HOST("A"), JOIN("B") }

/** The state that each phone of a room sends about its runner, once a second. */
data class RunnerState(
    val name: String,
    /** -1 without a sample. */
    val bpm: Int,
    val trackingState: TrackingState,
    val alerting: Boolean,
    val strapConnected: Boolean,
    /** The last state of a runner who leaves the room. */
    val leaving: Boolean,
) {
    /** 2 bytes and the name. A packet of 20 bytes or less needs no other MTU. */
    fun encode(): ByteArray {
        var flags = trackingState.ordinal
        if (alerting) flags = flags or ALERTING
        if (strapConnected) flags = flags or STRAP
        if (leaving) flags = flags or LEAVING
        var cut = name
        while (cut.toByteArray().size > PACKET - 2) cut = cut.dropLast(if (cut.last().isLowSurrogate()) 2 else 1)
        return byteArrayOf(flags.toByte(), (bpm + 1).coerceIn(0, 255).toByte()) + cut.toByteArray()
    }

    companion object {
        private const val PACKET = 20
        private const val ALERTING = 4
        private const val STRAP = 8
        private const val LEAVING = 16

        fun decode(packet: ByteArray): RunnerState? {
            if (packet.size < 2) return null
            val flags = packet[0].toInt()
            val trackingState = TrackingState.entries.getOrNull(flags and 3) ?: return null
            return RunnerState(
                name = packet.copyOfRange(2, packet.size).decodeToString(),
                bpm = (packet[1].toInt() and 0xFF) - 1,
                trackingState = trackingState,
                alerting = flags and ALERTING != 0,
                strapConnected = flags and STRAP != 0,
                leaving = flags and LEAVING != 0,
            )
        }
    }
}

/**
 * The zone line that my phone says when the state of the partner changes from [prev] to [next].
 * The alert of the partner decides, so my phone does not compute the zone of the partner.
 */
@StringRes
fun partnerLine(prev: RunnerState?, next: RunnerState): Int? = when {
    next.alerting && (prev?.alerting != true || prev.trackingState != next.trackingState) ->
        if (next.trackingState == TrackingState.HIGH) R.string.voice_too_high else R.string.voice_too_low
    !next.alerting && prev?.alerting == true && next.trackingState == TrackingState.GOOD && next.bpm >= 0 -> R.string.voice_good
    else -> null
}

/** A text of the room section, with the code of an error. */
data class RoomMessage(@StringRes val text: Int, val code: Int? = null)

sealed interface RoomState {
    data class Idle(val message: RoomMessage? = null) : RoomState
    data class Hosting(val code: String) : RoomState
    data class Joining(val code: String) : RoomState
    data class InRoom(val role: RoomRole, val partner: RunnerState, val linked: Boolean) : RoomState
}

/** The room of two runners. It lives until a runner leaves, or until the app closes. */
@Singleton
class Room @Inject constructor(
    private val ble: RoomBle,
    private val alerts: Alerts,
    private val sound: SoundManager,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<RoomState>(RoomState.Idle())
    val state: StateFlow<RoomState> = _state.asStateFlow()

    @Volatile var strapConnected = false
    @Volatile private var name = ""
    private var timeout: Job? = null

    private val role: RoomRole
        get() = when (val s = _state.value) {
            is RoomState.Joining -> RoomRole.JOIN
            is RoomState.InRoom -> s.role
            else -> RoomRole.HOST
        }

    init {
        ble.onState = { packet -> RunnerState.decode(packet)?.let(::onPartnerState) }
        ble.onLost = ::onLinkLost
        ble.onError = { text, code -> end(RoomMessage(text, code)) }
        scope.launch { settings.nameFlow.collect { name = it } }
        scope.launch(Dispatchers.Main) {
            while (true) {
                delay(1000)
                if (_state.value !is RoomState.Idle) ble.send(own().encode())
            }
        }
    }

    fun create() {
        if (!ble.canHost()) return end(RoomMessage(R.string.room_cannot_host))
        val code = "%04d".format(Random.nextInt(10000))
        set(RoomState.Hosting(code))
        ble.host(code)
    }

    fun join(code: String) {
        set(RoomState.Joining(code))
        endAfter(RoomMessage(R.string.room_not_found))
        ble.join(code)
    }

    fun leave() {
        ble.stop(own(leaving = true).encode())
        set(RoomState.Idle())
    }

    /** A state of the partner. The debug build calls it with a fake partner too. */
    fun onPartnerState(next: RunnerState) {
        val s = _state.value
        if (next.leaving) {
            say(R.string.voice_partner_left, next.name)
            return end(null)
        }
        if (s is RoomState.InRoom && !s.linked) say(R.string.voice_partner_back, next.name)
        partnerLine((s as? RoomState.InRoom)?.partner, next)?.let { say(it, next.name) }
        set(RoomState.InRoom(role, next, linked = true))
    }

    fun onLinkLost() {
        val s = _state.value as? RoomState.InRoom ?: return
        if (!s.linked) return
        say(R.string.voice_partner_lost, s.partner.name)
        set(s.copy(linked = false))
        endAfter(RoomMessage(R.string.room_ended))
    }

    /** Outside a session my runner has no pulse and no alert. */
    private fun own(leaving: Boolean = false): RunnerState {
        val running = alerts.isRunning
        val a = alerts.state.value
        return RunnerState(
            name = spokenName(name, role.letter),
            bpm = if (running) a.bpm else -1,
            trackingState = if (running) a.trackingState else TrackingState.GOOD,
            alerting = running && a.alerting,
            strapConnected = strapConnected,
            leaving = leaving,
        )
    }

    /** The lines of the partner sound only between Start and Stop. */
    private fun say(@StringRes line: Int, partner: String) {
        if (!alerts.isRunning) return
        scope.launch(Dispatchers.Main.immediate) { sound.sayPartner(line, partner, settings.volumeFlow.first()) }
    }

    private fun endAfter(message: RoomMessage) {
        timeout = scope.launch(Dispatchers.Main) {
            delay(TIMEOUT)
            end(message)
        }
    }

    private fun end(message: RoomMessage?) {
        ble.stop()
        set(RoomState.Idle(message))
    }

    private fun set(next: RoomState) {
        timeout?.cancel()
        timeout = null
        _state.value = next
        sound.letter = role.letter
    }

    private companion object {
        const val TIMEOUT = 4 * 60_000L
    }
}

@HiltViewModel
class RoomViewModel @Inject constructor(val room: Room) : ViewModel()
