package com.nikashitsa.polar_alert_android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nikashitsa.polar_alert_android.lib.BluetoothViewModel

/** Fixes the pulse of the demo with the `bpm` extra. A broadcast without it brings back the wave. */
class DemoBpmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bpm = intent.getIntExtra("bpm", -1).takeIf { it >= 0 }
        BluetoothViewModel.demoBpm = bpm
        Log.d("DemoBpmReceiver", "demo bpm ${bpm ?: "wave"}")
    }
}
