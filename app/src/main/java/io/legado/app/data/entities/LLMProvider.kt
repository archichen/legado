package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "llm_providers")
data class LLMProvider(
    @PrimaryKey
    var id: Long = System.currentTimeMillis(),
    var name: String = "",
    var baseUrl: String = "",
    var apiKey: String = "",
    var modelName: String = "",
    var isDefault: Boolean = false,
    var sortNumber: Int = 0
) : Parcelable {

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is LLMProvider) {
            return id == other.id
        }
        return false
    }
}
