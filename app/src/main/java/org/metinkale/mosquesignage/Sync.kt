package org.metinkale.mosquesignage

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

fun sync(ctx: Context): Boolean {
    var reloadNeeded = false

    val host = ctx.resources.getString(R.string.query)
    if (!www.exists()) www.mkdirs()
    val lines = fetchRemoteHashes("https://signage.igmg-bs.de/hash.php?hostname=$host")
    val remoteFiles = parseRemoteHashes(lines)

    for ((file, hash) in remoteFiles) {
        val localPath = File(www, file)
        if (!localPath.exists()) {
            Log.i("Sync", "File $file was added")
            localPath.parentFile?.mkdirs()
            downloadFile("https://signage.igmg-bs.de$file", localPath)
        } else {
            val localHash = calculateMd5(localPath)
            if (localHash != hash) {
                Log.i("Sync", "File $file has changed")
                localPath.parentFile?.mkdirs()
                val tempPath = File(localPath.parent, "${localPath.name}.tmp")
                downloadFile("https://signage.igmg-bs.de$file", tempPath)
                localPath.delete()
                tempPath.renameTo(localPath)

                if (file == "/index.html") {
                    reloadNeeded = true
                }
            }
        }
    }

    val localFiles = www.walkTopDown().filter { it.isFile }.map { it.relativeTo(www).path }
    for (path in localFiles) {
        if (!remoteFiles.containsKey("/$path")) {
            Log.i("Sync", "File $path was removed")
            File(www, path).delete()
        }
    }

    return reloadNeeded
}

private fun fetchRemoteHashes(url: String): List<String> = runBlocking {
    withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.inputStream.bufferedReader().readLines().also {
            connection.disconnect()
        }
    }
}

private fun parseRemoteHashes(lines: List<String>): Map<String, String> {
    return lines.associate {
        val parts = it.split("=")
        parts[0] to parts[1]
    }
}

private fun downloadFile(url: String, destination: File) = runBlocking {
    withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.inputStream.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        connection.disconnect()
    }
}


fun calculateMd5(file: File): String {
    val md = MessageDigest.getInstance("MD5")
    file.inputStream().use { input ->
        val buffer = ByteArray(1024)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            md.update(buffer, 0, bytesRead)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it).lowercase(Locale.US) }
}