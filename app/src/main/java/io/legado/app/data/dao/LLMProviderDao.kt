package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.LLMProvider
import kotlinx.coroutines.flow.Flow

@Dao
interface LLMProviderDao {

    @Query("select * from llm_providers order by sortNumber")
    fun observeAll(): Flow<List<LLMProvider>>

    @get:Query("select * from llm_providers order by sortNumber")
    val all: List<LLMProvider>

    @Query("select * from llm_providers where id = :id")
    fun get(id: Long): LLMProvider?

    @Query("select * from llm_providers where isDefault = 1 limit 1")
    fun getDefault(): LLMProvider?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg provider: LLMProvider)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(vararg provider: LLMProvider)

    @Delete
    fun delete(vararg provider: LLMProvider)

    @Query("delete from llm_providers where id = :id")
    fun delete(id: Long)
}
