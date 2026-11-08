package rocks.lumina.app.feature.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import rocks.lumina.app.core.data.PostStats
import rocks.lumina.app.core.designsystem.component.ContentColumn
import rocks.lumina.app.core.designsystem.component.EmptyState
import rocks.lumina.app.core.designsystem.component.ErrorState
import rocks.lumina.app.core.designsystem.component.LoadingBox
import rocks.lumina.app.core.designsystem.component.LuminaIconButton
import rocks.lumina.app.core.designsystem.component.LuminaTopBar
import rocks.lumina.app.core.designsystem.component.Wordmark
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.model.PicturePost
import rocks.lumina.app.core.ui.post.NewPostsPill
import rocks.lumina.app.core.ui.post.PostActionsHost
import rocks.lumina.app.core.ui.post.PostActionsViewModel
import rocks.lumina.app.core.ui.post.PostCallbacks
import rocks.lumina.app.core.ui.post.PostCard
import rocks.lumina.app.core.ui.post.PostCardSkeleton
import rocks.lumina.app.core.ui.post.PostNavigation
import rocks.lumina.app.core.ui.post.rememberPostCallbacks

@Composable
fun HomeRoute(
    nav: PostNavigation,
    onOpenMessages: () -> Unit,
    onOpenDiscover: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    viewModel: HomeViewModel = hiltViewModel(),
    actions: PostActionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats by actions.stats.collectAsStateWithLifecycle()
    val bookmarks by actions.bookmarks.collectAsStateWithLifecycle()
    val removed by actions.removed.collectAsStateWithLifecycle()
    val settings by actions.settings.collectAsStateWithLifecycle()
    LaunchedEffect(state.posts) { actions.loadStats(state.posts) }
    HomeScreen(
        state = state.copy(posts = state.posts.filterNot { it.id in removed }),
        stats = stats,
        bookmarks = bookmarks,
        revealSensitive = settings.showSensitive,
        callbacks = rememberPostCallbacks(actions, nav),
        listState = listState,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onShowPending = viewModel::showPending,
        onOpenMessages = onOpenMessages,
        onOpenDiscover = onOpenDiscover,
        onLogin = viewModel::requestLogin,
    )
    PostActionsHost(actions, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    stats: Map<String, PostStats>,
    bookmarks: Set<String>,
    revealSensitive: Boolean,
    callbacks: PostCallbacks,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onShowPending: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenDiscover: () -> Unit,
    onLogin: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        LuminaTopBar(
            leading = { Wordmark(Modifier.padding(start = 12.dp)) },
            actions = {
                LuminaIconButton(
                    LucideGlyphs.Send,
                    stringResource(R.string.feed_messages),
                    onClick = onOpenMessages,
                    badge = state.hasUnreadMessages,
                )
            },
        )
        ContentColumn {
            when {
                state.loading -> Column { repeat(2) { PostCardSkeleton() } }
                state.error -> ErrorState(title = stringResource(R.string.feed_error), onRetry = onRetry)
                state.empty != null -> FeedEmptyState(state.empty, onOpenDiscover, onLogin)
                else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    val nearEnd by remember {
                        derivedStateOf {
                            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= listState.layoutInfo.totalItemsCount - 3
                        }
                    }
                    LaunchedEffect(nearEnd, state.posts.size) { if (nearEnd) onLoadMore() }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(state.posts, key = { it.id }) { post ->
                            PostCard(
                                post = post,
                                stats = stats[post.id],
                                bookmarked = post.id in bookmarks,
                                revealSensitive = revealSensitive,
                                callbacks = callbacks,
                            )
                        }
                        item(key = "footer") {
                            when {
                                state.loadingMore -> LoadingBox()
                                state.endReached && state.posts.isNotEmpty() -> Text(
                                    stringResource(R.string.feed_end),
                                    style = LuminaTheme.typography.caption,
                                    color = LuminaTheme.colors.mutedForeground,
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        NewPostsPill(
                            visible = state.pendingNew.isNotEmpty(),
                            authors = state.pendingNew.map { it.pubkey }.distinct(),
                            onClick = {
                                onShowPending()
                                scope.launch { listState.animateScrollToItem(0) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedEmptyState(empty: FeedEmpty, onOpenDiscover: () -> Unit, onLogin: () -> Unit) {
    when (empty) {
        FeedEmpty.LOGGED_OUT -> Column {
            EmptyState(
                title = stringResource(R.string.feed_empty_logged_out_title),
                message = stringResource(R.string.feed_empty_logged_out_message),
                glyph = LucideGlyphs.Sparkles,
                actionLabel = stringResource(R.string.feed_log_in),
                onAction = onLogin,
            )
            rocks.lumina.app.core.designsystem.component.LuminaButton(
                stringResource(R.string.feed_go_discover),
                onClick = onOpenDiscover,
                style = rocks.lumina.app.core.designsystem.component.ButtonStyle.Ghost,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        FeedEmpty.NO_FOLLOWS -> EmptyState(
            title = stringResource(R.string.feed_empty_no_follows_title),
            message = stringResource(R.string.feed_empty_no_follows_message),
            glyph = LucideGlyphs.UserPlus,
            actionLabel = stringResource(R.string.feed_go_discover),
            onAction = onOpenDiscover,
        )
        FeedEmpty.NO_POSTS -> EmptyState(
            title = stringResource(R.string.feed_empty_no_posts_title),
            message = stringResource(R.string.feed_empty_no_posts_message),
            glyph = LucideGlyphs.Image,
            actionLabel = stringResource(R.string.feed_go_discover),
            onAction = onOpenDiscover,
        )
    }
}

@Composable
private fun PreviewHome(dark: Boolean, state: HomeUiState) {
    LuminaTheme(dark = dark) {
        HomeScreen(
            state = state,
            stats = emptyMap(),
            bookmarks = emptySet(),
            revealSensitive = false,
            callbacks = PostCallbacks(),
            listState = rememberLazyListState(),
            onRefresh = {}, onRetry = {}, onLoadMore = {}, onShowPending = {}, onOpenMessages = {}, onOpenDiscover = {}, onLogin = {},
        )
    }
}

@Preview(name = "Home loading – light")
@Composable
private fun HomeLoadingLight() = PreviewHome(false, HomeUiState(loading = true))

@Preview(name = "Home loading – dark")
@Composable
private fun HomeLoadingDark() = PreviewHome(true, HomeUiState(loading = true))

@Preview(name = "Home empty – light")
@Composable
private fun HomeEmptyLight() = PreviewHome(false, HomeUiState(loading = false, empty = FeedEmpty.NO_FOLLOWS))

@Preview(name = "Home empty – dark")
@Composable
private fun HomeEmptyDark() = PreviewHome(true, HomeUiState(loading = false, empty = FeedEmpty.LOGGED_OUT, hasUnreadMessages = true))

