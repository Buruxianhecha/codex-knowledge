package com.cleo.cleos.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    Column {
        Box(Modifier.fillMaxWidth().height(265.dp).clip(RoundedCornerShape(20.dp))
            .clickable(onClick=onOpenMyTimeline)
            .background(Color(0xFF25374A))) {
            val cover=data.profile.cover ?: settings?.wallpaper
            if(cover!=null) AsyncImage(model=c.images.file(cover),contentDescription="我的朋友圈封面",
                modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            else Text("那些值得记住的时刻",color=Color.White,modifier=Modifier.align(Alignment.Center),
                fontSize=19.sp)
            TextButton(
                onClick={coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},
                modifier=Modifier.align(Alignment.TopEnd).padding(8.dp),enabled=!working
            ) {Icon(Icons.Rounded.AddPhotoAlternate,null,tint=Color.White);Text("换封面",color=Color.White)}
            Text(data.profile.name.ifBlank {settings?.userName?.ifBlank{"我"} ?: "我"},
                modifier=Modifier.align(Alignment.BottomEnd).padding(end=87.dp,bottom=18.dp),
                color=Color.White,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),
            horizontalArrangement=Arrangement.spacedBy(12.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(70.dp).clip(RoundedCornerShape(15.dp))
                .background(Color(0xFF34475A)).clickable {
                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                val avatar=data.profile.avatar ?: settings?.userAvatar
                if(avatar!=null) AsyncImage(model=c.images.file(avatar),contentDescription="个人头像",
                    modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                else Text("我",color=Color.White,modifier=Modifier.align(Alignment.Center),fontSize=26.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(data.profile.name.ifBlank{settings?.userName?.ifBlank{"我"} ?: "我"},
                    fontSize=18.sp,color=palette.content,fontWeight=FontWeight.SemiBold)
                Text(data.profile.bio.ifBlank{"点击编辑你的个人简介"},
                    fontSize=13.sp,color=palette.contentSecondary,maxLines=3)
            }
            IconButton(onClick={
                name=data.profile.name.ifBlank{settings?.userName.orEmpty()}
                bio=data.profile.bio
                editor=true
            }) {Icon(Icons.Rounded.Edit,contentDescription="编辑个人主页",tint=palette.content)}
            IconButton(onClick={aiControls=true}) {
                Icon(Icons.Rounded.Settings,contentDescription="AI 朋友圈权限",tint=palette.content)
            }
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
                Text("按角色单独授权：主动逛朋友圈时可按人格决定跳过、点赞、评论或同时点赞评论。每 6 小时最多尝试互动一条；主动发帖每角色每天最多一次。23:00–08:00 暂停执行。所有开关默认关闭。",
                    color=palette.contentSecondary,fontSize=12.sp)
                people.forEach { ta ->
                    val item=data.ai.firstOrNull{it.companionId==ta.id}
                    Column {
                        Text(ta.name,color=palette.content,fontWeight=FontWeight.Medium)
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text("主动逛朋友圈",modifier=Modifier.weight(1f),color=palette.content)
                            Switch(checked=item?.browsing==true,onCheckedChange={on->
                                scope.launch{c.moments.setAiSettings(ta.id,on,item?.posting==true)}
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
                            Switch(checked=item?.posting==true,onCheckedChange={on->
                                scope.launch{c.moments.setAiSettings(ta.id,item?.browsing==true,on)}
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
