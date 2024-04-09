package me.uma.protocol

import kotlinx.serialization.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import me.uma.utils.serialFormat

/**
 * The request sent by the sender to the receiver to retrieve an invoice.
 */
sealed interface PayRequest {
    /**
     * The amount that the receiver will receive in either the smallest unit of the
     * sendingCurrencyCode or in msats (if sendingCurrencyCode is null).
     */
    val amount: Long

    /**
     * The data that the sender will send to the receiver to identify themselves.
     */
    val payerData: PayerData?

    /**
     * The currency code in which the amount field is specified. If null, the
     * amount is assumed to be specified in msats.
     */
    fun sendingCurrencyCode(): String?

    /**
     * The currency code that the receiver will receive for this payment.
     */
    fun receivingCurrencyCode(): String?

    fun isUmaRequest(): Boolean

    fun signablePayload(): ByteArray

    fun toJson(): String

    fun requestedPayeeData(): CounterPartyDataOptions?

    fun comment(): String?

    fun toQueryParamMap(): Map<String, String>

    companion object {
        fun fromQueryParamMap(queryMap: Map<String, List<String>>): PayRequest {
            val receivingCurrencyCode = queryMap["convert"]?.firstOrNull()

            val amountStr = queryMap["amount"]?.firstOrNull()
                ?: throw IllegalArgumentException("Amount is required")
            val parts = amountStr.split(".")
            val sendingCurrencyCode = if (parts.size == 2) parts[1] else null
            val amount = parts[0].toLong()

            val payerData =
                queryMap["payerData"]?.firstOrNull()?.let { serialFormat.decodeFromString(PayerData.serializer(), it) }
            val requestedPayeeData = queryMap["payeeData"]?.firstOrNull()?.let {
                serialFormat.decodeFromString(
                    MapSerializer(String.serializer(), CounterPartyDataOption.serializer()),
                    it,
                )
            }
            val comment = queryMap["comment"]?.firstOrNull()
            return PayRequestV1(
                sendingCurrencyCode,
                receivingCurrencyCode,
                amount,
                payerData,
                requestedPayeeData,
                comment,
            )
        }
    }
}

@Serializable
internal data class PayRequestV1(
    val sendingCurrencyCode: String?,
    val receivingCurrencyCode: String?,
    override val amount: Long,
    override val payerData: PayerData?,

    /**
     * The data that the sender requests the receiver to send to identify themselves.
     */
    val requestedPayeeData: CounterPartyDataOptions? = null,

    /**
     * A comment that the sender would like to include with the payment. This can only be included
     * if the receiver included the `commentAllowed` field in the lnurlp response. The length of
     * the comment must be less than or equal to the value of `commentAllowed`.
     */
    val comment: String? = null,
) : PayRequest {

    override fun receivingCurrencyCode() = receivingCurrencyCode

    override fun sendingCurrencyCode() = sendingCurrencyCode

    override fun signablePayload(): ByteArray {
        if (payerData == null) throw IllegalArgumentException("Payer data is required for UMA")
        if (payerData.identifier() == null) throw IllegalArgumentException("Payer identifier is required for UMA")
        val complianceData = payerData.compliance() ?: throw IllegalArgumentException("Compliance data is required")
        return complianceData.let {
            "${payerData.identifier()}|${it.signatureNonce}|${it.signatureTimestamp}".encodeToByteArray()
        }
    }

    override fun isUmaRequest() = payerData != null && payerData.compliance() != null && payerData.identifier() != null

    override fun toJson() = serialFormat.encodeToString(PayRequestV1Serializer, this)

    override fun requestedPayeeData(): CounterPartyDataOptions? = requestedPayeeData

    override fun comment(): String? = comment

    override fun toQueryParamMap(): Map<String, String> {
        val amountStr = if (sendingCurrencyCode != null) {
            "$amount.$sendingCurrencyCode"
        } else {
            amount.toString()
        }
        val map = mutableMapOf(
            "amount" to amountStr,
        )
        receivingCurrencyCode?.let { map["convert"] = it }
        payerData?.let { map["payerData"] = serialFormat.encodeToString(it) }
        requestedPayeeData?.let {
            map["payeeData"] = serialFormat.encodeToString(it)
        }
        comment?.let { map["comment"] = it }
        return map
    }
}

@Serializable
internal data class PayRequestV0(
    /**
     * The currency code that the receiver will receive for this payment.
     */
    @SerialName("currency")
    val currencyCode: String,

    override val amount: Long,
    override val payerData: PayerData,
) : PayRequest {
    override fun receivingCurrencyCode() = currencyCode

    override fun sendingCurrencyCode() = null

    override fun isUmaRequest() = true

    override fun requestedPayeeData() = null

    override fun comment(): String? = null

    override fun signablePayload() =
        payerData.compliance()?.let {
            "${payerData.identifier()}|${it.signatureNonce}|${it.signatureTimestamp}".encodeToByteArray()
        } ?: payerData.identifier()?.encodeToByteArray()
            ?: throw IllegalArgumentException("Payer identifier is required for UMA")

    override fun toJson() = serialFormat.encodeToString(this)

    override fun toQueryParamMap() = mapOf(
        "amount" to amount.toString(),
        "convert" to currencyCode,
        "payerData" to serialFormat.encodeToString(payerData),
    )
}

@OptIn(ExperimentalSerializationApi::class)
internal object PayRequestV1Serializer : KSerializer<PayRequestV1> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PayRequestV1") {
        element<String?>("convert", isOptional = true)
        element<String>("amount") // Serialize and deserialize amount as a string
        element<PayerData>("payerData")
        element<CounterPartyDataOptions?>("payeeData", isOptional = true)
        element<String?>("comment", isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: PayRequestV1) {
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

    override fun deserialize(decoder: Decoder): PayRequestV1 {
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
                    0 -> receivingCurrencyCode = decodeNullableSerializableElement(
                        descriptor,
                        index,
                        String.serializer().nullable,
                    )

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

                    4 -> comment = decodeNullableSerializableElement(descriptor, index, String.serializer().nullable)
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

            PayRequestV1(
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

object PayRequestSerializer : JsonContentPolymorphicSerializer<PayRequest>(PayRequest::class) {
    override fun selectDeserializer(element: JsonElement) = when {
        "currency" in element.jsonObject -> PayRequestV0.serializer()
        else -> PayRequestV1Serializer
    }
}
