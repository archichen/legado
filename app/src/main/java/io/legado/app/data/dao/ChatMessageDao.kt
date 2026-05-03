package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("select * from chat_messages where bookUrl = :bookUrl order by timestamp")
    fun observeByBook(bookUrl: String): Flow<List<ChatMessage>>

    @Query("select * from chat_messages where bookUrl = :bookUrl order by timestamp")
    fun getByBook(bookUrl: String): List<ChatMessage>

    @Query("select * from chat_messages where bookUrl = :bookUrl order by timestamp desc limit :limit")
    fun getRecentByBook(bookUrl: String, limit: Int): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg message: ChatMessage)

    @Query("delete from chat_messages where bookUrl = :bookUrl")
    fun deleteByBook(bookUrl: String)

    @Query("delete from chat_messages where id = :id")
    fun delete(id: Long)

    @Query("delete from chat_messages")
    fun deleteAll()
}
