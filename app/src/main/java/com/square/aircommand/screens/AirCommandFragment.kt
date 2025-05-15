package com.square.aircommand.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.square.aircommand.R
import com.square.aircommand.backgroundcamera.CameraService
import com.square.aircommand.databinding.FragmentAirCommandBinding

class AirCommandFragment : Fragment() {

    private var _binding: FragmentAirCommandBinding? = null
    private val binding get() = _binding!!

    private val timeOptions = listOf("설정 안 함", "1시간", "2시간", "4시간", "끄지 않음")

    private val CAMERA_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.FOREGROUND_SERVICE_CAMERA
    )

    private val REQUEST_CAMERA_PERMISSIONS = 1001

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAirCommandBinding.inflate(inflater, container, false)

        val prefs = requireContext().getSharedPreferences("air_command_prefs", Context.MODE_PRIVATE)
        val savedTime = prefs.getString("selected_time", "설정 안 함")
        binding.btnSelectTime.text = savedTime

        binding.btnSelectTime.setOnClickListener {
            val popup = PopupMenu(requireContext(), binding.btnSelectTime)
            timeOptions.forEachIndexed { index, option ->
                popup.menu.add(0, index, index, option)
            }

            popup.setOnMenuItemClickListener { item ->
                val selectedTime = timeOptions[item.itemId]
                binding.btnSelectTime.text = selectedTime
                prefs.edit().putString("selected_time", selectedTime).apply()
                true
            }

            popup.show()
        }

        binding.switchUse.setOnCheckedChangeListener { _, isChecked ->
            binding.tvUseStatus.text = if (isChecked) "사용 중" else "사용 안 함"
        }

        // 백그라운드 카메라 서비스 토글 스위치
        binding.switchCamera.setOnCheckedChangeListener { _, isChecked ->
            val context = requireContext()
            val intent = Intent(context, CameraService::class.java)

            if (isChecked) {
                if (hasCameraPermissions()) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        CAMERA_PERMISSIONS,
                        REQUEST_CAMERA_PERMISSIONS
                    )
                }
            } else {
                context.stopService(intent)
            }
        }

        binding.btnGestureSetting.setOnClickListener {
            findNavController().navigate(R.id.action_airCommand_to_gestureSetting)
        }

        binding.btnUserGesture.setOnClickListener {
            findNavController().navigate(R.id.action_airCommand_to_userGesture)
        }

        binding.btnTest.setOnClickListener {
            findNavController().navigate(R.id.action_airCommand_to_testFragment)
        }

        return binding.root
    }

    // 백그라운드 카메라 권한 확인 함수
    private fun hasCameraPermissions(): Boolean {
        val context = requireContext()
        return CAMERA_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // 백그라운드 카메라 권한 요청 결과 처리
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CAMERA_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                val intent = Intent(requireContext(), CameraService::class.java)
                ContextCompat.startForegroundService(requireContext(), intent)
            } else {
                binding.switchCamera.isChecked = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}