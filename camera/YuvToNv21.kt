package com.example.platecollector.camera


import android.graphics.ImageFormat
import android.media.Image

object YuvToNv21 {

    fun toNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val planes = image.planes

        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val nv21 = ByteArray(width * height * 3 / 2)
        var pos = 0

        // Y
        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            yBuffer.get(nv21, pos, width)
            pos += width
        }

        // VU interleaved
        val uvHeight = height / 2
        for (row in 0 until uvHeight) {
            var uvPos = row * uvRowStride
            for (col in 0 until width / 2) {
                val uIndex = uvPos + col * uvPixelStride
                val vIndex = uvPos + col * uvPixelStride + 1
                if (uIndex < uBuffer.capacity() && vIndex < vBuffer.capacity()) {
                    nv21[pos++] = vBuffer.get(vIndex)
                    nv21[pos++] = uBuffer.get(uIndex)
                }
            }
        }

        return nv21
    }
}