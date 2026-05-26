package com.ajou.runningcoach.healthconnector.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ajou.runningcoach.healthconnector.SessionUploader
import com.ajou.runningcoach.healthconnector.data.HealthDataRepository
import com.ajou.runningcoach.healthconnector.data.remote.ApiClient
import com.ajou.runningcoach.healthconnector.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var client: HealthConnectClient
    private val adapter = SessionAdapter()

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            client,
            HealthDataRepository(client),
            SessionUploader(ApiClient.bioApi, USER_ID)
        )
    }

    private val requestPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            if (granted.containsAll(viewModel.permissions)) {
                viewModel.loadSessions()
            } else {
                val missing = viewModel.permissions - granted
                showError(
                    title = "권한 허용 필요",
                    message = "다음 권한이 허용되지 않았습니다:\n" +
                        missing.joinToString("\n") { "• ${it.substringAfterLast('.')}" } +
                        "\n\nHealth Connect 앱 → 앱 권한 → 이 앱에서 다시 설정해 주세요.",
                    action = ErrorAction.OpenPermissions
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_UNAVAILABLE) {
            showInstallPrompt()
            return
        }

        client = HealthConnectClient.getOrCreate(this)
        binding.rvSessions.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.rvSessions.adapter = adapter

        binding.btnLoad.setOnClickListener {
            hideError()
            lifecycleScope.launch {
                try {
                    adapter.clearSelection()
                    if (viewModel.hasPermissions()) {
                        viewModel.loadSessions()
                    } else {
                        requestPermissions.launch(viewModel.permissions)
                    }
                } catch (e: Exception) {
                    showError(
                        title = "초기화 오류",
                        message = "Health Connect 연결 중 오류가 발생했습니다.\n${e::class.simpleName}: ${e.message}"
                    )
                }
            }
        }

        binding.btnUpload.setOnClickListener {
            hideError()
            viewModel.uploadSelected(adapter.getSelectedIds())
        }

        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        // 각 상태 처리에서 예외가 발생해도 collect 자체는 유지
                        runCatching { handleState(state) }
                    }
                }
                launch {
                    viewModel.sessions.collect { sessions ->
                        runCatching {
                            adapter.submitList(sessions)
                            binding.tvEmpty.visibility =
                                if (sessions.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun handleState(state: UiState) {
        val isLoading = state is UiState.Loading
        binding.btnLoad.isEnabled = !isLoading
        binding.btnUpload.isEnabled = !isLoading

        when (state) {
            is UiState.Idle -> {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text =
                    if (viewModel.sessions.value.isEmpty())
                        "세션 불러오기 버튼을 눌러 시작하세요."
                    else
                        "세션을 선택한 후 '선택 업로드'를 눌러주세요."
            }
            is UiState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.tvStatus.text = state.message
                hideError()
            }
            is UiState.Done -> {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = "${state.uploaded}개 세션 업로드 완료"
                adapter.clearSelection()
                hideError()
            }
            is UiState.Empty -> {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = "세션 없음"
                binding.tvEmpty.text = state.message
                binding.tvEmpty.visibility = View.VISIBLE
                hideError()
                showEmptySessionDialog(state.action)
            }
            is UiState.Error -> {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = state.title
                when (state.style) {
                    // 가벼운 안내는 Snackbar로
                    ErrorStyle.Snackbar -> Snackbar
                        .make(binding.root, state.message, Snackbar.LENGTH_LONG)
                        .show()
                    // 세션 로드 실패 등 중요한 오류는 항상 화면에 표시
                    ErrorStyle.Dialog -> showError(state.title, state.message, state.action)
                }
            }
        }
    }

    // 에러 카드를 화면에 표시 — 다이얼로그와 달리 항상 보임
    private fun showError(
        title: String,
        message: String,
        action: ErrorAction = ErrorAction.None
    ) {
        binding.cardError.visibility = View.VISIBLE
        binding.tvErrorTitle.text = title
        binding.tvErrorMessage.text = message

        when (action) {
            is ErrorAction.OpenPermissions -> {
                binding.btnErrorAction.visibility = View.VISIBLE
                binding.btnErrorAction.text = "권한 설정 열기"
                binding.btnErrorAction.setOnClickListener { openHealthConnectPermissions() }
            }
            is ErrorAction.OpenSamsungHealth -> {
                binding.btnErrorAction.visibility = View.VISIBLE
                binding.btnErrorAction.text = "Samsung Health 열기"
                binding.btnErrorAction.setOnClickListener { openSamsungHealth() }
            }
            is ErrorAction.Retry -> {
                binding.btnErrorAction.visibility = View.VISIBLE
                binding.btnErrorAction.text = "다시 시도"
                binding.btnErrorAction.setOnClickListener {
                    hideError()
                    viewModel.loadSessions()
                }
            }
            is ErrorAction.None -> binding.btnErrorAction.visibility = View.GONE
        }
    }

    private fun showEmptySessionDialog(action: ErrorAction) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("세션 없음")
            .setMessage(
                "불러온 세션이 없습니다.\n\n" +
                "Samsung Health → 설정 → Health Connect 연동이\n" +
                "활성화되어 있는지 확인해 주세요."
            )
            .setNegativeButton("닫기", null)

        if (action is ErrorAction.OpenSamsungHealth) {
            builder.setPositiveButton("Samsung Health 열기") { _, _ -> openSamsungHealth() }
        }

        builder.show()
    }

    private fun hideError() {
        binding.cardError.visibility = View.GONE
    }

    private fun openHealthConnectPermissions() {
        try {
            startActivity(Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"))
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Health Connect 앱을 찾을 수 없습니다.", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun openSamsungHealth() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.sec.android.app.shealth")
            if (intent != null) startActivity(intent)
            else Snackbar.make(binding.root, "Samsung Health 앱을 찾을 수 없습니다.", Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Samsung Health 앱을 열 수 없습니다.", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showInstallPrompt() {
        binding.tvStatus.text = "Health Connect 앱 설치가 필요합니다."
        binding.btnLoad.text = "Health Connect 설치"
        binding.btnUpload.visibility = View.GONE
        binding.btnLoad.setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(
                        "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"
                    )
                }
            )
        }
    }

    companion object {
        private const val USER_ID = "U123"
    }
}
