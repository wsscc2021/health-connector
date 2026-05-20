package com.ajou.runningcoach.healthconnector.data

import android.util.Log
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
import kotlinx.coroutines.CancellationException
import java.time.Instant

class HealthDataRepository(private val client: HealthConnectClient) {

    companion object {
        private const val TAG = "HealthDataRepository"
    }

    suspend fun getExerciseSessions(start: Instant, end: Instant): List<RunningSession> {
        val allRecords = readAllExerciseSessionPages(start, end)
        Log.d(TAG, "전체 운동 세션 수: ${allRecords.size}")
        Log.d(TAG, "운동 타입 목록: ${allRecords.map { it.exerciseType }.distinct()}")

        return allRecords.mapNotNull { session ->
            try {
                val heartRates = getHeartRateInSession(session.startTime, session.endTime)
                val steps = getStepsInSession(session.startTime, session.endTime)
                val distance = getDistanceInSession(session.startTime, session.endTime)

                RunningSession(
                    id = session.metadata.id,
                    startTime = session.startTime,
                    endTime = session.endTime,
                    deviceModel = session.metadata.device?.model ?: "unknown",
                    exerciseType = session.exerciseType,
                    heartRateSamples = heartRates,
                    totalSteps = steps,
                    totalDistanceMeters = distance
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "세션 ${session.metadata.id} 부가 데이터 로드 실패: ${e.message}")
                RunningSession(
                    id = session.metadata.id,
                    startTime = session.startTime,
                    endTime = session.endTime,
                    deviceModel = session.metadata.device?.model ?: "unknown",
                    exerciseType = session.exerciseType
                )
            }
        }
    }

    private suspend fun readAllExerciseSessionPages(
        start: Instant,
        end: Instant
    ): List<ExerciseSessionRecord> {
        val result = mutableListOf<ExerciseSessionRecord>()
        var pageToken: String? = null

        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = pageToken
                )
            )
            result.addAll(response.records)
            pageToken = response.pageToken
            Log.d(TAG, "페이지 로드: ${response.records.size}건, 다음 토큰: $pageToken")
        } while (pageToken != null)

        return result
    }

    suspend fun getHeartRateInSession(start: Instant, end: Instant): List<HeartRateSample> {
        val rawSamples = mutableListOf<HeartRateRecord.Sample>()
        var pageToken: String? = null

        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = pageToken
                )
            )
            rawSamples.addAll(response.records.flatMap { it.samples })
            pageToken = response.pageToken
        } while (pageToken != null)

        return rawSamples
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
