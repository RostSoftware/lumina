package rocks.lumina.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.JsonObject
import rocks.lumina.app.core.designsystem.component.ButtonStyle
import rocks.lumina.app.core.designsystem.component.ContentColumn
import rocks.lumina.app.core.designsystem.component.EmptyState
import rocks.lumina.app.core.designsystem.component.ErrorState
import rocks.lumina.app.core.designsystem.component.Hairline
import rocks.lumina.app.core.designsystem.component.LoadingBox
import rocks.lumina.app.core.designsystem.component.LuminaBottomSheet
import rocks.lumina.app.core.designsystem.component.LuminaButton
import rocks.lumina.app.core.designsystem.component.LuminaIconButton
import rocks.lumina.app.core.designsystem.component.LuminaTopBar
import rocks.lumina.app.core.designsystem.component.SheetAction
import rocks.lumina.app.core.designsystem.component.Skeleton
import rocks.lumina.app.core.designsystem.component.SkeletonCircle
import rocks.lumina.app.core.designsystem.component.tapTarget
import rocks.lumina.app.core.designsystem.icon.LucideGlyph
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.icon.LuminaIcon
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaShapes
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.NostrLink
import rocks.lumina.app.core.nostr.NostrLinks
import rocks.lumina.app.core.nostr.model.PicturePost
import rocks.lumina.app.core.nostr.model.PostImage
import rocks.lumina.app.core.nostr.model.Profile
import rocks.lumina.app.core.ui.post.PostActionsHost
import rocks.lumina.app.core.ui.post.PostActionsViewModel
import rocks.lumina.app.core.ui.post.PostGridCell
import rocks.lumina.app.core.ui.post.PostNavigation
import rocks.lumina.app.core.ui.sheet.ShareActions
import rocks.lumina.app.core.ui.text.RichText
import rocks.lumina.app.core.ui.text.RichTextActions
import rocks.lumina.app.core.ui.user.ProfileAvatar
import rocks.lumina.app.core.ui.user.UserName
import rocks.lumina.app.core.ui.util.MessageEffect
import rocks.lumina.app.core.ui.util.UiMessage

/** Profile screen. [pubkey] null = the active account (logged out → login prompt). */
@Composable
fun ProfileRoute(
    pubkey: String?,
    onBack: (() -> Unit)?,
    nav: PostNavigation,
    onOpenSettings: () -> Unit,
    onEditProfile: () -> Unit,
    onMessage: (String) -> Unit,
    onOpenFollows: (pubkey: String, followers: Boolean) -> Unit,
) {
    val vm: ProfileViewModel = hiltViewModel()
    val actions: PostActionsViewModel = hiltViewModel()
    LaunchedEffect(vm, pubkey) { vm.bind(pubkey) }
    val state by vm.state.collectAsStateWithLifecycle()
    val removed by actions.removed.collectAsStateWithLifecycle()
    val settings by actions.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    MessageEffect(vm.messages)
    ProfileScreen(
        state = state,
        removed = removed,
        revealSensitive = settings.showSensitive,
        onBack = onBack,
        callbacks = ProfileCallbacks(
            onOpenSettings = onOpenSettings,
            onEditProfile = onEditProfile,
            onShareProfile = { pk -> ShareActions.shareText(context, ShareActions.profileLink(pk)) },
            onCopyNpub = { pk ->
                ShareActions.copy(context, NostrLinks.npub(pk))
                actions.emit(UiMessage.Res(R.string.profile_npub_copied))
            },
            onToggleMute = { pk, name -> actions.toggleMute(pk, name) },
            onReport = { pk -> actions.openReport(pk, null) },
            onFollow = vm::toggleFollow,
            onMessage = { pk -> if (vm.canMessage()) onMessage(pk) },
            onZap = { pk -> actions.openZap(pk, null) },
            onOpenFollows = onOpenFollows,
            onSelectTab = vm::selectTab,
            onRetryTab = vm::retryTab,
            onRefresh = vm::refresh,
            onLoadMore = vm::loadMore,
            onLogin = vm::requestLogin,
            onOpenPost = nav.openPost,
            onMention = nav.openLink,
            onHashtag = nav.openHashtag,
        ),
    )
    PostActionsHost(actions, nav)
}

/** Every user action on the profile screen. */
class ProfileCallbacks(
    val onOpenSettings: () -> Unit = {},
    val onEditProfile: () -> Unit = {},
    val onShareProfile: (String) -> Unit = {},
    val onCopyNpub: (String) -> Unit = {},
    val onToggleMute: (String, String) -> Unit = { _, _ -> },
    val onReport: (String) -> Unit = {},
    val onFollow: () -> Unit = {},
    val onMessage: (String) -> Unit = {},
    val onZap: (String) -> Unit = {},
    val onOpenFollows: (String, Boolean) -> Unit = { _, _ -> },
    val onSelectTab: (ProfileTab) -> Unit = {},
    val onRetryTab: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onLogin: () -> Unit = {},
    val onOpenPost: (PicturePost) -> Unit = {},
    val onMention: (NostrLink) -> Unit = {},
    val onHashtag: (String) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    removed: Set<String>,
    revealSensitive: Boolean,
    onBack: (() -> Unit)?,
    callbacks: ProfileCallbacks,
) {
    val pk = state.pubkey
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val name = state.profile?.bestName ?: pk?.let { Profile.shortNpub(it) }.orEmpty()
    Column(Modifier.fillMaxSize().background(LuminaTheme.colors.background)) {
        when {
            state.loggedOut -> LuminaTopBar(title = stringResource(R.string.profile_title), onBack = onBack)
            pk == null -> LuminaTopBar(title = stringResource(R.string.profile_title), onBack = onBack)
            state.isOwn -> LuminaTopBar(
                title = state.profile?.handle ?: name,
                onBack = onBack,
                actions = {
                    LuminaIconButton(LucideGlyphs.Settings, stringResource(R.string.profile_settings), onClick = callbacks.onOpenSettings)
                },
            )
            else -> LuminaTopBar(
                title = name,
                onBack = onBack,
                actions = {
                    LuminaIconButton(LucideGlyphs.Ellipsis, stringResource(R.string.profile_more), onClick = { menuOpen = true })
                },
            )
        }
        when {
            state.loggedOut -> ContentColumn {
                EmptyState(
                    title = stringResource(R.string.profile_logged_out_title),
                    message = stringResource(R.string.profile_logged_out_message),
                    glyph = LucideGlyphs.User,
                    actionLabel = stringResource(R.string.profile_log_in),
                    onAction = callbacks.onLogin,
                )
            }
            pk == null -> ContentColumn {
                ErrorState(title = stringResource(R.string.profile_invalid_title), message = stringResource(R.string.profile_invalid_message))
            }
            else -> ProfileBody(state, pk, removed, revealSensitive, callbacks)
        }
    }
    if (menuOpen && pk != null) {
        LuminaBottomSheet(onDismiss = { menuOpen = false }) {
            SheetAction(stringResource(R.string.profile_copy_npub), LucideGlyphs.Copy, onClick = {
                menuOpen = false
                callbacks.onCopyNpub(pk)
            })
            SheetAction(stringResource(R.string.profile_share), LucideGlyphs.Share2, onClick = {
                menuOpen = false
                callbacks.onShareProfile(pk)
            })
            SheetAction(
                stringResource(if (state.muted) R.string.profile_unmute else R.string.profile_mute),
                LucideGlyphs.VolumeX,
                onClick = {
                    menuOpen = false
                    callbacks.onToggleMute(pk, name)
                },
            )
            SheetAction(stringResource(R.string.profile_report), LucideGlyphs.Flag, destructive = true, onClick = {
                menuOpen = false
                callbacks.onReport(pk)
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileBody(
    state: ProfileUiState,
    pk: String,
    removed: Set<String>,
    revealSensitive: Boolean,
    callbacks: ProfileCallbacks,
) {
    val gridState = rememberLazyGridState()
    val pullState = rememberPullToRefreshState()
    val tab = state.current
    val hidePosts = state.muted && !state.isOwn
    val posts = if (removed.isEmpty()) tab.posts else tab.posts.filterNot { it.id in removed }
    val loadMore by rememberUpdatedState(callbacks.onLoadMore)
    val canLoad by rememberUpdatedState(tab.nextUntil != null && !hidePosts)
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 9
        }.distinctUntilChanged().collect { near -> if (near && canLoad) loadMore() }
    }
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = callbacks.onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = state.refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = LuminaTheme.colors.surface,
                color = LuminaTheme.colors.foreground,
            )
        },
    ) {
        ContentColumn {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(LuminaDimens.gridGap),
                verticalArrangement = Arrangement.spacedBy(LuminaDimens.gridGap),
            ) {
                fullSpan("header") {
                    if (state.profile == null && state.profileLoading) {
                        HeaderSkeleton()
                    } else {
                        ProfileHeader(state, pk, callbacks)
                    }
                }
                if (hidePosts) {
                    fullSpan("muted") {
                        MutedBanner(onUnmute = { callbacks.onToggleMute(pk, state.profile?.bestName ?: Profile.shortNpub(pk)) })
                    }
                    return@LazyVerticalGrid
                }
                fullSpan("tabs") { TabsRow(state.tab, callbacks.onSelectTab) }
                tabContent(state, tab, posts, revealSensitive, callbacks)
            }
        }
    }
}

private fun LazyGridScope.fullSpan(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.tabContent(
    state: ProfileUiState,
    tab: TabState,
    posts: List<PicturePost>,
    revealSensitive: Boolean,
    callbacks: ProfileCallbacks,
) {
    when {
        tab.loading && posts.isEmpty() -> items(12, key = { "skeleton-$it" }) {
            Skeleton(Modifier.aspectRatio(1f), shape = RectangleShape)
        }
        tab.error && posts.isEmpty() -> fullSpan("error") {
            ErrorState(
                title = stringResource(R.string.profile_error_title),
                message = stringResource(R.string.profile_error_message),
                onRetry = callbacks.onRetryTab,
            )
        }
        posts.isEmpty() -> fullSpan("empty") {
            when (state.tab) {
                ProfileTab.Posts -> if (state.isOwn) {
                    EmptyState(
                        title = stringResource(R.string.profile_empty_own_title),
                        message = stringResource(R.string.profile_empty_own_message),
                        glyph = LucideGlyphs.Camera,
                    )
                } else {
                    EmptyState(title = stringResource(R.string.profile_empty_posts), glyph = LucideGlyphs.Camera)
                }
                ProfileTab.Reposts -> EmptyState(
                    title = stringResource(R.string.profile_empty_reposts),
                    message = stringResource(R.string.profile_empty_reposts_message),
                    glyph = LucideGlyphs.Repeat2,
                )
                ProfileTab.Tagged -> EmptyState(
                    title = stringResource(R.string.profile_empty_tagged),
                    message = stringResource(R.string.profile_empty_tagged_message),
                    glyph = LucideGlyphs.AtSign,
                )
            }
        }
        else -> {
            items(posts, key = { "${state.tab}-${it.id}" }) { post ->
                PostGridCell(post = post, revealSensitive = revealSensitive, onClick = { callbacks.onOpenPost(post) })
            }
            if (tab.loadingMore) fullSpan("more") { LoadingBox() }
        }
    }
}

@Composable
private fun ProfileHeader(state: ProfileUiState, pk: String, callbacks: ProfileCallbacks) {
    val profile = state.profile
    val colors = LuminaTheme.colors
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth()) {
        profile?.banner?.let { banner ->
            AsyncImage(
                model = banner,
                contentDescription = stringResource(R.string.profile_banner),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(96.dp).background(colors.surfaceMuted),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = LuminaDimens.gutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileAvatar(pk, LuminaDimens.avatarProfile, profile = profile, storyRing = true)
            Spacer(Modifier.width(16.dp))
            val postsLabel = state.posts.posts.size.let { n -> if (n == 0 && state.posts.loading) null else ProfileLogic.countLabel(n, state.posts.nextUntil != null) }
            Stat(postsLabel, stringResource(R.string.profile_posts), Modifier.weight(1f), onClick = null)
            Stat(
                state.followers?.let { ProfileLogic.countLabel(it, state.followersCapped) },
                stringResource(R.string.profile_followers),
                Modifier.weight(1f),
                onClick = { callbacks.onOpenFollows(pk, true) },
            )
            Stat(
                state.following?.let { ProfileLogic.countLabel(it, false) },
                stringResource(R.string.profile_following),
                Modifier.weight(1f),
                onClick = { callbacks.onOpenFollows(pk, false) },
            )
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = LuminaDimens.gutter), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            UserName(pk, profile = profile)
            profile?.nip05?.let { Text(it, style = LuminaTheme.typography.caption, color = colors.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            profile?.about?.takeIf { it.isNotBlank() }?.let { about ->
                RichText(
                    text = about,
                    actions = RichTextActions(onMention = callbacks.onMention, onHashtag = callbacks.onHashtag),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            profile?.website?.let { site ->
                Row(
                    Modifier
                        .heightIn(min = 32.dp)
                        .tapTarget(role = Role.Button, label = stringResource(R.string.profile_open_website, site), onClick = { runCatching { uriHandler.openUri(site) } }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LuminaIcon(LucideGlyphs.Link, null, size = 14.dp, tint = colors.accent)
                    Spacer(Modifier.width(4.dp))
                    Text(ProfileLogic.displayUrl(site), style = LuminaTheme.typography.username, color = colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            profile?.lightningAddress?.let { ln ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LuminaIcon(LucideGlyphs.Zap, stringResource(R.string.profile_lightning_address), size = 14.dp, tint = colors.zap)
                    Spacer(Modifier.width(4.dp))
                    Text(ln, style = LuminaTheme.typography.caption, color = colors.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = LuminaDimens.gutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.isOwn) {
                LuminaButton(stringResource(R.string.profile_edit), onClick = callbacks.onEditProfile, style = ButtonStyle.Secondary, modifier = Modifier.weight(1f))
                LuminaButton(stringResource(R.string.profile_share), onClick = { callbacks.onShareProfile(pk) }, style = ButtonStyle.Secondary, modifier = Modifier.weight(1f))
            } else {
                LuminaButton(
                    text = stringResource(if (state.isFollowing) R.string.profile_following_button else R.string.profile_follow),
                    onClick = callbacks.onFollow,
                    style = if (state.isFollowing) ButtonStyle.Secondary else ButtonStyle.Primary,
                    loading = state.followBusy,
                    modifier = Modifier.weight(1f),
                )
                LuminaButton(stringResource(R.string.profile_message), onClick = { callbacks.onMessage(pk) }, style = ButtonStyle.Secondary, modifier = Modifier.weight(1f))
                if (profile?.lightningAddress != null) {
                    LuminaButton(
                        stringResource(R.string.profile_zap),
                        onClick = { callbacks.onZap(pk) },
                        style = ButtonStyle.Secondary,
                        leadingIcon = LucideGlyphs.Zap,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String?, label: String, modifier: Modifier, onClick: (() -> Unit)?) {
    val description = "${value.orEmpty()} $label"
    Column(
        modifier
            .clip(LuminaShapes.control)
            .then(if (onClick != null) Modifier.tapTarget(label = description, onClick = onClick) else Modifier)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (value == null) {
            Skeleton(Modifier.width(28.dp).height(16.dp))
        } else {
            Text(value, style = LuminaTheme.typography.count, color = LuminaTheme.colors.foreground)
        }
        Text(label, style = LuminaTheme.typography.caption, color = LuminaTheme.colors.mutedForeground, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun TabsRow(selected: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = LuminaDimens.gridGap)) {
        Hairline()
        Row(Modifier.fillMaxWidth()) {
            ProfileTab.entries.forEach { tab ->
                val active = tab == selected
                val (glyph, label) = tabInfo(tab)
                Box(
                    Modifier
                        .weight(1f)
                        .height(LuminaDimens.touchTarget)
                        .tapTarget(role = Role.Tab, label = label, selected = active, onClick = { onSelect(tab) }),
                    contentAlignment = Alignment.Center,
                ) {
                    LuminaIcon(glyph, null, tint = if (active) LuminaTheme.colors.foreground else LuminaTheme.colors.mutedForeground)
                    if (active) {
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(LuminaTheme.colors.foreground))
                    }
                }
            }
        }
    }
}

@Composable
private fun tabInfo(tab: ProfileTab): Pair<LucideGlyph, String> = when (tab) {
    ProfileTab.Posts -> LucideGlyphs.Image to stringResource(R.string.profile_tab_posts)
    ProfileTab.Reposts -> LucideGlyphs.Repeat2 to stringResource(R.string.profile_tab_reposts)
    ProfileTab.Tagged -> LucideGlyphs.AtSign to stringResource(R.string.profile_tab_tagged)
}

@Composable
private fun MutedBanner(onUnmute: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(LuminaDimens.gutter)
            .clip(LuminaShapes.control)
            .background(LuminaTheme.colors.surfaceMuted)
            .padding(LuminaDimens.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LuminaIcon(LucideGlyphs.VolumeX, null, tint = LuminaTheme.colors.mutedForeground)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.profile_muted_banner), style = LuminaTheme.typography.title, color = LuminaTheme.colors.foreground, textAlign = TextAlign.Center)
        Text(stringResource(R.string.profile_muted_banner_message), style = LuminaTheme.typography.body, color = LuminaTheme.colors.mutedForeground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        LuminaButton(stringResource(R.string.profile_unmute), onClick = onUnmute, style = ButtonStyle.Outline)
    }
}

@Composable
private fun HeaderSkeleton() {
    Column(Modifier.fillMaxWidth().padding(LuminaDimens.gutter)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonCircle(LuminaDimens.avatarProfile)
            Spacer(Modifier.width(16.dp))
            repeat(3) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Skeleton(Modifier.width(28.dp).height(16.dp))
                    Spacer(Modifier.height(4.dp))
                    Skeleton(Modifier.width(48.dp).height(10.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Skeleton(Modifier.width(140.dp).height(14.dp))
        Spacer(Modifier.height(8.dp))
        Skeleton(Modifier.fillMaxWidth(0.9f).height(12.dp))
        Spacer(Modifier.height(6.dp))
        Skeleton(Modifier.fillMaxWidth(0.6f).height(12.dp))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Skeleton(Modifier.weight(1f).height(44.dp))
            Skeleton(Modifier.weight(1f).height(44.dp))
        }
    }
}

// ---- previews --------------------------------------------------------------------------------

internal fun previewPubkey(i: Int): String = "%064x".format(i)

internal fun previewPost(i: Int): PicturePost {
    val id = "%064x".format(1000 + i)
    return PicturePost(
        id = id, pubkey = previewPubkey(1), createdAt = 1_700_000_000L - i * 60, caption = "", title = null,
        images = listOf(PostImage("https://example.com/$i.jpg", "image/jpeg", 1080, 1080, null, null, null, null, emptyList())),
        hashtags = emptyList(), taggedPubkeys = emptyList(), location = null, geohash = null, contentWarning = null,
        emoji = emptyMap(), event = Event(id, previewPubkey(1), 1_700_000_000L, 20, emptyArray(), "", ""),
    )
}

internal val previewProfile = Profile(
    pubkey = previewPubkey(1), name = "alice", displayName = "Alice", about = "Photographer. Mountains, film and #nostr.",
    picture = null, banner = null, website = "https://alice.example", nip05 = "alice@example.com", lud16 = "alice@getalby.com",
    lud06 = null, createdAt = 0, raw = JsonObject(emptyMap()),
)

private val previewOther = ProfileUiState(
    pubkey = previewPubkey(1), profile = previewProfile, profileLoading = false, followers = 500, followersCapped = true,
    following = 213, posts = TabState(posts = List(9) { previewPost(it) }, nextUntil = 1, loaded = true),
)

@Preview(name = "Other · light", showBackground = true)
@Composable
private fun ProfileOtherLightPreview() {
    LuminaTheme(dark = false) { ProfileScreen(previewOther, emptySet(), false, onBack = {}, callbacks = ProfileCallbacks()) }
}

@Preview(name = "Own · dark", showBackground = true)
@Composable
private fun ProfileOwnDarkPreview() {
    LuminaTheme(dark = true) {
        ProfileScreen(previewOther.copy(isOwn = true, posts = TabState(loaded = true)), emptySet(), false, onBack = null, callbacks = ProfileCallbacks())
    }
}

@Preview(name = "Loading · light", showBackground = true)
@Composable
private fun ProfileLoadingLightPreview() {
    LuminaTheme(dark = false) { ProfileScreen(ProfileUiState(pubkey = previewPubkey(1)), emptySet(), false, onBack = {}, callbacks = ProfileCallbacks()) }
}

@Preview(name = "Muted · dark", showBackground = true)
@Composable
private fun ProfileMutedDarkPreview() {
    LuminaTheme(dark = true) { ProfileScreen(previewOther.copy(muted = true), emptySet(), false, onBack = {}, callbacks = ProfileCallbacks()) }
}

@Preview(name = "Logged out · light", showBackground = true)
@Composable
private fun ProfileLoggedOutLightPreview() {
    LuminaTheme(dark = false) { ProfileScreen(ProfileUiState(loggedOut = true), emptySet(), false, onBack = null, callbacks = ProfileCallbacks()) }
}

@Preview(name = "Tab error · dark", showBackground = true)
@Composable
private fun ProfileErrorDarkPreview() {
    LuminaTheme(dark = true) {
        ProfileScreen(previewOther.copy(tab = ProfileTab.Tagged, tagged = TabState(error = true)), emptySet(), false, onBack = {}, callbacks = ProfileCallbacks())
    }
}
