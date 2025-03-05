@file:OptIn(ExperimentalContracts::class)

package me.uma.protocol

import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.decodeURLQueryComponent
import me.uma.UmaException
import me.uma.UnsupportedVersionException
import me.uma.crypto.Secp256k1
import me.uma.generated.ErrorCode
import me.uma.isVersionSupported
import me.uma.utils.isDomainLocalhost
import kotlin.contracts.ExperimentalContracts

/**
 * The first request in the UMA/LNURL protocol.
 *
 * @param receiverAddress The address of the user at VASP2 that is receiving the payment.
 * @param nonce A random string that is used to prevent replay attacks.
 * @param signature The base64-encoded signature of sha256(ReceiverAddress|Nonce|Timestamp).
 * @param isSubjectToTravelRule Indicates whether the VASP1 is a financial institution that requires travel rule information.
 * @param vaspDomain The domain of the VASP that is sending the payment. It will be used by VASP2 to fetch the public keys of VASP1.
 * @param timestamp The unix timestamp in seconds of the moment when the request was sent. Used in the signature.
 * @param umaVersion  The version of the UMA protocol that VASP1 prefers to use for this transaction. For the version
 *     negotiation flow, see https://static.swimlanes.io/87f5d188e080cb8e0494e46f80f2ae74.png
 * @param backingSignatures A list of backing signatures from VASPs that can attest to the authenticity of the message.
 */
data class LnurlpRequest(
    val receiverAddress: String,
    val nonce: String?,
    val signature: String?,
    val isSubjectToTravelRule: Boolean?,
    val vaspDomain: String?,
    val timestamp: Long?,
    val umaVersion: String?,
    val backingSignatures: List<BackingSignature>? = null,
) {
    /**
     * Encodes the request to a URL that can be used to send the request to VASP2.
     * @throws UmaException if the receiverAddress is not in the format of "$user@domain.com".
     */
    fun encodeToUrl(): String {
        val receiverAddressParts = receiverAddress.split("@")
        if (receiverAddressParts.size != 2) {
            throw UmaException("Invalid receiverAddress: $receiverAddress", ErrorCode.INVALID_INPUT)
        }
        val scheme = if (isDomainLocalhost(receiverAddressParts[1])) URLProtocol.HTTP else URLProtocol.HTTPS
        val url =
            URLBuilder(
                protocol = scheme,
                host = receiverAddressParts[1],
                pathSegments = "/.well-known/lnurlp/${receiverAddressParts[0]}".split("/"),
                parameters =
                    Parameters.build {
                        vaspDomain?.let { append("vaspDomain", it) }
                        nonce?.let { append("nonce", it) }
                        signature?.let { append("signature", it) }
                        umaVersion?.let { append("umaVersion", it) }
                        timestamp?.let { append("timestamp", it.toString()) }
                        isSubjectToTravelRule?.let { append("isSubjectToTravelRule", it.toString()) }
                        backingSignatures?.let { signatures ->
                            append(
                                "backingSignatures",
                                signatures.joinToString(",") { "${it.domain}:${it.signature}" }
                            )
                        }
                    },
            ).build()
        return url.toString()
    }

    /**
     * Converts the request to a [UmaLnurlpRequest] if all the required fields are present. Otherwise, returns null.
     */
    fun asUmaRequest(): UmaLnurlpRequest? {
        return if (
            nonce != null &&
            signature != null &&
            vaspDomain != null &&
            timestamp != null &&
            umaVersion != null
        ) {
            UmaLnurlpRequest(
                receiverAddress,
                nonce,
                signature,
                isSubjectToTravelRule ?: false,
                vaspDomain,
                timestamp,
                umaVersion,
                backingSignatures,
            )
        } else {
            null
        }
    }

    companion object {
        /**
         * Parses a [LnurlpRequest] from a URL.
         * @throws UmaException if the request is invalid.
         * @throws UnsupportedVersionException if the UMA version is not supported.
         */
        fun decodeFromUrl(url: String): LnurlpRequest {
            val urlBuilder = URLBuilder(url)
            if (urlBuilder.protocol != URLProtocol.HTTP && urlBuilder.protocol != URLProtocol.HTTPS) {
                throw UmaException("Invalid URL schema: $url", ErrorCode.PARSE_LNURLP_REQUEST_ERROR)
            }
            if (urlBuilder.pathSegments.size != 4 ||
                urlBuilder.pathSegments[1] != ".well-known" ||
                urlBuilder.pathSegments[2] != "lnurlp"
            ) {
                throw UmaException("Invalid uma request path: $url", ErrorCode.PARSE_LNURLP_REQUEST_ERROR)
            }
            val port =
                if (urlBuilder.port != 443 && urlBuilder.port != 80 && urlBuilder.port != 0) {
                    ":${urlBuilder.port}"
                } else {
                    ""
                }
            val username = urlBuilder.pathSegments[3]
            val usernameRegex = "^[A-Za-z0-9._$+-]+$".toRegex()
            if (!username.matches(usernameRegex)) {
                throw UmaException(
                    "Invalid username. Only alphanumeric characters and ._$+- are allowed.",
                    ErrorCode.PARSE_LNURLP_REQUEST_ERROR
                )
            }
            val receiverAddress = "${urlBuilder.pathSegments[3]}@${urlBuilder.host}$port"
            val vaspDomain = urlBuilder.parameters["vaspDomain"]
            val nonce = urlBuilder.parameters["nonce"]
            val signature = urlBuilder.parameters["signature"]
            val isSubjectToTravelRule = urlBuilder.parameters["isSubjectToTravelRule"]?.toBoolean()
            val timestamp = urlBuilder.parameters["timestamp"]?.toLong()
            val umaVersion = urlBuilder.parameters["umaVersion"]
            val backingSignatures = urlBuilder.parameters["backingSignatures"]?.let { serialized ->
                serialized.split(",").map { pair ->
                    val decodedPair = pair.decodeURLQueryComponent()
                    val lastColonIndex = decodedPair.lastIndexOf(':')
                    if (lastColonIndex == -1) {
                        throw UmaException("Invalid backing signature format", ErrorCode.PARSE_LNURLP_REQUEST_ERROR)
                    }
                    BackingSignature(
                        domain = decodedPair.substring(0, lastColonIndex),
                        signature = decodedPair.substring(lastColonIndex + 1)
                    )
                }
            }

            if (umaVersion != null && !isVersionSupported(umaVersion)) {
                throw UnsupportedVersionException(umaVersion)
            }

            return LnurlpRequest(
                receiverAddress,
                nonce,
                signature,
                isSubjectToTravelRule,
                vaspDomain,
                timestamp,
                umaVersion,
                backingSignatures,
            )
        }
    }
}

/**
 * The first request in the UMA protocol. This is a version of [LnurlpRequest] that explicitly requires all UMA fields
 * to be present for convenience. You can convert a [LnurlpRequest] to a [UmaLnurlpRequest] using
 * [LnurlpRequest.asUmaRequest].
 *
 * @param receiverAddress The address of the user at VASP2 that is receiving the payment.
 * @param nonce A random string that is used to prevent replay attacks.
 * @param signature The base64-encoded signature of sha256(ReceiverAddress|Nonce|Timestamp).
 * @param isSubjectToTravelRule Indicates whether the VASP1 is a financial institution that requires travel rule information.
 * @param vaspDomain The domain of the VASP that is sending the payment. It will be used by VASP2 to fetch the public keys of VASP1.
 * @param timestamp The unix timestamp in seconds of the moment when the request was sent. Used in the signature.
 * @param umaVersion  The version of the UMA protocol that VASP1 prefers to use for this transaction. For the version
 *     negotiation flow, see https://static.swimlanes.io/87f5d188e080cb8e0494e46f80f2ae74.png
 * @param backingSignatures A list of backing signatures from VASPs that can attest to the authenticity of the message,
 *     along with their associated domains.
 */
data class UmaLnurlpRequest(
    val receiverAddress: String,
    val nonce: String,
    val signature: String,
    val isSubjectToTravelRule: Boolean,
    val vaspDomain: String,
    val timestamp: Long,
    val umaVersion: String,
    val backingSignatures: List<BackingSignature>?,
) {
    fun asLnurlpRequest() = LnurlpRequest(
        receiverAddress,
        nonce,
        signature,
        isSubjectToTravelRule,
        vaspDomain,
        timestamp,
        umaVersion,
        backingSignatures,
    )

    /**
     * Encodes the request to a URL that can be used to send the request to VASP2.
     */
    fun encodeToUrl() = asLnurlpRequest().encodeToUrl()

    fun signedWith(signature: String) = copy(signature = signature)

    fun signablePayload() = "$receiverAddress|$nonce|$timestamp".encodeToByteArray()

    @OptIn(kotlin.ExperimentalStdlibApi::class)
    fun appendBackingSignature(signingPrivateKey: ByteArray, domain: String): UmaLnurlpRequest {
        val signature = Secp256k1.signEcdsa(signablePayload(), signingPrivateKey).toHexString()
        val newBackingSignatures = (backingSignatures ?: emptyList()).toMutableList()
        newBackingSignatures.add(BackingSignature(domain = domain, signature = signature))
        return copy(backingSignatures = newBackingSignatures)
    }
}
