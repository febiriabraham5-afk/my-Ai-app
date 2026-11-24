package com.example.data

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ai.AnswerMode
import com.example.ui.theme.BackgroundStyle
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "luminary_prefs")

data class SqlTableInfo(
    val name: String,
    val rowCount: Long,
    val columns: List<String>
)

data class SqlExecutionResult(
    val sql: String,
    val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val statusMessage: String = "",
    val error: String? = null,
    val elapsedMs: Long = 0L
)

class LuminaryRepository(
    private val context: Context,
    private val database: LuminaryDatabase
) {
    private val dao: LuminaryDao = database.luminaryDao()

    private object PrefKeys {
        val ANSWER_MODE = stringPreferencesKey("answer_mode")
        val THEME_PREF = stringPreferencesKey("theme_preference")
        val BACKGROUND_STYLE = stringPreferencesKey("background_style")
    }

    val threads: Flow<List<ThreadEntity>> = dao.observeThreads()
    val bookmarkedMessages: Flow<List<MessageEntity>> = dao.observeBookmarkedMessages()
    val creations: Flow<List<CreationEntity>> = dao.observeAllCreations()

    val preferredMode: Flow<AnswerMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[PrefKeys.ANSWER_MODE]
        AnswerMode.entries.firstOrNull { it.name == raw } ?: AnswerMode.BALANCED
    }

    val themePreference: Flow<ThemePreference> = context.dataStore.data.map { prefs ->
        val raw = prefs[PrefKeys.THEME_PREF]
        ThemePreference.entries.firstOrNull { it.name == raw } ?: ThemePreference.SYSTEM
    }

    val backgroundStyle: Flow<BackgroundStyle> = context.dataStore.data.map { prefs ->
        val raw = prefs[PrefKeys.BACKGROUND_STYLE]
        BackgroundStyle.entries.firstOrNull { it.name == raw } ?: BackgroundStyle.AMBIENT
    }

    fun observeMessages(threadId: Long): Flow<List<MessageEntity>> =
        dao.observeMessagesForThread(threadId)

    suspend fun getMessagesOnce(threadId: Long): List<MessageEntity> =
        dao.getMessagesForThreadOnce(threadId)

    suspend fun createThread(title: String, mode: AnswerMode): Long {
        val now = System.currentTimeMillis()
        return dao.insertThread(
            ThreadEntity(
                title = title.take(80),
                modeName = mode.name,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun touchThread(threadId: Long, mode: AnswerMode) {
        dao.touchThread(threadId, System.currentTimeMillis(), mode.name)
    }

    suspend fun deleteThread(threadId: Long) {
        val messages = dao.getMessagesForThreadOnce(threadId)
        withContext(Dispatchers.IO) {
            messages.mapNotNull { it.imagePath }.forEach { path ->
                runCatching { File(path).delete() }
            }
        }
        dao.deleteThread(threadId)
    }

    suspend fun insertMessage(
        threadId: Long,
        role: String,
        text: String,
        thoughts: String = "",
        thinkingSeconds: Int = 0,
        imagePath: String? = null,
        modeLabel: String? = null
    ): Long {
        return dao.insertMessage(
            MessageEntity(
                threadId = threadId,
                role = role,
                text = text,
                thoughts = thoughts,
                thinkingSeconds = thinkingSeconds,
                imagePath = imagePath,
                modeLabel = modeLabel,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun setBookmarked(messageId: Long, bookmarked: Boolean) {
        dao.setBookmarked(messageId, bookmarked)
    }

    suspend fun deleteMessage(messageId: Long) {
        dao.deleteMessage(messageId)
    }

    suspend fun saveCreation(
        type: String,
        title: String,
        prompt: String,
        content: String = "",
        filePath: String? = null,
        metadata: String = ""
    ): Long {
        return dao.insertCreation(
            CreationEntity(
                type = type,
                title = title.take(100),
                prompt = prompt,
                content = content,
                filePath = filePath,
                metadata = metadata,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteCreation(creation: CreationEntity) {
        withContext(Dispatchers.IO) {
            creation.filePath?.let { runCatching { File(it).delete() } }
        }
        dao.deleteCreation(creation.id)
    }

    suspend fun savePreferredMode(mode: AnswerMode) {
        context.dataStore.edit { it[PrefKeys.ANSWER_MODE] = mode.name }
    }

    suspend fun saveThemePreference(theme: ThemePreference) {
        context.dataStore.edit { it[PrefKeys.THEME_PREF] = theme.name }
    }

    suspend fun saveBackgroundStyle(style: BackgroundStyle) {
        context.dataStore.edit { it[PrefKeys.BACKGROUND_STYLE] = style.name }
    }

    suspend fun persistGeneratedBitmap(bitmap: Bitmap, prefix: String = "gen"): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "creations").apply { mkdirs() }
        val file = File(dir, "${prefix}_${UUID.randomUUID()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file.absolutePath
    }

    suspend fun decodeAndPersistAttachment(uri: Uri): Pair<Bitmap, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val maxDim = 1280
            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > maxDim || boundsOpts.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@withContext null

            val scaled = scaleBitmapIfNeeded(decoded, maxDim)
            val dir = File(context.filesDir, "attachments").apply { mkdirs() }
            val file = File(dir, "img_${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            scaled to file.absolutePath
        }.getOrNull()
    }

    suspend fun readUriBytes(uri: Uri, maxBytes: Int = 20 * 1024 * 1024): Pair<ByteArray, String>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val mime = context.contentResolver.getType(uri) ?: "audio/mp4"
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val data = input.readBytes()
                    if (data.size > maxBytes) data.copyOf(maxBytes) else data
                } ?: return@withContext null
                bytes to mime
            }.getOrNull()
        }

    suspend fun loadBitmapFromPath(path: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext null
        runCatching {
            val file = File(path)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }.getOrNull()
    }

    suspend fun listSqlTables(): List<SqlTableInfo> = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        val tableNames = mutableListOf<String>()
        db.query(
            """
            SELECT name FROM sqlite_master
            WHERE type IN ('table', 'view')
              AND name NOT LIKE 'sqlite_%'
              AND name NOT LIKE 'room_%'
              AND name NOT LIKE 'android_%'
            ORDER BY name ASC
            """.trimIndent()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tableNames.add(cursor.getString(0))
            }
        }
        tableNames.map { tableName ->
            val safeName = tableName.replace("\"", "\"\"")
            val count = runCatching {
                db.query("SELECT COUNT(*) FROM \"$safeName\"").use { c ->
                    if (c.moveToFirst()) c.getLong(0) else 0L
                }
            }.getOrDefault(0L)
            val cols = mutableListOf<String>()
            runCatching {
                db.query("PRAGMA table_info(\"$safeName\")").use { c ->
                    val nameIdx = c.getColumnIndex("name")
                    val typeIdx = c.getColumnIndex("type")
                    while (c.moveToNext()) {
                        val colName = if (nameIdx >= 0) c.getString(nameIdx) else ""
                        val colType = if (typeIdx >= 0) c.getString(typeIdx) else ""
                        cols.add(if (colType.isNotBlank()) "$colName ($colType)" else colName)
                    }
                }
            }
            SqlTableInfo(name = tableName, rowCount = count, columns = cols)
        }
    }

    suspend fun getSqlSchemaSummary(): String = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        buildString {
            db.query(
                """
                SELECT name, sql FROM sqlite_master
                WHERE type IN ('table', 'view')
                  AND name NOT LIKE 'sqlite_%'
                  AND name NOT LIKE 'room_%'
                  AND name NOT LIKE 'android_%'
                ORDER BY name ASC
                """.trimIndent()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    appendLine("-- Table: ${cursor.getString(0)}")
                    appendLine(cursor.getString(1) ?: "")
                }
            }
        }.trim()
    }

    suspend fun executeRawSql(rawSql: String): SqlExecutionResult = withContext(Dispatchers.IO) {
        val cleaned = rawSql.trim()
        if (cleaned.isEmpty()) {
            return@withContext SqlExecutionResult(sql = rawSql, error = "Enter a SQL statement first.")
        }
        val started = System.currentTimeMillis()
        try {
            val db = database.openHelper.writableDatabase
            val upper = cleaned.trimStart().uppercase(Locale.ROOT)
            val isQuery = upper.startsWith("SELECT") ||
                upper.startsWith("PRAGMA") ||
                upper.startsWith("EXPLAIN") ||
                upper.startsWith("WITH")

            if (isQuery) {
                db.query(cleaned).use { cursor ->
                    val columns = cursor.columnNames.toList()
                    val rows = mutableListOf<List<String>>()
                    var rowLimit = 0
                    while (cursor.moveToNext() && rowLimit < 200) {
                        rows.add(readCursorRow(cursor, columns.size))
                        rowLimit++
                    }
                    val elapsed = System.currentTimeMillis() - started
                    return@withContext SqlExecutionResult(
                        sql = cleaned,
                        columns = columns,
                        rows = rows,
                        statusMessage = "${rows.size} row(s) returned in ${elapsed}ms",
                        elapsedMs = elapsed
                    )
                }
            } else {
                val statements = splitSqlStatements(cleaned)
                for (stmt in statements) {
                    db.execSQL(stmt)
                }
                val elapsed = System.currentTimeMillis() - started
                return@withContext SqlExecutionResult(
                    sql = cleaned,
                    statusMessage = "Executed ${statements.size} statement(s) successfully in ${elapsed}ms.",
                    elapsedMs = elapsed
                )
            }
        } catch (t: Throwable) {
            return@withContext SqlExecutionResult(
                sql = cleaned,
                error = t.message ?: "SQL execution failed.",
                elapsedMs = System.currentTimeMillis() - started
            )
        }
    }

    private fun readCursorRow(cursor: Cursor, columnCount: Int): List<String> {
        return List(columnCount) { idx ->
            when (cursor.getType(idx)) {
                Cursor.FIELD_TYPE_NULL -> "NULL"
                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(idx).toString()
                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(idx).toString()
                Cursor.FIELD_TYPE_BLOB -> "<BLOB ${cursor.getBlob(idx)?.size ?: 0}B>"
                else -> {
                    val v = cursor.getString(idx).orEmpty()
                    if (v.length > 140) v.take(137) + "..." else v
                }
            }
        }
    }

    private fun splitSqlStatements(sql: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var i = 0
        while (i < sql.length) {
            val ch = sql[i]
            val next = if (i + 1 < sql.length) sql[i + 1] else '\u0000'
            when {
                ch == '\'' && !inDoubleQuote -> {
                    current.append(ch)
                    if (inSingleQuote && next == '\'') {
                        current.append(next)
                        i++
                    } else {
                        inSingleQuote = !inSingleQuote
                    }
                }
                ch == '"' && !inSingleQuote -> {
                    current.append(ch)
                    if (inDoubleQuote && next == '"') {
                        current.append(next)
                        i++
                    } else {
                        inDoubleQuote = !inDoubleQuote
                    }
                }
                ch == ';' && !inSingleQuote && !inDoubleQuote -> {
                    val trimmed = current.toString().trim()
                    if (trimmed.isNotEmpty()) result.add(trimmed)
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        val tail = current.toString().trim()
        if (tail.isNotEmpty()) result.add(tail)
        return result
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val targetW = (w * ratio).toInt().coerceAtLeast(1)
        val targetH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
