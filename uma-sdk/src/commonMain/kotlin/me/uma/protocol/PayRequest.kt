package me.uma.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/**
 * The request sent by the sender to the receiver to retrieve an invoice.
 *
 * @property currencyCode The currency code that the receiver will receive for this payment.
 * @property amount The amount of the specified currency that the receiver will receive for this payment in the smallest
 *     unit of the specified currency (i.e. cents for USD).
 * @property payerData The data that the sender will send to the receiver to identify themselves.
 */
@Serializable
data class PayRequest(
    @SerialName("currency")
    val currencyCode: String,
    val amount: Long,
    val payerData: PayerData,
) {
    fun signablePayload() =
        payerData.compliance?.let {
            "${payerData.identifier}|${it.signatureNonce}|${it.signatureTimestamp}".encodeToByteArray()
        } ?: payerData.identifier.encodeToByteArray()

    fun toJson() = Json.encodeToString(this)
}

@Serializable
data class PayerData @JvmOverloads constructor(
    val identifier: String,
    val name: String? = null,
    val email: String? = null,
    val compliance: CompliancePayerData? = null,
)

/**
 * The compliance data from the sender, including utxo info.
 *
 * @property utxos The list of UTXOs of the sender's channels that might be used to fund the payment.
 * @property nodePubKey If known, the public key of the sender's node. If supported by the receiver VASP's compliance
 *     provider, this will be used to pre-screen the sender's UTXOs for compliance purposes.
 * @property kycStatus Indicates whether VASP1 has KYC information about the sender.
 * @property travelRuleInfo The travel rule information of the sender. This is encrypted with the receiver's public
 *     encryption key.
 * @property utxoCallback The URL that the receiver will call to send UTXOs of the channel that the receiver used to
 *     receive the payment once it completes.
 * @property signature The signature of the sender on the signable payload.
 * @property signatureNonce The nonce used in the signature.
 * @property signatureTimestamp The timestamp used in the signature.
 * @property travelRuleFormat An optional standardized format of the travel rule information (e.g. IVMS). Null
 *     indicates raw json or a custom format.
 */
@Serializable
data class CompliancePayerData(
    val utxos: List<String>,
    val nodePubKey: String?,
    val kycStatus: KycStatus,
    val travelRuleInfo: String?,
    val utxoCallback: String,
    val signature: String,
    val signatureNonce: String,
    val signatureTimestamp: Long,
    val travelRuleFormat: TravelRuleFormat? = null,
) {
    fun signedWith(signature: String) = copy(signature = signature)
}

/**
 * A standardized format of the travel rule information.
 */
@Serializable(with = TravelRuleFormatSerializer::class)
data class TravelRuleFormat(
    /** The type of the travel rule format (e.g. IVMS). */
    val type: String,
    /** The version of the travel rule format (e.g. 1.0). */
    val version: String?,
)

/**
 * Serializes the TravelRuleFormat to string in the format of "type@version". If there's no version, it will be
 * serialized as "type".
 */
class TravelRuleFormatSerializer : KSerializer<TravelRuleFormat> {
    override val descriptor = PrimitiveSerialDescriptor("TravelRuleFormat", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): TravelRuleFormat {
        val value = decoder.decodeString()
        if (!value.contains("@")) {
            return TravelRuleFormat(value, null)
        }
        val parts = value.split("@")
        return TravelRuleFormat(parts[0], parts.getOrNull(1))
    }

    override fun serialize(encoder: Encoder, value: TravelRuleFormat) {
        encoder.encodeString("${value.type}${value.version?.let { "@$it" } ?: ""}")
    }
}
