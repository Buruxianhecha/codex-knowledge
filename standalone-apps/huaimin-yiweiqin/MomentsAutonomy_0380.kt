package com.cleo.cleos.ai

import android.content.Context
import androidx.work.*
import com.cleo.cleos.AppContainer
import com.cleo.cleos.CleosApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Opt-in, six-hour background check. Never exceeds one AI post per day. */
class MomentsAutonomy(private val c:AppContainer) {
    suspend fun tick() {
        val hour=LocalTime.now().hour
        if(hour>=23 || hour<8)return
        for(p in c.moments.posts.value.ai.filter{it.browsing||it.posting}.take(24)) {
            val ta=c.companions.get(p.companionId) ?: continue
            val secret=c.secrets.key(ta.apiBaseUrl).orEmpty()
            if(secret.isBlank()||ta.apiModel.isBlank())continue
            try {
                val state=c.moments.posts.value.ai.firstOrNull{it.companionId==ta.id} ?:continue
                if(state.browsing && System.currentTimeMillis()-state.lastBrowseAt>TimeUnit.HOURS.toMillis(6)) {
                    browse(ta.id,ta.name,ta.persona,ta.apiBaseUrl,ta.apiModel,secret,state.lastSeenPostId)
                }
                val newer=c.moments.posts.value.ai.firstOrNull{it.companionId==ta.id} ?:continue
                if(newer.posting && !today(newer.lastPostAt) && !today(newer.lastPostAttemptAt)) {
                    c.moments.markPostAttempt(ta.id)
                    val request="你是"+ta.name+"，性格："+ta.persona.take(2500)+
                        "。请以自己的口吻发表一条自然短小的朋友圈动态，最多200字。"+
                        "不要声称真实经历不存在的事，也不要透露 API 或软件内部信息。只输出正文。"
                    val result=ask(ta.apiBaseUrl,ta.apiModel,secret,request,"写一条日常动态。").trim()
                    if(result.isNotBlank()) c.moments.publishAi(ta.id,result)
                }
            } catch(e:CancellationException) { throw e }
              catch(e:Exception) {android.util.Log.w("MomentsAutonomy","Skipped request",e)}
        }
    }
    private suspend fun browse(id:Long,name:String,persona:String,url:String,model:String,key:String,lastId:String?) {
        val posts=c.moments.posts.value.posts
        val entry=posts.firstOrNull{it.authorId!=id && it.id!=lastId &&
            it.comments.none{x->x.authorId==id} && it.text.isNotBlank()}
            ?: return
        c.moments.markAiBrowse(id,entry.id) // Reserve quota before any billable request.
        val instruction="你是"+name+"，性格："+persona.take(2500)+
            "。浏览朋友动态后选择不互动、点赞或自然评论。"+
            "只回答一行：SKIP 或 LIKE 或 COMMENT:评论正文。评论最多150字。"+
            "不要对自己发的内容评论，不要臆测图片细节。"
        val result=ask(url,model,key,instruction,"朋友发表：\n"+entry.text.take(1200)).trim()
        when {
            result.equals("LIKE",true) -> c.moments.aiLike(entry.id,id)
            result.startsWith("COMMENT:",true) -> {
                val reply=result.substringAfter(':').trim().take(150)
                if(reply.isNotBlank())c.moments.reply(entry.id,id,reply)
            }
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
    private fun today(at:Long):Boolean {
        if(at<=0)return false
        val zone=ZoneId.systemDefault()
        return LocalDate.ofInstant(Instant.ofEpochMilli(at),zone)==LocalDate.now(zone)
    }
    companion object {
        fun schedule(context:Context) {
            val task=PeriodicWorkRequestBuilder<MomentsAutonomyWorker>(6,TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("huaimin-moments",
                ExistingPeriodicWorkPolicy.KEEP,task)
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
