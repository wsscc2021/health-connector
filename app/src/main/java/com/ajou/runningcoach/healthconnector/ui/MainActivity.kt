package com.ajou.runningcoach.healthconnector.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
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
                // 일부 권한만 허용된 경우 어떤 권한이 빠졌는지 확인 가능하도록 안내
                val missing = viewModel.permissions - granted
                showDialog(
                    title = "권한 허용 필요",
                    message = "다음 권한이 허용되지 않았습니다:\n${missing.joinToString("\n") { "• ${it.substringAfterLast('.')}" }}\n\n" +
                        "Health Connect 앱 → 앱 권한 → 이 앱에서 다시 설정해 주세요.",
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
        binding.rvSessions.adapter = adapter

        binding.btnLoad.setOnClickListener {
            lifecycleScope.launch {
                adapter.clearSelection()
                if (viewModel.hasPermissions()) {
                    viewModel.loadSessions()
                } else {
                    requestPermissions.launch(viewModel.permissions)
                }
            }
        }

        binding.btnUpload.setOnClickListener {
            viewModel.uploadSelected(adapter.getSelectedIds())
        }

        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        val isLoading = state is UiState.Loading
                        // 로딩 중 버튼 비활성화로 중복 요청 방지
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
                            }
                            is UiState.Done -> {
                                binding.progressBar.visibility = View.GONE
                                binding.tvStatus.text = "${state.uploaded}개 세션 업로드 완료"
                                adapter.clearSelection()
                            }
                            is UiState.Error -> {
                                binding.progressBar.visibility = View.GONE
                                binding.tvStatus.text = state.title
                                when (state.style) {
                                    ErrorStyle.Snackbar -> showSnackbar(state.message)
                                    ErrorStyle.Dialog -> showDialog(state.title, state.message, state.action)
                                }
                            }
                        }
                    }
                }
                launch {
                    viewModel.sessions.collect { sessions ->
                        adapter.submitList(sessions)
                        binding.tvEmpty.visibility =
                            if (sessions.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showDialog(title: String, message: String, action: ErrorAction) {
        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("닫기", null)

        when (action) {
            is ErrorAction.OpenPermissions -> builder.setPositiveButton("권한 설정") { _, _ ->
                openHealthConnectPermissions()
            }
            is ErrorAction.OpenSamsungHealth -> builder.setPositiveButton("Samsung Health 열기") { _, _ ->
                openSamsungHealth()
            }
            is ErrorAction.Retry -> builder.setPositiveButton("다시 시도") { _, _ ->
                viewModel.loadSessions()
            }
            is ErrorAction.None -> Unit
        }

        builder.show()
    }

    private fun openHealthConnectPermissions() {
        try {
            startActivity(Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"))
        } catch (e: Exception) {
            showSnackbar("Health Connect 앱을 찾을 수 없습니다.")
        }
    }

    private fun openSamsungHealth() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.sec.android.app.shealth")
            if (intent != null) startActivity(intent)
            else showSnackbar("Samsung Health 앱을 찾을 수 없습니다.")
        } catch (e: Exception) {
            showSnackbar("Samsung Health 앱을 열 수 없습니다.")
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
