package com.square.aircommand.delegateconfirm

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.square.aircommand.tflite.TFLiteHelpers
import org.tensorflow.lite.Delegate

@Composable
fun DelegateStatusDisplay(delegateMap: Map<TFLiteHelpers.DelegateType, Delegate>) {
    val qnnUsed = delegateMap.containsKey(TFLiteHelpers.DelegateType.QNN_NPU)
    val gpuUsed = delegateMap.containsKey(TFLiteHelpers.DelegateType.GPUv2)

    Column {
        Text("🧠 Delegate 상태", style = MaterialTheme.typography.titleMedium)
        Text("• QNN NPU 사용됨: ${if (qnnUsed) "✅ 예" else "❌ 아니요"}")
        Text("• GPU v2 사용됨: ${if (gpuUsed) "✅ 예" else "❌ 아니요"}")
    }
}