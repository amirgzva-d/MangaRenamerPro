package com.amirgzva.mangarenamerpro

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
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
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        setContent {
            MangaLibraryApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MangaFileManager(onExit: () -> Unit = {}, onOpenMangaFile: (File) -> Unit = {}) {
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

    // Observe only the open directory. Debounce bursts into one background reload.
    DisposableEffect(hasAccess, currentPath) {
        val handler = Handler(Looper.getMainLooper())
        val refreshTask = Runnable { refreshKey++ }
        val observer = if (hasAccess) {
            runCatching {
                object : FileObserver(
                    currentPath,
                    FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or
                        FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE or FileObserver.ATTRIB
                ) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path != null) {
                            handler.removeCallbacks(refreshTask)
                            handler.postDelayed(refreshTask, 450L)
                        }
                    }
                }.also { it.startWatching() }
            }.getOrNull()
        } else null
        onDispose {
            observer?.stopWatching()
            handler.removeCallbacks(refreshTask)
        }
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
                data class Staged(val source: File, val temp: File, val target: File)
                val staged = mutableListOf<Staged>()
                val errors = mutableListOf<String>()
                var succeeded = 0

                fun moveSameFolder(source: File, destination: File): String? {
                    if (!source.exists()) return "فایل مبدأ وجود ندارد"
                    if (destination.exists()) return "نام مقصد از قبل وجود دارد"
                    if (source.renameTo(destination)) return null
                    return try {
                        Files.move(source.toPath(), destination.toPath())
                        null
                    } catch (e: Exception) {
                        e.localizedMessage ?: e.javaClass.simpleName
                    }
                }

                // First stage each eligible file independently. A failure never cancels other files.
                batch.forEach { (source, newName) ->
                    val temp = File(source.parentFile, ".manga-renamer-${UUID.randomUUID()}.tmp")
                    val error = try { moveSameFolder(source, temp) } catch (e: Exception) {
                        e.localizedMessage ?: e.javaClass.simpleName
                    }
                    if (error == null) staged.add(Staged(source, temp, File(source.parentFile, newName)))
                    else errors.add("${source.name}: $error")
                }

                // Then finalize each staged file independently.
                staged.forEach { item ->
                    val error = try { moveSameFolder(item.temp, item.target) } catch (e: Exception) {
                        e.localizedMessage ?: e.javaClass.simpleName
                    }
                    if (error == null) {
                        succeeded++
                        MangaStore.moveProgress(context, item.source.absolutePath, item.target.absolutePath)
                    } else {
                        errors.add("${item.source.name}: $error")
                        // Restore only this file and keep processing the rest of the batch.
                        val restoreError = runCatching {
                            moveSameFolder(item.temp, item.source)
                        }.getOrNull()
                        if (restoreError != null) {
                            errors.add("${item.source.name}: بازگردانی ناموفق؛ فایل موقت باقی ماند")
                        }
                    }
                }
                succeeded to errors
            }
            status = if (outcome.second.isEmpty()) {
                "با موفقیت انجام شد: ${outcome.first} فایل تغییر نام یافت."
            } else {
                "تغییرنام جزئی انجام شد: موفق ${outcome.first} | ردشده ${outcome.second.size}. " +
                    outcome.second.take(4).joinToString("؛ ")
            }
            pendingRename = emptyList()
            isRenaming = false
            refreshKey++
        }
    }

    // Eligibility is checked in one place so select-all, individual selection and rename use identical rules.
    // Eligibility is based on file type only. Actual write/rename permission is tested per file
    // during execution so one read-only or locked item cannot block the rest of the batch.
    fun canRename(file: File): Boolean =
        file.isFile && file.extension.lowercase(Locale.ROOT) in MangaExtensions

    val eligible = entries.filter(::canRename)
    // Drop stale/ineligible selections automatically after refresh or filesystem changes.
    LaunchedEffect(entries) {
        val eligiblePaths = eligible.map { it.absolutePath }.toSet()
        selected.removeAll { it !in eligiblePaths }
    }
    val selectedFiles = entries.filter { it.absolutePath in selected && canRename(it) }
    val renameCandidates = selectedFiles
    val rawRenameRows = renameCandidates.mapIndexed { index, file ->
        val ext = file.extension
        val stem = if (ext.isBlank()) file.name else file.nameWithoutExtension
        val normalized = extractEpisodeNumber(stem, index + 1)
        val baseName = if (ext.isBlank()) "$seriesName $normalized" else "$seriesName $normalized.$ext"
        Triple(file, baseName, ext)
    }
    // Give collisions a safe copy suffix instead of silently skipping an episode.
    val movableSourcePaths = rawRenameRows
        .filter { (source, baseName, _) -> source.name != baseName }
        .map { it.first.absolutePath }.toSet()
    val reservedTargetNames = mutableSetOf<String>()
    val renameRows = rawRenameRows.map { (file, baseName, ext) ->
        val baseStem = if (ext.isBlank()) baseName else baseName.removeSuffix(".$ext")
        var targetName = baseName
        var copyIndex = 2
        fun targetIsTaken(name: String): Boolean {
            val target = File(file.parentFile, name)
            return (target.exists() && target.absolutePath !in movableSourcePaths) ||
                name.lowercase(Locale.ROOT) in reservedTargetNames
        }
        while (targetIsTaken(targetName)) {
            targetName = if (ext.isBlank()) "$baseStem ($copyIndex)" else "$baseStem ($copyIndex).$ext"
            copyIndex++
        }
        reservedTargetNames.add(targetName.lowercase(Locale.ROOT))
        file to targetName
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
                            }) { Icon(Icons.Default.ArrowBack, "بازگشت به پوشه قبل", tint = TextMain) }
                        } else {
                            IconButton(onClick = onExit) { Icon(Icons.Default.ArrowBack, "بازگشت به کتابخانه", tint = TextMain) }
                        }
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
                    Text("${selectedFiles.size} مورد انتخاب‌شده", color = TextMain, fontWeight = FontWeight.SemiBold)
                    Text("${selectedFiles.count { it.isFile && it.extension.lowercase(Locale.ROOT) in MangaExtensions }} مورد واجد شرایط تغییر نام", color = TextMuted, fontSize = 11.sp)
                }
                TextButton(onClick = {
                    // Replace the entire selection with eligible files only; never retain invalid items.
                    val eligiblePaths = eligible.map { it.absolutePath }
                    val allAlreadySelected = eligiblePaths.isNotEmpty() && eligiblePaths.all { it in selected } &&
                        selected.all { it in eligiblePaths }
                    selected.clear()
                    if (eligiblePaths.isNotEmpty() && !allAlreadySelected) selected.addAll(eligiblePaths)
                }, enabled = eligible.isNotEmpty()) { Text("انتخاب همه", color = Accent) }
                Button(onClick = { seriesName = ""; renameOpen = true }, enabled = renameCandidates.isNotEmpty() && !isRenaming, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue), shape = RoundedCornerShape(12.dp)) {
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
                    val canRename = canRename(file)
                    val isSelected = file.absolutePath in selected
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (isSelected) Color(0xFF182B3B) else Bg).clickable {
                        if (file.isDirectory) {
                            currentPath = file.absolutePath
                            selected.clear()
                        } else if (file.extension.lowercase(Locale.ROOT) in MangaExtensions) {
                            onOpenMangaFile(file)
                        }
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
            LazyColumn(Modifier.heightIn(max = 220.dp)) { items(renameRows) { (file, name) -> Column(Modifier.padding(vertical = 4.dp)) {
                Text(file.name, color = TextMuted, fontSize = 10.sp)
                Text(name, color = TextMain, fontSize = 11.sp)
            } } }
        } },
        confirmButton = { Button(onClick = {
            if (seriesName.isBlank()) { status = "نام مانگا را وارد کن."; return@Button }
            if (seriesName.any { it in "\\\\/:*?<>|\"" }) { status = "نام واردشده دارای نویسه غیرمجاز است."; return@Button }
            if (isRenaming || pendingRename.isNotEmpty()) return@Button
            val candidates = renameRows.filter { (source, newName) -> source.name != newName }
            val accepted = candidates.filter { (_, newName) ->
                newName.isNotBlank() && newName.none { it in "\\\\/:*?<>|\"" }
            }
            val skipped = candidates.size - accepted.size
            if (accepted.isEmpty()) {
                status = if (candidates.isEmpty()) "هیچ فایلی نیاز به تغییر نام ندارد." else "هیچ‌کدام قابل اجرا نیستند؛ نام مقصد تکراری یا از قبل موجود است."
                return@Button
            }
            // Only valid candidates enter the worker. Invalid files are skipped, never a reason to cancel others.
            pendingRename = accepted
            renameOpen = false
            status = "شروع تغییر نام ${accepted.size} فایل؛ ${skipped} مورد به دلیل تداخل یا نام نامعتبر رد شد."
        }, enabled = renameRows.isNotEmpty() && seriesName.isNotBlank() && !isRenaming, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) { Text(if (isRenaming) "در حال اجرا…" else "اعمال تغییرات") } },
        dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("انصراف") } }
    )
}

/**
 * Extract the real episode/chapter number from a manga filename.
 * A trailing "(1)", "(2)", etc. is a duplicate-copy suffix, not an episode number.
 * Prefer an explicit CH/Chapter marker (including compact names such as "Ch2"),
 * then fall back to the last numeric group in the remaining filename.
 */
private fun extractEpisodeNumber(stem: String, fallbackIndex: Int): String {
    val withoutCopySuffix = stem
        .replace(Regex("""\s*\(\s*[0-9۰-۹٠-٩]+\s*\)\s*$"""), "")
        .trim()

    val numberPattern = """[0-9۰-۹٠-٩]+(?:[._-][0-9۰-۹٠-٩]+)?"""
    // Explicitly match compact names such as Ch2.pdf and Ch3.pdf.
    val compactChapterNumber = Regex(
        """(?i)^ch(?:apter)?[\s._-]*($numberPattern)$"""
    ).find(withoutCopySuffix)?.groupValues?.get(1)
    val chapterNumber = compactChapterNumber ?: Regex(
        """(?i)(?:^|[^a-z])ch(?:apter)?[\s._-]*($numberPattern)"""
    ).find(withoutCopySuffix)?.groupValues?.get(1)

    val rawNumber = chapterNumber
        ?: Regex(numberPattern).findAll(withoutCopySuffix).lastOrNull()?.value
        ?: fallbackIndex.toString().padStart(3, '0')

    return rawNumber.map { ch ->
        when (ch) {
            in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar()
            in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar()
            else -> ch
        }
    }.joinToString("").replace('_', '.').replace('-', '.')
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
