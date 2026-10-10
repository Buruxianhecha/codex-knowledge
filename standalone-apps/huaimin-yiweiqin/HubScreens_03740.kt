package com.cleo.cleos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.cleo.cleos.data.db.CompanionEntity
import com.cleo.cleos.data.db.ConversationEntity
import com.cleo.cleos.glass.*
import com.cleo.cleos.ui.common.*

/** Directory, not a stripped-down rewrite: every feature opens its original page. */
@Composable
fun DiscoverTab(bottomInset:Dp,onDiary:()->Unit,onTodo:()->Unit,
                onLetters:()->Unit,onMemory:()->Unit) {
    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    GlassPage(overlay={p->GlassTopBar(title="发现",backdrop=p)}) {
        LazyColumn(
            modifier=Modifier.fillMaxSize().fadeUnderTopBar(top+TopBarHeight),
            contentPadding=PaddingValues(start=14.dp,end=14.dp,top=top+TopBarHeight+16.dp,bottom=bottomInset+28.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)
        ) {
            item { DirectoryRow("日记","所有日记、私密日记、TA 的日记、写日记",Icons.Rounded.AutoStories,onDiary) }
            item { DirectoryRow("待办","添加、编辑、完成与恢复待办事项",Icons.Rounded.TaskAlt,onTodo) }
            item { DirectoryRow("回忆","TA 保存的长期记忆",Icons.Rounded.AutoStories,onMemory) }
        }
    }
}

@Composable
private fun DirectoryRow(title:String,sub:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit) {
    val p=LocalGlassPalette.current
    GlassSurface(modifier=Modifier.fillMaxWidth().clickable(onClick=onClick),
        shape=GlassShape.Rounded(20.dp),
        contentPadding=PaddingValues(horizontal=17.dp,vertical=18.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Icon(icon,contentDescription=null,tint=p.accentContent,modifier=Modifier.size(28.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,color=p.content,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                Text(sub,color=p.contentSecondary,fontSize=12.sp)
            }
            Icon(Icons.Rounded.ChevronRight,contentDescription="打开"+title,tint=p.contentSecondary)
        }
    }
}

/** Existing AI characters are the contacts; groups are in a second-level directory. */
@Composable
fun ContactsTab(bottomInset:Dp,onOpenCompanion:(Long)->Unit,
                onOpenGroups:()->Unit,onAddCompanion:()->Unit) {
    val c=appContainer()
    val people by remember {c.companions.all}.collectAsStateWithLifecycle(emptyList())
    val groups by remember {c.db.conversations().observeGroups()}.collectAsStateWithLifecycle(emptyList())
    val current by remember {c.companions.current}.collectAsStateWithLifecycle(null)
    var keyword by rememberSaveable { mutableStateOf("") }
    val matches=people.filter {keyword.isBlank() || it.name.contains(keyword,ignoreCase=true)}
        .sortedWith(compareBy<CompanionEntity>{it.name.lowercase()}.thenBy{it.id})
    val p=LocalGlassPalette.current
    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    GlassPage(overlay={page->
        GlassTopBar(title="通讯录",subtitle=people.size.toString()+" 位 AI 联系人",
            backdrop=page,trailing={
                GlassIconButton(Icons.Rounded.PersonAdd,"创建 AI 角色",onAddCompanion,page)
            })
    }) {
        LazyColumn(modifier=Modifier.fillMaxSize().fadeUnderTopBar(top+TopBarHeight),
            contentPadding=PaddingValues(start=14.dp,end=14.dp,top=top+TopBarHeight+10.dp,bottom=bottomInset+24.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(value=keyword,onValueChange={keyword=it},
                    label={Text("搜索 AI 联系人")},singleLine=true,modifier=Modifier.fillMaxWidth(),
                    leadingIcon={Icon(Icons.Rounded.Search,contentDescription=null)})
            }
            item {DirectoryRow("群聊",groups.size.toString()+" 个群聊 · 点击查看全部",Icons.Rounded.Group,onOpenGroups)}
            item {Text("我的 AI 联系人",fontSize=14.sp,color=p.contentSecondary,
                modifier=Modifier.padding(start=8.dp,top=12.dp,bottom=4.dp))}
            if(matches.isEmpty()) item {
                Text(if(keyword.isNotBlank()) "没有找到匹配角色" else "还没有创建 AI 角色",
                    color=p.contentSecondary,modifier=Modifier.padding(16.dp))
            }
            items(matches,key={it.id}) { ta ->
                GlassSurface(modifier=Modifier.fillMaxWidth().clickable {onOpenCompanion(ta.id)},
                    shape=GlassShape.Rounded(18.dp),
                    contentPadding=PaddingValues(horizontal=16.dp,vertical=13.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)) {
                        ContactImage(ta)
                        Column(Modifier.weight(1f)) {
                            Text(ta.name.ifBlank{"TA "+ta.id},color=p.content,fontSize=16.sp,
                                fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(if(ta.id==current?.id) "正在聊天 · 点击进入" else "点击进入单聊",
                                color=p.contentSecondary,fontSize=12.sp)
                        }
                        Icon(Icons.Rounded.ChevronRight,contentDescription="进入单聊",tint=p.contentSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactImage(ta:CompanionEntity) {
    val c=appContainer()
    val p=LocalGlassPalette.current
    val name=ta.name.ifBlank{"TA"}
    val avatar=ta.avatar
    if(avatar!=null) {
        AsyncImage(model=c.images.file(avatar),contentDescription=name+"的头像",
            contentScale=ContentScale.Crop,modifier=Modifier.size(48.dp).clip(CircleShape))
    } else Box(Modifier.size(48.dp).clip(CircleShape).background(p.accentContent.copy(alpha=0.17f)),
        contentAlignment=Alignment.Center) {
        Text(ta.avatarEmoji?.takeIf{it.isNotBlank()} ?: name.take(1),
            color=p.accentContent,fontSize=22.sp)
    }
}

/** The second-level group list is live Room data, not a cached group snapshot. */
@Composable
fun GroupDirectoryTab(onBack:()->Unit,
    onOpenGroup:(ConversationEntity)->Unit,onCreateGroup:(Set<Long>)->Unit) {
    val c=appContainer()
    val groups by remember {c.db.conversations().observeGroups()}.collectAsStateWithLifecycle(emptyList())
    val people by remember {c.companions.all}.collectAsStateWithLifecycle(emptyList())
    val p=LocalGlassPalette.current
    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var creating by remember {mutableStateOf(false)}
    var chosen by remember {mutableStateOf(emptySet<Long>())}
    GlassPage(overlay={page->
        GlassTopBar(title="群聊",subtitle=groups.size.toString()+" 个群聊",backdrop=page,
            leading={GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack,"返回通讯录",onBack,page)},
            trailing={GlassIconButton(Icons.Rounded.Add,"创建群聊",{creating=true},page)})
    }) {
        LazyColumn(modifier=Modifier.fillMaxSize().fadeUnderTopBar(top+TopBarHeight),
            contentPadding=PaddingValues(start=14.dp,end=14.dp,top=top+TopBarHeight+12.dp,bottom=30.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(groups.isEmpty()) item {
                Text("还没有群聊。点击右上角＋，邀请至少两个 AI 角色建立群聊。",
                    color=p.contentSecondary,modifier=Modifier.padding(16.dp))
            }
            items(groups,key={it.id}) { group ->
                GlassSurface(modifier=Modifier.fillMaxWidth().clickable{onOpenGroup(group)},
                    shape=GlassShape.Rounded(20.dp),
                    contentPadding=PaddingValues(horizontal=16.dp,vertical=18.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Rounded.Group,contentDescription=null,tint=p.accentContent,modifier=Modifier.size(30.dp))
                        Column(Modifier.weight(1f)) {
                            Text(group.title.ifBlank{"群聊 "+group.id},color=p.content,fontSize=16.sp,
                                fontWeight=FontWeight.Medium)
                            Text("点击进入群聊 · 在聊天中管理成员",color=p.contentSecondary,fontSize=12.sp)
                        }
                        Icon(Icons.Rounded.ChevronRight,contentDescription="进入群聊",tint=p.contentSecondary)
                    }
                }
            }
        }
    }
    if(creating) AlertDialog(onDismissRequest={creating=false},title={Text("创建 AI 群聊")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("选择 2–6 位 AI 角色，每位保留独立的人格与模型。",
                    color=p.contentSecondary,fontSize=12.sp)
                people.forEach {ta->
                    Row(modifier=Modifier.fillMaxWidth().clickable{
                        chosen=if(ta.id in chosen) chosen-ta.id else if(chosen.size<6) chosen+ta.id else chosen
                    },verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=ta.id in chosen,onCheckedChange={on->
                            chosen=if(!on) chosen-ta.id else if(chosen.size<6) chosen+ta.id else chosen
                        })
                        Text(ta.name.ifBlank{"TA "+ta.id},color=p.content)
                    }
                }
            }
        },confirmButton={TextButton(enabled=chosen.size>=2,onClick={
            onCreateGroup(chosen)
            chosen=emptySet();creating=false
        }) {Text("创建")}},
        dismissButton={TextButton(onClick={creating=false}){Text("取消")}})
}
