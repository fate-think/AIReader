package com.example.aireader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Shared local HTML handoff for the current workspace and saved reports. Call uri() on IO. */
object HtmlBrowser {
    fun uri(context: Context, html: String): Uri {
        val dir = File(context.cacheDir, "html_reports").apply { mkdirs() }
        val file = File(dir, "AIReader-${UUID.randomUUID()}.html")
        file.outputStream().use { HtmlExport.write(it, html) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun open(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/html")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
