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
 * @property signingCertChain list of X.509 certificates. The order of the certificates is from the
 *     leaf to the root. Used to verify signatures from the VASP.
 * @property encryptionCertChain list of X.509 certificates. The order of the certificates is from the
 *     leaf to the root. Used to encrypt TR info sent to the VASP.
 * @property signingPubKey The public key used to verify signatures from the VASP.
 * @property encryptionPubKey The public key used to encrypt TR info sent to the VASP.
 * @property expirationTimestamp Seconds since epoch at which these pub keys must be refreshed.
 *     They can be safely cached until this expiration (or forever if null).
 */
@Serializable
data class PubKeyResponse internal constructor(
    val signingCertChain: List<@Serializable(with = X509CertificateSerializer::class) X509Certificate>?,
    val encryptionCertChain: List<@Serializable(with = X509CertificateSerializer::class) X509Certificate>?,
    @Serializable(with = ByteArrayAsHexSerializer::class)
    private val signingPubKey: ByteArray?,
    @Serializable(with = ByteArrayAsHexSerializer::class)
    private val encryptionPubKey: ByteArray?,
    val expirationTimestamp: Long? = null,
) {
    @JvmOverloads
    constructor(signingKey: ByteArray, encryptionKey: ByteArray, expirationTs: Long? = null) : this(
        signingCertChain = null,
        encryptionCertChain = null,
        signingPubKey = signingKey,
        encryptionPubKey = encryptionKey,
        expirationTimestamp = expirationTs,
    )

    @JvmOverloads
    constructor(signingCertChain: String, encryptionCertChain: String, expirationTs: Long? = null) : this(
        signingCertChain = signingCertChain.toX509CertChain(),
        encryptionCertChain = encryptionCertChain.toX509CertChain(),
        signingPubKey = signingCertChain.toX509CertChain().getPubKeyBytes(),
        encryptionPubKey = encryptionCertChain.toX509CertChain().getPubKeyBytes(),
        expirationTimestamp = expirationTs,
    )

    fun getSigningPublicKey(): ByteArray {
        return if (signingCertChain != null) {
            signingCertChain.getPubKeyBytes()
        } else {
            signingPubKey ?: throw IllegalStateException("No signing public key")
        }
    }

    fun getEncryptionPublicKey(): ByteArray {
        return if (encryptionCertChain != null) {
            encryptionCertChain.getPubKeyBytes()
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
        if (signingCertChain != other.signingCertChain) return false
        if (encryptionCertChain != other.encryptionCertChain) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signingPubKey.contentHashCode()
        result = 31 * result + encryptionPubKey.contentHashCode()
        result = 31 * result + expirationTimestamp.hashCode()
        result = 31 * result + signingCertChain.hashCode()
        result = 31 * result + encryptionCertChain.hashCode()
        return result
    }

    fun toJson() = Json.encodeToString(this)
}

private fun String.toX509CertChain(): List<X509Certificate> {
    return CertificateFactory.getInstance("X.509")
        .generateCertificates(byteInputStream())
        .map {
            it as? X509Certificate
                ?: throw IllegalStateException("Could not be parsed as X.509 certificate")
        }
}

private fun List<X509Certificate>.getPubKeyBytes(): ByteArray {
    val publicKey = firstOrNull()?.publicKey
        ?: throw IllegalStateException("Certificate chain is empty")
    if (publicKey !is ECPublicKey || !publicKey.params.toString().contains("secp256k1")) {
        throw IllegalStateException("Public key extracted from certificate is not EC/secp256k1")
    }
    // encryptionPubKey.publicKey is an ASN.1/DER encoded X.509/SPKI key, the last 65
    // bytes are the uncompressed public key
    return publicKey.encoded.takeLast(65).toByteArray()
}
