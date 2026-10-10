#!/usr/bin/env python3
"""v0.38.4: add chat + grid and bind per-chat cards directly to the real 0.38.3 wallet ledger."""
from pathlib import Path
from shutil import copyfile
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
base = Path("app/src/main/java/com/cleo/cleos")
screen = base / "ui/chat/ChatScreen.kt"

def once(path, old, new, why):
    file = root / path
    value = file.read_text(encoding="utf-8")
    count = value.count(old)
    if count != 1:
        raise RuntimeError("%s: expected one anchor, found %s in %s" % (why, count, path))
    file.write_text(value.replace(old, new, 1), encoding="utf-8")

target = root / base / "ui/chat/ChatWalletPanel.kt"
target.parent.mkdir(parents=True, exist_ok=True)
copyfile(here / "ChatWalletPanel_0384.kt", target)

once(screen,
     "import androidx.compose.material.icons.rounded.AddComment\n",
     "import androidx.compose.material.icons.rounded.AddComment\nimport androidx.compose.material.icons.rounded.Add\n",
     "plus icon import")

once(screen,
     "private sealed interface ChatRow {\n    val key: Any\n",
     """private sealed interface ChatRow {
    val key: Any

    /** These cards are reconstructed from actual persistent wallet events. */
    data class Wallet(val event: ChatWalletItem) : ChatRow {
        override val key: Any get() = event.id
    }
""",
     "wallet card row")

once(screen,
     """    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var deletingSticker by remember { mutableStateOf<StickerEntity?>(null) }""",
     """    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var plusOpen by rememberSaveable { mutableStateOf(false) }
    var walletAction by remember { mutableStateOf<ChatMoneyKind?>(null) }
    var walletBusy by remember { mutableStateOf(false) }
    val walletBook by remember { c.wallet.state }.collectAsStateWithLifecycle()
    var deletingSticker by remember { mutableStateOf<StickerEntity?>(null) }""",
     "plus and wallet dialog state")

once(screen,
     "    BackHandler(enabled = drawerOpen && pageShown) { drawerOpen = false }",
     "    BackHandler(enabled = (drawerOpen || plusOpen) && pageShown) { drawerOpen = false; plusOpen = false }",
     "system back closes both drawers")

once(screen,
     """    val rows = remember(state.messages, state.recapUntil, state.avatarEachMessage) {
        buildRows(state.messages, state.recapUntil, eachFace = state.avatarEachMessage)
    }""",
     """    val rows = remember(state.messages, state.recapUntil, state.avatarEachMessage,
        walletBook, state.conversationId) {
        buildRows(state.messages, state.recapUntil, eachFace = state.avatarEachMessage)
            .toMutableList().also { merged ->
                // Merge by timestamp: later text messages must appear AFTER older wallet cards.
                chatWalletItems(walletBook, state.conversationId).forEach { event ->
                    val index = merged.indexOfFirst {
                        it is ChatRow.Message && it.message.createdAt <= event.at
                    }
                    merged.add(if (index < 0) merged.size else index, ChatRow.Wallet(event))
                }
            }
    }""",
     "wallet cards in chronological chat rows")

once(screen,
     """                onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemove = vm::detach,""",
     """                onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                plusOpen = plusOpen,
                onPlus = {
                    plusOpen = !plusOpen
                    drawerOpen = false
                    if (plusOpen) {
                        keyboard?.hide()
                        focusManager.clearFocus()
                    }
                },
                plusPanel = {
                    ChatPlusPanel(
                        onAlbum = {
                            plusOpen = false
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onCall = { plusOpen = false; startCall() },
                        onRedPacket = { plusOpen = false; walletAction = ChatMoneyKind.PACKET },
                        onTransfer = { plusOpen = false; walletAction = ChatMoneyKind.TRANSFER },
                    )
                },
                onRemove = vm::detach,""",
     "plus actions on real chat")

once(screen,
     """                    drawerOpen = !drawerOpen
                    // The drawer takes the keyboard's place; typing again closes it (onFieldFocus).""",
     """                    drawerOpen = !drawerOpen
                    plusOpen = false
                    // The drawer takes the keyboard's place; typing again closes it (onFieldFocus).""",
     "mutually exclusive sticker and plus panels")

once(screen,
     "                onFieldFocus = { drawerOpen = false },",
     "                onFieldFocus = { drawerOpen = false; plusOpen = false },",
     "close plus on typing")

once(screen,
     """                    when (row) {
                        is ChatRow.Stamp -> TimeStamp(row.at)""",
     """                    when (row) {
                        is ChatRow.Wallet -> ChatWalletCard(row.event)
                        is ChatRow.Stamp -> TimeStamp(row.at)""",
     "render ledger cards")

once(screen,
     """    if (creatingGroup) {
        GroupCreateDialog(""",
     """    walletAction?.let { kind ->
        ChatWalletActionDialog(
            kind = kind,
            members = if (state.isGroup) state.groupMembers else listOf(
                GroupMemberUi(state.companionId, state.aiName, state.aiAvatar, state.aiAvatarEmoji)
            ),
            isGroup = state.isGroup,
            balance = walletBook.balances[0L] ?: 0L,
            onDismiss = { if (!walletBusy) walletAction = null },
            onConfirm = { ids, cents, random ->
                val conversation = state.conversationId
                if (!walletBusy && conversation != null && ids.isNotEmpty()) {
                    walletBusy = true
                    scope.launch {
                        try {
                            // Recheck membership and conversation at commit time.
                            val live = c.db.conversations().get(conversation)
                                ?: throw IllegalStateException("会话已删除，请重新打开")
                            val currentMembers = if (live.isGroup) {
                                c.db.groupMembers().idsFor(conversation).toSet()
                            } else setOf(live.companionId)
                            require(ids.all { it in currentMembers }) { "群成员已变化，请重新选择" }
                            if (kind == ChatMoneyKind.PACKET) {
                                c.wallet.sendPacket(0L, ids, cents, random, conversation)
                            } else {
                                require(ids.size == 1) { "一次只能转账给一位 AI" }
                                c.wallet.transfer(0L, ids.single(), cents, conversation)
                            }
                            walletAction = null
                            sentCount++
                        } catch (error: Exception) {
                            voiceHint = error.message ?: "操作未完成，未确认扣款"
                        } finally {
                            walletBusy = false
                        }
                    }
                }
            },
        )
    }
    if (creatingGroup) {
        GroupCreateDialog(""",
     "wallet confirmation and membership recheck")

once(screen,
     """    onPick: () -> Unit,
    onRemove: (MessageImage) -> Unit,""",
     """    onPick: () -> Unit,
    plusOpen: Boolean = false,
    onPlus: () -> Unit = {},
    plusPanel: @Composable () -> Unit = {},
    onRemove: (MessageImage) -> Unit,""",
     "plus composer API")

once(screen,
     """        // The stickers, in the same glass as the rest, where the keyboard would otherwise be.
        if (drawerOpen) drawer()""",
     """        // Both panels share the existing input measurement and keyboard avoidance path.
        if (plusOpen) plusPanel()
        if (drawerOpen) drawer()""",
     "plus panel in input overlay")

once(screen,
     """            Box(Modifier.size(BarHeight), contentAlignment = Alignment.Center) {
                // Plain fills inside the glass, like the chips on a card: glass in glass reads as a hole.""",
     """            Box(Modifier.size(width = 40.dp, height = BarHeight), contentAlignment = Alignment.Center) {
                Box(Modifier.size(34.dp).clip(CircleShape)
                    .background(if (plusOpen) palette.content.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable(onClick = onPlus), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add,
                        contentDescription = if (plusOpen) "收起更多功能" else "更多功能",
                        tint = if (plusOpen) palette.accentContent else palette.contentSecondary,
                        modifier = Modifier.size(25.dp))
                }
            }
            Box(Modifier.size(BarHeight), contentAlignment = Alignment.Center) {
                // Plain fills inside the glass, like the chips on a card: glass in glass reads as a hole.""",
     "plus button beside emoji and voice/send button")

gradle=Path("app/build.gradle.kts")
once(gradle, 'versionName = "0.38.3"', 'versionName = "0.38.4"', "versionName")
once(gradle, 'versionCode = 62069', 'versionCode = 62070', "versionCode")
print("0.38.4/62070: plus panel, real per-chat transfer and packet ledger cards, single/group membership.")
