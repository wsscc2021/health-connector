package com.ajou.runningcoach.healthconnector.data.model

import java.time.Instant

data class OxygenSaturationSample(
    val timestamp: Instant,
    val percentage: Double  // SpO2 (0.0 ~ 100.0)
)
