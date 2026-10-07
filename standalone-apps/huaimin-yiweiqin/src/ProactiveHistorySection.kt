package com.cleo.cleos.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.sp
import com.cleo.cleos.data.db.WakeEntity
import com.cleo.cleos.glass.LocalGlassPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ProactiveHistorySection(vm: SettingsViewModel) {
    val records by vm.wakeHistory.collectAsStateWithLifecycle()
    if (records.isEmpty()) return
    val palette = LocalGlassPalette.current
    val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    Section("主动消息记录") {
        records.take(12).forEach { row ->
            val status = when (row.outcome) {
                WakeEntity.SENT -> "已发送"
                WakeEntity.SKIPPED -> "保持安静"
                WakeEntity.HELD -> "已跳过"
                WakeEntity.EXPIRED -> "已过期"
                WakeEntity.FAILED -> "失败"
                else -> row.outcome
            }
            val detail = row.detail.ifBlank { status }
            Text(
                "${format.format(Date(row.at))} · $status\n$detail",
                color = palette.contentSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        Text("这里只显示结果和原因，不保存或展示模型的隐藏思考。", color = palette.contentSecondary, fontSize = 11.sp)
    }
}
