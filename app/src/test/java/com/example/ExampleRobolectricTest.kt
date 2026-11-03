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

        // 5. Verify isolated project directories:
        // Projects/XTV/source/, Projects/XTV/build/, Projects/XTV/output/XTV.apk
        // Projects/SPORT/source/, Projects/SPORT/build/, Projects/SPORT/output/SPORT.apk
        assertEquals("XTV.apk", xtvCompleted.outputApkFile.name)
        assertTrue(xtvCompleted.outputApkFile.absolutePath.endsWith("Projects/XTV/output/XTV.apk"))
        assertTrue(File(xtvCompleted.projectRootDir, "source/app/src/main/AndroidManifest.xml").exists())
        assertTrue(File(xtvCompleted.projectRootDir, "build/AndroidManifest.xml").exists())

        assertEquals("SPORT.apk", sportCompleted.outputApkFile.name)
        assertTrue(sportCompleted.outputApkFile.absolutePath.endsWith("Projects/SPORT/output/SPORT.apk"))
        assertTrue(File(sportCompleted.projectRootDir, "source/app/src/main/AndroidManifest.xml").exists())
        assertTrue(File(sportCompleted.projectRootDir, "build/AndroidManifest.xml").exists())

        // 6. Verify that both APKs are recognized as two distinct, standalone apps
        //    and NEITHER has HTML TO APK STUDIO's package name or app name!
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

        // If aapt2 is installed in container, verify `aapt2 dump badging` on both generated APKs!
        val aapt2Bin = File("/opt/android/sdk/build-tools/36.0.0/aapt2")
        if (aapt2Bin.exists()) {
            val proc1 = ProcessBuilder(aapt2Bin.absolutePath, "dump", "badging", xtvCompleted.outputApkFile.absolutePath)
                .redirectErrorStream(true)
                .start()
            val out1 = proc1.inputStream.bufferedReader().readText()
            assertEquals("aapt2 dump badging on XTV.apk must succeed: $out1", 0, proc1.waitFor())
            assertTrue("aapt2 must report package name='com.xtv.live', got: $out1", out1.contains("name='com.xtv.live'"))
            assertTrue("aapt2 must report application-label:'XTV', got: $out1", out1.contains("application-label:'XTV'"))

            val proc2 = ProcessBuilder(aapt2Bin.absolutePath, "dump", "badging", sportCompleted.outputApkFile.absolutePath)
                .redirectErrorStream(true)
                .start()
            val out2 = proc2.inputStream.bufferedReader().readText()
            assertEquals("aapt2 dump badging on SPORT.apk must succeed: $out2", 0, proc2.waitFor())
            assertTrue("aapt2 must report package name='com.sport.live', got: $out2", out2.contains("name='com.sport.live'"))
            assertTrue("aapt2 must report application-label:'SPORT', got: $out2", out2.contains("application-label:'SPORT'"))
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
}
