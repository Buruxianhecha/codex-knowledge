package com.cleo.cleos.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.cleo.cleos.glass.*
import com.cleo.cleos.ui.common.appContainer
import kotlinx.coroutines.launch

/**
 * WeChat-style personal cover, avatar, name and bio. The cover is part of the actual
 * Moments timeline; tapping the profile can edit it without touching the chat identity.
 */
@Composable
fun MomentsMyProfile(onOpenMyTimeline:()->Unit={}) {
    val c=appContainer()
    val data by c.moments.posts.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle(null)
    val people by remember { c.companions.all }.collectAsStateWithLifecycle(emptyList())
    val palette=LocalGlassPalette.current
    val scope=rememberCoroutineScope()
    var editor by remember { mutableStateOf(false) }
    var aiControls by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val coverPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri:Uri? ->
        if(uri!=null) scope.launch {
            working=true
            try {c.moments.editProfilePhoto(uri,true)}
            catch(e:Exception) {error=e.message ?: "封面保存失败"}
            finally {working=false}
        }
    }
    val avatarPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri:Uri? ->
        if(uri!=null) scope.launch {
            working=true
            try {c.moments.editProfilePhoto(uri,false)}
            catch(e:Exception) {error=e.message ?: "头像保存失败"}
            finally {working=false}
        }
    }
    // Centered cover/profile header: round avatar overlaps the lower edge of cover.
    // Keep cover editor, avatar picker, AI settings, and personal timeline reachable.
    val displayName=data.profile.name.ifBlank {settings?.userName?.ifBlank{"我"} ?: "我"}
    fun editProfile() {
        name=data.profile.name.ifBlank{settings?.userName.orEmpty()}
        bio=data.profile.bio
        editor=true
    }
    Column(
        modifier=Modifier.fillMaxWidth(),
        horizontalAlignment=Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().height(255.dp)) {
            Box(
                Modifier.fillMaxWidth().height(200.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF25374A))
                    .clickable(onClick=onOpenMyTimeline)
            ) {
                val cover=data.profile.cover ?: settings?.wallpaper
                if(cover!=null) AsyncImage(
                    model=c.images.file(cover),contentDescription="我的朋友圈封面",
                    modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop
                ) else Text(
                    "那些值得记住的时刻",color=Color.White,
                    modifier=Modifier.align(Alignment.Center),fontSize=19.sp
                )
            }
            // Small overlay controls replace the old large "换封面" label.
            Row(
                modifier=Modifier.align(Alignment.TopEnd).padding(7.dp),
                horizontalArrangement=Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick={
                        coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled=!working,
                    modifier=Modifier.background(Color.Black.copy(alpha=0.32f),CircleShape)
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate,contentDescription="更换朋友圈封面",tint=Color.White)
                }
                IconButton(
                    onClick={aiControls=true},
                    modifier=Modifier.background(Color.Black.copy(alpha=0.32f),CircleShape)
                ) {
                    Icon(Icons.Rounded.Settings,contentDescription="AI 朋友圈权限",tint=Color.White)
                }
            }
            // Let the avatar overlap without clipping the outer Box.
            Box(
                modifier=Modifier.align(Alignment.BottomCenter).size(110.dp)
                    .border(3.dp,Color.White,CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF34475A))
                    .clickable {
                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment=Alignment.Center
            ) {
                val avatar=data.profile.avatar ?: settings?.userAvatar
                if(avatar!=null) AsyncImage(
                    model=c.images.file(avatar),contentDescription="更换个人头像",
                    modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop
                ) else Text("我",color=Color.White,fontSize=29.sp)
            }
        }
        Text(
            displayName,
            modifier=Modifier.padding(top=8.dp),
            fontSize=27.sp,
            color=palette.content,
            fontWeight=FontWeight.Bold
        )
        Row(
            modifier=Modifier.fillMaxWidth()
                .clickable(onClick=::editProfile)
                .padding(start=12.dp,end=12.dp,top=3.dp,bottom=7.dp),
            horizontalArrangement=Arrangement.Center,
            verticalAlignment=Alignment.CenterVertically
        ) {
            Text(
                data.profile.bio.ifBlank{"点击编辑你的个人简介"},
                color=palette.contentSecondary,
                fontSize=14.sp,maxLines=3,
                lineHeight=20.sp,
                modifier=Modifier.weight(1f,fill=false)
            )
            Icon(
                Icons.Rounded.Edit,contentDescription="编辑个人主页",
                tint=palette.contentSecondary,
                modifier=Modifier.padding(start=7.dp).size(17.dp)
            )
        }
    }
    if(editor) AlertDialog(
        onDismissRequest={if(!working)editor=false},title={Text("编辑我的朋友圈主页")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value=name,onValueChange={name=it.take(32)},
                label={Text("昵称")},singleLine=true)
            OutlinedTextField(value=bio,onValueChange={bio=it.take(200)},
                label={Text("个人简介")},minLines=3,maxLines=5)
            Text("点击主页头像或封面即可更换图片。此资料只作用于朋友圈，不修改 AI 的人格和聊天昵称。",
                fontSize=12.sp,color=palette.contentSecondary)
        }},
        confirmButton={TextButton(enabled=!working,onClick={
            scope.launch {
                working=true
                try {c.moments.editProfile(name,bio);editor=false}
                catch(e:Exception) {error=e.message ?: "保存失败"}
                finally{working=false}
            }
        }) {Text("保存")}},
        dismissButton={TextButton(onClick={editor=false}){Text("取消")}}
    )
    if(aiControls) AlertDialog(
        onDismissRequest={aiControls=false},title={Text("AI 的朋友圈行为")},
        text={
            Column(modifier=Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("AI 默认可以自主浏览、点赞、评论和发布动态。没有夜间禁用、六小时冷却或每日发帖次数上限。后台由 Android 系统定期唤醒，频繁调用会消耗 API 额度；需要暂停某位 AI 时可手动关掉对应选项。",
                    color=palette.contentSecondary,fontSize=12.sp)
                people.forEach { ta ->
                    val item=data.ai.firstOrNull{it.companionId==ta.id}
                    Column {
                        Text(ta.name,color=palette.content,fontWeight=FontWeight.Medium)
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text("主动逛朋友圈",modifier=Modifier.weight(1f),color=palette.content)
                            Switch(checked=item?.browsing!=false,onCheckedChange={on->
                                scope.launch{c.moments.setAiSettings(ta.id,on,item?.posting!=false)}
                            })
                        }
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text("允许自动点赞",modifier=Modifier.weight(1f),color=palette.content)
                            Switch(checked=item?.allowLikes!=false,onCheckedChange={on->
                                scope.launch{c.moments.setAiInteractions(ta.id,on,item?.allowComments!=false)}
                            })
                        }
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text("允许自动评论",modifier=Modifier.weight(1f),color=palette.content)
                            Switch(checked=item?.allowComments!=false,onCheckedChange={on->
                                scope.launch{c.moments.setAiInteractions(ta.id,item?.allowLikes!=false,on)}
                            })
                        }
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text("主动发自己的动态",modifier=Modifier.weight(1f),color=palette.content)
                            Switch(checked=item?.posting!=false,onCheckedChange={on->
                                scope.launch{c.moments.setAiSettings(ta.id,item?.browsing!=false,on)}
                            })
                        }
                    }
                    HorizontalDivider()
                }
                if(people.isEmpty()) Text("请先在通讯录中创建 AI 角色。",
                    color=palette.contentSecondary)
                Text("需要网络和有效 API Key。此功能会消耗模型额度；系统省电策略可能延迟执行。",
                    color=palette.contentSecondary,fontSize=12.sp)
            }
        },
        confirmButton={TextButton(onClick={aiControls=false}){Text("完成")}}
    )
    if(error!=null) AlertDialog(onDismissRequest={error=null},
        title={Text("操作失败")},text={Text(error.orEmpty())},
        confirmButton={TextButton(onClick={error=null}){Text("知道了")}})
}
