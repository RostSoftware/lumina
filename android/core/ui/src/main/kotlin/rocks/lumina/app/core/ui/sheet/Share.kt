package rocks.lumina.app.core.ui.sheet

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import rocks.lumina.app.core.designsystem.theme.LuminaShapes
import rocks.lumina.app.core.nostr.NostrLinks
import rocks.lumina.app.core.nostr.model.PicturePost
import rocks.lumina.app.core.ui.R

/** Share / copy helpers for posts and profiles. */
public object ShareActions {
    public fun postLink(post: PicturePost, relayHint: String?): String =
        NostrLinks.webLink(NostrLinks.nevent(post.id, post.pubkey, post.event.kind, listOfNotNull(relayHint)))

    public fun profileLink(pubkey: String): String = NostrLinks.webLink(NostrLinks.npub(pubkey))

    public fun shareText(context: Context, text: String, title: String? = null) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (title != null) putExtra(Intent.EXTRA_TITLE, title)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    public fun copy(context: Context, text: String, label: String = "LUMINA") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

/** QR code rendered with ZXing (black on white for scanner reliability). */
@Composable
public fun QrCode(content: String, size: Dp = 220.dp, modifier: Modifier = Modifier) {
    val bitmap = remember(content) { qrBitmap(content, 512) }
    Box(modifier.background(Color.White, LuminaShapes.control).padding(12.dp)) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.qr_code, content.take(24)),
            filterQuality = FilterQuality.None,
            modifier = Modifier.size(size),
        )
    }
}

public fun qrBitmap(content: String, px: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, px, px, mapOf(EncodeHintType.MARGIN to 0))
    val pixels = IntArray(px * px) { i -> if (matrix.get(i % px, i / px)) AndroidColor.BLACK else AndroidColor.WHITE }
    return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888)
}
