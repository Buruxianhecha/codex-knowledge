#!/usr/bin/env python3
"""0.38.11 integration guard: manage AI moments, AI diaries can be locked, no accidental preview."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
base=root/"app/src/main/java/com/cleo/cleos"
moment_ui=(base/"ui/MomentsScreen.kt").read_text(encoding="utf-8")
moment_store=(base/"data/MomentsStore.kt").read_text(encoding="utf-8")
tools=(base/"ai/Tools.kt").read_text(encoding="utf-8")
diary_list=(base/"ui/diary/DiaryListScreen.kt").read_text(encoding="utf-8")
diary_editor=(base/"ui/diary/DiaryEditorScreen.kt").read_text(encoding="utf-8")
diary_vm=(base/"ui/diary/DiaryEditorViewModel.kt").read_text(encoding="utf-8")
prompt=(base/"ai/Prompt.kt").read_text(encoding="utf-8")
version=(root/"app/build.gradle.kts").read_text(encoding="utf-8")

assert 'versionName = "0.38.11"' in version
assert 'versionCode = 62077' in version

assert 'if(post.authorId==0L) {' not in moment_ui, "AI post still gated from management menu"
assert 'Icon(Icons.Rounded.MoreHoriz,contentDescription="动态管理"' in moment_ui
assert '编辑' not in moment_ui or '修改可见范围' in moment_ui
assert 'DropdownMenuItem(text={Text("删除动态")}' in moment_ui
assert 'it.id==id && it.authorId==0L' not in moment_store, "store still blocks AI posts"
assert 'MomentAccess.canSee(it,viewer' not in moment_store
assert 'if(viewer==0L || viewer==post.authorId)' in moment_store
assert 'images.delete(original.photos)' in moment_store

assert 'val manageMySecret = ToolSpec(' in tools
assert 'ToolSpecs.manageMySecret.name -> manageMySecret(' in tools
assert 'secret = hide,' in tools
assert 'write_diary' in tools
assert 'it.companionId == companionId' in tools, "AI must read only own secret"
assert 'diary.update(entry.copy(secret=false' in tools
assert 'TA 写下了一篇小秘密（未公开正文）' in tools
assert 'manage_my_secret' in prompt

assert 'if (locked) emptyList() else DiaryBlocks.decode(e.blocks)' in diary_list
assert 'title = if (locked) "TA 的小秘密" else e.title' in diary_list
assert 'lockedForUser = locked' in diary_list
assert 'clickable(enabled = !card.lockedForUser' in diary_list
assert 'val lockedForUser: Boolean get() = readOnly && secret' in diary_vm
assert 'e.author == DiaryEntryEntity.AUTHOR_AI && e.secret' in diary_vm
assert 'if (vm.loaded && vm.lockedForUser)' in diary_editor
assert 'return' in diary_editor[diary_editor.index('if (vm.loaded && vm.lockedForUser)'):diary_editor.index('if (vm.loaded && vm.lockedForUser)')+1200]

print("v0.38.11: owner manages any author moments; only AI decides diary secrecy and can reveal; UI and direct reader are masked")
