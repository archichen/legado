package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["bookUrl", "timestamp"])]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var bookUrl: String = "",
    var role: String = ROLE_USER,
    var content: String = "",
    var timestamp: Long = System.currentTimeMillis()
) : Parcelable {

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_SYSTEM = "system"
        const val ROLE_THINKING = "thinking"
        const val ROLE_TOOL_CALL = "tool_call"
        const val ROLE_TOOL_RESULT = "tool_result"
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is ChatMessage) {
            return id == other.id
        }
        return false
    }
}
