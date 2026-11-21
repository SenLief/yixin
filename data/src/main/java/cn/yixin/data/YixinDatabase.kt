package cn.yixin.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import cn.yixin.core.Energy
import cn.yixin.core.FocusMode
import cn.yixin.core.FocusStyle
import cn.yixin.core.HomeView
import cn.yixin.core.Mood
import cn.yixin.core.Ritual
import cn.yixin.core.Sort
import cn.yixin.core.TagColor

@Entity(tableName = "categories")
internal data class CategoryRow(
    @PrimaryKey val id: String,
    val name: String,
    val color: TagColor,
    val deleted: Boolean,
    val position: Int,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(entity = CategoryRow::class, parentColumns = ["id"], childColumns = ["category"])],
    indices = [Index("category")],
)
internal data class TaskRow(
    @PrimaryKey val id: String,
    val title: String,
    val note: String,
    val dueDate: String?,
    val estimatedMinutes: Int?,
    val energy: Energy,
    val category: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
)

@Entity(
    tableName = "manual_order",
    foreignKeys = [ForeignKey(entity = TaskRow::class, parentColumns = ["id"], childColumns = ["taskId"])],
)
internal data class OrderRow(@PrimaryKey val taskId: String, val position: Int)

@Entity(tableName = "device_state")
internal data class DeviceRow(
    @PrimaryKey val slot: Int = 0,
    val datasetId: String,
    val currentId: String?,
    val pinnedId: String?,
    val shuffleSeed: Long,
    val theme: Mood,
    val sortMode: Sort,
    val ritual: Ritual,
    val homeView: HomeView,
    val focusMode: FocusMode,
    val focusMinutes: Int,
    val focusStyle: FocusStyle,
    val reduceMotion: Boolean,
)

@Entity(
    tableName = "focus_progress",
    foreignKeys = [ForeignKey(entity = TaskRow::class, parentColumns = ["id"], childColumns = ["taskId"])],
)
internal data class ProgressRow(
    @PrimaryKey val taskId: String,
    val mode: FocusMode,
    val durationMs: Long,
    val elapsedMs: Long,
)

@Dao
internal interface DeckDao {
    @Query("SELECT * FROM device_state WHERE slot = 0")
    suspend fun device(): DeviceRow?

    @Query("SELECT * FROM categories ORDER BY position")
    suspend fun categories(): List<CategoryRow>

    @Query("SELECT * FROM tasks ORDER BY createdAt, id")
    suspend fun tasks(): List<TaskRow>

    @Query("SELECT * FROM manual_order ORDER BY position")
    suspend fun order(): List<OrderRow>

    @Query("SELECT * FROM focus_progress ORDER BY taskId")
    suspend fun progress(): List<ProgressRow>

    @Insert suspend fun insertDevice(device: DeviceRow)
    @Update suspend fun updateDevice(device: DeviceRow)
    @Upsert suspend fun putCategories(categories: List<CategoryRow>)
    @Insert suspend fun insertTasks(tasks: List<TaskRow>)
    @Update suspend fun updateTasks(tasks: List<TaskRow>)
    @Upsert suspend fun putOrder(order: List<OrderRow>)
    @Insert suspend fun insertProgress(progress: List<ProgressRow>)
    @Update suspend fun updateProgress(progress: List<ProgressRow>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun removeCategory(id: String)

    @Query("DELETE FROM focus_progress WHERE taskId = :id")
    suspend fun removeProgress(id: String)
}

@Database(
    entities = [CategoryRow::class, TaskRow::class, OrderRow::class, DeviceRow::class, ProgressRow::class],
    version = 1,
    exportSchema = true,
)
internal abstract class YixinDatabase : RoomDatabase() {
    abstract fun deck(): DeckDao
}
