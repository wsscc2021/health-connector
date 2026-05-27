package com.ajou.runningcoach.healthconnector.data.model

data class BioEvent(
    val eventId: String,
    val sensorType: String,
    val measuredAt: String,
    val value: Float,
    val unit: String
)
