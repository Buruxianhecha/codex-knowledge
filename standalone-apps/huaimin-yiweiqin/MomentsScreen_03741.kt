package com.cleo.cleos.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.cleo.cleos.data.MomentPost
import com.cleo.cleos.data.MomentVisibility
import com.cleo.cleos.data.MomentAccess
import com.cleo.cleos.data.MomentsRules
import com.cleo.cleos.data.db.CompanionEntity
import com.cleo.cleos.glass.*
import com.cleo.cleos.ui.common.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Actual user-owned Moments, with real AI comments only on a manual invitation. */
@Composable
fun MomentsScreen(onBack:()->Unit,onOpenImage:(String)->Unit) {
    val c=appContainer()
    val data by c.moments.posts.collectAsStateWithLifecycle()
    val people by remember { c.companions.all }.collectAsStateWithLifecycle(emptyList())
    val s by c.settings.settings.collectAsStateWithLifecycle(null)
    val scope=rememberCoroutineScope()
    val p=LocalGlassPalette.current
    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var posting by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var failure by remember {mutableStateOf<String?>(null)}
    var draft by remember {mutableStateOf("")}
    var photos by remember {mutableStateOf(emptyList<Uri>())}
    var commenting by remember {mutableStateOf<MomentPost?>(null)}
    var commentDraft by remember {mutableStateOf("")}
    var inviting by remember {mutableStateOf<MomentPost?>(null)}
    var deleting by remember {mutableStateOf<MomentPost?>(null)}
    var editingAudience by remember {mutableStateOf<MomentPost?>(null)}
    var choosingAudience by remember {mutableStateOf(false)}
    var visibility by remember {mutableStateOf(MomentVisibility.PUBLIC)}
    var visibleIds by remember {mutableStateOf(emptySet<Long>())}
    var onlyMine by remember {mutableStateOf(false)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { chosen ->
        photos=chosen.take(MomentsRules.MAX_IMAGES)
    }
    fun execute(task:suspend ()->Unit) {
        if(busy) return
        busy=true
        failure=null
        scope.launch {
            try { task() }
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {failure=e.message ?: "操作失败，请稍后重试"}
            finally {busy=false}
        }
    }
    BackHandler { if(onlyMine) onlyMine=false else onBack() }
    LaunchedEffect(Unit) {c.moments.reload()}
    GlassPage(overlay={ page ->
        GlassTopBar(title=if(onlyMine) "我的朋友圈" else "朋友圈",subtitle="生活点滴与 AI 朋友的动态",backdrop=page,
            leading={ GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack,"返回", { if(onlyMine) onlyMine=false else onBack() },page) },
            trailing={ GlassIconButton(Icons.Rounded.Add,"发布动态",{ posting=true },page) })
    }) {
        LazyColumn(
            modifier=Modifier.fillMaxSize().fadeUnderTopBar(top+TopBarHeight),
            contentPadding=PaddingValues(start=14.dp,end=14.dp,top=top+TopBarHeight+8.dp,
                bottom=bottom+25.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)
        ) {
            item {
                MomentsMyProfile(onOpenMyTimeline={onlyMine=true})
            }
            /* Old welcome placeholder replaced with the real editable profile header. */
            /*
            item {
                GlassSurface(modifier=Modifier.fillMaxWidth(),shape=GlassShape.Rounded(22.dp),
                    contentPadding=PaddingValues(horizontal=20.dp,vertical=20.dp)) {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("把生活留在这里",color=p.content,fontSize=21.sp,fontWeight=FontWeight.Bold)
                        Text("文字、照片和那些想对 TA 说的小事。点右上角＋发布动态，也可以手动邀请一位 AI 来评论。",
                            color=p.contentSecondary,fontSize=13.sp)
                    }
                }
            }
            */
            if((if(onlyMine)data.posts.none{it.authorId==0L} else data.posts.isEmpty())) item {
                GlassSurface(modifier=Modifier.fillMaxWidth(),shape=GlassShape.Rounded(20.dp),
                    contentPadding=PaddingValues(22.dp)) {
                    Text("还没有动态。点击右上角 ＋，写下第一条朋友圈。",
                        color=p.contentSecondary,fontSize=15.sp)
                }
            }
            items(if(onlyMine)data.posts.filter{it.authorId==0L} else data.posts,key={it.id}) { post ->
                val authorName=if(post.authorId==0L) s?.userName?.ifBlank{"我"} ?: "我"
                    else people.firstOrNull{it.id==post.authorId}?.name ?: "已删除的 TA"
                GlassSurface(modifier=Modifier.fillMaxWidth(),shape=GlassShape.Rounded(22.dp),
                    contentPadding=PaddingValues(horizontal=17.dp,vertical=17.dp)) {
                    Column(verticalArrangement=Arrangement.spacedBy(13.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(authorName,color=p.content,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                                Text(momentTime(post.createdAt)+" · "+MomentAccess.label(post.visibility),color=p.contentSecondary,fontSize=12.sp)
                            }
                            // Owner can manage every post, including those published by AI.
                            run {
                                Box {
                                    var showActions by remember(post.id){mutableStateOf(false)}
                                    IconButton(onClick={showActions=true},enabled=!busy) {
                                        Icon(Icons.Rounded.MoreHoriz,contentDescription="动态管理",tint=p.contentSecondary)
                                    }
                                    DropdownMenu(expanded=showActions,onDismissRequest={showActions=false}) {
                                        DropdownMenuItem(text={Text("修改可见范围")},onClick={
                                            showActions=false
                                            editingAudience=post
                                        })
                                        DropdownMenuItem(text={Text("删除动态")},onClick={
                                            showActions=false
                                            deleting=post
                                        })
                                    }
                                }
                            }
                        }
                        if(post.text.isNotBlank()) Text(post.text,color=p.content,fontSize=15.sp,lineHeight=23.sp)
                        if(post.photos.isNotEmpty()) {
                            val rows=post.photos.chunked(3)
                            rows.forEach { row ->
                                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    row.forEach {file ->
                                        AsyncImage(
                                            model=c.images.file(file),contentDescription="查看朋友圈图片",
                                            contentScale=ContentScale.Crop,
                                            modifier=Modifier.weight(1f).aspectRatio(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable{onOpenImage(file)}
                                        )
                                    }
                                    repeat(3-row.size) {Spacer(Modifier.weight(1f))}
                                }
                            }
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                            TextButton(onClick={execute {c.moments.toggleLike(post.id)}},enabled=!busy) {
                                Icon(if(post.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription=null,modifier=Modifier.size(19.dp),tint=p.accentContent)
                                Spacer(Modifier.width(4.dp))
                                Text(if(post.liked) "已赞" else "点赞",color=p.content)
                            }
                            TextButton(onClick={commenting=post;commentDraft=""}) {
                                Icon(Icons.Rounded.ChatBubbleOutline,contentDescription=null,
                                    modifier=Modifier.size(19.dp),tint=p.accentContent)
                                Spacer(Modifier.width(4.dp))
                                Text("评论",color=p.content)
                            }
                            TextButton(onClick={inviting=post}) {
                                Icon(Icons.Rounded.PersonAdd,contentDescription=null,
                                    modifier=Modifier.size(19.dp),tint=p.accentContent)
                                Spacer(Modifier.width(4.dp))
                                Text("邀请 TA",color=p.content)
                            }
                        }
                        if(post.aiLikes.isNotEmpty()) {
                            val who=post.aiLikes.mapNotNull{id->
                                people.firstOrNull{it.id==id}?.name
                            }.joinToString("、")
                            if(who.isNotBlank()) Text("♥ "+who+" 赞了这条动态",
                                color=p.contentSecondary,fontSize=12.sp)
                        }
                        if(post.comments.isNotEmpty()) {
                            HorizontalDivider()
                            post.comments.forEach { comment ->
                                val who=if(comment.authorId==0L) s?.userName?.ifBlank{"我"} ?: "我"
                                    else people.firstOrNull {it.id==comment.authorId}?.name ?: "原 AI 角色"
                                Text(who+"： "+comment.text,color=p.content,fontSize=14.sp,lineHeight=21.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    if(choosingAudience) MomentAudienceDialog(
        original=visibility,originalIds=visibleIds,people=people,
        onSave={mode,ids->visibility=mode;visibleIds=ids;choosingAudience=false},
        onDismiss={choosingAudience=false})
    editingAudience?.let { target ->
        MomentAudienceDialog(
            original=target.visibility,originalIds=target.audienceIds.toSet(),people=people,
            onSave={mode,ids->
                execute {c.moments.updateVisibility(target.id,mode,ids.toList())}
                editingAudience=null
            },
            onDismiss={editingAudience=null}
        )
    }
    if(posting) AlertDialog(
        onDismissRequest={if(!busy) posting=false},
        title={Text("发布朋友圈")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                TextButton(enabled=!busy,onClick={choosingAudience=true}) {
                    Text("谁可以看："+MomentAccess.label(visibility))
                }
                OutlinedTextField(value=draft,onValueChange={if(it.length<=MomentsRules.MAX_TEXT)draft=it},
                    label={Text("分享这一刻……")},minLines=3,maxLines=7,
                    modifier=Modifier.fillMaxWidth())
                TextButton(enabled=!busy,onClick={
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Rounded.Image,contentDescription=null)
                    Spacer(Modifier.width(6.dp))
                    Text("添加照片（"+photos.size+"/9）")
                }
                if(photos.isNotEmpty()) Text("已选择 "+photos.size+" 张图片；发布后会复制到 App 私有存储",
                    fontSize=12.sp,color=p.contentSecondary)
            }
        },
        confirmButton={TextButton(enabled=!busy && (draft.isNotBlank() || photos.isNotEmpty()),
            onClick={execute {
                c.moments.publish(draft,photos,visibility,visibleIds.toList())
                draft="";photos=emptyList();visibility=MomentVisibility.PUBLIC
                visibleIds=emptySet();posting=false
            }}) {Text(if(busy)"发布中…" else "发布")}},
        dismissButton={TextButton(enabled=!busy,onClick={posting=false}) {Text("取消")}}
    )
    commenting?.let { post ->
        AlertDialog(onDismissRequest={if(!busy) commenting=null},title={Text("发表评论")},
            text={OutlinedTextField(value=commentDraft,onValueChange={
                if(it.length<=MomentsRules.MAX_COMMENT)commentDraft=it
            },label={Text("说点什么")},minLines=2,modifier=Modifier.fillMaxWidth())},
            confirmButton={TextButton(enabled=!busy && commentDraft.isNotBlank(),onClick={
                execute {
                    c.moments.reply(post.id,0L,commentDraft)
                    commentDraft="";commenting=null
                }
            }) {Text("发送")}},
            dismissButton={TextButton(enabled=!busy,onClick={commenting=null}) {Text("取消")}}
        )
    }
    inviting?.let { post ->
        AlertDialog(onDismissRequest={if(!busy) inviting=null},title={Text("邀请 TA 来看看")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("只在你点击角色后调用其真实模型。评论会保存在这条动态下，不会自动给所有 AI 发送。",
                        color=p.contentSecondary,fontSize=12.sp)
                    people.forEach {ta ->
                        TextButton(enabled=!busy,onClick={execute {
                            c.moments.inviteAi(post.id,ta,c.secrets,c.chatClient)
                            inviting=null
                        }}) {Text(ta.name.ifBlank{"TA "+ta.id})}
                    }
                    if(people.isEmpty()) Text("还没有创建 AI 联系人",color=p.contentSecondary)
                }
            },
            confirmButton={TextButton(enabled=!busy,onClick={inviting=null}){Text("关闭")}}
        )
    }
    deleting?.let {post ->
        AlertDialog(onDismissRequest={deleting=null},title={Text("删除这条朋友圈？")},
            text={Text("这条动态和它的评论、照片将被删除，无法恢复。")},
            confirmButton={TextButton(enabled=!busy,onClick={execute {
                c.moments.delete(post.id)
                deleting=null
            }}){Text("删除")}},
            dismissButton={TextButton(onClick={deleting=null}){Text("取消")}})
    }
    failure?.let { problem ->
        AlertDialog(onDismissRequest={failure=null},title={Text("操作未完成")},
            text={Text(problem)},confirmButton={TextButton(onClick={failure=null}){Text("知道了")}})
    }
}

private fun momentTime(ms:Long):String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
