package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "threads")
data class ThreadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val modeName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["threadId"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val threadId: Long,
    val role: String, // "user" or "model"
    val text: String,
    val thoughts: String = "",
    val thinkingSeconds: Int = 0,
    val imagePath: String? = null,
    val modeLabel: String? = null,
    val isBookmarked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "creations",
    indices = [Index(value = ["type"]), Index(value = ["createdAt"])]
)
data class CreationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String, // "IMAGE", "VIDEO", "TRANSCRIPT", "THREEJS", "VOICE"
    val title: String,
    val prompt: String,
    val content: String = "",
    val filePath: String? = null,
    val metadata: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
