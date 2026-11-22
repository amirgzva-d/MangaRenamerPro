package com.amirgzva.mangarenamerpro

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
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
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt

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

private fun pdfPageCount(file: File): Int {
    if (!file.isFile || !file.extension.equals("pdf", true)) return 0
    return runCatching {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        val count = renderer.pageCount
        renderer.close()
        descriptor.close()
        count
    }.getOrDefault(0)
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

private fun loadCoverBitmap(context: Context, file: File): Bitmap? {
    if (!file.exists()) return null
    val cache = File(context.cacheDir, "manga-cover-${file.absolutePath.hashCode()}-${file.length()}-${file.lastModified()}.jpg")
    if (cache.exists() && cache.length() > 0L) return BitmapFactory.decodeFile(cache.absolutePath)
    val bitmap = if (file.extension.lowercase(Locale.ROOT) in ImageExtensions) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 720 || bounds.outHeight / sample > 960) sample *= 2
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    } else if (file.extension.equals("pdf", true)) {
        runCatching {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(descriptor)
            if (renderer.pageCount == 0) {
                renderer.close()
                descriptor.close()
                null
            } else {
                val page = renderer.openPage(0)
                val width = 520
                val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(output, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                descriptor.close()
                output
            }
        }.getOrNull()
    } else if (file.extension.lowercase(Locale.ROOT) in setOf("cbz", "zip", "cbr", "rar", "7z")) {
        MangaArchivePages.pages(context, file).firstOrNull()?.let(::loadImagePage)
    } else null
    if (bitmap != null) {
        runCatching {
            cache.parentFile?.mkdirs()
            FileOutputStream(cache).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        }
        return bitmap
    }
    return null
}

private fun renderMangaPage(context: Context, file: File, pageIndex: Int, targetWidth: Int): Bitmap? {
    if (!file.extension.equals("pdf", true)) {
        if (file.extension.lowercase(Locale.ROOT) in setOf("cbz", "zip", "cbr", "rar", "7z")) {
            return MangaArchivePages.pages(context, file).getOrNull(pageIndex)?.let(::loadImagePage)
        }
        return loadImagePage(file)
    }
    return runCatching {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        if (renderer.pageCount == 0) {
            renderer.close()
            descriptor.close()
            return@runCatching null
        }
        val index = pageIndex.coerceIn(0, renderer.pageCount - 1)
        val page = renderer.openPage(index)
        val width = targetWidth.coerceIn(480, 1800).coerceAtMost(1800)
        val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderer.close()
        descriptor.close()
        bitmap
    }.getOrNull()
}

private fun loadImagePage(file: File): Bitmap? {
    if (file.extension.lowercase(Locale.ROOT) !in ImageExtensions) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1800 || bounds.outHeight / sample > 2400) sample *= 2
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}

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
private fun MangaCover(file: File?, modifier: Modifier = Modifier, radius: Int = 18) {
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
            Image(image!!, contentDescription = file?.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
    Box(
        modifier.fillMaxWidth().height(height.dp).clip(CircleShape).background(Color(0xFF303030))
    ) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f))
                .align(Alignment.CenterEnd).clip(CircleShape).background(MangaGold)
        )
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
    var columns by remember { mutableIntStateOf(MangaStore.settingInt(context, "columns", 2).coerceIn(1, 4)) }
    var layout by remember { mutableStateOf(MangaStore.setting(context, "layout", "grid")) }
    var showMetadata by remember { mutableStateOf(MangaStore.setting(context, "show_metadata", "true") != "false") }
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
                                    onFollowingChanged = { enabled ->
                                        MangaStore.setFollowing(context, detailFolder!!.path, enabled)
                                        refreshKey++
                                    },
                                    modifier = Modifier.padding(inner)
                                )
                            }
                        }
                        activeTab == "settings" -> SettingsScreen(
                            rootPath = rootPath,
                            columns = columns,
                            layout = layout,
                            showMetadata = showMetadata,
                            accentName = accentName,
                            onRootChange = { value -> rootPath = value; MangaStore.setLibraryRoot(context, value); refreshKey++ },
                            onColumnsChange = { columns = it; MangaStore.setSetting(context, "columns", it) },
                            onLayoutChange = { layout = it; MangaStore.setSetting(context, "layout", it) },
                            onMetadataChange = { showMetadata = it; MangaStore.setSetting(context, "show_metadata", it.toString()) },
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
    loading: Boolean,
    rootPath: String,
    accent: Color,
    onOpenFolder: (MangaFolderUi) -> Unit,
    onOpenRecent: (File) -> Unit,
    modifier: Modifier = Modifier
) {
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
        } else if (layout == "list" || layout == "compact") {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (recent.isNotEmpty() && layout == "list") item {
                    RecentReadingCard(recent.first(), accent, onOpenRecent)
                }
                items(folders, key = { it.path }) { folder ->
                    FolderListCard(folder, compact = layout == "compact", showMetadata = showMetadata, accent = accent, onClick = { onOpenFolder(folder) })
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns.coerceIn(1, 4)),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (recent.isNotEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        RecentReadingCard(recent.first(), accent, onOpenRecent)
                    }
                }
                gridItems(folders, key = { it.path }) { folder ->
                    FolderGridCard(folder, showMetadata, accent) { onOpenFolder(folder) }
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
private fun FolderGridCard(folder: MangaFolderUi, showMetadata: Boolean, accent: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MangaBlack),
        border = BorderStroke(1.5.dp, MangaLine)
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(0.77f).clip(RoundedCornerShape(24.dp))) {
                MangaCover(folder.cover, Modifier.fillMaxSize(), 24)
                if (folder.favorite) {
                    Surface(color = Color(0xCC18120A), shape = CircleShape, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF5B68), modifier = Modifier.padding(7.dp).size(17.dp))
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(folder.title, color = MangaWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showMetadata) {
                Spacer(Modifier.height(3.dp))
                Text(
                    if (folder.totalPages > 0) "${formatPersianNumber(folder.files.size)} فایل · ${formatPersianNumber(folder.percent)}٪"
                    else "${formatPersianNumber(folder.files.size)} فایل",
                    color = MangaMuted, fontSize = 13.sp, maxLines = 1
                )
            }
            Spacer(Modifier.height(8.dp))
            MangaProgressBar(if (folder.totalPages > 0) folder.percent / 100f else 0f, height = 7)
        }
    }
}

@Composable
private fun FolderListCard(folder: MangaFolderUi, compact: Boolean, showMetadata: Boolean, accent: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MangaBlack),
        border = BorderStroke(1.dp, MangaLine)
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MangaCover(folder.cover, Modifier.size(if (compact) 70.dp else 94.dp, if (compact) 82.dp else 116.dp), 16)
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
    onFollowingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
                text = if (folder.following) "دنبال می‌کنم" else "دنبال کردن",
                icon = Icons.Default.CheckCircle,
                tint = if (folder.following) MangaGreen else MangaWhite,
                background = if (folder.following) Color(0xFF07331E) else MangaPanelRaised,
                modifier = Modifier.weight(1f),
                onClick = { onFollowingChanged(!folder.following) }
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
    accentName: String,
    onRootChange: (String) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onLayoutChange: (String) -> Unit,
    onMetadataChange: (Boolean) -> Unit,
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
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SettingsGroup("چیدمان کتابخانه", "تعداد ستون‌ها و نوع نمایش کارت‌ها را مطابق سلیقه‌ات تنظیم کن.")
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            (1..4).forEach { count ->
                FilterChip(
                    selected = columns == count,
                    onClick = { onColumnsChange(count) },
                    label = { Text("$count ستون") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MangaGold, selectedLabelColor = MangaBlack)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FilterChip(selected = layout == "grid", onClick = { onLayoutChange("grid") }, label = { Icon(Icons.Default.GridView, null); Spacer(Modifier.width(5.dp)); Text("شبکه‌ای") })
            FilterChip(selected = layout == "list", onClick = { onLayoutChange("list") }, label = { Icon(Icons.Default.ViewList, null); Spacer(Modifier.width(5.dp)); Text("فهرستی") })
            FilterChip(selected = layout == "compact", onClick = { onLayoutChange("compact") }, label = { Text("فشرده") })
        }
        HorizontalDivider(color = MangaLine)
        SettingsGroup("اطلاعات کارت‌ها", "نمایش تعداد فایل‌ها و درصد پیشرفت زیر هر جلد.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("نمایش تعداد فایل و پیشرفت", color = MangaWhite)
            Switch(checked = showMetadata, onCheckedChange = onMetadataChange, colors = SwitchDefaults.colors(checkedThumbColor = MangaBlack, checkedTrackColor = MangaGold))
        }
        HorizontalDivider(color = MangaLine)
        SettingsGroup("رنگ تأکیدی", "رنگ خطوط پیشرفت، آیکون انتخاب‌شده و دکمه اصلی.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("gold" to MangaGold, "purple" to Color(0xFFB18AFF), "blue" to Color(0xFF62B8F2), "green" to Color(0xFF69C58D)).forEach { (key, color) ->
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(color)
                        .border(if (accentName == key) 3.dp else 0.dp, MangaWhite, CircleShape)
                        .clickable { onAccentChange(key) },
                    contentAlignment = Alignment.Center
                ) {
                    if (accentName == key) Icon(Icons.Default.CheckCircle, null, tint = MangaBlack)
                }
            }
        }
        HorizontalDivider(color = MangaLine)
        SettingsGroup(
            "پوشه مادر کتابخانه",
            "یک پوشه اصلی انتخاب کن که داخل آن پوشه‌های مانگا قرار دارند. نام هر پوشه، نام همان مانگا می‌شود و فایل‌های داخل آن فصل‌های همان مانگا هستند."
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MangaPanel,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MangaLine)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, tint = MangaGold)
                    Spacer(Modifier.width(8.dp))
                    Text("مسیر انتخاب‌شده", color = MangaWhite, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    rootPath,
                    color = MangaMuted,
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "ساختار مورد انتظار: پوشه مادر ← پوشه هر مانگا ← فایل‌های فصل",
                    color = MangaGold,
                    fontSize = 11.sp
                )
            }
        }
        Button(
            onClick = { folderPicker.launch(null) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MangaGold, contentColor = MangaBlack)
        ) {
            Icon(Icons.Default.Folder, null)
            Spacer(Modifier.width(8.dp))
            Text("انتخاب پوشه مادر", fontWeight = FontWeight.Bold)
        }
        if (rootError.isNotBlank()) {
            Text(rootError, color = Color(0xFFFF7777), fontSize = 12.sp)
        }
        HorizontalDivider(color = MangaLine)
        SettingsGroup("مدیریت فایل‌ها", "ابزار قبلی تغییر نام گروهی و نمایش تمام فایل‌ها حفظ شده است.")
        Button(
            onClick = onOpenFileManager,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MangaPanelRaised, contentColor = MangaWhite),
            shape = RoundedCornerShape(15.dp)
        ) {
            Icon(Icons.Default.Folder, null)
            Spacer(Modifier.width(8.dp))
            Text("باز کردن فایل‌منیجر و تغییر نام")
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
    val savedProgress = remember(file.absolutePath) { MangaStore.getProgress(context, file) }
    var page by remember(file.absolutePath) { mutableIntStateOf(savedProgress.page.coerceAtLeast(1)) }
    var total by remember(file.absolutePath) { mutableIntStateOf(savedProgress.total.coerceAtLeast(1)) }
    var initialized by remember(file.absolutePath) { mutableStateOf(false) }
    var pageImage by remember(file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(file.absolutePath) { mutableStateOf("") }
    var readerMode by remember(file.absolutePath) {
        mutableStateOf(MangaStore.setting(context, "reader_mode", "right"))
    }
    var modeMenuOpen by remember { mutableStateOf(false) }
    var verticalSeekTarget by remember(file.absolutePath) { mutableIntStateOf(-1) }
    val density = LocalDensity.current
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (savedProgress.page - 1).coerceAtLeast(0))
    BackHandler(onBack = onBack)

    LaunchedEffect(file.absolutePath) {
        val count = withContext(Dispatchers.IO) {
            when (file.extension.lowercase(Locale.ROOT)) {
                "pdf" -> pdfPageCount(file)
                "cbz", "zip", "cbr", "rar", "7z" -> MangaArchivePages.pages(context, file).size
                in ImageExtensions -> 1
                else -> 0
            }
        }
        if (count > 0) {
            total = count
            MangaStore.savePageCount(context, file, count)
            page = MangaStore.getProgress(context, file).page.coerceIn(1, count)
            initialized = true
        } else {
            error = "این فایل قابل باز شدن نیست؛ فرمت یا محتوای آن پشتیبانی نمی‌شود."
        }
    }

    LaunchedEffect(file.absolutePath, page, initialized, readerMode) {
        if (initialized && readerMode == "right") {
            pageImage = null
            val widthPx = with(density) { 1500.dp.roundToPx() }
            val rendered = withContext(Dispatchers.IO) {
                renderMangaPage(context, file, page - 1, widthPx)
            }
            if (rendered != null) {
                pageImage = rendered.asImageBitmap()
                MangaStore.saveProgress(context, file, page, total)
                error = ""
            } else {
                error = "نمایش این صفحه ناموفق بود؛ فایل را بررسی کن."
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
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF111111))
                    .statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "بازگشت", tint = MangaWhite)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(file.name, color = MangaWhite, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (initialized) "صفحه ${formatPersianNumber(page)} از ${formatPersianNumber(total)}"
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
                                    Text("ورق‌زدن صفحه‌ای با حرکت به راست", color = MangaMuted, fontSize = 11.sp)
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
                                    Text("صفحه‌ها پشت سر هم و اسکرولی", color = MangaMuted, fontSize = 11.sp)
                                }
                            },
                            onClick = {
                                readerMode = "right_vertical"
                                MangaStore.setSetting(context, "reader_mode", readerMode)
                                modeMenuOpen = false
                            },
                            leadingIcon = { Icon(Icons.Default.ViewList, null, tint = accent) }
                        )
                    }
                }
            }

            if (!initialized) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (error.isNotBlank()) {
                        Text(error, color = MangaMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                    } else {
                        CircularProgressIndicator(color = accent)
                    }
                }
            } else if (readerMode == "right_vertical") {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF090909)),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(total, key = { it }) { index ->
                        MangaReaderPageItem(
                            file = file,
                            pageIndex = index,
                            targetWidth = with(density) { 1500.dp.roundToPx() }
                        )
                    }
                }
            } else {
                Box(
                    Modifier.weight(1f).fillMaxWidth().background(Color(0xFF090909))
                        .pointerInput(page, total, initialized, readerMode) {
                            var dragDistance = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, amount ->
                                    dragDistance += amount
                                    change.consume()
                                },
                                onDragEnd = {
                                    if (initialized && dragDistance > 55f) {
                                        page = (page + 1).coerceAtMost(total)
                                    } else if (initialized && dragDistance < -55f) {
                                        page = (page - 1).coerceAtLeast(1)
                                    }
                                    dragDistance = 0f
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (pageImage != null) {
                        Image(
                            pageImage!!,
                            contentDescription = "صفحه ${page}",
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else if (error.isNotBlank()) {
                        Text(error, color = MangaMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                    } else {
                        CircularProgressIndicator(color = accent)
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().background(Color(0xFF111111))
                    .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            val nextPage = (page + 1).coerceAtMost(total)
                            page = nextPage
                            if (readerMode == "right_vertical") verticalSeekTarget = nextPage - 1
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
                            val previousPage = (page - 1).coerceAtLeast(1)
                            page = previousPage
                            if (readerMode == "right_vertical") verticalSeekTarget = previousPage - 1
                        },
                        enabled = initialized && page > 1
                    ) {
                        Icon(Icons.Default.ChevronLeft, "صفحه قبل", tint = if (page > 1) MangaWhite else MangaMuted)
                    }
                }
                if (initialized && total > 1) {
                    Slider(
                        value = page.toFloat().coerceIn(1f, total.toFloat()),
                        onValueChange = { selected ->
                            page = selected.roundToInt().coerceIn(1, total)
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

@Composable
private fun MangaReaderPageItem(file: File, pageIndex: Int, targetWidth: Int) {
    val context = LocalContext.current
    var loaded by remember(file.absolutePath, pageIndex, file.length(), file.lastModified()) { mutableStateOf(false) }
    val image by produceState<ImageBitmap?>(
        null,
        file.absolutePath,
        pageIndex,
        file.length(),
        file.lastModified()
    ) {
        value = withContext(Dispatchers.IO) {
            renderMangaPage(context, file, pageIndex, targetWidth)?.asImageBitmap()
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
