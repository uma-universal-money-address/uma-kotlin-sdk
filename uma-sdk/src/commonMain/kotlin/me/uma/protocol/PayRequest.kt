package me.uma.protocol

import kotlinx.serialization.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.Json

/**
 * The request sent by the sender to the receiver to retrieve an invoice.
 *
 * @property sendingCurrencyCode The currency code in which the amount field is specified. If null, the
 *     amount is assumed to be specified in msats.
 * @property receivingCurrencyCode The currency code that the receiver will receive for this payment.
 * @property amount The amount that the receiver will receive in either the smallest unit of the sendingCurrencyCode
 *     or in msats (if sendingCurrencyCode is null).
 * @property payerData The data that the sender will send to the receiver to identify themselves.
 * @property requestedPayeeData The data that the sender requests the receiver to send to identify themselves.
 * @property comment A comment that the sender would like to include with the payment. This can only be included
 *     if the receiver included the `commentAllowed` field in the lnurlp response. The length of
 *     the comment must be less than or equal to the value of `commentAllowed`.
 */
@Serializable(with = PayRequestSerializer::class)
data class PayRequest @JvmOverloads constructor(
    val sendingCurrencyCode: String?,
    val receivingCurrencyCode: String?,
    val amount: Long,
    val payerData: PayerData?,
    val requestedPayeeData: CounterPartyDataOptions? = null,
    val comment: String? = null,
) {
    fun signablePayload(): ByteArray {
        if (payerData == null) throw IllegalArgumentException("Payer data is required for UMA")
        if (payerData.identifier() == null) throw IllegalArgumentException("Payer identifier is required for UMA")
        val complianceData = payerData.compliance() ?: throw IllegalArgumentException("Compliance data is required")
        return complianceData.let {
            "${payerData.identifier()}|${it.signatureNonce}|${it.signatureTimestamp}".encodeToByteArray()
        }
    }

    fun isUmaRequest() = payerData != null && payerData.compliance() != null && payerData.identifier() != null

    fun toJson() = Json.encodeToString(this)
}

@OptIn(ExperimentalSerializationApi::class)
object PayRequestSerializer : KSerializer<PayRequest> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PayRequest") {
        element<String?>("convert")
        element<String>("amount") // Serialize and deserialize amount as a string
        element<PayerData>("payerData")
        element<CounterPartyDataOptions?>("payeeData")
        element<String?>("comment")
    }

    override fun serialize(encoder: Encoder, value: PayRequest) {
        encoder.encodeStructure(descriptor) {
            value.receivingCurrencyCode?.let { encodeStringElement(descriptor, 0, it) }
            encodeStringElement(
                descriptor,
                1,
                if (value.sendingCurrencyCode != null) {
                    "${value.amount}.${value.sendingCurrencyCode}"
                } else {
                    value.amount.toString()
                },
            )
            encodeNullableSerializableElement(descriptor, 2, PayerData.serializer(), value.payerData)
            encodeNullableSerializableElement(
                descriptor,
                3,
                MapSerializer(String.serializer(), CounterPartyDataOption.serializer()),
                value.requestedPayeeData,
            )
            value.comment?.let { encodeStringElement(descriptor, 4, it) }
        }
    }

    override fun deserialize(decoder: Decoder): PayRequest {
        var sendingCurrencyCode: String? = null
        var receivingCurrencyCode: String? = null
        var amount: String? = null
        var payerData: PayerData? = null
        var requestedPayeeData: CounterPartyDataOptions? = null
        var comment: String? = null

        return decoder.decodeStructure(descriptor) {
            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                when (index) {
                    0 -> receivingCurrencyCode = decodeStringElement(descriptor, index)
                    1 -> amount = decodeStringElement(descriptor, index)
                    2 -> payerData = decodeSerializableElement(descriptor, index, PayerData.serializer())
                    3 -> requestedPayeeData = decodeNullableSerializableElement(
                        descriptor,
                        index,
                        MapSerializer(
                            String.serializer(),
                            CounterPartyDataOption.serializer(),
                        ).nullable,
                    )
                    4 -> comment = decodeStringElement(descriptor, index)
                }
            }

            val parsedAmount = amount?.let {
                val parts = it.split(".")
                if (parts.size == 2) {
                    sendingCurrencyCode = parts[1]
                    parts[0].toLong()
                } else {
                    it.toLong()
                }
            } ?: throw IllegalArgumentException("Amount is required")

            PayRequest(
                sendingCurrencyCode,
                receivingCurrencyCode,
                parsedAmount,
                payerData,
                requestedPayeeData,
                comment,
            )
        }
    }
}
