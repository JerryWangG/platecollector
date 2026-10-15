package com.example.platecollector.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.hyperai.hyperlpr3.HyperLPR3

class PreviewAnalyzer(
    private val onResult: (String?) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastPlate = ""
    private var lastTime = 0L
    private val COOLDOWN = 2000L

    override fun analyze(imageProxy: ImageProxy) {
        val image = imageProxy.image
        if (image == null) {
            imageProxy.close()
            return
        }

        try {
            // ===== 关键改动：ImageProxy → Bitmap =====
            val bitmap = imageProxyToBitmap(imageProxy)

            if (bitmap != null) {
                // 旋转 Bitmap 到正确方向
                val rotated = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)

                // 用 HyperLPR3 的 Bitmap 接口（如果 SDK 支持）
                // 或者转成 BGRA 字节数组
                val bgra = bitmapToBgra(rotated)

                val plates = HyperLPR3.getInstance()
                    .plateRecognition(
                        bgra,
                        rotated.height,
                        rotated.width,
                        HyperLPR3.CAMERA_ROTATION_0,
                        HyperLPR3.STREAM_BGRA
                    )

                val best = plates
                    .filter { it.confidence > 0.8f }
                    .maxByOrNull { it.confidence }

                val plate = best?.code

                Log.d("PreviewAnalyzer", "bitmap size=${rotated.width}x${rotated.height}, best=$plate, conf=${best?.confidence}")

                if (plate != null) {
                    if (plate != lastPlate && System.currentTimeMillis() - lastTime > COOLDOWN) {
                        lastPlate = plate
                        lastTime = System.currentTimeMillis()
                        onResult(plate)
                    }
                } else {
                    if (lastPlate != "") {
                        lastPlate = ""
                        onResult(null)
                    }
                }

                rotated.recycle()
            }

        } catch (e: Exception) {
            Log.e("PreviewAnalyzer", "analyze error", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            // NV21 → Bitmap
            val yuvImage = android.graphics.YuvImage(
                nv21,
                android.graphics.ImageFormat.NV21,
                imageProxy.width,
                imageProxy.height,
                null
            )
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height),
                90,
                out
            )
            val imageBytes = out.toByteArray()
            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e("PreviewAnalyzer", "bitmap convert error", e)
            null
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun bitmapToBgra(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val bgra = ByteArray(w * h * 4)
        var idx = 0
        for (pixel in pixels) {
            bgra[idx++] = (pixel and 0xFF).toByte()           // B
            bgra[idx++] = ((pixel shr 8) and 0xFF).toByte()   // G
            bgra[idx++] = ((pixel shr 16) and 0xFF).toByte()  // R
            bgra[idx++] = ((pixel shr 24) and 0xFF).toByte()  // A
        }
        return bgra
    }
}