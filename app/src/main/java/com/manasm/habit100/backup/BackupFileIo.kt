package com.manasm.habit100.backup

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reading and writing the backup file itself, kept behind an interface so the view model can
 * be tested without the system file picker. [D] is whatever names a destination — a content
 * [Uri] in the app, a plain string in tests.
 */
interface BackupFileIo<D> {
    suspend fun write(destination: D, text: String)
    suspend fun read(destination: D): String
}

/**
 * The real thing: writes through the Storage Access Framework, so the user picks the location
 * and the app needs no storage permission.
 */
class ContentResolverBackupIo(private val resolver: ContentResolver) : BackupFileIo<Uri> {

    override suspend fun write(destination: Uri, text: String) = withContext(Dispatchers.IO) {
        // "wt" truncates — without it, overwriting a longer backup leaves a tail of the old
        // file behind and produces something that is not valid JSON.
        resolver.openOutputStream(destination, "wt")?.use { it.write(text.toByteArray()) }
            ?: throw java.io.IOException("could not open $destination for writing")
    }

    override suspend fun read(destination: Uri): String = withContext(Dispatchers.IO) {
        resolver.openInputStream(destination)?.use { it.readBytes().decodeToString() }
            ?: throw java.io.IOException("could not open $destination for reading")
    }
}
