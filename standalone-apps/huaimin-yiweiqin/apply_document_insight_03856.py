#!/usr/bin/env python3
"""Incremental 0.38.56 patch over v0.38.55, preserving chats and previous features."""
from pathlib import Path
import sys

def apply(root:Path):
    home=Path(__file__).resolve().parent
    src=root/"app/src/main/java/com/cleo/cleos"
    tests=root/"app/src/test/java/com/cleo/cleos"
    gradle=root/"app/build.gradle.kts"
    version=gradle.read_text(encoding="utf-8")
    assert version.count('versionName = "0.38.55"')==1
    assert version.count("versionCode = 62121")==1
    old=(src/"data/ChatFileTransfer.kt").read_text(encoding="utf-8")
    assert old.count("const val MAX_FILE_BYTES = 200L * 1024L * 1024L")==1

    for name,dest in {
        "ChatFileTransfer_03856.kt":src/"data/ChatFileTransfer.kt",
        "DocumentInsightExtractor_03856.kt":src/"data/DocumentInsightExtractor.kt",
        "ChatFileTransferTest_03856.kt":tests/"data/ChatFileTransferTest.kt",
        "BackupArchiveGuardTest_03856.kt":tests/"data/BackupArchiveGuardTest.kt",
    }.items():
        assert (home/name).is_file(),name
        dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_bytes((home/name).read_bytes())

    def change(path,changes):
        old=path.read_text(encoding="utf-8")
        new=old
        for a,b in changes:
            count=new.count(a)
            if count!=1:
                raise RuntimeError(f"{path.name}: expected one anchor ({count}): {a[:95]}")
            new=new.replace(a,b,1)
        path.write_text(new,encoding="utf-8")

    guard=src/"data/BackupArchiveGuard.kt"
    change(guard,[
        ("const val MAX_MEDIA_BYTES = 256L * 1024 * 1024",
         "const val MAX_MEDIA_BYTES = 1024L * 1024 * 1024"),
        ("const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024",
         "const val MAX_TOTAL_BYTES = 16L * 1024 * 1024 * 1024"),
    ])
    prompt=src/"ai/Prompt.kt"
    change(prompt,[
        ("import com.cleo.cleos.data.ChatFileTransfer",
         "import com.cleo.cleos.data.DocumentInsightExtractor\nimport com.cleo.cleos.data.ChatFileTransfer"),
        ("        add(FORMAT_RULE)","        add(FORMAT_RULE)\n        add(DocumentInsightExtractor.guide())"),
    ])
    screen=src/"ui/chat/ChatScreen.kt"
    change(screen,[
        ('''                                            Text((doc.bytes/1024L).toString()+" KB · " +
                                                if(doc.extracted.isBlank()) "仅原文件" else "可供AI阅读部分正文",
                                                fontSize=11.sp)
                                            Text("点击保存原文件",fontSize=11.sp)''',
         '''                                            val sizeLabel = if(doc.bytes >= 1024L*1024L*1024L)
                                                "%.2f GiB".format(doc.bytes.toDouble()/(1024L*1024L*1024L))
                                                else if(doc.bytes >= 1024L*1024L)
                                                "%.1f MiB".format(doc.bytes.toDouble()/(1024L*1024L))
                                                else (doc.bytes/1024L).toString()+" KiB"
                                            Text(sizeLabel+" · "+
                                                if(doc.note.contains("暂无可靠") || doc.extracted.isBlank()) "已保存原件"
                                                else "已提取内容样本",fontSize=11.sp)
                                            if(doc.note.isNotBlank()) Text(doc.note,fontSize=10.sp,
                                                color=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("点击保存原文件",fontSize=11.sp)'''),
    ])
    code=(src/"data/ChatFileTransfer.kt").read_text(encoding="utf-8")
    assert "1024L * 1024L * 1024L" in code
    assert "DocumentInsightExtractor.inspect" in code
    assert "images.file(internal)" in code and "dest.outputStream()" in code
    assert "文件大小" not in code or "1 GB" in code
    assert "const val MAX_EXCERPT = 14_000" in code
    assert "MAX_MEDIA_BYTES = 1024L" in guard.read_text(encoding="utf-8")
    assert "DocumentInsightExtractor.guide()" in prompt.read_text(encoding="utf-8")
    test=(tests/"data/ChatFileTransferTest.kt").read_text(encoding="utf-8")
    assert test.count("@Test ")==12
    assert (tests/"data/BackupArchiveGuardTest.kt").read_text(encoding="utf-8").count("@Test ")==14

    gradle.write_text(version.replace('versionName = "0.38.55"','versionName = "0.38.56"',1)
        .replace("versionCode = 62121","versionCode = 62122",1),encoding="utf-8")
    print("0.38.56 Build 62122: up to 1 GiB originals, evidence sampling, document-purpose reasoning")
    print("Limitation: sampled content is NOT full-text knowledge; huge/scanned PDFs still need specialized parsing")

if __name__=="__main__":
    if len(sys.argv)!=2:raise SystemExit("usage: apply_document_insight_03856.py ANDROID_ROOT")
    apply(Path(sys.argv[1]).resolve())
