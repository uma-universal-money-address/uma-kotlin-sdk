package me.uma.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure


class InvoiceTLVSerializer: KSerializer<Invoice> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Invoice") {
        element<String>("receiverUma")
        element<String>("invoiceUUID")
        element<Int>("amount")
        element<Boolean>("isSubjectToTravelRule")
        element<String>("umaVersion")
        element<Int>("commentCharsAllowed")
        element<String>("senderUma")
        element<Int>("invoiceLimit")
        element<String>("callback")

    }

    override fun serialize(encoder: Encoder, value: Invoice) {
        encoder.encodeStructure(descriptor) {
            encodeByteElement(descriptor, 0, '0'.toByte())
            encodeByteElement(descriptor, 1, value.receiverUma.length.toByte())
        }
    }

    override fun deserialize(decoder: Decoder): Invoice {
        TODO("Not yet implemented")
    }
}
