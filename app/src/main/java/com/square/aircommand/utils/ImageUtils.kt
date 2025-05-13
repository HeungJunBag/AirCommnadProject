package com.square.aircommand.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.media.Image
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalGetImage::class)
fun ImageProxy.toBitmapCompat(): Bitmap {
    val image: Image = this.image ?: throw IllegalArgumentException("Image is null")
    val yBuffer = image.planes[0].buffer
    val vuBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val vuSize = vuBuffer.remaining()

    val nv21 = ByteArray(ySize + vuSize)
    yBuffer.get(nv21, 0, ySize)
    vuBuffer.get(nv21, ySize, vuSize)

    val yuvImage = android.graphics.YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
    val yuv = out.toByteArray()
    return BitmapFactory.decodeByteArray(yuv, 0, yuv.size)
}