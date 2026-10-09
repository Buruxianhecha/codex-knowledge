package com.cleo.cleos.data

import android.content.Context
import android.net.Uri
import com.cleo.cleos.ai.ApiEndpoint
import com.cleo.cleos.ai.ApiMessage
import com.cleo.cleos.ai.ChatClient
import com.cleo.cleos.ai.ChatEvent
import com.cleo.cleos.data.db.CompanionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class MomentReply(
    val id: String = UUID.randomUUID().toString(),
    val authorId: Long = 0,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Per-post permissions: each AI is a separate local viewer. The user always sees all. */
@Serializable
enum class MomentVisibility { PUBLIC, PRIVATE, SELECTED, EXCLUDED }
object MomentAccess {
    fun canSee(post:MomentPost,viewer:Long):Boolean {
        if(viewer==0L || viewer==post.authorId) return true
        return when(post.visibility) {
            MomentVisibility.PUBLIC -> true
            MomentVisibility.PRIVATE -> false
            MomentVisibility.SELECTED -> viewer in post.audienceIds
            MomentVisibility.EXCLUDED -> viewer !in post.audienceIds
        }
    }
    fun label(v:MomentVisibility):String=when(v) {
        MomentVisibility.PUBLIC -> "公开 · 所有 AI 可见"
        MomentVisibility.PRIVATE -> "私密 · 仅自己可见"
        MomentVisibility.SELECTED -> "部分可见"
        MomentVisibility.EXCLUDED -> "不给谁看"
    }
}
@Serializable
data class MomentPost(
    val id: String = UUID.randomUUID().toString(),
    val authorId: Long = 0,
    val text: String,
    val photos: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val liked: Boolean = false,
    val comments: List<MomentReply> = emptyList(),
    val aiLikes: List<Long> = emptyList(),
    val visibility: MomentVisibility = MomentVisibility.PUBLIC,
    val audienceIds: List<Long> = emptyList(),
)

@Serializable
data class MomentProfile(val name:String="",val bio:String="",val cover:String?=null,val avatar:String?=null)
@Serializable
data class MomentAiSettings(
    val companionId:Long,
    val browsing:Boolean=false,
    val posting:Boolean=false,
    val lastBrowseAt:Long=0L,
    val lastPostAt:Long=0L,
    val lastSeenPostId:String?=null,
    val lastPostAttemptAt:Long=0L
)
@Serializable
data class SavedMessage(
    val id:String=UUID.randomUUID().toString(),
    val sourceId:Long,
    val sourceConversationId:Long,
    val author:String,
    val text:String="",
    val audioFile:String?=null,
    val createdAt:Long=System.currentTimeMillis()
)
@Serializable
data class MomentsSnapshot(
    val version:Int=1,
    val posts:List<MomentPost> = emptyList(),
    val profile:MomentProfile=MomentProfile(),
    val ai:List<MomentAiSettings> = emptyList(),
    val savedMessages:List<SavedMessage> = emptyList()
)

object MomentsRules {
    const val MAX_TEXT = 2000
    const val MAX_IMAGES = 9
    const val MAX_COMMENT = 500
    fun validatePost(text: String, photos: Int) {
        require(text.length <= MAX_TEXT) { "正文最多 2000 字" }
        require(photos <= MAX_IMAGES) { "每条动态最多 9 张图片" }
        require(text.isNotBlank() || photos > 0) { "写点什么，或者添加照片" }
    }
    fun validateReply(text: String) {
        require(text.isNotBlank() && text.length <= MAX_COMMENT) { "评论需要 1–500 字" }
    }
    fun validImageName(name: String): Boolean =
        name.isNotBlank() && name.length < 160 &&
            !name.contains('/') && !name.contains('\\') && !name.startsWith(".") &&
            (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"))
}

private val safeMediaExtensions=setOf("mp3","m4a","wav","ogg","aac","opus","amr","3gp")
private fun String.safeMediaName():Boolean =
    length in 1..159 && !contains('/') && !contains('\\') && !startsWith(".") &&
    substringAfterLast('.',"").lowercase() in safeMediaExtensions

class MomentsStore(context: Context, private val images: ImageStore) {
    private val target = File(context.filesDir, "moments-v1.json")
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys=true; encodeDefaults=true }
    private var broken: Throwable? = null
    private fun initial(): MomentsSnapshot {
        if (!target.exists()) return MomentsSnapshot()
        return try {
            json.decodeFromString<MomentsSnapshot>(target.readText()).also(::validate)
        } catch (e: Exception) {
            broken = e
            MomentsSnapshot()
        }
    }
    private val current = MutableStateFlow(initial())
    val posts: StateFlow<MomentsSnapshot> = current
    private fun validate(data: MomentsSnapshot) {
        require(data.version == 1) { "未知朋友圈文件版本" }
        require(data.posts.size <= 50_000) { "动态数量异常" }
        require(data.posts.map { it.id }.distinct().size == data.posts.size) { "动态 ID 重复" }
        data.posts.forEach {
            MomentsRules.validatePost(it.text,it.photos.size)
            require(it.photos.all(MomentsRules::validImageName)) { "动态照片文件名无效" }
            require(it.comments.size <= 10000) { "评论数量异常" }
            require(it.audienceIds.size<=200 && it.audienceIds.all{id->id>0L})
            require(it.audienceIds.distinct().size==it.audienceIds.size)
        }
        require(data.profile.name.length<=32 && data.profile.bio.length<=200)
        require(listOfNotNull(data.profile.cover,data.profile.avatar).all(MomentsRules::validImageName))
        require(data.ai.size<=200 && data.ai.map{it.companionId}.distinct().size==data.ai.size)
        require(data.savedMessages.size<=20000)
        data.savedMessages.forEach {
            require(it.text.length<=50000 && it.author.length<=100)
            require(it.audioFile==null || it.audioFile.safeMediaName())
        }
    }
    private fun save(data: MomentsSnapshot) {
        broken?.let { throw IllegalStateException("本机朋友圈数据读取失败，停止写入以保护旧数据",it) }
        validate(data)
        val tmp = File(target.path+".tmp")
        try {
            tmp.outputStream().buffered().use { stream ->
                stream.write(json.encodeToString(data).toByteArray(Charsets.UTF_8))
            }
            if (!tmp.renameTo(target)) throw IllegalStateException("朋友圈数据写入失败")
            current.value = data
        } finally { tmp.delete() }
    }
    suspend fun publish(raw:String,pending:List<Uri>,
                        visibility:MomentVisibility=MomentVisibility.PUBLIC,
                        audienceIds:List<Long> = emptyList()) = withContext(Dispatchers.IO) {
        require(audienceIds.size<=200 && audienceIds.all{it>0L} &&
            audienceIds.distinct().size==audienceIds.size)
        require(visibility !in setOf(MomentVisibility.SELECTED,MomentVisibility.EXCLUDED) ||
            audienceIds.isNotEmpty()) { "请选择至少一位 AI 朋友" }
        val text=raw.trim()
        MomentsRules.validatePost(text,pending.size)
        val copied=mutableListOf<String>()
        try {
            pending.forEach { uri ->
                copied.add(images.import(uri,maxEdge=1920,prefix="moments-").file)
            }
            lock.withLock {
                val data=current.value
                save(data.copy(posts=listOf(MomentPost(text=text,photos=copied.toList(),visibility=visibility,audienceIds=audienceIds))+data.posts))
            }
        } catch(e: Exception) {
            images.delete(copied)
            throw e
        }
    }
    suspend fun updateVisibility(id:String,mode:MomentVisibility,ids:List<Long>)=lock.withLock {
        require(ids.size<=200 && ids.all{it>0L} && ids.distinct().size==ids.size)
        require(mode !in setOf(MomentVisibility.SELECTED,MomentVisibility.EXCLUDED) ||
            ids.isNotEmpty()) { "至少选择一个 AI 联系人" }
        val existing=current.value.posts.firstOrNull{it.id==id && it.authorId==0L}
            ?: throw IllegalArgumentException("只能调整自己发表的朋友圈")
        save(current.value.copy(posts=current.value.posts.map {
            if(it.id==existing.id) it.copy(visibility=mode,audienceIds=ids) else it
        }))
    }
    suspend fun toggleLike(id:String) = lock.withLock {
        save(current.value.copy(posts=current.value.posts.map {
            if(it.id == id) it.copy(liked=!it.liked) else it
        }))
    }
    suspend fun reply(id:String, authorId:Long, raw:String) = lock.withLock {
        val text=raw.trim()
        MomentsRules.validateReply(text)
        val data=current.value
        require(data.posts.any {it.id==id && MomentAccess.canSee(it,authorId)}) {
            "动态已删除或此角色无权查看"
        }
        save(data.copy(posts=data.posts.map {
            if(it.id==id) it.copy(comments=it.comments+MomentReply(authorId=authorId,text=text)) else it
        }))
    }
    suspend fun delete(id:String) = lock.withLock {
        val data=current.value
        val original=data.posts.firstOrNull {it.id==id && it.authorId==0L} ?: return@withLock
        save(data.copy(posts=data.posts.filterNot {it.id==id}))
        images.delete(original.photos)
    }
    suspend fun editProfile(name:String,bio:String)=lock.withLock {
        require(name.length<=32 && bio.length<=200) { "昵称最多32字，简介最多200字" }
        save(current.value.copy(profile=current.value.profile.copy(name=name.trim(),bio=bio.trim())))
    }
    suspend fun editProfilePhoto(uri:Uri,isCover:Boolean)=withContext(Dispatchers.IO) {
        val image=images.import(uri,maxEdge=2048,prefix="moments-profile-").file
        try {
            lock.withLock {
                val p=current.value.profile
                save(current.value.copy(profile=if(isCover) p.copy(cover=image) else p.copy(avatar=image)))
            }
        } catch(e:Exception) {images.delete(listOf(image)); throw e}
    }
    suspend fun setAiSettings(id:Long,browsing:Boolean,posting:Boolean)=lock.withLock {
        val old=current.value.ai.firstOrNull{it.companionId==id} ?: MomentAiSettings(id)
        save(current.value.copy(ai=current.value.ai.filterNot{it.companionId==id}
            +old.copy(browsing=browsing,posting=posting)))
    }
    suspend fun markPostAttempt(id:Long)=lock.withLock {
        val old=current.value.ai.firstOrNull{it.companionId==id} ?: MomentAiSettings(id)
        save(current.value.copy(ai=current.value.ai.filterNot{it.companionId==id}+
            old.copy(lastPostAttemptAt=System.currentTimeMillis())))
    }
    suspend fun markAiBrowse(id:Long,postId:String)=lock.withLock {
        val old=current.value.ai.firstOrNull{it.companionId==id} ?: MomentAiSettings(id)
        save(current.value.copy(ai=current.value.ai.filterNot{it.companionId==id}+
            old.copy(lastBrowseAt=System.currentTimeMillis(),lastSeenPostId=postId)))
    }
    suspend fun publishAi(id:Long,body:String)=lock.withLock {
        val text=body.trim().take(500)
        MomentsRules.validatePost(text,0)
        val now=System.currentTimeMillis()
        val old=current.value.ai.firstOrNull{it.companionId==id} ?: MomentAiSettings(id)
        save(current.value.copy(posts=listOf(MomentPost(authorId=id,text=text))+current.value.posts,
            ai=current.value.ai.filterNot{it.companionId==id}+old.copy(lastPostAt=now)))
    }
    suspend fun aiLike(postId:String,taId:Long)=lock.withLock {
        val source=current.value.posts.firstOrNull{it.id==postId} ?: return@withLock
        if(source.authorId==taId || taId in source.aiLikes ||
            !MomentAccess.canSee(source,taId)) return@withLock
        save(current.value.copy(posts=current.value.posts.map {
            if(it.id==postId) it.copy(aiLikes=it.aiLikes+taId) else it
        }))
    }

    suspend fun favorite(message:com.cleo.cleos.data.db.MessageEntity,author:String)=withContext(Dispatchers.IO) {
        if(lock.withLock { current.value.savedMessages.any{it.sourceId==message.id} }) return@withContext
        val audio=MessageAudios.decode(message.audio)?.file
        require(message.content.isNotBlank() || audio!=null) { "没有可以收藏的文字或语音" }
        val copied=audio?.let { name ->
            require(name.safeMediaName()) { "语音路径不安全" }
            val src=images.file(name)
            require(src.isFile) { "原语音文件不存在" }
            val filename="favorite-"+UUID.randomUUID().toString()+"."+name.substringAfterLast('.')
            src.copyTo(images.file(filename))
            filename
        }
        try {
            lock.withLock {
                if(current.value.savedMessages.any{it.sourceId==message.id}) {
                    if(copied!=null)images.delete(listOf(copied))
                    return@withLock
                }
                val entry=SavedMessage(sourceId=message.id,sourceConversationId=message.conversationId,
                    author=author.take(100),text=message.content.take(50000),audioFile=copied)
                save(current.value.copy(savedMessages=listOf(entry)+current.value.savedMessages))
            }
        } catch(e:Exception) {if(copied!=null)images.delete(listOf(copied));throw e}
    }
    suspend fun deleteFavorite(id:String)=lock.withLock {
        val entry=current.value.savedMessages.firstOrNull{it.id==id} ?: return@withLock
        save(current.value.copy(savedMessages=current.value.savedMessages.filterNot{it.id==id}))
        entry.audioFile?.let{images.delete(listOf(it))}
    }
    suspend fun reload()=withContext(Dispatchers.IO) {
        lock.withLock {
            broken=null
            current.value=initial()
        }
    }
    /** AI replies exist only after explicit user invitation; no fabricated or scripted comments. */
    suspend fun inviteAi(postId:String, ta:CompanionEntity, secrets:SecretStore, client:ChatClient) {
        val post=lock.withLock { current.value.posts.firstOrNull {it.id==postId && MomentAccess.canSee(it,ta.id)} }
            ?: throw IllegalArgumentException("这条动态已删除")
        val key=secrets.key(ta.apiBaseUrl)?.trim().orEmpty()
        require(key.isNotBlank() && ta.apiModel.isNotBlank()) { "请先为这位 AI 配置可用的模型和 API Key" }
        val author=if(post.authorId==0L) "用户" else "另一位 AI"
        val system="你是"+ta.name+"。角色设定："+ta.persona.take(4000)+"。你正在浏览朋友圈。请自然、简短、真诚地评论一条"+author+"发表的动态，只输出评论本身，不要包含引号、身份前缀或动作描写，最多 150 字。不要假装你亲眼见过照片中未描述的细节。"
        val imageContext=if(post.photos.isEmpty()) "" else "\n（这条动态附有 "+post.photos.size+" 张图片，当前只向模型发送文字内容，不要臆测图片画面。）"
        val result=StringBuilder()
        client.stream(
            endpoint=ApiEndpoint(ta.apiBaseUrl,key,ta.apiModel),
            messages=listOf(ApiMessage("system",system),ApiMessage("user","动态内容：\n"+post.text+imageContext)),
            tools=emptyList(),thinking=false,notice={}
        ).collect { event ->
            if(event is ChatEvent.Delta) {
                result.append(event.text)
                if(result.length>1500) throw IllegalArgumentException("AI 评论超过长度限制")
            }
        }
        val answer=result.toString().trim().trim('"','“','”').take(150)
        MomentsRules.validateReply(answer)
        reply(postId,ta.id,answer)
    }
}
