package com.ajou.runningcoach.healthconnector.data.model

import java.time.Instant

data class BloodPressureSample(
    val timestamp: Instant,
    val systolicMmHg: Double,
    val diastolicMmHg: Double
)
