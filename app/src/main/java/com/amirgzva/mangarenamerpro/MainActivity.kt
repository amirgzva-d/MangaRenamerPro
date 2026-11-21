package com.amirgzva.mangarenamerpro

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val Ink = Color(0xFF07140F)
private val Panel = Color(0xFF10251B)
private val Panel2 = Color(0xFF183629)
private val Green = Color(0xFF62D6A5)
private val Gold = Color(0xFFE3BD69)
private val Muted = Color(0xFFA8C2B3)
private val ImageExt = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "avif")
private val KnownExt = ImageExt + setOf("pdf", "cbz", "cbr", "zip", "rar", "7z", "epub")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MangaRenamerApp() }
    }
}

private data class RenamePreview(
    val uri: String,
    val oldName: String,
    val newName: String,
    val warning: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MangaRenamerApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("manga_renamer", Context.MODE_PRIVATE) }
    var rootUri by remember { mutableStateOf(prefs.getString("tree_uri", null)?.let { Uri.parse(it) }) }
    var currentUri by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var columns by remember { mutableIntStateOf(prefs.getInt("columns", 2).coerceIn(1, 4)) }
    var showHidden by remember { mutableStateOf(false) }
    var favoritesOnly by remember { mutableStateOf(false) }
    val favorites = remember { mutableStateListOf<String>().apply { addAll(prefs.getStringSet("favorites", emptySet()) ?: emptySet()) } }
    val hidden = remember { mutableStateListOf<String>().apply { addAll(prefs.getStringSet("hidden", emptySet()) ?: emptySet()) } }
    val pinned = remember { mutableStateListOf<String>().apply { addAll(prefs.getStringSet("pinned", emptySet()) ?: emptySet()) } }
    val selected = remember { mutableStateListOf<String>() }
    var renameMode by remember { mutableIntStateOf(0) }
    var prefix by remember { mutableStateOf("Ch") }
    var fixedText by remember { mutableStateOf("Chapter ") }
    var fixedEnabled by remember { mutableStateOf(true) }
    var manualNumbers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var preview by remember { mutableStateOf<List<RenamePreview>>(emptyList()) }
    var status by remember { mutableStateOf("پوشه مانگا را انتخاب کنید تا شروع کنیم.") }
    var readerPages by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var readerIndex by remember { mutableIntStateOf(0) }
    var readerTitle by remember { mutableStateOf("") }
    var audioUri by remember { mutableStateOf(prefs.getString("audio_uri", null)?.let { Uri.parse(it) }) }
    var musicOn by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                rootUri = uri
                currentUri = null
                prefs.edit().putString("tree_uri", uri.toString()).apply()
                refresh++
                status = "پوشه با موفقیت اضافه شد."
            } catch (e: Exception) { status = "دسترسی به پوشه برقرار نشد: ${e.message}" }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                audioUri = uri
                prefs.edit().putString("audio_uri", uri.toString()).apply()
                status = "موسیقی پس‌زمینه انتخاب شد."
            } catch (e: Exception) { status = "انتخاب موسیقی ناموفق بود." }
        }
    }

    val currentFolder = remember(rootUri, currentUri, refresh) {
        val uri = currentUri?.let { Uri.parse(it) } ?: rootUri
        uri?.let { DocumentFile.fromTreeUri(context, it) }
    }
    val allFiles = remember(currentFolder, refresh) {
        currentFolder?.listFiles()?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" }) ?: emptyList()
    }
    val visibleFiles = allFiles.filter { doc ->
        val id = doc.uri.toString()
        (showHidden || id !in hidden) && (!favoritesOnly || id in favorites)
    }
    val lastRead = prefs.getString("last_read_uri", null)
    val lastParent = prefs.getString("last_parent_uri", null)
    val lastIndex = prefs.getInt("last_page", 0)

    fun saveSet(key: String, values: List<String>) {
        prefs.edit().putStringSet(key, values.toSet()).apply()
    }
    fun toggleValue(list: MutableList<String>, key: String, prefKey: String) {
        if (key in list) list.remove(key) else list.add(key)
        saveSet(prefKey, list)
    }
    fun openReader(doc: DocumentFile) {
        val ext = doc.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        if (ext in ImageExt) {
            val parent = doc.parentFile ?: currentFolder
            val pages = parent?.listFiles()?.filter { it.isFile && (it.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty() in ImageExt) }
                ?.sortedBy { it.name?.lowercase(Locale.ROOT) } ?: listOf(doc)
            readerPages = pages
            readerIndex = pages.indexOfFirst { it.uri == doc.uri }.coerceAtLeast(0)
            readerTitle = parent?.name ?: "مطالعه"
            prefs.edit().putString("last_read_uri", doc.uri.toString())
                .putString("last_parent_uri", parent?.uri?.toString()).putInt("last_page", readerIndex).apply()
            tab = 3
        } else {
            val mime = when (ext) {
                "pdf" -> "application/pdf"
                "cbz" -> "application/vnd.comicbook+zip"
                "cbr", "rar" -> "application/vnd.rar"
                "zip" -> "application/zip"
                "epub" -> "application/epub+zip"
                else -> "*/*"
            }
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(doc.uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                prefs.edit().putString("last_read_uri", doc.uri.toString())
                    .putString("last_parent_uri", doc.parentFile?.uri?.toString()).putInt("last_page", 0).apply()
            } catch (_: Exception) { status = "برای باز کردن این فرمت، برنامه مطالعه مناسب نصب کنید." }
        }
    }

    val darkScheme = darkColorScheme(
        primary = Green, secondary = Gold, background = Ink, surface = Panel,
        onPrimary = Ink, onSecondary = Ink, onBackground = Color(0xFFF1F7F3),
        onSurface = Color(0xFFF1F7F3), surfaceVariant = Panel2
    )

    MaterialTheme(colorScheme = darkScheme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(
                containerColor = Ink,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("MANGA RENAMER PRO", fontSize = 12.sp, color = Green, fontWeight = FontWeight.Bold)
                                Text("مدیریت هوشمند مانگا", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Ink),
                        actions = {
                            IconButton(onClick = { folderPicker.launch(rootUri) }) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = "انتخاب پوشه", tint = Green)
                            }
                        }
                    )
                },
                bottomBar = {
                    if (tab != 3) NavigationBar(containerColor = Panel) {
                        NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.CollectionsBookmark, null) }, label = { Text("کتابخانه") })
                        NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.DriveFileRenameOutline, null) }, label = { Text("تغییر نام") })
                        NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("تنظیمات") })
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
                    when (tab) {
                        0 -> LibraryScreen(
                            files = visibleFiles, rootUri = rootUri, currentUri = currentUri,
                            favorites = favorites, hidden = hidden, pinned = pinned,
                            showHidden = showHidden, favoritesOnly = favoritesOnly,
                            columns = columns, lastRead = lastRead,
                            onPickFolder = { folderPicker.launch(rootUri) },
                            onOpenFolder = { currentUri = it.uri.toString(); favoritesOnly = false; refresh++ },
                            onBack = { currentUri = null; favoritesOnly = false; refresh++ },
                            onOpen = ::openReader,
                            onFavorite = { toggleValue(favorites, it, "favorites") },
                            onHide = { toggleValue(hidden, it, "hidden"); refresh++ },
                            onPin = { toggleValue(pinned, it, "pinned") },
                            onShowHidden = { showHidden = it },
                            onFavoritesOnly = { favoritesOnly = it },
                            onColumns = { columns = it; prefs.edit().putInt("columns", it).apply() },
                            onContinue = {
                                val parent = lastParent?.let { Uri.parse(it) }?.let { DocumentFile.fromTreeUri(context, it) }
                                val pages = parent?.listFiles()?.filter { it.isFile && (it.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty() in ImageExt) }
                                    ?.sortedBy { it.name?.lowercase(Locale.ROOT) } ?: emptyList()
                                if (pages.isNotEmpty()) {
                                    readerPages = pages
                                    readerIndex = lastIndex.coerceIn(0, pages.lastIndex)
                                    readerTitle = parent?.name ?: "ادامه مطالعه"
                                    tab = 3
                                } else if (lastRead != null) {
                                    val doc = DocumentFile.fromSingleUri(context, Uri.parse(lastRead))
                                    if (doc != null) openReader(doc) else status = "فایل مطالعه قبلی پیدا نشد."
                                }
                            }
                        )
                        1 -> RenameScreen(
                            files = allFiles.filter { it.isFile },
                            selected = selected,
                            mode = renameMode, onMode = { renameMode = it; preview = emptyList() },
                            prefix = prefix, onPrefix = { prefix = it },
                            fixedText = fixedText, onFixedText = { fixedText = it },
                            fixedEnabled = fixedEnabled, onFixedEnabled = { fixedEnabled = it },
                            manualNumbers = manualNumbers, onManualNumbers = { manualNumbers = it },
                            preview = preview, onPreview = { preview = it },
                            status = status, onStatus = { status = it },
                            onDone = { refresh++; preview = emptyList(); selected.clear() }
                        )
                        2 -> SettingsScreen(
                            columns = columns, onColumns = { columns = it; prefs.edit().putInt("columns", it).apply() },
                            audioUri = audioUri, musicOn = musicOn,
                            onChooseAudio = { audioPicker.launch(arrayOf("audio/*")) },
                            onToggleMusic = {
                                try {
                                    if (musicOn) {
                                        player?.stop(); player?.release(); player = null; musicOn = false
                                    } else if (audioUri != null) {
                                        val mp = MediaPlayer()
                                        mp.setDataSource(context, audioUri!!)
                                        mp.isLooping = true
                                        mp.setOnPreparedListener { it.start(); musicOn = true }
                                        mp.prepareAsync()
                                        player = mp
                                    } else status = "ابتدا فایل موسیقی را انتخاب کنید."
                                } catch (e: Exception) { status = "پخش موسیقی ناموفق بود: ${e.message}" }
                            },
                            status = status
                        )
                        3 -> ReaderScreen(
                            pages = readerPages, index = readerIndex, title = readerTitle,
                            onBack = { tab = 0 },
                            onPage = { next ->
                                readerIndex = next.coerceIn(0, (readerPages.size - 1).coerceAtLeast(0))
                                readerPages.getOrNull(readerIndex)?.let {
                                    prefs.edit().putString("last_read_uri", it.uri.toString())
                                        .putInt("last_page", readerIndex).apply()
                                }
                            }
                        )
                    }
                    if (tab != 3 && status.isNotBlank()) {
                        Text(status, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { player?.release(); player = null }
    }
}

@Composable
private fun LibraryScreen(
    files: List<DocumentFile>, rootUri: Uri?, currentUri: String?,
    favorites: List<String>, hidden: List<String>, pinned: List<String>,
    showHidden: Boolean, favoritesOnly: Boolean, columns: Int, lastRead: String?,
    onPickFolder: () -> Unit, onOpenFolder: (DocumentFile) -> Unit, onBack: () -> Unit,
    onOpen: (DocumentFile) -> Unit, onFavorite: (String) -> Unit, onHide: (String) -> Unit,
    onPin: (String) -> Unit, onShowHidden: (Boolean) -> Unit, onFavoritesOnly: (Boolean) -> Unit,
    onColumns: (Int) -> Unit, onContinue: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        if (rootUri == null) {
            EmptyState(onPickFolder)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (currentUri != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, null, tint = Green) }
                Text(if (currentUri == null) "کتابخانه من" else "محتویات پوشه", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { onFavoritesOnly(!favoritesOnly) }) {
                    Icon(Icons.Default.Favorite, null, tint = if (favoritesOnly) Gold else Muted)
                }
                IconButton(onClick = { onShowHidden(!showHidden) }) {
                    Icon(if (showHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = Muted)
                }
            }
            if (lastRead != null && currentUri == null) {
                FilledTonalButton(onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("ادامه مطالعه قبلی")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("${files.size} مورد", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("نمایش", color = Muted, fontSize = 12.sp)
                (1..4).forEach { n ->
                    TextButton(onClick = { onColumns(n) }, contentPadding = PaddingValues(horizontal = 7.dp)) {
                        Text("$n", color = if (columns == n) Green else Muted, fontWeight = FontWeight.Bold)
                    }
                }
            }
            val pinnedFiles = files.filter { it.uri.toString() in pinned && it.isDirectory }
            if (pinnedFiles.isNotEmpty() && !favoritesOnly) {
                Text("پوشه‌های سنجاق‌شده", color = Gold, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    pinnedFiles.take(4).forEach { doc ->
                        AssistChip(onClick = { onOpenFolder(doc) }, label = { Text(doc.name ?: "پوشه", maxLines = 1) }, leadingIcon = { Icon(Icons.Default.PushPin, null) })
                    }
                }
            }
            if (files.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("این بخش خالی است.", color = Muted) }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(columns), contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(files, key = { it.uri.toString() }) { doc ->
                        MangaCard(
                            doc = doc, favorite = doc.uri.toString() in favorites,
                            isHidden = doc.uri.toString() in hidden, isPinned = doc.uri.toString() in pinned,
                            onClick = { if (doc.isDirectory) onOpenFolder(doc) else onOpen(doc) },
                            onFavorite = { onFavorite(doc.uri.toString()) },
                            onHide = { onHide(doc.uri.toString()) }, onPin = { onPin(doc.uri.toString()) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onPickFolder: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.AutoStories, null, tint = Green, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(14.dp))
        Text("کتابخانه مانگای خودت را بساز", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text("برای دسترسی امن، پوشه مانگا را انتخاب کن.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
        Button(onClick = onPickFolder) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("انتخاب پوشه") }
    }
}

@Composable
private fun MangaCard(
    doc: DocumentFile, favorite: Boolean, isHidden: Boolean, isPinned: Boolean,
    onClick: () -> Unit, onFavorite: () -> Unit, onHide: () -> Unit, onPin: () -> Unit
) {
    val ext = doc.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
    val cover = remember(doc.uri) {
        if (doc.isDirectory) doc.listFiles().firstOrNull {
            it.isFile && it.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty() in ImageExt
        }?.uri else if (ext in ImageExt) doc.uri else null
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(if (doc.isDirectory) 132.dp else 112.dp).background(Panel2), contentAlignment = Alignment.Center) {
                if (cover != null) AsyncImage(model = cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(
                    when {
                        doc.isDirectory -> Icons.Default.Folder
                        ext == "pdf" -> Icons.Default.PictureAsPdf
                        ext in setOf("cbz", "cbr", "zip", "rar", "7z") -> Icons.Default.CollectionsBookmark
                        else -> Icons.Default.InsertDriveFile
                    }, null, tint = Green, modifier = Modifier.size(42.dp)
                )
                if (favorite) Icon(Icons.Default.Favorite, null, tint = Gold, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp))
            }
            Text(doc.name ?: "بدون نام", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp).heightIn(min = 34.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onFavorite, modifier = Modifier.size(34.dp)) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) Gold else Muted, modifier = Modifier.size(17.dp)) }
                IconButton(onClick = onHide, modifier = Modifier.size(34.dp)) { Icon(if (isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = Muted, modifier = Modifier.size(17.dp)) }
                if (doc.isDirectory) IconButton(onClick = onPin, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.PushPin, null, tint = if (isPinned) Gold else Muted, modifier = Modifier.size(17.dp)) }
            }
        }
    }
}

@Composable
private fun RenameScreen(
    files: List<DocumentFile>, selected: MutableList<String>, mode: Int, onMode: (Int) -> Unit,
    prefix: String, onPrefix: (String) -> Unit, fixedText: String, onFixedText: (String) -> Unit,
    fixedEnabled: Boolean, onFixedEnabled: (Boolean) -> Unit,
    manualNumbers: Map<String, String>, onManualNumbers: (Map<String, String>) -> Unit,
    preview: List<RenamePreview>, onPreview: (List<RenamePreview>) -> Unit,
    status: String, onStatus: (String) -> Unit, onDone: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Text("تغییر نام گروهی مانگا", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text("شماره‌ها حفظ می‌شوند؛ موارد مبهم برای بررسی دستی علامت می‌خورند.", color = Muted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = mode == 0, onClick = { onMode(0) }, label = { Text("حفظ شماره موجود") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = mode == 1, onClick = { onMode(1) }, label = { Text("متن ثابت دلخواه") })
        }
        if (mode == 0) {
            OutlinedTextField(value = prefix, onValueChange = onPrefix, label = { Text("متن ابتدای نام (مثلاً Ch)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("فقط وقتی دقیقاً یک عدد و بدون پرانتز وجود داشته باشد، شماره خودکار حفظ می‌شود.", color = Muted, fontSize = 11.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("استفاده از متن ثابت", modifier = Modifier.weight(1f))
                Switch(checked = fixedEnabled, onCheckedChange = onFixedEnabled)
            }
            if (fixedEnabled) OutlinedTextField(value = fixedText, onValueChange = onFixedText, label = { Text("متن ثابت") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("شماره فایل تشخیص داده می‌شود؛ فقط در موارد مبهم، شماره را برای همان فایل دستی وارد کن.", color = Muted, fontSize = 11.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${selected.size} فایل انتخاب‌شده از ${files.size}", color = Green, modifier = Modifier.weight(1f), fontSize = 12.sp)
            TextButton(onClick = { if (selected.size == files.size) selected.clear() else { selected.clear(); selected.addAll(files.map { it.uri.toString() }) } }) {
                Text(if (selected.size == files.size) "لغو انتخاب" else "انتخاب همه")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val chosen = files.filter { it.uri.toString() in selected }
                if (chosen.isEmpty()) { onStatus("ابتدا فایل‌ها را انتخاب کن."); return@Button }
                val result = chosen.map { doc ->
                    val old = doc.name ?: ""
                    val ext = old.substringAfterLast('.', "").let { if (it.isNotBlank()) ".$it" else "" }
                    val stem = if (ext.isNotEmpty()) old.dropLast(ext.length) else old
                    val matches = Regex("\\d+").findAll(stem).toList()
                    val safeDetected = !stem.contains('(') && !stem.contains(')') && matches.size == 1 && matches[0].value.length <= 1000
                    val number = if (safeDetected) matches[0].value else manualNumbers[doc.uri.toString()]?.trim().orEmpty()
                    if (number.isBlank() || !number.all(Char::isDigit)) {
                        RenamePreview(doc.uri.toString(), old, old, "نیاز به بررسی دستی؛ شماره را وارد کن")
                    } else {
                        val text = if (mode == 0) prefix else if (fixedEnabled) fixedText else ""
                        RenamePreview(doc.uri.toString(), old, text + number + ext)
                    }
                }
                val duplicates = result.filter { it.warning.isEmpty() }.groupBy { it.newName.lowercase(Locale.ROOT) }.filterValues { it.size > 1 }.keys
                onPreview(result.map { if (it.newName.lowercase(Locale.ROOT) in duplicates && it.warning.isEmpty()) it.copy(warning = "نام مقصد تکراری است") else it })
                onStatus("پیش‌نمایش آماده شد؛ موارد نیازمند بررسی دستی را کامل کن.")
            }, modifier = Modifier.weight(1f)) { Text("ساخت پیش‌نمایش") }
            OutlinedButton(onClick = { selected.clear(); onPreview(emptyList()) }) { Text("پاک‌کردن") }
        }
        if (preview.isNotEmpty()) {
            Text("پیش‌نمایش نام‌ها", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(preview, key = { it.uri }) { item ->
                    val doc = remember(item.uri) { files.firstOrNull { it.uri.toString() == item.uri } }
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(item.oldName, fontSize = 12.sp)
                            if (item.warning.startsWith("نیاز")) {
                                OutlinedTextField(
                                    value = manualNumbers[item.uri].orEmpty(),
                                    onValueChange = { onManualNumbers(manualNumbers + (item.uri to it)) },
                                    label = { Text("شماره فصل برای این فایل") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true, modifier = Modifier.fillMaxWidth()
                                )
                            } else Text("← ${item.newName}", color = Green, fontSize = 12.sp)
                            if (item.warning.isNotBlank()) Text(item.warning, color = Gold, fontSize = 11.sp)
                        }
                    }
                }
            }
            Button(onClick = {
                val valid = preview.filter { it.warning.isEmpty() && it.oldName != it.newName }
                val existingNames = files.map { it.name?.lowercase(Locale.ROOT) }.toSet()
                val blocked = valid.filter { it.newName.lowercase(Locale.ROOT) in existingNames && it.newName.lowercase(Locale.ROOT) != it.oldName.lowercase(Locale.ROOT) }
                if (preview.any { it.warning.isNotBlank() } || blocked.isNotEmpty()) {
                    onStatus("هنوز موارد مبهم یا نام مقصد موجود است؛ ابتدا همه هشدارها را برطرف کن.")
                    return@Button
                }
                val confirmed = valid.size
                var success = 0
                valid.forEach { item ->
                    val target = files.firstOrNull { it.uri.toString() == item.uri }
                    if (target?.renameTo(item.newName) == true) success++
                }
                onStatus("تغییر نام انجام شد: $success از $confirmed فایل.")
                onDone()
            }, modifier = Modifier.fillMaxWidth(), enabled = preview.isNotEmpty()) { Text("اعمال تغییر نام") }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(files, key = { it.uri.toString() }) { doc ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Checkbox(checked = doc.uri.toString() in selected, onCheckedChange = { checked ->
                            if (checked) selected.add(doc.uri.toString()) else selected.remove(doc.uri.toString())
                        })
                        Text(doc.name ?: "بدون نام", fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderScreen(pages: List<DocumentFile>, index: Int, title: String, onBack: () -> Unit, onPage: (Int) -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Green) }
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${index + 1} / ${pages.size}", color = Muted, fontSize = 12.sp)
        }
        if (pages.isNotEmpty()) {
            AsyncImage(model = pages[index].uri, contentDescription = pages[index].name, modifier = Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Fit)
            LinearProgressIndicator(progress = { (index + 1).toFloat() / pages.size }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Green)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FilledTonalButton(onClick = { onPage(index - 1) }, enabled = index > 0) { Text("صفحه قبل") }
                FilledTonalButton(onClick = { onPage(index + 1) }, enabled = index < pages.lastIndex) { Text("صفحه بعد") }
            }
        } else Text("صفحه‌ای برای نمایش وجود ندارد.", color = Muted)
    }
}

@Composable
private fun SettingsScreen(
    columns: Int, onColumns: (Int) -> Unit, audioUri: Uri?, musicOn: Boolean,
    onChooseAudio: () -> Unit, onToggleMusic: () -> Unit, status: String
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("تنظیمات برنامه", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(14.dp)) {
                Text("چیدمان کتابخانه", fontWeight = FontWeight.Bold)
                Text("تعداد ستون‌ها: $columns", color = Muted, fontSize = 12.sp)
                Row {
                    (1..4).forEach { n ->
                        FilterChip(selected = columns == n, onClick = { onColumns(n) }, label = { Text("$n") }, modifier = Modifier.padding(end = 4.dp))
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(14.dp)) {
                Text("موسیقی پس‌زمینه مطالعه", fontWeight = FontWeight.Bold)
                Text(audioUri?.lastPathSegment ?: "هنوز موسیقی انتخاب نشده", color = Muted, fontSize = 11.sp, maxLines = 2)
                Row {
                    OutlinedButton(onClick = onChooseAudio) { Text("انتخاب موسیقی") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onToggleMusic, enabled = audioUri != null) { Text(if (musicOn) "توقف" else "پخش") }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(14.dp)) {
                Text("درباره Manga Renamer Pro", fontWeight = FontWeight.Bold)
                Text("نسخه 1.0.0 • رابط فارسی • دسترسی پوشه با مجوز انتخابی اندروید", color = Muted, fontSize = 12.sp)
            }
        }
        Text(status, color = Muted, fontSize = 12.sp)
    }
}
