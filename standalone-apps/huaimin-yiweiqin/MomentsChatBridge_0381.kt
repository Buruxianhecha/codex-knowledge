package com.cleo.cleos.ai

import com.cleo.cleos.data.MomentAccess
import com.cleo.cleos.data.MomentsStore
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Real, in-app Moments actions available to private and group conversations.
 * Uses the same MomentsStore as the Discover tab; never fabricates a feed result.
 * User-defined PUBLIC / PRIVATE / SELECTED / EXCLUDED visibility is still enforced.
 */
object MomentsChatBridge {
    @Volatile var store: MomentsStore? = null
    private val dateFormat=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private fun time(at:Long):String=Instant.ofEpochMilli(at)
        .atZone(ZoneId.systemDefault()).format(dateFormat)

    suspend fun execute(name:String,args:JsonObject,actorId:Long):ToolOutcome {
        val moments=store ?: return ToolOutcome("朋友圈尚未初始化，暂时不能操作。","朋友圈未初始化")
        if(actorId<=0L)return ToolOutcome("无法识别执行操作的 AI 角色。","缺少角色身份")
        return try {
            when(name) {
                "read_feed" -> {
                    val author=ToolArgs.text(args,"author")?.trim()?.lowercase().orEmpty().ifBlank{"user"}
                    val id=ToolArgs.text(args,"post_id")?.trim().orEmpty()
                    val limit=(ToolArgs.int(args["limit"])?:10).coerceIn(1,50)
                    val visible=moments.posts.value.posts.filter { MomentAccess.canSee(it,actorId) }
                    val chosen=visible.filter {
                        (id.isEmpty()||it.id==id) && when(author) {
                            "all" -> true
                            "self", "mine" -> it.authorId==actorId
                            "user", "owner" -> it.authorId==0L
                            else -> false
                        }
                    }.take(limit)
                    val results=chosen.map { post ->
                        val who=if(post.authorId==0L)"用户" else "角色#${post.authorId}"
                        val comments=post.comments.takeLast(12).joinToString(" | ") {
                            "${if(it.authorId==0L)"用户" else "角色#${it.authorId}"}：${it.text}"
                        }
                        "ID=${post.id}，作者=$who，时间=${time(post.createdAt)}，正文=${post.text}，"+
                            "图片=${post.photos.size}张（没有图片像素，不可猜测画面），"+
                            "我的点赞=${actorId in post.aiLikes}，AI 点赞数=${post.aiLikes.size}，"+
                            "评论总数=${post.comments.size}，最新评论=[$comments]"
                    }
                    val body=if(results.isEmpty())"没有找到此角色可查看的朋友圈动态。"
                        else results.joinToString("\n\n")
                    ToolOutcome("这是 Cleos 应用内真实的朋友圈数据，不是微信朋友圈。\n$body",
                        "查看了${results.size}条朋友圈")
                }
                "like_feed" -> {
                    val id=ToolArgs.text(args,"post_id")?.trim().orEmpty()
                    val post=moments.posts.value.posts.firstOrNull{it.id==id &&
                        MomentAccess.canSee(it,actorId)}
                        ?:return ToolOutcome("没有找到可以查看的动态，请先调用 read_feed 获取真实 ID。","动态不存在或不可见")
                    if(post.authorId==actorId)
                        return ToolOutcome("不能给自己发布的动态点赞。","不能给自己点赞")
                    if(actorId in post.aiLikes)
                        return ToolOutcome("这条动态之前已经点过赞，不需要重复写入。","此前已赞")
                    moments.aiLike(id,actorId)
                    val confirmed=moments.posts.value.posts.firstOrNull{it.id==id}
                    if(confirmed==null || actorId !in confirmed.aiLikes)
                        ToolOutcome("点赞未能写入（动态可能被删除或可见范围已更改）。","点赞未成功")
                    else ToolOutcome("点赞成功，已写入这条朋友圈。","赞了朋友圈")
                }
                "comment_feed" -> {
                    val id=ToolArgs.text(args,"post_id")?.trim().orEmpty()
                    val comment=ToolArgs.text(args,"text")?.trim().orEmpty()
                    val post=moments.posts.value.posts.firstOrNull{it.id==id &&
                        MomentAccess.canSee(it,actorId)}
                        ?:return ToolOutcome("没有找到可以评论的动态，请先调用 read_feed。","动态不存在或不可见")
                    if(post.authorId==actorId)
                        return ToolOutcome("不要给自己发表的朋友圈刷评论。","不能给自己评论")
                    moments.reply(id,actorId,comment)
                    ToolOutcome("评论已成功保存到对应朋友圈：$comment","评论了朋友圈")
                }
                "publish_feed" -> {
                    val body=ToolArgs.text(args,"text")?.trim().orEmpty()
                    if(body.isBlank()) return ToolOutcome("请给出要发布的真实正文。","动态内容为空")
                    moments.publishAi(actorId,body)
                    ToolOutcome("这条动态已真实发布到应用内朋友圈：$body","发布了朋友圈")
                }
                else -> ToolOutcome("不存在这个朋友圈工具。","工具不存在")
            }
        } catch(e:Exception) {
            ToolOutcome("朋友圈操作失败：${e.message ?: "未知错误"}，不能假装已完成。","朋友圈操作失败")
        }
    }
}
