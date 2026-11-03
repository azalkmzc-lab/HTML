package com.example.builder

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.StudioProjectEntity
import java.io.File
import java.util.Locale
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

data class ApkDeepValidationResult(
    val isValid: Boolean,
    val isZipValid: Boolean,
    val hasBinaryManifest: Boolean,
    val hasClassesDex: Boolean,
    val hasResourcesArsc: Boolean,
    val isResourcesArscUncompressed: Boolean,
    val isSignedWithCert: Boolean,
    val isPackageParserAccepted: Boolean,
    val isStandaloneFromStudio: Boolean,
    val parsedAppName: String?,
    val parsedPackageName: String?,
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
            libraryName = "Gradle & Android Packaging Engine (AXML + D8)",
            version = "9.3.1 / 9.1.1",
            minRequiredSdk = 21,
            recommendedCompileSdk = 36,
            fallbackNoteArabic = "يبني حزم APK مستقلة تماماً بدءاً من minSdk 21 حتى compileSdk 36"
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
     * Performs a deep binary and PackageManager validation on the generated `.apk` file,
     * extracting the real App Name, Package Name, Version Name, Version Code, Min/Target SDKs,
     * and verifying 100% separation from `HTML TO APK STUDIO`.
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
        var hasResourcesArsc = false
        var isResourcesArscUncompressed = true
        var hasManifestMf = false
        var hasCertSf = false
        var hasCertRsa = false
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
                            hasBinaryManifest = inspect.isBinaryAxml
                        }
                        name == "classes.dex" -> {
                            val header = ByteArray(4)
                            val read = zip.getInputStream(entry).use { it.read(header) }
                            if (read == 4 && String(header, Charsets.US_ASCII) == "dex\n") {
                                hasClassesDex = true
                            }
                        }
                        name == "resources.arsc" -> {
                            hasResourcesArsc = true
                            if (entry.method != java.util.zip.ZipEntry.STORED) {
                                isResourcesArscUncompressed = false
                            }
                        }
                        name.equals("META-INF/MANIFEST.MF", ignoreCase = true) -> hasManifestMf = true
                        name.startsWith("META-INF/", ignoreCase = true) && name.endsWith(".SF", ignoreCase = true) -> hasCertSf = true
                        name.startsWith("META-INF/", ignoreCase = true) &&
                            (name.endsWith(".RSA", ignoreCase = true) || name.endsWith(".DSA", ignoreCase = true) || name.endsWith(".EC", ignoreCase = true)) -> hasCertRsa = true
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

        if (!hasBinaryManifest) {
            errors.add("ملف AndroidManifest.xml غير صالح أو ليس بصيغة Binary AXML المترجمة.")
        }
        if (!hasClassesDex) {
            errors.add("ملف الكود التنفيذي classes.dex مفقود أو تالف.")
        }
        if (hasResourcesArsc && !isResourcesArscUncompressed) {
            errors.add("ملف resources.arsc مضغوط (DEFLATED) مما يسبب فشل تحليل الحزمة على Android 11+.")
        }

        val isSigned = hasManifestMf && hasCertSf && hasCertRsa
        if (!isSigned) {
            errors.add("توقيع APK الرقمي (META-INF/CERT.RSA & CERT.SF) مفقود.")
        }

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

        // CRITICAL IDENTITY & INDEPENDENCE CHECK:
        // Verify that the generated APK does NOT carry the Studio's package name or app name!
        val hostPkg = context.packageName
        val isForbiddenPkg = parsedPkg.isNullOrBlank() ||
            parsedPkg.equals(hostPkg, ignoreCase = true) ||
            FORBIDDEN_STUDIO_PACKAGES.any { it.equals(parsedPkg, ignoreCase = true) }

        val isForbiddenLabel = parsedAppName.equals("HTML TO APK STUDIO", ignoreCase = true)
        val isStandaloneFromStudio = !isForbiddenPkg && !isForbiddenLabel

        if (isForbiddenPkg) {
            errors.add("خطأ حرج: ملف APK الناتج يحمل معرف الحزمة ($parsedPkg) المطابق لتطبيق HTML TO APK STUDIO! تم حظر البناء والتثبيت لمنع استبدال تطبيق الاستوديو.")
        }
        if (isForbiddenLabel) {
            errors.add("خطأ حرج: ملف APK الناتج يحمل اسم HTML TO APK STUDIO بدلاً من اسم التطبيق الجديد المستقل.")
        }
        if (!expectedPackageName.isNullOrBlank() && !parsedPkg.equals(expectedPackageName, ignoreCase = true)) {
            errors.add("عدم تطابق Package Name داخل APK ($parsedPkg) مع الحزمة المطلوبة ($expectedPackageName).")
        }
        if (!expectedAppName.isNullOrBlank() && !parsedAppName.equals(expectedAppName, ignoreCase = true)) {
            errors.add("عدم تطابق اسم التطبيق داخل APK ($parsedAppName) مع الاسم المطلوب ($expectedAppName).")
        }

        return ApkDeepValidationResult(
            isValid = errors.isEmpty() && isZipValid && hasBinaryManifest && hasClassesDex && isSigned && isPackageParserAccepted && isStandaloneFromStudio,
            isZipValid = isZipValid,
            hasBinaryManifest = hasBinaryManifest,
            hasClassesDex = hasClassesDex,
            hasResourcesArsc = hasResourcesArsc,
            isResourcesArscUncompressed = isResourcesArscUncompressed,
            isSignedWithCert = isSigned,
            isPackageParserAccepted = isPackageParserAccepted,
            isStandaloneFromStudio = isStandaloneFromStudio,
            parsedAppName = parsedAppName,
            parsedPackageName = parsedPkg,
            parsedVersionName = parsedVerName,
            parsedVersionCode = parsedVerCode,
            parsedMinSdk = manifestInspect?.minSdkFound ?: expectedMinSdk,
            parsedTargetSdk = manifestInspect?.targetSdkFound ?: expectedTargetSdk,
            parsedCompileSdk = manifestInspect?.compileSdkFound ?: expectedTargetSdk,
            launcherActivityName = manifestInspect?.launcherActivityFound,
            detectedAbis = if (abisFound.isEmpty()) listOf("Universal (Standalone DEX)") else abisFound.toList(),
            validationErrors = errors
        )
    }

    /**
     * Inspects the generated `.apk` file directly before installation so the user can see the
     * REAL extracted App Name, Package Name, Version Name, Version Code, Min/Target SDK, Size,
     * and Signing Status — blocking installation immediately if the APK matches `HTML TO APK STUDIO`.
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
            isSigned = validation.isSignedWithCert,
            signingStatus = if (validation.isSignedWithCert) "✓ موقع رقمياً (RSA-2048 • SHA-256)" else "✗ غير موقع",
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

        val isSigned = validationResult?.isSignedWithCert ?: true
        val isApkValid = validationResult?.isValid ?: true
        val isManifestValid = validationResult?.hasBinaryManifest ?: true
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
