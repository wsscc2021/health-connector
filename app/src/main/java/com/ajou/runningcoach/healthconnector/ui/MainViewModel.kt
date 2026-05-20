package com.ajou.runningcoach.healthconnector.ui

import android.util.Log
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset

class MainViewModel(
    private val client: HealthConnectClient,
    private val repo: HealthDataRepository,
    private val uploader: SessionUploader
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"

        // Instant.EPOCH(1970년)은 일부 HC 구현에서 거부되므로 안전한 시작일 사용
        private val SESSION_QUERY_START =
            LocalDate.of(2020, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
    }

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

    fun loadSessions() {
        viewModelScope.launch {
            // 외부 try-catch: hasPermissions() 등 어디서든 예외가 발생해도 반드시 에러 상태로 전환
            try {
                _uiState.value = UiState.Loading("세션 불러오는 중...")

                val sessions = try {
                    repo.getExerciseSessions(
                        start = SESSION_QUERY_START,
                        end = Instant.now()
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "세션 로드 실패: ${e::class.simpleName} - ${e.message}", e)
                    _uiState.value = classifyReadError(e)
                    return@launch
                }

                _sessions.value = sessions
                Log.d(TAG, "로드된 세션 수: ${sessions.size}")

                _uiState.value = if (sessions.isEmpty()) {
                    UiState.Error(
                        title = "러닝 세션 없음",
                        message = "2020년 이후 저장된 러닝 세션을 찾을 수 없습니다.\n\n확인해 주세요:\n" +
                            "• Samsung Health → 설정 → Health Connect 연동 활성화\n" +
                            "• Health Connect 앱에서 Samsung Health 데이터 공유 허용\n" +
                            "• Samsung Health에서 러닝 운동이 기록되어 있는지 확인",
                        action = ErrorAction.OpenSamsungHealth
                    )
                } else {
                    UiState.Idle
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadSessions 예기치 못한 오류: ${e::class.simpleName} - ${e.message}", e)
                _uiState.value = classifyReadError(e)
            }
        }
    }

    fun uploadSelected(selectedIds: Set<String>) {
        if (selectedIds.isEmpty()) {
            _uiState.value = UiState.Error(
                title = "선택된 세션 없음",
                message = "업로드할 세션을 하나 이상 선택해 주세요.",
                action = ErrorAction.None,
                style = ErrorStyle.Snackbar
            )
            return
        }

        viewModelScope.launch {
            try {
                val targets = _sessions.value.filter { it.id in selectedIds }

                var uploaded = 0
                var failed = 0

                targets.forEachIndexed { idx, session ->
                    _uiState.value = UiState.Loading("업로드 중... (${idx + 1}/${targets.size})")
                    try {
                        uploader.upload(session).getOrThrow()
                        uploaded++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        failed++
                        _uiState.value = UiState.Error(
                            title = "네트워크 오류",
                            message = "서버에 연결할 수 없습니다.\n인터넷 연결을 확인한 후 다시 시도해 주세요.",
                            action = ErrorAction.Retry
                        )
                        return@launch
                    } catch (e: Exception) {
                        failed++
                        Log.e(TAG, "세션 업로드 실패: ${e.message}", e)
                    }
                }

                _uiState.value = if (failed == 0) {
                    UiState.Done(uploaded = uploaded)
                } else {
                    UiState.Error(
                        title = "일부 업로드 실패",
                        message = "${targets.size}개 세션 중 ${failed}개 업로드에 실패했습니다.\n잠시 후 다시 시도해 주세요.",
                        action = ErrorAction.Retry
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "uploadSelected 예기치 못한 오류: ${e::class.simpleName} - ${e.message}", e)
                _uiState.value = UiState.Error(
                    title = "업로드 오류",
                    message = "업로드 중 예기치 못한 오류가 발생했습니다.\n${e::class.simpleName}: ${e.message}",
                    action = ErrorAction.Retry
                )
            }
        }
    }

    private fun classifyReadError(e: Exception): UiState.Error {
        val detail = "${e::class.simpleName}: ${e.message}"
        return when (e) {
            is SecurityException -> UiState.Error(
                title = "권한 오류",
                message = "Health Connect 데이터에 접근할 권한이 없습니다.\n권한 설정을 다시 확인해 주세요.\n\n[${detail}]",
                action = ErrorAction.OpenPermissions
            )
            is IOException -> UiState.Error(
                title = "연결 오류",
                message = "Health Connect와 통신 중 오류가 발생했습니다.\n잠시 후 다시 시도해 주세요.\n\n[${detail}]",
                action = ErrorAction.Retry
            )
            else -> UiState.Error(
                title = "세션 로드 실패",
                message = "세션을 불러오는 중 예상치 못한 오류가 발생했습니다.\n\n[${detail}]",
                action = ErrorAction.Retry
            )
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
    data class Error(
        val title: String,
        val message: String,
        val action: ErrorAction = ErrorAction.None,
        val style: ErrorStyle = ErrorStyle.Dialog
    ) : UiState()
}

enum class ErrorStyle { Dialog, Snackbar }

sealed class ErrorAction {
    object None : ErrorAction()
    object OpenPermissions : ErrorAction()
    object OpenSamsungHealth : ErrorAction()
    object Retry : ErrorAction()
}
