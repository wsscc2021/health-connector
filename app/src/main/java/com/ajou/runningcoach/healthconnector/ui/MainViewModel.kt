package com.ajou.runningcoach.healthconnector.ui

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ajou.runningcoach.healthconnector.SessionUploader
import com.ajou.runningcoach.healthconnector.data.HealthDataRepository
import com.ajou.runningcoach.healthconnector.data.model.RunningSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant

class MainViewModel(
    private val client: HealthConnectClient,
    private val repo: HealthDataRepository,
    private val uploader: SessionUploader
) : ViewModel() {

    val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    private val _sessions = MutableStateFlow<List<RunningSession>>(emptyList())
    val sessions: StateFlow<List<RunningSession>> = _sessions

    fun syncSessions(daysBack: Long = 7) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading("세션 조회 중...")

            runCatching {
                repo.getExerciseSessions(
                    start = Instant.now().minusSeconds(daysBack * 86400),
                    end = Instant.now()
                )
            }.onFailure { e ->
                _uiState.value = UiState.Error(e.message ?: "알 수 없는 오류")
                return@launch
            }.onSuccess { sessions ->
                _sessions.value = sessions

                if (sessions.isEmpty()) {
                    _uiState.value = UiState.Done(uploaded = 0)
                    return@launch
                }

                var uploaded = 0
                sessions.forEachIndexed { idx, session ->
                    _uiState.value = UiState.Loading("업로드 중... (${idx + 1}/${sessions.size})")
                    uploader.upload(session).onSuccess { uploaded++ }
                }

                _uiState.value = UiState.Done(uploaded)
            }
        }
    }

    suspend fun hasPermissions(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }
}

sealed class UiState {
    object Idle : UiState()
    data class Loading(val message: String) : UiState()
    data class Done(val uploaded: Int) : UiState()
    data class Error(val message: String) : UiState()
}
