package com.mementostorage.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.mementostorage.app.data.local.AttachmentFileStore
import com.mementostorage.app.domain.model.EntryAttachment

/**
 * A small square preview of a PHOTO field's attachment, loading and decoding its bytes lazily.
 * Shows a placeholder icon while loading, on decode failure, or when the bytes aren't available
 * locally (e.g. a restored-from-backup attachment whose content hasn't been re-downloaded yet —
 * see SyncService's restoreLatestBackup doc comment).
 */
@Composable
fun PhotoThumbnail(
    fileStore: AttachmentFileStore,
    attachment: EntryAttachment?,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
) {
    var bitmap by remember(attachment?.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(attachment?.id, attachment?.localPath) {
        bitmap = attachment?.localPath
            ?.let { fileStore.readBytes(it) }
            ?.let { decodeImageBitmapOrNull(it) }
    }

    val decoded = bitmap
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
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
}
