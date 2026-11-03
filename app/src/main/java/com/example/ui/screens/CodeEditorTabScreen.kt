package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.analyzer.CodeIssue
import com.example.data.ExtraProjectFile
import com.example.data.StudioProjectEntity
import com.example.ui.theme.*

@Composable
fun CodeEditorTabScreen(
    project: StudioProjectEntity,
    extraFiles: List<ExtraProjectFile>,
    codeIssues: List<CodeIssue>,
    autoSaveEnabled: Boolean,
    onCodeChanged: (html: String?, css: String?, js: String?) -> Unit,
    onFormatCode: () -> Unit,
    onImportFileClick: (String) -> Unit,
    onAnalyzeClick: () -> Unit,
    onPreviewClick: () -> Unit
) {
    var selectedFileTab by remember { mutableStateOf("index.html") }
    val clipboardManager = LocalClipboardManager.current

    val currentCodeText = when (selectedFileTab) {
        "index.html" -> project.htmlCode
        "style.css" -> project.cssCode
        "app.js" -> project.jsCode
        else -> extraFiles.find { it.fileName == selectedFileTab }?.content ?: ""
    }

    val lineCount = remember(currentCodeText) {
        currentCodeText.lines().size.coerceAtLeast(1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackground)
            .padding(12.dp)
            .testTag("code_editor_tab_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Multi-File Tab Bar + Import Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                val baseTabs = listOf(
                    "index.html" to "HTML5",
                    "style.css" to "CSS3",
                    "app.js" to "JavaScript"
                )
                items(baseTabs) { (fileName, badge) ->
                    val isSelected = selectedFileTab == fileName
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFileTab = fileName },
                        label = { Text("$fileName ($badge)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan,
                            selectedLabelColor = StudioBackground
                        )
                    )
                }
                items(extraFiles) { extra ->
                    FilterChip(
                        selected = selectedFileTab == extra.fileName,
                        onClick = { selectedFileTab = extra.fileName },
                        label = { Text(extra.fileName) }
                    )
                }
            }

            if (autoSaveEnabled) {
                Badge(
                    containerColor = EmeraldGreen.copy(alpha = 0.2f),
                    contentColor = EmeraldGreen,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("حفظ تلقائي مفعل ✓", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }

        // 2. Editor Action Toolbar (Paste, Import HTML/CSS/JS, Format, Analyze, Preview)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = {
                    val clip = clipboardManager.getText()?.text
                    if (!clip.isNullOrBlank()) {
                        when (selectedFileTab) {
                            "index.html" -> onCodeChanged(clip, null, null)
                            "style.css" -> onCodeChanged(null, clip, null)
                            "app.js" -> onCodeChanged(null, null, clip)
                        }
                    }
                },
                label = { Text("لصق الكود") },
                leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("editor_paste_btn")
            )

            AssistChip(
                onClick = {
                    val type = when (selectedFileTab) {
                        "style.css" -> "CSS"
                        "app.js" -> "JS"
                        else -> "HTML"
                    }
                    onImportFileClick(type)
                },
                label = { Text("استيراد ملف ($selectedFileTab)") },
                leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("editor_import_file_btn")
            )

            AssistChip(
                onClick = onFormatCode,
                label = { Text("تنسيق وإصلاح الكود") },
                leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = ElectricViolet, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("editor_format_btn")
            )

            AssistChip(
                onClick = onAnalyzeClick,
                label = { Text("تحليل بالذكاء الاصطناعي") },
                leadingIcon = { Icon(Icons.Default.Psychology, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("editor_analyze_btn")
            )

            AssistChip(
                onClick = onPreviewClick,
                label = { Text("معاينة قبل التحويل") },
                leadingIcon = { Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("editor_preview_btn")
            )
        }

        // 3. Syntax-Highlighted Code Editor Surface with Line Numbers
        Surface(
            color = Color(0xFF070B12),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioCardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val verticalScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScroll)
                    .padding(10.dp)
            ) {
                // Line Numbers Gutter
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .width(38.dp)
                        .padding(end = 8.dp)
                ) {
                    for (lineNum in 1..lineCount) {
                        Text(
                            text = lineNum.toString(),
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = TextMuted
                            )
                        )
                    }
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height((lineCount * 20).dp.coerceAtLeast(300.dp))
                        .background(StudioCardBorder)
                )

                // Code Input Field with Live Syntax Highlighting
                BasicTextField(
                    value = currentCodeText,
                    onValueChange = { updatedText ->
                        when (selectedFileTab) {
                            "index.html" -> onCodeChanged(updatedText, null, null)
                            "style.css" -> onCodeChanged(null, updatedText, null)
                            "app.js" -> onCodeChanged(null, null, updatedText)
                        }
                    },
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = TextPrimary
                    ),
                    cursorBrush = SolidColor(NeonCyan),
                    visualTransformation = remember { CodeSyntaxHighlightTransformation() },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                        .defaultMinSize(minHeight = 320.dp)
                        .testTag("code_editor_text_field")
                )
            }
        }

        // 4. Real-Time Error & Diagnostic Inspector Footer
        if (codeIssues.isNotEmpty()) {
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(codeIssues) { issue ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = when (issue.severity) {
                                    "ERROR" -> Icons.Default.Error
                                    "WARNING" -> Icons.Default.Warning
                                    else -> Icons.Default.Info
                                },
                                contentDescription = null,
                                tint = when (issue.severity) {
                                    "ERROR" -> CrimsonError
                                    "WARNING" -> AmberWarning
                                    else -> NeonCyan
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "سطر ${issue.line}: ${issue.message}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Highlights HTML tags, attributes, strings, stream URLs (.m3u8/.mpd/.mp4), and JS keywords.
 */
class CodeSyntaxHighlightTransformation : VisualTransformation {
    private val tagRegex = Regex("</?[a-zA-Z0-9\\-]+|/?\\s*>")
    private val attrRegex = Regex("\\b(class|id|src|href|onclick|style|data-stream|data-category|data-backup|type|poster|controls)\\b")
    private val stringRegex = Regex("\"[^\"]*\"|'[^']*'")
    private val urlRegex = Regex("https?://[^\\s\"'<>]+")
    private val jsKeywordRegex = Regex("\\b(function|var|let|const|if|else|return|window|document|forEach|querySelector|addEventListener)\\b")

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val annotated = buildAnnotatedString {
            append(raw)
            tagRegex.findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = SyntaxTag, fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1)
            }
            attrRegex.findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = SyntaxAttribute), m.range.first, m.range.last + 1)
            }
            jsKeywordRegex.findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1)
            }
            stringRegex.findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = SyntaxString), m.range.first, m.range.last + 1)
            }
            urlRegex.findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = SyntaxUrl, fontWeight = FontWeight.Medium), m.range.first, m.range.last + 1)
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}
