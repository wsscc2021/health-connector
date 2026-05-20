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
                viewModel.syncSessions()
            } else {
                Snackbar.make(binding.root, "Health Connect 권한이 필요합니다.", Snackbar.LENGTH_LONG).show()
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

        binding.btnSync.setOnClickListener {
            lifecycleScope.launch {
                if (viewModel.hasPermissions()) {
                    viewModel.syncSessions()
                } else {
                    requestPermissions.launch(viewModel.permissions)
                }
            }
        }

        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is UiState.Idle -> {
                                binding.progressBar.visibility = View.GONE
                                binding.tvStatus.text = "동기화 버튼을 눌러 시작하세요."
                            }
                            is UiState.Loading -> {
                                binding.progressBar.visibility = View.VISIBLE
                                binding.tvStatus.text = state.message
                            }
                            is UiState.Done -> {
                                binding.progressBar.visibility = View.GONE
                                binding.tvStatus.text = "${state.uploaded}개 세션 업로드 완료"
                            }
                            is UiState.Error -> {
                                binding.progressBar.visibility = View.GONE
                                binding.tvStatus.text = "오류: ${state.message}"
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
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

    private fun showInstallPrompt() {
        binding.tvStatus.text = "Health Connect 앱 설치가 필요합니다."
        binding.btnSync.text = "Health Connect 설치"
        binding.btnSync.setOnClickListener {
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
