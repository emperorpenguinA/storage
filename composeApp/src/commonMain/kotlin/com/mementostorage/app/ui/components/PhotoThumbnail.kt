package com.mementostorage.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mementostorage.app.di.AppContainer
import com.mementostorage.app.domain.model.EntryAttachment

/**
 * A small square preview of a PHOTO field's attachment, loading and decoding its bytes lazily.
 * Shows a placeholder icon while loading or on decode failure. If the bytes aren't available
 * locally yet (e.g. a restored-from-backup attachment whose content was never re-downloaded),
 * [com.mementostorage.app.drive.SyncService.ensureAttachmentBytes] fetches and caches them from
 * Drive on first display.
 *
 * When [enlargeOnClick] is set, tapping the thumbnail opens it full-screen in a dialog (tap the
 * dialog's background or its close button to dismiss). Leave it off where the thumbnail sits
 * inside something else that's already clickable — e.g. a list row that opens the record — to
 * avoid the two clicks fighting over the tap.
 */
@Composable
fun PhotoThumbnail(
    container: AppContainer,
    attachment: EntryAttachment?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    enlargeOnClick: Boolean = false,
) {
    var bitmap by remember(attachment?.id) { mutableStateOf<ImageBitmap?>(null) }
    var showFullScreen by remember { mutableStateOf(false) }

    LaunchedEffect(attachment?.id, attachment?.localPath) {
        bitmap = attachment
            ?.let { container.syncService.ensureAttachmentBytes(it) }
            ?.let { decodeImageBitmapOrNull(it) }
    }

    val decoded = bitmap
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .let { if (enlargeOnClick && decoded != null) it.clickable { showFullScreen = true } else it },
        contentAlignment = Alignment.Center,
    ) {
        if (decoded != null) {
            Image(
                bitmap = decoded,
                contentDescription = attachment?.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            Icon(
                Icons.Default.Photo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showFullScreen && decoded != null) {
        Dialog(onDismissRequest = { showFullScreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { showFullScreen = false },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = decoded,
                    contentDescription = attachment?.fileName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(
                    onClick = { showFullScreen = false },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "閉じる", tint = Color.White)
                }
            }
        }
    }
}
