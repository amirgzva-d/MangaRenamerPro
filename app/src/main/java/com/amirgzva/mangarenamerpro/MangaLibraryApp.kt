package com.amirgzva.mangarenamerpro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.provider.Settings
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.Closeable
import java.util.Locale
import kotlin.math.roundToInt

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

private val MangaBlack = Color(0xFF050505)
private val MangaPanel = Color(0xFF171717)
private val MangaPanelRaised = Color(0xFF222222)
private val MangaGold = Color(0xFFD8B65A)
private val MangaGoldSoft = Color(0xFFB79743)
private val MangaGreen = Color(0xFF00B85A)
private val MangaWhite = Color(0xFFF7F7F7)
private val MangaMuted = Color(0xFFA7A7A7)
private val MangaLine = Color(0xFF282828)
private val LibraryExtensions = setOf("jpg", "jpeg", "png", "webp", "avif", "bmp", "gif", "pdf", "cbz", "cbr", "zip", "rar", "7z")
private val ImageExtensions = setOf("jpg", "jpeg", "png", "webp", "avif", "bmp", "gif")

internal data class ReadingProgress(val page: Int, val total: Int, val updatedAt: Long)
internal data class RecentMangaFile(val file: File, val page: Int, val total: Int, val updatedAt: Long)
private data class MangaFolderUi(
    val path: String,
    val title: String,
    val files: List<File>,
    val cover: File?,
    val totalPages: Int,
    val readPages: Int,
    val lastFile: File?,
    val lastPage: Int,
    val favorite: Boolean,
    val following: Boolean
) {
    val percent: Int get() = if (totalPages <= 0) 0 else ((readPages.toDouble() / totalPages) * 100.0).roundToInt().coerceIn(0, 100)
    val hasProgress: Boolean get() = readPages > 0
}

internal object MangaStore {
    private const val PREFS = "manga_library_v2"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getProgress(context: Context, file: File): ReadingProgress {
        val p = prefs(context)
        val path = file.absolutePath
        return ReadingProgress(
            p.getInt("progress_page:$path", 0),
            p.getInt("progress_total:$path", 0),
            p.getLong("progress_time:$path", 0L)
        )
    }

    fun savePageCount(context: Context, file: File, count: Int) {
        if (count > 0) prefs(context).edit().putInt("progress_total:${file.absolutePath}", count).apply()
    }

    fun saveProgress(context: Context, file: File, page: Int, total: Int) {
        val path = file.absolutePath
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putInt("progress_page:$path", page.coerceIn(1, total.coerceAtLeast(1)))
            .putInt("progress_total:$path", total.coerceAtLeast(1))
            .putLong("progress_time:$path", now)
            .putString("last_file:${file.parentFile?.absolutePath ?: ""}", path)
            .putString("global_last_file", path)
            .apply()
    }

    fun lastFile(context: Context, folder: File): File? =
        prefs(context).getString("last_file:${folder.absolutePath}", null)?.let(::File)?.takeIf { it.exists() }

    fun globalLastFile(context: Context): File? =
        prefs(context).getString("global_last_file", null)?.let(::File)?.takeIf { it.exists() }

    fun recentFiles(context: Context, limit: Int = 60): List<RecentMangaFile> {
        val p = prefs(context).all
        return p.keys.filter { it.startsWith("progress_time:") }
            .mapNotNull { key ->
                val path = key.removePrefix("progress_time:")
                val file = File(path)
                if (!file.exists()) null else {
                    val total = (p["progress_total:$path"] as? Int) ?: 0
                    val page = (p["progress_page:$path"] as? Int) ?: 0
                    val time = (p[key] as? Long) ?: 0L
                    if (page <= 0 || total <= 0) null else RecentMangaFile(file, page, total, time)
                }
            }
            .sortedByDescending { it.updatedAt }
            .take(limit)
    }

    fun isFavorite(context: Context, path: String): Boolean =
        prefs(context).getStringSet("favorite_folders", emptySet())?.contains(path) == true

    fun setFavorite(context: Context, path: String, enabled: Boolean) {
        val p = prefs(context)
        val values = p.getStringSet("favorite_folders", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (enabled) values.add(path) else values.remove(path)
        p.edit().putStringSet("favorite_folders", values).apply()
    }

    fun isFollowing(context: Context, path: String): Boolean =
        prefs(context).getStringSet("following_folders", emptySet())?.contains(path) == true

    fun setFollowing(context: Context, path: String, enabled: Boolean) {
        val p = prefs(context)
        val values = p.getStringSet("following_folders", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (enabled) values.add(path) else values.remove(path)
        p.edit().putStringSet("following_folders", values).apply()
    }

    fun title(context: Context, folder: File): String =
        prefs(context).getString("display_title:${folder.absolutePath}", null)?.takeIf { it.isNotBlank() } ?: folder.name

    fun setTitle(context: Context, folder: File, value: String) {
        prefs(context).edit().putString("display_title:${folder.absolutePath}", value.trim()).apply()
    }

    fun coverPath(context: Context, folder: File): String? =
        prefs(context).getString("cover_path:${folder.absolutePath}", null)

    fun setCover(context: Context, folder: File, file: File) {
        prefs(context).edit().putString("cover_path:${folder.absolutePath}", file.absolutePath).apply()
    }

    fun moveProgress(context: Context, oldPath: String, newPath: String) {
        val p = prefs(context)
        val editor = p.edit()
        listOf("progress_page:", "progress_total:", "progress_time:").forEach { prefix ->
            val oldKey = prefix + oldPath
            val value = p.all[oldKey]
            if (value != null) {
                when (value) {
                    is Int -> editor.putInt(prefix + newPath, value)
                    is Long -> editor.putLong(prefix + newPath, value)
                    is String -> editor.putString(prefix + newPath, value)
                    else -> Unit
                }
                editor.remove(oldKey)
            }
        }
        p.all.forEach { (key, value) ->
            if (value is String && value == oldPath && (key.startsWith("last_file:") || key == "global_last_file")) {
                editor.putString(key, newPath)
            }
        }
        editor.apply()
    }

    fun setting(context: Context, key: String, default: String): String =
        prefs(context).getString("setting:$key", default) ?: default

    fun settingInt(context: Context, key: String, default: Int): Int =
        prefs(context).getInt("setting:$key", default)

    fun setSetting(context: Context, key: String, value: String) {
        prefs(context).edit().putString("setting:$key", value).apply()
    }

    fun setSetting(context: Context, key: String, value: Int) {
        prefs(context).edit().putInt("setting:$key", value).apply()
    }

    fun libraryRoot(context: Context): String =
        prefs(context).getString("library_root", Environment.getExternalStorageDirectory().absolutePath)
            ?: Environment.getExternalStorageDirectory().absolutePath

    fun setLibraryRoot(context: Context, path: String) {
        prefs(context).edit().putString("library_root", path).apply()
    }
}

/**
 * One directory directly under the selected library root represents one manga.
 * Only files directly inside that manga directory belong to it; child directories
 * are never mixed into the manga's chapter list or treated as chapter files.
 */
private fun collectMangaFiles(folder: File): List<File> {
    if (!folder.isDirectory || !folder.canRead()) return emptyList()
    return runCatching {
        folder.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in LibraryExtensions }
            ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            ?.toList()
            ?: emptyList()
    }.getOrDefault(emptyList())
}

private data class PdfSliceSpec(
    val sourcePage: Int,
    val offsetY: Int,
    val height: Int,
    val scale: Float
)

private class PdfDocumentSession(private val file: File) : Closeable {
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var sliceWidth = 0
    private var sliceHeight = 0
    private var sliceSpecs: List<PdfSliceSpec> = emptyList()
    private val bitmapCache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    @Synchronized
    fun open(): Int {
        if (renderer == null) {
            if (!file.isFile) return 0
            val openedDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val openedRenderer = PdfRenderer(openedDescriptor)
                descriptor = openedDescriptor
                renderer = openedRenderer
            } catch (error: Throwable) {
                runCatching { openedDescriptor.close() }
                throw error
            }
        }
        return renderer?.pageCount ?: 0
    }

    /**
     * Converts every PDF page into screen-sized vertical slices at native device width.
     * Normal portrait pages remain a single slice. Very long webtoon pages become
     * several full-width slices, so they are readable without zooming or scrolling
     * inside the horizontal page-turn mode.
     */
    @Synchronized
    fun prepareSlices(targetWidth: Int, maxSliceHeight: Int): Int {
        val width = targetWidth.coerceIn(900, 1920)
        val height = maxSliceHeight.coerceIn(1300, 3400)
        if (sliceWidth == width && sliceHeight == height && sliceSpecs.isNotEmpty()) {
            return sliceSpecs.size
        }

        val pageCount = open()
        val activeRenderer = renderer ?: return 0
        val prepared = ArrayList<PdfSliceSpec>()
        for (pageIndex in 0 until pageCount) {
            var page: PdfRenderer.Page? = null
            try {
                val activePage = activeRenderer.openPage(pageIndex)
                page = activePage
                val scale = width.toFloat() / activePage.width.toFloat()
                val renderedHeight = (activePage.height * scale).roundToInt().coerceAtLeast(1)
                val sliceCount = ((renderedHeight + height - 1) / height).coerceAtLeast(1)
                repeat(sliceCount) { sliceIndex ->
                    val offset = sliceIndex * height
                    val tileHeight = (renderedHeight - offset).coerceAtMost(height).coerceAtLeast(1)
                    prepared += PdfSliceSpec(pageIndex, offset, tileHeight, scale)
                }
            } finally {
                runCatching { page?.close() }
            }
        }

        sliceWidth = width
        sliceHeight = height
        sliceSpecs = prepared
        bitmapCache.evictAll()
        return sliceSpecs.size
    }

    @Synchronized
    fun hasScreenSlices(): Boolean =
        sliceSpecs.size > (renderer?.pageCount ?: 0)

    @Synchronized
    fun renderSegment(segmentIndex: Int, targetWidth: Int): Bitmap? {
        val spec = sliceSpecs.getOrNull(segmentIndex) ?: return null
        val width = targetWidth.coerceIn(900, 1920).coerceAtMost(sliceWidth).coerceAtLeast(1)
        val key = "$segmentIndex:$width:$sliceHeight"
        bitmapCache.get(key)?.let { return it }

        var page: PdfRenderer.Page? = null
        var bitmap: Bitmap? = null
        try {
            val activeRenderer = renderer ?: return null
            val activePage = activeRenderer.openPage(spec.sourcePage)
            page = activePage
            bitmap = Bitmap.createBitmap(width, spec.height, Bitmap.Config.ARGB_8888)

            val transform = Matrix().apply {
                setValues(
                    floatArrayOf(
                        spec.scale, 0f, 0f,
                        0f, spec.scale, -spec.offsetY.toFloat(),
                        0f, 0f, 1f
                    )
                )
            }
            activePage.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmapCache.put(key, bitmap)
            return bitmap
        } catch (_: Exception) {
            runCatching { bitmap?.recycle() }
            return null
        } catch (_: OutOfMemoryError) {
            runCatching { bitmap?.recycle() }
            return null
        } finally {
            runCatching { page?.close() }
        }
    }

    @Synchronized
    override fun close() {
        bitmapCache.evictAll()
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
        renderer = null
        descriptor = null
        sliceSpecs = emptyList()
        sliceWidth = 0
        sliceHeight = 0
    }
}

private val PdfRendererLock = Any()

private fun openPdfWithComicScreen(context: Context, file: File) {
    if (!file.isFile || !file.exists()) {
        Toast.makeText(context, "فایل PDF پیدا نشد.", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        val contentUri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
        val comicScreenIntent = Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.viewer.comicscreen")
            setDataAndType(contentUri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(comicScreenIntent)
        } catch (_: Exception) {
            val genericReaderIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(genericReaderIntent, "انتخاب برنامه نمایش PDF"))
        }
    }.onFailure { error ->
        Toast.makeText(
            context,
            error.message ?: "باز کردن PDF در برنامه دیگر ناموفق بود.",
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun pdfPageCount(file: File): Int {
    if (!file.isFile || !file.extension.equals("pdf", true)) return 0
    return synchronized(PdfRendererLock) {
        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val activeRenderer = PdfRenderer(descriptor)
            renderer = activeRenderer
            activeRenderer.pageCount.coerceAtLeast(0)
        } catch (_: Exception) {
            0
        } catch (_: OutOfMemoryError) {
            0
        } finally {
            runCatching { renderer?.close() }
            runCatching { descriptor?.close() }
        }
    }
}

private fun renderPdfPageBitmap(file: File, pageIndex: Int, targetWidth: Int, maxHeight: Int): Bitmap? {
    if (!file.isFile || !file.extension.equals("pdf", true)) return null
    return synchronized(PdfRendererLock) {
        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var pdfPage: PdfRenderer.Page? = null
        var bitmap: Bitmap? = null
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val activeRenderer = PdfRenderer(descriptor)
            renderer = activeRenderer
            if (activeRenderer.pageCount <= 0) return@synchronized null

            val safeIndex = pageIndex.coerceIn(0, activeRenderer.pageCount - 1)
            val activePage = activeRenderer.openPage(safeIndex)
            pdfPage = activePage

            // Bound decoded PDF bitmaps to avoid OOM on very tall/scanned pages.
            val maxWidthPx = targetWidth.coerceIn(480, 1400)
            val maxHeightPx = maxHeight.coerceIn(720, 2200)
            val scale = minOf(
                maxWidthPx.toFloat() / activePage.width.toFloat(),
                maxHeightPx.toFloat() / activePage.height.toFloat()
            )
            val width = (activePage.width * scale).roundToInt().coerceIn(1, maxWidthPx)
            val height = (activePage.height * scale).roundToInt().coerceIn(1, maxHeightPx)
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            activePage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (_: Exception) {
            runCatching { bitmap?.recycle() }
            null
        } catch (_: OutOfMemoryError) {
            runCatching { bitmap?.recycle() }
            null
        } finally {
            runCatching { pdfPage?.close() }
            runCatching { renderer?.close() }
            runCatching { descriptor?.close() }
        }
    }
}
private fun resolvePageCount(context: Context, file: File): Int {
    val saved = MangaStore.getProgress(context, file).total
    if (saved > 0) return saved
    val count = when (file.extension.lowercase(Locale.ROOT)) {
        "pdf" -> pdfPageCount(file)
        "cbz", "zip", "cbr", "rar", "7z" -> MangaArchivePages.pages(context, file).size
        in ImageExtensions -> 1
        else -> 0
    }
    return count.also { if (it > 0) MangaStore.savePageCount(context, file, it) }
}

private fun loadFolderUi(context: Context, folder: File): MangaFolderUi {
    val files = collectMangaFiles(folder)
    var total = 0
    var read = 0
    files.forEach { file ->
        val count = resolvePageCount(context, file)
        val progress = MangaStore.getProgress(context, file)
        total += count
        read += progress.page.coerceAtMost(count)
    }
    val explicitCover = MangaStore.coverPath(context, folder)?.let(::File)?.takeIf { it.exists() }
    val cover = explicitCover
        ?: files.firstOrNull { it.extension.lowercase(Locale.ROOT) in ImageExtensions }
        ?: files.firstOrNull { it.extension.equals("pdf", true) }
        ?: files.firstOrNull { it.extension.lowercase(Locale.ROOT) in setOf("cbz", "zip", "cbr", "rar", "7z") }
    val last = MangaStore.lastFile(context, folder)
        ?: files.map { it to MangaStore.getProgress(context, it) }
            .filter { it.second.updatedAt > 0L }
            .maxByOrNull { it.second.updatedAt }?.first
    val lastProgress = last?.let { MangaStore.getProgress(context, it) }
    return MangaFolderUi(
        folder.absolutePath,
        MangaStore.title(context, folder),
        files,
        cover,
        total,
        read.coerceAtMost(total),
        last,
        lastProgress?.page ?: 0,
        MangaStore.isFavorite(context, folder.absolutePath),
        MangaStore.isFollowing(context, folder.absolutePath)
    )
}

private fun renderPdfCoverBitmap(file: File, targetWidth: Int, maxHeight: Int): Bitmap? {
    if (!file.isFile || !file.extension.equals("pdf", true)) return null
    return synchronized(PdfRendererLock) {
        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        var bitmap: Bitmap? = null
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val activeRenderer = PdfRenderer(descriptor)
            renderer = activeRenderer
            if (activeRenderer.pageCount <= 0) return@synchronized null
            val activePage = activeRenderer.openPage(0)
            page = activePage

            // Render the top of a tall PDF page at full cover width rather than
            // shrinking the entire webtoon strip into a tiny, blurry thumbnail.
            val width = targetWidth.coerceIn(360, 900)
            val heightLimit = maxHeight.coerceIn(480, 1400)
            val scale = width.toFloat() / activePage.width.toFloat()
            val fullHeight = (activePage.height * scale).roundToInt().coerceAtLeast(1)
            val height = fullHeight.coerceAtMost(heightLimit)
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val matrix = Matrix().apply {
                setValues(floatArrayOf(scale, 0f, 0f, 0f, scale, 0f, 0f, 0f, 1f))
            }
            activePage.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (_: Exception) {
            runCatching { bitmap?.recycle() }
            null
        } catch (_: OutOfMemoryError) {
            runCatching { bitmap?.recycle() }
            null
        } finally {
            runCatching { page?.close() }
            runCatching { renderer?.close() }
            runCatching { descriptor?.close() }
        }
    }
}

private fun loadCoverBitmap(context: Context, file: File): Bitmap? = runCatching {
    if (!file.exists() || !file.isFile) return@runCatching null
    val cache = File(
        context.cacheDir,
        "manga-cover-${file.absolutePath.hashCode()}-${file.length()}-${file.lastModified()}.jpg"
    )
    if (cache.exists() && cache.length() > 0L) {
        BitmapFactory.decodeFile(cache.absolutePath)?.let { return@runCatching it }
    }

    val bitmap = when (file.extension.lowercase(Locale.ROOT)) {
        in ImageExtensions -> loadImagePage(file, maxWidth = 720, maxHeight = 960)
        "pdf" -> renderPdfCoverBitmap(file, targetWidth = 720, maxHeight = 1080)
        "cbz", "zip", "cbr", "rar", "7z" ->
            MangaArchivePages.pages(context, file).firstOrNull()?.let { loadImagePage(it, 720, 960) }
        else -> null
    }

    if (bitmap != null) {
        runCatching {
            cache.parentFile?.mkdirs()
            FileOutputStream(cache).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        }
    }
    bitmap
}.getOrNull()
private fun renderMangaPage(
    context: Context,
    file: File,
    pageIndex: Int,
    targetWidth: Int,
    pdfSession: PdfDocumentSession? = null
): Bitmap? {
    return when (file.extension.lowercase(Locale.ROOT)) {
        "pdf" -> pdfSession?.renderSegment(pageIndex, targetWidth)
            ?: if (pdfSession == null) renderPdfPageBitmap(file, pageIndex, targetWidth, maxHeight = 2200) else null
        "cbz", "zip", "cbr", "rar", "7z" ->
            MangaArchivePages.pages(context, file).getOrNull(pageIndex)?.let {
                loadImagePage(it, maxWidth = 1400, maxHeight = 2200)
            }
        in ImageExtensions -> loadImagePage(file, maxWidth = 1400, maxHeight = 2200)
        else -> null
    }
}

private fun loadImagePage(file: File, maxWidth: Int = 1800, maxHeight: Int = 2400): Bitmap? = runCatching {
    if (!file.isFile || file.extension.lowercase(Locale.ROOT) !in ImageExtensions) return@runCatching null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    var sample = 1
    while (bounds.outWidth / sample > maxWidth || bounds.outHeight / sample > maxHeight) {
        sample *= 2
    }
    BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    )
}.getOrNull()
private fun formatPersianNumber(value: Int): String =
    value.toString().map { if (it in '0'..'9') ('۰'.code + it.code - '0'.code).toChar() else it }.joinToString("")

/**
 * Converts Android's local external-storage tree URI to a filesystem path.
 * The app still uses MANAGE_EXTERNAL_STORAGE for its existing full file manager;
 * the picker is only used to select the library's parent directory.
 */
private fun resolveLocalTreeDirectory(context: Context, treeUri: Uri): File? = runCatching {
    val documentId = DocumentsContract.getTreeDocumentId(treeUri)
    val separator = documentId.indexOf(':')
    if (separator <= 0) return@runCatching null

    val volumeId = documentId.substring(0, separator)
    val relativePath = documentId.substring(separator + 1)
    val volumeRoot = (if (volumeId.equals("primary", ignoreCase = true)) {
        Environment.getExternalStorageDirectory()
    } else {
        File("/storage/$volumeId")
    }).canonicalFile

    val selected = File(volumeRoot, relativePath).canonicalFile
    val rootPrefix = volumeRoot.path + File.separator
    if (selected.path != volumeRoot.path && !selected.path.startsWith(rootPrefix)) return@runCatching null
    if (!selected.exists() || !selected.isDirectory || !selected.canRead()) return@runCatching null
    selected
}.getOrNull()

private fun hasLibraryAccess(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

private fun requestLibraryAccess(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(appIntent) }.onFailure {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}

@Composable
private fun MangaCover(
    file: File?,
    modifier: Modifier = Modifier,
    radius: Int = 18,
    coverScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = file?.absolutePath,
        key2 = file?.length(),
        key3 = file?.lastModified()
    ) {
        value = file?.let { selected ->
            withContext(Dispatchers.IO) { loadCoverBitmap(context, selected)?.asImageBitmap() }
        }
    }
    Box(
        modifier.clip(RoundedCornerShape(radius.dp)).background(MangaPanelRaised),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(image!!, contentDescription = file?.name, modifier = Modifier.fillMaxSize(), contentScale = coverScale)
        } else {
            Icon(
                when {
                    file?.extension.equals("pdf", true) -> Icons.Default.PictureAsPdf
                    file?.extension?.lowercase(Locale.ROOT) in setOf("cbz", "zip", "cbr", "rar", "7z") -> Icons.Default.Folder
                    else -> Icons.Default.Folder
                },
                contentDescription = null,
                tint = MangaGold,
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

@Composable
private fun MangaProgressBar(progress: Float, modifier: Modifier = Modifier, height: Int = 7) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier.fillMaxWidth().height(height.dp).clip(CircleShape).background(Color(0xFF303030))
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f))
                    .align(Alignment.CenterEnd).clip(CircleShape).background(MangaGold)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaLibraryApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("manga_library_v2", Context.MODE_PRIVATE) }
    var hasAccess by remember { mutableStateOf(hasLibraryAccess()) }
    var activeTab by remember { mutableStateOf("library") }
    var showFileManager by remember { mutableStateOf(false) }
    var readerFile by remember { mutableStateOf<File?>(null) }
    var selectedFolderPath by remember { mutableStateOf<String?>(null) }
    var detailFolder by remember { mutableStateOf<MangaFolderUi?>(null) }
    var rootPath by remember { mutableStateOf(MangaStore.libraryRoot(context)) }
    var folders by remember { mutableStateOf<List<MangaFolderUi>>(emptyList()) }
    var recent by remember { mutableStateOf<List<RecentMangaFile>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var columns by remember {
        mutableIntStateOf(MangaStore.settingInt(context, "columns", 2).coerceIn(1, 4))
    }
    var layout by remember {
        mutableStateOf(
            MangaStore.setting(context, "layout", "grid")
                .takeIf { it in setOf("grid", "list", "compact") } ?: "grid"
        )
    }
    var showMetadata by remember { mutableStateOf(MangaStore.setting(context, "show_metadata", "true") != "false") }
    var coverScale by remember {
        mutableStateOf(MangaStore.setting(context, "cover_scale", "crop").takeIf { it in setOf("crop", "fit") } ?: "crop")
    }
    var cardRatio by remember {
        mutableStateOf(MangaStore.setting(context, "card_ratio", "poster").takeIf { it in setOf("poster", "square") } ?: "poster")
    }
    var sortMode by remember {
        mutableStateOf(MangaStore.setting(context, "sort_mode", "name_asc").takeIf { it in setOf("name_asc", "name_desc", "progress", "recent") } ?: "name_asc")
    }
    var accentName by remember { mutableStateOf(MangaStore.setting(context, "accent", "gold")) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var menuOpen by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var titleDialogOpen by remember { mutableStateOf(false) }
    var coverDialogOpen by remember { mutableStateOf(false) }

    val accent = when (accentName) {
        "purple" -> Color(0xFFB18AFF)
        "blue" -> Color(0xFF62B8F2)
        "green" -> Color(0xFF69C58D)
        else -> MangaGold
    }

    LaunchedEffect(rootPath, refreshKey, hasAccess) {
        if (!hasAccess) {
            folders = emptyList()
            recent = emptyList()
            loading = false
        } else {
            loading = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val root = File(rootPath)
                    if (!root.exists() || !root.isDirectory || !root.canRead()) {
                        throw IllegalStateException("مسیر کتابخانه در دسترس نیست")
                    }
                    // The selected parent folder contains manga folders only.
                    // Each immediate child directory is one manga, named by its folder.
                    root.listFiles()
                        ?.filter { it.isDirectory && !it.name.startsWith(".") }
                        ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                        ?.map { mangaFolder -> loadFolderUi(context, mangaFolder) }
                        ?: emptyList()
                }
            }
            folders = result.getOrElse { emptyList() }
            recent = MangaStore.recentFiles(context)
            loading = false
        }
    }

    LaunchedEffect(selectedFolderPath, refreshKey) {
        val path = selectedFolderPath ?: return@LaunchedEffect
        detailFolder = withContext(Dispatchers.IO) { runCatching { loadFolderUi(context, File(path)) }.getOrNull() }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = hasLibraryAccess()
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (readerFile != null) {
        MangaReaderScreen(file = readerFile!!, accent = accent, onBack = { readerFile = null; refreshKey++ })
    } else if (showFileManager) {
        MangaFileManager(
            onExit = { showFileManager = false; refreshKey++ },
            onOpenMangaFile = { readerFile = it }
        )
    } else {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = accent, secondary = MangaGold, background = MangaBlack, surface = MangaPanel,
                onPrimary = MangaBlack, onSecondary = MangaBlack, onBackground = MangaWhite, onSurface = MangaWhite
            )) {
                Box(Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = MangaBlack,
                    topBar = {
                        if (selectedFolderPath == null && activeTab != "settings") {
                            TopAppBar(
                                title = {
                                    if (searchOpen) {
                                        OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            singleLine = true,
                                            placeholder = { Text("جست‌وجوی مانگا...", color = MangaMuted) },
                                            trailingIcon = { IconButton(onClick = { searchQuery = ""; searchOpen = false }) { Icon(Icons.Default.Close, null) } },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        Text(
                                            when (activeTab) {
                                                "reading" -> "در حال مطالعه"
                                                "favorites" -> "علاقه‌مندی‌های من"
                                                else -> "کتابخانه من"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 22.sp,
                                            color = MangaWhite
                                        )
                                    }
                                },
                                actions = {
                                    if (!searchOpen) {
                                        IconButton(onClick = { searchOpen = true }) { Icon(Icons.Default.Search, "جست‌وجو", tint = MangaWhite) }
                                        Box {
                                            IconButton(onClick = { filterOpen = true }) { Icon(Icons.Default.Tune, "فیلتر", tint = MangaWhite) }
                                            DropdownMenu(expanded = filterOpen, onDismissRequest = { filterOpen = false }, containerColor = MangaPanel) {
                                                listOf("all" to "همه", "progress" to "در حال مطالعه", "complete" to "تکمیل‌شده", "unread" to "خوانده‌نشده").forEach { (key, label) ->
                                                    DropdownMenuItem(text = { Text(label, color = if (filter == key) accent else MangaWhite) }, onClick = { filter = key; filterOpen = false })
                                                }
                                            }
                                        }
                                        Box {
                                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "گزینه‌های بیشتر", tint = MangaWhite) }
                                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = MangaPanel) {
                                                DropdownMenuItem(text = { Text("تنظیمات نمایش") }, onClick = { activeTab = "settings"; menuOpen = false })
                                                DropdownMenuItem(text = { Text("مدیریت فایل و تغییر نام") }, onClick = { showFileManager = true; menuOpen = false })
                                                DropdownMenuItem(text = { Text("تازه‌سازی کتابخانه") }, onClick = { refreshKey++; menuOpen = false }, leadingIcon = { Icon(Icons.Default.Refresh, null) })
                                                HorizontalDivider(color = MangaLine)
                                                DropdownMenuItem(text = { Text("ترجمه · به‌زودی", color = MangaMuted) }, onClick = { menuOpen = false }, enabled = false)
                                                DropdownMenuItem(text = { Text("آهنگ · به‌زودی", color = MangaMuted) }, onClick = { menuOpen = false }, enabled = false)
                                            }
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = MangaBlack, titleContentColor = MangaWhite)
                            )
                        } else if (selectedFolderPath != null) {
                            TopAppBar(
                                title = { Text(detailFolder?.title ?: "جزئیات مانگا", color = MangaWhite, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                navigationIcon = { IconButton(onClick = { selectedFolderPath = null; detailFolder = null }) { Icon(Icons.Default.ArrowBack, "بازگشت", tint = MangaWhite) } },
                                actions = {
                                    Box {
                                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "گزینه‌ها", tint = MangaWhite) }
                                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = MangaPanel) {
                                            DropdownMenuItem(text = { Text("تغییر عنوان نمایشی") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuOpen = false; titleDialogOpen = true })
                                            DropdownMenuItem(text = { Text("انتخاب تصویر جلد") }, leadingIcon = { Icon(Icons.Default.Image, null) }, onClick = { menuOpen = false; coverDialogOpen = true })
                                            DropdownMenuItem(text = { Text("به‌روزرسانی اطلاعات") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = { refreshKey++; menuOpen = false })
                                            HorizontalDivider(color = MangaLine)
                                            DropdownMenuItem(text = { Text("ترجمه · به‌زودی", color = MangaMuted) }, onClick = { menuOpen = false }, enabled = false)
                                            DropdownMenuItem(text = { Text("آهنگ · به‌زودی", color = MangaMuted) }, onClick = { menuOpen = false }, enabled = false)
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = MangaBlack)
                            )
                        } else {
                            TopAppBar(
                                title = { Text("تنظیمات", color = MangaWhite, fontWeight = FontWeight.Bold) },
                                navigationIcon = { IconButton(onClick = { activeTab = "library" }) { Icon(Icons.Default.ArrowBack, "بازگشت", tint = MangaWhite) } },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = MangaBlack)
                            )
                        }
                    },
                    bottomBar = {
                        NavigationBar(containerColor = Color(0xFF080808), tonalElevation = 0.dp) {
                            NavigationBarItem(
                                selected = activeTab == "library" && selectedFolderPath == null,
                                onClick = { selectedFolderPath = null; activeTab = "library" },
                                icon = { Icon(Icons.Default.LibraryBooks, null) }, label = { Text("کتابخانه") },
                                colors = navColors(accent)
                            )
                            NavigationBarItem(
                                selected = activeTab == "reading" && selectedFolderPath == null,
                                onClick = { selectedFolderPath = null; activeTab = "reading" },
                                icon = { Icon(Icons.Default.Book, null) }, label = { Text("مطالعه") },
                                colors = navColors(accent)
                            )
                            NavigationBarItem(
                                selected = activeTab == "favorites" && selectedFolderPath == null,
                                onClick = { selectedFolderPath = null; activeTab = "favorites" },
                                icon = { Icon(Icons.Default.FavoriteBorder, null) }, label = { Text("علاقه‌مندی") },
                                colors = navColors(accent)
                            )
                            NavigationBarItem(
                                selected = activeTab == "settings",
                                onClick = { selectedFolderPath = null; activeTab = "settings" },
                                icon = { Icon(Icons.Default.Settings, null) }, label = { Text("تنظیمات") },
                                colors = navColors(accent)
                            )
                        }
                    }
                ) { inner ->
                    when {
                        !hasAccess -> AccessRequiredScreen(Modifier.padding(inner), onRequest = { requestLibraryAccess(context) })
                        selectedFolderPath != null -> {
                            if (detailFolder == null) {
                                Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent) }
                            } else {
                                MangaDetailScreen(
                                    folder = detailFolder!!,
                                    accent = accent,
                                    onOpenFile = { file ->
                                        if (
                                            file.extension.equals("pdf", true) ||
                                            file.extension.lowercase(Locale.ROOT) in ImageExtensions ||
                                            file.extension.lowercase(Locale.ROOT) in setOf("cbz", "zip", "cbr", "rar", "7z")
                                        ) {
                                            readerFile = file
                                        } else {
                                            Toast.makeText(context, "این فرمت در نمایشگر پشتیبانی نمی‌شود.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onFavoriteChanged = { enabled ->
                                        MangaStore.setFavorite(context, detailFolder!!.path, enabled)
                                        refreshKey++
                                    },
                                    onCoverChanged = { refreshKey++ },
                                    modifier = Modifier.padding(inner)
                                )
                            }
                        }
                        activeTab == "settings" -> SettingsScreen(
                            rootPath = rootPath,
                            columns = columns,
                            layout = layout,
                            showMetadata = showMetadata,
                            coverScale = coverScale,
                            cardRatio = cardRatio,
                            sortMode = sortMode,
                            accentName = accentName,
                            onRootChange = { value -> rootPath = value; MangaStore.setLibraryRoot(context, value); refreshKey++ },
                            onColumnsChange = { columns = it; MangaStore.setSetting(context, "columns", it) },
                            onLayoutChange = { layout = it; MangaStore.setSetting(context, "layout", it) },
                            onMetadataChange = { showMetadata = it; MangaStore.setSetting(context, "show_metadata", it.toString()) },
                            onCoverScaleChange = { coverScale = it; MangaStore.setSetting(context, "cover_scale", it) },
                            onCardRatioChange = { cardRatio = it; MangaStore.setSetting(context, "card_ratio", it) },
                            onSortModeChange = { sortMode = it; MangaStore.setSetting(context, "sort_mode", it); refreshKey++ },
                            onAccentChange = { accentName = it; MangaStore.setSetting(context, "accent", it) },
                            onOpenFileManager = { showFileManager = true },
                            modifier = Modifier.padding(inner)
                        )
                        activeTab == "reading" -> RecentReadingScreen(
                            recent = recent,
                            accent = accent,
                            onOpen = { readerFile = it },
                            onFolder = { file -> selectedFolderPath = file.parentFile?.absolutePath; activeTab = "library" },
                            modifier = Modifier.padding(inner)
                        )
                        else -> {
                            val visibleFolders = folders.filter { folder ->
                                val matchesSearch = folder.title.contains(searchQuery, true) || folder.files.any { it.name.contains(searchQuery, true) }
                                val matchesTab = activeTab != "favorites" || folder.favorite
                                val matchesFilter = when (filter) {
                                    "progress" -> folder.hasProgress && folder.percent < 100
                                    "complete" -> folder.totalPages > 0 && folder.percent >= 100
                                    "unread" -> folder.readPages == 0
                                    else -> true
                                }
                                matchesSearch && matchesTab && matchesFilter
                            }
                            LibraryHomeScreen(
                                folders = visibleFolders,
                                recent = recent,
                                columns = columns,
                                layout = layout,
                                showMetadata = showMetadata,
                                coverScale = coverScale,
                                cardRatio = cardRatio,
                                sortMode = sortMode,
                                loading = loading,
                                rootPath = rootPath,
                                accent = accent,
                                onOpenFolder = { folder -> selectedFolderPath = folder.path },
                                onOpenRecent = { readerFile = it },
                                modifier = Modifier.padding(inner)
                            )
                        }
                    }
                }
                if (titleDialogOpen && detailFolder != null) {
                    MangaTitleDialog(
                        context = context,
                        folder = detailFolder!!,
                        onDismiss = { titleDialogOpen = false },
                        onSaved = { titleDialogOpen = false; refreshKey++ }
                    )
                }
                if (coverDialogOpen && detailFolder != null) {
                    MangaCoverPickerDialog(
                        context = context,
                        folder = detailFolder!!,
                        onDismiss = { coverDialogOpen = false },
                        onSaved = { coverDialogOpen = false; refreshKey++ }
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun navColors(accent: Color) = NavigationBarItemDefaults.colors(
    selectedIconColor = accent,
    selectedTextColor = accent,
    indicatorColor = Color.Transparent,
    unselectedIconColor = MangaWhite,
    unselectedTextColor = MangaWhite
)

@Composable
private fun AccessRequiredScreen(modifier: Modifier = Modifier, onRequest: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Folder, null, tint = MangaGold, modifier = Modifier.size(68.dp))
        Spacer(Modifier.height(18.dp))
        Text("دسترسی کتابخانه", color = MangaWhite, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(9.dp))
        Text("برای نمایش پوشه‌ها، استخراج جلدها و ثبت پیشرفت مطالعه، مجوز مدیریت همه فایل‌ها را فعال کن.", color = MangaMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(containerColor = MangaGold), shape = RoundedCornerShape(15.dp)) {
            Text("فعال‌کردن دسترسی حافظه", color = MangaBlack, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LibraryHomeScreen(
    folders: List<MangaFolderUi>,
    recent: List<RecentMangaFile>,
    columns: Int,
    layout: String,
    showMetadata: Boolean,
    coverScale: String,
    cardRatio: String,
    sortMode: String,
    loading: Boolean,
    rootPath: String,
    accent: Color,
    onOpenFolder: (MangaFolderUi) -> Unit,
    onOpenRecent: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val orderedFolders = when (sortMode) {
        "name_desc" -> folders.sortedByDescending { it.title.lowercase(Locale.ROOT) }
        "progress" -> folders.sortedByDescending { it.percent }
        "recent" -> folders.sortedByDescending { folder ->
            folder.lastFile?.lastModified() ?: folder.files.maxOfOrNull { it.lastModified() } ?: 0L
        }
        else -> folders.sortedBy { it.title.lowercase(Locale.ROOT) }
    }
    Column(modifier.fillMaxSize()) {
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent, trackColor = MangaPanel)
        }
        if (folders.isEmpty() && !loading) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LibraryBooks, null, tint = accent, modifier = Modifier.size(54.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("هنوز مانگایی در این مسیر پیدا نشد", color = MangaWhite, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(rootPath, color = MangaMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
                    Text("مسیر کتابخانه را از تنظیمات تغییر بده یا پوشه‌ها را در حافظه قرار بده.", color = MangaMuted, textAlign = TextAlign.Center, fontSize = 12.sp)
                }
            }
        } else {
            when (layout) {
                "list" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(orderedFolders, key = { it.path }) { folder ->
                            FolderListCard(
                                folder = folder,
                                compact = false,
                                showMetadata = showMetadata,
                                accent = accent,
                                coverScale = coverScale
                            ) { onOpenFolder(folder) }
                        }
                    }
                }
                else -> {
                    val actualColumns = columns.coerceIn(1, 4)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(actualColumns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (layout == "compact") 7.dp else 10.dp),
                        verticalArrangement = Arrangement.spacedBy(if (layout == "compact") 8.dp else 12.dp)
                    ) {
                        gridItems(orderedFolders, key = { it.path }) { folder ->
                            FolderGridCard(
                                folder = folder,
                                showMetadata = showMetadata,
                                accent = accent,
                                compact = layout == "compact",
                                coverScale = coverScale,
                                cardRatio = cardRatio
                            ) { onOpenFolder(folder) }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun RecentReadingCard(item: RecentMangaFile, accent: Color, onClick: (File) -> Unit) {
    val percent = if (item.total > 0) item.page.toFloat() / item.total.toFloat() else 0f
    Card(
        onClick = { onClick(item.file) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MangaPanel),
        border = BorderStroke(1.dp, MangaLine)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            MangaCover(item.file, Modifier.size(width = 112.dp, height = 158.dp), 20)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("آخرین مطالعه", color = MangaWhite, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(item.file.parentFile?.name ?: item.file.nameWithoutExtension, color = MangaMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.file.name} · صفحه ${formatPersianNumber(item.page)} از ${formatPersianNumber(item.total)}", color = MangaMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                MangaProgressBar(percent, height = 7)
                Text("${formatPersianNumber((percent * 100).roundToInt())}٪ پیشرفت این فایل", color = MangaMuted, fontSize = 12.sp)
                Surface(color = Color(0xFF07331E), shape = RoundedCornerShape(24.dp), modifier = Modifier.clickable { onClick(item.file) }) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null, tint = MangaGreen, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ادامه مطالعه", color = MangaGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderGridCard(
    folder: MangaFolderUi,
    showMetadata: Boolean,
    accent: Color,
    compact: Boolean = false,
    coverScale: String = "crop",
    cardRatio: String = "poster",
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(1.5.dp, Color(0xFF1D1D1D))
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.fillMaxWidth().padding(if (compact) 6.dp else 9.dp)) {
                Box(
                    Modifier.fillMaxWidth()
                        .aspectRatio(if (cardRatio == "square") 1f else 0.75f)
                        .clip(RoundedCornerShape(if (compact) 15.dp else 20.dp))
                        .background(MangaPanelRaised)
                ) {
                    MangaCover(
                        folder.cover,
                        Modifier.fillMaxSize(),
                        if (compact) 15 else 20,
                        if (coverScale == "fit") ContentScale.Fit else ContentScale.Crop
                    )
                    if (folder.favorite) {
                        Surface(
                            color = Color(0xCC18120A),
                            shape = CircleShape,
                            modifier = Modifier.align(Alignment.TopEnd).padding(9.dp)
                        ) {
                            Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF5B68), modifier = Modifier.padding(7.dp).size(17.dp))
                        }
                    }
                }
                Spacer(Modifier.height(if (compact) 5.dp else 9.dp))
                Text(
                    folder.title,
                    modifier = Modifier.fillMaxWidth(),
                    color = MangaWhite,
                    fontSize = if (compact) 14.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Left
                )
                if (showMetadata) {
                    Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                    Text(
                        "${formatPersianNumber(folder.files.size)} فایل · ${formatPersianNumber(folder.percent)}٪",
                        modifier = Modifier.fillMaxWidth(),
                        color = MangaMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Right
                    )
                }
                Spacer(Modifier.height(8.dp))
                MangaProgressBar(
                    if (folder.totalPages > 0) folder.percent / 100f else 0f,
                    height = if (compact) 5 else 7
                )
            }
        }
    }
}
@Composable
private fun FolderListCard(
    folder: MangaFolderUi,
    compact: Boolean,
    showMetadata: Boolean,
    accent: Color,
    coverScale: String = "crop",
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MangaBlack),
        border = BorderStroke(1.dp, MangaLine)
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MangaCover(
                folder.cover,
                Modifier.size(if (compact) 70.dp else 94.dp, if (compact) 82.dp else 116.dp),
                16,
                if (coverScale == "fit") ContentScale.Fit else ContentScale.Crop
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(folder.title, color = MangaWhite, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showMetadata) Text(
                    "${formatPersianNumber(folder.files.size)} فایل" + if (folder.totalPages > 0) " · ${formatPersianNumber(folder.percent)}٪" else "",
                    color = MangaMuted, fontSize = 12.sp
                )
                MangaProgressBar(if (folder.totalPages > 0) folder.percent / 100f else 0f, height = 6)
            }
            if (folder.favorite) Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF6470), modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
private fun MangaDetailScreen(
    folder: MangaFolderUi,
    accent: Color,
    onOpenFile: (File) -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    onCoverChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coverScope = rememberCoroutineScope()
    var coverImporting by remember { mutableStateOf(false) }
    val galleryCoverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { selectedUri ->
        if (selectedUri != null) {
            coverScope.launch {
                coverImporting = true
                val savedCover = withContext(Dispatchers.IO) {
                    runCatching {
                        val directory = File(context.filesDir, "custom-manga-covers").apply { mkdirs() }
                        val extension = when (context.contentResolver.getType(selectedUri)?.lowercase(Locale.ROOT)) {
                            "image/png" -> "png"
                            "image/webp" -> "webp"
                            "image/gif" -> "gif"
                            else -> "jpg"
                        }
                        val destination = File(directory, "cover-${folder.path.hashCode()}-${System.currentTimeMillis()}.$extension")
                        val temporary = File(directory, destination.name + ".tmp")
                        context.contentResolver.openInputStream(selectedUri)?.use { input ->
                            FileOutputStream(temporary).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var copied = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    copied += count
                                    require(copied <= 32L * 1024L * 1024L) { "تصویر جلد بیشتر از ۳۲ مگابایت است." }
                                    output.write(buffer, 0, count)
                                }
                            }
                        } ?: error("فایل تصویر از گالری قابل خواندن نیست.")
                        if (!temporary.renameTo(destination)) {
                            temporary.copyTo(destination, overwrite = true)
                            temporary.delete()
                        }
                        destination
                    }
                }
                coverImporting = false
                savedCover.onSuccess { imageFile ->
                    MangaStore.setCover(context, File(folder.path), imageFile)
                    onCoverChanged()
                    Toast.makeText(context, "تصویر جلد ذخیره شد.", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(context, error.message ?: "ذخیره تصویر جلد ناموفق بود.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val lastFile = folder.lastFile
    val continueFile = lastFile
        ?: folder.files.firstOrNull { it.extension.equals("pdf", true) }
        ?: folder.files.firstOrNull { it.extension.lowercase(Locale.ROOT) in setOf("cbz", "zip", "cbr", "rar", "7z") }
        ?: folder.files.firstOrNull { it.extension.lowercase(Locale.ROOT) in ImageExtensions }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            MangaCover(folder.cover, Modifier.width(132.dp).height(190.dp), 22)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(folder.title, color = MangaWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("نام پوشه یا عنوان قابل ویرایش", color = MangaMuted, fontSize = 12.sp)
                Surface(color = Color(0xFF07331E), shape = RoundedCornerShape(24.dp)) {
                    Text(
                        when {
                            folder.percent >= 100 && folder.totalPages > 0 -> "تکمیل‌شده"
                            folder.readPages > 0 -> "در حال مطالعه"
                            else -> "هنوز شروع نشده"
                        },
                        color = MangaGreen, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp
                    )
                }
                Text(
                    "${formatPersianNumber(folder.files.size)} فایل · ${formatPersianNumber(folder.totalPages)} صفحه قابل مطالعه",
                    color = MangaMuted, fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.height(17.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailActionChip(
                text = if (folder.favorite) "علاقه‌مندشده" else "علاقه‌مندی",
                icon = if (folder.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                tint = if (folder.favorite) Color(0xFFFF5968) else MangaWhite,
                background = if (folder.favorite) Color(0xFF31170F) else MangaPanelRaised,
                modifier = Modifier.weight(1f),
                onClick = { onFavoriteChanged(!folder.favorite) }
            )
            DetailActionChip(
                text = if (coverImporting) "در حال ذخیره..." else "انتخاب جلد",
                icon = Icons.Default.Image,
                tint = MangaGold,
                background = MangaPanelRaised,
                modifier = Modifier.weight(1f),
                onClick = { if (!coverImporting) galleryCoverPicker.launch(arrayOf("image/*")) }
            )
            DetailActionChip(
                text = "اشتراک‌گذاری",
                icon = Icons.Default.Share,
                tint = MangaWhite,
                background = MangaPanelRaised,
                modifier = Modifier.weight(1f),
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, folder.title)
                    }
                    context.startActivity(Intent.createChooser(send, "اشتراک‌گذاری مانگا"))
                }
            )
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MangaLine)
        Spacer(Modifier.height(20.dp))
        Text("پیشرفت کلی مانگا", color = MangaWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(17.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${formatPersianNumber(folder.percent)}٪",
                color = MangaGreen,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "${formatPersianNumber(folder.readPages)} صفحه از ${formatPersianNumber(folder.totalPages)} صفحه",
                color = MangaMuted,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        MangaProgressBar(if (folder.totalPages > 0) folder.percent / 100f else 0f, height = 13)
        Spacer(Modifier.height(13.dp))
        Text(
            if (lastFile != null && folder.lastPage > 0) "آخرین مطالعه: ${lastFile.name} · صفحه ${formatPersianNumber(folder.lastPage)}"
            else "هنوز مطالعه‌ای برای این مانگا ثبت نشده است",
            color = MangaMuted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { continueFile?.let(onOpenFile) },
            enabled = continueFile != null,
            modifier = Modifier.fillMaxWidth().height(66.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = MangaBlack, disabledContainerColor = MangaPanelRaised)
        ) {
            Icon(Icons.Default.Bookmark, null, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (lastFile != null && folder.lastPage > 0) "ادامه مطالعه از آخرین صفحه" else "شروع مطالعه",
                fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("فایل‌های این مانگا", color = MangaWhite, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("${formatPersianNumber(folder.files.size)} فایل", color = MangaMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        if (folder.files.isEmpty()) {
            Text("فایلی در این پوشه پیدا نشد.", color = MangaMuted, modifier = Modifier.padding(vertical = 24.dp))
        } else {
            folder.files.forEach { file ->
                MangaFileProgressRow(file, accent, onClick = { onOpenFile(file) })
                Spacer(Modifier.height(9.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DetailActionChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(43.dp).clickable(onClick = onClick),
        color = background,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(Modifier.padding(horizontal = 7.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, color = tint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MangaFileProgressRow(file: File, accent: Color, onClick: () -> Unit) {
    val context = LocalContext.current
    val progress = MangaStore.getProgress(context, file)
    val percent = if (progress.total > 0) progress.page.toFloat() / progress.total.toFloat() else 0f
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MangaBlack).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MangaCover(file, Modifier.size(width = 100.dp, height = 128.dp), 16)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(file.name, color = MangaWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                progress.page > 0 && progress.total > 0 -> Text(
                    "صفحه ${formatPersianNumber(progress.page)} از ${formatPersianNumber(progress.total)} · ${formatPersianNumber((percent * 100).roundToInt())}٪",
                    color = MangaMuted, fontSize = 12.sp
                )
                file.extension.equals("pdf", true) -> Text("هنوز خوانده‌نشده", color = MangaMuted, fontSize = 12.sp)
                file.extension.lowercase(Locale.ROOT) in ImageExtensions -> Text("تصویر مانگا", color = MangaMuted, fontSize = 12.sp)
                file.extension.lowercase(Locale.ROOT) in setOf("cbz", "zip", "cbr", "rar", "7z") -> Text("هنوز خوانده‌نشده · آرشیو کمیک", color = MangaMuted, fontSize = 12.sp)
                else -> Text(file.extension.uppercase(Locale.ROOT) + " · برای مدیریت فایل", color = MangaMuted, fontSize = 12.sp)
            }
            MangaProgressBar(percent, height = 7)
        }
    }
}

@Composable
private fun RecentReadingScreen(
    recent: List<RecentMangaFile>,
    accent: Color,
    onOpen: (File) -> Unit,
    onFolder: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    if (recent.isEmpty()) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.History, null, tint = accent, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("هنوز سابقه مطالعه‌ای وجود ندارد", color = MangaWhite, fontWeight = FontWeight.Bold)
                Text("وقتی یک PDF یا فایل مانگا را داخل برنامه بخوانی، ادامه آن اینجا نمایش داده می‌شود.", color = MangaMuted, textAlign = TextAlign.Center, fontSize = 12.sp)
            }
        }
    } else {
        LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(recent, key = { it.file.absolutePath }) { item ->
                Card(
                    onClick = { onOpen(item.file) },
                    colors = CardDefaults.cardColors(containerColor = MangaPanel),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MangaLine)
                ) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        MangaCover(item.file, Modifier.size(68.dp, 88.dp), 13)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.file.parentFile?.name ?: item.file.nameWithoutExtension, color = MangaWhite, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.file.name, color = MangaMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("صفحه ${formatPersianNumber(item.page)} از ${formatPersianNumber(item.total)}", color = MangaMuted, fontSize = 11.sp)
                            MangaProgressBar(item.page.toFloat() / item.total.coerceAtLeast(1), height = 6)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    rootPath: String,
    columns: Int,
    layout: String,
    showMetadata: Boolean,
    coverScale: String,
    cardRatio: String,
    sortMode: String,
    accentName: String,
    onRootChange: (String) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onLayoutChange: (String) -> Unit,
    onMetadataChange: (Boolean) -> Unit,
    onCoverScaleChange: (String) -> Unit,
    onCardRatioChange: (String) -> Unit,
    onSortModeChange: (String) -> Unit,
    onAccentChange: (String) -> Unit,
    onOpenFileManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var rootError by remember { mutableStateOf("") }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { selectedUri ->
        if (selectedUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val selectedFolder = resolveLocalTreeDirectory(context, selectedUri)
            if (selectedFolder != null) {
                rootError = ""
                onRootChange(selectedFolder.absolutePath)
            } else {
                rootError = "این انتخاب، پوشه محلی قابل‌دسترسی نیست. لطفاً پوشه‌ای از حافظه داخلی یا کارت حافظه انتخاب کن."
            }
        }
    }

    val accentColor = when (accentName) {
        "purple" -> Color(0xFFB18AFF)
        "blue" -> Color(0xFF62B8F2)
        "green" -> Color(0xFF69C58D)
        else -> MangaGold
    }
    val sectionShape = RoundedCornerShape(22.dp)
    val selectedBorder = BorderStroke(1.5.dp, accentColor)
    val idleBorder = BorderStroke(1.dp, MangaLine)

    Column(
        modifier.fillMaxSize()
            .background(MangaBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Premium page header.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF171717),
            border = BorderStroke(1.dp, Color(0xFF34302A))
        ) {
            Box {
                Box(
                    Modifier.align(Alignment.TopEnd).size(150.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(accentColor.copy(alpha = 0.22f), Color.Transparent)
                            ),
                            CircleShape
                        )
                )
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(12.dp), color = accentColor.copy(alpha = 0.15f)) {
                            Icon(Icons.Default.Tune, null, tint = accentColor, modifier = Modifier.padding(11.dp).size(23.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("MANGA EXPERIENCE", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
                            Text("شخصی‌سازی کتابخانه", color = MangaWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Surface(shape = CircleShape, color = Color(0xFF173323)) {
                            Text("LIVE", color = Color(0xFF63D596), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                    Text(
                        "ظاهر و رفتار کتابخانه را دقیقاً مطابق سلیقه‌ات تنظیم کن. تغییرات به‌صورت زنده اعمال و ذخیره می‌شوند.",
                        color = MangaMuted,
                        fontSize = 12.sp,
                        lineHeight = 19.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GridView, null, tint = accentColor, modifier = Modifier.size(15.dp))
                        Text(
                            "$columns ستون  ·  " + when (layout) {
                                "list" -> "فهرستی"
                                "compact" -> "فشرده"
                                else -> "شبکه‌ای"
                            },
                            color = MangaWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.weight(1f))
                        Text("پیش‌نمایش زنده", color = accentColor, fontSize = 10.sp)
                    }
                    // Tiny visual preview follows the active grid/layout selection.
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF090909),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF292929))
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (layout == "list") {
                                repeat(2) {
                                    Row(
                                        Modifier.fillMaxWidth().height(42.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(Color(0xFF1A1A1A)).padding(5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(Modifier.width(29.dp).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(accentColor.copy(alpha = 0.7f)))
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Box(Modifier.fillMaxWidth(0.72f).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.8f)))
                                            Box(Modifier.fillMaxWidth(0.45f).height(4.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.6f)))
                                        }
                                    }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    repeat(columns.coerceAtMost(4)) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Box(
                                                Modifier.fillMaxWidth()
                                                    .aspectRatio(if (cardRatio == "square") 1f else 0.72f)
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(
                                                        Brush.verticalGradient(
                                                            listOf(accentColor.copy(alpha = 0.75f), Color(0xFF383838), Color(0xFF1D1D1D))
                                                        )
                                                    )
                                            )
                                            Box(Modifier.fillMaxWidth(0.78f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
                                            if (showMetadata) {
                                                Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.65f)))
                                            }
                                        }
                                    }
                                }
                            }
                            Text(
                                if (coverScale == "fit") "جلد کامل · بدون برش" else "جلد تمام‌کادر · برش متناسب",
                                color = MangaMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }

        // Layout type.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsGroup("۱. نوع چیدمان", "نحوه نمایش مجموعه مانگاها در صفحه اصلی.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val choices = listOf(
                    Triple("grid", "شبکه‌ای", Icons.Default.GridView),
                    Triple("list", "فهرستی", Icons.Default.ViewList),
                    Triple("compact", "فشرده", Icons.Default.Book)
                )
                choices.forEach { (key, title, icon) ->
                    val selected = layout == key
                    Card(
                        onClick = { onLayoutChange(key) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (selected) accentColor.copy(alpha = 0.13f) else MangaPanel),
                        border = if (selected) selectedBorder else idleBorder
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(icon, null, tint = if (selected) accentColor else MangaMuted, modifier = Modifier.size(25.dp))
                            Text(title, color = if (selected) MangaWhite else MangaMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (selected) Icon(Icons.Default.CheckCircle, null, tint = accentColor, modifier = Modifier.size(14.dp))
                            else Spacer(Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // Grid columns and cover proportions.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = sectionShape,
            color = MangaPanel,
            border = idleBorder
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                SettingsGroup("۲. اندازه و تراکم کارت‌ها", "تعداد ستون‌ها و نسبت جلدها را متناسب با نمایشگر انتخاب کن.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..4).forEach { count ->
                        val selected = columns == count
                        Card(
                            onClick = { onColumnsChange(count) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(13.dp),
                            colors = CardDefaults.cardColors(containerColor = if (selected) accentColor.copy(alpha = 0.14f) else Color(0xFF101010)),
                            border = if (selected) selectedBorder else idleBorder
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    repeat(count) {
                                        Box(
                                            Modifier.width(if (count == 4) 5.dp else 8.dp)
                                                .height(20.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(if (selected) accentColor else Color(0xFF666666))
                                        )
                                    }
                                }
                                Text("$count", color = if (selected) MangaWhite else MangaMuted, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Text("نسبت تصویر جلد", color = MangaWhite, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    listOf("poster" to "پوستری", "square" to "مربعی").forEach { (key, title) ->
                        val selected = cardRatio == key
                        FilterChip(
                            selected = selected,
                            onClick = { onCardRatioChange(key) },
                            label = { Text(title) },
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(
                                    if (key == "poster") Icons.Default.Book else Icons.Default.GridView,
                                    null
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.18f),
                                selectedLabelColor = MangaWhite,
                                labelColor = MangaMuted
                            )
                        )
                    }
                }
            }
        }

        // Cover image scaling.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = sectionShape,
            color = MangaPanel,
            border = idleBorder
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsGroup("۳. نحوه نمایش جلد", "انتخاب کن تصویر کامل دیده شود یا قاب کارت را پر کند.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("crop" to "تمام‌کادر", "fit" to "نمایش کامل").forEach { (key, title) ->
                        val selected = coverScale == key
                        Card(
                            onClick = { onCoverScaleChange(key) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp),
                            colors = CardDefaults.cardColors(containerColor = if (selected) accentColor.copy(alpha = 0.12f) else Color(0xFF101010)),
                            border = if (selected) selectedBorder else idleBorder
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier.size(width = 58.dp, height = 72.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color(0xFF343434)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth(if (key == "crop") 1f else 0.68f)
                                            .fillMaxHeight(if (key == "crop") 1f else 0.8f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Brush.linearGradient(listOf(accentColor, Color(0xFF555555))))
                                    )
                                }
                                Text(title, color = if (selected) MangaWhite else MangaMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                if (selected) Icon(Icons.Default.CheckCircle, null, tint = accentColor, modifier = Modifier.size(15.dp))
                                else Spacer(Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }

        // Sorting.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = sectionShape,
            color = MangaPanel,
            border = idleBorder
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsGroup("۴. مرتب‌سازی کتابخانه", "ترتیب نمایش مانگاها را تغییر بده.")
                val sortChoices = listOf(
                    "name_asc" to "نام: الف تا ی",
                    "name_desc" to "نام: ی تا الف",
                    "progress" to "بیشترین پیشرفت",
                    "recent" to "آخرین تغییر"
                )
                sortChoices.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (key, label) ->
                            FilterChip(
                                selected = sortMode == key,
                                onClick = { onSortModeChange(key) },
                                label = { Text(label, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor.copy(alpha = 0.16f),
                                    selectedLabelColor = MangaWhite,
                                    labelColor = MangaMuted
                                )
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // Metadata and theme colors.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = sectionShape,
            color = MangaPanel,
            border = idleBorder
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsGroup("۵. اطلاعات روی کارت‌ها", "جزئیات مطالعه را زیر هر جلد نمایش بده.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("تعداد فایل و درصد پیشرفت", color = MangaWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("نمایش آمار هر مانگا در کتابخانه", color = MangaMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = showMetadata,
                        onCheckedChange = onMetadataChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = MangaBlack, checkedTrackColor = accentColor)
                    )
                }
                HorizontalDivider(color = MangaLine)
                SettingsGroup("رنگ اصلی برنامه", "رنگ عناصر انتخاب‌شده و نوارهای پیشرفت.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "gold" to ("طلایی" to MangaGold),
                        "purple" to ("بنفش" to Color(0xFFB18AFF)),
                        "blue" to ("آبی" to Color(0xFF62B8F2)),
                        "green" to ("سبز" to Color(0xFF69C58D))
                    ).forEach { (key, pair) ->
                        val (label, color) = pair
                        val selected = accentName == key
                        Card(
                            onClick = { onAccentChange(key) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(13.dp),
                            colors = CardDefaults.cardColors(containerColor = if (selected) color.copy(alpha = 0.13f) else Color(0xFF101010)),
                            border = if (selected) BorderStroke(1.5.dp, color) else idleBorder
                        ) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(25.dp).clip(CircleShape).background(color))
                                Text(label, color = if (selected) MangaWhite else MangaMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // Root library folder.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = sectionShape,
            color = MangaPanel,
            border = idleBorder
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsGroup(
                    "۶. مسیر کتابخانه",
                    "پوشه مادر را انتخاب کن؛ پوشه‌های مستقیم داخل آن به‌عنوان مانگا نمایش داده می‌شوند."
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = accentColor.copy(alpha = 0.13f)) {
                        Icon(Icons.Default.Folder, null, tint = accentColor, modifier = Modifier.padding(10.dp).size(22.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("پوشه مادر فعلی", color = MangaWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(rootPath, color = MangaMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Button(
                    onClick = { folderPicker.launch(null) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = MangaBlack)
                ) {
                    Icon(Icons.Default.Folder, null)
                    Spacer(Modifier.width(8.dp))
                    Text("انتخاب پوشه مادر", fontWeight = FontWeight.Bold)
                }
                if (rootError.isNotBlank()) Text(rootError, color = Color(0xFFFF7777), fontSize = 11.sp)
            }
        }

        // Previous file manager retained as a dedicated tool.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = sectionShape,
            color = MangaPanel,
            border = idleBorder
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsGroup("۷. ابزارهای پیشرفته", "مدیریت مستقیم فایل‌ها و تغییر نام گروهی فصل‌ها.")
                Button(
                    onClick = onOpenFileManager,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF292929), contentColor = MangaWhite),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Folder, null)
                    Spacer(Modifier.width(8.dp))
                    Text("باز کردن فایل‌منیجر و تغییر نام")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsGroup(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = MangaWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(subtitle, color = MangaMuted, fontSize = 12.sp)
    }
}

@Composable
private fun MangaReaderScreen(file: File, accent: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findHostActivity() }
    DisposableEffect(hostActivity) {
        val window = hostActivity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousStatusColor = window?.statusBarColor
        val previousNavigationColor = window?.navigationBarColor
        val previousBehavior = controller?.systemBarsBehavior
        val previousLightStatusIcons = controller?.isAppearanceLightStatusBars
        val previousLightNavigationIcons = controller?.isAppearanceLightNavigationBars
        if (window != null && controller != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null && controller != null) {
                controller.show(WindowInsetsCompat.Type.systemBars())
                if (previousBehavior != null) controller.systemBarsBehavior = previousBehavior
                if (previousLightStatusIcons != null) controller.isAppearanceLightStatusBars = previousLightStatusIcons
                if (previousLightNavigationIcons != null) controller.isAppearanceLightNavigationBars = previousLightNavigationIcons
                WindowCompat.setDecorFitsSystemWindows(window, true)
                if (previousStatusColor != null) window.statusBarColor = previousStatusColor
                if (previousNavigationColor != null) window.navigationBarColor = previousNavigationColor
            }
        }
    }
    val savedProgress = remember(file.absolutePath) { MangaStore.getProgress(context, file) }
    var page by remember(file.absolutePath) { mutableIntStateOf(savedProgress.page.coerceAtLeast(1)) }
    var total by remember(file.absolutePath) { mutableIntStateOf(savedProgress.total.coerceAtLeast(1)) }
    var initialized by remember(file.absolutePath) { mutableStateOf(false) }
    var displayedPage by remember(file.absolutePath) { mutableStateOf<Pair<Int, ImageBitmap>?>(null) }
    val pdfSession = remember(file.absolutePath) {
        if (file.extension.equals("pdf", true)) PdfDocumentSession(file) else null
    }
    DisposableEffect(pdfSession) {
        onDispose { pdfSession?.close() }
    }
    var error by remember(file.absolutePath) { mutableStateOf("") }
    var readerMode by remember(file.absolutePath) {
        mutableStateOf(
            MangaStore.setting(context, "reader_mode", "right")
                .takeIf { it == "right" || it == "right_vertical" } ?: "right"
        )
    }
    var modeMenuOpen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(false) }
    var verticalSeekTarget by remember(file.absolutePath) { mutableIntStateOf(-1) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val pageRenderWidth = with(density) {
        (configuration.screenWidthDp.dp.toPx() * 1.25f).roundToInt()
    }.coerceIn(900, 1920)
    val pageRenderHeight = with(density) {
        (configuration.screenHeightDp.dp.toPx() * 1.25f).roundToInt()
    }.coerceIn(1300, 3400)
    val listState = rememberLazyListState()
    BackHandler(onBack = onBack)

    // Tap once to show the controls; tap again to hide them.

    LaunchedEffect(file.absolutePath, pageRenderWidth, pageRenderHeight) {
        val count = withContext(Dispatchers.IO) {
            runCatching {
                if (!file.exists() || !file.isFile) return@runCatching 0
                when (file.extension.lowercase(Locale.ROOT)) {
                    "pdf" -> pdfSession?.prepareSlices(pageRenderWidth, pageRenderHeight) ?: 0
                    "cbz", "zip", "cbr", "rar", "7z" -> MangaArchivePages.pages(context, file).size
                    in ImageExtensions -> 1
                    else -> 0
                }
            }.getOrDefault(0)
        }
        if (count > 0) {
            total = count
            MangaStore.savePageCount(context, file, count)
            page = MangaStore.getProgress(context, file).page.coerceIn(1, count)
            initialized = true
        } else {
            error = "این PDF یا فایل مانگا قابل خواندن نیست. ممکن است فایل خراب، رمزگذاری‌شده یا ناقص باشد."
        }
    }

    LaunchedEffect(file.absolutePath, page, initialized, readerMode, pageRenderWidth, pageRenderHeight) {
        if (initialized && readerMode == "right") {
            val targetPage = page
            val rendered = withContext(Dispatchers.IO) {
                renderMangaPage(context, file, targetPage - 1, pageRenderWidth, pdfSession)
            }
            if (rendered != null) {
                displayedPage = targetPage to rendered.asImageBitmap()
                MangaStore.saveProgress(context, file, targetPage, total)
                error = ""
                if (targetPage < total) {
                    launch(Dispatchers.IO) {
                        runCatching {
                            renderMangaPage(context, file, targetPage, pageRenderWidth, pdfSession)
                        }
                    }
                }
            } else {
                error = "نمایش این بخش ناموفق بود؛ فایل را بررسی کن."
            }
        }
    }

    LaunchedEffect(readerMode, initialized, verticalSeekTarget) {
        if (initialized && readerMode == "right_vertical") {
            val target = if (verticalSeekTarget >= 0) verticalSeekTarget else page - 1
            listState.scrollToItem(target.coerceIn(0, (total - 1).coerceAtLeast(0)))
            verticalSeekTarget = -1
        }
    }

    LaunchedEffect(readerMode, initialized, listState, total) {
        if (initialized && readerMode == "right_vertical") {
            snapshotFlow { listState.firstVisibleItemIndex + 1 }
                .distinctUntilChanged()
                .collect { visiblePage ->
                    val safePage = visiblePage.coerceIn(1, total)
                    page = safePage
                    MangaStore.saveProgress(context, file, safePage, total)
                }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                        }
                    )
                }
        ) {
            // The actual reading canvas uses the full screen. Top and bottom controls
            // float above it, instead of shrinking the page viewport.
            if (!initialized) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (error.isNotBlank()) {
                        Text(error, color = MangaMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                    } else {
                        CircularProgressIndicator(color = accent)
                    }
                }
            } else if (readerMode == "right_vertical") {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().background(Color(0xFF090909)),
                    contentPadding = PaddingValues(0.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(total, key = { it }) { index ->
                        MangaReaderPageItem(
                            file = file,
                            pageIndex = index,
                            targetWidth = pageRenderWidth,
                            sliceHeightKey = pageRenderHeight,
                            pdfSession = pdfSession
                        )
                    }
                }
            } else {
                Box(
                    Modifier.fillMaxSize().background(Color(0xFF090909))
                        .pointerInput(page, total, initialized, readerMode) {
                            var dragDistance = 0f
                            val swipeThreshold = maxOf(72.dp.toPx(), size.width * 0.18f)
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, amount ->
                                    dragDistance += amount
                                    change.consume()
                                },
                                onDragEnd = {
                                    // Right-to-left: advance only after a deliberate left swipe.
                                    // Tapping the page only reveals the temporary controls.
                                    if (initialized && dragDistance <= -swipeThreshold) {
                                        page = (page + 1).coerceAtMost(total)
                                    } else if (initialized && dragDistance >= swipeThreshold) {
                                        page = (page - 1).coerceAtLeast(1)
                                    }
                                    dragDistance = 0f
                                },
                                onDragCancel = { dragDistance = 0f }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (displayedPage != null) {
                        AnimatedContent(
                            targetState = displayedPage,
                            transitionSpec = {
                                val previousPageState = initialState
                                val nextPageState = targetState
                                val forward = previousPageState?.let { previous ->
                                    nextPageState?.let { next -> next.first > previous.first }
                                } ?: true
                                if (forward) {
                                    (slideInHorizontally(tween(240)) { it } + fadeIn(tween(180))) togetherWith
                                        (slideOutHorizontally(tween(240)) { -it } + fadeOut(tween(180)))
                                } else {
                                    (slideInHorizontally(tween(240)) { -it } + fadeIn(tween(180))) togetherWith
                                        (slideOutHorizontally(tween(240)) { it } + fadeOut(tween(180)))
                                }
                            },
                            label = "manga-page-turn"
                        ) { shown ->
                            if (shown != null) {
                                Image(
                                    shown.second,
                                    contentDescription = "بخش ${shown.first}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.FillBounds
                                )
                            }
                        }
                    } else if (error.isNotBlank()) {
                        Text(error, color = MangaMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                    } else {
                        CircularProgressIndicator(color = accent)
                    }
                }
            }

            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = slideInVertically(tween(160)) { -it } + fadeIn(tween(140)),
                exit = slideOutVertically(tween(180)) { -it } + fadeOut(tween(160))
            ) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xE6111111))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "بازگشت", tint = MangaWhite)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(file.name, color = MangaWhite, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (initialized) "${if (pdfSession?.hasScreenSlices() == true) "بخش" else "صفحه"} ${formatPersianNumber(page)} از ${formatPersianNumber(total)}"
                            else "در حال آماده‌سازی…",
                            color = MangaMuted,
                            fontSize = 11.sp
                        )
                    }
                    Box {
                        IconButton(onClick = { modeMenuOpen = true }) {
                            Icon(Icons.Default.MoreVert, "حالت مطالعه", tint = accent)
                        }
                        DropdownMenu(
                            expanded = modeMenuOpen,
                            onDismissRequest = { modeMenuOpen = false },
                            containerColor = MangaPanel
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("راست‌به‌چپ", color = if (readerMode == "right") accent else MangaWhite)
                                        Text("کشیدن صفحه به چپ برای رفتن به بخش بعد", color = MangaMuted, fontSize = 11.sp)
                                    }
                                },
                                onClick = {
                                    readerMode = "right"
                                    MangaStore.setSetting(context, "reader_mode", readerMode)
                                    modeMenuOpen = false
                                    },
                                leadingIcon = { Icon(Icons.Default.ChevronRight, null, tint = accent) }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("راست‌به‌چپ + عمودی", color = if (readerMode == "right_vertical") accent else MangaWhite)
                                        Text("بخش‌ها پشت سر هم و اسکرولی", color = MangaMuted, fontSize = 11.sp)
                                    }
                                },
                                onClick = {
                                    readerMode = "right_vertical"
                                    MangaStore.setSetting(context, "reader_mode", readerMode)
                                    modeMenuOpen = false
                                    controlsVisible = true
                                    },
                                leadingIcon = { Icon(Icons.Default.ViewList, null, tint = accent) }
                            )
                            if (file.extension.equals("pdf", true)) {
                                HorizontalDivider(color = MangaLine)
                                DropdownMenuItem(
                                    text = { Text("باز کردن با ComicScreen") },
                                    onClick = {
                                        modeMenuOpen = false
                                        openPdfWithComicScreen(context, file)
                                    },
                                    leadingIcon = { Icon(Icons.Default.OpenInNew, null, tint = accent) }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(160)) { it } + fadeIn(tween(140)),
                exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(160))
            ) {
                Column(
                    Modifier.fillMaxWidth().background(Color(0xE6111111))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                page = (page + 1).coerceAtMost(total)
                                if (readerMode == "right_vertical") verticalSeekTarget = page - 1
                                },
                            enabled = initialized && page < total
                        ) {
                            Icon(Icons.Default.ChevronRight, "صفحه بعد", tint = if (page < total) MangaWhite else MangaMuted)
                        }
                        Text(
                            "${formatPersianNumber(page)} / ${formatPersianNumber(total)}",
                            color = MangaWhite,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                page = (page - 1).coerceAtLeast(1)
                                if (readerMode == "right_vertical") verticalSeekTarget = page - 1

                            },
                            enabled = initialized && page > 1
                        ) {
                            Icon(Icons.Default.ChevronLeft, "صفحه قبل", tint = if (page > 1) MangaWhite else MangaMuted)
                        }
                    }
                    if (initialized && total > 1) {
                        Slider(
                            value = page.toFloat().coerceIn(1f, total.toFloat()),
                            onValueChange = {
                                page = it.roundToInt().coerceIn(1, total)
                                if (readerMode == "right_vertical") verticalSeekTarget = page - 1

                            },
                            valueRange = 1f..total.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = MangaLine)
                        )
                    }
                    Text(
                        "${if (readerMode == "right_vertical") "حالت راست‌به‌چپ + عمودی" else "حالت راست‌به‌چپ"} · پیشرفت این فایل: ${formatPersianNumber(if (total > 0) (page * 100 / total) else 0)}٪",
                        color = MangaMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun MangaReaderPageItem(
    file: File,
    pageIndex: Int,
    targetWidth: Int,
    sliceHeightKey: Int,
    pdfSession: PdfDocumentSession?
) {
    val context = LocalContext.current
    var loaded by remember(file.absolutePath, pageIndex, file.length(), file.lastModified()) { mutableStateOf(false) }
    val image by produceState<ImageBitmap?>(
        null,
        file.absolutePath,
        pageIndex,
        file.length(),
        file.lastModified(),
        targetWidth,
        sliceHeightKey
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                renderMangaPage(context, file, pageIndex, targetWidth, pdfSession)?.asImageBitmap()
            }.getOrNull()
        }
        loaded = true
    }
    if (image != null) {
        Image(
            image!!,
            contentDescription = "صفحه ${pageIndex + 1}",
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            contentScale = ContentScale.FillWidth
        )
    } else {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            if (loaded) {
                Text("نمایش این صفحه ناموفق بود", color = MangaMuted, fontSize = 12.sp)
            } else {
                CircularProgressIndicator(color = MangaGold, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun MangaTitleDialog(
    context: Context,
    folder: MangaFolderUi,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var title by remember(folder.path) { mutableStateOf(folder.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MangaPanel,
        title = { Text("عنوان نمایشی مانگا", color = MangaWhite) },
        text = {
            OutlinedTextField(
                value = title, onValueChange = { title = it }, singleLine = true, label = { Text("نام مانگا") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MangaGold, focusedTextColor = MangaWhite, unfocusedTextColor = MangaWhite)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) {
                    MangaStore.setTitle(context, File(folder.path), title)
                    onSaved()
                }
            }) { Text("ذخیره", color = MangaGold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف", color = MangaMuted) } }
    )
}

@Composable
private fun MangaCoverPickerDialog(
    context: Context,
    folder: MangaFolderUi,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val candidates = folder.files.filter { it.extension.lowercase(Locale.ROOT) in ImageExtensions || it.extension.equals("pdf", true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MangaPanel,
        title = { Text("انتخاب تصویر جلد", color = MangaWhite) },
        text = {
            if (candidates.isEmpty()) Text("تصویر یا PDF برای ساخت جلد پیدا نشد.", color = MangaMuted)
            else LazyColumn(Modifier.heightIn(max = 390.dp)) {
                items(candidates, key = { it.absolutePath }) { file ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            MangaStore.setCover(context, File(folder.path), file)
                            onSaved()
                        }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MangaCover(file, Modifier.size(48.dp, 64.dp), 8)
                        Spacer(Modifier.width(10.dp))
                        Text(file.name, color = MangaWhite, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن", color = MangaGold) } }
    )
}
