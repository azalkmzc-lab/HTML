package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.builder.RealApkCompilerEngine
import com.example.data.BuildLogEntity
import com.example.data.StudioProjectEntity
import com.example.ui.theme.*
import com.example.viewmodel.StudioDestination
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StudioDashboardScreen(
    allProjects: List<StudioProjectEntity>,
    savedProjects: List<StudioProjectEntity>,
    buildLogs: List<BuildLogEntity>,
    onNewProjectClick: () -> Unit,
    onPasteHtmlClick: () -> Unit,
    onImportHtmlFileClick: () -> Unit,
    onOpenProject: (StudioProjectEntity) -> Unit,
    onToggleSaveProject: (StudioProjectEntity) -> Unit,
    onInstallApkFromLog: (String) -> Unit,
    onNavigateToSection: (StudioDestination) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackground)
            .testTag("studio_dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Hero Studio Banner with Logo & Identity
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_studio_hero_1793706319417),
                        contentDescription = "HTML TO APK STUDIO Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        StudioBackground.copy(alpha = 0.35f),
                                        StudioBackground.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                            .align(Alignment.BottomStart)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_app_icon_fg_1793706318996),
                                contentDescription = "شعار التطبيق",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, NeonCyan, RoundedCornerShape(12.dp))
                            )
                            Column {
                                Text(
                                    text = "HTML TO APK STUDIO",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "محول أكواد HTML/CSS/JS وتطبيقات IPTV إلى APK أصلي مع Media3 1.5.1",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Primary Action Buttons (New Project, Paste HTML, Import File)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "أدوات البدء السريع",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardActionCard(
                        title = "إنشاء مشروع جديد",
                        subtitle = "قوالب IPTV أو HTML",
                        icon = Icons.Default.AddBox,
                        accentColor = NeonCyan,
                        testTag = "btn_new_project",
                        modifier = Modifier.weight(1f),
                        onClick = onNewProjectClick
                    )
                    DashboardActionCard(
                        title = "لصق كود HTML",
                        subtitle = "تحليل فوري بالذكاء الاصطناعي",
                        icon = Icons.Default.ContentPaste,
                        accentColor = ElectricViolet,
                        testTag = "btn_paste_html",
                        modifier = Modifier.weight(1f),
                        onClick = onPasteHtmlClick
                    )
                    DashboardActionCard(
                        title = "استيراد ملف HTML",
                        subtitle = "من ذاكرة الهاتف",
                        icon = Icons.Default.FileUpload,
                        accentColor = EmeraldGreen,
                        testTag = "btn_import_html",
                        modifier = Modifier.weight(1f),
                        onClick = onImportHtmlFileClick
                    )
                }
            }
        }

        // 3. Recent Projects Section (قسم مشاريعي السابقة)
        item {
            SectionHeaderRow(
                title = "مشاريعي السابقة (${allProjects.size})",
                actionLabel = "إدارة المشاريع",
                onActionClick = { onNavigateToSection(StudioDestination.PROJECT_MANAGER) }
            )
        }

        item {
            if (allProjects.isEmpty()) {
                EmptySectionBox("لا توجد مشاريع بعد. ابدأ بإنشاء مشروع أو لصق كود HTML.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    allProjects.take(4).forEach { project ->
                        ProjectSummaryCard(
                            project = project,
                            onOpen = { onOpenProject(project) },
                            onToggleSave = { onToggleSaveProject(project) }
                        )
                    }
                }
            }
        }

        // 4. Saved / Bookmarked Projects Section (قسم المشاريع المحفوظة)
        item {
            SectionHeaderRow(
                title = "المشاريع المحفوظة والمميزة (${savedProjects.size})",
                actionLabel = "عرض الكل",
                onActionClick = { onNavigateToSection(StudioDestination.PROJECT_MANAGER) }
            )
        }

        item {
            if (savedProjects.isEmpty()) {
                EmptySectionBox("اضغط على أيقونة النجمة ⭐ لحفظ مشاريعك المهمة هنا.")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(savedProjects, key = { "saved_${it.id}" }) { proj ->
                        SavedProjectChipCard(
                            project = proj,
                            onClick = { onOpenProject(proj) }
                        )
                    }
                }
            }
        }

        // 5. Conversion & APK Build History Section (سجل عمليات التحويل والبناء)
        item {
            SectionHeaderRow(
                title = "سجل عمليات التحويل وبناء APK (${buildLogs.size})",
                actionLabel = "السجل الكامل",
                onActionClick = { onNavigateToSection(StudioDestination.BUILD_HISTORY) }
            )
        }

        item {
            if (buildLogs.isEmpty()) {
                EmptySectionBox("لم يتم بناء أي حزمة APK بعد. افتح أي مشروع واضغط على 'بناء APK' لإنتاج ملف APK قابل للتثبيت.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    buildLogs.take(3).forEach { log ->
                        BuildHistoryRowCard(
                            log = log,
                            onInstallClick = { onInstallApkFromLog(log.apkFilePath) }
                        )
                    }
                }
            }
        }

        // 6. Quick App Settings Entry Card (قسم إعدادات التطبيق)
        item {
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToSection(StudioDestination.SETTINGS) }
                    .testTag("dashboard_settings_card")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(StudioSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = NeonCyan)
                        }
                        Column {
                            Text(
                                text = "إعدادات التطبيق وبيئة البناء",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "تخصيص المظهر (Dark Mode)، اللغة (RTL/LTR)، الحفظ التلقائي، وإعدادات المطور",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = NeonCyan)
                }
            }
        }
    }
}

@Composable
private fun DashboardActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = accentColor)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    actionLabel: String,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        TextButton(onClick = onActionClick) {
            Text(text = actionLabel, color = NeonCyan, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProjectSummaryCard(
    project: StudioProjectEntity,
    onOpen: () -> Unit,
    onToggleSave: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US) }
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("project_card_${project.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (project.isIptvModeEnabled) ElectricViolet.copy(alpha = 0.2f)
                        else NeonCyan.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (project.isIptvModeEnabled) Icons.Default.LiveTv else Icons.Default.Code,
                    contentDescription = null,
                    tint = if (project.isIptvModeEnabled) ElectricViolet else NeonCyan
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (project.isIptvModeEnabled) {
                        Badge(containerColor = ElectricViolet) {
                            Text("IPTV + Media3", color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                }
                Text(
                    text = "${project.packageName} • v${project.versionName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyan
                )
                Text(
                    text = "${project.detectedType} • آخر تعديل: ${dateFormat.format(Date(project.updatedAt))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onToggleSave) {
                Icon(
                    imageVector = if (project.isSavedFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "حفظ المشروع",
                    tint = if (project.isSavedFavorite) AmberWarning else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SavedProjectChipCard(
    project: StudioProjectEntity,
    onClick: () -> Unit
) {
    Surface(
        color = StudioSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
        modifier = Modifier
            .width(230.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                Text(
                    text = "v${project.versionName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyan
                )
            }
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = project.detectedType,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BuildHistoryRowCard(
    log: BuildLogEntity,
    onInstallClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US) }
    val apkExists = remember(log.apkFilePath) { File(log.apkFilePath).exists() }

    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(EmeraldGreen.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Android, contentDescription = null, tint = EmeraldGreen)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${log.projectName} v${log.versionName} (${log.versionCode})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${RealApkCompilerEngine.formatFileSize(log.fileSizeBytes)} • ${log.channelsBundled} قناة • ${dateFormat.format(Date(log.timestamp))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyan
                )
                Text(
                    text = log.buildSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (apkExists) {
                Button(
                    onClick = onInstallClick,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("تثبيت APK", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EmptySectionBox(message: String) {
    Surface(
        color = StudioSurface.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}
