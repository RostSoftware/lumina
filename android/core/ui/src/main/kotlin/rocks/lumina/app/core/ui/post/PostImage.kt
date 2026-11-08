package rocks.lumina.app.core.ui.post

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rocks.lumina.app.core.designsystem.component.Skeleton
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.icon.LuminaIcon
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.media.Blurhash
import rocks.lumina.app.core.nostr.UrlSafety
import rocks.lumina.app.core.nostr.model.PostImage
import rocks.lumina.app.core.ui.R
import rocks.lumina.app.core.ui.util.uiEntryPoint

/**
 * One post image: blurhash (or skeleton) while loading, then the photo. On failure, tries the `imeta`
 * `fallback` URLs, then other Blossom servers by `x` hash, before showing "Image unavailable".
 */
@Composable
public fun PostImageView(
    image: PostImage,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    blurhashSize: Int = 32,
) {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    val candidates = remember(image) {
        val blossom = if (inPreview || image.sha256 == null) emptyList() else context.uiEntryPoint().blossom().fallbackUrls(image.sha256.orEmpty(), image.url)
        (listOf(image.url) + image.fallbacks + blossom).mapNotNull(UrlSafety::httpsOrNull).distinct()
    }
    var index by remember(image) { mutableIntStateOf(0) }
    var loaded by remember(image) { mutableStateOf(false) }
    val placeholder: ImageBitmap? by produceState<ImageBitmap?>(null, image.blurhash) {
        val hash = image.blurhash
        value = if (hash == null) null else withContext(Dispatchers.Default) { Blurhash.decode(hash, blurhashSize, blurhashSize)?.asImageBitmap() }
    }
    Box(modifier.background(LuminaTheme.colors.surfaceMuted), contentAlignment = Alignment.Center) {
        if (!loaded) {
            val ph = placeholder
            if (ph != null) {
                Image(ph, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Skeleton(Modifier.fillMaxSize(), shape = androidx.compose.ui.graphics.RectangleShape)
            }
        }
        if (index < candidates.size) {
            AsyncImage(
                model = candidates[index],
                contentDescription = contentDescription,
                contentScale = contentScale,
                onSuccess = { loaded = true },
                onError = { index += 1 },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LuminaIcon(LucideGlyphs.Image, null, tint = LuminaTheme.colors.mutedForeground)
                Text(
                    stringResource(R.string.post_image_unavailable),
                    style = LuminaTheme.typography.caption,
                    color = LuminaTheme.colors.mutedForeground,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
