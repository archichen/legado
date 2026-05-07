package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "correction_tasks",
    indices = [Index(value = ["bookUrl", "chapterIndex"], unique = true)]
)
data class CorrectionTask(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var bookUrl: String = "",
    var chapterIndex: Int = 0,
    var status: String = STATUS_PENDING,
    var retryCount: Int = 0,
    var errorMessage: String? = null,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) : Parcelable {

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_PROCESSING = "processing"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val MAX_RETRY = 3
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is CorrectionTask) {
            return id == other.id
        }
        return false
    }
}
