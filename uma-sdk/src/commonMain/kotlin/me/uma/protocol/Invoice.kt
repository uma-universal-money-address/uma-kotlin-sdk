package me.uma.protocol

import me.uma.utils.ByteCodeable
import me.uma.utils.TLVCodeable
import java.nio.ByteBuffer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = InvoiceCurrencyTLVSerializer::class)
data class InvoiceCurrency(
    val code: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
) : TLVCodeable {

    companion object {
        fun fromTLV(bytes: ByteArray): InvoiceCurrency {
            var code = ""
            var name = ""
            var symbol = ""
            var decimals = -1
            var offset = 0
            while(offset < bytes.size) {
                when(bytes[offset].toInt()) {
                    0 ->  {
                        val length = bytes[offset+1].toInt()
                        code = bytes.getTLVString(offset+2, bytes[offset+1].toInt())
                        offset += 2 + length
                    }
                    1 ->  {
                        val length = bytes[offset+1].toInt()
                        name = bytes.getTLVString(offset+2, bytes[offset+1].toInt())
                        offset += 2 + length
                    }
                    2 ->  {
                        val length = bytes[offset+1].toInt()
                        symbol = bytes.getTLVString(offset+2, bytes[offset+1].toInt())
                        offset += 2 + length
                    }
                    3 -> {
                        val length = bytes[offset+1].toInt()
                        decimals = getTLVInt(bytes.slice(offset+2..<offset+2+length))
                        offset += 2 + length
                    }
                }

            }
            return InvoiceCurrency(name, symbol, code, decimals)
        }
    }

    override fun toTLV(): ByteArray {
        val bytes = ByteBuffer.allocate(
            6 + name.length + code.length + symbol.length + 3
        )
            .putTLVString(0, code)
            .putTLVString(1, name)
            .putTLVString(2, symbol)
            .putTLVNumber(3, decimals)
            .array()
        return bytes
    }
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

private fun ByteArray.getTLVString(offset: Int, length: Int): String {
    val decodedResult = String(
        slice(offset..<offset+length).toByteArray()
    )
    return decodedResult
}

private fun getTLVInt(bytes: List<Byte>): Int {
    return bytes[0].toInt()
}

@Serializable(with = InvoiceTLVSerializer::class)
class Invoice(
    val receiverUma: String,

    // Invoice UUID Served as both the identifier of the UMA invoice, and the validation of proof of payment.
    val invoiceUUID: String,

    // The amount of invoice to be paid in the smalest unit of the ReceivingCurrency.
    val amount: Int,

    // The currency of the invoice
    val receivingCurrency: InvoiceCurrency,

    // The unix timestamp the UMA invoice expires
    val expiration: Int,

    // Indicates whether the VASP is a financial institution that requires travel rule information.
    val isSubjectToTravelRule: Boolean,

    // RequiredPayerData the data about the payer that the sending VASP must provide in order to send a payment.
    val requiredPayerData: CounterPartyDataOptions,

    // UmaVersion is a list of UMA versions that the VASP supports for this transaction. It should be
    // containing the lowest minor version of each major version it supported, separated by commas.
    val umaVersion: String,

    // CommentCharsAllowed is the number of characters that the sender can include in the comment field of the pay request.
    val commentCharsAllowed: Int,

    // The sender's UMA address. If this field presents, the UMA invoice should directly go to the sending VASP instead of showing in other formats.
    val senderUma: String,

    // The maximum number of the invoice can be paid
    val invoiceLimit: Int,

    // KYC status of the receiver, default is verified.
    val kycStatus: KycStatus,

    // The callback url that the sender should send the PayRequest to.
    val callback: String,

    // The signature of the UMA invoice
    val signature: ByteArray,
) : TLVCodeable {
    override fun toTLV(): ByteArray {
        val bytes = ByteBuffer.allocate(
            6 + receiverUma.length + invoiceUUID.length + umaVersion.length + 3
        )
            .putTLVString(0, receiverUma)
            .putTLVString(1, invoiceUUID)
            .putTLVString(2, umaVersion)
            .putTLVNumber(3, invoiceLimit)
            .array()
        return bytes
    }
}

private fun ByteBuffer.putTLVString(tag: Int, value: String): ByteBuffer {
    return put(tag.toByte())
    .put(value.length.toByte())
    .put(value.toByteArray())
}

private fun ByteBuffer.putTLVNumber(tag: Int, value: Int): ByteBuffer {
    return put(tag.toByte())
    .put(1)
    .put(value.toByte())
}

private fun ByteBuffer.putTLVBoolean(tag: Int, value: Boolean): ByteBuffer {
    return put(tag.toByte())
        .put(1)
        .put(if (value) 1 else 0)
}

private fun ByteBuffer.putByteCodable(tag: Int, value: ByteCodeable): ByteBuffer {
    val encodedBytes = value.toBytes()
    return put(tag.toByte())
        .put(encodedBytes.size.toByte())
        .put(encodedBytes)
}

private fun ByteBuffer.putTLVCodeable(tag: Int, value: TLVCodeable): ByteBuffer {
    val encodedBytes = value.toTLV()
    return put(tag.toByte())
        .put(encodedBytes.size.toByte())
        .put(encodedBytes)
}
