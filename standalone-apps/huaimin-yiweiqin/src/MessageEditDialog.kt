package com.cleo.cleos.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cleo.cleos.data.MessageEdits
import com.cleo.cleos.data.MessageImages
import com.cleo.cleos.data.db.MessageEntity

@Composable
internal fun MessageEditDialog(
    source: MessageEntity,
    text: String,
    sending: Boolean,
    problem: String?,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("编辑消息") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("修改后从这条消息重新继续，AI 会重新回答。原来的完整对话保留在聊天记录里。")
                val pictures = MessageImages.decode(source.images).size
                if (pictures > 0) Text("这条消息的 $pictures 张图片会一起重新发送。")
                OutlinedTextField(
                    value = text, onValueChange = onChange, enabled = !sending,
                    modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8,
                    label = { Text("消息内容") },
                )
                if (problem != null) Text(problem)
            }
        },
        confirmButton = {
            TextButton(onClick = onSend, enabled = !sending && MessageEdits.canSubmit(source, text)) {
                Text(if (sending) "正在重新发送…" else "重新发送")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !sending) { Text("取消") }
        },
    )
}
