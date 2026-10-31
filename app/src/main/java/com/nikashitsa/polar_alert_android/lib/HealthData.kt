package com.nikashitsa.polar_alert_android.lib

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDateTime
import java.time.Period
import java.time.temporal.ChronoUnit

enum class HealthStatus { UNAVAILABLE, NO_PERMISSION, READY }

/** What Google Health and other apps stored in Health Connect, on this phone only. */
object HealthData {
    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    /** The zones never use [measuredPeak]: a false reading must not raise the limits of a person. */
    data class Reading(val resting: Int?, val measuredPeak: Int?)

    suspend fun status(context: Context): HealthStatus {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return HealthStatus.UNAVAILABLE
        }
        val granted = HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        return if (granted.containsAll(PERMISSIONS)) HealthStatus.READY else HealthStatus.NO_PERMISSION
    }

    suspend fun read(context: Context): Reading {
        val client = HealthConnectClient.getOrCreate(context)
        val resting = client.readRecords(
            ReadRecordsRequest(
                RestingHeartRateRecord::class,
                TimeRangeFilter.after(Instant.now().minus(14, ChronoUnit.DAYS)),
                ascendingOrder = false,
                pageSize = 7,
            )
        ).records.map { it.beatsPerMinute.toInt() }
        val now = LocalDateTime.now()
        val daily = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                setOf(HeartRateRecord.BPM_MAX),
                TimeRangeFilter.between(now.minusDays(90), now),
                Period.ofDays(1),
            )
        ).mapNotNull { it.result[HeartRateRecord.BPM_MAX]?.toInt() }
        return Reading(HeartZones.median(resting), daily.maxOrNull())
    }
}
