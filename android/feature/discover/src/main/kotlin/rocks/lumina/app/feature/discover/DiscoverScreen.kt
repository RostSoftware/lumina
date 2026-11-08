package rocks.lumina.app.feature.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import rocks.lumina.app.core.designsystem.component.ContentColumn
import rocks.lumina.app.core.designsystem.component.EmptyState
import rocks.lumina.app.core.designsystem.component.ErrorState
import rocks.lumina.app.core.designsystem.component.Hairline
import rocks.lumina.app.core.designsystem.component.LoadingBox
import rocks.lumina.app.core.designsystem.component.LuminaChip
import rocks.lumina.app.core.designsystem.component.LuminaIconButton
import rocks.lumina.app.core.designsystem.component.LuminaTextField
import rocks.lumina.app.core.designsystem.component.LuminaTopBar
import rocks.lumina.app.core.designsystem.component.Skeleton
import rocks.lumina.app.core.designsystem.component.SettingsRow
import rocks.lumina.app.core.designsystem.component.contentColumn
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.model.PicturePost
import rocks.lumina.app.core.nostr.model.PostImage
import rocks.lumina.app.core.ui.post.NewPostsPill
import rocks.lumina.app.core.ui.post.PostActionsHost
import rocks.lumina.app.core.ui.post.PostActionsViewModel
import rocks.lumina.app.core.ui.post.PostGridCell
import rocks.lumina.app.core.ui.post.PostNavigation
import rocks.lumina.app.core.ui.user.UserRow

/** Discover tab: Instagram-Explore grid of global kind 20, account search and hashtag grids. */
@Composable
fun DiscoverRoute(initialHashtag: String?, nav: PostNavigation) {
    val vm: DiscoverViewModel = hiltViewModel()
    val actions: PostActionsViewModel = hiltViewModel()
    LaunchedEffect(vm, initialHashtag) { vm.start(initialHashtag) }
    val state by vm.state.collectAsStateWithLifecycle()
    val removed by actions.removed.collectAsStateWithLifecycle()
    val settings by actions.settings.collectAsStateWithLifecycle()
    val initial = DiscoverLogic.normalizeHashtag(initialHashtag)
    BackHandler(enabled = state.searching || (state.hashtag != null && state.hashtag != initial)) { vm.clear() }
    DiscoverScreen(
        state = state,
        removed = removed,
        revealSensitive = settings.showSensitive,
        onQueryChange = vm::onQueryChange,
        onSubmit = vm::onSubmit,
        onSearchTab = vm::onSearchTab,
        onRetrySearch = vm::retrySearch,
        onOpenHashtag = vm::openHashtag,
        onClear = vm::clear,
        onRefresh = vm::refresh,
        onRetry = vm::retry,
        onLoadMore = vm::loadMore,
        onShowPending = vm::showPending,
        onOpenPost = nav.openPost,
        onOpenProfile = nav.openProfile,
    )
    PostActionsHost(actions, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    state: DiscoverUiState,
    removed: Set<String>,
    revealSensitive: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSearchTab: (SearchTab) -> Unit,
    onRetrySearch: () -> Unit,
    onOpenHashtag: (String) -> Unit,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onShowPending: () -> Unit,
    onOpenPost: (PicturePost) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(LuminaTheme.colors.background)) {
        val hashtag = state.hashtag
        if (hashtag != null && !state.searching) {
            LuminaTopBar(title = stringResource(R.string.discover_hashtag_title, hashtag), onBack = onClear)
        } else {
            SearchBar(query = state.query, onQueryChange = onQueryChange, onSubmit = onSubmit, onClear = onClear)
        }
        if (state.searching) {
            SearchResults(state, onSearchTab, onRetrySearch, onOpenHashtag, onOpenProfile)
        } else {
            val grid = state.grid
            val visible = if (removed.isEmpty()) grid.posts else grid.posts.filterNot { it.id in removed }
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = grid.refreshing,
                onRefresh = onRefresh,
                state = pullState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = grid.refreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = LuminaTheme.colors.surface,
                        color = LuminaTheme.colors.foreground,
                    )
                },
            ) {
                ContentColumn {
                    when {
                        grid.loading && visible.isEmpty() -> SkeletonGrid()
                        grid.error && visible.isEmpty() -> ScrollableState {
                            ErrorState(
                                title = stringResource(R.string.discover_error_title),
                                message = stringResource(R.string.discover_error_message),
                                onRetry = onRetry,
                            )
                        }
                        visible.isEmpty() -> ScrollableState {
                            if (hashtag != null) {
                                EmptyState(
                                    title = stringResource(R.string.discover_hashtag_empty_title, hashtag),
                                    message = stringResource(R.string.discover_hashtag_empty_message),
                                    glyph = LucideGlyphs.Hash,
                                )
                            } else {
                                EmptyState(
                                    title = stringResource(R.string.discover_empty_title),
                                    message = stringResource(R.string.discover_empty_message),
                                    glyph = LucideGlyphs.Search,
                                )
                            }
                        }
                        else -> PostGrid(
                            posts = visible,
                            gridState = gridState,
                            revealSensitive = revealSensitive,
                            loadingMore = grid.loadingMore,
                            canLoadMore = grid.nextUntil != null,
                            onLoadMore = onLoadMore,
                            onOpenPost = onOpenPost,
                        )
                    }
                    NewPostsPill(
                        visible = grid.pending.isNotEmpty(),
                        authors = grid.pending.map { it.pubkey }.distinct(),
                        onClick = {
                            onShowPending()
                            scope.launch { gridState.animateScrollToItem(0) }
                        },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onSubmit: () -> Unit, onClear: () -> Unit) {
    val focus = LocalFocusManager.current
    val requester = androidx.compose.runtime.remember { FocusRequester() }
    val label = stringResource(R.string.discover_search_label)
    Column(
        Modifier
            .fillMaxWidth()
            .background(LuminaTheme.colors.surface)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier.contentColumn().height(LuminaDimens.barHeight).padding(start = LuminaDimens.gutter, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LuminaTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = stringResource(R.string.discover_search_placeholder),
                    leadingGlyph = LucideGlyphs.Search,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        onSubmit()
                        focus.clearFocus()
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(requester)
                        .semantics { contentDescription = label },
                )
                if (query.isNotEmpty()) {
                    LuminaIconButton(
                        glyph = LucideGlyphs.X,
                        contentDescription = stringResource(R.string.discover_clear_search),
                        onClick = {
                            onClear()
                            focus.clearFocus()
                        },
                    )
                } else {
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(end = 12.dp))
                }
            }
        }
        Hairline()
    }
}

@Composable
private fun SearchResults(
    state: DiscoverUiState,
    onSearchTab: (SearchTab) -> Unit,
    onRetrySearch: () -> Unit,
    onOpenHashtag: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    ContentColumn {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = LuminaDimens.gutter, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LuminaChip(stringResource(R.string.discover_tab_accounts), state.searchTab == SearchTab.Accounts, { onSearchTab(SearchTab.Accounts) })
                LuminaChip(stringResource(R.string.discover_tab_tags), state.searchTab == SearchTab.Tags, { onSearchTab(SearchTab.Tags) })
            }
            when (state.searchTab) {
                SearchTab.Accounts -> when (val accounts = state.accounts) {
                    AccountResults.Idle, AccountResults.Loading -> AccountSkeleton()
                    AccountResults.Error -> ErrorState(
                        title = stringResource(R.string.discover_search_failed),
                        message = stringResource(R.string.discover_search_failed_message),
                        onRetry = onRetrySearch,
                    )
                    is AccountResults.Content -> if (accounts.pubkeys.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.discover_no_accounts),
                            message = stringResource(R.string.discover_no_accounts_message),
                            glyph = LucideGlyphs.Users,
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(accounts.pubkeys, key = { it }) { pk -> UserRow(pk, onClick = { onOpenProfile(pk) }) }
                        }
                    }
                }
                SearchTab.Tags -> {
                    val tag = state.tagSuggestion
                    if (tag == null) {
                        EmptyState(
                            title = stringResource(R.string.discover_tags_hint),
                            message = stringResource(R.string.discover_tags_hint_message),
                            glyph = LucideGlyphs.Hash,
                        )
                    } else {
                        SettingsRow(
                            title = stringResource(R.string.discover_tag_row, tag),
                            subtitle = stringResource(R.string.discover_tag_row_subtitle),
                            glyph = LucideGlyphs.Hash,
                            onClick = { onOpenHashtag(tag) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountSkeleton() {
    Column {
        repeat(6) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = LuminaDimens.gutter, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rocks.lumina.app.core.designsystem.component.SkeletonCircle(LuminaDimens.avatarList)
                Column(Modifier.padding(start = 12.dp)) {
                    Skeleton(Modifier.fillMaxWidth(0.5f).height(12.dp))
                    Skeleton(Modifier.padding(top = 6.dp).fillMaxWidth(0.3f).height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun PostGrid(
    posts: List<PicturePost>,
    gridState: LazyGridState,
    revealSensitive: Boolean,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenPost: (PicturePost) -> Unit,
) {
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    val currentCanLoad by rememberUpdatedState(canLoadMore)
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            DiscoverLogic.shouldLoadMore(info.visibleItemsInfo.lastOrNull()?.index ?: 0, info.totalItemsCount)
        }.distinctUntilChanged().collect { near -> if (near && currentCanLoad) currentLoadMore() }
    }
    LaunchedEffect(posts.size, canLoadMore) {
        val info = gridState.layoutInfo
        if (canLoadMore && DiscoverLogic.shouldLoadMore(info.visibleItemsInfo.lastOrNull()?.index ?: 0, info.totalItemsCount)) onLoadMore()
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(LuminaDimens.gridGap),
        verticalArrangement = Arrangement.spacedBy(LuminaDimens.gridGap),
    ) {
        items(posts, key = { it.id }) { post ->
            PostGridCell(post = post, revealSensitive = revealSensitive, onClick = { onOpenPost(post) })
        }
        if (loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "loading-more") {
                val label = stringResource(R.string.discover_loading_more)
                LoadingBox(Modifier.semantics { contentDescription = label })
            }
        }
    }
}

@Composable
private fun SkeletonGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(LuminaDimens.gridGap),
        verticalArrangement = Arrangement.spacedBy(LuminaDimens.gridGap),
    ) {
        items(18) { Skeleton(Modifier.aspectRatio(1f), shape = RectangleShape) }
    }
}

/** Empty/error states inside the pull-to-refresh box must be scrollable for the gesture to work. */
@Composable
private fun ScrollableState(content: @Composable () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) { item { content() } }
}

// ---- previews --------------------------------------------------------------------------------

private fun previewPost(i: Int, sensitive: Boolean = false): PicturePost {
    val pk = "%064x".format(i + 1)
    val id = "%064x".format(1000 + i)
    return PicturePost(
        id = id,
        pubkey = pk,
        createdAt = 1_700_000_000L - i * 60,
        caption = "Sunset #$i",
        title = null,
        images = listOf(PostImage("https://example.com/$i.jpg", "image/jpeg", 1080, 1080, null, null, null, null, emptyList())),
        hashtags = emptyList(),
        taggedPubkeys = emptyList(),
        location = null,
        geohash = null,
        contentWarning = if (sensitive) "" else null,
        emoji = emptyMap(),
        event = Event(id, pk, 1_700_000_000L, 20, emptyArray(), "", ""),
    )
}

@Composable
private fun PreviewScreen(state: DiscoverUiState) {
    DiscoverScreen(
        state = state, removed = emptySet(), revealSensitive = false,
        onQueryChange = {}, onSubmit = {}, onSearchTab = {}, onRetrySearch = {}, onOpenHashtag = {}, onClear = {},
        onRefresh = {}, onRetry = {}, onLoadMore = {}, onShowPending = {}, onOpenPost = {}, onOpenProfile = {},
    )
}

private val previewGrid = DiscoverUiState(
    grid = GridState(posts = List(12) { previewPost(it, sensitive = it == 4) }, loading = false, pending = listOf(previewPost(99))),
)

@Preview(name = "Grid · light", showBackground = true)
@Composable
private fun DiscoverGridLightPreview() {
    LuminaTheme(dark = false) { PreviewScreen(previewGrid) }
}

@Preview(name = "Grid · dark", showBackground = true)
@Composable
private fun DiscoverGridDarkPreview() {
    LuminaTheme(dark = true) { PreviewScreen(previewGrid) }
}

@Preview(name = "Hashtag loading · light", showBackground = true)
@Composable
private fun DiscoverLoadingLightPreview() {
    LuminaTheme(dark = false) { PreviewScreen(DiscoverUiState(hashtag = "sunset")) }
}

@Preview(name = "Hashtag empty · dark", showBackground = true)
@Composable
private fun DiscoverEmptyDarkPreview() {
    LuminaTheme(dark = true) { PreviewScreen(DiscoverUiState(hashtag = "sunset", grid = GridState(loading = false))) }
}

@Preview(name = "Search · light", showBackground = true)
@Composable
private fun DiscoverSearchLightPreview() {
    LuminaTheme(dark = false) {
        PreviewScreen(DiscoverUiState(query = "alice", accounts = AccountResults.Content(listOf("%064x".format(1), "%064x".format(2)))))
    }
}

@Preview(name = "Search tags · dark", showBackground = true)
@Composable
private fun DiscoverSearchDarkPreview() {
    LuminaTheme(dark = true) { PreviewScreen(DiscoverUiState(query = "#sunset", searchTab = SearchTab.Tags)) }
}

@Preview(name = "Error · light", showBackground = true)
@Composable
private fun DiscoverErrorLightPreview() {
    LuminaTheme(dark = false) { PreviewScreen(DiscoverUiState(grid = GridState(loading = false, error = true))) }
}
