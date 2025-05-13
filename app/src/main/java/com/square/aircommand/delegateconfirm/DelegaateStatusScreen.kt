package com.square.aircommand.delegateconfirm

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.square.aircommand.tflite.TFLiteHelpers.DelegateType

@Composable
fun DelegateStatusScreen(context: Context) {
    var delegateMap by remember { mutableStateOf<Map<DelegateType, org.tensorflow.lite.Delegate>>(emptyMap()) }
    var delegateStatusText by remember { mutableStateOf("🔄 Delegate 초기화 중...") }

    LaunchedEffect(Unit) {
        val (status, delegates) = TFLiteInitializer.initialize(context)
        delegateStatusText = status
        delegateMap = delegates
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(24.dp)
        ) {
            Text("🧠 Delegate 상태 확인", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

            Text(delegateStatusText, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))

            DelegateStatusDisplay(delegateMap = delegateMap)
        }
    }
}