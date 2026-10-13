#!/usr/bin/env python3
"""Upgrade file transfers over complete v0.38.52; don't touch conversations or backup schemas."""
from pathlib import Path
import sys

def main(root:Path):
    source=Path(__file__).resolve().parent
    app=root/"app/src/main/java/com/cleo/cleos"
    tests=root/"app/src/test/java/com/cleo/cleos"
    release=root/"app/build.gradle.kts"
    gradle=release.read_text(encoding="utf-8")
    assert gradle.count('versionName = "0.38.52"')==1
    assert gradle.count("versionCode = 62118")==1
    original=(app/"data/ChatFileTransfer.kt").read_text(encoding="utf-8")
    assert original.count("const val MAX_FILE_BYTES = 12L * 1024L * 1024L")==1
    new=(source/"ChatFileTransfer_03853.kt").read_text(encoding="utf-8")
    assert "const val MAX_FILE_BYTES = 200L * 1024L * 1024L" in new
    assert "it.readText()" not in new and "reader.read(buffer" in new
    assert "file.length() > MAX_SAFE_PDF_PARSE_BYTES" in new
    assert "12MB" not in new
    plus=(app/"ui/chat/ChatWalletPanel.kt").read_text(encoding="utf-8")
    assert 'Action("文件", Icons.Rounded.Folder, true, onFile)' in plus
    assert 'Action("相册"' in plus and 'Action("拍摄"' in plus
    test=(source/"ChatFileTransferTest_03853.kt").read_text(encoding="utf-8")
    assert test.count("@Test ")==8
    (app/"data/ChatFileTransfer.kt").write_text(new,encoding="utf-8")
    (tests/"data/ChatFileTransferTest.kt").write_text(test,encoding="utf-8")
    release.write_text(gradle.replace('versionName = "0.38.52"',
        'versionName = "0.38.53"').replace("versionCode = 62118",
        "versionCode = 62119"),encoding="utf-8")
    print("0.38.53 Build 62119: single file up to 200 MiB, stream to storage; model preview and memory bounded")

if __name__=="__main__":
    if len(sys.argv)!=2:raise SystemExit("usage: apply_large_chat_files_03853.py ANDROID_ROOT")
    main(Path(sys.argv[1]).resolve())
