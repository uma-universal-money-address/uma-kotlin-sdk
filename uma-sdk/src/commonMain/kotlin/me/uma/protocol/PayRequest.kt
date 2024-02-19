package me.uma.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
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
data class PayRequest @JvmOverloads constructor(
    @SerialName("currency")
    val currencyCode: String,
    val amount: Long,
    val payerData: PayerData,
    @SerialName("payeeData")
    val requestedPayeeData: CounterPartyDataOptions? = null,
) {
    fun signablePayload(): ByteArray {
        val complianceData = payerData.compliance() ?: throw IllegalArgumentException("Compliance data is required")
        return complianceData.let {
            "${payerData.identifier()}|${it.signatureNonce}|${it.signatureTimestamp}".encodeToByteArray()
        }
    }

    fun toJson() = Json.encodeToString(this)
}
