package com.cleo.cleos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cleo.cleos.data.MomentVisibility
import com.cleo.cleos.data.db.CompanionEntity

/**
 * "Who can see" is enforced by MomentsStore and the autonomous agent, not only UI.
 * Each AI role is a distinct local friend identity; there is no real WeChat network.
 */
@Composable
fun MomentAudienceDialog(
    original:MomentVisibility,
    originalIds:Set<Long>,
    people:List<CompanionEntity>,
    onSave:(MomentVisibility,Set<Long>)->Unit,
    onDismiss:()->Unit
) {
    var mode by remember(original) {mutableStateOf(original)}
    var ids by remember(original,originalIds) {mutableStateOf(originalIds)}
    val modes=listOf(
        MomentVisibility.PUBLIC to ("公开" to "所有已创建的 AI 联系人可见"),
        MomentVisibility.PRIVATE to ("私密" to "只有你自己能看"),
        MomentVisibility.SELECTED to ("部分可见" to "仅勾选的 AI 朋友可见"),
        MomentVisibility.EXCLUDED to ("不给谁看" to "除勾选的 AI 朋友外均可见")
    )
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("谁可以看")},
        text={
            Column(modifier=Modifier.heightIn(max=530.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(4.dp)) {
                modes.forEach { (value, pair) ->
                    Row(Modifier.fillMaxWidth().clickable {mode=value},
                        verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(selected=mode==value,onClick={mode=value})
                        Column(Modifier.weight(1f)) {
                            Text(pair.first)
                            Text(pair.second,style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if(mode==MomentVisibility.SELECTED || mode==MomentVisibility.EXCLUDED) {
                    HorizontalDivider(Modifier.padding(vertical=7.dp))
                    Text("选择 AI 联系人",style=MaterialTheme.typography.titleSmall)
                    if(people.isEmpty()) Text("没有 AI 联系人，请先去通讯录创建角色")
                    people.forEach { ta ->
                        Row(Modifier.fillMaxWidth().clickable {
                            ids=if(ta.id in ids) ids-ta.id else ids+ta.id
                        },verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(checked=ta.id in ids,onCheckedChange={isChecked->
                                ids=if(isChecked) ids+ta.id else ids-ta.id
                            })
                            Text(ta.name.ifBlank{"TA "+ta.id})
                        }
                    }
                }
                Text("可见范围会影响 AI 自主浏览、点赞、评论和手动邀请；不会把私密内容发送给无权查看的模型。",
                    style=MaterialTheme.typography.bodySmall,
                    modifier=Modifier.padding(top=12.dp))
            }
        },
        confirmButton={
            TextButton(
                enabled=(mode!=MomentVisibility.SELECTED && mode!=MomentVisibility.EXCLUDED) ||
                    ids.isNotEmpty(),
                onClick={
                    onSave(mode,if(mode==MomentVisibility.SELECTED || mode==MomentVisibility.EXCLUDED)
                        ids else emptySet())
                }
            ) {Text("确定")}
        },
        dismissButton={TextButton(onClick=onDismiss){Text("取消")}}
    )
}
