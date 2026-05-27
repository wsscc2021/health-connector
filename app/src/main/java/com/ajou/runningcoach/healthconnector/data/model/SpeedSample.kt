package com.ajou.runningcoach.healthconnector.data.model

import java.time.Instant

data class SpeedSample(
    val timestamp: Instant,
    val metersPerSecond: Double
)
