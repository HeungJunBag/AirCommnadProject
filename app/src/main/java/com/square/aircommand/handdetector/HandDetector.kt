package com.square.aircommand.handdetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.square.aircommand.tflite.AIHubDefaults
import com.square.aircommand.tflite.TFLiteHelpers
import com.square.aircommand.utils.ThrottledLogger
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.osgi.OpenCVNativeLoader
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HandDetector(
    context: Context,
    modelPath: String,
    delegatePriorityOrder: Array<Array<TFLiteHelpers.DelegateType>>
) : AutoCloseable {

    private val interpreter: Interpreter
    private val delegateStore: Map<TFLiteHelpers.DelegateType, Delegate>
    private val inputWidth: Int
    private val inputHeight: Int
    private val inputBuffer: ByteBuffer
    private val inputArray: FloatArray

    private fun sigmoid(x: Float): Float {
        return 1.0f / (1.0f + kotlin.math.exp(-x))
    }

    private fun nonMaximumSuppression(
        points: List<PointF>,
        scores: List<Float>,
        iouThreshold: Float = 0.6f
    ): List<PointF> {
        val picked = mutableListOf<PointF>()
        val indices = scores.indices.sortedByDescending { scores[it] }
        val visited = BooleanArray(points.size)

        for (i in indices) {
            if (visited[i]) continue
            val pointA = points[i]
            picked.add(pointA)
            visited[i] = true

            for (j in indices) {
                if (visited[j]) continue
                val pointB = points[j]
                if (arePointsClose(pointA, pointB, iouThreshold)) {
                    visited[j] = true
                }
            }
        }
        return picked
    }

    private fun arePointsClose(a: PointF, b: PointF, threshold: Float): Boolean {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        return dist < threshold * inputWidth
    }

    init {
        OpenCVNativeLoader().init()

        val (modelBuffer, hash) = TFLiteHelpers.loadModelFile(context.assets, modelPath)
        val (i, delegates) = TFLiteHelpers.CreateInterpreterAndDelegatesFromOptions(
            modelBuffer,
            delegatePriorityOrder,
            AIHubDefaults.numCPUThreads,
            context.applicationInfo.nativeLibraryDir,
            context.cacheDir.absolutePath,
            hash
        )

        interpreter = i
        delegateStore = delegates

        val inputTensor: Tensor = interpreter.getInputTensor(0)
        val inputShape = inputTensor.shape()
        inputHeight = inputShape[1]
        inputWidth = inputShape[2]

        inputArray = FloatArray(inputHeight * inputWidth * 3)
        inputBuffer = ByteBuffer.allocateDirect(inputArray.size * 4).order(ByteOrder.nativeOrder())
    }

    override fun close() {
        interpreter.close()
        delegateStore.values.forEach { it.close() }
    }

    fun detect(bitmap: Bitmap): List<PointF> {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2RGB)

        val resizedMat = Mat()
        Imgproc.resize(mat, resizedMat, Size(inputWidth.toDouble(), inputHeight.toDouble()))
        resizedMat.convertTo(resizedMat, CvType.CV_32FC3, 1.0 / 255.0)

        resizedMat.get(0, 0, inputArray)
        inputBuffer.rewind()
        inputBuffer.asFloatBuffer().put(inputArray)

        val boxCoords = Array(1) { Array(2944) { FloatArray(18) } }
        val boxScores = Array(1) { Array(2944) { FloatArray(1) } }
        val outputMap = mutableMapOf<Int, Any>().apply {
            put(interpreter.getOutputIndex("box_coords"), boxCoords)
            put(interpreter.getOutputIndex("box_scores"), boxScores)
        }

        interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)

        val origW = bitmap.width.toFloat()
        val origH = bitmap.height.toFloat()
        val scaleX = origW / inputWidth
        val scaleY = origH / inputHeight

        val candidates = mutableListOf<PointF>()
        val scores = mutableListOf<Float>()
        val threshold = 0.5f

        for (i in 0 until 2944) {
            val rawScore = boxScores[0][i][0]
            val score = sigmoid(rawScore)
            if (score <= threshold) continue

            val x = boxCoords[0][i][0] * scaleX
            val y = boxCoords[0][i][1] * scaleY

            candidates.add(PointF(x, y))
            scores.add(score)
        }

        return if (candidates.isEmpty()) {
            ThrottledLogger.log("HandDetector", "⚠️ 손을 감지하지 못했습니다.")
            emptyList()
        } else {
            nonMaximumSuppression(candidates, scores, iouThreshold = 0.6f)
        }
    }
}
