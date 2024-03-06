package me.uma.protocol

import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.uma.utils.ByteArrayAsHexSerializer
import me.uma.utils.X509CertificateSerializer

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
    @Serializable(with = X509CertificateSerializer::class)
    val signingCertificate: X509Certificate?,
    @Serializable(with = X509CertificateSerializer::class)
    val encryptionCertificate: X509Certificate?,
    @Serializable(with = ByteArrayAsHexSerializer::class)
    private val signingPubKey: ByteArray?,
    @Serializable(with = ByteArrayAsHexSerializer::class)
    private val encryptionPubKey: ByteArray?,
    val expirationTimestamp: Long? = null,
) {
    @JvmOverloads
    constructor(signingKey: ByteArray, encryptionKey: ByteArray, expirationTs: Long? = null) : this(
        signingCertificate = null,
        encryptionCertificate = null,
        signingPubKey = signingKey,
        encryptionPubKey = encryptionKey,
        expirationTimestamp = expirationTs,
    )

    @JvmOverloads
    constructor(signingCert: String, encryptionCert: String, expirationTs: Long? = null) : this(
        signingCertificate = signingCert.toX509Certificate(),
        encryptionCertificate = encryptionCert.toX509Certificate(),
        signingPubKey = signingCert.toX509Certificate().getPubKeyBytes(),
        encryptionPubKey = encryptionCert.toX509Certificate().getPubKeyBytes(),
        expirationTimestamp = expirationTs,
    )

    fun getSigningPubKey(): ByteArray {
        return if (signingCertificate != null) {
            signingCertificate.getPubKeyBytes()
        } else {
            signingPubKey ?: throw IllegalStateException("No signing public key")
        }
    }

    fun getEncryptionPubKey(): ByteArray {
        return if (encryptionCertificate != null) {
            encryptionCertificate.getPubKeyBytes()
        } else {
            encryptionPubKey ?: throw IllegalStateException("No encryption public key")
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

private fun String.toX509Certificate(): X509Certificate {
    return CertificateFactory.getInstance("X.509")
        .generateCertificate(byteInputStream()) as? X509Certificate
        ?: throw IllegalStateException("Could not be parsed as X.509 certificate")
}

private fun X509Certificate.getPubKeyBytes(): ByteArray {
    if (publicKey !is ECPublicKey ||
        !(publicKey as ECPublicKey).params.toString().contains("secp256k1")
    ) {
        throw IllegalStateException("Public key extracted from certificate is not EC/secp256k1")
    }
    // encryptionPubKey.publicKey is an ASN.1/DER encoded X.509/SPKI key, the last 65
    // bytes are the uncompressed public key
    return publicKey.encoded.takeLast(65).toByteArray()
}
