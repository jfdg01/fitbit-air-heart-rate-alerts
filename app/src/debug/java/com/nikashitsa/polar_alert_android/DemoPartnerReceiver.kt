package com.nikashitsa.polar_alert_android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nikashitsa.polar_alert_android.lib.Room
import com.nikashitsa.polar_alert_android.lib.RunnerState
import com.nikashitsa.polar_alert_android.lib.TrackingState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Gives the room the state of a fake partner, as if it came by BLE. The `lost` extra is a loss of
 * the link instead. Hilt has no @AndroidEntryPoint here: Kotlin cannot call the abstract onReceive.
 */
class DemoPartnerReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RoomEntryPoint {
        fun room(): Room
    }

    override fun onReceive(context: Context, intent: Intent) {
        val room = EntryPointAccessors.fromApplication(context, RoomEntryPoint::class.java).room()
        if (intent.getBooleanExtra("lost", false)) {
            Log.d("DemoPartnerReceiver", "partner lost")
            return room.onLinkLost()
        }
        val state = RunnerState(
            name = intent.getStringExtra("name") ?: "Ana",
            bpm = intent.getIntExtra("bpm", 150),
            trackingState = TrackingState.valueOf(intent.getStringExtra("state") ?: "GOOD"),
            alerting = intent.getBooleanExtra("alerting", false),
            strapConnected = intent.getBooleanExtra("strap", true),
            leaving = intent.getBooleanExtra("leave", false),
        )
        Log.d("DemoPartnerReceiver", "partner $state")
        room.onPartnerState(state)
    }
}
