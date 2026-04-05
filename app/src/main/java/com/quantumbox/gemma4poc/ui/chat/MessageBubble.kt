package com.quantumbox.gemma4poc.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageBubble(message: ChatMessage) {
    if (message.isUser) {
        UserBubble(message)
    } else {
        AiMessage(message)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserBubble(message: ChatMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(12.dp),
        ) {
            if (message.images.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    message.images.forEach { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (message.text.isNotEmpty()) {
                Text(
                    text = message.text,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun AiMessage(message: ChatMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
    ) {
        // Thinking
        if (!message.thinking.isNullOrEmpty()) {
            ThinkingSection(message.thinking)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Markdown content
        if (message.text.isNotEmpty()) {
            MarkdownText(
                markdown = message.text,
            )
        }

        // Streaming indicator
        if (message.isStreaming && message.text.isEmpty()) {
            Text(
                text = "...",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ThinkingSection(thinking: String) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable { expanded = !expanded }
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (expanded) "Thinking" else "Thinking (tap to expand)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = thinking,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun MarkdownText(markdown: String) {
    val parser = remember { Parser.builder().build() }
    val document = remember(markdown) { parser.parse(markdown) }
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Column(modifier = Modifier.fillMaxWidth()) {
        var node = document.firstChild
        while (node != null) {
            when (node) {
                is Heading -> {
                    val style = when (node.level) {
                        1 -> typography.headlineSmall
                        2 -> typography.titleLarge
                        3 -> typography.titleMedium
                        else -> typography.titleSmall
                    }
                    Text(
                        text = renderInlineNodes(node),
                        style = style.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                is Paragraph -> {
                    Text(
                        text = renderInlineAnnotated(node),
                        style = typography.bodyLarge,
                        color = colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                is FencedCodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorScheme.surfaceVariant)
                            .padding(12.dp),
                    ) {
                        Text(
                            text = node.literal.trimEnd(),
                            style = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                is IndentedCodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorScheme.surfaceVariant)
                            .padding(12.dp),
                    ) {
                        Text(
                            text = node.literal.trimEnd(),
                            style = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                is BulletList -> {
                    RenderList(node, ordered = false)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                is OrderedList -> {
                    RenderList(node, ordered = true)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                is BlockQuote -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(colorScheme.primary.copy(alpha = 0.5f)),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = renderInlineNodes(node),
                            style = typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                            color = colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                is ThematicBreak -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colorScheme.outlineVariant),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            node = node.next
        }
    }
}

@Composable
private fun RenderList(listNode: Node, ordered: Boolean) {
    var item = listNode.firstChild
    var index = 1
    while (item != null) {
        if (item is ListItem) {
            Row(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = if (ordered) "${index}. " else "• ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = renderInlineAnnotated(item),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            index++
        }
        item = item.next
    }
}

private fun renderInlineNodes(node: Node): String {
    val sb = StringBuilder()
    collectText(node, sb)
    return sb.toString()
}

private fun collectText(node: Node, sb: StringBuilder) {
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is org.commonmark.node.Text -> sb.append(child.literal)
            is Code -> sb.append(child.literal)
            is SoftLineBreak -> sb.append(" ")
            is HardLineBreak -> sb.append("\n")
            else -> collectText(child, sb)
        }
        child = child.next
    }
}

private fun renderInlineAnnotated(node: Node): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        appendInlineChildren(node)
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineChildren(node: Node) {
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is org.commonmark.node.Text -> append(child.literal)
            is StrongEmphasis -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendInlineChildren(child)
                }
            }
            is Emphasis -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendInlineChildren(child)
                }
            }
            is Code -> {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                    append(child.literal)
                }
            }
            is SoftLineBreak -> append(" ")
            is HardLineBreak -> append("\n")
            is Paragraph -> {
                appendInlineChildren(child)
                append("\n")
            }
            else -> appendInlineChildren(child)
        }
        child = child.next
    }
}
