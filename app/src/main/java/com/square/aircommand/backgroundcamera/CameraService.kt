package com.square.aircommand.backgroundcamera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class CameraService : Service() {

    private val TAG = "CameraService"
    private val channelId = "camera_service_channel"

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(1, createNotification())
        }

        startCamera()
        startRepeatingLog()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                preview.setSurfaceProvider(SurfaceProviderHolder.create(this))
                cameraProvider.bindToLifecycle(DummyLifecycleOwner(), cameraSelector, preview)
            } catch (_: Exception) {
                // 오류 로그 생략
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRepeatingLog() {
        serviceScope.launch {
            while (isActive) {
                Log.d(TAG, "📸 백그라운드 카메라 실행 중")
                delay(1000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotification(): Notification {
        return Notification.Builder(this, channelId)
            .setContentTitle("Camera Service")
            .setContentText("Camera is running in background.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Camera Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}