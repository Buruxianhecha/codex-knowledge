#!/usr/bin/env python3
"""v0.37.40beta: rewire bottom tabs without rewriting or deleting existing features.

The current Room diary and todo screens remain in place and are entered as second-
level pages with a real system back action; contacts and groups use live DB flows.
No schema changes and no migration of any existing diary, AI or group content.
"""
from pathlib import Path
from shutil import copyfile
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent

def patch(path,old,new,label):
    p=root/path
    content=p.read_text(encoding="utf-8")
    hits=content.count(old)
    if hits!=1: raise RuntimeError(f"{label}: expected 1 anchor, got {hits}")
    p.write_text(content.replace(old,new,1),encoding="utf-8")

main="app/src/main/java/com/cleo/cleos/ui/MainScreen.kt"
diary="app/src/main/java/com/cleo/cleos/ui/diary/DiaryListScreen.kt"
todo="app/src/main/java/com/cleo/cleos/ui/todo/TodoScreen.kt"
daos="app/src/main/java/com/cleo/cleos/data/db/Daos.kt"

patch(main,
'''import androidx.compose.animation.AnimatedVisibility
''',
'''import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
''',"Android back navigation")
patch(main,
'''import androidx.compose.material.icons.outlined.AutoStories
''',
'''import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Explore
''',"bottom icon imports")
patch(main,
'''import androidx.compose.material.icons.rounded.AutoStories
''',
'''import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Explore
''',"selected tab icons")
patch(main,
'''import com.cleo.cleos.Opening
''',
'''import com.cleo.cleos.Opening
import com.cleo.cleos.data.db.ConversationEntity
import kotlinx.coroutines.launch
''',"group selection imports")
patch(main,
'''    var tab by rememberSaveable { mutableIntStateOf(0) }
''',
'''    var tab by rememberSaveable { mutableIntStateOf(0) }
    /** Secondary pages replace the current tab but keep its navigation position. */
    var secondPage by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = secondPage.isNotEmpty()) { secondPage = "" }
''',"second-level navigation state")
patch(main,
'''        if (opening is Opening.Chat) {
            tab = 0
''',
'''        if (opening is Opening.Chat) {
            secondPage = ""
            tab = 0
''',"notification returns to chat")
patch(main,
'''            GlassTab("日记", Icons.Outlined.AutoStories, Icons.Rounded.AutoStories),
            GlassTab("待办", Icons.Outlined.TaskAlt, Icons.Rounded.TaskAlt),
''',
'''            GlassTab("通讯录", Icons.Outlined.Contacts, Icons.Rounded.Contacts),
            GlassTab("发现", Icons.Outlined.Explore, Icons.Rounded.Explore),
''',"bottom tab names/icons")
patch(main,
'''                            1 -> DiaryTab(bottomInset, onOpenDiaryEntry)
                            2 -> TodoTab(bottomInset)
''',
'''                            1 -> if (secondPage == "groups") {
                                GroupDirectoryTab(
                                    onBack = { secondPage = "" },
                                    onOpenGroup = { group ->
                                        c.appScope.launch {
                                            c.companions.select(group.companionId)
                                            c.settings.setCurrentConversation(group.id)
                                            secondPage = ""
                                            tab = 0
                                        }
                                    },
                                    onCreateGroup = { ids ->
                                        c.appScope.launch {
                                            val conversationId = c.chat.newGroupConversation(ids.toList())
                                            val group = c.db.conversations().get(conversationId)
                                            if (group != null) {
                                                c.companions.select(group.companionId)
                                                c.settings.setCurrentConversation(group.id)
                                                secondPage = ""
                                                tab = 0
                                            }
                                        }
                                    },
                                )
                            } else {
                                ContactsTab(
                                    bottomInset = bottomInset,
                                    onOpenCompanion = { id ->
                                        c.appScope.launch {
                                            c.companions.select(id)
                                            secondPage = ""
                                            tab = 0
                                        }
                                    },
                                    onOpenGroups = { secondPage = "groups" },
                                    onAddCompanion = {
                                        c.appScope.launch {
                                            c.companions.add()
                                            onOpenSettings()
                                        }
                                    },
                                )
                            }
                            2 -> when (secondPage) {
                                "diary" -> DiaryTab(24.dp, onOpenDiaryEntry, onBack = { secondPage = "" })
                                "todos" -> TodoTab(24.dp, onBack = { secondPage = "" })
                                else -> DiscoverTab(
                                    bottomInset = bottomInset,
                                    onDiary = { secondPage = "diary" },
                                    onTodo = { secondPage = "todos" },
                                    onLetters = onOpenLetters,
                                    onMemory = onOpenMemory,
                                )
                            }
''',"replace tabs with real nested screens")
patch(main,
'''            visible = !keyboardOpen,
''',
'''            visible = !keyboardOpen && secondPage.isEmpty(),
''',"hide bottom navigation on second-level pages")
patch(main,
'''                onSelect = { tab = it },
''',
'''                onSelect = { secondPage = ""; tab = it },
''',"reset details on tab switch")

# Preserve original DiaryTab and TodoTab in FULL, including privacy filters,
# undo, editors, AI journals, date grouping, creation actions and existing Room.
patch(diary,
'''import androidx.compose.material.icons.Icons
''',
'''import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
''',"diary back icon")
patch(diary,
'''fun DiaryTab(bottomInset: Dp, onOpenEntry: (id: Long, secret: Boolean) -> Unit) {
''',
'''fun DiaryTab(bottomInset: Dp, onOpenEntry: (id: Long, secret: Boolean) -> Unit,
             onBack: (() -> Unit)? = null) {
''',"diary nested route")
patch(diary,
'''                title = "日记",
                subtitle = if (count > 0) "$count 篇" else null,
                backdrop = page,
''',
'''                title = "日记",
                subtitle = if (count > 0) "$count 篇" else null,
                backdrop = page,
                leading = { if (onBack != null) GlassIconButton(
                    Icons.AutoMirrored.Rounded.ArrowBack, "返回发现", onBack, page) },
''',"diary return button")

patch(todo,
'''import androidx.compose.material.icons.Icons
''',
'''import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
''',"todo back icon")
patch(todo,
'''fun TodoTab(bottomInset: Dp) {
''',
'''fun TodoTab(bottomInset: Dp, onBack: (() -> Unit)? = null) {
''',"todo nested route")
patch(todo,
'''                title = "待办",
                subtitle = when {
''',
'''                title = "待办",
                leading = { if (onBack != null) GlassIconButton(
                    Icons.AutoMirrored.Rounded.ArrowBack, "返回发现", onBack, page) },
                subtitle = when {
''',"todo return button")

# One live query for all actual groups. No new table or migration is needed.
patch(daos,
'''    suspend fun groupsOwnedBy(companionId: Long): List<ConversationEntity>
''',
'''    suspend fun groupsOwnedBy(companionId: Long): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE isGroup = 1 ORDER BY pinned DESC, COALESCE(manualRank, updatedAt) DESC, id DESC")
    fun observeGroups(): Flow<List<ConversationEntity>>
''',"all-group directory DAO")

build=root/"app/build.gradle.kts"
text=build.read_text(encoding="utf-8")
for old,new in [('versionName = "0.37.39beta"','versionName = "0.37.40beta"'),
                ('versionCode = 62063','versionCode = 62064')]:
    if text.count(old)!=1: raise RuntimeError(f"Bad version marker: {old}")
    text=text.replace(old,new,1)
build.write_text(text,encoding="utf-8")

target=root/"app/src/main/java/com/cleo/cleos/ui/HubScreens.kt"
target.parent.mkdir(parents=True,exist_ok=True)
copyfile(here/"HubScreens_03740.kt",target)
print("v0.37.40beta: bottom Contacts/Discover, real AI/group directory, original Diary/Todo as nested screens")
