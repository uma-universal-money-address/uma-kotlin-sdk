package me.uma.protocol

import io.ktor.utils.io.core.toByteArray
import me.uma.crypto.Bech32
import me.uma.utils.ByteCodeable
import me.uma.utils.TLVCodeable
import me.uma.utils.array
import me.uma.utils.getBoolean
import me.uma.utils.getByteCodeable
import me.uma.utils.getNumber
import me.uma.utils.getString
import me.uma.utils.getTLV
import me.uma.utils.lengthOffset
import me.uma.utils.putByteCodeable
import me.uma.utils.putBoolean
import me.uma.utils.putByteArray
import me.uma.utils.putTLVCodeable
import me.uma.utils.putNumber
import me.uma.utils.putString
import me.uma.utils.valueOffset
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

private const val UMA_BECH32_PREFIX = "uma"
typealias MalformedUmaInvoiceException = IllegalArgumentException

@Serializable(with = InvoiceCurrencyTLVSerializer::class)
data class InvoiceCurrency(
    val code: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
) : TLVCodeable {

    companion object {
        val EMPTY = InvoiceCurrency("","","",0)

        fun fromTLV(bytes: ByteArray): InvoiceCurrency {
            var code = ""
            var name = ""
            var symbol = ""
            var decimals = -1
            var offset = 0
            while(offset < bytes.size) {
                val length = bytes[offset.lengthOffset()].toInt()
                when(bytes[offset].toInt()) {
                    0 -> code = bytes.getString(offset.valueOffset(), length)
                    1 -> name = bytes.getString(offset.valueOffset(), length)
                    2 -> symbol = bytes.getString(offset.valueOffset(), length)
                    3 -> decimals = bytes.getNumber(offset.valueOffset(), length)
                }
                offset = offset.valueOffset() + length
            }
            return InvoiceCurrency(code=code, name=name, symbol=symbol, decimals=decimals)
        }
    }

    override fun toTLV() = mutableListOf<ByteArray>()
        .putString(0, code)
        .putString(1, name)
        .putString(2, symbol)
        .putNumber(3, decimals)
        .array()
}

@OptIn(ExperimentalSerializationApi::class)
class InvoiceCurrencyTLVSerializer: KSerializer<InvoiceCurrency> {
    private val delegateSerializer = ByteArraySerializer()
    override val descriptor = SerialDescriptor("InvoiceCurrency", delegateSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: InvoiceCurrency) {
        encoder.encodeSerializableValue(
            delegateSerializer,
            value.toTLV()
        )
    }

    override fun deserialize(decoder: Decoder) = InvoiceCurrency.fromTLV(
        decoder.decodeSerializableValue(delegateSerializer)
    )
}

@Serializable(with = InvoiceTLVSerializer::class)
class Invoice(
    val receiverUma: String,

    /** Invoice UUID Served as both the identifier of the UMA invoice, and the validation of proof of payment.*/
    val invoiceUUID: String,

    /** The amount of invoice to be paid in the smallest unit of the ReceivingCurrency. */
    val amount: Int,

    /** The currency of the invoice */
    val receivingCurrency: InvoiceCurrency,

    /** The unix timestamp the UMA invoice expires */
    val expiration: Int,

    /** Indicates whether the VASP is a financial institution that requires travel rule information. */
    val isSubjectToTravelRule: Boolean,

    /** RequiredPayerData the data about the payer that the sending VASP must provide in order to send a payment. */
    val requiredPayerData: CounterPartyDataOptions? = null,

    /** UmaVersion is a list of UMA versions that the VASP supports for this transaction. It should be
    * containing the lowest minor version of each major version it supported, separated by commas.
    */
    val umaVersion: String,

    /** CommentCharsAllowed is the number of characters that the sender can include in the comment field of the pay request. */
    val commentCharsAllowed: Int? = null,

    /** The sender's UMA address. If this field presents, the UMA invoice should directly go to the sending VASP instead of showing in other formats. */
    val senderUma: String? = null,

    /** The maximum number of the invoice can be paid */
    val invoiceLimit: Int? = null,

    /** YC status of the receiver, default is verified. */
    val kycStatus: KycStatus? = null,

    /** The callback url that the sender should send the PayRequest to. */
    val callback: String,

    /** The signature of the UMA invoice */
    val signature: ByteArray? = null,
) : TLVCodeable {

    override fun toTLV() = mutableListOf<ByteArray>()
            .putString(0, receiverUma)
            .putString(1, invoiceUUID)
            .putNumber(2, amount)
            .putTLVCodeable(3, receivingCurrency)
            .putNumber(4, expiration)
            .putBoolean(5, isSubjectToTravelRule)
            .putByteCodeable(6, requiredPayerData?.let(::InvoiceCounterPartyDataOptions))
            .putString(7, umaVersion)
            .putNumber(8, commentCharsAllowed)
            .putString(9, senderUma)
            .putNumber(10, invoiceLimit)
            .putByteCodeable(11, kycStatus?.let(::InvoiceKycStatus))
            .putString(12, callback)
            .putByteArray(100, signature)
            .array()

    fun toBech32(): String {
        return Bech32.encodeBech32(
            UMA_BECH32_PREFIX,
            this.toTLV(),
        )
    }

    companion object {
        fun fromTLV(bytes: ByteArray): Invoice {
            val ib = InvoiceBuilder()
            var offset = 0
            while(offset < bytes.size) {
                val length = bytes[offset.lengthOffset()].toInt()
                when(bytes[offset].toInt()) {
                    0 -> ib.receiverUma = bytes.getString(offset.valueOffset(), length)
                    1 -> ib.invoiceUUID = bytes.getString(offset.valueOffset(), length)
                    2 -> ib.amount = bytes.getNumber(offset.valueOffset(), length)
                    3 -> ib.receivingCurrency = bytes.getTLV(offset.valueOffset(), length, InvoiceCurrency::fromTLV) as InvoiceCurrency
                    4 -> ib.expiration = bytes.getNumber(offset.valueOffset(), length)
                    5 -> ib.isSubjectToTravelRule = bytes.getBoolean(offset.valueOffset())
                    6 -> ib.requiredPayerData = (bytes.getByteCodeable(
                            offset.valueOffset(),
                            length,
                            InvoiceCounterPartyDataOptions::fromBytes) as InvoiceCounterPartyDataOptions
                        ).options
                    7 -> ib.umaVersion = bytes.getString(offset.valueOffset(), length)
                    8 -> ib.commentCharsAllowed = bytes.getNumber(offset.valueOffset(), length)
                    9 -> ib.senderUma = bytes.getString(offset.valueOffset(), length)
                    10 -> ib.invoiceLimit = bytes.getNumber(offset.valueOffset(), length)
                    11 -> ib.kycStatus = (bytes.getByteCodeable(offset.valueOffset(), length, InvoiceKycStatus::fromBytes) as InvoiceKycStatus).status
                    12 -> ib.callback = bytes.getString(offset.valueOffset(), length)
                    100 -> ib.signature = bytes.sliceArray(offset.valueOffset()..< offset.valueOffset()+length
                    )
                }
                offset = offset.valueOffset() + length
            }
            return ib.build()
        }

        fun fromBech32(bech32String: String): Invoice {
            val b32data = Bech32.decodeBech32(bech32String)
            return fromTLV(b32data.data)
        }
    }
}

class InvoiceBuilder {
    var receiverUma: String? = null
    var invoiceUUID: String? = null
    var amount: Int? = null
    var receivingCurrency: InvoiceCurrency? = null
    var expiration: Int? = null
    var isSubjectToTravelRule: Boolean? = null
    var requiredPayerData: CounterPartyDataOptions? = null
    var umaVersion: String? = null
    var commentCharsAllowed: Int? = null
    var senderUma: String? = null
    var invoiceLimit: Int? = null
    var kycStatus: KycStatus? = null
    var callback: String? = null
    var signature: ByteArray? = null

    private fun validate() {
        val requiredFields = listOf(
            "receiverUma",
            "invoiceUUID",
            "amount",
            "receivingCurrency",
            "expiration",
            "isSubjectToTravelRule",
            "umaVersion",
            "callback"
        )
        val missingRequiredFields = this::class.members.mapNotNull {
            if (it.name in requiredFields && it.call(this) == null) {
                it.name
            } else null
        }
        if (missingRequiredFields.isNotEmpty()) {
            throw MalformedUmaInvoiceException("missing required fields: ${missingRequiredFields}")
        }
    }

    /**
     * build invoice object.  Certain fields are required to be non null
     */
    fun build(): Invoice {
        validate()
        return Invoice(
            receiverUma = receiverUma!!,
            invoiceUUID = invoiceUUID!!,
            amount = amount!!,
            receivingCurrency = receivingCurrency!!,
            expiration = expiration!!,
            isSubjectToTravelRule = isSubjectToTravelRule!!,
            requiredPayerData = requiredPayerData,
            umaVersion = umaVersion!!,
            commentCharsAllowed = commentCharsAllowed,
            senderUma = senderUma,
            invoiceLimit = invoiceLimit,
            kycStatus = kycStatus,
            callback = callback!!,
            signature = signature,
        )
    }
}

@OptIn(ExperimentalSerializationApi::class)
class InvoiceTLVSerializer: KSerializer<Invoice> {
    private val delegateSerializer = ByteArraySerializer()
    override val descriptor = SerialDescriptor("Invoice", delegateSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: Invoice) {
        encoder.encodeSerializableValue(
            delegateSerializer,
            value.toTLV()
        )
    }

    override fun deserialize(decoder: Decoder) = Invoice.fromTLV(
        decoder.decodeSerializableValue(delegateSerializer)
    )
}

data class InvoiceCounterPartyDataOptions(
    val options: CounterPartyDataOptions
) : ByteCodeable {
    override fun toBytes(): ByteArray {
        return options.entries
            .sortedBy { it.key }
            .joinToString(",") { (key, option) ->
                "${key}:${if (option.mandatory) 1 else 0}"
            }
            .toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun fromBytes(bytes: ByteArray): InvoiceCounterPartyDataOptions {
            val optionsString = String(bytes)
            return InvoiceCounterPartyDataOptions(
                optionsString.split(",").mapNotNull {
                    val options = it.split(':')
                    if (options.size == 2) {
                        options[0] to CounterPartyDataOption(options[1] == "1")
                    } else null
                }.toMap()
            )
        }
    }
}

data class InvoiceKycStatus(val status: KycStatus): ByteCodeable {
    override fun toBytes(): ByteArray {
        return status.rawValue.toByteArray()
    }

    companion object {
        fun fromBytes(bytes: ByteArray): InvoiceKycStatus {
            return InvoiceKycStatus(
                KycStatus.fromRawValue(bytes.toString(Charsets.UTF_8))
            )
        }
    }
}
