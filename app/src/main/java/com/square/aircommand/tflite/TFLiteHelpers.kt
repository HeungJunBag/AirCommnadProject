package com.square.aircommand.tflite

import android.content.res.AssetManager
import android.util.Log
import com.qualcomm.qti.QnnDelegate
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.gpu.GpuDelegateFactory
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object TFLiteHelpers {
    private const val TAG = "QualcommTFLiteHelpers"

    enum class DelegateType {
        GPUv2, QNN_NPU
    }

    fun CreateInterpreterAndDelegatesFromOptions(
        tfLiteModel: MappedByteBuffer,
        delegatePriorityOrder: Array<Array<DelegateType>>,
        numCPUThreads: Int,
        nativeLibraryDir: String,
        cacheDir: String,
        modelIdentifier: String
    ): Pair<Interpreter, Map<DelegateType, Delegate>> {
        val delegates = mutableMapOf<DelegateType, Delegate>()
        val attemptedDelegates = mutableSetOf<DelegateType>()

        for (delegatesToRegister in delegatePriorityOrder) {
            delegatesToRegister.filterNot { attemptedDelegates.contains(it) }.forEach { type ->
                CreateDelegate(type, nativeLibraryDir, cacheDir, modelIdentifier)?.let {
                    delegates[type] = it
                }
                attemptedDelegates.add(type)
            }

            if (delegatesToRegister.any { !delegates.containsKey(it) }) continue

            val pairs = delegatesToRegister.map { Pair(it, delegates[it]) }.toTypedArray()
            val interpreter = CreateInterpreterFromDelegates(pairs, numCPUThreads, tfLiteModel) ?: continue

            val selectedDelegates = if (delegatesToRegister.isEmpty()) {
                "CPU (XNNPack)"
            } else {
                delegatesToRegister.joinToString(", ") { it.name }
            }
            Log.i(TAG, "✅ 선택된 delegate 조합: $selectedDelegates")

            val used = delegatesToRegister.toSet()
            delegates.keys.filterNot { it in used }.forEach {
                delegates.remove(it)?.close()
            }

            return Pair(interpreter, delegates)
        }

        throw RuntimeException("Unable to create interpreter with any delegate combination.")
    }

    fun CreateInterpreterFromDelegates(
        delegates: Array<Pair<DelegateType, Delegate?>>, numCPUThreads: Int, tfLiteModel: MappedByteBuffer
    ): Interpreter? {
        val options = Interpreter.Options().apply {
            setAllowBufferHandleOutput(true)
            setUseNNAPI(false)
            setNumThreads(numCPUThreads)
            setUseXNNPACK(true)

            delegates.forEach { (type, delegate) ->
                if (delegate != null) {
                    Log.i(TAG, "[$type] delegate가 정상적으로 추가되었습니다.")
                    addDelegate(delegate)
                } else {
                    Log.w(TAG, "[$type] delegate가 null이라 추가되지 않습니다.")
                }
            }
        }

        return try {
            Interpreter(tfLiteModel, options).apply {
                allocateTensors()
                Log.i(TAG, "Interpreter가 성공적으로 생성되었습니다. 사용된 delegate: ${
                    delegates.map { it.first }.joinToString(", ")
                }")
            }
        } catch (e: Exception) {
            val enabledDelegates = delegates.mapNotNull { it.first.name }.toMutableList().apply { add("XNNPack") }
            Log.e(TAG, "❌ Interpreter 생성 실패. 시도된 delegate: ${enabledDelegates.joinToString(", ")} | 오류: ${e.message}")
            null
        }
    }

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    fun loadModelFile(assets: AssetManager, modelFilename: String): Pair<MappedByteBuffer, String> {
        val fileDescriptor = assets.openFd(modelFilename)
        val buffer: MappedByteBuffer
        val hash: String

        FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
            val channel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            buffer = channel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val digest = MessageDigest.getInstance("MD5")
            inputStream.skip(startOffset)
            DigestInputStream(inputStream, digest).use { dis ->
                val data = ByteArray(8192)
                var totalRead = 0
                while (totalRead < declaredLength) {
                    val bytesRead = dis.read(data, 0, minOf(8192, (declaredLength - totalRead).toInt()))
                    if (bytesRead == -1) break
                    totalRead += bytesRead
                }
            }
            hash = digest.digest().joinToString("") { "%02x".format(it) }
        }
        return Pair(buffer, hash)
    }

    private fun CreateDelegate(
        delegateType: DelegateType,
        nativeLibraryDir: String,
        cacheDir: String,
        modelIdentifier: String
    ): Delegate? {
        return when (delegateType) {
            DelegateType.GPUv2 -> CreateGPUv2Delegate(cacheDir, modelIdentifier)
            DelegateType.QNN_NPU -> CreateQNN_NPUDelegate(nativeLibraryDir, cacheDir, modelIdentifier)
        }
    }

    private fun CreateQNN_NPUDelegate(
        nativeLibraryDir: String,
        cacheDir: String,
        modelIdentifier: String
    ): Delegate? {
        val options = QnnDelegate.Options().apply {
            setSkelLibraryDir(nativeLibraryDir)
            setLogLevel(QnnDelegate.Options.LogLevel.LOG_LEVEL_WARN)
            setCacheDir(cacheDir)
            setModelToken(modelIdentifier)

            if (QnnDelegate.checkCapability(QnnDelegate.Capability.DSP_RUNTIME)) {
                setBackendType(QnnDelegate.Options.BackendType.DSP_BACKEND)
                setDspOptions(
                    QnnDelegate.Options.DspPerformanceMode.DSP_PERFORMANCE_BURST,
                    QnnDelegate.Options.DspPdSession.DSP_PD_SESSION_ADAPTIVE
                )
            } else {
                val hasFP16 = QnnDelegate.checkCapability(QnnDelegate.Capability.HTP_RUNTIME_FP16)
                val hasQuant = QnnDelegate.checkCapability(QnnDelegate.Capability.HTP_RUNTIME_QUANTIZED)

                if (!hasFP16 && !hasQuant) {
                    Log.e(TAG, "QNN with NPU backend is not supported on this device.")
                    return null
                }

                setBackendType(QnnDelegate.Options.BackendType.HTP_BACKEND)
                setHtpUseConvHmx(QnnDelegate.Options.HtpUseConvHmx.HTP_CONV_HMX_ON)
                setHtpPerformanceMode(QnnDelegate.Options.HtpPerformanceMode.HTP_PERFORMANCE_BURST)
                if (hasFP16) {
                    setHtpPrecision(QnnDelegate.Options.HtpPrecision.HTP_PRECISION_FP16)
                }
            }
        }

        return try {
            QnnDelegate(options)
        } catch (e: Exception) {
            Log.e(TAG, "QNN delegate failed: ${e.message}")
            null
        }
    }

    private fun CreateGPUv2Delegate(cacheDir: String, modelIdentifier: String): Delegate? {
        val options = GpuDelegateFactory.Options().apply {
            inferencePreference = GpuDelegateFactory.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED
            isPrecisionLossAllowed = true
            setSerializationParams(cacheDir, modelIdentifier)
        }

        return try {
            GpuDelegate(options)
        } catch (e: Exception) {
            Log.e(TAG, "GPUv2 delegate failed: ${e.message}")
            null
        }
    }
}
