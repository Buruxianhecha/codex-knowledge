#!/usr/bin/env python3
"""Add real six-provider failover atop existing within-provider key rotation."""
from pathlib import Path
from shutil import copyfile
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
def patch(path,old,new,label):
    p=root/path
    code=p.read_text(encoding="utf-8")
    count=code.count(old)
    if count!=1: raise RuntimeError(f"{label}: expected one anchor got {count}")
    p.write_text(code.replace(old,new,1),encoding="utf-8")
client="app/src/main/java/com/cleo/cleos/ai/ChatClient.kt"
app="app/src/main/java/com/cleo/cleos/CleosApp.kt"
fields="app/src/main/java/com/cleo/cleos/ui/settings/EndpointFields.kt"
page="app/src/main/java/com/cleo/cleos/ui/settings/TaPages.kt"

patch(client,
'''    fun stream(
        endpoint: ApiEndpoint,
        messages: List<ApiMessage>,
        tools: List<ToolSpec> = emptyList(),
        thinking: Boolean = false,
        notice: (suspend (String) -> Unit)? = null,
    ): Flow<ChatEvent> = flow {''',
'''    /** Two-tier automatic routing: switch URL, key AND model for each service.
     * An already emitting stream or executed tool is never replayed.
     */
    fun stream(
        endpoint: ApiEndpoint,
        messages: List<ApiMessage>,
        tools: List<ToolSpec> = emptyList(),
        thinking: Boolean = false,
        notice: (suspend (String) -> Unit)? = null,
    ): Flow<ChatEvent> = flow {
        val router=providerPool
        val routes=if(router!=null && notice!=null) router.routes(endpoint) else emptyList()
        if(routes.size<2) {
            emitAll(streamWithinProvider(endpoint,messages,tools,thinking,notice))
            return@flow
        }
        for((index,candidate) in routes.withIndex()) {
            var outputStarted=false
            try {
                if(index==0 && !candidate.isPrimary)
                    notice!!("ℹ️ 当前自动使用「${candidate.name}」· ${candidate.endpoint.model}。")
                streamWithinProvider(candidate.endpoint,messages,tools,thinking,notice).collect { event ->
                    if(event is ChatEvent.Delta || event is ChatEvent.Reasoning ||
                        event is ChatEvent.ToolCalls) outputStarted=true
                    emit(event)
                }
                router!!.succeeded(endpoint,candidate)
                if(!candidate.isPrimary && index>0)
                    notice!!("✅ 已切换到「${candidate.name}」的 ${candidate.endpoint.model} 模型，继续使用此服务商。")
                return@flow
            } catch(e:kotlinx.coroutines.CancellationException) {
                throw e
            } catch(e:ChatException) {
                val failure=KeyFailureClassifier.classify(e.status,e.errorBody)
                if(!ProviderFailoverRules.mayRetry(failure.kind,outputStarted)) throw e
                val next=routes.getOrNull(index+1) ?: throw e
                val reason=when(failure.kind) {
                    KeyFailureKind.QUOTA_EXHAUSTED -> "额度耗尽"
                    KeyFailureKind.INVALID_CREDENTIAL -> "认证失效"
                    KeyFailureKind.RATE_LIMITED -> "临时限流（不是没余额）"
                    else -> "不可用"
                }
                notice!!("⚠️ 「${candidate.name}」${reason}，准备切换到「${next.name}」· ${next.endpoint.model}……")
            }
        }
    }

    private fun streamWithinProvider(
        endpoint: ApiEndpoint,
        messages: List<ApiMessage>,
        tools: List<ToolSpec> = emptyList(),
        thinking: Boolean = false,
        notice: (suspend (String) -> Unit)? = null,
    ): Flow<ChatEvent> = flow {''',
"provider-aware streaming wrapper")

p=root/client
text=p.read_text(encoding="utf-8")
start=text.index("class ChatClient(")
end=text.index(") {",start)
prefix=text[:end].rstrip()
if not prefix.endswith(","): prefix+=","
text=prefix+"\n    private val providerPool: ProviderFailoverPool? = null,\n"+text[end:]
p.write_text(text,encoding="utf-8")

patch(app,
'''    val apiKeyPool = com.cleo.cleos.ai.ApiKeyPool(secrets)
    val chatClient = ChatClient(http, keyPool=apiKeyPool)''',
'''    val apiKeyPool = com.cleo.cleos.ai.ApiKeyPool(secrets)
    val providerFailoverPool = com.cleo.cleos.ai.ProviderFailoverPool(secrets)
    val chatClient = ChatClient(http, keyPool=apiKeyPool, providerPool=providerFailoverPool)''',
"app DI provider pool")

patch(fields,
'''    var poolError by mutableStateOf<String?>(null)
''',
'''    var poolError by mutableStateOf<String?>(null)
    var providerPanel by mutableStateOf<com.cleo.cleos.ai.ProviderPanel?>(null)
    var providerModelDrafts by mutableStateOf<Map<String,String>>(emptyMap())
    var providerWarning by mutableStateOf<String?>(null)
    fun loadProviders() {
        val endpoint=ApiEndpoint(baseUrl,"",model)
        scope.launch {
            val current=c.providerFailoverPool.panel(endpoint)
            providerPanel=current
            providerModelDrafts=current.rows.associate { it.baseUrl to it.model }
            providerWarning=null
        }
    }
    fun editProviderModel(url:String,value:String) {
        providerModelDrafts=providerModelDrafts + (url to value)
    }
    fun saveProviderModel(url:String) {
        val chosen=providerModelDrafts[url].orEmpty().trim()
        scope.launch { c.providerFailoverPool.setModel(url,chosen);loadProviders() }
    }
    fun setCrossProviderEnabled(on:Boolean) {
        scope.launch { c.providerFailoverPool.turnOn(on);loadProviders() }
    }
    fun setProviderAllowed(url:String,on:Boolean) {
        scope.launch { c.providerFailoverPool.allow(url,on);loadProviders() }
    }
    fun moveProvider(url:String,delta:Int) {
        scope.launch { c.providerFailoverPool.move(url,delta);loadProviders() }
    }
    fun resetActiveProvider() {
        val endpoint=ApiEndpoint(baseUrl,"",model)
        scope.launch { c.providerFailoverPool.resetActive(endpoint);loadProviders() }
    }
''',"settings state provider dialog")

# Integrate next to the existing six provider buttons even if the prior key
# management patch has already inserted extra UI in this function.
p=root/page
code=p.read_text(encoding="utf-8")
start=code.find("private fun ServiceChips(fields: EndpointFields) {")
end=code.find("/** Address, key and model",start)
if start<0 or end<0: raise RuntimeError("Cannot locate existing provider chips")
section=code[start:end]
if "editingProviderFallback" in section: raise RuntimeError("Duplicate provider fallback UI")
section=section.replace(
    "private fun ServiceChips(fields: EndpointFields) {",
    "private fun ServiceChips(fields: EndpointFields) {\n"
    "    var editingProviderFallback by remember { mutableStateOf(false) }",1)
close=section.rfind("\n}")
if close<0: raise RuntimeError("Missing ServiceChips closing brace")
section=section[:close]+'''
    Chip("六家服务商 · 自动切换", selected = false) {
        fields.loadProviders()
        editingProviderFallback = true
    }
    if(editingProviderFallback) ProviderFallbackDialog(fields) {
        editingProviderFallback = false
    }
'''+section[close:]
p.write_text(code[:start]+section+code[end:],encoding="utf-8")

build=root/"app/build.gradle.kts"
code=build.read_text(encoding="utf-8")
for before,after in [('versionName = "0.37.38"','versionName = "0.37.39beta"'),
                     ('versionCode = 62062','versionCode = 62063')]:
    if code.count(before)!=1: raise RuntimeError("version marker "+before)
    code=code.replace(before,after,1)
build.write_text(code,encoding="utf-8")
for name,dest in [
 ("ProviderFailoverPool.kt","app/src/main/java/com/cleo/cleos/ai/ProviderFailoverPool.kt"),
 ("ProviderFailoverRulesTest.kt","app/src/test/java/com/cleo/cleos/ai/ProviderFailoverRulesTest.kt"),
 ("ProviderFallbackDialog.kt","app/src/main/java/com/cleo/cleos/ui/settings/ProviderFallbackDialog.kt")
]:
    out=root/dest
    out.parent.mkdir(parents=True,exist_ok=True)
    copyfile(here/name,out)
print("0.37.39beta provider chip fallback integrated, 62063")
