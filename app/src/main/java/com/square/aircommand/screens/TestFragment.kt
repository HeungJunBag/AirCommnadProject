package com.square.aircommand.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.square.aircommand.camera.CameraScreen
import com.square.aircommand.classifier.GestureClassifier
import com.square.aircommand.databinding.FragmentTestBinding
import com.square.aircommand.handdetector.HandDetector
import com.square.aircommand.handlandmarkdetector.HandLandmarkDetector
import com.square.aircommand.tflite.TFLiteHelpers

class TestFragment : Fragment() {

    private var _binding: FragmentTestBinding? = null
    private val binding get() = _binding!!

    private lateinit var handDetector: HandDetector
    private lateinit var landmarkDetector: HandLandmarkDetector
    private lateinit var gestureClassifier: GestureClassifier

    companion object {
        // 카메라 권한 요청 식별 코드
        private const val CAMERA_PERMISSION_REQUEST_CODE = 10
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // ViewBinding 초기화
        _binding = FragmentTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 뒤로가기 버튼 클릭 처리
        binding.backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // ✅ 카메라 권한이 있으면 모델 초기화 및 카메라 화면 표시
        if (allPermissionsGranted()) {
            initModels()
            showCameraCompose()
        } else {
            // ❌ 카메라 권한이 없으면 권한 요청
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    // ✅ 카메라 권한이 부여되어 있는지 확인하는 함수
    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    // ✅ AI 모델 초기화 함수 (TFLite + QNN 우선순위 적용)
    private fun initModels() {
        val delegateOrder = arrayOf(
            arrayOf(TFLiteHelpers.DelegateType.QNN_NPU),
            arrayOf(TFLiteHelpers.DelegateType.GPUv2),
            arrayOf() // CPU fallback
        )

        // 손 검출 모델 초기화
        handDetector = HandDetector(
            context = requireContext(),
            modelPath = "mediapipe_hand-handdetector.tflite",
            delegatePriorityOrder = delegateOrder
        )

        // 손 랜드마크 모델 초기화
        landmarkDetector = HandLandmarkDetector(
            context = requireContext(),
            modelPath = "mediapipe_hand-handlandmarkdetector.tflite",
            delegatePriorityOrder = delegateOrder
        )

        // 제스처 분류 모델 초기화
        gestureClassifier = GestureClassifier(
            context = requireContext(),
            modelPath = "update_gesture_model_cnn.tflite",
            delegatePriorityOrder = delegateOrder
        )
    }

    // ✅ Jetpack Compose 기반 카메라 뷰 출력
    private fun showCameraCompose() {
        binding.landmarkOverlay.setContent {
            CameraScreen(
                handDetector = handDetector,
                landmarkDetector = landmarkDetector,
                gestureClassifier = gestureClassifier
            )
        }
    }

    // View가 파괴될 때 리소스 정리 및 메모리 누수 방지
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        handDetector.close()
        landmarkDetector.close()
        gestureClassifier.close()
    }
}
