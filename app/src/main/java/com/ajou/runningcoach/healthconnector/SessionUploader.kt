package com.ajou.runningcoach.healthconnector

import com.ajou.runningcoach.healthconnector.data.model.BioEvent
import com.ajou.runningcoach.healthconnector.data.model.BioEventRequest
import com.ajou.runningcoach.healthconnector.data.model.RunningSession
import com.ajou.runningcoach.healthconnector.data.remote.BioApi

class SessionUploader(
    private val api: BioApi,
    private val userId: String
) {

    suspend fun upload(session: RunningSession): Result<Unit> = runCatching {
        val events = buildEvents(session)
        if (events.isEmpty()) return@runCatching

        // DynamoDB BatchWriteItem 최대 25건 제한에 맞게 분할 전송
        events.chunked(25).forEach { batch ->
            val response = api.postBioEvents(
                BioEventRequest(
                    userId = userId,
                    deviceId = session.deviceModel,
                    sessionId = session.id,
                    events = batch
                )
            )
            if (!response.isSuccessful) error("API 오류: ${response.code()} ${response.message()}")
        }
    }

    private fun buildEvents(session: RunningSession): List<BioEvent> {
        val heartRateEvents = session.heartRateSamples.map { sample ->
            BioEvent(
                eventId = "hr-${session.id}-${sample.timestamp.epochSecond}",
                sensorType = "heart_rate",
                measuredAt = sample.timestamp.toString(),
                value = sample.bpm.toFloat(),
                unit = "bpm"
            )
        }
        val bloodPressureEvents = session.bloodPressureSamples.map { sample ->
            BioEvent(
                eventId = "bp-${session.id}-${sample.timestamp.epochSecond}",
                sensorType = "blood_pressure",
                measuredAt = sample.timestamp.toString(),
                value = sample.systolicMmHg.toFloat(),       // systolic
                unit = "mmHg",
                secondaryValue = sample.diastolicMmHg.toFloat() // diastolic
            )
        }
        return heartRateEvents + bloodPressureEvents
    }
}
