package me.uma.protocol

import me.uma.UmaException
import me.uma.crypto.Secp256k1
import me.uma.generated.ErrorCode
import me.uma.utils.serialFormat
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
     * The currency code of the `amount` field. `null` indicates that `amount` is in the smallest
     * unit of the settlement asset. For lightning, this is millisatoshis as in LNURL without LUD-21.
     * If this is not `null`, then `amount` is in the smallest unit of the specified currency (e.g.
     * cents for USD). This currency code can be any currency which the receiver can quote. However,
     * there are two most common scenarios for UMA:
     *
     * 1. If the sender wants the receiver wants to receive a specific amount in their receiving
     * currency, then this field should be the same as `receiving_currency_code`. This is useful
     * for cases where the sender wants to ensure that the receiver receives a specific amount
     * in that destination currency, regardless of the exchange rate, for example, when paying
     * for some goods or services in a foreign currency.
     *
     * 2. If the sender has a specific amount in their own currency that they would like to send,
     * then this field should be left as `None` to indicate that the amount is in the smallest
     * unit of the settlement asset (ie. msats by default).
     * This will lock the sent amount on the sender side, and the receiver will receive the
     * equivalent amount in their receiving currency. NOTE: In this scenario, the sending VASP
     * *should not* pass the sending currency code here, as it is not relevant to the receiver.
     * Rather, by specifying an invoice amount in the settlement asset (for example, msats for
     * lightning), the sending VASP can ensure that their user will be sending a fixed amount,
     * regardless of the exchange rate on the receiving side.
     */
    fun sendingCurrencyCode(): String?

    /**
     * The currency code that the receiver will receive for this payment.
     */
    fun receivingCurrencyCode(): String?

    fun isUmaRequest(): Boolean

    @Throws(UmaException::class)
    fun signablePayload(): ByteArray

    fun toJson(): String

    fun requestedPayeeData(): CounterPartyDataOptions?

    fun comment(): String?

    fun invoiceUUID(): String?

    fun settlementInfo(): SettlementInfo?

    fun toQueryParamMap(): Map<String, String>

    fun appendBackingSignature(signingPrivateKey: ByteArray, domain: String): PayRequest

    companion object {
        @Throws(UmaException::class)
        fun fromQueryParamMap(queryMap: Map<String, List<String>>): PayRequest {
            val receivingCurrencyCode = queryMap["convert"]?.firstOrNull()

            val amountStr =
                queryMap["amount"]?.firstOrNull()
                    ?: throw UmaException("Amount is required", ErrorCode.MISSING_REQUIRED_UMA_PARAMETERS)
            val parts = amountStr.split(".")
            val sendingCurrencyCode = if (parts.size == 2) parts[1] else null
            val amount = parts[0].toLong()

            val payerData =
                queryMap["payerData"]?.firstOrNull()?.let { serialFormat.decodeFromString(PayerData.serializer(), it) }
            val requestedPayeeData =
                queryMap["payeeData"]?.firstOrNull()?.let {
                    serialFormat.decodeFromString(
                        MapSerializer(String.serializer(), CounterPartyDataOption.serializer()),
                        it,
                    )
                }
            val comment = queryMap["comment"]?.firstOrNull()
            val invoiceUUID = queryMap["invoiceUUID"]?.firstOrNull()
            val settlement = queryMap["settlement"]?.firstOrNull()?.let {
                serialFormat.decodeFromString(SettlementInfo.serializer(), it)
            }
            return PayRequestV1(
                sendingCurrencyCode,
                receivingCurrencyCode,
                amount,
                payerData,
                requestedPayeeData,
                comment,
                invoiceUUID,
                settlement,
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
    /**
     * InvoiceUUID is the invoice UUID that the sender is paying.
     * This only exists in the v1 pay request since the v0 SDK won't support invoices.
     */
    val invoiceUUID: String? = null,
    /**
     * Settlement information including the layer and asset chosen by the sender.
     * If not specified, the payment will settle on Lightning using BTC.
     */
    val settlementInfo: SettlementInfo? = null,
) : PayRequest {
    override fun receivingCurrencyCode() = receivingCurrencyCode

    override fun sendingCurrencyCode() = sendingCurrencyCode

    override fun signablePayload(): ByteArray {
        if (payerData == null) {
            throw UmaException(
                "Payer data is required for UMA",
                ErrorCode.MISSING_REQUIRED_UMA_PARAMETERS
            )
        }
        if (payerData.identifier() == null) {
            throw UmaException(
                "Payer identifier is required for UMA",
                ErrorCode.MISSING_REQUIRED_UMA_PARAMETERS
            )
        }
        val complianceData = payerData.compliance()
            ?: throw UmaException("Compliance data is required", ErrorCode.MISSING_REQUIRED_UMA_PARAMETERS)
        return complianceData.let {
            "${payerData.identifier()}|${it.signatureNonce}|${it.signatureTimestamp}".encodeToByteArray()
        }
    }

    override fun isUmaRequest() = payerData != null && payerData.compliance() != null && payerData.identifier() != null

    override fun toJson() = serialFormat.encodeToString(PayRequestV1Serializer, this)

    override fun requestedPayeeData(): CounterPartyDataOptions? = requestedPayeeData

    override fun comment(): String? = comment

    override fun invoiceUUID(): String? = invoiceUUID

    override fun settlementInfo(): SettlementInfo? = settlementInfo

    override fun toQueryParamMap(): Map<String, String> {
        val amountStr =
            if (sendingCurrencyCode != null) {
                "$amount.$sendingCurrencyCode"
            } else {
                amount.toString()
            }
        val map =
            mutableMapOf(
                "amount" to amountStr,
            )
        receivingCurrencyCode?.let { map["convert"] = it }
        payerData?.let { map["payerData"] = serialFormat.encodeToString(it) }
        requestedPayeeData?.let {
            map["payeeData"] = serialFormat.encodeToString(it)
        }
        comment?.let { map["comment"] = it }
        invoiceUUID?.let { map["invoiceUUID"] = it }
        settlementInfo?.let { map["settlement"] = serialFormat.encodeToString(it) }
        return map
    }

    @OptIn(kotlin.ExperimentalStdlibApi::class)
    @Throws(UmaException::class)
    override fun appendBackingSignature(signingPrivateKey: ByteArray, domain: String): PayRequestV1 {
        val signablePayload = signablePayload()
        val signature = Secp256k1.signEcdsa(signablePayload, signingPrivateKey).toHexString()
        val complianceData = payerData?.compliance()
            ?: throw UmaException("Compliance payer data is missing", ErrorCode.MISSING_REQUIRED_UMA_PARAMETERS)
        val backingSignatures = (complianceData.backingSignatures ?: emptyList()).toMutableList()
        backingSignatures.add(BackingSignature(domain = domain, signature = signature))
        val updatedComplianceData = complianceData.copy(backingSignatures = backingSignatures)
        val updatedPayerDataMap = payerData.toMutableMap()
        updatedPayerDataMap[CounterPartyDataKeys.COMPLIANCE] =
            serialFormat.encodeToJsonElement(CompliancePayerData.serializer(), updatedComplianceData)
        return this.copy(payerData = PayerData(updatedPayerDataMap))
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

    override fun invoiceUUID(): String? = null

    override fun settlementInfo(): SettlementInfo? = null

    override fun signablePayload() = payerData.compliance()?.let {
        "${payerData.identifier()}|${it.signatureNonce}|${it.signatureTimestamp}".encodeToByteArray()
    } ?: payerData.identifier()?.encodeToByteArray()
        ?: throw UmaException("Payer identifier is required for UMA", ErrorCode.MISSING_REQUIRED_UMA_PARAMETERS)

    override fun toJson() = serialFormat.encodeToString(this)

    override fun toQueryParamMap() = mapOf(
        "amount" to amount.toString(),
        "convert" to currencyCode,
        "payerData" to serialFormat.encodeToString(payerData),
    )

    override fun appendBackingSignature(signingPrivateKey: ByteArray, domain: String): PayRequestV0 = this
}

@OptIn(ExperimentalSerializationApi::class)
internal object PayRequestV1Serializer : KSerializer<PayRequestV1> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("PayRequestV1") {
            element<String?>("convert", isOptional = true)
            element<String>("amount") // Serialize and deserialize amount as a string
            element<PayerData>("payerData")
            element<CounterPartyDataOptions?>("payeeData", isOptional = true)
            element<String?>("comment", isOptional = true)
            element<String?>("invoiceUUID", isOptional = true)
            element<SettlementInfo?>("settlement", isOptional = true)
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
            value.invoiceUUID?.let { encodeStringElement(descriptor, 5, it) }
            encodeNullableSerializableElement(descriptor, 6, SettlementInfo.serializer(), value.settlementInfo)
        }
    }

    override fun deserialize(decoder: Decoder): PayRequestV1 {
        var sendingCurrencyCode: String? = null
        var receivingCurrencyCode: String? = null
        var amount: String? = null
        var payerData: PayerData? = null
        var requestedPayeeData: CounterPartyDataOptions? = null
        var comment: String? = null
        var invoiceUUID: String? = null
        var settlementInfo: SettlementInfo? = null

        return decoder.decodeStructure(descriptor) {
            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                when (index) {
                    0 ->
                        receivingCurrencyCode =
                            decodeNullableSerializableElement(
                                descriptor,
                                index,
                                String.serializer().nullable,
                            )

                    1 -> amount = decodeStringElement(descriptor, index)
                    2 -> payerData = decodeSerializableElement(descriptor, index, PayerData.serializer())
                    3 ->
                        requestedPayeeData =
                            decodeNullableSerializableElement(
                                descriptor,
                                index,
                                MapSerializer(
                                    String.serializer(),
                                    CounterPartyDataOption.serializer(),
                                ).nullable,
                            )

                    4 -> comment = decodeNullableSerializableElement(descriptor, index, String.serializer().nullable)
                    5 ->
                        invoiceUUID =
                            decodeNullableSerializableElement(descriptor, index, String.serializer().nullable)
                    6 ->
                        settlementInfo =
                            decodeNullableSerializableElement(descriptor, index, SettlementInfo.serializer().nullable)
                }
            }

            val parsedAmount =
                amount?.let {
                    val parts = it.split(".")
                    if (parts.size == 2) {
                        sendingCurrencyCode = parts[1]
                        parts[0].toLong()
                    } else {
                        it.toLong()
                    }
                } ?: throw UmaException("Amount is required", ErrorCode.MISSING_REQUIRED_UMA_PARAMETERS)

            PayRequestV1(
                sendingCurrencyCode,
                receivingCurrencyCode,
                parsedAmount,
                payerData,
                requestedPayeeData,
                comment,
                invoiceUUID,
                settlementInfo,
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
