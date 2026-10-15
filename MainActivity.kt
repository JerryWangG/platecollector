package com.example.platecollector

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.util.Size
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.platecollector.data.PlateRecord
import com.example.platecollector.data.PlateRepository
import com.example.platecollector.data.AppDatabase
import com.hyperai.hyperlpr3.HyperLPR3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.platecollector.camera.PreviewAnalyzer
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts


class MainActivity : AppCompatActivity() {
    private var flashEnabled = false
    private lateinit var repo: PlateRepository
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
       private var camera: androidx.camera.core.Camera? = null
    private var autoSaveEnabled = true  // 默认开
    // 拍照后暂存
    private var pendingBitmap: Bitmap? = null
    private var pendingPlate: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = PlateRepository(AppDatabase.get(this).dao())
        val btnAutoSave = findViewById<ImageButton>(R.id.btnAutoSave)
        updateAutoSaveIcon()  // 初始化图标状态

        btnAutoSave.setOnClickListener {
            autoSaveEnabled = !autoSaveEnabled
            updateAutoSaveIcon()

            val msg = if (autoSaveEnabled) "自动入库已开启" else "自动入库已关闭"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

            // 关闭时隐藏提示
            if (!autoSaveEnabled) {
                findViewById<TextView>(R.id.tvToast).apply {
                    text = ""
                    alpha = 0f
                }
            }
        }
        findViewById<View>(R.id.btnExit).setOnClickListener { confirmExit() }
        findViewById<View>(R.id.btnList).setOnClickListener { showList() }
        findViewById<View>(R.id.btnSearch).setOnClickListener { showSearch() }
        findViewById<android.view.View>(R.id.btnExport).setOnClickListener {
            android.util.Log.d("PlateCollector", "btnExport CLICKED")
            exportCsv()
        }

        findViewById<View>(R.id.btnCapture).setOnClickListener { captureAndRecognize() }
        findViewById<View>(R.id.btnConfirmYes).setOnClickListener { confirmAdd() }
        findViewById<View>(R.id.btnConfirmNo).setOnClickListener { cancelAdd() }
        findViewById<ImageButton>(R.id.btnFlash).setOnClickListener { toggleFlash() }


        startCamera()
    }
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(
                    findViewById<PreviewView>(R.id.previewView).surfaceProvider
                )
            }

            imageCapture = ImageCapture.Builder()
                .setTargetResolution(Size(1080, 1920))
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val analyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(720, 1280))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(ContextCompat.getMainExecutor(this),
                        PreviewAnalyzer() { plate ->
                            runOnUiThread {
                                val tv = findViewById<TextView>(R.id.tvToast)
                                if (plate != null) {
                                    tv.text = "识别到: $plate"
                                    tv.alpha = 1f
                                    tv.setTextColor(0xFF00FF00.toInt())

                                    // 自动入库
                                    autoSavePlate(plate)

                                }
                            }
                        })
                }

            // 绑定，拿到 Camera 引用
            camera = cameraProvider?.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture!!,
                analyzer
            )
        }, ContextCompat.getMainExecutor(this))
    }

    /** 拍照 + 识别 */
    private fun captureAndRecognize() {
        val capture = imageCapture ?: return

        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        // 转 Bitmap
                        val bitmap = image.toBitmap()
                        pendingBitmap = bitmap

                        // 识别
                        val plates = HyperLPR3.getInstance()
                            .plateRecognition(bitmap, HyperLPR3.CAMERA_ROTATION_0, HyperLPR3.STREAM_BGRA)

                        val plate = plates.firstOrNull()?.code

                        if (plate != null) {
                            pendingPlate = plate
                            // 显示确认栏
                            findViewById<ImageView>(R.id.imgPreview).setImageBitmap(bitmap)
                            findViewById<TextView>(R.id.tvDetectedPlate).text = plate
                            findViewById<LinearLayout>(R.id.layoutConfirm).visibility = View.VISIBLE
                             } else {
                            Toast.makeText(this@MainActivity, "未识别到车牌，请调整角度重试", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "拍照失败: ${exc.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun updateAutoSaveIcon() {
        val btn = findViewById<ImageButton>(R.id.btnAutoSave)
        if (autoSaveEnabled) {
            // 开启状态：绿色背景 + 勾图标
            btn.setBackgroundColor(0x8000FF00.toInt())  // 半透明绿
            btn.setImageResource(android.R.drawable.checkbox_on_background)
        } else {
            // 关闭状态：灰色
            btn.setBackgroundColor(0x80AAAAAA.toInt())  // 半透明灰
            btn.setImageResource(android.R.drawable.checkbox_off_background)
        }
    }
    private fun confirmAdd() {
        val plate = pendingPlate ?: return
        scope.launch {
            val result = repo.handlePlate(plate)
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    when (result) {
                        PlateRepository.Result.Added -> "✅ 已添加: $plate"
                        PlateRepository.Result.Exists -> "⚠️ 已存在: $plate"
                    },
                    Toast.LENGTH_LONG
                ).show()
                cancelAdd()
            }
        }
    }
    /** 丢弃 */
    private fun cancelAdd() {
        pendingBitmap = null
        pendingPlate = null
        findViewById<LinearLayout>(R.id.layoutConfirm).visibility = View.GONE
        findViewById<TextView>(R.id.tvToast).alpha = 0f
    }


    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle("确认退出")
            .setMessage("已录入数据保存在本地数据库，确定退出？")
            .setPositiveButton("退出") { _, _ ->
                camera?.cameraControl?.enableTorch(false)
                cameraProvider?.unbindAll()
                scope.cancel()
                finishAffinity()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 在 MainActivity 里加这个成员变量
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        scope.launch {
            try {
                val records = repo.getAll()
                if (records.isEmpty()) {
                    Toast.makeText(this@MainActivity, "数据库为空", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val content = buildString {
                    append("\uFEFF车牌号,首次入库,最近见到,识别次数\n")
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                    records.forEach {
                        append("${it.plate},${sdf.format(java.util.Date(it.firstSeen))},${sdf.format(java.util.Date(it.lastSeen))},${it.count}\n")
                    }
                }

                contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(content.toByteArray(Charsets.UTF_8))
                }

                runOnUiThread {
                    Toast.makeText(this@MainActivity, "✅ 导出成功（${records.size} 条）", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("PlateCollector", "export failed", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportCsv() {
        val defaultName = "plates_${System.currentTimeMillis()}.csv"
        exportLauncher.launch(defaultName)
    }

    private fun showList() {
        startActivity(Intent(this, PlateListActivity::class.java))
    }
    private fun showSearch() {
        val input = android.widget.EditText(this)
        input.hint = "输入车牌号或关键字"
        AlertDialog.Builder(this)
            .setTitle("搜索车牌")
            .setView(input)
            .setPositiveButton("搜索") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isNotEmpty()) {
                    scope.launch {
                        val results = repo.search(q)
                        if (results.isEmpty()) {
                            Toast.makeText(this@MainActivity, "未找到匹配车牌", Toast.LENGTH_SHORT).show()
                        } else {
                            showSearchResult(results)
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }


    private fun toggleFlash() {
        val cam = camera
        if (cam == null) {
            Toast.makeText(this, "相机未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        if (!cam.cameraInfo.hasFlashUnit()) {
            Toast.makeText(this, "该设备不支持闪光灯", Toast.LENGTH_SHORT).show()
            return
        }

        flashEnabled = !flashEnabled
        cam.cameraControl.enableTorch(flashEnabled)

        val btn = findViewById<ImageButton>(R.id.btnFlash)
        if (flashEnabled) {
            btn.setImageResource(R.drawable.ic_flash_on)
            Toast.makeText(this, "闪光灯已开启", Toast.LENGTH_SHORT).show()
        } else {
            btn.setImageResource(R.drawable.ic_flash_off)
            Toast.makeText(this, "闪光灯已关闭", Toast.LENGTH_SHORT).show()
        }
    }
    private fun showSearchResult(results: List<PlateRecord>) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_list, null)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerView)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = PlateAdapter().apply { submitList(results) }

        AlertDialog.Builder(this)
            .setTitle("搜索结果 (${results.size})")
            .setView(dialogView)
            .setNegativeButton("关闭", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun autoSavePlate(plate: String) {
        if (!autoSaveEnabled) return
        scope.launch {
            try {
                val result = repo.handlePlate(plate)
                runOnUiThread {
                    val tv = findViewById<TextView>(R.id.tvToast)
                    when (result) {
                        is PlateRepository.Result.Added -> {
                            tv.text = "✅ 已入库: $plate"
                            tv.setTextColor(0xFF00FF00.toInt())
                        }
                        is PlateRepository.Result.Exists -> {
                            // 已存在不提示，静默更新 lastSeen + count
                            // 想提示就取消下面注释
                             tv.text = "⚠️ 已存在: $plate"
                             tv.setTextColor(0xFFFFFF00.toInt())
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlateCollector", "autoSave error", e)
            }
        }
    }


    // ---- Adapter ----
    inner class PlateAdapter : RecyclerView.Adapter<PlateAdapter.VH>() {
        private val list = mutableListOf<PlateRecord>()

        fun submitList(data: List<PlateRecord>) {
            list.clear()
            list.addAll(data)
            notifyDataSetChanged()
        }

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvPlate: TextView = itemView.findViewById(R.id.tvPlate)
            val tvTime: TextView = itemView.findViewById(R.id.tvInfo)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_plate, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            holder.tvPlate.text = item.plate
            holder.tvTime.text = "首次: ${sdf.format(Date(item.firstSeen))} | 最近: ${sdf.format(Date(item.lastSeen))} | 次数: ${item.count}"
        }

        override fun getItemCount() = list.size
    }
}