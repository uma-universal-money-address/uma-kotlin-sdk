package me.uma.protocol

import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.uma.crypto.hexToByteArray
import me.uma.utils.ByteArrayAsHexSerializer

/**
 * Response from another VASP when requesting public keys.
 *
 * @property signingCertificate PEM encoded X.509 certificate string.
 *     Used to verify signatures from the VASP.
 * @property encryptionCertificate PEM encoded X.509 certificate string.
 *     Used to encrypt TR info sent to the VASP.
 * @property signingPubKey The public key used to verify signatures from the VASP.
 * @property encryptionPubKey The public key used to encrypt TR info sent to the VASP.
 * @property expirationTimestamp Seconds since epoch at which these pub keys must be refreshed.
 *     They can be safely cached until this expiration (or forever if null).
 */
@Serializable
data class PubKeyResponse internal constructor(
    private val signingCertificate: String? = null,
    private val encryptionCertificate: String? = null,
    @Serializable(with = ByteArrayAsHexSerializer::class)
    private val signingPubKey: ByteArray? = null,
    @Serializable(with = ByteArrayAsHexSerializer::class)
    private val encryptionPubKey: ByteArray? = null,
    val expirationTimestamp: Long?,
) {
    @JvmOverloads
    constructor(signingKey: ByteArray, encryptionKey: ByteArray, expirationTs: Long? = null) : this(
        signingPubKey = signingKey,
        encryptionPubKey = encryptionKey,
        expirationTimestamp = expirationTs,
    )

    @JvmOverloads
    constructor(signingCert: String, encryptionCert: String, expirationTs: Long? = null) : this(
        signingCertificate = signingCert,
        encryptionCertificate = encryptionCert,
        expirationTimestamp = expirationTs,
    )

    fun getSigningPubKey(): ByteArray {
        val signingCertificate = getSigningCertificate()
        return if (signingCertificate != null) {
            (signingCertificate.publicKey as ECPublicKey).toHexByteArray()
        } else {
            signingPubKey ?: throw IllegalStateException("No signing public key")
        }
    }

    fun getEncryptionPubKey(): ByteArray {
        val encryptionCertificate = getEncryptionCertificate()
        return if (encryptionCertificate != null) {
            (encryptionCertificate.publicKey as ECPublicKey).toHexByteArray()
        } else {
            encryptionPubKey ?: throw IllegalStateException("No encryption public key")
        }
    }

    fun getSigningCertificate(): X509Certificate? {
        return signingCertificate?.let {
            CertificateFactory.getInstance("X509")
                .generateCertificate(signingCertificate.byteInputStream()) as X509Certificate
        }
    }

    fun getEncryptionCertificate(): X509Certificate? {
        return encryptionCertificate?.let {
            CertificateFactory.getInstance("X509")
                .generateCertificate(encryptionCertificate.byteInputStream()) as X509Certificate
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PubKeyResponse

        if (!signingPubKey.contentEquals(other.signingPubKey)) return false
        if (!encryptionPubKey.contentEquals(other.encryptionPubKey)) return false
        if (expirationTimestamp != other.expirationTimestamp) return false
        if (signingCertificate != other.signingCertificate) return false
        if (encryptionCertificate != other.encryptionCertificate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signingPubKey.contentHashCode()
        result = 31 * result + encryptionPubKey.contentHashCode()
        result = 31 * result + expirationTimestamp.hashCode()
        result = 31 * result + signingCertificate.hashCode()
        result = 31 * result + encryptionCertificate.hashCode()
        return result
    }

    fun toJson() = Json.encodeToString(this)
}
fun ECPublicKey.toHexByteArray(): ByteArray {
    val xHex = this.w.affineX.toString(16)
    val yHex = this.w.affineY.toString(16)
    return "04$xHex$yHex".hexToByteArray()
}
