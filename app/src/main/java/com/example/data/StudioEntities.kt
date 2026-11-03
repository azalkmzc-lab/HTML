package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "studio_projects")
data class StudioProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val packageName: String,
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val developerName: String = "Independent Developer",
    val appDescription: String = "تطبيق أندرويد مستقل للبث المباشر والوسائط",
    val primaryColorHex: String = "#00E5FF",
    val accentColorHex: String = "#7C4DFF",
    val iconPreset: String = "LIVE_TV", // LIVE_TV, MOVIE_HUB, WEB_APP, GAME_PAD, MUSIC_WAVE
    val orientation: String = "AUTO", // AUTO, PORTRAIT, LANDSCAPE
    val permissionsCsv: String = "INTERNET,ACCESS_NETWORK_STATE,WAKE_LOCK",
    // Android SDK Compatibility & Architecture & Signing Configuration
    val autoCompatibility: Boolean = true,
    val minSdk: Int = 21,       // Android 5.0 (API 21) by default for maximum compatibility
    val targetSdk: Int = 35,    // Android 15 (API 35)
    val compileSdk: Int = 35,   // Android 15 (API 35)
    val cpuArchitecture: String = "Universal APK", // Universal APK, arm64-v8a, armeabi-v7a, x86, x86_64
    val buildSigningMode: String = "DEBUG", // DEBUG, RELEASE
    val htmlCode: String,
    val cssCode: String = "",
    val jsCode: String = "",
    val extraFilesJson: String = "[]", // Additional multi-file support
    val detectedType: String = "IPTV Live TV App",
    val isIptvModeEnabled: Boolean = true,
    val isSavedFavorite: Boolean = false,
    val playerThemeColorHex: String = "#00E5FF",
    val showServerSelector: Boolean = true,
    val autoRetryStream: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastBuiltApkPath: String? = null,
    val lastBuiltApkSize: Long = 0L,
    val lastBuiltVersion: String? = null,
    val lastBuiltAt: Long? = null
)

@Entity(tableName = "iptv_channels")
data class IptvChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val category: String = "عام",
    val streamUrl: String,
    val streamFormat: String = "HLS (M3U8)", // HLS (M3U8), DASH (MPD), MP4, TS
    val logoUrl: String = "",
    val backupServersCsv: String = "", // Comma-separated backup stream URLs
    val buttonSelectorId: String = "", // Extracted HTML element ID or onclick binding
    val sortOrder: Int = 0
)

@Entity(tableName = "build_logs")
data class BuildLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val projectName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val minSdk: Int = 21,
    val targetSdk: Int = 35,
    val compileSdk: Int = 35,
    val cpuArchitecture: String = "Universal APK",
    val signingMode: String = "DEBUG",
    val status: String, // SUCCESS, FAILED
    val apkFilePath: String,
    val sourceBundlePath: String,
    val fileSizeBytes: Long,
    val channelsBundled: Int,
    val buildDurationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val buildSummary: String
)

data class ExtraProjectFile(
    val fileName: String,
    val fileType: String, // HTML, CSS, JS, JSON, M3U
    val content: String
)
