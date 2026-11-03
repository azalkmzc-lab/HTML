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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testBinaryAxmlManifestPatchingAndInspection() {
        val initialAxml = BinaryManifestAndZipAligner.createMinimalValidBinaryAxml(
            minSdk = 24,
            targetSdk = 35,
            versionCode = 1
        )
        val inspect1 = BinaryManifestAndZipAligner.inspectBinaryManifest(initialAxml)
        assertTrue("Manifest should be recognized as valid Binary AXML", inspect1.isBinaryAxml)

        // Verify ZipAlign 4-byte padding computation
        val pad = BinaryManifestAndZipAligner.computeZipAlignExtraPadding(
            currentStreamOffset = 101L,
            entryName = "resources.arsc"
        )
        val dataStart = 101L + 30L + "resources.arsc".toByteArray().size + pad.size
        assertEquals("Data offset must be 4-byte aligned", 0L, dataStart % 4L)
    }

    @Test
    fun testCryptographicApkSignerDebugAndReleaseKeystores() {
        val context = RuntimeEnvironment.getApplication()
        val debugCreds = RealApkSignerEngine.getOrCreateSigningCredentials(
            contextFilesDir = context.filesDir,
            packageName = "com.studio.test.iptv",
            developerName = "HTML TO APK STUDIO",
            isReleaseMode = false
        )
        assertEquals("androiddebugkey", debugCreds.alias)
        assertTrue(debugCreds.x509CertificateDer.isNotEmpty())

        val releaseCreds = RealApkSignerEngine.getOrCreateSigningCredentials(
            contextFilesDir = context.filesDir,
            packageName = "com.studio.test.iptv",
            developerName = "HTML TO APK STUDIO",
            isReleaseMode = true
        )
        assertEquals("release_upload_key", releaseCreds.alias)
        assertTrue(releaseCreds.isReleaseKey)

        val sigFiles = RealApkSignerEngine.generateApkSignatureFiles(
            entrySha256DigestsBase64 = mapOf(
                "AndroidManifest.xml" to "dGVzdGRpZ2VzdDE=",
                "classes.dex" to "dGVzdGRpZ2VzdDI="
            ),
            credentials = releaseCreds
        )
        assertTrue(sigFiles.manifestMfBytes.isNotEmpty())
        assertTrue(sigFiles.certSfBytes.isNotEmpty())
        assertTrue(sigFiles.certRsaBytes.isNotEmpty())
        assertEquals("META-INF/CERT.SF", sigFiles.sfEntryName)
        assertEquals("META-INF/CERT.RSA", sigFiles.rsaEntryName)
    }

    @Test
    fun testRealApkBuildPipelineAndValidation() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val analyzer = HtmlAiAnalyzerEngine()
        val compiler = RealApkCompilerEngine(context)

        val project = StudioProjectEntity(
            id = 1L,
            name = "MAX IPTV TEST",
            packageName = "com.studio.maxiptv.test",
            versionName = "2.0.0",
            versionCode = 20,
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

        val report = analyzer.analyzeProjectCode(
            projectId = project.id,
            htmlCode = project.htmlCode,
            cssCode = project.cssCode,
            jsCode = project.jsCode
        )
        assertTrue("Should detect IPTV channels", report.detectedChannels.isNotEmpty())

        val emissions = compiler.executeRealApkBuild(
            project = project,
            channels = report.detectedChannels,
            analysisReport = report
        ).toList()

        val lastState = emissions.last()
        assertTrue("Build should complete successfully, got: $lastState", lastState is ApkBuildProgress.Completed)

        val completed = lastState as ApkBuildProgress.Completed
        assertTrue("APK file must exist", completed.outputApkFile.exists())
        assertTrue("APK validation must pass", completed.detailedReport.apkValidationResult.isValid)
        assertTrue("APK must be signed", completed.detailedReport.apkValidationResult.isSignedWithCert)
        assertEquals("Android 5.0 (API 21)", completed.detailedReport.minimumAndroidLabel)
        assertEquals("Android 15 (API 35)", completed.detailedReport.targetAndroidLabel)
    }
}
