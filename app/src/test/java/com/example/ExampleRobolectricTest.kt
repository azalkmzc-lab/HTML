package com.example

import com.example.analyzer.HtmlAiAnalyzerEngine
import com.example.builder.AndroidCompatibilityManager
import com.example.builder.ApkBuildProgress
import com.example.builder.BinaryManifestAndZipAligner
import com.example.builder.RealApkCompilerEngine
import com.example.builder.RealApkSignerEngine
import com.example.data.SampleTemplates
import com.example.data.StudioProjectEntity
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testBinaryAxmlStandaloneManifestGenerationAndInspection() {
        val axml = BinaryManifestAndZipAligner.generateStandaloneBinaryManifest(
            packageName = "com.xtv.live",
            appName = "XTV",
            versionCode = 10,
            versionName = "1.0.0",
            minSdk = 21,
            targetSdk = 35,
            compileSdk = 35,
            orientationMode = "LANDSCAPE",
            permissions = listOf("INTERNET", "ACCESS_NETWORK_STATE")
        )
        val inspect = BinaryManifestAndZipAligner.inspectBinaryManifest(axml)
        assertTrue("Manifest must be valid Binary AXML", inspect.isBinaryAxml)
        assertTrue("Manifest attributes must be strictly sorted by resource ID", inspect.areAttributesSortedByResId)
        assertEquals("com.xtv.live", inspect.packageNameFound)
        assertEquals("XTV", inspect.appNameFound)
        assertEquals("1.0.0", inspect.versionNameFound)
        assertEquals(10, inspect.versionCodeFound)
        assertEquals(21, inspect.minSdkFound)
        assertEquals(35, inspect.targetSdkFound)
        assertEquals(
            BinaryManifestAndZipAligner.STANDALONE_MAIN_ACTIVITY_CLASS,
            inspect.launcherActivityFound
        )

        // Verify ZipAlign 4-byte padding computation
        val pad = BinaryManifestAndZipAligner.computeZipAlignExtraPadding(
            currentStreamOffset = 101L,
            entryName = "resources.arsc"
        )
        val dataStart = 101L + 30L + "resources.arsc".toByteArray().size + pad.size
        assertEquals("Data offset must be 4-byte aligned", 0L, dataStart % 4L)
    }

    @Test
    fun testMandatoryApp6010LiveEndToEndBuildSignAlignAndSdkVerification() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val analyzer = HtmlAiAnalyzerEngine()
        val compiler = RealApkCompilerEngine(context)

        val testHtml = """
            <!DOCTYPE html>
            <html lang="ar" dir="rtl">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>App 6010 Live</title>
            </head>
            <body>
                <header>
                    <h1>الصفحة الرئيسية - App 6010 Live</h1>
                </header>
                <main>
                    <p id="statusText">مرحباً بك في التطبيق المستقل com.app6010.live</p>
                    <button id="actionBtn" onclick="handleButtonClick()">اضغط للتجربة التفاعلية</button>
                </main>
            </body>
            </html>
        """.trimIndent()

        val testCss = """
            body { background: #0B1020; color: #FFFFFF; font-family: sans-serif; text-align: center; padding: 24px; }
            button { background: #00E5FF; color: #000000; border: none; padding: 14px 24px; border-radius: 12px; font-weight: bold; }
        """.trimIndent()

        val testJs = """
            function handleButtonClick() {
                var el = document.getElementById('statusText');
                if (el) el.innerText = 'تم تفعيل الزر بنجاح داخل التطبيق المستقل!';
                if (window.AndroidNativePlayer && window.AndroidNativePlayer.showToast) {
                    window.AndroidNativePlayer.showToast('يعمل بنجاح داخل com.app6010.live');
                }
            }
        """.trimIndent()

        val app6010Project = StudioProjectEntity(
            id = 6010L,
            name = "App 6010 Live",
            packageName = "com.app6010.live",
            versionName = "1.0.0",
            versionCode = 1,
            developerName = "App6010 Studio",
            appDescription = "تطبيق ويب مستقل بالكامل بصيغة APK",
            primaryColorHex = "#00E5FF",
            accentColorHex = "#7C4DFF",
            iconPreset = "WEB_GLOBE",
            orientation = "AUTO",
            autoCompatibility = false,
            minSdk = 21,
            targetSdk = 35,
            compileSdk = 35,
            cpuArchitecture = "Universal APK",
            buildSigningMode = "RELEASE",
            htmlCode = testHtml,
            cssCode = testCss,
            jsCode = testJs
        )

        val analysisReport = analyzer.analyzeProjectCode(
            projectId = app6010Project.id,
            htmlCode = app6010Project.htmlCode,
            cssCode = app6010Project.cssCode,
            jsCode = app6010Project.jsCode
        )

        val emissions = compiler.executeRealApkBuild(
            project = app6010Project,
            channels = analysisReport.detectedChannels,
            analysisReport = analysisReport,
            allExistingProjects = listOf(app6010Project)
        ).toList()

        val finalState = emissions.last()
        assertTrue("com.app6010.live build must succeed, got: $finalState", finalState is ApkBuildProgress.Completed)
        val completed = finalState as ApkBuildProgress.Completed
        val apkFile = completed.outputApkFile

        assertTrue("APK file must exist and be non-empty", apkFile.exists() && apkFile.length() > 8192L)

        // 1. Verify all 8 mandatory APK VALIDATION checks passed
        val validation = completed.detailedReport.apkValidationResult
        assertTrue("Overall validation must pass", validation.isValid)
        assertTrue("✓ APK Structure Validation", validation.apkStructureValidationPassed)
        assertTrue("✓ AndroidManifest Validation", validation.manifestValidationPassed)
        assertTrue("✓ DEX Validation", validation.dexValidationPassed)
        assertTrue("✓ Resource Validation", validation.resourceValidationPassed)
        assertTrue("✓ Package Name Validation", validation.packageNameValidationPassed)
        assertTrue("✓ Signature Validation (V1 + V2)", validation.signatureValidationPassed)
        assertTrue("✓ ZIP Alignment Validation", validation.zipAlignmentValidationPassed)
        assertTrue("✓ SDK Compatibility Validation", validation.sdkCompatibilityValidationPassed)
        assertEquals("com.app6010.live", validation.parsedPackageName)
        assertEquals("com.app6010.live", validation.parsedResourcesPackageName)
        assertEquals("App 6010 Live", validation.parsedAppName)
        assertEquals("1.0.0", validation.parsedVersionName)
        assertEquals(1, validation.parsedVersionCode)
        assertEquals(21, validation.parsedMinSdk)
        assertEquals(35, validation.parsedTargetSdk)

        // 2. Verify with real Android SDK `apksigner verify --verbose`
        val apksignerBin = File("/opt/android/sdk/build-tools/36.0.0/apksigner")
        if (apksignerBin.exists()) {
            val proc = ProcessBuilder(
                apksignerBin.absolutePath,
                "verify",
                "--verbose",
                "--min-sdk-version", "21",
                apkFile.absolutePath
            ).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            assertEquals("apksigner verify must pass with exit code 0:\n$out", 0, proc.waitFor())
            assertTrue("Must verify V1 scheme:\n$out", out.contains("Verified using v1 scheme (JAR signing): true"))
            assertTrue("Must verify V2 scheme:\n$out", out.contains("Verified using v2 scheme (APK Signature Scheme v2): true"))
        }

        // 3. Verify with real Android SDK `zipalign -c -v 4`
        val zipalignBin = File("/opt/android/sdk/build-tools/36.0.0/zipalign")
        if (zipalignBin.exists()) {
            val proc = ProcessBuilder(
                zipalignBin.absolutePath,
                "-c", "-v", "4",
                apkFile.absolutePath
            ).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            assertEquals("zipalign -c -v 4 must pass with exit code 0:\n$out", 0, proc.waitFor())
        }

        // 4. Verify with real Android SDK `aapt2 dump badging`
        val aapt2Bin = File("/opt/android/sdk/build-tools/36.0.0/aapt2")
        if (aapt2Bin.exists()) {
            val proc = ProcessBuilder(
                aapt2Bin.absolutePath,
                "dump", "badging",
                apkFile.absolutePath
            ).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            assertEquals("aapt2 dump badging must pass with exit code 0:\n$out", 0, proc.waitFor())
            assertTrue("aapt2 must report package name='com.app6010.live':\n$out", out.contains("name='com.app6010.live'"))
            assertTrue("aapt2 must report versionCode='1':\n$out", out.contains("versionCode='1'"))
            assertTrue("aapt2 must report versionName='1.0.0':\n$out", out.contains("versionName='1.0.0'"))
            assertTrue("aapt2 must report minSdkVersion:'21':\n$out", out.contains("minSdkVersion:'21'") || out.contains("sdkVersion:'21'"))
            assertTrue("aapt2 must report targetSdkVersion:'35':\n$out", out.contains("targetSdkVersion:'35'"))
            assertTrue("aapt2 must report application-label:'App 6010 Live':\n$out", out.contains("application-label:'App 6010 Live'"))
            assertTrue("aapt2 must report launchable-activity:\n$out", out.contains("launchable-activity: name='com.standalone.runtime.StandaloneMainActivity'"))
        }
    }

    @Test
    fun testMandatoryTwoIndependentProjectsXtvAndSportBuildAndIsolation() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val analyzer = HtmlAiAnalyzerEngine()
        val compiler = RealApkCompilerEngine(context)

        // 1. Create Project 1: XTV (com.xtv.live)
        val xtvProject = StudioProjectEntity(
            id = 101L,
            name = "XTV",
            packageName = "com.xtv.live",
            versionName = "1.0.0",
            versionCode = 1,
            developerName = "XTV Media",
            appDescription = "تطبيق XTV المستقل للبث المباشر",
            primaryColorHex = "#00E5FF",
            accentColorHex = "#7C4DFF",
            autoCompatibility = false,
            minSdk = 21,
            targetSdk = 35,
            compileSdk = 35,
            cpuArchitecture = "Universal APK",
            buildSigningMode = "RELEASE",
            htmlCode = SampleTemplates.ARABIC_SPORTS_NEWS_IPTV_HTML,
            cssCode = SampleTemplates.ARABIC_SPORTS_NEWS_IPTV_CSS,
            jsCode = SampleTemplates.ARABIC_SPORTS_NEWS_IPTV_JS
        )

        // 2. Build XTV.apk
        val xtvReport = analyzer.analyzeProjectCode(
            projectId = xtvProject.id,
            htmlCode = xtvProject.htmlCode,
            cssCode = xtvProject.cssCode,
            jsCode = xtvProject.jsCode
        )
        val xtvEmissions = compiler.executeRealApkBuild(
            project = xtvProject,
            channels = xtvReport.detectedChannels,
            analysisReport = xtvReport,
            allExistingProjects = listOf(xtvProject)
        ).toList()

        val xtvFinal = xtvEmissions.last()
        assertTrue("XTV build must succeed, got: $xtvFinal", xtvFinal is ApkBuildProgress.Completed)
        val xtvCompleted = xtvFinal as ApkBuildProgress.Completed

        // 3. Create Project 2: SPORT (com.sport.live)
        val sportProject = StudioProjectEntity(
            id = 102L,
            name = "SPORT",
            packageName = "com.sport.live",
            versionName = "2.0.0",
            versionCode = 2,
            developerName = "Sport Network",
            appDescription = "تطبيق SPORT المستقل للقنوات الرياضية",
            primaryColorHex = "#10B981",
            accentColorHex = "#00E5FF",
            autoCompatibility = false,
            minSdk = 21,
            targetSdk = 35,
            compileSdk = 35,
            cpuArchitecture = "Universal APK",
            buildSigningMode = "RELEASE",
            htmlCode = SampleTemplates.ARABIC_SPORTS_NEWS_IPTV_HTML,
            cssCode = SampleTemplates.ARABIC_SPORTS_NEWS_IPTV_CSS,
            jsCode = SampleTemplates.ARABIC_SPORTS_NEWS_IPTV_JS
        )

        // 4. Build SPORT.apk
        val sportReport = analyzer.analyzeProjectCode(
            projectId = sportProject.id,
            htmlCode = sportProject.htmlCode,
            cssCode = sportProject.cssCode,
            jsCode = sportProject.jsCode
        )
        val sportEmissions = compiler.executeRealApkBuild(
            project = sportProject,
            channels = sportReport.detectedChannels,
            analysisReport = sportReport,
            allExistingProjects = listOf(xtvProject, sportProject)
        ).toList()

        val sportFinal = sportEmissions.last()
        assertTrue("SPORT build must succeed, got: $sportFinal", sportFinal is ApkBuildProgress.Completed)
        val sportCompleted = sportFinal as ApkBuildProgress.Completed

        // 5. Verify isolated project directories
        assertEquals("XTV.apk", xtvCompleted.outputApkFile.name)
        assertTrue(xtvCompleted.outputApkFile.absolutePath.endsWith("Projects/XTV/output/XTV.apk"))
        assertTrue(File(xtvCompleted.projectRootDir, "source/app/src/main/AndroidManifest.xml").exists())
        assertTrue(File(xtvCompleted.projectRootDir, "build/AndroidManifest.xml").exists())

        assertEquals("SPORT.apk", sportCompleted.outputApkFile.name)
        assertTrue(sportCompleted.outputApkFile.absolutePath.endsWith("Projects/SPORT/output/SPORT.apk"))
        assertTrue(File(sportCompleted.projectRootDir, "source/app/src/main/AndroidManifest.xml").exists())
        assertTrue(File(sportCompleted.projectRootDir, "build/AndroidManifest.xml").exists())

        // 6. Verify that both APKs are recognized as two distinct, standalone apps
        val xtvInspect = AndroidCompatibilityManager.inspectApkFileBeforeInstall(context, xtvCompleted.outputApkFile)
        val sportInspect = AndroidCompatibilityManager.inspectApkFileBeforeInstall(context, sportCompleted.outputApkFile)

        assertEquals("XTV", xtvInspect.extractedAppName)
        assertEquals("com.xtv.live", xtvInspect.extractedPackageName)
        assertEquals("1.0.0", xtvInspect.extractedVersionName)
        assertEquals(1, xtvInspect.extractedVersionCode)
        assertTrue(xtvInspect.isStandaloneFromStudio)
        assertTrue(xtvInspect.canProceedWithInstall)

        assertEquals("SPORT", sportInspect.extractedAppName)
        assertEquals("com.sport.live", sportInspect.extractedPackageName)
        assertEquals("2.0.0", sportInspect.extractedVersionName)
        assertEquals(2, sportInspect.extractedVersionCode)
        assertTrue(sportInspect.isStandaloneFromStudio)
        assertTrue(sportInspect.canProceedWithInstall)

        assertNotEquals(xtvInspect.extractedPackageName, sportInspect.extractedPackageName)
        assertNotEquals(xtvInspect.extractedAppName, sportInspect.extractedAppName)
        assertNotEquals(context.packageName, xtvInspect.extractedPackageName)
        assertNotEquals(context.packageName, sportInspect.extractedPackageName)

        // Verify neither APK contains any com/example/* Studio classes in classes.dex
        ZipFile(xtvCompleted.outputApkFile).use { zip ->
            val dexBytes = zip.getInputStream(zip.getEntry("classes.dex")).use { it.readBytes() }
            val dexAscii = String(dexBytes, Charsets.ISO_8859_1)
            assertTrue("Must contain StandaloneMainActivity", dexAscii.contains("Lcom/standalone/runtime/StandaloneMainActivity;"))
            assertFalse("Must NOT contain StudioViewModel", dexAscii.contains("StudioViewModel"))
        }
    }

    @Test
    fun testStudioPackageNameIsStrictlyBlocked() {
        val context = RuntimeEnvironment.getApplication()
        val check1 = AndroidCompatibilityManager.validatePackageNameIndependence(
            rawPackageName = "com.aistudio.htmltoapkstudio.vqxkmp",
            appName = "My App",
            studioPackageName = context.packageName
        )
        assertFalse("Must block Studio package name", check1.isValid)

        val check2 = AndroidCompatibilityManager.validatePackageNameIndependence(
            rawPackageName = "com.htmlapk.studio",
            appName = "My App",
            studioPackageName = context.packageName
        )
        assertFalse("Must block com.htmlapk.studio", check2.isValid)

        // Verify persistent keystore reuse when updating the same project
        val creds1 = RealApkSignerEngine.getOrCreateSigningCredentials(
            contextFilesDir = context.filesDir,
            packageName = "com.xtv.live",
            developerName = "XTV Media",
            isReleaseMode = true
        )
        val creds2 = RealApkSignerEngine.getOrCreateSigningCredentials(
            contextFilesDir = context.filesDir,
            packageName = "com.xtv.live",
            developerName = "XTV Media",
            isReleaseMode = true
        )
        assertEquals(
            "Updating the same project must reuse the exact same signing key fingerprint",
            creds1.certSha256Fingerprint,
            creds2.certSha256Fingerprint
        )
    }

    @Test
    fun testFirebaseIptvHtmlImportAnalyzeBuildPlayerBridgeAndSmartBackNavigation() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val analyzer = HtmlAiAnalyzerEngine()
        val compiler = RealApkCompilerEngine(context)

        // 1. Import HTML project containing Firebase Realtime Database + Categories + Dynamic Channels
        val firebaseIptvHtml = """
            <!DOCTYPE html>
            <html lang="ar" dir="rtl">
            <head>
                <meta charset="UTF-8">
                <title>Firebase Live TV 6010</title>
                <script src="https://www.gstatic.com/firebasejs/10.12.0/firebase-app-compat.js"></script>
                <script src="https://www.gstatic.com/firebasejs/10.12.0/firebase-database-compat.js"></script>
            </head>
            <body>
                <header><h1>Firebase Live TV 6010</h1></header>
                <nav id="categories-bar">
                    <button class="cat-tab active" data-category="الكل" onclick="filterCategory('الكل')">الكل</button>
                    <button class="cat-tab" data-category="قنوات رياضية" onclick="filterCategory('قنوات رياضية')">قنوات رياضية</button>
                    <button class="cat-tab" data-category="قنوات إخبارية" onclick="filterCategory('قنوات إخبارية')">قنوات إخبارية</button>
                </nav>
                <main id="channels-container"></main>
                <script>
                    const firebaseConfig = {
                        apiKey: "AIzaSyTestKeyForFirebaseLiveTv6010",
                        authDomain: "iptv-live-6010.firebaseapp.com",
                        databaseURL: "https://iptv-live-6010-default-rtdb.firebaseio.com",
                        projectId: "iptv-live-6010",
                        storageBucket: "iptv-live-6010.appspot.com"
                    };
                    firebase.initializeApp(firebaseConfig);
                    const db = firebase.database();

                    db.ref("categories").on("value", function(snap) {
                        console.log("Categories loaded from Firebase");
                    });

                    db.ref("channels").on("value", function(snapshot) {
                        const data = snapshot.val() || {};
                        const container = document.getElementById("channels-container");
                        container.innerHTML = "";
                        Object.keys(data).forEach(function(key) {
                            const ch = data[key];
                            const card = document.createElement("div");
                            card.className = "channel-card";
                            card.setAttribute("data-channel-id", key);
                            card.setAttribute("data-title", ch.name);
                            card.setAttribute("data-logo", ch.logo || "");
                            card.setAttribute("data-stream", ch.streamUrl);
                            card.setAttribute("data-category", ch.category || "قنوات رياضية");
                            card.onclick = function() {
                                playChannel({
                                    channelId: key,
                                    channelName: ch.name,
                                    channelLogo: ch.logo || "",
                                    streamUrl: ch.streamUrl,
                                    streamType: "HLS",
                                    categoryName: ch.category || "قنوات رياضية"
                                });
                            };
                            container.appendChild(card);
                        });
                    });

                    function playChannel(channelObj) {
                        console.log("Playing channel:", channelObj);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        // 2. Analyze the Firebase HTML project
        val report = analyzer.analyzeProjectCode(
            projectId = 6011L,
            htmlCode = firebaseIptvHtml,
            cssCode = "body { background: #0A0E17; color: #FFF; }",
            jsCode = ""
        )

        assertTrue("Firebase must be detected", report.firebaseSummary.usesFirebase)
        assertTrue("Firebase Realtime Database must be detected", report.firebaseSummary.usesRealtimeDatabase)
        assertEquals("iptv-live-6010", report.firebaseSummary.projectId)
        assertEquals("https://iptv-live-6010-default-rtdb.firebaseio.com", report.firebaseSummary.databaseUrl)
        assertTrue(
            "Must detect 'channels' & 'categories' paths",
            report.firebaseSummary.detectedPathsAndCollections.containsAll(listOf("channels", "categories"))
        )
        assertTrue(
            "Must detect dynamic playChannel function",
            report.firebaseSummary.dynamicChannelFunctions.contains("playChannel")
        )
        assertTrue("IPTV Smart Mode must be automatically triggered for Firebase channel project", report.isIptvSmartModeTriggered)

        // 3. Verify 100% preservation of original Firebase code + Smart Bridge injection
        val upgradedHtml = report.upgradedHtmlWithMedia3Bridge
        assertTrue("Original firebaseConfig must be preserved untouched", upgradedHtml.contains("https://iptv-live-6010-default-rtdb.firebaseio.com"))
        assertTrue("Original firebase.initializeApp must be preserved", upgradedHtml.contains("firebase.initializeApp(firebaseConfig)"))
        assertTrue("Original db.ref(\"channels\") must be preserved", upgradedHtml.contains("db.ref(\"channels\")"))
        assertTrue("Smart Bridge script must be injected", upgradedHtml.contains("id=\"studio-smart-bridge\""))
        assertTrue("Smart Back Navigation handler must be injected", upgradedHtml.contains("window.__handleStudioSmartBack"))
        assertTrue("JSON Native Player bridge call must be injected", upgradedHtml.contains("openNativePlayerJson"))
        assertTrue("Offline/online Firebase auto-reconnect must be injected", upgradedHtml.contains("goOnline"))

        // 4. Build standalone APK and verify PlayerActivity + StandaloneMainActivity + Permissions + Signatures
        val fbProject = StudioProjectEntity(
            id = 6011L,
            name = "Firebase Live 6010",
            packageName = "com.firebase6010.iptv",
            versionName = "1.0.0",
            versionCode = 1,
            developerName = "Live Stream Studio",
            appDescription = "تطبيق بث مباشر متصل بقاعدة بيانات Firebase",
            autoCompatibility = false,
            minSdk = 21,
            targetSdk = 35,
            compileSdk = 35,
            buildSigningMode = "RELEASE",
            htmlCode = firebaseIptvHtml,
            cssCode = "body { background: #0A0E17; color: #FFF; }",
            jsCode = ""
        )

        val emissions = compiler.executeRealApkBuild(
            project = fbProject,
            channels = report.detectedChannels,
            analysisReport = report,
            allExistingProjects = listOf(fbProject)
        ).toList()

        val finalState = emissions.last()
        assertTrue("Firebase IPTV APK build must succeed, got: $finalState", finalState is ApkBuildProgress.Completed)
        val completed = finalState as ApkBuildProgress.Completed
        val apkFile = completed.outputApkFile

        // Verify AndroidManifest.xml inside APK registers BOTH StandaloneMainActivity and PlayerActivity
        ZipFile(apkFile).use { zip ->
            val manifestBytes = zip.getInputStream(zip.getEntry("AndroidManifest.xml")).use { it.readBytes() }
            val manifestInfo = com.example.builder.BinaryManifestAndZipAligner.inspectBinaryManifest(manifestBytes)
            assertEquals("com.firebase6010.iptv", manifestInfo.packageNameFound)
            assertTrue(
                "Manifest must declare StandaloneMainActivity",
                manifestInfo.activitiesFound.contains("com.standalone.runtime.StandaloneMainActivity")
            )
            assertTrue(
                "Manifest must declare PlayerActivity for native channel playback",
                manifestInfo.activitiesFound.contains("com.standalone.runtime.PlayerActivity")
            )
            assertTrue(
                "Manifest must declare INTERNET permission for Firebase & streaming",
                manifestInfo.permissionsFound.contains("android.permission.INTERNET")
            )
            assertTrue(
                "Manifest must declare ACCESS_NETWORK_STATE permission",
                manifestInfo.permissionsFound.contains("android.permission.ACCESS_NETWORK_STATE")
            )

            // Verify classes.dex contains both StandaloneMainActivity and PlayerActivity
            val dexBytes = zip.getInputStream(zip.getEntry("classes.dex")).use { it.readBytes() }
            val dexAscii = String(dexBytes, Charsets.ISO_8859_1)
            assertTrue("classes.dex must contain StandaloneMainActivity", dexAscii.contains("Lcom/standalone/runtime/StandaloneMainActivity;"))
            assertTrue("classes.dex must contain PlayerActivity", dexAscii.contains("Lcom/standalone/runtime/PlayerActivity;"))

            // Verify assets/index.html inside APK has both Firebase config and Smart Bridge
            val packagedHtml = zip.getInputStream(zip.getEntry("assets/index.html")).use { it.readBytes().toString(Charsets.UTF_8) }
            assertTrue(packagedHtml.contains("https://iptv-live-6010-default-rtdb.firebaseio.com"))
            assertTrue(packagedHtml.contains("window.__handleStudioSmartBack"))
            assertTrue(packagedHtml.contains("openNativePlayerJson"))
        }

        // Verify with official Android SDK apksigner & zipalign
        val apksignerBin = File("/opt/android/sdk/build-tools/36.0.0/apksigner")
        if (apksignerBin.exists()) {
            val proc = ProcessBuilder(
                apksignerBin.absolutePath, "verify", "--verbose", "--min-sdk-version", "21", apkFile.absolutePath
            ).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            assertEquals("apksigner verify must succeed:\n$out", 0, proc.waitFor())
        }
    }
}

