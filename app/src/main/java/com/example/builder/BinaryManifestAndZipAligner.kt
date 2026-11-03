package com.example.builder

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Generator, Inspector, and Aligner for Android's compiled Binary XML (`AndroidManifest.xml`)
 * and 4-byte ZIP Alignment.
 *
 * Ensures every generated APK is a 100% standalone Android application with its own independent
 * `packageName`, `android:label` (App Name), `versionCode`, `versionName`, `minSdkVersion`,
 * `targetSdkVersion`, permissions, and standalone `StandaloneMainActivity` entry point —
 * completely separated from `HTML TO APK STUDIO`.
 */
object BinaryManifestAndZipAligner {

    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
    private const val RES_XML_START_NAMESPACE_TYPE = 0x0100
    private const val RES_XML_END_NAMESPACE_TYPE = 0x0101
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val RES_XML_END_ELEMENT_TYPE = 0x0103

    // Value data types in Res_value
    private const val TYPE_REFERENCE = 0x01
    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11
    private const val TYPE_INT_BOOLEAN = 0x12

    // Standard Android framework resource IDs (sorted in ascending order)
    private const val ATTR_LABEL = 0x01010001
    private const val ATTR_ICON = 0x01010002
    private const val ATTR_NAME = 0x01010003
    private const val ATTR_EXPORTED = 0x01010010
    private const val ATTR_SCREEN_ORIENTATION = 0x0101001e
    private const val ATTR_CONFIG_CHANGES = 0x0101001f
    private const val ATTR_MIN_SDK_VERSION = 0x0101020c
    private const val ATTR_VERSION_CODE = 0x0101021b
    private const val ATTR_VERSION_NAME = 0x0101021c
    private const val ATTR_TARGET_SDK_VERSION = 0x01010270
    private const val ATTR_ALLOW_BACKUP = 0x01010280
    private const val ATTR_SUPPORTS_RTL = 0x010103af
    private const val ATTR_USES_CLEARTEXT_TRAFFIC = 0x010104ec
    private const val ATTR_COMPILE_SDK_VERSION = 0x01010572

    private const val ANDROID_NS_URI = "http://schemas.android.com/apk/res/android"
    const val STANDALONE_MAIN_ACTIVITY_CLASS = "com.standalone.runtime.StandaloneMainActivity"
    const val STANDALONE_ICON_RES_ID = 0x7f010000

    data class ManifestInspectResult(
        val isBinaryAxml: Boolean,
        val minSdkFound: Int?,
        val targetSdkFound: Int?,
        val versionCodeFound: Int?,
        val packageNameFound: String? = null,
        val appNameFound: String? = null,
        val versionNameFound: String? = null,
        val compileSdkFound: Int? = null,
        val launcherActivityFound: String? = null,
        val permissionsFound: List<String> = emptyList()
    )

    private data class AxmlAttr(
        val nsIndex: Int,
        val nameIndex: Int,
        val rawValueIndex: Int,
        val dataType: Int,
        val dataValue: Int,
        val sortResourceId: Int
    )

    /**
     * Generates a complete, 100% standalone compiled Binary AXML (`AndroidManifest.xml`) from scratch
     * for the target user application. Contains ZERO references to HTML TO APK STUDIO.
     */
    fun generateStandaloneBinaryManifest(
        packageName: String,
        appName: String,
        versionCode: Int,
        versionName: String,
        minSdk: Int,
        targetSdk: Int,
        compileSdk: Int,
        orientationMode: String = "AUTO",
        permissions: List<String> = listOf("INTERNET", "ACCESS_NETWORK_STATE", "WAKE_LOCK"),
        launcherActivityClass: String = STANDALONE_MAIN_ACTIVITY_CLASS
    ): ByteArray {
        val cleanPkg = packageName.trim().ifBlank { "com.standalone.generated.app" }
        val cleanAppName = appName.trim().ifBlank { "Standalone App" }
        val cleanVerName = versionName.trim().ifBlank { "1.0.0" }

        // 1. Attribute names with framework resource IDs MUST come first in the String Pool
        //    in ascending order of resource ID so RES_XML_RESOURCE_MAP_TYPE aligns 1:1.
        val resourceMappedAttrs = listOf(
            "label" to ATTR_LABEL,                               // 0
            "icon" to ATTR_ICON,                                 // 1
            "name" to ATTR_NAME,                                 // 2
            "exported" to ATTR_EXPORTED,                         // 3
            "screenOrientation" to ATTR_SCREEN_ORIENTATION,       // 4
            "configChanges" to ATTR_CONFIG_CHANGES,              // 5
            "minSdkVersion" to ATTR_MIN_SDK_VERSION,             // 6
            "versionCode" to ATTR_VERSION_CODE,                  // 7
            "versionName" to ATTR_VERSION_NAME,                  // 8
            "targetSdkVersion" to ATTR_TARGET_SDK_VERSION,       // 9
            "allowBackup" to ATTR_ALLOW_BACKUP,                  // 10
            "supportsRtl" to ATTR_SUPPORTS_RTL,                  // 11
            "usesCleartextTraffic" to ATTR_USES_CLEARTEXT_TRAFFIC, // 12
            "compileSdkVersion" to ATTR_COMPILE_SDK_VERSION      // 13
        )

        val stringList = mutableListOf<String>()
        val resourceIds = IntArray(resourceMappedAttrs.size)
        resourceMappedAttrs.forEachIndexed { idx, (attrName, resId) ->
            stringList.add(attrName)
            resourceIds[idx] = resId
        }

        fun getOrAddString(s: String): Int {
            val existing = stringList.indexOf(s)
            if (existing >= 0) return existing
            stringList.add(s)
            return stringList.size - 1
        }

        // Fixed indices for resource-mapped attributes
        val idxLabel = 0
        val idxIcon = 1
        val idxName = 2
        val idxExported = 3
        val idxScreenOrientation = 4
        val idxConfigChanges = 5
        val idxMinSdk = 6
        val idxVersionCode = 7
        val idxVersionName = 8
        val idxTargetSdk = 9
        val idxAllowBackup = 10
        val idxSupportsRtl = 11
        val idxCleartext = 12
        val idxCompileSdk = 13

        // Non-resource strings (elements, namespaces, values)
        val idxNsPrefix = getOrAddString("android")
        val idxNsUri = getOrAddString(ANDROID_NS_URI)
        val idxManifest = getOrAddString("manifest")
        val idxPackageAttr = getOrAddString("package")
        val idxPkgValue = getOrAddString(cleanPkg)
        val idxVerNameValue = getOrAddString(cleanVerName)

        val idxUsesSdk = getOrAddString("uses-sdk")
        val idxUsesPermission = getOrAddString("uses-permission")

        val fullPermStrings = permissions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { if (it.startsWith("android.permission.")) it else "android.permission.$it" }
            .distinct()
        val permValueIndices = fullPermStrings.map { getOrAddString(it) }

        val idxApplication = getOrAddString("application")
        val idxAppNameValue = getOrAddString(cleanAppName)
        val idxActivity = getOrAddString("activity")
        val idxActivityClassValue = getOrAddString(launcherActivityClass)
        val idxIntentFilter = getOrAddString("intent-filter")
        val idxAction = getOrAddString("action")
        val idxActionMainValue = getOrAddString("android.intent.action.MAIN")
        val idxCategory = getOrAddString("category")
        val idxCategoryLauncherValue = getOrAddString("android.intent.category.LAUNCHER")

        val orientInt = when (orientationMode.uppercase()) {
            "LANDSCAPE" -> 0 // ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "PORTRAIT" -> 1  // ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> -1       // ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        // Build XML chunks
        val xmlChunks = ByteArrayOutputStream()

        // Start Namespace: xmlns:android="http://schemas.android.com/apk/res/android"
        xmlChunks.write(buildNamespaceChunk(isStart = true, line = 2, prefixIdx = idxNsPrefix, uriIdx = idxNsUri))

        // <manifest package="..." android:versionCode="..." android:versionName="..." android:compileSdkVersion="...">
        val manifestAttrs = listOf(
            AxmlAttr(
                nsIndex = -1,
                nameIndex = idxPackageAttr,
                rawValueIndex = idxPkgValue,
                dataType = TYPE_STRING,
                dataValue = idxPkgValue,
                sortResourceId = 0
            ),
            AxmlAttr(
                nsIndex = idxNsUri,
                nameIndex = idxVersionCode,
                rawValueIndex = -1,
                dataType = TYPE_INT_DEC,
                dataValue = versionCode,
                sortResourceId = ATTR_VERSION_CODE
            ),
            AxmlAttr(
                nsIndex = idxNsUri,
                nameIndex = idxVersionName,
                rawValueIndex = idxVerNameValue,
                dataType = TYPE_STRING,
                dataValue = idxVerNameValue,
                sortResourceId = ATTR_VERSION_NAME
            ),
            AxmlAttr(
                nsIndex = idxNsUri,
                nameIndex = idxCompileSdk,
                rawValueIndex = -1,
                dataType = TYPE_INT_DEC,
                dataValue = compileSdk,
                sortResourceId = ATTR_COMPILE_SDK_VERSION
            )
        )
        xmlChunks.write(buildStartElementChunk(line = 2, nameIdx = idxManifest, attrs = manifestAttrs))

        // <uses-sdk android:minSdkVersion="..." android:targetSdkVersion="..." />
        val usesSdkAttrs = listOf(
            AxmlAttr(
                nsIndex = idxNsUri,
                nameIndex = idxMinSdk,
                rawValueIndex = -1,
                dataType = TYPE_INT_DEC,
                dataValue = minSdk,
                sortResourceId = ATTR_MIN_SDK_VERSION
            ),
            AxmlAttr(
                nsIndex = idxNsUri,
                nameIndex = idxTargetSdk,
                rawValueIndex = -1,
                dataType = TYPE_INT_DEC,
                dataValue = targetSdk,
                sortResourceId = ATTR_TARGET_SDK_VERSION
            )
        )
        xmlChunks.write(buildStartElementChunk(line = 4, nameIdx = idxUsesSdk, attrs = usesSdkAttrs))
        xmlChunks.write(buildEndElementChunk(line = 4, nameIdx = idxUsesSdk))

        // <uses-permission android:name="..." />
        permValueIndices.forEachIndexed { idx, permValIdx ->
            val permAttrs = listOf(
                AxmlAttr(
                    nsIndex = idxNsUri,
                    nameIndex = idxName,
                    rawValueIndex = permValIdx,
                    dataType = TYPE_STRING,
                    dataValue = permValIdx,
                    sortResourceId = ATTR_NAME
                )
            )
            xmlChunks.write(buildStartElementChunk(line = 6 + idx, nameIdx = idxUsesPermission, attrs = permAttrs))
            xmlChunks.write(buildEndElementChunk(line = 6 + idx, nameIdx = idxUsesPermission))
        }

        // <application android:label="..." android:icon="@0x7f010000" android:allowBackup="true" android:supportsRtl="true" android:usesCleartextTraffic="true">
        val appAttrs = listOf(
            AxmlAttr(
                nsIndex = idxNsUri,
                nameIndex = idxLabel,
                rawValueIndex = idxAppNameValue,
                dataType = TYPE_STRING,
                dataValue = idxAppNameValue,
                sortResourceId = ATTR_LABEL
            ),
            AxmlAttr(
                nsIndex = idxNsUri,
                nameIndex = idxIcon,
                rawValueIndex = -1,
                dataType = TYPE_REFERENCE,
                dataValue = STANDALONE_ICON_RES_ID,
                sortResourceId = ATTR_ICON
            ),
            AxmlAttr(
                nsIndex = idxNsUri,
                nameIndex = idxAllowBackup,
                rawValueIndex = -1,
                dataType = TYPE_INT_BOOLEAN,
                dataValue = -1,
                sortResourceId = ATTR_ALLOW_BACKUP
            ),
            AxmlAttr(
                nsIndex = idxNsUri,
                nameIndex = idxSupportsRtl,
                rawValueIndex = -1,
                dataType = TYPE_INT_BOOLEAN,
                dataValue = -1,
                sortResourceId = ATTR_SUPPORTS_RTL
            ),
            AxmlAttr(
                nsIndex = idxNsUri,
                nameIndex = idxCleartext,
                rawValueIndex = -1,
                dataType = TYPE_INT_BOOLEAN,
                dataValue = -1,
                sortResourceId = ATTR_USES_CLEARTEXT_TRAFFIC
            )
        )
        xmlChunks.write(buildStartElementChunk(line = 12, nameIdx = idxApplication, attrs = appAttrs))

        // <activity android:label="..." android:icon="@0x7f010000" android:name="com.standalone.runtime.StandaloneMainActivity" android:exported="true" android:screenOrientation="..." android:configChanges="0x04a0">
        val activityAttrs = mutableListOf(
            AxmlAttr(
                nsIndex = idxNsUri,
                nameIndex = idxLabel,
                rawValueIndex = idxAppNameValue,
                dataType = TYPE_STRING,
                dataValue = idxAppNameValue,
                sortResourceId = ATTR_LABEL
            ),
            AxmlAttr(
                nsIndex = idxNsUri,
                nameIndex = idxIcon,
                rawValueIndex = -1,
                dataType = TYPE_REFERENCE,
                dataValue = STANDALONE_ICON_RES_ID,
                sortResourceId = ATTR_ICON
            ),
            AxmlAttr(
                nsIndex = idxNsUri,
                nameIndex = idxName,
                rawValueIndex = idxActivityClassValue,
                dataType = TYPE_STRING,
                dataValue = idxActivityClassValue,
                sortResourceId = ATTR_NAME
            ),
            AxmlAttr(
                nsIndex = idxNsUri,
                nameIndex = idxExported,
                rawValueIndex = -1,
                dataType = TYPE_INT_BOOLEAN,
                dataValue = -1,
                sortResourceId = ATTR_EXPORTED
            ),
            AxmlAttr(
                nsIndex = idxNsUri,
                nameIndex = idxScreenOrientation,
                rawValueIndex = -1,
                dataType = TYPE_INT_DEC,
                dataValue = orientInt,
                sortResourceId = ATTR_SCREEN_ORIENTATION
            ),
            AxmlAttr(
                nsIndex = idxNsUri,
                nameIndex = idxConfigChanges,
                rawValueIndex = -1,
                dataType = TYPE_INT_HEX,
                dataValue = 0x000004A0, // orientation|screenSize|keyboardHidden
                sortResourceId = ATTR_CONFIG_CHANGES
            )
        )
        xmlChunks.write(buildStartElementChunk(line = 14, nameIdx = idxActivity, attrs = activityAttrs))

        // <intent-filter>
        xmlChunks.write(buildStartElementChunk(line = 16, nameIdx = idxIntentFilter, attrs = emptyList()))

        // <action android:name="android.intent.action.MAIN" />
        val actionAttrs = listOf(
            AxmlAttr(
                nsIndex = idxNsUri,
                nameIndex = idxName,
                rawValueIndex = idxActionMainValue,
                dataType = TYPE_STRING,
                dataValue = idxActionMainValue,
                sortResourceId = ATTR_NAME
            )
        )
        xmlChunks.write(buildStartElementChunk(line = 17, nameIdx = idxAction, attrs = actionAttrs))
        xmlChunks.write(buildEndElementChunk(line = 17, nameIdx = idxAction))

        // <category android:name="android.intent.category.LAUNCHER" />
        val categoryAttrs = listOf(
            AxmlAttr(
                nsIndex = idxNsUri,
                nameIndex = idxName,
                rawValueIndex = idxCategoryLauncherValue,
                dataType = TYPE_STRING,
                dataValue = idxCategoryLauncherValue,
                sortResourceId = ATTR_NAME
            )
        )
        xmlChunks.write(buildStartElementChunk(line = 18, nameIdx = idxCategory, attrs = categoryAttrs))
        xmlChunks.write(buildEndElementChunk(line = 18, nameIdx = idxCategory))

        // </intent-filter>
        xmlChunks.write(buildEndElementChunk(line = 19, nameIdx = idxIntentFilter))
        // </activity>
        xmlChunks.write(buildEndElementChunk(line = 20, nameIdx = idxActivity))
        // </application>
        xmlChunks.write(buildEndElementChunk(line = 21, nameIdx = idxApplication))
        // </manifest>
        xmlChunks.write(buildEndElementChunk(line = 22, nameIdx = idxManifest))

        // End Namespace
        xmlChunks.write(buildNamespaceChunk(isStart = false, line = 22, prefixIdx = idxNsPrefix, uriIdx = idxNsUri))

        val stringPoolBytes = buildUtf16StringPoolChunk(stringList)
        val resourceMapBytes = buildResourceMapChunk(resourceIds)
        val xmlNodeBytes = xmlChunks.toByteArray()

        val totalFileSize = 8 + stringPoolBytes.size + resourceMapBytes.size + xmlNodeBytes.size
        val finalBuffer = ByteBuffer.allocate(totalFileSize).order(ByteOrder.LITTLE_ENDIAN)
        finalBuffer.putShort(RES_XML_TYPE.toShort())
        finalBuffer.putShort(8.toShort())
        finalBuffer.putInt(totalFileSize)
        finalBuffer.put(stringPoolBytes)
        finalBuffer.put(resourceMapBytes)
        finalBuffer.put(xmlNodeBytes)
        return finalBuffer.array()
    }

    private fun buildUtf16StringPoolChunk(strings: List<String>): ByteArray {
        val stringCount = strings.size
        val encodedStrings = ArrayList<ByteArray>(stringCount)
        val offsets = IntArray(stringCount)
        var currentStringOffset = 0

        for (i in 0 until stringCount) {
            offsets[i] = currentStringOffset
            val s = strings[i]
            val charLen = s.length
            val strBytes = s.toByteArray(StandardCharsets.UTF_16LE)
            val entryBuf = ByteBuffer.allocate(2 + strBytes.size + 2).order(ByteOrder.LITTLE_ENDIAN)
            entryBuf.putShort(charLen.toShort())
            entryBuf.put(strBytes)
            entryBuf.putShort(0.toShort()) // UTF-16 null terminator
            val arr = entryBuf.array()
            encodedStrings.add(arr)
            currentStringOffset += arr.size
        }

        val headerSize = 28
        val stringsStart = headerSize + stringCount * 4
        val unalignedTotal = stringsStart + currentStringOffset
        val padding = (4 - (unalignedTotal % 4)) % 4
        val totalChunkSize = unalignedTotal + padding

        val buf = ByteBuffer.allocate(totalChunkSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(RES_STRING_POOL_TYPE.toShort())
        buf.putShort(headerSize.toShort())
        buf.putInt(totalChunkSize)
        buf.putInt(stringCount)
        buf.putInt(0) // styleCount
        buf.putInt(0) // flags = 0 (UTF-16LE)
        buf.putInt(stringsStart)
        buf.putInt(0) // stylesStart

        for (offset in offsets) {
            buf.putInt(offset)
        }
        for (arr in encodedStrings) {
            buf.put(arr)
        }
        for (p in 0 until padding) {
            buf.put(0.toByte())
        }
        return buf.array()
    }

    private fun buildResourceMapChunk(resourceIds: IntArray): ByteArray {
        val chunkSize = 8 + resourceIds.size * 4
        val buf = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(RES_XML_RESOURCE_MAP_TYPE.toShort())
        buf.putShort(8.toShort())
        buf.putInt(chunkSize)
        for (id in resourceIds) {
            buf.putInt(id)
        }
        return buf.array()
    }

    private fun buildNamespaceChunk(isStart: Boolean, line: Int, prefixIdx: Int, uriIdx: Int): ByteArray {
        val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort((if (isStart) RES_XML_START_NAMESPACE_TYPE else RES_XML_END_NAMESPACE_TYPE).toShort())
        buf.putShort(16.toShort())
        buf.putInt(24)
        buf.putInt(line)
        buf.putInt(-1) // comment
        buf.putInt(prefixIdx)
        buf.putInt(uriIdx)
        return buf.array()
    }

    private fun buildStartElementChunk(line: Int, nameIdx: Int, attrs: List<AxmlAttr>): ByteArray {
        val chunkSize = 36 + attrs.size * 20
        val buf = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(RES_XML_START_ELEMENT_TYPE.toShort())
        buf.putShort(16.toShort())
        buf.putInt(chunkSize)
        buf.putInt(line)
        buf.putInt(-1) // comment
        buf.putInt(-1) // ns
        buf.putInt(nameIdx)
        buf.putShort(20.toShort()) // attributeStart
        buf.putShort(20.toShort()) // attributeSize
        buf.putShort(attrs.size.toShort()) // attributeCount
        buf.putShort(0.toShort()) // idIndex
        buf.putShort(0.toShort()) // classIndex
        buf.putShort(0.toShort()) // styleIndex

        for (attr in attrs) {
            buf.putInt(attr.nsIndex)
            buf.putInt(attr.nameIndex)
            buf.putInt(attr.rawValueIndex)
            buf.putShort(8.toShort()) // typedValue.size
            buf.put(0.toByte())       // typedValue.res0
            buf.put(attr.dataType.toByte())
            buf.putInt(attr.dataValue)
        }
        return buf.array()
    }

    private fun buildEndElementChunk(line: Int, nameIdx: Int): ByteArray {
        val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(RES_XML_END_ELEMENT_TYPE.toShort())
        buf.putShort(16.toShort())
        buf.putInt(24)
        buf.putInt(line)
        buf.putInt(-1) // comment
        buf.putInt(-1) // ns
        buf.putInt(nameIdx)
        return buf.array()
    }

    /**
     * Patches the integer attributes (`minSdkVersion`, `targetSdkVersion`, `versionCode`, `compileSdkVersion`,
     * and `screenOrientation`) inside a compiled binary `AndroidManifest.xml` byte array.
     */
    fun patchBinaryManifestIntegers(
        axmlBytes: ByteArray,
        newMinSdk: Int,
        newTargetSdk: Int,
        newCompileSdk: Int,
        newVersionCode: Int,
        orientationMode: String
    ): ByteArray {
        if (axmlBytes.size < 24) return axmlBytes
        val outBytes = axmlBytes.copyOf()
        val buf = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)

        val fileType = buf.getShort(0).toInt() and 0xFFFF
        if (fileType != RES_XML_TYPE) {
            return axmlBytes
        }

        val totalSize = buf.getInt(4)
        var offset = (buf.getShort(2).toInt() and 0xFFFF)
        var resourceIds = IntArray(0)

        while (offset + 8 <= outBytes.size && offset < totalSize) {
            val chunkType = buf.getShort(offset).toInt() and 0xFFFF
            val headerSize = buf.getShort(offset + 2).toInt() and 0xFFFF
            val chunkSize = buf.getInt(offset + 4)
            if (chunkSize <= 0) break

            when (chunkType) {
                RES_XML_RESOURCE_MAP_TYPE -> {
                    val count = (chunkSize - headerSize) / 4
                    if (count > 0 && offset + headerSize + count * 4 <= outBytes.size) {
                        resourceIds = IntArray(count)
                        for (i in 0 until count) {
                            resourceIds[i] = buf.getInt(offset + headerSize + i * 4)
                        }
                    }
                }

                RES_XML_START_ELEMENT_TYPE -> {
                    if (offset + 36 <= outBytes.size) {
                        val attrStart = buf.getShort(offset + 24).toInt() and 0xFFFF
                        val attrSize = buf.getShort(offset + 26).toInt() and 0xFFFF
                        val attrCount = buf.getShort(offset + 28).toInt() and 0xFFFF
                        val attrsBase = offset + 16 + attrStart

                        if (attrSize >= 20) {
                            for (i in 0 until attrCount) {
                                val attrPos = attrsBase + i * attrSize
                                if (attrPos + 20 > outBytes.size) break

                                val attrNameIdx = buf.getInt(attrPos + 4)
                                val dataType = buf.get(attrPos + 15).toInt() and 0xFF
                                val resId = if (attrNameIdx in resourceIds.indices) resourceIds[attrNameIdx] else 0

                                if (dataType == TYPE_INT_DEC || dataType == TYPE_INT_HEX) {
                                    when (resId) {
                                        ATTR_MIN_SDK_VERSION -> buf.putInt(attrPos + 16, newMinSdk)
                                        ATTR_TARGET_SDK_VERSION -> buf.putInt(attrPos + 16, newTargetSdk)
                                        ATTR_COMPILE_SDK_VERSION -> buf.putInt(attrPos + 16, newCompileSdk)
                                        ATTR_VERSION_CODE -> buf.putInt(attrPos + 16, newVersionCode)
                                        ATTR_SCREEN_ORIENTATION -> {
                                            val orientInt = when (orientationMode) {
                                                "LANDSCAPE" -> 0
                                                "PORTRAIT" -> 1
                                                else -> -1
                                            }
                                            buf.putInt(attrPos + 16, orientInt)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            offset += chunkSize
        }

        return outBytes
    }

    /**
     * Deeply inspects a binary `AndroidManifest.xml` (`0x00080003`), decoding its String Pool
     * and XML element attributes to extract the REAL `packageName`, `appName` (`android:label`),
     * `versionName`, `versionCode`, `minSdkVersion`, `targetSdkVersion`, `compileSdkVersion`,
     * and launcher activity directly from the binary APK!
     */
    fun inspectBinaryManifest(axmlBytes: ByteArray): ManifestInspectResult {
        if (axmlBytes.size < 24) {
            return ManifestInspectResult(false, null, null, null)
        }
        val buf = ByteBuffer.wrap(axmlBytes).order(ByteOrder.LITTLE_ENDIAN)
        val fileType = buf.getShort(0).toInt() and 0xFFFF
        val headerSize = buf.getShort(2).toInt() and 0xFFFF
        if (fileType != RES_XML_TYPE || headerSize != 8) {
            return ManifestInspectResult(false, null, null, null)
        }

        val totalSize = buf.getInt(4).coerceAtMost(axmlBytes.size)
        var offset = headerSize
        var stringPool = emptyList<String>()
        var resourceIds = IntArray(0)

        var minSdk: Int? = null
        var targetSdk: Int? = null
        var compileSdk: Int? = null
        var versionCode: Int? = null
        var packageNameFound: String? = null
        var appNameFound: String? = null
        var versionNameFound: String? = null
        var launcherActivityFound: String? = null
        val permissionsFound = mutableListOf<String>()

        while (offset + 8 <= axmlBytes.size && offset < totalSize) {
            val chunkType = buf.getShort(offset).toInt() and 0xFFFF
            val chunkHeaderSize = buf.getShort(offset + 2).toInt() and 0xFFFF
            val chunkSize = buf.getInt(offset + 4)
            if (chunkSize <= 0 || offset + chunkSize > axmlBytes.size) break

            when (chunkType) {
                RES_STRING_POOL_TYPE -> {
                    stringPool = parseStringPoolChunk(buf, offset, chunkSize)
                }
                RES_XML_RESOURCE_MAP_TYPE -> {
                    val count = (chunkSize - chunkHeaderSize) / 4
                    if (count > 0 && offset + chunkHeaderSize + count * 4 <= axmlBytes.size) {
                        resourceIds = IntArray(count)
                        for (i in 0 until count) {
                            resourceIds[i] = buf.getInt(offset + chunkHeaderSize + i * 4)
                        }
                    }
                }
                RES_XML_START_ELEMENT_TYPE -> {
                    if (offset + 36 <= axmlBytes.size) {
                        val elemNameIdx = buf.getInt(offset + 20)
                        val elemName = stringPool.getOrNull(elemNameIdx) ?: ""

                        val attrStart = buf.getShort(offset + 24).toInt() and 0xFFFF
                        val attrSize = buf.getShort(offset + 26).toInt() and 0xFFFF
                        val attrCount = buf.getShort(offset + 28).toInt() and 0xFFFF
                        val attrsBase = offset + 16 + attrStart
                        if (attrSize >= 20) {
                            for (i in 0 until attrCount) {
                                val attrPos = attrsBase + i * attrSize
                                if (attrPos + 20 > axmlBytes.size) break
                                val attrNameIdx = buf.getInt(attrPos + 4)
                                val rawValIdx = buf.getInt(attrPos + 8)
                                val dataType = buf.get(attrPos + 15).toInt() and 0xFF
                                val dataVal = buf.getInt(attrPos + 16)

                                val attrName = stringPool.getOrNull(attrNameIdx) ?: ""
                                val resId = if (attrNameIdx in resourceIds.indices) resourceIds[attrNameIdx] else 0
                                val strVal = when {
                                    rawValIdx in stringPool.indices -> stringPool[rawValIdx]
                                    dataType == TYPE_STRING && dataVal in stringPool.indices -> stringPool[dataVal]
                                    else -> null
                                }

                                if (elemName == "manifest" && (attrName == "package") && !strVal.isNullOrBlank()) {
                                    packageNameFound = strVal
                                }

                                when (resId) {
                                    ATTR_MIN_SDK_VERSION -> minSdk = dataVal
                                    ATTR_TARGET_SDK_VERSION -> targetSdk = dataVal
                                    ATTR_COMPILE_SDK_VERSION -> compileSdk = dataVal
                                    ATTR_VERSION_CODE -> versionCode = dataVal
                                    ATTR_VERSION_NAME -> if (!strVal.isNullOrBlank()) versionNameFound = strVal
                                    ATTR_LABEL -> {
                                        if ((elemName == "application" || elemName == "activity") &&
                                            !strVal.isNullOrBlank() && appNameFound == null
                                        ) {
                                            appNameFound = strVal
                                        }
                                    }
                                    ATTR_NAME -> {
                                        if (elemName == "uses-permission" && !strVal.isNullOrBlank()) {
                                            permissionsFound.add(strVal)
                                        } else if (elemName == "activity" && !strVal.isNullOrBlank() && launcherActivityFound == null) {
                                            launcherActivityFound = strVal
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            offset += chunkSize
        }

        return ManifestInspectResult(
            isBinaryAxml = true,
            minSdkFound = minSdk,
            targetSdkFound = targetSdk,
            versionCodeFound = versionCode,
            packageNameFound = packageNameFound,
            appNameFound = appNameFound,
            versionNameFound = versionNameFound,
            compileSdkFound = compileSdk,
            launcherActivityFound = launcherActivityFound,
            permissionsFound = permissionsFound
        )
    }

    private fun parseStringPoolChunk(buf: ByteBuffer, chunkOffset: Int, chunkSize: Int): List<String> {
        if (chunkSize < 28) return emptyList()
        val stringCount = buf.getInt(chunkOffset + 8)
        if (stringCount <= 0 || stringCount > 10000) return emptyList()
        val flags = buf.getInt(chunkOffset + 16)
        val isUtf8 = (flags and (1 shl 8)) != 0
        val stringsStart = buf.getInt(chunkOffset + 20)
        val poolDataBase = chunkOffset + stringsStart
        val offsetsBase = chunkOffset + 28

        val result = ArrayList<String>(stringCount)
        for (i in 0 until stringCount) {
            val offsetPos = offsetsBase + i * 4
            if (offsetPos + 4 > buf.limit()) break
            val strOffset = buf.getInt(offsetPos)
            val pos = poolDataBase + strOffset
            if (pos < 0 || pos + 2 > chunkOffset + chunkSize) {
                result.add("")
                continue
            }
            try {
                if (isUtf8) {
                    var p = pos
                    val b1 = buf.get(p).toInt() and 0xFF
                    p += if ((b1 and 0x80) != 0) 2 else 1
                    val b2 = buf.get(p).toInt() and 0xFF
                    val byteLen = if ((b2 and 0x80) != 0) {
                        val b2Low = buf.get(p + 1).toInt() and 0xFF
                        p += 2
                        ((b2 and 0x7F) shl 8) or b2Low
                    } else {
                        p += 1
                        b2
                    }
                    if (p + byteLen <= chunkOffset + chunkSize) {
                        val bytes = ByteArray(byteLen)
                        for (k in 0 until byteLen) bytes[k] = buf.get(p + k)
                        result.add(String(bytes, StandardCharsets.UTF_8))
                    } else {
                        result.add("")
                    }
                } else {
                    val charLen = buf.getShort(pos).toInt() and 0xFFFF
                    val byteLen = charLen * 2
                    val start = pos + 2
                    if (start + byteLen <= chunkOffset + chunkSize) {
                        val bytes = ByteArray(byteLen)
                        for (k in 0 until byteLen) bytes[k] = buf.get(start + k)
                        result.add(String(bytes, StandardCharsets.UTF_16LE))
                    } else {
                        result.add("")
                    }
                }
            } catch (_: Exception) {
                result.add("")
            }
        }
        return result
    }

    /**
     * Synthesizes a valid standalone binary AXML (`0x00080003`) with full package and label metadata.
     */
    fun createMinimalValidBinaryAxml(
        minSdk: Int,
        targetSdk: Int,
        versionCode: Int,
        packageName: String = "com.standalone.generated.app",
        appName: String = "Standalone App",
        versionName: String = "1.0.0"
    ): ByteArray {
        return generateStandaloneBinaryManifest(
            packageName = packageName,
            appName = appName,
            versionCode = versionCode,
            versionName = versionName,
            minSdk = minSdk,
            targetSdk = targetSdk,
            compileSdk = targetSdk
        )
    }

    /**
     * Calculates the exact `extra` byte array needed in a ZIP Local File Header so that an
     * uncompressed (`STORED`) entry's payload starts at a 4-byte aligned offset (`offset % 4 == 0`),
     * matching the `zipalign -f 4` specification required by Android 11+.
     */
    fun computeZipAlignExtraPadding(currentStreamOffset: Long, entryName: String, existingExtraLen: Int = 0): ByteArray {
        val nameBytes = entryName.toByteArray(StandardCharsets.UTF_8)
        val dataStartWithoutPadding = currentStreamOffset + 30L + nameBytes.size
        val remainder = (dataStartWithoutPadding % 4L).toInt()
        val padNeeded = if (remainder == 0) 0 else (4 - remainder)
        return ByteArray(padNeeded)
    }
}
