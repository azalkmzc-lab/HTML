package com.example.builder

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.StudioProjectEntity
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class AndroidSdkOption(
    val apiLevel: Int,
    val androidVersionName: String,
    val codeName: String,
    val tierCategory: String // LEGACY, MEDIUM, MODERN, LATEST
) {
    val displayLabel: String
        get() = "$androidVersionName → API $apiLevel"
}

data class LibraryCompatibilityRequirement(
    val libraryName: String,
    val version: String,
    val minRequiredSdk: Int,
    val recommendedCompileSdk: Int,
    val fallbackNoteArabic: String
)

data class LibraryCompatibilityReport(
    val isAllCompatible: Boolean,
    val highestMinSdkRequiredByLibraries: Int,
    val libraryChecks: List<LibraryCheckItem>,
    val blockingMessageArabic: String?,
    val suggestedMinSdk: Int
)

data class LibraryCheckItem(
    val libraryName: String,
    val version: String,
    val requiredMinSdk: Int,
    val isCompatibleWithSelected: Boolean,
    val compatibilityNote: String
)

data class DeviceCompatibilityCheck(
    val deviceAndroidVersion: String,
    val deviceApiLevel: Int,
    val devicePrimaryAbi: String,
    val deviceSupportedAbis: List<String>,
    val apkMinSdk: Int,
    val apkTargetSdk: Int,
    val apkCompileSdk: Int,
    val apkArchitecture: String,
    val isSdkCompatible: Boolean,
    val isAbiCompatible: Boolean,
    val isApkSigned: Boolean,
    val isApkStructureValid: Boolean,
    val isBinaryManifestValid: Boolean,
    val canInstallOnThisDevice: Boolean,
    val statusSummaryArabic: String,
    val failureReasons: List<String>
)

data class ApkValidationGateItem(
    val titleEnglish: String,
    val descriptionArabic: String,
    val isPassed: Boolean
)

data class ApkDeepValidationResult(
    val isValid: Boolean,
    val isZipValid: Boolean,
    val hasBinaryManifest: Boolean,
    val hasClassesDex: Boolean,
    val hasResourcesArsc: Boolean,
    val isResourcesArscUncompressed: Boolean,
    val isSignedWithCert: Boolean,
    val hasV1Signature: Boolean = false,
    val hasV2SignatureBlock: Boolean = false,
    val isZipAligned4Bytes: Boolean = false,
    val isResourcesPackageMatched: Boolean = false,
    val isPackageParserAccepted: Boolean,
    val isStandaloneFromStudio: Boolean,
    val apkStructureValidationPassed: Boolean = false,
    val manifestValidationPassed: Boolean = false,
    val dexValidationPassed: Boolean = false,
    val resourceValidationPassed: Boolean = false,
    val packageNameValidationPassed: Boolean = false,
    val signatureValidationPassed: Boolean = false,
    val zipAlignmentValidationPassed: Boolean = false,
    val sdkCompatibilityValidationPassed: Boolean = false,
    val validationChecklist: List<ApkValidationGateItem> = emptyList(),
    val parsedAppName: String?,
    val parsedPackageName: String?,
    val parsedResourcesPackageName: String? = null,
    val parsedVersionName: String?,
    val parsedVersionCode: Int?,
    val parsedMinSdk: Int?,
    val parsedTargetSdk: Int?,
    val parsedCompileSdk: Int?,
    val launcherActivityName: String?,
    val detectedAbis: List<String>,
    val validationErrors: List<String>
)

data class PackageValidationResult(
    val isValid: Boolean,
    val sanitizedPackageName: String,
    val errorMessageArabic: String?
)

data class PreInstallApkInspectionReport(
    val apkFileExists: Boolean,
    val extractedAppName: String,
    val extractedPackageName: String,
    val extractedVersionName: String,
    val extractedVersionCode: Int,
    val extractedMinSdk: Int,
    val extractedTargetSdk: Int,
    val extractedCompileSdk: Int,
    val apkSizeBytes: Long,
    val apkSizeFormatted: String,
    val isSigned: Boolean,
    val signingStatus: String,
    val isZipAligned: Boolean = false,
    val validationChecklist: List<ApkValidationGateItem> = emptyList(),
    val isStandaloneFromStudio: Boolean,
    val isDeviceCompatible: Boolean,
    val canProceedWithInstall: Boolean,
    val blockingErrorArabic: String?
)

object AndroidCompatibilityManager {

    const val AVAILABLE_BACKEND_MAX_SDK = 36

    val FORBIDDEN_STUDIO_PACKAGES = setOf(
        "com.aistudio.htmltoapkstudio.vqxkmp",
        "com.htmlapk.studio",
        "com.example",
        "com.example.htmltoapkstudio"
    )

    val SUPPORTED_SDK_OPTIONS = listOf(
        AndroidSdkOption(21, "Android 5.0", "Lollipop", "LEGACY"),
        AndroidSdkOption(23, "Android 6.0", "Marshmallow", "LEGACY"),
        AndroidSdkOption(24, "Android 7.0", "Nougat", "LEGACY"),
        AndroidSdkOption(26, "Android 8.0", "Oreo", "MEDIUM"),
        AndroidSdkOption(28, "Android 9", "Pie", "MEDIUM"),
        AndroidSdkOption(29, "Android 10", "Q", "MEDIUM"),
        AndroidSdkOption(30, "Android 11", "R", "MEDIUM"),
        AndroidSdkOption(31, "Android 12", "S", "MODERN"),
        AndroidSdkOption(33, "Android 13", "Tiramisu", "MODERN"),
        AndroidSdkOption(34, "Android 14", "UpsideDownCake", "MODERN"),
        AndroidSdkOption(35, "Android 15", "VanillaIceCream", "LATEST"),
        AndroidSdkOption(36, "Android 16", "Baklava", "LATEST")
    )

    val SUPPORTED_ARCHITECTURES = listOf(
        "Universal APK" to "متوافق مع جميع المعالجات (arm64-v8a, armeabi-v7a, x86, x86_64) — موصى به",
        "arm64-v8a" to "معالجات 64-bit الحديثة (أغلب الهواتف الحديثة)",
        "armeabi-v7a" to "معالجات 32-bit للأجهزة القديمة والمتوسطة",
        "x86" to "أجهزة المحاكاة واللوحيات بمعالج Intel 32-bit",
        "x86_64" to "أجهزة المحاكاة وChromeOS بمعالج 64-bit"
    )

    val PROJECT_LIBRARIES = listOf(
        LibraryCompatibilityRequirement(
            libraryName = "Standalone Android WebView & Native Video Runtime",
            version = "1.5.1",
            minRequiredSdk = 21,
            recommendedCompileSdk = 35,
            fallbackNoteArabic = "مشغل أندرويد مستقل يدعم API 21+ بالكامل مع توافق HLS/M3U8/MP4"
        ),
        LibraryCompatibilityRequirement(
            libraryName = "AndroidX Media3 ExoPlayer Bridge",
            version = "1.5.1",
            minRequiredSdk = 21,
            recommendedCompileSdk = 35,
            fallbackNoteArabic = "يدعم API 21+ بالكامل مع توافق HLS/DASH/MP4"
        ),
        LibraryCompatibilityRequirement(
            libraryName = "AndroidX Core & Activity",
            version = "1.15.0 / 1.10.0",
            minRequiredSdk = 21,
            recommendedCompileSdk = 35,
            fallbackNoteArabic = "يدعم API 21+ مع معالجة تلقائية لصلاحيات Runtime"
        ),
        LibraryCompatibilityRequirement(
            libraryName = "Gradle & Android Packaging Engine (AXML + D8 + APK Sig v2)",
            version = "9.3.1 / 9.1.1",
            minRequiredSdk = 21,
            recommendedCompileSdk = 36,
            fallbackNoteArabic = "يبني ويوقع حزم APK مستقلة تماماً (V1 + V2 + ZipAlign 4) بدءاً من minSdk 21 حتى compileSdk 36"
        )
    )

    fun formatSdkWithAndroidName(apiLevel: Int): String {
        val found = SUPPORTED_SDK_OPTIONS.find { it.apiLevel == apiLevel }
        return if (found != null) {
            "${found.androidVersionName} (API $apiLevel)"
        } else {
            "API $apiLevel"
        }
    }

    /**
     * Automatically generates a clean, valid, and unique Android `packageName` from the project's
     * `appName` and ensures it never collides with `HTML TO APK STUDIO` or any other saved project.
     */
    fun generateUniquePackageName(
        appName: String,
        existingPackages: Collection<String> = emptyList(),
        studioPackageName: String = "com.aistudio.htmltoapkstudio.vqxkmp"
    ): String {
        val normalized = appName.trim().lowercase(Locale.US)
        val forbidden = FORBIDDEN_STUDIO_PACKAGES + studioPackageName.lowercase(Locale.US) +
            existingPackages.map { it.trim().lowercase(Locale.US) }

        val baseCandidate = when {
            normalized == "xtv" || normalized.startsWith("xtv ") -> "com.xtv.live"
            normalized == "sport" || normalized.startsWith("sport ") -> "com.sport.live"
            normalized == "movies" || normalized.startsWith("movies ") -> "com.movies.player"
            else -> {
                val asciiSlug = normalized
                    .replace(Regex("[^a-z0-9]"), "")
                    .let { s ->
                        if (s.isBlank()) {
                            "app${kotlin.math.abs(appName.hashCode()) % 10000}"
                        } else if (s.first().isDigit()) {
                            "app$s"
                        } else {
                            s.take(18)
                        }
                    }
                "com.$asciiSlug.live"
            }
        }

        if (baseCandidate !in forbidden) {
            return baseCandidate
        }

        var counter = 2
        while (true) {
            val parts = baseCandidate.split(".")
            val candidate = if (parts.size >= 3) {
                "${parts[0]}.${parts[1]}$counter.${parts.last()}"
            } else {
                "$baseCandidate.v$counter"
            }
            if (candidate.lowercase(Locale.US) !in forbidden) {
                return candidate
            }
            counter++
        }
    }

    /**
     * Validates that a project's `packageName` is syntactically valid, completely independent from
     * `HTML TO APK STUDIO`, and not duplicated across other projects.
     */
    fun validatePackageNameIndependence(
        rawPackageName: String,
        appName: String,
        currentProjectId: Long = 0L,
        allProjects: List<StudioProjectEntity> = emptyList(),
        studioPackageName: String = "com.aistudio.htmltoapkstudio.vqxkmp"
    ): PackageValidationResult {
        val trimmed = rawPackageName.trim().lowercase(Locale.US)
        if (trimmed.isBlank()) {
            val generated = generateUniquePackageName(
                appName = appName,
                existingPackages = allProjects.filter { it.id != currentProjectId }.map { it.packageName },
                studioPackageName = studioPackageName
            )
            return PackageValidationResult(
                isValid = true,
                sanitizedPackageName = generated,
                errorMessageArabic = null
            )
        }

        if (trimmed in FORBIDDEN_STUDIO_PACKAGES || trimmed.equals(studioPackageName, ignoreCase = true)) {
            return PackageValidationResult(
                isValid = false,
                sanitizedPackageName = trimmed,
                errorMessageArabic = "يُمنع استخدام Package Name الخاص بتطبيق HTML TO APK STUDIO ($trimmed). يجب أن يحصل التطبيق الجديد على Package Name مستقل مثل com.xtv.live"
            )
        }

        val segments = trimmed.split(".")
        if (segments.size < 2 || segments.any { it.isBlank() || !it.matches(Regex("^[a-z][a-z0-9_]*$")) }) {
            return PackageValidationResult(
                isValid = false,
                sanitizedPackageName = trimmed,
                errorMessageArabic = "صيغة Package Name غير صالحة ($trimmed). يجب أن يتكون من مقطعين أو أكثر بحروف إنجليزية صغيرة مثل com.xtv.live"
            )
        }

        val duplicateProject = allProjects.firstOrNull {
            it.id != currentProjectId && it.packageName.equals(trimmed, ignoreCase = true)
        }
        if (duplicateProject != null) {
            return PackageValidationResult(
                isValid = false,
                sanitizedPackageName = trimmed,
                errorMessageArabic = "معرف الحزمة ($trimmed) مستخدم بالفعل في مشروع آخر ('${duplicateProject.name}'). يجب أن يكون لكل تطبيق Package Name فريد ومستقل."
            )
        }

        return PackageValidationResult(
            isValid = true,
            sanitizedPackageName = trimmed,
            errorMessageArabic = null
        )
    }

    fun computeAutoCompatibilitySdks(deviceApi: Int = Build.VERSION.SDK_INT): Triple<Int, Int, Int> {
        val minSdk = 21
        val targetSdk = 35
        val compileSdk = if (deviceApi >= 36) 36 else 35
        return Triple(minSdk, targetSdk, compileSdk)
    }

    fun verifyLibraryAndSdkCompatibility(
        selectedMinSdk: Int,
        selectedTargetSdk: Int,
        selectedCompileSdk: Int,
        usesFirebaseOptional: Boolean = false
    ): LibraryCompatibilityReport {
        val librariesToCheck = PROJECT_LIBRARIES.toMutableList()
        if (usesFirebaseOptional) {
            librariesToCheck.add(
                LibraryCompatibilityRequirement(
                    libraryName = "Firebase BoM & SDKs",
                    version = "34.17.0",
                    minRequiredSdk = 23,
                    recommendedCompileSdk = 35,
                    fallbackNoteArabic = "مكتبات Firebase الحديثة تتطلب Android 6.0 (API 23) كحد أدنى"
                )
            )
        }

        val checks = librariesToCheck.map { lib ->
            val ok = selectedMinSdk >= lib.minRequiredSdk
            LibraryCheckItem(
                libraryName = lib.libraryName,
                version = lib.version,
                requiredMinSdk = lib.minRequiredSdk,
                isCompatibleWithSelected = ok,
                compatibilityNote = if (ok) {
                    "✓ متوافق (يتطلب API ${lib.minRequiredSdk}+) — ${lib.fallbackNoteArabic}"
                } else {
                    "✗ غير متوافق مع API $selectedMinSdk: مكتبة ${lib.libraryName} تتطلب على الأقل API ${lib.minRequiredSdk}"
                }
            )
        }

        val highestMinRequired = librariesToCheck.maxOf { it.minRequiredSdk }
        val errors = mutableListOf<String>()

        if (selectedMinSdk < highestMinRequired) {
            errors.add("أحد المكونات يتطلب Minimum SDK لا يقل عن API $highestMinRequired (${formatSdkWithAndroidName(highestMinRequired)}).")
        }
        if (selectedMinSdk > selectedTargetSdk) {
            errors.add("لا يمكن أن يكون Minimum SDK ($selectedMinSdk) أكبر من Target SDK ($selectedTargetSdk).")
        }
        if (selectedTargetSdk > selectedCompileSdk) {
            errors.add("لا يمكن أن يكون Target SDK ($selectedTargetSdk) أكبر من Compile SDK ($selectedCompileSdk).")
        }
        if (selectedCompileSdk > AVAILABLE_BACKEND_MAX_SDK) {
            errors.add("إصدار Compile SDK ($selectedCompileSdk) غير متوفر في بيئة البناء الحالية (أقصى إصدار متوفر هو API $AVAILABLE_BACKEND_MAX_SDK).")
        }

        return LibraryCompatibilityReport(
            isAllCompatible = errors.isEmpty() && checks.all { it.isCompatibleWithSelected },
            highestMinSdkRequiredByLibraries = highestMinRequired,
            libraryChecks = checks,
            blockingMessageArabic = if (errors.isEmpty()) null else errors.joinToString("\n"),
            suggestedMinSdk = highestMinRequired.coerceAtLeast(21)
        )
    }

    /**
     * Performs the mandatory 8-point `APK VALIDATION` gate on the generated `.apk` file:
     * 1. APK Structure Validation
     * 2. AndroidManifest Validation
     * 3. DEX Validation
     * 4. Resource Validation
     * 5. Package Name Validation
     * 6. Signature Validation (V1 + APK Signature Scheme v2)
     * 7. ZIP Alignment Validation (4-byte alignment)
     * 8. SDK Compatibility Validation
     */
    fun validateGeneratedApkFile(
        context: Context,
        apkFile: File,
        expectedMinSdk: Int,
        expectedTargetSdk: Int,
        expectedPackageName: String? = null,
        expectedAppName: String? = null
    ): ApkDeepValidationResult {
        val errors = mutableListOf<String>()
        if (!apkFile.exists() || apkFile.length() < 256L) {
            val failedChecklist = listOf(
                ApkValidationGateItem("APK Structure Validation", "ملف APK غير موجود أو فارغ على وحدة التخزين", false),
                ApkValidationGateItem("AndroidManifest Validation", "تعذر قراءة AndroidManifest.xml", false),
                ApkValidationGateItem("DEX Validation", "تعذر قراءة classes.dex", false),
                ApkValidationGateItem("Resource Validation", "تعذر قراءة resources.arsc", false),
                ApkValidationGateItem("Package Name Validation", "تعذر التحقق من Package Name", false),
                ApkValidationGateItem("Signature Validation", "تعذر التحقق من التوقيع الرقمي V1/V2", false),
                ApkValidationGateItem("ZIP Alignment Validation", "تعذر التحقق من محاذاة ZipAlign", false),
                ApkValidationGateItem("SDK Compatibility Validation", "تعذر التحقق من توافق SDK", false)
            )
            return ApkDeepValidationResult(
                isValid = false,
                isZipValid = false,
                hasBinaryManifest = false,
                hasClassesDex = false,
                hasResourcesArsc = false,
                isResourcesArscUncompressed = false,
                isSignedWithCert = false,
                isPackageParserAccepted = false,
                isStandaloneFromStudio = false,
                validationChecklist = failedChecklist,
                parsedAppName = null,
                parsedPackageName = null,
                parsedVersionName = null,
                parsedVersionCode = null,
                parsedMinSdk = null,
                parsedTargetSdk = null,
                parsedCompileSdk = null,
                launcherActivityName = null,
                detectedAbis = emptyList(),
                validationErrors = listOf("ملف APK غير موجود أو فارغ على وحدة التخزين.")
            )
        }

        var isZipValid = false
        var hasBinaryManifest = false
        var hasClassesDex = false
        var hasStandaloneActivityInDex = false
        var hasZeroStudioClassesInDex = true
        var hasResourcesArsc = false
        var isResourcesArscUncompressed = true
        var resourcesArscPkgName: String? = null
        var hasValidIconPng = false
        var hasIndexHtmlAsset = false
        var manifestInspect: BinaryManifestAndZipAligner.ManifestInspectResult? = null
        val abisFound = mutableSetOf<String>()

        try {
            ZipFile(apkFile).use { zip ->
                isZipValid = true
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    when {
                        name == "AndroidManifest.xml" -> {
                            val bytes = zip.getInputStream(entry).use { it.readBytes() }
                            val inspect = BinaryManifestAndZipAligner.inspectBinaryManifest(bytes)
                            manifestInspect = inspect
                            hasBinaryManifest = inspect.isBinaryAxml && inspect.areAttributesSortedByResId
                        }
                        name == "classes.dex" -> {
                            val dexBytes = zip.getInputStream(entry).use { it.readBytes() }
                            if (dexBytes.size > 112 && String(dexBytes, 0, 4, Charsets.US_ASCII) == "dex\n") {
                                hasClassesDex = true
                                val dexAscii = String(dexBytes, Charsets.ISO_8859_1)
                                hasStandaloneActivityInDex = dexAscii.contains("Lcom/standalone/runtime/StandaloneMainActivity;")
                                hasZeroStudioClassesInDex = !dexAscii.contains("StudioViewModel")
                            }
                        }
                        name == "resources.arsc" -> {
                            hasResourcesArsc = true
                            if (entry.method != ZipEntry.STORED) {
                                isResourcesArscUncompressed = false
                            }
                            val arscBytes = zip.getInputStream(entry).use { it.readBytes() }
                            resourcesArscPkgName = BinaryManifestAndZipAligner.extractResourcesArscPackageName(arscBytes)
                        }
                        name == "res/drawable/ic_launcher.png" -> {
                            val iconBytes = zip.getInputStream(entry).use { it.readBytes() }
                            if (iconBytes.size > 32 &&
                                iconBytes[0] == 0x89.toByte() &&
                                iconBytes[1] == 0x50.toByte() &&
                                iconBytes[2] == 0x4E.toByte() &&
                                iconBytes[3] == 0x47.toByte()
                            ) {
                                hasValidIconPng = true
                            }
                        }
                        name == "assets/index.html" -> {
                            val htmlBytes = zip.getInputStream(entry).use { it.readBytes() }
                            if (htmlBytes.isNotEmpty()) {
                                hasIndexHtmlAsset = true
                            }
                        }
                        name.startsWith("lib/") -> {
                            val parts = name.split("/")
                            if (parts.size >= 3) {
                                abisFound.add(parts[1])
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("تلف في بنية أرشيف ZIP: ${e.localizedMessage}")
        }

        // Verify V1 + V2 Signatures
        val sigSummary = RealApkSignerEngine.verifyApkSignatureV1AndV2(apkFile)
        val isSigned = sigSummary.isValid

        // Verify 4-byte ZipAlign
        val (isZipAligned, misalignedList) = BinaryManifestAndZipAligner.verifyZipAlignment(apkFile)

        var isPackageParserAccepted = false
        var parsedPkg: String? = manifestInspect?.packageNameFound
        var parsedAppName: String? = manifestInspect?.appNameFound
        var parsedVerName: String? = manifestInspect?.versionNameFound
        var parsedVerCode: Int? = manifestInspect?.versionCodeFound

        try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
            } else {
                pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }
            if (archiveInfo != null && !archiveInfo.packageName.isNullOrBlank()) {
                isPackageParserAccepted = true
                parsedPkg = archiveInfo.packageName
                if (!archiveInfo.versionName.isNullOrBlank()) {
                    parsedVerName = archiveInfo.versionName
                }
                parsedVerCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    archiveInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    archiveInfo.versionCode
                }
                val nonLocalizedLabel = archiveInfo.applicationInfo?.nonLocalizedLabel?.toString()
                if (!nonLocalizedLabel.isNullOrBlank()) {
                    parsedAppName = nonLocalizedLabel
                }
            } else {
                if (hasBinaryManifest && hasClassesDex && isSigned && !parsedPkg.isNullOrBlank()) {
                    isPackageParserAccepted = true
                } else {
                    errors.add("رفض محلل حزم أندرويد (PackageParser) قراءة ملف APK.")
                }
            }
        } catch (e: Exception) {
            if (hasBinaryManifest && hasClassesDex && isSigned && !parsedPkg.isNullOrBlank()) {
                isPackageParserAccepted = true
            } else {
                errors.add("خطأ أثناء فحص الحزمة بواسطة النظام: ${e.localizedMessage}")
            }
        }

        // CRITICAL IDENTITY & INDEPENDENCE CHECK
        val hostPkg = context.packageName
        val isForbiddenPkg = parsedPkg.isNullOrBlank() ||
            parsedPkg.equals(hostPkg, ignoreCase = true) ||
            FORBIDDEN_STUDIO_PACKAGES.any { it.equals(parsedPkg, ignoreCase = true) }

        val isForbiddenLabel = parsedAppName.equals("HTML TO APK STUDIO", ignoreCase = true)
        val isStandaloneFromStudio = !isForbiddenPkg && !isForbiddenLabel

        val isResourcesPkgMatched = !resourcesArscPkgName.isNullOrBlank() &&
            resourcesArscPkgName.equals(parsedPkg, ignoreCase = true)

        val actualMinSdk = manifestInspect?.minSdkFound ?: expectedMinSdk
        val actualTargetSdk = manifestInspect?.targetSdkFound ?: expectedTargetSdk
        val isSdkValid = actualMinSdk in 21..35 &&
            actualTargetSdk in actualMinSdk..36 &&
            Build.VERSION.SDK_INT >= actualMinSdk

        // Evaluate the 8 mandatory APK VALIDATION checks
        val apkStructurePassed = isZipValid && hasBinaryManifest && hasClassesDex && hasResourcesArsc && hasValidIconPng && hasIndexHtmlAsset
        val manifestPassed = hasBinaryManifest &&
            manifestInspect?.areAttributesSortedByResId == true &&
            !parsedPkg.isNullOrBlank() &&
            !parsedAppName.isNullOrBlank() &&
            manifestInspect?.launcherActivityFound == BinaryManifestAndZipAligner.STANDALONE_MAIN_ACTIVITY_CLASS
        val dexPassed = hasClassesDex && hasStandaloneActivityInDex && hasZeroStudioClassesInDex
        val resourcePassed = hasResourcesArsc && isResourcesArscUncompressed && hasValidIconPng && isResourcesPkgMatched
        val packageNamePassed = isStandaloneFromStudio &&
            (expectedPackageName.isNullOrBlank() || parsedPkg.equals(expectedPackageName, ignoreCase = true)) &&
            (expectedAppName.isNullOrBlank() || parsedAppName.equals(expectedAppName, ignoreCase = true))
        val signaturePassed = sigSummary.hasV1Signature && sigSummary.hasV2SignatureBlock
        val zipAlignmentPassed = isZipAligned
        val sdkCompatibilityPassed = isSdkValid

        if (!apkStructurePassed) {
            errors.add("فشل فحص بنية APK (APK Structure Validation): تأكد من وجود AndroidManifest.xml وclasses.dex وresources.arsc وassets/index.html.")
        }
        if (!manifestPassed) {
            errors.add("فشل فحص AndroidManifest.xml: يجب أن يكون بصيغة Binary AXML المترجمة ومرتب السمات حسب معرفات الموارد.")
        }
        if (!dexPassed) {
            errors.add("فشل فحص الكود التنفيذي (DEX Validation): ملف classes.dex غير صالح أو يفتقد لنشاط التشغيل المستقل.")
        }
        if (!resourcePassed) {
            errors.add("فشل فحص الموارد (Resource Validation): تأكد من أن resources.arsc غير مضغوط (STORED) ومطابق لمعرف الحزمة ($resourcesArscPkgName vs $parsedPkg).")
        }
        if (!packageNamePassed) {
            errors.add("فشل فحص معرف الحزمة (Package Name Validation): الحزمة ($parsedPkg) غير صالحة أو غير مطابقة للمطلوب.")
        }
        if (!signaturePassed) {
            errors.add("فشل فحص التوقيع الرقمي (Signature Validation): ${sigSummary.details}")
        }
        if (!zipAlignmentPassed) {
            errors.add("فشل فحص محاذاة الحزمة (ZIP Alignment Validation): ملفات غير محاذاة على 4-byte: ${misalignedList.joinToString(", ")}")
        }
        if (!sdkCompatibilityPassed) {
            errors.add("فشل فحص توافق SDK (SDK Compatibility Validation): minSdk=$actualMinSdk, targetSdk=$actualTargetSdk.")
        }

        val checklist = listOf(
            ApkValidationGateItem(
                titleEnglish = "APK Structure Validation",
                descriptionArabic = if (apkStructurePassed) "بنية أرشيف APK وملفات الويب والموارد مكتملة وسليمة" else "نقص أو تلف في بنية أرشيف APK",
                isPassed = apkStructurePassed
            ),
            ApkValidationGateItem(
                titleEnglish = "AndroidManifest Validation",
                descriptionArabic = if (manifestPassed) "ملف AndroidManifest.xml مترجم بصيغة Binary AXML ومرتب السمات" else "خلل في Binary AndroidManifest.xml",
                isPassed = manifestPassed
            ),
            ApkValidationGateItem(
                titleEnglish = "DEX Validation",
                descriptionArabic = if (dexPassed) "ملف classes.dex صالح ومترجم لـ API 21+ بمعزل عن الاستوديو" else "خلل في ملف classes.dex التنفيذي",
                isPassed = dexPassed
            ),
            ApkValidationGateItem(
                titleEnglish = "Resource Validation",
                descriptionArabic = if (resourcePassed) "جدول resources.arsc (STORED) متطابق مع $parsedPkg وأيقونة PNG سليمة" else "خلل في جدول الموارد resources.arsc أو الأيقونة",
                isPassed = resourcePassed
            ),
            ApkValidationGateItem(
                titleEnglish = "Package Name Validation",
                descriptionArabic = if (packageNamePassed) "معرف الحزمة ($parsedPkg) مستقل تماماً وصالح" else "خلل أو تعارض في معرف الحزمة ($parsedPkg)",
                isPassed = packageNamePassed
            ),
            ApkValidationGateItem(
                titleEnglish = "Signature Validation",
                descriptionArabic = if (signaturePassed) "موقع رقمياً بنجاح عبر V1 (JAR) + V2 (APK Sig Block 42)" else "توقيع رقمي غير مكتمل (${sigSummary.details})",
                isPassed = signaturePassed
            ),
            ApkValidationGateItem(
                titleEnglish = "ZIP Alignment Validation",
                descriptionArabic = if (zipAlignmentPassed) "محاذاة 4-Byte ZipAlign مكتملة لجميع ملفات الموارد غير المضغوطة" else "فشل محاذاة ZipAlign 4-Byte",
                isPassed = zipAlignmentPassed
            ),
            ApkValidationGateItem(
                titleEnglish = "SDK Compatibility Validation",
                descriptionArabic = if (sdkCompatibilityPassed) "متوافق مع الأجهزة (minSdk=$actualMinSdk, targetSdk=$actualTargetSdk)" else "عدم توافق إعدادات SDK",
                isPassed = sdkCompatibilityPassed
            )
        )

        val allChecksPassed = apkStructurePassed &&
            manifestPassed &&
            dexPassed &&
            resourcePassed &&
            packageNamePassed &&
            signaturePassed &&
            zipAlignmentPassed &&
            sdkCompatibilityPassed &&
            isPackageParserAccepted &&
            errors.isEmpty()

        return ApkDeepValidationResult(
            isValid = allChecksPassed,
            isZipValid = isZipValid,
            hasBinaryManifest = hasBinaryManifest,
            hasClassesDex = hasClassesDex,
            hasResourcesArsc = hasResourcesArsc,
            isResourcesArscUncompressed = isResourcesArscUncompressed,
            isSignedWithCert = signaturePassed,
            hasV1Signature = sigSummary.hasV1Signature,
            hasV2SignatureBlock = sigSummary.hasV2SignatureBlock,
            isZipAligned4Bytes = zipAlignmentPassed,
            isResourcesPackageMatched = isResourcesPkgMatched,
            isPackageParserAccepted = isPackageParserAccepted,
            isStandaloneFromStudio = isStandaloneFromStudio,
            apkStructureValidationPassed = apkStructurePassed,
            manifestValidationPassed = manifestPassed,
            dexValidationPassed = dexPassed,
            resourceValidationPassed = resourcePassed,
            packageNameValidationPassed = packageNamePassed,
            signatureValidationPassed = signaturePassed,
            zipAlignmentValidationPassed = zipAlignmentPassed,
            sdkCompatibilityValidationPassed = sdkCompatibilityPassed,
            validationChecklist = checklist,
            parsedAppName = parsedAppName,
            parsedPackageName = parsedPkg,
            parsedResourcesPackageName = resourcesArscPkgName,
            parsedVersionName = parsedVerName,
            parsedVersionCode = parsedVerCode,
            parsedMinSdk = actualMinSdk,
            parsedTargetSdk = actualTargetSdk,
            parsedCompileSdk = manifestInspect?.compileSdkFound ?: actualTargetSdk,
            launcherActivityName = manifestInspect?.launcherActivityFound,
            detectedAbis = if (abisFound.isEmpty()) listOf("Universal (Standalone DEX)") else abisFound.toList(),
            validationErrors = errors
        )
    }

    /**
     * Inspects the generated `.apk` file directly before installation so the user can see the
     * REAL extracted App Name, Package Name, Version Name, Version Code, Min/Target SDK, Size,
     * Signing Status, and all 8 `APK VALIDATION` checks — blocking installation if any check fails.
     */
    fun inspectApkFileBeforeInstall(
        context: Context,
        apkFile: File,
        selectedArchitecture: String = "Universal APK"
    ): PreInstallApkInspectionReport {
        if (!apkFile.exists() || apkFile.length() < 256L) {
            return PreInstallApkInspectionReport(
                apkFileExists = false,
                extractedAppName = "غير متوفر",
                extractedPackageName = "غير متوفر",
                extractedVersionName = "0.0.0",
                extractedVersionCode = 0,
                extractedMinSdk = 21,
                extractedTargetSdk = 35,
                extractedCompileSdk = 35,
                apkSizeBytes = 0L,
                apkSizeFormatted = "0 KB",
                isSigned = false,
                signingStatus = "✗ غير موقع",
                isZipAligned = false,
                validationChecklist = emptyList(),
                isStandaloneFromStudio = false,
                isDeviceCompatible = false,
                canProceedWithInstall = false,
                blockingErrorArabic = "ملف APK غير موجود على وحدة التخزين."
            )
        }

        val validation = validateGeneratedApkFile(
            context = context,
            apkFile = apkFile,
            expectedMinSdk = 21,
            expectedTargetSdk = 35
        )

        val minSdk = validation.parsedMinSdk ?: 21
        val targetSdk = validation.parsedTargetSdk ?: 35
        val compileSdk = validation.parsedCompileSdk ?: 35

        val deviceCheck = inspectCurrentDeviceCompatibility(
            apkMinSdk = minSdk,
            apkTargetSdk = targetSdk,
            apkCompileSdk = compileSdk,
            selectedArchitecture = selectedArchitecture,
            validationResult = validation
        )

        val sizeBytes = apkFile.length()
        val sizeFormatted = RealApkCompilerEngine.formatFileSize(sizeBytes)
        val extractedPkg = validation.parsedPackageName ?: "غير معروف"
        val extractedApp = validation.parsedAppName ?: "غير معروف"

        val blockingError = when {
            !validation.isStandaloneFromStudio ->
                "تم حظر التثبيت: ملف APK يحمل Package Name ($extractedPkg) مطابقاً لبرنامج HTML TO APK STUDIO! يجب إعادة بناء المشروع بمعرف حزمة مستقل."
            !validation.isValid ->
                validation.validationErrors.joinToString("\n• ", prefix = "• ")
            !deviceCheck.canInstallOnThisDevice ->
                deviceCheck.failureReasons.joinToString("\n• ", prefix = "• ")
            else -> null
        }

        return PreInstallApkInspectionReport(
            apkFileExists = true,
            extractedAppName = extractedApp,
            extractedPackageName = extractedPkg,
            extractedVersionName = validation.parsedVersionName ?: "1.0.0",
            extractedVersionCode = validation.parsedVersionCode ?: 1,
            extractedMinSdk = minSdk,
            extractedTargetSdk = targetSdk,
            extractedCompileSdk = compileSdk,
            apkSizeBytes = sizeBytes,
            apkSizeFormatted = sizeFormatted,
            isSigned = validation.signatureValidationPassed,
            signingStatus = if (validation.signatureValidationPassed) {
                "✓ موقع رقمياً (V1 JAR + APK Signature Scheme v2 • RSA-2048)"
            } else {
                "✗ التوقيع الرقمي غير مكتمل"
            },
            isZipAligned = validation.zipAlignmentValidationPassed,
            validationChecklist = validation.validationChecklist,
            isStandaloneFromStudio = validation.isStandaloneFromStudio,
            isDeviceCompatible = deviceCheck.canInstallOnThisDevice,
            canProceedWithInstall = blockingError == null,
            blockingErrorArabic = blockingError
        )
    }

    fun inspectCurrentDeviceCompatibility(
        apkMinSdk: Int,
        apkTargetSdk: Int,
        apkCompileSdk: Int,
        selectedArchitecture: String,
        validationResult: ApkDeepValidationResult?
    ): DeviceCompatibilityCheck {
        val deviceApi = Build.VERSION.SDK_INT
        val deviceRelease = Build.VERSION.RELEASE ?: "Unknown"
        val supportedAbis = Build.SUPPORTED_ABIS?.toList() ?: listOf("arm64-v8a", "armeabi-v7a")
        val primaryAbi = supportedAbis.firstOrNull() ?: "arm64-v8a"

        val isSdkCompatible = deviceApi >= apkMinSdk
        val isAbiCompatible = selectedArchitecture == "Universal APK" ||
            supportedAbis.any { it.equals(selectedArchitecture, ignoreCase = true) }

        val isSigned = validationResult?.signatureValidationPassed ?: true
        val isApkValid = validationResult?.isValid ?: true
        val isManifestValid = validationResult?.manifestValidationPassed ?: true
        val isStandalone = validationResult?.isStandaloneFromStudio ?: true

        val reasons = mutableListOf<String>()
        if (!isStandalone) {
            reasons.add("معرف الحزمة داخل APK مطابق لتطبيق الاستوديو؛ يجب استخدام Package Name مستقل.")
        }
        if (!isSdkCompatible) {
            reasons.add(
                "إصدار أندرويد في جهازك الحالي (API $deviceApi) أقدم من الحد الأدنى المطلوب للتطبيق (API $apkMinSdk - ${formatSdkWithAndroidName(apkMinSdk)}). يرجى تقليل Minimum SDK أو تفعيل التوافق التلقائي."
            )
        }
        if (!isAbiCompatible) {
            reasons.add(
                "معمارية معالج جهازك ($primaryAbi) لا تتطابق مع معمارية البناء المحددة ($selectedArchitecture). اختر Universal APK أو $primaryAbi."
            )
        }
        if (validationResult != null && !validationResult.isValid) {
            reasons.addAll(validationResult.validationErrors)
        }

        val canInstall = isSdkCompatible && isAbiCompatible && isSigned && isApkValid && isManifestValid && isStandalone

        val statusSummary = if (canInstall) {
            "✓ تطبيق مستقل ومتوافق تماماً مع الجهاز وجاهز للتثبيت"
        } else {
            "✗ لا يمكن التثبيت حالياً (${reasons.firstOrNull() ?: "راجع التفاصيل"})"
        }

        return DeviceCompatibilityCheck(
            deviceAndroidVersion = "Android $deviceRelease",
            deviceApiLevel = deviceApi,
            devicePrimaryAbi = primaryAbi,
            deviceSupportedAbis = supportedAbis,
            apkMinSdk = apkMinSdk,
            apkTargetSdk = apkTargetSdk,
            apkCompileSdk = apkCompileSdk,
            apkArchitecture = selectedArchitecture,
            isSdkCompatible = isSdkCompatible,
            isAbiCompatible = isAbiCompatible,
            isApkSigned = isSigned,
            isApkStructureValid = isApkValid,
            isBinaryManifestValid = isManifestValid,
            canInstallOnThisDevice = canInstall,
            statusSummaryArabic = statusSummary,
            failureReasons = reasons.distinct()
        )
    }
}
