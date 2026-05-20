package com.ajou.runningcoach.healthconnector.data.model

data class BioEventRequest(
    val userId: String,
    val deviceId: String,
    val sessionId: String?,
    val events: List<BioEvent>
)
