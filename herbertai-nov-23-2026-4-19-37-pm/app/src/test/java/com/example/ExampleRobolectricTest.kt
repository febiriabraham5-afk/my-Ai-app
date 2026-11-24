package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.LuminaryDatabase
import com.example.data.LuminaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Luminary", appName)
  }

  @Test
  fun `sqlite repository creates tables, saves creations, and executes queries`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = LuminaryDatabase.getInstance(context)
    val repo = LuminaryRepository(context, db)

    repo.saveCreation(
      type = "THREEJS",
      title = "Solar Orrery",
      prompt = "Solar system",
      content = "console.log('3d');"
    )

    val createRes = repo.executeRawSql(
      "CREATE TABLE IF NOT EXISTS test_items (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT);"
    )
    assertNull(createRes.error)

    val insertRes = repo.executeRawSql(
      "INSERT INTO test_items (name) VALUES ('hello;world');"
    )
    assertNull(insertRes.error)

    val selectRes = repo.executeRawSql("SELECT name FROM test_items;")
    assertNull(selectRes.error)
    assertEquals(1, selectRes.rows.size)
    assertEquals("hello;world", selectRes.rows[0][0])

    val tables = repo.listSqlTables().map { it.name }
    assertTrue(tables.contains("creations"))
    assertTrue(tables.contains("threads"))
    assertTrue(tables.contains("messages"))
    assertTrue(tables.contains("test_items"))
  }

  @Test
  fun `repository persists thought traces and background style`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = LuminaryDatabase.getInstance(context)
    val repo = LuminaryRepository(context, db)

    val threadId = repo.createThread(
      title = "Reasoning check",
      mode = com.example.ai.AnswerMode.BALANCED
    )
    repo.insertMessage(
      threadId = threadId,
      role = "model",
      text = "Final structured answer.",
      modeLabel = "Balanced",
      thoughts = "Step 1: Verify inputs.\nStep 2: Conclude.",
      thinkingSeconds = 4
    )
    val msgs = repo.getMessagesOnce(threadId)
    assertEquals(1, msgs.size)
    assertEquals("Step 1: Verify inputs.\nStep 2: Conclude.", msgs[0].thoughts)
    assertEquals(4, msgs[0].thinkingSeconds)

    repo.saveBackgroundStyle(com.example.ui.theme.BackgroundStyle.NEBULA)
  }
}
