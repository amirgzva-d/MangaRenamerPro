package com.amirgzva.mangarenamerpro

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.UUID
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
            MaterialTheme(colorScheme = darkColorScheme(primary = AccentBlue, secondary = Accent, background = Bg, surface = SurfaceDark, onPrimary = Color.White, onSecondary = Bg, onBackground = TextMain, onSurface = TextMain)) {
                MangaFileManager()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MangaFileManager() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasAccess by remember { mutableStateOf(hasAllFilesAccess()) }
    var currentPath by remember { mutableStateOf(Environment.getExternalStorageDirectory().absolutePath) }
    var entries by remember { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var renameOpen by remember { mutableStateOf(false) }
    var seriesName by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    var pendingRename by remember { mutableStateOf<List<Pair<File, String>>>(emptyList()) }
    var isRenaming by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = hasAllFilesAccess()
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // All filesystem work is done on Dispatchers.IO. No periodic full-directory scans.
    LaunchedEffect(hasAccess, currentPath, refreshKey) {
        if (!hasAccess) {
            entries = emptyList()
            loading = false
        } else {
            loading = true
            val requestedPath = currentPath
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val directory = File(requestedPath)
                    if (!directory.exists() || !directory.isDirectory || !directory.canRead()) {
                        throw SecurityException("دسترسی خواندن این پوشه وجود ندارد")
                    }
                    directory.listFiles()?.sortedWith(
                        compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) })
                    ) ?: throw IllegalStateException("خواندن فهرست پوشه ناموفق بود")
                }
            }
            if (requestedPath == currentPath) {
                entries = result.getOrElse {
                    status = it.localizedMessage ?: "خطا در خواندن پوشه"
                    emptyList()
                }
                if (result.isSuccess) status = ""
            }
            loading = false
        }
    }

    LaunchedEffect(pendingRename) {
        val batch = pendingRename
        if (batch.isNotEmpty()) {
            isRenaming = true
            val outcome = withContext(Dispatchers.IO) {
                val staged = mutableListOf<Triple<File, File, File>>()
                val completed = mutableListOf<Triple<File, File, File>>()
                val failed = mutableListOf<String>()
                try {
                    // Phase 1: move every source to a unique temporary name.
                    batch.forEach { (source, newName) ->
                        val target = File(source.parentFile, newName)
                        val temp = File(source.parentFile, ".manga-renamer-${UUID.randomUUID()}.tmp")
                        if (!source.exists()) throw IllegalStateException("فایل مبدأ پیدا نشد: ${source.name}")
                        if (target.exists() && batch.none { it.first.absolutePath == target.absolutePath }) {
                            throw IllegalStateException("نام مقصد موجود است: ${target.name}")
                        }
                        Files.move(source.toPath(), temp.toPath())
                        staged.add(Triple(source, temp, target))
                    }
                    // Phase 2: move all staged files to their final names.
                    staged.forEach { (source, temp, target) ->
                        Files.move(temp.toPath(), target.toPath())
                        completed.add(Triple(source, temp, target))
                    }
                } catch (e: Exception) {
                    failed.add(e.localizedMessage ?: e.javaClass.simpleName)
                    // Roll back finished destinations first, then unfinished temp files.
                    completed.asReversed().forEach { (source, _, target) ->
                        runCatching { if (target.exists() && !source.exists()) Files.move(target.toPath(), source.toPath()) }
                    }
                    staged.asReversed().filter { item -> completed.none { it.second == item.second } }.forEach { (source, temp, _) ->
                        runCatching { if (temp.exists() && !source.exists()) Files.move(temp.toPath(), source.toPath()) }
                    }
                }
                (if (failed.isEmpty()) completed.size else 0) to failed
            }
            status = if (outcome.second.isEmpty()) "انجام شد: ${outcome.first} فایل تغییر نام یافت."
                else "موفق: ${outcome.first} | ناموفق: ${outcome.second.size} — ${outcome.second.take(2).joinToString("؛ ")}"
            pendingRename = emptyList()
            isRenaming = false
            refreshKey++
        }
    }

    // Selection may include any file; only supported manga files are renamed.
    val eligible = entries.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in MangaExtensions }
    val selectedFiles = entries.filter { it.absolutePath in selected }
    val renameCandidates = selectedFiles.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in MangaExtensions }
    val skippedSelectionCount = selectedFiles.size - renameCandidates.size
    val renameRows = renameCandidates.mapIndexed { index, file ->
        val ext = file.extension
        val stem = if (ext.isBlank()) file.name else file.nameWithoutExtension
        val number = Regex("[0-9۰-۹٠-٩]+(?:[._-][0-9۰-۹٠-٩]+)?").findAll(stem).lastOrNull()?.value
            ?: (index + 1).toString().padStart(3, '0')
        val normalized = number.map { ch ->
            when (ch) {
                in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar()
                in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar()
                else -> ch
            }
        }.joinToString("").replace('_', '.').replace('-', '.')
        file to if (ext.isBlank()) "$seriesName $normalized" else "$seriesName $normalized.$ext"
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            Column(Modifier.fillMaxWidth().background(Bg)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                        if (currentPath != Environment.getExternalStorageDirectory().absolutePath) {
                            IconButton(onClick = {
                                currentPath = File(currentPath).parent ?: Environment.getExternalStorageDirectory().absolutePath
                                selected.clear()
                            }) { Icon(Icons.Default.ArrowBack, "بازگشت", tint = TextMain) }
                        }
                        IconButton(onClick = { refreshKey++ }) { Icon(Icons.Default.Refresh, "بروزرسانی", tint = TextMain) }
                        Column(Modifier.padding(start = 4.dp)) {
                            Text("MANGA FILE MANAGER", fontSize = 10.sp, color = Accent, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text("مدیریت فایل‌های مانگا", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextMain)
                        }
                    }
                }
                Text(currentPath.removePrefix(Environment.getExternalStorageDirectory().absolutePath).ifBlank { "حافظه داخلی" }, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                HorizontalDivider(color = Color(0xFF252832))
                if (status.isNotBlank()) Text(status, color = Accent, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        bottomBar = {
            if (hasAccess) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Row(Modifier.fillMaxWidth().background(SurfaceDark).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                Column(Modifier.weight(1f)) {
                    Text("${selected.size} مورد انتخاب‌شده", color = TextMain, fontWeight = FontWeight.SemiBold)
                    Text("${selectedFiles.count { it.isFile && it.extension.lowercase(Locale.ROOT) in MangaExtensions }} مورد واجد شرایط تغییر نام", color = TextMuted, fontSize = 11.sp)
                }
                TextButton(onClick = {
                    // Select only files that the rename engine supports.
                    val eligiblePaths = eligible.map { it.absolutePath }
                    if (eligiblePaths.isNotEmpty() && eligiblePaths.all { it in selected }) {
                        selected.clear()
                    } else {
                        selected.clear()
                        selected.addAll(eligiblePaths)
                    }
                }, enabled = eligible.isNotEmpty()) { Text("انتخاب همه", color = Accent) }
                Button(onClick = { seriesName = ""; renameOpen = true }, enabled = selected.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = AccentBlue), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.DriveFileRenameOutline, null); Spacer(Modifier.width(5.dp)); Text("تغییر نام")
                }
            } }
        }
    ) { padding ->
        when {
            !hasAccess -> Column(Modifier.fillMaxSize().padding(padding).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Folder, null, tint = Accent, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(18.dp))
                Text("دسترسی مدیریت همه فایل‌ها", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(8.dp))
                Text("برای نمایش مستقیم پوشه‌ها، مجوز مدیریت همه فایل‌ها را فعال کن. دیگر پنجره انتخاب پوشه باز نمی‌شود.", color = TextMuted)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { openAllFilesSettings(context) }, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) { Text("فعال‌کردن دسترسی حافظه") }
            }
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentBlue)
                    Spacer(Modifier.height(12.dp))
                    Text("در حال خواندن پوشه…", color = TextMuted)
                }
            }
            entries.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text(if (status.isBlank()) "این پوشه خالی است" else status, color = TextMuted) }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(entries, key = { it.absolutePath }) { file ->
                    val canRename = file.isFile && file.extension.lowercase(Locale.ROOT) in MangaExtensions
                    val isSelected = file.absolutePath in selected
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (isSelected) Color(0xFF182B3B) else Bg).clickable {
                        if (file.isDirectory) { currentPath = file.absolutePath; selected.clear() }
                    }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (canRename) {
                            Checkbox(isSelected, { checked -> if (checked) selected.add(file.absolutePath) else selected.remove(file.absolutePath) })
                        } else {
                            Spacer(Modifier.width(48.dp))
                        }
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(if (file.isDirectory) Color(0xFF49351D) else SurfaceRaised), contentAlignment = Alignment.Center) {
                            Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description, null, tint = if (file.isDirectory) Accent else AccentBlue, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.name, color = TextMain, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (file.isDirectory) "پوشه" else file.extension.uppercase(Locale.ROOT) + " • " + formatSize(file.length()), color = TextMuted, fontSize = 11.sp)
                        }
                    }
                    HorizontalDivider(color = Color(0xFF1B1D24), thickness = 0.6.dp)
                }
            }
        }
    }

    if (renameOpen) AlertDialog(
        onDismissRequest = { renameOpen = false }, containerColor = SurfaceDark,
        title = { Text("تغییر نام گروهی", color = TextMain) },
        text = { Column {
            Text("شماره فصل از نام فایل استخراج می‌شود؛ پیش‌نمایش را بررسی کن.", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(seriesName, { seriesName = it }, label = { Text("نام مانگا") }, singleLine = true)
            Text("${renameRows.size} فایل واجد شرایط آماده تغییر نام", color = Accent, fontSize = 12.sp)
            if (skippedSelectionCount > 0) Text("$skippedSelectionCount مورد انتخاب‌شده به دلیل پشتیبانی‌نشدن یا پوشه‌بودن تغییر نام داده نمی‌شود.", color = TextMuted, fontSize = 11.sp)
            LazyColumn(Modifier.heightIn(max = 220.dp)) { items(renameRows) { (file, name) -> Column(Modifier.padding(vertical = 4.dp)) {
                Text(file.name, color = TextMuted, fontSize = 10.sp)
                Text(name, color = TextMain, fontSize = 11.sp)
            } } }
        } },
        confirmButton = { Button(onClick = {
            if (seriesName.isBlank()) { status = "نام مانگا را وارد کن."; return@Button }
            if (seriesName.any { it in "\\\\/:*?<>|\"" }) { status = "نام واردشده دارای نویسه غیرمجاز است."; return@Button }
            val targets = renameRows.map { it.second.lowercase(Locale.ROOT) }
            val movingSources = renameRows.map { it.first.absoluteFile.normalize().path }.toSet()
            val externalConflicts = renameRows.filter { (source, newName) ->
                val target = File(source.parentFile, newName).absoluteFile.normalize().path
                File(target).exists() && target !in movingSources
            }
            if (targets.size != targets.distinct().size || externalConflicts.isNotEmpty()) {
                status = if (targets.size != targets.distinct().size) "چند فایل نام مقصد یکسان دارند؛ الگوی نام را اصلاح کن."
                    else "نام مقصد بعضی فایل‌ها با یک فایل خارج از انتخاب تداخل دارد."
                return@Button
            }
            if (isRenaming || pendingRename.isNotEmpty()) return@Button
            pendingRename = renameRows.filter { (source, newName) -> source.name != newName }
            if (pendingRename.isEmpty()) { status = "هیچ فایلی نیاز به تغییر نام ندارد."; return@Button }
            renameOpen = false
            status = "در حال تغییر نام ${pendingRename.size} فایل…"
        }, enabled = renameRows.isNotEmpty() && seriesName.isNotBlank() && !isRenaming, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) { Text(if (isRenaming) "در حال اجرا…" else "اعمال تغییرات") } },
        dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("انصراف") } }
    )
}

private fun hasAllFilesAccess(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

private fun openAllFilesSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(appIntent) }.onFailure {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024} KB"
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
}
