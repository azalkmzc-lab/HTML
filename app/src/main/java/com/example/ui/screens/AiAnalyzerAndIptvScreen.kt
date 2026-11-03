package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.analyzer.AnalysisReport
import com.example.data.IptvChannelEntity
import com.example.ui.theme.*

@Composable
fun AiAnalyzerAndIptvScreen(
    mode: String, // "AI_ANALYZER" or "IPTV_CONVERTER"
    analysisReport: AnalysisReport?,
    channels: List<IptvChannelEntity>,
    isAnalyzing: Boolean,
    onReAnalyzeClick: () -> Unit,
    onSyncExtractedChannels: () -> Unit,
    onApplyMedia3BridgeToHtml: () -> Unit,
    onPlayChannelInMedia3: (IptvChannelEntity) -> Unit,
    onSaveChannel: (IptvChannelEntity) -> Unit,
    onDeleteChannel: (Long) -> Unit,
    onProceedToBuildApk: () -> Unit
) {
    var selectedCategoryFilter by remember { mutableStateOf("الكل") }
    var editingChannel by remember { mutableStateOf<IptvChannelEntity?>(null) }
    var showAddChannelDialog by remember { mutableStateOf(false) }

    val allCategories = remember(channels) {
        listOf("الكل") + channels.map { it.category }.filter { it.isNotBlank() }.distinct()
    }

    val filteredChannels = remember(channels, selectedCategoryFilter) {
        if (selectedCategoryFilter == "الكل") channels
        else channels.filter { it.category == selectedCategoryFilter }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackground)
            .testTag(if (mode == "AI_ANALYZER") "ai_analyzer_tab_screen" else "iptv_converter_tab_screen"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header Card: Smart AI Code Classification & IPTV Mode Banner
        item {
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (analysisReport?.isIptvSmartModeTriggered == true) ElectricViolet else NeonCyan
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ElectricViolet.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (mode == "AI_ANALYZER") Icons.Default.Psychology else Icons.Default.LiveTv,
                                    contentDescription = null,
                                    tint = NeonCyan
                                )
                            }
                            Column {
                                Text(
                                    text = if (mode == "AI_ANALYZER") "نظام الذكاء الاصطناعي لتحليل الأكواد" else "وضع IPTV الذكي ومحول مشغل Media3",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = analysisReport?.projectTypeArabic ?: "جاري التحليل...",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NeonCyan
                                )
                            }
                        }

                        IconButton(onClick = onReAnalyzeClick) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = NeonCyan, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "إعادة التحليل", tint = NeonCyan)
                            }
                        }
                    }

                    if (analysisReport != null) {
                        Text(
                            text = analysisReport.aiDeepSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        // Key Metrics Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricMiniCard(
                                title = "القنوات المكتشفة",
                                value = "${channels.size}",
                                color = NeonCyan,
                                modifier = Modifier.weight(1f)
                            )
                            MetricMiniCard(
                                title = "روابط البث",
                                value = "${analysisReport.streamUrls.size}",
                                color = ElectricViolet,
                                modifier = Modifier.weight(1f)
                            )
                            MetricMiniCard(
                                title = "التصنيفات",
                                value = "${allCategories.size - 1}",
                                color = EmeraldGreen,
                                modifier = Modifier.weight(1f)
                            )
                            MetricMiniCard(
                                title = "الملاحظات",
                                value = "${analysisReport.codeErrors.size}",
                                color = AmberWarning,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        if (mode == "AI_ANALYZER" && analysisReport != null) {
            // Detailed AI Analysis Report Cards (Matching all 7 Project Types & Report items)
            item {
                AnalysisDetailSectionCard(
                    title = "تفاصيل التقرير الفني للمشروع",
                    items = listOf(
                        "نوع المشروع المصنف" to "[#${analysisReport.projectTypeCode}] ${analysisReport.projectTypeArabic}",
                        "وضع IPTV الذكي" to if (analysisReport.isIptvSmartModeTriggered) "مفعل تلقائياً ✓" else "غير مطلوب (موقع ويب عادي)",
                        "نوع المشغل المطلوب" to analysisReport.requiredPlayerType,
                        "صيغ الفيديو المكتشفة" to analysisReport.videoFormatsUsed.joinToString(" • "),
                        "أزرار التشغيل المكتشفة" to "${analysisReport.detectedButtonsAndTriggers.size} زر وحدث تشغيل",
                        "واجهات JSON / API" to if (analysisReport.jsonAndApiEndpoints.isEmpty()) "لا توجد روابط JSON خارجية" else analysisReport.jsonAndApiEndpoints.joinToString("\n")
                    )
                )
            }

            // Missing Files & Code Errors Section
            item {
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
                        Text(
                            text = "الأخطاء البرمجية والملفات الناقصة",
                            style = MaterialTheme.typography.titleMedium,
                            color = AmberWarning,
                            fontWeight = FontWeight.Bold
                        )
                        if (analysisReport.missingFiles.isEmpty() && analysisReport.codeErrors.isEmpty()) {
                            Text(
                                text = "لم يتم العثور على أي ملفات ناقصة أو أخطاء برمجية حرجة.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = EmeraldGreen
                            )
                        } else {
                            analysisReport.missingFiles.forEach { missing ->
                                Text("• ملف ناقص: $missing", color = CrimsonError, style = MaterialTheme.typography.bodyMedium)
                            }
                            analysisReport.codeErrors.forEach { err ->
                                Text(
                                    text = "• [سطر ${err.line} - ${err.severity}] ${err.message}\n  💡 الحل: ${err.suggestion}",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // Suggested Modifications & Media3 Auto-Upgrade Action
            item {
                Surface(
                    color = StudioSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "التعديلات والتحسينات المقترحة",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldGreen,
                            fontWeight = FontWeight.Bold
                        )
                        analysisReport.suggestedImprovements.forEach { suggestion ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircleOutline,
                                    contentDescription = null,
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onApplyMedia3BridgeToHtml,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ترقية الكود لـ Media3", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onProceedToBuildApk,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Build, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("الانتقال لبناء APK", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // IPTV Channels Management & Native Media3 Direct Launcher (Shown in IPTV tab and at bottom of Analyzer)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "قنوات البث وروابط Media3 ExoPlayer (${channels.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "اضغط على أي قناة لتشغيلها مباشرة في مشغل Media3 ExoPlayer 1.5.1 الأصلي",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(
                        onClick = onSyncExtractedChannels,
                        modifier = Modifier.testTag("btn_sync_html_channels")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "استخراج من HTML", tint = NeonCyan)
                    }
                    FilledIconButton(
                        onClick = { showAddChannelDialog = true },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = NeonCyan),
                        modifier = Modifier.testTag("btn_add_channel")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة قناة", tint = Color.Black)
                    }
                }
            }
        }

        // Category Filter Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allCategories) { category ->
                    FilterChip(
                        selected = selectedCategoryFilter == category,
                        onClick = { selectedCategoryFilter = category },
                        label = { Text(category) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }
        }

        // Channels List
        items(filteredChannels, key = { "ch_${it.id}_${it.name}" }) { channel ->
            IptvChannelCardItem(
                channel = channel,
                onPlayInNativeMedia3 = { onPlayChannelInMedia3(channel) },
                onEditChannel = { editingChannel = channel },
                onDeleteChannel = { onDeleteChannel(channel.id) }
            )
        }
    }

    // Add / Edit Channel Modal Dialog
    if (showAddChannelDialog || editingChannel != null) {
        ChannelEditorDialog(
            initialChannel = editingChannel,
            onDismiss = {
                showAddChannelDialog = false
                editingChannel = null
            },
            onConfirmSave = { saved ->
                onSaveChannel(saved)
                showAddChannelDialog = false
                editingChannel = null
            }
        )
    }
}

@Composable
private fun MetricMiniCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = StudioSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
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
private fun AnalysisDetailSectionCard(
    title: String,
    items: List<Pair<String, String>>
) {
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = NeonCyan,
                fontWeight = FontWeight.Bold
            )
            items.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.weight(0.38f)
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(0.62f)
                    )
                }
                HorizontalDivider(color = StudioCardBorder.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun IptvChannelCardItem(
    channel: IptvChannelEntity,
    onPlayInNativeMedia3: () -> Unit,
    onEditChannel: () -> Unit,
    onDeleteChannel: () -> Unit
) {
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayInNativeMedia3)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = channel.logoUrl.ifBlank { "https://picsum.photos/seed/${channel.name.hashCode()}/100/100" },
                contentDescription = channel.name,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(StudioSurfaceVariant),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Badge(containerColor = NeonCyan.copy(alpha = 0.2f)) {
                        Text(channel.streamFormat, color = NeonCyan, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
                Text(
                    text = "التصنيف: ${channel.category}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ElectricViolet
                )
                Text(
                    text = channel.streamUrl,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onPlayInNativeMedia3) {
                    Icon(Icons.Default.PlayCircleFilled, contentDescription = "تشغيل في Media3", tint = EmeraldGreen)
                }
                IconButton(onClick = onEditChannel) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل القناة", tint = NeonCyan)
                }
                IconButton(onClick = onDeleteChannel) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف القناة", tint = CrimsonError)
                }
            }
        }
    }
}

@Composable
private fun ChannelEditorDialog(
    initialChannel: IptvChannelEntity?,
    onDismiss: () -> Unit,
    onConfirmSave: (IptvChannelEntity) -> Unit
) {
    var name by remember { mutableStateOf(initialChannel?.name ?: "") }
    var category by remember { mutableStateOf(initialChannel?.category ?: "رياضية") }
    var streamUrl by remember { mutableStateOf(initialChannel?.streamUrl ?: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8") }
    var logoUrl by remember { mutableStateOf(initialChannel?.logoUrl ?: "") }
    var backupServers by remember { mutableStateOf(initialChannel?.backupServersCsv ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface,
        title = {
            Text(
                text = if (initialChannel == null) "إضافة قناة بث جديدة" else "تعديل بيانات القناة",
                style = MaterialTheme.typography.titleLarge,
                color = NeonCyan
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم القناة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("تصنيف القناة (مثال: رياضية، إخبارية، أفلام)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    label = { Text("رابط البث المباشر (M3U8 / MPD / MP4)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = logoUrl,
                    onValueChange = { logoUrl = it },
                    label = { Text("رابط شعار القناة (Logo URL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = backupServers,
                    onValueChange = { backupServers = it },
                    label = { Text("روابط سيرفرات احتياطية (مفصولة بفاصلة)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && streamUrl.isNotBlank()) {
                        val format = when {
                            streamUrl.contains(".m3u8", true) -> "HLS (M3U8)"
                            streamUrl.contains(".mpd", true) -> "DASH (MPD)"
                            streamUrl.contains(".mp4", true) -> "MP4"
                            else -> "HLS (M3U8)"
                        }
                        onConfirmSave(
                            IptvChannelEntity(
                                id = initialChannel?.id ?: 0L,
                                projectId = initialChannel?.projectId ?: 0L,
                                name = name.trim(),
                                category = category.trim().ifBlank { "عام" },
                                streamUrl = streamUrl.trim(),
                                streamFormat = format,
                                logoUrl = logoUrl.trim(),
                                backupServersCsv = backupServers.trim()
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Text("حفظ القناة", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}
