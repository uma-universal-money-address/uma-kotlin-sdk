package me.uma.protocol

import me.uma.crypto.Secp256k1
import me.uma.utils.serialFormat
import kotlinx.serialization.*

/**
 * Response from VASP2 to the [LnurlpRequest].
 *
 * @property callback The URL which should be used by VASP1 for the [PayRequest].
 * @property minSendable The minimum amount that the receiver can receive in millisatoshis.
 * @property maxSendable The maximum amount that the receiver can receive in millisatoshis.
 * @property metadata Encoded metadata that can be used to verify the invoice later. See the
 *     [LUD-06 Spec](https://github.com/lnurl/luds/blob/luds/06.md).
 * @property currencies The list of currencies that the receiver accepts.
 * @property requiredPayerData The data that the sender must send to the receiver to identify themselves.
 * @property compliance The compliance data from the receiver, including TR status, kyc info, etc.
 * @property umaVersion The version of the UMA protocol that VASP2 has chosen for this transaction based on its own
 *     support and VASP1's specified preference in the LnurlpRequest. For the version negotiation flow, see
 * 	   https://static.swimlanes.io/87f5d188e080cb8e0494e46f80f2ae74.png
 * @property commentCharsAllowed The number of characters that the sender can include in the comment field of the pay
 *     request.
 * @property nostrPubkey An optional nostr pubkey used for nostr zaps (NIP-57). If set, it should be a valid
 *     BIP-340 public key in hex format.
 * @property allowsNostr Should be set to true if the receiving VASP allows nostr zaps (NIP-57).
 * @property backingSignatures A list of backing signatures from VASPs that can attest to the authenticity of the message.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LnurlpResponse(
    val callback: String,
    val minSendable: Long,
    val maxSendable: Long,
    val metadata: String,
    val currencies: List<
        @Serializable(with = CurrencySerializer::class)
        Currency
        >?,
    @SerialName("payerData")
    val requiredPayerData: CounterPartyDataOptions?,
    val compliance: LnurlComplianceResponse?,
    val umaVersion: String?,
    @SerialName("commentAllowed")
    val commentCharsAllowed: Int? = null,
    val nostrPubkey: String? = null,
    val allowsNostr: Boolean? = null,
    @EncodeDefault
    val tag: String = "payRequest",
    val backingSignatures: List<BackingSignature>? = null,
) {
    fun asUmaResponse(): UmaLnurlpResponse? = if (
        currencies != null &&
        requiredPayerData != null &&
        compliance != null &&
        umaVersion != null
    ) {
        UmaLnurlpResponse(
            callback,
            minSendable,
            maxSendable,
            metadata,
            currencies,
            requiredPayerData,
            compliance,
            umaVersion,
            commentCharsAllowed,
            nostrPubkey,
            allowsNostr,
            tag,
            backingSignatures,
        )
    } else {
        null
    }

    fun toJson() = serialFormat.encodeToString(this)
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UmaLnurlpResponse(
    val callback: String,
    val minSendable: Long,
    val maxSendable: Long,
    val metadata: String,
    val currencies: List<
        @Serializable(with = CurrencySerializer::class)
        Currency
        >,
    @SerialName("payerData")
    val requiredPayerData: CounterPartyDataOptions,
    val compliance: LnurlComplianceResponse,
    val umaVersion: String,
    @SerialName("commentAllowed")
    val commentCharsAllowed: Int? = null,
    val nostrPubkey: String? = null,
    val allowsNostr: Boolean? = null,
    @EncodeDefault
    val tag: String = "payRequest",
    val backingSignatures: List<BackingSignature>?,
) {
    fun toJson() = serialFormat.encodeToString(this)

    /**
     * Appends a backing signature to the UmaLnurlpResponse.
     *
     * @param signingPrivateKey The private key to use to sign the payload
     * @param domain The domain of the VASP that is signing the payload. The associated public key will be fetched from
     *              /.well-known/lnurlpubkey on this domain to verify the signature.
     * @return response with the backing signature appended
     */
    @OptIn(kotlin.ExperimentalStdlibApi::class)
    @Throws(Exception::class)
    fun appendBackingSignature(signingPrivateKey: ByteArray, domain: String): UmaLnurlpResponse {
        val signature = Secp256k1.signEcdsa(compliance.signablePayload(), signingPrivateKey).toHexString()
        val newBackingSignatures = (backingSignatures ?: emptyList()).toMutableList()
        newBackingSignatures.add(BackingSignature(domain = domain, signature = signature))
        return copy(backingSignatures = newBackingSignatures)
    }
}
