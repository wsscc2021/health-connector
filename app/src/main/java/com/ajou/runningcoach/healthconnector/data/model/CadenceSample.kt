package com.ajou.runningcoach.healthconnector.data.model

import java.time.Instant

data class CadenceSample(
    val timestamp: Instant,
    val stepsPerMinute: Double
)
