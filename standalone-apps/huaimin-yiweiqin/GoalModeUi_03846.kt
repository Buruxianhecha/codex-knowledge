package com.cleo.cleos.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleo.cleos.ai.GoalRecord
import com.cleo.cleos.ai.GoalBudget
import com.cleo.cleos.ai.GoalRules

private fun label(status: String) = when(status) {
    "PENDING" -> "待开始"
    "RUNNING" -> "执行中"
    "VERIFYING" -> "验收中"
    "WAITING_INPUT" -> "等待输入／授权"
    "PAUSED" -> "已暂停"
    "BLOCKED" -> "受阻"
    "COMPLETED" -> "已完成"
    "FAILED" -> "失败"
    "CANCELED" -> "已取消"
    "PASSED" -> "通过"
    "UNVERIFIED" -> "未验证"
    "DONE" -> "已完成"
    "ACTIVE" -> "进行中"
    else -> status
}

@Composable
fun GoalStatusPill(goal: GoalRecord, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val passed=goal.criteria.count { it.status=="PASSED" }
    val current=goal.steps.firstOrNull { it.status !in setOf("DONE") }?.title
        ?: if(goal.steps.isEmpty()) "等待 AI 生成计划" else "等待验收"
    Column(
        modifier.fillMaxWidth()
            .background(Color(0xEDF6F8FC), RoundedCornerShape(15.dp))
            .clickable(onClick=onOpen)
            .padding(horizontal=14.dp,vertical=9.dp),
    ) {
        Text("◎ 目标模式 · ${label(goal.status)} · ${passed}/${goal.criteria.size} 项验收",
            fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFF243B54))
        Text(goal.description.take(70),fontSize=12.sp,color=Color(0xFF40556A),maxLines=1)
        Text("当前：${current.take(65)}",fontSize=11.sp,color=Color(0xFF536579),maxLines=1)
    }
}

/** Full in-app controls; the model does not decide whether pause/cancel is honored. */
@Composable
fun GoalModeDialog(
    current: GoalRecord?,
    history: List<GoalRecord>,
    onDismiss: () -> Unit,
    onCreate: (String,String,List<String>,List<String>,GoalBudget,Boolean)->Unit,
    onControl: (String,String,String,List<String>,List<String>,GoalBudget,Int)->Unit,
) {
    var newMode by remember(current?.id) { mutableStateOf(current==null) }
    var description by remember(current?.id,newMode) { mutableStateOf(if(newMode) "" else current?.description.orEmpty()) }
    var constraints by remember(current?.id,newMode) { mutableStateOf(if(newMode) "" else current?.constraints.orEmpty()) }
    var criteria by remember(current?.id,newMode) {
        mutableStateOf(if(newMode) "" else current?.criteria?.joinToString("\n"){it.text}.orEmpty())
    }
    var steps by remember(current?.id,newMode) {
        mutableStateOf(if(newMode) "" else current?.steps?.joinToString("\n"){it.title}.orEmpty())
    }
    var calls by remember(current?.id,newMode) {
        mutableStateOf((if(newMode) 18 else current?.budget?.maxRounds ?: 18).toString())
    }
    var minutes by remember(current?.id,newMode) {
        mutableStateOf((if(newMode) 25 else current?.budget?.maxMinutes ?: 25).toString())
    }
    var replace by remember(current?.id) { mutableStateOf(false) }
    val budget=GoalBudget(calls.toIntOrNull() ?: 18,minutes.toIntOrNull() ?: 25).safe()
    val checks=criteria.lineSequence().map {it.trim()}.filter {it.isNotBlank()}.take(20).toList()
    val plan=steps.lineSequence().map {it.trim()}.filter {it.isNotBlank()}.take(30).toList()
    AlertDialog(
        onDismissRequest=onDismiss,
        title={ Text(if(newMode) "创建目标" else "目标模式 · ${label(current?.status.orEmpty())}") },
        text={
            Column(Modifier.heightIn(max=530.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(current!=null) {
                    val passed=current.criteria.count { it.status=="PASSED" }
                    Text("目标 #${current.id.take(8)} · 版本 ${current.version} · ${label(current.status)}",
                        fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                    Text("已核验 ${passed}/${current.criteria.size} 项 · 已发起 ${current.rounds}/${current.budget.maxRounds} 次模型请求",
                        fontSize=12.sp)
                    Text("已记录耗时约 ${(current.elapsedMs+(current.runningSince?.let{(System.currentTimeMillis()-it).coerceAtLeast(0)}?:0))/1000} 秒；Token 和费用未获接口可靠数据时不显示精确值。",
                        fontSize=11.sp)
                    if(current.lastError.isNotBlank())
                        Text("注意：${current.lastError}",color=Color(0xFFB54E28),fontSize=12.sp)
                    if(current.delivery.capabilityGap.isNotBlank()) {
                        Text("文件交付能力：${current.delivery.capabilityGap}",
                            color=Color(0xFF6D5A2D),fontSize=12.sp)
                    }
                    val deliveryIssue = current.pending==null && (
                        com.cleo.cleos.ai.GoalDeliveryRules.formatGap(current.lastError) ||
                        current.delivery.capabilityGap.isNotBlank())
                    if(deliveryIssue && current.status !in GoalRules.terminal) {
                        Text(
                            if(com.cleo.cleos.ai.GoalDeliveryRules.fileIsRequired(current) &&
                                !current.delivery.userConfirmed)
                                "原目标的文件或字体要求仍属于必需验收。可以先继续检索和撰写；如改成正文/Markdown，需由你亲自确认，原验收结果将重新核验。"
                            else
                                "可使用聊天正文或 Markdown 继续整理；未生成的文件不能显示成已经交付。",
                            fontSize=12.sp)
                        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick={
                                onControl("resume","","",emptyList(),emptyList(),budget,-1)
                            }) { Text("继续原目标",fontSize=12.sp) }
                        }
                        TextButton(onClick={
                            onControl("delivery_chat","","",emptyList(),emptyList(),budget,-1)
                        }) { Text("确认改为聊天正文交付",fontSize=12.sp) }
                        TextButton(onClick={
                            onControl("delivery_markdown","","",emptyList(),emptyList(),budget,-1)
                        }) { Text("确认改为 Markdown 交付",fontSize=12.sp) }
                    }
                    if(current.pending!=null) {
                        Text("待授权工具：${current.pending.name}。请核对动作是否符合目标及当前权限。",
                            fontSize=12.sp,color=Color(0xFF9B3F24))
                        TextButton(onClick={onControl("approve","","",emptyList(),emptyList(),budget,-1)}) {
                            Text("我已核对，允许这次工具调用")
                        }
                    }
                    if(current.inFlight!=null && current.status!="RUNNING")
                        TextButton(onClick={onControl("reconcile","","",emptyList(),emptyList(),budget,-1)}) {
                            Text("我已检查中断操作（不自动认定成功）")
                        }
                    if(!newMode) {
                        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            if(current.status=="RUNNING")
                                TextButton(onClick={onControl("pause","","",emptyList(),emptyList(),budget,-1)}) { Text("暂停") }
                            else if(current.status !in GoalRules.terminal)
                                TextButton(onClick={onControl("resume","","",emptyList(),emptyList(),budget,-1)}) { Text("继续") }
                            if(current.status !in GoalRules.terminal)
                                TextButton(onClick={onControl("cancel","","",emptyList(),emptyList(),budget,-1)}) { Text("取消") }
                            TextButton(onClick={newMode=true}) { Text("新建目标") }
                        }
                        Text("步骤与检查点",fontWeight=FontWeight.SemiBold,fontSize=13.sp)
                        if(current.steps.isEmpty()) Text("等待 AI 生成初始计划",fontSize=12.sp)
                        current.steps.forEachIndexed { i,step ->
                            Text("${i+1}. [${label(step.status)}] ${step.title}\n${step.result.take(150)}",
                                fontSize=12.sp)
                        }
                        Text("验收证据",fontWeight=FontWeight.SemiBold,fontSize=13.sp)
                        current.criteria.forEachIndexed { i,item ->
                            Text("${i+1}. [${label(item.status)}] ${item.text}\n证据：${item.evidence.ifBlank{"未提供可验证依据"}}",
                                fontSize=12.sp)
                            if(current.status !in GoalRules.terminal) {
                                Row {
                                    TextButton(onClick={onControl("pass","","",emptyList(),emptyList(),budget,i)}){Text("我验收通过",fontSize=11.sp)}
                                    TextButton(onClick={onControl("fail","","",emptyList(),emptyList(),budget,i)}){Text("未通过",fontSize=11.sp)}
                                }
                            }
                        }
                        if(current.receipts.isNotEmpty()) {
                            Text("工具回执",fontWeight=FontWeight.SemiBold,fontSize=13.sp)
                            current.receipts.takeLast(5).forEach {
                                Text("${it.tool} · ${it.id.take(24)} · ${if(it.verified)"已获取可信结果" else "尚未独立核验"}",fontSize=11.sp)
                            }
                        }
                        if(current.events.isNotEmpty()) {
                            Text("最近任务事件",fontWeight=FontWeight.SemiBold,fontSize=13.sp)
                            current.events.takeLast(5).forEach {
                                Text("${it.type} · ${it.detail.take(110)}",fontSize=11.sp)
                            }
                        }
                        Text("编辑下方内容后保存，将增加目标版本并使旧验收结果失效。",
                            fontSize=11.sp,color=Color(0xFF5B6F80))
                    }
                }
                if(newMode && current!=null) {
                    Row {
                        Checkbox(checked=replace,onCheckedChange={replace=it})
                        Text("明确取消当前目标（历史保留），创建并运行新目标",
                            Modifier.padding(top=13.dp),fontSize=12.sp)
                    }
                }
                OutlinedTextField(description,{description=it},label={Text("目标描述")},modifier=Modifier.fillMaxWidth(),minLines=2)
                OutlinedTextField(constraints,{constraints=it},label={Text("约束与权限边界")},modifier=Modifier.fillMaxWidth(),minLines=2)
                OutlinedTextField(criteria,{criteria=it},label={Text("必需验收标准（每行一项）")},modifier=Modifier.fillMaxWidth(),minLines=2)
                OutlinedTextField(steps,{steps=it},label={Text("初始步骤（每行一项，可留空让 AI 生成）")},modifier=Modifier.fillMaxWidth(),minLines=2)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(calls,{calls=it.filter(Char::isDigit).take(3)},
                        label={Text("最大模型轮数")},modifier=Modifier.weight(1f))
                    OutlinedTextField(minutes,{minutes=it.filter(Char::isDigit).take(3)},
                        label={Text("最长分钟")},modifier=Modifier.weight(1f))
                }
                if(history.isNotEmpty()) {
                    Text("历史目标（不会因清除活动目标而删除）",fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                    history.takeLast(5).reversed().forEach {
                        Text("${label(it.status)} · ${it.description.take(60)}",fontSize=11.sp)
                    }
                }
            }
        },
        confirmButton={
            TextButton(
                enabled=description.isNotBlank() && checks.isNotEmpty() &&
                    (current==null || !newMode || replace),
                onClick={
                    if(newMode) onCreate(description,constraints,checks,plan,budget,replace)
                    else onControl("edit",description,constraints,checks,plan,budget,-1)
                },
            ) { Text(if(newMode) "创建并开始" else "保存修改") }
        },
        dismissButton={TextButton(onClick=onDismiss){Text("关闭")}},
    )
}
