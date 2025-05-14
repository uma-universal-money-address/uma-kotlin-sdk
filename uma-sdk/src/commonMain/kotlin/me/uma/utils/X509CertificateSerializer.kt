package me.uma.utils

import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@OptIn(ExperimentalStdlibApi::class)
class X509CertificateSerializer : KSerializer<X509Certificate> {
    override val descriptor = buildClassSerialDescriptor("X509Certificate")

    override fun serialize(encoder: Encoder, value: X509Certificate) {
        encoder.encodeString(value.encoded.toHexString())
    }

    override fun deserialize(decoder: Decoder): X509Certificate {
        val bytes = decoder.decodeString().hexToByteArray()
        return CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(bytes))
          as X509Certificate
    }
}
