package com.ajou.runningcoach.healthconnector.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationChannelCompat
import com.ajou.runningcoach.healthconnector.R

class UploadNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "upload_results"
        private const val NOTIFICATION_ID = 1001
    }

    fun createChannel() {
        val channel = NotificationChannelCompat
            .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("업로드 결과")
            .setDescription("세션 데이터 업로드 결과를 알립니다")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun notifySuccess(uploadedCount: Int) {
        show(
            title = "업로드 완료",
            text = "${uploadedCount}개 세션 데이터가 성공적으로 업로드되었습니다."
        )
    }

    fun notifyFailure(message: String) {
        show(title = "업로드 실패", text = message)
    }

    private fun show(title: String, text: String) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
