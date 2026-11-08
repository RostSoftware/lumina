package rocks.lumina.app.core.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.lumina.app.core.data.Engagement
import rocks.lumina.app.core.data.ReportType
import rocks.lumina.app.core.designsystem.component.ButtonStyle
import rocks.lumina.app.core.designsystem.component.EmptyState
import rocks.lumina.app.core.designsystem.component.LoadingBox
import rocks.lumina.app.core.designsystem.component.LuminaBottomSheet
import rocks.lumina.app.core.designsystem.component.LuminaButton
import rocks.lumina.app.core.designsystem.component.LuminaChip
import rocks.lumina.app.core.designsystem.component.LuminaTextField
import rocks.lumina.app.core.designsystem.component.SheetAction
import rocks.lumina.app.core.designsystem.component.tapTarget
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.icon.LuminaIcon
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaShapes
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.RelayDefaults
import rocks.lumina.app.core.nostr.model.PicturePost
import rocks.lumina.app.core.nostr.model.Profile
import rocks.lumina.app.core.ui.R
import rocks.lumina.app.core.ui.user.UserRow
import rocks.lumina.app.core.ui.user.rememberProfile
import rocks.lumina.app.core.ui.util.formatSats

/** Zap amount sheet with presets, custom amount and an optional comment. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun ZapSheet(
    recipient: String,
    defaultSats: Long,
    sending: Boolean,
    onDismiss: () -> Unit,
    onZap: (sats: Long, comment: String) -> Unit,
) {
    val profile by rememberProfile(recipient)
    var selected by remember { mutableStateOf(defaultSats) }
    var custom by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    val amount = custom.toLongOrNull()?.takeIf { it > 0 } ?: selected
    LuminaBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.zap_title, profile?.bestName ?: Profile.shortNpub(recipient))) {
        Column(Modifier.padding(horizontal = LuminaDimens.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RelayDefaults.zapPresets.forEach { preset ->
                    LuminaChip(
                        text = "⚡ " + formatSats(preset),
                        selected = custom.isBlank() && selected == preset,
                        onClick = {
                            selected = preset
                            custom = ""
                        },
                    )
                }
            }
            LuminaTextField(
                value = custom,
                onValueChange = { v -> custom = v.filter(Char::isDigit).take(9) },
                placeholder = stringResource(R.string.zap_custom),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            LuminaTextField(
                value = comment,
                onValueChange = { comment = it.take(280) },
                placeholder = stringResource(R.string.zap_comment),
                modifier = Modifier.fillMaxWidth(),
            )
            LuminaButton(
                text = stringResource(R.string.zap_send, formatSats(amount)),
                onClick = { onZap(amount, comment) },
                loading = sending,
                leadingIcon = LucideGlyphs.Zap,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "…" menu for a post. */
@Composable
public fun PostMenuSheet(
    post: PicturePost,
    isOwn: Boolean,
    authorMuted: Boolean,
    onDismiss: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
    onViewRaw: () -> Unit,
    onGoToProfile: () -> Unit,
    onToggleMute: () -> Unit,
    onReport: () -> Unit,
    onDelete: () -> Unit,
) {
    val profile by rememberProfile(post.pubkey)
    val name = profile?.bestName ?: Profile.shortNpub(post.pubkey)
    var confirmDelete by remember { mutableStateOf(false) }
    LuminaBottomSheet(onDismiss = onDismiss) {
        SheetAction(stringResource(R.string.menu_copy_link), LucideGlyphs.Link, onCopyLink)
        SheetAction(stringResource(R.string.post_share), LucideGlyphs.Share2, onShare)
        SheetAction(stringResource(R.string.menu_go_to_profile), LucideGlyphs.User, onGoToProfile)
        SheetAction(stringResource(R.string.menu_view_raw), LucideGlyphs.Code, onViewRaw)
        if (!isOwn) {
            SheetAction(
                stringResource(if (authorMuted) R.string.menu_unmute else R.string.menu_mute, name),
                LucideGlyphs.VolumeX,
                onToggleMute,
            )
            SheetAction(stringResource(R.string.menu_report), LucideGlyphs.Flag, onReport, destructive = true)
        } else {
            SheetAction(stringResource(R.string.menu_delete), LucideGlyphs.Trash2, { confirmDelete = true }, destructive = true)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.menu_delete_confirm), style = LuminaTheme.typography.body) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.menu_delete), color = LuminaTheme.colors.destructive) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.menu_cancel), color = LuminaTheme.colors.foreground) } },
            containerColor = LuminaTheme.colors.surface,
        )
    }
}

/** NIP-56 report sheet. */
@Composable
public fun ReportSheet(sending: Boolean, onDismiss: () -> Unit, onReport: (ReportType, String) -> Unit) {
    var type by remember { mutableStateOf<ReportType?>(null) }
    var details by remember { mutableStateOf("") }
    LuminaBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.report_title)) {
        Text(
            stringResource(R.string.report_why),
            style = LuminaTheme.typography.body,
            color = LuminaTheme.colors.mutedForeground,
            modifier = Modifier.padding(horizontal = LuminaDimens.gutter),
        )
        Column(Modifier.verticalScroll(rememberScrollState())) {
            ReportType.entries.forEach { t ->
                val label = stringResource(
                    when (t) {
                        ReportType.SPAM -> R.string.report_spam
                        ReportType.NUDITY -> R.string.report_nudity
                        ReportType.PROFANITY -> R.string.report_profanity
                        ReportType.ILLEGAL -> R.string.report_illegal
                        ReportType.IMPERSONATION -> R.string.report_impersonation
                        ReportType.MALWARE -> R.string.report_malware
                        ReportType.OTHER -> R.string.report_other
                    },
                )
                Row(
                    Modifier.fillMaxWidth().tapTarget(role = androidx.compose.ui.semantics.Role.RadioButton, selected = type == t, label = label, onClick = { type = t })
                        .heightIn(min = 48.dp).padding(horizontal = LuminaDimens.gutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = LuminaTheme.typography.body, color = LuminaTheme.colors.foreground, modifier = Modifier.weight(1f))
                    if (type == t) LuminaIcon(LucideGlyphs.Check, null, tint = LuminaTheme.colors.accent)
                }
            }
            LuminaTextField(
                value = details,
                onValueChange = { details = it.take(500) },
                placeholder = stringResource(R.string.report_details),
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(horizontal = LuminaDimens.gutter, vertical = 8.dp),
            )
            LuminaButton(
                text = stringResource(R.string.report_send),
                onClick = { type?.let { onReport(it, details) } },
                enabled = type != null,
                loading = sending,
                style = ButtonStyle.Destructive,
                modifier = Modifier.fillMaxWidth().padding(horizontal = LuminaDimens.gutter),
            )
        }
    }
}

/** Pretty-printed raw event JSON with copy. */
@Composable
public fun RawEventSheet(json: String, onDismiss: () -> Unit, onCopy: (String) -> Unit) {
    LuminaBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.raw_event), skipPartiallyExpanded = false) {
        Box(
            Modifier
                .padding(horizontal = LuminaDimens.gutter)
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .background(LuminaTheme.colors.surfaceMuted, LuminaShapes.control)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Text(json, style = LuminaTheme.typography.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = LuminaTheme.colors.foreground)
        }
        LuminaButton(
            stringResource(R.string.copy),
            onClick = { onCopy(json) },
            style = ButtonStyle.Secondary,
            leadingIcon = LucideGlyphs.Copy,
            modifier = Modifier.fillMaxWidth().padding(LuminaDimens.gutter),
        )
    }
}

/** Likes / reposts / zaps lists. */
@Composable
public fun EngagementSheet(engagement: Engagement?, onDismiss: () -> Unit, onOpenProfile: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    LuminaBottomSheet(onDismiss = onDismiss, skipPartiallyExpanded = false) {
        Row(Modifier.padding(horizontal = LuminaDimens.gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LuminaChip(stringResource(R.string.eng_likes) + engagement?.reactions?.size?.let { " $it" }.orEmpty(), tab == 0, { tab = 0 })
            LuminaChip(stringResource(R.string.eng_reposts) + engagement?.reposters?.size?.let { " $it" }.orEmpty(), tab == 1, { tab = 1 })
            LuminaChip(stringResource(R.string.eng_zaps) + engagement?.zaps?.size?.let { " $it" }.orEmpty(), tab == 2, { tab = 2 })
        }
        Spacer(Modifier.padding(4.dp))
        if (engagement == null) {
            LoadingBox()
            return@LuminaBottomSheet
        }
        val rows: List<Pair<String, String?>> = when (tab) {
            0 -> engagement.reactions.map { it.pubkey to it.content.takeIf { c -> c != "+" && c.isNotBlank() && !c.startsWith(":") } }
            1 -> engagement.reposters.map { it.first to null }
            else -> engagement.zaps.filter { it.sender != null }.map { it.sender.orEmpty() to "⚡ ${formatSats(it.amountSats)}" + if (it.comment.isNotBlank()) " · ${it.comment}" else "" }
        }
        if (rows.isEmpty()) {
            EmptyState(stringResource(R.string.eng_empty))
        } else {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(rows, key = { it.first + (it.second ?: "") }) { (pk, sub) ->
                    UserRow(pk, subtitle = sub, onClick = { onOpenProfile(pk) })
                }
            }
        }
    }
}

