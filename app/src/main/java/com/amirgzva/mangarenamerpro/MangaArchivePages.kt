package com.amirgzva.mangarenamerpro

import android.content.Context
import com.github.junrar.Junrar
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipFile

internal object MangaArchivePages {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "avif", "bmp", "gif")
    private const val MAX_ENTRIES = 20000
    private const val MAX_ENTRY_BYTES = 150L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 1536L * 1024L * 1024L

    fun pages(context: Context, archive: File): List<File> {
        if (!archive.isFile) return emptyList()
        val cacheRoot = File(context.cacheDir, "manga-archive-pages")
        val key = archive.absolutePath.hashCode().toUInt().toString(16) + "-" + archive.length() + "-" + archive.lastModified()
        val target = File(cacheRoot, key)
        val marker = File(target, ".complete")
        if (marker.exists()) return listImages(target)
        runCatching { target.deleteRecursively() }
        if (!target.mkdirs() && !target.isDirectory) return emptyList()
        return try {
            val result = when (archive.extension.lowercase(Locale.ROOT)) {
                "cbz", "zip" -> extractZip(archive, target)
                "7z" -> extractSevenZip(archive, target)
                "cbr", "rar" -> extractRar(archive, target)
                else -> emptyList()
            }
            if (result.isNotEmpty()) marker.writeText("ok")
            result
        } catch (_: Exception) {
            runCatching { target.deleteRecursively() }
            emptyList()
        }
    }

    private fun extractZip(archive: File, target: File): List<File> {
        ZipFile(archive).use { zip ->
            val candidates = mutableListOf<Pair<String, java.util.zip.ZipEntry>>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory && isImage(entry.name)) {
                    if (candidates.size >= MAX_ENTRIES) throw IllegalStateException("Archive entry limit exceeded")
                    candidates.add(entry.name to entry)
                }
            }
            candidates.sortWith { a, b -> naturalCompare(a.first, b.first) }
            var total = 0L
            val output = mutableListOf<File>()
            candidates.forEachIndexed { index, pair ->
                val entry = pair.second
                if (entry.size > MAX_ENTRY_BYTES) return@forEachIndexed
                val out = File(target, safeOutputName(index, pair.first))
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(out).use { stream ->
                        total += copyLimited(input, stream, MAX_ENTRY_BYTES, MAX_TOTAL_BYTES - total)
                    }
                }
                if (out.length() > 0L) output.add(out) else out.delete()
                if (total >= MAX_TOTAL_BYTES) throw IllegalStateException("Archive expanded size limit exceeded")
            }
            return output
        }
    }

    private fun extractSevenZip(archive: File, target: File): List<File> {
        val candidates = mutableListOf<Pair<String, ByteArray>>()
        SevenZFile(archive).use { seven ->
            var entry = seven.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && isImage(entry.name)) {
                    if (candidates.size >= MAX_ENTRIES) throw IllegalStateException("Archive entry limit exceeded")
                    if (entry.size in 1..MAX_ENTRY_BYTES) {
                        val bytes = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(64 * 1024)
                        var entryTotal = 0L
                        while (true) {
                            val count = seven.read(buffer)
                            if (count < 0) break
                            entryTotal += count
                            if (entryTotal > MAX_ENTRY_BYTES) throw IllegalStateException("Archive entry too large")
                            bytes.write(buffer, 0, count)
                        }
                        candidates.add(entry.name to bytes.toByteArray())
                    } else {
                        val buffer = ByteArray(64 * 1024)
                        while (seven.read(buffer) >= 0) Unit
                    }
                }
                entry = seven.nextEntry
            }
        }
        candidates.sortWith { a, b -> naturalCompare(a.first, b.first) }
        var total = 0L
        return candidates.mapIndexedNotNull { index, pair ->
            total += pair.second.size
            if (total > MAX_TOTAL_BYTES) throw IllegalStateException("Archive expanded size limit exceeded")
            File(target, safeOutputName(index, pair.first)).also { it.writeBytes(pair.second) }
        }
    }

    private fun extractRar(archive: File, target: File): List<File> {
        val extracted = Junrar.extract(archive, target)
        val root = target.canonicalFile
        return extracted.asSequence()
            .filter { it.isFile && isImage(it.name) }
            .filter { runCatching { it.canonicalFile.path.startsWith(root.path + File.separator) }.getOrDefault(false) }
            .sortedWith { a, b -> naturalCompare(a.name, b.name) }
            .take(MAX_ENTRIES)
            .toList()
    }

    private fun listImages(directory: File): List<File> = directory.walkTopDown()
        .filter { it.isFile && isImage(it.name) }
        .sortedWith { a, b -> naturalCompare(a.relativeTo(directory).path, b.relativeTo(directory).path) }
        .take(MAX_ENTRIES)
        .toList()

    private fun isImage(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in imageExtensions

    private fun safeOutputName(index: Int, original: String): String {
        val extension = original.substringAfterLast('.', "jpg").lowercase(Locale.ROOT)
        return "%05d.%s".format(Locale.ROOT, index + 1, extension)
    }

    private fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream, entryLimit: Long, remainingTotal: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var written = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            written += count
            if (written > entryLimit || written > remainingTotal) throw IllegalStateException("Archive expanded size limit exceeded")
            output.write(buffer, 0, count)
        }
        return written
    }

    private fun naturalCompare(left: String, right: String): Int {
        val a = Regex("\\d+|\\D+").findAll(left.lowercase(Locale.ROOT)).map { it.value }.toList()
        val b = Regex("\\d+|\\D+").findAll(right.lowercase(Locale.ROOT)).map { it.value }.toList()
        for (i in 0 until minOf(a.size, b.size)) {
            val x = a[i]; val y = b[i]
            val result = if (x.all(Char::isDigit) && y.all(Char::isDigit)) {
                val nx = x.trimStart('0').ifEmpty { "0" }; val ny = y.trimStart('0').ifEmpty { "0" }
                nx.length.compareTo(ny.length).takeIf { it != 0 } ?: nx.compareTo(ny)
            } else x.compareTo(y)
            if (result != 0) return result
        }
        return a.size.compareTo(b.size)
    }
}
