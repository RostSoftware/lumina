package rocks.lumina.app.core.ui.post

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import rocks.lumina.app.core.nostr.NostrLink
import rocks.lumina.app.core.nostr.model.PicturePost
import rocks.lumina.app.core.nostr.model.Profile
import rocks.lumina.app.core.ui.R
import rocks.lumina.app.core.ui.sheet.EngagementSheet
import rocks.lumina.app.core.ui.sheet.PostMenuSheet
import rocks.lumina.app.core.ui.sheet.RawEventSheet
import rocks.lumina.app.core.ui.sheet.ReportSheet
import rocks.lumina.app.core.ui.sheet.ShareActions
import rocks.lumina.app.core.ui.sheet.ZapSheet
import rocks.lumina.app.core.ui.util.MessageEffect
import rocks.lumina.app.core.ui.util.UiMessage
import rocks.lumina.app.core.ui.util.uiEntryPoint

/** Navigation hooks a post screen provides. */
public class PostNavigation(
    public val openProfile: (String) -> Unit,
    public val openPost: (PicturePost) -> Unit,
    public val openMedia: (PicturePost, Int) -> Unit,
    public val openHashtag: (String) -> Unit,
    public val openLink: (NostrLink) -> Unit,
)

/** Builds [PostCallbacks] backed by [vm] and [nav]. */
@Composable
public fun rememberPostCallbacks(vm: PostActionsViewModel, nav: PostNavigation): PostCallbacks {
    val context = LocalContext.current
    return PostCallbacks(
        onOpenProfile = nav.openProfile,
        onOpenPost = nav.openPost,
        onLike = vm::toggleLike,
        onDoubleTapLike = vm::doubleTapLike,
        onComment = nav.openPost,
        onRepost = vm::toggleRepost,
        onZap = { vm.openZap(it.pubkey, it) },
        onShare = { post -> ShareActions.shareText(context, ShareActions.postLink(post, vm.relayHint())) },
        onBookmark = vm::toggleBookmark,
        onMore = vm::openMenu,
        onOpenMedia = nav.openMedia,
        onHashtag = nav.openHashtag,
        onMention = nav.openLink,
        onShowEngagement = vm::openEngagement,
    )
}

private val prettyJson = Json { prettyPrint = true }

/** Renders the post sheets, snackbars and external-wallet launches for [vm]. Place once per screen. */
@Composable
public fun PostActionsHost(vm: PostActionsViewModel, nav: PostNavigation) {
    val context = LocalContext.current
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val mutes by vm.mutes.collectAsStateWithLifecycle()
    MessageEffect(vm.messages)
    LaunchedEffect(vm) {
        vm.externalInvoices.collect { invoice ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, "lightning:$invoice".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                vm.emit(UiMessage.Res(R.string.zap_open_wallet_failed))
            }
        }
    }
    when (val s = sheet) {
        null -> Unit
        is PostSheet.Menu -> {
            val post = s.post
            PostMenuSheet(
                post = post,
                isOwn = post.pubkey == vm.myPubkey,
                authorMuted = post.pubkey in mutes.pubkeys,
                onDismiss = vm::dismissSheet,
                onCopyLink = {
                    ShareActions.copy(context, ShareActions.postLink(post, vm.relayHint()))
                    vm.dismissSheet()
                    vm.emit(UiMessage.Res(R.string.post_link_copied))
                },
                onShare = {
                    vm.dismissSheet()
                    ShareActions.shareText(context, ShareActions.postLink(post, vm.relayHint()))
                },
                onViewRaw = { vm.openRaw(post) },
                onGoToProfile = {
                    vm.dismissSheet()
                    nav.openProfile(post.pubkey)
                },
                onToggleMute = {
                    val name = context.uiEntryPoint().profiles().peek(post.pubkey)?.bestName ?: Profile.shortNpub(post.pubkey)
                    vm.toggleMute(post.pubkey, name)
                },
                onReport = { vm.openReport(post.pubkey, post) },
                onDelete = { vm.delete(post) },
            )
        }
        is PostSheet.Zap -> ZapSheet(
            recipient = s.pubkey,
            defaultSats = settings.defaultZapSats,
            sending = busy,
            onDismiss = vm::dismissSheet,
            onZap = { sats, comment -> vm.zap(s.pubkey, s.post, sats, comment) },
        )
        is PostSheet.Report -> ReportSheet(
            sending = busy,
            onDismiss = vm::dismissSheet,
            onReport = { type, details -> vm.report(s.pubkey, s.post, type, details) },
        )
        is PostSheet.Raw -> {
            val json = runCatching { prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(s.post.event.toJson())) }
                .getOrDefault(s.post.event.toJson())
            RawEventSheet(json, onDismiss = vm::dismissSheet, onCopy = {
                ShareActions.copy(context, it)
                vm.emit(UiMessage.Res(R.string.copied))
            })
        }
        is PostSheet.EngagementList -> EngagementSheet(s.data, onDismiss = vm::dismissSheet, onOpenProfile = {
            vm.dismissSheet()
            nav.openProfile(it)
        })
    }
}
