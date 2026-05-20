package com.ajou.runningcoach.healthconnector.data.model

import java.time.Instant

data class HeartRateSample(
    val timestamp: Instant,
    val bpm: Int
)
