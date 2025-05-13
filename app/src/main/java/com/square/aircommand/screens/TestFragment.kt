package com.square.aircommand.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.square.aircommand.databinding.FragmentTestBinding
import com.square.aircommand.handdetector.HandDetector
import com.square.aircommand.tflite.TFLiteHelpers
import com.square.aircommand.utils.toBitmapCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class TestFragment : Fragment() {

    private var _binding: FragmentTestBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var handDetector: HandDetector? = null
    private val isProcessing = AtomicBoolean(false)

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 10
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            initHandDetector()
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }

        binding.btnFrontCamera.setOnClickListener {
            lensFacing = CameraSelector.LENS_FACING_FRONT
            startCamera()
        }

        binding.btnBackCamera.setOnClickListener {
            lensFacing = CameraSelector.LENS_FACING_BACK
            startCamera()
        }
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    private fun initHandDetector() {
        handDetector = HandDetector(
            context = requireContext(),
            modelPath = "mediapipe_hand-handdetector.tflite",
            delegatePriorityOrder = arrayOf(
                arrayOf(TFLiteHelpers.DelegateType.QNN_NPU),
                arrayOf(TFLiteHelpers.DelegateType.GPUv2),
                arrayOf() // CPU fallback (XNNPack)
            )
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { image ->
                        if (!isProcessing.get()) {
                            isProcessing.set(true)
                            val bitmap = image.toBitmapCompat()
                            val handPoints: List<PointF> = handDetector?.detect(bitmap) ?: emptyList()
                            Log.d("TestFragment", "🔍 감지된 손 위치: $handPoints")
                            isProcessing.set(false)
                        }
                        image.close()
                    }
                }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("TestFragment", "카메라 바인딩 실패", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraExecutor.shutdown()
        handDetector?.close()
    }
}
