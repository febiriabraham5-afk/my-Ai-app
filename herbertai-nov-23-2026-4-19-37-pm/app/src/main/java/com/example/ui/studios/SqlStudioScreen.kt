package com.example.ui.studios

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.SqlStudioUiState
import com.example.ui.StudioViewModel

private val SQL_QUICK_SNIPPETS = listOf(
    "Creations" to "SELECT id, type, title, metadata, createdAt FROM creations ORDER BY createdAt DESC LIMIT 25;",
    "Threads" to "SELECT id, title, modeName, updatedAt FROM threads ORDER BY updatedAt DESC LIMIT 25;",
    "Bookmarks" to "SELECT id, threadId, substr(text, 1, 90) AS preview FROM messages WHERE isBookmarked = 1;",
    "Create Notes Table" to "CREATE TABLE IF NOT EXISTS user_notes (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, body TEXT, created_at INTEGER);",
    "Insert Note" to "INSERT INTO user_notes (title, body, created_at) VALUES ('Luminary Note', 'Stored in local SQLite database', strftime('%s','now'));",
    "Read Notes" to "SELECT * FROM user_notes ORDER BY id DESC;"
)

@Composable
fun SqlStudioScreen(
    studioViewModel: StudioViewModel,
    state: SqlStudioUiState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 880.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("SQLite Database Studio", style = MaterialTheme.typography.titleLarge)
                        }
                        IconButton(
                            onClick = studioViewModel::refreshSqlTables,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sql_refresh_tables_btn")
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh SQL tables")
                        }
                    }

                    Text(
                        text = "Inspect Room SQLite tables, create custom tables, run SQL queries, or ask Gemini to write SQL from plain English.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        state.tables.forEach { table ->
                            FilterChip(
                                selected = state.selectedTable == table.name,
                                onClick = { studioViewModel.inspectSqlTable(table.name) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("${table.name} (${table.rowCount})") },
                                modifier = Modifier.testTag("sql_table_${table.name}")
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ask Gemini -> SQL", style = MaterialTheme.typography.titleMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.nlPrompt,
                            onValueChange = studioViewModel::updateSqlNlPrompt,
                            label = { Text("e.g., Count creations by type or list my recent threads") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sql_nl_input")
                        )
                        OutlinedButton(
                            onClick = studioViewModel::generateSqlFromNaturalLanguage,
                            enabled = !state.isGeneratingSql && state.nlPrompt.isNotBlank(),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("sql_nl_generate_btn")
                        ) {
                            if (state.isGeneratingSql) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = "Generate SQL", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("SQL")
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        SQL_QUICK_SNIPPETS.forEach { (label, query) ->
                            AssistChip(
                                onClick = {
                                    studioViewModel.updateSqlQuery(query)
                                    studioViewModel.runSqlQuery(query)
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.sqlQuery,
                        onValueChange = studioViewModel::updateSqlQuery,
                        label = { Text("SQLite Statement") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sql_editor_input")
                    )

                    Button(
                        onClick = { studioViewModel.runSqlQuery() },
                        enabled = !state.isRunningSql,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("run_sql_btn")
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isRunningSql) "Executing..." else "Run SQL Query")
                    }
                }
            }
        }

        val result = state.lastResult
        if (result != null) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (result.error != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = result.error,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            Text(
                                text = result.statusMessage,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            if (result.columns.isNotEmpty()) {
                                val scroll = rememberScrollState()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(scroll)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(vertical = 8.dp, horizontal = 6.dp)
                                    ) {
                                        result.columns.forEach { col ->
                                            Text(
                                                text = col,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                ),
                                                modifier = Modifier
                                                    .width(136.dp)
                                                    .padding(horizontal = 6.dp)
                                            )
                                        }
                                    }
                                    result.rows.forEachIndexed { rIdx, row ->
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        Row(
                                            modifier = Modifier
                                                .background(
                                                    if (rIdx % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                                    else MaterialTheme.colorScheme.surface
                                                )
                                                .padding(vertical = 8.dp, horizontal = 6.dp)
                                        ) {
                                            row.forEach { cell ->
                                                Text(
                                                    text = cell,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                    modifier = Modifier
                                                        .width(136.dp)
                                                        .padding(horizontal = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
