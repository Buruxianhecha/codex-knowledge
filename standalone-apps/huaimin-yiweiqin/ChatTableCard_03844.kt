package com.cleo.cleos.ui.chat

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleo.cleos.data.ChatTable
import com.cleo.cleos.data.ChatTables
import kotlinx.coroutines.launch

/**
 * A genuine table message, not text with | separators.
 * Paper-like high-contrast cards stay readable over custom wallpaper and in dark mode.
 * Wide grids scroll horizontally; oversized tables expand vertically in a dialog.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatTableCard(table: ChatTable, onLongClick: () -> Unit = {}) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    fun copy() {
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("表格", ChatTables.tsv(table))))
        }
    }
    Column(
        Modifier.widthIn(max = 310.dp)
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE2E5EA), RoundedCornerShape(16.dp))
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFF2F4F7))
                .padding(start = 10.dp, end = 2.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(table.title.ifBlank { "表格" }, color = Color(0xFF263343),
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            TextButton(onClick = ::copy) { Text("复制", fontSize = 12.sp) }
            TextButton(onClick = { expanded = true }) { Text("展开", fontSize = 12.sp) }
        }
        TableGrid(table, expanded = false)
        if (table.rows.size > 8 || table.columns.size > 2) {
            Text(
                if (table.rows.size > 8) "显示前8行 · 展开查看全部" else "左右滑动查看完整列",
                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                fontSize = 11.sp, color = Color(0xFF697688),
            )
        }
    }
    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = {
                Text(table.title.ifBlank { "完整表格" }, fontSize = 17.sp)
            },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    TableGrid(table, expanded = true)
                }
            },
            confirmButton = { TextButton(onClick = ::copy) { Text("复制表格") } },
            dismissButton = { TextButton(onClick = { expanded = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun TableGrid(table: ChatTable, expanded: Boolean) {
    val widths = table.columns.mapIndexed { index, _ ->
        when {
            table.columns.size == 2 -> if (index == 0) 140.dp else 180.dp
            table.columns.size == 3 -> when (index) {
                0 -> 138.dp
                1 -> 110.dp
                else -> 200.dp
            }
            else -> 150.dp
        }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Start,
    ) {
        Column {
            TableGridRow(table.columns, widths, header = true)
            (if (expanded) table.rows else table.rows.take(8)).forEachIndexed { index, cells ->
                TableGridRow(cells, widths, header = false, alt = index % 2 == 1)
            }
        }
    }
}

@Composable
private fun TableGridRow(cells: List<String>, widths: List<Dp>, header: Boolean, alt: Boolean = false) {
    Row {
        cells.forEachIndexed { index, cell ->
            Box(
                Modifier.width(widths[index])
                    .background(
                        when {
                            header -> Color(0xFFF6F7F9)
                            alt -> Color(0xFFFAFBFD)
                            else -> Color.White
                        },
                    )
                    .border(0.5.dp, Color(0xFFE3E6EA))
                    .padding(horizontal = 10.dp, vertical = 11.dp),
            ) {
                Text(
                    text = cell, color = Color(0xFF25303B),
                    fontSize = 13.sp, lineHeight = 19.sp,
                    fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = if (header || expanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
