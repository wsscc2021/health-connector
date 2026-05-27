package com.ajou.runningcoach.healthconnector.data

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.ajou.runningcoach.healthconnector.data.model.CadenceSample
import com.ajou.runningcoach.healthconnector.data.model.HeartRateSample
import com.ajou.runningcoach.healthconnector.data.model.OxygenSaturationSample
import com.ajou.runningcoach.healthconnector.data.model.RunningSession
import com.ajou.runningcoach.healthconnector.data.model.SpeedSample
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

        val result = mutableListOf<RunningSession>()
        for (session in allRecords) {
            // 각 센서를 독립적으로 fetch — 하나가 실패해도 나머지 데이터는 보존
            val heartRates = fetchOrEmpty(session.metadata.id, "heart_rate") {
                getHeartRateInSession(session.startTime, session.endTime)
            }
            val cadences = fetchOrEmpty(session.metadata.id, "cadence") {
                getCadenceInSession(session.startTime, session.endTime)
            }
            val speeds = fetchOrEmpty(session.metadata.id, "speed") {
                getSpeedInSession(session.startTime, session.endTime)
            }
            val oxygenSaturations = fetchOrEmpty(session.metadata.id, "oxygen_saturation") {
                getOxygenSaturationInSession(session.startTime, session.endTime)
            }
            val steps = fetchOrZero(session.metadata.id, "steps") {
                getStepsInSession(session.startTime, session.endTime)
            }
            val distance = fetchOrZeroDouble(session.metadata.id, "distance") {
                getDistanceInSession(session.startTime, session.endTime)
            }
            result.add(
                RunningSession(
                    id = session.metadata.id,
                    startTime = session.startTime,
                    endTime = session.endTime,
                    deviceModel = session.metadata.device?.model ?: "unknown",
                    exerciseType = session.exerciseType,
                    heartRateSamples = heartRates,
                    cadenceSamples = cadences,
                    speedSamples = speeds,
                    oxygenSaturationSamples = oxygenSaturations,
                    totalSteps = steps,
                    totalDistanceMeters = distance
                )
            )
        }
        return result
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

    suspend fun getCadenceInSession(start: Instant, end: Instant): List<CadenceSample> {
        // READ_STEPS_CADENCE 권한 미승인 시 SecurityException이 발생하므로
        // 개별 try-catch 처리 — 예외가 바깥 catch에 탈출하면 폴백이 실행되지 않음
        val fromCadenceRecord = try {
            readStepsCadenceRecords(start, end)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "StepsCadenceRecord 읽기 실패 (${e::class.simpleName}): ${e.message}")
            emptyList()
        }

        if (fromCadenceRecord.isNotEmpty()) {
            Log.d(TAG, "StepsCadenceRecord에서 케이던스 ${fromCadenceRecord.size}건 로드")
            return fromCadenceRecord
        }

        // Samsung Health는 StepsCadenceRecord를 HC에 동기화하지 않으므로
        // StepsRecord(1분 단위)에서 steps/min을 파생
        Log.d(TAG, "StepsCadenceRecord 없음 → StepsRecord에서 케이던스 파생")
        return deriveFromStepsRecord(start, end)
    }

    private suspend fun readStepsCadenceRecords(start: Instant, end: Instant): List<CadenceSample> {
        val rawSamples = mutableListOf<StepsCadenceRecord.Sample>()
        var pageToken: String? = null

        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsCadenceRecord::class,
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
                CadenceSample(
                    timestamp = Instant.ofEpochSecond(minuteBucket * 60),
                    stepsPerMinute = samples.map { it.rate }.average()
                )
            }
            .sortedBy { it.timestamp }
    }

    private suspend fun deriveFromStepsRecord(start: Instant, end: Instant): List<CadenceSample> {
        val records = mutableListOf<StepsRecord>()
        var pageToken: String? = null

        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = pageToken
                )
            )
            records.addAll(response.records)
            pageToken = response.pageToken
        } while (pageToken != null)

        if (records.isNotEmpty()) {
            Log.d(TAG, "StepsRecord ${records.size}건 → 분당 케이던스 계산")
            return records
                .groupBy { it.startTime.epochSecond / 60 }
                .map { (minuteBucket, bucketRecords) ->
                    val totalSteps = bucketRecords.sumOf { it.count }
                    val totalSeconds = bucketRecords
                        .sumOf { it.endTime.epochSecond - it.startTime.epochSecond }
                        .coerceAtLeast(1)
                    CadenceSample(
                        timestamp = Instant.ofEpochSecond(minuteBucket * 60),
                        stepsPerMinute = totalSteps.toDouble() / totalSeconds * 60.0
                    )
                }
                .sortedBy { it.timestamp }
        }

        // Samsung Health는 운동 중 StepsRecord를 세션 단위가 아닌 일별 집계로 HC에 동기화함.
        // ReadRecordsRequest 필터가 세션 시작 전에 시작된 일별 레코드를 제외하므로
        // AggregateRequest(겹치는 레코드 포함)로 총 걸음 수를 가져와 평균 케이던스로 대체.
        Log.d(TAG, "StepsRecord 없음 → AggregateRequest로 평균 케이던스 계산")
        val aggResponse = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        val totalSteps = aggResponse[StepsRecord.COUNT_TOTAL] ?: 0L
        if (totalSteps <= 0L) {
            Log.d(TAG, "스텝 데이터 없음 → 케이던스 계산 불가")
            return emptyList()
        }

        val durationSeconds = (end.epochSecond - start.epochSecond).coerceAtLeast(1)
        val stepsPerMinute = totalSteps.toDouble() / durationSeconds * 60.0
        Log.d(TAG, "평균 케이던스: %.1f spm (totalSteps=$totalSteps, duration=${durationSeconds}s)".format(stepsPerMinute))
        return listOf(
            CadenceSample(
                timestamp = start,
                stepsPerMinute = stepsPerMinute
            )
        )
    }

    suspend fun getSpeedInSession(start: Instant, end: Instant): List<SpeedSample> {
        val rawSamples = mutableListOf<SpeedRecord.Sample>()
        var pageToken: String? = null

        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SpeedRecord::class,
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
                SpeedSample(
                    timestamp = Instant.ofEpochSecond(minuteBucket * 60),
                    metersPerSecond = samples.map { it.speed.inMetersPerSecond }.average()
                )
            }
            .sortedBy { it.timestamp }
    }

    suspend fun getOxygenSaturationInSession(start: Instant, end: Instant): List<OxygenSaturationSample> {
        val records = mutableListOf<OxygenSaturationRecord>()
        var pageToken: String? = null

        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = pageToken
                )
            )
            records.addAll(response.records)
            pageToken = response.pageToken
        } while (pageToken != null)

        Log.d(TAG, "OxygenSaturationRecord ${records.size}건 로드 (Samsung Health는 운동 중 SpO2를 연속 측정하지 않으면 0건)")
        return records
            .map { record ->
                OxygenSaturationSample(
                    timestamp = record.time,
                    percentage = record.percentage.value
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

    private suspend fun <T> fetchOrEmpty(
        sessionId: String,
        name: String,
        fetch: suspend () -> List<T>
    ): List<T> = try {
        fetch()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "세션 $sessionId $name 로드 실패 (${e::class.simpleName}): ${e.message}")
        emptyList()
    }

    private suspend fun fetchOrZero(
        sessionId: String,
        name: String,
        fetch: suspend () -> Long
    ): Long = try {
        fetch()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "세션 $sessionId $name 로드 실패 (${e::class.simpleName}): ${e.message}")
        0L
    }

    private suspend fun fetchOrZeroDouble(
        sessionId: String,
        name: String,
        fetch: suspend () -> Double
    ): Double = try {
        fetch()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "세션 $sessionId $name 로드 실패 (${e::class.simpleName}): ${e.message}")
        0.0
    }
}
