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
    val upgradedHtmlWithMedia3Bridge: String
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

        // 1. Extract all stream URLs (.m3u8, .mpd, .mp4, .ts, .aac, .mp3, rtmp://, etc.)
        val streamUrlRegex = Regex(
            """(https?://[^\s"'<>]+?\.(?:m3u8|mpd|mp4|ts|mkv|webm|aac|mp3)(?:\?[^\s"'<>]*)?)""",
            RegexOption.IGNORE_CASE
        )
        val streamUrls = streamUrlRegex.findAll(combinedCode)
            .map { it.groupValues[1].trim() }
            .distinct()
            .toList()

        // 2. Detect video formats used
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
        if (combinedCode.contains("application/x-mpegURL", ignoreCase = true)) {
            formats.add("HLS (M3U8)")
        }
        if (combinedCode.contains("application/dash+xml", ignoreCase = true)) {
            formats.add("DASH (MPD)")
        }

        // 3. Extract Channel Cards, M3U entries, onclick triggers, logos, and categories
        val extractedChannels = extractChannelsFromCode(projectId, htmlCode, jsCode, streamUrls)

        val categories = extractedChannels.map { it.category }.filter { it.isNotBlank() }.distinct()
        val logos = extractedChannels.map { it.logoUrl }.filter { it.isNotBlank() }.distinct()

        // 4. Extract onclick buttons & player navigation triggers
        val buttonTriggers = mutableListOf<String>()
        val onclickRegex = Regex("""onclick\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        onclickRegex.findAll(htmlCode).forEach { match ->
            buttonTriggers.add(match.groupValues[1].trim())
        }
        if (htmlCode.contains("<video", ignoreCase = true)) {
            buttonTriggers.add("<video> HTML5 Tag -> Media3 ExoPlayer Auto-Hook")
        }

        // 5. Detect JSON & API endpoints
        val apiRegex = Regex("""(https?://[^\s"'<>]+?(?:\.json|/api/|/v1/|/v2/|playlist)[^\s"'<>]*)""", RegexOption.IGNORE_CASE)
        val jsonEndpoints = apiRegex.findAll(combinedCode)
            .map { it.groupValues[1] }
            .filter { !streamUrls.contains(it) }
            .distinct()
            .toList()

        // 6. Detect Missing Files (linked CSS/JS files not provided inline or in tabs)
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

        // 7. Detect Code Errors & Warnings
        val codeIssues = inspectCodeSyntaxAndSecurity(htmlCode, cssCode, jsCode)

        // 8. Classify into one of the 7 Project Types required by the prompt:
        // 1. مواقع HTML العادية
        // 2. تطبيقات البث التلفزيوني المباشر IPTV
        // 3. تطبيقات مشاهدة الأفلام والمسلسلات
        // 4. تطبيقات الفيديو والصوت
        // 5. مشغلات الوسائط
        // 6. تطبيقات تحتوي على قوائم قنوات وروابط بث
        // 7. تطبيقات تعتمد على ملفات JSON وواجهات API
        val hasM3u8 = formats.contains("HLS (M3U8)") || combinedCode.contains("iptv", ignoreCase = true)
        val hasVodKeywords = combinedCode.contains("فيلم") || combinedCode.contains("أفلام") ||
            combinedCode.contains("مسلسل") || combinedCode.contains("movie", ignoreCase = true) ||
            combinedCode.contains("series", ignoreCase = true) || combinedCode.contains("vod", ignoreCase = true)
        val hasVideoTag = htmlCode.contains("<video", ignoreCase = true) || htmlCode.contains("<audio", ignoreCase = true)

        val (projectTypeCode, projectTypeArabic) = when {
            (hasM3u8 && extractedChannels.size >= 2) || combinedCode.contains("iptv", ignoreCase = true) ->
                2 to "تطبيقات البث التلفزيوني المباشر IPTV"
            hasVodKeywords && (streamUrls.isNotEmpty() || extractedChannels.isNotEmpty()) ->
                3 to "تطبيقات مشاهدة الأفلام والمسلسلات (VOD)"
            extractedChannels.size >= 2 && streamUrls.isNotEmpty() ->
                6 to "تطبيقات تحتوي على قوائم قنوات وروابط بث"
            jsonEndpoints.isNotEmpty() || combinedCode.contains("fetch(", ignoreCase = true) ->
                7 to "تطبيقات تعتمد على ملفات JSON وواجهات API"
            hasVideoTag && streamUrls.size == 1 ->
                5 to "مشغلات الوسائط (Media Players)"
            streamUrls.isNotEmpty() || hasVideoTag ->
                4 to "تطبيقات الفيديو والصوت"
            else ->
                1 to "مواقع HTML العادية"
        }

        val isIptvModeTriggered = projectTypeCode in listOf(2, 3, 4, 5, 6) || streamUrls.isNotEmpty()

        val requiredPlayerType = if (isIptvModeTriggered) {
            "AndroidX Media3 ExoPlayer 1.5.1 (Native HLS/DASH/MP4 Engine)"
        } else {
            "Android WebView Engine + JS Bridge"
        }

        // 9. Build Suggested Modifications
        val suggestions = mutableListOf<String>()
        if (htmlCode.contains("<video", ignoreCase = true)) {
            suggestions.add("تحويل عنصر <video> التقليدي في HTML إلى جسر AndroidNativePlayer لتشغيل البث عبر Media3 ExoPlayer 1.5.1 الأصلي بدون تقطيع.")
        }
        if (formats.contains("HLS (M3U8)")) {
            suggestions.add("تفعيل وحدة media3-exoplayer-hls لدعم البث المباشر المتكيف (Adaptive Bitrate) وإعادة الاتصال التلقائي عند انقطاع الشبكة.")
        }
        if (formats.contains("DASH (MPD)")) {
            suggestions.add("تفعيل وحدة media3-exoplayer-dash لتشغيل تدفقات MPD عالية الدقة 4K/FHD.")
        }
        if (streamUrls.any { it.startsWith("http://") }) {
            suggestions.add("تفعيل usesCleartextTraffic=\"true\" في AndroidManifest.xml لدعم روابط البث المباشر عبر بروتوكول HTTP.")
        }
        if (!htmlCode.contains("viewport", ignoreCase = true)) {
            suggestions.add("إضافة وسم <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"> لضمان توافق التصميم مع جميع شاشات الهواتف والأجهزة اللوحية.")
        }
        suggestions.add("ربط جميع أزرار القنوات (${buttonTriggers.size} زر مكتشف) بمشغل Media3 ExoPlayer الأصلي مع قائمة التبديل السريع بين القنوات والسيرفرات.")

        // 10. Upgrade HTML code automatically with Media3 Native Interceptor script
        val upgradedHtml = injectMedia3NativeBridgeIntoHtml(htmlCode, cssCode, jsCode, extractedChannels)

        // 11. Optional Gemini Cloud AI Enrichment if GEMINI_API_KEY is configured, otherwise deterministic deep engineering summary
        val aiSummary = fetchOptionalGeminiInsightOrFallback(
            projectTypeArabic = projectTypeArabic,
            channelCount = extractedChannels.size,
            formats = formats.toList(),
            issuesCount = codeIssues.size
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
            upgradedHtmlWithMedia3Bridge = upgradedHtml
        )
    }

    private fun extractChannelsFromCode(
        projectId: Long,
        htmlCode: String,
        jsCode: String,
        allStreamUrls: List<String>
    ): List<IptvChannelEntity> {
        val channels = mutableListOf<IptvChannelEntity>()
        val usedUrls = mutableSetOf<String>()

        // Pattern A: Structured HTML cards with data-stream or onclick="playChannel('name', 'url', 'cat')"
        val cardRegex = Regex(
            """<div[^>]*class\s*=\s*["'][^"']*channel-card[^"']*["'][^>]*>([\s\S]*?)</div>\s*</div>|<div[^>]*data-stream\s*=\s*["']([^"']+)["'][^>]*>([\s\S]*?)</div>""",
            RegexOption.IGNORE_CASE
        )

        // Check playChannel / playMovie calls in HTML
        val playCallRegex = Regex(
            """(?:playChannel|playMovie|playStream|loadChannel|openStream)\s*\(\s*['"]([^'"]+)['"]\s*,\s*['"](https?://[^'"]+)['"](?:\s*,\s*['"]([^'"]+)['"])?\s*\)""",
            RegexOption.IGNORE_CASE
        )

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
        playCallRegex.findAll("$htmlCode\n$jsCode").forEach { match ->
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

        // Pattern B: M3U playlist blocks (#EXTINF:-1 tvg-logo="..." group-title="...",Channel Name \n http://...)
        val m3uRegex = Regex(
            """#EXTINF:[^\n]*?(?:tvg-logo="([^"]*)")?[^\n]*?(?:group-title="([^"]*)")?[^\n]*?,\s*([^\n\r]+)\s*[\r\n]+\s*(https?://[^\s\r\n]+)""",
            RegexOption.IGNORE_CASE
        )
        m3uRegex.findAll("$htmlCode\n$jsCode").forEach { match ->
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

        // Pattern C: Any remaining raw stream URLs found in <source>, <video>, <a href>, or JS variables
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

        // Check balanced tags for basic structural integrity
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

    fun injectMedia3NativeBridgeIntoHtml(
        htmlCode: String,
        cssCode: String,
        jsCode: String,
        channels: List<IptvChannelEntity>
    ): String {
        val bridgeScript = """
<script>
// === HTML TO APK STUDIO: Native Media3 ExoPlayer 1.5.1 Interceptor ===
(function() {
    window.playInNativeMedia3 = function(title, url, category) {
        if (window.AndroidNativePlayer && window.AndroidNativePlayer.openNativeExoPlayer) {
            window.AndroidNativePlayer.openNativeExoPlayer(title || 'بث مباشر', url, category || 'IPTV');
            return true;
        }
        return false;
    };

    document.addEventListener('DOMContentLoaded', function() {
        // Intercept all <video> tags and route to Native Media3 ExoPlayer
        var videos = document.querySelectorAll('video');
        videos.forEach(function(v) {
            v.addEventListener('play', function(e) {
                var src = v.currentSrc || v.src || (v.querySelector('source') ? v.querySelector('source').src : '');
                if (src && window.AndroidNativePlayer) {
                    e.preventDefault();
                    v.pause();
                    window.AndroidNativePlayer.openNativeExoPlayer(document.title || 'قناة مباشرة', src, 'بث مباشر');
                }
            });
        });

        // Intercept elements with data-stream attribute
        var streamCards = document.querySelectorAll('[data-stream]');
        streamCards.forEach(function(card) {
            card.addEventListener('click', function(e) {
                var streamUrl = card.getAttribute('data-stream');
                var cat = card.getAttribute('data-category') || 'عام';
                var titleEl = card.querySelector('h3, h4, .title');
                var title = titleEl ? titleEl.innerText : 'قناة بث مباشر';
                if (streamUrl && window.AndroidNativePlayer) {
                    window.AndroidNativePlayer.openNativeExoPlayer(title, streamUrl, cat);
                }
            });
        });
    });
})();
</script>
        """.trimIndent()

        var combined = htmlCode
        if (cssCode.isNotBlank() && !combined.contains(cssCode.take(30))) {
            val styleBlock = "<style>\n$cssCode\n</style>"
            combined = if (combined.contains("</head>", ignoreCase = true)) {
                combined.replaceFirst(Regex("</head>", RegexOption.IGNORE_CASE), "$styleBlock\n</head>")
            } else {
                "$styleBlock\n$combined"
            }
        }
        if (jsCode.isNotBlank() && !combined.contains(jsCode.take(30))) {
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
        issuesCount: Int
    ): String {
        val defaultSummary = buildString {
            append("تم تحليل بنية الكود بنجاح وتصنيفه كـ [$projectTypeArabic]. ")
            if (channelCount > 0) {
                append("تم استخراج $channelCount قناة/مصدر بث بصيغ (${formats.joinToString(", ")}) ")
                append("وتحويل جميع أزرار التشغيل وعناصر الفيديو إلى جسر مشغل AndroidX Media3 ExoPlayer 1.5.1 الأصلي. ")
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
