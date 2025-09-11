package my.noveldokusha.feature.local_database.tables

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ChapterBody(
    @PrimaryKey val url: String,
    val body: String,
    val createdEpochTimeMilli: Long = System.currentTimeMillis(),
    val lastUpdatedEpochTimeMilli: Long = System.currentTimeMillis()
)
