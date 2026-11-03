package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class StudioRepository(
    private val dao: StudioDao,
    private val context: Context
) {
    val allProjects: Flow<List<StudioProjectEntity>> = dao.observeAllProjects()
    val savedProjects: Flow<List<StudioProjectEntity>> = dao.observeSavedProjects()
    val allBuildLogs: Flow<List<BuildLogEntity>> = dao.observeAllBuildLogs()

    fun observeChannelsForProject(projectId: Long): Flow<List<IptvChannelEntity>> =
        dao.observeChannelsForProject(projectId)

    suspend fun getChannelsSync(projectId: Long): List<IptvChannelEntity> =
        dao.getChannelsForProjectSync(projectId)

    suspend fun getProjectById(id: Long): StudioProjectEntity? =
        dao.getProjectById(id)

    suspend fun ensureDefaultProjectsSeeded() = withContext(Dispatchers.IO) {
        if (dao.getProjectCount() == 0) {
            val xtvProjectId = dao.insertProject(
                StudioProjectEntity(
                    name = "XTV",
                    packageName = "com.xtv.live",
                    versionName = "2.1.0",
                    versionCode = 21,
                    developerName = "XTV Media Network",
                    appDescription = "تطبيق XTV المستقل للبث التلفزيوني المباشر والقنوات الفضائية",
                    primaryColorHex = "#00E5FF",
                    accentColorHex = "#7C4DFF",
                    iconPreset = "LIVE_TV",
                    orientation = "AUTO",
                    permissionsCsv = "INTERNET,ACCESS_NETWORK_STATE,WAKE_LOCK",
                    htmlCode = SampleTemplates.ARABIC_SPORTS_NEWS_IPTV_HTML,
                    cssCode = SampleTemplates.ARABIC_SPORTS_NEWS_IPTV_CSS,
                    jsCode = SampleTemplates.ARABIC_SPORTS_NEWS_IPTV_JS,
                    detectedType = "تطبيق البث التلفزيوني المباشر IPTV",
                    isIptvModeEnabled = true,
                    isSavedFavorite = true
                )
            )

            val defaultChannels = listOf(
                IptvChannelEntity(
                    projectId = xtvProjectId,
                    name = "قناة الوثائقية الفضائية HD",
                    category = "وثائقية",
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    streamFormat = "HLS (M3U8)",
                    logoUrl = "https://picsum.photos/seed/doc1/120/120",
                    backupServersCsv = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8,https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
                    buttonSelectorId = "onclick:playChannel('قناة الوثائقية الفضائية HD')",
                    sortOrder = 1
                ),
                IptvChannelEntity(
                    projectId = xtvProjectId,
                    name = "الإخبارية العالمية المباشرة 24",
                    category = "إخبارية",
                    streamUrl = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
                    streamFormat = "HLS (M3U8)",
                    logoUrl = "https://picsum.photos/seed/news24/120/120",
                    backupServersCsv = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    buttonSelectorId = "onclick:playChannel('الإخبارية العالمية المباشرة 24')",
                    sortOrder = 2
                ),
                IptvChannelEntity(
                    projectId = xtvProjectId,
                    name = "الرياضية بريميوم 1 FHD",
                    category = "رياضية",
                    streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
                    streamFormat = "HLS (M3U8)",
                    logoUrl = "https://picsum.photos/seed/sport1/120/120",
                    backupServersCsv = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    buttonSelectorId = "onclick:playChannel('الرياضية بريميوم 1 FHD')",
                    sortOrder = 3
                ),
                IptvChannelEntity(
                    projectId = xtvProjectId,
                    name = "سينما ماكس DASH 4K",
                    category = "أفلام",
                    streamUrl = "https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd",
                    streamFormat = "DASH (MPD)",
                    logoUrl = "https://picsum.photos/seed/cinema4k/120/120",
                    backupServersCsv = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    buttonSelectorId = "onclick:playChannel('سينما ماكس DASH 4K')",
                    sortOrder = 4
                )
            )
            dao.insertChannels(defaultChannels)

            val sportProjectId = dao.insertProject(
                StudioProjectEntity(
                    name = "SPORT",
                    packageName = "com.sport.live",
                    versionName = "1.5.0",
                    versionCode = 15,
                    developerName = "Sport Live Broadcast",
                    appDescription = "تطبيق SPORT المستقل للقنوات الرياضية والبطولات المباشرة",
                    primaryColorHex = "#10B981",
                    accentColorHex = "#00E5FF",
                    iconPreset = "LIVE_TV",
                    orientation = "AUTO",
                    permissionsCsv = "INTERNET,ACCESS_NETWORK_STATE,WAKE_LOCK",
                    htmlCode = SampleTemplates.ARABIC_SPORTS_NEWS_IPTV_HTML,
                    cssCode = SampleTemplates.ARABIC_SPORTS_NEWS_IPTV_CSS,
                    jsCode = SampleTemplates.ARABIC_SPORTS_NEWS_IPTV_JS,
                    detectedType = "تطبيق البث التلفزيوني المباشر IPTV",
                    isIptvModeEnabled = true,
                    isSavedFavorite = true
                )
            )

            dao.insertChannels(
                listOf(
                    IptvChannelEntity(
                        projectId = sportProjectId,
                        name = "الرياضية بريميوم 1 FHD",
                        category = "رياضية",
                        streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
                        streamFormat = "HLS (M3U8)",
                        logoUrl = "https://picsum.photos/seed/sport1/120/120",
                        backupServersCsv = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                        buttonSelectorId = "onclick:playChannel('الرياضية بريميوم 1 FHD')",
                        sortOrder = 1
                    ),
                    IptvChannelEntity(
                        projectId = sportProjectId,
                        name = "قناة المحركات والسباقات GT",
                        category = "رياضية",
                        streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
                        streamFormat = "MP4",
                        logoUrl = "https://picsum.photos/seed/motors/120/120",
                        backupServersCsv = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                        buttonSelectorId = "onclick:playChannel('قناة المحركات والسباقات GT')",
                        sortOrder = 2
                    )
                )
            )

            val vodProjectId = dao.insertProject(
                StudioProjectEntity(
                    name = "MOVIES",
                    packageName = "com.movies.player",
                    versionName = "1.4.0",
                    versionCode = 14,
                    developerName = "Cinema Movies Hub",
                    appDescription = "تطبيق MOVIES المستقل لمشاهدة الأفلام والمسلسلات VOD",
                    primaryColorHex = "#7C4DFF",
                    accentColorHex = "#00E5FF",
                    iconPreset = "MOVIE_HUB",
                    orientation = "LANDSCAPE",
                    permissionsCsv = "INTERNET,ACCESS_NETWORK_STATE",
                    htmlCode = SampleTemplates.CINEMA_VOD_JSON_HTML,
                    cssCode = "body { background: #0B0F19; color: #FFFFFF; }",
                    jsCode = "// Native Media3 Bridge Active",
                    detectedType = "تطبيقات مشاهدة الأفلام والمسلسلات",
                    isIptvModeEnabled = true,
                    isSavedFavorite = false
                )
            )

            dao.insertChannels(
                listOf(
                    IptvChannelEntity(
                        projectId = vodProjectId,
                        name = "فيلم دموع الفولاذ Tears of Steel 1080p",
                        category = "أفلام أكشن",
                        streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                        streamFormat = "MP4",
                        logoUrl = "https://picsum.photos/seed/movie1/120/160",
                        backupServersCsv = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                        buttonSelectorId = "onclick:playMovie('Tears of Steel')",
                        sortOrder = 1
                    ),
                    IptvChannelEntity(
                        projectId = vodProjectId,
                        name = "فيلم Big Buck Bunny FHD",
                        category = "أنيميشن",
                        streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        streamFormat = "MP4",
                        logoUrl = "https://picsum.photos/seed/movie2/120/160",
                        backupServersCsv = "https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd",
                        buttonSelectorId = "onclick:playMovie('Big Buck Bunny')",
                        sortOrder = 2
                    )
                )
            )
        }
    }

    suspend fun createProject(project: StudioProjectEntity, channels: List<IptvChannelEntity>): Long {
        val newId = dao.insertProject(project)
        if (channels.isNotEmpty()) {
            dao.insertChannels(channels.map { it.copy(id = 0, projectId = newId) })
        }
        return newId
    }

    suspend fun updateProject(project: StudioProjectEntity) {
        dao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun duplicateProject(project: StudioProjectEntity): Long {
        val existingChannels = dao.getChannelsForProjectSync(project.id)
        val copyProject = project.copy(
            id = 0,
            name = "${project.name} (نسخة)",
            packageName = "${project.packageName}.copy",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastBuiltApkPath = null,
            lastBuiltAt = null
        )
        val newId = dao.insertProject(copyProject)
        dao.insertChannels(existingChannels.map { it.copy(id = 0, projectId = newId) })
        return newId
    }

    suspend fun deleteProject(projectId: Long) {
        dao.deleteAllChannelsForProject(projectId)
        dao.deleteProjectById(projectId)
    }

    suspend fun replaceProjectChannels(projectId: Long, channels: List<IptvChannelEntity>) {
        dao.deleteAllChannelsForProject(projectId)
        dao.insertChannels(channels.mapIndexed { idx, ch ->
            ch.copy(id = 0, projectId = projectId, sortOrder = idx + 1)
        })
    }

    suspend fun addOrUpdateChannel(channel: IptvChannelEntity) {
        if (channel.id == 0L) {
            dao.insertChannel(channel)
        } else {
            dao.updateChannel(channel)
        }
    }

    suspend fun deleteChannel(channelId: Long) {
        dao.deleteChannelById(channelId)
    }

    suspend fun recordBuildLog(log: BuildLogEntity) {
        dao.insertBuildLog(log)
    }

    suspend fun exportBackupJson(): File = withContext(Dispatchers.IO) {
        val projects = mutableListOf<StudioProjectEntity>()
        // Collect snapshot via direct query or Room backup file
        val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
        val backupFile = File(backupDir, "html_to_apk_studio_backup_${System.currentTimeMillis()}.json")
        val root = JSONObject()
        root.put("studioVersion", "1.5.1")
        root.put("exportedAt", System.currentTimeMillis())
        backupFile.writeText(root.toString(2))
        backupFile
    }

    suspend fun exportFullProjectsBackup(projects: List<StudioProjectEntity>): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("studio", "HTML TO APK STUDIO")
        root.put("exportedAt", System.currentTimeMillis())
        val arr = JSONArray()
        for (p in projects) {
            val obj = JSONObject()
            obj.put("name", p.name)
            obj.put("packageName", p.packageName)
            obj.put("versionName", p.versionName)
            obj.put("versionCode", p.versionCode)
            obj.put("developerName", p.developerName)
            obj.put("primaryColorHex", p.primaryColorHex)
            obj.put("accentColorHex", p.accentColorHex)
            obj.put("htmlCode", p.htmlCode)
            obj.put("cssCode", p.cssCode)
            obj.put("jsCode", p.jsCode)
            obj.put("detectedType", p.detectedType)
            obj.put("isIptvModeEnabled", p.isIptvModeEnabled)

            val channels = dao.getChannelsForProjectSync(p.id)
            val chArr = JSONArray()
            for (c in channels) {
                val cObj = JSONObject()
                cObj.put("name", c.name)
                cObj.put("category", c.category)
                cObj.put("streamUrl", c.streamUrl)
                cObj.put("streamFormat", c.streamFormat)
                cObj.put("logoUrl", c.logoUrl)
                cObj.put("backupServersCsv", c.backupServersCsv)
                chArr.put(cObj)
            }
            obj.put("channels", chArr)
            arr.put(obj)
        }
        root.put("projects", arr)
        val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
        val file = File(backupDir, "studio_projects_backup.json")
        file.writeText(root.toString(2))
        file.absolutePath
    }

    suspend fun restoreProjectsFromBackup(jsonContent: String): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(jsonContent)
        val arr = root.optJSONArray("projects") ?: return@withContext 0
        var restoredCount = 0
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val proj = StudioProjectEntity(
                name = obj.optString("name", "Restored Project"),
                packageName = obj.optString("packageName", "com.studio.restored.app"),
                versionName = obj.optString("versionName", "1.0.0"),
                versionCode = obj.optInt("versionCode", 1),
                developerName = obj.optString("developerName", "Studio Developer"),
                primaryColorHex = obj.optString("primaryColorHex", "#00E5FF"),
                accentColorHex = obj.optString("accentColorHex", "#7C4DFF"),
                htmlCode = obj.optString("htmlCode", ""),
                cssCode = obj.optString("cssCode", ""),
                jsCode = obj.optString("jsCode", ""),
                detectedType = obj.optString("detectedType", "مواقع HTML العادية"),
                isIptvModeEnabled = obj.optBoolean("isIptvModeEnabled", false)
            )
            val newId = dao.insertProject(proj)
            val chArr = obj.optJSONArray("channels")
            if (chArr != null) {
                val list = mutableListOf<IptvChannelEntity>()
                for (j in 0 until chArr.length()) {
                    val cObj = chArr.getJSONObject(j)
                    list.add(
                        IptvChannelEntity(
                            projectId = newId,
                            name = cObj.optString("name", "Channel ${j + 1}"),
                            category = cObj.optString("category", "عام"),
                            streamUrl = cObj.optString("streamUrl", ""),
                            streamFormat = cObj.optString("streamFormat", "HLS (M3U8)"),
                            logoUrl = cObj.optString("logoUrl", ""),
                            backupServersCsv = cObj.optString("backupServersCsv", ""),
                            sortOrder = j + 1
                        )
                    )
                }
                dao.insertChannels(list)
            }
            restoredCount++
        }
        restoredCount
    }
}
