package com.example.builder

import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

/**
 * Real Cryptographic APK Signer (JAR/V1 Signature + Project Keystore Manager).
 *
 * Generates and persists RSA-2048 keys per project (or shared Debug Keystore), constructs a real
 * X.509 v3 DER certificate, computes SHA-256 digests for every entry in the APK ZIP archive,
 * generates `META-INF/MANIFEST.MF` and `META-INF/CERT.SF` (or `ANDROIDD.SF`), signs `CERT.SF`
 * using `SHA256withRSA`, and wraps the signature and certificate in a valid PKCS#7 / CMS
 * `SignedData` DER block (`META-INF/CERT.RSA`).
 *
 * This guarantees that Android's PackageParser / PackageInstaller validates the APK's
 * cryptographic signature after assets are injected or modified.
 */
object RealApkSignerEngine {

    data class SigningCredentials(
        val alias: String,
        val subjectDn: String,
        val privateKey: PrivateKey,
        val publicKey: PublicKey,
        val x509CertificateDer: ByteArray,
        val certSha256Fingerprint: String,
        val keystoreFilePath: String,
        val isReleaseKey: Boolean
    )

    /**
     * Loads or creates either the standard Android Debug Keystore credentials or a dedicated,
     * persistent Project Release Keystore stored safely inside `filesDir/keystores/`.
     */
    fun getOrCreateSigningCredentials(
        contextFilesDir: File,
        packageName: String,
        developerName: String,
        isReleaseMode: Boolean
    ): SigningCredentials {
        val keystoresDir = File(contextFilesDir, "keystores").apply { mkdirs() }
        val sanitizedPkg = packageName.replace(Regex("[^a-zA-Z0-9_.]"), "_").ifBlank { "default_app" }

        val keyFile = if (isReleaseMode) {
            File(keystoresDir, "${sanitizedPkg}_release_keystore.json")
        } else {
            File(keystoresDir, "${sanitizedPkg}_debug_keystore.json")
        }

        val alias = if (isReleaseMode) "project_release_key" else "project_debug_key"
        val cleanDev = developerName.replace(Regex("[^a-zA-Z0-9 ]"), "").trim().ifBlank { "Independent Developer" }
        val subjectCommonName = if (isReleaseMode) cleanDev else "$cleanDev Debug"

        if (keyFile.exists()) {
            try {
                val json = JSONObject(keyFile.readText())
                val privBytes = Base64.getDecoder().decode(json.getString("privateKeyPkcs8"))
                val pubBytes = Base64.getDecoder().decode(json.getString("publicKeyX509"))
                val certBytes = Base64.getDecoder().decode(json.getString("certificateDer"))
                val kf = KeyFactory.getInstance("RSA")
                val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val pubKey = kf.generatePublic(X509EncodedKeySpec(pubBytes))
                val sha256Hex = sha256ColonHex(certBytes)
                return SigningCredentials(
                    alias = alias,
                    subjectDn = json.optString("subjectDn", "CN=$subjectCommonName,O=$cleanDev,C=US"),
                    privateKey = privKey,
                    publicKey = pubKey,
                    x509CertificateDer = certBytes,
                    certSha256Fingerprint = sha256Hex,
                    keystoreFilePath = keyFile.absolutePath,
                    isReleaseKey = isReleaseMode
                )
            } catch (_: Exception) {
                // Regenerate if corrupted
            }
        }

        // Generate fresh 2048-bit RSA KeyPair and self-signed X.509 Certificate DER
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val keyPair: KeyPair = kpg.generateKeyPair()

        val serialNumber = BigInteger(64, SecureRandom()).abs().add(BigInteger.ONE)
        val certDer = generateSelfSignedX509CertificateDer(
            keyPair = keyPair,
            commonName = subjectCommonName,
            organization = cleanDev,
            country = "US",
            serial = serialNumber
        )

        val subjectDn = "CN=$subjectCommonName,O=$cleanDev,C=US"
        val jsonToSave = JSONObject().apply {
            put("alias", alias)
            put("packageName", packageName)
            put("subjectDn", subjectDn)
            put("createdAt", System.currentTimeMillis())
            put("isReleaseKey", isReleaseMode)
            put("privateKeyPkcs8", Base64.getEncoder().encodeToString(keyPair.private.encoded))
            put("publicKeyX509", Base64.getEncoder().encodeToString(keyPair.public.encoded))
            put("certificateDer", Base64.getEncoder().encodeToString(certDer))
        }
        keyFile.writeText(jsonToSave.toString(2))

        return SigningCredentials(
            alias = alias,
            subjectDn = subjectDn,
            privateKey = keyPair.private,
            publicKey = keyPair.public,
            x509CertificateDer = certDer,
            certSha256Fingerprint = sha256ColonHex(certDer),
            keystoreFilePath = keyFile.absolutePath,
            isReleaseKey = isReleaseMode
        )
    }

    data class SignatureBlockFiles(
        val manifestMfBytes: ByteArray,
        val certSfBytes: ByteArray,
        val certRsaBytes: ByteArray,
        val sfEntryName: String,
        val rsaEntryName: String
    )

    // Generates META-INF/MANIFEST.MF, META-INF/CERT.SF (or ANDROIDD.SF), and META-INF/CERT.RSA
    fun generateApkSignatureFiles(
        entrySha256DigestsBase64: Map<String, String>,
        credentials: SigningCredentials
    ): SignatureBlockFiles {
        val sbManifest = StringBuilder()
        sbManifest.append("Manifest-Version: 1.0\r\n")
        sbManifest.append("Created-By: 1.0 (Android Standalone APK Packager)\r\n\r\n")

        val individualSections = mutableMapOf<String, ByteArray>()
        for ((entryName, base64Digest) in entrySha256DigestsBase64.entries.sortedBy { it.key }) {
            val section = "Name: $entryName\r\nSHA-256-Digest: $base64Digest\r\n\r\n"
            sbManifest.append(section)
            individualSections[entryName] = section.toByteArray(Charsets.UTF_8)
        }

        val manifestMfBytes = sbManifest.toString().toByteArray(Charsets.UTF_8)
        val fullManifestDigestBase64 = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(manifestMfBytes)
        )

        val sbSf = StringBuilder()
        sbSf.append("Signature-Version: 1.0\r\n")
        sbSf.append("Created-By: 1.0 (Android Standalone APK Packager)\r\n")
        sbSf.append("SHA-256-Digest-Manifest: $fullManifestDigestBase64\r\n\r\n")

        for ((entryName, sectionBytes) in individualSections) {
            val sectionDigest = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(sectionBytes)
            )
            sbSf.append("Name: $entryName\r\nSHA-256-Digest: $sectionDigest\r\n\r\n")
        }

        val certSfBytes = sbSf.toString().toByteArray(Charsets.UTF_8)

        // Sign CERT.SF using SHA256withRSA
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(credentials.privateKey)
        sig.update(certSfBytes)
        val rsaSignatureBytes = sig.sign()

        // Wrap X.509 Certificate + RSA Signature in a PKCS#7 / CMS SignedData DER block
        val certRsaBytes = buildPkcs7SignedDataDer(
            certificateDer = credentials.x509CertificateDer,
            signatureBytes = rsaSignatureBytes
        )

        val prefix = if (credentials.isReleaseKey) "CERT" else "ANDROIDD"
        return SignatureBlockFiles(
            manifestMfBytes = manifestMfBytes,
            certSfBytes = certSfBytes,
            certRsaBytes = certRsaBytes,
            sfEntryName = "META-INF/$prefix.SF",
            rsaEntryName = "META-INF/$prefix.RSA"
        )
    }

    private fun sha256ColonHex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    // =========================================================================
    // Pure ASN.1 DER X.509 v3 Certificate & PKCS#7 CMS Builder (Zero external deps)
    // =========================================================================

    private val OID_SHA256_WITH_RSA = byteArrayOf(
        0x06, 0x09, 0x2A.toByte(), 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B
    )
    private val OID_RSA_ENCRYPTION = byteArrayOf(
        0x06, 0x09, 0x2A.toByte(), 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01
    )
    private val OID_SHA256 = byteArrayOf(
        0x06, 0x09, 0x60, 0x86.toByte(), 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01
    )
    private val OID_PKCS7_DATA = byteArrayOf(
        0x06, 0x09, 0x2A.toByte(), 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x01
    )
    private val OID_PKCS7_SIGNED_DATA = byteArrayOf(
        0x06, 0x09, 0x2A.toByte(), 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x02
    )
    private val ASN1_NULL = byteArrayOf(0x05, 0x00)

    private fun generateSelfSignedX509CertificateDer(
        keyPair: KeyPair,
        commonName: String,
        organization: String,
        country: String,
        serial: BigInteger
    ): ByteArray {
        val versionExplicit = derTag(0xA0, derInteger(BigInteger.valueOf(2))) // v3 = 2
        val serialDer = derInteger(serial)
        val sigAlgDer = derSequence(OID_SHA256_WITH_RSA, ASN1_NULL)
        val nameDer = buildX500NameDer(commonName, organization, country)

        val utcFmt = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val notBefore = Date(System.currentTimeMillis() - 86400_000L)
        val notAfter = Date(System.currentTimeMillis() + 10000L * 86400_000L) // 10,000 days validity
        val validityDer = derSequence(
            derUtcTime(utcFmt.format(notBefore)),
            derUtcTime(utcFmt.format(notAfter))
        )

        val subjectPubKeyInfo = keyPair.public.encoded // Already X.509 SubjectPublicKeyInfo DER

        val tbsCertificate = derSequence(
            versionExplicit,
            serialDer,
            sigAlgDer,
            nameDer,
            validityDer,
            nameDer,
            subjectPubKeyInfo
        )

        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(tbsCertificate)
        val signatureValue = signer.sign()

        return derSequence(
            tbsCertificate,
            sigAlgDer,
            derBitString(signatureValue)
        )
    }

    // Builds a valid PKCS#7 / CMS ContentInfo containing SignedData for META-INF/CERT.RSA
    private fun buildPkcs7SignedDataDer(
        certificateDer: ByteArray,
        signatureBytes: ByteArray
    ): ByteArray {
        // Extract issuer and serialNumber from certificateDer TBSCertificate
        // For our generated v3 certificate, we parse or reconstruct IssuerAndSerialNumber
        val certInfo = extractIssuerAndSerialFromCert(certificateDer)

        val version1 = derInteger(BigInteger.ONE)
        val digestAlgorithms = derSet(derSequence(OID_SHA256, ASN1_NULL))
        val contentInfoData = derSequence(OID_PKCS7_DATA)
        val certificatesImplicit = derTag(0xA0, certificateDer)

        val signerInfo = derSequence(
            version1,
            derSequence(certInfo.issuerDer, certInfo.serialDer),
            derSequence(OID_SHA256, ASN1_NULL),
            derSequence(OID_RSA_ENCRYPTION, ASN1_NULL),
            derOctetString(signatureBytes)
        )
        val signerInfos = derSet(signerInfo)

        val signedData = derSequence(
            version1,
            digestAlgorithms,
            contentInfoData,
            certificatesImplicit,
            signerInfos
        )

        return derSequence(
            OID_PKCS7_SIGNED_DATA,
            derTag(0xA0, signedData)
        )
    }

    private data class CertIssuerSerial(val issuerDer: ByteArray, val serialDer: ByteArray)

    private fun extractIssuerAndSerialFromCert(certDer: ByteArray): CertIssuerSerial {
        // Default fallback if parsing fails
        val fallbackName = buildX500NameDer("Android Debug", "Android", "US")
        val fallbackSerial = derInteger(BigInteger.ONE)
        return try {
            // Skip outer SEQUENCE header
            var pos = skipTagAndLength(certDer, 0)
            // Inside TBSCertificate SEQUENCE
            pos = skipTagAndLength(certDer, pos)
            // Check optional [0] version tag (0xA0)
            if ((certDer[pos].toInt() and 0xFF) == 0xA0) {
                pos = skipElement(certDer, pos)
            }
            // Next element is serialNumber (INTEGER)
            val serialEnd = skipElement(certDer, pos)
            val serialDer = certDer.copyOfRange(pos, serialEnd)
            pos = serialEnd
            // Next element is signature AlgorithmIdentifier (SEQUENCE)
            pos = skipElement(certDer, pos)
            // Next element is issuer Name (SEQUENCE)
            val issuerEnd = skipElement(certDer, pos)
            val issuerDer = certDer.copyOfRange(pos, issuerEnd)
            CertIssuerSerial(issuerDer, serialDer)
        } catch (_: Exception) {
            CertIssuerSerial(fallbackName, fallbackSerial)
        }
    }

    private fun skipTagAndLength(bytes: ByteArray, offset: Int): Int {
        var p = offset + 1
        val lenByte = bytes[p].toInt() and 0xFF
        p++
        if ((lenByte and 0x80) != 0) {
            val numOctets = lenByte and 0x7F
            p += numOctets
        }
        return p
    }

    private fun skipElement(bytes: ByteArray, offset: Int): Int {
        var p = offset + 1
        val lenByte = bytes[p].toInt() and 0xFF
        p++
        var length = 0
        if ((lenByte and 0x80) == 0) {
            length = lenByte
        } else {
            val numOctets = lenByte and 0x7F
            for (i in 0 until numOctets) {
                length = (length shl 8) or (bytes[p].toInt() and 0xFF)
                p++
            }
        }
        return p + length
    }

    private fun buildX500NameDer(cn: String, o: String, c: String): ByteArray {
        val oidC = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x06)
        val oidO = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x0A)
        val oidCn = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03)

        val rdnC = derSet(derSequence(oidC, derPrintableString(c)))
        val rdnO = derSet(derSequence(oidO, derUtf8String(o)))
        val rdnCn = derSet(derSequence(oidCn, derUtf8String(cn)))
        return derSequence(rdnC, rdnO, rdnCn)
    }

    private fun derSequence(vararg items: ByteArray): ByteArray = derTag(0x30, concat(*items))
    private fun derSet(vararg items: ByteArray): ByteArray = derTag(0x31, concat(*items))
    private fun derOctetString(bytes: ByteArray): ByteArray = derTag(0x04, bytes)
    private fun derUtf8String(str: String): ByteArray = derTag(0x0C, str.toByteArray(Charsets.UTF_8))
    private fun derPrintableString(str: String): ByteArray = derTag(0x13, str.toByteArray(Charsets.US_ASCII))
    private fun derUtcTime(str: String): ByteArray = derTag(0x17, str.toByteArray(Charsets.US_ASCII))
    private fun derInteger(value: BigInteger): ByteArray = derTag(0x02, value.toByteArray())

    private fun derBitString(bytes: ByteArray): ByteArray {
        val content = ByteArray(bytes.size + 1)
        content[0] = 0x00 // 0 unused bits
        System.arraycopy(bytes, 0, content, 1, bytes.size)
        return derTag(0x03, content)
    }

    private fun derTag(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val len = content.size
        if (len < 128) {
            out.write(len)
        } else {
            val lenBytes = ByteArrayOutputStream()
            var tmp = len
            while (tmp > 0) {
                lenBytes.write(tmp and 0xFF)
                tmp = tmp ushr 8
            }
            val rev = lenBytes.toByteArray().reversedArray()
            out.write(0x80 or rev.size)
            out.write(rev)
        }
        out.write(content)
        return out.toByteArray()
    }

    private fun concat(vararg arrays: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (a in arrays) out.write(a)
        return out.toByteArray()
    }
}
