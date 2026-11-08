package rocks.lumina.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rocks.lumina.app.core.designsystem.component.ButtonStyle
import rocks.lumina.app.core.designsystem.component.ContentColumn
import rocks.lumina.app.core.designsystem.component.EmptyState
import rocks.lumina.app.core.designsystem.component.ErrorState
import rocks.lumina.app.core.designsystem.component.LuminaButton
import rocks.lumina.app.core.designsystem.component.LuminaTopBar
import rocks.lumina.app.core.designsystem.component.Skeleton
import rocks.lumina.app.core.designsystem.component.SkeletonCircle
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.ui.user.UserRow
import rocks.lumina.app.core.ui.util.MessageEffect

@Composable
fun FollowListRoute(pubkey: String, followers: Boolean, onBack: () -> Unit, onOpenProfile: (String) -> Unit) {
    val vm: FollowListViewModel = hiltViewModel()
    LaunchedEffect(vm, pubkey, followers) { vm.bind(pubkey, followers) }
    val state by vm.state.collectAsStateWithLifecycle()
    MessageEffect(vm.messages)
    FollowListScreen(state, onBack, onOpenProfile, onToggleFollow = vm::toggleFollow, onRetry = vm::load)
}

@Composable
fun FollowListScreen(
    state: FollowListUiState,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onToggleFollow: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(LuminaTheme.colors.background)) {
        LuminaTopBar(
            title = stringResource(if (state.followers) R.string.profile_followers_title else R.string.profile_following_title),
            onBack = onBack,
        )
        ContentColumn {
            when (val content = state.content) {
                FollowListContent.Loading -> ListSkeleton()
                FollowListContent.Error -> ErrorState(
                    title = stringResource(R.string.profile_follows_error),
                    message = stringResource(R.string.profile_error_message),
                    onRetry = onRetry,
                )
                is FollowListContent.Loaded -> if (content.pubkeys.isEmpty()) {
                    EmptyState(
                        title = stringResource(if (state.followers) R.string.profile_no_followers else R.string.profile_no_following),
                        glyph = LucideGlyphs.Users,
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (content.capped) {
                            item(key = "capped") {
                                Text(
                                    stringResource(R.string.profile_followers_capped),
                                    style = LuminaTheme.typography.caption,
                                    color = LuminaTheme.colors.mutedForeground,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = LuminaDimens.gutter, vertical = 8.dp),
                                )
                            }
                        }
                        items(content.pubkeys, key = { it }) { pk ->
                            UserRow(pk, onClick = { onOpenProfile(pk) }) {
                                if (pk != state.myPubkey) {
                                    val following = pk in state.following
                                    LuminaButton(
                                        text = stringResource(if (following) R.string.profile_following_button else R.string.profile_follow),
                                        onClick = { onToggleFollow(pk) },
                                        style = if (following) ButtonStyle.Secondary else ButtonStyle.Primary,
                                        loading = pk in state.busy,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListSkeleton() {
    Column {
        repeat(8) {
            Row(Modifier.fillMaxWidth().padding(horizontal = LuminaDimens.gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SkeletonCircle(LuminaDimens.avatarList)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Skeleton(Modifier.fillMaxWidth(0.5f).height(12.dp))
                    Spacer(Modifier.height(6.dp))
                    Skeleton(Modifier.fillMaxWidth(0.3f).height(10.dp))
                }
                Skeleton(Modifier.width(88.dp).height(36.dp))
            }
        }
    }
}

private val previewFollows = FollowListUiState(
    followers = true,
    content = FollowListContent.Loaded(List(5) { previewPubkey(it + 2) }, capped = true),
    myPubkey = previewPubkey(2),
    following = setOf(previewPubkey(3)),
    busy = setOf(previewPubkey(4)),
)

@Preview(name = "Followers · light", showBackground = true)
@Composable
private fun FollowListLightPreview() {
    LuminaTheme(dark = false) { FollowListScreen(previewFollows, {}, {}, {}, {}) }
}

@Preview(name = "Following · dark", showBackground = true)
@Composable
private fun FollowListDarkPreview() {
    LuminaTheme(dark = true) { FollowListScreen(previewFollows.copy(followers = false), {}, {}, {}, {}) }
}

@Preview(name = "Loading · light", showBackground = true)
@Composable
private fun FollowListLoadingPreview() {
    LuminaTheme(dark = false) { FollowListScreen(FollowListUiState(), {}, {}, {}, {}) }
}

@Preview(name = "Empty · dark", showBackground = true)
@Composable
private fun FollowListEmptyPreview() {
    LuminaTheme(dark = true) { FollowListScreen(FollowListUiState(content = FollowListContent.Loaded(emptyList(), false)), {}, {}, {}, {}) }
}
