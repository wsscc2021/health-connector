package com.ajou.runningcoach.healthconnector.data

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.ajou.runningcoach.healthconnector.data.model.HeartRateSample
import com.ajou.runningcoach.healthconnector.data.model.RunningSession
import java.time.Instant

class HealthDataRepository(private val client: HealthConnectClient) {

    suspend fun getExerciseSessions(start: Instant, end: Instant): List<RunningSession> {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )

        return response.records
            .filter { it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING }
            .map { session ->
                val heartRates = getHeartRateInSession(session.startTime, session.endTime)
                val steps = getStepsInSession(session.startTime, session.endTime)
                val distance = getDistanceInSession(session.startTime, session.endTime)

                RunningSession(
                    id = session.metadata.id,
                    startTime = session.startTime,
                    endTime = session.endTime,
                    deviceModel = session.metadata.device?.model ?: "unknown",
                    heartRateSamples = heartRates,
                    totalSteps = steps,
                    totalDistanceMeters = distance
                )
            }
    }

    suspend fun getHeartRateInSession(start: Instant, end: Instant): List<HeartRateSample> {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )

        // 1분 단위 평균 집계: 같은 분에 측정된 샘플들을 묶어 평균 bpm 계산
        return response.records
            .flatMap { it.samples }
            .groupBy { it.time.epochSecond / 60 }
            .map { (minuteBucket, samples) ->
                HeartRateSample(
                    timestamp = Instant.ofEpochSecond(minuteBucket * 60),
                    bpm = samples.map { it.beatsPerMinute }.average().toInt()
                )
            }
            .sortedBy { it.timestamp }
    }

    private suspend fun getStepsInSession(start: Instant, end: Instant): Long {
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response[StepsRecord.COUNT_TOTAL] ?: 0L
    }

    private suspend fun getDistanceInSession(start: Instant, end: Instant): Double {
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
    }
}
