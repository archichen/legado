package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.CorrectionTask
import kotlinx.coroutines.flow.Flow

@Dao
interface CorrectionTaskDao {

    @Query("select * from correction_tasks where bookUrl = :bookUrl order by chapterIndex")
    fun observeByBook(bookUrl: String): Flow<List<CorrectionTask>>

    @Query("select * from correction_tasks where bookUrl = :bookUrl order by chapterIndex")
    fun getByBook(bookUrl: String): List<CorrectionTask>

    @Query("select * from correction_tasks where bookUrl = :bookUrl and status = :status order by chapterIndex")
    fun getByStatus(bookUrl: String, status: String): List<CorrectionTask>

    @Query("select * from correction_tasks where bookUrl = :bookUrl and chapterIndex = :chapterIndex")
    fun getTask(bookUrl: String, chapterIndex: Int): CorrectionTask?

    @Query("select count(*) from correction_tasks where bookUrl = :bookUrl and status = 'completed'")
    fun getCompletedCount(bookUrl: String): Int

    @Query("select count(*) from correction_tasks where bookUrl = :bookUrl and status = 'pending'")
    fun getPendingCount(bookUrl: String): Int

    @Query("select count(*) from correction_tasks where bookUrl = :bookUrl and status = 'processing'")
    fun getProcessingCount(bookUrl: String): Int

    @Query("select count(*) from correction_tasks where bookUrl = :bookUrl and status = 'failed'")
    fun getFailedCount(bookUrl: String): Int

    @Query("select count(*) from correction_tasks where bookUrl = :bookUrl")
    fun getTotalCount(bookUrl: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg task: CorrectionTask)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(vararg task: CorrectionTask)

    @Query("delete from correction_tasks where bookUrl = :bookUrl")
    fun deleteByBook(bookUrl: String)

    @Query("delete from correction_tasks where bookUrl = :bookUrl and status = 'pending'")
    fun deletePendingByBook(bookUrl: String)

    @Query("delete from correction_tasks")
    fun deleteAll()
}
