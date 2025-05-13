package com.square.aircommand.delegateconfirm

import android.content.Context
import com.square.aircommand.tflite.AIHubDefaults
import com.square.aircommand.tflite.TFLiteHelpers
import java.lang.Exception

object TFLiteInitializer {
    suspend fun initialize(context: Context): Pair<String, Map<TFLiteHelpers.DelegateType, org.tensorflow.lite.Delegate>> {
        return try {
            val (model, modelId) = TFLiteHelpers.loadModelFile(context.assets, "mediapipe_hand-handdetector.tflite")

            val (interpreter, delegates) = TFLiteHelpers.CreateInterpreterAndDelegatesFromOptions(
                tfLiteModel = model,
                delegatePriorityOrder = AIHubDefaults.delegatePriorityOrderForDelegates(AIHubDefaults.enabledDelegates),
                numCPUThreads = AIHubDefaults.numCPUThreads,
                nativeLibraryDir = context.applicationInfo.nativeLibraryDir,
                cacheDir = context.cacheDir.absolutePath,
                modelIdentifier = modelId
            )

            "✅ Delegate 등록 성공: ${delegates.keys.joinToString(", ")}" to delegates
        } catch (e: Exception) {
            val error = buildString {
                append("❌ Delegate 등록 실패:\n")
                appendLine(e.message)
                appendLine(e.stackTraceToString())
            }
            error to emptyMap()
        }
    }
}