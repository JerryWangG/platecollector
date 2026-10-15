package com.example.platecollector.data


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlateRepository(private val dao: PlateDao) {

    sealed interface Result {
        data object Added : Result
        data object Exists : Result
    }

    suspend fun handlePlate(plate: String): Result = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existed = dao.exists(plate)
        if (!existed) {
            dao.insert(PlateRecord(plate, now, now, 1))
            Result.Added
        } else {
            dao.touch(plate, now)
            Result.Exists
        }
    }
    suspend fun getAll(): List<PlateRecord> = withContext(Dispatchers.IO) {
        dao.getAllOnce()
    }
    fun flowAll() = dao.all()

    suspend fun search(q: String): List<PlateRecord> = withContext(Dispatchers.IO) {
        dao.search(q)
    }

    suspend fun update(record: PlateRecord) = withContext(Dispatchers.IO) {
        dao.update(record)
    }

    suspend fun delete(record: PlateRecord) = withContext(Dispatchers.IO) {
        dao.delete(record)
    }

    /** 改车牌号：删旧 + 插新，保留原记录的首次时间、次数等 */
    suspend fun rename(oldPlate: String, newPlate: String): Boolean = withContext(Dispatchers.IO) {
        if (dao.exists(newPlate)) return@withContext false  // 新车牌已存在，拒绝
        val old = dao.allOnce(oldPlate)  // 需要 DAO 加一个按主键查询
        if (old == null) return@withContext false
        dao.delete(old)
        dao.insert(old.copy(plate = newPlate))
        true
    }

    private suspend fun PlateDao.allOnce(plate: String): PlateRecord? {
        // 复用 search，取第一个精确匹配的
        return search(plate).firstOrNull { it.plate == plate }
    }
}