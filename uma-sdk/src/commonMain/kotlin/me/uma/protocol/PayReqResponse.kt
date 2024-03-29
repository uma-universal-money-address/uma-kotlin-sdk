package me.uma.protocol

import kotlinx.serialization.*
import me.uma.utils.serialFormat

/**
 * The response sent by the receiver to the sender to provide an invoice.
 *
 * @property encodedInvoice The BOLT11 invoice that the sender will pay.
 * @property routes Usually just an empty list from legacy LNURL, which was replaced by route hints in the BOLT11
 *     invoice.
 * @property compliance The compliance data from the receiver, including utxo info.
 * @property paymentInfo The payment info from the receiver, including currency and an updated conversion rate.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PayReqResponse(
    @SerialName("pr")
    val encodedInvoice: String,
    val compliance: PayReqResponseCompliance,
    val paymentInfo: PayReqResponsePaymentInfo,
    @EncodeDefault
    val routes: List<Route> = emptyList(),
) {
    fun toJson() = serialFormat.encodeToString(this)
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

/**
 * The payment info from the receiver.
 *
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
    val currencyCode: String,
    val decimals: Int,
    val multiplier: Double,
    val exchangeFeesMillisatoshi: Long,
)
