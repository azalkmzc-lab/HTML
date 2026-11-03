package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.builder.RealApkCompilerEngine
import com.example.data.BuildLogEntity
import com.example.data.StudioProjectEntity
import com.example.ui.theme.*
import com.example.viewmodel.StudioSettingsState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectManagerScreen(
    allProjects: List<StudioProjectEntity>,
    onCreateProjectClick: () -> Unit,
    onOpenProject: (StudioProjectEntity) -> Unit,
    onDuplicateProject: (StudioProjectEntity) -> Unit,
    onRenameProject: (StudioProjectEntity, String) -> Unit,
    onDeleteProject: (Long) -> Unit,
    onToggleSaveProject: (StudioProjectEntity) -> Unit,
    onBackupProjectsClick: () -> Unit,
    onRestoreProjectsClick: () -> Unit,
    onInstallLastApk: (File) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var projectToRename by remember { mutableStateOf<StudioProjectEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    val filtered = remember(allProjects, searchQuery) {
        if (searchQuery.isBlank()) allProjects
        else allProjects.filter {
            it.name.contains(searchQuery, true) ||
                it.packageName.contains(searchQuery, true) ||
                it.detectedType.contains(searchQuery, true)
        }
    }

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackground)
            .padding(16.dp)
            .testTag("project_manager_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header + Backup / Restore / Create Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "مدير المشاريع المتقدم (${allProjects.size})",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "إنشاء، فتح، تعديل، نسخ، إعادة تسمية، وحفظ نسخ احتياطية",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Button(
                onClick = onCreateProjectClick,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("مشروع جديد", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        // Backup & Restore Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBackupProjectsClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_backup_projects")
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = NeonCyan)
                Spacer(modifier = Modifier.width(6.dp))
                Text("حفظ نسخة احتياطية (JSON)", color = NeonCyan)
            }
            OutlinedButton(
                onClick = onRestoreProjectsClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_restore_projects")
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, tint = ElectricViolet)
                Spacer(modifier = Modifier.width(6.dp))
                Text("استعادة المشاريع", color = ElectricViolet)
            }
        }

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("ابحث باسم المشروع أو Package Name...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeonCyan) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Projects List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filtered, key = { "mgr_${it.id}" }) { project ->
                val lastApkFile = project.lastBuiltApkPath?.let { File(it) }
                val hasValidApk = lastApkFile != null && lastApkFile.exists()

                Surface(
                    color = StudioSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NeonCyan.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (project.isIptvModeEnabled) Icons.Default.LiveTv else Icons.Default.Web,
                                        contentDescription = null,
                                        tint = NeonCyan
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = project.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${project.packageName} • v${project.versionName} (${project.versionCode})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NeonCyan
                                    )
                                }
                            }

                            IconButton(onClick = { onToggleSaveProject(project) }) {
                                Icon(
                                    imageVector = if (project.isSavedFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "تمييز",
                                    tint = if (project.isSavedFavorite) AmberWarning else TextSecondary
                                )
                            }
                        }

                        // Metadata & Last Modified / Last APK Built Info
                        Text(
                            text = "النوع: ${project.detectedType} • آخر تعديل: ${dateFormat.format(Date(project.updatedAt))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        if (project.lastBuiltVersion != null && project.lastBuiltAt != null) {
                            Surface(
                                color = StudioSurfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "آخر نسخة APK مبنية: v${project.lastBuiltVersion}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = EmeraldGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "الحجم: ${RealApkCompilerEngine.formatFileSize(project.lastBuiltApkSize)} • بتاريخ ${dateFormat.format(Date(project.lastBuiltAt))}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextSecondary
                                        )
                                    }
                                    if (hasValidApk && lastApkFile != null) {
                                        Button(
                                            onClick = { onInstallLastApk(lastApkFile) },
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("تثبيت APK", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Action Buttons Row (Open/Edit, Duplicate, Rename, Delete)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onOpenProject(project) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Icon(Icons.Default.EditNote, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("فتح وتعديل", color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            OutlinedIconButton(
                                onClick = { onDuplicateProject(project) }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ المشروع", tint = NeonCyan)
                            }

                            OutlinedIconButton(
                                onClick = {
                                    projectToRename = project
                                    renameText = project.name
                                }
                            ) {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "إعادة تسمية", tint = AmberWarning)
                            }

                            OutlinedIconButton(
                                onClick = { onDeleteProject(project.id) }
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف المشروع", tint = CrimsonError)
                            }
                        }
                    }
                }
            }
        }
    }

    if (projectToRename != null) {
        AlertDialog(
            onDismissRequest = { projectToRename = null },
            containerColor = StudioSurface,
            title = { Text("إعادة تسمية المشروع", color = NeonCyan) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("الاسم الجديد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        projectToRename?.let { onRenameProject(it, renameText) }
                        projectToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("حفظ الاسم", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToRename = null }) {
                    Text("إلغاء", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun BuildHistoryScreen(
    buildLogs: List<BuildLogEntity>,
    onInstallApk: (File) -> Unit,
    onShareApk: (File, String) -> Unit,
    onSaveApkToPhone: (File) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackground)
            .padding(16.dp)
            .testTag("build_history_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "سجل عمليات التحويل وبناء APK (${buildLogs.size})",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "جميع حزم APK والمشاريع التي تم بناؤها وتوقيعها داخل الاستوديو",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        if (buildLogs.isEmpty()) {
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("لا توجد عمليات بناء مسجلة حتى الآن.", color = TextSecondary)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(buildLogs, key = { "log_${it.id}" }) { log ->
                    val apkFile = File(log.apkFilePath)
                    val exists = apkFile.exists()

                    Surface(
                        color = StudioSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${log.projectName} — v${log.versionName} (${log.versionCode})",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                Badge(containerColor = EmeraldGreen) {
                                    Text(log.status, color = Color.Black, modifier = Modifier.padding(horizontal = 6.dp))
                                }
                            }
                            Text(
                                text = "الحزمة: ${log.packageName} • الحجم: ${RealApkCompilerEngine.formatFileSize(log.fileSizeBytes)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "عدد القنوات المدمجة: ${log.channelsBundled} • زمن البناء: ${log.buildDurationMs}ms • التاريخ: ${dateFormat.format(Date(log.timestamp))}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = log.buildSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = EmeraldGreen
                            )

                            if (exists) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onInstallApk(apkFile) },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.InstallMobile, contentDescription = null, tint = Color.Black)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تثبيت", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { onShareApk(apkFile, log.projectName) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = NeonCyan)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("مشاركة", color = NeonCyan)
                                    }
                                    OutlinedButton(
                                        onClick = { onSaveApkToPhone(apkFile) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.SaveAlt, contentDescription = null, tint = ElectricViolet)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("حفظ", color = ElectricViolet)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudioSettingsScreen(
    settings: StudioSettingsState,
    onUpdateSettings: (darkTheme: Boolean?, rtlArabic: Boolean?, autoSave: Boolean?, devName: String?) -> Unit
) {
    var devNameInput by remember(settings.defaultDeveloperName) {
        mutableStateOf(settings.defaultDeveloperName)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackground)
            .padding(16.dp)
            .testTag("studio_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "إعدادات HTML TO APK STUDIO",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "تخصيص المظهر، اتجاه الواجهة، المحرر، ومشغل Media3 ExoPlayer 1.5.1",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        item {
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("الوضع الليلي الاحترافي (Dark Mode IDE)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("تفعيل هوية Obsidian Neon المريحة للعين أثناء البرمجة", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.isDarkTheme,
                            onCheckedChange = { onUpdateSettings(it, null, null, null) }
                        )
                    }

                    HorizontalDivider(color = StudioCardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("دعم الاتجاه العربي RTL (أو الإنجليزي LTR)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("التبديل الفوري بين تخطيط الواجهة من اليمين لليسار أو اليسار لليمين", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.isRtlArabic,
                            onCheckedChange = { onUpdateSettings(null, it, null, null) }
                        )
                    }

                    HorizontalDivider(color = StudioCardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("الحفظ التلقائي للتعديلات في المحرر", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("حفظ أكواد HTML وCSS وJavaScript تلقائياً أثناء الكتابة", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.autoSaveEnabled,
                            onCheckedChange = { onUpdateSettings(null, null, it, null) }
                        )
                    }

                    HorizontalDivider(color = StudioCardBorder)

                    OutlinedTextField(
                        value = devNameInput,
                        onValueChange = { devNameInput = it },
                        label = { Text("اسم المطور الافتراضي لتوقيع المشاريع") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { onUpdateSettings(null, null, null, devNameInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                    ) {
                        Text("حفظ إعدادات المطور", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Technical Architecture Card
        item {
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("المواصفات التقنية لبيئة البناء", style = MaterialTheme.typography.titleMedium, color = NeonCyan, fontWeight = FontWeight.Bold)
                    Text("• المشغل الأصلي المدمج: AndroidX Media3 ExoPlayer 1.5.1", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text("• البروتوكولات المدعومة: HLS (M3U8), DASH (MPD), MP4, Progressive TS", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text("• محرك التحليل الذكي: تصنيف 7 أنواع مشاريع + استخراج القنوات والشعارات", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text("• مخرجات البناء: ملف APK موقع وقابل للتثبيت + أرشيف Android Studio Source ZIP", color = EmeraldGreen, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
