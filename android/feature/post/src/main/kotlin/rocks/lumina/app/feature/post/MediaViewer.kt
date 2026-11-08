package rocks.lumina.app.feature.post

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import rocks.lumina.app.core.data.FeedRepository
import rocks.lumina.app.core.designsystem.component.LoadingBox
import rocks.lumina.app.core.designsystem.component.LuminaIconButton
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.model.PicturePost
import rocks.lumina.app.core.ui.post.Dots
import rocks.lumina.app.core.ui.post.PostImageView

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface MediaViewerEntryPoint {
    fun feed(): FeedRepository
}

/** Full-screen photo viewer with pinch-zoom, pan, double-tap zoom and swipe between images. */
@Composable
fun MediaViewerRoute(postId: String, index: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val post by produceState<PicturePost?>(null, postId) {
        val feed = EntryPointAccessors.fromApplication(context.applicationContext, MediaViewerEntryPoint::class.java).feed()
        value = feed.cachedPost(postId) ?: runCatching { feed.post(postId) }.getOrNull()
    }
    MediaViewerScreen(post, index, onBack)
}

@Composable
fun MediaViewerScreen(post: PicturePost?, index: Int, onBack: () -> Unit) {
    val title = stringResource(R.string.post_viewer_title)
    Box(Modifier.fillMaxSize().background(Color.Black).semantics { contentDescription = title }) {
        if (post == null) {
            LoadingBox(Modifier.align(Alignment.Center))
        } else {
            val pager = rememberPagerState(initialPage = index.coerceIn(0, post.images.lastIndex)) { post.images.size }
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                ZoomableImage(post, page)
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                post.images[pager.currentPage].alt?.let {
                    Text(it, style = LuminaTheme.typography.body, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (post.images.size > 1) Dots(post.images.size, pager.currentPage, Modifier.fillMaxWidth())
            }
        }
        LuminaIconButton(
            LucideGlyphs.X,
            stringResource(R.string.post_close),
            onClick = onBack,
            tint = Color.White,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp),
        )
    }
}

@Composable
private fun ZoomableImage(post: PicturePost, page: Int) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(page) {
        scale = 1f
        offset = Offset.Zero
    }
    val image = post.images[page]
    PostImageView(
        image = image,
        contentDescription = image.alt ?: "",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(page) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            }
            .pointerInput(page) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
