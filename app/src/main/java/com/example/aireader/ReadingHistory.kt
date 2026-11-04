package com.example.aireader

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "reading_history", indices = [Index("createdAt")])
data class ReadingHistoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val originalArticle: String?,
    val markdownResult: String,
    val readingModeId: String,
    val readingModeName: String,
    val modelId: String,
    val apiConfigName: String,
    val createdAt: Long,
    val updatedAt: Long
)

/** List queries deliberately exclude the original article and full report. */
data class ReadingHistorySummary(
    val id: String,
    val title: String,
    val createdAt: Long,
    val readingModeName: String,
    val modelId: String,
    val preview: String
)

@Dao
interface ReadingHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: ReadingHistoryEntity): Long

    @Query("""SELECT id, title, createdAt, readingModeName, modelId,
        substr(markdownResult, 1, 160) AS preview FROM reading_history
        WHERE (:query = '' OR instr(lower(title), lower(:query)) > 0
            OR instr(lower(readingModeName), lower(:query)) > 0)
        ORDER BY createdAt DESC, id DESC""")
    fun summaries(query: String): Flow<List<ReadingHistorySummary>>

    @Query("SELECT COUNT(*) FROM reading_history")
    fun count(): Flow<Int>

    @Query("SELECT * FROM reading_history WHERE id = :id LIMIT 1")
    suspend fun find(id: String): ReadingHistoryEntity?

    @Query("UPDATE reading_history SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, updatedAt: Long): Int

    @Query("DELETE FROM reading_history WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM reading_history WHERE id IN (:ids)")
    suspend fun deleteMany(ids: List<String>): Int

    @Query("DELETE FROM reading_history")
    suspend fun clearAll(): Int
}

@Database(entities = [ReadingHistoryEntity::class], version = 1, exportSchema = true)
abstract class ReadingHistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): ReadingHistoryDao

    companion object {
        @Volatile private var instance: ReadingHistoryDatabase? = null

        fun get(context: Context): ReadingHistoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext,
                ReadingHistoryDatabase::class.java, "reading_history.db")
                // Future versions must register explicit migrations here; never destroy user history.
                .build().also { instance = it }
        }
    }
}

class ReadingHistoryRepository(private val dao: ReadingHistoryDao) {
    fun summaries(query: String): Flow<List<ReadingHistorySummary>> = dao.summaries(query.trim())
    fun count(): Flow<Int> = dao.count()
    suspend fun find(id: String): ReadingHistoryEntity? = dao.find(id)
    suspend fun insertOnce(record: ReadingHistoryEntity): Boolean = dao.insert(record) != -1L
    suspend fun rename(id: String, title: String, now: Long = System.currentTimeMillis()): Boolean {
        val clean = title.trim()
        require(clean.isNotEmpty()) { "历史标题不能为空。" }
        return dao.rename(id, clean, now) > 0
    }
    suspend fun delete(id: String): Boolean = dao.delete(id) > 0
    suspend fun deleteMany(ids: Set<String>): Int = ids.chunked(500).sumOf { dao.deleteMany(it) }
    suspend fun clearAll(): Int = dao.clearAll()
}
