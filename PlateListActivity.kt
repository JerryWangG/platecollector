package com.example.platecollector

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.platecollector.data.PlateRecord
import com.example.platecollector.data.PlateRepository
import com.example.platecollector.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlateListActivity : AppCompatActivity() {

    private lateinit var repo: PlateRepository
    private lateinit var adapter: PlateAdapter
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plate_list)

        repo = PlateRepository(AppDatabase.get(this).dao())

        adapter = PlateAdapter(
            onItemClick = { record -> showEditDialog(record) },
            onDeleteClick = { record -> showDeleteDialog(record) }
        )

        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        loadData()
    }

    private fun loadData() {
        scope.launch {
            val list = repo.getAll()
            adapter.submit(list)
        }
    }

    /** 编辑对话框：修改车牌号 */
    private fun showEditDialog(record: PlateRecord) {
        val input = EditText(this).apply {
            setText(record.plate)
            inputType = InputType.TYPE_CLASS_TEXT
            setSelection(record.plate.length)
        }
        AlertDialog.Builder(this)
            .setTitle("修改车牌号")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val newPlate = input.text.toString().trim().uppercase()
                if (newPlate.isEmpty() || newPlate == record.plate) return@setPositiveButton
                scope.launch {
                    val ok = repo.rename(record.plate, newPlate)
                    withContext(Dispatchers.Main) {
                        if (ok) {
                            loadData()  // 刷新列表
                        } else {
                            AlertDialog.Builder(this@PlateListActivity)
                                .setTitle("提示")
                                .setMessage("车牌「$newPlate」已存在，无法修改为重复车牌")
                                .setPositiveButton("确定", null)
                                .show()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 删除确认对话框 */
    private fun showDeleteDialog(record: PlateRecord) {
        AlertDialog.Builder(this)
            .setTitle("删除确认")
            .setMessage("确定删除车牌「${record.plate}」？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                scope.launch {
                    repo.delete(record)
                    loadData()  // 刷新列表
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
