package rocks.lumina.app.core.ui.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rocks.lumina.app.core.designsystem.component.Avatar
import rocks.lumina.app.core.designsystem.component.tapTarget
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.icon.LuminaIcon
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.model.Profile
import rocks.lumina.app.core.ui.R
import rocks.lumina.app.core.ui.util.uiEntryPoint

/** Observes a profile (loads it on first use). Returns null in previews or while loading. */
@Composable
public fun rememberProfile(pubkey: String): State<Profile?> {
    if (LocalInspectionMode.current) return remember { mutableStateOf<Profile?>(null) }
    val context = LocalContext.current
    val flow = remember(pubkey) { context.uiEntryPoint().profiles().observe(pubkey) }
    return flow.collectAsState()
}

/** True when the profile's NIP-05 resolves to its pubkey. */
@Composable
public fun rememberNip05Verified(profile: Profile?): Boolean {
    if (LocalInspectionMode.current) return profile?.nip05 != null
    val context = LocalContext.current
    val nip05 = profile?.nip05
    val verified by produceState(false, profile?.pubkey, nip05) {
        value = if (profile != null && nip05 != null) {
            runCatching { context.uiEntryPoint().profiles().verifyNip05(profile.pubkey, nip05) }.getOrDefault(false)
        } else {
            false
        }
    }
    return verified
}

@Composable
public fun ProfileAvatar(
    pubkey: String,
    size: Dp,
    modifier: Modifier = Modifier,
    profile: Profile? = null,
    ring: Boolean = false,
    storyRing: Boolean = false,
) {
    val observed by rememberProfile(pubkey)
    val p = profile ?: observed
    Avatar(
        url = p?.picture,
        seed = pubkey,
        contentDescription = stringResource(R.string.user_avatar_of, p?.bestName ?: Profile.shortNpub(pubkey)),
        size = size,
        initial = p?.bestName,
        ring = ring,
        storyRing = storyRing,
        modifier = modifier,
    )
}

/** Display name with an optional verified NIP-05 badge. */
@Composable
public fun UserName(
    pubkey: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LuminaTheme.typography.username,
    showBadge: Boolean = true,
    profile: Profile? = null,
) {
    val observed by rememberProfile(pubkey)
    val p = profile ?: observed
    val verified = showBadge && rememberNip05Verified(p)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = p?.bestName ?: Profile.shortNpub(pubkey),
            style = style,
            color = LuminaTheme.colors.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (verified) {
            Spacer(Modifier.width(4.dp))
            LuminaIcon(
                LucideGlyphs.BadgeCheck,
                contentDescription = stringResource(R.string.user_nip05_verified, p?.nip05.orEmpty()),
                size = 14.dp,
                tint = LuminaTheme.colors.accent,
            )
        }
    }
}

/** List row: avatar (44), name + subtitle, trailing content. */
@Composable
public fun UserRow(
    pubkey: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val profile by rememberProfile(pubkey)
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.tapTarget(label = profile?.bestName, onClick = onClick) else Modifier)
            .heightIn(min = 60.dp)
            .padding(horizontal = LuminaDimens.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileAvatar(pubkey, LuminaDimens.avatarList, profile = profile)
        Column(Modifier.weight(1f)) {
            UserName(pubkey, profile = profile)
            val sub = subtitle ?: profile?.nip05 ?: profile?.handle?.let { "@$it" }
            if (sub != null) {
                Text(sub, style = LuminaTheme.typography.caption, color = LuminaTheme.colors.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}
