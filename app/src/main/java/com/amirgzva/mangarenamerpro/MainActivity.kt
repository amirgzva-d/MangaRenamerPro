package com.amirgzva.mangarenamerpro

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.util.Locale

private val Bg = Color(0xFF08090D)
private val SurfaceDark = Color(0xFF12141B)
private val SurfaceRaised = Color(0xFF1A1D26)
private val Accent = Color(0xFFFFBD4A)
private val AccentBlue = Color(0xFF35A9F5)
private val TextMain = Color(0xFFF5F5F7)
private val TextMuted = Color(0xFF9296A2)
private val MangaExtensions = setOf("jpg", "jpeg", "png", "webp", "avif", "bmp", "gif", "pdf", "cbz", "cbr", "zip", "rar", "7z")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = AccentBlue, secondary = Accent, background = Bg, surface = SurfaceDark,
                onPrimary = Color.White, onSecondary = Bg, onBackground = TextMain, onSurface = TextMain
            )) { MangaFileManager() }
        }
    }
}

private data class RenameRow(val file: DocumentFile, val oldName: String, val newName: String, val chapter: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MangaFileManager() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("manga_renamer", 0) }
    var rootUri by remember { mutableStateOf(prefs.getString("root_tree", null)?.let(Uri::parse)) }
    val folders = remember { mutableStateListOf<DocumentFile>() }
    var refresh by remember { mutableIntStateOf(0) }
    var searchOpen by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var renameOpen by remember { mutableStateOf(false) }
    var seriesName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    var pendingRename by remember { mutableStateOf<List<Pair<File, String>>>(emptyList()) }

    LaunchedEffect(pendingRename) {
        val batch = pendingRename
        if (batch.isNotEmpty()) {
            val result = withContext(Dispatchers.IO) { batch.count { (file, newName) -> runCatching { file.renameTo(File(file.parentFile, newName)) }.getOrDefault(false) } }
            status = "تغییر نام موفق: $result از ${batch.size} فایل"
            pendingRename = emptyList()
            refreshKey++
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                rootUri = uri
                prefs.edit().putString("root_tree", uri.toString()).apply()
                folders.clear()
                selected.clear()
                refresh++
                status = "دسترسی پوشه ذخیره شد."
            } catch (e: Exception) { status = "ذخیره دسترسی ناموفق بود: ${e.localizedMessage}" }
        }
    }

    val root = remember(rootUri) { rootUri?.let { DocumentFile.fromTreeUri(context, it) } }
    val current = folders.lastOrNull() ?: root

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(current?.uri?.toString()) {
        while (true) {
            delay(2500)
            refresh++
        }
    }

    val all = remember(current, refresh) {
        current?.listFiles()?.sortedWith(compareBy<DocumentFile>({ !it.isDirectory }, { it.name?.lowercase(Locale.ROOT) ?: "" })) ?: emptyList()
    }
    val shown = all.filter { it.name.orEmpty().contains(query, ignoreCase = true) }
    val eligible = all.filter { it.isFile && extensionOf(it.name.orEmpty()) in MangaExtensions }
    val selectedFiles = eligible.filter { it.uri.toString() in selected }
    val renameRows = selectedFiles.mapNotNull { file ->
        val old = file.name ?: return@mapNotNull null
        val ext = extensionOf(old)
        val stem = if (ext.isBlank()) old else old.dropLast(ext.length + 1)
        val match = Regex("[0-9۰-۹٠-٩]+(?:[._-][0-9۰-۹٠-٩]+)?").findAll(stem).lastOrNull()
        val chapter = match?.value?.map { c ->
            when (c) { in '۰'..'۹' -> ('0'.code + (c.code - '۰'.code)).toChar(); in '٠'..'٩' -> ('0'.code + (c.code - '٠'.code)).toChar(); else -> c }
        }?.joinToString("")?.replace('_', '.')?.replace('-', '.') ?: ""
        if (chapter.isBlank()) null else RenameRow(file, old, if (ext.isBlank()) "$seriesName $chapter" else "$seriesName $chapter.$ext", chapter)
    }
    val missingNumbers = selectedFiles.size - renameRows.size

    Scaffold(
        containerColor = Bg,
        topBar = {
            Column(Modifier.fillMaxWidth().background(Bg)) {
                TopAppBar(
                    title = {
                        Column {
                            Text("MANGA FILE MANAGER", fontSize = 11.sp, color = Accent, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                            Text("مدیریت فایل‌های مانگا", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        if (folders.isNotEmpty()) IconButton(onClick = { folders.removeAt(folders.lastIndex); selected.clear(); refresh++ }) {
                            Icon(Icons.Default.ArrowForward, "بازگشت", tint = TextMain)
                        }
                    },
                    actions = {
                        IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "بروزرسانی", tint = TextMain) }
                        IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) { Icon(Icons.Default.Search, "جستجو", tint = TextMain) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
                )
                if (searchOpen) OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("جستجوی پوشه یا فایل") }, singleLine = true, shape = RoundedCornerShape(14.dp)
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(current?.name ?: "حافظه داخلی", color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(if (folders.isEmpty()) "ریشه پوشه انتخاب‌شده" else "${folders.size} پوشه در مسیر", color = TextMuted, fontSize = 11.sp)
                    }
                    Text("${shown.size} مورد", color = Accent, fontSize = 12.sp)
                }
                HorizontalDivider(color = Color(0xFF252832))
                if (status.isNotBlank()) Text(status, color = Accent, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        bottomBar = {
            if (current != null) Row(
                Modifier.fillMaxWidth().background(SurfaceDark).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${selected.size} فایل انتخاب‌شده", color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("از ${eligible.size} فایل قابل تغییر نام", color = TextMuted, fontSize = 11.sp)
                }
                TextButton(onClick = {
                    if (selected.size == eligible.size) selected.clear()
                    else { selected.clear(); selected.addAll(eligible.map { it.uri.toString() }) }
                }) { Text(if (selected.size == eligible.size && eligible.isNotEmpty()) "لغو همه" else "انتخاب همه", color = Accent) }
                Button(
                    onClick = { seriesName = ""; renameOpen = true },
                    enabled = selected.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Icon(Icons.Default.DriveFileRenameOutline, null)
                    Spacer(Modifier.width(6.dp))
                    Text("تغییر نام")
                }
            }
        },
        floatingActionButton = {
            if (root == null) FloatingActionButton(onClick = { folderPicker.launch(null) }, containerColor = AccentBlue, contentColor = Color.White) {
                Icon(Icons.Default.FolderOpen, "انتخاب پوشه")
            }
        }
    ) { padding ->
        if (root == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(104.dp).clip(CircleShape).background(SurfaceRaised), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.FolderOpen, null, tint = Accent, modifier = Modifier.size(54.dp))
                }
                Spacer(Modifier.height(22.dp))
                Text("برای شروع، حافظه را باز کن", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("یک‌بار پوشه اصلی حافظه را از پنجره رسمی اندروید انتخاب کن. سپس پوشه‌ها و فایل‌ها همین‌جا نمایش داده می‌شوند.", color = TextMuted, fontSize = 13.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(22.dp))
                Button(onClick = { folderPicker.launch(null) }, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) { Text("انتخاب حافظه") }
            }
        } else if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(if (query.isBlank()) "این پوشه خالی است" else "موردی پیدا نشد", color = TextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(shown, key = { it.uri.toString() }) { file ->
                    val ext = extensionOf(file.name.orEmpty())
                    val canRename = file.isFile && ext in MangaExtensions
                    val isSelected = file.uri.toString() in selected
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFF182B3B) else Bg)
                            .clickable {
                                if (file.isDirectory) { folders.add(file); selected.clear(); query = ""; refresh++ }
                                else openExternal(context, file)
                            }.padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canRename) {
                            Checkbox(checked = isSelected, onCheckedChange = { checked ->
                                if (checked) selected.add(file.uri.toString()) else selected.remove(file.uri.toString())
                            })
                        } else Spacer(Modifier.width(48.dp))
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(if (file.isDirectory) Color(0xFF49351D) else SurfaceRaised), contentAlignment = Alignment.Center) {
                            Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description, null,
                                tint = if (file.isDirectory) Accent else AccentBlue, modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.name ?: "بدون نام", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when { file.isDirectory -> "پوشه"; else -> ext.uppercase(Locale.ROOT).ifBlank { "فایل" } + "  •  " + formatSize(file.length()) },
                                color = TextMuted, fontSize = 11.sp
                            )
                        }
                        if (file.isDirectory) Icon(Icons.Default.ArrowBack, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(color = Color(0xFF1B1D24), thickness = 0.6.dp)
                }
            }
        }
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            containerColor = SurfaceDark,
            title = { Text("تغییر نام گروهی", color = TextMain, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("نام مانگا یا متن دلخواه را وارد کن. شماره فصل هر فایل از نام فعلی آن حفظ می‌شود.", color = TextMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = seriesName, onValueChange = { seriesName = it }, label = { Text("نام جدید؛ مثال Daughter of the Emperor") }, singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    Text("پیش‌نمایش: ${renameRows.size} فایل آماده • ${missingNumbers} فایل بدون شماره قابل تشخیص", color = Accent, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(Modifier.heightIn(max = 230.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(renameRows, key = { it.file.uri.toString() }) { row ->
                            Column(Modifier.fillMaxWidth().background(SurfaceRaised, RoundedCornerShape(8.dp)).padding(8.dp)) {
                                Text(row.oldName, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(row.newName, color = TextMain, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (missingNumbers > 0) Text("فایل‌های بدون شماره برای جلوگیری از نام‌گذاری اشتباه تغییر نمی‌کنند.", color = Accent, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (seriesName.isBlank()) { status = "نام جدید را وارد کن."; return@Button }
                    val targets = renameRows.map { it.newName.lowercase(Locale.ROOT) }
                    val existing = all.mapNotNull { it.name?.lowercase(Locale.ROOT) }.toSet()
                    val conflicts = targets.size != targets.distinct().size || renameRows.any { it.newName.lowercase(Locale.ROOT) in existing && it.newName.lowercase(Locale.ROOT) != it.oldName.lowercase(Locale.ROOT) }
                    if (conflicts) { status = "نام مقصد تکراری است؛ نام یا انتخاب‌ها را اصلاح کن."; return@Button }
                    var success = 0
                    renameRows.forEach { row -> if (row.file.renameTo(row.newName)) success++ }
                    status = "تغییر نام: $success از ${renameRows.size} فایل. فایل‌های بدون شماره: $missingNumbers"
                    renameOpen = false
                    selected.clear()
                    refresh++
                }, enabled = renameRows.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) { Text("تأیید و اجرا") }
            },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("انصراف") } }
        )
    }
}

private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024} KB"
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
}
private fun openExternal(context: android.content.Context, file: DocumentFile) {
    val ext = extensionOf(file.name.orEmpty())
    val mime = when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "cbz", "zip" -> "application/zip"
        "cbr", "rar" -> "application/vnd.rar"
        else -> "*/*"
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(file.uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    } catch (_: Exception) { }
}
