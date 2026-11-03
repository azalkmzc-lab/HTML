package com.example.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.builder.ApkBuildProgress
import com.example.builder.BuildStepStatus
import com.example.builder.RealApkCompilerEngine
import com.example.data.StudioProjectEntity
import com.example.ui.theme.*
import java.io.File

@Composable
fun ApkBuilderTabScreen(
    project: StudioProjectEntity,
    channelsCount: Int,
    buildState: ApkBuildProgress?,
    onSaveMetadata: (
        name: String,
        pkg: String,
        verName: String,
        verCode: Int,
        dev: String,
        primaryHex: String,
        accentHex: String,
        iconPreset: String,
        orientation: String,
        permsCsv: String
    ) -> Unit,
    onStartRealApkBuild: () -> Unit,
    onInstallApk: (File) -> Unit,
    onShareApk: (File, String) -> Unit,
    onSaveApkToPhone: (File) -> Unit
) {
    var appName by remember(project.id) { mutableStateOf(project.name) }
    var packageName by remember(project.id) { mutableStateOf(project.packageName) }
    var versionName by remember(project.id) { mutableStateOf(project.versionName) }
    var versionCodeText by remember(project.id) { mutableStateOf(project.versionCode.toString()) }
    var developerName by remember(project.id) { mutableStateOf(project.developerName) }
    var primaryColorHex by remember(project.id) { mutableStateOf(project.primaryColorHex) }
    var accentColorHex by remember(project.id) { mutableStateOf(project.accentColorHex) }
    var iconPreset by remember(project.id) { mutableStateOf(project.iconPreset) }
    var orientation by remember(project.id) { mutableStateOf(project.orientation) }

    val allPermissions = listOf(
        "INTERNET" to "الاتصال بالإنترنت وبث القنوات",
        "ACCESS_NETWORK_STATE" to "فحص حالة الشبكة والواي فاي",
        "WAKE_LOCK" to "منع انطفاء الشاشة أثناء المشاهدة",
        "FOREGROUND_SERVICE" to "تشغيل الوسائط في الخلفية",
        "POST_NOTIFICATIONS" to "إشعارات البث المباشر"
    )
    var selectedPermissions by remember(project.id) {
        mutableStateOf(
            project.permissionsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        )
    }

    val colorChoices = listOf(
        "#00E5FF" to Color(0xFF00E5FF),
        "#7C4DFF" to Color(0xFF7C4DFF),
        "#10B981" to Color(0xFF10B981),
        "#F59E0B" to Color(0xFFF59E0B),
        "#EF4444" to Color(0xFFEF4444),
        "#EC4899" to Color(0xFFEC4899)
    )

    val iconPresets = listOf(
        "LIVE_TV" to "بث تلفزيوني IPTV",
        "MOVIE_HUB" to "سينما وأفلام VOD",
        "WEB_APP" to "تطبيق ويب HTML5",
        "MUSIC_WAVE" to "مشغل صوتيات وميديا"
    )

    val orientations = listOf(
        "AUTO" to "تلقائي (Auto Rotate)",
        "PORTRAIT" to "عمودي (Portrait)",
        "LANDSCAPE" to "أفقي (Landscape TV)"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackground)
            .testTag("apk_builder_tab_screen"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Card
        item {
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(NeonCyan.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(30.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "نظام بناء وتحويل HTML إلى APK حقيقي",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "تجهيز الحزمة + دمج مشغل Media3 ExoPlayer 1.5.1 ($channelsCount قناة مدمجة)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeonCyan
                        )
                    }
                }
            }
        }

        // 2. APK Configuration Form (App Name, Package Name, Version, Developer, Orientation, Colors, Icon, Permissions)
        item {
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "إعدادات وهوية حزمة التطبيق (APK Configuration)",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = appName,
                            onValueChange = { appName = it },
                            label = { Text("اسم التطبيق (App Name)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_apk_app_name")
                        )
                        OutlinedTextField(
                            value = developerName,
                            onValueChange = { developerName = it },
                            label = { Text("اسم المطور") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        label = { Text("معرف الحزمة (Package Name)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_apk_package_name")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = versionName,
                            onValueChange = { versionName = it },
                            label = { Text("إصدار التطبيق (Version Name)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = versionCodeText,
                            onValueChange = { versionCodeText = it.filter { c -> c.isDigit() } },
                            label = { Text("رقم الإصدار (Version Code)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Icon Preset Selection
                    Text("اختيار أيقونة التطبيق الناتج:", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(iconPresets) { (code, label) ->
                            FilterChip(
                                selected = iconPreset == code,
                                onClick = { iconPreset = code },
                                label = { Text(label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (code) {
                                            "LIVE_TV" -> Icons.Default.LiveTv
                                            "MOVIE_HUB" -> Icons.Default.Movie
                                            "MUSIC_WAVE" -> Icons.Default.GraphicEq
                                            else -> Icons.Default.Language
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    // App Theme Colors Selection
                    Text("اختيار اللون الرئيسي للتطبيق والمشغل:", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        colorChoices.forEach { (hex, colorVal) ->
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(colorVal)
                                    .border(
                                        width = if (primaryColorHex == hex) 3.dp else 1.dp,
                                        color = if (primaryColorHex == hex) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { primaryColorHex = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (primaryColorHex == hex) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // Screen Orientation Selection
                    Text("تحديد اتجاه الشاشة (Screen Orientation):", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(orientations) { (code, label) ->
                            FilterChip(
                                selected = orientation == code,
                                onClick = { orientation = code },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricViolet,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    // Android Permissions Checkboxes
                    Text("الأذونات المطلوبة داخل ملف APK:", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        allPermissions.forEach { (permKey, permDesc) ->
                            val isChecked = selectedPermissions.contains(permKey)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPermissions = if (isChecked) {
                                            selectedPermissions - permKey
                                        } else {
                                            selectedPermissions + permKey
                                        }
                                    }
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedPermissions = if (checked) {
                                            selectedPermissions + permKey
                                        } else {
                                            selectedPermissions - permKey
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = NeonCyan, checkmarkColor = Color.Black)
                                )
                                Text(
                                    text = "$permKey — $permDesc",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Save & Start Build Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onSaveMetadata(
                                    appName,
                                    packageName,
                                    versionName,
                                    versionCodeText.toIntOrNull() ?: 1,
                                    developerName,
                                    primaryColorHex,
                                    accentColorHex,
                                    iconPreset,
                                    orientation,
                                    selectedPermissions.joinToString(",")
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = NeonCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حفظ الإعدادات", color = NeonCyan)
                        }

                        Button(
                            onClick = {
                                onSaveMetadata(
                                    appName,
                                    packageName,
                                    versionName,
                                    versionCodeText.toIntOrNull() ?: 1,
                                    developerName,
                                    primaryColorHex,
                                    accentColorHex,
                                    iconPreset,
                                    orientation,
                                    selectedPermissions.joinToString(",")
                                )
                                onStartRealApkBuild()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("btn_start_real_apk_build")
                        ) {
                            Icon(Icons.Default.BuildCircle, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("بناء ملف APK الآن", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. 9-Stage Live Build Progress & Console Output
        if (buildState != null) {
            item {
                BuildProgressCardSection(
                    project = project,
                    buildState = buildState,
                    onInstallApk = onInstallApk,
                    onShareApk = onShareApk,
                    onSaveApkToPhone = onSaveApkToPhone,
                    onRebuildApk = onStartRealApkBuild
                )
            }
        }
    }
}

@Composable
private fun BuildProgressCardSection(
    project: StudioProjectEntity,
    buildState: ApkBuildProgress,
    onInstallApk: (File) -> Unit,
    onShareApk: (File, String) -> Unit,
    onSaveApkToPhone: (File) -> Unit,
    onRebuildApk: () -> Unit
) {
    val steps: List<BuildStepStatus> = when (buildState) {
        is ApkBuildProgress.InProgress -> buildState.steps
        is ApkBuildProgress.Completed -> buildState.steps
        is ApkBuildProgress.Failed -> emptyList()
    }

    val logs: List<String> = when (buildState) {
        is ApkBuildProgress.InProgress -> buildState.consoleLogs
        is ApkBuildProgress.Completed -> buildState.consoleLogs
        is ApkBuildProgress.Failed -> buildState.consoleLogs
    }

    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            when (buildState) {
                is ApkBuildProgress.Completed -> EmeraldGreen
                is ApkBuildProgress.Failed -> CrimsonError
                is ApkBuildProgress.InProgress -> NeonCyan
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("apk_build_progress_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (buildState) {
                        is ApkBuildProgress.InProgress -> "جاري بناء حزمة APK (${(buildState.progressFraction * 100).toInt()}%)..."
                        is ApkBuildProgress.Completed -> "اكتمل بناء وتوقيع ملف APK بنجاح! ✓"
                        is ApkBuildProgress.Failed -> "فشلت عملية البناء"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = when (buildState) {
                        is ApkBuildProgress.Completed -> EmeraldGreen
                        is ApkBuildProgress.Failed -> CrimsonError
                        is ApkBuildProgress.InProgress -> NeonCyan
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            if (buildState is ApkBuildProgress.InProgress) {
                LinearProgressIndicator(
                    progress = { buildState.progressFraction },
                    color = NeonCyan,
                    trackColor = StudioSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            // 9 Mandatory Build Stages Checklist
            steps.forEach { step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when {
                        step.isCompleted -> Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        step.isActive -> CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = NeonCyan,
                            strokeWidth = 2.dp
                        )
                        else -> Icon(
                            Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = step.titleArabic,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (step.isCompleted || step.isActive) TextPrimary else TextMuted,
                            fontWeight = if (step.isActive) FontWeight.Bold else FontWeight.Normal
                        )
                        if (step.isCompleted || step.isActive) {
                            Text(
                                text = step.detailLog,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeonCyan,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Console Output Box
            Surface(
                color = Color(0xFF06090F),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    logs.takeLast(5).forEach { logLine ->
                        Text(
                            text = logLine,
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldGreen,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Post-Build Result Actions (Install APK, Share APK, Save APK to Phone, Rebuild)
            if (buildState is ApkBuildProgress.Completed) {
                HorizontalDivider(color = StudioCardBorder)

                Surface(
                    color = StudioSurfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "بيانات ملف APK الناتج:",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text("• اسم التطبيق: ${project.name}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Text("• الحزمة والإصدار: ${project.packageName} — v${project.versionName} (${project.versionCode})", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Text("• حجم ملف APK الفعلي: ${RealApkCompilerEngine.formatFileSize(buildState.fileSizeBytes)}", color = EmeraldGreen, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("• بصمة التوقيع SHA-256: ${buildState.sha256Signature.take(24)}...", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                        Text("• المسار: ${buildState.outputApkFile.name}", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Primary Install & Share Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onInstallApk(buildState.outputApkFile) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_install_generated_apk")
                    ) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تثبيت التطبيق", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onShareApk(buildState.outputApkFile, project.name) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_share_generated_apk")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاركة APK", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // Save APK to Phone & Rebuild Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSaveApkToPhone(buildState.outputApkFile) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_save_apk_to_phone")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = NeonCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حفظ APK في الهاتف", color = NeonCyan)
                    }

                    OutlinedButton(
                        onClick = onRebuildApk,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = AmberWarning)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إعادة بناء APK", color = AmberWarning)
                    }
                }
            }
        }
    }
}
