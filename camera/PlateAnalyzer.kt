package com.example.platecollector.camera



import android.content.Context
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.platecollector.data.PlateRepository
import com.hyperai.hyperlpr3.HyperLPR3

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.hyperai.hyperlpr3.bean.Plate
class PlateAnalyzer(
    private val context: Context,
    private val repo: PlateRepository,
    private val onResult: (String, PlateRepository.Result) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastPlate = ""
    private var lastTime = 0L
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun analyze(imageProxy: ImageProxy) {
        val image = imageProxy.image
        if (image == null) {
            imageProxy.close()
            return
        }

        try {
            val nv21 = YuvToNv21.toNv21(image)
            val w = image.width
            val h = image.height
            val rotation = when (imageProxy.imageInfo.rotationDegrees) {
                0 -> HyperLPR3.CAMERA_ROTATION_0
                90 -> HyperLPR3.CAMERA_ROTATION_90
                180 -> HyperLPR3.CAMERA_ROTATION_180
                else -> HyperLPR3.CAMERA_ROTATION_270
            }

            val plates: Array<Plate> = HyperLPR3.getInstance()
                .plateRecognition(nv21, h, w, rotation, HyperLPR3.STREAM_YUV_NV21)

            val plate = plates.firstOrNull()?.code

            if (plate != null && plate != lastPlate && System.currentTimeMillis() - lastTime > 1500) {
                lastPlate = plate
                lastTime = System.currentTimeMillis()
                scope.launch {
                    val result = repo.handlePlate(plate)
                    onResult(plate, result)
                }
            } else if (plate == null) {
                lastPlate = ""
            }
        } catch (e: Exception) {
            Log.e("PlateAnalyzer", "analyze error", e)
        } finally {
            imageProxy.close()
        }
    }
}