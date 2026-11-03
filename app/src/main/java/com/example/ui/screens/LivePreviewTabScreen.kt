package com.example.ui.screens

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.IptvChannelEntity
import com.example.data.StudioProjectEntity
import com.example.ui.theme.*
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LivePreviewTabScreen(
    project: StudioProjectEntity,
    upgradedHtml: String,
    channels: List<IptvChannelEntity>,
    onTriggerNativeMedia3Player: (title: String, streamUrl: String, category: String) -> Unit,
    onOpenFullMedia3Player: () -> Unit
) {
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var lastInterceptedEvent by remember { mutableStateOf("اضغط على أي قناة داخل المعاينة لتشغيلها عبر Media3 ExoPlayer 1.5.1") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var jsBackStackDepth by remember { mutableIntStateOf(0) }
    var currentJsViewLabel by remember { mutableStateOf("الصفحة الرئيسية") }

    val triggerSmartBackInPreview = {
        val wv = webViewRef
        if (wv != null) {
            wv.evaluateJavascript(
                "(function(){ return (window.__handleStudioSmartBack && window.__handleStudioSmartBack()) ? 'HANDLED' : 'ROOT'; })()"
            ) { result ->
                if (result != null && result.contains("HANDLED")) {
                    lastInterceptedEvent = "تم الرجوع خطوة للخلف داخل التطبيق مع حفظ موضع التمرير"
                } else if (wv.canGoBack()) {
                    wv.goBack()
                    lastInterceptedEvent = "تم الرجوع للصفحة السابقة في سجل WebView"
                }
            }
        }
    }

    // Intercept hardware Back when user is inside a sub-page/category/modal inside the preview WebView
    BackHandler(enabled = jsBackStackDepth > 0 || (webViewRef?.canGoBack() == true)) {
        triggerSmartBackInPreview()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackground)
            .padding(12.dp)
            .testTag("live_preview_tab_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Preview Control Header Bar
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "المعاينة التفاعلية المباشرة (${project.name})",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = lastInterceptedEvent,
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldGreen
                    )
                    if (jsBackStackDepth > 0) {
                        Text(
                            text = "سجل التنقل الذكي: عمق ($jsBackStackDepth) • $currentJsViewLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberWarning
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(
                        onClick = triggerSmartBackInPreview,
                        modifier = Modifier.testTag("preview_smart_back_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع ذكي داخل التطبيق",
                            tint = AmberWarning
                        )
                    }
                    FilledTonalIconButton(onClick = { reloadTrigger++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "إعادة تحميل المعاينة", tint = NeonCyan)
                    }
                    Button(
                        onClick = onOpenFullMedia3Player,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                        modifier = Modifier.testTag("preview_open_native_player_btn")
                    ) {
                        Icon(Icons.Default.PlayCircleFilled, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشغل Media3", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // 2. Quick Native Channel Bar (Direct Media3 test bar)
        if (channels.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(channels, key = { "prev_ch_${it.id}_${it.name}" }) { ch ->
                    AssistChip(
                        onClick = {
                            lastInterceptedEvent = "تم توجيه '${ch.name}' إلى مشغل Media3 ExoPlayer الأصلي"
                            onTriggerNativeMedia3Player(ch.name, ch.streamUrl, ch.category)
                        },
                        label = { Text(ch.name) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.LiveTv,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }

        // 3. Simulated Device Frame Hosting WebView + JavascriptInterface Bridge
        Surface(
            color = Color(0xFF090D16),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, NeonCyan.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
        ) {
            key(reloadTrigger, upgradedHtml) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewRef = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            @Suppress("DEPRECATION")
                            settings.allowFileAccessFromFileURLs = true
                            @Suppress("DEPRECATION")
                            settings.allowUniversalAccessFromFileURLs = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                try {
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                } catch (_: Exception) {
                                }
                            }

                            // Register Native Media3 Bridge supporting JSON payload, full metadata,
                            // legacy 3-arg calls, error diagnostics, and Smart Back stack tracking
                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun openNativePlayerJson(jsonPayload: String) {
                                        post {
                                            try {
                                                val obj = JSONObject(jsonPayload)
                                                val name = obj.optString("channelName", "قناة مباشرة")
                                                val url = obj.optString("streamUrl", "")
                                                val cat = obj.optString("categoryName", "بث مباشر")
                                                val type = obj.optString("streamType", "HLS (M3U8)")
                                                if (url.isNotBlank()) {
                                                    lastInterceptedEvent = "تم استلام قناة ($name - $type) وتشغيلها عبر Media3"
                                                    onTriggerNativeMedia3Player(name, url, cat)
                                                } else {
                                                    lastInterceptedEvent = "تنبيه: رابط البث غير متوفر للقناة ($name)"
                                                }
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }

                                    @JavascriptInterface
                                    fun openNativeExoPlayerFull(
                                        channelId: String,
                                        channelName: String,
                                        channelLogo: String,
                                        streamUrl: String,
                                        streamType: String,
                                        categoryName: String,
                                        backupUrlsCsv: String
                                    ) {
                                        post {
                                            if (streamUrl.isNotBlank()) {
                                                lastInterceptedEvent = "تم تشغيل '$channelName' ($streamType) عبر Media3"
                                                onTriggerNativeMedia3Player(channelName, streamUrl, categoryName)
                                            } else {
                                                lastInterceptedEvent = "تنبيه: رابط البث غير متوفر للقناة ($channelName)"
                                            }
                                        }
                                    }

                                    @JavascriptInterface
                                    fun openNativeExoPlayer(title: String, streamUrl: String, category: String) {
                                        post {
                                            lastInterceptedEvent = "تم اعتراض رابط البث وتشغيله عبر Media3: $title"
                                            onTriggerNativeMedia3Player(title, streamUrl, category)
                                        }
                                    }

                                    @JavascriptInterface
                                    fun reportChannelError(channelName: String, reason: String) {
                                        post {
                                            lastInterceptedEvent = "تنبيه القناة ($channelName): $reason"
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onNavigationStateChanged(stackDepth: Int, currentView: String) {
                                        post {
                                            jsBackStackDepth = stackDepth.coerceAtLeast(0)
                                            currentJsViewLabel = currentView.ifBlank { "القسم الحالي" }
                                        }
                                    }
                                },
                                "AndroidNativePlayer"
                            )

                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    if (url.contains(".m3u8", true) ||
                                        url.contains(".mpd", true) ||
                                        url.contains(".mp4", true) ||
                                        url.contains(".ts", true) ||
                                        url.startsWith("rtmp://", true) ||
                                        url.startsWith("rtsp://", true)
                                    ) {
                                        lastInterceptedEvent = "تم تحويل رابط الفيديو مباشرة إلى Media3 ExoPlayer"
                                        onTriggerNativeMedia3Player("قناة مباشرة", url, "بث مباشر")
                                        return true
                                    }
                                    return false
                                }
                            }

                            loadDataWithBaseURL(
                                "https://studio.local/",
                                upgradedHtml,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("live_preview_webview")
                )
            }
        }
    }
}

