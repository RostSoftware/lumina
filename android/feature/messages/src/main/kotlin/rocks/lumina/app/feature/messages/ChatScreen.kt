package rocks.lumina.app.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import rocks.lumina.app.core.data.DmMessage
import rocks.lumina.app.core.data.SendState
import rocks.lumina.app.core.designsystem.component.ContentColumn
import rocks.lumina.app.core.designsystem.component.EmptyState
import rocks.lumina.app.core.designsystem.component.Hairline
import rocks.lumina.app.core.designsystem.component.LuminaIconButton
import rocks.lumina.app.core.designsystem.component.LuminaTextField
import rocks.lumina.app.core.designsystem.component.LuminaTopBar
import rocks.lumina.app.core.designsystem.component.Skeleton
import rocks.lumina.app.core.designsystem.component.Spinner
import rocks.lumina.app.core.designsystem.component.contentColumn
import rocks.lumina.app.core.designsystem.component.tapTarget
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.icon.LuminaIcon
import rocks.lumina.app.core.designsystem.theme.LocalLuminaColors
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.model.Profile
import rocks.lumina.app.core.ui.text.RichText
import rocks.lumina.app.core.ui.user.ProfileAvatar
import rocks.lumina.app.core.ui.user.UserName
import rocks.lumina.app.core.ui.user.rememberProfile
import rocks.lumina.app.core.ui.util.MessageEffect
import rocks.lumina.app.core.ui.util.formatTime

@Composable
fun ChatRoute(pubkey: String, onBack: () -> Unit, onOpenProfile: (String) -> Unit) {
    val vm = hiltViewModel<ChatViewModel, ChatViewModel.Factory>(key = "chat:$pubkey") { it.create(pubkey) }
    val state by vm.state.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    MessageEffect(vm.messages)
    // Mark read whenever the chat is (re)shown and whenever a new message arrives while visible.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.markRead() }
    LaunchedEffect(state.newestId) { if (state.newestId != null) vm.markRead() }
    ChatScreen(
        state = state,
        draft = draft,
        onBack = onBack,
        onOpenProfile = { onOpenProfile(state.peer) },
        onDraftChange = vm::onDraftChange,
        onSend = vm::send,
        onRetry = vm::retry,
        onLogin = vm::requestLogin,
    )
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    draft: String,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: (String) -> Unit,
    onLogin: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(LuminaTheme.colors.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ChatTopBar(peer = state.peer, onBack = onBack, onOpenProfile = onOpenProfile)
        ContentColumn(Modifier.weight(1f)) {
            when {
                !state.loggedIn -> EmptyState(
                    title = stringResource(R.string.messages_login_title),
                    message = stringResource(R.string.messages_login_message),
                    glyph = LucideGlyphs.Lock,
                    actionLabel = stringResource(R.string.messages_login_action),
                    onAction = onLogin,
                )
                state.showSkeleton -> ChatSkeleton()
                state.items.isEmpty() -> ChatEmpty(state.peer)
                else -> MessageList(state, onRetry)
            }
        }
        if (state.loggedIn) {
            Hairline()
            Composer(draft = draft, onDraftChange = onDraftChange, onSend = onSend)
        }
    }
}

@Composable
private fun ChatTopBar(peer: String, onBack: () -> Unit, onOpenProfile: () -> Unit) {
    val profile by rememberProfile(peer)
    val name = profile?.bestName ?: Profile.shortNpub(peer)
    LuminaTopBar(
        leading = {
            LuminaIconButton(
                glyph = LucideGlyphs.ArrowLeft,
                contentDescription = stringResource(R.string.messages_back),
                onClick = onBack,
            )
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .tapTarget(label = stringResource(R.string.messages_open_profile, name), onClick = onOpenProfile)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProfileAvatar(peer, LuminaDimens.avatarPost, profile = profile)
                UserName(peer, profile = profile, style = LuminaTheme.typography.title)
            }
        },
    )
}

@Composable
private fun MessageList(state: ChatUiState, onRetry: (String) -> Unit) {
    val listState = rememberLazyListState()
    // reverseLayout: index 0 is the newest message, so the list naturally sits at the bottom.
    val reversed = remember(state.items) { state.items.asReversed() }
    val newestOwn = (state.items.lastOrNull() as? ChatItem.Bubble)?.own == true
    LaunchedEffect(state.newestId) {
        if (listState.firstVisibleItemIndex <= 3 || newestOwn) listState.animateScrollToItem(0)
    }
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        items(reversed, key = { it.key }, contentType = { it::class }) { item ->
            when (item) {
                is ChatItem.Day -> DaySeparator(item.date)
                is ChatItem.Bubble -> MessageBubble(item, onRetry = { onRetry(item.message.id) })
            }
        }
    }
}

@Composable
private fun DaySeparator(date: LocalDate) {
    val today = LocalDate.now()
    val label = when (date) {
        today -> stringResource(R.string.messages_today)
        today.minusDays(1) -> stringResource(R.string.messages_yesterday)
        else -> remember(date) { date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
    }
    Text(
        text = label,
        style = LuminaTheme.typography.caption,
        color = LuminaTheme.colors.mutedForeground,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    )
}

private val BubbleShape = RoundedCornerShape(18.dp)

@Composable
private fun MessageBubble(item: ChatItem.Bubble, onRetry: () -> Unit) {
    val colors = LuminaTheme.colors
    val message = item.message
    val failed = message.state == SendState.FAILED
    val retryLabel = stringResource(R.string.messages_retry)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = if (item.groupedWithNext) 2.dp else 8.dp),
        horizontalAlignment = if (item.own) Alignment.End else Alignment.Start,
    ) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth(0.8f),
            contentAlignment = if (item.own) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            val bubbleModifier = Modifier
                .clip(BubbleShape)
                .background(if (item.own) colors.accent else colors.surfaceMuted)
                .then(if (failed) Modifier.tapTarget(label = retryLabel, onClick = onRetry) else Modifier)
                .padding(horizontal = 14.dp, vertical = 9.dp)
            Box(bubbleModifier) {
                if (item.own) {
                    // Own bubbles: on-primary text (links too) on the accent background.
                    CompositionLocalProvider(LocalLuminaColors provides colors.copy(foreground = colors.onPrimary, accent = colors.onPrimary)) {
                        RichText(message.content)
                    }
                } else {
                    RichText(message.content)
                }
            }
        }
        if (!item.groupedWithNext || message.state != SendState.SENT) {
            Row(
                Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (message.state) {
                    SendState.SENDING -> {
                        Spinner(size = 10.dp)
                        Meta(stringResource(R.string.messages_sending))
                    }
                    SendState.FAILED -> {
                        LuminaIcon(LucideGlyphs.CircleAlert, null, size = 12.dp, tint = colors.destructive)
                        Text(
                            stringResource(R.string.messages_failed),
                            style = LuminaTheme.typography.caption,
                            color = colors.destructive,
                            modifier = Modifier.tapTarget(label = retryLabel, onClick = onRetry),
                        )
                    }
                    SendState.SENT -> {
                        if (message.legacy) LegacyLabel()
                        Meta(formatTime(message.createdAt))
                    }
                }
            }
        }
    }
}

@Composable
private fun Meta(text: String) {
    Text(text, style = LuminaTheme.typography.caption, color = LuminaTheme.colors.mutedForeground)
}

@Composable
private fun Composer(draft: String, onDraftChange: (String) -> Unit, onSend: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(LuminaTheme.colors.surface), contentAlignment = Alignment.Center) {
        Row(
            Modifier.contentColumn().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LuminaTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = stringResource(R.string.messages_composer_hint),
                singleLine = false,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            val canSend = draft.isNotBlank()
            LuminaIconButton(
                glyph = LucideGlyphs.Send,
                contentDescription = stringResource(R.string.messages_send),
                onClick = onSend,
                enabled = canSend,
                tint = LuminaTheme.colors.accent,
            )
        }
    }
}

@Composable
private fun ChatEmpty(peer: String) {
    val profile by rememberProfile(peer)
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ProfileAvatar(peer, LuminaDimens.avatarProfile, profile = profile)
        Spacer(Modifier.height(12.dp))
        UserName(peer, profile = profile, style = LuminaTheme.typography.title)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.messages_chat_empty),
            style = LuminaTheme.typography.body,
            color = LuminaTheme.colors.mutedForeground,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChatSkeleton() {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Bottom) {
        listOf(0.55f to false, 0.4f to true, 0.7f to false, 0.35f to true, 0.5f to true).forEach { (fraction, own) ->
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = if (own) Alignment.CenterEnd else Alignment.CenterStart) {
                Skeleton(Modifier.fillMaxWidth(fraction).height(36.dp), shape = BubbleShape)
            }
        }
    }
}

// ---- Previews ----

private fun previewChat(): ChatUiState {
    val me = "a".repeat(64)
    val peer = "b".repeat(64)
    val now = System.currentTimeMillis() / 1000
    val messages = listOf(
        DmMessage("1", peer, peer, "Hey! Where was that mountain photo taken?", now - 90_000, legacy = false),
        DmMessage("2", peer, me, "Dolomites, last summer 🏔️", now - 89_000, legacy = false),
        DmMessage("3", peer, peer, "Wow. Adding it to my list https://example.com", now - 600, legacy = true),
        DmMessage("4", peer, me, "You should go in September", now - 120, legacy = false),
        DmMessage("5", peer, me, "Fewer tourists", now - 100, legacy = false, state = SendState.SENDING),
        DmMessage("6", peer, me, "And cheaper huts", now - 60, legacy = false, state = SendState.FAILED),
    )
    return ChatUiState(peer = peer, loading = false, items = ChatItems.build(messages, me), newestId = "6")
}

@Preview(name = "Chat light", showBackground = true)
@Composable
private fun ChatLightPreview() {
    LuminaTheme(dark = false) { ChatScreen(previewChat(), "", {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Chat dark", showBackground = true)
@Composable
private fun ChatDarkPreview() {
    LuminaTheme(dark = true) { ChatScreen(previewChat(), "Draft", {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Chat empty", showBackground = true)
@Composable
private fun ChatEmptyPreview() {
    LuminaTheme(dark = false) { ChatScreen(ChatUiState(peer = "b".repeat(64), loading = false), "", {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Chat loading dark", showBackground = true)
@Composable
private fun ChatLoadingPreview() {
    LuminaTheme(dark = true) { ChatScreen(ChatUiState(peer = "b".repeat(64)), "", {}, {}, {}, {}, {}, {}) }
}
