package com.ajou.runningcoach.healthconnector.data.model

import java.time.Instant

data class RunningSession(
    val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val deviceModel: String,
    val heartRateSamples: List<HeartRateSample> = emptyList(),
    val totalSteps: Long = 0,
    val totalDistanceMeters: Double = 0.0
)
