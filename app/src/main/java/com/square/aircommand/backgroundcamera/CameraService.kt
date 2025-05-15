package com.square.aircommand.backgroundcamera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PointF
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.square.aircommand.classifier.GestureClassifier
import com.square.aircommand.classifier.GestureLabelMapper
import com.square.aircommand.handdetector.HandDetector
import com.square.aircommand.handlandmarkdetector.HandLandmarkDetector
import com.square.aircommand.utils.ThrottledLogger
import com.square.aircommand.utils.toBitmapCompat
import com.square.aircommand.camera.HandAnalyzer
import com.square.aircommand.camera.getBackCameraSensorOrientation
import com.square.aircommand.tflite.TFLiteHelpers
import kotlinx.coroutines.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.Executors

class CameraService : Service() {

    private val tag = "CameraService"
    private val channelId = "camera_service_channel"

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var handDetector: HandDetector
    private lateinit var landmarkDetector: HandLandmarkDetector
    private lateinit var gestureClassifier: GestureClassifier
    private lateinit var handAnalyzer: ImageAnalysis.Analyzer

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(1, createNotification())
        }

        initModels()
        initAnalyzer()
        startCamera()
    }

    private fun initModels() {
        val delegateOrder = arrayOf(
            arrayOf(TFLiteHelpers.DelegateType.QNN_NPU),
            arrayOf(TFLiteHelpers.DelegateType.GPUv2),
            arrayOf()
        )

        handDetector = HandDetector(this, "mediapipe_hand-handdetector.tflite", delegateOrder)
        landmarkDetector = HandLandmarkDetector(this, "mediapipe_hand-handlandmarkdetector.tflite", delegateOrder)
        gestureClassifier = GestureClassifier(this, "update_gesture_model_cnn.tflite", delegateOrder)
    }

    private fun initAnalyzer() {
        val gestureText = mutableStateOf("제스처 없음")
        val detectionFrameCount = mutableIntStateOf(0)
        val latestPoints = mutableStateListOf<PointF>()
        val landmarksState = mutableStateOf<List<Triple<Double, Double, Double>>>(emptyList())

        handAnalyzer = HandAnalyzer(
            context = this,
            handDetector = handDetector,
            landmarkDetector = landmarkDetector,
            gestureClassifier = gestureClassifier,
            gestureLabelMapper = GestureLabelMapper(this),
            gestureText = gestureText,
            detectionFrameCount = detectionFrameCount,
            latestPoints = latestPoints,
            landmarksState = landmarksState,
            validDetectionThreshold = 10
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor(), handAnalyzer)
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    DummyLifecycleOwner(),
                    cameraSelector,
                    analysis
                )
                Log.i(tag, "📸 백그라운드 카메라 및 분석기 연결 완료")
            } catch (e: Exception) {
                Log.e(tag, "❌ 카메라 연결 실패: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        handDetector.close()
        landmarkDetector.close()
        gestureClassifier.close()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotification(): Notification {
        return Notification.Builder(this, channelId)
            .setContentTitle("Gesture Camera Service")
            .setContentText("손 제스처를 백그라운드에서 분석 중입니다.")
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
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}