package com.cleo.cleos.ui

import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleo.cleos.glass.*
import com.cleo.cleos.ui.common.*
import kotlinx.coroutines.launch

/** Independent voice and text favorites survive deletion of their original messages. */
@Composable
fun FavoritesScreen(onBack:()->Unit) {
    val c=appContainer()
    val snapshot by c.moments.posts.collectAsStateWithLifecycle()
    val p=LocalGlassPalette.current
    val scope=rememberCoroutineScope()
    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var confirmDelete by remember {mutableStateOf<String?>(null)}
    var player by remember {mutableStateOf<MediaPlayer?>(null)}
    var currentId by remember {mutableStateOf<String?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    fun stop() {
        runCatching{player?.stop();player?.release()}
        player=null
        currentId=null
    }
    fun play(file:String,id:String) {
        if(currentId==id){stop();return}
        stop()
        try {
            val p=MediaPlayer().apply {
                setDataSource(c.images.file(file).absolutePath)
                setOnCompletionListener{ stop() }
                prepare()
                start()
            }
            player=p
            currentId=id
        } catch(e:Exception){stop();error=e.message ?: "语音播放失败"}
    }
    DisposableEffect(Unit) {onDispose {stop()} }
    BackHandler { stop();onBack() }
    GlassPage(overlay={page->
        GlassTopBar(title="收藏",subtitle=snapshot.savedMessages.size.toString()+" 条",
            backdrop=page,leading={
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack,"返回发现",
                    {stop();onBack()},page)
            })
    }) {
        LazyColumn(modifier=Modifier.fillMaxSize().fadeUnderTopBar(top+TopBarHeight),
            contentPadding=PaddingValues(start=14.dp,end=14.dp,
                top=top+TopBarHeight+10.dp,bottom=bottom+25.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(snapshot.savedMessages.isEmpty()) item {
                GlassSurface(modifier=Modifier.fillMaxWidth(),shape=GlassShape.Rounded(22.dp),
                    contentPadding=PaddingValues(24.dp)) {
                    Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Rounded.Bookmark,contentDescription=null,tint=p.accentContent)
                        Text("还没有收藏",fontSize=19.sp,color=p.content)
                        Text("去聊天页长按一条文字或语音消息，点击「收藏」，以后即使原聊天被清理也可以在这里查看。",
                            color=p.contentSecondary,fontSize=13.sp)
                    }
                }
            }
            items(snapshot.savedMessages,key={it.id}) { item ->
                GlassSurface(modifier=Modifier.fillMaxWidth(),shape=GlassShape.Rounded(20.dp),
                    contentPadding=PaddingValues(horizontal=17.dp,vertical=15.dp)) {
                    Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(item.author,color=p.content,fontSize=16.sp,modifier=Modifier.weight(1f))
                            IconButton(onClick={confirmDelete=item.id}) {
                                Icon(Icons.Rounded.DeleteOutline,"取消收藏",tint=p.contentSecondary)
                            }
                        }
                        if(item.text.isNotBlank())
                            Text(item.text,color=p.content,fontSize=15.sp,lineHeight=22.sp)
                        item.audioFile?.let { file ->
                            TextButton(onClick={play(file,item.id)}) {
                                Icon(if(currentId==item.id) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    null,tint=p.accentContent)
                                Text(if(currentId==item.id)"停止语音" else "播放收藏的语音")
                            }
                        }
                        Text(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.getDefault())
                            .format(java.util.Date(item.createdAt)),fontSize=11.sp,color=p.contentSecondary)
                    }
                }
            }
        }
    }
    confirmDelete?.let { id ->
        AlertDialog(onDismissRequest={confirmDelete=null},title={Text("取消收藏？")},
            text={Text("这条收藏的独立副本将删除，原聊天消息不受影响。")},
            confirmButton={TextButton(onClick={
                if(currentId==id)stop()
                scope.launch{
                    try{c.moments.deleteFavorite(id)}
                    catch(e:Exception){error=e.message ?: "删除收藏失败"}
                }
                confirmDelete=null
            }){Text("删除")}},
            dismissButton={TextButton(onClick={confirmDelete=null}){Text("取消")}})
    }
    error?.let {message ->
        AlertDialog(onDismissRequest={error=null},title={Text("操作未完成")},
            text={Text(message)},confirmButton={TextButton(onClick={error=null}){Text("知道了")}})
    }
}
