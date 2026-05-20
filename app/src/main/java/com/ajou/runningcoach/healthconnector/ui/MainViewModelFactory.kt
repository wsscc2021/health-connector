package com.ajou.runningcoach.healthconnector.ui

import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ajou.runningcoach.healthconnector.SessionUploader
import com.ajou.runningcoach.healthconnector.data.HealthDataRepository

class MainViewModelFactory(
    private val client: HealthConnectClient,
    private val repo: HealthDataRepository,
    private val uploader: SessionUploader
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(client, repo, uploader) as T
    }
}
