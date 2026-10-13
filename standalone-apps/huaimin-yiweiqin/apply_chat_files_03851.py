#!/usr/bin/env python3
"""Incremental 0.38.51 chat-file integration; keep the existing + panel and all prior chat features."""
from pathlib import Path
import hashlib
import sys

root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
base=root/"app/src/main/java/com/cleo/cleos"
tests=root/"app/src/test/java/com/cleo/cleos"
expected={
    "ui/chat/ChatScreen.kt":"bf9c6f3cd3ebd7da044aa471b9bb23f91d7d0d347edbdd39ed62d574fb8b8be0",
    "ui/chat/ChatViewModel.kt":"7226ff02a8ba314eeccbfa0637fb69eecae44d921b042358bd398b46f0fc16f3",
    "ai/ChatRepository.kt":"eb7c1dbeba21ceffb6896c70bfd273ab7c75f4ede45896df601faab1377c7575",
    "ai/Prompt.kt":"894c9417d20f180fb3af7ea30166837452b2c11066fa93ed315778ad229e2913",
    "data/BackupService.kt":"eeb93d7085bcac1e2fd059605bd84d6bacf5be32cbc59f3f855c8796fffa6246",
}
for name, digest in expected.items():
    present=hashlib.sha256((base/name).read_bytes()).hexdigest()
    assert present==digest, "0.38.50 generated tree changed unexpectedly: "+name
def update(name,changes):
    path=base/name
    text=path.read_text(encoding="utf-8")
    for old,new in changes:
        if text.count(old)!=1:
            raise RuntimeError(name+": expected exactly one anchor: "+old[:130]+" ("+str(text.count(old))+")")
        text=text.replace(old,new,1)
    path.write_text(text,encoding="utf-8")

file_src=here/"ChatFileTransfer_03851.kt"
assert file_src.is_file()
(base/"data/ChatFileTransfer.kt").write_bytes(file_src.read_bytes())
(test_src:=here/"ChatFileTransferTest_03851.kt")
assert test_src.is_file()
(tests/"data").mkdir(parents=True,exist_ok=True)
(tests/"data/ChatFileTransferTest.kt").write_bytes(test_src.read_bytes())

update("ui/chat/ChatViewModel.kt",[
    ("import android.net.Uri","import android.net.Uri\nimport android.content.Context"),
    ("import com.cleo.cleos.data.MessageAudio\n","import com.cleo.cleos.data.ChatDocument\nimport com.cleo.cleos.data.ChatFileTransfer\nimport com.cleo.cleos.data.MessageAudio"),
    ("import kotlinx.coroutines.launch","import kotlinx.coroutines.launch\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.withContext"),
    ("    fun detach(image: MessageImage) {",
     """    /** Picker copies selected files before they can be sent; UI remains responsive. */
    val documents = mutableStateListOf<ChatDocument>()
    var importingDocuments by mutableStateOf(false)
        private set

    fun attachDocuments(context: Context, uris: List<Uri>, onError:(String)->Unit) {
        if (uris.isEmpty() || importingDocuments) return
        viewModelScope.launch {
            importingDocuments = true
            try {
                for (uri in uris.take((ChatFileTransfer.MAX_FILES-documents.size).coerceAtLeast(0))) {
                    try {
                        val doc=withContext(Dispatchers.IO) { ChatFileTransfer.import(context,c.images,uri) }
                        documents.add(doc)
                    } catch (failure:Exception) {
                        onError("文件导入失败：" + (failure.message ?: "无法读取"))
                    }
                }
            } finally { importingDocuments = false }
        }
    }

    fun detachDocument(doc:ChatDocument) {
        if (documents.remove(doc)) c.appScope.launch { c.images.delete(listOf(doc.file)) }
    }

    fun detach(image: MessageImage) {"""),
    ("        if(attachments.isEmpty() && c.chat.goalCommand(id,state.value.companionId,text.trim())) return true\n        if (!c.chat.send(id, text, attachments.toList(), quoting, mentions)) return false\n        attachments.clear()",
     """        if(attachments.isEmpty() && documents.isEmpty() &&
            c.chat.goalCommand(id,state.value.companionId,text.trim())) return true
        if(importingDocuments) return false
        val body=if(documents.isEmpty()) text else ChatFileTransfer.encode(text,documents.toList())
        if (!c.chat.send(id, body, attachments.toList(), quoting, mentions)) return false
        attachments.clear()
        documents.clear()""")
])

update("ui/chat/ChatScreen.kt",[
    ("import com.cleo.cleos.data.MessageAudio\n","import com.cleo.cleos.data.ChatDocument\nimport com.cleo.cleos.data.ChatFileTransfer\nimport com.cleo.cleos.data.MessageAudio"),
    ("    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS)) {",
     """    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked ->
        vm.attachDocuments(context,picked) { voiceHint=it }
    }
    var exportingDoc by remember { mutableStateOf<ChatDocument?>(null) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destination ->
        val current=exportingDoc
        if(destination!=null && current!=null) playScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ChatFileTransfer.export(context,c.images,current,destination)
                }
                voiceHint="文件已保存：" + current.name
            } catch(e:Exception) { voiceHint="保存失败：" + (e.message ?: "不可写入") }
        }
        exportingDoc=null
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS)) {"""),
    ("                attachments = vm.attachments,\n                attaching = vm.attaching,",
     """                attachments = vm.attachments,
                documents = vm.documents,
                onRemoveDocument = vm::detachDocument,
                attaching = vm.attaching || vm.importingDocuments,"""),
    ("                plusPanel = {\n                    ChatPlusPanel(",
     """                plusPanel = {
                    Column {
                        TextButton(onClick = {
                            plusOpen=false
                            filePicker.launch(arrayOf("*/*"))
                        }, modifier=Modifier.fillMaxWidth()) {
                            Text("📎 文件 · HTML / PDF / Word / Excel / 更多格式")
                        }
                        ChatPlusPanel("""),
    ("                        voiceInputBusy = dictationBusy,\n                    )\n                },",
     """                        voiceInputBusy = dictationBusy,
                        )
                    }
                },"""),
    ("                                        onOpenImage = onOpenImage,",
     """                                        onOpenImage = onOpenImage,
                                        onOpenDocument = { doc ->
                                            exportingDoc=doc
                                            exportPicker.launch(doc.name)
                                        },"""),
    ("    onOpenImage: (String) -> Unit,\n    transcribing:",
     """    onOpenImage: (String) -> Unit,
    onOpenDocument:(ChatDocument)->Unit={},
    transcribing:"""),
    ("    val locationPoint = remember(message.content) { ChatLocationPayload.decode(message.content) }",
     """    val documentMessage=remember(message.content) { ChatFileTransfer.decode(message.content) }
    val locationPoint = remember(message.content) { ChatLocationPayload.decode(message.content) }"""),
    ("                    } else if (hasTables) {\n                        tableParts.forEach",
     """                    } else if (documentMessage != null) {
                        BubbleSurface(
                            mine=mine,companionId=aiLabelId ?: LocalBubbleCompanion.current,
                            modifier=Modifier.widthIn(max=bubbleMaxWidth())
                                .combinedClickable(onClick={},onLongClick={menu=true})
                        ) {
                            Column(Modifier.padding(vertical=4.dp)) {
                                if(documentMessage.text.isNotBlank())
                                    Text(documentMessage.text,color=bubbleTextColor(mine,aiLabelId ?: LocalBubbleCompanion.current))
                                documentMessage.files.forEach { doc ->
                                    TextButton(onClick={onOpenDocument(doc)}) {
                                        Column {
                                            Text("📄 " + doc.name,maxLines=2,overflow=TextOverflow.Ellipsis)
                                            Text((doc.bytes/1024L).toString()+" KB · " +
                                                if(doc.extracted.isBlank()) "仅原文件" else "可供AI阅读部分正文",
                                                fontSize=11.sp)
                                            Text("点击保存原文件",fontSize=11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    } else if (hasTables) {
                        tableParts.forEach"""),
    ("    attachments: List<MessageImage>,\n    attaching: Boolean,",
     """    attachments: List<MessageImage>,
    documents:List<ChatDocument> = emptyList(),
    onRemoveDocument:(ChatDocument)->Unit={},
    attaching: Boolean,"""),
    ("    val canSend = text.isNotBlank() || attachments.isNotEmpty()",
     "    val canSend = text.isNotBlank() || attachments.isNotEmpty() || documents.isNotEmpty()"),
    ("        if (isGroup && mentionActive) {",
     """        if(documents.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState())
                .padding(start=10.dp,end=10.dp,top=8.dp),
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                documents.forEach { doc ->
                    TextButton(onClick={onRemoveDocument(doc)}) {
                        Text("📎 "+doc.name.take(30)+" ×",maxLines=1)
                    }
                }
            }
        }
        if (isGroup && mentionActive) {"""),
])
update("ai/Prompt.kt",[
    ("import com.cleo.cleos.data.MessageImages","import com.cleo.cleos.data.ChatFileTransfer\nimport com.cleo.cleos.data.MessageImages"),
    ("        val text = words(content)\n        val spokenText",
     "        val text = words(ChatFileTransfer.forModel(content))\n        val spokenText"),
])
update("ai/ChatRepository.kt",[
    ("import com.cleo.cleos.data.MessageAudio\n","import com.cleo.cleos.data.ChatFileTransfer\nimport com.cleo.cleos.data.MessageAudio"),
    ("        val content = text.trim()\n        if (content.isEmpty()",
     "        val content = text.trim()\n        if (content.isEmpty()"),
    ('StickerText.plain(content).lineSequence().first().take(24)', 
     'StickerText.plain(ChatFileTransfer.display(content)).lineSequence().first().take(24)'),
    ('images.delete(MessageImages.decode(m?.images).map { it.file } + listOfNotNull(MessageAudios.decode(m?.audio)?.file))',
     'images.delete(MessageImages.decode(m?.images).map { it.file } + listOfNotNull(MessageAudios.decode(m?.audio)?.file) + ChatFileTransfer.originals(m?.content.orEmpty()))'),
    ('db.messages().audioIn(id).mapNotNull { MessageAudios.decode(it)?.file }',
     'db.messages().audioIn(id).mapNotNull { MessageAudios.decode(it)?.file } +\n                db.messages().all().filter { it.conversationId==id }.flatMap { ChatFileTransfer.originals(it.content) }'),
])
update("data/BackupService.kt",[
    ("import android.net.Uri","import android.net.Uri\nimport com.cleo.cleos.data.ChatFileTransfer"),
    ("data.messages.flatMap { m -> MessageImages.decode(m.images).map { it.file } } +",
     "data.messages.flatMap { m -> MessageImages.decode(m.images).map { it.file } + ChatFileTransfer.originals(m.content) } +"),
])
gradle=root/"app/build.gradle.kts"
g=gradle.read_text(encoding="utf-8")
assert g.count('versionName = "0.38.50"') == 1
assert g.count("versionCode = 62116") == 1
assert 'dependencies {' in g
g=g.replace('versionName = "0.38.50"','versionName = "0.38.51"',1)
g=g.replace('versionCode = 62116','versionCode = 62117',1)
g=g.replace("dependencies {","dependencies {\n    implementation(\"com.tom-roush:pdfbox-android:2.0.27.0\")",1)
gradle.write_text(g,encoding="utf-8")
print("0.38.51 62117: + menu file chooser, bounded document extractor, visible file messages, backups, exports, and model context")
