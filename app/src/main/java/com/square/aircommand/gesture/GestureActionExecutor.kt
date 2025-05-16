package com.square.aircommand.gesture

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import com.square.aircommand.utils.ThrottledLogger

/**
 * 제스처에 대응하는 실제 기능을 실행하는 객체
 */
object GestureActionExecutor {

    /**
     * 지정된 제스처 동작을 실행
     */
    fun execute(action: GestureAction, context: Context) {
        when (action) {
            GestureAction.VOLUME_UP -> adjustVolume(context, true)
            GestureAction.VOLUME_DOWN -> adjustVolume(context, false)
            GestureAction.TOGGLE_FLASH -> toggleFlash()
            GestureAction.TAKE_PHOTO -> takePhoto()
            GestureAction.SCREENSHOT -> takeScreenshot(context)
            GestureAction.NONE -> ThrottledLogger.log("GestureAction", "🛑제스처에 아무 기능도 할당되지 않음")
        }
    }

    // TODO: 볼륨 제어 속도 너무 빠름 조정 필요
    /**
     * 볼륨 조절 함수
     * @param up true면 볼륨 올리기, false면 볼륨 내리기
     * @flag FLAG_SHOW_UI를 적용하여 시스템 볼륨 UI도 함께 표시
     */
    private fun adjustVolume(context: Context, up: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI // 👉 볼륨 슬라이더 UI 표시
        )
        Log.d("GestureAction", if (up) "🔊 볼륨 증가" else "🔉 볼륨 감소")
    }

    /**
     * 플래시 토글 기능 (추후 구현 필요)
     */
    private fun toggleFlash() {
        Log.d("GestureAction", "💡 플래시 토글 기능은 아직 구현되지 않았습니다.")
        // TODO: 실제 플래시 제어 로직 추가
    }

    /**
     * 사진 촬영 기능 (추후 구현 필요)
     */
    private fun takePhoto() {
        Log.d("GestureAction", "📷 사진 촬영 기능은 아직 구현되지 않았습니다.")
        // TODO: 카메라 캡처 트리거 로직 추가
    }

    /**
     * 스크린샷 기능 (추후 구현 필요)
     */
    private fun takeScreenshot(context: Context) {
        Log.d("GestureAction", "🖼️ 스크린샷 기능은 아직 구현되지 않았습니다.")
        // TODO: MediaProjection API 또는 shell command 사용
    }
}