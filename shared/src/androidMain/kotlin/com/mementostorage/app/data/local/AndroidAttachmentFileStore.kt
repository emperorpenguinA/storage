package com.mementostorage.app.data.local

import android.content.Context
import com.mementostorage.app.util.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidAttachmentFileStore(private val context: Context) : AttachmentFileStore {
    private val directory: File
        get() = File(context.filesDir, "attachments").apply { mkdirs() }

    override suspend fun readBytes(localPath: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(localPath)
        if (file.exists()) file.readBytes() else null
    }

    override suspend fun writeBytes(fileName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val safeName = "${IdGenerator.newId()}_${fileName.substringAfterLast('/')}"
        val file = File(directory, safeName)
        file.writeBytes(bytes)
        file.absolutePath
    }
}
