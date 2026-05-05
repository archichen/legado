package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.BookEmbedding
import kotlinx.coroutines.flow.Flow

@Dao
interface BookEmbeddingDao {

    @Query("select * from book_embeddings where bookUrl = :bookUrl order by chapterIndex, chunkIndex")
    fun getByBook(bookUrl: String): List<BookEmbedding>

    @Query("select * from book_embeddings where bookUrl = :bookUrl order by chapterIndex, chunkIndex")
    fun observeByBook(bookUrl: String): Flow<List<BookEmbedding>>

    @Query("select * from book_embeddings where bookUrl = :bookUrl and chapterIndex = :chapterIndex order by chunkIndex")
    fun getByChapter(bookUrl: String, chapterIndex: Int): List<BookEmbedding>

    @Query("select count(*) from book_embeddings where bookUrl = :bookUrl")
    fun getCount(bookUrl: String): Int

    @Query("select count(distinct chapterIndex) from book_embeddings where bookUrl = :bookUrl")
    fun getChapterCount(bookUrl: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg embedding: BookEmbedding)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(embeddings: List<BookEmbedding>)

    @Query("delete from book_embeddings where bookUrl = :bookUrl")
    fun deleteByBook(bookUrl: String)

    @Query("delete from book_embeddings where bookUrl = :bookUrl and chapterIndex = :chapterIndex")
    fun deleteByChapter(bookUrl: String, chapterIndex: Int)

    @Query("delete from book_embeddings")
    fun deleteAll()
}
