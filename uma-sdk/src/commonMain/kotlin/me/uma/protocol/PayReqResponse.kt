package me.uma.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import me.uma.utils.serialFormat

/**
 * The response sent by the receiver to the sender to provide an invoice.
 *
 * @property encodedInvoice The BOLT11 invoice that the sender will pay.
 * @property paymentInfo Information about the payment that the receiver will receive. Includes
 *     Final currency-related information for the payment. Required for UMA.
 * @property payeeData The data about the receiver that the sending VASP requested in the payreq request.
 *     Required for UMA.
 * @property routes Usually just an empty list from legacy LNURL, which was replaced by route hints in the BOLT11
 *     invoice.
 * @property disposable This field may be used by a WALLET to decide whether the initial LNURL link will
 *     be stored locally for later reuse or erased. If disposable is null, it should be
 *     interpreted as true, so if SERVICE intends its LNURL links to be stored it must
 *     return `disposable: false`. UMA should always return `disposable: false`. See LUD-11.
 * @property successAction Defines a struct which can be stored and shown to the user on payment success. See LUD-09.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PayReqResponse(
    @SerialName("pr")
    val encodedInvoice: String,
    val paymentInfo: PayReqResponsePaymentInfo?,
    val payeeData: PayeeData?,
    @EncodeDefault
    val routes: List<Route> = emptyList(),
    val disposable: Boolean? = null,
    val successAction: Map<String, String>? = null,
) {
    fun toJson() = serialFormat.encodeToString(this)

    fun signablePayload(payerIdentifier: String): ByteArray {
        if (payeeData == null) throw IllegalArgumentException("Payee data is required for UMA")
        if (payeeData.identifier() == null) throw IllegalArgumentException("Payee identifier is required for UMA")
        val complianceData = payeeData.payeeCompliance()
            ?: throw IllegalArgumentException("Compliance data is required")
        return complianceData.let {
            "$payerIdentifier|${payeeData.identifier()}|${it.signatureNonce}|${it.signatureTimestamp}"
                .encodeToByteArray()
        }
    }

    fun isUmaResponse() = payeeData != null &&
        payeeData.payeeCompliance() != null &&
        payeeData.identifier() != null &&
        paymentInfo != null
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
 *
 * @property amount The amount that the receiver will receive in the smallest unit of the specified currency.
 * @property currencyCode The currency code that the receiver will receive for this payment.
 * @property decimals Number of digits after the decimal point for the receiving currency. For example, in USD, by
 *     convention, there are 2 digits for cents - $5.95. In this case, `decimals` would be 2. This should align with
 *     the currency's `decimals` field in the LNURLP response. It is included here for convenience. See
 *     [UMAD-04](https://github.com/uma-universal-money-address/protocol/blob/main/umad-04-lnurlp-response.md) for
 *     details, edge cases, and examples.
 * @property multiplier The conversion rate. It is the number of millisatoshis that the receiver will receive for 1
 *     unit of the specified currency (eg: cents in USD). In this context, this is just for convenience. The conversion
 *     rate is also baked into the invoice amount itself. Specifically:
 *     `invoiceAmount = amount * multiplier + exchangeFeesMillisatoshi`
 * @property exchangeFeesMillisatoshi The fees charged (in millisats) by the receiving VASP for this transaction. This
 * 	   is separate from the [multiplier].
 */
@Serializable
data class PayReqResponsePaymentInfo(
    val amount: Long,
    val currencyCode: String,
    val decimals: Int,
    val multiplier: Double,
    @SerialName("fee")
    val exchangeFeesMillisatoshi: Long,
)
