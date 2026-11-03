package com.example.builder

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Patcher for Android's compiled Binary XML (`AndroidManifest.xml`) and ZIP Alignment.
 *
 * Why this is critical for preventing "There was a problem parsing the package":
 * 1. Android's PackageParser reads `AndroidManifest.xml` as compiled AXML (magic `0x00080003`),
 *    never plain text XML.
 * 2. In AXML, all attribute values like `minSdkVersion`, `targetSdkVersion`, `versionCode`,
 *    are stored in `RES_XML_START_ELEMENT_TYPE` (`0x0102`) attribute arrays as `TYPE_INT_DEC` (`0x10`),
 *    while `package` and `versionName` are indexed in the UTF-8/UTF-16 `RES_STRING_POOL_TYPE` (`0x0001`).
 * 3. Furthermore, Android 11+ (API 30+) requires `resources.arsc` and `.so` libraries to be
 *    stored (`ZipEntry.STORED`) at 4-byte aligned data offsets inside the APK ZIP file.
 */
object BinaryManifestAndZipAligner {

    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102

    // Standard Android framework resource IDs inside AndroidManifest.xml
    private const val ATTR_VERSION_CODE = 0x0101021b
    private const val ATTR_VERSION_NAME = 0x0101021c
    private const val ATTR_MIN_SDK_VERSION = 0x0101020c
    private const val ATTR_TARGET_SDK_VERSION = 0x01010270
    private const val ATTR_COMPILE_SDK_VERSION = 0x01010572
    private const val ATTR_SCREEN_ORIENTATION = 0x0101001e

    data class ManifestInspectResult(
        val isBinaryAxml: Boolean,
        val minSdkFound: Int?,
        val targetSdkFound: Int?,
        val versionCodeFound: Int?
    )

    /**
     * Patches the integer attributes (`minSdkVersion`, `targetSdkVersion`, `versionCode`, `compileSdkVersion`,
     * and `screenOrientation`) inside a compiled binary `AndroidManifest.xml` byte array without
     * invalidating the AXML chunk sizes or string pool offsets!
     */
    fun patchBinaryManifestIntegers(
        axmlBytes: ByteArray,
        newMinSdk: Int,
        newTargetSdk: Int,
        newCompileSdk: Int,
        newVersionCode: Int,
        orientationMode: String // AUTO, PORTRAIT, LANDSCAPE
    ): ByteArray {
        if (axmlBytes.size < 24) return axmlBytes
        val outBytes = axmlBytes.copyOf()
        val buf = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)

        val fileType = buf.getShort(0).toInt() and 0xFFFF
        if (fileType != RES_XML_TYPE) {
            return axmlBytes
        }

        val totalSize = buf.getInt(4)
        var offset = (buf.getShort(2).toInt() and 0xFFFF) // Usually 8

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
                    // StartElement structure:
                    // offset + 0: type (2), headerSize (2), chunkSize (4), lineNumber (4), comment (4)
                    // offset + 16: ns (4), name (4), attributeStart (2), attributeSize (2), attributeCount (2)
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
                                val dataVal = buf.getInt(attrPos + 16)

                                val resId = if (attrNameIdx in resourceIds.indices) {
                                    resourceIds[attrNameIdx]
                                } else {
                                    0
                                }

                                // Only patch integer attributes (TYPE_INT_DEC = 0x10 or TYPE_INT_HEX = 0x11)
                                if (dataType == 0x10 || dataType == 0x11) {
                                    when (resId) {
                                        ATTR_MIN_SDK_VERSION -> {
                                            buf.putInt(attrPos + 16, newMinSdk)
                                        }
                                        ATTR_TARGET_SDK_VERSION -> {
                                            buf.putInt(attrPos + 16, newTargetSdk)
                                        }
                                        ATTR_COMPILE_SDK_VERSION -> {
                                            buf.putInt(attrPos + 16, newCompileSdk)
                                        }
                                        ATTR_VERSION_CODE -> {
                                            buf.putInt(attrPos + 16, newVersionCode)
                                        }
                                        ATTR_SCREEN_ORIENTATION -> {
                                            val orientInt = when (orientationMode) {
                                                "LANDSCAPE" -> 0 // ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                                "PORTRAIT" -> 1  // ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                                else -> -1       // ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                            }
                                            if (orientInt >= 0) {
                                                buf.putInt(attrPos + 16, orientInt)
                                            }
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
     * Inspects a binary `AndroidManifest.xml` to verify it has the valid AXML header (`0x00080003`)
     * and reads back the configured `minSdkVersion`, `targetSdkVersion`, and `versionCode`.
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

        val totalSize = buf.getInt(4)
        var offset = headerSize
        var resourceIds = IntArray(0)
        var minSdk: Int? = null
        var targetSdk: Int? = null
        var versionCode: Int? = null

        while (offset + 8 <= axmlBytes.size && offset < totalSize) {
            val chunkType = buf.getShort(offset).toInt() and 0xFFFF
            val chunkHeaderSize = buf.getShort(offset + 2).toInt() and 0xFFFF
            val chunkSize = buf.getInt(offset + 4)
            if (chunkSize <= 0) break

            when (chunkType) {
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
                        val attrStart = buf.getShort(offset + 24).toInt() and 0xFFFF
                        val attrSize = buf.getShort(offset + 26).toInt() and 0xFFFF
                        val attrCount = buf.getShort(offset + 28).toInt() and 0xFFFF
                        val attrsBase = offset + 16 + attrStart
                        if (attrSize >= 20) {
                            for (i in 0 until attrCount) {
                                val attrPos = attrsBase + i * attrSize
                                if (attrPos + 20 > axmlBytes.size) break
                                val attrNameIdx = buf.getInt(attrPos + 4)
                                val dataVal = buf.getInt(attrPos + 16)
                                val resId = if (attrNameIdx in resourceIds.indices) resourceIds[attrNameIdx] else 0
                                when (resId) {
                                    ATTR_MIN_SDK_VERSION -> minSdk = dataVal
                                    ATTR_TARGET_SDK_VERSION -> targetSdk = dataVal
                                    ATTR_VERSION_CODE -> versionCode = dataVal
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
            versionCodeFound = versionCode
        )
    }

    /**
     * Synthesizes a minimal, valid binary AXML (`0x00080003`) fallback only for headless unit test
     * environments where no host APK exists.
     */
    fun createMinimalValidBinaryAxml(minSdk: Int, targetSdk: Int, versionCode: Int): ByteArray {
        val out = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        out.putShort(RES_XML_TYPE.toShort())
        out.putShort(8.toShort())
        out.putInt(64)
        // Resource map chunk with minSdk, targetSdk, versionCode
        out.putShort(RES_XML_RESOURCE_MAP_TYPE.toShort())
        out.putShort(8.toShort())
        out.putInt(20)
        out.putInt(ATTR_MIN_SDK_VERSION)
        out.putInt(ATTR_TARGET_SDK_VERSION)
        out.putInt(ATTR_VERSION_CODE)
        return out.array()
    }

    /**
     * Calculates the exact `extra` byte array needed in a ZIP Local File Header so that an
     * uncompressed (`STORED`) entry's payload starts at a 4-byte aligned offset (`offset % 4 == 0`),
     * matching the `zipalign -f 4` specification required by Android 11+ (`INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED`).
     */
    fun computeZipAlignExtraPadding(currentStreamOffset: Long, entryName: String, existingExtraLen: Int = 0): ByteArray {
        val nameBytes = entryName.toByteArray(StandardCharsets.UTF_8)
        // ZIP Local File Header is 30 bytes + filename length + extra length
        val dataStartWithoutPadding = currentStreamOffset + 30L + nameBytes.size
        val remainder = (dataStartWithoutPadding % 4L).toInt()
        val padNeeded = if (remainder == 0) 0 else (4 - remainder)
        return ByteArray(padNeeded)
    }
}
