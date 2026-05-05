package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "book_embeddings",
    indices = [
        Index(value = ["bookUrl", "chapterIndex", "chunkIndex"], unique = true),
        Index(value = ["bookUrl"])
    ]
)
data class BookEmbedding(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var bookUrl: String = "",
    var chapterIndex: Int = 0,
    var chunkIndex: Int = 0,
    var chapterTitle: String = "",
    var text: String = "",
    var vector: ByteArray = ByteArray(0),
    var createdAt: Long = System.currentTimeMillis()
) : Parcelable {

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is BookEmbedding) {
            return id == other.id
        }
        return false
    }
}
