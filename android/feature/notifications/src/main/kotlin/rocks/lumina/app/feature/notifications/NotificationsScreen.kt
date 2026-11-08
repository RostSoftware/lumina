package rocks.lumina.app.feature.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import rocks.lumina.app.core.data.NotificationGroup
import rocks.lumina.app.core.data.NotificationType
import rocks.lumina.app.core.designsystem.component.ContentColumn
import rocks.lumina.app.core.designsystem.component.EmptyState
import rocks.lumina.app.core.designsystem.component.ErrorState
import rocks.lumina.app.core.designsystem.component.LuminaButton
import rocks.lumina.app.core.designsystem.component.LuminaIconButton
import rocks.lumina.app.core.designsystem.component.LuminaTopBar
import rocks.lumina.app.core.designsystem.component.SectionHeader
import rocks.lumina.app.core.designsystem.component.Skeleton
import rocks.lumina.app.core.designsystem.component.SkeletonCircle
import rocks.lumina.app.core.designsystem.component.Spinner
import rocks.lumina.app.core.designsystem.component.tapTarget
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.icon.LuminaIcon
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaShapes
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.model.PicturePost
import rocks.lumina.app.core.nostr.model.Profile
import rocks.lumina.app.core.ui.post.PostImageView
import rocks.lumina.app.core.ui.post.PostNavigation
import rocks.lumina.app.core.ui.user.ProfileAvatar
import rocks.lumina.app.core.ui.user.rememberProfile
import rocks.lumina.app.core.ui.util.formatSats
import rocks.lumina.app.core.ui.util.relativeTime

@Composable
fun NotificationsRoute(nav: PostNavigation) {
    val vm: NotificationsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    NotificationsScreen(
        state = state,
        onRefresh = vm::refresh,
        onRetry = vm::retry,
        onLoadMore = vm::loadMore,
        onLogin = vm::requestLogin,
        onOpenProfile = nav.openProfile,
        onOpenPost = nav.openPost,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    state: NotificationsUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onLogin: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenPost: (PicturePost) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(LuminaTheme.colors.background)) {
        LuminaTopBar(title = stringResource(R.string.notifications_title))
        ContentColumn(Modifier.weight(1f)) {
            when (val content = state.content) {
                NotificationsContent.LoggedOut -> EmptyState(
                    title = stringResource(R.string.notifications_login_title),
                    message = stringResource(R.string.notifications_login_message),
                    glyph = LucideGlyphs.Heart,
                    actionLabel = stringResource(R.string.notifications_login_action),
                    onAction = onLogin,
                )
                NotificationsContent.Loading -> NotificationsSkeleton()
                NotificationsContent.Error -> ErrorState(
                    message = stringResource(R.string.notifications_error),
                    onRetry = onRetry,
                )
                NotificationsContent.Empty -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (state.systemNotificationsEnabled) item(key = "permission") { PermissionBanner() }
                        item(key = "empty") {
                            EmptyState(
                                title = stringResource(R.string.notifications_empty_title),
                                message = stringResource(R.string.notifications_empty_message),
                                glyph = LucideGlyphs.Heart,
                            )
                        }
                    }
                }
                is NotificationsContent.Loaded -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    NotificationList(state, content, onLoadMore, onOpenProfile, onOpenPost)
                }
            }
        }
    }
}

@Composable
private fun NotificationList(
    state: NotificationsUiState,
    content: NotificationsContent.Loaded,
    onLoadMore: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenPost: (PicturePost) -> Unit,
) {
    val listState = rememberLazyListState()
    val loadMore by rememberUpdatedState(onLoadMore)
    val canLoad = state.canLoadMore && !state.loadMoreFailed
    LaunchedEffect(listState, canLoad) {
        if (!canLoad) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount == 0 || last >= info.totalItemsCount - 6
        }.distinctUntilChanged().filter { it }.collect { loadMore() }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        if (state.systemNotificationsEnabled) item(key = "permission") { PermissionBanner() }
        section(R.string.notifications_section_new, content.newGroups, content.posts, onOpenProfile, onOpenPost)
        section(R.string.notifications_section_earlier, content.earlierGroups, content.posts, onOpenProfile, onOpenPost)
        item(key = "footer") {
            Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                when {
                    state.loadingMore -> Spinner()
                    state.loadMoreFailed -> LuminaButton(
                        text = stringResource(R.string.notifications_load_more_retry),
                        onClick = onLoadMore,
                        style = rocks.lumina.app.core.designsystem.component.ButtonStyle.Outline,
                        leadingIcon = LucideGlyphs.RefreshCw,
                    )
                    state.canLoadMore -> Spinner()
                    else -> Unit
                }
            }
        }
    }
}

private fun LazyListScope.section(
    title: Int,
    groups: List<NotificationGroup>,
    posts: Map<String, PicturePost>,
    onOpenProfile: (String) -> Unit,
    onOpenPost: (PicturePost) -> Unit,
) {
    if (groups.isEmpty()) return
    item(key = "header:$title") { SectionHeader(stringResource(title)) }
    items(groups, key = { it.key }) { group ->
        NotificationRow(group, posts[group.postId], onOpenProfile, onOpenPost)
    }
}

@Composable
private fun NotificationRow(
    group: NotificationGroup,
    post: PicturePost?,
    onOpenProfile: (String) -> Unit,
    onOpenPost: (PicturePost) -> Unit,
) {
    val first = group.actors.firstOrNull() ?: return
    val text = notificationText(group)
    val colors = LuminaTheme.colors
    val openRow = { if (post != null) onOpenPost(post) else onOpenProfile(first) }
    Row(
        Modifier
            .fillMaxWidth()
            .tapTarget(label = text.text, onClick = openRow)
            .heightIn(min = 64.dp)
            .padding(horizontal = LuminaDimens.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ActorAvatars(group.actors, onClick = { onOpenProfile(first) })
        Text(
            text = text,
            style = LuminaTheme.typography.body,
            color = colors.foreground,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (group.unread) {
            Box(Modifier.size(6.dp).background(colors.accent, CircleShape))
        }
        val image = post?.images?.firstOrNull()
        if (post != null && image != null) {
            PostImageView(
                image = image,
                contentDescription = image.alt ?: stringResource(R.string.notifications_post_thumbnail),
                modifier = Modifier
                    .size(LuminaDimens.avatarList)
                    .clip(LuminaShapes.control)
                    .tapTarget(label = stringResource(R.string.notifications_open_post), onClick = { onOpenPost(post) }),
            )
        }
    }
}

@Composable
private fun ActorAvatars(actors: List<String>, onClick: () -> Unit) {
    val first = actors.first()
    val profile by rememberProfile(first)
    val label = stringResource(R.string.notifications_open_profile, profile?.bestName ?: Profile.shortNpub(first))
    Box(
        Modifier
            .size(LuminaDimens.avatarList)
            .clip(CircleShape)
            .tapTarget(label = label, onClick = onClick),
    ) {
        if (actors.size == 1) {
            ProfileAvatar(first, LuminaDimens.avatarList, profile = profile)
        } else {
            ProfileAvatar(actors[1], 32.dp, modifier = Modifier.align(Alignment.TopEnd))
            ProfileAvatar(
                first,
                32.dp,
                profile = profile,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .border(2.dp, LuminaTheme.colors.background, CircleShape),
            )
        }
    }
}

/** "alice and 4 others liked your post. 2h" — names bold, time muted. */
@Composable
private fun notificationText(group: NotificationGroup): AnnotatedString {
    val firstName = actorName(group.actors[0])
    val secondName = group.actors.getOrNull(1)?.let { actorName(it) }
    val others = group.actors.size - 1
    val actors = when {
        others == 0 -> firstName
        others == 1 && secondName != null -> stringResource(R.string.notifications_actors_two, firstName, secondName)
        else -> pluralStringResource(R.plurals.notifications_actors_others, others, firstName, others)
    }
    val sentence = when (group.type) {
        NotificationType.LIKE -> stringResource(R.string.notifications_liked, actors)
        NotificationType.COMMENT -> stringResource(R.string.notifications_commented, actors, group.text.orEmpty().replace('\n', ' ').take(120))
        NotificationType.REPOST -> stringResource(R.string.notifications_reposted, actors)
        NotificationType.ZAP -> pluralStringResource(
            R.plurals.notifications_zapped,
            group.zapSats.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            actors,
            formatSats(group.zapSats),
        )
        NotificationType.MENTION -> stringResource(R.string.notifications_mentioned, actors)
        NotificationType.FOLLOW -> stringResource(R.string.notifications_followed, actors)
    }
    val time = relativeTime(group.createdAt)
    val muted = LuminaTheme.colors.mutedForeground
    return remember(sentence, time, firstName, secondName, muted) {
        buildAnnotatedString {
            append(sentence)
            listOfNotNull(firstName, secondName.takeIf { others == 1 }).forEach { name ->
                val start = sentence.indexOf(name)
                if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), start, start + name.length)
            }
            append(" ")
            withStyle(SpanStyle(color = muted)) { append(time) }
        }
    }
}

@Composable
private fun actorName(pubkey: String): String {
    val profile by rememberProfile(pubkey)
    return profile?.bestName ?: Profile.shortNpub(pubkey)
}

/** Asks for POST_NOTIFICATIONS (Android 13+) so the background worker can show system notifications. */
@Composable
private fun PermissionBanner() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || LocalInspectionMode.current) return
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        if (!result) dismissed = true
    }
    if (granted || dismissed) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = LuminaDimens.gutter, vertical = 8.dp)
            .clip(LuminaShapes.control)
            .background(LuminaTheme.colors.surfaceMuted)
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LuminaIcon(LucideGlyphs.Bell, null, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.notifications_permission_message),
            style = LuminaTheme.typography.body,
            color = LuminaTheme.colors.foreground,
            modifier = Modifier.weight(1f),
        )
        LuminaButton(
            text = stringResource(R.string.notifications_permission_action),
            onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            style = rocks.lumina.app.core.designsystem.component.ButtonStyle.Ghost,
        )
        LuminaIconButton(
            glyph = LucideGlyphs.X,
            contentDescription = stringResource(R.string.notifications_permission_dismiss),
            onClick = { dismissed = true },
            iconSize = 18.dp,
        )
    }
}

@Composable
private fun NotificationsSkeleton() {
    Column(Modifier.fillMaxWidth()) {
        Skeleton(Modifier.padding(start = LuminaDimens.gutter, top = 20.dp, bottom = 6.dp).width(60.dp).height(12.dp))
        repeat(9) { index ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = LuminaDimens.gutter, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkeletonCircle(LuminaDimens.avatarList)
                Column(Modifier.weight(1f)) {
                    Skeleton(Modifier.fillMaxWidth(0.9f).height(12.dp))
                    Spacer(Modifier.height(6.dp))
                    Skeleton(Modifier.fillMaxWidth(0.5f).height(12.dp))
                }
                if (index % 3 != 2) Skeleton(Modifier.size(LuminaDimens.avatarList))
            }
        }
    }
}

// ---- Previews ----

private fun previewState(): NotificationsUiState {
    val now = System.currentTimeMillis() / 1000
    val a = "a".repeat(64)
    val b = "b".repeat(64)
    val c = "c".repeat(64)
    fun group(key: String, type: NotificationType, actors: List<String>, ago: Long, unread: Boolean, text: String? = null, sats: Long = 0) =
        NotificationGroup(key, type, actors, now - ago, postId = null, text = text, zapSats = sats, unread = unread)
    return NotificationsUiState(
        content = NotificationsContent.Loaded(
            newGroups = listOf(
                group("1", NotificationType.LIKE, listOf(a, b, c, "d".repeat(64), "e".repeat(64)), 300, true),
                group("2", NotificationType.ZAP, listOf(b), 1_200, true, sats = 1_000),
            ),
            earlierGroups = listOf(
                group("3", NotificationType.COMMENT, listOf(c), 90_000, false, text = "What a view! Where is this?"),
                group("4", NotificationType.FOLLOW, listOf(a, b), 200_000, false),
                group("5", NotificationType.MENTION, listOf(b), 400_000, false),
                group("6", NotificationType.REPOST, listOf(a), 900_000, false),
            ),
            posts = emptyMap(),
        ),
        canLoadMore = false,
    )
}

@Preview(name = "Notifications light", showBackground = true)
@Composable
private fun NotificationsLightPreview() {
    LuminaTheme(dark = false) { NotificationsScreen(previewState(), {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Notifications dark", showBackground = true)
@Composable
private fun NotificationsDarkPreview() {
    LuminaTheme(dark = true) { NotificationsScreen(previewState(), {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Notifications loading", showBackground = true)
@Composable
private fun NotificationsLoadingPreview() {
    LuminaTheme(dark = false) { NotificationsScreen(NotificationsUiState(), {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Notifications empty dark", showBackground = true)
@Composable
private fun NotificationsEmptyPreview() {
    LuminaTheme(dark = true) { NotificationsScreen(NotificationsUiState(content = NotificationsContent.Empty), {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Notifications logged out", showBackground = true)
@Composable
private fun NotificationsLoggedOutPreview() {
    LuminaTheme(dark = false) { NotificationsScreen(NotificationsUiState(content = NotificationsContent.LoggedOut), {}, {}, {}, {}, {}, {}) }
}
