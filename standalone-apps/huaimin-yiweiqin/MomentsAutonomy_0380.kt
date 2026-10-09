package com.cleo.cleos.ai

import android.content.Context
import androidx.work.*
import com.cleo.cleos.AppContainer
import com.cleo.cleos.CleosApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Unlimited by-app-policy AI Moments work. Android may defer periodic background work. */
class MomentsAutonomy(private val c:AppContainer) {
    suspend fun tick() {
        // Every real companion participates, including existing installs without an AI settings row.
        // Explicitly switched-off roles still remain off; no implicit daily/quiet-hour quota.
        for(ta in c.companions.all.first()) {
            val secret=c.secrets.key(ta.apiBaseUrl).orEmpty()
            if(secret.isBlank()||ta.apiModel.isBlank())continue
            try {
                val state=c.moments.posts.value.ai.firstOrNull{it.companionId==ta.id}
                    ?: com.cleo.cleos.data.MomentAiSettings(ta.id)
                if(state.browsing) {
                    browse(ta.id,ta.name,ta.persona,ta.apiBaseUrl,ta.apiModel,secret,state.lastSeenPostId,
                        state.allowLikes,state.allowComments)
                }
                val newer=c.moments.posts.value.ai.firstOrNull{it.companionId==ta.id}
                    ?: com.cleo.cleos.data.MomentAiSettings(ta.id)
                if(newer.posting) {
                    c.moments.markPostAttempt(ta.id)
                    val request="你是"+ta.name+"，性格："+ta.persona.take(2500)+
                        "。请自行决定现在是否有值得分享的话。没有想说的事，只输出 SKIP；"+
                        "否则以自己的口吻写一条自然的朋友圈，不必为了定时任务强行发帖。"+
                        "不要编造未发生的真实经历，也不要透露 API 或软件内部信息。只输出正文或者 SKIP。"
                    val result=ask(ta.apiBaseUrl,ta.apiModel,secret,request,"想发就发，不想发就跳过。").trim()
                    if(result.isNotBlank() && !result.equals("SKIP",ignoreCase=true))
                        c.moments.publishAi(ta.id,result)
                }
            } catch(e:CancellationException) { throw e }
              catch(e:Exception) {android.util.Log.w("MomentsAutonomy","Skipped request",e)}
        }
    }
    /** Periodic interaction without a six-hour gate; the AI decides LIKE, COMMENT, BOTH or SKIP.
     * Never feed an excluded/private post to the model. Revalidate on writes to handle
     * privacy edits racing a network response.
     */
    private suspend fun browse(id:Long,name:String,persona:String,url:String,model:String,
        key:String,lastId:String?,allowLikes:Boolean,allowComments:Boolean) {
        if(!allowLikes && !allowComments) return
        val saved=c.moments.posts.value.ai.firstOrNull{it.companionId==id}
        val seen=(saved?.seenPostIds.orEmpty()+listOfNotNull(lastId)).toSet()
        // Process all new, visible posts; no arbitrary one-per-worker or per-day limit.
        // SKIP is persisted as a review too, so periodic work does not re-bill old posts.
        val entries=c.moments.posts.value.posts.filter { post ->
            post.authorId!=id && post.id !in seen &&
            com.cleo.cleos.data.MomentAccess.canSee(post,id) &&
            (post.text.isNotBlank() || post.photos.isNotEmpty())
        }
        for (entry in entries) {
        val choices=buildList {
            add("SKIP")
            if(allowLikes && id !in entry.aiLikes) add("LIKE")
            if(allowComments && entry.comments.none{it.authorId==id}) add("COMMENT:评论文字")
            if(allowLikes && allowComments && id !in entry.aiLikes &&
                entry.comments.none{it.authorId==id}) add("BOTH:评论文字")
        }
        val system="你是"+name+"，你的人格设定："+persona.take(2500)+
            "。你正在自主浏览朋友的动态，请根据你的性格、你与此人的关系和动态内容，自然决定是否点赞或留言。"+
            "只能返回一种格式："+choices.joinToString(" / ")+"。不需要互动就回答 SKIP。"+
            "评论应口语化、真实，不能复述机器人说明，不得编造照片中的细节，最多120字。"+
            "除这条允许查看的动态外你不具备查看其他朋友圈内容的权限。"
        val imageNote=if(entry.photos.isEmpty()) "" else
            "\n附有"+entry.photos.size+"张照片，但你没有收到图片内容，不得推测画面。"
        val raw=ask(url,model,key,system,"朋友发表的文字：\n"+entry.text.take(1000)+imageNote)
            .trim().take(240)
        if(raw.isBlank()) continue // Retry if the provider gave no usable result.
        c.moments.markAiBrowse(id,entry.id) // Mark visited only after an actual model answer.
        // Exact parser, not substring guessing. Invalid output is treated as skip.
        val upper=raw.uppercase(java.util.Locale.ROOT)
        val hasLiked=id in entry.aiLikes
        val hasCommented=entry.comments.any{it.authorId==id}
        val like=upper=="LIKE" || upper.startsWith("BOTH:")
        val comment=when {
            upper.startsWith("COMMENT:") -> raw.substringAfter(":").trim().take(120)
            upper.startsWith("BOTH:") -> raw.substringAfter(":").trim().take(120)
            else -> ""
        }
        if(like && allowLikes && !hasLiked) c.moments.aiLike(entry.id,id)
        if(comment.isNotBlank() && allowComments && !hasCommented)
            c.moments.reply(entry.id,id,comment)
        }
    }
    private suspend fun ask(url:String,model:String,key:String,system:String,prompt:String):String {
        val response=StringBuilder()
        c.chatClient.stream(ApiEndpoint(url,key,model),
            listOf(ApiMessage("system",system),ApiMessage("user",prompt)),
            tools=emptyList(),thinking=false,notice={}
        ).collect {event->
            if(event is ChatEvent.Delta) {
                if(response.length>2000)throw IllegalStateException("Generated too much")
                response.append(event.text)
            }
        }
        return response.toString()
    }
    companion object {
        fun schedule(context:Context) {
            val task=PeriodicWorkRequestBuilder<MomentsAutonomyWorker>(15,TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("huaimin-moments",
                ExistingPeriodicWorkPolicy.UPDATE,task)
        }
    }
}

class MomentsAutonomyWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=applicationContext as? CleosApp ?: return Result.success()
        return try {app.container.momentsAutonomy.tick();Result.success()}
        catch(e:CancellationException){throw e}
        catch(e:Exception){android.util.Log.w("MomentsAutonomy","Worker skipped",e);Result.success()}
    }
}
