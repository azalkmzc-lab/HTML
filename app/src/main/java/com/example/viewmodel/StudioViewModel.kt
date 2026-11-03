package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.analyzer.AnalysisReport
import com.example.analyzer.HtmlAiAnalyzerEngine
import com.example.builder.ApkBuildProgress
import com.example.builder.RealApkCompilerEngine
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

private val Context.dataStore by preferencesDataStore(name = "studio_settings")

enum class StudioDestination {
    DASHBOARD,
    PROJECT_MANAGER,
    BUILD_HISTORY,
    SETTINGS,
    WORKSPACE_EDITOR,
    NATIVE_PLAYER_FULLSCREEN
}

enum class WorkspaceTab {
    CODE_EDITOR,
    AI_ANALYZER,
    IPTV_CONVERTER,
    LIVE_PREVIEW,
    APK_BUILDER
}

data class StudioSettingsState(
    val isDarkTheme: Boolean = true,
    val isRtlArabic: Boolean = true,
    val autoSaveEnabled: Boolean = true,
    val autoAnalyzeOnChange: Boolean = true,
    val defaultDeveloperName: String = "HTML TO APK STUDIO",
    val defaultPackagePrefix: String = "com.studio.apk"
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val database = StudioDatabase.getInstance(application)
    private val repository = StudioRepository(database.studioDao(), application)
    private val analyzerEngine = HtmlAiAnalyzerEngine()
    val compilerEngine = RealApkCompilerEngine(application)

    // Navigation & Active Workspace State
    private val _currentDestination = MutableStateFlow(StudioDestination.DASHBOARD)
    val currentDestination: StateFlow<StudioDestination> = _currentDestination.asStateFlow()

    private val _activeWorkspaceTab = MutableStateFlow(WorkspaceTab.CODE_EDITOR)
    val activeWorkspaceTab: StateFlow<WorkspaceTab> = _activeWorkspaceTab.asStateFlow()

    // Projects & Logs Flows
    val allProjects: StateFlow<List<StudioProjectEntity>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedProjects: StateFlow<List<StudioProjectEntity>> = repository.savedProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBuildLogs: StateFlow<List<BuildLogEntity>> = repository.allBuildLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Project & Channels
    private val _activeProject = MutableStateFlow<StudioProjectEntity?>(null)
    val activeProject: StateFlow<StudioProjectEntity?> = _activeProject.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeProjectChannels: StateFlow<List<IptvChannelEntity>> = _activeProject
        .flatMapLatest { proj ->
            if (proj == null) flowOf(emptyList())
            else repository.observeChannelsForProject(proj.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Extra Multi-Files
    private val _extraFiles = MutableStateFlow<List<ExtraProjectFile>>(emptyList())
    val extraFiles: StateFlow<List<ExtraProjectFile>> = _extraFiles.asStateFlow()

    // AI Analysis Report State
    private val _analysisReport = MutableStateFlow<AnalysisReport?>(null)
    val analysisReport: StateFlow<AnalysisReport?> = _analysisReport.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    // Selected Channel for Native Media3 ExoPlayer 1.5.1
    private val _selectedChannelForPlayback = MutableStateFlow<IptvChannelEntity?>(null)
    val selectedChannelForPlayback: StateFlow<IptvChannelEntity?> = _selectedChannelForPlayback.asStateFlow()

    // APK Build Progress State
    private val _apkBuildState = MutableStateFlow<ApkBuildProgress?>(null)
    val apkBuildState: StateFlow<ApkBuildProgress?> = _apkBuildState.asStateFlow()

    // UI Status Banner / Toast Message
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Settings State
    private val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
    private val KEY_RTL_ARABIC = booleanPreferencesKey("rtl_arabic")
    private val KEY_AUTO_SAVE = booleanPreferencesKey("auto_save")
    private val KEY_DEV_NAME = stringPreferencesKey("dev_name")

    val settingsState: StateFlow<StudioSettingsState> = application.dataStore.data
        .map { prefs ->
            StudioSettingsState(
                isDarkTheme = prefs[KEY_DARK_THEME] ?: true,
                isRtlArabic = prefs[KEY_RTL_ARABIC] ?: true,
                autoSaveEnabled = prefs[KEY_AUTO_SAVE] ?: true,
                defaultDeveloperName = prefs[KEY_DEV_NAME] ?: "HTML TO APK STUDIO"
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StudioSettingsState())

    private var autoSaveJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureDefaultProjectsSeeded()
            val first = repository.allProjects.firstOrNull()?.firstOrNull()
            if (first != null && _activeProject.value == null) {
                loadProjectIntoWorkspace(first, navigateToEditor = false)
            }
        }
    }

    fun showStatus(msg: String) {
        _statusMessage.value = msg
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun navigateTo(destination: StudioDestination) {
        _currentDestination.value = destination
    }

    fun selectWorkspaceTab(tab: WorkspaceTab) {
        _activeWorkspaceTab.value = tab
    }

    fun loadProjectIntoWorkspace(project: StudioProjectEntity, navigateToEditor: Boolean = true) {
        _activeProject.value = project
        _extraFiles.value = parseExtraFiles(project.extraFilesJson)
        _apkBuildState.value = null
        if (navigateToEditor) {
            _currentDestination.value = StudioDestination.WORKSPACE_EDITOR
            _activeWorkspaceTab.value = WorkspaceTab.CODE_EDITOR
        }
        runAiCodeAnalysis(syncChannelsIfEmpty = false)
    }

    fun createNewProject(
        name: String,
        packageName: String,
        templateType: String // IPTV, VOD, BLANK_HTML
    ) {
        viewModelScope.launch {
            val (html, css, js) = when (templateType) {
                "IPTV" -> Triple(
                    SampleTemplates.ARABIC_SPORTS_NEWS_IPTV_HTML,
                    SampleTemplates.ARABIC_SPORTS_NEWS_IPTV_CSS,
                    SampleTemplates.ARABIC_SPORTS_NEWS_IPTV_JS
                )
                "VOD" -> Triple(
                    SampleTemplates.CINEMA_VOD_JSON_HTML,
                    "body { background:#0B0F19; color:#fff; }",
                    "// Native Media3 Bridge Ready"
                )
                else -> Triple(
                    """
                    <!DOCTYPE html>
                    <html lang="ar" dir="rtl">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>$name</title>
                    </head>
                    <body style="background:#0A0E17; color:#F8FAFC; font-family:sans-serif; padding:20px;">
                        <h1 style="color:#00E5FF;">$name</h1>
                        <p>مشروع أندرويد جديد جاهز للتطوير والتحويل إلى APK.</p>
                        <button onclick="playChannel('قناة تجريبية HD', 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8', 'عام')" style="background:#7C4DFF; color:#fff; border:none; padding:12px 20px; border-radius:8px;">
                            تشغيل البث المباشر عبر Media3
                        </button>
                    </body>
                    </html>
                    """.trimIndent(),
                    "body { margin: 0; background: #0A0E17; color: #F8FAFC; }",
                    "function playChannel(title, url, cat) { if (window.AndroidNativePlayer) window.AndroidNativePlayer.openNativeExoPlayer(title, url, cat); }"
                )
            }

            val initialReport = analyzerEngine.analyzeProjectCode(0L, html, css, js)
            val newProj = StudioProjectEntity(
                name = name.ifBlank { "مشروع أندرويد جديد" },
                packageName = packageName.ifBlank { "com.studio.app.custom" },
                developerName = settingsState.value.defaultDeveloperName,
                htmlCode = html,
                cssCode = css,
                jsCode = js,
                detectedType = initialReport.projectTypeArabic,
                isIptvModeEnabled = initialReport.isIptvSmartModeTriggered
            )
            val id = repository.createProject(newProj, initialReport.detectedChannels)
            val saved = repository.getProjectById(id)
            if (saved != null) {
                loadProjectIntoWorkspace(saved, navigateToEditor = true)
                showStatus("تم إنشاء المشروع '${saved.name}' وتحليل الكود تلقائياً!")
            }
        }
    }

    fun createProjectFromPastedHtml(
        projectName: String,
        pastedHtml: String
    ) {
        viewModelScope.launch {
            val report = analyzerEngine.analyzeProjectCode(0L, pastedHtml, "", "")
            val slug = projectName.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "iptvapp" }
            val newProj = StudioProjectEntity(
                name = projectName.ifBlank { "تطبيق HTML مستورد" },
                packageName = "com.studio.$slug.app",
                developerName = settingsState.value.defaultDeveloperName,
                htmlCode = pastedHtml,
                cssCode = "",
                jsCode = "",
                detectedType = report.projectTypeArabic,
                isIptvModeEnabled = report.isIptvSmartModeTriggered
            )
            val id = repository.createProject(newProj, report.detectedChannels)
            val saved = repository.getProjectById(id)
            if (saved != null) {
                loadProjectIntoWorkspace(saved, navigateToEditor = true)
                _activeWorkspaceTab.value = WorkspaceTab.AI_ANALYZER
                showStatus("تم لصق الكود واكتشاف ${report.detectedChannels.size} قناة بث (${report.projectTypeArabic})")
            }
        }
    }

    fun importFileFromUri(uri: Uri, targetType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: return@launch

                val current = _activeProject.value
                if (current == null || targetType == "NEW_HTML_PROJECT") {
                    withContext(Dispatchers.Main) {
                        createProjectFromPastedHtml("مشروع مستورد من الهاتف", content)
                    }
                    return@launch
                }

                val updated = when (targetType) {
                    "HTML" -> current.copy(htmlCode = content)
                    "CSS" -> current.copy(cssCode = content)
                    "JS" -> current.copy(jsCode = content)
                    else -> {
                        val extraList = _extraFiles.value.toMutableList()
                        extraList.add(ExtraProjectFile("imported_${extraList.size + 1}.$targetType", targetType, content))
                        _extraFiles.value = extraList
                        current.copy(extraFilesJson = serializeExtraFiles(extraList))
                    }
                }
                repository.updateProject(updated)
                _activeProject.value = updated
                withContext(Dispatchers.Main) {
                    runAiCodeAnalysis(syncChannelsIfEmpty = true)
                    showStatus("تم استيراد الملف بنجاح وتحديث المشروع تلقائياً.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showStatus("تعذر قراءة الملف: ${e.localizedMessage}")
                }
            }
        }
    }

    fun updateCodeInEditor(
        newHtml: String? = null,
        newCss: String? = null,
        newJs: String? = null
    ) {
        val current = _activeProject.value ?: return
        val updated = current.copy(
            htmlCode = newHtml ?: current.htmlCode,
            cssCode = newCss ?: current.cssCode,
            jsCode = newJs ?: current.jsCode,
            updatedAt = System.currentTimeMillis()
        )
        _activeProject.value = updated

        if (settingsState.value.autoSaveEnabled) {
            autoSaveJob?.cancel()
            autoSaveJob = viewModelScope.launch {
                delay(500L)
                repository.updateProject(updated)
            }
        }
    }

    fun formatAndFixActiveCode() {
        val current = _activeProject.value ?: return
        var fixedHtml = current.htmlCode.trim()
        if (!fixedHtml.contains("<!DOCTYPE html>", ignoreCase = true)) {
            fixedHtml = "<!DOCTYPE html>\n$fixedHtml"
        }
        // Simple indentation normalizer
        val formattedLines = mutableListOf<String>()
        var indent = 0
        fixedHtml.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isNotEmpty()) {
                if (line.startsWith("</")) {
                    indent = (indent - 1).coerceAtLeast(0)
                }
                formattedLines.add("    ".repeat(indent) + line)
                if (line.startsWith("<") &&
                    !line.startsWith("</") &&
                    !line.startsWith("<!") &&
                    !line.startsWith("<meta", ignoreCase = true) &&
                    !line.startsWith("<link", ignoreCase = true) &&
                    !line.startsWith("<img", ignoreCase = true) &&
                    !line.startsWith("<br", ignoreCase = true) &&
                    !line.startsWith("<input", ignoreCase = true) &&
                    !line.contains("</") &&
                    !line.endsWith("/>")
                ) {
                    indent = (indent + 1).coerceAtMost(8)
                }
            }
        }
        val cleanHtml = formattedLines.joinToString("\n")
        updateCodeInEditor(newHtml = cleanHtml)
        runAiCodeAnalysis(syncChannelsIfEmpty = false)
        showStatus("تم تنسيق الكود وإصلاح الترويسة بنجاح!")
    }

    fun runAiCodeAnalysis(syncChannelsIfEmpty: Boolean = false) {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            _isAnalyzing.value = true
            val report = analyzerEngine.analyzeProjectCode(
                projectId = current.id,
                htmlCode = current.htmlCode,
                cssCode = current.cssCode,
                jsCode = current.jsCode
            )
            _analysisReport.value = report

            val updatedProj = current.copy(
                detectedType = report.projectTypeArabic,
                isIptvModeEnabled = report.isIptvSmartModeTriggered
            )
            _activeProject.value = updatedProj
            repository.updateProject(updatedProj)

            val existingChannels = repository.getChannelsSync(current.id)
            if ((syncChannelsIfEmpty || existingChannels.isEmpty()) && report.detectedChannels.isNotEmpty()) {
                repository.replaceProjectChannels(current.id, report.detectedChannels)
            }
            _isAnalyzing.value = false
        }
    }

    fun syncExtractedChannelsFromHtml() {
        val current = _activeProject.value ?: return
        val report = _analysisReport.value ?: return
        viewModelScope.launch {
            if (report.detectedChannels.isNotEmpty()) {
                repository.replaceProjectChannels(current.id, report.detectedChannels)
                showStatus("تم استخراج ومزامنة ${report.detectedChannels.size} قناة من كود HTML بنجاح!")
            } else {
                showStatus("لم يتم العثور على روابط قنوات جديدة في كود HTML.")
            }
        }
    }

    fun applyMedia3UpgradedHtmlToEditor() {
        val report = _analysisReport.value ?: return
        updateCodeInEditor(newHtml = report.upgradedHtmlWithMedia3Bridge)
        showStatus("تم دمج جسر Media3 ExoPlayer 1.5.1 الأصلي داخل كود HTML!")
    }

    fun saveChannel(channel: IptvChannelEntity) {
        viewModelScope.launch {
            repository.addOrUpdateChannel(channel)
            showStatus("تم حفظ بيانات القناة '${channel.name}' بنجاح.")
        }
    }

    fun deleteChannel(channelId: Long) {
        viewModelScope.launch {
            repository.deleteChannel(channelId)
            showStatus("تم حذف القناة.")
        }
    }

    fun openNativeMedia3Player(channel: IptvChannelEntity?) {
        val fallback = channel ?: activeProjectChannels.value.firstOrNull()
        _selectedChannelForPlayback.value = fallback
        _currentDestination.value = StudioDestination.NATIVE_PLAYER_FULLSCREEN
    }

    fun openNativeMedia3PlayerFromUrl(title: String, streamUrl: String, category: String) {
        val currentProjId = _activeProject.value?.id ?: 0L
        val matched = activeProjectChannels.value.find { it.streamUrl == streamUrl }
            ?: IptvChannelEntity(
                projectId = currentProjId,
                name = title.ifBlank { "بث مباشر" },
                category = category.ifBlank { "IPTV" },
                streamUrl = streamUrl,
                streamFormat = analyzerEngine.detectFormatLabel(streamUrl)
            )
        openNativeMedia3Player(matched)
    }

    fun updateProjectMetadata(
        name: String,
        packageName: String,
        versionName: String,
        versionCode: Int,
        developerName: String,
        primaryColorHex: String,
        accentColorHex: String,
        iconPreset: String,
        orientation: String,
        permissionsCsv: String,
        autoCompatibility: Boolean = true,
        minSdk: Int = 21,
        targetSdk: Int = 35,
        compileSdk: Int = 35,
        cpuArchitecture: String = "Universal APK",
        buildSigningMode: String = "DEBUG"
    ) {
        val current = _activeProject.value ?: return
        val updated = current.copy(
            name = name,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            developerName = developerName,
            primaryColorHex = primaryColorHex,
            accentColorHex = accentColorHex,
            iconPreset = iconPreset,
            orientation = orientation,
            permissionsCsv = permissionsCsv,
            autoCompatibility = autoCompatibility,
            minSdk = minSdk,
            targetSdk = targetSdk,
            compileSdk = compileSdk,
            cpuArchitecture = cpuArchitecture,
            buildSigningMode = buildSigningMode,
            updatedAt = System.currentTimeMillis()
        )
        _activeProject.value = updated
        viewModelScope.launch {
            repository.updateProject(updated)
            showStatus("تم حفظ إعدادات التوافق (minSdk=$minSdk, targetSdk=$targetSdk) وتوقيع APK بنجاح.")
        }
    }

    fun toggleProjectFavorite(project: StudioProjectEntity) {
        viewModelScope.launch {
            val updated = project.copy(isSavedFavorite = !project.isSavedFavorite)
            repository.updateProject(updated)
            if (_activeProject.value?.id == project.id) {
                _activeProject.value = updated
            }
        }
    }

    fun duplicateProject(project: StudioProjectEntity) {
        viewModelScope.launch {
            repository.duplicateProject(project)
            showStatus("تم نسخ المشروع '${project.name}' بنجاح.")
        }
    }

    fun renameProject(project: StudioProjectEntity, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val updated = project.copy(name = newName.trim(), updatedAt = System.currentTimeMillis())
            repository.updateProject(updated)
            if (_activeProject.value?.id == project.id) {
                _activeProject.value = updated
            }
            showStatus("تم تغيير اسم المشروع إلى '$newName'.")
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            if (_activeProject.value?.id == projectId) {
                _activeProject.value = allProjects.value.firstOrNull { it.id != projectId }
            }
            showStatus("تم حذف المشروع.")
        }
    }

    fun startRealApkBuild() {
        val project = _activeProject.value ?: return
        viewModelScope.launch {
            val channels = repository.getChannelsSync(project.id)
            val report = _analysisReport.value ?: analyzerEngine.analyzeProjectCode(
                project.id,
                project.htmlCode,
                project.cssCode,
                project.jsCode
            )

            compilerEngine.executeRealApkBuild(project, channels, report).collect { progress ->
                _apkBuildState.value = progress
                if (progress is ApkBuildProgress.Completed) {
                    val updatedProject = project.copy(
                        lastBuiltApkPath = progress.outputApkFile.absolutePath,
                        lastBuiltApkSize = progress.fileSizeBytes,
                        lastBuiltVersion = "${project.versionName} (${project.versionCode})",
                        lastBuiltAt = System.currentTimeMillis()
                    )
                    _activeProject.value = updatedProject
                    repository.updateProject(updatedProject)

                    val det = progress.detailedReport
                    repository.recordBuildLog(
                        BuildLogEntity(
                            projectId = project.id,
                            projectName = project.name,
                            packageName = det.packageName,
                            versionName = project.versionName,
                            versionCode = project.versionCode,
                            minSdk = det.apkValidationResult.parsedMinSdk ?: project.minSdk,
                            targetSdk = det.apkValidationResult.parsedTargetSdk ?: project.targetSdk,
                            compileSdk = project.compileSdk,
                            cpuArchitecture = project.cpuArchitecture,
                            signingMode = project.buildSigningMode,
                            status = "SUCCESS",
                            apkFilePath = progress.outputApkFile.absolutePath,
                            sourceBundlePath = progress.outputSourceZipFile.absolutePath,
                            fileSizeBytes = progress.fileSizeBytes,
                            channelsBundled = channels.size,
                            buildDurationMs = progress.buildDurationMs,
                            buildSummary = "✓ Signed (${project.buildSigningMode}) • MinSDK ${det.minimumAndroidLabel} • TargetSDK ${det.targetAndroidLabel} • ${project.cpuArchitecture} (${channels.size} قناة)"
                        )
                    )
                }
            }
        }
    }

    fun installBuiltApk(apkFile: File) {
        val res = compilerEngine.installGeneratedApk(apkFile)
        res.onFailure { err ->
            showStatus(err.localizedMessage ?: "تعذر بدء تثبيت ملف APK.")
        }
    }

    fun shareBuiltApk(apkFile: File, projectName: String) {
        val res = compilerEngine.shareGeneratedApk(apkFile, projectName)
        res.onFailure { err ->
            showStatus(err.localizedMessage ?: "تعذر مشاركة ملف APK.")
        }
    }

    fun saveApkToDeviceUri(sourceFile: File, targetUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                context.contentResolver.openOutputStream(targetUri)?.use { out: OutputStream ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                }
                withContext(Dispatchers.Main) {
                    showStatus("تم حفظ ملف APK في ذاكرة الهاتف بنجاح! (${RealApkCompilerEngine.formatFileSize(sourceFile.length())})")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showStatus("فشل حفظ ملف APK: ${e.localizedMessage}")
                }
            }
        }
    }

    fun createProjectsBackupFile(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val path = repository.exportFullProjectsBackup(allProjects.value)
            showStatus("تم حفظ النسخة الاحتياطية لجميع المشاريع في: $path")
            onReady(path)
        }
    }

    fun restoreBackupFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: return@launch
                val count = repository.restoreProjectsFromBackup(json)
                withContext(Dispatchers.Main) {
                    showStatus("تم استعادة $count مشروع بنجاح من النسخة الاحتياطية!")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showStatus("فشل استعادة النسخة الاحتياطية: ${e.localizedMessage}")
                }
            }
        }
    }

    fun updateSettings(
        darkTheme: Boolean? = null,
        rtlArabic: Boolean? = null,
        autoSave: Boolean? = null,
        devName: String? = null
    ) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                darkTheme?.let { prefs[KEY_DARK_THEME] = it }
                rtlArabic?.let { prefs[KEY_RTL_ARABIC] = it }
                autoSave?.let { prefs[KEY_AUTO_SAVE] = it }
                devName?.let { prefs[KEY_DEV_NAME] = it }
            }
        }
    }

    private fun parseExtraFiles(jsonStr: String): List<ExtraProjectFile> {
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<ExtraProjectFile>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ExtraProjectFile(
                        fileName = obj.optString("fileName"),
                        fileType = obj.optString("fileType"),
                        content = obj.optString("content")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeExtraFiles(files: List<ExtraProjectFile>): String {
        val arr = JSONArray()
        files.forEach { f ->
            val obj = JSONObject()
            obj.put("fileName", f.fileName)
            obj.put("fileType", f.fileType)
            obj.put("content", f.content)
            arr.put(obj)
        }
        return arr.toString()
    }
}
