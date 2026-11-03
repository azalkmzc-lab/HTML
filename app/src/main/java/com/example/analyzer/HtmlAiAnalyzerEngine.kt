package com.example.analyzer

import com.example.BuildConfig
import com.example.data.IptvChannelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CodeIssue(
    val line: Int,
    val severity: String, // ERROR, WARNING, INFO
    val message: String,
    val suggestion: String
)

data class FirebaseDetectionSummary(
    val usesFirebase: Boolean = false,
    val usesRealtimeDatabase: Boolean = false,
    val usesCloudFirestore: Boolean = false,
    val usesFirebaseAuth: Boolean = false,
    val usesFirebaseStorage: Boolean = false,
    val projectId: String? = null,
    val databaseUrl: String? = null,
    val detectedPathsAndCollections: List<String> = emptyList(),
    val dynamicChannelFunctions: List<String> = emptyList()
)

data class AnalysisReport(
    val projectTypeArabic: String,
    val projectTypeCode: Int, // 1..7 matching user prompt
    val isIptvSmartModeTriggered: Boolean,
    val detectedChannels: List<IptvChannelEntity>,
    val streamUrls: List<String>,
    val videoFormatsUsed: List<String>,
    val requiredPlayerType: String,
    val detectedCategories: List<String>,
    val detectedLogos: List<String>,
    val detectedButtonsAndTriggers: List<String>,
    val jsonAndApiEndpoints: List<String>,
    val missingFiles: List<String>,
    val codeErrors: List<CodeIssue>,
    val suggestedImprovements: List<String>,
    val aiDeepSummary: String,
    val upgradedHtmlWithMedia3Bridge: String,
    val firebaseSummary: FirebaseDetectionSummary = FirebaseDetectionSummary()
)

class HtmlAiAnalyzerEngine {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeProjectCode(
        projectId: Long,
        htmlCode: String,
        cssCode: String,
        jsCode: String
    ): AnalysisReport = withContext(Dispatchers.Default) {
        val combinedCode = "$htmlCode\n$cssCode\n$jsCode"

        // 1. Detect Firebase Configuration, Services, Paths & Collections (Preserving 100% of original config)
        val firebaseSummary = analyzeFirebaseUsage(combinedCode)

        // 2. Extract all stream URLs (.m3u8, .mpd, .mp4, .ts, .aac, .mp3, rtmp://, etc.)
        val streamUrlRegex = Regex(
            """(https?://[^\s"'<>]+?\.(?:m3u8|mpd|mp4|ts|mkv|webm|aac|mp3)(?:\?[^\s"'<>]*)?)""",
            RegexOption.IGNORE_CASE
        )
        val streamUrls = streamUrlRegex.findAll(combinedCode)
            .map { it.groupValues[1].trim() }
            .distinct()
            .toList()

        // 3. Detect video formats used
        val formats = mutableSetOf<String>()
        streamUrls.forEach { url ->
            val lower = url.lowercase()
            when {
                lower.contains(".m3u8") -> formats.add("HLS (M3U8)")
                lower.contains(".mpd") -> formats.add("DASH (MPD)")
                lower.contains(".mp4") -> formats.add("MP4 (H.264/HEVC)")
                lower.contains(".ts") -> formats.add("MPEG-TS")
                lower.contains(".webm") || lower.contains(".mkv") -> formats.add("WebM / Matroska")
                lower.contains(".mp3") || lower.contains(".aac") -> formats.add("Audio Stream (AAC/MP3)")
            }
        }
        if (combinedCode.contains("application/x-mpegURL", ignoreCase = true) ||
            combinedCode.contains("m3u8", ignoreCase = true) ||
            combinedCode.contains("Hls", ignoreCase = true)
        ) {
            formats.add("HLS (M3U8)")
        }
        if (combinedCode.contains("application/dash+xml", ignoreCase = true) ||
            combinedCode.contains(".mpd", ignoreCase = true)
        ) {
            formats.add("DASH (MPD)")
        }
        if (firebaseSummary.usesFirebase && formats.isEmpty()) {
            formats.add("HLS (M3U8) / MP4 (Dynamic Firebase Streams)")
        }

        // 4. Extract Channel Cards, M3U entries, Firebase seed/fallback channels, onclick triggers, logos, and categories
        val extractedChannels = extractChannelsFromCode(projectId, htmlCode, jsCode, streamUrls)

        // Extract categories from both static channels and HTML category tabs / Firebase category arrays
        val htmlCategoryTabs = extractCategoriesFromDomAndJs(htmlCode, jsCode)
        val categories = (extractedChannels.map { it.category } + htmlCategoryTabs)
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "الكل" }
            .distinct()

        val logos = extractedChannels.map { it.logoUrl }.filter { it.isNotBlank() }.distinct()

        // 5. Extract onclick buttons, addEventListener triggers & JS playback functions
        val buttonTriggers = mutableListOf<String>()
        val onclickRegex = Regex("""onclick\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        onclickRegex.findAll(htmlCode).forEach { match ->
            buttonTriggers.add(match.groupValues[1].trim())
        }
        val addEventListenerRegex = Regex("""addEventListener\s*\(\s*['"]click['"]""", RegexOption.IGNORE_CASE)
        val clickListenersCount = addEventListenerRegex.findAll(combinedCode).count()
        if (clickListenersCount > 0) {
            buttonTriggers.add("addEventListener('click') ($clickListenersCount معالج أحداث ديناميكي)")
        }
        firebaseSummary.dynamicChannelFunctions.forEach { fnName ->
            buttonTriggers.add("JS Function: $fnName(...) -> Media3 ExoPlayer Auto-Bridge")
        }
        if (htmlCode.contains("<video", ignoreCase = true)) {
            buttonTriggers.add("<video> HTML5 Tag -> Media3 ExoPlayer Auto-Hook")
        }

        // 6. Detect JSON, Firebase & API endpoints
        val apiRegex = Regex("""(https?://[^\s"'<>]+?(?:\.json|/api/|/v1/|/v2/|playlist|firebaseio\.com|firebasedatabase\.app|firestore\.googleapis\.com)[^\s"'<>]*)""", RegexOption.IGNORE_CASE)
        val jsonEndpoints = (
            apiRegex.findAll(combinedCode).map { it.groupValues[1] }.toList() +
                listOfNotNull(firebaseSummary.databaseUrl)
            )
            .filter { !streamUrls.contains(it) }
            .distinct()

        // 7. Detect Missing Files (linked CSS/JS files not provided inline or in tabs)
        val missingFiles = mutableListOf<String>()
        val linkHrefRegex = Regex("""<link[^>]+href\s*=\s*["']([^"']+\.css)["']""", RegexOption.IGNORE_CASE)
        linkHrefRegex.findAll(htmlCode).forEach { m ->
            val href = m.groupValues[1]
            if (!href.startsWith("http") && cssCode.isBlank()) {
                missingFiles.add("$href (ملف تنسيق CSS مشار إليه ولكن تبويب CSS فارغ)")
            }
        }
        val scriptSrcRegex = Regex("""<script[^>]+src\s*=\s*["']([^"']+\.js)["']""", RegexOption.IGNORE_CASE)
        scriptSrcRegex.findAll(htmlCode).forEach { m ->
            val src = m.groupValues[1]
            if (!src.startsWith("http") && jsCode.isBlank()) {
                missingFiles.add("$src (ملف سكريبت JS مشار إليه ولكن تبويب JS فارغ)")
            }
        }

        // 8. Detect Code Errors & Warnings
        val codeIssues = inspectCodeSyntaxAndSecurity(htmlCode, cssCode, jsCode)

        // 9. Classify into Project Types & Automatically Trigger IPTV Converter Mode
        val hasIptvKeywords = formats.contains("HLS (M3U8)") ||
            combinedCode.contains("iptv", ignoreCase = true) ||
            combinedCode.contains("channel", ignoreCase = true) ||
            combinedCode.contains("قنوات") ||
            combinedCode.contains("قناة") ||
            combinedCode.contains("بث مباشر") ||
            firebaseSummary.dynamicChannelFunctions.isNotEmpty()

        val hasVodKeywords = combinedCode.contains("فيلم") || combinedCode.contains("أفلام") ||
            combinedCode.contains("مسلسل") || combinedCode.contains("movie", ignoreCase = true) ||
            combinedCode.contains("series", ignoreCase = true) || combinedCode.contains("vod", ignoreCase = true)
        val hasVideoTag = htmlCode.contains("<video", ignoreCase = true) || htmlCode.contains("<audio", ignoreCase = true)

        val (projectTypeCode, projectTypeArabic) = when {
            firebaseSummary.usesFirebase && hasIptvKeywords ->
                2 to "تطبيق IPTV وبث مباشر متصل بـ Firebase"
            (hasIptvKeywords && extractedChannels.size >= 2) || combinedCode.contains("iptv", ignoreCase = true) ->
                2 to "تطبيقات البث التلفزيوني المباشر IPTV"
            hasVodKeywords && (streamUrls.isNotEmpty() || extractedChannels.isNotEmpty() || firebaseSummary.usesFirebase) ->
                3 to "تطبيقات مشاهدة الأفلام والمسلسلات (VOD)"
            extractedChannels.size >= 2 && streamUrls.isNotEmpty() ->
                6 to "تطبيقات تحتوي على قوائم قنوات وروابط بث"
            firebaseSummary.usesFirebase || jsonEndpoints.isNotEmpty() || combinedCode.contains("fetch(", ignoreCase = true) ->
                7 to "تطبيقات تعتمد على Firebase وملفات JSON وواجهات API"
            hasVideoTag && streamUrls.size == 1 ->
                5 to "مشغلات الوسائط (Media Players)"
            streamUrls.isNotEmpty() || hasVideoTag || hasIptvKeywords ->
                4 to "تطبيقات الفيديو والصوت"
            else ->
                1 to "مواقع HTML العادية"
        }

        val isIptvModeTriggered = projectTypeCode in listOf(2, 3, 4, 5, 6) ||
            streamUrls.isNotEmpty() ||
            hasIptvKeywords ||
            (firebaseSummary.usesFirebase && (extractedChannels.isNotEmpty() || firebaseSummary.dynamicChannelFunctions.isNotEmpty()))

        val requiredPlayerType = if (isIptvModeTriggered) {
            "AndroidX Media3 ExoPlayer 1.5.1 + PlayerActivity (Native HLS/DASH/MP4 Engine)"
        } else {
            "Android WebView Engine + Smart Back Navigation Bridge"
        }

        // 10. Build Suggested Modifications
        val suggestions = mutableListOf<String>()
        if (firebaseSummary.usesFirebase) {
            val fbServices = buildList {
                if (firebaseSummary.usesRealtimeDatabase) add("Realtime Database")
                if (firebaseSummary.usesCloudFirestore) add("Cloud Firestore")
                if (firebaseSummary.usesFirebaseAuth) add("Authentication")
                if (firebaseSummary.usesFirebaseStorage) add("Storage")
            }.ifEmpty { listOf("Firebase Core") }.joinToString(" + ")
            suggestions.add("الحفاظ الكامل على اتصال Firebase الأصلي ($fbServices) وجلب القنوات والتصنيفات مباشرة من السحابة أثناء تشغيل APK دون تحويلها لبيانات ثابتة.")
        }
        suggestions.add("تفعيل نظام الرجوع الذكي (Smart Back Navigation) لمزامنة سجل WebView وHistory API والقوائم المنبثقة والتصنيفات ومشغل الفيديو ومنع الخروج المفاجئ من التطبيق.")
        if (isIptvModeTriggered) {
            suggestions.add("تفعيل وضع IPTV Converter تلقائياً وربط جميع القنوات (بما فيها المحملة ديناميكياً من Firebase) بـ PlayerActivity ومشغل Media3 ExoPlayer 1.5.1 الأصلي.")
        }
        if (htmlCode.contains("<video", ignoreCase = true)) {
            suggestions.add("اعتراض عنصر <video> التقليدي في HTML وتوجيه البث تلقائياً إلى مشغل Media3 ExoPlayer 1.5.1 الأصلي.")
        }
        if (formats.any { it.contains("HLS") }) {
            suggestions.add("تفعيل وحدة media3-exoplayer-hls 1.5.1 لدعم البث المباشر المتكيف (Adaptive Bitrate) وإعادة المحاولة عند انقطاع الإنترنت.")
        }
        if (formats.any { it.contains("DASH") }) {
            suggestions.add("تفعيل وحدة media3-exoplayer-dash 1.5.1 لتشغيل تدفقات MPD عالية الدقة.")
        }

        // 11. Upgrade HTML code automatically with Smart Back Navigation + Firebase Preserver + Media3 Native Interceptor
        val upgradedHtml = injectMedia3NativeBridgeIntoHtml(htmlCode, cssCode, jsCode, extractedChannels)

        // 12. Summary
        val aiSummary = fetchOptionalGeminiInsightOrFallback(
            projectTypeArabic = projectTypeArabic,
            channelCount = extractedChannels.size,
            formats = formats.toList(),
            issuesCount = codeIssues.size,
            firebaseSummary = firebaseSummary
        )

        AnalysisReport(
            projectTypeArabic = projectTypeArabic,
            projectTypeCode = projectTypeCode,
            isIptvSmartModeTriggered = isIptvModeTriggered,
            detectedChannels = extractedChannels,
            streamUrls = streamUrls,
            videoFormatsUsed = if (formats.isEmpty()) listOf("لا توجد صيغ فيديو مباشرة") else formats.toList(),
            requiredPlayerType = requiredPlayerType,
            detectedCategories = if (categories.isEmpty()) listOf("عام") else categories,
            detectedLogos = logos,
            detectedButtonsAndTriggers = buttonTriggers,
            jsonAndApiEndpoints = jsonEndpoints,
            missingFiles = missingFiles,
            codeErrors = codeIssues,
            suggestedImprovements = suggestions,
            aiDeepSummary = aiSummary,
            upgradedHtmlWithMedia3Bridge = upgradedHtml,
            firebaseSummary = firebaseSummary
        )
    }

    /**
     * Inspects HTML/JS code for Firebase configurations, Realtime Database, Cloud Firestore,
     * Firebase Auth, Firebase Storage, database paths/collections, and dynamic channel functions.
     */
    fun analyzeFirebaseUsage(combinedCode: String): FirebaseDetectionSummary {
        val hasFirebaseKeyword = combinedCode.contains("firebase", ignoreCase = true) ||
            combinedCode.contains("initializeApp", ignoreCase = true) ||
            combinedCode.contains("firebaseio.com", ignoreCase = true) ||
            combinedCode.contains("firebasedatabase.app", ignoreCase = true) ||
            combinedCode.contains("firestore", ignoreCase = true)

        val usesRealtimeDb = combinedCode.contains("firebase-database", ignoreCase = true) ||
            combinedCode.contains("getDatabase", ignoreCase = true) ||
            combinedCode.contains("firebase.database", ignoreCase = true) ||
            combinedCode.contains("firebaseio.com", ignoreCase = true) ||
            combinedCode.contains("firebasedatabase.app", ignoreCase = true) ||
            combinedCode.contains("onValue(", ignoreCase = true)

        val usesFirestore = combinedCode.contains("firebase-firestore", ignoreCase = true) ||
            combinedCode.contains("getFirestore", ignoreCase = true) ||
            combinedCode.contains("firebase.firestore", ignoreCase = true) ||
            combinedCode.contains("onSnapshot(", ignoreCase = true) ||
            combinedCode.contains("getDocs(", ignoreCase = true)

        val usesAuth = combinedCode.contains("firebase-auth", ignoreCase = true) ||
            combinedCode.contains("getAuth", ignoreCase = true) ||
            combinedCode.contains("firebase.auth", ignoreCase = true) ||
            combinedCode.contains("signInAnonymously", ignoreCase = true) ||
            combinedCode.contains("onAuthStateChanged", ignoreCase = true)

        val usesStorage = combinedCode.contains("firebase-storage", ignoreCase = true) ||
            combinedCode.contains("getStorage", ignoreCase = true) ||
            combinedCode.contains("firebase.storage", ignoreCase = true) ||
            combinedCode.contains("getDownloadURL", ignoreCase = true)

        val projectIdMatch = Regex("""projectId\s*:\s*['"]([^'"]+)['"]""").find(combinedCode)
        val dbUrlMatch = Regex("""databaseURL\s*:\s*['"](https?://[^'"]+)['"]""").find(combinedCode)
            ?: Regex("""(https://[a-zA-Z0-9-]+(?:-default-rtdb)?\.(?:firebaseio\.com|[a-z0-9-]+\.firebasedatabase\.app))""").find(combinedCode)

        val paths = mutableSetOf<String>()
        val refRegex = Regex("""(?:\.ref|ref|collection|\.collection)\s*\(\s*(?:[a-zA-Z0-9_$]+\s*,\s*)?['"]([a-zA-Z0-9_/-]+)['"]\s*\)""")
        refRegex.findAll(combinedCode).forEach { m ->
            val p = m.groupValues[1].trim('/')
            if (p.isNotBlank()) paths.add(p)
        }

        val dynamicFns = mutableSetOf<String>()
        val fnCandidates = listOf(
            "playChannel", "playStream", "openChannel", "openStream", "loadChannel",
            "startStream", "playVideo", "playMovie", "playSeries", "watchChannel",
            "selectChannel", "launchPlayer", "renderChannels", "loadChannels", "fetchChannels"
        )
        for (fn in fnCandidates) {
            if (Regex("""\b$fn\b""").containsMatchIn(combinedCode)) {
                dynamicFns.add(fn)
            }
        }

        return FirebaseDetectionSummary(
            usesFirebase = hasFirebaseKeyword,
            usesRealtimeDatabase = usesRealtimeDb,
            usesCloudFirestore = usesFirestore,
            usesFirebaseAuth = usesAuth,
            usesFirebaseStorage = usesStorage,
            projectId = projectIdMatch?.groupValues?.getOrNull(1),
            databaseUrl = dbUrlMatch?.groupValues?.getOrNull(1),
            detectedPathsAndCollections = paths.toList(),
            dynamicChannelFunctions = dynamicFns.toList()
        )
    }

    private fun extractCategoriesFromDomAndJs(htmlCode: String, jsCode: String): List<String> {
        val cats = mutableSetOf<String>()
        val combined = "$htmlCode\n$jsCode"
        val filterCallRegex = Regex("""(?:filterCategory|selectCategory|showCategory|openCategory)\s*\(\s*['"]([^'"]+)['"]\s*\)""")
        filterCallRegex.findAll(combined).forEach { m ->
            cats.add(m.groupValues[1].trim())
        }
        val dataCatRegex = Regex("""data-category\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        dataCatRegex.findAll(combined).forEach { m ->
            cats.add(m.groupValues[1].trim())
        }
        val jsCatFieldRegex = Regex("""category(?:Name)?\s*:\s*['"]([^'"]+)['"]""")
        jsCatFieldRegex.findAll(combined).forEach { m ->
            cats.add(m.groupValues[1].trim())
        }
        return cats.toList()
    }

    private fun extractChannelsFromCode(
        projectId: Long,
        htmlCode: String,
        jsCode: String,
        allStreamUrls: List<String>
    ): List<IptvChannelEntity> {
        val channels = mutableListOf<IptvChannelEntity>()
        val usedUrls = mutableSetOf<String>()
        val combined = "$htmlCode\n$jsCode"

        // Find all img tags for logo matching
        val imgRegex = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["'][^>]*alt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        val altLogos = mutableMapOf<String, String>()
        imgRegex.findAll(htmlCode).forEach { m ->
            val src = m.groupValues[1]
            val alt = m.groupValues[2]
            if (alt.isNotBlank()) altLogos[alt] = src
        }

        // Extract backup attributes by stream URL
        val backupMap = mutableMapOf<String, String>()
        val dataStreamBackupRegex = Regex(
            """data-stream\s*=\s*["']([^"']+)["'][^>]*data-backup\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        dataStreamBackupRegex.findAll(htmlCode).forEach { m ->
            backupMap[m.groupValues[1]] = m.groupValues[2]
        }

        var order = 1

        // Pattern A: Direct playChannel('name', 'url', 'cat') calls
        val playCallRegex = Regex(
            """(?:playChannel|playMovie|playStream|loadChannel|openStream|openChannel)\s*\(\s*['"]([^'"]+)['"]\s*,\s*['"](https?://[^'"]+)['"](?:\s*,\s*['"]([^'"]+)['"])?\s*\)""",
            RegexOption.IGNORE_CASE
        )
        playCallRegex.findAll(combined).forEach { match ->
            val name = match.groupValues[1].trim()
            val url = match.groupValues[2].trim()
            val cat = match.groupValues[3].trim().ifBlank { "بث مباشر" }
            if (url.isNotBlank() && !usedUrls.contains("$name|$url")) {
                usedUrls.add("$name|$url")
                val logo = altLogos[name] ?: "https://picsum.photos/seed/${name.hashCode()}/120/120"
                channels.add(
                    IptvChannelEntity(
                        projectId = projectId,
                        name = name,
                        category = cat,
                        streamUrl = url,
                        streamFormat = detectFormatLabel(url),
                        logoUrl = logo,
                        backupServersCsv = backupMap[url] ?: "",
                        buttonSelectorId = "onclick:${match.value.take(50)}",
                        sortOrder = order++
                    )
                )
            }
        }

        // Pattern B: Structured JS/Firebase objects containing { name/title/channelName: "...", url/streamUrl/stream: "https://..." }
        val jsObjRegex = Regex(
            """\{[^{}]*?(?:name|title|channelName)\s*:\s*['"]([^'"]+)['"][^{}]*?(?:streamUrl|url|stream|link|src)\s*:\s*['"](https?://[^'"]+)['"][^{}]*?\}""",
            RegexOption.IGNORE_CASE
        )
        jsObjRegex.findAll(combined).forEach { match ->
            val block = match.value
            val name = match.groupValues[1].trim()
            val url = match.groupValues[2].trim()
            val catMatch = Regex("""(?:category|categoryName|group)\s*:\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE).find(block)
            val logoMatch = Regex("""(?:logo|channelLogo|icon|image)\s*:\s*['"](https?://[^'"]+)['"]""", RegexOption.IGNORE_CASE).find(block)
            val cat = catMatch?.groupValues?.getOrNull(1)?.trim()?.ifBlank { "قنوات Firebase" } ?: "قنوات Firebase"
            val logo = logoMatch?.groupValues?.getOrNull(1)?.trim() ?: altLogos[name] ?: "https://picsum.photos/seed/${name.hashCode()}/120/120"

            if (url.isNotBlank() && !usedUrls.contains("$name|$url")) {
                usedUrls.add("$name|$url")
                channels.add(
                    IptvChannelEntity(
                        projectId = projectId,
                        name = name,
                        category = cat,
                        streamUrl = url,
                        streamFormat = detectFormatLabel(url),
                        logoUrl = logo,
                        backupServersCsv = backupMap[url] ?: "",
                        buttonSelectorId = "firebase-dynamic-channel",
                        sortOrder = order++
                    )
                )
            }
        }

        // Pattern C: M3U playlist blocks (#EXTINF:-1 tvg-logo="..." group-title="...",Channel Name \n http://...)
        val m3uRegex = Regex(
            """#EXTINF:[^\n]*?(?:tvg-logo="([^"]*)")?[^\n]*?(?:group-title="([^"]*)")?[^\n]*?,\s*([^\n\r]+)\s*[\r\n]+\s*(https?://[^\s\r\n]+)""",
            RegexOption.IGNORE_CASE
        )
        m3uRegex.findAll(combined).forEach { match ->
            val logo = match.groupValues[1].trim()
            val group = match.groupValues[2].trim().ifBlank { "قنوات M3U" }
            val name = match.groupValues[3].trim()
            val url = match.groupValues[4].trim()
            if (url.isNotBlank() && !usedUrls.contains("$name|$url")) {
                usedUrls.add("$name|$url")
                channels.add(
                    IptvChannelEntity(
                        projectId = projectId,
                        name = name,
                        category = group,
                        streamUrl = url,
                        streamFormat = detectFormatLabel(url),
                        logoUrl = logo.ifBlank { "https://picsum.photos/seed/${name.hashCode()}/120/120" },
                        backupServersCsv = "",
                        buttonSelectorId = "m3u-playlist-entry",
                        sortOrder = order++
                    )
                )
            }
        }

        // Pattern D: Any remaining raw stream URLs found in <source>, <video>, <a href>, or JS variables
        allStreamUrls.forEachIndexed { idx, rawUrl ->
            if (channels.none { it.streamUrl == rawUrl }) {
                channels.add(
                    IptvChannelEntity(
                        projectId = projectId,
                        name = "قناة مكتشفة #${idx + 1} (${detectFormatLabel(rawUrl)})",
                        category = "بث مكتشف تلقائياً",
                        streamUrl = rawUrl,
                        streamFormat = detectFormatLabel(rawUrl),
                        logoUrl = "https://picsum.photos/seed/stream${idx + 1}/120/120",
                        backupServersCsv = "",
                        buttonSelectorId = "source-tag-${idx + 1}",
                        sortOrder = order++
                    )
                )
            }
        }

        return channels
    }

    fun detectFormatLabel(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".m3u8") -> "HLS (M3U8)"
            lower.contains(".mpd") -> "DASH (MPD)"
            lower.contains(".mp4") -> "MP4"
            lower.contains(".ts") -> "MPEG-TS"
            lower.contains(".mkv") || lower.contains(".webm") -> "WebM/MKV"
            else -> "HLS (M3U8)"
        }
    }

    private fun inspectCodeSyntaxAndSecurity(
        htmlCode: String,
        cssCode: String,
        jsCode: String
    ): List<CodeIssue> {
        val issues = mutableListOf<CodeIssue>()
        val lines = htmlCode.lines()

        if (!htmlCode.contains("<!DOCTYPE html>", ignoreCase = true)) {
            issues.add(
                CodeIssue(
                    line = 1,
                    severity = "WARNING",
                    message = "وسم <!DOCTYPE html> غير موجود في بداية الملف.",
                    suggestion = "أضف <!DOCTYPE html> في السطر الأول لضمان التوافق القياسي HTML5."
                )
            )
        }

        lines.forEachIndexed { index, line ->
            val lineNum = index + 1
            if (line.contains("<video", ignoreCase = true)) {
                issues.add(
                    CodeIssue(
                        line = lineNum,
                        severity = "INFO",
                        message = "تم اكتشاف مشغل <video> تقليدي في السطر $lineNum.",
                        suggestion = "سيقوم النظام بربطه تلقائياً بمشغل AndroidX Media3 ExoPlayer 1.5.1 الأصلي."
                    )
                )
            }
            if (line.contains("http://", ignoreCase = true)) {
                issues.add(
                    CodeIssue(
                        line = lineNum,
                        severity = "WARNING",
                        message = "رابط غير مشفر (HTTP) في السطر $lineNum.",
                        suggestion = "سيتم تفعيل usesCleartextTraffic في ملف APK لضمان عمل البث."
                    )
                )
            }
            if (line.contains("document.write(", ignoreCase = true)) {
                issues.add(
                    CodeIssue(
                        line = lineNum,
                        severity = "WARNING",
                        message = "استخدام document.write() قد يبطئ تحميل واجهة التطبيق.",
                        suggestion = "استخدم DOM APIs مثل innerHTML أو appendChild."
                    )
                )
            }
        }

        val openDivs = Regex("<div\\b", RegexOption.IGNORE_CASE).findAll(htmlCode).count()
        val closeDivs = Regex("</div\\s*>", RegexOption.IGNORE_CASE).findAll(htmlCode).count()
        if (openDivs != closeDivs) {
            issues.add(
                CodeIssue(
                    line = lines.size,
                    severity = "ERROR",
                    message = "عدم تطابق عدد وسوم <div> المفتوحة ($openDivs) مع المغلقة ($closeDivs).",
                    suggestion = "استخدم زر 'تنسيق وإصلاح الكود' في المحرر لإغلاق الوسوم الناقصة تلقائياً."
                )
            )
        }

        return issues
    }

    /**
     * Injects the non-destructive Smart Back Navigation + Dynamic Firebase Channel Interceptor +
     * Media3 ExoPlayer 1.5.1 JS Bridge into the HTML document while preserving 100% of the
     * original CSS, layout, colors, fonts, and Firebase configuration.
     */
    fun injectMedia3NativeBridgeIntoHtml(
        htmlCode: String,
        cssCode: String,
        jsCode: String,
        channels: List<IptvChannelEntity>
    ): String {
        val bridgeScript = """
<script id="studio-smart-bridge">
// === HTML TO APK STUDIO: Smart Back Navigation + Firebase Live Bridge + Native Media3 ExoPlayer 1.5.1 ===
(function() {
    if (window.__StudioSmartBridgeInitialized) return;
    window.__StudioSmartBridgeInitialized = true;

    // 1. Security Guard: Only allow trusted local app context to invoke Android Native Bridge
    function isTrustedAppOrigin() {
        var proto = window.location.protocol || '';
        var host = window.location.hostname || '';
        return proto === 'file:' || proto === 'about:' || proto === 'data:' ||
               host === 'studio.local' || host === 'localhost' || host === '127.0.0.1' || host === '';
    }

    // 2. Detect stream type (HLS M3U8, DASH MPD, MP4, MPEG-TS)
    function detectStreamType(url) {
        if (!url || typeof url !== 'string') return 'UNKNOWN';
        var lower = url.toLowerCase();
        if (lower.indexOf('.m3u8') !== -1 || lower.indexOf('mpegurl') !== -1) return 'HLS';
        if (lower.indexOf('.mpd') !== -1 || lower.indexOf('dash') !== -1) return 'DASH';
        if (lower.indexOf('.mp4') !== -1) return 'MP4';
        if (lower.indexOf('.ts') !== -1) return 'TS';
        if (lower.indexOf('rtmp://') === 0) return 'RTMP';
        return 'HLS';
    }

    function isLikelyStreamUrl(url) {
        if (!url || typeof url !== 'string') return false;
        var trimmed = url.trim();
        if (trimmed.indexOf('http://') !== 0 && trimmed.indexOf('https://') !== 0 &&
            trimmed.indexOf('rtmp://') !== 0 && trimmed.indexOf('rtsp://') !== 0) {
            return false;
        }
        var lower = trimmed.toLowerCase();
        return lower.indexOf('.m3u8') !== -1 ||
               lower.indexOf('.mpd') !== -1 ||
               lower.indexOf('.mp4') !== -1 ||
               lower.indexOf('.ts') !== -1 ||
               lower.indexOf('.mkv') !== -1 ||
               lower.indexOf('.webm') !== -1 ||
               lower.indexOf('/live/') !== -1 ||
               lower.indexOf('/hls/') !== -1 ||
               lower.indexOf('stream') !== -1;
    }

    // 3. Unified Native Media3 ExoPlayer Dispatcher (Supports full channel metadata & error reporting)
    window.__lastLaunchedChannelMeta = null;

    window.dispatchChannelToNativePlayer = function(meta) {
        if (!isTrustedAppOrigin()) {
            console.warn('[StudioBridge] Blocked untrusted origin call');
            return false;
        }
        var chObj = meta || {};
        var channelId = String(chObj.channelId || chObj.id || chObj.key || ('ch_' + Date.now()));
        var channelName = String(chObj.channelName || chObj.name || chObj.title || document.title || 'قناة بث مباشر').trim();
        var channelLogo = String(chObj.channelLogo || chObj.logo || chObj.icon || chObj.image || '').trim();
        var streamUrl = String(chObj.streamUrl || chObj.url || chObj.stream || chObj.link || chObj.src || '').trim();
        var categoryName = String(chObj.categoryName || chObj.category || chObj.group || 'بث مباشر').trim();
        var streamType = String(chObj.streamType || detectStreamType(streamUrl));
        var backupUrls = String(chObj.backupUrls || chObj.backup || '');

        if (!streamUrl) {
            var emptyMsg = 'تعذر تشغيل القناة (' + channelName + '): رابط البث فارغ أو غير متوفر في قاعدة البيانات.';
            if (window.AndroidNativePlayer && window.AndroidNativePlayer.reportChannelError) {
                window.AndroidNativePlayer.reportChannelError(channelName, emptyMsg);
            } else if (window.AndroidNativePlayer && window.AndroidNativePlayer.showToast) {
                window.AndroidNativePlayer.showToast(emptyMsg);
            } else {
                showNonBlockingErrorBanner(emptyMsg);
            }
            return false;
        }

        if (streamUrl.indexOf('http://') !== 0 && streamUrl.indexOf('https://') !== 0 &&
            streamUrl.indexOf('rtmp://') !== 0 && streamUrl.indexOf('rtsp://') !== 0) {
            var invalidMsg = 'تعذر تشغيل القناة (' + channelName + '): صيغة رابط البث غير صالحة (' + streamUrl + ').';
            if (window.AndroidNativePlayer && window.AndroidNativePlayer.reportChannelError) {
                window.AndroidNativePlayer.reportChannelError(channelName, invalidMsg);
            } else if (window.AndroidNativePlayer && window.AndroidNativePlayer.showToast) {
                window.AndroidNativePlayer.showToast(invalidMsg);
            }
            return false;
        }

        var payload = {
            channelId: channelId,
            channelName: channelName,
            channelLogo: channelLogo,
            streamUrl: streamUrl,
            streamType: streamType,
            categoryName: categoryName,
            backupUrls: backupUrls
        };
        window.__lastLaunchedChannelMeta = payload;

        if (window.AndroidNativePlayer) {
            if (typeof window.AndroidNativePlayer.openNativePlayerJson === 'function') {
                window.AndroidNativePlayer.openNativePlayerJson(JSON.stringify(payload));
                return true;
            }
            if (typeof window.AndroidNativePlayer.openNativeExoPlayer === 'function') {
                window.AndroidNativePlayer.openNativeExoPlayer(channelName, streamUrl, categoryName);
                return true;
            }
        }
        return false;
    };

    window.playInNativeMedia3 = function(title, url, category, logo, channelId) {
        return window.dispatchChannelToNativePlayer({
            channelId: channelId || '',
            channelName: title || 'بث مباشر',
            channelLogo: logo || '',
            streamUrl: url || '',
            streamType: detectStreamType(url),
            categoryName: category || 'IPTV'
        });
    };

    // 4. Smart Multi-Layer Back Navigation System (Android Back Button + Back Gesture + SPA + History API)
    window.__StudioNavStack = [];
    window.__currentActiveCategory = 'الكل';

    function notifyAndroidNavState() {
        var canGoBack = window.__StudioNavStack.length > 0 || (window.history && window.history.length > 1);
        var topLabel = window.__StudioNavStack.length > 0
            ? window.__StudioNavStack[window.__StudioNavStack.length - 1].label
            : 'HOME';
        if (window.AndroidNativePlayer && typeof window.AndroidNativePlayer.onNavigationStateChanged === 'function') {
            try {
                window.AndroidNativePlayer.onNavigationStateChanged(canGoBack, topLabel);
            } catch (e) {}
        }
    }

    function captureSectionVisibilitySnapshot() {
        var nodes = document.querySelectorAll('section, main > div, .page, .view, .screen, .tab-content, [data-page], [data-view]');
        var states = [];
        for (var i = 0; i < nodes.length; i++) {
            var el = nodes[i];
            states.push({
                el: el,
                display: el.style.display,
                className: el.className,
                hidden: el.hidden
            });
        }
        return states;
    }

    function restoreSectionVisibilitySnapshot(states) {
        if (!states) return;
        for (var i = 0; i < states.length; i++) {
            var item = states[i];
            if (item.el) {
                item.el.style.display = item.display;
                item.el.className = item.className;
                item.el.hidden = item.hidden;
            }
        }
    }

    window.pushStudioNavState = function(type, label, customRestoreFn) {
        var savedScrollY = window.scrollY || window.pageYOffset || 0;
        var domSnap = captureSectionVisibilitySnapshot();
        window.__StudioNavStack.push({
            type: type || 'VIEW',
            label: label || 'SUBVIEW',
            scrollY: savedScrollY,
            domSnap: domSnap,
            restoreFn: customRestoreFn
        });
        try {
            window.history.pushState({ studioStep: window.__StudioNavStack.length, label: label }, '');
        } catch (e) {}
        notifyAndroidNavState();
    };

    // Hook History API pushState so SPA routers automatically sync with Android Back Navigation
    var origPushState = window.history && window.history.pushState ? window.history.pushState.bind(window.history) : null;
    if (origPushState) {
        window.history.pushState = function(state, title, url) {
            if (!state || !state.studioStep) {
                window.__StudioNavStack.push({
                    type: 'HISTORY_API',
                    label: url || title || 'SPA_ROUTE',
                    scrollY: window.scrollY || 0,
                    domSnap: captureSectionVisibilitySnapshot(),
                    restoreFn: null
                });
            }
            var res = origPushState(state, title, url);
            notifyAndroidNavState();
            return res;
        };
    }

    // Master Smart Back Handler called by Android's onBackPressed / OnBackInvokedCallback
    window.__handleStudioSmartBack = function() {
        // Priority 1: Exit HTML Fullscreen if active
        if (document.fullscreenElement || document.webkitFullscreenElement) {
            if (document.exitFullscreen) document.exitFullscreen();
            else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
            return 'HANDLED_FULLSCREEN';
        }

        // Priority 2: Close any open Modal, Popup, Drawer, or Search Overlay
        var openModals = document.querySelectorAll(
            '.modal.show, .modal.active, .modal.open, .popup.open, .popup.active, ' +
            '.drawer.open, .drawer.active, .sidebar.open, .sidebar.active, ' +
            '[data-modal-open="true"], .overlay.active, .lightbox.active'
        );
        for (var i = openModals.length - 1; i >= 0; i--) {
            var m = openModals[i];
            var style = window.getComputedStyle(m);
            if (style.display !== 'none' && style.visibility !== 'hidden') {
                m.classList.remove('show', 'active', 'open');
                m.setAttribute('data-modal-open', 'false');
                m.style.display = 'none';
                notifyAndroidNavState();
                return 'HANDLED_MODAL';
            }
        }

        // Priority 3: Pop from our SPA Navigation Stack (Movie/Series details -> Channel List -> Category -> Home)
        if (window.__StudioNavStack.length > 0) {
            var lastState = window.__StudioNavStack.pop();
            if (lastState && typeof lastState.restoreFn === 'function') {
                try {
                    lastState.restoreFn();
                } catch (e) {}
            } else if (lastState && lastState.domSnap) {
                restoreSectionVisibilitySnapshot(lastState.domSnap);
            }
            if (lastState && typeof lastState.scrollY === 'number') {
                setTimeout(function() {
                    window.scrollTo(0, lastState.scrollY);
                }, 20);
            }
            notifyAndroidNavState();
            return 'HANDLED_SPA_STATE:' + (lastState ? lastState.label : '');
        }

        // Priority 4: If a non-default category tab is active, return to 'الكل' (All Categories)
        var activeTab = document.querySelector('.tab.active, .category-btn.active, .cat-item.active');
        var allTabs = document.querySelectorAll('.tab, .category-btn, .cat-item');
        if (activeTab && allTabs.length > 1 && activeTab !== allTabs[0]) {
            allTabs[0].click();
            window.scrollTo(0, 0);
            notifyAndroidNavState();
            return 'HANDLED_CATEGORY_RESET';
        }

        return 'AT_ROOT_HOME';
    };

    // 5. Wrap Global Playback & Navigation Functions (Works for both static & Firebase dynamic scripts)
    var wrappedFunctions = {};

    function extractChannelMetaFromArgs(fnName, args) {
        if (!args || args.length === 0) return null;
        var first = args[0];
        if (first && typeof first === 'object' && !(first instanceof Element)) {
            return {
                channelId: first.id || first.channelId || first.key || '',
                channelName: first.channelName || first.name || first.title || 'قناة مباشرة',
                channelLogo: first.channelLogo || first.logo || first.icon || first.image || '',
                streamUrl: first.streamUrl || first.url || first.stream || first.link || first.src || '',
                streamType: first.streamType || first.type || '',
                categoryName: first.categoryName || first.category || first.group || 'بث مباشر',
                backupUrls: first.backupUrls || first.backup || ''
            };
        }
        if (args.length >= 2 && typeof args[0] === 'string' && typeof args[1] === 'string') {
            if (isLikelyStreamUrl(args[1]) || args[1].indexOf('http') === 0) {
                return {
                    channelName: args[0],
                    streamUrl: args[1],
                    categoryName: typeof args[2] === 'string' ? args[2] : 'بث مباشر',
                    channelLogo: typeof args[3] === 'string' ? args[3] : ''
                };
            }
            if (isLikelyStreamUrl(args[0]) || args[0].indexOf('http') === 0) {
                return {
                    streamUrl: args[0],
                    channelName: args[1],
                    categoryName: typeof args[2] === 'string' ? args[2] : 'بث مباشر'
                };
            }
        }
        if (args.length === 1 && typeof first === 'string' && (isLikelyStreamUrl(first) || first.indexOf('http') === 0)) {
            return {
                channelName: document.title || 'قناة مباشرة',
                streamUrl: first,
                categoryName: 'بث مباشر'
            };
        }
        return null;
    }

    function hookGlobalFunctions() {
        var playFnNames = [
            'playChannel', 'playStream', 'openChannel', 'openStream',
            'loadChannel', 'startStream', 'playVideo', 'playMovie',
            'playSeries', 'watchChannel', 'selectChannel', 'launchPlayer'
        ];
        playFnNames.forEach(function(fnName) {
            if (typeof window[fnName] === 'function' && !window[fnName].__studioWrapped) {
                var originalFn = window[fnName];
                var wrapper = function() {
                    var args = Array.prototype.slice.call(arguments);
                    var meta = extractChannelMetaFromArgs(fnName, args);
                    if (meta && meta.streamUrl) {
                        var handled = window.dispatchChannelToNativePlayer(meta);
                        if (handled) return;
                    } else if (meta && !meta.streamUrl) {
                        window.dispatchChannelToNativePlayer(meta);
                        return;
                    }
                    return originalFn.apply(this, args);
                };
                wrapper.__studioWrapped = true;
                window[fnName] = wrapper;
            }
        });

        var navFnNames = [
            'filterCategory', 'selectCategory', 'showCategory', 'openCategory',
            'showPage', 'openPage', 'showSection', 'navigateTo', 'openMovieDetails', 'openSeriesDetails'
        ];
        navFnNames.forEach(function(fnName) {
            if (typeof window[fnName] === 'function' && !window[fnName].__studioNavWrapped) {
                var originalNavFn = window[fnName];
                var navWrapper = function() {
                    var args = Array.prototype.slice.call(arguments);
                    var argLabel = args.length > 0 ? String(args[0]) : fnName;
                    var prevScrollY = window.scrollY || 0;
                    var prevCat = window.__currentActiveCategory;
                    var prevSnap = captureSectionVisibilitySnapshot();

                    if (fnName.toLowerCase().indexOf('category') !== -1) {
                        if (argLabel !== prevCat) {
                            window.pushStudioNavState('CATEGORY', argLabel, function() {
                                window.__currentActiveCategory = prevCat;
                                originalNavFn.call(window, prevCat);
                                window.scrollTo(0, prevScrollY);
                            });
                            window.__currentActiveCategory = argLabel;
                        }
                    } else {
                        window.pushStudioNavState('SECTION', argLabel, function() {
                            restoreSectionVisibilitySnapshot(prevSnap);
                            window.scrollTo(0, prevScrollY);
                        });
                    }
                    return originalNavFn.apply(this, args);
                };
                navWrapper.__studioNavWrapped = true;
                window[fnName] = navWrapper;
            }
        });
    }

    // 6. Intercept HTML5 <video> playback & dynamic src assignment so video NEVER plays inside WebView
    if (window.HTMLMediaElement && window.HTMLMediaElement.prototype) {
        var origVideoPlay = window.HTMLMediaElement.prototype.play;
        window.HTMLMediaElement.prototype.play = function() {
            var v = this;
            var src = v.currentSrc || v.src || (v.querySelector('source') ? v.querySelector('source').src : '');
            if (src && window.AndroidNativePlayer) {
                try { v.pause(); } catch (e) {}
                var titleEl = document.getElementById('current-channel-title') || document.querySelector('.channel-title, h1, h2');
                var title = (window.__lastClickedCardMeta && window.__lastClickedCardMeta.channelName) ||
                            (titleEl ? titleEl.innerText : document.title) || 'بث مباشر';
                var logo = (window.__lastClickedCardMeta && window.__lastClickedCardMeta.channelLogo) || '';
                var cat = (window.__lastClickedCardMeta && window.__lastClickedCardMeta.categoryName) || 'بث مباشر';
                window.dispatchChannelToNativePlayer({
                    channelName: title,
                    channelLogo: logo,
                    streamUrl: src,
                    categoryName: cat
                });
                return Promise.resolve();
            }
            return origVideoPlay.apply(this, arguments);
        };
    }

    // 7. Document-Level Click Delegation for Dynamically Loaded Firebase Channels & Back Buttons
    document.addEventListener('click', function(e) {
        hookGlobalFunctions();
        var target = e.target;
        if (!target || !(target instanceof Element)) return;

        // Check if user clicked an in-HTML Back button
        var backBtn = target.closest('.back-btn, .btn-back, [data-action="back"], [data-back="true"]');
        if (backBtn) {
            var res = window.__handleStudioSmartBack();
            if (res !== 'AT_ROOT_HOME') {
                e.preventDefault();
                e.stopPropagation();
                return;
            }
        }

        // Check if user clicked a Channel Card (including cards dynamically rendered from Firebase)
        var card = target.closest('[data-stream], [data-url], [data-src], [data-link], [data-video], [data-channel-url], .channel-card, .channel-item, .stream-card');
        if (card) {
            var streamUrl = card.getAttribute('data-stream') ||
                            card.getAttribute('data-url') ||
                            card.getAttribute('data-src') ||
                            card.getAttribute('data-link') ||
                            card.getAttribute('data-video') ||
                            card.getAttribute('data-channel-url') || '';
            var titleEl = card.querySelector('h3, h4, .channel-name, .title, .name, span');
            var imgEl = card.querySelector('img');
            var cat = card.getAttribute('data-category') || card.getAttribute('data-group') || window.__currentActiveCategory || 'بث مباشر';
            var chId = card.getAttribute('data-id') || card.getAttribute('data-channel-id') || '';
            var backup = card.getAttribute('data-backup') || '';
            var title = card.getAttribute('data-name') || card.getAttribute('data-title') || (titleEl ? titleEl.innerText : 'قناة بث مباشر');
            var logo = card.getAttribute('data-logo') || (imgEl ? imgEl.src : '');

            window.__lastClickedCardMeta = {
                channelId: chId,
                channelName: title,
                channelLogo: logo,
                streamUrl: streamUrl,
                categoryName: cat,
                backupUrls: backup
            };

            if (streamUrl && window.AndroidNativePlayer) {
                e.preventDefault();
                e.stopPropagation();
                window.dispatchChannelToNativePlayer(window.__lastClickedCardMeta);
            }
        }
    }, true);

    // 8. Live Internet & Firebase Connectivity Banner (Non-destructive, auto-reconnects)
    var networkBannerEl = null;

    function showNonBlockingErrorBanner(msgText) {
        if (!document.body) return;
        if (!networkBannerEl) {
            networkBannerEl = document.createElement('div');
            networkBannerEl.id = 'studio-network-status-banner';
            networkBannerEl.style.cssText = 'position:fixed;bottom:14px;left:14px;right:14px;z-index:99999;background:#1E1B4B;color:#F8FAFC;border:1px solid #00E5FF;border-radius:12px;padding:12px 16px;display:flex;align-items:center;justify-content:space-between;box-shadow:0 8px 24px rgba(0,0,0,0.65);font-family:sans-serif;font-size:13px;direction:rtl;';
            document.body.appendChild(networkBannerEl);
        }
        networkBannerEl.innerHTML = '<span>' + msgText + '</span>' +
            '<button onclick="window.__retryStudioConnection()" style="background:#00E5FF;color:#000;border:none;border-radius:8px;padding:6px 12px;font-weight:bold;cursor:pointer;margin-right:10px;">إعادة المحاولة</button>';
        networkBannerEl.style.display = 'flex';
    }

    function hideNetworkBanner() {
        if (networkBannerEl) {
            networkBannerEl.style.display = 'none';
        }
    }

    window.__retryStudioConnection = function() {
        if (navigator.onLine) {
            hideNetworkBanner();
            try {
                if (window.firebase && typeof window.firebase.database === 'function') {
                    window.firebase.database().goOnline();
                }
            } catch (e) {}
            if (typeof window.loadChannels === 'function') window.loadChannels();
            else if (typeof window.fetchChannels === 'function') window.fetchChannels();
        } else {
            showNonBlockingErrorBanner('لا يوجد اتصال بالإنترنت حالياً — تحقق من الشبكة ثم اضغط إعادة المحاولة.');
        }
    };

    window.__onAndroidNetworkChange = function(isOnline) {
        if (isOnline) {
            hideNetworkBanner();
            try {
                if (window.firebase && typeof window.firebase.database === 'function') {
                    window.firebase.database().goOnline();
                }
            } catch (e) {}
        } else {
            showNonBlockingErrorBanner('انقطع الاتصال بالإنترنت — سيتم إعادة الاتصال بـ Firebase تلقائياً فور عودة الشبكة.');
        }
    };

    window.addEventListener('offline', function() {
        window.__onAndroidNetworkChange(false);
    });
    window.addEventListener('online', function() {
        window.__onAndroidNetworkChange(true);
    });

    document.addEventListener('DOMContentLoaded', function() {
        hookGlobalFunctions();
        notifyAndroidNavState();
        if (window.MutationObserver && document.body) {
            var observer = new MutationObserver(function() {
                hookGlobalFunctions();
            });
            observer.observe(document.body, { childList: true, subtree: true });
        }
    });
    setTimeout(hookGlobalFunctions, 500);
    setTimeout(hookGlobalFunctions, 1500);
})();
</script>
        """.trimIndent()

        var combined = htmlCode
        if (cssCode.isNotBlank() && !combined.contains(cssCode.take(30)) && !combined.contains("style.css", ignoreCase = true)) {
            val styleBlock = "<style>\n$cssCode\n</style>"
            combined = if (combined.contains("</head>", ignoreCase = true)) {
                combined.replaceFirst(Regex("</head>", RegexOption.IGNORE_CASE), "$styleBlock\n</head>")
            } else {
                "$styleBlock\n$combined"
            }
        }
        if (jsCode.isNotBlank() && !combined.contains(jsCode.take(30)) && !combined.contains("app.js", ignoreCase = true)) {
            val scriptBlock = "<script>\n$jsCode\n</script>"
            combined = if (combined.contains("</body>", ignoreCase = true)) {
                combined.replaceFirst(Regex("</body>", RegexOption.IGNORE_CASE), "$scriptBlock\n$bridgeScript\n</body>")
            } else {
                "$combined\n$scriptBlock\n$bridgeScript"
            }
        } else {
            combined = if (combined.contains("</body>", ignoreCase = true)) {
                combined.replaceFirst(Regex("</body>", RegexOption.IGNORE_CASE), "$bridgeScript\n</body>")
            } else {
                "$combined\n$bridgeScript"
            }
        }
        return combined
    }

    private fun fetchOptionalGeminiInsightOrFallback(
        projectTypeArabic: String,
        channelCount: Int,
        formats: List<String>,
        issuesCount: Int,
        firebaseSummary: FirebaseDetectionSummary
    ): String {
        val defaultSummary = buildString {
            append("تم تحليل بنية الكود بنجاح وتصنيفه كـ [$projectTypeArabic]. ")
            if (firebaseSummary.usesFirebase) {
                val pathsText = if (firebaseSummary.detectedPathsAndCollections.isNotEmpty()) {
                    " للمسارات (${firebaseSummary.detectedPathsAndCollections.joinToString(", ")})"
                } else ""
                append("تم اكتشاف اتصال Firebase سحابي حي$pathsText مع الحفاظ الكامل على إعداداته الأصلية، ")
            }
            if (channelCount > 0 || firebaseSummary.dynamicChannelFunctions.isNotEmpty()) {
                append("وربط القنوات (${if (channelCount > 0) "$channelCount قناة مكتشفة" else "قنوات ديناميكية من Firebase"}) ")
                append("بمشغل AndroidX Media3 ExoPlayer 1.5.1 الأصلي مع تفعيل نظام الرجوع الذكي (Smart Back Navigation). ")
            }
            if (issuesCount > 0) {
                append("تم رصد $issuesCount ملاحظة برمجية مع اقتراحات التحسين التلقائي قبل بناء حزمة APK.")
            } else {
                append("الكود خالٍ من الأخطاء الحرجة وجاهز لعملية البناء الفوري إلى APK.")
            }
        }

        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return defaultSummary
        }

        return try {
            val promptText = "حلل باختصار شديد (في جملتين بالعربية) مشروع أندرويد من نوع $projectTypeArabic يحتوي على $channelCount قناة بث وصيغ ${formats.joinToString()} جاهز للتحويل إلى APK مع Media3 ExoPlayer 1.5.1."
            val payload = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", promptText)))
                ))
            }
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return defaultSummary
                val bodyStr = resp.body?.string() ?: return defaultSummary
                val json = JSONObject(bodyStr)
                val candidates = json.optJSONArray("candidates") ?: return defaultSummary
                val first = candidates.optJSONObject(0) ?: return defaultSummary
                val content = first.optJSONObject("content") ?: return defaultSummary
                val parts = content.optJSONArray("parts") ?: return defaultSummary
                val text = parts.optJSONObject(0)?.optString("text")
                if (!text.isNullOrBlank()) text.trim() else defaultSummary
            }
        } catch (_: Exception) {
            defaultSummary
        }
    }
}
