package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.SampleTemplates
import com.example.player.NativeMedia3PlayerScreen
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.viewmodel.StudioDestination
import com.example.viewmodel.StudioViewModel
import com.example.viewmodel.WorkspaceTab
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: StudioViewModel = viewModel()
            val settings by viewModel.settingsState.collectAsStateWithLifecycle()

            HtmlToApkStudioTheme(
                darkTheme = settings.isDarkTheme,
                isRtl = settings.isRtlArabic
            ) {
                HtmlToApkStudioRootApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlToApkStudioRootApp(viewModel: StudioViewModel) {
    val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val activeWorkspaceTab by viewModel.activeWorkspaceTab.collectAsStateWithLifecycle()
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val savedProjects by viewModel.savedProjects.collectAsStateWithLifecycle()
    val allBuildLogs by viewModel.allBuildLogs.collectAsStateWithLifecycle()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val activeChannels by viewModel.activeProjectChannels.collectAsStateWithLifecycle()
    val extraFiles by viewModel.extraFiles.collectAsStateWithLifecycle()
    val analysisReport by viewModel.analysisReport.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val selectedChannelForPlayback by viewModel.selectedChannelForPlayback.collectAsStateWithLifecycle()
    val apkBuildState by viewModel.apkBuildState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()

    // Dialog States
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showPasteHtmlDialog by remember { mutableStateOf(false) }
    var pendingImportType by remember { mutableStateOf("HTML") }
    var pendingApkFileToSave by remember { mutableStateOf<File?>(null) }

    // File Import Launcher (HTML, CSS, JS, Backup JSON)
    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            if (pendingImportType == "RESTORE_BACKUP") {
                viewModel.restoreBackupFromUri(uri)
            } else {
                viewModel.importFileFromUri(uri, pendingImportType)
            }
        }
    }

    // Save APK to Phone Storage Launcher (CreateDocument)
    val saveApkDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { targetUri ->
        val file = pendingApkFileToSave
        if (targetUri != null && file != null) {
            viewModel.saveApkToDeviceUri(file, targetUri)
        }
    }

    // Handle Android Back Navigation across custom screens
    if (currentDestination != StudioDestination.DASHBOARD) {
        BackHandler {
            if (currentDestination == StudioDestination.NATIVE_PLAYER_FULLSCREEN) {
                viewModel.navigateTo(StudioDestination.WORKSPACE_EDITOR)
            } else {
                viewModel.navigateTo(StudioDestination.DASHBOARD)
            }
        }
    }

    // Fullscreen Native Media3 ExoPlayer 1.5.1 Destination
    if (currentDestination == StudioDestination.NATIVE_PLAYER_FULLSCREEN) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = StudioBackground
        ) {
            NativeMedia3PlayerScreen(
                channels = activeChannels,
                initialChannel = selectedChannelForPlayback,
                autoRetryEnabled = activeProject?.autoRetryStream ?: true,
                onBack = { viewModel.navigateTo(StudioDestination.WORKSPACE_EDITOR) }
            )
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = StudioBackground,
            topBar = {
                if (currentDestination == StudioDestination.WORKSPACE_EDITOR && activeProject != null) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = StudioSurface,
                            titleContentColor = TextPrimary
                        ),
                        navigationIcon = {
                            IconButton(
                                onClick = { viewModel.navigateTo(StudioDestination.DASHBOARD) },
                                modifier = Modifier.testTag("workspace_back_to_dashboard_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "العودة للرئيسية", tint = NeonCyan)
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = activeProject?.name ?: "مشروع",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${activeProject?.packageName} • ${activeProject?.detectedType}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonCyan,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.openNativeMedia3Player(activeChannels.firstOrNull()) },
                                modifier = Modifier.testTag("top_open_media3_player_btn")
                            ) {
                                Icon(Icons.Default.PlayCircleFilled, contentDescription = "مشغل Media3", tint = EmeraldGreen)
                            }
                            Button(
                                onClick = { viewModel.selectWorkspaceTab(WorkspaceTab.APK_BUILDER) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .testTag("top_build_apk_btn")
                            ) {
                                Icon(Icons.Default.Android, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("بناء APK", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!isWideScreen) {
                    StudioBottomNavigationBar(
                        currentDestination = currentDestination,
                        onSelectDestination = { dest -> viewModel.navigateTo(dest) }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Tablet / Foldable Navigation Rail
                if (isWideScreen) {
                    StudioSideNavigationRail(
                        currentDestination = currentDestination,
                        onSelectDestination = { dest -> viewModel.navigateTo(dest) }
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Status Toast Banner
                    AnimatedVisibility(visible = statusMessage != null) {
                        Surface(
                            color = StudioSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = statusMessage ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NeonCyan,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.clearStatus() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSecondary)
                                }
                            }
                        }
                    }

                    // Screen Content Switcher
                    when (currentDestination) {
                        StudioDestination.DASHBOARD -> {
                            StudioDashboardScreen(
                                allProjects = allProjects,
                                savedProjects = savedProjects,
                                buildLogs = allBuildLogs,
                                onNewProjectClick = { showNewProjectDialog = true },
                                onPasteHtmlClick = { showPasteHtmlDialog = true },
                                onImportHtmlFileClick = {
                                    pendingImportType = "NEW_HTML_PROJECT"
                                    fileImportLauncher.launch(arrayOf("text/html", "text/plain", "*/*"))
                                },
                                onOpenProject = { proj ->
                                    viewModel.loadProjectIntoWorkspace(proj, navigateToEditor = true)
                                },
                                onToggleSaveProject = { proj ->
                                    viewModel.toggleProjectFavorite(proj)
                                },
                                onInstallApkFromLog = { path ->
                                    viewModel.installBuiltApk(File(path))
                                },
                                onNavigateToSection = { dest ->
                                    viewModel.navigateTo(dest)
                                }
                            )
                        }

                        StudioDestination.PROJECT_MANAGER -> {
                            ProjectManagerScreen(
                                allProjects = allProjects,
                                onCreateProjectClick = { showNewProjectDialog = true },
                                onOpenProject = { proj ->
                                    viewModel.loadProjectIntoWorkspace(proj, navigateToEditor = true)
                                },
                                onDuplicateProject = { proj ->
                                    viewModel.duplicateProject(proj)
                                },
                                onRenameProject = { proj, newName ->
                                    viewModel.renameProject(proj, newName)
                                },
                                onDeleteProject = { id ->
                                    viewModel.deleteProject(id)
                                },
                                onToggleSaveProject = { proj ->
                                    viewModel.toggleProjectFavorite(proj)
                                },
                                onBackupProjectsClick = {
                                    viewModel.createProjectsBackupFile { }
                                },
                                onRestoreProjectsClick = {
                                    pendingImportType = "RESTORE_BACKUP"
                                    fileImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                },
                                onInstallLastApk = { file ->
                                    viewModel.installBuiltApk(file)
                                }
                            )
                        }

                        StudioDestination.BUILD_HISTORY -> {
                            BuildHistoryScreen(
                                buildLogs = allBuildLogs,
                                onInstallApk = { file -> viewModel.installBuiltApk(file) },
                                onShareApk = { file, name -> viewModel.shareBuiltApk(file, name) },
                                onSaveApkToPhone = { file ->
                                    pendingApkFileToSave = file
                                    saveApkDocumentLauncher.launch(file.name)
                                }
                            )
                        }

                        StudioDestination.SETTINGS -> {
                            StudioSettingsScreen(
                                settings = settings,
                                onUpdateSettings = { dark, rtl, autoSave, devName ->
                                    viewModel.updateSettings(dark, rtl, autoSave, devName)
                                }
                            )
                        }

                        StudioDestination.WORKSPACE_EDITOR -> {
                            val proj = activeProject
                            if (proj != null) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Workspace Sub-Tabs (Editor, AI Analyzer, IPTV Converter, Preview, APK Builder)
                                    ScrollableTabRow(
                                        selectedTabIndex = activeWorkspaceTab.ordinal,
                                        containerColor = StudioSurface,
                                        contentColor = NeonCyan,
                                        edgePadding = 12.dp
                                    ) {
                                        val tabs = listOf(
                                            WorkspaceTab.CODE_EDITOR to ("محرر الأكواد" to Icons.Default.Code),
                                            WorkspaceTab.AI_ANALYZER to ("محلل الذكاء AI" to Icons.Default.Psychology),
                                            WorkspaceTab.IPTV_CONVERTER to ("نظام IPTV والقنوات" to Icons.Default.LiveTv),
                                            WorkspaceTab.LIVE_PREVIEW to ("المعاينة المباشرة" to Icons.Default.Visibility),
                                            WorkspaceTab.APK_BUILDER to ("بناء APK حقيقي" to Icons.Default.Android)
                                        )
                                        tabs.forEach { (tab, pair) ->
                                            val (label, icon) = pair
                                            Tab(
                                                selected = activeWorkspaceTab == tab,
                                                onClick = { viewModel.selectWorkspaceTab(tab) },
                                                text = { Text(label, fontWeight = FontWeight.Bold) },
                                                icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp)) },
                                                modifier = Modifier.testTag("workspace_tab_${tab.name}")
                                            )
                                        }
                                    }

                                    // Active Workspace Tab Content
                                    Box(modifier = Modifier.weight(1f)) {
                                        when (activeWorkspaceTab) {
                                            WorkspaceTab.CODE_EDITOR -> {
                                                CodeEditorTabScreen(
                                                    project = proj,
                                                    extraFiles = extraFiles,
                                                    codeIssues = analysisReport?.codeErrors ?: emptyList(),
                                                    autoSaveEnabled = settings.autoSaveEnabled,
                                                    onCodeChanged = { html, css, js ->
                                                        viewModel.updateCodeInEditor(html, css, js)
                                                    },
                                                    onFormatCode = { viewModel.formatAndFixActiveCode() },
                                                    onImportFileClick = { fileType ->
                                                        pendingImportType = fileType
                                                        fileImportLauncher.launch(arrayOf("*/*"))
                                                    },
                                                    onAnalyzeClick = {
                                                        viewModel.runAiCodeAnalysis(syncChannelsIfEmpty = true)
                                                        viewModel.selectWorkspaceTab(WorkspaceTab.AI_ANALYZER)
                                                    },
                                                    onPreviewClick = {
                                                        viewModel.selectWorkspaceTab(WorkspaceTab.LIVE_PREVIEW)
                                                    }
                                                )
                                            }

                                            WorkspaceTab.AI_ANALYZER -> {
                                                AiAnalyzerAndIptvScreen(
                                                    mode = "AI_ANALYZER",
                                                    analysisReport = analysisReport,
                                                    channels = activeChannels,
                                                    isAnalyzing = isAnalyzing,
                                                    onReAnalyzeClick = { viewModel.runAiCodeAnalysis(syncChannelsIfEmpty = false) },
                                                    onSyncExtractedChannels = { viewModel.syncExtractedChannelsFromHtml() },
                                                    onApplyMedia3BridgeToHtml = { viewModel.applyMedia3UpgradedHtmlToEditor() },
                                                    onPlayChannelInMedia3 = { ch -> viewModel.openNativeMedia3Player(ch) },
                                                    onSaveChannel = { ch -> viewModel.saveChannel(ch.copy(projectId = proj.id)) },
                                                    onDeleteChannel = { id -> viewModel.deleteChannel(id) },
                                                    onProceedToBuildApk = { viewModel.selectWorkspaceTab(WorkspaceTab.APK_BUILDER) }
                                                )
                                            }

                                            WorkspaceTab.IPTV_CONVERTER -> {
                                                AiAnalyzerAndIptvScreen(
                                                    mode = "IPTV_CONVERTER",
                                                    analysisReport = analysisReport,
                                                    channels = activeChannels,
                                                    isAnalyzing = isAnalyzing,
                                                    onReAnalyzeClick = { viewModel.runAiCodeAnalysis(syncChannelsIfEmpty = true) },
                                                    onSyncExtractedChannels = { viewModel.syncExtractedChannelsFromHtml() },
                                                    onApplyMedia3BridgeToHtml = { viewModel.applyMedia3UpgradedHtmlToEditor() },
                                                    onPlayChannelInMedia3 = { ch -> viewModel.openNativeMedia3Player(ch) },
                                                    onSaveChannel = { ch -> viewModel.saveChannel(ch.copy(projectId = proj.id)) },
                                                    onDeleteChannel = { id -> viewModel.deleteChannel(id) },
                                                    onProceedToBuildApk = { viewModel.selectWorkspaceTab(WorkspaceTab.APK_BUILDER) }
                                                )
                                            }

                                            WorkspaceTab.LIVE_PREVIEW -> {
                                                LivePreviewTabScreen(
                                                    project = proj,
                                                    upgradedHtml = analysisReport?.upgradedHtmlWithMedia3Bridge ?: proj.htmlCode,
                                                    channels = activeChannels,
                                                    onTriggerNativeMedia3Player = { title, url, cat ->
                                                        viewModel.openNativeMedia3PlayerFromUrl(title, url, cat)
                                                    },
                                                    onOpenFullMedia3Player = {
                                                        viewModel.openNativeMedia3Player(activeChannels.firstOrNull())
                                                    }
                                                )
                                            }

                                            WorkspaceTab.APK_BUILDER -> {
                                                ApkBuilderTabScreen(
                                                    project = proj,
                                                    channelsCount = activeChannels.size,
                                                    buildState = apkBuildState,
                                                    onSaveMetadata = { name, pkg, verName, verCode, dev, primary, accent, icon, orient, perms ->
                                                        viewModel.updateProjectMetadata(
                                                            name, pkg, verName, verCode, dev, primary, accent, icon, orient, perms
                                                        )
                                                    },
                                                    onStartRealApkBuild = { viewModel.startRealApkBuild() },
                                                    onInstallApk = { file -> viewModel.installBuiltApk(file) },
                                                    onShareApk = { file, name -> viewModel.shareBuiltApk(file, name) },
                                                    onSaveApkToPhone = { file ->
                                                        pendingApkFileToSave = file
                                                        saveApkDocumentLauncher.launch(file.name)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        StudioDestination.NATIVE_PLAYER_FULLSCREEN -> {}
                    }
                }
            }
        }
    }

    // Create New Project Modal Dialog
    if (showNewProjectDialog) {
        NewProjectModalDialog(
            onDismiss = { showNewProjectDialog = false },
            onCreate = { name, pkg, templateType ->
                showNewProjectDialog = false
                viewModel.createNewProject(name, pkg, templateType)
            }
        )
    }

    // Paste HTML Code Modal Dialog
    if (showPasteHtmlDialog) {
        PasteHtmlModalDialog(
            onDismiss = { showPasteHtmlDialog = false },
            onConfirmPaste = { projName, pastedHtml ->
                showPasteHtmlDialog = false
                viewModel.createProjectFromPastedHtml(projName, pastedHtml)
            }
        )
    }
}

@Composable
private fun StudioBottomNavigationBar(
    currentDestination: StudioDestination,
    onSelectDestination: (StudioDestination) -> Unit
) {
    NavigationBar(
        containerColor = StudioSurface,
        contentColor = TextPrimary
    ) {
        val navItems = listOf(
            StudioDestination.DASHBOARD to ("الرئيسية" to Icons.Default.Dashboard),
            StudioDestination.WORKSPACE_EDITOR to ("الاستوديو" to Icons.Default.Code),
            StudioDestination.PROJECT_MANAGER to ("مشاريعي" to Icons.Default.FolderCopy),
            StudioDestination.BUILD_HISTORY to ("سجل APK" to Icons.Default.History),
            StudioDestination.SETTINGS to ("الإعدادات" to Icons.Default.Settings)
        )
        navItems.forEach { (dest, pair) ->
            val (label, icon) = pair
            NavigationBarItem(
                selected = currentDestination == dest,
                onClick = { onSelectDestination(dest) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = StudioBackground,
                    selectedTextColor = NeonCyan,
                    indicatorColor = NeonCyan,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                ),
                modifier = Modifier.testTag("nav_${dest.name}")
            )
        }
    }
}

@Composable
private fun StudioSideNavigationRail(
    currentDestination: StudioDestination,
    onSelectDestination: (StudioDestination) -> Unit
) {
    NavigationRail(
        containerColor = StudioSurface,
        contentColor = TextPrimary
    ) {
        val navItems = listOf(
            StudioDestination.DASHBOARD to ("الرئيسية" to Icons.Default.Dashboard),
            StudioDestination.WORKSPACE_EDITOR to ("الاستوديو" to Icons.Default.Code),
            StudioDestination.PROJECT_MANAGER to ("مشاريعي" to Icons.Default.FolderCopy),
            StudioDestination.BUILD_HISTORY to ("سجل APK" to Icons.Default.History),
            StudioDestination.SETTINGS to ("الإعدادات" to Icons.Default.Settings)
        )
        navItems.forEach { (dest, pair) ->
            val (label, icon) = pair
            NavigationRailItem(
                selected = currentDestination == dest,
                onClick = { onSelectDestination(dest) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = StudioBackground,
                    selectedTextColor = NeonCyan,
                    indicatorColor = NeonCyan
                )
            )
        }
    }
}

@Composable
private fun NewProjectModalDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, pkg: String, templateType: String) -> Unit
) {
    var name by remember { mutableStateOf("تطبيقي للبث المباشر IPTV") }
    var pkg by remember { mutableStateOf("com.studio.iptv.custom") }
    var selectedTemplate by remember { mutableStateOf("IPTV") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface,
        title = { Text("إنشاء مشروع أندرويد جديد", color = NeonCyan, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المشروع / التطبيق") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it },
                    label = { Text("Package Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("اختر القالب الابتدائي:", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                val templates = listOf(
                    "IPTV" to "قالب قنوات بث مباشر IPTV متكامل (M3U8/MPD/MP4)",
                    "VOD" to "قالب منصة أفلام ومسلسلات VOD + JSON",
                    "BLANK_HTML" to "مشروع HTML5/CSS/JS مخصص"
                )
                templates.forEach { (code, title) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedTemplate == code,
                            onClick = { selectedTemplate = code },
                            colors = RadioButtonDefaults.colors(selectedColor = NeonCyan)
                        )
                        Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, pkg, selectedTemplate) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                modifier = Modifier.testTag("confirm_create_project_btn")
            ) {
                Text("إنشاء وفتح المحرر", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun PasteHtmlModalDialog(
    onDismiss: () -> Unit,
    onConfirmPaste: (projectName: String, htmlCode: String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var projectName by remember { mutableStateOf("تطبيق IPTV من كود ملصق") }
    var htmlInput by remember {
        val clip = clipboardManager.getText()?.text
        mutableStateOf(
            if (!clip.isNullOrBlank() && clip.contains("<")) clip
            else SampleTemplates.ARABIC_SPORTS_NEWS_IPTV_HTML
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface,
        title = { Text("لصق كود HTML وتحليله بالذكاء الاصطناعي", color = NeonCyan, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("اسم التطبيق") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("كود HTML / IPTV:", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                    TextButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) htmlInput = clip
                        }
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("لصق من الحافظة", color = NeonCyan)
                    }
                }
                OutlinedTextField(
                    value = htmlInput,
                    onValueChange = { htmlInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmPaste(projectName, htmlInput) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                modifier = Modifier.testTag("confirm_paste_html_btn")
            ) {
                Icon(Icons.Default.Psychology, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("تحليل الكود واستخراج القنوات", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}
