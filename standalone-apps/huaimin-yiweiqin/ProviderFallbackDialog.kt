package com.cleo.cleos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** In the very same screen as DeepSeek/智谱/OpenAI/硅基流动/Kimi/随想.
 * This is provider switching, separate from rotating multiple keys at one URL.
 * No keys are shown or copied; a provider can be used only with its own saved key
 * and a nonblank configured model ID.
 */
@Composable
internal fun ProviderFallbackDialog(fields: EndpointFields, onClose: () -> Unit) {
    val panel=fields.providerPanel
    AlertDialog(
        onDismissRequest=onClose,
        title={ Text("跨服务商自动切换") },
        text={
            Column(
                modifier=Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(10.dp)
            ) {
                Text("依次尝试下方已启用的服务商。每家使用自己的 API Key、接口地址和模型。只有额度不足、鉴权失效或临时限流且尚未输出内容时才切换。",
                    fontSize=12.sp)
                Row(modifier=Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("启用跨服务商自动切换")
                    Switch(checked=panel?.enabled==true,onCheckedChange={ fields.setCrossProviderEnabled(it) })
                }
                Text("当前实际使用：" + (panel?.activeName ?: "按角色当前设置"),
                    fontSize=13.sp)
                TextButton(onClick={ fields.resetActiveProvider() }) {
                    Text("恢复优先使用角色当前服务商")
                }
                panel?.rows?.forEach { provider ->
                    Row(modifier=Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        Column {
                            Text(provider.name)
                            Text(if(provider.hasKey) "已保存该服务商 Key" else "未保存该服务商 Key（无法作为备用）",
                                fontSize=11.sp)
                        }
                        Switch(checked=provider.selected,
                            onCheckedChange={ fields.setProviderAllowed(provider.baseUrl,it) })
                    }
                    OutlinedTextField(
                        value=fields.providerModelDrafts[provider.baseUrl] ?: provider.model,
                        onValueChange={ fields.editProviderModel(provider.baseUrl,it) },
                        singleLine=true, modifier=Modifier.fillMaxWidth(),
                        label={ Text(provider.name + " 使用的模型 ID") }
                    )
                    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick={ fields.saveProviderModel(provider.baseUrl) }) { Text("保存模型") }
                        TextButton(onClick={ fields.moveProvider(provider.baseUrl,-1) }) { Text("↑ 优先") }
                        TextButton(onClick={ fields.moveProvider(provider.baseUrl,1) }) { Text("↓ 顺序") }
                    }
                }
                fields.providerWarning?.let { Text(it,fontSize=12.sp) }
                Text("切换会改变实际服务商、模型和计费方式；工具、图片等能力可能不同。只发送当前请求的数据，不共享各家的 API Key。输出中断后不会自动重放。",
                    fontSize=12.sp)
            }
        },
        confirmButton={ TextButton(onClick=onClose) { Text("完成") } }
    )
}
