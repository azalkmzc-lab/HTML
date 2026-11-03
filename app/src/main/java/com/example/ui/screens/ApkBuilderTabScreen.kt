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
import com.example.builder.AndroidCompatibilityManager
import com.example.builder.ApkBuildProgress
import com.example.builder.BuildStepStatus
import com.example.builder.DeviceCompatibilityCheck
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
        permsCsv: String,
        autoCompatibility: Boolean,
        minSdk: Int,
        targetSdk: Int,
        compileSdk: Int,
        cpuArchitecture: String,
        buildSigningMode: String
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

    // Android Compatibility, Architecture & Signing States
    var autoCompatibility by remember(project.id) { mutableStateOf(project.autoCompatibility) }
    var selectedMinSdk by remember(project.id) { mutableIntStateOf(project.minSdk) }
    var selectedTargetSdk by remember(project.id) { mutableIntStateOf(project.targetSdk) }
    var selectedCompileSdk by remember(project.id) { mutableIntStateOf(project.compileSdk) }
    var cpuArchitecture by remember(project.id) { mutableStateOf(project.cpuArchitecture) }
    var buildSigningMode by remember(project.id) { mutableStateOf(project.buildSigningMode) }

    var showInstallPreCheckDialog by remember { mutableStateOf<File?>(null) }

    // Ensure minSdk is never equal to or greater than targetSdk when manually adjusted
    LaunchedEffect(autoCompatibility) {
        if (autoCompatibility) {
            val (autoMin, autoTarget, autoCompile) = AndroidCompatibilityManager.computeAutoCompatibilitySdks()
            selectedMinSdk = autoMin
            selectedTargetSdk = autoTarget
            selectedCompileSdk = autoCompile
        }
    }

    val liveLibraryReport = remember(selectedMinSdk, selectedTargetSdk, selectedCompileSdk) {
        AndroidCompatibilityManager.verifyLibraryAndSdkCompatibility(
            selectedMinSdk = selectedMinSdk,
            selectedTargetSdk = selectedTargetSdk,
            selectedCompileSdk = selectedCompileSdk
        )
    }

    val liveDeviceCheck = remember(selectedMinSdk, selectedTargetSdk, selectedCompileSdk, cpuArchitecture, buildState) {
        val validation = (buildState as? ApkBuildProgress.Completed)?.detailedReport?.apkValidationResult
        AndroidCompatibilityManager.inspectCurrentDeviceCompatibility(
            apkMinSdk = selectedMinSdk,
            apkTargetSdk = selectedTargetSdk,
            apkCompileSdk = selectedCompileSdk,
            selectedArchitecture = cpuArchitecture,
            validationResult = validation
        )
    }

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
                            text = "نظام بناء وتوقيع APK الذكي (Android Compatibility & Signer)",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Binary AXML + ZipAlign 4-Byte + RSA-2048 Signer + Media3 1.5.1 ($channelsCount قناة)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeonCyan
                        )
                    }
                }
            }
        }

        // 2. Android Compatibility & SDK Selector Card (Min SDK, Target SDK, Compile SDK, Auto Compatibility)
        item {
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Android Compatibility (توافق إصدارات أندرويد)",
                                style = MaterialTheme.typography.titleMedium,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "فصل دقيق بين minSdk و targetSdk و compileSdk لضمان عمل APK على الأجهزة القديمة والحديثة",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    // Auto Compatibility Toggle
                    Surface(
                        color = StudioSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (autoCompatibility) EmeraldGreen else StudioCardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "التوافق التلقائي (Auto Compatibility — موصى به)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (autoCompatibility) EmeraldGreen else TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "يضبط تلقائياً: minSdk = 21 (Android 5.0) • targetSdk = 35 (Android 15) • compileSdk = 35 ليعمل على أكبر عدد من الأجهزة",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = autoCompatibility,
                                onCheckedChange = { enabled ->
                                    autoCompatibility = enabled
                                    if (enabled) {
                                        val (m, t, c) = AndroidCompatibilityManager.computeAutoCompatibilitySdks()
                                        selectedMinSdk = m
                                        selectedTargetSdk = t
                                        selectedCompileSdk = c
                                    }
                                },
                                modifier = Modifier.testTag("switch_auto_compatibility")
                            )
                        }
                    }

                    // Quick Compatibility Tier Presets (Legacy 5.0+, Medium 8.0+, Modern 12+, Latest 15/16)
                    Text(
                        text = "مستويات التوافق السريعة:",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val presets = listOf(
                            Triple("تلقائي (شامل)", 21, true),
                            Triple("أجهزة قديمة (Android 5.0+ → API 21)", 21, false),
                            Triple("متوسط التوافق (Android 8.0+ → API 26)", 26, false),
                            Triple("تطبيق حديث (Android 12+ → API 31)", 31, false),
                            Triple("أحدث إصدار (Android 14+ → API 34)", 34, false)
                        )
                        items(presets) { (label, minApi, isAutoPreset) ->
                            val isSelected = (isAutoPreset && autoCompatibility) || (!isAutoPreset && !autoCompatibility && selectedMinSdk == minApi)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    autoCompatibility = isAutoPreset
                                    selectedMinSdk = minApi
                                    if (selectedTargetSdk <= minApi) {
                                        selectedTargetSdk = 35
                                    }
                                    if (selectedCompileSdk < selectedTargetSdk) {
                                        selectedCompileSdk = 35
                                    }
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    // Detailed Minimum Android Version Picker (Android 5.0 -> API 21 up to Android 16 -> API 36)
                    Text(
                        text = "Minimum Android Version (أقدم إصدار أندرويد مدعوم - minSdk):",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = autoCompatibility,
                                onClick = {
                                    autoCompatibility = true
                                    val (m, t, c) = AndroidCompatibilityManager.computeAutoCompatibilitySdks()
                                    selectedMinSdk = m
                                    selectedTargetSdk = t
                                    selectedCompileSdk = c
                                },
                                label = { Text("تلقائي (API 21)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldGreen,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                        items(AndroidCompatibilityManager.SUPPORTED_SDK_OPTIONS) { sdkOpt ->
                            val isSelected = !autoCompatibility && selectedMinSdk == sdkOpt.apiLevel
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    autoCompatibility = false
                                    selectedMinSdk = sdkOpt.apiLevel
                                    // Ensure minSdk is never equal to targetSdk when possible
                                    if (selectedTargetSdk <= sdkOpt.apiLevel) {
                                        selectedTargetSdk = (sdkOpt.apiLevel + 1).coerceIn(33, 36)
                                    }
                                    if (selectedCompileSdk < selectedTargetSdk) {
                                        selectedCompileSdk = selectedTargetSdk.coerceAtLeast(35)
                                    }
                                },
                                label = { Text(sdkOpt.displayLabel) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    // Target SDK & Compile SDK Pickers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Target SDK (الإصدار المستهدف):", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val targetOptions = AndroidCompatibilityManager.SUPPORTED_SDK_OPTIONS.filter { it.apiLevel >= 28 }
                                items(targetOptions) { opt ->
                                    FilterChip(
                                        selected = selectedTargetSdk == opt.apiLevel,
                                        onClick = {
                                            autoCompatibility = false
                                            selectedTargetSdk = opt.apiLevel.coerceAtLeast((selectedMinSdk + 1).coerceAtMost(36))
                                            if (selectedCompileSdk < selectedTargetSdk) {
                                                selectedCompileSdk = selectedTargetSdk
                                            }
                                        },
                                        label = { Text("API ${opt.apiLevel} (${opt.androidVersionName})") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ElectricViolet,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Compile SDK (إصدار الترجمة المتوفر في البناء):", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val compileOptions = listOf(34, 35, 36)
                                items(compileOptions) { api ->
                                    FilterChip(
                                        selected = selectedCompileSdk == api,
                                        onClick = {
                                            autoCompatibility = false
                                            selectedCompileSdk = api
                                            if (selectedTargetSdk > api) {
                                                selectedTargetSdk = api
                                            }
                                        },
                                        label = { Text(AndroidCompatibilityManager.formatSdkWithAndroidName(api)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = EmeraldGreen,
                                            selectedLabelColor = Color.Black
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Active SDK Separation Summary Banner
                    Surface(
                        color = StudioBackground,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text("minSdk = $selectedMinSdk", color = NeonCyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Text("targetSdk = $selectedTargetSdk", color = ElectricViolet, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Text("compileSdk = $selectedCompileSdk", color = EmeraldGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // 3. Pre-Build Library Compatibility Inspector Card (Media3 1.5.1, AndroidX, Kotlin, AGP)
        item {
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (liveLibraryReport.isAllCompatible) EmeraldGreen.copy(alpha = 0.5f) else CrimsonError
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "فحص توافق المكتبات (Library & SDK Verification)",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (liveLibraryReport.isAllCompatible) EmeraldGreen else CrimsonError,
                            fontWeight = FontWeight.Bold
                        )
                        Badge(containerColor = if (liveLibraryReport.isAllCompatible) EmeraldGreen else CrimsonError) {
                            Text(
                                text = if (liveLibraryReport.isAllCompatible) "متوافق 100%" else "يوجد تعارض",
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                        }
                    }

                    liveLibraryReport.libraryChecks.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isCompatibleWithSelected) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (item.isCompatibleWithSelected) EmeraldGreen else CrimsonError,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = "${item.libraryName} (${item.version})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.compatibilityNote,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    if (liveLibraryReport.blockingMessageArabic != null) {
                        Surface(
                            color = CrimsonError.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${liveLibraryReport.blockingMessageArabic}\n💡 أقل إصدار مقترح: API ${liveLibraryReport.suggestedMinSdk}",
                                color = CrimsonError,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. CPU Architecture (ABI) & Signing Mode (Debug APK vs Release APK) Card
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
                        text = "معمارية المعالج (CPU Architecture / ABI) والتوقيع الرقمي (Signing)",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )

                    Text("اختر معمارية المعالج (Architecture):", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AndroidCompatibilityManager.SUPPORTED_ARCHITECTURES) { (archName, _) ->
                            FilterChip(
                                selected = cpuArchitecture == archName,
                                onClick = { cpuArchitecture = archName },
                                label = { Text(archName) },
                                leadingIcon = {
                                    Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                    val selectedArchDesc = AndroidCompatibilityManager.SUPPORTED_ARCHITECTURES.find { it.first == cpuArchitecture }?.second ?: ""
                    Text(selectedArchDesc, style = MaterialTheme.typography.bodyMedium, color = EmeraldGreen, fontSize = 12.sp)

                    HorizontalDivider(color = StudioCardBorder)

                    Text("نظام توقيع الحزمة (APK Signing Mode):", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val modes = listOf(
                            "DEBUG" to ("Debug APK (توقيع تطوير قياسي)" to "موقع بشهادة Android Debug Keystore لاختبار التثبيت الفوري"),
                            "RELEASE" to ("Release APK (توقيع نهائي بمفتاح خاص)" to "ينشئ ويحفظ Keystore خاص بالمشروع (RSA-2048) لتوقيع الإصدار النهائي والتحديثات")
                        )
                        modes.forEach { (modeCode, pair) ->
                            val (title, desc) = pair
                            val isSelected = buildSigningMode == modeCode
                            Surface(
                                color = if (isSelected) StudioSurfaceVariant else StudioBackground,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp,
                                    if (isSelected) NeonCyan else StudioCardBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { buildSigningMode = modeCode }
                                    .testTag("signing_mode_$modeCode")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (modeCode == "RELEASE") Icons.Default.VerifiedUser else Icons.Default.BugReport,
                                            contentDescription = null,
                                            tint = if (isSelected) NeonCyan else TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) NeonCyan else TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Device Compatibility Pre-Check Card (فحص الجهاز قبل البناء والتثبيت)
        item {
            DeviceCompatibilityCard(deviceCheck = liveDeviceCheck)
        }

        // 6. APK Identity & Permissions Form
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
                        text = "هوية التطبيق والأذونات (Manifest & Package Config)",
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
                            label = { Text("اسم المطور (Keystore CN)") },
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

                    Text("اختيار أيقونة التطبيق الناتج:", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(iconPresets) { (code, label) ->
                            FilterChip(
                                selected = iconPreset == code,
                                onClick = { iconPreset = code },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    Text("اختيار اللون الرئيسي:", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        colorChoices.forEach { (hex, colorVal) ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
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

                    Text("تحديد اتجاه الشاشة:", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
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

                    Text("الأذونات المطلوبة داخل AndroidManifest.xml:", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        allPermissions.forEach { (permKey, permDesc) ->
                            val isChecked = selectedPermissions.contains(permKey)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPermissions = if (isChecked) selectedPermissions - permKey else selectedPermissions + permKey
                                    }
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedPermissions = if (checked) selectedPermissions + permKey else selectedPermissions - permKey
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = NeonCyan, checkmarkColor = Color.Black)
                                )
                                Text("$permKey — $permDesc", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
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
                                    selectedPermissions.joinToString(","),
                                    autoCompatibility,
                                    selectedMinSdk,
                                    selectedTargetSdk,
                                    selectedCompileSdk,
                                    cpuArchitecture,
                                    buildSigningMode
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
                                    selectedPermissions.joinToString(","),
                                    autoCompatibility,
                                    selectedMinSdk,
                                    selectedTargetSdk,
                                    selectedCompileSdk,
                                    cpuArchitecture,
                                    buildSigningMode
                                )
                                onStartRealApkBuild()
                            },
                            enabled = liveLibraryReport.isAllCompatible,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("btn_start_real_apk_build")
                        ) {
                            Icon(Icons.Default.BuildCircle, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (buildSigningMode == "RELEASE") "بناء وتوقيع Release APK" else "بناء وتوقيع Debug APK",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 7. 9-Stage Live Build Progress, Validation & Detailed Build Report
        if (buildState != null) {
            item {
                BuildProgressCardSection(
                    project = project,
                    buildState = buildState,
                    onInstallApkRequest = { apkFile ->
                        showInstallPreCheckDialog = apkFile
                    },
                    onShareApk = onShareApk,
                    onSaveApkToPhone = onSaveApkToPhone,
                    onRebuildApk = onStartRealApkBuild
                )
            }
        }
    }

    // Device Compatibility Check Modal Dialog Before Installing APK
    val fileToInstall = showInstallPreCheckDialog
    if (fileToInstall != null) {
        AlertDialog(
            onDismissRequest = { showInstallPreCheckDialog = null },
            containerColor = StudioSurface,
            title = {
                Text(
                    text = "فحص توافق الجهاز قبل التثبيت (Device Compatibility Check)",
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Android Version: ${liveDeviceCheck.deviceAndroidVersion}", color = TextPrimary)
                    Text("• API Level: ${liveDeviceCheck.deviceApiLevel}", color = TextPrimary)
                    Text("• Device CPU Architecture: ${liveDeviceCheck.devicePrimaryAbi}", color = TextPrimary)
                    Text("• APK Minimum SDK: ${liveDeviceCheck.apkMinSdk}", color = NeonCyan)
                    Text("• APK Target SDK: ${liveDeviceCheck.apkTargetSdk}", color = NeonCyan)
                    Text("• APK Architecture: ${liveDeviceCheck.apkArchitecture}", color = NeonCyan)
                    Text(
                        text = "• هل APK موقع رقمياً؟ ${if (liveDeviceCheck.isApkSigned) "نعم ✓" else "لا ✗"}",
                        color = if (liveDeviceCheck.isApkSigned) EmeraldGreen else CrimsonError
                    )
                    Text(
                        text = "• هل بنية APK وBinary Manifest صالحة؟ ${if (liveDeviceCheck.isApkStructureValid) "نعم ✓" else "لا ✗"}",
                        color = if (liveDeviceCheck.isApkStructureValid) EmeraldGreen else CrimsonError
                    )
                    HorizontalDivider(color = StudioCardBorder)
                    Text(
                        text = liveDeviceCheck.statusSummaryArabic,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (liveDeviceCheck.canInstallOnThisDevice) EmeraldGreen else CrimsonError,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showInstallPreCheckDialog = null
                        onInstallApk(fileToInstall)
                    },
                    enabled = liveDeviceCheck.canInstallOnThisDevice,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("متابعة تثبيت APK الآن", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstallPreCheckDialog = null }) {
                    Text("إغلاق", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun DeviceCompatibilityCard(deviceCheck: DeviceCompatibilityCheck) {
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (deviceCheck.canInstallOnThisDevice) EmeraldGreen.copy(alpha = 0.6f) else AmberWarning
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device_compatibility_card")
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
                    text = "Device Compatibility Check (فحص الجهاز الحالي)",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
                Badge(containerColor = if (deviceCheck.canInstallOnThisDevice) EmeraldGreen else CrimsonError) {
                    Text(
                        text = if (deviceCheck.canInstallOnThisDevice) "متوافق للتثبيت ✓" else "غير متوافق ✗",
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Android Version: ${deviceCheck.deviceAndroidVersion}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text("API Level: ${deviceCheck.deviceApiLevel}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text("Architecture: ${deviceCheck.devicePrimaryAbi}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
                    Text("Minimum SDK: ${deviceCheck.apkMinSdk}", color = NeonCyan, style = MaterialTheme.typography.bodyMedium)
                    Text("Target SDK: ${deviceCheck.apkTargetSdk}", color = ElectricViolet, style = MaterialTheme.typography.bodyMedium)
                    Text("Compile SDK: ${deviceCheck.apkCompileSdk}", color = EmeraldGreen, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Surface(
                color = StudioSurfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Status: ${deviceCheck.statusSummaryArabic}",
                    color = if (deviceCheck.canInstallOnThisDevice) EmeraldGreen else CrimsonError,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
private fun BuildProgressCardSection(
    project: StudioProjectEntity,
    buildState: ApkBuildProgress,
    onInstallApkRequest: (File) -> Unit,
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
            Text(
                text = when (buildState) {
                    is ApkBuildProgress.InProgress -> "جاري بناء وتوقيع حزمة APK (${(buildState.progressFraction * 100).toInt()}%)..."
                    is ApkBuildProgress.Completed -> "اكتمل البناء والتوقيع والفحص بنجاح! ✓"
                    is ApkBuildProgress.Failed -> "فشلت عملية البناء أو فحص صلاحية الحزمة"
                },
                style = MaterialTheme.typography.titleLarge,
                color = when (buildState) {
                    is ApkBuildProgress.Completed -> EmeraldGreen
                    is ApkBuildProgress.Failed -> CrimsonError
                    is ApkBuildProgress.InProgress -> NeonCyan
                },
                fontWeight = FontWeight.Bold
            )

            if (buildState is ApkBuildProgress.Failed) {
                Surface(
                    color = CrimsonError.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "سبب الفشل الحقيقي:\n${buildState.errorMessage}",
                        color = CrimsonError,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
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

            steps.forEach { step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when {
                        step.isCompleted -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                        step.isActive -> CircularProgressIndicator(modifier = Modifier.size(18.dp), color = NeonCyan, strokeWidth = 2.dp)
                        else -> Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
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

            // Detailed Post-Build Report & Actions
            if (buildState is ApkBuildProgress.Completed) {
                val report = buildState.detailedReport
                HorizontalDivider(color = StudioCardBorder)

                Surface(
                    color = StudioSurfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "تقرير البناء والفحص المفصل (Detailed Build & Validation Report):",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text("✓ Build Status: ${report.buildStatus}", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                        Text("✓ APK Validation: ${if (report.apkValidationResult.isValid) "✓ APK Valid (ZIP + DEX + Uncompressed resources.arsc)" else "✗ Invalid"}", color = EmeraldGreen)
                        Text("✓ Signing Status: ${report.signingStatus}", color = EmeraldGreen)
                        Text("✓ Manifest Validation: ${if (report.apkValidationResult.hasBinaryManifest) "✓ Binary AXML Manifest Valid" else "✗ Invalid"}", color = EmeraldGreen)
                        Text("✓ Install Compatibility: ${if (report.deviceCompatibility.canInstallOnThisDevice) "✓ Compatible with this Device" else "✗ Not Compatible"}", color = if (report.deviceCompatibility.canInstallOnThisDevice) EmeraldGreen else CrimsonError)
                        HorizontalDivider(color = StudioCardBorder)
                        Text("• APK Size: ${report.apkSizeFormatted}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Text("• Minimum Android: ${report.minimumAndroidLabel}", color = NeonCyan, style = MaterialTheme.typography.bodyMedium)
                        Text("• Target Android: ${report.targetAndroidLabel}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Text("• Compile SDK: ${report.compileSdkLabel}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Text("• CPU Architecture: ${report.cpuArchitecture}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Text("• Package Name: ${report.packageName}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Text("• Version: ${report.versionName} (versionCode = ${report.versionCode})", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Text("• Certificate SHA-256: ${report.certSha256Fingerprint.take(29)}...", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Primary Install & Share Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onInstallApkRequest(buildState.outputApkFile) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_install_generated_apk")
                    ) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فحص وتثبيت APK", color = Color.Black, fontWeight = FontWeight.Bold)
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
