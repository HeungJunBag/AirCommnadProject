package com.square.aircommand.utils

import android.util.Log

object ThrottledLogger {
    // 태그별로 마지막 출력 시간을 저장
    private val lastLogTimes = mutableMapOf<String, Long>()

    // 로그 출력 간격 (기본 1000ms = 1초)
    private const val defaultIntervalMs = 2000L

    fun log(tag: String, message: String, intervalMs: Long = defaultIntervalMs) {
        val currentTime = System.currentTimeMillis()
        val lastTime = lastLogTimes[tag] ?: 0L

        if (currentTime - lastTime > intervalMs) {
            Log.d(tag, message)
            lastLogTimes[tag] = currentTime
        }
    }
}