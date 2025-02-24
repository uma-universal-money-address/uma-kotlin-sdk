package me.uma.protocol

import me.uma.crypto.Secp256k1
import me.uma.utils.serialFormat
import kotlinx.serialization.*
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

/**
 * The response sent by the receiver to the sender to provide an invoice.
 */
sealed interface PayReqResponse {
    /**
     * The BOLT11 invoice that the sender will pay.
     */
    val encodedInvoice: String

    /**
     * Information about the payment that the receiver will receive. Includes final currency-related
     * information for the payment. Required for UMA.
     */
    val paymentInfo: PayReqResponsePaymentInfo?

    /**
     * Usually just an empty list from legacy LNURL, which was replaced by route hints in the BOLT11
     * invoice.
     */
    val routes: List<Route>

    fun payeeData(): PayeeData?

    fun disposable(): Boolean?

    fun successAction(): Map<String, String>?

    fun isUmaResponse(): Boolean

    fun toJson(): String

    fun appendBackingSignature(
        signingPrivateKey: ByteArray,
        domain: String,
        payerIdentifier: String,
        payeeIdentifier: String,
    ): PayReqResponse
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class PayReqResponseV1(
    @SerialName("pr")
    override val encodedInvoice: String,
    @SerialName("converted")
    override val paymentInfo: V1PayReqResponsePaymentInfo?,
    /**
     * The data about the receiver that the sending VASP requested in the payreq request.
     * Required for UMA.
     */
    val payeeData: PayeeData?,
    @EncodeDefault
    override val routes: List<Route> = emptyList(),
    /**
     * This field may be used by a WALLET to decide whether the initial LNURL link will
     *  be stored locally for later reuse or erased. If disposable is null, it should be
     *  interpreted as true, so if SERVICE intends its LNURL links to be stored it must
     *  return `disposable: false`. UMA should always return `disposable: false`. See LUD-11.
     */
    val disposable: Boolean? = null,
    /**
     * Defines a struct which can be stored and shown to the user on payment success. See LUD-09.
     */
    val successAction: Map<String, String>? = null,
) : PayReqResponse {
    override fun payeeData() = payeeData

    override fun disposable() = disposable

    override fun successAction() = successAction

    override fun toJson() = serialFormat.encodeToString(this)

    override fun isUmaResponse() = payeeData != null &&
        payeeData.payeeCompliance() != null &&
        payeeData.identifier() != null &&
        paymentInfo != null

    fun signablePayload(payerIdentifier: String): ByteArray {
        if (payeeData == null) throw IllegalArgumentException("Payee data is required for UMA")
        if (payeeData.identifier() == null) throw IllegalArgumentException("Payee identifier is required for UMA")
        val complianceData =
            payeeData.payeeCompliance()
                ?: throw IllegalArgumentException("Compliance data is required")
        return complianceData.let {
            "$payerIdentifier|${payeeData.identifier()}|${it.signatureNonce}|${it.signatureTimestamp}"
                .lowercase()
                .encodeToByteArray()
        }
    }

    @OptIn(kotlin.ExperimentalStdlibApi::class)
    @Throws(Exception::class)
    override fun appendBackingSignature(
        signingPrivateKey: ByteArray,
        domain: String,
        payerIdentifier: String,
        payeeIdentifier: String,
    ): PayReqResponseV1 {
        val signablePayload = signablePayload(payerIdentifier)
        val signature = Secp256k1.signEcdsa(signablePayload, signingPrivateKey).toHexString()
        val complianceData = payeeData?.payeeCompliance()
            ?: throw IllegalArgumentException("Compliance payee data is missing")
        val backingSignatures = (complianceData.backingSignatures ?: emptyList()).toMutableList()
        backingSignatures.add(BackingSignature(domain = domain, signature = signature))
        val updatedComplianceData = complianceData.copy(backingSignatures = backingSignatures)
        val updatedPayeeDataMap = payeeData.toMutableMap()
        updatedPayeeDataMap["compliance"] =
            serialFormat.encodeToJsonElement(CompliancePayeeData.serializer(), updatedComplianceData)
        return this.copy(payeeData = PayeeData(updatedPayeeDataMap))
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class PayReqResponseV0(
    @SerialName("pr")
    override val encodedInvoice: String,
    /**
     * The compliance data from the receiver, including utxo info.
     */
    val compliance: PayReqResponseCompliance,
    override val paymentInfo: V0PayReqResponsePaymentInfo,
    @EncodeDefault
    override val routes: List<Route> = emptyList(),
) : PayReqResponse {
    override fun payeeData(): PayeeData? = null

    override fun disposable() = null

    override fun successAction() = null

    override fun isUmaResponse() = true

    override fun toJson() = serialFormat.encodeToString(this)

    override fun appendBackingSignature(
        signingPrivateKey: ByteArray,
        domain: String,
        payerIdentifier: String,
        payeeIdentifier: String,
    ): PayReqResponseV0 = this
}

@Serializable
data class Route(
    val pubkey: String,
    val path: List<RouteHop>,
)

@Serializable
data class RouteHop(
    val pubkey: String,
    val channel: String,
    val fee: Long,
    val msatoshi: Long,
)

/**
 * The payment info from the receiver.
 */
sealed interface PayReqResponsePaymentInfo {
    /**
     * The amount that the receiver will receive in the smallest unit of the specified currency.
     */
    val amount: Long?

    /**
     * The currency code that the receiver will receive for this payment.
     */
    val currencyCode: String

    /**
     * Number of digits after the decimal point for the receiving currency. For example, in USD, by
     * convention, there are 2 digits for cents - $5.95. In this case, `decimals` would be 2. This should align with
     * the currency's `decimals` field in the LNURLP response. It is included here for convenience. See
     * [UMAD-04](https://github.com/uma-universal-money-address/protocol/blob/main/umad-04-lnurlp-response.md) for
     * details, edge cases, and examples.
     */
    val decimals: Int

    /**
     * The conversion rate. It is the number of millisatoshis that the receiver will receive for 1
     * unit of the specified currency (eg: cents in USD). In this context, this is just for convenience. The conversion
     * rate is also baked into the invoice amount itself. Specifically:
     * `invoiceAmount = amount * multiplier + exchangeFeesMillisatoshi`
     */
    val multiplier: Double

    /**
     * The fees charged (in millisats) by the receiving VASP for this transaction. This
     * is separate from the [multiplier].
     */
    val exchangeFeesMillisatoshi: Long
}

@Serializable
internal data class V1PayReqResponsePaymentInfo(
    override val amount: Long? = null,
    override val currencyCode: String,
    override val decimals: Int,
    override val multiplier: Double,
    @SerialName("fee")
    override val exchangeFeesMillisatoshi: Long,
) : PayReqResponsePaymentInfo

@Serializable
internal data class V0PayReqResponsePaymentInfo(
    override val currencyCode: String,
    override val decimals: Int,
    override val multiplier: Double,
    override val exchangeFeesMillisatoshi: Long,
) : PayReqResponsePaymentInfo {
    override val amount: Long? = null
}

/**
 * The compliance data from the receiver, including utxo info.
 *
 * @property utxos A list of UTXOs of channels over which the receiver will likely receive the payment.
 * @property nodePubKey If known, the public key of the receiver's node. If supported by the sending VASP's compliance
 *     provider, this will be used to pre-screen the receiver's UTXOs for compliance purposes.
 * @property utxoCallback The URL that the sender VASP will call to send UTXOs of the channel that the sender used to
 *     send the payment once it completes.
 */
@Serializable
data class PayReqResponseCompliance(
    val utxos: List<String>,
    val nodePubKey: String?,
    val utxoCallback: String,
)

object PayReqResponseSerializer : JsonContentPolymorphicSerializer<PayReqResponse>(PayReqResponse::class) {
    override fun selectDeserializer(element: JsonElement) = when {
        "compliance" in element.jsonObject -> PayReqResponseV0.serializer()
        else -> PayReqResponseV1.serializer()
    }
}
