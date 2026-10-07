#!/usr/bin/env python3
from pathlib import Path
import shutil, sys

ROOT = Path(sys.argv[1]).resolve()
HERE = Path(__file__).resolve().parent

def rep(rel, old, new):
    p = ROOT / rel
    s = p.read_text(encoding='utf-8')
    n = s.count(old)
    if n != 1:
        raise SystemExit(f'{rel}: expected one match, got {n}: {old[:120]!r}')
    p.write_text(s.replace(old, new, 1), encoding='utf-8')

def cp(src_name, rel):
    src = HERE / src_name
    dst = ROOT / rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dst)

vm = 'app/src/main/java/com/cleo/cleos/ui/chat/ChatViewModel.kt'
rep(vm,
'''data class ChatUiState(
    val conversationId: Long? = null,''',
'''data class GroupMemberUi(val id: Long, val name: String, val avatar: String?, val avatarEmoji: String?)

data class ChatUiState(
    val conversationId: Long? = null,''')
rep(vm,
'''    /** The TA this conversation is with. */
    val companionId: Long = 0,
    val aiName: String = "",''',
'''    /** The legacy/primary TA for this conversation. Group messages carry their real sender separately. */
    val companionId: Long = 0,
    val isGroup: Boolean = false,
    val groupMembers: List<GroupMemberUi> = emptyList(),
    val aiName: String = "",''')
rep(vm,
'''        val here = combine(c.db.conversations().observe(id), c.companions.all) { conversation, list ->
            (list.firstOrNull { it.id == conversation?.companionId } ?: list.firstOrNull())?.let { conversation to it }
        }.filterNotNull()''',
'''        val here = combine(c.db.conversations().observe(id), c.companions.all, c.db.groupMembers().observeFor(id)) { conversation, list, membership ->
            val primary = list.firstOrNull { it.id == conversation?.companionId } ?: list.firstOrNull()
            primary?.let {
                val members = if (conversation?.isGroup == true) membership.mapNotNull { row -> list.firstOrNull { it.id == row.companionId } } else listOf(it)
                Triple(conversation, it, members)
            }
        }.filterNotNull()''')
rep(vm,
'''            here.map { it.second.apiBaseUrl }.distinctUntilChanged().flatMapLatest { c.secrets.hasKey(it) },
            c.settings.settings,
        ) { messages, (streaming, transcribing), (conversation, ta), hasKey, s ->''',
'''            here.map { it.second.apiBaseUrl }.distinctUntilChanged().flatMapLatest { c.secrets.hasKey(it) },
            c.settings.settings,
        ) { messages, (streaming, transcribing), (conversation, ta, members), hasKey, s ->''')
rep(vm,
'''                companionId = ta.id,
                aiName = ta.name,''',
'''                companionId = ta.id,
                isGroup = conversation?.isGroup == true,
                groupMembers = members.map { GroupMemberUi(it.id, it.name, it.avatar, it.avatarEmoji) },
                aiName = if (conversation?.isGroup == true) conversation.title else ta.name,''')
rep(vm,
'''                model = ta.apiModel,
                recap = conversation?.recap,''',
'''                model = if (conversation?.isGroup == true) "群聊 · ${members.size} 个角色" else ta.apiModel,
                recap = if (conversation?.isGroup == true) null else conversation?.recap,''')
rep(vm,
'''    fun switchTo(companionId: Long) {
        viewModelScope.launch { c.companions.select(companionId) }
    }

    /** A new TA, chosen right away; [then] opens their settings to name them and pick a model. */''',
'''    fun switchTo(companionId: Long) {
        viewModelScope.launch { c.companions.select(companionId) }
    }

    fun createGroup(memberIds: Set<Long>) {
        viewModelScope.launch {
            val current = c.companions.current.first().id
            val ordered = (listOf(current) + memberIds).distinct()
            if (ordered.size < 2) return@launch
            c.settings.setCurrentConversation(c.chat.newGroupConversation(ordered))
        }
    }

    /** A new TA, chosen right away; [then] opens their settings to name them and pick a model. */''')

print('apply_group_chats_vm.py applied')
