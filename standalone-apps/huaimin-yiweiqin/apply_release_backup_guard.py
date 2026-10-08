#!/usr/bin/env python3
"""Harden portable restores before touching the user's database or files."""
from pathlib import Path
import shutil
import sys

root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
def rep(rel,old,new,count=1):
    p=root/rel
    s=p.read_text(encoding="utf-8")
    n=s.count(old)
    if n != count:
        raise SystemExit(f"{rel}: expected {count} matches, got {n}: {old[:130]!r}")
    p.write_text(s.replace(old,new),encoding="utf-8")

base="app/src/main/java/com/cleo/cleos/"
rep("app/build.gradle.kts", 'versionCode = 62045', 'versionCode = 62046')
shutil.copyfile(here/"BackupArchiveGuard.kt",root/base/"data/BackupArchiveGuard.kt")
shutil.copyfile(here/"BackupArchiveGuardTest.kt",root/"app/src/test/java/com/cleo/cleos/data/BackupArchiveGuardTest.kt")
rel=base+"data/BackupService.kt"
rep(rel,
'''import java.io.BufferedOutputStream
import java.io.File''',
'''import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File''')
rep(rel,
'''            var data: BackupFile? = null
            var configuration: ConfigurationBackup? = null
            ZipInputStream(BufferedInputStream(input)).use { zip ->''',
'''            var data: BackupFile? = null
            var configuration: ConfigurationBackup? = null
            val mediaNames = HashSet<String>()
            var entries = 0
            var uncompressed = 0L
            fun countUncompressed(bytes: Int) {
                uncompressed += bytes.toLong()
                if (uncompressed > BackupArchiveGuard.MAX_TOTAL_BYTES)
                    throw BackupException("备份超过可恢复的总大小限制")
            }
            ZipInputStream(BufferedInputStream(input)).use { zip ->''')
rep(rel,
'''                    val entry = zip.nextEntry ?: break
                    when {''',
'''                    val entry = zip.nextEntry ?: break
                    entries++
                    if (entries > BackupArchiveGuard.MAX_ENTRIES) throw BackupException("备份包含太多文件，没有恢复")
                    when {''')
rep(rel,
'''json.decodeFromString<BackupFile>(zip.readBytes().decodeToString())''',
'''json.decodeFromString<BackupFile>(
                                    BackupArchiveGuard.readLimited(zip, BackupArchiveGuard.MAX_JSON_BYTES, ::countUncompressed).decodeToString()
                                )''')
rep(rel,
'''                            configuration = ConfigurationCodec.decode(zip)''',
'''                            configuration = ConfigurationCodec.decode(
                                ByteArrayInputStream(BackupArchiveGuard.readLimited(
                                    zip, BackupArchiveGuard.MAX_CONFIG_BYTES, ::countUncompressed,
                                )),
                            )''')
rep(rel,
'''                            val name = File(entry.name).name
                            if (name.isNotBlank() && !name.startsWith(".")) {
                                File(staging, name).outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }''',
'''                            val name = BackupArchiveGuard.imageName(entry.name)
                            if (!mediaNames.add(name)) throw BackupException("备份包含重复图片或音频，没有恢复")
                            File(staging, name).outputStream().use { output ->
                                BackupArchiveGuard.copyLimited(zip, output, BackupArchiveGuard.MAX_MEDIA_BYTES, ::countUncompressed)
                            }
                        }
                        entry.isDirectory -> Unit
                        else -> throw BackupException("备份包含无法识别的文件，没有恢复")
                    }''')
rep(rel,
'''            if (d.version > BackupFile.VERSION) throw BackupException("这份备份来自更新版本的怀民亦未寝，先更新 App 再恢复")

            val fullConfiguration''',
'''            if (d.version > BackupFile.VERSION) throw BackupException("这份备份来自更新版本的怀民亦未寝，先更新 App 再恢复")
            // Validate graph before writing a snapshot, copying media, or modifying Room.
            val roleIds = d.companions.map { it.id }.ifEmpty { listOf(Companions.FIRST) }
            BackupArchiveGuard.validateRows(roleIds, d.conversations, d.groupMembers, d.messages)

            val fullConfiguration''')
print("Release backup guard, preflight references and bounded ZIP extraction applied")
