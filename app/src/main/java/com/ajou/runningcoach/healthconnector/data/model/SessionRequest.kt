package com.ajou.runningcoach.healthconnector.data.model

data class SessionRequest(
    val sessionId: String,
    val userId: String,
    val deviceId: String,
    val startTime: String,
    val endTime: String
)
