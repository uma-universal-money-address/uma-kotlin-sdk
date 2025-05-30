@file:JvmName("PayeeData")

package me.uma.protocol

import me.uma.utils.serialFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

typealias PayeeData = JsonObject

@JvmOverloads
fun createPayeeData(
    compliance: CompliancePayeeData? = null,
    identifier: String? = null,
    name: String? = null,
    email: String? = null,
): PayerData {
    val payerDataMap = mutableMapOf<String, JsonElement>()
    if (compliance != null) {
        payerDataMap[CounterPartyDataKeys.COMPLIANCE] = serialFormat.encodeToJsonElement(compliance)
    }
    if (identifier != null) {
        payerDataMap[CounterPartyDataKeys.IDENTIFIER] = JsonPrimitive(identifier)
    }
    if (name != null) {
        payerDataMap[CounterPartyDataKeys.NAME] = JsonPrimitive(name)
    }
    if (email != null) {
        payerDataMap[CounterPartyDataKeys.EMAIL] = JsonPrimitive(email)
    }
    return JsonObject(payerDataMap)
}

fun PayeeData.payeeCompliance(): CompliancePayeeData? {
    val jsonCompliance = get(CounterPartyDataKeys.COMPLIANCE) ?: return null
    return serialFormat.decodeFromJsonElement(jsonCompliance)
}

/**
 * The compliance data from the receiver, including utxo info.
 *
 * @property utxos A list of UTXOs of channels over which the receiver will likely receive the payment.
 * @property nodePubKey If known, the public key of the receiver's node. If supported by the sending VASP's compliance
 *     provider, this will be used to pre-screen the receiver's UTXOs for compliance purposes.
 * @property utxoCallback The URL that the sender VASP will call to send UTXOs of the channel that the sender used to
 *     send the payment once it completes.
 * @property signature The signature of the receiver on the signable payload.
 * @property signatureNonce The nonce used in the signature.
 * @property signatureTimestamp The timestamp used in the signature.
 * @property backingSignatures The list of backing signatures from VASPs that can attest to the authenticity of the message.
 */
@Serializable
data class CompliancePayeeData @JvmOverloads constructor(
    val utxos: List<String>,
    val nodePubKey: String?,
    val utxoCallback: String = "",
    val signature: String,
    val signatureNonce: String,
    val signatureTimestamp: Long,
    val backingSignatures: List<BackingSignature>? = null,
) {
    fun signedWith(signature: String) = copy(signature = signature)
}
