package com.ajou.runningcoach.healthconnector.data.model

import java.time.Instant

data class RunningSession(
    val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val deviceModel: String,
    val exerciseType: Int = 0,
    val heartRateSamples: List<HeartRateSample> = emptyList(),
    val cadenceSamples: List<CadenceSample> = emptyList(),
    val totalSteps: Long = 0,
    val totalDistanceMeters: Double = 0.0
)
