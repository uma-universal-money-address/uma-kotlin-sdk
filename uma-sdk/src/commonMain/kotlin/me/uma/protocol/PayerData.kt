@file:JvmName("PayerData")

package me.uma.protocol

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import me.uma.UmaException
import me.uma.generated.ErrorCode
import me.uma.utils.serialFormat

typealias PayerData = JsonObject

@JvmOverloads
fun createPayerData(
  identifier: String,
  compliance: CompliancePayerData? = null,
  name: String? = null,
  email: String? = null,
): PayerData {
    val payerDataMap = mutableMapOf<String, JsonElement>("identifier" to JsonPrimitive(identifier))
    if (compliance != null) {
        payerDataMap["compliance"] = serialFormat.encodeToJsonElement(compliance)
    }
    if (name != null) {
        payerDataMap["name"] = JsonPrimitive(name)
    }
    if (email != null) {
        payerDataMap["email"] = JsonPrimitive(email)
    }
    return JsonObject(payerDataMap)
}

fun PayerData.compliance(): CompliancePayerData? {
    val jsonCompliance = get("compliance") ?: return null
    return serialFormat.decodeFromJsonElement(CompliancePayerDataSerializer, jsonCompliance)
}

fun PayerData.identifier(): String? = get("identifier")?.jsonPrimitive?.content

/**
 * The compliance data from the sender, including utxo info.
 *
 * @property utxos The list of UTXOs of the sender's channels that might be used to fund the payment.
 * @property nodePubKey If known, the public key of the sender's node. If supported by the receiver VASP's compliance
 *   provider, this will be used to pre-screen the sender's UTXOs for compliance purposes.
 * @property kycStatus Indicates whether VASP1 has KYC information about the sender.
 * @property encryptedTravelRuleInfo The travel rule information of the sender. This is encrypted with the receiver's
 *   public encryption key.
 * @property utxoCallback The URL that the receiver will call to send UTXOs of the channel that the receiver used to
 *   receive the payment once it completes.
 * @property signature The signature of the sender on the signable payload.
 * @property signatureNonce The nonce used in the signature.
 * @property signatureTimestamp The timestamp used in the signature.
 * @property travelRuleFormat An optional standardized format of the travel rule information (e.g. IVMS). Null indicates
 *   raw json or a custom format.
 * @property backingSignatures The list of backing signatures from VASPs that can attest to the authenticity of the
 *   message.
 */
@Serializable(with = CompliancePayerDataSerializer::class)
data class CompliancePayerData
@JvmOverloads
constructor(
  val utxos: List<String>,
  val nodePubKey: String?,
  val kycStatus: KycStatus,
  val encryptedTravelRuleInfo: String?,
  val utxoCallback: String,
  val signature: String,
  val signatureNonce: String,
  val signatureTimestamp: Long,
  val travelRuleFormat: TravelRuleFormat? = null,
  val backingSignatures: List<BackingSignature>? = null,
  // If adding new fields, please update the serializer below.
) {
    fun signedWith(signature: String) = copy(signature = signature)
}

/**
 * The entire point of this custom serializer is to gracefully handle missing `utxos` fields because some SDKs allow for
 * that field to be missing. This will be resolved in the next major version bump, but for now, this needs to be handled
 * directly.
 *
 * Note: This can't just use JsonTransformingSerializer (although I'd love to) because they can't easily be registered
 * as default serializers for a specific type without causing circular references or other runtime failures. I tried to
 * make that work for a while because it's much cleaner, but eventually gave up.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object CompliancePayerDataSerializer : KSerializer<CompliancePayerData> {
    override val descriptor: SerialDescriptor =
      buildClassSerialDescriptor("CompliancePayerData") {
          element<List<String>>("utxos")
          element<String?>("nodePubKey", isOptional = true)
          element<KycStatus>("kycStatus")
          element<String?>("encryptedTravelRuleInfo", isOptional = true)
          element<String>("utxoCallback")
          element<String>("signature")
          element<String>("signatureNonce")
          element<Long>("signatureTimestamp")
          element<TravelRuleFormat?>("travelRuleFormat", isOptional = true)
          element<List<BackingSignature>?>("backingSignatures", isOptional = true)
      }

    override fun serialize(encoder: Encoder, value: CompliancePayerData) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, ListSerializer(String.serializer()), value.utxos)
            encodeNullableSerializableElement(descriptor, 1, String.serializer().nullable, value.nodePubKey)
            encodeSerializableElement(descriptor, 2, KycStatus.serializer(), value.kycStatus)
            encodeNullableSerializableElement(
              descriptor,
              3,
              String.serializer().nullable,
              value.encryptedTravelRuleInfo,
            )
            encodeStringElement(descriptor, 4, value.utxoCallback)
            encodeStringElement(descriptor, 5, value.signature)
            encodeStringElement(descriptor, 6, value.signatureNonce)
            encodeLongElement(descriptor, 7, value.signatureTimestamp)
            encodeNullableSerializableElement(
              descriptor,
              8,
              TravelRuleFormatSerializer().nullable,
              value.travelRuleFormat,
            )
            encodeNullableSerializableElement(
              descriptor,
              9,
              ListSerializer(BackingSignature.serializer()).nullable,
              value.backingSignatures,
            )
        }
    }

    override fun deserialize(decoder: Decoder): CompliancePayerData {
        return decoder.decodeStructure(descriptor) {
            var utxos: List<String>? = null
            var nodePubKey: String? = null
            var kycStatus: KycStatus? = null
            var encryptedTravelRuleInfo: String? = null
            var utxoCallback: String? = null
            var signature: String? = null
            var signatureNonce: String? = null
            var signatureTimestamp: Long? = null
            var travelRuleFormat: TravelRuleFormat? = null
            var backingSignatures: List<BackingSignature>? = null

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> break
                    0 -> utxos = decodeSerializableElement(descriptor, 0, ListSerializer(String.serializer()))
                    1 -> nodePubKey = decodeNullableSerializableElement(descriptor, 1, String.serializer().nullable)
                    2 -> kycStatus = decodeSerializableElement(descriptor, 2, KycStatus.serializer())
                    3 ->
                      encryptedTravelRuleInfo =
                        decodeNullableSerializableElement(descriptor, 3, String.serializer().nullable)

                    4 -> utxoCallback = decodeStringElement(descriptor, 4)
                    5 -> signature = decodeStringElement(descriptor, 5)
                    6 -> signatureNonce = decodeStringElement(descriptor, 6)
                    7 -> signatureTimestamp = decodeLongElement(descriptor, 7)
                    8 ->
                      travelRuleFormat =
                        decodeNullableSerializableElement(descriptor, 8, TravelRuleFormatSerializer().nullable)

                    9 ->
                      backingSignatures =
                        decodeNullableSerializableElement(
                          descriptor,
                          9,
                          ListSerializer(BackingSignature.serializer()).nullable,
                        )

                    else ->
                      throw UmaException(
                        "Unexpected field index $index when deserializing CompliancePayerData",
                        ErrorCode.PARSE_PAYREQ_REQUEST_ERROR,
                      )
                }
            }

            CompliancePayerData(
              utxos = utxos ?: emptyList(),
              nodePubKey = nodePubKey,
              kycStatus =
                kycStatus ?: throw UmaException("kycStatus is missing", ErrorCode.MISSING_REQUIRED_UMA_PARAMETERS),
              encryptedTravelRuleInfo = encryptedTravelRuleInfo,
              utxoCallback = utxoCallback ?: "",
              signature =
                signature ?: throw UmaException("signature is missing", ErrorCode.MISSING_REQUIRED_UMA_PARAMETERS),
              signatureNonce =
                signatureNonce
                  ?: throw UmaException("signatureNonce is missing", ErrorCode.MISSING_REQUIRED_UMA_PARAMETERS),
              signatureTimestamp =
                signatureTimestamp
                  ?: throw UmaException("signatureTimestamp is missing", ErrorCode.MISSING_REQUIRED_UMA_PARAMETERS),
              travelRuleFormat = travelRuleFormat,
              backingSignatures = backingSignatures,
            )
        }
    }
}

/** A standardized format of the travel rule information. */
@Serializable(with = TravelRuleFormatSerializer::class)
data class TravelRuleFormat(
  /** The type of the travel rule format (e.g. IVMS). */
  val type: String,
  /** The version of the travel rule format (e.g. 1.0). */
  val version: String?,
)

/**
 * Serializes the TravelRuleFormat to string in the format of "type@version". If there's no version, it will be
 * serialized as "type".
 */
class TravelRuleFormatSerializer : KSerializer<TravelRuleFormat> {
    override val descriptor = PrimitiveSerialDescriptor("TravelRuleFormat", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): TravelRuleFormat {
        val value = decoder.decodeString()
        if (!value.contains("@")) {
            return TravelRuleFormat(value, null)
        }
        val parts = value.split("@")
        return TravelRuleFormat(parts[0], parts.getOrNull(1))
    }

    override fun serialize(encoder: Encoder, value: TravelRuleFormat) {
        encoder.encodeString("${value.type}${value.version?.let { "@$it" } ?: ""}")
    }
}
