#!/usr/bin/env python3
"""Inject safe 0.37.37beta multi-key failover without altering Room, tools or prompts."""
from pathlib import Path
from shutil import copyfile
import sys

root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
def patch(path, old, new, label):
    p=root/path
    text=p.read_text(encoding="utf-8")
    matches=text.count(old)
    if matches!=1:
        raise RuntimeError(f"{label}: expected 1 anchor, got {matches}: {old[:100]!r}")
    p.write_text(text.replace(old,new,1),encoding="utf-8")

client="app/src/main/java/com/cleo/cleos/ai/ChatClient.kt"
app="app/src/main/java/com/cleo/cleos/CleosApp.kt"
chat="app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"

patch(client, "import kotlinx.coroutines.Dispatchers\n",
    "import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.flow.flow\nimport kotlinx.coroutines.flow.emitAll\nimport kotlinx.coroutines.flow.collect\n",
    "flow imports")
p=root/client
t=p.read_text(encoding="utf-8")
start=t.index("class ChatException(")
end=t.index(") : Exception(message)",start)
t=t[:end]+"    val errorBody: String? = null,\n"+t[end:]
p.write_text(t,encoding="utf-8")
t=(root/client).read_text(encoding="utf-8")
start=t.index("class ChatClient(")
end=t.index(") {",start)
if "keyPool:" in t[start:end]: raise RuntimeError("Duplicate key pool declaration")
prefix=t[:end].rstrip()
if not prefix.endswith(","): prefix += ","
t=prefix+"\n    private val keyPool: ApiKeyPool? = null,\n"+t[end:]
(root/client).write_text(t,encoding="utf-8")
patch(client,
'''    fun stream(
        endpoint: ApiEndpoint,
        messages: List<ApiMessage>,
        tools: List<ToolSpec> = emptyList(),
        thinking: Boolean = false,
    ): Flow<ChatEvent> = callbackFlow {''',
'''    /** Per-request attempt tracking. Never replays partial streamed content or tool calls.
     * An ordered suspend callback writes the app's in-chat system notice and returns
     * BEFORE the next network request is initiated. No notice goes into the model.
     */
    fun stream(
        endpoint: ApiEndpoint,
        messages: List<ApiMessage>,
        tools: List<ToolSpec> = emptyList(),
        thinking: Boolean = false,
        notice: (suspend (String) -> Unit)? = null,
    ): Flow<ChatEvent> = flow {
        val pool=keyPool
        if (pool==null || notice==null || !pool.enabled()) {
            emitAll(streamOne(endpoint,messages,tools,thinking))
            return@flow
        }
        val keys=pool.candidates(endpoint.baseUrl,endpoint.model,endpoint.apiKey)
        if (keys.isEmpty()) {
            val allGone=pool.allConfirmedExhausted(endpoint.baseUrl,endpoint.model)
            val warning=if (allGone)
                "❌ 所有兼容 API 密钥的额度均已确认耗尽，请充值或添加密钥后重试。"
            else "⚠️ 当前没有可用于此模型的 API 密钥，请到角色模型设置恢复或添加密钥。"
            notice(warning)
            throw ChatException(warning)
        }
        val tried=HashSet<String>()
        for ((index,candidate) in keys.take(13).withIndex()) {
            if (!tried.add(candidate.id)) continue
            if (index==0 && candidate.value != endpoint.apiKey)
                notice("⚠️ 当前密钥已不可用，准备使用备用密钥「${candidate.alias}」……")
            var output=false
            try {
                streamOne(endpoint.copy(apiKey=candidate.value),messages,tools,thinking).collect { part ->
                    if(part is ChatEvent.Delta || part is ChatEvent.Reasoning ||
                        part is ChatEvent.ToolCalls) output=true
                    emit(part)
                }
                pool.success(endpoint.baseUrl,candidate.id)
                if(candidate.value != endpoint.apiKey)
                    notice("✅ 已切换至备用密钥「${candidate.alias}」，正在继续回复。")
                return@flow
            } catch(e:kotlinx.coroutines.CancellationException) {
                throw e
            } catch(e:ChatException) {
                val failure=KeyFailureClassifier.classify(e.status,e.errorBody)
                if(failure.kind in setOf(KeyFailureKind.QUOTA_EXHAUSTED,
                    KeyFailureKind.INVALID_CREDENTIAL,KeyFailureKind.RATE_LIMITED))
                    pool.record(endpoint.baseUrl,candidate.id,failure.kind)
                val switchable=failure.kind==KeyFailureKind.QUOTA_EXHAUSTED ||
                    failure.kind==KeyFailureKind.INVALID_CREDENTIAL
                if(!switchable || output) throw e
                if(keys.drop(index+1).none { it.id !in tried }) {
                    notice(if(pool.allConfirmedExhausted(endpoint.baseUrl,endpoint.model))
                        "❌ 所有兼容 API 密钥的额度均已耗尽，请充值或添加新密钥后重试。"
                        else "⚠️ 当前没有可用于此模型的备用密钥，请检查或恢复密钥配置。")
                    throw e
                }
                // Room system note is committed before the next API request.
                notice(if(failure.kind==KeyFailureKind.INVALID_CREDENTIAL)
                    "⚠️ 当前 API 密钥已失效，正在检查其他已配置密钥……"
                    else if(index==0)
                    "⚠️ 当前 API 密钥额度已耗尽，正在切换至备用密钥……"
                    else "⚠️ 备用密钥额度也已耗尽，正在尝试下一个……")
            }
        }
    }

    private fun streamOne(
        endpoint: ApiEndpoint,
        messages: List<ApiMessage>,
        tools: List<ToolSpec> = emptyList(),
        thinking: Boolean = false,
    ): Flow<ChatEvent> = callbackFlow {''',
"safe streaming failover")
t=(root/client).read_text(encoding="utf-8")
start=t.index("private fun httpFailure(")
end=t.index("\n    )",start)
if "errorBody = body" in t[start:end]: raise RuntimeError("Duplicate HTTP response body")
t=t[:end]+"\n        errorBody = body,"+t[end:]
(root/client).write_text(t,encoding="utf-8")
# Some OpenAI-compatible relays respond with HTTP 200 and an SSE error object.
# Classification still needs the structured error body, not the human-readable text.
patch("app/src/main/java/com/cleo/cleos/ai/StreamParser.kt",
    '''        obj["error"]?.let { throw ChatException("服务端报错：" + errorText(it)) }''',
    '''        obj["error"]?.let {
            throw ChatException("服务端报错：" + errorText(it), errorBody = data)
        }''',
    "SSE structured provider error")

patch(app,
'''    val companions = Companions(db, settings, secrets, images)
    val chatClient = ChatClient(http)''',
'''    val companions = Companions(db, settings, secrets, images)
    val apiKeyPool = com.cleo.cleos.ai.ApiKeyPool(secrets)
    val chatClient = ChatClient(http, keyPool=apiKeyPool)''',
"app-wide key pool")
patch(chat,
'''            client.stream(endpoint, messages, specs, thinking).collect { event ->''',
'''            client.stream(endpoint, messages, specs, thinking,
                notice={ state -> note(conversationId,state) }).collect { event ->''',
"normal group and single reply notices")
patch(chat,
'''            client.stream(endpoint, messages, specs).collect { event ->''',
'''            client.stream(endpoint, messages, specs,
                notice={ state -> note(conversationId,state) }).collect { event ->''',
"call-mode notices")

for src,dest in [
("ApiKeyFailure.kt","app/src/main/java/com/cleo/cleos/ai/ApiKeyFailure.kt"),
("ApiKeyPool.kt","app/src/main/java/com/cleo/cleos/ai/ApiKeyPool.kt"),
("ApiKeyFailureTest.kt","app/src/test/java/com/cleo/cleos/ai/ApiKeyFailureTest.kt")]:
    path=root/dest
    path.parent.mkdir(parents=True,exist_ok=True)
    copyfile(here/src,path)
print("0.37.37beta encrypted multi-key streaming failover integrated")
