package rocks.lumina.app.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rocks.lumina.app.core.data.Conversation
import rocks.lumina.app.core.data.DmMessage
import rocks.lumina.app.core.designsystem.component.ContentColumn
import rocks.lumina.app.core.designsystem.component.EmptyState
import rocks.lumina.app.core.designsystem.component.LuminaBottomSheet
import rocks.lumina.app.core.designsystem.component.LuminaIconButton
import rocks.lumina.app.core.designsystem.component.LuminaTextField
import rocks.lumina.app.core.designsystem.component.LuminaTopBar
import rocks.lumina.app.core.designsystem.component.Skeleton
import rocks.lumina.app.core.designsystem.component.SkeletonCircle
import rocks.lumina.app.core.designsystem.component.Spinner
import rocks.lumina.app.core.designsystem.component.UnreadDot
import rocks.lumina.app.core.designsystem.component.tapTarget
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaShapes
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.model.Profile
import rocks.lumina.app.core.ui.user.ProfileAvatar
import rocks.lumina.app.core.ui.user.UserName
import rocks.lumina.app.core.ui.user.UserRow
import rocks.lumina.app.core.ui.user.rememberProfile
import rocks.lumina.app.core.ui.util.relativeTime

@Composable
fun ConversationsRoute(onBack: () -> Unit, onOpenChat: (pubkey: String) -> Unit) {
    val vm: ConversationsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    ConversationsScreen(
        state = state,
        onBack = onBack,
        onOpenChat = onOpenChat,
        onLogin = vm::requestLogin,
        onNewMessage = vm::openSearch,
    )
    if (search.open) {
        NewMessageSheet(
            state = search,
            onQueryChange = vm::onQueryChange,
            onDismiss = vm::closeSearch,
            onPick = { pubkey ->
                vm.closeSearch()
                onOpenChat(pubkey)
            },
        )
    }
}

@Composable
fun ConversationsScreen(
    state: ConversationsUiState,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onLogin: () -> Unit,
    onNewMessage: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(LuminaTheme.colors.background)) {
        LuminaTopBar(
            title = stringResource(R.string.messages_title),
            onBack = onBack,
            actions = {
                if (state.loggedIn) {
                    LuminaIconButton(
                        glyph = LucideGlyphs.Pencil,
                        contentDescription = stringResource(R.string.messages_new),
                        onClick = onNewMessage,
                    )
                }
            },
        )
        ContentColumn {
            when {
                !state.loggedIn -> EmptyState(
                    title = stringResource(R.string.messages_login_title),
                    message = stringResource(R.string.messages_login_message),
                    glyph = LucideGlyphs.Lock,
                    actionLabel = stringResource(R.string.messages_login_action),
                    onAction = onLogin,
                )
                state.showSkeleton -> ConversationsSkeleton()
                state.isEmpty -> EmptyState(
                    title = stringResource(R.string.messages_empty_title),
                    message = stringResource(R.string.messages_empty_message),
                    glyph = LucideGlyphs.Send,
                    actionLabel = stringResource(R.string.messages_new),
                    onAction = onNewMessage,
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.conversations, key = { it.peer }) { conversation ->
                        ConversationRow(conversation, me = state.me, onClick = { onOpenChat(conversation.peer) })
                    }
                    if (state.loading) {
                        item(key = "loading") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Spinner() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, me: String?, onClick: () -> Unit) {
    val profile by rememberProfile(conversation.peer)
    val name = profile?.bestName ?: Profile.shortNpub(conversation.peer)
    val last = conversation.last
    val preview = if (last.sender == me) stringResource(R.string.messages_you_prefix, last.content) else last.content
    val rowLabel = stringResource(R.string.messages_open_chat_with, name)
    val colors = LuminaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .tapTarget(label = rowLabel, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = LuminaDimens.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileAvatar(conversation.peer, LuminaDimens.avatarList, profile = profile)
        Column(Modifier.weight(1f)) {
            UserName(conversation.peer, profile = profile)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = preview.replace('\n', ' '),
                    style = if (conversation.unread) LuminaTheme.typography.username else LuminaTheme.typography.body,
                    color = if (conversation.unread) colors.foreground else colors.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = " · " + relativeTime(last.createdAt),
                    style = LuminaTheme.typography.body,
                    color = colors.mutedForeground,
                    maxLines = 1,
                )
            }
            if (last.legacy) LegacyLabel(Modifier.padding(top = 2.dp))
        }
        if (conversation.unread) {
            UnreadDot()
        }
    }
}

@Composable
internal fun LegacyLabel(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.messages_legacy),
        style = LuminaTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
        color = LuminaTheme.colors.mutedForeground,
        modifier = modifier
            .background(LuminaTheme.colors.surfaceMuted, LuminaShapes.pill)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun ConversationsSkeleton() {
    val label = stringResource(R.string.messages_loading)
    Column(Modifier.fillMaxWidth().tapTarget(label = label, enabled = false, onClick = {})) {
        repeat(8) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = LuminaDimens.gutter, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonCircle(LuminaDimens.avatarList)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Skeleton(Modifier.width(120.dp).height(14.dp))
                    Spacer(Modifier.height(6.dp))
                    Skeleton(Modifier.fillMaxWidth(0.7f).height(12.dp))
                }
            }
        }
    }
}

@Composable
internal fun NewMessageSheet(
    state: UserSearchState,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LuminaBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.messages_new)) {
        LuminaTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.messages_search_hint),
            leadingGlyph = LucideGlyphs.Search,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            trailing = if (state.searching) {
                { Spinner(size = 18.dp) }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LuminaDimens.gutter, vertical = 8.dp)
                .focusRequester(focus),
        )
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Box(Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 480.dp)) {
            when {
                state.failed -> Text(
                    stringResource(R.string.messages_search_failed),
                    style = LuminaTheme.typography.body,
                    color = LuminaTheme.colors.mutedForeground,
                    modifier = Modifier.padding(LuminaDimens.gutter),
                )
                state.query.isNotBlank() && !state.searching && state.results.isEmpty() -> Text(
                    stringResource(R.string.messages_search_no_results),
                    style = LuminaTheme.typography.body,
                    color = LuminaTheme.colors.mutedForeground,
                    modifier = Modifier.padding(LuminaDimens.gutter),
                )
                else -> LazyColumn(Modifier.fillMaxWidth()) {
                    items(state.results, key = { it.pubkey }) { profile ->
                        UserRow(profile.pubkey, onClick = { onPick(profile.pubkey) })
                    }
                }
            }
        }
    }
}

// ---- Previews ----

private val PREVIEW_ME = "a".repeat(64)
private val previewPeers = listOf("b".repeat(64), "c".repeat(64), "d".repeat(64))

private fun previewState(): ConversationsUiState {
    val now = System.currentTimeMillis() / 1000
    return ConversationsUiState(
        loggedIn = true,
        me = PREVIEW_ME,
        loading = false,
        conversations = listOf(
            Conversation(previewPeers[0], DmMessage("1", previewPeers[0], previewPeers[0], "Loved your sunset shots!", now - 120, legacy = false), unread = true),
            Conversation(previewPeers[1], DmMessage("2", previewPeers[1], PREVIEW_ME, "See you tomorrow", now - 7_200, legacy = false), unread = false),
            Conversation(previewPeers[2], DmMessage("3", previewPeers[2], previewPeers[2], "gm", now - 200_000, legacy = true), unread = false),
        ),
    )
}

@Preview(name = "Conversations light", showBackground = true)
@Composable
private fun ConversationsLightPreview() {
    LuminaTheme(dark = false) { ConversationsScreen(previewState(), {}, {}, {}, {}) }
}

@Preview(name = "Conversations dark", showBackground = true)
@Composable
private fun ConversationsDarkPreview() {
    LuminaTheme(dark = true) { ConversationsScreen(previewState(), {}, {}, {}, {}) }
}

@Preview(name = "Conversations loading", showBackground = true)
@Composable
private fun ConversationsLoadingPreview() {
    LuminaTheme(dark = false) { ConversationsScreen(ConversationsUiState(loading = true), {}, {}, {}, {}) }
}

@Preview(name = "Conversations logged out dark", showBackground = true)
@Composable
private fun ConversationsLoggedOutPreview() {
    LuminaTheme(dark = true) { ConversationsScreen(ConversationsUiState(loggedIn = false, loading = false), {}, {}, {}, {}) }
}
