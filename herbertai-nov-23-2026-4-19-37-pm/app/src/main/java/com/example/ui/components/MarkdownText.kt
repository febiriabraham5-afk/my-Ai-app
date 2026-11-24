package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.delay

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class BulletItem(val marker: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock
    data object Divider : MdBlock
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Heading -> {
                        val style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.titleMedium
                        }
                        Text(
                            text = parseInlineMarkdown(block.text),
                            style = style,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    is MdBlock.Paragraph -> {
                        Text(
                            text = parseInlineMarkdown(block.text),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    is MdBlock.BulletItem -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = block.marker,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.tertiary
                                ),
                                modifier = Modifier
                                    .widthIn(min = 26.dp)
                                    .padding(end = 6.dp)
                            )
                            Text(
                                text = parseInlineMarkdown(block.text),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    is MdBlock.Quote -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Spacer(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(22.dp)
                                    .background(
                                        MaterialTheme.colorScheme.tertiary,
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = parseInlineMarkdown(block.text),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = FontStyle.Italic
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is MdBlock.CodeBlock -> {
                        CodeBlockCard(language = block.language, code = block.code)
                    }

                    is MdBlock.Table -> {
                        MarkdownTableCard(headers = block.headers, rows = block.rows)
                    }

                    MdBlock.Divider -> {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableCard(
    headers: List<String>,
    rows: List<List<String>>
) {
    val colCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                for (c in 0 until colCount) {
                    val cellText = headers.getOrNull(c).orEmpty()
                    Text(
                        text = parseInlineMarkdown(cellText),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .widthIn(min = 110.dp, max = 220.dp)
                            .padding(horizontal = 8.dp)
                    )
                }
            }
            rows.forEachIndexed { rIdx, row ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .background(
                            if (rIdx % 2 == 1) MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        .padding(horizontal = 8.dp, vertical = 9.dp)
                ) {
                    for (c in 0 until colCount) {
                        val cellText = row.getOrNull(c).orEmpty()
                        Text(
                            text = parseInlineMarkdown(cellText),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .widthIn(min = 110.dp, max = 220.dp)
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy),
                        tint = if (copied) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = code,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            )
        }
    }
}

@Composable
private fun parseInlineMarkdown(input: String): AnnotatedString {
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val codeFg = MaterialTheme.colorScheme.tertiary
    val linkColor = MaterialTheme.colorScheme.tertiary

    return buildAnnotatedString {
        var i = 0
        while (i < input.length) {
            when {
                input[i] == '[' -> {
                    val closeBracket = input.indexOf(']', i + 1)
                    val hasUrlParen = closeBracket != -1 &&
                        closeBracket + 1 < input.length &&
                        input[closeBracket + 1] == '('
                    val closeParen = if (hasUrlParen) input.indexOf(')', closeBracket + 2) else -1
                    if (closeBracket != -1 && hasUrlParen && closeParen != -1) {
                        val label = input.substring(i + 1, closeBracket)
                        val url = input.substring(closeBracket + 2, closeParen).trim()
                        val validScheme = url.startsWith("https://", ignoreCase = true) ||
                            url.startsWith("http://", ignoreCase = true)
                        if (label.isNotEmpty() && validScheme && !url.any { it.isWhitespace() }) {
                            withLink(
                                LinkAnnotation.Url(
                                    url = url,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = linkColor,
                                            fontWeight = FontWeight.Medium,
                                            textDecoration = TextDecoration.Underline
                                        )
                                    )
                                )
                            ) {
                                append(label)
                            }
                            i = closeParen + 1
                        } else {
                            append(input[i])
                            i++
                        }
                    } else {
                        append(input[i])
                        i++
                    }
                }
                input.startsWith("**", i) -> {
                    val end = input.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(input.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(input[i])
                        i++
                    }
                }
                input[i] == '`' -> {
                    val end = input.indexOf('`', i + 1)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 14.sp,
                                background = codeBg,
                                color = codeFg
                            )
                        ) {
                            append(" ${input.substring(i + 1, end)} ")
                        }
                        i = end + 1
                    } else {
                        append(input[i])
                        i++
                    }
                }
                input[i] == '*' && (i + 1 < input.length && input[i + 1] != ' ') -> {
                    val end = input.indexOf('*', i + 1)
                    if (end != -1 && end > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(input.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(input[i])
                        i++
                    }
                }
                else -> {
                    append(input[i])
                    i++
                }
            }
        }
    }
}

private fun isMarkdownTableRow(trimmed: String): Boolean =
    trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.count { it == '|' } >= 2

private fun isMarkdownSeparatorRow(trimmed: String): Boolean {
    if (!trimmed.contains("|") && !trimmed.contains("-")) return false
    val cells = parseTableCells(trimmed)
    return cells.isNotEmpty() && cells.all { cell ->
        cell.isNotBlank() && cell.all { it == '-' || it == ':' || it == ' ' }
    }
}

private fun parseTableCells(trimmed: String): List<String> =
    trimmed.trim()
        .removePrefix("|")
        .removeSuffix("|")
        .split("|")
        .map { it.trim() }

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.replace("\r\n", "\n").lines()
    val blocks = mutableListOf<MdBlock>()
    val paragraphBuffer = StringBuilder()
    var inCodeBlock = false
    var codeLang = ""
    val codeBuffer = StringBuilder()
    var orderedCounter = 0

    fun flushParagraph() {
        if (paragraphBuffer.isNotBlank()) {
            blocks.add(MdBlock.Paragraph(paragraphBuffer.toString().trim()))
            paragraphBuffer.clear()
        }
    }

    var idx = 0
    while (idx < lines.size) {
        val line = lines[idx]
        val leadingSpaces = line.length - line.trimStart().length
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                blocks.add(MdBlock.CodeBlock(codeLang, codeBuffer.toString().trimEnd()))
                codeBuffer.clear()
                codeLang = ""
                inCodeBlock = false
            } else {
                flushParagraph()
                if (leadingSpaces < 2) orderedCounter = 0
                inCodeBlock = true
                codeLang = trimmed.removePrefix("```").trim()
            }
            idx++
            continue
        }

        if (inCodeBlock) {
            if (codeBuffer.isNotEmpty()) codeBuffer.append('\n')
            codeBuffer.append(line)
            idx++
            continue
        }

        // Detect Markdown tables: header row + |---|---| separator row
        if (idx + 1 < lines.size &&
            isMarkdownTableRow(trimmed) &&
            isMarkdownSeparatorRow(lines[idx + 1].trim())
        ) {
            flushParagraph()
            orderedCounter = 0
            val headers = parseTableCells(trimmed)
            val rows = mutableListOf<List<String>>()
            idx += 2
            while (idx < lines.size) {
                val rowTrimmed = lines[idx].trim()
                if (!isMarkdownTableRow(rowTrimmed)) break
                rows.add(parseTableCells(rowTrimmed))
                idx++
            }
            blocks.add(MdBlock.Table(headers = headers, rows = rows))
            continue
        }

        when {
            trimmed.isEmpty() -> flushParagraph()
            trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                flushParagraph()
                orderedCounter = 0
                blocks.add(MdBlock.Divider)
            }
            trimmed.startsWith("### ") -> {
                flushParagraph()
                orderedCounter = 0
                blocks.add(MdBlock.Heading(3, trimmed.removePrefix("### ").trim()))
            }
            trimmed.startsWith("## ") -> {
                flushParagraph()
                orderedCounter = 0
                blocks.add(MdBlock.Heading(2, trimmed.removePrefix("## ").trim()))
            }
            trimmed.startsWith("# ") -> {
                flushParagraph()
                orderedCounter = 0
                blocks.add(MdBlock.Heading(1, trimmed.removePrefix("# ").trim()))
            }
            trimmed.startsWith("> ") -> {
                flushParagraph()
                orderedCounter = 0
                blocks.add(MdBlock.Quote(trimmed.removePrefix("> ").trim()))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                if (leadingSpaces < 2) {
                    orderedCounter = 0
                }
                blocks.add(MdBlock.BulletItem("•", trimmed.drop(2).trim()))
            }
            Regex("^\\d+\\.\\s+.*").matches(trimmed) -> {
                flushParagraph()
                val dotIndex = trimmed.indexOf('.')
                val parsedNum = trimmed.substring(0, dotIndex).toIntOrNull() ?: 1
                orderedCounter = if (orderedCounter == 0) {
                    parsedNum
                } else if (parsedNum > orderedCounter) {
                    parsedNum
                } else {
                    orderedCounter + 1
                }
                val marker = "$orderedCounter."
                val body = trimmed.substring(dotIndex + 1).trim()
                blocks.add(MdBlock.BulletItem(marker, body))
            }
            else -> {
                if (leadingSpaces < 2) {
                    orderedCounter = 0
                }
                if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append(' ')
                paragraphBuffer.append(trimmed)
            }
        }
        idx++
    }

    if (inCodeBlock && codeBuffer.isNotBlank()) {
        blocks.add(MdBlock.CodeBlock(codeLang, codeBuffer.toString().trimEnd()))
    }
    flushParagraph()
    return blocks
}
