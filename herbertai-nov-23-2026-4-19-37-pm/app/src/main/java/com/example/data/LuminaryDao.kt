package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LuminaryDao {

    @Query("SELECT * FROM threads ORDER BY updatedAt DESC")
    fun observeThreads(): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp ASC, id ASC")
    fun observeMessagesForThread(threadId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesForThreadOnce(threadId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE isBookmarked = 1 ORDER BY timestamp DESC")
    fun observeBookmarkedMessages(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ThreadEntity): Long

    @Query("UPDATE threads SET title = :title, updatedAt = :updatedAt, modeName = :modeName WHERE id = :threadId")
    suspend fun updateThreadMeta(threadId: Long, title: String, updatedAt: Long, modeName: String)

    @Query("UPDATE threads SET updatedAt = :updatedAt, modeName = :modeName WHERE id = :threadId")
    suspend fun touchThread(threadId: Long, updatedAt: Long, modeName: String)

    @Query("DELETE FROM threads WHERE id = :threadId")
    suspend fun deleteThread(threadId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("UPDATE messages SET isBookmarked = :bookmarked WHERE id = :messageId")
    suspend fun setBookmarked(messageId: Long, bookmarked: Boolean)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)

    @Query("SELECT * FROM creations ORDER BY createdAt DESC")
    fun observeAllCreations(): Flow<List<CreationEntity>>

    @Query("SELECT * FROM creations WHERE type = :type ORDER BY createdAt DESC")
    fun observeCreationsByType(type: String): Flow<List<CreationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCreation(creation: CreationEntity): Long

    @Query("DELETE FROM creations WHERE id = :id")
    suspend fun deleteCreation(id: Long)
}
