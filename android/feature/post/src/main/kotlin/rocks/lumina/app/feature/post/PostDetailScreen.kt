package rocks.lumina.app.feature.post

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rocks.lumina.app.core.data.PostStats
import rocks.lumina.app.core.designsystem.component.ContentColumn
import rocks.lumina.app.core.designsystem.component.EmptyState
import rocks.lumina.app.core.designsystem.component.ErrorState
import rocks.lumina.app.core.designsystem.component.Hairline
import rocks.lumina.app.core.designsystem.component.LuminaIconButton
import rocks.lumina.app.core.designsystem.component.LuminaTopBar
import rocks.lumina.app.core.designsystem.component.Skeleton
import rocks.lumina.app.core.designsystem.component.SkeletonCircle
import rocks.lumina.app.core.designsystem.component.Spinner
import rocks.lumina.app.core.designsystem.component.tapTarget
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaShapes
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.model.Comment
import rocks.lumina.app.core.nostr.model.Profile
import rocks.lumina.app.core.ui.post.PostActionsHost
import rocks.lumina.app.core.ui.post.PostActionsViewModel
import rocks.lumina.app.core.ui.post.PostCallbacks
import rocks.lumina.app.core.ui.post.PostCard
import rocks.lumina.app.core.ui.post.PostCardSkeleton
import rocks.lumina.app.core.ui.post.PostNavigation
import rocks.lumina.app.core.ui.post.rememberPostCallbacks
import rocks.lumina.app.core.ui.text.RichText
import rocks.lumina.app.core.ui.text.RichTextActions
import rocks.lumina.app.core.ui.user.ProfileAvatar
import rocks.lumina.app.core.ui.user.UserName
import rocks.lumina.app.core.ui.user.UserRow
import rocks.lumina.app.core.ui.user.rememberProfile
import rocks.lumina.app.core.ui.util.MessageEffect
import rocks.lumina.app.core.ui.util.relativeTime

@Composable
fun PostDetailRoute(
    postId: String,
    relays: List<String>,
    author: String?,
    focusComment: Boolean,
    onBack: () -> Unit,
    nav: PostNavigation,
    viewModel: PostDetailViewModel = hiltViewModel(),
    actions: PostActionsViewModel = hiltViewModel(),
) {
    LaunchedEffect(postId) { viewModel.load(postId, relays, author) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats by actions.stats.collectAsStateWithLifecycle()
    val bookmarks by actions.bookmarks.collectAsStateWithLifecycle()
    val settings by actions.settings.collectAsStateWithLifecycle()
    val removed by actions.removed.collectAsStateWithLifecycle()
    LaunchedEffect(state.post) { state.post?.let { actions.loadStats(listOf(it), force = true) } }
    LaunchedEffect(viewModel) { viewModel.commented.collect { actions.onCommented(it) } }
    LaunchedEffect(removed) { if (postId in removed) onBack() }
    MessageEffect(viewModel.messages)
    var focus by rememberSaveable { mutableStateOf(focusComment) }
    val baseCallbacks = rememberPostCallbacks(actions, nav)
    val callbacks = remember(baseCallbacks) {
        PostCallbacks(
            onOpenProfile = baseCallbacks.onOpenProfile, onOpenPost = {}, onLike = baseCallbacks.onLike,
            onDoubleTapLike = baseCallbacks.onDoubleTapLike, onComment = { focus = true }, onRepost = baseCallbacks.onRepost,
            onZap = baseCallbacks.onZap, onShare = baseCallbacks.onShare, onBookmark = baseCallbacks.onBookmark,
            onMore = baseCallbacks.onMore, onOpenMedia = baseCallbacks.onOpenMedia, onHashtag = baseCallbacks.onHashtag,
            onMention = baseCallbacks.onMention, onShowEngagement = baseCallbacks.onShowEngagement,
        )
    }
    PostDetailScreen(
        state = state,
        stats = state.post?.let { stats[it.id] },
        bookmarked = state.post?.id in bookmarks,
        revealSensitive = settings.showSensitive,
        callbacks = callbacks,
        requestFocus = focus,
        onFocusHandled = { focus = false },
        onBack = onBack,
        onReply = viewModel::replyTo,
        onComposerChanged = viewModel::onComposerChanged,
        mentionFor = viewModel::mentionFor,
        onSend = viewModel::send,
        onDeleteComment = viewModel::deleteComment,
        onRetryComments = viewModel::retryComments,
        richActions = RichTextActions(onMention = nav.openLink, onHashtag = nav.openHashtag),
    )
    PostActionsHost(actions, nav)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostDetailScreen(
    state: PostDetailUiState,
    stats: PostStats?,
    bookmarked: Boolean,
    revealSensitive: Boolean,
    callbacks: PostCallbacks,
    requestFocus: Boolean,
    onFocusHandled: () -> Unit,
    onBack: () -> Unit,
    onReply: (Comment?) -> Unit,
    onComposerChanged: (String, Int) -> Unit,
    mentionFor: (Profile) -> String,
    onSend: (String, () -> Unit) -> Unit,
    onDeleteComment: (Comment) -> Unit,
    onRetryComments: () -> Unit,
    richActions: RichTextActions,
) {
    var toDelete by remember { mutableStateOf<Comment?>(null) }
    Column(Modifier.fillMaxSize().imePadding()) {
        LuminaTopBar(title = stringResource(R.string.post_title), onBack = onBack)
        ContentColumn(Modifier.weight(1f)) {
            when {
                state.loading -> Column { PostCardSkeleton() }
                state.notFound || state.post == null -> EmptyState(
                    title = stringResource(R.string.post_not_found),
                    message = stringResource(R.string.post_not_found_message),
                    glyph = LucideGlyphs.Image,
                )
                else -> {
                    val post = state.post
                    LazyColumn(state = rememberLazyListState(), modifier = Modifier.fillMaxSize()) {
                        item(key = "post") {
                            PostCard(post, stats, bookmarked, revealSensitive, callbacks, expandCaption = true, showCommentsLink = false)
                            Hairline()
                        }
                        when {
                            state.commentsLoading && state.threads.isEmpty() -> items(3) { CommentSkeleton() }
                            state.commentsError -> item { ErrorState(title = stringResource(R.string.post_comment_failed), onRetry = onRetryComments) }
                            state.threads.isEmpty() -> item {
                                EmptyState(stringResource(R.string.post_no_comments), message = stringResource(R.string.post_no_comments_message), glyph = LucideGlyphs.MessageCircle)
                            }
                            else -> state.threads.forEach { thread ->
                                item(key = thread.comment.id) {
                                    CommentRow(thread.comment, false, state.myPubkey, callbacks.onOpenProfile, onReply, { toDelete = it }, richActions)
                                }
                                items(thread.replies, key = { it.id }) { reply ->
                                    CommentRow(reply, true, state.myPubkey, callbacks.onOpenProfile, onReply, { toDelete = it }, richActions)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (state.post != null) {
            Composer(state, requestFocus, onFocusHandled, onReply, onComposerChanged, mentionFor, onSend)
        }
    }
    toDelete?.let { comment ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            text = { Text(stringResource(R.string.post_delete_comment_confirm), style = LuminaTheme.typography.body) },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    onDeleteComment(comment)
                }) { Text(stringResource(R.string.post_delete_comment), color = LuminaTheme.colors.destructive) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.post_cancel), color = LuminaTheme.colors.foreground) } },
            containerColor = LuminaTheme.colors.surface,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentRow(
    comment: Comment,
    isReply: Boolean,
    myPubkey: String?,
    onOpenProfile: (String) -> Unit,
    onReply: (Comment) -> Unit,
    onDelete: (Comment) -> Unit,
    richActions: RichTextActions,
) {
    val own = comment.pubkey == myPubkey
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = if (own) ({ onDelete(comment) }) else null)
            .padding(start = if (isReply) 56.dp else LuminaDimens.gutter, end = LuminaDimens.gutter, top = 10.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ProfileAvatar(comment.pubkey, if (isReply) 24.dp else LuminaDimens.avatarPost, modifier = Modifier.tapTarget(label = null, onClick = { onOpenProfile(comment.pubkey) }))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserName(comment.pubkey, style = LuminaTheme.typography.username, modifier = Modifier.weight(1f, fill = false).tapTarget(label = null, onClick = { onOpenProfile(comment.pubkey) }))
                Spacer(Modifier.width(8.dp))
                Text(relativeTime(comment.createdAt), style = LuminaTheme.typography.caption, color = LuminaTheme.colors.mutedForeground)
            }
            RichText(comment.content, emoji = comment.emoji, actions = richActions)
            Row {
                Text(
                    stringResource(R.string.post_reply),
                    style = LuminaTheme.typography.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = LuminaTheme.colors.mutedForeground,
                    modifier = Modifier.tapTarget(label = null, onClick = { onReply(comment) }).padding(vertical = 6.dp, horizontal = 2.dp),
                )
                if (own) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.post_delete_comment),
                        style = LuminaTheme.typography.caption,
                        color = LuminaTheme.colors.mutedForeground,
                        modifier = Modifier.tapTarget(label = null, onClick = { onDelete(comment) }).padding(vertical = 6.dp, horizontal = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentSkeleton() {
    Row(Modifier.fillMaxWidth().padding(LuminaDimens.gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SkeletonCircle(LuminaDimens.avatarPost)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Skeleton(Modifier.width(100.dp).height(10.dp))
            Skeleton(Modifier.fillMaxWidth(0.8f).height(12.dp))
        }
    }
}

@Composable
private fun Composer(
    state: PostDetailUiState,
    requestFocus: Boolean,
    onFocusHandled: () -> Unit,
    onReply: (Comment?) -> Unit,
    onComposerChanged: (String, Int) -> Unit,
    mentionFor: (Profile) -> String,
    onSend: (String, () -> Unit) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestFocus, state.replyingTo) {
        if (requestFocus || state.replyingTo != null) {
            runCatching { focusRequester.requestFocus() }
            onFocusHandled()
        }
    }
    Column(Modifier.fillMaxWidth().background(LuminaTheme.colors.surface).navigationBarsPadding()) {
        Hairline()
        if (state.suggestions.isNotEmpty()) {
            state.suggestions.forEach { profile ->
                UserRow(profile.pubkey, onClick = {
                    val (text, cursor) = replaceMention(value.text, value.selection.end, mentionFor(profile))
                    value = TextFieldValue(text, TextRange(cursor))
                    onComposerChanged(text, cursor)
                })
            }
            Hairline()
        }
        val replying = state.replyingTo
        if (replying != null) {
            val profile by rememberProfile(replying.pubkey)
            Row(
                Modifier.fillMaxWidth().background(LuminaTheme.colors.surfaceMuted).padding(start = LuminaDimens.gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.post_replying_to, profile?.bestName ?: Profile.shortNpub(replying.pubkey)),
                    style = LuminaTheme.typography.caption,
                    color = LuminaTheme.colors.mutedForeground,
                    modifier = Modifier.weight(1f),
                )
                LuminaIconButton(LucideGlyphs.X, stringResource(R.string.post_cancel_reply), onClick = { onReply(null) }, iconSize = 16.dp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val placeholder = if (!state.loggedIn) {
                stringResource(R.string.post_log_in_to_comment)
            } else if (replying != null) {
                val profile by rememberProfile(replying.pubkey)
                stringResource(R.string.post_reply_to, profile?.bestName ?: Profile.shortNpub(replying.pubkey))
            } else {
                stringResource(R.string.post_add_comment)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = {
                    value = it
                    onComposerChanged(it.text, it.selection.end)
                },
                textStyle = LuminaTheme.typography.body.copy(color = LuminaTheme.colors.foreground),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(LuminaTheme.colors.accent),
                maxLines = 5,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .background(LuminaTheme.colors.surfaceMuted, LuminaShapes.pill)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (value.text.isEmpty()) Text(placeholder, style = LuminaTheme.typography.body, color = LuminaTheme.colors.mutedForeground)
                    inner()
                },
            )
            if (state.sending) {
                Spinner(Modifier.padding(12.dp))
            } else {
                LuminaIconButton(
                    LucideGlyphs.Send,
                    stringResource(R.string.post_send),
                    onClick = { onSend(value.text) { value = TextFieldValue("") } },
                    enabled = value.text.isNotBlank(),
                    tint = LuminaTheme.colors.accent,
                )
            }
        }
    }
}

@Composable
private fun PreviewDetail(dark: Boolean, state: PostDetailUiState) {
    LuminaTheme(dark = dark) {
        PostDetailScreen(
            state = state, stats = null, bookmarked = false, revealSensitive = false, callbacks = PostCallbacks(),
            requestFocus = false, onFocusHandled = {}, onBack = {}, onReply = {}, onComposerChanged = { _, _ -> },
            mentionFor = { "" }, onSend = { _, _ -> }, onDeleteComment = {}, onRetryComments = {}, richActions = RichTextActions(),
        )
    }
}

@Preview(name = "Post loading – light")
@Composable
private fun DetailLoadingLight() = PreviewDetail(false, PostDetailUiState())

@Preview(name = "Post loading – dark")
@Composable
private fun DetailLoadingDark() = PreviewDetail(true, PostDetailUiState())

@Preview(name = "Post not found – light")
@Composable
private fun DetailMissingLight() = PreviewDetail(false, PostDetailUiState(loading = false, notFound = true))

@Preview(name = "Post not found – dark")
@Composable
private fun DetailMissingDark() = PreviewDetail(true, PostDetailUiState(loading = false, notFound = true))

